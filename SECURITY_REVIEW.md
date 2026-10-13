# Security review plan (for the session after 2026-10-13)

**Agreed with the user on 2026-10-13: before the next build (1.1.6), review
the whole app for security, every part, however small.** Then keep doing it
side by side: anything added later gets the same checks as it is built.

Start here. Do not trust any line below without checking it; this file was
written from the code on `rebuild` at `13f8b22`, not from a review.

## What exists already

- `security-patches/SECURITY-REVIEW-2026-08-25.md` (git-ignored, local only):
  a review of the August app (terracotta, `dev`), with patches 01 to 07
  (Firestore access control, storage group-avatar path, photoUrl host,
  release logging, backup exclusions, invite-link crash). **First task: for
  each patch, check whether it is in `rebuild` today**, and record it here.
- Never reviewed: everything built since, which is most of the app. Phase 5
  signals (high fives, reminders), walked days, streaks and shields, the
  weekly finalizer changes, the notifications rewrite (scheduled reminders,
  go-quiet counter), `getInvitePreview` (no auth, App Check only), the
  invite-before-sign-in flow and auto-join, the photo on the name step, the
  data export (now with signals), the goal celebration, the widgets.

## How to work

- Read the code and rules, then prove each finding with a test: a rules test
  in the emulator suite (in pairs: the attack refused, the real query still
  allowed), a unit test, or a functions test. A finding without a test is a
  guess.
- The `claude-security` skill can run a structured scan; use it as one input,
  not as the review.
- Production: read-only checks only (rules via the Rules API, logs, function
  config). Never read user documents. Deploys only with the user's OK, and
  nothing that judges see until Shipaton judging is over.
- Fix as you go; keep a findings table below (severity, where, fix, test).

## Scope (tick each when checked, with what was found)

### Backend
- [ ] `firestore.rules`, every match block (18 today): users, groups,
      memberships, steps, signals, weeks, access/billing projections,
      pushRegistrations, adventures and planning (still live for 1.0.x).
      For each: who can get, list, create, update, delete; list rules only
      name filtered fields; get rules never touch `resource.data`; no list on
      users or groups; memberCount bounded by maxMemberCap; server-owned
      fields (streaks, shields, `shieldsStarted`) not client-writable.
- [ ] `storage.rules`: avatar and group photo paths, owner-only writes, size
      and content-type limits, no listing.
- [ ] Every callable in `functions`, `functions-weekly`,
      `functions-notifications`: auth required or deliberately not
      (`getInvitePreview`), App Check enforced, input validated, errors that
      leak nothing, cost and abuse limits (maxInstances, per-user limits).
- [ ] Every trigger and schedule: fan-out cost, retries that cannot double
      send or double count, what a malicious write can make the server do
      (signals push, goal reached, member joined, finalizer).
- [ ] Billing: RevenueCat webhook authentication, how Premium is granted,
      that no client write can grant or move Premium, sponsorship moves.
- [ ] Data lifecycle: account deletion removes everything (profile, steps,
      memberships, signals, push registrations, photos, billing references
      where allowed); signals TTL; export completeness.
- [ ] Firebase project settings that code cannot show: App Check enforcement
      on Firestore and Storage, API key restrictions in Google Cloud, Auth
      settings (email enumeration protection, phone abuse limits). Ask the
      user to read these out if no read-only API shows them.

### App
- [ ] Authentication: Google, phone (OTP abuse, resend), email (sign-up,
      sign-in, reset without revealing which emails exist), account linking,
      re-authentication before delete, sign-out clearing local data, widgets,
      notifications and caches.
- [ ] Every way into the app: the https App Link, the BROWSABLE
      `pamoja://join` scheme, the install referrer, notification taps,
      widget clicks; every value from them validated before use.
- [ ] `AndroidManifest.xml`: exported components and why, the FileProvider
      scope, permissions, `allowBackup` with `data_extraction_rules` and
      `backup_rules` (what is excluded), cleartext traffic.
- [ ] Local storage: DataStore keys, caches, the cropped photo, the export
      file, the weekly card image; nothing secret or stale left behind.
- [ ] Logging, Crashlytics and Analytics: no tokens, emails, phone numbers,
      step data or user IDs in logs or event parameters in release.
- [ ] User-written text (names, group names): length limits matched to the
      rules, what it does inside notifications (loc args), widgets, the
      weekly card, the invite preview, and the web page; odd characters
      (right-to-left overrides, very long words).
- [ ] What shows on a locked phone: notification text, widgets.
- [ ] Release build: R8 keep rules, debug-only activities absent from
      release, no test endpoints or emulator hosts in release.

### Repo, web and dependencies
- [ ] Secrets: scan the full git history before the public push (keystore,
      `keystore.properties`, `local.properties`, service accounts, RevenueCat
      or webhook secrets). `google-services.json` is public by design.
- [ ] Dependencies: `npm audit` in all three functions folders; Gradle
      dependency versions with known issues.
- [ ] Web: `join-landing.html` (only a validated group ID from the path,
      nothing fetched), `assetlinks.json`, the legal-page publishing
      pipeline (it sanitises, but check), headers on the live site.

## Findings

| # | Severity | Where | Finding | Fix | Test |
|---|---|---|---|---|---|
| | | | | | |
