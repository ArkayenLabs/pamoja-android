import {initializeApp} from "firebase-admin/app";

initializeApp();

export {
  cleanupPushRegistrationsOnUserDelete,
  eraseDepartedMemberSignals,
  notifyGroupGoalReached,
  notifyGroupMemberJoined,
  notifySignalCreated,
  pruneStalePushRegistrations,
  registerPushInstallation,
  sendScheduledReminders,
  unregisterPushInstallation,
} from "./notifications.js";

export {deleteGroup} from "./group-admin.js";
export {getInvitePreview} from "./invite-preview.js";
