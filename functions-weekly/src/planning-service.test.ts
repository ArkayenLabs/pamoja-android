import assert from "node:assert/strict";
import test from "node:test";
import {randomUUID} from "node:crypto";
import {initializeApp} from "firebase-admin/app";
import {getFirestore, Timestamp} from "firebase-admin/firestore";
import {applyDuePlan, getPlanningWindow, saveAuthoritativePlan} from "./planning-service.js";

// Never run these fixtures against production. Admin transport is explicitly
// directed at the emulator, and every test uses an isolated random group.
const enabled = /^(127\.0\.0\.1|localhost):\d+$/.test(process.env.FIRESTORE_EMULATOR_HOST ?? "");
const db = enabled ? getFirestore(initializeApp({projectId: "pamoja-android-1ac6d"}, "planning-tests")) : null;
const start = Date.parse("2026-09-12T12:00:00Z");

async function fixture() {
  const groupId = randomUUID();
  const group = db!.doc(`groups/${groupId}`);
  const access = db!.doc(`groupAccess/${groupId}`);
  await group.set({groupId, adminId: "organizer", weekStartDay: "MONDAY", weeklyTarget: 70_000, weekStart: "2026-08-03", weeklySteps: 123});
  await db!.doc(`memberships/organizer_${groupId}`).set({userId: "organizer", groupId});
  await db!.doc(`memberships/member_${groupId}`).set({userId: "member", groupId});
  await access.set({isPremium: true, featureSet: ["circle_v1"], leaseValidUntil: Timestamp.fromMillis(start + 60_000)});
  await db!.doc("featureRollouts/dayOnePlanning").set({enabled: true, publicEnrollment: true});
  const window = await getPlanningWindow(db!, "organizer", groupId, "Asia/Kolkata", () => start);
  const input = {groupId, weekStart: window.weekStart, targetSteps: 60_000, choice: "gentler", basisTargetSteps: 70_000};
  return {groupId, group, access, window, input, plan: group.collection("nextWeekPlans").doc(window.weekStart)};
}

test("authoritative admission, expiry promise, dormant cache and duplicate scheduler", {skip: !enabled}, async () => {
  const f = await fixture();
  const memberWindow = await getPlanningWindow(db!, "member", f.groupId, "America/Los_Angeles", () => start);
  assert.equal(memberWindow.timeZone, f.window.timeZone);
  await assert.rejects(saveAuthoritativePlan(db!, "member", f.input, () => start));
  await assert.rejects(saveAuthoritativePlan(db!, "organizer", {...f.input, weekStart: "2028-01-03"}, () => start));
  await assert.rejects(saveAuthoritativePlan(db!, "organizer", {...f.input, targetSteps: 70_000}, () => start));
  await saveAuthoritativePlan(db!, "organizer", f.input, () => start);
  assert.equal((await f.group.get()).get("weeklyTarget"), 70_000);
  assert.equal((await f.group.get()).get("planningTimeZone"), f.window.timeZone);
  assert.equal((await f.group.get()).get("plannedWeekStart"), f.window.weekStart);
  await assert.rejects(saveAuthoritativePlan(db!, "organizer", f.input, () => start + 60_000));
  await applyDuePlan(db!, f.plan, () => f.window.appliesAtMillis - 1);
  assert.equal((await f.group.get()).get("weeklyTarget"), 70_000);
  await Promise.all([applyDuePlan(db!, f.plan, () => f.window.appliesAtMillis),
    applyDuePlan(db!, f.plan, () => f.window.appliesAtMillis)]);
  assert.equal((await f.group.get()).get("weeklyTarget"), 60_000);
  assert.equal((await f.plan.get()).get("status"), "applied");
  assert.equal((await f.group.get()).get("plannedWeekStart"), "");
  assert.equal((await f.group.collection("weekGoals").doc("2026-08-03").get()).get("targetSteps"), 70_000);
  const summary = (await f.group.collection("planningSummary").doc("current").get()).data()!;
  assert.equal(summary.status, "applied");
  assert.deepEqual(Object.keys(summary).sort(), ["appliesAt", "endsAt", "status", "targetSteps", "timeZone", "updatedAt", "weekStart"]);
});

test("stale open screen is rejected at the boundary even with a valid paid lease", {skip: !enabled}, async () => {
  const f = await fixture();
  await f.access.update({leaseValidUntil: Timestamp.fromMillis(f.window.endsAtMillis)});
  await assert.rejects(saveAuthoritativePlan(db!, "organizer", f.input, () => f.window.appliesAtMillis), /week has changed/);
  await f.group.update({weeklyTarget: 90_000});
  await assert.rejects(saveAuthoritativePlan(db!, "organizer", f.input, () => start), /goal changed/);
});

test("scheduler outage never applies an old promise to a later week", {skip: !enabled}, async () => {
  const f = await fixture();
  await saveAuthoritativePlan(db!, "organizer", f.input, () => start);
  await applyDuePlan(db!, f.plan, () => f.window.endsAtMillis);
  assert.equal((await f.group.get()).get("weeklyTarget"), 70_000);
  assert.equal((await f.plan.get()).get("status"), "missed");
});

test("deleted group cannot be recreated by a delayed scheduler", {skip: !enabled}, async () => {
  const f = await fixture();
  await saveAuthoritativePlan(db!, "organizer", f.input, () => start);
  await f.group.delete();
  await applyDuePlan(db!, f.plan, () => f.window.appliesAtMillis);
  assert.equal((await f.group.get()).exists, false);
});

test("controlled rollout admits only the selected group", {skip: !enabled}, async () => {
  const f = await fixture();
  const rollout = db!.doc("featureRollouts/dayOnePlanning");
  await rollout.set({enabled: true});
  await assert.rejects(getPlanningWindow(db!, "organizer", f.groupId, "UTC", () => start), /not enabled/);
  await rollout.set({enabled: true, groupIds: [f.groupId]});
  assert.equal((await getPlanningWindow(db!, "member", f.groupId, "UTC", () => start)).timeZone, f.window.timeZone);
});

test("obsolete legacy plans are retained as missed without changing the goal", {skip: !enabled}, async () => {
  const f = await fixture();
  await db!.doc(`groupPlanning/${f.groupId}`).delete();
  const old = f.group.collection("nextWeekPlans").doc("2026-08-10");
  await old.set({schemaVersion: 1, status: "scheduled", targetSteps: 90_000});
  await getPlanningWindow(db!, "organizer", f.groupId, "Asia/Kolkata", () => start);
  assert.equal((await old.get()).get("status"), "missed");
  assert.equal((await f.group.get()).get("weeklyTarget"), 70_000);
  // A promise for the current/incoming week must not silently change calendar.
  await db!.doc(`groupPlanning/${f.groupId}`).delete();
  await f.plan.set({schemaVersion: 1, status: "scheduled", targetSteps: 90_000});
  await assert.rejects(getPlanningWindow(db!, "organizer", f.groupId, "UTC", () => start), /must finish/);
});

test("calendar setup distinguishes expired Premium from another member needing the organizer", {skip: !enabled}, async () => {
  const f = await fixture();
  await db!.doc(`groupPlanning/${f.groupId}`).delete();
  const reason = (expected: string) => (error: unknown) =>
    (error as {details?: {reason?: string}}).details?.reason === expected;
  await assert.rejects(getPlanningWindow(db!, "member", f.groupId, "UTC", () => start), reason("organizer_setup_required"));
  await f.access.update({leaseValidUntil: Timestamp.fromMillis(start)});
  await assert.rejects(getPlanningWindow(db!, "organizer", f.groupId, "UTC", () => start), reason("premium_required"));
  await assert.rejects(getPlanningWindow(db!, "member", f.groupId, "UTC", () => start), reason("premium_required"));
  await assert.rejects(saveAuthoritativePlan(db!, "organizer", f.input, () => start), reason("premium_required"));
  assert.equal((await db!.doc(`groupPlanning/${f.groupId}`).get()).exists, false);
});
