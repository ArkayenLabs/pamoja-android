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
// 2. PERSONAL MEANS SPECIFIC.
//    A first name alone is cosmetic. Use the live group name, teammate, gap,
//    step count, time pressure, or personal contribution that caused this
//    notification. Group names are truncated and placed only where arbitrary
//    user-created text cannot break the grammar.
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

/**
 * Minutes from midnight to a wall-clock time.
 *
 * Quiet hours are stored as an Int because a formatted string would break the
 * moment the device locale or timezone changed. Coerced rather than trusted, so
 * a corrupt value cannot throw inside the notification path.
 */
fun minuteOfDayToTime(minuteOfDay: Int): LocalTime {
    val safe = minuteOfDay.coerceIn(0, 24 * 60 - 1)
    return LocalTime.of(safe / 60, safe % 60)
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
    val userJustTookLead: Boolean = false,
    val memberJustBehindName: String? = null,
    val stepsAheadOfMemberBehind: Long = 0L,

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

    /**
     * Categories the user has switched off in Settings.
     *
     * Carried here rather than read by the engine, so the engine stays pure
     * Kotlin with no dependency on storage, and every gate input arrives the
     * same way.
     */
    val mutedCategories: Set<NotificationCategory> = emptySet(),

    /** User-chosen quiet window. Defaults match what used to be hardcoded. */
    val quietStart: LocalTime = LocalTime.of(22, 0),
    val quietEnd: LocalTime = LocalTime.of(8, 0),
)

object SmartNotificationEngine {

    // Quiet hours are no longer constants. They arrive on NotificationContext
    // because the user chooses them in Settings.

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

    /**
     * Every category at once, with the gates bypassed. Debug builds only.
     *
     * The gates are what make this engine correct in production, and they are
     * also what make it impossible to exercise by hand: quiet hours, a 20 hour
     * minimum between sends, a 10:00 to 19:30 nudge window and engagement
     * backoff mean a developer can wait a full day and see nothing. This is the
     * only way to look at all four channels without editing constants.
     */
    fun debugSamples(ctx: NotificationContext): List<PamojaNotification> =
        buildCandidates(ctx)
            .sortedByDescending { it.priority }
            .distinctBy { it.category }

    // ── Gatekeeping ─────────────────────────────────────────────────────────

    private fun isAllowed(n: PamojaNotification, ctx: NotificationContext): Boolean {
        // Checked before anything else, including the achievement override
        // below. A switch the user turned off is a decision, not a heuristic,
        // and nothing the engine believes about value should overrule it.
        if (n.category in ctx.mutedCategories) return false

        // Hitting the goal is the emotional peak of the product. Rare, and
        // unambiguously welcome, so it is the only thing that overrides quiet
        // hours and backoff.
        if (n.category == NotificationCategory.ACHIEVEMENT && ctx.goalReachedJustNow) return true

        if (isQuietHours(ctx.now, ctx.quietStart, ctx.quietEnd)) return false
        if (ctx.hoursSinceLastNotification < MIN_HOURS_BETWEEN_NOTIFICATIONS) return false
        if (ctx.consecutiveIgnored >= IGNORES_BEFORE_DEEP_BACKOFF) return false
        if (ctx.consecutiveIgnored >= IGNORES_BEFORE_BACKOFF &&
            n.category == NotificationCategory.REMINDER
        ) return false
        if (n.category == NotificationCategory.REMINDER && !isInNudgeWindow(ctx.now)) return false

        return true
    }

    /**
     * Handles a window that wraps past midnight, which the usual one is.
     *
     * 22:00 to 08:00 means "after 22:00 OR before 08:00", but a user who sets
     * 09:00 to 17:00 means "after 09:00 AND before 17:00". Treating both the
     * same way silences the app all day for anyone who picks a daytime window.
     */
    private fun isQuietHours(now: LocalTime, start: LocalTime, end: LocalTime): Boolean =
        if (start <= end) {
            now >= start && now < end
        } else {
            now >= start || now < end
        }

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
        val group = groupLabel(ctx.groupName)

        // 100 · Goal reached. Peak end rule: this is both the peak and the
        // ending. Credit stays collective, because naming a top contributor
        // implies a bottom one.
        if (ctx.goalReachedJustNow) {
            val total = format(ctx.groupStepsTotal)
            val target = format(ctx.weeklyGoal)
            out += achievement(seed, ctx, 100, listOf(
                "$group did it." to "$total steps together. Goal met. Show-offs.",
                "Goal met. Everyone act casual." to "$group crossed $target steps.",
            ))
        }

        // 90 · First ever sync. Kills the "is this thing even on" doubt that
        // quietly murders week one retention.
        if (ctx.isFirstEverSync) {
            val todaySteps = format(ctx.userStepsToday)
            val variants = if (ctx.userStepsToday > 0L) {
                listOf(
                    "$todaySteps steps. Found them." to
                        "You're live in $group. The board just changed.",
                    "You are on the board." to
                        "$todaySteps steps landed in $group. Good start.",
                )
            } else {
                listOf(
                    "You are live." to "$group is connected. Your next steps count.",
                )
            }
            out += achievement(seed, ctx, 90, variants)
        }

        // 80 · Someone joined. Closes the loop for whoever sent the invite.
        ctx.newMemberName?.let { name ->
            val who = shortName(name)
            val variants = if (ctx.memberCount == 2) {
                listOf(
                    "Now it’s a group." to "$who joined you. Two people, one weekly goal.",
                )
            } else {
                listOf(
                    "$who actually joined." to
                        "$group is now ${ctx.memberCount} strong. Try to look normal.",
                    "Say hi to $who." to "$group has ${ctx.memberCount} people now.",
                )
            }
            out += groupActivity(seed, ctx, 80, variants)
        }

        // 70 · Close to the goal, running out of week. Goal gradient effect:
        // motivation climbs as the target nears. Collective, so nobody is
        // singled out.
        if (remaining > 0 && progress >= 0.75f && ctx.daysLeftInWeek <= 2) {
            val left = format(remaining)
            val each = format(perMemberShare(remaining, ctx.memberCount))
            val variants = if (ctx.daysLeftInWeek == 1) {
                listOf(
                    "Final day. $left left." to "$each each. Suspiciously doable.",
                    "$left steps left." to "Open $group. Someone needs to call the walk.",
                )
            } else {
                listOf(
                    "$left steps left." to "$each each. Two days. Very doable.",
                    "The goal is close." to "$left steps between $group and done.",
                )
            }
            out += reminder(seed, ctx, 70, variants)
        }

        // 68 · The user just took first place. This is earned swagger, backed
        // by the exact gap and the teammate who can take it back.
        if (ctx.userJustTookLead &&
            ctx.stepsAheadOfMemberBehind > 0L &&
            !ctx.memberJustBehindName.isNullOrBlank()
        ) {
            val who = shortName(ctx.memberJustBehindName)
            val gap = format(ctx.stepsAheadOfMemberBehind)
            out += groupActivity(seed, ctx, 68, listOf(
                "Look who is first." to
                    "You, by $gap steps. Screenshot it before $who walks.",
            ))
        }

        // 65 · Someone passed you. Cheeky about the friend, never about the
        // user. Always paired with something actionable.
        ctx.memberWhoJustPassedYouName?.let { name ->
            val who = shortName(name)
            val gap = format(ctx.stepsToOvertakeMemberAhead)
            val walk = walkInSongs(ctx.stepsToOvertakeMemberAhead)
            out += groupActivity(seed, ctx, 65, listOf(
                "$who passed you." to "By $gap steps. That is annoyingly catchable.",
                "Tiny problem: $who." to "$gap steps ahead. $walk should fix it.",
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
                val walk = walkInSongs(gap)
                out += reminder(seed, ctx, 60, listOf(
                    "Tiny problem: $who." to
                        "$who is $g steps ahead. $walk should fix it.",
                    "$g steps behind $who." to "About $mins minutes. Annoyingly close.",
                ))
            }
        }

        // 55 · Streak at risk. Loss aversion, deliberately defanged. Stated
        // once, with a genuine exit. No countdown, no threat, no guilt.
        if (ctx.streakAtRiskToday && ctx.currentStreakDays >= 3) {
            val days = ctx.currentStreakDays
            out += achievement(seed, ctx, 55, listOf(
                "Your streak is being dramatic." to "It wants one walk before midnight.",
                "$days days. Still alive." to "One walk keeps it safe tonight.",
            ))
        }

        // 50 · Monday recap.
        if (ctx.today == DayOfWeek.MONDAY) {
            val target = format(ctx.weeklyGoal)
            out += recap(seed, ctx, 50, listOf(
                "New week. Same people." to "$target steps waiting in $group.",
                "$group starts again." to "Fresh scoreboard. Go make it interesting.",
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
                "Your steps count today." to "$group is $left short. Even a small walk changes it.",
            ))
        }

        // 35 · A small, recoverable low-step day can take a gentle roast. This
        // is only for someone who already moved on another day this week. Two
        // quiet days or a hard week require warmth, and that data does not yet
        // exist here, so the engine stays silent rather than guessing.
        if (ctx.userStepsToday in 1L..500L &&
            ctx.userStepsThisWeek > ctx.userStepsToday &&
            !ctx.now.isBefore(LocalTime.of(16, 0))
        ) {
            val todaySteps = format(ctx.userStepsToday)
            val name = personalName(ctx.userName)
            val title = name?.let { "$it, be honest." } ?: "Be honest."
            out += reminder(seed, ctx, 35, listOf(
                title to "$todaySteps steps today. Did the phone stay home, or did you?",
                "Your walking shoes asked." to "I said you were busy. Do not make me a liar.",
            ))
        }

        // 30 · Solo group. The old version mocked people for being alone.
        // This roasts the empty room instead, and gives them the fix.
        if (ctx.memberCount <= 1 && ctx.groupAgeMs > TWELVE_HOURS_MS) {
            out += reminder(seed, ctx, 30, listOf(
                "Strong leader. Tiny team." to "Still just you in $group. Send the invite.",
                "Attendance today: you." to "Your invite link is getting lonely.",
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

    private fun personalName(raw: String): String? =
        shortName(raw).takeUnless {
            it.isBlank() ||
                it.equals("there", ignoreCase = true) ||
                it.equals("friend", ignoreCase = true) ||
                it.equals("someone", ignoreCase = true)
        }

    private fun groupLabel(raw: String): String {
        val clean = raw.trim().replace(Regex("\\s+"), " ")
        return if (clean.isBlank()) "Your group" else clean.take(MAX_NAME_IN_TITLE).trimEnd()
    }

    /** Locale independent thousands separators, so tests stay stable. */
    private fun format(value: Long): String =
        value.toString().reversed().chunked(3).joinToString(",").reversed()

    /** Deliberately conservative, roughly 100 steps per minute. */
    private fun minutesToWalk(steps: Long): Int = (steps / 100).coerceAtLeast(1).toInt()

    private fun walkInSongs(steps: Long): String {
        val songs = ((minutesToWalk(steps) + 3) / 4).coerceAtLeast(1)
        return if (songs == 1) "One song" else "$songs songs"
    }

    private fun perMemberShare(remaining: Long, memberCount: Int): Long {
        val members = memberCount.coerceAtLeast(1).toLong()
        return (remaining + members - 1) / members
    }

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
