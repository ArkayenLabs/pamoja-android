package com.pamoja.app.util

import java.time.DayOfWeek
import java.time.LocalTime

// ============================================================================
// PAMOJA NOTIFICATION ENGINE
// ============================================================================
// Pure Kotlin. No Android imports, so it is unit testable in isolation.
//
// ---------------------------------------------------------------------------
// COPY RULES (all four are hard requirements)
//
// 1. SHORT. Android truncates around 40 characters of title and 55 of body on
//    most devices. If it does not fit in the tray it does not exist. Titles
//    are kept under ~30 characters and bodies under ~55.
//
// 2. NEVER INSERT THE GROUP NAME INTO A SENTENCE.
//    Users name groups anything: "Sharma Family", "friends", "ready", "office",
//    "The Walkers", a single letter. Dropping that into a sentence produces
//    "friends needs 8,200" or "Still just you in ready". Both read as broken.
//    So sentences always say "your group". The real name appears only as a
//    standalone title, where no grammar can break, and only when it is short
//    enough to survive the tray.
//
// 3. WIT, NOT SHAME.
//    Duolingo and Zomato are funny because they roast the SITUATION. Duo is
//    dramatic about a streak; Zomato is dramatic about pizza. Neither tells
//    you that you personally are inadequate.
//    Pamoja may be cheeky about: the sofa, the weather, an empty group, a
//    friend nudging ahead, the app itself.
//    Pamoja is never cheeky about: the user's body, fitness, rank, being last,
//    being slow, or being behind the group average. A 2025 UCL and
//    Loughborough study of 58,881 posts found roughly a quarter of negative
//    fitness app sentiment was shame, and that it drove people away from the
//    app AND from exercising. That is the one line we do not cross.
//    Test: would this land badly on someone who is ill, injured, or having a
//    hard week? If yes, cut it.
//
// 4. NO EM DASHES. Use full stops or commas.
//
// ---------------------------------------------------------------------------
// BEHAVIOURAL MODEL
//
// Koehler effect: weaker group members work harder when they feel
// INDISPENSABLE and when comparison is against a MODERATELY better peer.
// So low contributors are told their steps are NEEDED, never that they are
// behind, and comparison is only ever against the person directly ahead.
//
// Fogg (B = MAP): a notification is only the Prompt. Ability must also be
// present, so step nudges fire only inside a window where the user can still
// act on them.
//
// Loss aversion: streaks work, but the documented failure is people engaging
// to avoid loss rather than for value. Streak lines always offer an exit.
//
// Self determination theory: notification volume itself becomes autonomy
// thwarting. Restraint is a feature. Silence is a valid outcome.
// ============================================================================

/** Categories map to separate Android channels so muting nudges does not also
 *  mute the moments that matter. */
enum class NotificationCategory(val channelId: String) {
    ACHIEVEMENT("pamoja_achievements"),
    GROUP_ACTIVITY("pamoja_group_activity"),
    REMINDER("pamoja_reminders"),
    RECAP("pamoja_recap"),
}

data class PamojaNotification(
    val category: NotificationCategory,
    val title: String,
    val body: String,
    val priority: Int,
    val groupId: String?,
    val notificationId: Int,
)

data class NotificationContext(
    val userName: String,
    val groupId: String,
    val groupName: String,
    val memberCount: Int,
    val groupAgeMs: Long,

    val weeklyGoal: Long,
    val groupStepsTotal: Long,
    val userStepsThisWeek: Long,
    val userStepsToday: Long,
    val daysLeftInWeek: Int,

    val memberJustAheadName: String? = null,
    val stepsToOvertakeMemberAhead: Long = 0L,
    val memberWhoJustPassedYouName: String? = null,

    val goalReachedJustNow: Boolean = false,
    val newMemberName: String? = null,
    val isFirstEverSync: Boolean = false,

    val currentStreakDays: Int = 0,
    val streakAtRiskToday: Boolean = false,

    val now: LocalTime,
    val today: DayOfWeek,
    val hoursSinceLastNotification: Long,
    val consecutiveIgnored: Int,

    /** Day of year. Rotates wording so repeated types do not read identically.
     *  Deterministic, so tests stay stable and nothing extra is persisted. */
    val rotationSeed: Int = 0,
)

object SmartNotificationEngine {

    private val QUIET_START: LocalTime = LocalTime.of(22, 0)
    private val QUIET_END: LocalTime = LocalTime.of(8, 0)

    /** Fogg's Ability condition. Telling someone at 21:30 they need 4,000 steps
     *  is a prompt they cannot act on, and it teaches them to ignore us. */
    private val NUDGE_WINDOW_START: LocalTime = LocalTime.of(10, 0)
    private val NUDGE_WINDOW_END: LocalTime = LocalTime.of(19, 30)

    private const val MIN_HOURS_BETWEEN_NOTIFICATIONS = 20L
    private const val IGNORES_BEFORE_BACKOFF = 4
    private const val IGNORES_BEFORE_DEEP_BACKOFF = 8

    /** Longer names get truncated rather than filling the whole title bar. */
    private const val MAX_NAME_IN_TITLE = 18

    private const val ID_ACHIEVEMENT = 2001
    private const val ID_GROUP_ACTIVITY = 2002
    private const val ID_REMINDER = 2003
    private const val ID_RECAP = 2004

    private const val TWELVE_HOURS_MS = 12 * 60 * 60 * 1000L

    /** The single most relevant notification, or null to stay silent.
     *  Silence is frequently the correct answer. Every message withheld
     *  protects the ones actually sent. */
    fun select(ctx: NotificationContext): PamojaNotification? =
        buildCandidates(ctx).filter { isAllowed(it, ctx) }.maxByOrNull { it.priority }

    // ── Gatekeeping ─────────────────────────────────────────────────────────

    private fun isAllowed(n: PamojaNotification, ctx: NotificationContext): Boolean {
        // Hitting the goal is the emotional peak of the product. Rare, and
        // unambiguously welcome, so it is the only thing that overrides quiet
        // hours and backoff.
        if (n.category == NotificationCategory.ACHIEVEMENT && ctx.goalReachedJustNow) return true

        if (isQuietHours(ctx.now)) return false
        if (ctx.hoursSinceLastNotification < MIN_HOURS_BETWEEN_NOTIFICATIONS) return false
        if (ctx.consecutiveIgnored >= IGNORES_BEFORE_DEEP_BACKOFF) return false
        if (ctx.consecutiveIgnored >= IGNORES_BEFORE_BACKOFF &&
            n.category == NotificationCategory.REMINDER
        ) return false
        if (n.category == NotificationCategory.REMINDER && !isInNudgeWindow(ctx.now)) return false

        return true
    }

    private fun isQuietHours(now: LocalTime): Boolean =
        now.isAfter(QUIET_START) || now.isBefore(QUIET_END)

    private fun isInNudgeWindow(now: LocalTime): Boolean =
        !now.isBefore(NUDGE_WINDOW_START) && !now.isAfter(NUDGE_WINDOW_END)

    // ── Candidates, highest value first ─────────────────────────────────────

    private fun buildCandidates(ctx: NotificationContext): List<PamojaNotification> {
        val out = mutableListOf<PamojaNotification>()
        val seed = ctx.rotationSeed

        val remaining = (ctx.weeklyGoal - ctx.groupStepsTotal).coerceAtLeast(0L)
        val progress = if (ctx.weeklyGoal > 0) {
            ctx.groupStepsTotal.toFloat() / ctx.weeklyGoal
        } else 0f

        // 100 · Goal reached. Peak end rule: this is both the peak and the
        // ending. Credit stays collective, because naming a top contributor
        // implies a bottom one.
        if (ctx.goalReachedJustNow) {
            out += achievement(seed, ctx, 100, listOf(
                "Goal smashed" to "All of you got there. Go sit down.",
                "That is the week" to "Weekly goal done. Take the win.",
                "You did it" to "Every single one of you.",
            ))
        }

        // 90 · First ever sync. Kills the "is this thing even on" doubt that
        // quietly murders week one retention.
        if (ctx.isFirstEverSync) {
            out += achievement(seed, ctx, 90, listOf(
                "It is working" to "Your steps are live. Your group can see them.",
                "You are on the board" to "Steps syncing. No excuses now.",
            ))
        }

        // 80 · Someone joined. Closes the loop for whoever sent the invite.
        ctx.newMemberName?.let { name ->
            val who = shortName(name)
            out += groupActivity(seed, ctx, 80, listOf(
                "$who joined" to "The group just grew.",
                "$who is in" to "One more pair of legs.",
                "Say hi to $who" to "They just joined your group.",
            ))
        }

        // 70 · Close to the goal, running out of week. Goal gradient effect:
        // motivation climbs as the target nears. Collective, so nobody is
        // singled out.
        if (remaining > 0 && progress >= 0.75f && ctx.daysLeftInWeek <= 2) {
            val left = format(remaining)
            val when_ = if (ctx.daysLeftInWeek == 1) "Today is the last day." else "Two days left."
            out += reminder(seed, ctx, 70, listOf(
                "$left to go" to when_,
                "So close" to "$left left. $when_",
                "Nearly there" to "$left between you and the goal.",
            ))
        }

        // 65 · Someone passed you. Cheeky about the friend, never about the
        // user. Always paired with something actionable.
        ctx.memberWhoJustPassedYouName?.let { name ->
            val who = shortName(name)
            out += groupActivity(seed, ctx, 65, listOf(
                "$who just passed you" to "Rude. Also fixable.",
                "$who slipped ahead" to "That is one walk away.",
                "$who is ahead now" to "Surely not for long.",
            ))
        }

        // 60 · Within reach of the person directly ahead. The Koehler
        // mechanism done properly: a moderately better peer, close enough that
        // catching them is plausible. Never the leader, never the average.
        val gap = ctx.stepsToOvertakeMemberAhead
        ctx.memberJustAheadName?.let { name ->
            if (gap in 1..2500) {
                val who = shortName(name)
                val g = format(gap)
                val mins = minutesToWalk(gap)
                out += reminder(seed, ctx, 60, listOf(
                    "$g behind $who" to "About a $mins minute walk. Just saying.",
                    "$g from $who" to "That is $mins minutes of walking.",
                    "$who is $g ahead" to "Catchable before bed.",
                ))
            }
        }

        // 55 · Streak at risk. Loss aversion, deliberately defanged. Stated
        // once, with a genuine exit. No countdown, no threat, no guilt.
        if (ctx.streakAtRiskToday && ctx.currentStreakDays >= 3) {
            val days = ctx.currentStreakDays
            out += achievement(seed, ctx, 55, listOf(
                "$days days so far" to "One walk keeps it going. Or do not. Genuinely fine.",
                "$days day streak" to "Still alive. Your call today.",
            ))
        }

        // 50 · Monday recap.
        if (ctx.today == DayOfWeek.MONDAY) {
            val mine = format(ctx.userStepsThisWeek)
            out += recap(seed, ctx, 50, listOf(
                "New week" to "You put in $mine last week. Clean slate.",
                "Here we go again" to "$mine steps behind you. Fresh target today.",
            ))
        }

        // 40 · Indispensability. This fires for exactly the person the old
        // engine mocked, and reframes their steps as NEEDED rather than
        // deficient. Rank is never mentioned. No other member is named.
        val averagePerMember = if (ctx.memberCount > 0) ctx.groupStepsTotal / ctx.memberCount else 0L
        if (remaining > 0 &&
            ctx.memberCount > 1 &&
            ctx.userStepsThisWeek < averagePerMember &&
            ctx.daysLeftInWeek in 1..4
        ) {
            val left = format(remaining)
            out += reminder(seed, ctx, 40, listOf(
                "Your group needs $left" to "Yours count too.",
                "$left left this week" to "Every step lands in the same pot.",
                "Group is $left short" to "Any walk helps close it.",
            ))
        }

        // 30 · Solo group. The old version mocked people for being alone.
        // This roasts the empty room instead, and gives them the fix.
        if (ctx.memberCount <= 1 && ctx.groupAgeMs > TWELVE_HOURS_MS) {
            out += reminder(seed, ctx, 30, listOf(
                "Party of one" to "Send that invite link to someone.",
                "It is quiet in here" to "Pamoja works better with company.",
            ))
        }

        // 20 · Weekend nudge. Lowest priority on purpose. It carries no news,
        // so it should almost never win.
        if (remaining > 0 &&
            (ctx.today == DayOfWeek.SATURDAY || ctx.today == DayOfWeek.SUNDAY) &&
            progress < 0.9f
        ) {
            val pct = percent(progress)
            out += reminder(seed, ctx, 20, listOf(
                "The sofa can wait" to "Your group is $pct percent there.",
                "Weekend legs" to "$pct percent done. Room for more.",
            ))
        }

        return out
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun pick(seed: Int, variants: List<Pair<String, String>>): Pair<String, String> =
        variants[Math.floorMod(seed, variants.size)]

    /**
     * First name only, truncated. Group members can have long or multi part
     * names, and a title that overflows is a title nobody reads.
     */
    private fun shortName(raw: String): String {
        val first = raw.trim().split(" ").firstOrNull()?.trim().orEmpty()
        val name = if (first.isBlank()) raw.trim() else first
        return if (name.length > MAX_NAME_IN_TITLE) name.take(MAX_NAME_IN_TITLE).trimEnd() else name
    }

    /** Locale independent thousands separators, so tests stay stable. */
    private fun format(value: Long): String =
        value.toString().reversed().chunked(3).joinToString(",").reversed()

    private fun percent(progress: Float): Int = (progress * 100).toInt().coerceIn(0, 100)

    /** Deliberately conservative, roughly 100 steps per minute. */
    private fun minutesToWalk(steps: Long): Int = (steps / 100).coerceAtLeast(1).toInt()

    private fun achievement(
        seed: Int, ctx: NotificationContext, priority: Int, v: List<Pair<String, String>>,
    ) = build(NotificationCategory.ACHIEVEMENT, ID_ACHIEVEMENT, seed, ctx, priority, v)

    private fun groupActivity(
        seed: Int, ctx: NotificationContext, priority: Int, v: List<Pair<String, String>>,
    ) = build(NotificationCategory.GROUP_ACTIVITY, ID_GROUP_ACTIVITY, seed, ctx, priority, v)

    private fun reminder(
        seed: Int, ctx: NotificationContext, priority: Int, v: List<Pair<String, String>>,
    ) = build(NotificationCategory.REMINDER, ID_REMINDER, seed, ctx, priority, v)

    private fun recap(
        seed: Int, ctx: NotificationContext, priority: Int, v: List<Pair<String, String>>,
    ) = build(NotificationCategory.RECAP, ID_RECAP, seed, ctx, priority, v)

    private fun build(
        category: NotificationCategory,
        id: Int,
        seed: Int,
        ctx: NotificationContext,
        priority: Int,
        variants: List<Pair<String, String>>,
    ): PamojaNotification {
        val (title, body) = pick(seed, variants)
        return PamojaNotification(category, title, body, priority, ctx.groupId, id)
    }
}
