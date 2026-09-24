import {initializeApp} from "firebase-admin/app";
import {FieldValue, Timestamp, getFirestore} from "firebase-admin/firestore";
import {logger, setGlobalOptions} from "firebase-functions/v2";
import {onCall, HttpsError} from "firebase-functions/v2/https";
import {onSchedule} from "firebase-functions/v2/scheduler";
import {getPlanningWindow, saveAuthoritativePlan, applyDuePlan} from "./planning-service.js";
import {
  onDocumentDeleted,
  onDocumentUpdated,
  onDocumentWritten,
} from "firebase-functions/v2/firestore";

import {
  buildGroupWeekSummary,
  datesInWeek,
  isNormalWeekRollover,
  isValidGroupId,
  redactUserFromContributions,
} from "./weekly-summary.js";
import {buildCirclePreviewSeed} from "./circle-preview.js";
import {eraseDepartedAdventureData, eraseDeletedGroupAdventures} from "./adventure-privacy.js";
import {exportAdventurePage} from "./adventure-export-service.js";
import {startTogetherTrail} from "./adventure-start-service.js";
import {readAdventure, readAdventureEntry, saveAdventureCommitment} from "./adventure-commitment-service.js";
import {prepareAdventureSync, submitAdventureSync, pauseAdventureContribution,
  finalizeAdventure} from "./adventure-sync-service.js";
import {
  countNextWeekResponses,
  scheduledTargetForWeek,
} from "./next-week-plan.js";

initializeApp();

setGlobalOptions({
  // Must remain aligned with Firestore and the already-live functions. Omitting
  // this silently changes discovery to us-central1 and makes Firebase treat an
  // update as two new functions plus two destructive deletions.
  region: "asia-south1",
  maxInstances: 10,
  memory: "256MiB",
  timeoutSeconds: 30,
});

const firestore = getFirestore();

// Read-only export is independent of enrollment and subscription state.
export const exportTogetherTrailPage = onCall({enforceAppCheck: true}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first");
  return exportAdventurePage(firestore, request.auth.uid, request.data ?? {});
});

export const getTogetherTrailEntry = onCall({enforceAppCheck: true}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first");
  return readAdventureEntry(firestore, request.auth.uid, request.data?.groupId);
});

export const getTogetherTrail = onCall({enforceAppCheck: true}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first");
  return readAdventure(firestore, request.auth.uid, request.data ?? {});
});

export const startTogetherTrailAdventure = onCall({enforceAppCheck: true}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first");
  return startTogetherTrail(firestore, request.auth.uid, request.data ?? {});
});

export const saveTogetherTrailCommitment = onCall({enforceAppCheck: true}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first");
  return saveAdventureCommitment(firestore, request.auth.uid, request.data ?? {});
});

export const prepareTogetherTrailSync = onCall({enforceAppCheck: true}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first");
  return prepareAdventureSync(firestore, request.auth.uid, request.data ?? {});
});

export const submitTogetherTrailSync = onCall({enforceAppCheck: true}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first");
  return submitAdventureSync(firestore, request.auth.uid, request.data ?? {});
});

export const pauseTogetherTrailContribution = onCall({enforceAppCheck: true}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first");
  return pauseAdventureContribution(firestore, request.auth.uid, request.data ?? {});
});

export const finalizeTogetherTrailAdventure = onCall({enforceAppCheck: true}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first");
  return finalizeAdventure(firestore, request.auth.uid, request.data ?? {});
});

export const getNextWeekPlanningWindow = onCall({enforceAppCheck: true}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first");
  return getPlanningWindow(firestore, request.auth.uid, request.data?.groupId, request.data?.timeZone);
});

export const saveNextWeekPlan = onCall({enforceAppCheck: true}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in first");
  await saveAuthoritativePlan(firestore, request.auth.uid, request.data ?? {});
  return {saved: true};
});

// Bounded pages prevent a long outage from creating one unbounded invocation.
// Applied/missed documents leave the query; the next run continues the backlog.
export const applyScheduledWeekPlans = onSchedule("every 5 minutes", async () => {
  const due = await firestore.collectionGroup("nextWeekPlans")
    .where("schemaVersion", "==", 2).where("status", "==", "scheduled")
    .where("appliesAt", "<=", Timestamp.now()).orderBy("appliesAt").limit(100).get();
  for (const plan of due.docs) await applyDuePlan(firestore, plan.ref);
});

/**
 * Finalizes the previous group week when the existing display cache rolls to a
 * new week. The trigger is intentionally idempotent because Firestore events
 * are delivered at least once and their ordering is not guaranteed.
 *
 * Daily documents are read only by the Admin SDK. The stored summary contains
 * user IDs and weekly totals, never profile fields or raw day-by-day history.
 */
export const finalizeCompletedGroupWeek = onDocumentUpdated(
  "groups/{groupId}",
  async (event) => {
    const change = event.data;
    if (!change) return;

    const before = change.before.data();
    const after = change.after.data();
    const previousWeekStart = before.weekStart;
    const nextWeekStart = after.weekStart;
    if (!isNormalWeekRollover(previousWeekStart, nextWeekStart)) return;

    const groupId = event.params.groupId;
    if (!isValidGroupId(groupId)) {
      logger.warn("Skipped weekly summary for malformed group ID");
      return;
    }

    const summaryRef = firestore
      .collection("groups")
      .doc(groupId)
      .collection("weeks")
      .doc(previousWeekStart);
    const previewRef = firestore.collection("groupPreviews").doc(groupId);
    const nextWeekPlanRef = firestore
      .collection("groups")
      .doc(groupId)
      .collection("nextWeekPlans")
      .doc(nextWeekStart);

    // Most duplicate deliveries finish here without repeating the roster and
    // daily-document reads. The transaction below remains the final race guard.
    if ((await summaryRef.get()).exists) return;

    const membershipSnapshot = await firestore
      .collection("memberships")
      .where("groupId", "==", groupId)
      .limit(20)
      .get();
    const members = membershipSnapshot.docs.flatMap((document) => {
      const userId = document.get("userId");
      return typeof userId === "string" ? [{userId, ref: document.ref}] : [];
    });
    const memberUserIds = members.map((member) => member.userId);
    const dates = datesInWeek(previousWeekStart);
    const stepReferences = memberUserIds.flatMap((userId) =>
      dates.map((date) => firestore.collection("steps").doc(`${userId}_${date}`)),
    );
    const stepSnapshots = stepReferences.length > 0 ?
      await firestore.getAll(...stepReferences) :
      [];
    const stepCountsByDocumentId: Record<string, unknown> = {};
    stepSnapshots.forEach((snapshot) => {
      if (snapshot.exists) {
        stepCountsByDocumentId[snapshot.id] = snapshot.get("stepCount");
      }
    });

    const previewStartedAtMillis = Date.now();
    await firestore.runTransaction(async (transaction) => {
      const groupRef = firestore.collection("groups").doc(groupId);
      const [currentGroup, existingSummary, existingPreview, nextWeekPlan, previousGoal] = await Promise.all([
        transaction.get(groupRef),
        transaction.get(summaryRef),
        transaction.get(previewRef),
        transaction.get(nextWeekPlanRef),
        transaction.get(groupRef.collection("weekGoals").doc(previousWeekStart)),
      ]);
      if (!currentGroup.exists || existingSummary.exists) return;

      // The roster query and raw-step reads happened outside the transaction.
      // Re-reading those exact memberships here closes the race where somebody
      // leaves after the query but before history is created.
      const currentMemberships = members.length > 0 ?
        await transaction.getAll(...members.map((member) => member.ref)) :
        [];
      const currentMemberUserIds = currentMemberships.flatMap((snapshot, index) =>
        snapshot.exists ? [members[index].userId] : [],
      );
      const summary = buildGroupWeekSummary({
        groupId,
        weekStart: previousWeekStart,
        targetSteps: previousGoal.exists ? previousGoal.get("targetSteps") : before.weeklyTarget,
        memberUserIds: currentMemberUserIds,
        stepCountsByDocumentId,
      });

      transaction.create(summaryRef, {
        ...summary,
        finalizedAt: FieldValue.serverTimestamp(),
      });
      const preview = buildCirclePreviewSeed(summary, previewStartedAtMillis);
      if (preview && !existingPreview.exists) {
        transaction.create(previewRef, {
          schemaVersion: preview.schemaVersion,
          groupId: preview.groupId,
          featureSet: preview.featureSet,
          eligibleWeekStart: preview.eligibleWeekStart,
          startedAt: Timestamp.fromMillis(preview.startedAtMillis),
          validUntil: Timestamp.fromMillis(preview.validUntilMillis),
          createdAt: FieldValue.serverTimestamp(),
        });
      }

      // A plan is a promise for the incoming week, never an edit to the week
      // that was just summarized. Applying it in this transaction makes the
      // rollover, summary and target change one atomic operation.
      const scheduledTarget = scheduledTargetForWeek(
        nextWeekPlan.get("schemaVersion") === 1 ? nextWeekPlan.data() : undefined,
        groupId,
        nextWeekStart,
      );
      if (currentGroup.get("weekStart") === nextWeekStart && scheduledTarget != null) {
        transaction.update(groupRef, {weeklyTarget: scheduledTarget});
        transaction.update(nextWeekPlanRef, {
          status: "applied",
          appliedTargetSteps: scheduledTarget,
          appliedAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
        });
      }
    });
  },
);

/**
 * Maintains only aggregate availability on the plan. Individual answers remain
 * readable solely by their owner in Firestore rules. Recomputing inside a
 * transaction makes duplicate and concurrent trigger deliveries idempotent.
 */
export const aggregateNextWeekResponses = onDocumentWritten(
  "groups/{groupId}/nextWeekPlans/{weekStart}/responses/{userId}",
  async (event) => {
    if (!event.data) return;
    const {groupId, weekStart} = event.params;
    if (!isValidGroupId(groupId) ||
        typeof weekStart !== "string" ||
        !/^\d{4}-\d{2}-\d{2}$/.test(weekStart)) {
      logger.warn("Skipped malformed next-week response path");
      return;
    }

    const planRef = firestore.collection("groups").doc(groupId)
      .collection("nextWeekPlans").doc(weekStart);
    const responsesQuery = planRef.collection("responses").limit(20);
    await firestore.runTransaction(async (transaction) => {
      const plan = await transaction.get(planRef);
      const responses = await transaction.get(responsesQuery);
      if (!plan.exists ||
          plan.get("groupId") !== groupId ||
          plan.get("weekStart") !== weekStart) return;

      const counts = countNextWeekResponses(
        responses.docs.map((document) => document.data()),
      );
      transaction.update(planRef, {
        ...counts,
        responsesUpdatedAt: FieldValue.serverTimestamp(),
      });
    });
  },
);

/**
 * Redacts the direct user identifier from old group weeks when a membership
 * ends. Aggregate totals and counts remain part of the group's history, but a
 * former member's weekly health total no longer points back to their account.
 */
export const redactDepartedMemberFromGroupWeeks = onDocumentDeleted(
  "memberships/{membershipId}",
  async (event) => {
    const membership = event.data?.data();
    const userId = membership?.userId;
    const groupId = membership?.groupId;
    if (typeof userId !== "string" ||
        userId.length === 0 ||
        userId.length > 128 ||
        userId.includes("/") ||
        !isValidGroupId(groupId)) {
      logger.warn("Skipped malformed membership history redaction");
      return;
    }

    const groupRef = firestore.collection("groups").doc(groupId);
    const [weeks, plans] = await Promise.all([
      groupRef.collection("weeks").get(),
      groupRef.collection("nextWeekPlans").get(),
    ]);
    const bulkWriter = firestore.bulkWriter();
    weeks.docs.forEach((week) => {
      const contributions = week.get("contributions");
      if (!Array.isArray(contributions) ||
          !contributions.some((value) => value?.userId === userId)) {
        return;
      }
      bulkWriter.update(week.ref, {
        contributions: redactUserFromContributions(contributions, userId),
      });
    });
    // A departed member's availability answer is no longer relevant and must
    // not remain attached to their user ID. The aggregate trigger recalculates
    // the plan after each real deletion.
    plans.docs.forEach((plan) => {
      bulkWriter.delete(plan.ref.collection("responses").doc(userId));
    });
    await bulkWriter.close();
  },
);

export const eraseDepartedMemberAdventureData = onDocumentDeleted(
  {document: "memberships/{membershipId}", retry: true, timeoutSeconds: 540},
  async (event) => {
    const membership = event.data?.data();
    const created = event.data?.createTime;
    const userId = membership?.userId;
    if (created && isValidGroupId(membership?.groupId) && typeof userId === "string" &&
        userId.length > 0 && userId.length <= 128 && !userId.includes("/")) {
      await eraseDepartedAdventureData(firestore, membership!.groupId, userId,
        `${created.seconds}:${created.nanoseconds}`);
    }
  },
);

// Covers account deletion paths that remove the parent directly. Group admin
// deletion already recursively erases children; duplicate cleanup is safe.
export const eraseAdventuresAfterGroupDeletion = onDocumentDeleted(
  {document: "groups/{groupId}", retry: true, timeoutSeconds: 540},
  async (event) => {
    const created = event.data?.createTime;
    if (created && isValidGroupId(event.params.groupId)) {
      await eraseDeletedGroupAdventures(firestore, event.params.groupId,
        `${created.seconds}:${created.nanoseconds}`);
    }
  },
);
