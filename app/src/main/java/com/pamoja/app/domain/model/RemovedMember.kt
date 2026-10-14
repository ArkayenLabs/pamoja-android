package com.pamoja.app.domain.model

/**
 * Someone the group's admin removed. They cannot rejoin with the group's link
 * until the admin lets them back in (firestore.rules, groups/{id}/removed).
 */
data class RemovedMember(
    val userId: String,
    /** Their name when they were removed. */
    val displayName: String,
    /** Epoch millis; 0 until the server has stamped it. */
    val removedAt: Long,
)
