package com.pamoja.app.ui.profile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * [decodeForCrop], against real image files.
 *
 * This exists because the crop screen shipped unable to open **any** photo. The
 * bounds pass was written as
 *
 * ```
 * contentResolver.openInputStream(uri)?.use { decodeStream(it, null, bounds) } ?: return null
 * ```
 *
 * which reads like a null check on the stream and is actually a null check on
 * the decoded bitmap. `BitmapFactory.decodeStream` returns null *by contract*
 * while `inJustDecodeBounds` is set, so that line returned null for every image
 * that has ever existed. The user-visible symptom was "That image could not be
 * opened. Try another one." on every single photo, for both group photos and
 * profile photos, since both routes reach this screen.
 *
 * The identical mistake had already been found and fixed in
 * `FirebaseAvatarRepositoryImpl.compress`, in the same commit that introduced
 * this copy of it. One was fixed, one was not, and no test covered either.
 *
 * So the first test here is the whole point: a real JPEG must decode.
 */
@RunWith(AndroidJUnit4::class)
class PhotoCropDecodeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Writes a real JPEG to the cache and returns its uri, as the picker would. */
    private fun writeJpeg(width: Int, height: Int, name: String): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.rgb(196, 92, 60))

        val file = File(context.cacheDir, name)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
        return Uri.fromFile(file).toString()
    }

    /**
     * The regression. If this fails, no photo in the app can be cropped, and
     * therefore no group photo and no profile photo can be set at all.
     */
    @Test
    fun decodesARealJpeg() {
        val uri = writeJpeg(1200, 900, "crop_test_basic.jpg")

        val decoded = decodeForCrop(context, uri)

        assertNotNull("A real JPEG must decode. This is the bug that shipped.", decoded)
    }

    /**
     * A camera-sized photo must come back downsampled rather than whole. The
     * bitmap is held in composition while the user drags it around, so a full
     * decode of a modern camera file is tens of megabytes of heap.
     */
    @Test
    fun downsamplesALargePhotoButStillReturnsIt() {
        val uri = writeJpeg(5000, 3000, "crop_test_large.jpg")

        val decoded = decodeForCrop(context, uri)

        assertNotNull(decoded)
        val longestEdge = maxOf(decoded!!.width, decoded.height)
        // Sampled down towards DECODE_EDGE_PX, so it must be well under source.
        assertEquals(true, longestEdge < 5000)
    }

    /** A small image is worth keeping at full size, so it must not be shrunk. */
    @Test
    fun leavesASmallPhotoAlone() {
        val uri = writeJpeg(400, 300, "crop_test_small.jpg")

        val decoded = decodeForCrop(context, uri)

        assertNotNull(decoded)
        assertEquals(400, decoded!!.width)
        assertEquals(300, decoded.height)
    }

    /**
     * The other half of the pair: the failure path must still fail. A fix that
     * simply stopped returning null would pass the test above and be worse than
     * the bug, because the crop screen would then hand a null bitmap onward.
     */
    @Test
    fun stillRejectsSomethingThatIsNotAnImage() {
        val file = File(context.cacheDir, "crop_test_not_an_image.jpg")
        file.writeText("this is not a JPEG, it is a sentence")

        val decoded = decodeForCrop(context, Uri.fromFile(file).toString())

        assertNull(decoded)
    }

    @Test
    fun stillRejectsAMissingFile() {
        val missing = Uri.fromFile(File(context.cacheDir, "crop_test_absent.jpg")).toString()

        val decoded = decodeForCrop(context, missing)

        assertNull(decoded)
    }
}
