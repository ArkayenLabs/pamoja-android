import {Firestore, Timestamp} from "firebase-admin/firestore";
import {HttpsError} from "firebase-functions/v2/https";
import {isValidGroupId} from "./weekly-summary.js";
import {AdventureError, authorizeAdventureStart, newAdventureProgress} from "./adventure-ledger.js";
import {planningWindow} from "./planning-window.js";

export const TOGETHER_TRAIL_ROUTE = "together_trail_v1";

export interface StartAdventureInput {
  groupId?: unknown;
  /** Stable UUID retained by the client across retry, not regenerated per tap. */
  adventureId?: unknown;
  targetSteps?: unknown;
  timeZone?: unknown;
}

function denied(error: unknown): never {
  if (!(error instanceof AdventureError)) throw error;
  const code = error.reason === "access_denied" ? "permission-denied" :
    error.reason === "invalid_input" ? "invalid-argument" : "failed-precondition";
  throw new HttpsError(code, "The adventure could not be started", {reason: error.reason});
}

/** The callable enforces Firebase Auth and App Check; this transaction owns
 * membership, organizer, paid-lease and rollout authorization. */
export async function startTogetherTrail(db: Firestore, uid: string,
  input: StartAdventureInput, clock: () => number = Date.now) {
  if (!uid || uid.includes("/") || uid.length > 128) {
    throw new HttpsError("unauthenticated", "Sign in first");
  }
  if (!isValidGroupId(input.groupId) || !isValidGroupId(input.adventureId) ||
      typeof input.targetSteps !== "number") {
    throw new HttpsError("invalid-argument", "Invalid adventure");
  }
  const groupId = input.groupId as string;
  const adventureId = input.adventureId as string;
  let progress;
  try { progress = newAdventureProgress(input.targetSteps); } catch (error) { denied(error); }
  const initialProgress = progress;
  return db.runTransaction(async (tx) => {
    const groupRef = db.doc(`groups/${groupId}`);
    const stateRef = groupRef.collection("adventureState").doc("current");
    const adventureRef = groupRef.collection("adventures").doc(adventureId);
    const [group, member, access, rollout, state, existing, calendar] = await tx.getAll(
      groupRef, db.doc(`memberships/${uid}_${groupId}`), db.doc(`groupAccess/${groupId}`),
      db.doc("featureRollouts/togetherTrail"), stateRef, adventureRef, db.doc(`groupPlanning/${groupId}`));
    // Retry never reveals group data to a former member or a new account.
    if (!group.exists || !member.exists || group.get("adminId") !== uid ||
        group.get("deletionPending") === true) {
      throw new HttpsError("permission-denied", "Current organizer only");
    }
    const groupEpoch = `${group.createTime!.seconds}:${group.createTime!.nanoseconds}`;
    if ((existing.exists && existing.get("groupEpoch") !== groupEpoch) ||
        (state.exists && state.get("groupEpoch") !== groupEpoch)) {
      throw new HttpsError("failed-precondition", "Previous group cleanup is still pending");
    }
    if (existing.exists) {
      if (existing.get("createdBy") !== uid || existing.get("routeId") !== TOGETHER_TRAIL_ROUTE ||
          existing.get("targetSteps") !== initialProgress.targetSteps) {
        throw new HttpsError("already-exists", "This request was already used", {reason: "request_conflict"});
      }
      // Preserve success if a network retry arrives after the paid lease expires.
      return {groupId, adventureId, created: false};
    }
    if (state.exists && state.get("activeAdventureId")) {
      throw new HttpsError("already-exists", "Your group already has an active adventure",
        {reason: "adventure_already_active"});
    }
    const now = clock();
    const expiry = access.get("leaseValidUntil");
    const features = access.get("featureSet");
    const hasPaidLease = access.get("isPremium") === true && Array.isArray(features) &&
      features.includes("circle_v1") && expiry instanceof Timestamp;
    const enabledGroups: unknown = rollout.get("groupIds");
    const enabled = rollout.get("enabled") === true && (rollout.get("publicEnrollment") === true ||
      (Array.isArray(enabledGroups) && enabledGroups.includes(groupId)));
    try {
      authorizeAdventureStart({member: true, organizer: true, deletionPending: false,
        rolloutEnabled: enabled, premiumValidUntilMillis: hasPaidLease ? expiry.toMillis() : null}, now);
    } catch (error) { denied(error); }
    let window;
    try {
      // New groups persist an empty timezone until planning is first enabled.
      // Only absent/blank values may fall back; invalid configured zones still
      // fail rather than silently changing an established shared calendar.
      const configuredZone = [calendar.get("timeZone"), group.get("planningTimeZone"), input.timeZone]
        .find((value) => value != null && value !== "");
      window = planningWindow(now, configuredZone, group.get("weekStartDay"));
    } catch { throw new HttpsError("invalid-argument", "A valid shared timezone is required"); }
    tx.create(adventureRef, {
      schemaVersion: 1, groupId, groupEpoch, routeId: TOGETHER_TRAIL_ROUTE, status: "active",
      ...initialProgress, createdBy: uid, createdAtMillis: now,
      creatorMembershipEpoch: `${member.createTime!.seconds}:${member.createTime!.nanoseconds}`,
      timeZone: window.timeZone, weekStartDay: window.startDay,
      // This finite grant belongs to this adventure and this group, not the payer.
      completionGrant: {policyVersion: 1, issuedAtMillis: now, source: "verified_group_premium"},
    });
    tx.set(stateRef, {schemaVersion: 1, groupEpoch, activeAdventureId: adventureId, updatedAtMillis: now});
    return {groupId, adventureId, created: true};
  });
}
