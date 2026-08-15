package com.pamoja.app.data.remote.firebase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import com.google.firebase.storage.FirebaseStorage
import com.pamoja.app.domain.error.AppError
import com.pamoja.app.domain.repository.AvatarRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.max

/**
 * Profile photos, in Firebase Storage.
 *
 * One object per user at a path derived from their UID, never a generated
 * filename. A known path is what lets account deletion find the photo to
 * destroy, and it means uploading again replaces rather than accumulates: a
 * user who changes their picture ten times costs one object, not ten.
 *
 * Every upload is downscaled and recompressed first. A modern phone camera
 * produces four to eight megabytes, and an avatar is rendered at 80dp. Sending
 * the original would be paying storage and egress forever for pixels nobody
 * can see, on a bucket with no backend in front of it to stop us.
 */
class FirebaseAvatarRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: FirebaseStorage,
) : AvatarRepository {

    override suspend fun uploadAvatar(userId: String, imageUri: String): Result<String> =
        avatarCatching {
            val bytes = withContext(Dispatchers.IO) { compress(imageUri) }
                ?: throw AppError.Validation(
                    com.pamoja.app.domain.error.ValidationField.AvatarUnreadable
                )

            val ref = avatarRef(userId)
            ref.putBytes(bytes).await()
            // The download URL carries a token, so it must be read back after
            // the upload rather than constructed from the path.
            ref.downloadUrl.await().toString()
        }

    override suspend fun deleteAvatar(userId: String): Result<Unit> = avatarCatching {
        runCatching { avatarRef(userId).delete().await() }
        // Deliberately swallows a failure. There is nothing to delete when the
        // user never had a photo, and Storage reports that as an error. Account
        // deletion must not stop over a missing file.
        Unit
    }

    private fun avatarRef(userId: String) =
        storage.reference.child("avatars/$userId/profile.jpg")

    /**
     * Decodes at a sample size chosen from the source dimensions, so a large
     * photo is never fully decoded into memory first. Decoding an 8000px image
     * at full size to then shrink it is how an avatar picker runs a phone out
     * of heap.
     */
    private fun compress(imageUri: String): ByteArray? {
        val uri = imageUri.toUri()

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null

        val longestEdge = max(bounds.outWidth, bounds.outHeight)
        if (longestEdge <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = generateSequence(1) { it * 2 }
                .first { longestEdge / it <= TARGET_EDGE_PX * 2 }
        }

        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val scale = TARGET_EDGE_PX.toFloat() / max(decoded.width, decoded.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            decoded
        }

        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            out.toByteArray()
        }
    }

    /** As elsewhere: never swallow cancellation, always map to an AppError. */
    private suspend fun <T> avatarCatching(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e.toFirebaseAppError())
    }

    private companion object {
        /** Generous for an 80dp circle, still tiny. Covers future larger uses. */
        const val TARGET_EDGE_PX = 512

        /** Indistinguishable from 100 at this size, roughly a third the bytes. */
        const val JPEG_QUALITY = 85
    }
}
