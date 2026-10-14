# Security review, 2026-10-13

> **Deployed 2026-10-14** (rules and indexes 13:56 UTC, functions 14:01 to
> 14:06 UTC, from `25fcf57`), so the push no longer discloses open holes. The
> app half ships in 1.1.6.

**Agreed with the user on 2026-10-13: before the next build (1.1.6), review
the whole app for security, every part, however small.** Done on `rebuild`
from `30405d3`, in one session. From now on every new feature gets the same
checks as it is built.

Every finding below was proven by a test that fails on the old code and passes
on the new (rules tests in pairs: the attack refused, the real write still
allowed), except where the Test column says otherwise. Nothing was deployed;
production was not read.

## How it was checked

- Read: all of `firestore.rules` and `storage.rules`, the three functions
  codebases, the app's entry points (manifest, `MainActivity`, navigation,
  invite parsing, push parsing, widgets), account deletion and sign-out, the
  data layer's writes on `rebuild` and on `dev` (what 1.0.12 was built from,
  so no rules change breaks the live app), logging and analytics, the web
  pages, the live site's headers, and the git history.
- New emulator tests: `MembershipIntegrityRulesEmulatorTest` (16),
  `GroupIntegrityRulesEmulatorTest` (11), one in `StepAggregationEmulatorTest`.
  Run against `HEAD`'s rules first: exactly the 11 new attack tests failed and
  every August regression test and every pair passed. Then all 37 passed on
  the fixed rules, and the rest of the rules package (86) passed too.
- New Node tests: `isCurrentWeekStart` (functions-weekly), `mayPushGoal`
  (functions-notifications), and `functions-notifications/group-cleanup.test.cjs`
  against the Firestore and Storage emulators under a `demo-` project.
- New JVM test: `InviteLinkTest`.
- Not finished: a last full instrumented run (lint, then all 185) stopped when
  the emulator shut down at 113; two failures there (a Billing socket timeout
  and one `StepAggregationEmulatorTest` case with no reason recorded) had
  passed earlier on the same rules. Lint output was not read. The full suite
  runs once before 1.1.6 is built.

## The August 2026 patches

All seven are in `rebuild`, and so is C3 (the Advertising ID permission is
removed in the manifest):

| Patch | In `rebuild` | Pinned by a test before today |
|---|---|---|
| 01 rules access control (C1, C2, H1, H2, M4) | yes | C2 only (`GroupGoalRulesEmulatorTest`). C1, H1, H2, H3 and M4 now are (see #19) |
| 02 storage UUID group path (M1) | yes | no; narrows, does not close (see O7) |
| 03 photoUrl host (M2) | yes, both Storage URL forms | now (#19) |
| 04 stale rules footer (L3) | yes | n/a |
| 05 release logging (L1) | yes | n/a |
| 06 backup exclusions (M3) | yes | n/a |
| 07 invite link crash (H4, L11) | yes | now (`InviteLinkTest`, tightened, #13) |

## Findings

Severity is about this product: health-adjacent data, a public repository,
rules as the only control in front of Firestore.

| # | Severity | Where | Finding | Fix | Test |
|---|---|---|---|---|---|
| 1 | **High** | `firestore.rules` groups Case E; `functions-weekly` `finalizeCompletedGroupWeek` | Any member could write any ISO date as the group's `weekStart`, and the finalizer closes whatever week the marker moves away from, checking only that the gap is whole weeks. A marker a week ahead finalized this week early; written again, every later week as missed: streak gone, Premium shields spent, permanently (finalized weeks are immutable). A marker decades back created one history week per write (~160 reads each). | Rules: `isRecentWeekStart`, the marker must be this week's or last week's. Finalizer: `isCurrentWeekStart`, by the server clock. | `GroupIntegrityRulesEmulatorTest.aMemberCannotMoveTheWeekMarkerOutOfTheCurrentWeek` / `thisWeeksAndLastWeeksMarkersStillPublish`; `weekly-summary.test` "only a week that has begun" |
| 2 | Medium | rules memberships `delete` | Anyone holding an invite could join, delete their own membership (no decrement needed), and join again, walking `memberCount` to the cap with nobody in the group: closed to every real joiner, and a "joined" push to every member per lap. | A self-delete must bring the count down in the same commit, unless the group is gone or going, or the count is already 0 (the only shapes leave, account deletion and handover take, on `rebuild` and `dev`). | `MembershipIntegrity…leavingWithoutBringingTheCountDownIsRefused` / `aRealLeaveStillWorks`, `theAdminCanStillRemoveAMember`, four `accountDeletionStillWorksFor…` |
| 3 | Medium | rules groups Case A | `getAfter()` also finds an existing member's old membership, so any member could add one to the count per write until the group read as full. | `!exists()` before the commit: the joiner's membership must be new. | `aMemberCannotRaiseTheCountWithoutAnyoneJoining` (its pair inside) |
| 4 | Medium | rules groups Cases A and D | A joiner or leaver could set `inviteLinkActive` to false. The app refuses to join a group whose invite is off and has no screen to reopen it, so one person closed the group to everyone after them. | A join may close it only when it fills the group; a leave may only reopen it. | `aJoinerCannotSwitchTheInviteOff` / `theJoinThatFillsTheGroupStillClosesTheInvite`; `aLeaverCannotSwitchTheInviteOff` / `leavingAFullGroupStillReopensTheInvite` |
| 5 | Medium | rules memberships `get` | `get` was a bare `isSignedIn()`. The group document names its `adminId` and anyone with the invite may read it, so anyone with a forwarded link read the starter's membership: name, photo URL, live weekly and today's steps, walked days. | Own membership, or one in a group you are in, authorised on the document ID (never `resource.data`, so `joinGroup()`'s read of a missing membership still works). | `anInviteHolderCannotReadTheStartersMembership` / `yourOwnMembershipAndYourGroupmatesStillRead` |
| 6 | Medium | account deletion (app) + functions | A group's last member deleting their account deletes the group document from the app. That left the group photo in Storage, the group's subcollections, its access, preview and billing projections, and any orphaned memberships; `billingViews/{uid}` stayed after every account deletion. | `cleanUpDeletedGroup`, a delete trigger running the same cleanup as `deleteGroup` (now shared, idempotent); the user-delete trigger also removes `billingViews/{uid}`. `billingAccounts` stays (a Play subscription keeps billing after deletion; its webhooks need it). | `group-cleanup.test.cjs` (the pair: another group's data, photo and billing untouched) |
| 7 | Medium | `FirebaseUserRepositoryImpl.deleteAllUserData` | The successor lookup was `runCatching { … }.getOrNull()`: a failed read looked like "nobody to inherit", and the next branch deleted the group with its members still in it. | Read from the server and let a failure stop the deletion before anything is removed; cancellation rethrown. | The account deletion tests run the new path. A failing read cannot be staged on the emulator, so the failure branch is proven by reading only. |
| 8 | Low | rules groups `create` | A deleted group's ID (the permanent invite key in every link and QR it shared) could be re-created by anyone who knew it, taking over its old invites. | Tombstone `deletedGroups/{groupId}`, written by `deleteGroup` and `cleanUpDeletedGroup`, refused by create. | `aDeletedGroupsIdCannotBeTakenOver` (pair inside) |
| 9 | Low | `notifyGroupGoalReached` | Fires on any crossing of the member-written step cache, so a member could write it under the goal and back as often as they liked: every phone in the group buzzed again each time. | One push per group per week, claimed in a transaction (`goalPushWeek`, `goalPushEventId`, server-owned); a redelivered event keeps its claim. | `notification-domain.test` "once a week" |
| 10 | Low | rules memberships `create` | Open key set (a joiner could park up to 1 MiB on a document every member downloads) and a joiner could take the admin role and target permission (displayed, never authorised on). | `keys().hasOnly(MembershipDto)`; admin role only with the group's admin. | `aJoinerCannotClaimTheAdminRoleOrCarryExtraFields` (pair inside) |
| 11 | Low | rules groups `create` and Case B | Open key set on create, stored `groupId` unchecked, `weekStartDay` unvalidated on create and settings (the server's reminders and finalizer read it). | `keys().hasOnly(GroupDto)`, `groupId` equals the document ID, `isWeekStartDay`, `inviteLinkActive is bool`. | `aGroupCannotBeCreatedWithExtraFieldsOrAnotherId`, `theStartDayMustBeADay` |
| 12 | Low | rules `steps` | The ID was pinned to `{uid}_{date}` but the date was free: unbounded documents nothing reads. | `isIsoDate(date)`. | `StepAggregationEmulatorTest.aStepEntryMustBeForARealDay` |
| 13 | Low | `InviteLink.parseCode`, `CreateOrJoinViewModel` | August's fix refused only `/`; spaces, `%`, `&` and the rest still reached the `join/{code}` route and DataStore, where a pending code is replayed after sign-in. | `isCodeShaped`: letters, digits, `-`, `_`, 1 to 64; a stored pending code is checked too. | `InviteLinkTest` |
| 14 | Low | `StepSyncWorker`, `FirebaseAnalyticsManager` | Two release `Log.w` lines wrote the group ID (the join key); "group created" sent the user-written group name to Analytics. | Both removed. | By reading; no test asserts log or event content. |
| 15 | Low | `SettingsViewModel` | Tray notifications ("High five from Ravi") stayed after sign-out and account deletion. | `SmartNotificationHelper.clearShown()` on both. | By reading. |
| 16 | Low | `PhotoCropScreen` | August L13: every crop left a copy of a face in cache. | Older crops deleted when a new one is written. | By reading. |
| 17 | Low | `group-admin.ts removeBillingReferences` | Copied `groupLeaseValidUntil` from the payer's view and threw when it was absent, aborting the whole group deletion. Found by #6's test. | `?? null`. | `group-cleanup.test.cjs` |
| 18 | Info | functions dependencies | `npm audit --omit=dev`: 9 to 10 per folder (1 critical `proxy-addr`, 2 high `@grpc/grpc-js` and `brace-expansion`, moderate `uuid` chain). None reachable as used (no proxy-trust IP decisions, gRPC client only, no attacker brace patterns, no `uuid` buffers). | `firebase-admin` 14.3.0 to 14.5.0 (released 2026-09-23) and `npm audit fix`: 0 in all three. | All three `npm test` pass (functions 9 + 1 skipped, weekly 47 + 25 emulator-only skipped, notifications 36). |
| 19 | Info | tests | The August rules fixes C1, H1, H2, H3, M2, M4 were in the rules but no test pinned them. | Pinned. | `aStrangerCannotWriteThemselvesIntoAGroup`, `aRealJoinStillWorksAndTheCapStillHolds`, `aMemberCannotDriveTheCountDownWithoutLeaving`, `aMemberCannotRepointTheirMembershipAtAnotherGroup`, `aJoinerCannotBackdateJoinedAt`, `aPhotoMustComeFromThisProjectsStorage`, `theGroupStepCacheIsBounded` |

`storage.rules` also had a wrong comment, now corrected: it said avatars are
not public, but the stored `photoUrl` is a `getDownloadUrl()` link whose token
opens the photo for anyone who has it. Privacy rests on who can read the URL.

### Open: needs a decision or access I don't have

| # | Severity | Where | What | Suggested |
|---|---|---|---|---|
| O1 | Medium | product + rules | Removing a member is not durable: they can rejoin at once with the same link (the group ID), and `inviteLinkActive` is checked only by the app. With #2 fixed, each real rejoin still pushes "joined" to everyone. | **Decided and built 2026-10-14 (user: yes, with undo).** `groups/{id}/removed/{uid}` written by the admin's removal in the same transaction; the membership create rule refuses anyone listed; only the admin lists it and lets people back in ("Removed" in Group details); the invite preview says so before Join; erased with the removed person's account (`cleanupPushRegistrationsOnUserDelete`, collection-group index). Leaving keeps nobody out. Only removals made from 1.1.6 on are kept out, and a removed person with a second account can still join (and be removed again). Tests: `RemovedMembersRulesEmulatorTest` (7, in pairs), `GroupDetailsScreenTest` (2), `group-cleanup.test.cjs` (account pair), `GroupGoalUseCasesTest`. |
| O2 | Low | `web/assetlinks.json` and the live file | The middle fingerprint (`09:82:DF…`) is this machine's debug keystore. An app signed with it cannot install beside the Play app, but production links should not trust a debug key. | Remove it from the live file after judging (debug builds then stop verifying invite links). |
| O3 | Low | live join page | Vercel Analytics records page paths, and an invite path carries the group ID. Our own dashboard, so low. | A `beforeSend` that strips the ID, on the website repo (not on this machine). |
| O4 | Low | design | Steps are self-reported from Health Connect, so a member can claim up to the caps (300k a day): a fake goal week, streak and shields. | Accepted; inherent to client-reported steps. |
| O5 | Low | rules memberships `create` | August H3 residual: a joiner may still set `joinedAt` back to the group's `createdAt` and so be first in line to inherit admin. | Accepted, or bound it to `request.time` (a phone with a wrong clock could then not join). |
| O6 | Low | RevenueCat | The public SDK key lets anyone who knows a UID read that customer's subscription status, and group-mates see each other's UIDs. | Accepted (RevenueCat design); never set personal subscriber attributes. |
| O7 | Low | `storage.rules`, rules `users` | August M1 and L9 residuals: `group_avatars/{uuid}/` is writable by any signed-in user; the `users` document has an open key set (owner-only). | App Check enforcement and a budget alert. |
| O8 | Info | Firebase console | Not checkable from code: App Check enforcement on Firestore and Storage (callables enforce it in code), API key restrictions, Auth email enumeration protection, SMS region policy, a billing budget alert. | Please read these out, or check them. |
| O9 | Info | git | Local refs `refs/codex/snapshots/*` (not branches) contain a committed `functions-notifications/node_modules/`. `git push origin rebuild release/1.0` does not send them; `--mirror` would. History on the branches is clean (no keys, tokens or passwords). | Delete those refs if they are no longer needed. |
| O10 | Info | rules groups `get` | The group photo URL is on the group document, so anyone signed in with the invite sees it, like a chat group's icon on an invite. | Accepted. |
| O11 | Low | app copy | The in-app delete dialog does not say that deleting the account does not cancel a Google Play subscription (the web deletion page does). | Done 2026-10-14: `settings_delete_subscription`, en, es and hi, always shown. |

## Scope

### Backend
- [x] `firestore.rules`, every match block. Findings 1 to 5, 8, 10 to 12;
      August fixes pinned (19). Clean: users, signals, weeks, planning,
      access, previews, billing projections, push registrations, steps reads.
- [x] `storage.rules`: owner-only paths, 2 MB, JPEG, no listing. Comment
      corrected; O7.
- [x] Callables: all require auth (except `getInvitePreview`, deliberately)
      and enforce App Check; inputs validated (UUID v4, installation IDs, time
      zones); errors generic; `maxInstances` 5 to 10. Clean.
- [x] Triggers and schedules: 1 and 9 fixed; joined pushes bounded by 2 (O1
      remains); signals one per kind, pair and day by ID; reminders once a
      day per installation.
- [x] Billing: webhook authorization header (timing-safe) plus HMAC signature
      with a time window, event-ID replay guard, then RevenueCat's REST API as
      the source of truth; Premium granted only by the server for the caller's
      own UID, never as a fallback; sponsorship cannot be taken from an active
      sponsor; no client rule can write any billing collection. Clean; O6.
- [x] Data lifecycle: 6, 7, 17. Signals TTL and departed-member erasure,
      week redaction and push cleanup were already in place.
- [ ] Firebase project settings: O8.

### App
- [x] Authentication: re-authentication before deletion, checked first; OTP
      resend capped; account linking keeps the UID; sign-out unregisters FCM
      and clears preferences, widgets and now the tray (15). Enumeration
      protection is a console setting (O8).
- [x] Entry points: invite parser (13); group ID extras are validated by Home
      before use; pushes from extras go through the strict parser with a
      recipient check; install referrer accepts only a group ID; no nav-graph
      deep links.
- [x] Manifest: only `MainActivity` exported (and the permission-gated
      alias); receivers, service and providers not exported; FileProvider
      limited to `cache/shared/`; PendingIntents immutable; backup excludes
      shared prefs, DataStore and databases on both channels; no cleartext in
      release.
- [x] Local storage: 16; the widget store and faces are cleared on sign-out;
      the export goes through the system file picker.
- [x] Logging and analytics: 14; `Log.d/v/i` stripped in release.
- [x] User-written text: names are 40 (users) and 100 (memberships) at most;
      pushes use the first word, 18 characters, and the app drops longer
      fields; the export uses `org.json`; the web page shows no group data.
- [x] Lock screen: channels and FCM messages are private visibility.
- [x] Release build: DTOs and Workers kept; the merged release manifest has
      no debug activities; no emulator hosts in `main`; the debug App Check
      provider is debug-only.

### Repo, web and dependencies
- [x] Secrets: the branches' full history scanned (keys, tokens, passwords,
      keystores, service accounts): clean; `google-services.json` never
      committed. O9.
- [x] Dependencies: 18. Gradle dependencies are current releases; no
      automated Gradle advisory scan was run.
- [x] Web: the join page trusts only a UUID from its own path and fetches
      nothing; live headers include HSTS preload, CSP, nosniff and
      frame-ancestors. O2, O3.

## Deploy (only with the user's OK, after Shipaton judging)

Meant to be backward compatible with 1.0.12: every rule change was checked by
reading what `dev` writes (its DTOs are a subset of `rebuild`'s; its join,
leave, removal and account deletion commit the same shapes), and the rules
package passes with `rebuild`'s real repositories. 1.0.12 itself was not run
against the new rules.

1. `firebase deploy --only firestore:rules,firestore:indexes` (the index is
   the collection-group `removed.userId` override for O1's erasure; it builds
   in the background and nothing reads it until the functions deploy)
2. `$env:FUNCTIONS_DISCOVERY_TIMEOUT=60`, then
   `firebase deploy --only functions:notifications,functions:weekly-history,functions:billing`
   (notifications: `cleanUpDeletedGroup` is new, plus #6, #9, #17 and O1's
   erasure on account deletion; weekly:
   #1; billing: the dependency update only).
3. Then the app (1.1.6).
4. Only then push.
