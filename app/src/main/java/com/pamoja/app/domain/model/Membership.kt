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
    val joinedAt: Long = 0L,
    /**
     * This member's combined steps for [weekStart], denormalised here.
     *
     * Same reasoning as [displayName] and [photoUrl]: the leaderboard needs it
     * for every member, and reading it from the steps collection meant a query
     * across other people's step documents. That query could not be secured,
     * because a list rule may only refer to fields the query filters on and the
     * leaderboard's query names no group at all, so any rule permitting it also
     * permitted reading every user's steps. Written by each member's own device.
     */
    val weeklySteps: Long = 0L,
    /** ISO date of the week [weeklySteps] belongs to. Blank means never synced. */
    val weekStart: String = "",
    /**
     * This member's steps so far today, denormalised for the same reason.
     *
     * The leaderboard ranks on today rather than on the week, so this is not a
     * detail that could be dropped when the numbers moved off the steps
     * collection.
     */
    val todaySteps: Long = 0L,
    /**
     * ISO date [todaySteps] belongs to.
     *
     * Read it before trusting the figure. A member whose device has not synced
     * since yesterday carries yesterday's count, and showing that as today's is
     * the one way this denormalisation can lie.
     */
    val todayDate: String = "",
)