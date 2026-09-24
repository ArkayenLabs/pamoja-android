/** Pure accounting rules. Call only with server-accepted observations inside a
 * transaction that has checked current membership, device binding and rollout.
 * This module does not authenticate phones or verify physical exercise. */
export const UTC_DAY_MILLIS = 86_400_000;
export const MAX_ADVENTURE_DAILY_STEPS = 200_000;
export const MAX_ADVENTURE_TARGET = 11_200_000;
export const MAX_ADVENTURE_MEMBERS = 20;

export class AdventureError extends Error {
  constructor(readonly reason: "invalid_input" | "access_denied" | "premium_required" |
    "feature_disabled" | "source_conflict" | "binding_changed") {
    super(reason);
  }
}

function integer(value: number, min: number, max = Number.MAX_SAFE_INTEGER): void {
  if (!Number.isSafeInteger(value) || value < min || value > max) {
    throw new AdventureError("invalid_input");
  }
}

function identifier(value: string): void {
  if (typeof value !== "string" || value.length < 1 || value.length > 128 ||
      !/^[A-Za-z0-9_-]+$/.test(value)) throw new AdventureError("invalid_input");
}

/** UTC windows never move when the user changes language, travels or crosses DST. */
export function adventureWindow(timeMillis: number): {startMillis: number; endMillis: number} {
  integer(timeMillis, 0, 8_640_000_000_000_000 - UTC_DAY_MILLIS);
  const startMillis = Math.floor(timeMillis / UTC_DAY_MILLIS) * UTC_DAY_MILLIS;
  return {startMillis, endMillis: startMillis + UTC_DAY_MILLIS};
}

export interface AdventureStartContext {
  member: boolean;
  organizer: boolean;
  deletionPending: boolean;
  rolloutEnabled: boolean;
  premiumValidUntilMillis: number | null;
}

/** Caller loads this context from server-owned records, never request fields. */
export function authorizeAdventureStart(context: AdventureStartContext, now: number): void {
  integer(now, 0);
  if (!context.member || !context.organizer || context.deletionPending) {
    throw new AdventureError("access_denied");
  }
  if (!context.rolloutEnabled) throw new AdventureError("feature_disabled");
  if (context.premiumValidUntilMillis === null ||
      !Number.isSafeInteger(context.premiumValidUntilMillis) ||
      context.premiumValidUntilMillis <= now) throw new AdventureError("premium_required");
}

export interface AdventureProgress {
  targetSteps: number;
  creditedSteps: number;
  /** Highest earned chapter, retained after source corrections. */
  earnedChapters: number;
  completedAtMillis: number | null;
}

export function newAdventureProgress(targetSteps: number): AdventureProgress {
  integer(targetSteps, 1_000, MAX_ADVENTURE_TARGET);
  return {targetSteps, creditedSteps: 0, earnedChapters: 0, completedAtMillis: null};
}

/** A new segment is required after pause, leave/rejoin or device transfer. */
export interface AdventureSegment {
  segmentId: string;
  bindingId: string;
  joinedAtMillis: number;
  baselineWindowStartMillis: number;
  baselineSteps: number;
  closedAtMillis: number | null;
}

/** Revision and acceptedAtMillis are assigned by server ingestion. The observed
 * count originates on the phone. A monotonic revision does not prove its truth. */
export interface AdventureObservation {
  bindingId: string;
  windowStartMillis: number;
  revision: number;
  steps: number;
  acceptedAtMillis: number;
}

export interface AdventureCredit {
  segmentId: string;
  bindingId: string;
  windowStartMillis: number;
  revision: number;
  observedSteps: number;
  creditedSteps: number;
}

function validateObservation(observation: AdventureObservation, now: number): void {
  identifier(observation.bindingId);
  integer(observation.revision, 1);
  integer(observation.steps, 0, MAX_ADVENTURE_DAILY_STEPS);
  integer(observation.acceptedAtMillis, 0, now);
  const window = adventureWindow(observation.windowStartMillis);
  if (window.startMillis !== observation.windowStartMillis ||
      observation.windowStartMillis > observation.acceptedAtMillis) {
    throw new AdventureError("invalid_input");
  }
}

/** A baseline must be observed in the current UTC window and in the joining
 * transaction's freshness period. Old daily totals cannot seed participation. */
export function joinAdventureSegment(segmentId: string, observation: AdventureObservation,
  now: number): AdventureSegment {
  integer(now, 0);
  identifier(segmentId);
  validateObservation(observation, now);
  if (adventureWindow(now).startMillis !== observation.windowStartMillis ||
      now - observation.acceptedAtMillis > 60_000) throw new AdventureError("source_conflict");
  return {segmentId, bindingId: observation.bindingId, joinedAtMillis: now,
    baselineWindowStartMillis: observation.windowStartMillis, baselineSteps: observation.steps,
    closedAtMillis: null};
}

/** Closing freezes the accepted ledger. Delayed updates cannot add steps after
 * departure. Resume must create a distinct segment with a fresh baseline. */
export function closeAdventureSegment(segment: AdventureSegment, now: number): AdventureSegment {
  integer(now, segment.joinedAtMillis);
  return segment.closedAtMillis === null ? {...segment, closedAtMillis: now} : segment;
}

export function reconcileAdventureCredit(segment: AdventureSegment,
  previous: AdventureCredit | null, observation: AdventureObservation, now: number): {
    credit: AdventureCredit | null; delta: number;
  } {
  integer(now, segment.joinedAtMillis);
  validateObservation(observation, now);
  if (segment.bindingId !== observation.bindingId) throw new AdventureError("binding_changed");
  if (previous && (previous.segmentId !== segment.segmentId ||
      previous.bindingId !== segment.bindingId ||
      previous.windowStartMillis !== observation.windowStartMillis)) {
    throw new AdventureError("source_conflict");
  }
  if (segment.closedAtMillis !== null ||
      observation.windowStartMillis < segment.baselineWindowStartMillis ||
      observation.acceptedAtMillis < segment.joinedAtMillis) return {credit: previous, delta: 0};
  if (previous && observation.revision <= previous.revision) {
    if (observation.revision === previous.revision && observation.steps !== previous.observedSteps) {
      throw new AdventureError("source_conflict");
    }
    return {credit: previous, delta: 0};
  }
  const baseline = observation.windowStartMillis === segment.baselineWindowStartMillis ?
    segment.baselineSteps : 0;
  const creditedSteps = Math.max(0, observation.steps - baseline);
  return {credit: {segmentId: segment.segmentId, bindingId: segment.bindingId,
    windowStartMillis: observation.windowStartMillis, revision: observation.revision,
    observedSteps: observation.steps, creditedSteps},
  delta: creditedSteps - (previous?.creditedSteps ?? 0)};
}

/** Must be committed atomically with its credit entry. Applying a delta without
 * writing the matching entry would defeat replay protection. */
export function applyAdventureDelta(progress: AdventureProgress, delta: number,
  now: number): AdventureProgress {
  integer(progress.targetSteps, 1_000, MAX_ADVENTURE_TARGET);
  integer(progress.creditedSteps, 0);
  integer(progress.earnedChapters, 0, 5);
  integer(now, 0);
  if (!Number.isSafeInteger(delta)) throw new AdventureError("invalid_input");
  const creditedSteps = progress.creditedSteps + delta;
  // Negative values indicate ledger corruption; don't hide it by clamping.
  integer(creditedSteps, 0);
  const reached = Math.min(5, Math.floor(Math.min(creditedSteps, progress.targetSteps) *
    5 / progress.targetSteps));
  return {...progress, creditedSteps, earnedChapters: Math.max(progress.earnedChapters, reached),
    completedAtMillis: progress.completedAtMillis ?? (reached === 5 ? now : null)};
}

/** Unknown commitments deliberately suppress the forecast. Rest is an explicit
 * zero, not missing source data and not an instruction to increase others' goals. */
export function adventurePace(remainingSteps: number, commitments: readonly (number | null)[]): {
  plannedSteps: number; approximateWeeks: number | null; awaitingMembers: number;
} {
  integer(remainingSteps, 0);
  if (commitments.length > MAX_ADVENTURE_MEMBERS) throw new AdventureError("invalid_input");
  let plannedSteps = 0;
  let awaitingMembers = 0;
  for (const value of commitments) {
    if (value === null) awaitingMembers++;
    else { integer(value, 0, 7 * MAX_ADVENTURE_DAILY_STEPS); plannedSteps += value; }
  }
  return {plannedSteps, awaitingMembers,
    approximateWeeks: remainingSteps === 0 ? 0 :
      awaitingMembers > 0 || plannedSteps === 0 ? null : Math.ceil(remainingSteps / plannedSteps)};
}
