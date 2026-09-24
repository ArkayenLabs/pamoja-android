import {createHmac, timingSafeEqual} from "node:crypto";

export const CIRCLE_FEATURE_SET = [
  "circle_v1",
  "next_week_together_v1",
] as const;

export const FALLBACK_LEASE_DURATION_MS = 72 * 60 * 60 * 1_000;
export const WEBHOOK_SIGNATURE_TOLERANCE_SECONDS = 5 * 60;

const FIREBASE_UID_MAX_LENGTH = 128;
const REVENUECAT_ANONYMOUS_PREFIX = "$RCAnonymousID:";
const GROUP_ID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const EVENT_ID_PATTERN = /^[A-Za-z0-9_-]{1,200}$/;

export interface RevenueCatEntitlement {
  entitlement_id?: unknown;
  expires_at?: unknown;
}

export interface VerifiedEntitlement {
  active: boolean;
  groupCapacity: number;
  entitlementExpiresAtMs: number | null;
  leaseValidUntilMs: number;
}

export interface RevenueCatWebhookEvent {
  id?: unknown;
  type?: unknown;
  app_user_id?: unknown;
  original_app_user_id?: unknown;
  aliases?: unknown;
  environment?: unknown;
}

export function isValidGroupId(value: unknown): value is string {
  return typeof value === "string" && GROUP_ID_PATTERN.test(value);
}

export function isValidEventId(value: unknown): value is string {
  return typeof value === "string" && EVENT_ID_PATTERN.test(value);
}

/**
 * Verifies RevenueCat's HMAC signature over the exact request bytes.
 * The timestamp window also prevents a captured delivery from being replayed.
 */
export function verifyRevenueCatWebhookSignature(
  rawBody: Uint8Array,
  signatureHeader: string,
  signingSecret: string,
  nowSeconds: number,
  toleranceSeconds = WEBHOOK_SIGNATURE_TOLERANCE_SECONDS,
): boolean {
  if (
    !signingSecret ||
    !Number.isFinite(nowSeconds) ||
    !Number.isFinite(toleranceSeconds) ||
    toleranceSeconds < 0 ||
    signatureHeader.length > 256
  ) {
    return false;
  }

  const parts = new Map<string, string>();
  for (const part of signatureHeader.split(",")) {
    const separatorIndex = part.indexOf("=");
    if (separatorIndex <= 0) return false;

    const key = part.slice(0, separatorIndex).trim();
    const value = part.slice(separatorIndex + 1).trim();
    if (!key || !value || parts.has(key)) return false;
    parts.set(key, value);
  }

  const timestampText = parts.get("t");
  const signatureHex = parts.get("v1");
  if (
    parts.size !== 2 ||
    !timestampText ||
    !/^\d{1,12}$/.test(timestampText) ||
    !signatureHex ||
    !/^[0-9a-f]{64}$/i.test(signatureHex)
  ) {
    return false;
  }

  const timestampSeconds = Number(timestampText);
  if (
    !Number.isSafeInteger(timestampSeconds) ||
    Math.abs(nowSeconds - timestampSeconds) > toleranceSeconds
  ) {
    return false;
  }

  const computed = createHmac("sha256", signingSecret)
    .update(`${timestampText}.`)
    .update(rawBody)
    .digest();
  const received = Buffer.from(signatureHex, "hex");
  return received.length === computed.length &&
    timingSafeEqual(received, computed);
}

export function verifyActiveEntitlement(
  items: readonly RevenueCatEntitlement[],
  expectedEntitlementId: string,
  nowMs: number,
  capacityEntitlements: Readonly<Record<string, number>> = {},
): VerifiedEntitlement {
  const match = items.find(
    (item) => item.entitlement_id === expectedEntitlementId,
  );

  if (!match) {
    return inactiveEntitlement(nowMs);
  }

  if (match.expires_at === null) {
    return {
      active: true,
      groupCapacity: highestActiveCapacity(items, nowMs, capacityEntitlements),
      entitlementExpiresAtMs: null,
      leaseValidUntilMs: nowMs + FALLBACK_LEASE_DURATION_MS,
    };
  }

  if (
    typeof match.expires_at !== "number" ||
    !Number.isFinite(match.expires_at) ||
    match.expires_at <= nowMs
  ) {
    return inactiveEntitlement(nowMs);
  }

  return {
    active: true,
    groupCapacity: highestActiveCapacity(items, nowMs, capacityEntitlements),
    entitlementExpiresAtMs: match.expires_at,
    leaseValidUntilMs: match.expires_at,
  };
}

function highestActiveCapacity(
  items: readonly RevenueCatEntitlement[],
  nowMs: number,
  capacityEntitlements: Readonly<Record<string, number>>,
): number {
  return items.reduce((capacity, item) => {
    if (typeof item.entitlement_id !== "string") return capacity;
    const configured = capacityEntitlements[item.entitlement_id];
    if (!Number.isSafeInteger(configured) || configured < 1 || configured > 50) {
      return capacity;
    }
    const active = item.expires_at === null ||
      (typeof item.expires_at === "number" &&
        Number.isFinite(item.expires_at) && item.expires_at > nowMs);
    return active ? Math.max(capacity, configured) : capacity;
  }, 1);
}

export function revenueCatUserCandidates(
  event: RevenueCatWebhookEvent,
): string[] {
  const rawCandidates = [
    event.app_user_id,
    event.original_app_user_id,
    ...(Array.isArray(event.aliases) ? event.aliases : []),
  ];

  const candidates = rawCandidates.filter(
    (candidate): candidate is string =>
      typeof candidate === "string" &&
      candidate.length > 0 &&
      candidate.length <= FIREBASE_UID_MAX_LENGTH &&
      !candidate.startsWith(REVENUECAT_ANONYMOUS_PREFIX),
  );

  return [...new Set(candidates)];
}

function inactiveEntitlement(nowMs: number): VerifiedEntitlement {
  return {
    active: false,
    groupCapacity: 0,
    entitlementExpiresAtMs: null,
    leaseValidUntilMs: nowMs,
  };
}
