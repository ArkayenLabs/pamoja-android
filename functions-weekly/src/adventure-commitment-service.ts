import {Firestore, Timestamp} from "firebase-admin/firestore";
import {HttpsError} from "firebase-functions/v2/https";
import {adventureRefs, readAdventureAccess} from "./adventure-access.js";
import {MAX_ADVENTURE_DAILY_STEPS, MAX_ADVENTURE_MEMBERS, adventurePace} from "./adventure-ledger.js";
import {planningWindow} from "./planning-window.js";

interface CommitmentInput {
  groupId?: unknown; adventureId?: unknown; weekStart?: unknown;
  steps?: unknown; expectedRevision?: unknown;
}

export async function readAdventureEntry(db: Firestore, uid: string, groupId: unknown, clock = Date.now) {
  const refs = adventureRefs(db, uid, groupId);
  return db.runTransaction(async (tx) => {
    const ctx = await readAdventureAccess(tx, refs, true);
    const state = await tx.get(refs.group.collection("adventureState").doc("current"));
    const access = await tx.get(db.doc(`groupAccess/${refs.group.id}`));
    const expiry = access.get("leaseValidUntil");
    const features = access.get("featureSet");
    const covered = access.get("isPremium") === true && Array.isArray(features) && features.includes("circle_v1") &&
      expiry instanceof Timestamp && expiry.toMillis() > clock();
    const belongsToGroup = state.get("groupEpoch") === ctx.groupEpoch;
    return {groupId: refs.group.id, covered, organizer: ctx.group.get("adminId") === uid,
      activeAdventureId: belongsToGroup ? state.get("activeAdventureId") ?? null : null,
      lastAdventureId: belongsToGroup ? state.get("lastAdventureId") ?? null : null};
  });
}

export async function saveAdventureCommitment(db: Firestore, uid: string,
  input: CommitmentInput, clock: () => number = Date.now) {
  const refs = adventureRefs(db, uid, input.groupId, input.adventureId);
  if (!refs.adventure || typeof input.steps !== "number" || !Number.isSafeInteger(input.steps) ||
      input.steps < 0 || input.steps > 7 * MAX_ADVENTURE_DAILY_STEPS ||
      typeof input.expectedRevision !== "number" || !Number.isSafeInteger(input.expectedRevision) ||
      input.expectedRevision < 0 || input.expectedRevision >= Number.MAX_SAFE_INTEGER) {
    throw new HttpsError("invalid-argument", "Invalid commitment");
  }
  const steps = input.steps;
  return db.runTransaction(async (tx) => {
    const ctx = await readAdventureAccess(tx, refs, true);
    if (ctx.adventure!.get("status") !== "active" || ctx.adventure!.get("completedAtMillis") !== null) {
      throw new HttpsError("failed-precondition", "Adventure already finished");
    }
    const now = clock();
    const window = planningWindow(now, ctx.adventure!.get("timeZone"), ctx.adventure!.get("weekStartDay"));
    if (input.weekStart !== window.weekStart) {
      throw new HttpsError("failed-precondition", "Refresh the shared week", {reason: "week_changed"});
    }
    const participant = refs.adventure!.collection("participants").doc(uid);
    const commitment = participant.collection("commitments").doc(window.weekStart);
    const [person, previous] = await tx.getAll(participant, commitment);
    if (person.get("membershipEpoch") !== ctx.epoch || person.get("status") !== "active") {
      throw new HttpsError("failed-precondition", "Join or resume contribution first", {reason: "join_required"});
    }
    const sameEpoch = previous.get("membershipEpoch") === ctx.epoch;
    const revision = sameEpoch ? previous.get("revision") as number : 0;
    if (input.expectedRevision !== revision) {
      // A lost success response can be acknowledged safely; a distinct stale
      // intent must refresh instead of silently overwriting the latest answer.
      if (sameEpoch && revision === (input.expectedRevision as number) + 1 && previous.get("steps") === steps) {
        return {saved: true, revision};
      }
      throw new HttpsError("aborted", "Commitment changed; refresh first", {reason: "commitment_changed"});
    }
    tx.set(commitment, {membershipEpoch: ctx.epoch, weekStart: window.weekStart,
      steps, revision: revision + 1, updatedAtMillis: now});
    return {saved: true, revision: revision + 1};
  });
}

/** Only shared totals plus the caller's own participation leave the server.
 * Other members' source totals, device bindings and commitments stay private. */
export async function readAdventure(db: Firestore, uid: string,
  input: {groupId?: unknown; adventureId?: unknown}, clock: () => number = Date.now) {
  const refs = adventureRefs(db, uid, input.groupId, input.adventureId);
  if (!refs.adventure) throw new HttpsError("invalid-argument", "Adventure required");
  return db.runTransaction(async (tx) => {
    const ctx = await readAdventureAccess(tx, refs, true);
    const adventure = ctx.adventure!;
    const serverNowMillis = clock();
    const window = planningWindow(serverNowMillis, adventure.get("timeZone"), adventure.get("weekStartDay"));
    const members = await tx.get(db.collection("memberships").where("groupId", "==", refs.group.id)
      .limit(MAX_ADVENTURE_MEMBERS + 1));
    if (members.size > MAX_ADVENTURE_MEMBERS) throw new HttpsError("resource-exhausted", "Group exceeds adventure capacity");
    const choices: (number | null)[] = [];
    let own: Record<string, unknown> | null = null;
    for (const member of members.docs) {
      const userId = member.get("userId");
      if (typeof userId !== "string" || userId.includes("/")) continue;
      const personRef = refs.adventure!.collection("participants").doc(userId);
      const [person, choice] = await tx.getAll(personRef, personRef.collection("commitments").doc(window.weekStart));
      const epoch = `${member.createTime.seconds}:${member.createTime.nanoseconds}`;
      if (person.get("membershipEpoch") !== epoch) continue;
      const selected = choice.get("membershipEpoch") === epoch;
      if (person.get("status") === "active") choices.push(selected ? choice.get("steps") : null);
      if (userId === uid) own = {status: person.get("status"), lastSyncAtMillis: person.get("lastSyncAtMillis"),
        baselineWindowStartMillis: person.get("baselineWindowStartMillis"),
        commitmentSteps: selected ? choice.get("steps") : null, commitmentRevision: selected ? choice.get("revision") : 0};
    }
    return {serverNowMillis, adventureId: adventure.id, routeId: adventure.get("routeId"), status: adventure.get("status"),
      targetSteps: adventure.get("targetSteps"), creditedSteps: adventure.get("creditedSteps"),
      earnedChapters: adventure.get("earnedChapters"), completedAtMillis: adventure.get("completedAtMillis"),
      organizer: ctx.group.get("adminId") === uid, nextWeek: window, participation: own,
      pace: adventurePace(Math.max(0, adventure.get("targetSteps") - adventure.get("creditedSteps")), choices)};
  });
}
