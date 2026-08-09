# Pamoja Backlog

Working list of outstanding issues, from real device testing on 2026-08-04.
Companion to `CHECKLIST.md` (exhaustive launch gate) and `SUMMARY.md` (state of the product).

**Organising principle:** work is split by *layer*, not by feature. Logic-layer fixes
survive the design rebuild. UI-layer work does not, so it waits for the new design.

---

## 00. STATUS NOTES, 2026-08-05

**Design assets: `design/` folder.** The Pamoja design produced from the Claude
design session lives there alongside `design/DESIGN_BRIEF.md`. Refer to it when
building any screen. Round one covered auth (§2C) and invite/QR/join (§1, 2, 2B)
using the **warm ember original** system. Rounds still to run: §3 state matrix and
component library (do this next, everything else depends on it), §4 monetization,
§4B profile and settings, §4C notifications, §5 small additions.

**Firebase auth providers:** Email/Password, Google and Phone are **enabled**.
Blaze upgrade pending but trivial. Remaining blocker is fingerprints plus a fresh
`google-services.json`.

**SHA fingerprints, four in total, all added to Firebase:**
- Two from **Play Console → App integrity → App signing key certificate** (SHA-1 and SHA-256).
  These are Google's key, used for anything installed from Play.
- Two **debug** keys from the local machine:
  - SHA-1 `8C:27:3A:1F:F8:2F:D6:44:E5:11:00:50:B6:0C:CC:76:24:68:65:6E`
  - SHA-256 `09:82:DF:6F:BA:37:00:20:A1:0C:F0:A4:FA:E0:48:AA:8F:31:0E:9F:B2:1E:3F:A5:88:CC:60:51:B4:34:4A:B6`

Play Console and Firebase show different values because they are different keys.
Firebase does not learn Google's signing key until it is pasted in. Miss it and
Google Sign-In works in debug and fails in production.

**⏰ RevenueCat Shipaton 2026, UNRESOLVED AND TIME CRITICAL**
- Window 1 Aug to 30 Sep 2026. Over $700k in cash prizes
- Requires the first **public** release inside the window, plus the RevenueCat SDK
  powering at least one in-app purchase
- Pamoja ran a **closed test before 1 Aug**, so eligibility is genuinely unclear.
  The official rule says "first public version", and secondary sources claim any
  app in testing beforehand is excluded, but that wording is not in the official
  rules text
- **Ask RevenueCat directly** (Discord or Devpost) describing the exact situation:
  closed test completed, production approved, never published to production
- If eligible: RevenueCat becomes **mandatory**, overriding the earlier native
  Play Billing recommendation, and monetization moves from release two to launch
- If not eligible: ask whether a **new package name** resets it. Viable since
  there are no users, but it costs the existing Play listing and review history

---

## 0. CONTEXT CHANGE: no testers, single dev device

As of 2026-08-04 there are no external testers. Only the developer's own device
runs the app, and no user data needs preserving.

**This removes every backward compatibility constraint.** A lot of the code
currently exists purely to avoid breaking installs and documents that no longer
exist. Verified dead by scan:

| Thing | Status | Why it existed |
|---|---|---|
| ~60 legacy colour vals in `Color.kt` | **0 references** outside `ui/theme/` | Kept so un-migrated screens compiled. All screens are migrated now |
| `StepCounterService.kt` | **0 references** | Pre Health Connect sensor approach |
| `StepReader.kt` | **0 references** | Same |
| `CreateOrJoinGroupScreen.kt` | **0 references**, not in nav graph | Superseded by the Home join dialog |
| `GroupHeader()` alias | **0 call sites** | Compatibility shim |
| `PamojaTextField()` alias | **0 call sites** | Compatibility shim |
| `StepCounterManager.kt` | 1 reference, DI only | Pre Health Connect |
| `pamoja://` scheme support | Only for links shared during the closed test | No such links exist now |
| Legacy `memberCount` backfill in `joinGroup()` | Only for groups created before the field existed | No such groups need to survive |
| `inviteLink` field on the group document | Duplicates the document ID | Never read now that lookup is by ID |
| `groups (inviteLink, inviteLinkActive)` composite index | Dead, nothing queries it | Old lookup path |

**What this unlocks**

- [ ] Delete all of the above. Smaller APK, less attack surface, and the design
      rebuild starts from a clean base instead of inheriting shims
- [ ] **Wipe Firestore and start clean.** No migration logic needed anywhere
- [ ] Switch invite codes from UUIDs to short readable codes with no migration
      path to write, which also makes QR and manual entry much nicer
- [ ] Drop `pamoja://` from the manifest and `InviteLink`, https only
- [ ] Remove the legacy branch from `joinGroup()`, which is the most intricate
      and highest risk code in the repo
- [ ] Reshape the Firestore schema freely, for example denormalising member
      display names onto memberships. That removes N reads per group screen open
      AND lets `users/{userId}` be locked to owner only. See `CHECKLIST.md` §17.7

**Recommended timing: do this cleanup BEFORE the design rebuild**, not after.
Redesigning on top of dead code means reading, and possibly porting, code that
should not exist.

---

## A. FIX NOW, logic layer, survives the redesign

### A1. The shared invite link is still the old scheme 🔴
**This makes the entire App Links setup dead.** `assetlinks.json` is deployed and
verified, the manifest declares the https filter, but `InviteLink.build()` is never
called anywhere in the app. Every share still emits `pamoja://join/{groupId}` read
straight from Firestore.

Consequences seen on device:
- The link renders as plain text in messaging apps, not a tappable blue link, because
  chat clients only linkify recognised schemes like https
- Nobody can tell it is a link
- The verified https path never gets exercised

Fix:
- [ ] Generate and share `https://www.arkayenlabs.com/pamoja/join/{code}` via `InviteLink.build()`
- [ ] Keep accepting `pamoja://` on the way in, so links already shared during the closed test keep working

### A2. Share text wraps the link in prose 🔴
Currently: `Join my Pamoja group "Reddy"! We're tracking our steps together. Join here: pamoja://join/4c2f...`

Tester had to manually delete everything before `pamoja` to get a usable link.

- [ ] Share the bare URL only. Let the receiving app render its own preview
- [ ] If any text is kept, the URL must be last and on its own line
- [ ] Two places build this string: `InviteScreen.kt` (x2) and `GroupScreen.kt` (x1). Consolidate into one helper

### A3. Group lookup queries by full link string 🟠
`getGroupByInviteLink()` runs `whereEqualTo("inviteLink", link)`. Three problems:
1. Forces `list` permission on the `groups` collection in Firestore rules, which permits enumerating every group in the database
2. Breaks the moment the link format changes, since old rows hold `pamoja://` strings
3. The stored link duplicates data already in the document ID

Fix: resolve by document ID instead. The invite code already *is* the group ID.
- [ ] Change `getGroupByInviteLink(code)` to a direct `get(groups/{code})`
- [ ] Makes old and new link formats both work with zero migration
- [ ] Then tighten `firestore.rules`: `allow get: if isSignedIn(); allow list: if false;`

### A4. Joining a group you are already in is silent 🟠
Admin copies their own link, opens it, and lands in the group with no explanation.
The behaviour is correct, the communication is not.

- [ ] Detect existing membership before attempting the join
- [ ] Distinguish three cases explicitly: not a member, already a member, group full
- [ ] Feed that into the join preview screen (see B1)

### A5. No way to test notifications 🟠
The engine deliberately gates on: 20h since last send, quiet hours 22:00 to 08:00,
a 10:00 to 19:30 window for reminders, and engagement backoff. Correct in production,
impossible to exercise by hand.

- [ ] Add a `BuildConfig.DEBUG`-only trigger that bypasses the gates and posts one of each category
- [ ] Debug-only entry point, must never ship in release

---

## B. BLOCKED ON THE DESIGN REBUILD, UI layer

### B1. Join preview screen 🔴
Right now a link joins silently with no confirmation. Needs: group name, member
count, weekly goal, Join and Cancel. Plus the states from A4, already a member,
group full, invalid or expired code, and a loading state.

### B2. Remove the WhatsApp button 🔴
`InviteScreen.kt` hardcodes `setPackage("com.whatsapp")`. When WhatsApp is absent the
primary call to action dead-ends in a snackbar. It also presumes the user's messaging app.

Target: **Copy link** as primary, **Share** as secondary opening the system sheet,
which surfaces WhatsApp automatically for anyone who has it.

Small enough to do before the rebuild if the dead-end CTA is bothering testers.

### B3. QR code 🟠
Deferred by request. Generation needs ZXing, scanning needs CameraX plus ML Kit and a
camera permission flow. Encodes the same https link, so it inherits App Links for free.

### B4. UI states 🔴
See `CHECKLIST.md` §11. Roughly 14 states per data-driven screen, currently about 2.
Biggest gaps: inline form validation, partial failure, offline, stale data,
session expired, permission revoked mid-session.

### B5. Profile screen 🔴
Does not exist. `ProfileSetupScreen` is onboarding-only and unreachable afterwards.
Photo, age, height and weight are captured once and can never be edited.

### B6. Settings gaps 🟠
See `CHECKLIST.md` §11C. Notably: theme selector (light and dark both work but the
user cannot choose), Health Connect status and reconnect, open source licences,
Terms of Use row, export data.

---

## C. ARCHITECTURE, needs a decision before building

### C0. REAL AUTH, decision made 2026-08-05 🔴

Anonymous-only is being replaced with real accounts: **Google, phone (OTP) and
email**. Phone is the preferred method in India.

**Built and compiling:**
- `AuthRepository` rewritten with all three providers, plus `AuthMethods` and
  `PhoneVerification` models and explicit `linkGoogle` / `linkEmail` / `linkPhone`
- `FirebaseAuthRepositoryImpl` implementing all of it
- Use cases with real validation: email format, E.164 phone shape, 6 digit OTP
- Credential Manager dependencies added (the old `GoogleSignInClient` API is
  deprecated)

**The rule that runs through the implementation: link, do not replace.**
A user can create groups before signing in. If sign-in called
`signInWithCredential` it would mint a new UID and silently strand every group,
membership and step record. So when the current session is anonymous we call
`linkWithCredential`, upgrading that same UID in place. The fallback path, where
the Google account already exists as its own Firebase user, signs into it
instead, which is correct: that person is returning to an account they had.

**BLOCKED on Firebase console setup, cannot proceed without it:**
- [ ] 🔴 Add debug SHA-1 `8C:27:3A:1F:F8:2F:D6:44:E5:11:00:50:B6:0C:CC:76:24:68:65:6E`
      and SHA-256 `09:82:DF:6F:...` to Project Settings, Android app
- [ ] 🔴 Add the **Play app signing** SHA-1 and SHA-256 too, or Google Sign-In
      works in debug and fails silently in production
- [ ] 🔴 Enable Email/Password, Google and Phone in Authentication, Sign-in method
- [ ] 🔴 Upgrade to **Blaze**, phone auth has required billing since Sept 2024
- [ ] 🔴 Re-download `google-services.json`. It currently has **zero** oauth_client
      entries, so Google Sign-In cannot be configured at all
- [ ] 🔴 Set an SMS quota. Phone auth is about **$0.01 per verification in India**
      and SMS pumping fraud targets exactly this

**Still to build once unblocked:**
- [ ] Auth UI: landing, phone entry, OTP entry, email sign up and sign in,
      forgot password. Designs added to the continuation brief §2C
- [ ] Wire Credential Manager to fetch the Google ID token, needs the Web Client
      ID from the new `google-services.json`
- [ ] Account section in the profile, §4B of the brief
- [ ] Prompt existing anonymous users to link an account before they lose it
- [ ] Remove the fake "Welcome back" sign-in screen
- [ ] Re-check `deleteAccount`, Firebase requires recent re-authentication for
      destructive operations and will fail on an old session

---

### C1. Sign-in is theatre 🟠 (superseded by C0)
`SignInScreen` shows a Welcome Back screen with a Continue button. There is no
account. Auth is anonymous, so the screen restores whatever session Firebase already
holds and nothing more.

This is not only cosmetic. It causes a real product failure:
**uninstalling the app destroys every group the user belongs to, permanently.**
There is no recovery path, because there is no account to recover.

Options, in ascending order of effort:
1. **Remove the fake sign-in screen.** Honest, and the flow gets shorter. Does not fix data loss
2. **Optional account linking.** Keep anonymous by default, offer "link a Google account" in Settings to make the account portable. Firebase supports upgrading an anonymous user in place, so existing data carries over
3. **Real accounts from the start.** Most work, adds signup friction to a product whose whole appeal is that it is frictionless

Recommendation: option 2. It preserves the zero-friction start that makes the app easy
to adopt, while removing the permanent data loss. Needs a product decision before code.

### C2. FCM is declared but unused 🟡
`firebase-messaging` ships in the APK with no `FirebaseMessagingService`, no manifest
registration, and no `onNewToken`. `updateDeviceToken()` exists and is never called
with a real token.

Needed for notifications triggered by *other people's* actions: someone joined,
someone cheered you, admin changed the target. Not needed for anything shipping today.
Decide alongside the cheers and nudges feature.

---

## D. EXTERNAL, not code

- [ ] `/terms/pamoja` route does not exist. The app and the invite landing page both
      link to it, so both are 404 right now. Draft is at `legal/TERMS_OF_USE.md`,
      needs placeholders filled and a lawyer's review
- [ ] Back up the release keystore in two places. Losing it ends the ability to update
      this app under the same package name
- [ ] Google Cloud budget alert on the Firebase project
- [ ] Run the design continuation brief

---

## Verified working on device, 2026-08-04

- Step sync and leaderboard totals
- Light and dark theme across every screen
- All screens and navigation
- Four notification channels present, old "roasts" channel removed
- Onboarding, group creation, settings
