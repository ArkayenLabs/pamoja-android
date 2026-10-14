import {FieldValue, getFirestore} from "firebase-admin/firestore";
import {getStorage} from "firebase-admin/storage";
import {onDocumentDeleted} from "firebase-functions/v2/firestore";
import {HttpsError, onCall} from "firebase-functions/v2/https";

import {isValidGroupId} from "./notification-domain.js";

const FUNCTION_REGION = "asia-south1";
const RUNTIME_OPTIONS = {
  enforceAppCheck: true,
  maxInstances: 10,
  memory: "256MiB" as const,
  region: FUNCTION_REGION,
  timeoutSeconds: 120,
};

interface DeleteGroupData {
  groupId?: unknown;
}

/**
 * Permanently removes one group through a server-owned boundary.
 *
 * Firestore does not cascade a document deletion. Doing this from the Android
 * client would leave memberships, completed weeks, access projections and the
 * group photo behind. The parent is marked first to stop joins, then deleted
 * last so an interrupted request can be retried safely.
 */
export const deleteGroup = onCall<DeleteGroupData>(
  RUNTIME_OPTIONS,
  async (request) => {
    const userId = request.auth?.uid;
    if (!userId) {
      throw new HttpsError("unauthenticated", "Sign in to delete a group.");
    }

    const groupId = request.data?.groupId;
    if (!isValidGroupId(groupId)) {
      throw new HttpsError("invalid-argument", "The group is invalid.");
    }

    const firestore = getFirestore();
    const groupRef = firestore.collection("groups").doc(groupId);

    await firestore.runTransaction(async (transaction) => {
      const group = await transaction.get(groupRef);
      // Idempotent success covers a reply lost after the final delete.
      if (!group.exists) return;
      if (group.get("adminId") !== userId) {
        throw new HttpsError(
          "permission-denied",
          "Only the group admin can delete this group.",
        );
      }
      transaction.update(groupRef, {
        deletionPending: true,
        inviteLinkActive: false,
        deletionRequestedAt: FieldValue.serverTimestamp(),
      });
    });

    const markedGroup = await groupRef.get();
    if (!markedGroup.exists) return {deleted: true};
    if (markedGroup.get("adminId") !== userId) {
      throw new HttpsError(
        "permission-denied",
        "Only the group admin can delete this group.",
      );
    }

    await markDeleted(groupId);
    await finishGroupDeletion(groupId);
    await groupRef.delete();
    return {deleted: true};
  },
);

/**
 * Finishes a group's deletion however the document went.
 *
 * deleteGroup above does this before removing the document. Account deletion
 * by a group's last member deletes the document from the app instead, and
 * until 2026-10-13 that left the group photo in Storage, the group's
 * subcollections, its access and billing projections, and any memberships
 * still pointing at it. Every step is idempotent, so after deleteGroup this
 * costs a few empty reads.
 */
export const cleanUpDeletedGroup = onDocumentDeleted(
  {
    document: "groups/{groupId}",
    region: FUNCTION_REGION,
    maxInstances: 10,
    memory: "256MiB",
    timeoutSeconds: 120,
    retry: true,
  },
  async (event) => {
    const groupId = event.params.groupId;
    if (!isValidGroupId(groupId)) return;
    await markDeleted(groupId);
    await finishGroupDeletion(groupId);
  },
);

/**
 * A tombstone, read by the groups create rule. A group id is the permanent
 * invite key in every link and QR code ever shared, so without one anyone who
 * knew a deleted group's id could create a new group under it and receive
 * its old invites. Holds the id and when, nothing about anyone.
 */
async function markDeleted(groupId: string): Promise<void> {
  await getFirestore().collection("deletedGroups").doc(groupId).set({
    deletedAt: FieldValue.serverTimestamp(),
  });
}

async function finishGroupDeletion(groupId: string): Promise<void> {
  const firestore = getFirestore();
  const groupRef = firestore.collection("groups").doc(groupId);

  await removeBillingReferences(groupId);
  await deleteMemberships(groupId);

  const childCollections = await groupRef.listCollections();
  for (const childCollection of childCollections) {
    await firestore.recursiveDelete(childCollection);
  }

  await getStorage().bucket().deleteFiles({
    prefix: `group_avatars/${groupId}/`,
  });
}

async function removeBillingReferences(groupId: string): Promise<void> {
  const firestore = getFirestore();
  const billingRef = firestore.collection("groupBilling").doc(groupId);
  const accessRef = firestore.collection("groupAccess").doc(groupId);
  const previewRef = firestore.collection("groupPreviews").doc(groupId);

  await firestore.runTransaction(async (transaction) => {
    const billing = await transaction.get(billingRef);
    const sponsorUid = stringOrNull(billing.get("sponsorUid"));
    const accountRef = sponsorUid ?
      firestore.collection("billingAccounts").doc(sponsorUid) : null;
    const viewRef = sponsorUid ?
      firestore.collection("billingViews").doc(sponsorUid) : null;
    const [account, view] = accountRef && viewRef ?
      await Promise.all([
        transaction.get(accountRef),
        transaction.get(viewRef),
      ]) : [null, null];

    transaction.delete(billingRef);
    transaction.delete(accessRef);
    transaction.delete(previewRef);

    if (accountRef && account) {
      const remainingGroupIds = sponsoredGroupIds(account).filter((id) => id !== groupId);
      transaction.set(accountRef, {
        sponsoredGroupId: remainingGroupIds[0] ?? null,
        sponsoredGroupIds: remainingGroupIds,
        updatedAt: FieldValue.serverTimestamp(),
      }, {merge: true});
    }
    if (viewRef && view) {
      const remainingGroupIds = sponsoredGroupIds(view).filter((id) => id !== groupId);
      transaction.set(viewRef, {
        sponsoredGroupId: remainingGroupIds[0] ?? null,
        sponsoredGroupIds: remainingGroupIds,
        // ?? null: an absent field reads as undefined, which Firestore
        // refuses, and that would abort the whole deletion.
        groupLeaseValidUntil: remainingGroupIds.length > 0 ?
          view.get("groupLeaseValidUntil") ?? null : null,
        updatedAt: FieldValue.serverTimestamp(),
      }, {merge: true});
    }
  });
}

async function deleteMemberships(groupId: string): Promise<void> {
  const firestore = getFirestore();
  while (true) {
    const memberships = await firestore.collection("memberships")
      .where("groupId", "==", groupId)
      .limit(400)
      .get();
    if (memberships.empty) return;

    const batch = firestore.batch();
    memberships.docs.forEach((membership) => batch.delete(membership.ref));
    await batch.commit();
  }
}

function stringOrNull(value: unknown): string | null {
  return typeof value === "string" && value.length > 0 ? value : null;
}

function sponsoredGroupIds(snapshot: FirebaseFirestore.DocumentSnapshot): string[] {
  const values = snapshot.get("sponsoredGroupIds");
  if (Array.isArray(values)) {
    return [...new Set(values.filter((value): value is string =>
      typeof value === "string" && value.length > 0))];
  }
  const legacy = stringOrNull(snapshot.get("sponsoredGroupId"));
  return legacy ? [legacy] : [];
}
