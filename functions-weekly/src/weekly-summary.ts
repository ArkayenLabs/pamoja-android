const ISO_DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;
const GROUP_ID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export const DAYS_PER_WEEK = 7;
export const MAX_DAILY_STEPS = 300_000;
export const MAX_GROUP_MEMBERS = 20;
export const MAX_GROUP_TARGET_STEPS = 2_800_000;
export const MAX_ROLLOVER_GAP_DAYS = 53 * DAYS_PER_WEEK;

export interface GroupWeekContribution {
  userId: string;
  stepCount: number;
}

export interface GroupWeekSummary {
  schemaVersion: 1;
  groupId: string;
  weekStart: string;
  weekEnd: string;
  targetSteps: number;
  totalSteps: number;
  goalHit: boolean;
  memberCount: number;
  activeMemberCount: number;
  contributions: GroupWeekContribution[];
}

interface BuildGroupWeekSummaryInput {
  groupId: string;
  weekStart: string;
  targetSteps: unknown;
  memberUserIds: readonly string[];
  stepCountsByDocumentId: Readonly<Record<string, unknown>>;
}

export function isValidGroupId(value: unknown): value is string {
  return typeof value === "string" && GROUP_ID_PATTERN.test(value);
}

/**
 * A normal group rollover advances by whole weeks. Capping the gap at one year
 * lets a dormant group preserve its last active week without letting a forged
 * cache marker create unbounded historical reads.
 *
 * A change to the group's configured start day can move the boundary by less
 * than seven days; that transition is deliberately not finalized. Mixing two
 * overlapping definitions of a week would create a history row that cannot be
 * explained honestly. The next normal rollover resumes history.
 */
export function isNormalWeekRollover(
  previousWeekStart: unknown,
  nextWeekStart: unknown,
): previousWeekStart is string {
  const previous = parseIsoDate(previousWeekStart);
  const next = parseIsoDate(nextWeekStart);
  if (!previous || !next) return false;

  const gapDays = Math.round(
    (next.getTime() - previous.getTime()) / (24 * 60 * 60 * 1_000),
  );
  return gapDays >= DAYS_PER_WEEK &&
    gapDays <= MAX_ROLLOVER_GAP_DAYS &&
    gapDays % DAYS_PER_WEEK === 0;
}

export function datesInWeek(weekStart: string): string[] {
  const start = parseIsoDate(weekStart);
  if (!start) return [];

  return Array.from({length: DAYS_PER_WEEK}, (_, offset) => {
    const date = new Date(start.getTime());
    date.setUTCDate(date.getUTCDate() + offset);
    return formatIsoDate(date);
  });
}

/**
 * Builds the public group summary from server reads of the seven daily step
 * documents for every current member. Only user IDs and totals survive; names,
 * photos and private profile/health fields are never copied into history.
 */
export function buildGroupWeekSummary(
  input: BuildGroupWeekSummaryInput,
): GroupWeekSummary {
  const dates = datesInWeek(input.weekStart);
  if (dates.length !== DAYS_PER_WEEK) {
    throw new Error("Invalid weekStart");
  }

  const memberUserIds = [...new Set(input.memberUserIds)]
    .filter(isSafeFirebaseUserId)
    .sort()
    .slice(0, MAX_GROUP_MEMBERS);
  const contributions = memberUserIds.map((userId) => ({
    userId,
    stepCount: dates.reduce((total, date) => {
      const documentId = `${userId}_${date}`;
      return total + sanitizeDailySteps(
        input.stepCountsByDocumentId[documentId],
      );
    }, 0),
  }));
  const totalSteps = contributions.reduce(
    (total, contribution) => total + contribution.stepCount,
    0,
  );
  const targetSteps = sanitizeTarget(input.targetSteps);

  return {
    schemaVersion: 1,
    groupId: input.groupId,
    weekStart: input.weekStart,
    weekEnd: dates[dates.length - 1],
    targetSteps,
    totalSteps,
    goalHit: targetSteps > 0 && totalSteps >= targetSteps,
    memberCount: contributions.length,
    activeMemberCount: contributions.filter(
      (contribution) => contribution.stepCount > 0,
    ).length,
    contributions,
  };
}

/**
 * Removes the direct identifier when somebody leaves a group. The completed
 * group total and historical participation counts stay truthful, while former
 * members no longer have a personal step total attached to their Firebase UID.
 */
export function redactUserFromContributions(
  contributions: unknown,
  departedUserId: string,
): GroupWeekContribution[] {
  if (!Array.isArray(contributions)) return [];

  return contributions.flatMap((value) => {
    if (typeof value !== "object" || value === null) return [];
    const contribution = value as {userId?: unknown; stepCount?: unknown};
    if (typeof contribution.userId !== "string" ||
        contribution.userId === departedUserId ||
        !isSafeFirebaseUserId(contribution.userId)) {
      return [];
    }

    return [{
      userId: contribution.userId,
      stepCount: sanitizeWeeklySteps(contribution.stepCount),
    }];
  });
}

function parseIsoDate(value: unknown): Date | null {
  if (typeof value !== "string") return null;
  const match = ISO_DATE_PATTERN.exec(value);
  if (!match) return null;

  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const date = new Date(Date.UTC(year, month - 1, day));
  return formatIsoDate(date) === value ? date : null;
}

function formatIsoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

function isSafeFirebaseUserId(value: string): boolean {
  return value.length > 0 && value.length <= 128 && !value.includes("/");
}

function sanitizeDailySteps(value: unknown): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value)) return 0;
  return Math.min(Math.max(value, 0), MAX_DAILY_STEPS);
}

function sanitizeWeeklySteps(value: unknown): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value)) return 0;
  return Math.min(Math.max(value, 0), MAX_DAILY_STEPS * DAYS_PER_WEEK);
}

function sanitizeTarget(value: unknown): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value)) return 0;
  return Math.min(Math.max(value, 0), MAX_GROUP_TARGET_STEPS);
}
