# Pamoja. Pre-Launch State of the Product

**Audit date:** 2026-07-27
**Build:** versionCode 1 / versionName 1.0 · minSdk 26 · targetSdk 36
**Status:** Play Console says "ready for production". **Engineering says: not yet.** Four blockers below.

This document is the honest assessment. `CHECKLIST.md` is the exhaustive task list.

---

## 1. THE FOUR BLOCKERS, do not publish until these are closed

### BLOCKER 1. There are no Firestore security rules in this repository
**Severity: critical. This is the one that can end the product.**

There is no `firestore.rules` and no `firebase.json` in the repo. Only `firestore.indexes.json` exists. That means the rules protecting your database live *only* in the Firebase Console, are not version-controlled, are not reviewed, and nobody can tell from the code what they are.

Why this is severe for **this specific app**:
- The app uses **anonymous auth + direct client-side Firestore access**. There is no backend in between. The security rules are the *only* thing standing between the public internet and your database.
- The data is not trivial: **name, age, height, weight, and a daily step history per user.** Age/height/weight plus health metrics is health-adjacent personal data. A leak here is a regulatory incident, not an embarrassment.
- If the project was ever created in **test mode**, the rules read `allow read, write: if true;` and expire to fully-open. Every user record in the app is world-readable and world-writable.

**Action:** a starter `firestore.rules` has been written to the repo root as part of this audit. It must be reviewed, the two code refactors it requires must be made (see §2.1), and it must be tested against the emulator before deploying. **Verify the current live rules in the Firebase Console today**, before anything else on this list.

---

### BLOCKER 2. Invite links cannot open the app
**Severity: critical for the core growth loop.**

`AndroidManifest.xml` contains **no deep-link intent filter at all**. The only intent filters present are `MAIN/LAUNCHER` and the Health Connect permissions rationale.

The invite link is `pamoja://join/{groupId}`, generated in `GroupUseCases.kt`. Today, tapping that link in WhatsApp or anywhere else does **nothing**, no app on the device claims it. The only way to join is to manually copy the link and paste it into an in-app dialog. That is the entire acquisition funnel, and it is broken.

Two separate problems:
1. **No intent filter**, the link is not registered.
2. **`pamoja://` is a custom scheme, which is the wrong mechanism.** Any other app on the device can register the identical scheme and hijack your invites. Custom schemes are also stripped or rendered unclickable by many messaging clients and email providers. Android's answer to both problems is **App Links**, a verified `https://` link tied to a domain you own via `assetlinks.json`.

**Action:** migrate to App Links on a domain you control, keep `pamoja://` as a legacy fallback, and add link handling to `MainActivity`. Full spec in §2.1.

---

### BLOCKER 3. No crash reporting
**Severity: high.**

`app/build.gradle.kts` includes `firebase-auth`, `firebase-firestore`, `firebase-messaging`, and `firebase-analytics`. **Firebase Crashlytics is absent.**

You are about to ship a release build with **R8 full mode + resource shrinking enabled for the first time**, to real devices, across every Android version from 8.0 to 16, with no visibility into crashes. Play Console's vitals dashboard gives you a delayed, sampled, stack-trace-only view. You will not know why users are churning.

R8 deserves emphasis: `isMinifyEnabled = true` changes runtime behaviour. Reflection-based code. Firestore DTO deserialization, Hilt, WorkManager, breaks under R8 in ways that never appear in debug builds. **If you have not run and exercised a release build end-to-end, assume it is broken until proven otherwise.**

**Action:** add Crashlytics, upload mapping files, and do a full manual pass on a signed release build.

---

### BLOCKER 4. Health Connect requires a Play declaration you probably have not filed
**Severity: critical for approval.**

The app requests `android.permission.health.READ_STEPS`. Google gates Health Connect access behind a **separate declaration form and policy review**, independent of the normal Play review. Apps that ship without it get rejected, or removed after the fact.

Requirements that apply to you specifically:
- A **Health Apps declaration** in Play Console describing exactly why you read step data.
- A **privacy policy that explicitly covers health data**, how it is collected, used, shared, retained, and deleted. A generic template policy will fail this review.
- The in-app **permissions rationale screen** (you have this, the `ACTION_SHOW_PERMISSIONS_RATIONALE` intent filter and the "WHAT PAMOJA ACCESSES" card, good).
- Health data must **not** be used for ads, and must not be shared with third parties.

The Settings screen links to `https://pamoja-app.web.app/privacy-policy`. **Confirm that URL is live, reachable, and actually covers health data**, a dead or placeholder privacy policy link is an instant rejection and is trivially checked by reviewers.

---

## 2. HIGH-PRIORITY WORK

### 2.1 Invite system rework (also covers the requested WhatsApp removal + QR)

The current invite system has design problems beyond the broken link:

| Problem | Detail |
|---|---|
| **Link is the group ID** | `inviteLink = "pamoja://join/$groupId"`. The invite secret and the group's primary key are the same value. You can never rotate an invite without changing the group's identity. |
| **Deactivation is permanent** | On reaching the member cap, `joinGroup()` sets `inviteLinkActive = false`. If a member later leaves, **the link stays dead forever**, there is no regeneration path. This is a live bug that will hit real groups. |
| **Lookup requires a collection query** | `getGroupByInviteLink()` runs `whereEqualTo("inviteLink").whereEqualTo("inviteLinkActive", true)`. This forces Firestore rules to permit `list` on the `groups` collection, which permits **enumerating every group in the database**. Since the link already contains the group ID, this should be a direct document `get()` instead, that single change lets you deny `list` entirely. |
| **WhatsApp is hardcoded** | `InviteScreen.kt` hardcodes `setPackage("com.whatsapp")` as the primary CTA. |

**Target design:**
1. **Remove the WhatsApp-specific button.** It fails wherever WhatsApp is not installed (it currently degrades to a snackbar, a dead-end primary action), it presumes a messaging app your users may not use, and the system share sheet already surfaces WhatsApp *first* for users who have it. Replace the primary action with **Copy link**, and make the system share sheet the secondary action.
2. **Add a separate `inviteCode` field** on the group, distinct from `groupId`, short, unambiguous, and **regenerable**. Use a Crockford Base32-style alphabet (no `I`/`L`/`O`/`U`) so codes can be read aloud and typed without confusion.
3. **Add a QR code** on the invite screen for in-person joining, encoding the App Link URL. This is the right call, for a family or office group standing in the same room, QR is the fastest possible path.
4. **Add manual code entry** as a fallback ("Enter code" → 8 characters), so joining never depends on a link surviving a messaging app.
5. **Fix the deactivation bug**, recompute `inviteLinkActive` when a member leaves, or drop the flag and evaluate `memberCount < maxMemberCap` at join time.
6. **Add a join preview**, show group name, member count, and goal *before* committing, plus explicit `group full` and `invalid/expired code` states.

**Decision required from you:** App Links need a domain you control. `pamoja-app.web.app` (Firebase Hosting) appears to be yours already, since the privacy policy is hosted there. If so, host `/.well-known/assetlinks.json` there containing your **release** signing certificate SHA-256 fingerprint, and links become `https://pamoja-app.web.app/join/{code}`. Confirm and this can be implemented immediately.

### 2.2 Local data is being backed up to Google Drive, silently

`AndroidManifest.xml` sets `android:allowBackup="true"`, and **both** `backup_rules.xml` and `data_extraction_rules.xml` are the untouched IDE templates, every rule inside them is commented out.

The practical effect: everything in DataStore is uploaded to the user's Google Drive and copied during device-to-device transfer. That includes `user_id`, `user_name`, `health_connect_granted`, `active_group_id`, and the step baseline values.

This is not catastrophic, but it is **undeclared data movement in a health app**, it must be disclosed on the Play Data Safety form, and restoring a stale `user_id` onto a new device can produce confusing session states. Make an explicit decision and write it into the rules files rather than shipping the template.

### 2.3 Account deletion needs a *web* URL, not just the in-app button

You have in-app deletion (`DeleteAccountUseCase` + the Settings confirmation dialog), good, and required. What people miss: Google **also** requires a **publicly reachable web URL** where a user can request account deletion *without installing the app*. It is a mandatory field on the Data Safety form. Not having it blocks submission.

Also verify the deletion is **complete**: the user document, all their memberships, all their step entries, and the Firebase Auth account itself. A partial delete that leaves orphaned step records is a GDPR/DPDP problem, and orphaned memberships will corrupt group member counts.

### 2.4 There is no test suite

Only the IDE template tests exist (`ExampleUnitTest`, `ExampleInstrumentedTest`). For a first launch this is a risk you can consciously accept, but not for the three pieces of logic where a bug is silent and corrupts real data:

- **`FirebaseGroupRepositoryImpl.joinGroup()`**, the member-cap transaction, the legacy `memberCount` backfill, and idempotent rejoin. A bug here over-fills groups or double-counts members.
- **Week boundary math**, `GroupScreen` computes `daysLeft` from `TemporalAdjusters.nextOrSame(SUNDAY)`. Untested against timezone changes, DST, and users travelling across the date line.
- **Step aggregation**, the Health Connect read path and the baseline logic in DataStore.

---

## 3. WHAT IS GENUINELY IN GOOD SHAPE

Credit where it is due, this is not a fragile codebase:

- **Architecture is clean and consistent.** Real Clean Architecture, correct dependency direction, `Result<T>` throughout, no import cycles (verified via the knowledge graph). 575 nodes across 39 coherent modules.
- **The `joinGroup` transaction is genuinely well-built**, atomic cap enforcement, idempotent rejoin, and a thought-through legacy migration path. Whoever wrote it was careful.
- **Secrets hygiene is correct.** `keystore.properties`, `*.jks`, `*.keystore`, and `local.properties` are all gitignored and confirmed untracked.
- **Health Connect is implemented the right way**, `client.aggregate()` rather than raw record summing, which avoids the double-counting bug that affects most step apps.
- **Graceful degradation**, `StepSyncWorker` returns success when Health Connect is unavailable rather than retrying forever. Correct instinct.
- **ProGuard rules are thorough**. DTOs, domain models, Hilt, WorkManager, and Health Connect are all covered.
- **The design system port is complete**, full light + dark token system, custom icon set, zero emoji, all nine screens migrated, build verified.

---

## 4. A NOTE ON "HASHING"

You mentioned wanting everything "properly hashed". To be precise, because it changes what you should actually do:

**Hashing is not the right tool for almost anything in this app.** Hashing is one-way, you use it for passwords, so a breach does not reveal the original. **Pamoja has no passwords.** Auth is anonymous; Firebase holds the credential. There is nothing to hash.

What actually protects your users' data:

| Concern | The real mechanism | Status |
|---|---|---|
| Data in transit | TLS | Automatic with the Firebase SDK ✅ |
| Data at rest | Google-managed AES-256 encryption | Automatic in Firestore ✅ |
| **Who can read what** | **Firestore security rules** | **MISSING. Blocker 1** ❌ |
| Data on the device | Android app sandbox + `allowBackup` policy | Needs the §2.2 decision ⚠️ |
| Data minimisation | Do not collect what you do not need | Age/height/weight are collected but currently unused, see below |
| Leakage via logs | No PII in logcat or Analytics | Needs an audit pass |

**The single highest-leverage security action is Blocker 1, the security rules. Encryption is already handled for you; authorization is not.**

One data-minimisation point worth a decision: the app collects **age, height, and weight**, but nothing in the product currently uses them. Under GDPR/DPDP data-minimisation principles, collecting personal data you have no use for is a liability with no upside. Either use them (calorie/distance estimates) or drop the fields until you do.

---

## 4B. MONETIZATION. THE DECISIONS THAT MUST HAPPEN BEFORE LAUNCH

Full detail in `CHECKLIST.md` §17–§21. The parts that change what you build *now*:

### Two external deadlines
1. ⏰ **Play Billing Library v8+ is mandatory for all new apps and updates from 31 August 2026** (extension available to 1 November). v9 shipped May 2026, and there is **no direct v7→v9 path**. You are launching weeks before this cutoff, **integrate v8 or v9 from day one** or you inherit a forced migration immediately after launch.
2. ⏰ **The Health apps declaration is required for apps on closed and open testing tracks, not just production.** Your closed test is live. If that form is not filed, you are non-compliant *today*. January 2026 also introduced Medical Device labelling and stricter per-data-type justifications, a declaration filed before then likely needs re-filing.

### Ads are effectively off the table
Play's Health Apps policy forbids using health/fitness data to serve ads (personalized or interest-based), and forbids transferring or selling it to ad platforms or data brokers. For Pamoja that rules out the standard ad-supported model. Fortunately Health & Fitness has the **highest install LTV of any app category**, so subscriptions are both the compliant path and the better one.

### The model I'd recommend
**Freemium subscription, no ads.** Not a hard paywall, even though hard paywalls convert ~10.7% vs ~2.1%, one-year retention is nearly identical, and your entire growth loop depends on free users being able to join a group. A hard paywall would strangle the invite mechanic that makes the product work.

- **Never paywall step tracking.** Paywall *depth*: multiple groups, larger groups, history, stats, streaks, badges, custom targets.
- **Put the paywall after the aha moment**, that moment is *seeing your first synced steps appear on the group leaderboard*. Paywalling before it converts 3–5× worse.
- **Lead with annual.** Annual plans are 60.6% of Health & Fitness revenue, and trials measurably help annual conversion in this category.
- **Consider a group plan**, one admin pays, the group gets premium. For a social product this is the strongest pricing lever available.
- **Corporate/team wellness (B2B) is plausibly the bigger long-term business.** Pamoja's group mechanic is already the exact shape of an employee wellness product, and per-seat contracts dwarf consumer subscription revenue.

### The Android-specific revenue leak nobody fixes
**About a third of Google Play subscription cancellations are involuntary billing failures, roughly double the App Store rate.** This is payment plumbing, not user intent. Enable grace periods and account hold, and build an in-app "update your payment method" prompt. This is the highest-ROI monetization work available and most teams never do it.

### The decision you cannot defer
**Set the free-vs-premium split before launch.** Loosening limits later is easy; tightening them later means either angering your earliest and most loyal users or carrying grandfathering logic forever. Decide now.

### One thing to instrument before you ship
You cannot retroactively measure a funnel you did not track. At minimum, instrument **invite created → link opened → group joined** and **install → first steps synced**. That is your growth loop and your activation moment.

---

## 5. RECOMMENDED SEQUENCE FOR THE NEXT 3–4 DAYS

**Day 1. Security. Nothing else matters until this is done.**
1. Open the Firebase Console and read the live Firestore rules. If they are open, treat it as an active incident.
2. Review `firestore.rules` (written to the repo), make the two required refactors, test against the emulator, deploy.
3. Add Crashlytics.

**Day 2. The invite system.**
4. App Links + `assetlinks.json` + `MainActivity` link handling.
5. Remove WhatsApp; copy-link primary, share sheet secondary.
6. `inviteCode` field, QR code, manual entry, join preview, fix the deactivation bug.

**Day 3. Compliance and release hardening.**
7. Health Apps declaration, Data Safety form, privacy policy verification, web deletion URL, terms of use.
8. Backup rules decision.
9. Build a signed release APK/AAB and manually exercise **every** flow on it. This is where R8 problems surface.

**Day 4. Testing and polish.**
10. Tests for `joinGroup`, week boundaries, step aggregation.
11. Device matrix testing, the edge cases in `CHECKLIST.md`, accessibility pass.
12. Store listing, screenshots, staged rollout at 10%.

**Ship on a staged rollout, never at 100%.** Start at 10%, watch Crashlytics and vitals for 48 hours, then ramp.

### Where monetization fits

You do **not** need billing implemented to launch. But you do need three things decided and two things built:

**Decide before launch (cheap now, expensive later):**
- The free vs premium feature split
- Pricing and whether you offer a group plan
- Subscription-led, no ads, written down

**Build before launch:**
- **Analytics instrumentation** for the activation and invite funnels (Day 1–2 work, and you cannot backfill it)
- **Billing Library v8/v9** if you are shipping paid tiers at launch. If you are launching free-only, you can defer, but check the 31 August deadline against your next update

**Honest recommendation: launch free, monetize on the second release.** You need real retention and funnel data before you know what people will pay for, and shipping billing under time pressure is how entitlement bugs reach production. Instrument everything now, watch the first cohort, then price it. The one exception is the Billing Library deadline, factor it into your update schedule.
