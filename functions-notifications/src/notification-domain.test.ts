import assert from "node:assert/strict";
import test from "node:test";

import {
  buildGoalReachedPush,
  buildMemberJoinedPush,
  isValidAppVersion,
  isValidGroupId,
  isValidInstallationId,
} from "./notification-domain.js";

const GROUP_ID = "6413795d-42d0-4f2b-80df-f017a9d32817";

test("installation registrations accept only bounded Firebase-safe IDs", () => {
  assert.equal(isValidInstallationId("cT9_fid-123"), true);
  assert.equal(isValidInstallationId("bad/fid"), false);
  assert.equal(isValidInstallationId(""), false);
  assert.equal(isValidInstallationId("a".repeat(201)), false);
  assert.equal(isValidAppVersion("1.0.0"), true);
  assert.equal(isValidAppVersion(""), false);
  assert.equal(isValidGroupId(GROUP_ID), true);
  assert.equal(isValidGroupId("../../groups/other"), false);
});

test("join push uses safe short copy and a stable dedupe identifier", () => {
  const input = {
    membershipId: `member_${GROUP_ID}`,
    joinedAt: 1_800_000_000_000,
    displayName: "Alexandria-With-A-Very-Long-Name Example",
    groupId: GROUP_ID,
    groupName: "Weekend Walkers With A Very Long Name",
    memberCount: 4,
  };
  const first = buildMemberJoinedPush(input);
  const second = buildMemberJoinedPush(input);

  assert.deepEqual(first, second);
  assert.equal(first.category, "GROUP_ACTIVITY");
  assert.equal(first.title, "Alexandria-With-A- actually joined.");
  assert.equal(
    first.body,
    "Weekend Walkers Wi is now 4 strong. Try to look normal.",
  );
  assert.match(first.eventId, /^member-joined-[a-f0-9]{32}$/);
});

test("first teammate joining gets a warmer two-person message", () => {
  const push = buildMemberJoinedPush({
    membershipId: `member_${GROUP_ID}`,
    joinedAt: 1_800_000_000_000,
    displayName: "Priya Sharma",
    groupId: GROUP_ID,
    groupName: "bama",
    memberCount: 2,
  });

  assert.equal(push.title, "Now it’s a group.");
  assert.equal(push.body, "Priya joined you. Two people, one weekly goal.");
});

test("goal push is collective and dedupes by group week", () => {
  const push = buildGoalReachedPush({
    groupId: GROUP_ID,
    weekStart: "2026-08-31",
    groupName: "bama",
    finalSteps: 42_252,
  });

  assert.equal(push.category, "ACHIEVEMENT");
  assert.equal(push.title, "bama did it.");
  assert.equal(push.body, "42,252 steps together. Goal met. Show-offs.");
  assert.match(push.eventId, /^goal-reached-[a-f0-9]{32}$/);
});

test("notification copy never contains an em dash", () => {
  const joined = buildMemberJoinedPush({
    membershipId: `member_${GROUP_ID}`,
    joinedAt: 1_800_000_000_000,
    displayName: "Priya",
    groupId: GROUP_ID,
    groupName: "bama",
    memberCount: 3,
  });
  const completed = buildGoalReachedPush({
    groupId: GROUP_ID,
    weekStart: "2026-08-31",
    groupName: "bama",
    finalSteps: 42_252,
  });

  for (const push of [joined, completed]) {
    assert.equal(`${push.title}${push.body}`.includes("\u2014"), false);
  }
});
