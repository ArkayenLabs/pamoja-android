import assert from "node:assert/strict";
import test from "node:test";
import {randomUUID} from "node:crypto";
import {getFirestore, Timestamp} from "firebase-admin/firestore";

const host = process.env.FIRESTORE_EMULATOR_HOST;
const enabled = !!host && /^(127\.0\.0\.1|localhost):\d+$/.test(host);

test("checkout context and sponsorship mutations enforce identity, membership and transfer consent", {skip: !enabled}, async () => {
  // Never use a real RevenueCat key or contact its service in this test.
  process.env.GCLOUD_PROJECT = "demo-pamoja-security";
  process.env.REVENUECAT_SERVER_CONFIG = JSON.stringify({apiKey: "test", projectId: "test",
    entitlementId: "premium", webhookAuthorization: "test", webhookSigningSecret: "test"});
  const api = await import("./index.js");
  const db = getFirestore();
  const group = randomUUID();
  const old = randomUUID();
  const payer = `payer-${randomUUID()}`;
  const expiry = Date.now() + 3_600_000;
  const fetchBefore = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({items: [{entitlement_id: "premium", expires_at: expiry}]}));
  const request = (uid: string, data: Record<string, unknown>) => ({auth: {uid, token: {}}, data, rawRequest: {}} as never);
  try {
    await db.doc(`groups/${group}`).set({name: "Target"});
    await db.doc(`groups/${old}`).set({name: "Private old group"});
    await db.doc(`memberships/${payer}_${group}`).set({userId: payer, groupId: group});
    await db.doc(`billingAccounts/${payer}`).set({sponsoredGroupId: old, status: "inactive"});
    await db.doc(`groupBilling/${old}`).set({sponsorUid: payer, status: "inactive"});
    await db.doc("featureRollouts/togetherTrail").set({enabled: true, publicEnrollment: false, groupIds: [old]});
    await assert.rejects(api.getCirclePurchaseContext.run(request("stranger", {groupId: group})), {code: "permission-denied"});
    const context = await api.getCirclePurchaseContext.run(request(payer, {groupId: group}));
    assert.equal(context.sponsoredGroupName, null);
    assert.equal(context.sponsoredGroupId, old);
    assert.deepEqual(context.sponsoredGroupIds, [old]);
    assert.equal(context.groupCapacity, 1);
    assert.equal(context.subscriptionIsActive, true);
    assert.equal(context.planningAvailable, false);
    assert.equal(context.trailAvailable, false, "another group's rollout must not be sold here");
    await db.doc("featureRollouts/togetherTrail").set({enabled: true, publicEnrollment: false, groupIds: [group]});
    assert.equal((await api.getCirclePurchaseContext.run(request(payer, {groupId: group}))).trailAvailable, true);
    await db.doc("featureRollouts/togetherTrail").update({enabled: false});
    assert.equal((await api.getCirclePurchaseContext.run(request(payer, {groupId: group}))).trailAvailable, false);
    const data = {groupId: group, expectedPayerUid: payer, replaceExisting: true, expectedSponsoredGroupId: old};
    await assert.rejects(api.activateCircleSponsorship.run(request("stranger", data)), {code: "unauthenticated"});
    await assert.rejects(api.activateCircleSponsorship.run(request(payer, {...data, replaceExisting: false})), {code: "failed-precondition"});
    await assert.rejects(api.activateCircleSponsorship.run(request(payer, {...data, expectedSponsoredGroupId: randomUUID()})), {code: "failed-precondition"});
    await db.doc(`groupBilling/${group}`).set({sponsorUid: "another-payer", status: "active", leaseValidUntil: Timestamp.fromMillis(expiry)});
    await assert.rejects(api.activateCircleSponsorship.run(request(payer, data)), {code: "already-exists"});
    assert.equal((await db.doc(`billingAccounts/${payer}`).get()).get("sponsoredGroupId"), old);
    await db.doc(`groupBilling/${group}`).delete();
    await api.activateCircleSponsorship.run(request(payer, data));
    await api.activateCircleSponsorship.run(request(payer, data)); // Idempotent retry.
    assert.equal((await db.doc(`groupAccess/${group}`).get()).get("isPremium"), true);
    assert.equal((await db.doc(`groupAccess/${old}`).get()).get("isPremium"), false);
    const account = await db.doc(`billingAccounts/${payer}`).get();
    assert.equal(account.get("sponsoredGroupId"), group);
    assert.deepEqual(account.get("sponsoredGroupIds"), [group]);
    assert.equal(account.get("groupCapacity"), 1);
    await db.doc(`memberships/${payer}_${group}`).delete();
    await assert.rejects(api.activateCircleSponsorship.run(request(payer, data)), {code: "permission-denied"});
  } finally { globalThis.fetch = fetchBefore; }
});
