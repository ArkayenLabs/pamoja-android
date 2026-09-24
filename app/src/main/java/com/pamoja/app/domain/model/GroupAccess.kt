package com.pamoja.app.domain.model

import com.pamoja.app.domain.error.AppError

/** Paid capabilities the installed app knows how to enforce. */
enum class GroupFeature(val wireName: String) {
    CircleV1("circle_v1"),
    NextWeekTogetherV1("next_week_together_v1");

    companion object {
        fun fromWireName(value: String): GroupFeature? =
            entries.firstOrNull { feature -> feature.wireName == value }
    }
}

/**
 * A server-issued, group-scoped access lease.
 *
 * It deliberately contains no payer, product, price or receipt data. One copy
 * is read by every current member, so paid capabilities belong to the group
 * rather than to whichever phone completed checkout.
 */
data class GroupAccess(
    val groupId: String,
    val featureSet: Set<GroupFeature>,
    val validUntilMillis: Long,
) {
    fun includes(feature: GroupFeature): Boolean = feature in featureSet
}

/** One server-created, non-renewable product Preview shared by the group. */
data class GroupPreview(
    val groupId: String,
    val featureSet: Set<GroupFeature>,
    val eligibleWeekStart: String,
    val startedAtMillis: Long,
    val validUntilMillis: Long,
) {
    fun includes(feature: GroupFeature): Boolean = feature in featureSet
}

/** The two independent server projections that may currently grant Circle. */
data class GroupAccessSnapshot(
    val premiumAccess: GroupAccess? = null,
    val preview: GroupPreview? = null,
)

/**
 * The complete state a paid-feature gate must handle.
 *
 * Only [Premium] and [Preview] grant Circle capabilities. Loading, an
 * unavailable server, an ended Preview and missing documents all fail closed
 * without breaking the free current-week group experience.
 */
sealed interface GroupAccessState {
    data object Loading : GroupAccessState
    data object Free : GroupAccessState
    data class Premium(val access: GroupAccess) : GroupAccessState
    data class Preview(val preview: GroupPreview) : GroupAccessState
    data class PreviewExpired(val preview: GroupPreview) : GroupAccessState
    data class Unavailable(val error: AppError) : GroupAccessState

    val isPremium: Boolean get() = this is Premium
    val isPreview: Boolean get() = this is Preview
    val hasCircleAccess: Boolean get() = this is Premium || this is Preview

    /**
     * Every valid paid lease gets planning, including leases issued by an older
     * backend that only advertised circle_v1. Preview deliberately stays read-only.
     */
    val hasNextWeekTogether: Boolean get() = this is Premium

    fun includes(feature: GroupFeature): Boolean = when (this) {
        is Premium -> access.includes(feature)
        is Preview -> preview.includes(feature)
        else -> false
    }
}
