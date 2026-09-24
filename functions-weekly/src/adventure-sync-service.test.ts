import assert from "node:assert/strict";
import test, {after} from "node:test";
import {randomUUID} from "node:crypto";
import {initializeApp, deleteApp} from "firebase-admin/app";
import {getFirestore, Timestamp} from "firebase-admin/firestore";
import {startTogetherTrail} from "./adventure-start-service.js";
import {eraseDepartedAdventureData, eraseDeletedGroupAdventures} from "./adventure-privacy.js";
import {readAdventure, readAdventureEntry, saveAdventureCommitment} from "./adventure-commitment-service.js";
import {exportAdventurePage} from "./adventure-export-service.js";
import {prepareAdventureSync, submitAdventureSync, pauseAdventureContribution,
  finalizeAdventure} from "./adventure-sync-service.js";

const enabled = /^(127\.0\.0\.1|localhost):\d+$/.test(process.env.FIRESTORE_EMULATOR_HOST ?? "");
const app = enabled ? initializeApp({projectId: "demo-pamoja-adventure-sync"}, "sync-tests") : null;
const db = app ? getFirestore(app) : null;
after(async () => { if (db) await db.terminate(); if (app) await deleteApp(app); });
const noon = Date.parse("2026-09-17T12:00:00Z");
async function fixture(targetSteps = 200000) {
  const groupId = randomUUID();
  await db!.doc(`groups/${groupId}`).set({adminId: "owner"});
  for (const uid of ["owner", "member"]) await db!.doc(`memberships/${uid}_${groupId}`).set({userId: uid, groupId});
  await db!.doc(`groupAccess/${groupId}`).set({isPremium: true, featureSet: ["circle_v1"], leaseValidUntil: Timestamp.fromMillis(noon + 1)});
  await db!.doc("featureRollouts/togetherTrail").set({enabled: true, publicEnrollment: true});
  const base = {groupId, adventureId: randomUUID(), bindingId: randomUUID(), timeZone: "UTC"};
  await startTogetherTrail(db!, "owner", {...base, targetSteps}, () => noon);
  const ticket = await prepareAdventureSync(db!, "member", {...base, join: true}, () => noon + 10);
  await submitAdventureSync(db!, "member", {...base, token: ticket.token, steps: 5000}, () => noon + 20);
  return base;
}
async function sync(base: Awaited<ReturnType<typeof fixture>>, steps: number, now: number) {
  const ticket = await prepareAdventureSync(db!, "member", {...base, join: false}, () => now);
  const input = {...base, token: ticket.token, steps};
  await submitAdventureSync(db!, "member", input, () => now + 10);
  return input;
}

test("expired subscription can finish its granted adventure; concurrent replay counts once", {skip: !enabled}, async () => {
  const base = await fixture();
  const ticket = await prepareAdventureSync(db!, "member", {...base, join: false}, () => noon + 3000);
  const input = {...base, token: ticket.token, steps: 7000};
  await Promise.all([submitAdventureSync(db!, "member", input, () => noon + 3010),
    submitAdventureSync(db!, "member", input, () => noon + 3010)]);
  assert.equal((await db!.doc(`groups/${base.groupId}/adventures/${base.adventureId}`).get()).get("creditedSteps"), 2000);
  await assert.rejects(submitAdventureSync(db!, "member", {...input, steps: 8000}, () => noon + 3020));
});
test("corrections and pause/resume change only eligible new contributions", {skip: !enabled}, async () => {
  const base = await fixture();
  await sync(base, 7000, noon + 3000);
  await sync(base, 6000, noon + 6000);
  await pauseAdventureContribution(db!, "member", base, () => noon + 7000);
  await assert.rejects(prepareAdventureSync(db!, "member", {...base, join: false}, () => noon + 9000));
  const resume = await prepareAdventureSync(db!, "member", {...base, join: true}, () => noon + 9000);
  await submitAdventureSync(db!, "member", {...base, token: resume.token, steps: 9000}, () => noon + 9010);
  await sync(base, 9500, noon + 12000);
  assert.equal((await db!.doc(`groups/${base.groupId}/adventures/${base.adventureId}`).get()).get("creditedSteps"), 1500);
});
test("old membership epoch, another device and another member cannot consume a ticket", {skip: !enabled}, async () => {
  const base = await fixture();
  const ticket = await prepareAdventureSync(db!, "member", {...base, join: false}, () => noon + 3000);
  await assert.rejects(prepareAdventureSync(db!, "member", {...base, bindingId: randomUUID(), join: true}, () => noon + 4000));
  await assert.rejects(submitAdventureSync(db!, "owner", {...base, token: ticket.token, steps: 8000}, () => noon + 4010));
  const membership = db!.doc(`memberships/member_${base.groupId}`);
  await membership.delete();
  await membership.set({userId: "member", groupId: base.groupId});
  await assert.rejects(submitAdventureSync(db!, "member", {...base, token: ticket.token, steps: 8000}, () => noon + 5000));
});
test("completion cutoff is immutable; archive unblocks a new adventure but cannot manufacture completion", {skip: !enabled}, async () => {
  const base = await fixture(1000);
  await assert.rejects(finalizeAdventure(db!, "owner", base, () => noon + 1000));
  await sync(base, 6500, noon + 3000);
  const ticket = await prepareAdventureSync(db!, "member", {...base, join: false}, () => noon + 6000);
  assert.equal(ticket.observedThroughMillis, noon + 3010);
  await submitAdventureSync(db!, "member", {...base, token: ticket.token, steps: 5500}, () => noon + 6010);
  const ref = db!.doc(`groups/${base.groupId}/adventures/${base.adventureId}`);
  assert.equal((await ref.get()).get("earnedChapters"), 5);
  await finalizeAdventure(db!, "owner", base, () => noon + 7000);
  await finalizeAdventure(db!, "owner", base, () => noon + 7010);
  assert.equal((await ref.get()).get("status"), "completed");
  assert.equal((await db!.doc(`groups/${base.groupId}/adventureState/current`).get()).get("activeAdventureId"), null);
  await assert.rejects(prepareAdventureSync(db!, "member", {...base, join: false}, () => noon + 8000));
});

test("departure removes personal source records, preserves totals and is safe after rejoin", {skip: !enabled}, async () => {
  const base = await fixture();
  await sync(base, 7000, noon + 3000);
  const participant = db!.doc(`groups/${base.groupId}/adventures/${base.adventureId}/participants/member`);
  const before = await participant.get();
  const oldEpoch = before.get("membershipEpoch");
  const oldSegment = participant.collection("segments").doc(before.get("segmentId"));
  const membership = db!.doc(`memberships/member_${base.groupId}`);
  await membership.delete();
  await membership.set({userId: "member", groupId: base.groupId});
  const resume = await prepareAdventureSync(db!, "member", {...base, join: true}, () => noon + 6000);
  await submitAdventureSync(db!, "member", {...base, token: resume.token, steps: 9000}, () => noon + 6010);
  await eraseDepartedAdventureData(db!, base.groupId, "member", oldEpoch);
  await eraseDepartedAdventureData(db!, base.groupId, "member", oldEpoch);
  assert.equal((await oldSegment.get()).exists, false);
  assert.equal((await oldSegment.collection("credits").get()).empty, true);
  assert.equal((await participant.get()).get("status"), "active");
  await sync(base, 9500, noon + 9000);
  assert.equal((await db!.doc(`groups/${base.groupId}/adventures/${base.adventureId}`).get()).get("creditedSteps"), 2500);
  const newEpoch = (await participant.get()).get("membershipEpoch");
  await membership.delete();
  await eraseDepartedAdventureData(db!, base.groupId, "member", newEpoch);
  assert.equal((await participant.get()).exists, false);
  assert.equal((await participant.collection("segments").get()).empty, true);
  assert.equal((await participant.collection("syncTickets").get()).empty, true);
  assert.equal((await db!.doc(`groups/${base.groupId}/adventures/${base.adventureId}`).get()).get("creditedSteps"), 2500);
});

test("departed organizer attribution is erased without deleting the shared adventure", {skip: !enabled}, async () => {
  const base = await fixture();
  const adventure = db!.doc(`groups/${base.groupId}/adventures/${base.adventureId}`);
  const epoch = (await adventure.get()).get("creatorMembershipEpoch");
  await db!.doc(`memberships/owner_${base.groupId}`).delete();
  await eraseDepartedAdventureData(db!, base.groupId, "owner", epoch);
  assert.equal((await adventure.get()).get("createdBy"), undefined);
  assert.equal((await adventure.get()).get("status"), "active");
});

test("group deletion erases nested sources; delayed cleanup preserves a recreated group", {skip: !enabled}, async () => {
  const base = await fixture();
  await sync(base, 7000, noon + 3000);
  const group = db!.doc(`groups/${base.groupId}`);
  const adventure = group.collection("adventures").doc(base.adventureId);
  const person = adventure.collection("participants").doc("member");
  const oldSegment = person.collection("segments").doc((await person.get()).get("segmentId"));
  const epoch = (await adventure.get()).get("groupEpoch");
  await group.delete();
  await group.set({adminId: "owner"});
  await assert.rejects(prepareAdventureSync(db!, "member", {...base, join: false}, () => noon + 6000));
  await eraseDeletedGroupAdventures(db!, base.groupId, epoch);
  assert.equal((await adventure.get()).exists, false);
  assert.equal((await person.get()).exists, false);
  assert.equal((await oldSegment.collection("credits").get()).empty, true);
  assert.equal((await group.get()).exists, true);
  await db!.doc(`groupAccess/${base.groupId}`).update({leaseValidUntil: Timestamp.fromMillis(noon + 60000)});
  const nextId = randomUUID();
  await startTogetherTrail(db!, "owner", {...base, adventureId: nextId, targetSteps: 1000}, () => noon + 7000);
  await eraseDeletedGroupAdventures(db!, base.groupId, epoch);
  assert.equal((await group.collection("adventures").doc(nextId).get()).exists, true);
  assert.equal((await group.collection("adventureState").doc("current").get()).get("activeAdventureId"), nextId);
});
test("expired or invalid source reads cannot seed a baseline", {skip: !enabled}, async () => {
  const base = await fixture();
  const ticket = await prepareAdventureSync(db!, "member", {...base, join: false}, () => noon + 3000);
  await assert.rejects(submitAdventureSync(db!, "member", {...base, token: ticket.token, steps: 8000}, () => noon + 64000));
  await assert.rejects(prepareAdventureSync(db!, "member", {...base, join: false, windowStartMillis: 1}, () => noon + 65000));
  for (const steps of [-1, 200001, 1.5]) await assert.rejects(submitAdventureSync(db!, "member", {...base, token: ticket.token, steps}, () => noon + 4000));
});

test("rest changes chosen pace only, stale edits fail, and the next shared week rolls over", {skip: !enabled}, async () => {
  const base = await fixture();
  await sync(base, 7000, noon + 3000);
  const before = await readAdventure(db!, "member", base, () => noon + 4000);
  assert.equal(before.pace.awaitingMembers, 1);
  const input = {...base, weekStart: before.nextWeek.weekStart, steps: 20000, expectedRevision: 0};
  await saveAdventureCommitment(db!, "member", input, () => noon + 5000);
  assert.equal((await saveAdventureCommitment(db!, "member", input, () => noon + 5100)).revision, 1);
  assert.equal((await readAdventure(db!, "owner", base, () => noon + 6000)).pace.approximateWeeks, 10);
  await assert.rejects(saveAdventureCommitment(db!, "owner", input, () => noon + 6500));
  await assert.rejects(saveAdventureCommitment(db!, "member", {...input, steps: 50000}, () => noon + 6500));
  await saveAdventureCommitment(db!, "member", {...input, steps: 0, expectedRevision: 1}, () => noon + 7000);
  const resting = await readAdventure(db!, "member", base, () => noon + 8000);
  assert.equal(resting.pace.plannedSteps, 0);
  assert.equal(resting.pace.approximateWeeks, null);
  assert.equal(resting.creditedSteps, 2000);
  assert.equal(resting.participation!.status, "active");
  const later = () => before.nextWeek.appliesAtMillis + 1;
  await assert.rejects(saveAdventureCommitment(db!, "member", {...input, expectedRevision: 2}, later));
  assert.equal((await readAdventure(db!, "member", base, later)).pace.awaitingMembers, 1);
  await assert.rejects(readAdventure(db!, "outsider", base, () => noon));
});

test("export is owner scoped, paginated and available while rollout is off; departure erases commitments", {skip: !enabled}, async () => {
  const base = await fixture();
  const view = await readAdventure(db!, "member", base, () => noon + 4000);
  await saveAdventureCommitment(db!, "member", {...base, weekStart: view.nextWeek.weekStart,
    steps: 10000, expectedRevision: 0}, () => noon + 5000);
  const segments = await exportAdventurePage(db!, "member", {...base, kind: "segments"});
  const segmentId = segments.records[0].id;
  await db!.doc("featureRollouts/togetherTrail").set({enabled: false});
  assert.equal((await exportAdventurePage(db!, "member", {...base, kind: "commitments"})).records.length, 1);
  assert.equal((await exportAdventurePage(db!, "owner", {...base, kind: "participant"})).records.length, 0);
  await assert.rejects(exportAdventurePage(db!, "owner", {...base, kind: "credits", segmentId}));
  await assert.rejects(exportAdventurePage(db!, "outsider", {...base, kind: "segments"}));
  await assert.rejects(exportAdventurePage(db!, "member", {...base, kind: "syncTickets"}));
  const batch = db!.batch();
  for (let i = 0; i < 51; i++) {
    batch.set(db!.doc(`groups/${base.groupId}/adventures/${base.adventureId}/participants/member/segments/${segmentId}/credits/${String(i).padStart(3, "0")}`), {creditedSteps: i});
  }
  await batch.commit();
  const first = await exportAdventurePage(db!, "member", {...base, kind: "credits", segmentId});
  assert.equal(first.records.length, 50);
  const last = await exportAdventurePage(db!, "member", {...base, kind: "credits", segmentId, after: first.next});
  assert.equal(last.records.length, 1);
  assert.equal(last.next, null);
  const person = db!.doc(`groups/${base.groupId}/adventures/${base.adventureId}/participants/member`);
  const epoch = (await person.get()).get("membershipEpoch");
  await db!.doc(`memberships/member_${base.groupId}`).delete();
  await eraseDepartedAdventureData(db!, base.groupId, "member", epoch);
  assert.equal((await person.collection("commitments").get()).empty, true);
});

test("shared calendar survives phone timezone changes and later group calendar edits", {skip: !enabled}, async () => {
  const base = await fixture();
  await db!.doc(`groups/${base.groupId}`).update({planningTimeZone: "Pacific/Auckland", weekStartDay: "SUNDAY"});
  const view = await readAdventure(db!, "member", base, () => noon);
  assert.equal(view.nextWeek.timeZone, "UTC");
  assert.equal(view.nextWeek.startDay, "MONDAY");
});

test("entry confirms the same group coverage for organizer and member and distinguishes expiry", {skip: !enabled}, async () => {
  const base = await fixture();
  for (const uid of ["owner", "member"]) {
    const entry = await readAdventureEntry(db!, uid, base.groupId, () => noon);
    assert.equal(entry.covered, true);
    assert.equal(entry.organizer, uid === "owner");
    assert.equal(entry.activeAdventureId, base.adventureId);
    assert.equal((await readAdventureEntry(db!, uid, base.groupId, () => noon + 2)).covered, false);
  }
  await assert.rejects(readAdventureEntry(db!, "outsider", base.groupId, () => noon));
});

test("missed UTC days recover once and cannot cross pause or the lookback boundary", {skip: !enabled}, async () => {
  const base = await fixture();
  const day = 86400000;
  const midnight = noon - day / 2;
  const later = noon + 3 * day;
  const input = {...base, join: false, windowStartMillis: midnight + day};
  const ticket = await prepareAdventureSync(db!, "member", input, () => later);
  assert.equal(ticket.observedThroughMillis, midnight + 2 * day);
  const observation = {...base, token: ticket.token, steps: 4000};
  await submitAdventureSync(db!, "member", observation, () => later + 10);
  await submitAdventureSync(db!, "member", observation, () => later + 20);
  const adventure = db!.doc(`groups/${base.groupId}/adventures/${base.adventureId}`);
  assert.equal((await adventure.get()).get("creditedSteps"), 4000);
  await assert.rejects(prepareAdventureSync(db!, "member", {...input, windowStartMillis: midnight - 6 * day}, () => later + 3000));
  const pending = await prepareAdventureSync(db!, "member", {...input, windowStartMillis: midnight + 2 * day}, () => later + 3000);
  await pauseAdventureContribution(db!, "member", base, () => later + 3010);
  await assert.rejects(submitAdventureSync(db!, "member", {...base, token: pending.token, steps: 6000}, () => later + 3020));
  assert.equal((await adventure.get()).get("creditedSteps"), 4000);
});
