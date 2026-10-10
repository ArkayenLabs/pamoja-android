# Notifications: rewrite and delivery plan

Written 2026-10-05 after the user tested 1.1.1 (18): "nudge" is a word many
people won't understand, and the notifications read as stupid ("You are on
the board"). Decisions marked **(user)** need a yes
before building; everything else is a recommendation. Verify against the code
before acting, like every doc here.

**NEXT SESSION, START HERE (updated 2026-10-10).** Committed on `rebuild`
after `37b66d4`: notifications in the app's language on Android 12-, Hindi
and Spanish widget previews, and the "go quiet" backoff, both halves (app:
`FcmRegistrationManager` delivery 3 + `reportOpened()` from
`MainActivity.onResume`; server: `applyBackoff` / `QUIET_AFTER = 8` in
reminders.ts, `OPENS_DELIVERY_VERSION = 3`, `opened` resets
`unopenedReminders` in the register callable, the scheduler increments it).
Verified: functions tests 26, unit 186, lint 0 errors (117 warnings); not run
on a device. 1.1.4 (21) built: `design/release-builds/pamoja-1.1.4-21-internal.aab`
(supersedes 1.1.3). `functions:notifications` deployed 2026-10-10 ~10:24 UTC
(user OK). Next: the user uploads and tests 1.1.4. Production waits for Shipaton (judging to
~2026-10-13, winners 2026-10-21), then `DEPLOY_CHECKLIST.md`.

## 0. Built 2026-10-05 (committed `857bc7f`, nothing deployed)

§1 and §2 below describe the code **before** this work. What changed:

- **Word:** "Remind" / "Reminded" (the user left the pick to the plan's
  recommendation). Only English changed; es/hi already said *Avisar* /
  याद दिलाएँ. The wire value `"nudge"` is unchanged everywhere.
- **Copy:** every line in §3 is in `values/strings_notifications.xml` (+ es,
  hi, written fresh, **not yet read by a native speaker**). Channel names and
  descriptions now reuse the Settings rows' strings. `NotificationStringsTest`
  (app) and `notification-copy.test.ts` (functions) hold en/es/hi and the
  server's English fallback together.
- **On-device engine** now sends only the first-sync confirmation
  (`SmartNotificationEngine` + `NotificationCopy`). Deleted: "X passed you",
  "you took first", "X steps behind Y", both roasts, and the engine's
  goal-close / "your steps count" / recap / solo branches (moved to the
  server), with the rank and ignored-count preferences they used.
- **Pushes** (`functions-notifications`): new copy, `title_loc_key` /
  `body_loc_key` + args, channel, tag `p1:{groupId}:{eventId}`, visibility
  private, sent as **notification messages only to installations that
  registered `delivery: 2`** (app 1.1.2+, `FcmRegistrationManager`). Every
  message keeps the old data fields plus `titleLocKey`/`titleLocArg1..2`
  etc., so 1.0.12 and 1.1.x keep working (data-only, now with the new
  English). In the recipient's quiet hours a push goes data-only and the app
  decides as before (goal reached still shows). Pushes FCM draws in the
  background are added to Updates on tap (`MainActivity`) or from the tray on
  resume (`recordTrayNotifications`); one swiped away first is missed.
- **Scheduled** `sendScheduledReminders` (hourly at :05 UTC, `reminders.ts`):
  per installation, at local 10:00 on the group week's first day the shield
  line or the recap, at local 18:00 the streak / last-day / two-days /
  "every walk counts" (once in 6 days, not if walked today) / empty-group
  (days 1, 3, 7 only) line. One per installation per local day, never in quiet
  hours, delivery-2 installations only, 3 h TTL. **No rules change was
  needed:** time zone and quiet hours ride on the server-only
  `pushRegistrations` doc via the registration callable (merge-set, so the
  server's `reminderSent` survives), not on `users` as §2 proposed.
- **Correction to §2.1, checked in firebase-messaging 25.1.1:** a
  notification message is drawn by the FCM SDK *inside the app's process*
  (`DisplayNotification`, loc keys via `getIdentifier`). If an OEM stops the
  app from starting, it is lost exactly like a data message. The gains are
  real but smaller: loc keys, no app code (Hilt, DataStore) on the display
  path, always counted as user-visible. **The fix for ColorOS/MIUI is the
  §2.4 background-permission card**, still not built.
- **§2.4 card built 2026-10-08 (uncommitted):** `BackgroundRestrictions`
  maps `Build.MANUFACTURER` to ColorOS (oppo, realme, oneplus), MIUI
  (xiaomi, redmi, poco), vivo/iQOO, Samsung, Huawei/Honor; Settings shows one
  `KitNotice` above Health Connect with that maker's switch names, "Open App
  info" and "Hide". Either button hides it for good (`backgroundHelpDone`,
  survives sign-out). Samsung is not asked once Android reports the app
  exempt from battery optimisation. It opens the app's own App info page, not
  the makers' hidden auto-start screens (their class names change and newer
  ones refuse outside apps). The switch names are best knowledge, **not
  checked on those phones**; es/hi need a native read (hi keeps the English
  switch names).
- Widget picker previews re-exported with "Remind" (group widget, light and
  dark; the other two had no word to change).
- **Language on Android 12 and below fixed 2026-10-08 (uncommitted):** a
  context outside an activity there carries the phone's language, not the
  one chosen in the app, so notifications, channel names and FCM's own tray
  drawing used the phone's. Now each goes through
  `ContextCompat.getContextForLanguage` (it reads AppCompat's stored choice
  without an activity; the widgets already used it), including the FCM
  service's `attachBaseContext`, and channel names are rewritten on each app
  start. Not checked on a real Android 12 phone.
- Widget picker previews now also in Hindi and Spanish (lossless WebP in
  `drawable-hi*-nodpi` / `drawable-es*-nodpi`, 158 KB).
- **§4.4 honest backoff built and deployed 2026-10-10:** the scheduler
  counts scheduled reminders sent since the app was last opened
  (`unopenedReminders` on the push registration); at 8 it sends
  `push_quiet_*` once, then nothing until a delivery-3 build reports an open
  (at most every 6 hours, from `onResume`). Builds below delivery 3 are never
  silenced, since they cannot reset the count. Pushes from people never count.
- Not built: action buttons (impossible with notification messages), §4
  ideas 2, 3, 5-7.

**Deploy when the user says (not before):** functions `notifications`
codebase first (`firebase deploy --only functions:notifications`; adds
`sendScheduledReminders`, a Cloud Scheduler job), then an internal build.
Deploying it **changes what 1.0.12 users, including Shipaton judges, see**:
the new English words on join, goal, cheer and reminder pushes (still
data-only, drawn by their app). Nothing else changes for them: they never
register `delivery`, so no tray messages and no scheduled reminders.

---

## 1. How notifications work today (checked in the code)

There are **two delivery paths**. "Push or FCM?" is not the choice: FCM is
already the push path.

| Path | What it sends | How it is delivered | Reliable? |
|---|---|---|---|
| **A. On-device engine** (`util/SmartNotificationEngine.kt`, called from `StepSyncWorker`) | First sync ("You are on the board"), goal close, "X passed you", "you took first place", "X steps behind Y", Monday recap, "your steps count", low-step roast, solo group. Streak branch exists but is unreachable. | Only when the background step sync runs (WorkManager, about every 15 min), then a local notification. | **No.** ColorOS, MIUI, OneUI battery savers delay or kill background work, so these arrive late or never. If the sync doesn't run, nothing is sent. |
| **B. Server push** (`functions-notifications`, FCM) | Someone joined, goal reached, cheer, nudge. | High-priority **data-only** FCM messages (24 h TTL). The app's `PamojaFirebaseMessagingService` receives each one and draws it, after checking muted categories and quiet hours. | **Mostly.** On a Pixel, yes. On ColorOS or MIUI, if the app isn't allowed to auto-start or was swiped away, a data-only message may not wake the app, and it is silently dropped. |

Other facts found:
- **All engine copy is hard-coded English in Kotlin** (20 variants): Spanish
  and Hindi users get English. Server push copy is English too.
- CLAUDE.md said the first-sync notification "can never fire". **Wrong:**
  `StepSyncWorker.kt:130` sets `isFirstEverSync`, which is how the user saw
  "You are on the board". (Joins are server-side; streak is still unwired.)
- The engine already has good bones: quiet hours, one notification per 20 h,
  a 10:00 to 19:30 window for reminders, backoff after 4 and 8 ignored, muted
  channels respected. Keep those; the problem is the words and the delivery.

## 2. Delivery: yes, lean on FCM, but change how it is sent

Recommendation, in order of value:

1. **Send server pushes as notification messages, not data-only.** Add an
   `android.notification` block (channel id, `tag` for de-duplication, click
   opens the group) alongside the data. Android itself then shows it even
   when the app is killed or not allowed to auto-start, which is exactly the
   OEM case that fails today. Use **`title_loc_key` / `body_loc_key` with
   args**: the phone fills the text from the app's own string resources, so
   pushes come out in the user's language with no server-side translation.
   Cost: the app no longer filters these in code when it's in the
   background. Muted categories still work (Android honours a muted channel);
   quiet hours move to the server (step 2). **And notification messages
   cannot carry action buttons** (no "Cheer back" in the tray): buttons need
   the app to draw the notification, which is the data-only path that fails
   on OEM phones. Recommended: reliability first (notification messages);
   tapping opens the group with Cheer back right there.
2. **Move time-based reminders to the server.** A scheduled Cloud Function
   (for example hourly) decides who gets the evening reminder, Monday recap
   and "goal is close" from the membership documents it already reads, and
   sends FCM. Needs, per user, a time zone and quiet hours stored where the
   function can read them (a small rules change: owner-only fields on
   `users`). The server only knows figures as of each phone's last sync, so
   copy must never claim "you haven't walked"; say what the group did.
3. **Keep the on-device engine only for what needs the phone:** the
   first-sync confirmation. Delete the leaderboard branches (see §4).
4. **Help OEM phones once.** If a push was sent but the app shows it wasn't
   opened for days, or on first setup on ColorOS/MIUI/OneUI/Vivo, one card
   in Settings: "Allow Pamoja to run in the background" with the exact
   setting to open. Not a nag; shown once, dismissible.

Second-order effects to accept: function invocations grow with users (small
at this size); notification-message pushes show even during the app's own
quiet hours unless the server holds them, so step 2's server-side quiet
hours must land with step 1; old app versions receive the same payload and
must still parse it (keep the data fields; add, don't remove).

## 3. The words

### Replace "nudge" **(user: pick one)**

| Option | Button / done state | Push to the person | Notes |
|---|---|---|---|
| **Remind** (recommended) | Remind / Reminded | "Priya: walk today?" | Everyone knows it. Hindi already says याद दिलाएँ, Spanish *avisar*. |
| Ask to walk | Ask to walk / Asked | "Priya asked you to walk" | Warmer, longer; tight on small buttons. |
| Give a shout | Shout / Shouted | "Priya gave you a shout" | Friendly, but less clear what it does. |

Only user-facing text changes. The wire value `"nudge"` in Firestore, rules,
functions and analytics stays, or every installed app breaks.

Strings to change (en, es, hi): `home_nudge`, `home_nudged`,
`notif_group_sub`, `home_tour_today_body`, `start_cheers_body`,
`social_primer_body`, `widget_group_description`,
`settings_widgets_group_subtitle`, and the push copy.

### Copy principles (mechanics borrowed, not their words)

- **From a person beats from the app.** "Priya cheered your walk" lands;
  "Time to walk!" doesn't. (Strava's kudos works for this reason.)
- **One reason, one number, sent when you can act.** Duolingo times its
  reminder to when you usually practise, and stops when it isn't working
  ("these reminders don't seem to help, we'll stop"). Do both.
- **Gentle on hard days.** Finch never guilts; a quiet day gets kindness or
  silence, never a roast. Our own engine rule ("would this land badly on
  someone who's ill?") stays, and two of today's lines fail it.
- **Short and specific, with local warmth.** Zomato and Swiggy win on short,
  situational lines tied to the moment (lunch, rain, a match). For us: the
  evening walk, after-dinner walks, the weekend. No emoji (house rule), no
  puns that need explaining, no "I".
- **Collective, not competitive.** The product is one shared weekly goal.
  Rank pings ("X passed you", "you're first") turn friends into opponents.

### Every notification: current and proposed (en)

| When | Today | Proposed |
|---|---|---|
| First sync, with steps | "You are on the board." / "{n} steps landed in {group}. Good start." | **Your steps are in** / "{n} steps today, counting for {group} now." |
| First sync, no steps yet | "You are live." / "{group} is connected. Your next steps count." | **You're all set** / "Your walks will count for {group} from now on." |
| Second member joins | "Now it's a group." / "{who} joined you. Two people, one weekly goal." | **{who} joined {group}** / "There are two of you now. Walks are better with company." |
| Later member joins | "{who} actually joined." / "{group} is now {n} strong. Try to look normal." | **{who} joined {group}** / "{n} of you now. Say hi on your next walk." |
| Goal reached | "{group} did it." / "{total} steps together. Goal met. Show-offs." | **{group} made it** / "{total} steps this week, together. Take a bow." |
| Goal close, last day | "Final day. {left} left." / "{each} each. Suspiciously doable." | **Last day: {left} steps to go** / "About {each} each. An evening walk would do it." |
| Goal close, two days | "{left} steps left." / "{each} each. Two days. Very doable." | **{left} steps to go** / "Two days left, about {each} each." |
| Cheer received | "{who} cheered your walk" / "{group}" | **{who} cheered your walk** / "{group}. Tap to cheer back." (opens the group with Cheer back) |
| Nudge received | "{who} nudged you" / "{group} is walking today. Join in?" | **{who}: walk today?** / "{group} is out walking. Join in when you can." |
| Monday recap | "New week. Same people." / "{target} steps waiting in {group}." | **New week for {group}** / "Last week: {lastTotal} steps. This week's goal: {target}." |
| "Your steps count" | "Your steps count today." / "{group} is {left} short. Even a small walk changes it." | **Every walk counts today** / "{group} is {left} steps from the goal." |
| Low-step day | "{name}, be honest." / "Did the phone stay home, or did you?" and "Your walking shoes asked. I said you were busy..." | **Delete.** It fails our own hard-day test and speaks as "I". If anything: **Fancy a short walk?** / "{group} has walked today. Ten minutes counts." (only when the group has walked) |
| Alone in a group | "Strong leader. Tiny team." / "Still just you in {group}. Send the invite." | **{group} needs its people** / "Send your invite. A shared goal is easier to keep." |
| "X passed you", "you took first", "X steps behind Y" | (three competitive variants) | **Delete** (collective product; see principles). |
| Streak (unwired) | "Your streak is being dramatic." | Rebuild on the **server group streak**: **{n} weeks in a row** / "{left} steps keeps it going this week." Shield used: **A shield kept your streak** / "{group} missed the goal, but the streak lives." |
| Permission primer | "Hear it when they cheer" / "Turn on notifications to get cheers and nudges..." | **Hear it when they cheer** / "Turn on notifications so you know when your group cheers you on." |

Hindi and Spanish: write fresh, don't translate word for word, and have a
native speaker read them.

## 4. New ideas worth building (ranked)

1. **Cheer back and Remind in one tap from the notification.** Action
   buttons need data-only messages (less reliable on OEM phones, see §2), so
   start with a tap that opens the group with the button ready; revisit
   buttons if delivery data says data-only arrives fine.
2. **Your usual walk time.** Learn when the user typically reaches 1,000
   steps; send the one daily reminder an hour before that, not at a fixed
   time.
3. **Evening group digest for whoever has walked:** "Priya and Ravi walked
   today. Meera hasn't yet." with **Remind Meera**. At most once a day.
4. **Honest backoff** (built 2026-10-10, see §0). After 8 ignored: one last message, "We'll go quiet
   for now. Your group's still here when you want it.", then silence.
5. **Celebrate a person in the recap** (never rank): "Meera walked every day
   last week."
6. **Milestones:** first goal met, 4 and 10 week streaks, first shield used.
7. Later: weather and heat awareness ("Hot afternoon. An evening walk counts
   the same.") needs a weather source; festivals and cricket days, maybe.

## 5. Suggested build order for the next session

1. **(user)** Pick the word for "nudge"; read the proposed copy in §3.
2. Strings: rename across en/es/hi; move every engine and push line into
   string resources (`title_loc_key` for pushes).
3. Server pushes as notification messages with channel, tag, loc keys and
   a Cheer back action; keep the data fields for old app versions.
4. Delete the competitive and roast branches; wire the server streak and
   shield notifications.
5. Server-scheduled reminders and recap (time zone and quiet hours on the
   user, rules change, emulator tests in pairs).
6. OEM background-permission card.
7. Tests: engine unit tests updated, functions tests for payloads, one
   instrumented check that a notification-message push opens the right group.
8. Deploy order still: rules, functions, then the app (internal testing).
