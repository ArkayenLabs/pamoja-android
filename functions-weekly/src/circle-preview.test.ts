import assert from "node:assert/strict";
import test from "node:test";

import {
  CIRCLE_PREVIEW_DURATION_MS,
  buildCirclePreviewSeed,
} from "./circle-preview.js";
import type {GroupWeekSummary} from "./weekly-summary.js";

test("two active members earn one shared 14 day preview seed", () => {
  const startedAtMillis = 1_800_000_000_000;
  const preview = buildCirclePreviewSeed(summary(), startedAtMillis);

  assert.deepEqual(preview, {
    schemaVersion: 1,
    groupId: GROUP_ID,
    featureSet: ["circle_v1"],
    eligibleWeekStart: "2026-08-17",
    startedAtMillis,
    validUntilMillis: startedAtMillis + CIRCLE_PREVIEW_DURATION_MS,
  });
  assert.equal("userId" in (preview ?? {}), false);
  assert.equal("contributions" in (preview ?? {}), false);
});

test("a missed goal still earns the helpful preview", () => {
  const preview = buildCirclePreviewSeed(
    summary({totalSteps: 40_000, targetSteps: 70_000, goalHit: false}),
    1_800_000_000_000,
  );

  assert.notEqual(preview, null);
});

test("one active member does not start a group preview", () => {
  assert.equal(
    buildCirclePreviewSeed(
      summary({memberCount: 2, activeMemberCount: 1}),
      1_800_000_000_000,
    ),
    null,
  );
});

test("a solo group does not start a preview", () => {
  assert.equal(
    buildCirclePreviewSeed(
      summary({memberCount: 1, activeMemberCount: 1}),
      1_800_000_000_000,
    ),
    null,
  );
});

test("invalid server time cannot mint a preview", () => {
  assert.equal(buildCirclePreviewSeed(summary(), 0), null);
  assert.equal(buildCirclePreviewSeed(summary(), Number.NaN), null);
  assert.equal(buildCirclePreviewSeed(summary(), Number.MAX_VALUE), null);
});

function summary(
  overrides: Partial<GroupWeekSummary> = {},
): GroupWeekSummary {
  return {
    schemaVersion: 1,
    groupId: GROUP_ID,
    weekStart: "2026-08-17",
    weekEnd: "2026-08-23",
    targetSteps: 70_000,
    totalSteps: 84_000,
    goalHit: true,
    memberCount: 2,
    activeMemberCount: 2,
    contributions: [
      {userId: "member-a", stepCount: 42_000},
      {userId: "member-b", stepCount: 42_000},
    ],
    ...overrides,
  };
}

const GROUP_ID = "6413795d-42d0-4f2b-80df-f017a9d32817";
