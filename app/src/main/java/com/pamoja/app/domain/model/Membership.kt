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
    /**
     * Denormalised copy of the member's photo, for the same reason
     * [displayName] is: users/{userId} is owner-only, so a leaderboard cannot
     * read other people's profiles to find their avatar.
     *
     * Blank whenever the member has not opted in. That is what makes
     * [com.pamoja.app.domain.model.User.showPhotoInGroups] a real privacy
     * control rather than a client-side courtesy: opting out removes the URL
     * from the only document other members can see.
     */
    val photoUrl: String = "",
    val joinedAt: Long = 0L
)