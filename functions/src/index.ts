import {timingSafeEqual} from "node:crypto";

import {initializeApp} from "firebase-admin/app";
import {getAuth} from "firebase-admin/auth";
import {
  FieldValue,
  Timestamp,
  getFirestore,
} from "firebase-admin/firestore";
import {logger, setGlobalOptions} from "firebase-functions/v2";
import {HttpsError, onCall, onRequest} from "firebase-functions/v2/https";
import {defineJsonSecret} from "firebase-functions/params";

import {
  CIRCLE_FEATURE_SET,
  RevenueCatEntitlement,
  RevenueCatWebhookEvent,
  VerifiedEntitlement,
  isValidEventId,
  isValidGroupId,
  revenueCatUserCandidates,
  verifyActiveEntitlement,
  verifyRevenueCatWebhookSignature,
} from "./domain.js";

initializeApp();

setGlobalOptions({
  maxInstances: 10,
  memory: "256MiB",
  region: "asia-south1",
  timeoutSeconds: 30,
});

const firestore = getFirestore();
const auth = getAuth();

const revenueCatServerConfig = defineJsonSecret("REVENUECAT_SERVER_CONFIG");

interface RevenueCatServerConfig {
  apiKey: string;
  projectId: string;
  entitlementId: string;
  capacityEntitlements?: Record<string, number>;
  webhookAuthorization: string;
  webhookSigningSecret: string;
}

interface ActiveEntitlementsResponse {
  items?: unknown;
}

interface SponsorshipData {
  groupId?: unknown;
  replaceExisting?: unknown;
  expectedSponsoredGroupId?: unknown;
  expectedPayerUid?: unknown;
}

/** Read-only checkout admission. No subscription or group access is changed. */
export const getCirclePurchaseContext = onCall<SponsorshipData>(
  {secrets: [revenueCatServerConfig], enforceAppCheck: true},
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in before subscribing.");
    const groupId = request.data?.groupId;
    if (!isValidGroupId(groupId)) throw new HttpsError("invalid-argument", "Invalid group");
    const memberRef = firestore.doc(`memberships/${uid}_${groupId}`);
    // Verify membership before looking up private billing information.
    if (!(await memberRef.get()).exists) throw new HttpsError("permission-denied", "Current members only");
    const verified = await fetchVerifiedEntitlement(uid, readRevenueCatConfig(), Date.now());
    return firestore.runTransaction(async (tx) => {
      const [group, member, account, billing, rollout, trailRollout] = await tx.getAll(
        firestore.doc(`groups/${groupId}`), memberRef,
        firestore.doc(`billingAccounts/${uid}`), firestore.doc(`groupBilling/${groupId}`),
        firestore.doc("featureRollouts/dayOnePlanning"),
        firestore.doc("featureRollouts/togetherTrail"),
      );
      if (!group.exists || group.get("deletionPending") === true || !member.exists) {
        throw new HttpsError("permission-denied", "Current members only");
      }
      const sponsoredGroupIds = readSponsoredGroupIds(account);
      const sponsoredGroupId = sponsoredGroupIds.find((id) => id !== groupId) ??
        sponsoredGroupIds[0] ?? null;
      let sponsoredGroupName: string | null = null;
      if (sponsoredGroupId && sponsoredGroupId !== groupId) {
        const [previous, previousMembership] = await tx.getAll(
          firestore.doc(`groups/${sponsoredGroupId}`),
          firestore.doc(`memberships/${uid}_${sponsoredGroupId}`),
        );
        // A former sponsor must not learn a departed group's current name.
        if (previousMembership.exists) sponsoredGroupName = stringOrNull(previous.get("name"));
      }
      const groupIsPremium = billing.get("status") === "active" &&
        (timestampMillisOrNull(billing.get("leaseValidUntil")) ?? 0) > Date.now();
      return {groupId, payerUid: uid, groupIsPremium,
        trailAvailable: trailRollout.get("enabled") === true &&
          (trailRollout.get("publicEnrollment") === true ||
            (Array.isArray(trailRollout.get("groupIds")) && trailRollout.get("groupIds").includes(groupId))),
        planningAvailable: rollout.get("enabled") === true &&
          (rollout.get("publicEnrollment") === true ||
            (Array.isArray(rollout.get("groupIds")) && rollout.get("groupIds").includes(groupId))),
        isGroupSponsor: groupIsPremium && billing.get("sponsorUid") === uid,
        subscriptionIsActive: verified.active,
        groupCapacity: verified.groupCapacity,
        sponsoredGroupIds,
        sponsoredGroupId,
        sponsoredGroupName};
    });
  },
);

/**
 * Assigns the signed-in payer's active entitlement to one Pamoja group.
 *
 * RevenueCat is queried by Firebase UID, which is also the appUserID configured
 * by the Android client. The client cannot mint or modify group access itself.
 */
export const activateCircleSponsorship = onCall<SponsorshipData>(
  {
    secrets: [revenueCatServerConfig],
    enforceAppCheck: true,
  },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) {
      throw new HttpsError("unauthenticated", "Sign in before subscribing.");
    }

    if (request.data?.expectedPayerUid != null && request.data.expectedPayerUid !== uid) {
      throw new HttpsError("unauthenticated", "The signed-in account changed. Reopen Premium.");
    }

    const groupId = request.data?.groupId;
    if (!isValidGroupId(groupId)) {
      throw new HttpsError(
        "invalid-argument",
        "A valid group is required.",
        {reason: "invalid_group"},
      );
    }

    const groupRef = firestore.collection("groups").doc(groupId);
    const membershipRef = firestore
      .collection("memberships")
      .doc(`${uid}_${groupId}`);
    const [groupSnapshot, membershipSnapshot] = await Promise.all([
      groupRef.get(),
      membershipRef.get(),
    ]);

    if (!groupSnapshot.exists || groupSnapshot.get("deletionPending") === true) {
      throw new HttpsError(
        "not-found",
        "This group no longer exists.",
        {reason: "group_unavailable"},
      );
    }
    if (!membershipSnapshot.exists) {
      throw new HttpsError(
        "permission-denied",
        "Only a group member can sponsor this group.",
        {reason: "not_current_member"},
      );
    }

    const nowMs = Date.now();
    const config = readRevenueCatConfig();
    const verified = await fetchVerifiedEntitlement(uid, config, nowMs);
    if (!verified.active) {
      throw new HttpsError(
        "failed-precondition",
        "No active Pamoja Circle subscription was found for this account.",
        {reason: "no_active_subscription"},
      );
    }

    await activateForGroup(
      uid,
      groupId,
      verified,
      nowMs,
      request.data?.replaceExisting === true,
      request.data?.expectedSponsoredGroupId,
    );

    return {
      groupId,
      isPremium: true,
      leaseValidUntilMs: verified.leaseValidUntilMs,
    };
  },
);

/**
 * Reconciles RevenueCat lifecycle events against RevenueCat's canonical active
 * entitlement endpoint. Event types are notifications, not the source of truth.
 */
export const revenueCatWebhook = onRequest(
  {
    secrets: [revenueCatServerConfig],
  },
  async (request, response) => {
    if (request.method !== "POST") {
      response.set("Allow", "POST").status(405).send("Method not allowed");
      return;
    }

    const config = readRevenueCatConfig();
    if (
      !secureEquals(
        request.get("authorization") ?? "",
        config.webhookAuthorization,
      )
    ) {
      response.status(401).send("Unauthorized");
      return;
    }

    if (
      !verifyRevenueCatWebhookSignature(
        request.rawBody,
        request.get("x-revenuecat-webhook-signature") ?? "",
        config.webhookSigningSecret,
        Math.floor(Date.now() / 1_000),
      )
    ) {
      response.status(401).send("Invalid signature");
      return;
    }

    const event = readWebhookEvent(request.body);
    const eventId = event?.id;
    if (!event || !isValidEventId(eventId)) {
      response.status(400).send("Invalid RevenueCat event");
      return;
    }
    const validatedEvent: RevenueCatWebhookEvent & {id: string} = {
      ...event,
      id: eventId,
    };

    const eventRef = firestore.collection("revenueCatEvents").doc(eventId);
    if ((await eventRef.get()).exists) {
      response.status(200).send("Already processed");
      return;
    }

    const uid = await resolveFirebaseUid(validatedEvent);
    if (!uid) {
      await recordIgnoredEvent(
        eventRef,
        validatedEvent,
        "firebase_user_not_found",
      );
      logger.warn("RevenueCat event did not map to a Firebase user", {
        eventId,
        eventType: stringOrNull(validatedEvent.type),
      });
      response.status(200).send("Ignored");
      return;
    }

    const nowMs = Date.now();
    const verified = await fetchVerifiedEntitlement(uid, config, nowMs);
    await reconcileWebhookEvent(uid, validatedEvent, verified, eventRef, nowMs);

    response.status(200).send("Processed");
  },
);

async function activateForGroup(
  uid: string,
  groupId: string,
  verified: VerifiedEntitlement,
  nowMs: number,
  replaceExisting: boolean,
  expectedSponsoredGroupId: unknown,
): Promise<void> {
  const accountRef = firestore.collection("billingAccounts").doc(uid);
  const groupRef = firestore.collection("groups").doc(groupId);
  const membershipRef = firestore
    .collection("memberships")
    .doc(`${uid}_${groupId}`);
  const groupBillingRef = firestore.collection("groupBilling").doc(groupId);
  const groupAccessRef = firestore.collection("groupAccess").doc(groupId);
  const billingViewRef = firestore.collection("billingViews").doc(uid);

  await firestore.runTransaction(async (transaction) => {
    const [
      accountSnapshot,
      groupSnapshot,
      membershipSnapshot,
      groupBillingSnapshot,
    ] = await Promise.all([
      transaction.get(accountRef),
      transaction.get(groupRef),
      transaction.get(membershipRef),
      transaction.get(groupBillingRef),
    ]);

    if (!groupSnapshot.exists || groupSnapshot.get("deletionPending") === true) {
      throw new HttpsError(
        "not-found",
        "This group no longer exists.",
        {reason: "group_unavailable"},
      );
    }
    if (!membershipSnapshot.exists) {
      throw new HttpsError(
        "permission-denied",
        "Only a current group member can sponsor this group.",
        {reason: "not_current_member"},
      );
    }

    const sponsoredGroupIds = readSponsoredGroupIds(accountSnapshot);
    const sponsoredGroupId = sponsoredGroupIds.find((id) => id !== groupId) ?? null;
    let previousGroupBillingRef: FirebaseFirestore.DocumentReference | null = null;
    let previousGroupBillingSnapshot: FirebaseFirestore.DocumentSnapshot | null =
      null;
    const targetAlreadySponsored = sponsoredGroupIds.includes(groupId);
    const capacityReached = !targetAlreadySponsored &&
      sponsoredGroupIds.length >= verified.groupCapacity;
    if (capacityReached) {
      if (!replaceExisting) {
        throw new HttpsError(
          "failed-precondition",
          "This subscription already sponsors another group.",
          {reason: "subscription_assigned_elsewhere"},
        );
      }
      if (!sponsoredGroupId || expectedSponsoredGroupId !== sponsoredGroupId) {
        throw new HttpsError("failed-precondition", "The subscription assignment changed. Reopen Premium.",
          {reason: "sponsorship_changed"});
      }
      previousGroupBillingRef = firestore
        .collection("groupBilling")
        .doc(sponsoredGroupId);
      previousGroupBillingSnapshot = await transaction.get(
        previousGroupBillingRef,
      );
    }

    const currentSponsorUid = stringOrNull(
      groupBillingSnapshot.get("sponsorUid"),
    );
    const currentLeaseValidUntil = timestampMillisOrNull(
      groupBillingSnapshot.get("leaseValidUntil"),
    );
    const occupiedByAnotherActiveSponsor =
      currentSponsorUid !== null &&
      currentSponsorUid !== uid &&
      currentLeaseValidUntil !== null &&
      currentLeaseValidUntil > nowMs;
    if (occupiedByAnotherActiveSponsor) {
      throw new HttpsError(
        "already-exists",
        "This group is already sponsored by another member.",
        {reason: "group_already_sponsored"},
      );
    }

    const timestamps = entitlementTimestamps(verified);
    const auditFields = {
      lastVerifiedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    };

    const resultingGroupIds = targetAlreadySponsored ?
      sponsoredGroupIds :
      previousGroupBillingRef ?
        [...sponsoredGroupIds.filter((id) => id !== sponsoredGroupId), groupId] :
        [...sponsoredGroupIds, groupId];

    if (
      previousGroupBillingRef &&
      previousGroupBillingSnapshot?.get("sponsorUid") === uid
    ) {
      const previousGroupAccessRef = firestore
        .collection("groupAccess")
        .doc(sponsoredGroupId!);
      transaction.set(
        previousGroupBillingRef,
        {
          status: "moved",
          leaseValidUntil: Timestamp.fromMillis(nowMs),
          updatedAt: FieldValue.serverTimestamp(),
        },
        {merge: true},
      );
      transaction.set(
        previousGroupAccessRef,
        {
          isPremium: false,
          featureSet: [],
          leaseValidUntil: Timestamp.fromMillis(nowMs),
          updatedAt: FieldValue.serverTimestamp(),
        },
        {merge: true},
      );
    }

    transaction.set(
      accountRef,
      {
        uid,
        revenueCatCustomerId: uid,
        sponsoredGroupId: resultingGroupIds[0] ?? null,
        sponsoredGroupIds: resultingGroupIds,
        groupCapacity: verified.groupCapacity,
        status: "active",
        ...timestamps,
        ...auditFields,
      },
      {merge: true},
    );
    transaction.set(
      groupBillingRef,
      {
        sponsorUid: uid,
        status: "active",
        ...timestamps,
        updatedAt: FieldValue.serverTimestamp(),
      },
      {merge: true},
    );
    transaction.set(
      groupAccessRef,
      {
        isPremium: true,
        featureSet: [...CIRCLE_FEATURE_SET],
        leaseValidUntil: Timestamp.fromMillis(verified.leaseValidUntilMs),
        updatedAt: FieldValue.serverTimestamp(),
      },
      {merge: true},
    );
    transaction.set(
      billingViewRef,
      {
        isEntitled: true,
        sponsoredGroupId: resultingGroupIds[0] ?? null,
        sponsoredGroupIds: resultingGroupIds,
        groupCapacity: verified.groupCapacity,
        groupLeaseValidUntil: Timestamp.fromMillis(
          verified.leaseValidUntilMs,
        ),
        ...auditFields,
      },
      {merge: true},
    );
  });
}

async function reconcileWebhookEvent(
  uid: string,
  event: RevenueCatWebhookEvent & {id: string},
  verified: VerifiedEntitlement,
  eventRef: FirebaseFirestore.DocumentReference,
  nowMs: number,
): Promise<void> {
  const accountRef = firestore.collection("billingAccounts").doc(uid);
  const billingViewRef = firestore.collection("billingViews").doc(uid);

  await firestore.runTransaction(async (transaction) => {
    const eventSnapshot = await transaction.get(eventRef);
    if (eventSnapshot.exists) return;

    const accountSnapshot = await transaction.get(accountRef);
    const sponsoredGroupIds = readSponsoredGroupIds(accountSnapshot);
    const groupBillingRefs = sponsoredGroupIds.map((groupId) =>
      firestore.collection("groupBilling").doc(groupId));
    const groupBillingSnapshots = groupBillingRefs.length > 0 ?
      await transaction.getAll(...groupBillingRefs) : [];
    const ownedGroupIds = groupBillingSnapshots
      .filter((snapshot) => snapshot.get("sponsorUid") === uid)
      .map((snapshot) => snapshot.id);
    // A different member may legitimately take over after this payer's lease
    // expires. A late renewal webhook from the old payer must not reclaim that
    // group or leave their personal projection pointing at it.
    const effectiveSponsoredGroupIds = verified.active ?
      ownedGroupIds.slice(0, verified.groupCapacity) : [];
    const effectiveSponsoredGroupId = effectiveSponsoredGroupIds[0] ?? null;

    const timestamps = entitlementTimestamps(verified);
    transaction.set(
      accountRef,
      {
        uid,
        revenueCatCustomerId: uid,
        sponsoredGroupId: effectiveSponsoredGroupId,
        sponsoredGroupIds: effectiveSponsoredGroupIds,
        groupCapacity: verified.groupCapacity,
        status: verified.active ? "active" : "inactive",
        ...timestamps,
        lastVerifiedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      },
      {merge: true},
    );
    transaction.set(
      billingViewRef,
      {
        isEntitled: verified.active,
        sponsoredGroupId: effectiveSponsoredGroupId,
        sponsoredGroupIds: effectiveSponsoredGroupIds,
        groupCapacity: verified.groupCapacity,
        groupLeaseValidUntil: effectiveSponsoredGroupId ?
          Timestamp.fromMillis(verified.leaseValidUntilMs) :
          null,
        lastVerifiedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      },
      {merge: true},
    );

    for (const groupBillingSnapshot of groupBillingSnapshots) {
      if (groupBillingSnapshot.get("sponsorUid") !== uid) continue;
      const groupBillingRef = groupBillingSnapshot.ref;
      const groupId = groupBillingSnapshot.id;
      const remainsCovered = effectiveSponsoredGroupIds.includes(groupId);
      const groupAccessRef = firestore
        .collection("groupAccess")
        .doc(groupId);
      transaction.set(
        groupBillingRef,
        {
          status: remainsCovered ? "active" : "inactive",
          ...timestamps,
          updatedAt: FieldValue.serverTimestamp(),
        },
        {merge: true},
      );
      transaction.set(
        groupAccessRef,
        {
          isPremium: remainsCovered,
          featureSet: remainsCovered ? [...CIRCLE_FEATURE_SET] : [],
          leaseValidUntil: Timestamp.fromMillis(verified.leaseValidUntilMs),
          updatedAt: FieldValue.serverTimestamp(),
        },
        {merge: true},
      );
    }

    transaction.create(eventRef, {
      uid,
      eventType: stringOrNull(event.type),
      environment: stringOrNull(event.environment),
      active: verified.active,
      groupCapacity: verified.groupCapacity,
      source: "webhook",
      receivedAtMs: nowMs,
      processedAt: FieldValue.serverTimestamp(),
    });
  });
}

async function fetchVerifiedEntitlement(
  uid: string,
  config: RevenueCatServerConfig,
  nowMs: number,
): Promise<VerifiedEntitlement> {
  const url = new URL(
    `https://api.revenuecat.com/v2/projects/${encodeURIComponent(config.projectId)}` +
      `/customers/${encodeURIComponent(uid)}/active_entitlements`,
  );
  url.searchParams.set("limit", "100");

  let result: Response;
  try {
    result = await fetch(url, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${config.apiKey}`,
        Accept: "application/json",
      },
      signal: AbortSignal.timeout(10_000),
    });
  } catch (error) {
    logger.error("RevenueCat verification request failed", error);
    throw new HttpsError(
      "unavailable",
      "Subscription verification is temporarily unavailable.",
    );
  }

  if (result.status === 404) {
    return verifyActiveEntitlement(
      [], config.entitlementId, nowMs, config.capacityEntitlements,
    );
  }
  if (!result.ok) {
    logger.error("RevenueCat verification returned an error", {
      status: result.status,
    });
    throw new HttpsError(
      "unavailable",
      "Subscription verification is temporarily unavailable.",
    );
  }

  let body: ActiveEntitlementsResponse;
  try {
    body = (await result.json()) as ActiveEntitlementsResponse;
  } catch (error) {
    logger.error("RevenueCat returned invalid JSON", error);
    throw new HttpsError(
      "unavailable",
      "Subscription verification is temporarily unavailable.",
    );
  }

  const items = Array.isArray(body.items) ?
    (body.items as RevenueCatEntitlement[]) :
    [];
  return verifyActiveEntitlement(
    items, config.entitlementId, nowMs, config.capacityEntitlements,
  );
}

async function resolveFirebaseUid(
  event: RevenueCatWebhookEvent,
): Promise<string | null> {
  for (const candidate of revenueCatUserCandidates(event)) {
    try {
      await auth.getUser(candidate);
      return candidate;
    } catch (error) {
      if (!isFirebaseUserNotFound(error)) throw error;
    }
  }
  return null;
}

async function recordIgnoredEvent(
  eventRef: FirebaseFirestore.DocumentReference,
  event: RevenueCatWebhookEvent & {id: string},
  reason: string,
): Promise<void> {
  await firestore.runTransaction(async (transaction) => {
    if ((await transaction.get(eventRef)).exists) return;
    transaction.create(eventRef, {
      eventType: stringOrNull(event.type),
      environment: stringOrNull(event.environment),
      source: "webhook",
      status: "ignored",
      reason,
      processedAt: FieldValue.serverTimestamp(),
    });
  });
}

function readWebhookEvent(
  body: unknown,
): (RevenueCatWebhookEvent & {id: unknown}) | null {
  if (!body || typeof body !== "object") return null;
  const event = (body as {event?: unknown}).event;
  if (!event || typeof event !== "object") return null;
  return event as RevenueCatWebhookEvent & {id: unknown};
}

function readRevenueCatConfig(): RevenueCatServerConfig {
  const raw = revenueCatServerConfig.value() as Partial<RevenueCatServerConfig>;
  const entries = [
    raw.apiKey,
    raw.projectId,
    raw.entitlementId,
    raw.webhookAuthorization,
    raw.webhookSigningSecret,
  ];
  if (entries.some((value) => typeof value !== "string" || !value.trim())) {
    logger.error("REVENUECAT_SERVER_CONFIG is incomplete");
    throw new HttpsError("internal", "Billing is not configured.");
  }
  return raw as RevenueCatServerConfig;
}

function entitlementTimestamps(verified: VerifiedEntitlement): {
  entitlementExpiresAt: Timestamp | null;
  leaseValidUntil: Timestamp;
} {
  return {
    entitlementExpiresAt: verified.entitlementExpiresAtMs === null ?
      null :
      Timestamp.fromMillis(verified.entitlementExpiresAtMs),
    leaseValidUntil: Timestamp.fromMillis(verified.leaseValidUntilMs),
  };
}

function timestampMillisOrNull(value: unknown): number | null {
  return value instanceof Timestamp ? value.toMillis() : null;
}

function stringOrNull(value: unknown): string | null {
  return typeof value === "string" && value.length > 0 ? value : null;
}

/** Reads the new multi-group projection and transparently migrates legacy rows. */
function readSponsoredGroupIds(
  snapshot: FirebaseFirestore.DocumentSnapshot,
): string[] {
  const raw = snapshot.get("sponsoredGroupIds");
  if (Array.isArray(raw)) {
    return [...new Set(raw.filter(isValidGroupId))];
  }
  const legacy = stringOrNull(snapshot.get("sponsoredGroupId"));
  return legacy && isValidGroupId(legacy) ? [legacy] : [];
}

function secureEquals(received: string, expected: string): boolean {
  const receivedBuffer = Buffer.from(received);
  const expectedBuffer = Buffer.from(expected);
  return receivedBuffer.length === expectedBuffer.length &&
    timingSafeEqual(receivedBuffer, expectedBuffer);
}

function isFirebaseUserNotFound(error: unknown): boolean {
  return Boolean(
    error &&
      typeof error === "object" &&
      "code" in error &&
      error.code === "auth/user-not-found",
  );
}
