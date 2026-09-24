import type {GroupWeekSummary} from "./weekly-summary.js";

export const CIRCLE_PREVIEW_DURATION_MS = 14 * 24 * 60 * 60 * 1_000;
export const CIRCLE_PREVIEW_FEATURE_SET = ["circle_v1"] as const;

export interface CirclePreviewSeed {
  schemaVersion: 1;
  groupId: string;
  featureSet: readonly ["circle_v1"];
  eligibleWeekStart: string;
  startedAtMillis: number;
  validUntilMillis: number;
}

/**
 * Starts the product preview only after the group has actually moved together.
 *
 * Two active members is the smallest result that demonstrates group value.
 * Goal completion is deliberately irrelevant: a group that missed its target
 * is exactly the group that may value a review and kind next-week recovery.
 */
export function buildCirclePreviewSeed(
  summary: GroupWeekSummary,
  startedAtMillis: number,
): CirclePreviewSeed | null {
  if (!Number.isSafeInteger(startedAtMillis) || startedAtMillis <= 0) {
    return null;
  }
  if (summary.memberCount < 2 || summary.activeMemberCount < 2) {
    return null;
  }
  if (summary.totalSteps <= 0) return null;

  const validUntilMillis = startedAtMillis + CIRCLE_PREVIEW_DURATION_MS;
  if (!Number.isSafeInteger(validUntilMillis)) return null;

  return {
    schemaVersion: 1,
    groupId: summary.groupId,
    featureSet: CIRCLE_PREVIEW_FEATURE_SET,
    eligibleWeekStart: summary.weekStart,
    startedAtMillis,
    validUntilMillis,
  };
}
