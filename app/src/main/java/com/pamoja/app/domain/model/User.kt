package com.pamoja.app.domain.model

data class User(
    val userId: String = "",
    val name: String = "",
    val photoUrl: String? = null,
    val age: Int? = null,
    val height: Float? = null,
    val weight: Float? = null,
    val deviceToken: String? = null
)