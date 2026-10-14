// The server half of group deletion, against local emulators only. From the
// repository root, after `npm --prefix functions-notifications run build`:
//
//   firebase emulators:exec --only firestore,storage --project demo-pamoja-cleanup \
//     "node --test functions-notifications/group-cleanup.test.cjs"
//
// Account deletion by a group's last member deletes the group document from
// the app, which until 2026-10-13 left its photo, subcollections, access and
// billing projections and any orphaned memberships behind (2026-10-13 review).
// Also the account half of removal (2026-10-14): a deleted account's entries
// in groups' removed lists, which carry its name, go with it.
const assert = require('node:assert/strict');
const {test} = require('node:test');

const project = process.env.GCLOUD_PROJECT || '';
if (!/^127\.0\.0\.1:\d+$/.test(process.env.FIRESTORE_EMULATOR_HOST || '') ||
    !/^127\.0\.0\.1:\d+$/.test(process.env.FIREBASE_STORAGE_EMULATOR_HOST || '') ||
    !project.startsWith('demo-')) {
  throw new Error('Local Firestore and Storage emulators and a demo- project required');
}

const {initializeApp} = require('firebase-admin/app');
const {getFirestore} = require('firebase-admin/firestore');
const {getStorage} = require('firebase-admin/storage');
initializeApp({projectId: project, storageBucket: `${project}.appspot.com`});
const {cleanUpDeletedGroup} = require('./lib/group-admin.js');
const {cleanupPushRegistrationsOnUserDelete} = require('./lib/notifications.js');

const db = getFirestore();
const bucket = getStorage().bucket();
const GONE = '6413795d-42d0-4f2b-80df-f017a9d32817';
const KEPT = '0b9e3c1a-5d2f-4c6e-9a8b-7f1e2d3c4b5a';

async function seed(groupId, payer) {
  await db.doc(`memberships/${payer}_${groupId}`).set({userId: payer, groupId});
  await db.doc(`memberships/other_${groupId}`).set({userId: 'other', groupId});
  await db.doc(`groups/${groupId}/weeks/2026-10-05`).set({totalSteps: 1});
  await db.doc(`groups/${groupId}/signals/2026-10-13_cheer_a_b`).set({kind: 'cheer'});
  await db.doc(`groupAccess/${groupId}`).set({isPremium: true});
  await db.doc(`groupPreviews/${groupId}`).set({groupId});
  await db.doc(`groupBilling/${groupId}`).set({sponsorUid: payer});
  await bucket.file(`group_avatars/${groupId}/${payer}.jpg`).save(Buffer.from('jpeg'));
}

test('a group deleted from the app is cleaned up, and nothing of another group is touched', async () => {
  await seed(GONE, 'payer');
  await seed(KEPT, 'payer');
  await db.doc('groups/' + KEPT).set({name: 'Still here'});
  await db.doc('billingAccounts/payer').set({sponsoredGroupIds: [GONE, KEPT]});
  await db.doc('billingViews/payer').set({sponsoredGroupIds: [GONE, KEPT]});

  await cleanUpDeletedGroup.run({params: {groupId: GONE}});

  assert.equal((await db.doc(`deletedGroups/${GONE}`).get()).exists, true, 'tombstone');
  for (const path of [`memberships/payer_${GONE}`, `memberships/other_${GONE}`,
    `groups/${GONE}/weeks/2026-10-05`, `groups/${GONE}/signals/2026-10-13_cheer_a_b`,
    `groupAccess/${GONE}`, `groupPreviews/${GONE}`, `groupBilling/${GONE}`]) {
    assert.equal((await db.doc(path).get()).exists, false, path);
  }
  const [photo] = await bucket.file(`group_avatars/${GONE}/payer.jpg`).exists();
  assert.equal(photo, false, 'group photo');
  assert.deepEqual((await db.doc('billingAccounts/payer').get()).get('sponsoredGroupIds'), [KEPT]);
  assert.deepEqual((await db.doc('billingViews/payer').get()).get('sponsoredGroupIds'), [KEPT]);

  // The pair: the other group, its members, history, access and photo.
  for (const path of [`groups/${KEPT}`, `memberships/payer_${KEPT}`, `memberships/other_${KEPT}`,
    `groups/${KEPT}/weeks/2026-10-05`, `groupAccess/${KEPT}`, `groupBilling/${KEPT}`]) {
    assert.equal((await db.doc(path).get()).exists, true, path);
  }
  const [keptPhoto] = await bucket.file(`group_avatars/${KEPT}/payer.jpg`).exists();
  assert.equal(keptPhoto, true, 'other group photo');
  assert.equal((await db.doc(`deletedGroups/${KEPT}`).get()).exists, false);

  // Running again, as after deleteGroup or a retried event, is harmless.
  await cleanUpDeletedGroup.run({params: {groupId: GONE}});
});

test('a malformed group id does nothing', async () => {
  await cleanUpDeletedGroup.run({params: {groupId: 'not-a-group'}});
  assert.equal((await db.doc('deletedGroups/not-a-group').get()).exists, false);
});

test('a deleted account takes its removal entries with it, and nobody else\'s', async () => {
  const entry = (userId) => ({userId, displayName: 'Bob', removedAt: new Date()});
  await db.doc(`groups/${GONE}/removed/gone-user`).set(entry('gone-user'));
  await db.doc(`groups/${KEPT}/removed/gone-user`).set(entry('gone-user'));
  await db.doc(`groups/${KEPT}/removed/still-here`).set(entry('still-here'));

  await cleanupPushRegistrationsOnUserDelete.run({params: {userId: 'gone-user'}});

  assert.equal((await db.doc(`groups/${GONE}/removed/gone-user`).get()).exists, false);
  assert.equal((await db.doc(`groups/${KEPT}/removed/gone-user`).get()).exists, false);
  // The pair: someone else's entry still keeps them out.
  assert.equal((await db.doc(`groups/${KEPT}/removed/still-here`).get()).exists, true);
});
