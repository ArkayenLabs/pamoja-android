import {datesInWeek} from "./weekly-summary.js";

/** Weeks in a row a group met its goal, ending with `streakThroughWeek`. */
export interface GroupStreak {
  streakWeeks: number;
  streakThroughWeek: string;
}

/**
 * Streak shields, Premium's lead feature. Present on a group from the first
 * week it is finalized with Premium (or its preview), absent before.
 */
export interface ShieldState {
  streakShields: number;
  /** Goal weeks met while Premium since the last shield was earned. */
  shieldProgress: number;
  lastShieldedWeek?: string;
}

export interface WeekOutcome extends GroupStreak {
  /** The week missed its goal and a shield kept the streak alive. */
  shielded: boolean;
  shields?: ShieldState;
}

export interface FinalizedWeek {
  weekStart: string;
  goalHit: boolean;
  shielded?: boolean;
}

/** A group's first Premium week starts it with one shield, once ever. */
export const STARTING_SHIELDS = 1;
/** A group never holds more, so the streak keeps meaning something. */
export const MAX_SHIELDS = 2;
/** Goal weeks met while Premium that earn the next shield. */
export const GOAL_WEEKS_PER_SHIELD = 4;

/** Ten years. Only bounds a corrupt stored value; no real group gets near it. */
const MAX_STREAK_WEEKS = 520;

/**
 * The group's streak, and its shields, once `weekStart` has been finalized.
 *
 * Stored on the group document, server-owned, so every client reads one
 * number instead of needing the week history (which is Premium). A missed
 * week resets the streak to zero rather than deleting it, so the marker still
 * moves, unless the group is Premium and holds a shield: then the shield is
 * spent and the streak holds (it neither grows nor resets). Spending is
 * automatic; nobody has to remember to press anything.
 *
 * Shields are earned by walking, never bought: one when Premium first
 * applies, then one per GOAL_WEEKS_PER_SHIELD goal weeks, at most MAX_SHIELDS.
 * Banked shields survive a lapse but only work while Premium is active.
 *
 * `stored` is what the group document carries now. The streak there is
 * trusted only if it ends on the week just before; otherwise it is rebuilt
 * from `history`, the group's earlier finalized weeks, where a shielded week
 * continues the streak without counting. That rebuild gives a group that
 * finalized weeks before this field existed its real streak the first time,
 * and covers a stored value left by a week that was never finalized (nobody
 * synced), which cannot have met the goal.
 */
export function nextGroupStreak(input: {
  weekStart: string;
  goalHit: boolean;
  /** A paid lease or a live preview, at finalization. */
  hasPremium: boolean;
  stored: {
    streakWeeks?: unknown;
    streakThroughWeek?: unknown;
    shieldsStarted?: unknown;
    streakShields?: unknown;
    shieldProgress?: unknown;
    lastShieldedWeek?: unknown;
  };
  history: readonly FinalizedWeek[];
}): WeekOutcome {
  let shields = readShields(input.stored, input.hasPremium);
  const prior = priorStreak(input.weekStart, input.stored, input.history);

  if (input.goalHit) {
    if (shields && input.hasPremium) {
      const progress = shields.shieldProgress + 1;
      shields = progress >= GOAL_WEEKS_PER_SHIELD ?
        {...shields, streakShields: Math.min(shields.streakShields + 1, MAX_SHIELDS), shieldProgress: 0} :
        {...shields, shieldProgress: progress};
    }
    return {
      streakWeeks: Math.min(prior + 1, MAX_STREAK_WEEKS),
      streakThroughWeek: input.weekStart,
      shielded: false,
      shields,
    };
  }

  // Nothing to protect at zero, so a shield is never spent on it.
  if (input.hasPremium && shields && shields.streakShields > 0 && prior > 0) {
    return {
      streakWeeks: prior,
      streakThroughWeek: input.weekStart,
      shielded: true,
      shields: {...shields, streakShields: shields.streakShields - 1, lastShieldedWeek: input.weekStart},
    };
  }
  return {streakWeeks: 0, streakThroughWeek: input.weekStart, shielded: false, shields};
}

/** Whether nextGroupStreak would need `history` for this group document. */
export function needsStreakHistory(weekStart: string, storedThroughWeek: unknown): boolean {
  return storedThroughWeek !== shiftWeeks(weekStart, -1);
}

/** A paid lease that grants the Premium bundle (`circle_v1`) right now. */
export function hasActivePaidAccess(
  access: {isPremium?: unknown; featureSet?: unknown; leaseValidUntilMs?: number | null} | undefined,
  nowMs: number,
): boolean {
  return access?.isPremium === true &&
    Array.isArray(access.featureSet) && access.featureSet.includes("circle_v1") &&
    typeof access.leaseValidUntilMs === "number" && nowMs < access.leaseValidUntilMs;
}

/** The group's one-time Premium preview, if it is running right now. */
export function hasActivePreview(
  preview: {featureSet?: unknown; startedAtMs?: number | null; validUntilMs?: number | null} | undefined,
  nowMs: number,
): boolean {
  return Array.isArray(preview?.featureSet) && preview.featureSet.includes("circle_v1") &&
    typeof preview.startedAtMs === "number" && preview.startedAtMs <= nowMs &&
    typeof preview.validUntilMs === "number" && nowMs < preview.validUntilMs;
}

/**
 * `shieldsStarted`, not the count, says whether a group's shields have begun:
 * the app writes `streakShields: 0` when it creates a group, and that zero
 * must not read as "had shields, used them all".
 */
function readShields(
  stored: {shieldsStarted?: unknown; streakShields?: unknown; shieldProgress?: unknown; lastShieldedWeek?: unknown},
  hasPremium: boolean,
): ShieldState | undefined {
  const lastShieldedWeek = typeof stored.lastShieldedWeek === "string" &&
    datesInWeek(stored.lastShieldedWeek).length > 0 ? stored.lastShieldedWeek : undefined;
  if (stored.shieldsStarted !== true) {
    // Only Premium starts a group's shields, once ever.
    return hasPremium ? withLast({streakShields: STARTING_SHIELDS, shieldProgress: 0}, lastShieldedWeek) : undefined;
  }
  const raw = stored.streakShields;
  const count = typeof raw === "number" && Number.isSafeInteger(raw) ? Math.min(Math.max(raw, 0), MAX_SHIELDS) : 0;
  const progress = stored.shieldProgress;
  return withLast({
    streakShields: count,
    shieldProgress: typeof progress === "number" && Number.isSafeInteger(progress) &&
      progress >= 0 && progress < GOAL_WEEKS_PER_SHIELD ? progress : 0,
  }, lastShieldedWeek);
}

function withLast(shields: ShieldState, lastShieldedWeek: string | undefined): ShieldState {
  return lastShieldedWeek ? {...shields, lastShieldedWeek} : shields;
}

function priorStreak(
  weekStart: string,
  stored: {streakWeeks?: unknown; streakThroughWeek?: unknown},
  history: readonly FinalizedWeek[],
): number {
  const weekBefore = shiftWeeks(weekStart, -1);
  if (!weekBefore) return 0;
  const value = stored.streakWeeks;
  if (stored.streakThroughWeek === weekBefore &&
      typeof value === "number" && Number.isSafeInteger(value) && value >= 0) {
    return Math.min(value, MAX_STREAK_WEEKS);
  }
  return rebuiltStreak(weekBefore, history);
}

/** Walks back through finalized weeks: a met week counts, a shielded one only continues. */
function rebuiltStreak(lastWeek: string, history: readonly FinalizedWeek[]): number {
  const byWeek = new Map(history.map((week) => [week.weekStart, week]));
  let count = 0;
  let steps = 0;
  let week: string | null = lastWeek;
  while (week && steps < MAX_STREAK_WEEKS) {
    const finalized = byWeek.get(week);
    if (finalized?.goalHit) count += 1;
    else if (!finalized?.shielded) break;
    steps += 1;
    week = shiftWeeks(week, -1);
  }
  return count;
}

function shiftWeeks(weekStart: string, weeks: number): string | null {
  // datesInWeek validates the date and returns nothing for a malformed one.
  if (datesInWeek(weekStart).length === 0) return null;
  const date = new Date(`${weekStart}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + weeks * 7);
  return date.toISOString().slice(0, 10);
}
