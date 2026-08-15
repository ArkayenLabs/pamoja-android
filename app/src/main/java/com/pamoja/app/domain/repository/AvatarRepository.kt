package com.pamoja.app.domain.repository

/**
 * Profile photos.
 *
 * Its own interface rather than more methods on [UserRepository], because the
 * storage behind it is a different service with a different cost model and its
 * own security rules. Keeping it separate also means the rest of the app can
 * keep working, and keep showing initials, if photos are ever switched off.
 */
interface AvatarRepository {

    /**
     * Uploads [imageUri] as this user's avatar and returns its download URL.
     *
     * The image is downscaled and recompressed before it leaves the device.
     */
    suspend fun uploadAvatar(userId: String, imageUri: String): Result<String>

    /**
     * Removes the avatar. Succeeds when there was never one to remove, so
     * account deletion cannot stall on a user who never set a photo.
     */
    suspend fun deleteAvatar(userId: String): Result<Unit>
}
