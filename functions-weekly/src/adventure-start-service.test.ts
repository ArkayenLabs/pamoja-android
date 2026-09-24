import assert from "node:assert/strict";
import test, {after} from "node:test";
import {randomUUID} from "node:crypto";
import {deleteApp, initializeApp} from "firebase-admin/app";
import {getFirestore, Timestamp} from "firebase-admin/firestore";
import {startTogetherTrail} from "./adventure-start-service.js";

// Fixtures must never reach a live database. Use a demo project even locally.
const enabled = /^(127\.0\.0\.1|localhost):\d+$/.test(process.env.FIRESTORE_EMULATOR_HOST ?? "");
const app = enabled ? initializeApp({projectId: "demo-pamoja-adventure"}, "adventure-tests") : null;
const db = app ? getFirestore(app) : null;
const now = Date.parse("2026-09-17T12:00:00Z");
after(async () => { if (db) await db.terminate(); if (app) await deleteApp(app); });

async function fixture() {
  if (!db) throw new Error("Local Firestore emulator required");
  const groupId = randomUUID();
  const group = db.doc(`groups/${groupId}`);
  await group.set({adminId: "organizer", weeklyTarget: 50000});
  await db.doc(`memberships/organizer_${groupId}`).set({userId: "organizer", groupId});
  await db.doc(`memberships/member_${groupId}`).set({userId: "member", groupId});
  const access = db.doc(`groupAccess/${groupId}`);
  await access.set({isPremium: true, featureSet: ["circle_v1"], leaseValidUntil: Timestamp.fromMillis(now + 1000)});
  await db.doc("featureRollouts/togetherTrail").set({enabled: true, publicEnrollment: true});
  const input = {groupId, adventureId: randomUUID(), targetSteps: 200000, timeZone: "UTC"};
  return {group, access, input};
}

test("new groups with blank planning timezone use organizer zone; configured calendar wins", {skip: !enabled}, async () => {
  for (const configured of [null, "America/New_York"]) {
    const f = await fixture();
    await f.group.update({planningTimeZone: ""});
    if (configured) await db!.doc(`groupPlanning/${f.input.groupId}`).set({timeZone: configured});
    await startTogetherTrail(db!, "organizer", {...f.input, timeZone: "Europe/London"}, () => now);
    const created = await f.group.collection("adventures").doc(f.input.adventureId).get();
    assert.equal(created.get("timeZone"), configured ?? "Europe/London");
  }
  const invalid = await fixture();
  await invalid.group.update({planningTimeZone: "invalid/zone"});
  await assert.rejects(startTogetherTrail(db!, "organizer", invalid.input, () => now), {code: "invalid-argument"});
  assert.equal((await invalid.group.collection("adventures").get()).size, 0);
});

test("start creates one group grant; retry survives normal expiry but cannot change the target", {skip: !enabled}, async () => {
  const f = await fixture();
  assert.equal((await startTogetherTrail(db!, "organizer", f.input, () => now)).created, true);
  await f.access.update({leaseValidUntil: Timestamp.fromMillis(now)});
  assert.equal((await startTogetherTrail(db!, "organizer", f.input, () => now + 2000)).created, false);
  await assert.rejects(startTogetherTrail(db!, "organizer", {...f.input, targetSteps: 250000}, () => now));
  const adventure = await f.group.collection("adventures").doc(f.input.adventureId).get();
  assert.equal(adventure.get("completionGrant.source"), "verified_group_premium");
  assert.equal(adventure.get("targetSteps"), 200000);
  assert.equal((await f.group.get()).get("weeklyTarget"), 50000);
});

test("two concurrent distinct starts cannot create two adventures", {skip: !enabled}, async () => {
  const f = await fixture();
  const results = await Promise.allSettled([
    startTogetherTrail(db!, "organizer", f.input, () => now),
    startTogetherTrail(db!, "organizer", {...f.input, adventureId: randomUUID()}, () => now),
  ]);
  assert.equal(results.filter((result) => result.status === "fulfilled").length, 1);
  assert.equal((await f.group.collection("adventures").get()).size, 1);
});

test("members, outsiders and departed organizers cannot start or replay an organizer operation", {skip: !enabled}, async () => {
  const f = await fixture();
  for (const uid of ["member", "outsider"]) {
    await assert.rejects(startTogetherTrail(db!, uid, f.input, () => now), {code: "permission-denied"});
  }
  await startTogetherTrail(db!, "organizer", f.input, () => now);
  await db!.doc(`memberships/organizer_${f.input.groupId}`).delete();
  await assert.rejects(startTogetherTrail(db!, "organizer", f.input, () => now), {code: "permission-denied"});
});

test("expired coverage, missing rollout and pending deletion fail before a grant is written", {skip: !enabled}, async () => {
  const f = await fixture();
  await assert.rejects(startTogetherTrail(db!, "organizer", f.input, () => now + 1000));
  await db!.doc("featureRollouts/togetherTrail").delete();
  await assert.rejects(startTogetherTrail(db!, "organizer", f.input, () => now));
  await f.group.update({deletionPending: true});
  await assert.rejects(startTogetherTrail(db!, "organizer", f.input, () => now), {code: "permission-denied"});
  assert.equal((await f.group.collection("adventures").get()).size, 0);
});
