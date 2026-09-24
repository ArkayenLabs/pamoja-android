import assert from "node:assert/strict";
import {createHmac} from "node:crypto";
import test from "node:test";

import {
  CIRCLE_FEATURE_SET,
  FALLBACK_LEASE_DURATION_MS,
  isValidEventId,
  isValidGroupId,
  revenueCatUserCandidates,
  verifyActiveEntitlement,
  verifyRevenueCatWebhookSignature,
} from "./domain.js";

test("paid leases advertise review and next-week planning", () => {
  assert.deepEqual(CIRCLE_FEATURE_SET, ["circle_v1", "next_week_together_v1"]);
});

test("verifyActiveEntitlement uses the canonical entitlement expiry", () => {
  const nowMs = 1_800_000_000_000;
  const expiryMs = nowMs + 10_000;

  assert.deepEqual(
    verifyActiveEntitlement(
      [{entitlement_id: "pamoja_circle", expires_at: expiryMs}],
      "pamoja_circle",
      nowMs,
    ),
    {
      active: true,
      groupCapacity: 1,
      entitlementExpiresAtMs: expiryMs,
      leaseValidUntilMs: expiryMs,
    },
  );
});

test("verifyActiveEntitlement rejects missing, malformed, or expired data", () => {
  const nowMs = 1_800_000_000_000;

  for (const items of [
    [],
    [{entitlement_id: "other", expires_at: nowMs + 10_000}],
    [{entitlement_id: "pamoja_circle", expires_at: "tomorrow"}],
    [{entitlement_id: "pamoja_circle", expires_at: nowMs}],
  ]) {
    assert.deepEqual(
      verifyActiveEntitlement(items, "pamoja_circle", nowMs),
      {
        active: false,
        groupCapacity: 0,
        entitlementExpiresAtMs: null,
        leaseValidUntilMs: nowMs,
      },
    );
  }
});

test("a non-expiring entitlement receives a bounded verification lease", () => {
  const nowMs = 1_800_000_000_000;

  assert.deepEqual(
    verifyActiveEntitlement(
      [{entitlement_id: "pamoja_circle", expires_at: null}],
      "pamoja_circle",
      nowMs,
    ),
    {
      active: true,
      groupCapacity: 1,
      entitlementExpiresAtMs: null,
      leaseValidUntilMs: nowMs + FALLBACK_LEASE_DURATION_MS,
    },
  );
});

test("capacity entitlement raises the number of groups without replacing Premium", () => {
  const nowMs = 1_800_000_000_000;
  const expiryMs = nowMs + 10_000;

  assert.equal(
    verifyActiveEntitlement(
      [
        {entitlement_id: "premium", expires_at: expiryMs},
        {entitlement_id: "premium_three_groups", expires_at: expiryMs},
      ],
      "premium",
      nowMs,
      {premium_three_groups: 3},
    ).groupCapacity,
    3,
  );
});

test("webhook candidates are unique Firebase-compatible non-anonymous IDs", () => {
  assert.deepEqual(
    revenueCatUserCandidates({
      app_user_id: "$RCAnonymousID:abc",
      original_app_user_id: "firebase-user",
      aliases: ["firebase-user", "second-user", 123],
    }),
    ["firebase-user", "second-user"],
  );
});

test("group and event identifiers use the formats accepted by the backend", () => {
  assert.equal(
    isValidGroupId("6413795d-42d0-4f2b-80df-f017a9d32817"),
    true,
  );
  assert.equal(isValidGroupId("../../groups/other"), false);
  assert.equal(isValidEventId("d8a7d4e7-1a0e-48ee-9fb7_abc"), true);
  assert.equal(isValidEventId("bad/event"), false);
});

test("RevenueCat webhook signature accepts exact fresh request bytes", () => {
  const timestamp = 1_800_000_000;
  const secret = "test-signing-secret";
  const rawBody = Buffer.from('{"event":{"id":"event-1"}}');
  const signature = createHmac("sha256", secret)
    .update(`${timestamp}.`)
    .update(rawBody)
    .digest("hex");

  assert.equal(
    verifyRevenueCatWebhookSignature(
      rawBody,
      `t=${timestamp},v1=${signature}`,
      secret,
      timestamp + 30,
    ),
    true,
  );
});

test("RevenueCat webhook signature rejects tampering and replay", () => {
  const timestamp = 1_800_000_000;
  const secret = "test-signing-secret";
  const rawBody = Buffer.from('{"event":{"id":"event-1"}}');
  const signature = createHmac("sha256", secret)
    .update(`${timestamp}.`)
    .update(rawBody)
    .digest("hex");
  const header = `t=${timestamp},v1=${signature}`;

  assert.equal(
    verifyRevenueCatWebhookSignature(
      Buffer.from('{"event":{"id":"event-2"}}'),
      header,
      secret,
      timestamp,
    ),
    false,
  );
  assert.equal(
    verifyRevenueCatWebhookSignature(
      rawBody,
      header,
      secret,
      timestamp + 301,
    ),
    false,
  );
  assert.equal(
    verifyRevenueCatWebhookSignature(
      rawBody,
      `v1=${signature},t=${timestamp},t=${timestamp}`,
      secret,
      timestamp,
    ),
    false,
  );
});
