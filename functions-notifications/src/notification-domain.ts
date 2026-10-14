import {createHash} from "node:crypto";

export type PushCategory = "ACHIEVEMENT" | "GROUP_ACTIVITY" | "REMINDER" | "RECAP";

/** A string in the app's values/strings_notifications.xml, and its arguments. */
export interface LocText {
  key: CopyKey;
  args: string[];
}

export interface PushPayload {
  schemaVersion: "1";
  eventId: string;
  category: PushCategory;
  /** English, for app versions without the strings, and the app's fallback. */
  title: string;
  body: string;
  groupId: string;
  titleLoc: LocText;
  bodyLoc: LocText;
}

/**
 * The English of every push, exactly as values/strings_notifications.xml has
 * it (notification-copy.test.ts holds the two together). The phone shows its
 * own translation of the key; this is what a phone without the key shows.
 */
export const COPY = {
  push_joined_title: "%1$s joined %2$s",
  push_joined_second_body: "There are two of you now. Walks are better with company.",
  push_joined_body: "%1$s of you now. Say hi on your next walk.",
  push_goal_title: "%1$s made it",
  push_goal_body: "%1$s steps this week, together. Take a bow.",
  push_cheer_title: "High five from %1$s",
  // No placeholder any more, but the group is still sent: builds before 1.1.5
  // carry "%1$s. Tap to cheer back." and would fail to format without it.
  push_cheer_body: "For your walk today. Send one back?",
  push_remind_title: "%1$s: walk today?",
  push_remind_body: "Any walk today counts for %1$s.",
  push_last_day_title: "Last day: %1$s steps to go",
  push_last_day_body: "About %1$s each. An evening walk would do it.",
  push_two_days_title: "%1$s steps to go",
  push_two_days_body: "Two days left, about %1$s each.",
  push_streak_title: "%1$s weeks in a row",
  push_streak_body: "%1$s steps keeps %2$s going this week.",
  push_walk_counts_title: "Every walk counts today",
  push_walk_counts_body: "%1$s is %2$s steps from the goal.",
  push_new_week_title: "New week for %1$s",
  push_new_week_body: "Last week: %1$s steps. This week's goal: %2$s.",
  push_new_week_first_body: "This week's goal: %1$s steps.",
  push_shield_title: "A shield kept your streak",
  push_shield_body: "%1$s missed the goal, but the streak lives.",
  push_solo_title: "%1$s needs its people",
  push_solo_body: "Send your invite. A shared goal is easier to keep.",
  push_first_goal_title: "First goal, done",
  push_first_goal_body: "%1$s made it: %2$s steps together.",
  push_milestone_title: "%1$s weeks in a row",
  push_milestone_body: "%1$s has met its goal %2$s weeks running. That's a habit now.",
  push_recap_named_body: "%1$s walked every day last week. This week's goal: %2$s.",
  push_recap_you_body: "You walked every day last week. This week's goal: %1$s.",
  push_quiet_title: "We'll go quiet for now",
  push_quiet_body: "Your group's still here when you want it.",
} as const;

export type CopyKey = keyof typeof COPY;

/** The app's channel for each category (NotificationCategory.channelId). */
export const CHANNEL_IDS: Record<PushCategory, string> = {
  ACHIEVEMENT: "pamoja_achievements",
  GROUP_ACTIVITY: "pamoja_group_activity",
  REMINDER: "pamoja_reminders",
  RECAP: "pamoja_recap",
};

/** The app's own limits (RemoteNotificationParser); a longer title is dropped. */
export const MAX_TITLE = 40;
const MAX_BODY = 120;
const MAX_ARG = 60;

/**
 * The first app delivery version that draws notification messages from its
 * own strings and logs them in Updates. Older versions registered nothing
 * here and keep getting data-only pushes, exactly as before.
 */
export const TRAY_DELIVERY_VERSION = 2;

/**
 * The first app delivery version that reports when the app is opened, so
 * scheduled reminders may stop when nobody opens them. Never applied to an
 * older build: it cannot reset the count and would go quiet for good.
 */
export const OPENS_DELIVERY_VERSION = 3;

const INSTALLATION_ID_PATTERN = /^[A-Za-z0-9_-]{1,200}$/;
const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const GROUP_ID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const MAX_NAME_IN_TITLE = 18;
const MAX_GROUP_NAME = 18;

export function isValidInstallationId(value: unknown): value is string {
  return typeof value === "string" && INSTALLATION_ID_PATTERN.test(value);
}

export function isValidAppVersion(value: unknown): value is string {
  return typeof value === "string" && value.length > 0 && value.length <= 32;
}

export function isValidIsoDate(value: unknown): value is string {
  return typeof value === "string" && ISO_DATE_PATTERN.test(value);
}

export function isValidGroupId(value: unknown): value is string {
  return typeof value === "string" && GROUP_ID_PATTERN.test(value);
}

export function isValidTimeZone(value: unknown): value is string {
  if (typeof value !== "string" || value.length === 0 || value.length > 64) return false;
  try {
    new Intl.DateTimeFormat("en-US", {timeZone: value});
    return true;
  } catch {
    return false;
  }
}

export function isMinuteOfDay(value: unknown): value is number {
  return Number.isInteger(value) && (value as number) >= 0 && (value as number) < 24 * 60;
}

/** Fills %1$s-style placeholders, the same way Android's getString does. */
export function fill(key: CopyKey, args: readonly string[]): string {
  return COPY[key].replace(/%(\d)\$s/g, (_, index: string) => args[Number(index) - 1] ?? "");
}

/**
 * A payload from its two strings. The group name in a title is cut further
 * when the whole title would pass [MAX_TITLE], since the app drops a longer
 * one rather than show it.
 */
export function payload(input: {
  kind: string;
  identity: string;
  category: PushCategory;
  groupId: string;
  title: LocText;
  body: LocText;
  /** Index into title.args of a group name that may be shortened to fit. */
  shortenTitleArg?: number;
}): PushPayload {
  const titleArgs = [...input.title.args];
  const index = input.shortenTitleArg;
  if (index !== undefined) {
    while (fill(input.title.key, titleArgs).length > MAX_TITLE && titleArgs[index].length > 1) {
      titleArgs[index] = titleArgs[index].slice(0, -1);
    }
    titleArgs[index] = titleArgs[index].trimEnd();
  }
  const titleLoc = {key: input.title.key, args: titleArgs.map(arg)};
  const bodyLoc = {key: input.body.key, args: input.body.args.map(arg)};
  return {
    schemaVersion: "1",
    eventId: notificationEventId(input.kind, input.identity),
    category: input.category,
    title: fill(titleLoc.key, titleLoc.args).slice(0, MAX_TITLE),
    body: fill(bodyLoc.key, bodyLoc.args).slice(0, MAX_BODY),
    groupId: input.groupId,
    titleLoc,
    bodyLoc,
  };
}

export function buildMemberJoinedPush(input: {
  membershipId: string;
  joinedAt: unknown;
  displayName: unknown;
  groupId: string;
  groupName: unknown;
  memberCount: number;
}): PushPayload {
  const who = shortName(input.displayName);
  const group = shortGroupName(input.groupName);
  return payload({
    kind: "member-joined",
    identity: `${input.membershipId}:${String(input.joinedAt)}`,
    category: "GROUP_ACTIVITY",
    groupId: input.groupId,
    title: {key: "push_joined_title", args: [who, group]},
    shortenTitleArg: 1,
    body: input.memberCount === 2 ?
      {key: "push_joined_second_body", args: []} :
      {key: "push_joined_body", args: [String(input.memberCount)]},
  });
}

export function buildGoalReachedPush(input: {
  groupId: string;
  weekStart: string;
  groupName: unknown;
  finalSteps: number;
  /** The group has never met its goal before: said once, as its own moment. */
  first?: boolean;
}): PushPayload {
  const name = shortGroupName(input.groupName);
  return payload({
    kind: "goal-reached",
    identity: `${input.groupId}:${input.weekStart}`,
    category: "ACHIEVEMENT",
    groupId: input.groupId,
    title: input.first ?
      {key: "push_first_goal_title", args: []} :
      {key: "push_goal_title", args: [name]},
    body: input.first ?
      {key: "push_first_goal_body", args: [name, formatNumber(input.finalSteps)]} :
      {key: "push_goal_body", args: [formatNumber(input.finalSteps)]},
  });
}

/**
 * Whether a goal crossing may push, given the group's server-owned claim.
 * Once per group per week: the step cache it fires on is written by members,
 * and before 2026-10-13 a member could write it under the goal and back over
 * as often as they liked, buzzing every phone in the group each time (the
 * tray tag replaces the notification but alerts again). A redelivery of the
 * event that made the claim keeps it, so a failed send is still retried.
 */
export function mayPushGoal(
  claim: {week?: unknown; eventId?: unknown},
  weekStart: string,
  eventId: string,
): boolean {
  return claim.week !== weekStart || claim.eventId === eventId;
}

/** "nudge" stays the wire value; people see "Remind" (NOTIFICATIONS_PLAN.md §3). */
export type SignalKind = "cheer" | "nudge";

export interface ParsedSignal {
  kind: SignalKind;
  fromUserId: string;
  toUserId: string;
  date: string;
}

/** Same threshold the app uses for "walked today" and the day dots. */
export const WALKED_MIN_STEPS = 1_000;

/** How long a cheer or reminder is kept before the TTL policy removes it. */
export const SIGNAL_RETENTION_MS = 14 * 24 * 60 * 60 * 1_000;

/** Reads a signal document defensively; the rules already shaped it. */
export function parseSignal(data: unknown): ParsedSignal | null {
  if (typeof data !== "object" || data === null) return null;
  const value = data as Record<string, unknown>;
  const kind = value.kind;
  if (kind !== "cheer" && kind !== "nudge") return null;
  if (!isUserId(value.fromUserId) || !isUserId(value.toUserId)) return null;
  if (!isValidIsoDate(value.date)) return null;
  return {kind, fromUserId: value.fromUserId, toUserId: value.toUserId, date: value.date};
}

/**
 * Whether the recipient's own published figures already show a walk on the
 * reminder's date, in which case it is stale by the time it would land.
 */
export function walkedOn(membership: unknown, date: string): boolean {
  if (typeof membership !== "object" || membership === null) return false;
  const value = membership as Record<string, unknown>;
  return value.todayDate === date &&
    typeof value.todaySteps === "number" &&
    value.todaySteps >= WALKED_MIN_STEPS;
}

/**
 * One event per kind, recipient, group and day. Both the app and the tray
 * (by tag) drop an event already shown, so however many people cheer someone
 * in a day, that person gets one cheer notification per group, and the same
 * for reminders. The rest are on Home.
 */
export function buildSignalPush(input: {
  signal: ParsedSignal;
  groupId: string;
  senderName: unknown;
  groupName: unknown;
}): PushPayload {
  const who = shortName(input.senderName);
  const group = shortGroupName(input.groupName);
  const {kind, toUserId, date} = input.signal;
  return payload({
    kind: `signal-${kind}`,
    identity: `${input.groupId}:${toUserId}:${date}`,
    category: "GROUP_ACTIVITY",
    groupId: input.groupId,
    title: kind === "cheer" ?
      {key: "push_cheer_title", args: [who]} :
      {key: "push_remind_title", args: [who]},
    body: kind === "cheer" ?
      {key: "push_cheer_body", args: [group]} :
      {key: "push_remind_body", args: [group]},
  });
}

/** What the server knows about one installation, from pushRegistrations. */
export interface Registration {
  installationId: string;
  userId: string;
  delivery: number;
  timeZone: string | null;
  quietStartMinute: number | null;
  quietEndMinute: number | null;
}

export function parseRegistration(data: Record<string, unknown>): Registration | null {
  const installationId = data.installationId;
  const userId = data.userId;
  if (!isValidInstallationId(installationId) || !isUserId(userId)) return null;
  return {
    installationId,
    userId,
    delivery: Number.isInteger(data.delivery) ? data.delivery as number : 1,
    timeZone: isValidTimeZone(data.timeZone) ? data.timeZone : null,
    quietStartMinute: isMinuteOfDay(data.quietStartMinute) ? data.quietStartMinute : null,
    quietEndMinute: isMinuteOfDay(data.quietEndMinute) ? data.quietEndMinute : null,
  };
}

/** Wall-clock facts for one instant in one time zone. */
export interface LocalClock {
  date: string;
  minuteOfDay: number;
}

export function localClock(nowMs: number, timeZone: string): LocalClock {
  const parts = Object.fromEntries(new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date(nowMs)).map((part) => [part.type, part.value]));
  return {
    date: `${parts.year}-${parts.month}-${parts.day}`,
    minuteOfDay: Number(parts.hour) * 60 + Number(parts.minute),
  };
}

/** Same rule as the app's isQuietHours: a window may wrap past midnight. */
export function isQuietAt(minuteOfDay: number, start: number, end: number): boolean {
  return start <= end ?
    minuteOfDay >= start && minuteOfDay < end :
    minuteOfDay >= start || minuteOfDay < end;
}

export function inQuietHours(registration: Registration, nowMs: number): boolean {
  const {timeZone, quietStartMinute, quietEndMinute} = registration;
  if (timeZone === null || quietStartMinute === null || quietEndMinute === null) return false;
  return isQuietAt(localClock(nowMs, timeZone).minuteOfDay, quietStartMinute, quietEndMinute);
}

/**
 * The FCM message for one installation.
 *
 * Builds that registered for it get a notification message (channel, tag and
 * loc keys), which FCM's SDK draws in the phone's language without running
 * the app's own code. The data fields stay in every message, so older builds,
 * and this one in the foreground, still parse and draw it themselves.
 *
 * In quiet hours a notification message would be drawn regardless of the
 * app's own setting, so it is sent data-only instead and the app decides, as
 * it always has: a goal reached still shows, everything else waits for Home.
 */
export function buildFcmMessage(
  registration: Registration,
  push: PushPayload,
  nowMs: number,
  options: {packageName: string; ttlMs: number},
) {
  const data: Record<string, string> = {
    schemaVersion: push.schemaVersion,
    eventId: push.eventId,
    category: push.category,
    title: push.title,
    body: push.body,
    groupId: push.groupId,
    recipientUid: registration.userId,
    ...locFields("title", push.titleLoc),
    ...locFields("body", push.bodyLoc),
  };
  const tray = registration.delivery >= TRAY_DELIVERY_VERSION &&
    (push.category === "ACHIEVEMENT" || !inQuietHours(registration, nowMs));
  return {
    fid: registration.installationId,
    data,
    android: {
      priority: "high" as const,
      ttl: options.ttlMs,
      restrictedPackageName: options.packageName,
      ...(tray ? {
        notification: {
          channelId: CHANNEL_IDS[push.category],
          // RemoteNotificationParser.parseTrayTag reads this back for Updates.
          tag: `p1:${push.groupId}:${push.eventId}`,
          title: push.title,
          body: push.body,
          titleLocKey: push.titleLoc.key,
          titleLocArgs: push.titleLoc.args,
          bodyLocKey: push.bodyLoc.key,
          bodyLocArgs: push.bodyLoc.args,
          visibility: "private" as const,
        },
      } : {}),
    },
  };
}

function locFields(field: "title" | "body", text: LocText): Record<string, string> {
  const fields: Record<string, string> = {[`${field}LocKey`]: text.key};
  text.args.forEach((value, index) => {
    fields[`${field}LocArg${index + 1}`] = value;
  });
  return fields;
}

function arg(value: string): string {
  return value.slice(0, MAX_ARG);
}

function isUserId(value: unknown): value is string {
  return typeof value === "string" && value.length > 0 && value.length <= 128 && !value.includes("/");
}

export function notificationEventId(kind: string, identity: string): string {
  const digest = createHash("sha256")
    .update(`${kind}:${identity}`)
    .digest("hex")
    .slice(0, 32);
  return `${kind}-${digest}`;
}

export function shortName(value: unknown): string {
  if (typeof value !== "string") return "Someone";
  const first = value.trim().split(/\s+/)[0] ?? "";
  if (!first) return "Someone";
  return first.slice(0, MAX_NAME_IN_TITLE);
}

export function shortGroupName(value: unknown): string {
  if (typeof value !== "string") return "Your group";
  const clean = value.trim().replace(/\s+/g, " ");
  return clean ? clean.slice(0, MAX_GROUP_NAME).trimEnd() : "Your group";
}

export function formatNumber(value: number): string {
  const safe = Number.isFinite(value) && value >= 0 ? Math.trunc(value) : 0;
  return String(safe).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}
