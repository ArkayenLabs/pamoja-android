# Deploy checklist: end of the Shipaton freeze

**Written 2026-09-29 against `rebuild` at `5da2755`, `release/1.0` at `db4593a`,
`dev` at `4be3a80`.** Every claim below was checked against the code that day.
If any of those branches has moved, re-check the "What changes" table first.

Nothing here runs until the user says the freeze is over. Every step is run by
the user (or on their explicit go-ahead), one at a time, in this order.

---

## Production route (written 2026-10-14; Shipaton is over, release planned 2026-10-15)

The freeze ended 2026-10-14 (results out, Pamoja did not place). The rules,
functions and TTL from steps 2 to 4 below have been live since 2026-10-01;
what is left is the security review, O1, the app and the store. Each step
still waits for the user's word.

**1.0.13 (Release A) is skipped.** It existed to put the minimum-version gate
on installs before the rebuild; 1.1.6 carries the same gate
(`FirebaseAppConfigRepositoryImpl`, `min_supported_version_code`), and every
1.0.12 user who updates gets it from 1.1.6. Leave Remote Config unset.

| # | Step | Who | Notes |
|---|---|---|---|
| 1 | Commit: the security review (staged, its own commit), then O1 + the delete-dialog line | Claude, on the user's word | |
| 2 | `firebase deploy --only firestore:rules,firestore:indexes` | Claude, on the user's OK | Watch Rules Deny/Error for 30 min (baseline 0). The index is `removed.userId`, collection group. |
| 3 | `functions:notifications`, then `functions:weekly-history`, then `functions:billing` (`FUNCTIONS_DISCOVERY_TIMEOUT=60`) | Claude, on the user's OK | Answer No to any deletion prompt. `cleanUpDeletedGroup` is the one new function. |
| 4 | Push `rebuild` and `release/1.0` | Claude, on the user's OK, only after 2 and 3 | The review is a disclosure until both are live. |
| 5 | Full instrumented suite + lint once, then the bundle: `./gradlew bundleRelease -PpamojaVersionCode=23 -PpamojaVersionName=1.1.6 -PtogetherTrailEnabled=true` (sales on via `local.properties`) | Claude, when the user says build | Copy to `design/release-builds/pamoja-1.1.6-23.aab`. |
| 6 | Upload 1.1.6 to **internal testing** and test on a real phone (list below) | User | Nothing after 1.1.2 has been on a phone yet. |
| 7 | Play Console, in parallel with 6: Data safety against `legal/DATA_SAFETY.md`; store listing from `design/store-listing/listing-1.1.md` (icon 512, six screenshots, feature graphic, short/full text, What's new); the 7-day free trial on the Premium base plan(s) | User | Listing changes go through review too; publish them with the release so 1.0.12 users never see Lilac screenshots of an app they don't have yet. |
| 8 | Legal pages live: fast-forward `dev` to `rebuild` and push (`dev` is an ancestor, checked 2026-10-14). The push to `dev` triggers `redeploy-website.yml`, which republishes privacy, terms and deletion pages; Android CI also runs | Claude, on the user's OK | The drafts have not been read by a lawyer (user's call). |
| 9 | Website repo (not on this machine): Lilac join page + `web/og/pamoja-invite-og.png` (step 6 below), O2 (debug key out of `assetlinks.json`) and O3 (strip the group ID from Vercel Analytics) if the user says yes | User, or Claude with that repo open | |
| 10 | Promote the tested internal release to **production, staged**: 20% first, then 50%, then 100% a day or two apart, watching the triggers at the end of this file | User | Play review of an update usually takes hours, sometimes longer: "publish tomorrow" means submitted tomorrow. Managed publishing lets the listing and the app go live together. |
| 11 | Tag what 1.0.12 was built from (`git tag v1.0.12-prod 4be3a80`) once confirmed, and `v1.1.6` on the bundle's commit | Claude, on the user's word | |

**Checked read-only against production on 2026-10-14**, so steps 2 and 3
change only what git says they change:
- Live Firestore rules are ruleset `71df964d…` (2026-10-01 16:22 UTC),
  identical to `HEAD`'s `firestore.rules` apart from comment emoji encoding.
- Live index config = the two composite indexes in the file plus the
  `signals.expireAt` TTL override, which was set with gcloud and was missing
  from `firestore.indexes.json`. **It is now in the file, copied exactly:**
  without it, `firestore:indexes` would have offered to delete the TTL.
  The only addition is `removed.userId`.
- Live functions: billing 3, notifications 11, weekly-history 17, matching
  the local exports exactly except `cleanUpDeletedGroup` (new). Nothing is
  deleted by the deploy.

**Steps 1 to 3 done 2026-10-14 with the user's OK:**
- Committed `a4693cb` (security review) and `25fcf57` (O1, O11, release prep).
- Rules + indexes from `25fcf57` released 13:56 UTC: live ruleset
  `5f7ca95b…` is byte-identical to `firestore.rules`; `signals.expireAt` TTL
  still ACTIVE; `removed.userId` (collection and collection group) READY.
  Baseline before: 1,254 allows, 0 denies or errors in 24 hours.
- Functions: notifications 14:01 UTC (1 created, `cleanUpDeletedGroup`; 11
  updated; none deleted), weekly-history 14:04 (17 updated), billing 14:06
  (3 updated). 32 live. Non-interactive deploys need `--force` when a new
  function retries on failure; it also skips the deletion prompt, so check
  live against local exports first (done: nothing to delete). No errors in
  the logs after; rules 0 denies in the first 15 minutes.
- Rollback, if needed: rules history in the console (the previous release is
  `71df964d…`), functions by redeploying a codebase from `30405d3`.
- **Step 4 done 2026-10-14 ~14:15 UTC:** `rebuild` and `release/1.0` pushed
  to GitHub (new branches; `dev` not moved, so the legal pages are not
  republished yet: that is step 8).
- **Step 5 done 2026-10-14:** full device suite on `4d2c1b3`: 194, 193
  passed, 1 skipped (the usual), 0 failures. Bundle 1.1.6 (23) at
  `4d2c1b3`: `design/release-builds/pamoja-1.1.6-23-internal.aab`, release,
  sales on, Trail sync on, signed with the upload key (`EC:3F…`), 13.9 MB.
  Production 35 minutes after the rules deploy: 115 allows, 0 denies, no
  function errors.
- **Step 6 started:** the user uploaded 1.1.6 (23) to internal testing on
  2026-10-14; the phone test below is still to do.
- **Phone test, part 1 (2026-10-19, Claude over adb; vivo I2301, Android 15,
  1.1.6 (23) from Play):** no crash anywhere. Passed: Home, Last week and its
  review, Past weeks, group switcher, paywall with Play prices (no trial
  yet: no offer), weekly card both weeks and share, invite screen, invite
  link opening the app (app link verified), Settings and every sub-page,
  data export (all sections incl. `high_fives_and_reminders`), light and
  dark, three widgets drawing and following the active group. Found:
  the past-week card says "this week, together" (`weekly_card_walks_label`);
  finished weeks read "Walked N of 7 days so far" to screen readers
  (`home_days_walked_desc` reused in WeeklyReviewScreen). Both fixed the
  same day, uncommitted, for the next build (`weekly_card_walks_label_finished`,
  `week_days_walked_desc`, en/es/hi). Still for the
  user: sign-in, pushes and the two-person flows, remove and let back in,
  invite before sign-in and fresh install, license-tester purchase and
  restore, Health Connect grant, account deletion.
- **Checked 2026-10-20 (Claude):** 1.1.6 permissions equal production
  1.0.12's (Health Connect unchanged, so no new Play declaration);
  `ACCESS_ADSERVICES_ATTRIBUTION` removed for 1.1.7 like AD_ID (answer "No"
  to the Advertising ID question). Backend = git, no function errors since
  the 10-14 deploy; Trail callables' 400s are the same before and after it.
  Remote Config empty. Health Connect on the phone totals 7 steps today
  (Google Fit, then the phone itself), the same as Pamoja. Lint 0 errors.
- **Open, not blocking:** saving the data export (Settings > Export > Save)
  causes 1 to 2 rules-denied single-document GETs in production, 3 of 3
  times; cancelling the picker causes none (2 of 2), nor does any other
  step replayed. The file is complete and nothing visible fails. Nothing
  in the save path reads Firestore, so the document is unknown: production
  has no per-request rules logs. Finding it needs Firestore Data Access
  audit logs switched on briefly (the user's call).
- Next: the phone test, step 7 in parallel, then steps 8 to 11.

**Phone test for step 6** (release build from Play, both themes): sign in
(Google first tap, phone); a two-person group: High five and Remind arrive as
pushes; the goal moment; the invite link before sign-in, sign-in, then the
automatic join; a fresh install from the link (Install Referrer); remove the
second person, check their link shows "You can't rejoin", let them back in
and rejoin; Last week on Home; widgets; Health Connect permission and a sync;
weekly card share; data export; paywall with a license tester (prices, the
trial once it exists, purchase, restore); delete a throwaway account (the
subscription line shows). R8 was narrowed, so every flow on the release build.

---

## What changes, and why the order matters

| Piece | Changes from `dev` (what production runs) | Safe for 1.0.12? |
|---|---|---|
| `firestore.rules` | New `signals` subcollection; memberships accept `walkedDays` / `walkedWeekStart`; group create refuses streak/shield fields; `weeks/{streakThroughWeek}` becomes a free `get` | Yes. Old clients never write the new keys; the rules read them with defaults. 1.0.12 group writes are all field-level `update()`s (its whole-document `updateGroup` `set()` has no callers), so server-written streak fields survive them. |
| `functions-notifications` (codebase `notifications`) | Adds `notifySignalCreated`, `eraseDepartedMemberSignals` | Yes. Both only act on `signals`, which only the new app creates. |
| `functions-weekly` (codebase `weekly-history`) | Finalizer also computes the group streak and shields, writes them onto the group, marks `shielded` weeks | Yes. Old clients ignore unknown fields. |
| `functions` (codebase `billing`) | **No change. Do not deploy.** | n/a |
| `firestore.indexes.json`, `storage.rules`, `firebase.json` | **No change.** The new queries are single-collection equality filters, served by automatic indexes. | n/a |
| TTL policy | New: collection group `signals`, field `expireAt` (stamped by `notifySignalCreated`, 14 days) | n/a |
| `web/join-landing.html` | `intent://` button, Play fallback carrying `referrer=invite=<code>`, Lilac colours | Yes. 1.0.12 handles `pamoja://join` (its manifest declares it). |
| App | `release/1.0` = 1.0.13 (version gate, Phase 1 fixes). `rebuild` = the Lilac app. | |

**Hard order: rules, then functions, then the `rebuild` app.** A `rebuild` build
against the old rules has its **entire** step publish refused (`onlyChanged`
rejects `walkedDays`), so its users' figures would stop updating.

**One-way door:** once any `rebuild` build is installed anywhere outside your
own devices, the rules can never go back to `dev`'s version, for the same reason.
Before that point, rolling rules back is safe.

`release/1.0` (1.0.13) has **no backend dependency**. It can ship on day one,
in parallel with the backend steps.

---

## Route chosen 2026-10-01: internal testing while Shipaton is judged

Submissions closed 2026-10-01 and judging has no known end date. The user
chose to keep going without touching what judges see: they have the
production build (1.0.12) and its listing.

- **Goes ahead now:** step 2 (rules), step 3 (functions), step 4 (TTL), then
  the `rebuild` bundle on the **internal testing** track only (step 7's
  internal list).
- **Stays frozen until results:** the production track (including 1.0.13),
  the store listing (icon, screenshots), the trial offer (step 5), RevenueCat,
  and the web join page (step 6).
- Checked 2026-10-01 before deploying: live rules are `dev`'s exactly
  (ruleset created 2026-09-13); the notifications deploy only adds
  `notifySignalCreated` and `eraseDepartedMemberSignals`, the weekly deploy
  replaces its 17 functions in place, nothing is deleted; instrumented suite
  on `4491d68` 153 (1 skipped); functions tests 8 and 46 passed (25 skipped
  need the emulator and cover Trail and planning, unchanged here).
- **Step 2 done 2026-10-01 16:22 UTC** by the user: live ruleset
  `71df964d…` is byte-identical to `rebuild`'s `firestore.rules`. Rules
  monitor baseline before it: zero denies or errors in six hours. From now
  on, rolling the rules back breaks only testers on the new app.
- **Steps 3 and 4 done 2026-10-01:** notifications 16:44 UTC, weekly 16:48
  UTC (needed `$env:FUNCTIONS_DISCOVERY_TIMEOUT=60` in PowerShell: the CLI's
  10 s discovery timeout fails on this machine with "User code failed to
  load"); 27 to 29 functions, none removed, billing untouched; `signals.expireAt`
  TTL ACTIVE. No function errors; rules 0 denies in the first 34 minutes.
- **1.1.0 (17) tested by the user 2026-10-02**; feedback fixed in 1.1.1
  (18), `design/release-builds/pamoja-1.1.1-18-internal.aab`. Upload it to
  internal testing the same way.
- **Notifications rewrite (`857bc7f`) deployed 2026-10-08 ~06:40 UTC:**
  `firebase deploy --only functions:notifications` (Bash, with
  `FUNCTIONS_DISCOVERY_TIMEOUT=60`): 9 updated, `sendScheduledReminders`
  created (scheduler job `5 * * * *` UTC, ENABLED), none deleted, 30 functions
  live. From now 1.0.12 and 1.1.x get the new push words (data-only, as
  before). The CLI warned it could not clean up build images in
  `gcr.io/.../asia/gcf` (small storage cost; delete there if it grows).
- **1.1.2 (19)** built 2026-10-08: `design/release-builds/pamoja-1.1.2-19-internal.aab`,
  same flags as 1.1.1 (`-PpamojaVersionCode=19 -PpamojaVersionName=1.1.2
  -PtogetherTrailEnabled=true`, sales on). First build that registers
  `delivery: 2`, so the first to get tray-drawn pushes and scheduled
  reminders. Uploaded to internal testing by the user 2026-10-08.
- **1.1.3 (20)** built 2026-10-08 at `4bbe8b6`:
  `design/release-builds/pamoja-1.1.3-20-internal.aab`, same flags. Adds the
  background-restriction card in Settings (ColorOS, MIUI, vivo, Samsung,
  Huawei/Honor) and the "Remind" widget preview. Not uploaded yet.
- **1.1.4 (21)** built 2026-10-10 at `57594e0`:
  `design/release-builds/pamoja-1.1.4-21-internal.aab`, same flags
  (`-PpamojaVersionCode=21 -PpamojaVersionName=1.1.4
  -PtogetherTrailEnabled=true`). Supersedes 1.1.3. Registers `delivery: 3`
  and reports opens, so scheduled reminders go quiet after 8 unopened.
  `functions:notifications` redeployed from `3695147` (same functions as
  `57594e0`) on 2026-10-10 ~10:24 UTC with the user's OK: 10 updated, none
  created or deleted, 30 live, no errors right after. Older builds are
  unaffected (backoff only for delivery 3). Also: notifications follow the
  app's language on Android 12-, Hindi and Spanish widget previews.
  Not uploaded yet; superseded by 1.1.5.
- **1.1.5 (22)** built 2026-10-10 at `6a0c4f1`:
  `design/release-builds/pamoja-1.1.5-22-internal.aab`, same flags
  (`-PpamojaVersionCode=22 -PpamojaVersionName=1.1.5
  -PtogetherTrailEnabled=true`). "High five" instead of "Cheer", the new
  Remind line, and the goal-reached moment (GoalCelebration). Its copy
  needed `functions:notifications` redeployed from `6a0c4f1`: done
  2026-10-10 ~11:38 UTC with the user's OK, 10 updated, none created or
  deleted, 30 live, no errors after. The first try failed with "User code
  failed to load" while a Gradle build ran; the retry used
  `FUNCTIONS_DISCOVERY_TIMEOUT=120` on an idle machine. Not uploaded yet.
- **`getInvitePreview` created 2026-10-11, about 05:10 UTC** (`functions:notifications`
  from `5f6acf8`, user's OK): 1 created, 10 updated, none deleted, 31 live.
  A call without App Check returns 401; no errors after. Nothing uses it
  until a build with the invite-before-sign-in screen ships.
- **Milestone and named-recap pushes deployed 2026-10-11 ~05:45 UTC**
  (`functions:notifications` from `7f6587d`, user's OK): 11 updated, none
  created or deleted, 31 live, no errors since 04:00 UTC. New lines: first
  goal ever, 4 and 10 weeks in a row, "X walked every day last week".
  Apps before the next build show them in the server's English.
- Bundle: `design/release-builds/pamoja-1.1.0-17-internal.aab`, versionCode
  17, "1.1.0", sales on, Trail sync on. 17 does not block 1.0.13 (16) on
  production later.
- **Security review fixes, 2026-10-13: NOT deployed, waiting for the user's
  OK.** See `SECURITY_REVIEW.md` "Deploy". Rules first
  (`firebase deploy --only firestore:rules`), then
  `functions:notifications` (creates `cleanUpDeletedGroup`, updates the goal
  push and the user-delete cleanup), `functions:weekly-history` (finalizer
  clock check) and `functions:billing` (dependency update only), each with
  `FUNCTIONS_DISCOVERY_TIMEOUT=60`. They change what 1.0.12 users hit; they
  are meant to be compatible (see the review). Push to GitHub only after both
  are live: the review describes holes that are open until then.

## 0. Before anything

- [ ] The user has said the freeze is over.
- [ ] `firebase use` prints `pamoja-android-1ac6d`.
- [ ] Working tree clean on the branch you deploy from: `git status`.
- [ ] Record the live baseline to compare against later (screenshot or note):
      Crashlytics crash-free users for 1.0.12 (last 7 days), Play Console
      version distribution, Firestore **Rules** monitor (Allow / Deny / Error
      counts, Firestore > Usage).
- [ ] Confirm the live rules are `dev`'s. Firebase console > Firestore > Rules
      > history: the current release should match `git show dev:firestore.rules`.
      If it does not, stop: something was deployed that git does not know about,
      and the diff above is wrong.
- [ ] Tag what production was built from, so it can always be rebuilt
      (REBUILD_PLAN §2.2): `git tag v1.0.12-prod 4be3a80`, once the user
      confirms `4be3a80` is what built 1.0.12 (§9.2 was never settled).

## 1. Release A (recommended first): 1.0.13 from `release/1.0`

Why first: the minimum-version gate only works on installs that have it. Every
week 1.0.13 is not out is another week before old clients can be retired and
Phase 8 can start. It needs nothing from the backend.

- [ ] `git switch release/1.0`
- [ ] **Match 1.0.12's flags.** 1.0.12 shipped with all four flags on (user,
      2026-09-26), and none is set by default. Only `subscriptionSalesEnabled`
      is in `local.properties` today. Add `circlePreviewEnabled=true` to
      `local.properties`, then build:

      ./gradlew bundleRelease -PtogetherTrailEnabled=true -PdayOnePlanningEnabled=true

      Without these, 1.0.13 silently switches the Trail, planning and Circle
      Preview off for every user who updates.
- [ ] Version is 16 / "1.0.13" by default; confirm in the bundle
      (`app/build/outputs/bundle/release/`) and that 16 is unused in Play Console.
- [ ] Internal track, install on a real phone from Play, smoke test: sign in,
      steps sync, group standings, Premium state shows.
- [ ] Closed track, then production staged 5% > 20% > 100%, watching
      Crashlytics against the baseline between steps.
- [ ] Do **not** set Remote Config `min_supported_version_code` yet. Unset
      means 0, nothing gated. Raising it is Phase 8, and only once its
      replacement is at 100%.

## 2. Firestore rules (from `rebuild`)

- [ ] `git switch rebuild`
- [ ] Emulator suite green on this exact commit (2026-09-29 on `5da2755`: 153,
      1 skipped). Rerun only if the rules or the app changed since.
- [ ] `firebase deploy --only firestore:rules`
- [ ] Watch for 30 minutes: Rules monitor Deny/Error counts should stay at the
      baseline. On a phone running 1.0.12: open the app, let steps sync, and
      check your membership document's `weeklySteps` / `todaySteps` moved
      (console > Firestore > `memberships/{uid}_{groupId}`).
- **Rollback** (only safe before any `rebuild` build is out): console > Rules >
  history > roll back to the previous release, or deploy from a `dev` worktree
  (`git worktree add ../pamoja-dev dev`, then from there
  `firebase deploy --only firestore:rules`).

## 3. Functions, one codebase at a time

- [ ] Tests: `npm --prefix functions-notifications test` and
      `npm --prefix functions-weekly test` (2026-09-28: 8 and 46 passed).
- [ ] `firebase deploy --only functions:notifications`
- [ ] `firebase functions:list` shows `notifySignalCreated` and
      `eraseDepartedMemberSignals` in `asia-south1`, and no function was
      deleted (the CLI asks before deleting; answer **No** to any deletion).
- [ ] `firebase deploy --only functions:weekly-history`
- [ ] After the next week rollover of any group: `firebase functions:log
      --only finalizeCompletedGroupWeek` shows no errors, and that group
      document now has `streakWeeks` and `streakThroughWeek`.
- **Rollback:** redeploy that codebase from `dev`
  (`git worktree add ../pamoja-dev dev`, then from there
  `firebase deploy --only functions:<codebase>`). Streak fields already written
  are harmless to every client.

## 4. TTL policy on signals

- [ ] `gcloud firestore fields ttls update expireAt --collection-group=signals --enable-ttl --project=pamoja-android-1ac6d`
- [ ] `gcloud firestore fields ttls list --project=pamoja-android-1ac6d`
      shows it; state goes CREATING to ACTIVE (can take a while). TTL deletes
      within about a day of `expireAt`, not on the minute; that is expected.

## 5. Play subscription trial

- [ ] Play Console > the Premium subscription > base plan > add a 7-day
      free-trial offer (Phase 6 decision). Entitlement id `premium` and the
      products stay as they are (REBUILD_PLAN §2.6).
- [ ] RevenueCat picks up the offer on its own; the rebuilt paywall shows a
      trial timeline only when the plan has one. Check with a license tester
      on the internal track in step 7.

## 6. Web join page

Published by hand in the **website** repo; it is not on the auto-redeploy path
(`.github/workflows/redeploy-website.yml` watches only the three legal pages).

- [ ] Port `web/join-landing.html` from `rebuild` (Lilac since 2026-10-11)
      into the website repo's join route, and replace `/pamoja-invite-og.png`
      with `web/og/pamoja-invite-og.png`, then deploy (Vercel). The live page
      is the site's own copy and was still terracotta on 2026-10-11, with no
      `intent://` button. Details: `web/DEPLOY.md`, "The invite page and its
      link preview".
- [ ] `curl -s https://www.arkayenlabs.com/pamoja/join/test123 | grep -c "intent://"`
      prints 1 or more.
- [ ] On a phone with the app: the link opens the app. Without it: Play opens
      with the referrer (Install Referrer end to end needs a real Play install
      of the `rebuild` build, step 7).

## 7. Release B: the `rebuild` app

Only after steps 2 and 3 are live and watched.

- [ ] Version: 17 or higher, above whatever step 1 used. Name is the user's call
      (suggestion "1.1.0").
- [ ] Flags on `rebuild`: only `subscriptionSalesEnabled` (local.properties) and
      `togetherTrailEnabled` (-P) still do anything; `circlePreviewEnabled` and
      `dayOnePlanningEnabled` are provided but read nowhere. Keep the Trail sync
      on (`-PtogetherTrailEnabled=true`) so mixed groups' Trails still advance
      for members on 1.0.x, until Phase 8.

      ./gradlew bundleRelease -PpamojaVersionCode=17 -PpamojaVersionName=1.1.0 -PtogetherTrailEnabled=true

- [ ] Launcher icon and splash are Lilac on `rebuild` (done 2026-09-29).
      With the release, upload `design/store-listing/pamoja-icon-512.png` as
      the Play Store icon (regenerate: `python tools/launcher_icon.py`), and
      replace the phone screenshots with the six Lilac ones in
      `design/store-listing/` (1080x1920, 24-bit PNG, regenerated 2026-10-11
      with Remind, High five and the goal moment; the 2026-09-29 set, which
      still said Nudge and Cheer, is in `2026-09-29/`, the terracotta set in
      `terracotta/`). Captions go through `adb shell`, which re-splits them:
      quote twice, `--es caption "'Your people. One weekly goal.'"`. Replace the feature graphic too if it shows the
      old look.
- [ ] Internal track, real phones, both themes:
  - [ ] Sign in with Google (first tap works), phone, email.
  - [ ] Two accounts in one group: cheer and nudge arrive as pushes; the
        signal document gets `expireAt`; a second cheer the same day is refused.
  - [ ] Day dots show after a sync; a 1.0.12 member in the same group still
        syncs (mixed-version group).
  - [ ] Invite link opens the preview; join works; a fresh install from the
        link joins the group (Install Referrer).
  - [ ] Paywall: license tester sees prices and the trial; purchase; restore.
  - [ ] Widgets place and refresh; Nudge from the widget sends.
  - [ ] **R8 was narrowed on `rebuild`** (release-only risk): every flow
        above on the release build, plus Health Connect permission and sync,
        the weekly card share, the data export, a push arriving, and the
        in-app update prompt.
  - [ ] QR scanner opens and reads an invite code, upright and rotated
        (its portrait lock was removed).
- [ ] Closed track, then production staged 5% > 20% > 100%, checking between
      steps: Crashlytics crash-free vs baseline, Rules monitor Deny/Error,
      `notifySignalCreated` errors in the functions log.

## Rollback triggers (decide now, not during)

- Crash-free users more than 0.5 points below the 1.0.12 baseline: halt the
  staged rollout (Play Console > Release > Halt). Users who already have it keep
  it, so the fix ships as a higher versionCode; the backend stays as it is.
- Rules Deny or Error count clearly above baseline after step 2: roll the rules
  back (safe only before Release B is out).
- Any function erroring on most invocations: redeploy that codebase from `dev`.
- A 1.0.12 user's steps stop updating: that is the joint client/rules invariant
  breaking. Roll back rules first, then investigate.

## Afterwards

- [ ] Update REBUILD_PLAN.md §0 and CLAUDE.md (deploy state, live version).
- [ ] Push branches only when the user asks.
- [ ] Phase 8 starts only when Play's version distribution shows old versions
      gone and `min_supported_version_code` has been raised past them.

## Not part of this deploy

- ~~**Pre-sign-in invite preview callable**~~: built after all
  (`getInvitePreview`, deployed 2026-10-11, in 1.1.6).
- **Pamoja-owned invite domain**: links stay on `www.arkayenlabs.com/pamoja/join`
  until joinpamoja.com is bought (decided 2026-10-13; three edits, REBUILD_PLAN Phase 3).
- **Terms, Privacy, Play Data Safety** for dropping age/height/weight: Phase 8.
  (The privacy drafts cover high fives and reminders since 2026-10-11.)
- `CODEX-CHECKLIST.md` has 92 unchecked items, many visibly stale (it lists
  RevenueCat setup as open while sales are live). Not a gate for this deploy.
