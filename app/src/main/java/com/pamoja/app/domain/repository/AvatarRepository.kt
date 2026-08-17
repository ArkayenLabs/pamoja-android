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

    /**
     * Uploads a group's photo and returns its download URL.
     *
     * [uploaderId] is part of the storage path, not decoration. Storage rules
     * cannot read Firestore, so they have no way to ask who a group's admin is;
     * keying the object on the uploader is what lets them enforce anything at
     * all. Making the object *become* the group's photo is a separate write to
     * the group document, which the Firestore rules do gate on admin, so a
     * member who uploaded their own object could never point the group at it.
     */
    suspend fun uploadGroupAvatar(
        groupId: String,
        uploaderId: String,
        imageUri: String,
    ): Result<String>

    /** Removes a group photo this user uploaded. Succeeds when there was none. */
    suspend fun deleteGroupAvatar(groupId: String, uploaderId: String): Result<Unit>
}
