package com.pamoja.app.data.remote.model

data class MembershipDto(
    val userId: String = "",
    val groupId: String = "",
    /** Denormalised copy of the member's name. See Membership for why. */
    val displayName: String = "",
    val role: String = "member",
    val canEditTarget: Boolean = false,
    /** Denormalised copy of the member's photo. Blank when they opted out. */
    val photoUrl: String = "",
    val joinedAt: Long = 0L
) {
    fun toDomain() = com.pamoja.app.domain.model.Membership(
        userId = userId,
        groupId = groupId,
        displayName = displayName,
        role = role,
        canEditTarget = canEditTarget,
        photoUrl = photoUrl,
        joinedAt = joinedAt
    )

    companion object {
        fun fromDomain(membership: com.pamoja.app.domain.model.Membership) = MembershipDto(
            userId = membership.userId,
            groupId = membership.groupId,
            displayName = membership.displayName,
            role = membership.role,
            canEditTarget = membership.canEditTarget,
            photoUrl = membership.photoUrl,
            joinedAt = membership.joinedAt
        )
    }
}
