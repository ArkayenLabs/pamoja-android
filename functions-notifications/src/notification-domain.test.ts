import assert from "node:assert/strict";
import test from "node:test";

import {
  buildFcmMessage,
  buildGoalReachedPush,
  inQuietHours,
  localClock,
  parseRegistration,
  buildSignalPush,
  parseSignal,
  walkedOn,
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
  // The group name gives way so the whole title fits the app's 40.
  assert.equal(first.title, "Alexandria-With-A- joined Weekend Walker");
  assert.ok(first.title.length <= 40);
  assert.equal(first.body, "4 of you now. Say hi on your next walk.");
  assert.deepEqual(first.titleLoc, {key: "push_joined_title", args: ["Alexandria-With-A-", "Weekend Walker"]});
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

  assert.equal(push.title, "Priya joined bama");
  assert.equal(push.body, "There are two of you now. Walks are better with company.");
});

test("goal push is collective and dedupes by group week", () => {
  const push = buildGoalReachedPush({
    groupId: GROUP_ID,
    weekStart: "2026-08-31",
    groupName: "bama",
    finalSteps: 42_252,
  });

  assert.equal(push.category, "ACHIEVEMENT");
  assert.equal(push.title, "bama made it");
  assert.equal(push.body, "42,252 steps this week, together. Take a bow.");
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

test("signals parse only the two kinds with real ids and an ISO date", () => {
  const good = {kind: "cheer", fromUserId: "alice", toUserId: "bob", date: "2026-09-28"};
  assert.deepEqual(parseSignal(good), good);
  assert.equal(parseSignal({...good, kind: "poke"}), null);
  assert.equal(parseSignal({...good, toUserId: "bob/../x"}), null);
  assert.equal(parseSignal({...good, date: "yesterday"}), null);
  assert.equal(parseSignal(null), null);
});

test("a nudge is stale once the recipient's own figures show a walk that day", () => {
  assert.equal(walkedOn({todayDate: "2026-09-28", todaySteps: 1_000}, "2026-09-28"), true);
  assert.equal(walkedOn({todayDate: "2026-09-28", todaySteps: 999}, "2026-09-28"), false);
  assert.equal(walkedOn({todayDate: "2026-09-27", todaySteps: 9_000}, "2026-09-28"), false);
  assert.equal(walkedOn(undefined, "2026-09-28"), false);
});

test("signal pushes fit the app's limits and share one event per recipient and day", () => {
  const signal = {kind: "cheer" as const, fromUserId: "alice", toUserId: "bob", date: "2026-09-28"};
  const fromAlice = buildSignalPush({
    signal, groupId: GROUP_ID, senderName: "Alexandria-With-A-Very-Long-Name", groupName: "Sharma Family",
  });
  const fromCarol = buildSignalPush({
    signal: {...signal, fromUserId: "carol"}, groupId: GROUP_ID, senderName: "Carol", groupName: "Sharma Family",
  });
  const nudge = buildSignalPush({
    signal: {...signal, kind: "nudge"}, groupId: GROUP_ID, senderName: "Carol", groupName: "Sharma Family",
  });

  assert.equal(fromAlice.category, "GROUP_ACTIVITY");
  assert.ok(fromAlice.title.length <= 40, fromAlice.title);
  assert.ok(nudge.body.length <= 120, nudge.body);
  assert.equal(fromAlice.eventId, fromCarol.eventId);
  assert.notEqual(fromAlice.eventId, nudge.eventId);
  assert.equal(nudge.title, "Carol: walk today?");
  assert.equal(nudge.body, "Any walk today counts for Sharma Family.");
  assert.equal(fromCarol.body, "For your walk today. Send one back?");
});

const REGISTRATION = {
  installationId: "fid-1",
  userId: "bob",
  delivery: 2,
  timeZone: "Asia/Kolkata",
  quietStartMinute: 22 * 60,
  quietEndMinute: 8 * 60,
};
// 2026-10-05 12:30 UTC is 18:00 in Kolkata; 18:30 UTC is midnight there.
const EVENING_IST = Date.UTC(2026, 9, 5, 12, 30);
const MIDNIGHT_IST = Date.UTC(2026, 9, 5, 18, 30);
const OPTIONS = {packageName: "com.arkayenlabs.pamoja", ttlMs: 1_000};

function cheer() {
  return buildSignalPush({
    signal: {kind: "cheer", fromUserId: "alice", toUserId: "bob", date: "2026-10-05"},
    groupId: GROUP_ID,
    senderName: "Alice",
    groupName: "bama",
  });
}

test("a build that registered for it gets a tray message, with the data kept for older parsers", () => {
  const message = buildFcmMessage(REGISTRATION, cheer(), EVENING_IST, OPTIONS);

  assert.equal(message.data.title, "High five from Alice");
  assert.equal(message.data.recipientUid, "bob");
  assert.equal(message.data.titleLocKey, "push_cheer_title");
  assert.equal(message.data.titleLocArg1, "Alice");
  assert.equal(message.data.bodyLocArg1, "bama");
  const tray = message.android.notification;
  assert.ok(tray);
  assert.equal(tray.channelId, "pamoja_group_activity");
  assert.equal(tray.tag, `p1:${GROUP_ID}:${message.data.eventId}`);
  assert.equal(tray.titleLocKey, "push_cheer_title");
  assert.deepEqual(tray.titleLocArgs, ["Alice"]);
  assert.equal(tray.visibility, "private");
});

test("older builds and quiet hours stay data-only, but a goal reached still goes to the tray", () => {
  const older = {...REGISTRATION, delivery: 1, timeZone: null, quietStartMinute: null, quietEndMinute: null};
  assert.equal(buildFcmMessage(older, cheer(), EVENING_IST, OPTIONS).android.notification, undefined);
  assert.equal(buildFcmMessage(REGISTRATION, cheer(), MIDNIGHT_IST, OPTIONS).android.notification, undefined);

  const goal = buildGoalReachedPush({groupId: GROUP_ID, weekStart: "2026-10-05", groupName: "bama", finalSteps: 70_000});
  assert.equal(buildFcmMessage(REGISTRATION, goal, MIDNIGHT_IST, OPTIONS).android.notification?.channelId,
    "pamoja_achievements");
});

test("registrations and clocks are read defensively", () => {
  assert.equal(parseRegistration({installationId: "bad/id", userId: "bob"}), null);
  const legacy = parseRegistration({installationId: "fid-1", userId: "bob", timeZone: "Mars/Olympus"});
  assert.equal(legacy?.delivery, 1);
  assert.equal(legacy?.timeZone, null);
  assert.deepEqual(localClock(EVENING_IST, "Asia/Kolkata"), {date: "2026-10-05", minuteOfDay: 18 * 60});
  assert.equal(inQuietHours(REGISTRATION, MIDNIGHT_IST), true);
  assert.equal(inQuietHours(REGISTRATION, EVENING_IST), false);
});

test("the first goal a group ever meets is told as its own moment", () => {
  const first = buildGoalReachedPush({
    groupId: GROUP_ID, weekStart: "2026-10-05", groupName: "bama", finalSteps: 70_000, first: true,
  });
  assert.equal(first.title, "First goal, done");
  assert.equal(first.body, "bama made it: 70,000 steps together.");
  const later = buildGoalReachedPush({groupId: GROUP_ID, weekStart: "2026-10-05", groupName: "bama", finalSteps: 70_000});
  assert.equal(later.title, "bama made it");
  // Same event either way, so a retry never sends both.
  assert.equal(first.eventId, later.eventId);
});
