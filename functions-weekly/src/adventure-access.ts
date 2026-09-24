import {DocumentSnapshot, Firestore, Transaction} from "firebase-admin/firestore";
import {HttpsError} from "firebase-functions/v2/https";
import {isValidGroupId} from "./weekly-summary.js";

export function documentEpoch(snapshot: DocumentSnapshot): string {
  if (!snapshot.createTime) throw new HttpsError("permission-denied", "Current members only");
  return `${snapshot.createTime.seconds}:${snapshot.createTime.nanoseconds}`;
}

export function adventureRefs(db: Firestore, uid: string, groupId: unknown, adventureId?: unknown) {
  if (!uid || uid.includes("/") || uid.length > 128) throw new HttpsError("unauthenticated", "Sign in first");
  if (!isValidGroupId(groupId) || (adventureId !== undefined && !isValidGroupId(adventureId))) {
    throw new HttpsError("invalid-argument", "Invalid adventure");
  }
  const group = db.doc(`groups/${groupId}`);
  return {group, member: db.doc(`memberships/${uid}_${groupId}`),
    rollout: db.doc("featureRollouts/togetherTrail"),
    adventure: adventureId === undefined ? null : group.collection("adventures").doc(adventureId as string)};
}

/** Export deliberately remains available when rollout is paused. */
export async function readAdventureAccess(tx: Transaction, refs: ReturnType<typeof adventureRefs>,
  requireRollout: boolean) {
  const [group, member, rollout] = await tx.getAll(refs.group, refs.member, refs.rollout);
  if (!group.exists || group.get("deletionPending") === true || !member.exists) {
    throw new HttpsError("permission-denied", "Current members only");
  }
  if (requireRollout && !(rollout.get("enabled") === true && (rollout.get("publicEnrollment") === true ||
      (Array.isArray(rollout.get("groupIds")) && rollout.get("groupIds").includes(group.id))))) {
    throw new HttpsError("failed-precondition", "Adventure unavailable", {reason: "feature_disabled"});
  }
  const adventure = refs.adventure ? await tx.get(refs.adventure) : null;
  if (refs.adventure && (!adventure?.exists || adventure.get("groupEpoch") !== documentEpoch(group))) {
    throw new HttpsError("not-found", "Adventure unavailable");
  }
  return {group, member, adventure, epoch: documentEpoch(member), groupEpoch: documentEpoch(group)};
}
