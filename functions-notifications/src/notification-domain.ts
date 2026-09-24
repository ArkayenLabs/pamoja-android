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
