import assert from "node:assert/strict";
import test from "node:test";

import {invitePreviewFrom} from "./invite-preview.js";

const GROUP = {
  name: " Sharma Family ",
  memberCount: 4,
  maxMemberCap: 10,
  weeklyTarget: 70_000,
  inviteLinkActive: true,
  adminId: "ravi",
  photoUrl: "https://example.com/group.jpg",
  weeklySteps: 48_320,
};

test("an invite shows the name, size, goal and who started it, and nothing else", () => {
  const preview = invitePreviewFrom(GROUP, "Ravi Sharma");
  assert.deepEqual(preview, {
    name: "Sharma Family",
    memberCount: 4,
    maxMemberCap: 10,
    weeklyTarget: 70_000,
    full: false,
    startedBy: "Ravi",
  });
});

test("a full group still previews, so the app can say it is full", () => {
  assert.equal(invitePreviewFrom({...GROUP, memberCount: 10}, "Ravi")?.full, true);
  assert.equal(invitePreviewFrom({...GROUP, inviteLinkActive: false}, "Ravi")?.full, true);
});

test("a missing, nameless or departing group leads nowhere", () => {
  assert.equal(invitePreviewFrom(undefined, "Ravi"), null);
  assert.equal(invitePreviewFrom({...GROUP, name: "  "}, "Ravi"), null);
  assert.equal(invitePreviewFrom({...GROUP, deletionPending: true}, "Ravi"), null);
});

test("an admin who left, or a strange name, simply goes unnamed", () => {
  assert.equal(invitePreviewFrom(GROUP, undefined)?.startedBy, null);
  assert.equal(invitePreviewFrom(GROUP, "   ")?.startedBy, null);
  assert.equal(invitePreviewFrom({...GROUP, memberCount: "4"}, "Ravi")?.memberCount, 0);
});
