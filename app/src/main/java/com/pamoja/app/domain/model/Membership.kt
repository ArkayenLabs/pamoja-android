package com.pamoja.app.domain.model

data class Membership(
    val userId: String = "",
    val groupId: String = "",
    /**
     * The member's display name, copied here rather than read from their user
     * document.
     *
     * Denormalised deliberately. Rendering a leaderboard previously cost one read
     * per member on top of the membership query, and that ran again on every
     * snapshot update. Storing the name here makes a member list a single query.
     *
     * It also lets users/{userId} be locked to owner-only in the security rules,
     * since nothing needs to read other people's profiles any more. That closes
     * the last route to reading someone's age, height and weight.
     *
     * Trade-off: renaming yourself has to fan out to your membership documents.
     */
    val displayName: String = "",
    val role: String = "member",
    val canEditTarget: Boolean = false,
    val joinedAt: Long = 0L
)