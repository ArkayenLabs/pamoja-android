package com.pamoja.app.data.remote.model

data class GroupDto(
    val groupId: String = "",
    val name: String = "",
    val adminId: String = "",
    val weeklyTarget: Int = 70000,
    // Retired compatibility field. New shared-total groups always write zero.
    val dailyPerPersonTarget: Int = 0,
    val maxMemberCap: Int = 10,
    val memberCount: Int = 0,
    val canMembersEditTarget: Boolean = false,
    val inviteLink: String = "",
    val inviteLinkActive: Boolean = true,
    val createdAt: Long = 0L,
    // Defaulted like every other field here, which is also what makes this
    // backward compatible: groups written before these existed simply decode as
    // 0 and "", and a blank weekStart already reads as "no current total".
    val weeklySteps: Long = 0L,
    val weekStart: String = "",
    val weekStartDay: String = "",
    val photoUrl: String = "",
    val planningTimeZone: String = "",
    val plannedWeekStart: String = "",
) {
    fun toDomain() = com.pamoja.app.domain.model.Group(
        groupId = groupId,
        name = name,
        adminId = adminId,
        weeklyTarget = weeklyTarget,
        dailyPerPersonTarget = dailyPerPersonTarget,
        maxMemberCap = maxMemberCap,
        memberCount = memberCount,
        canMembersEditTarget = canMembersEditTarget,
        inviteLink = inviteLink,
        inviteLinkActive = inviteLinkActive,
        createdAt = createdAt,
        weeklySteps = weeklySteps,
        weekStart = weekStart,
        weekStartDay = weekStartDay,
        photoUrl = photoUrl,
        planningTimeZone = planningTimeZone,
        plannedWeekStart = plannedWeekStart,
    )

    companion object {
        fun fromDomain(group: com.pamoja.app.domain.model.Group) = GroupDto(
            groupId = group.groupId,
            name = group.name,
            adminId = group.adminId,
            weeklyTarget = group.weeklyTarget,
            dailyPerPersonTarget = group.dailyPerPersonTarget,
            maxMemberCap = group.maxMemberCap,
            memberCount = group.memberCount,
            canMembersEditTarget = group.canMembersEditTarget,
            inviteLink = group.inviteLink,
            inviteLinkActive = group.inviteLinkActive,
            createdAt = group.createdAt,
            weeklySteps = group.weeklySteps,
            weekStart = group.weekStart,
            weekStartDay = group.weekStartDay,
            photoUrl = group.photoUrl,
            planningTimeZone = group.planningTimeZone,
            plannedWeekStart = group.plannedWeekStart,
        )
    }
}
