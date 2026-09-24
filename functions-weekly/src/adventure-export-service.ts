import {FieldPath, Firestore} from "firebase-admin/firestore";
import {HttpsError} from "firebase-functions/v2/https";
import {adventureRefs, readAdventureAccess} from "./adventure-access.js";
import {isValidGroupId} from "./weekly-summary.js";

interface ExportInput {
  groupId?: unknown; adventureId?: unknown; kind?: unknown; segmentId?: unknown; after?: unknown;
}

/** Bounded pages for the account export. Identity always comes from Firebase
 * Auth; no caller-supplied owner is accepted. Rollout/paid expiry cannot block
 * access to one's retained records. Sync ticket capabilities are never exposed. */
export async function exportAdventurePage(db: Firestore, uid: string, input: ExportInput) {
  const refs = adventureRefs(db, uid, input.groupId, input.adventureId);
  const kind = input.kind;
  if (!["adventures", "participant", "segments", "credits", "commitments"].includes(String(kind)) ||
      (input.after !== undefined && (typeof input.after !== "string" || input.after.length > 128 ||
        !/^[a-zA-Z0-9_-]+$/.test(input.after)))) {
    throw new HttpsError("invalid-argument", "Invalid export page");
  }
  return db.runTransaction(async (tx) => {
    const ctx = await readAdventureAccess(tx, refs, false);
    if (kind === "adventures") {
      let query = refs.group.collection("adventures").orderBy(FieldPath.documentId()).limit(50);
      if (input.after) query = query.startAfter(input.after);
      const page = await tx.get(query);
      return {records: page.docs.filter((doc) => doc.get("groupEpoch") === ctx.groupEpoch).map((doc) => ({
        id: doc.id, routeId: doc.get("routeId"), status: doc.get("status"),
      })), next: page.size === 50 ? page.docs[49].id : null};
    }
    if (!refs.adventure) throw new HttpsError("invalid-argument", "Adventure required");
    const participant = refs.adventure.collection("participants").doc(uid);
    if (kind === "participant") {
      const person = await tx.get(participant);
      return {records: person.get("membershipEpoch") === ctx.epoch ? [{id: uid, ...person.data()}] : [], next: null};
    }
    if (kind === "credits" && !isValidGroupId(input.segmentId)) {
      throw new HttpsError("invalid-argument", "Segment required");
    }
    let collection = participant.collection(kind === "segments" ? "segments" : "commitments");
    if (kind === "credits") {
      const segment = participant.collection("segments").doc(input.segmentId as string);
      const source = await tx.get(segment);
      if (source.get("membershipEpoch") !== ctx.epoch) throw new HttpsError("not-found", "Source unavailable");
      collection = segment.collection("credits");
    }
    // Filter each bounded page rather than requiring another composite index.
    let query = collection.orderBy(FieldPath.documentId()).limit(50);
    if (input.after) query = query.startAfter(input.after);
    const page = await tx.get(query);
    return {records: page.docs.filter((doc) => kind === "credits" || doc.get("membershipEpoch") === ctx.epoch)
      .map((doc) => ({id: doc.id, ...doc.data()})), next: page.size === 50 ? page.docs[49].id : null};
  });
}
