package com.pamoja.app.domain.model

import kotlin.math.roundToInt

/**
 * The one place that decides what a group's weekly goal is.
 *
 * **The goal is expressed per person per day, not as a group total.** A total
 * cannot be judged by a human: to know whether 150,000 is easy or hard you have
 * to divide it by the number of members and then by seven, every time you see
 * it. A goal nobody can evaluate cannot motivate anyone.
 *
 * A total is also wrong in a way that gets worse as the product succeeds. It
 * does not scale with the group, so with the old fixed 70,000 default:
 *
 * | Members | Steps per person per day |
 * |---------|--------------------------|
 * | 2       | 5,000                    |
 * | 8       | 1,250                    |
 * | 20      | 500                      |
 *
 * At the time, the member cap was 8 on free and 20 on premium, so **paying made
 * the goal 2.5x easier**. The incentive was backwards, and it got worse the more
 * premium sold. That particular trap is gone twice over: the goal now scales
 * with the group, and group size is no longer sold at all (see [PlanLimits]).
 *
 * So [dailyPerPerson] is the admin's intent and the only figure any screen
 * asks for. [weeklyTotalFor] derives the absolute, which is still stored on the
 * group and is still what the leaderboard, the celebration and `firestore.rules`
 * compare against. Deriving *and* storing it is deliberate: it keeps all of that
 * logic and the rules' simple numeric bound unchanged.
 *
 * This file exists because the ceiling for the same field used to be written in
 * four places that disagreed: 150,000 in the create screen's presets, no upper
 * bound at all in `CreateGroupUseCase`, 500,000 in the edit screen's slider, and
 * 1,000,000 in the edit use case and the rules. Anything that needs a bound must
 * read it from here.
 */
object StepGoal {

    /**
     * Below this a goal is not worth setting. Deliberately low rather than
     * aspirational, because a group recovering from a bad week should be able
     * to set something they will actually hit.
     */
    const val MIN_DAILY_PER_PERSON = 2_000

    /**
     * Roughly the top of what a walking group sustains. Above this the number
     * stops being a goal and starts being a reason to quit.
     */
    const val MAX_DAILY_PER_PERSON = 20_000

    /**
     * **8,000, not 10,000.** 10,000 is a marketing figure that came from the
     * brand name of a 1960s Japanese pedometer, not from a health finding, and
     * the measured benefit curve flattens closer to 7,500. 8,000 is also more
     * achievable, and a goal that is never met demotivates faster than one that
     * is slightly too easy.
     */
    const val DEFAULT_DAILY_PER_PERSON = 8_000

    /** What the create and edit screens offer. Free on every tier, always. */
    val PRESETS_DAILY_PER_PERSON = listOf(4_000, 6_000, 8_000, 10_000, 12_000)

    /** The largest group the product allows. Mirrors the member cap. */
    const val MAX_GROUP_MEMBERS = 20

    private const val DAYS = 7

    /**
     * The absolute ceiling any stored `weeklyTarget` may reach, and therefore
     * the number `firestore.rules` must allow.
     *
     * Derived rather than chosen, so it cannot drift from the inputs: the
     * largest group the product permits, every member at the highest
     * per-person figure, for seven days. Worth noting that the **default**
     * already exceeded the old 1,000,000 rules ceiling at the premium cap
     * (20 x 8,000 x 7 = 1,120,000), so that ceiling was not merely
     * conservative, it made the recommended default unsavable.
     */
    const val MAX_WEEKLY_TOTAL = MAX_DAILY_PER_PERSON * MAX_GROUP_MEMBERS * DAYS

    /**
     * The weekly group total for [dailyPerPerson] across [members].
     *
     * [members] is coerced to at least one so a group mid-creation, or a
     * document with a corrupt count, can never produce a target of zero, which
     * would read as "already achieved" everywhere it is compared.
     */
    fun weeklyTotalFor(dailyPerPerson: Int, members: Int): Int =
        dailyPerPerson.coerceIn(MIN_DAILY_PER_PERSON, MAX_DAILY_PER_PERSON) *
            members.coerceAtLeast(1) *
            DAYS

    /**
     * The per-person figure a stored group total implies.
     *
     * Only for **display**, and only for groups created before the goal was
     * expressed per person. Those keep whatever absolute target they were given
     * rather than having it silently rewritten mid-week, so the screens still
     * need something human to show next to it.
     */
    fun dailyPerPersonFor(weeklyTotal: Int, members: Int): Int =
        (weeklyTotal.toDouble() / (members.coerceAtLeast(1) * DAYS)).roundToInt()

    /** Whether a stored total is one this model could have produced. */
    fun isValidWeeklyTotal(weeklyTotal: Int): Boolean =
        weeklyTotal > 0 && weeklyTotal <= MAX_WEEKLY_TOTAL

}
