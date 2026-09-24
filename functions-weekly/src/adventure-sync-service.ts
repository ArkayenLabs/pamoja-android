import {randomUUID} from "node:crypto";
import {DocumentSnapshot, Firestore, Transaction} from "firebase-admin/firestore";
import {HttpsError} from "firebase-functions/v2/https";
import {isValidGroupId} from "./weekly-summary.js";
import {AdventureCredit, AdventureObservation, AdventureProgress, AdventureSegment,
  MAX_ADVENTURE_DAILY_STEPS, UTC_DAY_MILLIS, adventureWindow, applyAdventureDelta,
  joinAdventureSegment, reconcileAdventureCredit} from "./adventure-ledger.js";

export interface AdventureSyncInput {
  groupId?: unknown; adventureId?: unknown; bindingId?: unknown;
  join?: unknown; token?: unknown; steps?: unknown; windowStartMillis?: unknown;
}

const fail = (reason: string): never => {
  throw new HttpsError("failed-precondition", "Refresh the adventure and try again", {reason});
};
function ids(input: AdventureSyncInput): {groupId: string; adventureId: string} {
  if (!isValidGroupId(input.groupId) || !isValidGroupId(input.adventureId)) {
    throw new HttpsError("invalid-argument", "Invalid adventure");
  }
  return {groupId: input.groupId as string, adventureId: input.adventureId as string};
}
function memberEpoch(member: DocumentSnapshot): string {
  const created = member.createTime;
  if (!created) throw new HttpsError("permission-denied", "Current members only");
  return `${created.seconds}:${created.nanoseconds}`;
}
function references(db: Firestore, uid: string, input: AdventureSyncInput) {
  if (!uid || uid.includes("/") || uid.length > 128) throw new HttpsError("unauthenticated", "Sign in first");
  const {groupId, adventureId} = ids(input);
  const group = db.doc(`groups/${groupId}`);
  const adventure = group.collection("adventures").doc(adventureId);
  const participant = adventure.collection("participants").doc(uid);
  return {groupId, adventureId, group, adventure, participant,
    member: db.doc(`memberships/${uid}_${groupId}`),
    ticket: participant.collection("syncTickets").doc("current"),
    rollout: db.doc("featureRollouts/togetherTrail"),
    state: group.collection("adventureState").doc("current")};
}
async function context(tx: Transaction, refs: ReturnType<typeof references>, allowCompleted = false) {
  const [group, member, adventure, participant, ticket, rollout] = await tx.getAll(
    refs.group, refs.member, refs.adventure, refs.participant, refs.ticket, refs.rollout);
  if (!group.exists || group.get("deletionPending") === true || !member.exists) {
    throw new HttpsError("permission-denied", "Current members only");
  }
  const enabled = rollout.get("enabled") === true && (rollout.get("publicEnrollment") === true ||
    (Array.isArray(rollout.get("groupIds")) && rollout.get("groupIds").includes(refs.groupId)));
  if (!enabled) fail("feature_disabled");
  if (!adventure.exists || adventure.get("completionGrant.policyVersion") !== 1 ||
      adventure.get("groupEpoch") !== memberEpoch(group) ||
      (adventure.get("status") !== "active" &&
        !(allowCompleted && adventure.get("status") === "completed"))) fail("adventure_not_active");
  return {group, member, adventure, participant, ticket, epoch: memberEpoch(member)};
}

/** Issues one short-lived, server-timed read ticket. Health counts are still
 * supplied by the phone; this bounds accounting, not physical authenticity. */
export async function prepareAdventureSync(db: Firestore, uid: string,
  input: AdventureSyncInput, clock: () => number = Date.now) {
  const refs = references(db, uid, input);
  if (!isValidGroupId(input.bindingId) || typeof input.join !== "boolean") {
    throw new HttpsError("invalid-argument", "Invalid sync request");
  }
  return db.runTransaction(async (tx) => {
    const ctx = await context(tx, refs);
    const now = clock();
    const currentWindow = adventureWindow(now);
    const active = ctx.participant.get("status") === "active" &&
      ctx.participant.get("membershipEpoch") === ctx.epoch;
    if (active && ctx.participant.get("bindingId") !== input.bindingId) fail("device_bound_elsewhere");
    if (!active && !input.join) fail("join_required");
    const joining = !active;
    const requestedWindow = input.windowStartMillis ?? currentWindow.startMillis;
    if (typeof requestedWindow !== "number" || !Number.isSafeInteger(requestedWindow) ||
        requestedWindow % UTC_DAY_MILLIS !== 0 || requestedWindow > currentWindow.startMillis ||
        requestedWindow < currentWindow.startMillis - 7 * UTC_DAY_MILLIS ||
        (joining && requestedWindow !== currentWindow.startMillis)) {
      throw new HttpsError("invalid-argument", "Invalid source window");
    }
    const segmentId = joining ? randomUUID() : ctx.participant.get("segmentId");
    if (!isValidGroupId(segmentId)) fail("invalid_participation");
    if (!joining && requestedWindow < ctx.participant.get("baselineWindowStartMillis")) fail("before_join");
    const completedAt = ctx.adventure.get("completedAtMillis");
    const through = Math.min(now, requestedWindow + UTC_DAY_MILLIS,
      typeof completedAt === "number" ? completedAt : now);
    if (through <= requestedWindow) fail("sync_not_ready");
    const old = ctx.ticket.data();
    if (old && old.expiresAtMillis > now && old.consumed !== true &&
        old.membershipEpoch === ctx.epoch && old.bindingId === input.bindingId &&
        old.windowStartMillis === requestedWindow && old.joining === joining) {
      return {token: old.token as string, windowStartMillis: requestedWindow,
        observedThroughMillis: old.observedThroughMillis as number, joining};
    }
    if (old && old.issuedAtMillis > now - 2000) fail("sync_too_soon");
    const token = randomUUID();
    tx.set(refs.ticket, {token, segmentId, bindingId: input.bindingId,
      membershipEpoch: ctx.epoch, windowStartMillis: requestedWindow,
      observedThroughMillis: through, issuedAtMillis: now, expiresAtMillis: now + 60_000,
      joining, consumed: false});
    return {token, windowStartMillis: requestedWindow, observedThroughMillis: through, joining};
  });
}

/** Accepts one response to a server-issued ticket and commits source revision,
 * per-window credit and group progress in the SAME transaction. */
export async function submitAdventureSync(db: Firestore, uid: string,
  input: AdventureSyncInput, clock: () => number = Date.now) {
  const refs = references(db, uid, input);
  if (!isValidGroupId(input.token) || !isValidGroupId(input.bindingId) ||
      typeof input.steps !== "number" || !Number.isSafeInteger(input.steps) ||
      input.steps < 0 || input.steps > MAX_ADVENTURE_DAILY_STEPS) {
    throw new HttpsError("invalid-argument", "Invalid observation");
  }
  const steps = input.steps;
  return db.runTransaction(async (tx) => {
    const ctx = await context(tx, refs);
    const ticket = ctx.ticket.data();
    const now = clock();
    if (!ticket || ticket.token !== input.token || ticket.bindingId !== input.bindingId ||
        ticket.membershipEpoch !== ctx.epoch) fail("stale_sync");
    const t = ticket!;
    if (t.consumed === true) {
      if (t.steps !== steps) fail("source_conflict");
      return {saved: true, creditedSteps: ctx.adventure.get("creditedSteps") as number};
    }
    if (t.expiresAtMillis < now) fail("sync_expired");
    const currentEpoch = ctx.participant.get("membershipEpoch") === ctx.epoch;
    if (t.joining && currentEpoch && ctx.participant.get("status") === "active") fail("already_joined");
    if (!t.joining && (!currentEpoch || ctx.participant.get("status") !== "active" ||
        ctx.participant.get("bindingId") !== t.bindingId ||
        ctx.participant.get("segmentId") !== t.segmentId)) fail("participation_changed");
    const segmentRef = refs.participant.collection("segments").doc(t.segmentId);
    const creditRef = segmentRef.collection("credits").doc(String(t.windowStartMillis));
    const [storedSegment, storedCredit] = await tx.getAll(segmentRef, creditRef);
    const previous = storedCredit.exists ? storedCredit.data() as AdventureCredit : null;
    const observation: AdventureObservation = {bindingId: t.bindingId,
      windowStartMillis: t.windowStartMillis, steps, revision: (previous?.revision ?? 0) + 1,
      acceptedAtMillis: now};
    let progress = {targetSteps: ctx.adventure.get("targetSteps"),
      creditedSteps: ctx.adventure.get("creditedSteps"), earnedChapters: ctx.adventure.get("earnedChapters"),
      completedAtMillis: ctx.adventure.get("completedAtMillis")} as AdventureProgress;
    if (t.joining) {
      // Joining at the next midnight would seed a different day; request a new ticket.
      if (adventureWindow(now).startMillis !== t.windowStartMillis) fail("sync_expired");
      const segment = joinAdventureSegment(t.segmentId, observation, now);
      tx.create(segmentRef, {...segment, membershipEpoch: ctx.epoch});
      tx.set(refs.participant, {userId: uid, groupId: refs.groupId, adventureId: refs.adventureId,
        membershipEpoch: ctx.epoch, bindingId: t.bindingId, segmentId: t.segmentId,
        baselineWindowStartMillis: t.windowStartMillis, status: "active",
        lastSyncAtMillis: now});
    } else {
      if (!storedSegment.exists) fail("participation_changed");
      const segment = storedSegment.data() as AdventureSegment;
      const result = reconcileAdventureCredit(segment, previous, observation, now);
      progress = applyAdventureDelta(progress, result.delta, now);
      if (result.credit) tx.set(creditRef, result.credit);
      tx.update(refs.participant, {lastSyncAtMillis: now});
      tx.update(refs.adventure, {...progress});
      tx.set(refs.state, {updatedAtMillis: now}, {merge: true});
      // Completion is sticky; explicit finalization/correction protocol is a
      // separate operation. Keeping status active permits delayed corrections.
    }
    tx.update(refs.ticket, {consumed: true, steps});
    return {saved: true, creditedSteps: progress.creditedSteps};
  });
}

/** Explicit pause also permits choosing a different contributing device on the
 * next join. It never imports the new phone's historical daily totals. */
export async function pauseAdventureContribution(db: Firestore, uid: string,
  input: AdventureSyncInput, clock: () => number = Date.now) {
  const refs = references(db, uid, input);
  return db.runTransaction(async (tx) => {
    const ctx = await context(tx, refs);
    if (ctx.participant.get("membershipEpoch") !== ctx.epoch) return {paused: true};
    const segmentId = ctx.participant.get("segmentId");
    if (!isValidGroupId(segmentId)) return {paused: true};
    const segmentRef = refs.participant.collection("segments").doc(segmentId);
    const segment = await tx.get(segmentRef);
    const now = clock();
    if (segment.exists && segment.get("closedAtMillis") === null) tx.update(segmentRef, {closedAtMillis: now});
    tx.update(refs.participant, {status: "paused"});
    tx.delete(refs.ticket);
    return {paused: true};
  });
}

/** Archive an earned finish. Before this explicit finalization, corrected reads
 * may reconcile activity up to the completion cutoff. Afterwards the keepsake
 * is a frozen record, and the group may start a new paid adventure. */
export async function finalizeAdventure(db: Firestore, uid: string,
  input: AdventureSyncInput, clock: () => number = Date.now) {
  const refs = references(db, uid, input);
  return db.runTransaction(async (tx) => {
    const ctx = await context(tx, refs, true);
    if (ctx.adventure.get("status") === "completed") return {completed: true};
    const state = await tx.get(refs.state);
    if (ctx.adventure.get("completedAtMillis") === null || ctx.adventure.get("earnedChapters") !== 5) {
      fail("adventure_not_finished");
    }
    const now = clock();
    tx.update(refs.adventure, {status: "completed", finalizedAtMillis: now});
    if (state.get("activeAdventureId") === refs.adventureId) {
      tx.set(refs.state, {activeAdventureId: null, lastAdventureId: refs.adventureId, updatedAtMillis: now}, {merge: true});
    }
    return {completed: true};
  });
}
