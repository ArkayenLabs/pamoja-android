import {FieldPath, FieldValue, Firestore} from "firebase-admin/firestore";
import {isValidGroupId} from "./weekly-summary.js";

/** Called with the deleted membership's createTime, never the delivery time.
 * A delayed/retried departure event must not erase a subsequent membership.
 * No group total is reduced: only personal attribution/source records leave. */
export async function eraseDepartedAdventureData(db: Firestore, groupId: string,
  userId: string, deletedMembershipEpoch: string): Promise<void> {
  if (!isValidGroupId(groupId) || !userId || userId.includes("/") || userId.length > 128 ||
      !/^\d+:\d+$/.test(deletedMembershipEpoch)) throw new Error("Invalid departure context");
  const adventures = db.collection(`groups/${groupId}/adventures`);
  let cursor: string | undefined;
  for (;;) {
    const query = adventures.orderBy(FieldPath.documentId()).limit(50);
    const page = await (cursor ? query.startAfter(cursor) : query).get();
    if (page.empty) break;
    for (const adventure of page.docs) {
      const participant = adventure.ref.collection("participants").doc(userId);
      const ticket = participant.collection("syncTickets").doc("current");
      // Rejoining writes a new epoch. This transaction conflicts/retries with
      // that write and therefore cannot delete the new participant or ticket.
      await db.runTransaction(async (tx) => {
        const [person, pending, currentAdventure] = await tx.getAll(participant, ticket, adventure.ref);
        if (person.get("membershipEpoch") === deletedMembershipEpoch) tx.delete(participant);
        if (pending.get("membershipEpoch") === deletedMembershipEpoch) tx.delete(ticket);
        if (currentAdventure.get("createdBy") === userId &&
            currentAdventure.get("creatorMembershipEpoch") === deletedMembershipEpoch) {
          tx.update(adventure.ref, {createdBy: FieldValue.delete(), creatorMembershipEpoch: FieldValue.delete()});
        }
      });
      // Segment IDs are immutable UUIDs. Only old-epoch segments are removed;
      // a concurrent new join creates a distinct segment. Query also works when
      // the participant parent has already been deleted by an earlier retry.
      for (;;) {
        const segments = await participant.collection("segments")
          .where("membershipEpoch", "==", deletedMembershipEpoch).limit(50).get();
        if (segments.empty) break;
        for (const segment of segments.docs) await db.recursiveDelete(segment.ref);
      }
      for (;;) {
        const commitments = await participant.collection("commitments")
          .where("membershipEpoch", "==", deletedMembershipEpoch).limit(50).get();
        if (commitments.empty) break;
        const batch = db.batch();
        // A rejoin can replace the same week's document between query and
        // delete. Fail/retry cleanup rather than erasing that new answer.
        commitments.docs.forEach((record) => batch.delete(record.ref, {lastUpdateTime: record.updateTime}));
        await batch.commit();
      }
    }
    cursor = page.docs[page.docs.length - 1].id;
  }
}

/** Firestore does not delete descendants when a group parent is deleted.
 * Scope cleanup to the deleted group's generation, including delayed events
 * where a group with the same ID has subsequently been created. */
export async function eraseDeletedGroupAdventures(db: Firestore, groupId: string,
  deletedGroupEpoch: string): Promise<void> {
  if (!isValidGroupId(groupId) || !/^\d+:\d+$/.test(deletedGroupEpoch)) throw new Error("Invalid group deletion context");
  const group = db.doc(`groups/${groupId}`);
  for (;;) {
    const page = await group.collection("adventures")
      .where("groupEpoch", "==", deletedGroupEpoch).limit(50).get();
    if (page.empty) break;
    for (const adventure of page.docs) await db.recursiveDelete(adventure.ref);
  }
  const state = group.collection("adventureState").doc("current");
  await db.runTransaction(async (tx) => {
    const snapshot = await tx.get(state);
    if (snapshot.get("groupEpoch") === deletedGroupEpoch) tx.delete(state);
  });
}
