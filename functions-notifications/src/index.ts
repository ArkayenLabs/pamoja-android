import {initializeApp} from "firebase-admin/app";

initializeApp();

export {
  cleanupPushRegistrationsOnUserDelete,
  notifyGroupGoalReached,
  notifyGroupMemberJoined,
  pruneStalePushRegistrations,
  registerPushInstallation,
  unregisterPushInstallation,
} from "./notifications.js";

export {deleteGroup} from "./group-admin.js";
