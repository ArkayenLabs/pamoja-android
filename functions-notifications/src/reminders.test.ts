import assert from "node:assert/strict";
import test from "node:test";

import {
  EVENING_HOUR,
  QUIET_AFTER,
  RECAP_HOUR,
  ReminderCandidate,
  ReminderGroup,
  applyBackoff,
  daysLeftIn,
  planReminder,
  weekStartOf,
} from "./reminders.js";

const GROUP_ID = "6413795d-42d0-4f2b-80df-f017a9d32817";
// 2026-10-05 is a Monday.
const MONDAY = "2026-10-05";
const SATURDAY = "2026-10-10";
const SUNDAY = "2026-10-11";
const DAY = 24 * 60 * 60 * 1_000;
const NOW = Date.UTC(2026, 9, 5, 12, 30);

function group(overrides: Partial<ReminderGroup> = {}): ReminderGroup {
  return {
    groupId: GROUP_ID,
    name: "bama",
    weeklyTarget: 70_000,
    weeklySteps: 30_000,
    weekStart: MONDAY,
    weekStartDay: "MONDAY",
    planningTimeZone: "",
    memberCount: 3,
    createdAtMs: NOW - 60 * DAY,
    streakWeeks: 0,
    streakThroughWeek: "",
    lastShieldedWeek: "",
    previousWeekTotal: null,
    previousWeekEveryDay: [],
    ...overrides,
  };
}

function candidate(
  groupOverrides: Partial<ReminderGroup> = {},
  membership: Partial<ReminderCandidate["membership"]> = {},
): ReminderCandidate {
  return {
    group: group(groupOverrides),
    membership: {weeklySteps: 10_000, weekStart: MONDAY, todaySteps: 0, todayDate: "", ...membership},
  };
}

function plan(
  today: string,
  localHour: number,
  candidates: ReminderCandidate[],
  sent: Record<string, string> = {},
  nowMs = NOW,
) {
  return planReminder({
    userId: "bob",
    localHour,
    phoneToday: today,
    nowMs,
    localDateIn: () => today,
    candidates,
    sent,
  });
}

test("the week maths matches the app's WeekWindow", () => {
  assert.equal(weekStartOf("MONDAY", SUNDAY), MONDAY);
  assert.equal(weekStartOf("SUNDAY", SUNDAY), SUNDAY);
  assert.equal(weekStartOf("", "2026-10-07"), MONDAY);
  assert.equal(daysLeftIn("MONDAY", MONDAY), 7);
  assert.equal(daysLeftIn("MONDAY", SUNDAY), 1);
  assert.equal(daysLeftIn("SATURDAY", SATURDAY), 7);
});

test("nothing is planned outside the two hours", () => {
  assert.equal(plan(SUNDAY, 14, [candidate({weeklySteps: 65_000})]), null);
});

test("the last day, close to the goal, says what is left and each person's share", () => {
  const reminder = plan(SUNDAY, EVENING_HOUR, [candidate({weeklySteps: 61_000})]);

  assert.equal(reminder?.kind, "last-day");
  assert.equal(reminder?.push.title, "Last day: 9,000 steps to go");
  assert.equal(reminder?.push.body, "About 3,000 each. An evening walk would do it.");
  assert.equal(reminder?.push.category, "REMINDER");
});

test("a standing streak turns the same moment into the streak line", () => {
  const reminder = plan(SATURDAY, EVENING_HOUR, [candidate({
    weeklySteps: 60_000,
    streakWeeks: 4,
    streakThroughWeek: "2026-09-28",
  })]);

  assert.equal(reminder?.kind, "streak");
  assert.equal(reminder?.push.title, "4 weeks in a row");
  assert.equal(reminder?.push.body, "10,000 steps keeps bama going this week.");
});

test("a quieter member hears that their walk counts, at most once in six days", () => {
  const quiet = candidate({weeklySteps: 30_000}, {weeklySteps: 2_000});
  const thursday = "2026-10-08";

  const first = plan(thursday, EVENING_HOUR, [quiet]);
  assert.equal(first?.kind, "walk-counts");
  assert.equal(first?.push.body, "bama is 40,000 steps from the goal.");
  assert.equal(plan(thursday, EVENING_HOUR, [quiet], {"walk-counts": "2026-10-05"}), null);
});

test("nobody who has walked today is reminded, and the copy never names a rank", () => {
  const walked = candidate({weeklySteps: 30_000}, {weeklySteps: 2_000, todaySteps: 1_500, todayDate: "2026-10-08"});
  assert.equal(plan("2026-10-08", EVENING_HOUR, [walked]), null);
});

test("an empty group hears about its invite on a few days only", () => {
  const solo = (ageDays: number) => candidate({memberCount: 1, weeklySteps: 0, createdAtMs: NOW - ageDays * DAY - 1_000});

  assert.equal(plan("2026-10-07", EVENING_HOUR, [solo(1)])?.push.title, "bama needs its people");
  assert.equal(plan("2026-10-07", EVENING_HOUR, [solo(2)]), null);
  assert.equal(plan("2026-10-07", EVENING_HOUR, [solo(3)])?.kind, "solo");
});

test("the recap opens the week with last week's total, or only the goal when there is none", () => {
  const withTotal = plan(MONDAY, RECAP_HOUR, [candidate({previousWeekTotal: 64_210})]);
  assert.equal(withTotal?.kind, "recap");
  assert.equal(withTotal?.push.title, "New week for bama");
  assert.equal(withTotal?.push.body, "Last week: 64,210 steps. This week's goal: 70,000.");
  assert.equal(withTotal?.push.category, "RECAP");

  const quietWeek = plan(MONDAY, RECAP_HOUR, [candidate({previousWeekTotal: 0, weekStart: "2026-09-28", weeklySteps: 0})]);
  assert.equal(quietWeek?.push.body, "This week's goal: 70,000 steps.");

  assert.equal(plan("2026-10-06", RECAP_HOUR, [candidate()]), null);
});

test("a shield used last week is told instead of the recap", () => {
  const reminder = plan(MONDAY, RECAP_HOUR, [candidate({lastShieldedWeek: "2026-09-28"})]);

  assert.equal(reminder?.kind, "shield");
  assert.equal(reminder?.push.title, "A shield kept your streak");
  assert.equal(reminder?.push.category, "ACHIEVEMENT");
});

test("across groups the most useful reminder wins, and each person-day has one event", () => {
  const close = candidate({groupId: "1d0c7a52-6d0e-4d3b-9f6e-0a4a7b3c2d1e", weeklySteps: 61_000});
  const quiet = candidate({}, {weeklySteps: 1_000});

  const first = plan(SUNDAY, EVENING_HOUR, [quiet, close]);
  const again = plan(SUNDAY, EVENING_HOUR, [quiet, close]);
  assert.equal(first?.kind, "last-day");
  assert.equal(first?.push.eventId, again?.push.eventId);
});

test("after eight unopened reminders, one honest line, then silence until the app is opened", () => {
  const reminder = plan(SUNDAY, EVENING_HOUR, [candidate({weeklySteps: 61_000})]);
  const today = {userId: "bob", today: SUNDAY};

  assert.equal(applyBackoff(reminder, QUIET_AFTER - 1, true, today), reminder);
  const last = applyBackoff(reminder, QUIET_AFTER, true, today);
  assert.equal(last?.kind, "going-quiet");
  assert.equal(last?.push.title, "We'll go quiet for now");
  assert.equal(last?.push.body, "Your group's still here when you want it.");
  assert.equal(last?.push.groupId, GROUP_ID);
  assert.equal(applyBackoff(reminder, QUIET_AFTER + 1, true, today), null);
  assert.equal(applyBackoff(null, QUIET_AFTER, true, today), null);
});

test("a build that cannot report opens is never silenced", () => {
  const reminder = plan(SUNDAY, EVENING_HOUR, [candidate({weeklySteps: 61_000})]);
  assert.equal(applyBackoff(reminder, 50, false, {userId: "bob", today: SUNDAY}), reminder);
});

const LAST_MONDAY = "2026-09-28";

test("four and ten weeks in a row are told as a milestone, other lengths are not", () => {
  const four = plan(MONDAY, RECAP_HOUR, [candidate({streakWeeks: 4, streakThroughWeek: LAST_MONDAY})]);
  assert.equal(four?.kind, "milestone");
  assert.equal(four?.push.title, "4 weeks in a row");
  assert.equal(four?.push.body, "bama has met its goal 4 weeks running. That's a habit now.");
  const ten = plan(MONDAY, RECAP_HOUR, [candidate({streakWeeks: 10, streakThroughWeek: LAST_MONDAY})]);
  assert.equal(ten?.kind, "milestone");
  const five = plan(MONDAY, RECAP_HOUR, [candidate({streakWeeks: 5, streakThroughWeek: LAST_MONDAY})]);
  assert.equal(five?.kind, "recap");
  // A streak that ended before last week is no milestone now.
  const stale = plan(MONDAY, RECAP_HOUR, [candidate({streakWeeks: 4, streakThroughWeek: "2026-09-21"})]);
  assert.equal(stale?.kind, "recap");
});

test("a shield used last week is still told before a milestone", () => {
  const reminder = plan(MONDAY, RECAP_HOUR, [
    candidate({streakWeeks: 4, streakThroughWeek: LAST_MONDAY, lastShieldedWeek: LAST_MONDAY}),
  ]);
  assert.equal(reminder?.kind, "shield");
});

test("the recap names someone who walked every day, or says you did", () => {
  const meera = {userId: "meera", name: "Meera"};
  const named = plan(MONDAY, RECAP_HOUR, [candidate({previousWeekTotal: 64_000, previousWeekEveryDay: [meera]})]);
  assert.equal(named?.kind, "recap");
  assert.equal(named?.push.body, "Meera walked every day last week. This week's goal: 70,000.");
  const you = plan(MONDAY, RECAP_HOUR, [
    candidate({previousWeekTotal: 64_000, previousWeekEveryDay: [meera, {userId: "bob", name: "Bob"}]}),
  ]);
  assert.equal(you?.push.body, "You walked every day last week. This week's goal: 70,000.");
  const nobody = plan(MONDAY, RECAP_HOUR, [candidate({previousWeekTotal: 64_000})]);
  assert.equal(nobody?.push.body, "Last week: 64,000 steps. This week's goal: 70,000.");
});

test("with several who walked every day, the one named turns over by week", () => {
  const everyDay = [{userId: "meera", name: "Meera"}, {userId: "ravi", name: "Ravi"}];
  const thisWeek = plan(MONDAY, RECAP_HOUR, [candidate({previousWeekEveryDay: everyDay})]);
  const nextWeek = plan("2026-10-12", RECAP_HOUR, [
    candidate({weekStart: "2026-10-12", previousWeekEveryDay: everyDay}),
  ], {}, NOW + 7 * DAY);
  assert.notEqual(thisWeek?.push.body, nextWeek?.push.body);
});
