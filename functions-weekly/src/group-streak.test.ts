import assert from "node:assert/strict";
import test from "node:test";

import {
  GOAL_WEEKS_PER_SHIELD,
  MAX_SHIELDS,
  hasActivePaidAccess,
  hasActivePreview,
  needsStreakHistory,
  nextGroupStreak,
} from "./group-streak.js";

const FREE = {hasPremium: false, history: []};

test("a met week extends a streak that ends the week before", () => {
  const outcome = nextGroupStreak({
    ...FREE,
    weekStart: "2026-09-21",
    goalHit: true,
    stored: {streakWeeks: 5, streakThroughWeek: "2026-09-14"},
  });
  assert.deepEqual(outcome, {streakWeeks: 6, streakThroughWeek: "2026-09-21", shielded: false, shields: undefined});
});

test("a missed week resets to zero and still moves the marker", () => {
  const outcome = nextGroupStreak({
    ...FREE,
    weekStart: "2026-09-21",
    goalHit: false,
    stored: {streakWeeks: 5, streakThroughWeek: "2026-09-14"},
  });
  assert.equal(outcome.streakWeeks, 0);
  assert.equal(outcome.streakThroughWeek, "2026-09-21");
  assert.equal(outcome.shielded, false);
});

test("a week never finalized in between breaks the streak", () => {
  // 2026-09-14 was never finalized: nobody synced, so it cannot have met the goal.
  const outcome = nextGroupStreak({
    ...FREE,
    weekStart: "2026-09-21",
    goalHit: true,
    stored: {streakWeeks: 5, streakThroughWeek: "2026-09-07"},
    history: [{weekStart: "2026-09-07", goalHit: true}],
  });
  assert.equal(outcome.streakWeeks, 1);
});

test("a group from before the field existed gets its real streak from history", () => {
  const outcome = nextGroupStreak({
    ...FREE,
    weekStart: "2026-09-21",
    goalHit: true,
    stored: {},
    history: [
      {weekStart: "2026-09-14", goalHit: true},
      {weekStart: "2026-09-07", goalHit: true},
      {weekStart: "2026-08-31", goalHit: false},
      {weekStart: "2026-08-24", goalHit: true},
    ],
  });
  assert.equal(outcome.streakWeeks, 3);
});

test("the rebuild crosses a year boundary, and a shielded week continues without counting", () => {
  const outcome = nextGroupStreak({
    ...FREE,
    weekStart: "2027-01-04",
    goalHit: true,
    stored: {},
    history: [
      {weekStart: "2026-12-28", goalHit: true},
      {weekStart: "2026-12-21", goalHit: false, shielded: true},
      {weekStart: "2026-12-14", goalHit: true},
    ],
  });
  // Two met weeks behind the shielded one, plus this one.
  assert.equal(outcome.streakWeeks, 3);
});

test("a corrupt stored count is not trusted", () => {
  const outcome = nextGroupStreak({
    ...FREE,
    weekStart: "2026-09-21",
    goalHit: true,
    stored: {streakWeeks: "lots", streakThroughWeek: "2026-09-14"},
    history: [{weekStart: "2026-09-14", goalHit: true}],
  });
  assert.equal(outcome.streakWeeks, 2);
});

test("history is only needed when the stored streak does not end the week before", () => {
  assert.equal(needsStreakHistory("2026-09-21", "2026-09-14"), false);
  assert.equal(needsStreakHistory("2026-09-21", "2026-09-07"), true);
  assert.equal(needsStreakHistory("2026-09-21", undefined), true);
});

// ── Shields ──────────────────────────────────────────────────────────────────

test("a free group never gets shields", () => {
  const outcome = nextGroupStreak({
    ...FREE,
    weekStart: "2026-09-21",
    goalHit: false,
    stored: {streakWeeks: 5, streakThroughWeek: "2026-09-14"},
  });
  assert.equal(outcome.shields, undefined);
  assert.equal(outcome.streakWeeks, 0);
});

test("the first Premium week starts one shield, and a miss spends it and holds the streak", () => {
  const outcome = nextGroupStreak({
    weekStart: "2026-09-21",
    goalHit: false,
    hasPremium: true,
    stored: {streakWeeks: 5, streakThroughWeek: "2026-09-14"},
    history: [],
  });
  assert.deepEqual(outcome, {
    streakWeeks: 5,
    streakThroughWeek: "2026-09-21",
    shielded: true,
    shields: {streakShields: 0, shieldProgress: 0, lastShieldedWeek: "2026-09-21"},
  });
});

test("with no shield left, a Premium miss resets like any other", () => {
  const outcome = nextGroupStreak({
    weekStart: "2026-09-28",
    goalHit: false,
    hasPremium: true,
    stored: {streakWeeks: 5, streakThroughWeek: "2026-09-21", shieldsStarted: true, streakShields: 0, shieldProgress: 2},
    history: [],
  });
  assert.equal(outcome.streakWeeks, 0);
  assert.equal(outcome.shielded, false);
  assert.deepEqual(outcome.shields, {streakShields: 0, shieldProgress: 2});
});

test("a shield is never spent on a streak of zero", () => {
  const outcome = nextGroupStreak({
    weekStart: "2026-09-28",
    goalHit: false,
    hasPremium: true,
    stored: {streakWeeks: 0, streakThroughWeek: "2026-09-21", shieldsStarted: true, streakShields: 1, shieldProgress: 0},
    history: [],
  });
  assert.equal(outcome.shielded, false);
  assert.equal(outcome.shields?.streakShields, 1);
});

test("goal weeks while Premium earn a shield, up to the cap", () => {
  const earning = nextGroupStreak({
    weekStart: "2026-09-28",
    goalHit: true,
    hasPremium: true,
    stored: {
      streakWeeks: 7, streakThroughWeek: "2026-09-21", shieldsStarted: true,
      streakShields: 1, shieldProgress: GOAL_WEEKS_PER_SHIELD - 1,
    },
    history: [],
  });
  assert.deepEqual(earning.shields, {streakShields: 2, shieldProgress: 0});

  const capped = nextGroupStreak({
    weekStart: "2026-09-28",
    goalHit: true,
    hasPremium: true,
    stored: {
      streakWeeks: 7, streakThroughWeek: "2026-09-21", shieldsStarted: true,
      streakShields: MAX_SHIELDS, shieldProgress: GOAL_WEEKS_PER_SHIELD - 1,
    },
    history: [],
  });
  assert.deepEqual(capped.shields, {streakShields: MAX_SHIELDS, shieldProgress: 0});
});

test("banked shields survive a lapse but do nothing until Premium is back", () => {
  const outcome = nextGroupStreak({
    weekStart: "2026-09-28",
    goalHit: false,
    hasPremium: false,
    stored: {streakWeeks: 4, streakThroughWeek: "2026-09-21", shieldsStarted: true, streakShields: 2, shieldProgress: 1},
    history: [],
  });
  assert.equal(outcome.shielded, false);
  assert.equal(outcome.streakWeeks, 0);
  assert.deepEqual(outcome.shields, {streakShields: 2, shieldProgress: 1});
});

test("only a live circle_v1 lease or preview counts as Premium", () => {
  const now = Date.parse("2026-09-28T00:00:00Z");
  assert.equal(hasActivePaidAccess({isPremium: true, featureSet: ["circle_v1"], leaseValidUntilMs: now + 1}, now), true);
  assert.equal(hasActivePaidAccess({isPremium: true, featureSet: ["circle_v1"], leaseValidUntilMs: now}, now), false);
  assert.equal(hasActivePaidAccess({isPremium: true, featureSet: ["other"], leaseValidUntilMs: now + 1}, now), false);
  assert.equal(hasActivePaidAccess(undefined, now), false);
  assert.equal(hasActivePreview({featureSet: ["circle_v1"], startedAtMs: now - 1, validUntilMs: now + 1}, now), true);
  assert.equal(hasActivePreview({featureSet: ["circle_v1"], startedAtMs: now - 2, validUntilMs: now - 1}, now), false);
});

test("a group created by the app with streakShields 0 still gets its starting shield", () => {
  const outcome = nextGroupStreak({
    weekStart: "2026-09-21",
    goalHit: true,
    hasPremium: true,
    stored: {streakWeeks: 0, streakThroughWeek: "2026-09-14", streakShields: 0, lastShieldedWeek: ""},
    history: [],
  });
  assert.deepEqual(outcome.shields, {streakShields: 1, shieldProgress: 1});
});
