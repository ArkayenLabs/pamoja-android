import {createHash} from "node:crypto";

export type PushCategory = "ACHIEVEMENT" | "GROUP_ACTIVITY";

export interface PushPayload {
  schemaVersion: "1";
  eventId: string;
  category: PushCategory;
  title: string;
  body: string;
  groupId: string;
}

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
  const isFirstTeammate = input.memberCount === 2;
  return {
    schemaVersion: "1",
    eventId: notificationEventId(
      "member-joined",
      `${input.membershipId}:${String(input.joinedAt)}`,
    ),
    category: "GROUP_ACTIVITY",
    title: isFirstTeammate ? "Now it’s a group." : `${who} actually joined.`,
    body: isFirstTeammate ?
      `${who} joined you. Two people, one weekly goal.` :
      `${group} is now ${input.memberCount} strong. Try to look normal.`,
    groupId: input.groupId,
  };
}

export function buildGoalReachedPush(input: {
  groupId: string;
  weekStart: string;
  groupName: unknown;
  finalSteps: number;
}): PushPayload {
  const group = shortGroupName(input.groupName);
  return {
    schemaVersion: "1",
    eventId: notificationEventId(
      "goal-reached",
      `${input.groupId}:${input.weekStart}`,
    ),
    category: "ACHIEVEMENT",
    title: `${group} did it.`,
    body: `${formatNumber(input.finalSteps)} steps together. Goal met. Show-offs.`,
    groupId: input.groupId,
  };
}

export type SignalKind = "cheer" | "nudge";

export interface ParsedSignal {
  kind: SignalKind;
  fromUserId: string;
  toUserId: string;
  date: string;
}

/** Same threshold the app uses for "walked today" and the day dots. */
export const WALKED_MIN_STEPS = 1_000;

/** How long a cheer or nudge is kept before the TTL policy removes it. */
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
 * nudge's date, in which case the nudge is stale by the time it would land.
 */
export function walkedOn(membership: unknown, date: string): boolean {
  if (typeof membership !== "object" || membership === null) return false;
  const value = membership as Record<string, unknown>;
  return value.todayDate === date &&
    typeof value.todaySteps === "number" &&
    value.todaySteps >= WALKED_MIN_STEPS;
}

/**
 * One event per kind, recipient, group and day. The app drops an event it
 * has already shown, so however many people cheer someone in a day, that
 * person gets one cheer notification per group, and the same for nudges.
 * The rest are on Home.
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
  return {
    schemaVersion: "1",
    eventId: notificationEventId(`signal-${kind}`, `${input.groupId}:${toUserId}:${date}`),
    category: "GROUP_ACTIVITY",
    title: kind === "cheer" ? `${who} cheered your walk` : `${who} nudged you`,
    body: kind === "cheer" ? group : `${group} is walking today. Join in?`,
    groupId: input.groupId,
  };
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

function shortName(value: unknown): string {
  if (typeof value !== "string") return "Someone";
  const first = value.trim().split(/\s+/)[0] ?? "";
  if (!first) return "Someone";
  return first.slice(0, MAX_NAME_IN_TITLE);
}

function shortGroupName(value: unknown): string {
  if (typeof value !== "string") return "Your group";
  const clean = value.trim().replace(/\s+/g, " ");
  return clean ? clean.slice(0, MAX_GROUP_NAME) : "Your group";
}

function formatNumber(value: number): string {
  const safe = Number.isFinite(value) && value >= 0 ? Math.trunc(value) : 0;
  return String(safe).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}
