// Run only against the local emulator with the repository's actual rules loaded.
const assert = require('node:assert/strict');
const {test} = require('node:test');
const express = require('express');
const host = process.env.FIRESTORE_EMULATOR_HOST;
if (!/^127\.0\.0\.1:\d+$/.test(host || '')) throw new Error('Local emulator required');
const project = 'demo-pamoja-security';
process.env.GCLOUD_PROJECT = project;
process.env.GOOGLE_CLOUD_PROJECT = project;
// Never enable the Functions emulator shortcut that skips token verification.
delete process.env.FUNCTIONS_EMULATOR;
const endpoints = require('./lib/index.js');
const names = ['exportTogetherTrailPage', 'getTogetherTrailEntry', 'getTogetherTrail',
  'startTogetherTrailAdventure', 'saveTogetherTrailCommitment', 'prepareTogetherTrailSync',
  'submitTogetherTrailSync', 'pauseTogetherTrailContribution', 'finalizeTogetherTrailAdventure'];

test('every trail HTTP endpoint rejects missing/invalid App Check; handler rejects missing Auth', async () => {
  const app = express();
  app.use(express.json());
  for (const name of names) app.post('/' + name, endpoints[name]);
  const server = app.listen(0, '127.0.0.1');
  await new Promise(resolve => server.once('listening', resolve));
  try {
    for (const name of names) {
      for (const appCheck of [null, 'invalid-app-check']) {
        const response = await fetch(`http://127.0.0.1:${server.address().port}/${name}`, {
          method: 'POST', headers: {'content-type': 'application/json',
            ...(appCheck ? {'X-Firebase-AppCheck': appCheck} : {})}, body: JSON.stringify({data: {}}),
        });
        assert.equal(response.status, 401, name);
        const body = await response.json();
        assert.equal(body.error.status, 'UNAUTHENTICATED', name);
        // Wrapper rejection precedes our handler's distinct "Sign in first" error.
        assert.equal(body.error.message, 'Unauthenticated', name);
      }
      // .run bypasses the HTTP wrapper deliberately, to test Auth independently.
      await assert.rejects(endpoints[name].run({data: {}, app: {appId: 'sample'}}),
        {code: 'unauthenticated', message: 'Sign in first'}, name);
    }
  } finally { await new Promise(resolve => server.close(resolve)); }
});

function token(uid) {
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const now = Math.floor(Date.now() / 1000);
  return `${encode({alg: 'none', typ: 'JWT'})}.${encode({sub: uid, user_id: uid, aud: project,
    iss: `https://securetoken.google.com/${project}`, iat: now, exp: now + 3600,
    firebase: {sign_in_provider: 'custom', identities: {}}})}.`;
}
const base = `http://${host}/v1/projects/${project}/databases/(default)/documents`;
async function request(path, method, bearer, fields) {
  return fetch(`${base}/${path}`, {method,
    headers: {'content-type': 'application/json', ...(bearer ? {Authorization: `Bearer ${bearer}`} : {})},
    ...(fields ? {body: JSON.stringify({fields})} : {})});
}
const string = stringValue => ({stringValue});

test('actual Firestore rules deny protected trail and billing writes for every client role', async () => {
  const groupId = '11111111-1111-4111-8111-111111111111';
  const adventureId = '22222222-2222-4222-8222-222222222222';
  const group = `groups/${groupId}`;
  const adventure = `${group}/adventures/${adventureId}`;
  for (const [path, fields] of [
    [group, {adminId: string('organizer')}],
    [`memberships/organizer_${groupId}`, {userId: string('organizer'), groupId: string(groupId)}],
    [`memberships/member_${groupId}`, {userId: string('member'), groupId: string(groupId)}],
    [`groupAccess/${groupId}`, {isPremium: {booleanValue: true}}],
  ]) assert.equal((await request(path, 'PATCH', 'owner', fields)).status, 200, path);

  // Positive controls prove tokens and rules are functioning, rather than all reads failing.
  assert.equal((await request(`groupAccess/${groupId}`, 'GET', token('member'))).status, 200);
  assert.equal((await request(`groupAccess/${groupId}`, 'GET', token('outsider'))).status, 403);
  const privatePaths = [adventure, `${group}/adventureState/current`,
    `${adventure}/participants/member`, `${adventure}/participants/member/syncTickets/current`,
    `${adventure}/participants/member/segments/segment`,
    `${adventure}/participants/member/segments/segment/credits/day`,
    `${adventure}/participants/member/commitments/2026-09-21`,
    'featureRollouts/togetherTrail', 'billingAccounts/member', `groupBilling/${groupId}`,
    'revenueCatEvents/fake-event'];
  for (const path of privatePaths) assert.equal((await request(path, 'PATCH', 'owner',
    {marker: string('protected')})).status, 200, path);
  for (const uid of [null, 'organizer', 'member', 'outsider']) {
    const bearer = uid ? token(uid) : null;
    for (const path of privatePaths) {
      for (const method of ['GET', 'PATCH', 'DELETE']) {
        assert.equal((await request(path, method, bearer,
          method === 'PATCH' ? {steps: {integerValue: '999999'}, isPremium: {booleanValue: true}} : undefined)).status,
        403, `${uid} ${method} ${path}`);
      }
    }
    for (const path of [`groupAccess/${groupId}`, 'billingViews/member']) {
      assert.equal((await request(path, 'PATCH', bearer, {isPremium: {booleanValue: true}})).status, 403);
    }
    // Collection listing is also denied, even when the caller supplies a group ID.
    assert.equal((await request(`${group}/adventures`, 'GET', bearer)).status, 403);
  }
});
