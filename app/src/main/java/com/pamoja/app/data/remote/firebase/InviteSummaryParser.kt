package com.pamoja.app.data.remote.firebase

import com.pamoja.app.domain.model.InviteSummary

/**
 * Reads `getInvitePreview`'s reply (functions-notifications/src/invite-preview.ts).
 * Defensive like every read of server data: a missing or odd field becomes a
 * safe default, and a reply without a name is no preview at all.
 */
internal fun inviteSummaryFrom(groupId: String, data: Any?): InviteSummary? {
    val map = data as? Map<*, *> ?: return null
    val name = (map["name"] as? String)?.trim().orEmpty()
    if (name.isEmpty()) return null
    fun count(key: String): Int = (map[key] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
    return InviteSummary(
        groupId = groupId,
        name = name,
        memberCount = count("memberCount"),
        maxMemberCap = count("maxMemberCap"),
        weeklyTarget = count("weeklyTarget"),
        isFull = map["full"] == true,
        startedBy = (map["startedBy"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
    )
}
