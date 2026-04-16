package com.pamoja.app.data.remote.model

data class UserDto(
    val userId: String = "",
    val name: String = "",
    val photoUrl: String? = null,
    val age: Int? = null,
    val height: Float? = null,
    val weight: Float? = null,
    val deviceToken: String? = null
) {
    fun toDomain() = com.pamoja.app.domain.model.User(
        userId = userId,
        name = name,
        photoUrl = photoUrl,
        age = age,
        height = height,
        weight = weight,
        deviceToken = deviceToken
    )

    companion object {
        fun fromDomain(user: com.pamoja.app.domain.model.User) = UserDto(
            userId = user.userId,
            name = user.name,
            photoUrl = user.photoUrl,
            age = user.age,
            height = user.height,
            weight = user.weight,
            deviceToken = user.deviceToken
        )
    }
}