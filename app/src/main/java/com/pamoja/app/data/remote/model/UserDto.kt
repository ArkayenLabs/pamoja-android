package com.pamoja.app.data.remote.model

data class UserDto(
    val userId: String = "",
    val name: String = "",
    val photoUrl: String? = null,
    val age: Int? = null,
    val height: Float? = null,
    val weight: Float? = null,
    /** See User.showPhotoInGroups. Defaults false, which is the private choice. */
    val showPhotoInGroups: Boolean = false,
) {
    fun toDomain() = com.pamoja.app.domain.model.User(
        userId = userId,
        name = name,
        photoUrl = photoUrl,
        age = age,
        height = height,
        weight = weight,
        showPhotoInGroups = showPhotoInGroups,
    )

    companion object {
        fun fromDomain(user: com.pamoja.app.domain.model.User) = UserDto(
            userId = user.userId,
            name = user.name,
            photoUrl = user.photoUrl,
            age = user.age,
            height = user.height,
            weight = user.weight,
            showPhotoInGroups = user.showPhotoInGroups,
        )
    }
}
