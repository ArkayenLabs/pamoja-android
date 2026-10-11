import {getFirestore} from "firebase-admin/firestore";
import {HttpsError, onCall} from "firebase-functions/v2/https";

import {isValidGroupId} from "./notification-domain.js";

/**
 * What someone holding an invite may see before they sign in (REBUILD_PLAN.md
 * §6, decided 2026-09-26): the group's name, its size and goal, and the first
 * name of whoever started it. Nothing else, and never a photo, a member list
 * or anyone's steps.
 *
 * The code is the group ID, which is already the key that lets anyone who
 * signs in join, so this shows nothing that link does not already open. It
 * runs without auth but with App Check, so it answers the app and not a
 * script.
 */
export interface InvitePreview {
  name: string;
  memberCount: number;
  maxMemberCap: number;
  weeklyTarget: number;
  full: boolean;
  startedBy: string | null;
}

/** The preview for a group document, or null when there is nothing to join. */
export function invitePreviewFrom(
  group: Record<string, unknown> | undefined,
  adminDisplayName: unknown,
): InvitePreview | null {
  if (!group || group.deletionPending === true) return null;
  const name = typeof group.name === "string" ? group.name.trim() : "";
  if (name.length === 0) return null;
  const memberCount = count(group.memberCount);
  const maxMemberCap = count(group.maxMemberCap);
  return {
    name: name.slice(0, 80),
    memberCount,
    maxMemberCap,
    weeklyTarget: count(group.weeklyTarget),
    // The join itself enforces the cap; this only lets the preview explain it.
    full: group.inviteLinkActive === false || (maxMemberCap > 0 && memberCount >= maxMemberCap),
    startedBy: firstName(adminDisplayName),
  };
}

function count(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) && value > 0 ? Math.floor(value) : 0;
}

/** First word only, as Home shows names: "Ravi Sharma" is "Ravi". */
function firstName(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const first = value.trim().split(/\s+/)[0] ?? "";
  return first.length > 0 ? first.slice(0, 40) : null;
}

export const getInvitePreview = onCall<{code?: unknown}>(
  {
    enforceAppCheck: true,
    maxInstances: 5,
    memory: "256MiB",
    region: "asia-south1",
    timeoutSeconds: 20,
  },
  async (request) => {
    const code = request.data?.code;
    if (!isValidGroupId(code)) {
      throw new HttpsError("invalid-argument", "The invite is invalid.");
    }
    const firestore = getFirestore();
    const group = await firestore.collection("groups").doc(code).get();
    const adminId = group.get("adminId");
    const admin = typeof adminId === "string" && adminId.length > 0 ?
      await firestore.collection("memberships").doc(`${adminId}_${code}`).get() :
      null;
    const preview = invitePreviewFrom(group.data(), admin?.get("displayName"));
    if (!preview) throw new HttpsError("not-found", "This invite leads nowhere.");
    return preview;
  },
);
