package com.pamoja.app.data.remote.model

data class MembershipDto(
    val userId: String = "",
    val groupId: String = "",
    val role: String = "member",
    val canEditTarget: Boolean = false,
    val joinedAt: Long = 0L
) {
    fun toDomain() = com.pamoja.app.domain.model.Membership(
        userId = userId,
        groupId = groupId,
        role = role,
        canEditTarget = canEditTarget,
        joinedAt = joinedAt
    )

    companion object {
        fun fromDomain(membership: com.pamoja.app.domain.model.Membership) = MembershipDto(
            userId = membership.userId,
            groupId = membership.groupId,
            role = membership.role,
            canEditTarget = membership.canEditTarget,
            joinedAt = membership.joinedAt
        )
    }
}