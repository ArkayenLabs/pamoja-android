# Pamoja. Pre-Launch Checklist

Exhaustive gate list before publishing to production on Google Play.
Companion to `SUMMARY.md` (which explains the *why* and the priority order).

**Legend:** 🔴 blocker · 🟠 high · 🟡 medium · 🔵 nice-to-have

> **Monetization, business setup, tax, and growth are in §17–§21.** Read those *before* launch, not after, several decisions there (free-tier limits, Billing Library version, analytics instrumentation) are extremely painful to change once you have real users.

> ⏰ **Two hard external deadlines discovered during research, see §17.2 and §2.3.**

---

## PROGRESS, updated 2026-08-11

> **`BACKLOG.md` is the current handoff document.** It has the full done/remaining
> list in execution order, the Shipaton deadline, and the conventions established
> while fixing things. Read it first. This section is a summary.

### ✅ Done since 2026-07-27
- **Real authentication.** Google, phone OTP, email. Anonymous removed entirely
- **Account deletion actually deletes.** It previously removed only the Auth
  record, leaving Firestore data permanently unreachable. Re-auth added for all
  three methods
- **Crashlytics** with mapping upload, **App Check** (enforcement still off)
- **Firestore rules** gained a leave-group case; without it deletion was denied
  for every non-admin
- **Invite preview screen**, so a link no longer joins you silently
- **Error taxonomy, connectivity observer, component library.** Home and the
  group dashboard fully state-covered. Fixed an infinite spinner on every group
  load failure
- **Full string extraction.** Every screen translatable; no ViewModel holds copy
- **Terms written and published; privacy policy rewritten** — the live one
  described anonymous auth and denied collecting email or phone, both false now
- Release build verified once under R8, lint at 0 errors

### 🔴 Next up (blocking launch)
1. **Run it on a real device.** Nothing since 2026-08-04 is device-verified
2. **Deploy the legal pages**, redo the **Data Safety form**
3. Register the **App Check debug token** before enabling enforcement
4. Finish state coverage: create group ✅, profile setup ✅, invite ✅ (§11).
   Settings is folded into the §4B redesign round, which rewrites that screen
5. Redesign rounds §4B profile/settings and §4C notifications
6. **RevenueCat SDK + one working IAP.** Mandatory for Shipaton, target early Sep

### ⏰ Real deadline
Shipaton submission closes **30 Sep 2026**; be live by **23 Sep**. Closed testing
does **not** disqualify you, confirmed against RevenueCat's own guidance.

### 📋 Decisions made
- Sign-in is a hard gate, no anonymous accounts
- Custom Compose paywall, not RevenueCat Paywalls V2
- English only at launch, but all strings extracted
- Entity: Balaji Thukuntala, sole proprietor, trading as Arkayen Labs

### ✅ Previously done
- **Design system ported to Compose**, full light + dark semantic token system (`PamojaColors`, `LocalPamojaColors`), `Shape.kt`, `Dimens.kt`, `Motion.kt`. No dynamic color (brand hue protected)
- **Typography**. Baloo 2 (display) + Nunito (body) via downloadable Google Fonts + official AOSP cert array
- **Icon system**. 22 custom Lucide vector drawables via `PamojaIcons`. **Zero emoji anywhere in the app**
- **All 9 screens migrated** onto theme-aware tokens + the icon set (verified by grep: no legacy colour refs outside `ui/theme`)
- **Light mode enabled app-wide**, `PamojaTheme` follows `isSystemInDarkTheme()`
- **Firestore security rules written**, replaces `allow read, write: if true` (was fully public). *Deploying now*
- **Health apps declaration**, filed 28 Jun 2026, "Activity and fitness" ✅
- **Play declarations**. 10 actioned incl. Data safety, Privacy policy, Ads, Target audience, Content rating
- **Closed testing requirement met**, production access approved
- Secrets hygiene verified · `SUMMARY.md` + `CHECKLIST.md` + knowledge graph created

### 🔴 Next up (blocking launch)
1. **Deploy + verify the Firestore rules** (§1.1)
2. **UI states**, the largest quality gap (§11). Currently ~2 states per screen; needs ~14
3. **Crashlytics** (§5), shipping R8 blind
4. **App Links + invite rework** (§3), invite links cannot open the app at all
5. **Cost control**, listeners, App Check, budget cap (§17.7)
6. **Release-build verification** under R8 (§4)

### 📋 Decisions still open
- Domain for App Links + release SHA-256 fingerprint
- Free vs premium split (§17.4)
- Which design direction to build (see the two design prompts)
- Launch free vs with billing (recommendation: free first)

**Revised timeline: 1–2 weeks.** The right call, the blockers above are not 3-day work if done properly.

---

## 1. SECURITY. DATA PROTECTION

### 1.1 Firestore security rules 🔴
- [ ] 🔴 Open Firebase Console → Firestore → Rules and **read the currently deployed rules**
- [ ] 🔴 If they are `allow read, write: if true` (test mode), treat as an active data incident, close it immediately
- [ ] 🔴 Commit `firestore.rules` to the repo so rules are version-controlled and reviewable
- [ ] 🔴 Add `firebase.json` so rules + indexes deploy reproducibly via CLI, not by hand in the console
- [ ] 🔴 `users/{userId}`, write restricted to `request.auth.uid == userId`
- [ ] 🔴 `users`, `list` **denied** (prevents enumerating every user in the database)
- [ ] 🔴 `groups`, `list` **denied** (requires refactoring invite lookup to a direct `get()` first)
- [ ] 🔴 `groups/{id}`, `update` restricted to members; admin-only fields (`maxMemberCap`, `adminId`) restricted to the admin
- [ ] 🔴 `groups/{id}`, `delete` restricted to the admin
- [ ] 🔴 `memberships/{userId}_{groupId}`, write only where the document ID matches the caller's UID
- [ ] 🔴 `steps/{userId}_{date}`, write only your own; read only if you share a group
- [ ] 🔴 Deny-by-default catch-all rule at the bottom: `match /{document=**} { allow read, write: if false; }`
- [ ] 🔴 Validate field types and ranges in rules (e.g. `steps` is a non-negative int, `weeklyTarget` within allowed presets), stops a malicious client writing 999,999,999 steps
- [ ] 🟠 Test all rules against the **Firebase Emulator Suite** with a real test matrix, not just the console simulator
- [ ] 🟠 Verify a signed-in user **cannot** read a group they are not a member of
- [ ] 🟠 Verify a user **cannot** write another user's step entries
- [ ] 🟠 Verify a non-admin **cannot** change the group's member cap
- [ ] 🟠 Verify a user **cannot** inflate `memberCount` directly to bypass the cap

### 1.2 Authentication
- [ ] 🟠 Confirm anonymous auth is the only enabled provider in Firebase Console (disable unused providers)
- [ ] 🟠 Verify session restore works after: app kill, device reboot, 30+ days idle
- [ ] 🟠 Verify behaviour on **uninstall → reinstall** (anonymous UID is lost, user becomes a new person, orphaning their old memberships). Decide and document whether this is acceptable or needs an account-recovery path
- [ ] 🟡 Consider offering optional account linking (Google Sign-In) so users can recover groups after reinstall, currently **all group membership is permanently lost on reinstall**, which is a serious retention issue
- [ ] 🟡 Confirm Firebase Auth quota/abuse settings are configured

### 1.3 Secrets and signing
- [x] ✅ `keystore.properties`, `*.jks`, `*.keystore`, `local.properties` gitignored and untracked (verified)
- [ ] 🔴 **Back up the release keystore in at least two secure locations.** If it is lost, you can never update this app again under the same package name
- [ ] 🔴 Record the keystore password, key alias, and key password in a password manager
- [ ] 🟠 Enable **Play App Signing** (Google holds the app signing key; you hold the upload key), this is the safety net for a lost keystore
- [ ] 🟠 Record the **release** SHA-256 certificate fingerprint (needed for App Links `assetlinks.json`)
- [ ] 🟠 Verify `google-services.json` is the production Firebase project, not a dev project
- [ ] 🟡 Restrict the Firebase/Maps API keys in Google Cloud Console to your package name + signing certificate

### 1.4 Local data on the device
- [ ] 🟠 Decide and explicitly configure `android:allowBackup` (currently `true` with empty template rules)
- [ ] 🟠 Fill in `backup_rules.xml`, currently every rule is commented out
- [ ] 🟠 Fill in `data_extraction_rules.xml`, currently every rule is commented out
- [ ] 🟠 Exclude `user_id` and any session state from cloud backup and device transfer, or handle a restored stale session gracefully
- [ ] 🟡 Confirm no sensitive data is written to external/shared storage
- [ ] 🟡 Confirm DataStore file is inside the app sandbox (default, verify no custom path)

### 1.5 Logging and leakage
- [ ] 🟠 Audit every `Log.*` call, no user IDs, names, ages, weights, step data, or invite codes in logcat
- [ ] 🟠 Strip or guard debug logging in release builds (ProGuard `-assumenosideeffects` for `android.util.Log`)
- [ ] 🟠 Confirm Firebase Analytics events carry **no PII**, check every constant in `AnalyticsConstants.kt`
- [ ] 🟠 Confirm health/step data is **never** sent to Analytics (a Play policy violation for Health Connect apps)
- [ ] 🟡 Confirm crash reports will not capture PII in custom keys or breadcrumbs
- [ ] 🟡 Disable Analytics debug logging in release

### 1.6 Network
- [ ] 🟠 Confirm no cleartext HTTP anywhere; set `android:usesCleartextTraffic="false"` explicitly
- [ ] 🟡 Add a network security config that disallows user-added CAs in release
- [ ] 🔵 Consider certificate pinning (low value here. Firebase SDK manages its own transport)

---

## 2. LEGAL AND REGULATORY COMPLIANCE

### 2.1 Privacy policy 🔴
- [ ] 🔴 Confirm `https://pamoja-app.web.app/privacy-policy` is **live and reachable** (linked from the Settings screen, reviewers will click it)
- [ ] 🔴 Policy explicitly covers **health data** (step counts), required for Health Connect approval
- [ ] 🔴 Policy states what is collected: name, optional age/height/weight, step counts, group membership, device identifiers
- [ ] 🔴 Policy states **who it is shared with** (group members see your name and step counts)
- [ ] 🔴 Policy states the retention period and the deletion process
- [ ] 🔴 Policy names a contact email for privacy requests
- [ ] 🔴 Policy states health data is **not used for advertising** and **not sold**
- [ ] 🟠 Policy covers Firebase/Google as a sub-processor
- [ ] 🟠 Policy has a "last updated" date
- [ ] 🟠 Link the privacy policy in **both** Play Console and in-app (in-app link exists ✅)

### 2.2 Terms of Use 🟠
- [ ] 🟠 Write and host Terms of Use / EULA
- [ ] 🟠 Link from the Settings screen (currently **missing**, only Privacy Policy is linked)
- [ ] 🟠 Include a health/fitness disclaimer: the app is not a medical device, step data is indicative not diagnostic, consult a doctor before starting exercise
- [ ] 🟠 Include acceptable-use terms and a termination clause
- [ ] 🟡 Include a limitation-of-liability clause
- [ ] 🟡 Specify governing jurisdiction

### 2.3 Health Connect / Health Apps declaration 🔴
> ⏰ **This applies RIGHT NOW, not at launch.** Google requires the Health apps declaration from every app published on Play **including apps on closed testing and open testing tracks**, not just production. If your closed test is live and this form is not filed, you are already out of compliance. **Check this today.**
>
> ⚠️ **New requirements landed in January 2026:** a "Medical Device" labelling system, stricter written justifications for each Health Connect data type requested, and a platform-wide ban on using age-restricted signals for health profiling. If you filed a declaration before January 2026, it likely needs re-filing under the new form.

- [ ] 🔴 Submit the **Health Apps declaration form** in Play Console for `READ_STEPS`
- [ ] 🔴 Confirm the declaration covers the **closed testing track** you are currently running
- [ ] 🔴 Complete the **Medical Device labelling** question (Pamoja is *not* a medical device, declare accordingly)
- [ ] 🔴 Write a specific written justification for why `READ_STEPS` is required
- [ ] 🔴 Justify the exact use of step data in the declaration
- [ ] 🔴 Confirm the in-app permissions rationale screen is reachable (intent filter present ✅, verify it actually launches)
- [ ] 🔴 Confirm health data is not shared with third parties
- [ ] 🟠 Confirm the app handles permission revocation mid-session (user disables it in Health Connect while the app is open)
- [ ] 🟠 Confirm the app works when Health Connect is not installed on the device
- [ ] 🟠 Confirm the app works on Android 13 and below (where Health Connect is a separate installable app)

### 2.4 Play Data Safety form 🔴
- [ ] 🔴 Declare collection of: name, health/fitness data, app interactions, device identifiers
- [ ] 🔴 Declare data is transmitted off-device and encrypted in transit
- [ ] 🔴 Declare whether data is backed up (see §1.4, currently yes, via `allowBackup`)
- [ ] 🔴 Provide the **web-based account deletion URL**, mandatory, and separate from in-app deletion
- [ ] 🔴 Ensure the form matches reality exactly, mismatches are a common rejection and removal cause
- [ ] 🟠 Declare data-sharing with other users (group members see your name and steps)

### 2.5 GDPR / India DPDP / regional law
- [ ] 🟠 Right to access, a user can obtain their data (in-app view or on request)
- [ ] 🟠 Right to erasure, in-app delete exists ✅; verify it is **complete** (user doc + memberships + all step entries + Auth account)
- [ ] 🟠 Right to rectification, name is editable ✅
- [ ] 🟠 Lawful basis for processing documented
- [ ] 🟠 **Data minimisation**, age/height/weight are collected but unused. Either use them or remove the fields
- [ ] 🟡 Data portability, an export path
- [ ] 🟡 Confirm the Firestore region and whether data residency matters for your primary market
- [ ] 🟡 Cookie/consent not applicable (no web view), confirm

### 2.6 Content rating and audience
- [ ] 🔴 Complete the Play content rating questionnaire
- [ ] 🔴 Set the target age group. **If any part of the audience is under 13**, COPPA and Play Families policy apply and anonymous data collection rules change significantly
- [ ] 🟠 Add a minimum-age statement to the Terms
- [ ] 🟠 Confirm the app is not designed for or marketed to children

---

## 3. DEEP LINKS AND THE INVITE SYSTEM 🔴

### 3.1 App Links
- [ ] 🔴 Confirm ownership of the domain used for links (`pamoja-app.web.app` or a custom domain)
- [ ] 🔴 Host `/.well-known/assetlinks.json` with the **release** SHA-256 fingerprint
- [ ] 🔴 Add an `https` `<intent-filter>` with `android:autoVerify="true"` to `MainActivity`
- [ ] 🔴 Keep the `pamoja://` scheme as a legacy fallback filter (older invites already in the wild)
- [ ] 🔴 Implement link handling in `MainActivity`, `onCreate` **and** `onNewIntent`
- [ ] 🔴 Handle a link arriving when the user is **not signed in** (defer the join until onboarding completes)
- [ ] 🔴 Handle a link arriving when the user is **already a member** (open the group, do not error)
- [ ] 🟠 Verify link verification actually passed: `adb shell pm get-app-links com.arkayenlabs.pamoja`
- [ ] 🟠 Add a **web fallback page** for users without the app installed → send them to the Play Store
- [ ] 🟠 Test the link from WhatsApp, Gmail, SMS, Chrome, Instagram DM, and a notes app

### 3.2 Invite UX rework
- [ ] 🟠 **Remove the WhatsApp-specific button** (`setPackage("com.whatsapp")` in `InviteScreen.kt`), fails when WhatsApp is absent, presumes the user's messaging app
- [ ] 🟠 Make **Copy link** the primary action
- [ ] 🟠 Make the **system share sheet** the secondary action (it surfaces WhatsApp automatically for users who have it)
- [ ] 🟠 Add a **QR code** on the invite screen for in-person joining
- [ ] 🟠 Add a **QR scanner** on the join path
- [ ] 🟠 Add **manual code entry** as a fallback
- [ ] 🟠 Add a separate, short, **regenerable `inviteCode`** field (do not reuse `groupId`); use an unambiguous alphabet (no `I`/`L`/`O`/`U`)
- [ ] 🟠 Add a **regenerate invite link** action for admins
- [ ] 🔴 **Fix the deactivation bug**, `inviteLinkActive` is set `false` at the cap and is never restored when a member leaves, permanently killing the link
- [ ] 🟠 Refactor `getGroupByInviteLink()` to a direct document `get()` so Firestore `list` can be denied on `groups`
- [ ] 🟠 Add a **join preview** (group name, member count, goal) before committing
- [ ] 🟠 Design and implement the **group full** state
- [ ] 🟠 Design and implement the **invalid / expired code** state

---

## 4. RELEASE BUILD AND CONFIGURATION 🔴

- [ ] 🔴 Build a **signed release AAB** and install it on a physical device
- [ ] 🔴 **Manually exercise every single flow on the release build**. R8 full mode is enabled and breaks reflection-based code (Firestore DTOs, Hilt, WorkManager) in ways debug builds never reveal
- [ ] 🔴 Verify Firestore DTO serialization survives R8 (all four DTOs: Group, User, StepEntry, Membership)
- [ ] 🔴 Verify Hilt injection survives R8
- [ ] 🔴 Verify `StepSyncWorker` still runs under R8
- [ ] 🟠 Bump `versionCode` / `versionName` for the actual release
- [ ] 🟠 Confirm `isMinifyEnabled` and `isShrinkResources` are true for release only
- [ ] 🟠 Confirm the debug build type cannot reach production Firebase (consider separate dev/prod Firebase projects)
- [ ] 🟠 Check the final AAB size and the per-ABI download size
- [ ] 🟡 Consider a debug `applicationIdSuffix` so debug and release can coexist on one device
- [ ] 🟡 Review `targetSdk 36` behaviour changes (edge-to-edge enforcement, predictive back)
- [ ] 🟡 Add `android:enableOnBackInvokedCallback="true"` for predictive back (Android 13+)
- [ ] 🟡 Migrate `kapt` → `KSP` (kapt falls back to Kotlin 1.9 and slows every build, visible in the build log)

---

## 5. CRASH REPORTING AND MONITORING 🔴

- [ ] 🔴 Add **Firebase Crashlytics** (currently absent)
- [ ] 🔴 Verify mapping/symbol files upload on release builds so stack traces are readable
- [ ] 🟠 Force a test crash and confirm it appears in the console
- [ ] 🟠 Add non-fatal reporting for caught exceptions in repository `Result.failure` paths
- [ ] 🟠 Set a Crashlytics custom key for the current screen to aid triage
- [ ] 🟠 Enable Play Console vitals alerts (ANR rate, crash rate)
- [ ] 🟡 Set a **Firebase billing budget alert**, a snapshot-listener bug can generate a very large bill overnight
- [ ] 🟡 Monitor Firestore reads/writes per user to catch runaway listeners
- [ ] 🟡 Define the crash-free-users threshold that would trigger a rollback

---

## 6. FUNCTIONAL TEST CASES

### 6.1 Onboarding
- [ ] Fresh install → Welcome renders, progress shows 1 of 3
- [ ] "Get started" → Profile Setup
- [ ] Continue is disabled with an empty name
- [ ] Name with only spaces is rejected
- [ ] Very long name (100+ chars) does not break layout, is truncated sensibly
- [ ] Emoji / RTL / accented characters in the name render correctly
- [ ] Age/height/weight accept only valid numbers; reject negatives and absurd values
- [ ] Skipping all optional fields works
- [ ] Health Connect: grant → success state → proceeds
- [ ] Health Connect: deny → denied state → "Open Settings" opens the right screen
- [ ] Health Connect: deny permanently → correct messaging
- [ ] Health Connect not installed → "unavailable" state → "Continue without steps" works
- [ ] "Skip for now" reaches Home successfully
- [ ] Killing the app mid-onboarding and reopening lands in a sane state
- [ ] Rotating the device mid-onboarding preserves input

### 6.2 Groups
- [ ] Create group with a valid name → success → Invite screen
- [ ] Create with an empty name is blocked
- [ ] All five weekly-target presets save correctly
- [ ] Member cap slider covers 2–20 and saves correctly
- [ ] "Members can edit goal" toggle persists
- [ ] Join with a valid link succeeds
- [ ] Join with a malformed link shows a clear error
- [ ] Join with a link for a **full** group shows "group is full"
- [ ] Join a group you are **already in** → no duplicate membership, no member-count inflation
- [ ] Two devices joining the last slot **simultaneously** → exactly one succeeds (transaction test)
- [ ] Member count is accurate after every join
- [ ] Legacy group with `memberCount = 0` backfills correctly on first join
- [ ] Group with 20 members renders and scrolls correctly
- [ ] Group with exactly 2 members renders correctly
- [ ] Leaving a group (if implemented) decrements the count and reactivates the invite

### 6.3 Steps and sync
- [ ] Steps appear on the dashboard after a Health Connect sync
- [ ] Combined group total equals the sum of member weekly totals
- [ ] Progress ring percentage math is correct at 0%, 50%, 99%, 100%, and >100%
- [ ] Ring colour changes at the correct stage thresholds
- [ ] "Days left" is correct on each day Mon–Sun
- [ ] **Week rollover at Sunday→Monday midnight resets totals correctly**
- [ ] Timezone change (fly across zones) does not corrupt the week boundary
- [ ] DST transition does not double-count or skip a day
- [ ] Device date changed manually → no negative or absurd values
- [ ] Steps recorded on a second device / watch are not double-counted
- [ ] Revoking Health Connect permission mid-session degrades gracefully
- [ ] `StepSyncWorker` runs on schedule; verify with `adb shell dumpsys jobscheduler`
- [ ] Sync works after the app is force-stopped
- [ ] Sync respects Doze / battery optimisation without silently dying forever
- [ ] Zero steps for a member renders as 0, not blank or a crash

### 6.4 Leaderboard
- [ ] Ranking order is correct by weekly steps
- [ ] Ties are handled deterministically (not flickering between renders)
- [ ] Gold/silver/bronze medals map to ranks 1/2/3
- [ ] Rank 4+ shows a plain number
- [ ] The "you" badge appears on exactly one row, yours
- [ ] Your row is highlighted wherever you rank
- [ ] Long member names truncate without breaking the row
- [ ] A member with no name set renders a sensible fallback

### 6.5 Settings and account
- [ ] Edit name → persists → reflects on the leaderboard for other members
- [ ] Copy User ID → clipboard confirmation appears
- [ ] Privacy Policy link opens successfully
- [ ] Rate Us opens the Play Store (and does not crash when the Play Store is absent)
- [ ] Log out → returns to Welcome, session cleared
- [ ] Delete account → confirmation dialog → deletion completes
- [ ] **After deletion, verify in Firestore that the user doc, all memberships, and all step entries are gone**
- [ ] After deletion, group member counts are decremented correctly
- [ ] Deletion while offline fails gracefully with a clear message

### 6.6 Notifications
- [ ] POST_NOTIFICATIONS permission requested correctly on Android 13+
- [ ] Denying notification permission does not break the app
- [ ] Notifications render correctly and deep-link to the right screen
- [ ] Notification content contains no sensitive data on the lock screen
- [ ] No notification spam, `last_notification_time` throttling works

---

## 7. EDGE CASES AND FAILURE MODES

- [ ] 🔴 **Airplane mode / no internet** on every screen, clear errors, never an infinite spinner
- [ ] 🟠 Network drops mid-write (mid-join, mid-create) → no partial state
- [ ] 🟠 Very slow network (throttle to 2G) → loading states hold, no ANR
- [ ] 🟠 Firestore offline persistence, decide whether it is on, and test stale-data display
- [ ] 🟠 App killed by the OS in the background → state restores correctly
- [ ] 🟠 Low memory → no crash on return
- [ ] 🟠 Rapid double-tap on every button → no duplicate groups, no duplicate joins
- [ ] 🟠 Back button / gesture from every screen → no dead ends, no crash
- [ ] 🟠 Deep link received while the app is already open (`onNewIntent`)
- [ ] 🟡 Device clock set incorrectly
- [ ] 🟡 Storage full → DataStore write failure handled
- [ ] 🟡 Play Services missing or outdated (affects Firebase and downloadable fonts)
- [ ] 🟡 **Downloadable Google Fonts fail to load** (no network on first launch, or no Play Services) → verify the fallback font is acceptable, not broken layout
- [ ] 🟡 Admin deletes their account while others are in the group → group is not orphaned
- [ ] 🟡 A member is removed while viewing the group screen

---

## 8. DEVICE AND OS MATRIX

- [ ] 🔴 Android 8.0 (API 26, your `minSdk`)
- [ ] 🟠 Android 10, 12, 13, 14, 15, 16
- [ ] 🟠 Android 13 specifically (notification permission + Health Connect as a separate app)
- [ ] 🟠 Android 14+ (Health Connect built into the platform)
- [ ] 🟠 Small screen (5", 720p)
- [ ] 🟠 Large screen / tablet, confirm it does not look broken even if unsupported
- [ ] 🟠 Foldable (inner/outer display switch, configuration change)
- [ ] 🟠 High refresh rate (90/120 Hz), animation smoothness
- [ ] 🟡 Low-end device (2 GB RAM), startup time, jank
- [ ] 🟡 Samsung One UI, Xiaomi MIUI, OnePlus OxygenOS, aggressive battery managers killing `StepSyncWorker`
- [ ] 🟡 Gesture navigation and 3-button navigation
- [ ] 🟡 Display size / font size set to maximum in system settings

---

## 9. PERFORMANCE

- [ ] 🟠 Cold start under 2 seconds on a mid-range device
- [ ] 🟠 No jank scrolling a 20-member leaderboard
- [ ] 🟠 No memory leaks. Firestore listeners removed in `awaitClose` (verify with LeakCanary in debug)
- [ ] 🟠 Verify snapshot listeners are not duplicated on recomposition (this was a past bug in `GroupViewModel.observeMembersAndSteps()`)
- [ ] 🟠 Firestore read count per session is reasonable (watch the cost)
- [ ] 🟡 Battery drain over 24 hours is acceptable
- [ ] 🟡 Recomposition profiling on the Group dashboard
- [ ] 🟡 Add a Baseline Profile for faster startup
- [ ] 🟡 APK/AAB size review

---

## 10. ACCESSIBILITY

- [ ] 🟠 `contentDescription` on every meaningful icon and image
- [ ] 🟠 Decorative icons explicitly `null` so TalkBack skips them
- [ ] 🟠 Full TalkBack pass on every screen
- [ ] 🟠 Text contrast meets WCAG AA in **both** light and dark themes
- [ ] 🟠 All tap targets at least 48×48dp
- [ ] 🟠 Rank/progress/status not conveyed by **colour alone** (medals need shape or label too)
- [ ] 🟠 Layouts survive 200% system font scale
- [ ] 🟡 Respect "reduce motion" for the ring and celebration animations
- [ ] 🟡 Logical focus order for keyboard/switch access
- [ ] 🟡 Progress ring exposes a meaningful accessibility value ("62 percent of weekly goal")

---

## 11. UI STATES. THE BIGGEST QUALITY GAP 🔴

> **Current status: essentially unimplemented.** Every screen today handles roughly two states, `isLoading` and "content". Everything else (offline, permission revoked, session expired, empty, partial failure, validation) either shows a raw exception string in a snackbar or nothing at all.
>
> This is the clearest line between a demo and a product. Users do not encounter the happy path, they encounter a train tunnel, an expired session, a revoked permission, and a group with no data yet. **Design and build these deliberately.**

### 11.1 The state matrix, every data-driven screen needs all of these

| State | Requirement |
|---|---|
| **Loading (initial)** | Skeleton matching the real layout, not a bare centred spinner |
| **Loading (refresh)** | Existing content stays visible; a subtle indicator on top |
| **Loading (action)** | The button itself shows progress and disables, never a full-screen block |
| **Empty** | Illustration + encouraging copy + a clear primary action |
| **Error (retryable)** | Plain-language cause + a **Retry** button |
| **Error (terminal)** | Explain what happened and what to do instead |
| **Offline** | Persistent banner + show cached data + state clearly it is stale |
| **Slow network** | Skeleton persists; after ~10s offer "still trying… / cancel" |
| **Partial failure** | Show what loaded; mark what failed; allow retry of just that part |
| **Permission denied** | Explain the consequence, link straight to the relevant settings |
| **Session expired** | Explain plainly, route to sign-in, preserve intent |
| **Form validation** | Inline, next to the field, on blur, not a snackbar on submit |
| **Success** | Explicit confirmation for every action that changed something |
| **Disabled** | Visually clear *and* explain why it is disabled |

### 11.2 Shared components to build (`ui/components/` is currently empty)

- [ ] 🔴 `PamojaEmptyState`, icon, title, body, optional action
- [ ] 🔴 `PamojaErrorState`, icon, message, retry action
- [ ] 🔴 `PamojaLoadingSkeleton`, shimmer placeholders per layout shape
- [ ] 🔴 `OfflineBanner`, persistent, dismissible, auto-hides on reconnect
- [ ] 🟠 `PamojaSnackbar`, consistent success/error/info styling
- [ ] 🟠 Move `DarkTextField` and `OnboardingProgressBar` out of `ProfileSetupScreen.kt` into `ui/components/`
- [ ] 🟠 Add validation support to the text field (error text, error border, supporting text)
- [ ] 🟡 `PamojaConfirmDialog`, one consistent destructive-confirmation pattern

### 11.3 Error taxonomy, stop leaking exceptions to users 🔴

Today every repository returns `Result.failure(Exception("..."))` and screens push `exception.message` straight into a snackbar. That means Firestore internals (`PERMISSION_DENIED`, `UNAVAILABLE`, index URLs) are shown to real users.

- [ ] 🔴 Define a sealed `AppError` type: `Network`, `Offline`, `PermissionDenied`, `NotFound`, `Conflict`, `SessionExpired`, `RateLimited`, `Unknown`
- [ ] 🔴 Map Firestore/Firebase exceptions to `AppError` in the data layer
- [ ] 🔴 Map `AppError` → user-facing copy in the UI layer
- [ ] 🔴 **Never** display a raw exception message
- [ ] 🟠 Log the technical detail to Crashlytics while showing the friendly message
- [ ] 🟠 Decide which errors are retryable and surface Retry only for those

### 11.4 Connectivity

- [ ] 🔴 Add a connectivity observer (`ConnectivityManager` → `Flow<Boolean>`)
- [ ] 🔴 Expose online/offline state app-wide
- [ ] 🟠 Show the offline banner on every screen
- [ ] 🟠 Enable Firestore offline persistence so cached data renders offline
- [ ] 🟠 Indicate staleness ("Last updated 2 hours ago") when offline
- [ ] 🟠 Queue writes made offline and confirm when they sync
- [ ] 🟠 Disable actions that genuinely cannot work offline (create/join group) with an explanation
- [ ] 🟡 Auto-retry on reconnect

### 11.5 Per-screen state coverage

- [ ] 🔴 **Home**, loading skeleton · no groups (exists ✅, verify) · load failed + retry · offline w/ cached groups · session expired (partially handled ✅)
- [ ] 🔴 **Group dashboard**, loading skeleton · group has no steps yet · member list failed · steps failed but group loaded (partial) · offline/stale · Health Connect revoked · group deleted while viewing
- [ ] 🔴 **Join flow**, validating · invalid code · expired link · **group full** · already a member · network failure
- [x] ✅ **Create group**, inline validation on name · submitting · failed + retry · success · offline blocks the write with an explanation (it used to hang forever)
- [x] ✅ **Profile setup**, inline validation on name · numeric validation on age/height/weight · submitting · failed · offline. Submit is now gated on validity, not `isNotBlank()`
- [ ] 🔴 **Health permission**, all four states exist ✅ · add **revoked mid-session** · add Health Connect needs update
- [ ] 🔴 **Settings**, loading · delete in progress · delete failed · sign-out failed · offline (disable destructive actions)
- [x] ✅ **Invite**, details loading (skeleton, not a defaulted cap presented as fact) · details failed → link still works, retry just the details · offline · copy confirmation ✅
- [ ] 🟠 **Sign in**, loading ✅ · failed + retry · no network

### 11.6 Form validation

- [ ] 🔴 Validate on blur and on submit, never only on submit
- [ ] 🔴 Inline error text beneath the field, not a snackbar
- [ ] 🔴 Group name: non-empty after trim, max length, reject whitespace-only
- [ ] 🔴 Profile name: same rules, currently only `isNotBlank()` is checked at the button
- [ ] 🟠 Age/height/weight: numeric, sane ranges, clear messages
- [ ] 🟠 Invite code: format-check before hitting the network
- [ ] 🟠 Disable submit until valid, and make it obvious *why* it is disabled
- [ ] 🟡 Trim whitespace on all input before saving

### 11.7 General UX polish

- [ ] 🟠 Destructive actions all have confirmation dialogs (Settings ✅, verify elsewhere)
- [ ] 🟠 Success feedback for every action that changes data
- [ ] 🟠 Add **pull-to-refresh** on Home and Group dashboard (also the fix for dropping real-time listeners, see §17.7)
- [ ] 🟡 Light/dark switch mid-session does not break any screen
- [ ] 🟡 Consistent back-navigation behaviour throughout
- [ ] 🟡 Keyboard does not obscure inputs (`imePadding` on Profile Setup ✅, verify elsewhere)
- [ ] 🟡 Double-tap protection on every submit button
- [ ] 🔵 Haptic feedback on key actions
- [ ] 🔵 First-run tooltips

---

## 11B. NOTIFICATIONS, needs a rework 🔴

> Notifications are the retention engine of a habit app. Right now Pamoja's are **personalized but tonally wrong, badly timed, and structurally incomplete**. Reviewed `SmartNotificationEngine.kt`, `SmartNotificationHelper.kt`, and `StepSyncWorker.checkAndTriggerNotification()`.

### 11B.1 🔴 The tone is shaming, and it contradicts your own brand

The engine currently sends, to whoever is last on the leaderboard:

> *"Slower than a snail?", "You're in last place at Rank 6. We promise the floor won't bite."*
> *"Leaderboard anchor", "You are keeping the team grounded at the bottom."*
> *"Are you in bed?", "The team average is 8,400 steps. You're at 1,200."*
> *"Sitting this one out? Everyone else is walking."*

Your own design system says: *"encouraging teammate, not a drill sergeant. No guilt-based messaging."* This is the opposite.

It also misreads the Zomato comparison. Zomato is cheeky about **pizza**, low stakes, no personal judgement. Pamoja would be mocking someone's **physical activity**, and the person at the bottom of a step leaderboard is often ill, injured, disabled, caring for a family member, working a double shift, or having a hard week. Being told they're "keeping the team grounded at the bottom" is the fastest uninstall you can engineer.

The Android notification channel is even user-visibly named **"Step reminders & roasts"**.

- [ ] 🔴 **Rewrite every message in the encouraging register.** Warm, specific, forward-looking. Humour is fine, humour *at the user's expense* is not
- [ ] 🔴 **Never reference last place, being behind, or a negative comparison to the group average**
- [ ] 🔴 Rename the channel to something neutral ("Step reminders")
- [ ] 🟠 Rule of thumb: would you send this to a friend recovering from illness? If not, cut it

### 11B.2 🔴 Bugs found in the current implementation

- [ ] 🔴 **"Weekend push" fires on any day.** `getRandomMotivation()` branch 3 hardcodes *"It's the weekend!"* with no day-of-week check, it can fire on a Tuesday
- [ ] 🔴 **Small icon is `R.mipmap.ic_launcher`.** Android requires a white-silhouette small icon; a full-colour launcher icon renders as a **white blob** on most devices. Needs a dedicated monochrome notification icon
- [ ] 🔴 **Accent colour is `R.color.purple_500`**, a leftover project-template colour, not the Pamoja brand indigo
- [ ] 🟠 **Tapping a notification just opens the app at the start destination** (`FLAG_ACTIVITY_CLEAR_TASK`). It should deep-link to the relevant group
- [ ] 🟠 **Fixed notification ID (1001)**, every notification replaces the previous one. Correct for a recurring reminder, wrong once you have distinct events

### 11B.3 🟠 It is not actually "smart"

Despite the name, `selectNotification()` picks a branch with `random.nextInt(3)` and then a message with `random.nextInt(6)`. It does not rank messages by relevance, a user who is 200 steps from overtaking a teammate can be served a generic "weekend push" instead.

- [ ] 🟠 Replace random selection with **priority-ranked selection**: evaluate all eligible messages, score by relevance and urgency, send the highest
- [ ] 🟠 Suppress messages whose data makes them meaningless (no "you're close to overtaking" when the gap is 12,000 steps)
- [ ] 🟡 Never repeat the same message two sends running

### 11B.4 🔴 Timing

Notifications currently fire whenever `StepSyncWorker` happens to run and the 12-hour throttle has elapsed. **Research shows ~39% of users who dislike notifications object to the *timing*, not the content.**

A "you need 3,000 more steps today" at 11pm is useless, the user cannot act on it. The same message at 6pm is genuinely motivating.

- [ ] 🔴 Send in **actionable windows**, a step nudge belongs in late afternoon/early evening, never late at night
- [ ] 🔴 Add **quiet hours** (no notifications 22:00–08:00 local)
- [ ] 🟠 Learn each user's active window from app-open times and target it
- [ ] 🟠 Make timing relative to the *user's* timezone, not the server's
- [ ] 🟡 Weekly recap on Monday morning; goal push on Saturday morning when there's still time

### 11B.5 🔴 Engagement backoff, the Duolingo lesson

Duolingo's most important notification rule: **if a user stops interacting with notifications, stop sending them.** Continuing to push at a disengaged user gets you muted or uninstalled, and once muted, you have lost the channel permanently.

- [ ] 🔴 Track notification **sent / opened / dismissed** per user
- [ ] 🔴 **Back off automatically** after N consecutive ignores (e.g. 5 → drop to weekly; 10 → reminders off, keep only high-value events)
- [ ] 🟠 Re-engage cautiously if the user returns on their own
- [ ] 🟠 Never send more than one notification per day by default

### 11B.6 🟠 Multiple channels, currently only one

One channel means users face an all-or-nothing choice, so annoyance at reminders kills your goal-achieved notifications too. Android lets users disable channels individually, use that.

- [ ] 🟠 **Step reminders**, daily nudges (the one people mute)
- [ ] 🟠 **Goals & achievements**, goal hit, streaks, badges (high value, rarely muted)
- [ ] 🟠 **Group activity**, someone joined, overtook you, admin changed the target
- [ ] 🟠 **Weekly recap**. Monday summary
- [ ] 🟠 Set sensible per-channel importance (achievements can vibrate; reminders should be quiet)

### 11B.7 🟠 Missing event-driven notifications (the highest-value ones)

Everything today is a periodic nudge. The notifications that actually drive retention are **event-driven**, because they carry real news:

- [ ] 🔴 **Your group hit the weekly goal** 🎉, the emotional payoff of the entire product, currently not notified at all
- [ ] 🟠 **Someone joined your group**, closes the invite loop for the inviter
- [ ] 🟠 **Someone overtook you**, the single most motivating social trigger in a leaderboard product
- [ ] 🟠 **Group is close to the goal** with 1–2 days left ("2,400 steps between you and the goal")
- [ ] 🟠 **You've been nudged/cheered** by a teammate
- [ ] 🟠 **Weekly recap**, how the group did, your contribution, next week's target
- [ ] 🟠 **Streak at risk**, gently, and never twice
- [ ] 🟡 **Admin changed the weekly target**
- [ ] 🟡 **First steps synced**, confirms setup worked (big trust moment)

### 11B.8 🟠 User control and infrastructure

- [ ] 🔴 **Notification settings screen**, per-channel toggles, quiet hours, frequency
- [ ] 🟠 Android 13+ permission is requested on Home ✅, add a **pre-permission primer** explaining the value before the system dialog (measurably lifts opt-in)
- [ ] 🟠 Handle permission denied gracefully; do not re-prompt aggressively
- [ ] 🟠 Notification content must be safe on a **lock screen**, no sensitive health data
- [ ] 🟠 FCM is a dependency but appears unused, decide between local-only (current) and server-driven, which is required for event notifications from other users' actions
- [ ] 🟡 Deep-link every notification to the relevant screen
- [ ] 🟡 Track notification → open → action in analytics
- [ ] 🟡 Test on Xiaomi/Samsung/OnePlus, where aggressive battery managers silently kill scheduled work

---

## 11C. SETTINGS AND PROFILE, incomplete 🟠

> **There is no profile screen.** `ProfileSetupScreen` is onboarding-only and unreachable afterwards. Settings has a minimal PROFILE section (name + user ID). A user cannot change their photo, age, height, or weight after onboarding, the data is collected once and then permanently uneditable.

### 11C.1 Profile

- [ ] 🔴 **Profile header at the top of Settings**, avatar, display name, and a summary stat (member since / total steps / current streak). Tapping it opens the profile editor
- [ ] 🔴 **Edit Profile screen**, name, photo, age, height, weight, all editable (currently only name is, via a dialog)
- [ ] 🟠 **Avatar upload**, the camera icon exists in onboarding but does nothing; either implement it or remove it
- [ ] 🟡 Personal stats view, totals, averages, best day, streak history

### 11C.2 Settings, what is missing

**Preferences**
- [ ] 🔴 **Theme selector (System / Light / Dark)**, you now have a full light+dark system but **no way for a user to choose**; it only follows the system. Many users want to force one
- [ ] 🔴 **Notification settings** (see §11B.8)
- [ ] 🟡 Units (metric/imperial), matters once distance is added
- [ ] 🟡 Week start day (currently hardcoded Monday)

**Health**
- [ ] 🟠 **Health Connect status + reconnect**, after onboarding there is no way to see whether it is connected or to fix it. This is the #1 predictable support issue
- [ ] 🟠 "Last synced" timestamp + a manual **Sync now**

**Legal & about**
- [ ] 🔴 **Open source licences**, legally required for the OSS you bundle. Use Google's `oss-licenses-plugin`, which generates the screen automatically from your dependencies
- [ ] 🔴 **Terms of Use** row, draft written at `legal/TERMS_OF_USE.md`, not yet linked in-app
- [x] ✅ Privacy Policy row. URL corrected to `arkayenlabs.com/privacy/pamoja`
- [ ] 🟠 App version ✅, add build number and make it tappable for debug info
- [ ] 🟡 "What's new" / changelog

**Support**
- [ ] 🟠 **Contact support / report a problem**, with device and app version pre-filled
- [ ] 🟡 Help / FAQ
- [x] ✅ Rate us

**Data**
- [ ] 🟠 **Export my data**. GDPR/DPDP right to portability
- [x] ✅ Delete account
- [x] ✅ Log out

**Account (later)**
- [ ] 🟠 Manage subscription (when billing ships)
- [ ] 🟠 Link account to Google, **currently, uninstalling the app permanently loses every group**. This is a serious retention problem, not just a nicety

---

## 12. FIREBASE BACKEND CONFIGURATION

- [ ] 🔴 Deploy security rules (§1.1)
- [ ] 🟠 Deploy all composite indexes from `firestore.indexes.json`
- [ ] 🟠 Verify every compound query has a matching index (a missing index fails **only in production**, with a console link in the error)
- [ ] 🟠 Set the Firestore location/region deliberately
- [ ] 🟠 Enable daily Firestore backups
- [ ] 🟠 Set a billing budget with alerts
- [ ] 🟡 Configure App Check to block requests from outside your app, **high value for this architecture**, since the client talks to Firestore directly
- [ ] 🟡 Review Firestore quotas against expected launch traffic
- [ ] 🟡 Plan a data-retention policy for old step entries (they accumulate forever, one document per user per day)

---

## 13. PLAY STORE LISTING

- [ ] 🔴 App title, short description, full description
- [ ] 🔴 Feature graphic (1024×500)
- [ ] 🔴 Phone screenshots (minimum 2, ideally 8), use the new design
- [ ] 🔴 High-res icon (512×512)
- [ ] 🔴 Category, tags, contact email
- [ ] 🟠 Screenshots in both light and dark to showcase the theming
- [ ] 🟠 Description clearly states the app reads step data and why
- [ ] 🟠 Country/region availability
- [ ] 🟠 Pricing (free) and in-app-purchase declaration (none)
- [ ] 🟡 Ads declaration (none)
- [ ] 🟡 Localised listings for target markets
- [ ] 🔵 Promo video

---

## 14. RELEASE PROCESS

- [ ] 🔴 Test the exact release AAB via **internal testing** before production
- [ ] 🔴 Verify the upgrade path: install the current beta build, then update to the release build, confirm no data loss and no crash
- [ ] 🟠 **Staged rollout starting at 10%**, never 100% on day one
- [ ] 🟠 Watch Crashlytics and Play vitals for 48 hours before ramping
- [ ] 🟠 Define the crash-rate threshold that triggers a halt
- [ ] 🟠 Know how to halt a rollout in Play Console
- [ ] 🟠 Tag the release in git and keep the mapping file archived
- [ ] 🟡 Write release notes
- [ ] 🟡 Set up a support email and monitor it
- [ ] 🟡 Have a plan for responding to Play Store reviews

---

## 15. POST-LAUNCH

- [ ] 🟠 Monitor crash-free user rate daily for the first week
- [ ] 🟠 Monitor Firestore costs daily for the first week
- [ ] 🟠 Watch the funnel: install → onboard → create/join group → first steps synced
- [ ] 🟠 Track invite-link conversion (this is the growth loop, instrument it)
- [ ] 🟡 Set up alerting for an ANR or crash-rate spike
- [ ] 🟡 Plan the first patch release cadence
- [ ] 🟡 Collect and triage user feedback

---

## 16. KNOWN TECHNICAL DEBT (accept or fix before launch)

- [ ] 🟡 Dead code: `StepCounterService.kt`, `StepCounterManager.kt`, `StepReader.kt`, legacy sensor approach, superseded by Health Connect. Remove to shrink the APK and the attack surface
- [ ] 🟡 `CreateOrJoinGroupScreen.kt` is not in the nav graph (dead screen; its ViewModel is used by Home)
- [ ] 🟡 `GroupHeader()` in `GroupScreen.kt` is a legacy alias with an unused `isAdmin` parameter
- [ ] 🟡 All UI strings are hardcoded in composables, blocks localisation entirely. `strings.xml` contains only `app_name`
- [ ] 🟡 `ui/components/` exists but is empty; shared composables live in `ProfileSetupScreen.kt`
- [ ] 🟡 No test suite beyond IDE templates
- [ ] 🔵 `kapt` → `KSP` migration

---
---

# PART II. MONETIZATION AND BUSINESS

> This is not "phase 2" work. Several items here are **irreversible after launch** or have **external deadlines**. Read this section before you ship.

---

## 17. MONETIZATION

### 17.1 The constraint that shapes everything: you cannot monetize with ads 🔴

Google Play's Health Apps policy explicitly prohibits, for apps handling health/fitness data:
- **Transferring or selling** health or fitness data to third parties, ad platforms, data brokers, or resellers
- **Using health or fitness data to serve ads**, including personalized or interest-based advertising
- Using it for credit-worthiness, insurance eligibility, employment suitability, or lending

**What this means for Pamoja:** an ad-supported model is effectively off the table as a primary strategy. You could technically show fully non-personalized ads that never touch step data, but for a warm, private, family-oriented product, ads would damage the brand far more than they'd earn. Combined with Health & Fitness having the **highest install LTV of any app category**, subscriptions are clearly the right model.

- [ ] 🔴 Decide formally: **subscription-led, no ads.** Write the decision down so it is not relitigated later
- [ ] 🔴 Confirm no ad SDK is present in the dependency tree
- [ ] 🔴 Confirm the Data Safety form and privacy policy state health data is **not** used for advertising
- [ ] 🟠 If you ever add ads, they must be non-personalized and provably isolated from all health data

### 17.2 Play Billing Library, hard deadline ⏰🔴

> **By 31 August 2026, all new apps and all updates must use Play Billing Library v8 or later.** An extension to 1 November 2026 can be requested. Library v9 shipped May 2026. **There is no direct v7 → v9 upgrade path**, you must go through v8.
>
> You are launching within weeks of this deadline. **Integrate v8 or v9 from day one.** Starting on v7 means an immediate forced migration.

- [ ] 🔴 Integrate **Play Billing Library v8 or v9** (never v7)
- [ ] 🔴 Add the billing dependency to `libs.versions.toml`, not as a hardcoded string
- [ ] 🔴 Set up a Google Play **merchant account** (required before you can sell anything)
- [ ] 🔴 Create subscription products in Play Console with base plans and offers
- [ ] 🟠 Decide: **native Billing Library** vs a wrapper (RevenueCat / Adapty / Superwall)
  - Native: no third-party dependency, no revenue share, but you build receipt validation, entitlement state, and paywall changes require an app release
  - Wrapper: server-side validation, entitlement management, and **remote paywall config + A/B testing without shipping an app update**, significant for iterating on conversion. Costs a revenue share above a free tier
  - *Recommendation for a solo/small team: use a wrapper.* Subscription state management is deceptively hard and getting it wrong means either lost revenue or angry users with broken entitlements
- [ ] 🔴 Implement **server-side receipt validation** (never trust the client for entitlement)
- [ ] 🔴 Implement purchase **acknowledgement within 3 days**, unacknowledged purchases are automatically refunded by Google
- [ ] 🔴 Handle **restore purchases** on reinstall and on a new device
- [ ] 🟠 Handle all subscription states: active, in grace period, on hold, paused, cancelled-but-active, expired, revoked/refunded
- [ ] 🟠 Set up **Real-time Developer Notifications (RTDN)** via Pub/Sub so your backend learns about renewals, cancellations, and refunds
- [ ] 🟠 Test with **licence testers** (real purchase flow, no charge) before launch
- [ ] 🟠 Test upgrade, downgrade, and proration between tiers
- [ ] 🟠 Test cancellation and confirm access persists to the end of the paid period
- [ ] 🟠 Test refund handling, access must be revoked

### 17.3 Involuntary churn, the biggest Android-specific revenue leak 🟠

> Roughly **one third of all Google Play subscription cancellations are involuntary billing failures**, more than **double** the App Store rate. This is not a user-intent problem, it is a payment-plumbing problem, and it is the single highest-ROI thing most Android subscription apps ignore.

- [ ] 🟠 Enable **grace periods** in Play Console (keeps access while a payment retries)
- [ ] 🟠 Enable **account hold** (suspends access without cancelling the subscription)
- [ ] 🟠 Implement an in-app "fix your payment method" prompt when a subscription enters grace/hold
- [ ] 🟠 Enable Play's **subscription restore / win-back offers**
- [ ] 🟡 Track voluntary vs involuntary churn separately, they need completely different fixes

### 17.4 What to actually charge for

**Design principle: never paywall step tracking itself.** It is the core value, the network effect depends on free users joining groups, and paywalling health data access reads badly to reviewers and users alike.

**Recommended tier structure:**

| | **Free** | **Premium** |
|---|---|---|
| Groups | 1 group | Unlimited groups |
| Group size | up to 5 members | up to 20 members |
| History | current week only | full history + weekly recaps |
| Stats | basic leaderboard | charts, trends, personal records, week-over-week |
| Streaks & badges | streak count only | full achievements system |
| Targets | 5 presets | fully custom targets |
| Extras |, | widgets, themes, data export, priority support |

- [ ] 🔴 **Decide the free/premium split BEFORE launch.** Tightening free limits later is one of the most reliable ways to anger an existing user base. Launching generous and restricting later requires grandfathering, which is permanent complexity
- [ ] 🟠 Consider a **group plan**, one admin pays, the whole group gets premium. For a social app this is the highest-leverage pricing model: it raises ARPU, and the payer gets social credit for it
- [ ] 🟠 Consider **corporate / team wellness (B2B)** as a serious second revenue line. Companies pay per-seat for employee wellness programmes, contract values dwarf consumer subscriptions, and Pamoja's group mechanic is already exactly the right product shape. This is plausibly the biggest long-term opportunity
- [ ] 🟡 Consider sponsored challenges, but **only** with non-personalized targeting, never using health data
- [ ] 🔵 Cosmetic IAP (badge/theme packs), low yield, generally not worth the complexity

### 17.5 Pricing and paywall

> Benchmarks: free→paid conversion for fitness apps typically **2–5%**. **Annual plans drive 60.6% of Health & Fitness revenue.** Free trials measurably help annual conversions in this category. Hard paywalls convert ~**10.7%** vs ~**2.1%** for freemium, but one-year retention ends up nearly identical, so freemium is the right call for a product whose growth depends on free users joining groups.

- [ ] 🟠 Offer **monthly and annual** plans; make annual visibly the better value (annual is where the revenue is)
- [ ] 🟠 Offer a **free trial on the annual plan** (7 days)
- [ ] 🔴 **Place the paywall after the "aha moment", never before it.** For Pamoja the aha moment is *seeing your first synced steps land on the group leaderboard*. Paywalling before that converts 3–5× worse
- [ ] 🟠 Set **local pricing** per country, do not just currency-convert USD. Price sensitivity in India, Brazil, and Indonesia is completely different from the US
- [ ] 🟠 Design the paywall screen properly (it is a core screen, add it to the design brief)
- [ ] 🟠 Show a clear value comparison, not just a price
- [ ] 🟠 Make cancellation easy and obvious, required by policy, and dark patterns get apps removed
- [ ] 🟡 **55% of 3-day trial cancellations happen on day 0**, the first session after subscribing decides everything. Make onboarding into premium immediate and rewarding
- [ ] 🟡 Plan paywall A/B tests (much easier with a wrapper SDK)
- [ ] 🟡 Add a win-back offer for lapsed subscribers

### 17.6 Legal and store requirements for selling

- [ ] 🔴 Subscription terms disclosed **before** purchase: price, billing period, renewal terms, trial length and what happens when it ends
- [ ] 🔴 Add subscription terms to your Terms of Use
- [ ] 🔴 Add a **Manage Subscription** entry in Settings linking to the Play subscription centre
- [ ] 🔴 Add a **Restore Purchases** action
- [ ] 🟠 Declare in-app purchases in Play Console and on the store listing
- [ ] 🟠 Update the Data Safety form to include purchase history
- [ ] 🟠 Confirm your refund policy and how you handle refund requests
- [ ] 🟠 Verify the current Play service fee tiers (a reduced rate applies to the first $1M of annual revenue; confirm the exact current terms in Play Console rather than trusting any blog)

---

## 17.6B Which payment provider, the Phase 2 decision 🟠

> **Correcting a common misconception: you will not pay 30%.** At your revenue scale the rate is **15%**, and it is heading lower. The fee landscape changed substantially in 2026, verify all of this in Play Console before committing, because it is still moving.

### What you would actually pay

| Situation | Service fee |
|---|---|
| **First $1M/year, small business programme** | **15%** (programme rate cards live from 30 Sep 2026) |
| First $1M/year. US, EEA, UK | **10%** headline, from 30 Jun 2026 |
| Above $1M/year | up to 30% |
| **India and "Rest of World"** | **unchanged at the current 15–30% structure until 30 Sep 2027** |
| User choice billing (your PSP alongside Google's) | service fee **reduced by 4%** |
| External web links / alternative billing | ~9–20% depending on path, **but add your own PSP's ~5% and you often land at or above where you started** |

### Why Google Play Billing is still the right answer for you

- **It is 15%, not 30%,** at your scale.
- **Google acts as merchant of record in most jurisdictions and handles VAT/GST remittance for you.** For a solo developer selling into dozens of countries with different digital-goods tax rules, this is worth the fee on its own. Running Stripe/Razorpay yourself means *you* become responsible for global tax compliance.
- **It works in every country with no integration difference.** Card penetration, UPI, carrier billing, gift cards. Google already supports the local methods.
- Alternative billing means you handle failed payments, refunds, chargebacks, disputes, and dunning yourself. Given that **a third of Play subscription cancellations are already involuntary billing failures** (§17.3), taking on payment plumbing as a solo dev is a bad trade.
- Regulatory changes (Epic settlement, March 2026) now permit external payments and links in the US, but the economics rarely favour a small developer, and it adds a second system to maintain.

- [ ] 🟠 **Default decision: Google Play Billing.** Revisit only if you exceed $1M/year or add a web product
- [ ] 🟠 Enrol in the **small business / Apps Experience programme** to secure the 15% rate, do not assume it is automatic
- [ ] 🟠 Verify the current fee tier for **your** country in Play Console (India timelines differ from US/EEA/UK)
- [ ] 🟡 If you later add a web signup flow, purchases made there carry **no Play fee** at all, a legitimate long-term lever
- [ ] 🟡 Revisit user choice billing only if the −4% meaningfully exceeds your PSP costs
- [ ] 🟡 Check whether your app was auto-enrolled for distribution to **third-party US Android app stores** (Google began this from 22 July 2026 unless developers opted out), decide if you want that

---

## 17.7 Infrastructure cost control 🔴

> **The fear:** "if the app succeeds, Firebase costs explode and I pay out of pocket."
> **The reality:** at realistic early scale this is tens of dollars per month, not hundreds. The thing that produces a frightening bill is **a listener bug**, not success. Control the bug risk and the growth risk takes care of itself.

**Rough model** (verify against current Firestore pricing, rates change):
- A user in one 8-member group costs roughly **~300 document reads/day** (group doc + memberships + user docs + a week of step entries per group-screen open, times ~3 opens/day, plus live listener updates)
- Free (Spark) tier: 50,000 reads/day and 20,000 writes/day → you exceed it around **150–250 DAU** (writes bite first if `StepSyncWorker` runs hourly)
- **1,000 DAU ≈ 9M reads/month ≈ single-digit dollars/month**
- **10,000 DAU ≈ tens of dollars/month**
- **100,000 DAU ≈ a few hundred dollars/month**, and at that scale even 2% conversion at $5/mo is ~$10k/month revenue, so infra is ~5% of income

**The actions that actually matter:**

- [ ] 🔴 Set a **Google Cloud budget alert** on the Firebase project immediately
- [ ] 🔴 Understand that **budgets do not stop spending**, they only notify. For a true ceiling, deploy the Cloud Function that disables billing when a budget threshold is crossed. Do this before launch for peace of mind
- [ ] 🔴 **Enable Firebase App Check.** It stops non-app traffic (scrapers, someone else's script) from consuming your quota. This is a security *and* cost control
- [ ] 🟠 **Audit every snapshot listener, this is the single biggest cost lever.** Real-time listeners bill per document delivered *and* per changed document. Ask of each one: does this genuinely need to be live?
- [ ] 🟠 **The leaderboard does not need real-time.** Steps sync roughly hourly anyway, so a live listener buys nothing a one-time `get()` plus pull-to-refresh wouldn't. Converting the group screen from `addSnapshotListener` to fetch-on-open could cut reads several-fold
- [ ] 🟠 Verify listeners are **not re-subscribed on recomposition**, `GroupViewModel.observeMembersAndSteps()` chains flows with `flatMapLatest` and has had a nested-collect bug before. A duplicated listener silently doubles your bill
- [ ] 🟠 Verify every listener is properly removed in `awaitClose`
- [ ] 🟠 **Denormalize member display names onto the membership document**, removes N user-document reads per group screen open, and simultaneously lets you lock down `users/{userId}` (see `firestore.rules`)
- [ ] 🟠 Enable **Firestore offline persistence** so repeat opens serve from cache instead of billing fresh reads
- [ ] 🟠 Reconsider `StepSyncWorker` frequency, hourly is 24 writes/user/day. Every 2–3 hours is likely indistinguishable to users and cuts writes proportionally
- [ ] 🟡 Cap the step-history query window (current week only for free users) rather than fetching unbounded history
- [ ] 🟡 Add a Firestore usage dashboard and check it weekly for the first month
- [ ] 🟡 Plan a retention/archival policy for old step documents, one doc per user per day accumulates forever

---

## 18. BUSINESS, TAX, AND PAYMENTS

> Applies from your first sale. Getting this wrong is a tax problem, not a bug.

- [ ] 🔴 Set up a **Google Play merchant / payments profile** (required before selling)
- [ ] 🔴 Provide bank account details for payouts and verify them
- [ ] 🔴 Complete tax identity information in the Google payments centre
- [ ] 🔴 Decide the legal entity: sole proprietor vs registered company. This affects tax, liability, and whether you are exempt from the personal-developer-account testing rules
- [ ] 🟠 **If based in India:** submit your **PAN** to Google (verified against the Income Tax database)
- [ ] 🟠 **If based in India:** determine whether you need a **GSTIN**. Google collects and remits GST for developers *outside* India, but an India-based merchant account may make **you** liable for GST on sales to Indian users. Google withholds GST TCS on taxable sales by Indian developers
- [ ] 🟠 **If based in India:** collect quarterly **Form 16A** withholding tax certificates from Google
- [ ] 🟠 **If based in India:** understand **FIRC** requirements for foreign inward remittance
- [ ] 🔴 **Consult an actual tax advisor.** Cross-border digital-goods VAT/GST is genuinely complicated and varies by buyer country. This checklist is not tax advice
- [ ] 🟠 Set up bookkeeping from month one, track revenue, Play's fee, taxes withheld, and Firebase costs
- [ ] 🟠 Model **unit economics**: Firebase cost per active user vs revenue per active user. Firestore charges per read, a chatty snapshot listener can make an active user cost more than they pay
- [ ] 🟡 Register the trademark for "Pamoja" if the brand matters long-term (note: it is a common Swahili word, so the mark may be weak)
- [ ] 🟡 Check the name is not already trademarked in your market by a competing fitness product
- [ ] 🟡 Secure the domain and social handles
- [ ] 🟡 Set a revenue-reporting cadence and know your break-even MAU

---

## 19. ANALYTICS AND GROWTH INSTRUMENTATION

> **Instrument before launch.** You cannot retroactively measure a funnel you did not track, and your first cohort is your most informative one.

- [ ] 🔴 Track the full activation funnel: install → onboarding start → profile created → health permission granted → **group created or joined** → **first steps synced**
- [ ] 🔴 Track invite-link conversion: invite created → link opened → group joined. **This is your growth loop, if you measure nothing else, measure this**
- [ ] 🟠 Track D1 / D7 / D30 retention
- [ ] 🟠 Track paywall views → trial starts → paid conversions
- [ ] 🟠 Track trial-to-paid and subscription churn
- [ ] 🟠 Identify your **north-star metric** (suggestion: weekly active *groups* that hit their target, it captures both engagement and the social loop)
- [ ] 🟠 Confirm **no health data or PII** reaches Analytics (policy requirement, see §1.5)
- [ ] 🟡 Set up a funnel dashboard you actually look at weekly
- [ ] 🟡 Track group size vs retention, the correlation will tell you your ideal group size and should drive the free-tier limit
- [ ] 🟡 Consider install attribution if you plan paid acquisition
- [ ] 🔵 In-app feedback or NPS prompt after a group hits its goal (best possible moment to ask)

---

## 20. STORE OPTIMIZATION AND ACQUISITION

- [ ] 🟠 Keyword research for the title and short description ("step challenge", "family fitness", "group steps", "walking challenge")
- [ ] 🟠 First two screenshots must communicate the *group* concept instantly, that is your differentiator against every solo step counter
- [ ] 🟠 Short description must land the hook in one line
- [ ] 🟠 Prompt for a Play Store review **after a positive moment** (goal achieved), never on launch, using the In-App Review API
- [ ] 🟡 Localise the listing for your primary markets
- [ ] 🟡 Plan the launch channels. WhatsApp/family groups are your natural distribution given the product
- [ ] 🟡 Build a simple landing page (you already have Firebase Hosting for the privacy policy)
- [ ] 🟡 Prepare a Product Hunt / Reddit / community launch
- [ ] 🔵 Consider a referral incentive (invite N friends → free premium month), fits the product's mechanics perfectly

---

## 21. SUPPORT AND OPERATIONS

- [ ] 🔴 Set up a support email and **actually monitor it** (required on the store listing)
- [ ] 🟠 Write an FAQ covering: steps not syncing, Health Connect setup, invite link not working, subscription and billing questions
- [ ] 🟠 Define a policy for responding to Play Store reviews (respond to every negative one)
- [ ] 🟠 Have a documented rollback plan and know how to halt a staged rollout
- [ ] 🟠 Define who handles a data-breach notification and within what timeframe (GDPR: 72 hours)
- [ ] 🟡 Write a runbook for the top 5 predictable support issues
- [ ] 🟡 Set up a status/changelog page
- [ ] 🟡 Plan the release cadence and how you communicate updates
- [ ] 🔵 Build a small community space for engaged users
