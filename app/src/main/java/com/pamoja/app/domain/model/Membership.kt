package com.pamoja.app.domain.model

data class Membership(
    val userId: String = "",
    val groupId: String = "",
    val role: String = "member",
    val canEditTarget: Boolean = false,
    val joinedAt: Long = 0L
)