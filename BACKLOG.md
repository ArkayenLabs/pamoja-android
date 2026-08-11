# Pamoja Backlog

**Last updated: 2026-08-11.** This is the working handoff document. `CHECKLIST.md`
is the exhaustive launch gate, `SUMMARY.md` explains the why behind the priorities.

Read this first in a new session. It says what is done, what is left, in what
order, and which conventions were established along the way.

---

## 0. WHERE THINGS STAND

Branch `dev`, everything pushed to `origin/dev`. 25 commits ahead of the state
this work started from. `./gradlew assembleDebug` passes, `./gradlew lint` is at
**0 errors**, and a signed release build has been produced once with R8, so the
release path is known to work.

**Nothing has been run on a physical device yet.** That is the single biggest
gap. Auth, phone OTP, App Links and Health Connect revocation are all
code-complete and entirely unverified against real hardware.

### The deadline that shapes everything

RevenueCat Shipaton 2026. Researched and confirmed on 2026-08-11:

- Submission window closes **30 September 2026, 11:45pm PDT**
- App must be **live**, not "in review". RevenueCat advise being live by **23 Sep**
- **Closed testing does NOT disqualify you.** RevenueCat's own prep guide says so
  explicitly, and treats it as a required step for new personal Play accounts.
  Pamoja has never been published to production, so it is eligible
- Mandatory: **RevenueCat SDK powering at least one in-app purchase**
- Demo video: public, **under 2 minutes**, showing the app on a device
- Judges must be able to test premium: a free trial, or a promo code
- RevenueCat Paywalls are **not** required. A custom paywall was chosen

Target: live around **20–25 August**, which leaves roughly five weeks of slack.

---

## 1. DONE THIS SESSION

### Authentication, replacing anonymous entirely
- Hard sign-in gate. Google, phone OTP, email/password. No anonymous accounts
- Landing, phone entry with searchable country picker, OTP, email sign-up and
  sign-in, forgot password. All share one AuthViewModel scoped to a nested graph
- Google via Credential Manager, using `default_web_client_id`
- Launch routing distinguishes signed-in-but-no-profile from fully onboarded

### Account deletion, was badly broken
- Deletion removed only the Firebase Auth record. The user document, every
  membership and every step entry stayed behind, and since the rules key on
  `request.auth`, destroying the account first made that data permanently
  unreachable. Right-to-erasure failure, and every group they belonged to was
  left over-counted
- Now: data first, memberships decrement each group's count and revive the
  invite link, steps deleted in chunks, auth record last
- Re-authentication before deletion for all three methods, including SMS

### Monitoring and security
- Crashlytics, with ProGuard keeping `SourceFile,LineNumberTable`. Without those
  the uploaded mapping still cannot produce a line number
- Firebase App Check with Play Integrity. **Enforcement is off** and must stay
  off until the debug token is registered
- `firestore.rules` gained a leave-group case. Case A only ever permitted
  memberCount to increase, so account deletion was denied for non-admins

### Invite flow
- Verified https App Links, bare URL sharing, resolution by document ID
- **Invite preview screen.** A link used to join silently and navigate, so the
  first thing anyone learned about a group was that they were already in it.
  Now it resolves read-only and shows name, member count and weekly target
  before asking. Handles already-a-member, group full, dead code, offline

### State coverage, design brief §3
- `AppError` taxonomy, mapped once at the data-layer boundary
- `ConnectivityObserver`, checking `NET_CAPABILITY_VALIDATED` not merely
  "a network exists", so captive portals report as offline
- Component library in the previously-empty `ui/components/`: `PamojaTextField`
  with on-blur validation, `PamojaEmptyState`, `PamojaErrorState`,
  `PamojaNotice`, `OfflineBanner`, `PamojaConfirmDialog`, skeletons
- Home and the group dashboard fully covered. **Group dashboard had a live bug**:
  every load failure rendered an infinite spinner, because the error branch was
  unreachable code
- Health Connect revocation is now detected on resume. The flag was only written
  by our own permission dialog, so revoking in system settings left it stuck at
  granted forever

### Internationalisation
- Every screen reads from `strings.xml`. Adding a language is now a translation
  job, not a code change
- Country names come from `Locale.getDisplayCountry()`, so they are already
  localised in every language Android supports. That avoided 65 translatable
  strings
- No ViewModel holds user-facing copy or needs a Context
- `AppError.Validation` carries a `ValidationField` enum; the UI supplies wording

### Legal
- Terms of Use written, filled in and published as `web/terms-pamoja.html`
- **Privacy policy rewritten.** The live one said anonymous auth and "we do not
  collect your email address or phone number", both false since this session
- Removed claims about features that do not exist: leaving a group, admins
  removing members, revoking invite links

---

## 2. WHAT YOU MUST DO, no code involved

These block progress and only you can do them.

- [ ] 🔴 **Fix Google sign-in.** It failed on the 2026-08-11 device run, see §6.
      Start with `./gradlew signingReport` and confirm that SHA-1 is registered
      on the Firebase project. This blocks launch
- [ ] 🔴 **Run the *release* APK on a real phone.** A debug build was exercised
      on 2026-08-11 (§6) and most of it worked, but R8 full mode breaks
      reflection-based code in ways debug builds never reveal. Still owed: phone
      OTP with a real SMS, account deletion, and a genuine second member joining
- [ ] 🔴 **Register the App Check debug token** before enabling enforcement.
      Run a debug build, `adb logcat | grep -i DebugAppCheck`, paste it into
      Firebase App Check → Apps → Manage debug tokens. Enabling enforcement
      first will break every Firestore call in development
- [ ] 🔴 **Deploy the legal pages.** `web/privacy-pamoja.html` to
      `/privacy/pamoja` and `web/terms-pamoja.html` to `/terms/pamoja`. The
      terms route currently returns a soft 404
- [ ] 🔴 **Redo the Play Data Safety form.** It under-declares: you now collect
      email, phone number, crash logs and diagnostics. Point the account
      deletion URL at the new deletion section
- [ ] 🔴 **Back up `D:\Play Console\Pamoja\upload-keystore.jks`.** Still a single
      copy on one drive
- [ ] 🔴 **Start the Play merchant / payments profile today.** Added 2026-08-11.
      Nothing about monetization can be tested until this chain finishes, and
      every link in it is someone else's queue: payments profile → bank account
      verification → tax identity (PAN verified against the Income Tax database)
      → *only then* can subscription products be created → only then can a
      licence tester make a test purchase. §3.3 targets the code for early
      September, but the code was never the long pole. A single verification
      bounce in September costs the Shipaton submission. Start it now and it
      matures in the background
- [ ] 🟠 **Firebase test phone numbers**, for the judges. Authentication →
      Sign-in method → Phone → Test phone numbers. A fixed number and code, no
      SMS sent, no cost. Do the same with a throwaway email account
- [ ] 🟠 **Install the CodeRabbit GitHub App** on the repo. `.coderabbit.yaml`
      is committed but dormant and does nothing until the app has access
- [ ] 🟠 Google Cloud budget alert on the Firebase project
- [ ] 🟠 Confirm `support@arkayenlabs.com` receives mail. The site publishes
      `hello@`, and Play requires a monitored address
- [ ] 🟡 Ask RevenueCat on Discord to confirm eligibility in writing. The
      evidence is strongly in your favour but it is worth having

---

## 3. WHAT I DO NEXT, in this order

### 3.1 Finish the state matrix, design brief §3 — done for three of four

**Create group, profile setup and invite are covered** as of 2026-08-11.
`CHECKLIST.md` §11.5 for those three is satisfied. What the round actually
found, beyond the missing states:

- **Both forms could submit invalid input.** The submit buttons asked
  `name.isNotBlank()`, which is true for `"   "`, so a whitespace-only name
  passed the gate and reached Firestore trimmed to `""`. Profile setup had the
  same hole on age, height and weight: the on-blur check flagged an age of 999
  but the button never consulted it. Each rule is now defined once and asked
  by both the field and the button
- **Both forms hung forever offline.** Firestore only completes a write Task on
  server acknowledgement, so `set().await()` never returns without a network and
  the button spun with nothing behind it. Both now block with an explanation
  instead of faking progress
- **Failures were snackbars.** They announced themselves for four seconds and
  left, so a form that had silently done nothing looked filled in and saved.
  Now inline and persistent, next to the button they block
- **Invite rendered a guess as fact.** The member cap defaulted to 10 while the
  group was still loading, so it stated the wrong number and then corrected
  itself. Now a skeleton until the fetch answers. The group name was being
  fetched and never displayed at all, so the screen never said which group you
  were inviting people to
- **Invite treats a failed load as partial.** The link is derived from the group
  ID, never fetched, so copy and share keep working while only the details retry

**Settings state coverage was deliberately not done here.** §3.2 §4B rewrites
that screen: profile header, edit-profile, theme selector, notification
settings. Covering the current one first means building it twice. Do it as part
of §4B.

### 3.2 The redesign proper
`design/DESIGN_BRIEF.md` rounds still to run, in the order the brief gives.

**§4B profile and settings, started 2026-08-11.**

Done:
- **Profile editor** at `ui/profile/`, reachable from a profile header at the
  top of Settings. Name, age, height and weight, all editable and clearable,
  with the same validation rules as onboarding so a value onboarding would
  reject cannot become acceptable later. Full state coverage: load skeleton,
  load failure with retry, offline gate, saving, save failure inline, success
- **Fixed a data-loss bug found on the way.** The Settings rename dialog built
  a `User` from just the id and the new name, and `updateUser` writes the whole
  document with `set()`. Renaming yourself erased your age, height, weight and
  deviceToken. The dialog is gone; the editor copies onto the loaded document

Still to do in §4B, roughly in value order:
- **Theme selector, System / Light / Dark.** 🔴 The app has a full light and
  dark system and still no way for a user to choose
- **Health Connect status, last synced, and Sync now.** The predictable #1
  support issue, since after onboarding there is no way to see or fix it
- **Notification settings screen**, per-channel toggles and quiet hours
- **Account section**: which sign-in method is connected, add a second method,
  change password. `AuthRepository.linkGoogle/linkEmail/linkPhone` already
  exist and are unused, this is what they were written for
- **Open source licences.** Legally required. Use `oss-licenses-plugin`
- **Avatar upload.** Needs a Firebase Storage bucket, rules and a cost surface,
  so it is a genuine piece of work rather than a UI job. The editor currently
  shows initials and says photos are coming; onboarding's camera icon is still
  a dead control
- Export my data, contact support, units, week start day

Then:
- **§4C notifications**, including the settings screen for per-channel control
- **§5 small additions**

### 3.3 Monetization, target early September
Decided: **RevenueCat SDK, custom Compose paywall**, not Paywalls V2. Even with
custom UI, RevenueCat still controls offerings server-side, so pricing and
products change without shipping; only layout is baked in.
- Free vs premium split must be decided before launch. Loosening later is easy,
  tightening later means grandfathering forever
- Paywall goes **after** the aha moment: first synced steps on the leaderboard
- `CHECKLIST.md` §17.4 has the proposed tier table

### 3.4 Tests
`CHECKLIST.md` §2.4 names the three places a silent bug corrupts real data:
`joinGroup()`'s cap transaction, week-boundary maths, step aggregation. There is
no test suite beyond IDE templates. The email regex is now plain Kotlin, so use
cases are unit-testable off-device for the first time.

### 3.5 Submission materials
Demo video under 2 minutes, store listing, screenshots in both themes.

---

## 4. CONVENTIONS ESTABLISHED, do not regress these

Most were established by fixing the same bug more than once.

- **Never classify errors by matching message text.** Several bugs came from
  `contains("expired")`, `contains("index")`, and one screen detecting session
  expiry by searching for the words "Session expired". Match on type
- **Never show `exception.message`.** `toErrorCopy()` / `toSnackbarMessage()`
- **`runCatching` around suspend work swallows `CancellationException`**, which
  turns a cancelled coroutine into a fake error. Use `authCatching`, or catch
  and rethrow cancellation explicitly
- **Domain layer is pure Kotlin.** No Android imports, including fully qualified
  ones like `android.util.Patterns`. No user-facing strings
- **`LocalContext.current as Activity` throws** on a wrapped context. Use
  `LocalActivity.current` and handle the null
- **`stringResource` needs a composable scope.** Hoist into locals for anything
  used in `validate` lambdas, click handlers or coroutine scopes
- **Do not resolve strings inside `remember`.** They freeze against language
  changes. Remember the input, resolve outside
- Colours from `LocalPamojaColors`, icons from `PamojaIcons`, no emoji anywhere
- Distinguish "still loading" from "genuinely empty" with a `hasLoadedOnce`
  flag, or the empty state flashes before content lands
- Run `./gradlew lint` before committing. It found three real crashes this
  session that the build did not

---

## 5. KNOWN GAPS, deliberately left

- ~~**No profile screen.**~~ Done 2026-08-11, see §3.2. Age, height and weight
  are now shown in Settings and editable
- **`AuthRepository.linkGoogle/linkEmail/linkPhone` are unused.** Written for
  adding a second sign-in method from Settings, which is not built yet
- **FCM ships in the APK unused.** No `FirebaseMessagingService`, no
  `onNewToken`. Needed only for notifications triggered by other people
- **`deactivateInviteLink` is dead code**, not wired to any UI
- **Analytics does not cover the auth funnel.** `CHECKLIST.md` §19 wants
  install → sign-in → group joined → first steps synced, and you cannot backfill
  a funnel you did not instrument
- **Legacy `pamoja://` scheme still accepted.** Harmless, and no links from the
  closed test are worth breaking

---

## 6. DEVICE RUN, 2026-08-11

A ~4 minute recorded run on a real phone, on a debug build that included the
§3.1 state-coverage work. Supersedes the 2026-08-04 run below.

### Worked
- **Email sign-up and sign-in**, including sign out and sign back in
- **Profile setup**, Health Connect connect, and **real step data syncing**
  (1,568 steps landed on the dashboard and the leaderboard)
- **Group creation** end to end, with the goal and cap sliders and the
  members-can-edit toggle all persisting what was set
- **Invite screen showing the group name and the real cap of 12**, which is the
  §3.1 change verified on device
- **Copy link, the system share sheet, and the bare URL** rendering as a
  tappable link
- **The invite link opening the app** and the preview correctly saying
  "already a member" rather than joining silently
- **The web fallback page** for people without the app
- **Notifications**, three channels posted, and the rewritten tone reads
  correctly ("320 from Priya. That is 3 minutes of walking.")
- Privacy Policy link opens and renders

### Failed
- 🔴 **Google sign-in fails.** "Opening Google…" then the generic failure
  notice. Not a cancellation, that path is silent by design. This is the
  primary sign-in method and it is a launch blocker. Almost certainly console
  config rather than code: the debug keystore's SHA-1 is probably not
  registered on the Firebase project. Capture the real cause with
  `adb logcat | grep -iE "credential|signin|GoogleId"` while reproducing, and
  check the SHA-1 from `./gradlew signingReport` is listed in Firebase
  → Project settings → Your apps. The release keystore needs its own entry
- 🔴 **Terms of Use opens a hard 404** at `arkayenlabs.com/ter…`. Confirms §2's
  "deploy the legal pages", now witnessed from inside the app. Reviewers click
  these
- 🟠 **Home greeted "Good afternoon, there"** while Settings showed the name
  correctly. Fixed: `getCurrentUser()` returns `User(userId = uid)` with a
  blank name, so the greeting fell back for every user on every sign-in
  method. Home now reads the profile
- 🟠 **"1 members"** on the group dashboard, in the header and the stat tile.
  Fixed with a `member_count` plural. The string was also hardcoded English,
  which the extraction pass had missed
- 🟠 **A failed Google attempt followed the user onto the email screen** and sat
  under the password field saying "That did not work" while they typed. Every
  auth screen shares one ViewModel and nothing cleared the error on
  navigation. Fixed

### Still not exercised
Phone OTP with a real SMS, account deletion, joining as a genuinely new second
member, and anything on a release build under R8.

---

## 6B. VERIFIED WORKING ON DEVICE, 2026-08-04

- Step sync and leaderboard totals
- Light and dark theme across every screen
- All screens and navigation
- Four notification channels
- Onboarding, group creation, settings
