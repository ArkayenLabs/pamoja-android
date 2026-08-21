# Play Data Safety form. Answer sheet

**Written 2026-08-20.** Every answer below was derived from the code, not from
memory, with the source named next to it so it can be re-checked rather than
trusted.

🔴 **The form, this file, `legal/PRIVACY_POLICY.md`, the deployed privacy page
and the app must all agree.** A mismatch between them is one of the most common
causes of an app being rejected, and of being **removed after** it is already
live. If any of the five changes, change all five in the same session.

---

## Before filling the form

- [ ] Deploy `web/delete-account-pamoja.html` and confirm it loads **in a
      private window with no login**. The form asks for this URL and Google
      does check it. A page behind auth, or one that 404s, fails the review.
- [ ] Confirm the URL: `https://www.arkayenlabs.com/privacy/pamoja/delete-account`
      (matches the existing convention, `/privacy/freshtrack/delete-account`)
- [ ] Confirm the privacy page links to it. Verified 2026-08-20 that it does
      **not**, so the link has to be added when the page is deployed

---

## Data types to declare

Collected means it leaves the device. All of it does, since Firestore is remote.

### Personal info

| Type | Collected | Shared | Optional? | Purpose | Where in code |
|---|---|---|---|---|---|
| Name | Yes | **Yes, other users** | Required | App functionality | `UserDto.name`, denormalised to `MembershipDto.displayName` |
| Email address | Yes | No | Required for email sign-in | App functionality, Account management | Firebase Auth, email provider |
| Phone number | Yes | No | Required for phone sign-in | App functionality, Account management | Firebase Auth, phone provider |
| User IDs | Yes | **Yes, other users** | Required | App functionality | Firebase UID, on every membership doc |

Name is shared because the leaderboard shows it to every other member of your
group. That is the product working as intended, not a leak, but it must be
declared.

### Health and fitness

| Type | Collected | Shared | Optional? | Purpose | Where in code |
|---|---|---|---|---|---|
| Health info | Yes | No | **Optional** | App functionality | `UserDto.age`, `.height`, `.weight` |
| Fitness info | Yes | **Yes, other users** | Required | App functionality | Step counts via Health Connect `READ_STEPS` |

Age, height and weight are optional, owner-only, and never leave the `users`
document, which `firestore.rules` locks to its owner. Step counts are shared
because today's and this week's totals are denormalised onto the membership
document so the group leaderboard can read them.

🔴 **Health Connect data is never shared with any third party.** It goes only to
this project's own Firebase and to other members of the user's own group. The
Health Apps declaration must say the same thing.

### Photos and videos

| Type | Collected | Shared | Optional? | Purpose | Where in code |
|---|---|---|---|---|---|
| Photos | Yes | **Yes, other users** | Optional | App functionality | `avatars/{uid}/profile.jpg`, group photo |

Optional in the real sense: a profile photo is only visible to other members
when `showPhotoInGroups` is on, and that defaults to **false**.

### App activity

| Type | Collected | Shared | Optional? | Purpose | Where in code |
|---|---|---|---|---|---|
| App interactions | Yes | No | Required | Analytics | Firebase Analytics, 20 typed events in `AnalyticsManager` |

### App info and performance

| Type | Collected | Shared | Optional? | Purpose | Where in code |
|---|---|---|---|---|---|
| Crash logs | Yes | No | Required | Analytics | Firebase Crashlytics |
| Diagnostics | Yes | No | Required | Analytics | Firebase Crashlytics / Performance |

### Device or other IDs

| Type | Collected | Shared | Optional? | Purpose | Where in code |
|---|---|---|---|---|---|
| Device or other IDs | Yes | No | Required | Analytics, App functionality | Firebase Analytics app instance ID, Firebase Installations ID, FCM registration |

🔴 **Do not cite `UserDto.deviceToken` as the source, which an earlier draft of
this file did.** `SaveDeviceTokenUseCase` exists but is **never called from
anywhere**, so that field is never written and no push token of ours is stored.

The declaration is still **Yes**, for a different reason: `firebase-messaging`,
`firebase-analytics` and Crashlytics are all shipped, and those SDKs generate
and transmit device-scoped identifiers on their own regardless of whether this
app's code uses them.

**Worth acting on after launch:** the app ships `firebase-messaging` but has no
`FirebaseMessagingService` and no `MESSAGING_EVENT` intent filter, so it can
never receive a push. Every notification the app shows is **local**, posted by
`SmartNotificationHelper` from `StepSyncWorker`. The dependency is therefore
collecting an identifier for a feature that does not exist. Removing it would
shrink both the APK and this declaration. Do not remove it the day before a
release; see the note in the release plan about not cleaning up dead code on the
eve of shipping.

---

## Security practices

| Question | Answer | Basis |
|---|---|---|
| Is data encrypted in transit? | **Yes** | All traffic is Firebase over TLS. The one cleartext exception is `10.0.2.2` for the emulator, permitted only by the **debug** network security config, so it is not in the shipped app |
| Can users request data deletion? | **Yes** | In-app, plus the web URL above |
| Independent security review? | **No** | None has been done. Do not claim one |
| Play Families policy? | **N/A** | Not targeted at children |

---

## Two judgement calls, both made deliberately

**1. "Shared" is declared generously.** Google's definition of sharing centres
on transfer to a *third party*, and name, steps and photos here are visible to
other members of the user's own group rather than sold or sent onward. It would
be arguable to declare no sharing at all. **Declare it anyway.** Under-declaring
is what gets apps removed; over-declaring costs nothing but a line on the store
listing, and "group members can see your name and step count" is the honest
description of a social leaderboard.

**2. On-device backup is on, and is not what this form asks about.**
`AndroidManifest.xml` sets `allowBackup="true"` with `backup_rules.xml` and
`data_extraction_rules.xml` **still the untouched scaffold templates**, so every
DataStore preference goes to the user's Google Drive backup. The Data Safety
form does not ask about Android Auto Backup directly, so it changes no answer
here. It is flagged because `CHECKLIST.md` §1.4 raised it and it is still
unaddressed: worth an explicit `<exclude>` pass after launch, not before.

---

## After filing

- [ ] Re-read the deployed privacy page and confirm it matches every row above
- [ ] Confirm the store listing's data-safety summary reads the way you expect
- [ ] Record the filing date here so the next session does not re-derive it
