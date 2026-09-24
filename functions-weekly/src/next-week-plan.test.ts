import assert from "node:assert/strict";
import test from "node:test";

import {
  countNextWeekResponses,
  scheduledTargetForWeek,
} from "./next-week-plan.js";

const valid = {
  schemaVersion: 1,
  groupId: "8ddc357f-dd4e-4d5c-9571-49c0d9201f66",
  weekStart: "2026-09-14",
  targetSteps: 70_000,
  choice: "repeat",
  status: "scheduled",
};

test("day-one plans apply at their intended rollover without a historical week", () => {
  const dayOne = {...valid, schemaVersion: 2, sourceWeekStart: null,
    basisTotalSteps: null, basisTargetSteps: 70_000};
  assert.equal(scheduledTargetForWeek(dayOne, valid.groupId, valid.weekStart), 70_000);
  assert.equal(scheduledTargetForWeek(dayOne, valid.groupId, "2026-09-21"), null);
  assert.equal(scheduledTargetForWeek({...dayOne, status: "applied"}, valid.groupId, valid.weekStart), null);
  assert.equal(scheduledTargetForWeek({...dayOne, schemaVersion: 3}, valid.groupId, valid.weekStart), null);
  assert.equal(scheduledTargetForWeek({...dayOne, basisTotalSteps: 0}, valid.groupId, valid.weekStart), null);
  assert.equal(scheduledTargetForWeek({...dayOne, basisTargetSteps: 0}, valid.groupId, valid.weekStart), null);
});

test("accepts only the exact scheduled group week", () => {
  assert.equal(
    scheduledTargetForWeek(valid, valid.groupId, valid.weekStart),
    70_000,
  );
  assert.equal(
    scheduledTargetForWeek(valid, valid.groupId, "2026-09-21"),
    null,
  );
  assert.equal(
    scheduledTargetForWeek({...valid, groupId: "other"}, valid.groupId, valid.weekStart),
    null,
  );
});

test("rejects applied, malformed and out-of-range plans", () => {
  assert.equal(scheduledTargetForWeek({...valid, status: "applied"}, valid.groupId, valid.weekStart), null);
  assert.equal(scheduledTargetForWeek({...valid, choice: "automatic"}, valid.groupId, valid.weekStart), null);
  assert.equal(scheduledTargetForWeek({...valid, targetSteps: 9_999}, valid.groupId, valid.weekStart), null);
  assert.equal(scheduledTargetForWeek({...valid, targetSteps: 70_000.5}, valid.groupId, valid.weekStart), null);
});

test("availability aggregation ignores malformed answers and is repeatable", () => {
  const responses = [
    {response: "in"},
    {response: "in"},
    {response: "prefer_gentler"},
    {response: "private free text"},
  ];
  const expected = {inCount: 2, preferGentlerCount: 1, restingCount: 0};

  assert.deepEqual(countNextWeekResponses(responses), expected);
  assert.deepEqual(countNextWeekResponses(responses), expected);
});
