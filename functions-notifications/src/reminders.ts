import {
  LocText,
  PushPayload,
  WALKED_MIN_STEPS,
  formatNumber,
  isValidTimeZone,
  payload,
  shortGroupName,
} from "./notification-domain.js";

/**
 * Server-scheduled reminders and the weekly recap (NOTIFICATIONS_PLAN.md §2).
 *
 * These used to be decided on the phone by the background step sync, which
 * phones that hold back background work run late or never. The server reads
 * only what every member already shares on their membership and the group
 * document, and only as of each phone's last sync, so no line here may claim
 * someone has not walked. Each says what the group did, or what is left.
 *
 * Restraint is the feature: at most one of these per installation per local
 * day, only at two local hours, never in quiet hours, and only for app
 * versions that no longer send their own.
 */

/** Local hour of the morning recap, on the first day of the group's week. */
export const RECAP_HOUR = 10;
/** Local hour of the one daily reminder: after work, before dinner. */
export const EVENING_HOUR = 18;
/** A below-average member hears "every walk counts" at most this often. */
const WALK_COUNTS_GAP_DAYS = 6;
/** The empty-group invite reminder, on these days after creation only. */
const SOLO_REMINDER_DAYS = new Set([1, 3, 7]);
/** Streak lengths told as a milestone on the recap morning, instead of the recap. */
export const MILESTONE_WEEKS = new Set([4, 10]);
const DAY_MS = 24 * 60 * 60 * 1_000;
/** Scheduled pushes in a row, unopened, before one last line and silence. */
export const QUIET_AFTER = 8;

const DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];

export interface ReminderGroup {
  groupId: string;
  name: unknown;
  weeklyTarget: number;
  /** Group total for [weekStart], as last published by a member's sync. */
  weeklySteps: number;
  weekStart: string;
  weekStartDay: unknown;
  planningTimeZone: unknown;
  memberCount: number;
  createdAtMs: number;
  streakWeeks: number;
  streakThroughWeek: string;
  lastShieldedWeek: string;
  /** Total of the finished previous week, when its summary exists. */
  previousWeekTotal: number | null;
  /** Current members who walked all seven days of the finished previous week. */
  previousWeekEveryDay: ReadonlyArray<{userId: string; name: string}>;
}

export interface ReminderMembership {
  weeklySteps: number;
  weekStart: string;
  todaySteps: number;
  todayDate: string;
}

export interface ReminderCandidate {
  group: ReminderGroup;
  membership: ReminderMembership;
}

export type ReminderKind =
  "shield" | "milestone" | "recap" | "streak" | "last-day" | "two-days" | "walk-counts" | "solo" |
  "going-quiet";

export interface PlannedReminder {
  kind: ReminderKind;
  push: PushPayload;
}

/** ISO date arithmetic in UTC, where a date has no time zone to trip on. */
export function addDays(date: string, days: number): string {
  return new Date(Date.parse(`${date}T00:00:00Z`) + days * DAY_MS).toISOString().slice(0, 10);
}

/** 0 = Monday, like java.time.DayOfWeek.value - 1. */
function weekdayIndex(date: string): number {
  return (new Date(`${date}T00:00:00Z`).getUTCDay() + 6) % 7;
}

/** WeekWindow.parseStartDay: blank or unknown is Monday, what old groups ran on. */
function startDayIndex(stored: unknown): number {
  const index = DAYS.indexOf(String(stored));
  return index === -1 ? 0 : index;
}

/** WeekWindow.startOf. */
export function weekStartOf(startDay: unknown, today: string): string {
  return addDays(today, -((weekdayIndex(today) - startDayIndex(startDay) + 7) % 7));
}

/** WeekWindow.daysLeftIn: seven on the first day, one on the last. */
export function daysLeftIn(startDay: unknown, today: string): number {
  return 7 - ((weekdayIndex(today) - startDayIndex(startDay) + 7) % 7);
}

/** WeekWindow.todayFor: the group's calendar when it has one, else this phone's. */
export function groupToday(group: ReminderGroup, phoneToday: string, localDateIn: (zone: string) => string): string {
  return isValidTimeZone(group.planningTimeZone) ? localDateIn(group.planningTimeZone) : phoneToday;
}

/**
 * The one reminder worth sending to this person at this local hour, or null.
 * Highest value first; silence is a frequent and correct answer.
 */
export function planReminder(input: {
  userId: string;
  localHour: number;
  phoneToday: string;
  nowMs: number;
  localDateIn: (zone: string) => string;
  candidates: readonly ReminderCandidate[];
  /** Last local date each kind was sent to this installation. */
  sent: Readonly<Record<string, string>>;
}): PlannedReminder | null {
  const planned = input.candidates.flatMap((candidate) => {
    const today = groupToday(candidate.group, input.phoneToday, input.localDateIn);
    const option = input.localHour === RECAP_HOUR ?
      morning(candidate, today, input.userId) :
      input.localHour === EVENING_HOUR ?
        evening(candidate, today, input) :
        null;
    return option ? [option] : [];
  });
  planned.sort((a, b) => b.priority - a.priority);
  return planned[0]?.reminder ?? null;
}

interface Ranked {
  priority: number;
  reminder: PlannedReminder;
}

function morning(candidate: ReminderCandidate, today: string, userId: string): Ranked | null {
  const {group} = candidate;
  if (daysLeftIn(group.weekStartDay, today) !== 7) return null;
  const previousWeek = addDays(today, -7);
  const name = shortGroupName(group.name);
  const identity = `${group.groupId}:${userId}:${today}`;

  if (group.lastShieldedWeek === previousWeek) {
    return rank(90, "shield", payload({
      kind: "reminder-shield",
      identity,
      category: "ACHIEVEMENT",
      groupId: group.groupId,
      title: {key: "push_shield_title", args: []},
      body: {key: "push_shield_body", args: [name]},
    }));
  }

  // The finalizer has counted last week by now (the shield line above needs
  // the same), so a streak through last week is the one to mark.
  if (group.streakThroughWeek === previousWeek && MILESTONE_WEEKS.has(group.streakWeeks)) {
    const weeks = String(group.streakWeeks);
    return rank(80, "milestone", payload({
      kind: "reminder-milestone",
      identity,
      category: "ACHIEVEMENT",
      groupId: group.groupId,
      title: {key: "push_milestone_title", args: [weeks]},
      body: {key: "push_milestone_body", args: [name, weeks]},
    }));
  }

  const lastTotal = group.previousWeekTotal ??
    (group.weekStart === previousWeek ? group.weeklySteps : null);
  const target = formatNumber(group.weeklyTarget);
  return rank(50, "recap", payload({
    kind: "reminder-recap",
    identity,
    category: "RECAP",
    groupId: group.groupId,
    title: {key: "push_new_week_title", args: [name]},
    shortenTitleArg: 0,
    body: recapBody(group, userId, previousWeek, lastTotal, target),
  }));
}

/**
 * Celebrates a person, never a rank (NOTIFICATIONS_PLAN.md §4.5): someone who
 * walked every day last week, by name, or "you" for each of them. With
 * several, the one named turns over week by week rather than always being
 * the same person.
 */
function recapBody(
  group: ReminderGroup,
  userId: string,
  previousWeek: string,
  lastTotal: number | null,
  target: string,
): LocText {
  const everyDay = group.previousWeekEveryDay;
  if (everyDay.some((person) => person.userId === userId)) {
    return {key: "push_recap_you_body", args: [target]};
  }
  const others = [...everyDay].sort((a, b) => a.userId.localeCompare(b.userId));
  if (others.length > 0) {
    const turn = [...previousWeek].reduce((sum, char) => sum + char.charCodeAt(0), 0) % others.length;
    return {key: "push_recap_named_body", args: [others[turn].name, target]};
  }
  // "Last week: 0 steps" reads as a telling-off; say only the new goal.
  return lastTotal && lastTotal > 0 ?
    {key: "push_new_week_body", args: [formatNumber(lastTotal), target]} :
    {key: "push_new_week_first_body", args: [target]};
}

function evening(
  candidate: ReminderCandidate,
  today: string,
  input: {userId: string; nowMs: number; sent: Readonly<Record<string, string>>},
): Ranked | null {
  const {group, membership} = candidate;
  const name = shortGroupName(group.name);
  const identity = `${group.groupId}:${input.userId}:${today}`;
  const thisWeek = weekStartOf(group.weekStartDay, today);
  const daysLeft = daysLeftIn(group.weekStartDay, today);
  const members = Math.max(1, group.memberCount);
  const total = group.weekStart === thisWeek ? group.weeklySteps : 0;
  const remaining = Math.max(0, group.weeklyTarget - total);
  const progress = group.weeklyTarget > 0 ? total / group.weeklyTarget : 0;

  // Close to the goal, running out of week: collective, nobody singled out.
  if (remaining > 0 && progress >= 0.75 && daysLeft <= 2) {
    const left = formatNumber(remaining);
    const each = formatNumber(Math.ceil(remaining / members));
    // The streak standing this week, as Group.streakFor reads it.
    const streak = group.streakThroughWeek === addDays(thisWeek, -7) ? group.streakWeeks : 0;
    if (streak >= 2) {
      return rank(70, "streak", payload({
        kind: "reminder-streak",
        identity,
        category: "REMINDER",
        groupId: group.groupId,
        title: {key: "push_streak_title", args: [String(streak)]},
        body: {key: "push_streak_body", args: [left, name]},
      }));
    }
    return daysLeft === 1 ?
      rank(70, "last-day", payload({
        kind: "reminder-last-day",
        identity,
        category: "REMINDER",
        groupId: group.groupId,
        title: {key: "push_last_day_title", args: [left]},
        body: {key: "push_last_day_body", args: [each]},
      })) :
      rank(70, "two-days", payload({
        kind: "reminder-two-days",
        identity,
        category: "REMINDER",
        groupId: group.groupId,
        title: {key: "push_two_days_title", args: [left]},
        body: {key: "push_two_days_body", args: [each]},
      }));
  }

  // Reframes a quieter member's steps as needed, never as behind. Rank is
  // never mentioned, no one else is named, and nobody who already walked
  // today hears it.
  const mine = membership.weekStart === thisWeek ? membership.weeklySteps : 0;
  const walkedToday = membership.todayDate === today && membership.todaySteps >= WALKED_MIN_STEPS;
  const lastSent = input.sent["walk-counts"];
  const rested = !lastSent || addDays(lastSent, WALK_COUNTS_GAP_DAYS) <= today;
  if (remaining > 0 && members > 1 && mine < total / members && daysLeft <= 4 &&
      !walkedToday && rested) {
    return rank(40, "walk-counts", payload({
      kind: "reminder-walk-counts",
      identity,
      category: "REMINDER",
      groupId: group.groupId,
      title: {key: "push_walk_counts_title", args: []},
      body: {key: "push_walk_counts_body", args: [name, formatNumber(remaining)]},
    }));
  }

  // An empty group, with the fix, on a few days only: a daily "invite
  // someone" is a nag.
  const ageDays = Math.floor((input.nowMs - group.createdAtMs) / DAY_MS);
  if (members <= 1 && group.createdAtMs > 0 && SOLO_REMINDER_DAYS.has(ageDays)) {
    return rank(30, "solo", payload({
      kind: "reminder-solo",
      identity,
      category: "REMINDER",
      groupId: group.groupId,
      title: {key: "push_solo_title", args: [name]},
      shortenTitleArg: 0,
      body: {key: "push_solo_body", args: []},
    }));
  }

  return null;
}

/**
 * Honest backoff (NOTIFICATIONS_PLAN.md §4.4). [unopened] counts scheduled
 * pushes sent since the app was last opened. Below [QUIET_AFTER] the plan
 * goes out; at it, once, a line saying we will stop; above it, nothing until
 * the app is opened again. Only for builds that report opens. Pushes from
 * people (cheers, reminders, joins, goals) never count and never stop.
 */
export function applyBackoff(
  plan: PlannedReminder | null,
  unopened: number,
  reportsOpens: boolean,
  input: {userId: string; today: string},
): PlannedReminder | null {
  if (!plan || !reportsOpens || unopened < QUIET_AFTER) return plan;
  if (unopened > QUIET_AFTER) return null;
  return {
    kind: "going-quiet",
    push: payload({
      kind: "reminder-going-quiet",
      identity: `${input.userId}:${input.today}`,
      category: "REMINDER",
      groupId: plan.push.groupId,
      title: {key: "push_quiet_title", args: []},
      body: {key: "push_quiet_body", args: []},
    }),
  };
}

function rank(priority: number, kind: ReminderKind, push: PushPayload): Ranked {
  return {priority, reminder: {kind, push}};
}
