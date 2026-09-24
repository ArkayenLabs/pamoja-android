import assert from "node:assert/strict";
import test from "node:test";

import {
  MAX_DAILY_STEPS,
  MAX_GROUP_TARGET_STEPS,
  buildGroupWeekSummary,
  datesInWeek,
  isNormalWeekRollover,
  isValidGroupId,
  redactUserFromContributions,
} from "./weekly-summary.js";

test("group IDs accept only the UUID format used by Pamoja groups", () => {
  assert.equal(
    isValidGroupId("6413795d-42d0-4f2b-80df-f017a9d32817"),
    true,
  );
  assert.equal(isValidGroupId("not-a-group"), false);
});

test("normal rollover accepts whole weeks and rejects malformed transitions", () => {
  assert.equal(isNormalWeekRollover("2026-08-17", "2026-08-24"), true);
  assert.equal(isNormalWeekRollover("2026-08-10", "2026-08-24"), true);
  assert.equal(isNormalWeekRollover("2026-08-24", "2026-08-17"), false);
  assert.equal(isNormalWeekRollover("2026-08-17", "2026-08-23"), false);
  assert.equal(isNormalWeekRollover("2026-02-30", "2026-03-09"), false);
  assert.equal(isNormalWeekRollover("not-a-date", "2026-08-24"), false);
});

test("datesInWeek stays correct across month and year boundaries", () => {
  assert.deepEqual(datesInWeek("2026-12-28"), [
    "2026-12-28",
    "2026-12-29",
    "2026-12-30",
    "2026-12-31",
    "2027-01-01",
    "2027-01-02",
    "2027-01-03",
  ]);
});

test("summary totals seven daily documents per member without storing PII", () => {
  const summary = buildGroupWeekSummary({
    groupId: "6413795d-42d0-4f2b-80df-f017a9d32817",
    weekStart: "2026-08-17",
    targetSteps: 70_000,
    memberUserIds: ["member-b", "member-a", "member-a"],
    stepCountsByDocumentId: {
      "member-a_2026-08-17": 10_000,
      "member-a_2026-08-18": 20_000,
      "member-b_2026-08-17": 40_000,
    },
  });

  assert.deepEqual(summary, {
    schemaVersion: 1,
    groupId: "6413795d-42d0-4f2b-80df-f017a9d32817",
    weekStart: "2026-08-17",
    weekEnd: "2026-08-23",
    targetSteps: 70_000,
    totalSteps: 70_000,
    goalHit: true,
    memberCount: 2,
    activeMemberCount: 2,
    contributions: [
      {userId: "member-a", stepCount: 30_000},
      {userId: "member-b", stepCount: 40_000},
    ],
  });
  assert.equal("displayName" in summary.contributions[0], false);
  assert.equal("photoUrl" in summary.contributions[0], false);
});

test("summary treats missing or malformed steps safely and enforces daily cap", () => {
  const summary = buildGroupWeekSummary({
    groupId: "6413795d-42d0-4f2b-80df-f017a9d32817",
    weekStart: "2026-08-17",
    targetSteps: "70000",
    memberUserIds: ["active", "inactive", "bad/user"],
    stepCountsByDocumentId: {
      "active_2026-08-17": MAX_DAILY_STEPS + 1,
      "active_2026-08-18": -50,
      "active_2026-08-19": 12.5,
    },
  });

  assert.equal(summary.totalSteps, MAX_DAILY_STEPS);
  assert.equal(summary.targetSteps, 0);
  assert.equal(summary.goalHit, false);
  assert.equal(summary.memberCount, 2);
  assert.equal(summary.activeMemberCount, 1);
});

test("summary bounds a target to the same maximum accepted by group rules", () => {
  const summary = buildGroupWeekSummary({
    groupId: "6413795d-42d0-4f2b-80df-f017a9d32817",
    weekStart: "2026-08-17",
    targetSteps: MAX_GROUP_TARGET_STEPS + 1,
    memberUserIds: [],
    stepCountsByDocumentId: {},
  });

  assert.equal(summary.targetSteps, MAX_GROUP_TARGET_STEPS);
});

test("departed members lose their direct identifier without changing other totals", () => {
  const contributions = [
    {userId: "departed", stepCount: 15_000},
    {userId: "remaining", stepCount: 20_000},
  ];

  assert.deepEqual(redactUserFromContributions(contributions, "departed"), [
    {userId: "remaining", stepCount: 20_000},
  ]);
  assert.equal(contributions.length, 2);
});

test("redaction also removes malformed identifiers and bounds retained totals", () => {
  assert.deepEqual(redactUserFromContributions([
    {userId: "bad/user", stepCount: 100},
    {userId: "remaining", stepCount: 2_100_001},
    "not-an-object",
  ], "departed"), [
    {userId: "remaining", stepCount: 2_100_000},
  ]);
  assert.deepEqual(redactUserFromContributions("not-an-array", "departed"), []);
});
