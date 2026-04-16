package com.pamoja.app.domain.model

data class Group(
    val groupId: String = "",
    val name: String = "",
    val adminId: String = "",
    val weeklyTarget: Int = 70000,
    val maxMemberCap: Int = 10,
    val inviteLink: String = "",
    val inviteLinkActive: Boolean = true,
    val createdAt: Long = 0L
)