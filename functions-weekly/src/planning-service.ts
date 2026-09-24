import {FieldValue, Firestore, Timestamp, DocumentReference} from "firebase-admin/firestore";
import {HttpsError} from "firebase-functions/v2/https";
import {isValidGroupId} from "./weekly-summary.js";
import {planningWindow, planningZone, targetForPlan} from "./planning-window.js";
import {scheduledTargetForWeek} from "./next-week-plan.js";

function requireId(groupId: unknown): asserts groupId is string {
  if (!isValidGroupId(groupId)) throw new HttpsError("invalid-argument", "Invalid group");
}

function paid(data: FirebaseFirestore.DocumentData | undefined, now: number): boolean {
  return data?.isPremium === true && Array.isArray(data.featureSet) &&
    data.featureSet.includes("circle_v1") && data.leaseValidUntil instanceof Timestamp &&
    data.leaseValidUntil.toMillis() > now;
}

function planningEnabled(data: FirebaseFirestore.DocumentData | undefined, groupId: string): boolean {
  return data?.enabled === true && (data.publicEnrollment === true ||
    (Array.isArray(data.groupIds) && data.groupIds.includes(groupId)));
}

/** The zone is chosen once by the current organiser. Membership is checked on
 * every request; neither a device clock nor a cached group week is authority. */
export async function getPlanningWindow(db: Firestore, uid: string, groupId: unknown,
  requestedZone: unknown, now = Date.now) {
  requireId(groupId);
  return db.runTransaction(async (tx) => {
    const groupRef = db.doc(`groups/${groupId}`);
    const configRef = db.doc(`groupPlanning/${groupId}`);
    const [group, member, config, access, rollout] = await tx.getAll(groupRef,
      db.doc(`memberships/${uid}_${groupId}`), configRef,
      db.doc(`groupAccess/${groupId}`), db.doc("featureRollouts/dayOnePlanning"));
    if (!group.exists || !member.exists) throw new HttpsError("permission-denied", "Current members only");
    if (!planningEnabled(rollout.data(), groupId)) throw new HttpsError("failed-precondition", "Planning is not enabled for this group yet", {reason: "planning_not_enabled"});
    const time = now();
    let zone = config.get("timeZone");
    if (!config.exists) {
      if (!paid(access.data(), time)) {
        throw new HttpsError("failed-precondition", "Active group Premium is required", {reason: "premium_required"});
      }
      if (group.get("adminId") !== uid) {
        throw new HttpsError("failed-precondition", "The organiser needs to open planning first", {reason: "organizer_setup_required"});
      }
      try { zone = planningZone(requestedZone); } catch {
        throw new HttpsError("invalid-argument", "Invalid timezone");
      }
      // Do not switch a legacy scheduled promise to a different boundary.
      const pending = await tx.get(groupRef.collection("nextWeekPlans").where("status", "==", "scheduled").limit(50));
      const currentWeek = planningWindow(time, zone, group.get("weekStartDay")).currentWeekStart;
      if (pending.size === 50 || pending.docs.some((plan) => plan.id >= currentWeek)) {
        throw new HttpsError("failed-precondition", "Your saved plan must finish before enabling the new planning calendar", {reason: "existing_plan_pending"});
      }
      // An obsolete promise must not block a dormant group forever. Preserve
      // it as missed; never apply that old target to the current week.
      pending.docs.forEach((plan) => tx.update(plan.ref, {
        status: "missed", updatedAt: FieldValue.serverTimestamp(),
      }));
      tx.create(configRef, {timeZone: zone, createdAt: FieldValue.serverTimestamp()});
    }
    if (group.get("planningTimeZone") !== zone) tx.update(groupRef, {planningTimeZone: zone});
    return {...planningWindow(time, zone, group.get("weekStartDay")),
      serverNowMillis: time, currentTargetSteps: group.get("weeklyTarget")};
  });
}

/** Writes the paid plan and the minimal member-readable promise atomically. */
export async function saveAuthoritativePlan(db: Firestore, uid: string,
  input: Record<string, unknown>, now = Date.now): Promise<void> {
  const groupId = input.groupId;
  requireId(groupId);
  if (typeof input.weekStart !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(input.weekStart)) {
    throw new HttpsError("invalid-argument", "Invalid week");
  }
  await db.runTransaction(async (tx) => {
    const groupRef = db.doc(`groups/${groupId}`);
    const planRef = groupRef.collection("nextWeekPlans").doc(input.weekStart as string);
    const [group, member, access, config, rollout, existing] = await tx.getAll(groupRef,
      db.doc(`memberships/${uid}_${groupId}`), db.doc(`groupAccess/${groupId}`),
      db.doc(`groupPlanning/${groupId}`), db.doc("featureRollouts/dayOnePlanning"), planRef);
    const time = now();
    if (!group.exists || !member.exists || group.get("adminId") !== uid) {
      throw new HttpsError("permission-denied", "An organiser with Premium is required");
    }
    if (!paid(access.data(), time)) {
      throw new HttpsError("failed-precondition", "Active group Premium is required", {reason: "premium_required"});
    }
    if (!config.exists || !planningEnabled(rollout.data(), groupId)) {
      throw new HttpsError("failed-precondition", "Open planning again");
    }
    const window = planningWindow(time, config.get("timeZone"), group.get("weekStartDay"));
    if (window.weekStart !== input.weekStart || (existing.exists && existing.get("status") !== "scheduled")) {
      throw new HttpsError("failed-precondition", "The week has changed. Open planning again");
    }
    const currentTarget = group.get("weeklyTarget");
    let target: number;
    try { target = targetForPlan(currentTarget, input.choice, input.targetSteps); } catch {
      throw new HttpsError("invalid-argument", "Invalid weekly target");
    }
    if (target !== input.targetSteps || input.basisTargetSteps !== currentTarget) {
      throw new HttpsError("failed-precondition", "The group goal changed. Open planning again");
    }
    // Read a historical basis from its server-owned document, never trust totals
    // supplied by a caller. Day-one plans have explicit null historical fields.
    let source: FirebaseFirestore.DocumentData | undefined;
    if (input.sourceWeekStart != null) {
      if (typeof input.sourceWeekStart !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(input.sourceWeekStart) ||
          input.sourceWeekStart >= window.currentWeekStart) {
        throw new HttpsError("invalid-argument", "Invalid historical basis");
      }
      source = (await tx.get(groupRef.collection("weeks").doc(input.sourceWeekStart))).data();
      if (!source) throw new HttpsError("failed-precondition", "The historical week is unavailable");
    }
    const appliesAt = Timestamp.fromMillis(window.appliesAtMillis);
    const endsAt = Timestamp.fromMillis(window.endsAtMillis);
    const data = {
      schemaVersion: 2, groupId, weekStart: window.weekStart, targetSteps: target,
      choice: input.choice, sourceWeekStart: input.sourceWeekStart ?? null,
      basisTotalSteps: source?.totalSteps ?? null, basisTargetSteps: currentTarget,
      status: "scheduled", timeZone: window.timeZone, appliesAt, endsAt,
      updatedAt: FieldValue.serverTimestamp(),
    };
    if (existing.exists) tx.update(planRef, data);
    else tx.create(planRef, {...data, createdBy: uid, createdAt: FieldValue.serverTimestamp(),
      inCount: 0, preferGentlerCount: 0, restingCount: 0});
    tx.update(groupRef, {plannedWeekStart: window.weekStart});
    tx.set(groupRef.collection("planningSummary").doc("current"), {
      weekStart: window.weekStart, targetSteps: target, timeZone: window.timeZone,
      status: "scheduled", appliesAt, endsAt, updatedAt: FieldValue.serverTimestamp(),
    });
  });
}

/** A delayed scheduler may apply within the intended week, but never to a
 * later week. No paid-lease recheck: a saved promise survives Premium expiry. */
export async function applyDuePlan(db: Firestore, planRef: DocumentReference, now = Date.now): Promise<void> {
  await db.runTransaction(async (tx) => {
    const plan = await tx.get(planRef);
    const data = plan.data();
    if (!data || data.schemaVersion !== 2 || data.status !== "scheduled" ||
        !(data.appliesAt instanceof Timestamp) || !(data.endsAt instanceof Timestamp)) return;
    const time = now();
    if (time < data.appliesAt.toMillis()) return;
    const target = scheduledTargetForWeek(data, data.groupId, plan.id);
    if (target == null) return;
    const groupRef = planRef.parent.parent!;
    const summaryRef = groupRef.collection("planningSummary").doc("current");
    const [group, summary] = await tx.getAll(groupRef, summaryRef);
    if (!group.exists) { tx.update(planRef, {status: "missed"}); return; }
    const missed = time >= data.endsAt.toMillis();
    if (!missed) {
      // Preserve the old goal for the existing cache-driven history finalizer.
      // Multiple scheduler retries and an overlapping phone rollover cannot
      // replace this snapshot or apply a plan twice.
      const cachedWeek = group.get("weekStart");
      const priorRef = typeof cachedWeek === "string" && /^\d{4}-\d{2}-\d{2}$/.test(cachedWeek) &&
        cachedWeek < plan.id ? groupRef.collection("weekGoals").doc(cachedWeek) : null;
      const prior = priorRef ? await tx.get(priorRef) : null;
      if (priorRef && !prior?.exists) tx.create(priorRef, {targetSteps: group.get("weeklyTarget")});
      tx.update(groupRef, {weeklyTarget: target});
    }
    tx.update(planRef, {status: missed ? "missed" : "applied",
      ...(missed ? {} : {appliedTargetSteps: target, appliedAt: FieldValue.serverTimestamp()}),
      updatedAt: FieldValue.serverTimestamp()});
    if (summary.get("weekStart") === plan.id) tx.update(summaryRef, {
      status: missed ? "missed" : "applied", updatedAt: FieldValue.serverTimestamp(),
    });
    if (group.get("plannedWeekStart") === plan.id) tx.update(groupRef, {plannedWeekStart: ""});
  });
}
