package com.pamoja.app.domain.model

/**
 * Everything Pamoja holds about one person, gathered for export.
 *
 * The counterpart to account deletion, and the same list of places: the user
 * document, every membership, and every step entry. If a collection is ever
 * added to one of those two, it belongs in the other on the same day, or the
 * app is either failing to erase something or failing to disclose it.
 *
 * Groups are included as they were at export time because a membership on its
 * own is an opaque ID. Only the fields the user can already see are carried:
 * this is their data, not the other members'.
 */
data class UserDataExport(
    val user: User,
    val memberships: List<Membership>,
    val groups: List<Group>,
    val steps: List<StepEntry>,
    /** Epoch millis the export was produced. */
    val exportedAt: Long,
)
