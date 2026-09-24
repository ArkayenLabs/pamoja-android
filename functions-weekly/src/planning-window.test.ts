import assert from "node:assert/strict";
import test from "node:test";
import {planningWindow, targetForPlan} from "./planning-window.js";

test("fixed group zone gives one boundary across the international date line", () => {
  const now = Date.parse("2026-09-13T18:30:00Z");
  const window = planningWindow(now, "Asia/Kolkata", "MONDAY");
  assert.equal(window.currentWeekStart, "2026-09-14");
  assert.equal(window.weekStart, "2026-09-21");
  assert.equal(window.appliesAtMillis, Date.parse("2026-09-20T18:30:00Z"));
  assert.equal(planningWindow(now - 1, "Asia/Kolkata", "MONDAY").weekStart, "2026-09-14");
});

test("DST weeks use calendar midnights, including the shorter and longer week", () => {
  const spring = planningWindow(Date.parse("2026-03-07T12:00:00Z"), "America/New_York", "SUNDAY");
  assert.equal(spring.endsAtMillis - spring.appliesAtMillis, 167 * 3_600_000);
  const fall = planningWindow(Date.parse("2026-10-31T12:00:00Z"), "America/New_York", "SUNDAY");
  assert.equal(fall.endsAtMillis - fall.appliesAtMillis, 169 * 3_600_000);
});

test("dormant cached weeks are not inputs; blank start day retains Monday", () => {
  assert.equal(planningWindow(Date.parse("2027-02-03T12:00:00Z"), "Pacific/Kiritimati", "").weekStart, "2027-02-08");
  assert.throws(() => planningWindow(Date.now(), "Invalid/Zone", "MONDAY"));
});

test("server computes rounded choices and bounds custom targets", () => {
  assert.equal(targetForPlan(70_000, "gentler", 1), 60_000);
  assert.equal(targetForPlan(10_000, "gentler", 1), 10_000);
  assert.equal(targetForPlan(70_000, "repeat", 1), 70_000);
  assert.throws(() => targetForPlan(70_000, "custom", 9_999));
  assert.throws(() => targetForPlan(70_000, "custom", 2_800_001));
});
