import {FieldValue, Timestamp, getFirestore} from "firebase-admin/firestore";
import {getMessaging} from "firebase-admin/messaging";
import {logger} from "firebase-functions/v2";
import {
  onDocumentCreated,
  onDocumentDeleted,
  onDocumentUpdated,
} from "firebase-functions/v2/firestore";
import {HttpsError, onCall} from "firebase-functions/v2/https";
import {onSchedule} from "firebase-functions/v2/scheduler";

import {
  PushPayload,
  buildGoalReachedPush,
  buildMemberJoinedPush,
  isValidAppVersion,
  isValidGroupId,
  isValidInstallationId,
  isValidIsoDate,
} from "./notification-domain.js";

const APP_PACKAGE = "com.arkayenlabs.pamoja";
const FUNCTION_REGION = "asia-south1";
const REGISTRATION_MAX_AGE_MS = 35 * 24 * 60 * 60 * 1_000;
const MAX_REGISTRATIONS_PER_GROUP = 100;
const RUNTIME_OPTIONS = {
  maxInstances: 10,
  memory: "256MiB" as const,
  region: FUNCTION_REGION,
  timeoutSeconds: 30,
};

interface PushRegistrationData {
  installationId?: unknown;
  appVersion?: unknown;
}

export const registerPushInstallation = onCall<PushRegistrationData>(
  {...RUNTIME_OPTIONS, enforceAppCheck: true},
  async (request) => {
    const userId = request.auth?.uid;
    if (!userId) {
      throw new HttpsError("unauthenticated", "Sign in to enable notifications.");
    }

    const installationId = request.data?.installationId;
    const appVersion = request.data?.appVersion;
    if (!isValidInstallationId(installationId) ||
        !isValidAppVersion(appVersion)) {
      throw new HttpsError(
        "invalid-argument",
        "The push registration is invalid.",
      );
    }

    await getFirestore().collection("pushRegistrations")
      .doc(installationId)
      .set({
        installationId,
        userId,
        platform: "android",
        appVersion,
        updatedAt: FieldValue.serverTimestamp(),
      });

    return {registered: true};
  },
);

export const unregisterPushInstallation = onCall<PushRegistrationData>(
  {...RUNTIME_OPTIONS, enforceAppCheck: true},
  async (request) => {
    const userId = request.auth?.uid;
    if (!userId) {
      throw new HttpsError("unauthenticated", "Sign in to change notifications.");
    }

    const installationId = request.data?.installationId;
    if (!isValidInstallationId(installationId)) {
      throw new HttpsError(
        "invalid-argument",
        "The push registration is invalid.",
      );
    }

    const ref = getFirestore().collection("pushRegistrations")
      .doc(installationId);
    await getFirestore().runTransaction(async (transaction) => {
      const snapshot = await transaction.get(ref);
      if (snapshot.get("userId") === userId) transaction.delete(ref);
    });

    return {unregistered: true};
  },
);

/** Sends a real push when a new person joins an existing group. */
export const notifyGroupMemberJoined = onDocumentCreated(
  {
    ...RUNTIME_OPTIONS,
    document: "memberships/{membershipId}",
    retry: true,
  },
  async (event) => {
    const membership = event.data?.data();
    const userId = stringOrNull(membership?.userId);
    const groupId = stringOrNull(membership?.groupId);
    if (!userId || !groupId || !isValidGroupId(groupId)) return;

    const memberSnapshot = await getFirestore().collection("memberships")
      .where("groupId", "==", groupId)
      .limit(20)
      .get();
    const recipients = memberSnapshot.docs
      .map((document) => stringOrNull(document.get("userId")))
      .filter((candidate): candidate is string =>
        candidate !== null && candidate !== userId,
      );
    if (recipients.length === 0) return;

    const groupSnapshot = await getFirestore().collection("groups").doc(groupId).get();

    await sendToUsers(
      recipients,
      buildMemberJoinedPush({
        membershipId: event.params.membershipId,
        joinedAt: membership?.joinedAt,
        displayName: membership?.displayName,
        groupId,
        groupName: groupSnapshot.get("name"),
        memberCount: memberSnapshot.size,
      }),
    );
  },
);

/** Sends once per client-visible goal crossing; clients dedupe retries by ID. */
export const notifyGroupGoalReached = onDocumentUpdated(
  {
    ...RUNTIME_OPTIONS,
    document: "groups/{groupId}",
    retry: true,
  },
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    const groupId = event.params.groupId;
    if (!before || !after || !isValidGroupId(groupId)) return;

    const weekStart = after.weekStart;
    const target = safePositiveInteger(after.weeklyTarget);
    const afterSteps = safeNonNegativeInteger(after.weeklySteps);
    const beforeSteps = before.weekStart === weekStart ?
      safeNonNegativeInteger(before.weeklySteps) :
      0;
    const stepsIncreased = afterSteps > beforeSteps;
    const crossedGoal = target > 0 && beforeSteps < target && afterSteps >= target;
    if (!isValidIsoDate(weekStart) || !stepsIncreased || !crossedGoal) return;

    const memberSnapshot = await getFirestore().collection("memberships")
      .where("groupId", "==", groupId)
      .limit(20)
      .get();
    const recipients = memberSnapshot.docs
      .map((document) => stringOrNull(document.get("userId")))
      .filter((candidate): candidate is string => candidate !== null);

    await sendToUsers(
      recipients,
      buildGoalReachedPush({
        groupId,
        weekStart,
        groupName: after.name,
        finalSteps: afterSteps,
      }),
    );
  },
);

/** Removes server-only delivery identifiers when the profile is erased. */
export const cleanupPushRegistrationsOnUserDelete = onDocumentDeleted(
  {
    ...RUNTIME_OPTIONS,
    document: "users/{userId}",
    retry: true,
  },
  async (event) => {
    await deleteRegistrationsForUser(event.params.userId);
  },
);

/** Removes abandoned installation mappings even when no push happens to send. */
export const pruneStalePushRegistrations = onSchedule(
  {
    ...RUNTIME_OPTIONS,
    schedule: "every day 03:00",
    timeZone: "Asia/Kolkata",
  },
  async () => {
    const staleBefore = Timestamp.fromMillis(
      Date.now() - REGISTRATION_MAX_AGE_MS,
    );
    let deleted = 0;
    while (true) {
      const snapshot = await getFirestore().collection("pushRegistrations")
        .where("updatedAt", "<", staleBefore)
        .limit(400)
        .get();
      if (snapshot.empty) break;
      await deleteReferences(snapshot.docs.map((document) => document.ref));
      deleted += snapshot.size;
    }
    logger.info("Stale push registration cleanup completed", {deleted});
  },
);

async function sendToUsers(
  requestedUserIds: readonly string[],
  payload: PushPayload,
): Promise<void> {
  const userIds = [...new Set(requestedUserIds)].slice(0, 20);
  if (userIds.length === 0) return;

  const snapshot = await getFirestore().collection("pushRegistrations")
    .where("userId", "in", userIds)
    .limit(MAX_REGISTRATIONS_PER_GROUP)
    .get();
  const staleBefore = Date.now() - REGISTRATION_MAX_AGE_MS;
  const staleRefs: FirebaseFirestore.DocumentReference[] = [];
  const registrations = snapshot.docs.flatMap((document) => {
    const userId = stringOrNull(document.get("userId"));
    const installationId = document.get("installationId");
    const updatedAt = document.get("updatedAt");
    const updatedAtMs = updatedAt instanceof Timestamp ? updatedAt.toMillis() : 0;

    if (!userId || !isValidInstallationId(installationId) ||
        updatedAtMs < staleBefore) {
      staleRefs.push(document.ref);
      return [];
    }
    return [{ref: document.ref, userId, installationId}];
  });
  await deleteReferences(staleRefs);
  if (registrations.length === 0) return;

  const deliveries = await Promise.all(registrations.map(async (registration) => {
    try {
      await getMessaging().send({
        fid: registration.installationId,
        data: {
          ...payload,
          recipientUid: registration.userId,
        },
        android: {
          priority: "high",
          ttl: 24 * 60 * 60 * 1_000,
          restrictedPackageName: APP_PACKAGE,
        },
      });
      return {registration, error: null};
    } catch (error) {
      return {registration, error};
    }
  }));

  const invalidRefs = deliveries
    .filter((delivery) => isInvalidRegistrationError(delivery.error))
    .map((delivery) => delivery.registration.ref);
  await deleteReferences(invalidRefs);

  const transientFailures = deliveries.filter(
    (delivery) => delivery.error && !isInvalidRegistrationError(delivery.error),
  );
  if (transientFailures.length > 0) {
    logger.error("Push delivery had transient failures", {
      category: payload.category,
      failures: transientFailures.length,
      recipients: registrations.length,
    });
    throw new Error("Push delivery failed and will be retried");
  }

  logger.info("Push delivery completed", {
    category: payload.category,
    recipients: registrations.length,
    invalidRegistrations: invalidRefs.length,
  });
}

async function deleteRegistrationsForUser(userId: string): Promise<void> {
  while (true) {
    const snapshot = await getFirestore().collection("pushRegistrations")
      .where("userId", "==", userId)
      .limit(400)
      .get();
    if (snapshot.empty) return;
    await deleteReferences(snapshot.docs.map((document) => document.ref));
  }
}

async function deleteReferences(
  references: readonly FirebaseFirestore.DocumentReference[],
): Promise<void> {
  if (references.length === 0) return;
  const batch = getFirestore().batch();
  references.forEach((reference) => batch.delete(reference));
  await batch.commit();
}

function isInvalidRegistrationError(error: unknown): boolean {
  if (!error || typeof error !== "object" || !("code" in error)) return false;
  return error.code === "messaging/installation-id-not-registered" ||
    error.code === "messaging/registration-token-not-registered" ||
    error.code === "messaging/invalid-registration-token";
}

function stringOrNull(value: unknown): string | null {
  return typeof value === "string" && value.length > 0 && value.length <= 128 ?
    value :
    null;
}

function safePositiveInteger(value: unknown): number {
  return Number.isSafeInteger(value) && (value as number) > 0 ?
    value as number :
    0;
}

function safeNonNegativeInteger(value: unknown): number {
  return Number.isSafeInteger(value) && (value as number) >= 0 ?
    value as number :
    0;
}
