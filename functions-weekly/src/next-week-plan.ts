const MIN_WEEKLY_TARGET = 10_000;
const MAX_WEEKLY_TARGET = 2_800_000;

/**
 * Returns the scheduled target only for the exact group/week being rolled into.
 * All other data fails closed so a malformed client document can never mutate
 * a group's live goal.
 */
export function scheduledTargetForWeek(
  value: Record<string, unknown> | undefined,
  groupId: string,
  weekStart: string,
): number | null {
  if (!value ||
      ![1, 2].includes(value.schemaVersion as number) ||
      value.groupId !== groupId ||
      value.weekStart !== weekStart ||
      value.status !== "scheduled" ||
      !["repeat", "gentler", "custom"].includes(String(value.choice))) {
    return null;
  }

  if (value.schemaVersion === 2) {
    const hasHistory = typeof value.sourceWeekStart === "string" &&
      /^\d{4}-\d{2}-\d{2}$/.test(value.sourceWeekStart) &&
      value.sourceWeekStart < weekStart && Number.isInteger(value.basisTotalSteps) &&
      (value.basisTotalSteps as number) >= 0 && (value.basisTotalSteps as number) <= 42_000_000;
    const dayOne = value.sourceWeekStart === null && value.basisTotalSteps === null;
    if ((!hasHistory && !dayOne) || !Number.isInteger(value.basisTargetSteps) ||
        (value.basisTargetSteps as number) < MIN_WEEKLY_TARGET ||
        (value.basisTargetSteps as number) > MAX_WEEKLY_TARGET) return null;
  }

  const target = value.targetSteps;
  return Number.isInteger(target) &&
    (target as number) >= MIN_WEEKLY_TARGET &&
    (target as number) <= MAX_WEEKLY_TARGET ?
    target as number :
    null;
}

export interface NextWeekResponseCounts {
  inCount: number;
  preferGentlerCount: number;
  restingCount: number;
}

/** Idempotent aggregation: retries always derive counts from current documents. */
export function countNextWeekResponses(
  values: Array<Record<string, unknown>>,
): NextWeekResponseCounts {
  const counts: NextWeekResponseCounts = {
    inCount: 0,
    preferGentlerCount: 0,
    restingCount: 0,
  };
  values.forEach((value) => {
    switch (value.response) {
    case "in": counts.inCount += 1; break;
    case "prefer_gentler": counts.preferGentlerCount += 1; break;
    case "resting": counts.restingCount += 1; break;
    default: break;
    }
  });
  return counts;
}
