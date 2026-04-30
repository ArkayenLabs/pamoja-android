package com.pamoja.app.data.remote.model

data class GroupDto(
    val groupId: String = "",
    val name: String = "",
    val adminId: String = "",
    val weeklyTarget: Int = 70000,
    val maxMemberCap: Int = 10,
    val canMembersEditTarget: Boolean = false,
    val inviteLink: String = "",
    val inviteLinkActive: Boolean = true,
    val createdAt: Long = 0L
) {
    fun toDomain() = com.pamoja.app.domain.model.Group(
        groupId = groupId,
        name = name,
        adminId = adminId,
        weeklyTarget = weeklyTarget,
        maxMemberCap = maxMemberCap,
        canMembersEditTarget = canMembersEditTarget,
        inviteLink = inviteLink,
        inviteLinkActive = inviteLinkActive,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(group: com.pamoja.app.domain.model.Group) = GroupDto(
            groupId = group.groupId,
            name = group.name,
            adminId = group.adminId,
            weeklyTarget = group.weeklyTarget,
            maxMemberCap = group.maxMemberCap,
            canMembersEditTarget = group.canMembersEditTarget,
            inviteLink = group.inviteLink,
            inviteLinkActive = group.inviteLinkActive,
            createdAt = group.createdAt
        )
    }
}