import assert from "node:assert/strict";
import test from "node:test";
import {AdventureObservation, AdventureCredit, AdventureError, UTC_DAY_MILLIS,
  adventureWindow, authorizeAdventureStart, newAdventureProgress, joinAdventureSegment,
  closeAdventureSegment, reconcileAdventureCredit, applyAdventureDelta, adventurePace,
} from "./adventure-ledger.js";

const noon = Date.parse("2026-09-17T12:00:00Z");
const day = adventureWindow(noon).startMillis;
const source = (steps: number, revision: number, acceptedAtMillis = noon,
  windowStartMillis = day): AdventureObservation =>
  ({steps, revision, acceptedAtMillis, windowStartMillis, bindingId: "phoneA"});

test("joining excludes earlier steps; replay and out-of-order delivery do not inflate the group", () => {
  const member = joinAdventureSegment("segment1", source(5000, 1), noon);
  let credit: AdventureCredit | null = null;
  let group = newAdventureProgress(200000);
  for (const observation of [source(7000, 3), source(7000, 3), source(6000, 2)]) {
    const result = reconcileAdventureCredit(member, credit, observation, noon);
    credit = result.credit;
    group = applyAdventureDelta(group, result.delta, noon);
  }
  assert.equal(group.creditedSteps, 2000);
  assert.equal(credit?.revision, 3);
  assert.throws(() => reconcileAdventureCredit(member, credit, source(9000, 3), noon),
    (error: unknown) => error instanceof AdventureError && error.reason === "source_conflict");
});

test("corrections reconcile downward, without taking away an earned chapter or finish", () => {
  const member = joinAdventureSegment("segment1", source(5000, 1), noon);
  const first = reconcileAdventureCredit(member, null, source(6500, 2), noon);
  const completed = applyAdventureDelta(newAdventureProgress(1000), first.delta, noon);
  assert.equal(completed.earnedChapters, 5);
  const correction = reconcileAdventureCredit(member, first.credit, source(5200, 3), noon);
  const corrected = applyAdventureDelta(completed, correction.delta, noon + 1);
  assert.equal(corrected.creditedSteps, 200);
  assert.equal(corrected.earnedChapters, 5);
  assert.equal(corrected.completedAtMillis, noon);
  assert.throws(() => applyAdventureDelta(corrected, -201, noon));
});

test("the next UTC day counts independently, even when a device's local date differs", () => {
  const member = joinAdventureSegment("segment1", source(5000, 1), noon);
  const next = day + UTC_DAY_MILLIS;
  const update = source(800, 1, next + 1000, next);
  assert.equal(reconcileAdventureCredit(member, null, update, next + 1000).delta, 800);
  assert.deepEqual(adventureWindow(Date.parse("2026-09-18T01:00:00+14:00")),
    adventureWindow(Date.parse("2026-09-17T01:00:00-10:00")));
  assert.equal(adventureWindow(Date.parse("2026-11-01T12:00:00Z")).endMillis -
    adventureWindow(Date.parse("2026-11-01T12:00:00Z")).startMillis, UTC_DAY_MILLIS);
});

test("pause/leave freezes credit; rejoin excludes activity during the gap", () => {
  const member = joinAdventureSegment("segment1", source(5000, 1), noon);
  const first = reconcileAdventureCredit(member, null, source(6000, 2), noon);
  const closed = closeAdventureSegment(member, noon + 1);
  assert.equal(reconcileAdventureCredit(closed, first.credit, source(9000, 3, noon + 2), noon + 2).delta, 0);
  const resumed = joinAdventureSegment("segment2", source(9000, 3, noon + 2), noon + 2);
  const later = reconcileAdventureCredit(resumed, null, source(9500, 4, noon + 3), noon + 3);
  assert.equal(first.delta + later.delta, 1500);
  assert.throws(() => reconcileAdventureCredit(resumed, first.credit, source(9500, 4, noon + 3), noon + 3));
});

test("new device needs a new binding and baseline, not a maximum of both phones", () => {
  const member = joinAdventureSegment("segment1", source(5000, 1), noon);
  assert.throws(() => reconcileAdventureCredit(member, null,
    {...source(15000, 2), bindingId: "phoneB"}, noon),
  (error: unknown) => error instanceof AdventureError && error.reason === "binding_changed");
});

test("stale baselines, future windows, invalid counts and pre-join dates cannot earn credit", () => {
  assert.throws(() => joinAdventureSegment("segment1", source(5000, 1), noon + 60001));
  assert.throws(() => joinAdventureSegment("segment1", source(5000, 1, noon, day - UTC_DAY_MILLIS), noon));
  assert.throws(() => joinAdventureSegment("segment1", source(5000, 1, noon, day + UTC_DAY_MILLIS), noon));
  const member = joinAdventureSegment("segment1", source(5000, 1), noon);
  for (const count of [-1, 1.5, 200001, NaN, Infinity]) {
    assert.throws(() => reconcileAdventureCredit(member, null, source(count, 2), noon));
  }
  assert.equal(reconcileAdventureCredit(member, null, source(4000, 2, noon, day - UTC_DAY_MILLIS), noon).delta, 0);
});

test("rest changes forecast without changing the other member's commitment", () => {
  const commitments = [20000, 10000];
  assert.equal(adventurePace(120000, commitments).approximateWeeks, 4);
  assert.equal(adventurePace(120000, [commitments[0], 0]).approximateWeeks, 6);
  assert.deepEqual(commitments, [20000, 10000]);
  assert.equal(adventurePace(120000, [0, 0]).approximateWeeks, null);
  assert.equal(adventurePace(120000, [20000, null]).approximateWeeks, null);
  assert.equal(adventurePace(0, [0, null]).approximateWeeks, 0);
});

test("new starts require current organizer membership, rollout and unexpired paid access", () => {
  const context = {member: true, organizer: true, deletionPending: false,
    rolloutEnabled: true, premiumValidUntilMillis: noon + 1};
  authorizeAdventureStart(context, noon);
  for (const override of [{member: false}, {organizer: false}, {deletionPending: true},
    {rolloutEnabled: false}, {premiumValidUntilMillis: null}, {premiumValidUntilMillis: noon}]) {
    assert.throws(() => authorizeAdventureStart({...context, ...override}, noon));
  }
});

test("five thresholds use integer credit; a correction below a baseline cannot make negative credit", () => {
  let state = newAdventureProgress(1001);
  state = applyAdventureDelta(state, 200, noon);
  assert.equal(state.earnedChapters, 0);
  state = applyAdventureDelta(state, 1, noon);
  assert.equal(state.earnedChapters, 1);
  const member = joinAdventureSegment("segment1", source(5000, 1), noon);
  assert.equal(reconcileAdventureCredit(member, null, source(4000, 2), noon).delta, 0);
});
