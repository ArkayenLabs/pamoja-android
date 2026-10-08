import {
  FieldPath,
  FieldValue,
  QueryDocumentSnapshot,
  Timestamp,
  getFirestore,
} from "firebase-admin/firestore";
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
  Registration,
  SIGNAL_RETENTION_MS,
  TRAY_DELIVERY_VERSION,
  buildFcmMessage,
  buildGoalReachedPush,
  buildMemberJoinedPush,
  buildSignalPush,
  inQuietHours,
  isMinuteOfDay,
  isValidTimeZone,
  localClock,
  parseRegistration,
  parseSignal,
  walkedOn,
  isValidAppVersion,
  isValidGroupId,
  isValidInstallationId,
  isValidIsoDate,
} from "./notification-domain.js";
import {
  EVENING_HOUR,
  RECAP_HOUR,
  ReminderCandidate,
  addDays,
  daysLeftIn,
  groupToday,
  planReminder,
} from "./reminders.js";

const APP_PACKAGE = "com.arkayenlabs.pamoja";
const FUNCTION_REGION = "asia-south1";
const REGISTRATION_MAX_AGE_MS = 35 * 24 * 60 * 60 * 1_000;
const MAX_REGISTRATIONS_PER_GROUP = 100;
const EVENT_TTL_MS = 24 * 60 * 60 * 1_000;
/** A reminder that lands hours late, at night, is worse than none. */
const REMINDER_TTL_MS = 3 * 60 * 60 * 1_000;
const RUNTIME_OPTIONS = {
  maxInstances: 10,
  memory: "256MiB" as const,
  region: FUNCTION_REGION,
  timeoutSeconds: 30,
};

interface PushRegistrationData {
  installationId?: unknown;
  appVersion?: unknown;
  delivery?: unknown;
  timeZone?: unknown;
  quietStartMinute?: unknown;
  quietEndMinute?: unknown;
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

    // Optional, sent by app versions from 1.1.2: what they can draw, and
    // the local clock the scheduled reminders need. Absent means an older
    // build, which keeps getting data-only pushes and no reminders. Merged,
    // so the server's own reminderSent survives a re-registration; anything
    // an older build does not send is removed rather than left stale.
    const {delivery, timeZone, quietStartMinute, quietEndMinute} = request.data ?? {};
    const optional = (valid: boolean, value: unknown) => valid ? value : FieldValue.delete();
    const validDelivery = Number.isInteger(delivery) && (delivery as number) > 0 &&
      (delivery as number) < 100;
    await getFirestore().collection("pushRegistrations")
      .doc(installationId)
      .set({
        installationId,
        userId,
        platform: "android",
        appVersion,
        delivery: optional(validDelivery, delivery),
        timeZone: optional(isValidTimeZone(timeZone), timeZone),
        quietStartMinute: optional(isMinuteOfDay(quietStartMinute), quietStartMinute),
        quietEndMinute: optional(isMinuteOfDay(quietEndMinute), quietEndMinute),
        updatedAt: FieldValue.serverTimestamp(),
      }, {merge: true});

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

/**
 * Pushes a cheer or reminder ("nudge" on the wire) to its recipient, and
 * stamps the expiry that a TTL policy on the signals collection group's
 * expireAt field uses to delete it after two weeks.
 *
 * Nothing is sent if either person has left the group since, and a reminder
 * is dropped if the recipient's own figures already show a walk that day.
 */
export const notifySignalCreated = onDocumentCreated(
  {
    ...RUNTIME_OPTIONS,
    document: "groups/{groupId}/signals/{signalId}",
    retry: true,
  },
  async (event) => {
    const snapshot = event.data;
    const groupId = event.params.groupId;
    const signal = parseSignal(snapshot?.data());
    if (!snapshot || !signal || !isValidGroupId(groupId)) return;

    const createdAt = snapshot.get("createdAt");
    const createdAtMs = createdAt instanceof Timestamp ? createdAt.toMillis() : Date.now();
    await snapshot.ref.update({expireAt: Timestamp.fromMillis(createdAtMs + SIGNAL_RETENTION_MS)});

    const firestore = getFirestore();
    const [sender, recipient, group] = await firestore.getAll(
      firestore.collection("memberships").doc(`${signal.fromUserId}_${groupId}`),
      firestore.collection("memberships").doc(`${signal.toUserId}_${groupId}`),
      firestore.collection("groups").doc(groupId),
    );
    if (!sender.exists || !recipient.exists || !group.exists) return;
    if (signal.kind === "nudge" && walkedOn(recipient.data(), signal.date)) return;

    await sendToUsers(
      [signal.toUserId],
      buildSignalPush({
        signal,
        groupId,
        senderName: sender.get("displayName"),
        groupName: group.get("name"),
      }),
    );
  },
);

/**
 * Deletes what a departing member sent and received in that group, so an
 * account that leaves or is deleted is not left behind in other people's
 * cheers for the two weeks until expiry. Group deletion removes the whole
 * subcollection separately.
 */
export const eraseDepartedMemberSignals = onDocumentDeleted(
  {
    ...RUNTIME_OPTIONS,
    document: "memberships/{membershipId}",
    retry: true,
  },
  async (event) => {
    const userId = stringOrNull(event.data?.get("userId"));
    const groupId = stringOrNull(event.data?.get("groupId"));
    if (!userId || !groupId || !isValidGroupId(groupId)) return;

    const signals = getFirestore().collection("groups").doc(groupId).collection("signals");
    for (const field of ["fromUserId", "toUserId"]) {
      while (true) {
        const snapshot = await signals.where(field, "==", userId).limit(400).get();
        if (snapshot.empty) break;
        await deleteReferences(snapshot.docs.map((document) => document.ref));
      }
    }
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
    const registration = parseRegistration(document.data());
    if (!registration || updatedAtMillis(document) < staleBefore) {
      staleRefs.push(document.ref);
      return [];
    }
    return [{ref: document.ref, registration}];
  });
  await deleteReferences(staleRefs);
  if (registrations.length === 0) return;

  const nowMs = Date.now();
  const deliveries = await Promise.all(registrations.map(async (registration) => {
    try {
      await getMessaging().send(buildFcmMessage(registration.registration, payload, nowMs, {
        packageName: APP_PACKAGE,
        ttlMs: EVENT_TTL_MS,
      }));
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

/**
 * The scheduled reminder and weekly recap (reminders.ts), for app versions
 * that registered a delivery version, a time zone and quiet hours. Runs
 * hourly; each installation is considered only in the hour its own clock
 * reads RECAP_HOUR or EVENING_HOUR, and hears at most one a day.
 */
export const sendScheduledReminders = onSchedule(
  {
    ...RUNTIME_OPTIONS,
    schedule: "5 * * * *",
    timeZone: "Etc/UTC",
    timeoutSeconds: 300,
  },
  async () => {
    const firestore = getFirestore();
    const nowMs = Date.now();
    const staleBefore = nowMs - REGISTRATION_MAX_AGE_MS;
    const candidatesByUser = new Map<string, Promise<ReminderCandidate[]>>();
    let considered = 0;
    let sent = 0;
    let cursor: QueryDocumentSnapshot | undefined;

    while (true) {
      let query = firestore.collection("pushRegistrations")
        .where("delivery", ">=", TRAY_DELIVERY_VERSION)
        .orderBy("delivery")
        .orderBy(FieldPath.documentId())
        .limit(300);
      if (cursor) query = query.startAfter(cursor);
      const page = await query.get();
      if (page.empty) break;
      cursor = page.docs[page.docs.length - 1];

      for (const document of page.docs) {
        const registration = parseRegistration(document.data());
        if (!registration?.timeZone || updatedAtMillis(document) < staleBefore) continue;
        const clock = localClock(nowMs, registration.timeZone);
        const localHour = Math.floor(clock.minuteOfDay / 60);
        if (localHour !== RECAP_HOUR && localHour !== EVENING_HOUR) continue;
        if (inQuietHours(registration, nowMs)) continue;
        const reminderSent = sentLog(document.get("reminderSent"));
        if (reminderSent.day === clock.date) continue;
        considered++;

        let candidates = candidatesByUser.get(registration.userId);
        if (!candidates) {
          candidates = loadCandidates(registration, clock.date, nowMs);
          candidatesByUser.set(registration.userId, candidates);
        }
        const plan = planReminder({
          userId: registration.userId,
          localHour,
          phoneToday: clock.date,
          nowMs,
          localDateIn: (zone) => localClock(nowMs, zone).date,
          candidates: await candidates,
          sent: reminderSent,
        });
        if (!plan) continue;

        if (await sendOne(document.ref, registration, plan.push, nowMs)) {
          sent++;
          await document.ref.update({
            reminderSent: {...reminderSent, day: clock.date, [plan.kind]: clock.date},
          });
        }
      }
    }
    logger.info("Scheduled reminders completed", {considered, sent});
  },
);

/** The user's groups, with what the planner needs, read once per run. */
async function loadCandidates(
  registration: Registration,
  phoneToday: string,
  nowMs: number,
): Promise<ReminderCandidate[]> {
  const firestore = getFirestore();
  const memberships = await firestore.collection("memberships")
    .where("userId", "==", registration.userId)
    .limit(20)
    .get();
  const groupIds = memberships.docs
    .map((document) => stringOrNull(document.get("groupId")))
    .filter((groupId): groupId is string => groupId !== null && isValidGroupId(groupId));
  if (groupIds.length === 0) return [];
  const groups = await firestore.getAll(
    ...groupIds.map((groupId) => firestore.collection("groups").doc(groupId)),
  );

  return Promise.all(groups.filter((group) => group.exists).map(async (group) => {
    const membership = memberships.docs.find((document) => document.get("groupId") === group.id);
    const base = {
      groupId: group.id,
      name: group.get("name"),
      weeklyTarget: safePositiveInteger(group.get("weeklyTarget")),
      weeklySteps: safeNonNegativeInteger(group.get("weeklySteps")),
      weekStart: stringOrEmpty(group.get("weekStart")),
      weekStartDay: group.get("weekStartDay"),
      planningTimeZone: group.get("planningTimeZone"),
      memberCount: safeNonNegativeInteger(group.get("memberCount")),
      createdAtMs: safeNonNegativeInteger(group.get("createdAt")),
      streakWeeks: safeNonNegativeInteger(group.get("streakWeeks")),
      streakThroughWeek: stringOrEmpty(group.get("streakThroughWeek")),
      lastShieldedWeek: stringOrEmpty(group.get("lastShieldedWeek")),
      previousWeekTotal: null as number | null,
    };
    // Only the recap reads the finished week, and only on the week's first day.
    const today = groupToday(base, phoneToday, (zone) => localClock(nowMs, zone).date);
    if (daysLeftIn(base.weekStartDay, today) === 7) {
      const summary = await group.ref.collection("weeks").doc(addDays(today, -7)).get();
      if (summary.exists) base.previousWeekTotal = safeNonNegativeInteger(summary.get("totalSteps"));
    }
    return {
      group: base,
      membership: {
        weeklySteps: safeNonNegativeInteger(membership?.get("weeklySteps")),
        weekStart: stringOrEmpty(membership?.get("weekStart")),
        todaySteps: safeNonNegativeInteger(membership?.get("todaySteps")),
        todayDate: stringOrEmpty(membership?.get("todayDate")),
      },
    };
  }));
}

/** Sends one scheduled push; false (and a dead registration removed) when it fails. */
async function sendOne(
  ref: FirebaseFirestore.DocumentReference,
  registration: Registration,
  push: PushPayload,
  nowMs: number,
): Promise<boolean> {
  try {
    await getMessaging().send(buildFcmMessage(registration, push, nowMs, {
      packageName: APP_PACKAGE,
      ttlMs: REMINDER_TTL_MS,
    }));
    return true;
  } catch (error) {
    if (isInvalidRegistrationError(error)) {
      await deleteReferences([ref]);
    } else {
      // Not retried: the hour has passed, and a late reminder is no reminder.
      logger.warn("Scheduled reminder not delivered", {category: push.category});
    }
    return false;
  }
}

function sentLog(value: unknown): Record<string, string> {
  if (typeof value !== "object" || value === null) return {};
  return Object.fromEntries(Object.entries(value as Record<string, unknown>)
    .filter((entry): entry is [string, string] => isValidIsoDate(entry[1])));
}

function updatedAtMillis(document: FirebaseFirestore.DocumentSnapshot): number {
  const updatedAt = document.get("updatedAt");
  return updatedAt instanceof Timestamp ? updatedAt.toMillis() : 0;
}

function stringOrEmpty(value: unknown): string {
  return typeof value === "string" ? value : "";
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
