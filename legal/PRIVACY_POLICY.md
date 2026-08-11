# Privacy Policy. Pamoja

**Last updated: 11 August 2026**

> Source of truth for the page published at `https://www.arkayenlabs.com/privacy/pamoja`.
> Edit here first, then mirror into `web/privacy-pamoja.html`.
>
> ⚠️ **The Data Safety form in Play Console must match this document exactly.**
> A mismatch between the app, this policy and that form is one of the most
> common causes of rejection and post-launch removal. Re-check all three
> together whenever any of them changes.

---

## 1. Introduction

Pamoja ("together" in Swahili) is a social group step-tracking Android application operated by **Balaji Thukuntala, sole proprietor, trading as Arkayen Labs** ("we", "us", "our"), Mumbai, Maharashtra, India. Pamoja lets you form private walking groups, set shared weekly step goals, and track progress together on a leaderboard.

This policy explains what we collect, why, who can see it, and what choices you have. By installing or using Pamoja you agree to the practices described here.

## 2. Accounts and sign-in

**Pamoja requires an account.** You create one using any of three methods:

| Method | What we receive |
|---|---|
| **Google** | Your Google account's email address, and the display name and profile picture URL attached to it |
| **Phone number** | Your phone number in international format |
| **Email and password** | Your email address. The password is handled by Firebase Authentication and **we never see or store it** |

Whichever you choose, Firebase Authentication issues a stable user ID that identifies you across devices. Signing in again with the same method on a new phone restores your groups and history.

**This is a change from earlier versions of Pamoja**, which used anonymous accounts with no email or phone number. If you used Pamoja before this policy's date, sign-in is now required.

We do **not** collect identity documents, government identifiers, or any financial information.

### Phone verification

If you sign in by phone, Firebase sends a one-time code by SMS. Your carrier's standard message rates apply. The number is used to authenticate you and to recognise you on a future device. **It is never shown to other members of your groups.**

## 3. What we collect

### a) Account identifiers

- Your **email address**, if you signed in with email or Google
- Your **phone number**, if you signed in by phone
- The **user ID** Firebase assigns to your account

### b) Profile information

Provided by you during onboarding:

- **Display name**, shown to members of your groups
- **Age**, optional
- **Height and weight**, optional

Age, height and weight are collected but **not currently used by any feature**, and are never shown to other members. We are reviewing whether to keep collecting them.

### c) Step count data

- Your **daily aggregated step count**, read from Google Health Connect
- The **date** each count corresponds to

Only the daily total is read and uploaded. No heart rate, sleep, nutrition, exercise sessions, or any other health metric is ever accessed or stored.

### d) Group and membership data

- Groups you create or join: group name, weekly step target, member cap
- Your role in each group, admin or member
- Your display name, copied onto your membership record so leaderboards can render without other members reading your profile
- The time you joined

### e) Usage and analytics data

We use **Firebase Analytics** to understand how the app is used. Events logged include screen views, onboarding progress, group lifecycle events (created, joined, invite link used, weekly goal reached), and background sync outcomes.

Analytics events may include your user ID, group ID, step count and a timestamp. They do **not** include your display name, email address, phone number, age, height or weight.

### f) Crash and diagnostic data

We use **Firebase Crashlytics** to find and fix crashes. When Pamoja crashes it sends a stack trace along with your device model, operating system version, available memory and storage, and a Crashlytics-generated installation identifier. Crash reports do **not** include your step data, display name, email address or phone number.

Crash reporting is **disabled in development builds** and active only in the version distributed through Google Play.

### g) Device integrity

We use **Firebase App Check** with Google Play Integrity to confirm that requests come from a genuine, unmodified copy of Pamoja rather than a script. This checks the integrity of the app and device, not you, and returns no personal information to us.

### h) Data stored on your device

Pamoja keeps a small amount of data locally using Android Jetpack DataStore: your user ID and display name, onboarding status, Health Connect permission status, active group ID, step baseline values, a pending invite code if you opened an invite link before signing in, and counters used to time and rate-limit notifications.

This never leaves your device and is deleted when you clear app data or uninstall Pamoja.

## 4. Google Health Connect

Pamoja reads step data through Google Health Connect. Health Connect is built into Android 14 and above, and available from the Play Store on Android 9 to 13.

**Permission requested:** `android.permission.health.READ_STEPS`, daily step count only.

We request no other Health Connect permission. We do not read or write heart rate, sleep, blood pressure, nutrition, exercise sessions, body measurements, or any other data type.

### How step data flows

1. Health Connect stores step records from your device's sensors and any connected fitness apps
2. Pamoja reads your **aggregated total** for the current day, on your device
3. Only that single daily number is uploaded to Cloud Firestore, for your group's leaderboard
4. No individual step records, session timestamps, or source app names are transmitted

### Limited Use disclosure

Pamoja's use and transfer of information received from Health Connect adheres to the Health Connect Permissions policy, including the Limited Use requirements. Specifically:

- Health Connect data is used **solely** to provide the group step-tracking feature visible to you in the app
- Health Connect data is **never** used for advertising or marketing, and is never sold to data brokers
- Health Connect data is **never** used to determine creditworthiness or for lending
- Health Connect data is not transferred to any third party except as needed to provide the core feature, that is uploading your daily total to Firestore, or where required by law
- Health Connect data is **not used to train machine learning models**

Your use of Health Connect is also governed by Google's Privacy Policy.

## 5. Background step sync

Pamoja uses Android WorkManager to sync your step count roughly **every 30 minutes**, only when the device has network connectivity and the battery is not low. Each run reads the current day's total from Health Connect and uploads that one number. No foreground service or persistent notification is used.

If Health Connect is unavailable, or you revoke step-reading permission, sync stops quietly without retrying or prompting you.

## 6. Notifications

Pamoja sends reminders and updates about your groups. On Android 13 and above these require your permission, which you can grant or refuse when asked and change at any time in system settings. Refusing does not affect any other part of the app.

Notifications are generated **on your device** from data already synced. Notification content avoids anything sensitive so it is safe on a lock screen.

## 7. How your data is shared

Pamoja is a private, invite-only product:

- Your **display name, daily step count, weekly total and ranking** are visible to members of groups you have joined
- Your **email address, phone number, age, height and weight are never visible to other members**
- Groups are reachable only through an invite link or code shared by a member. There is no public directory
- Invite links stop working once a group reaches its member cap, and work again if a place frees up
- Nothing is public, and nothing is visible to anyone outside your groups
- We do **not** sell, rent, license or share your data with advertisers, data brokers or marketing platforms
- Pamoja contains **no advertising**

## 8. Third-party services

Pamoja relies on the following Google services. Each is governed by Google's own privacy policy.

| Service | Purpose |
|---|---|
| Firebase Authentication | Google, phone and email sign-in, and session management |
| Google Play Services, Credential Manager | Presenting the Google sign-in sheet and returning a sign-in token |
| Cloud Firestore | Storing profiles, groups, memberships and daily step counts |
| Firebase Analytics | Aggregated usage and onboarding funnel analytics |
| Firebase Crashlytics | Crash reports and diagnostics |
| Firebase App Check, Play Integrity | Confirming requests come from the genuine app |
| Firebase Cloud Messaging | Push notification infrastructure, present in the app for future use |
| Google Health Connect | Reading your daily step count |
| Google Play In-App Updates | Delivering app updates |

Phone sign-in additionally involves your **mobile network operator**, which carries the verification SMS.

## 9. Storage and security

Server-side data is held in Google Cloud Firestore, encrypted in transit and at rest. Firestore security rules restrict every record to its owner and to the groups they belong to; your profile document is readable only by you.

Local data sits in the app's private storage, sandboxed by Android and inaccessible to other apps.

We use industry-standard practices, but no method of transmission or storage is completely secure and we cannot guarantee absolute security.

## 10. Android permissions

| Permission | Why |
|---|---|
| `health.READ_STEPS` | Read your daily step count from Health Connect |
| `INTERNET` | Sync steps, groups and profile with our database |
| `POST_NOTIFICATIONS` | Send step reminders and group updates, Android 13 and above |

Pamoja does **not** request camera, microphone, contacts, location, file, or any other sensor access.

## 11. Retention and deletion

We keep your data for as long as your account exists.

### Deleting your account

**Settings → Delete Account** removes everything, in this order:

1. Every membership you hold, with each group's member count corrected
2. Your entire step history
3. Your profile
4. Your authentication record

For your protection we ask you to confirm your identity first, by signing in again with Google, your password, or a code sent to your phone.

**Deletion is immediate and permanent. It cannot be undone.**

### Deleting your account without the app

If you cannot access the app, email **support@arkayenlabs.com** from the address associated with your account, or including the phone number associated with it, and we will delete your data within 30 days.

### Other controls

- **Revoke Health Connect permission** at any time in your device's Health Connect settings. Background sync stops automatically
- **Uninstalling** removes all locally stored data and revokes Health Connect access. Your cloud data remains until you delete your account or ask us to

## 12. International transfers

Your data is processed on Google Cloud infrastructure, which may sit in data centres outside your country of residence. By using Pamoja you consent to that transfer. Google Cloud complies with applicable frameworks for international data transfers.

## 13. Age requirement

**Pamoja is for people aged 18 and over.** It is not directed at children, and we do not knowingly collect data from anyone under 18.

India's Digital Personal Data Protection Act treats everyone under 18 as a child requiring verifiable parental consent, which is why the threshold is 18 rather than 13.

If you believe someone under 18 has given us personal data, email us and we will delete it promptly.

## 14. Your rights

Depending on where you live, you may have the right to access your data, correct it, delete it, object to or restrict its processing, or receive it in a portable form.

Your display name is editable in the app, and full deletion is available in Settings. For anything else, email us and we will respond within 30 days.

## 15. Changes

We may update this policy to reflect changes in the app, our practices, or the law. Material changes update the date at the top of this page. Continued use after a change means you accept the updated policy.

## 16. Contact

**Balaji Thukuntala, trading as Arkayen Labs**
Mumbai, Maharashtra, India
Email: **support@arkayenlabs.com**

Terms of Use: [https://www.arkayenlabs.com/terms/pamoja](https://www.arkayenlabs.com/terms/pamoja)

---

## Status, internal

Corrections made on 11 August 2026, against the version dated 22 June 2026:

- [x] §2 rewritten. The previous version described anonymous authentication and stated
      that no email address or phone number was collected. Both became false when
      sign-in was made mandatory
- [x] §3 gained account identifiers, crash data (Crashlytics) and device integrity
      (App Check), none of which existed before
- [x] §3(b) now admits age, height and weight are collected but unused
- [x] §10 gained `POST_NOTIFICATIONS`, which was missing
- [x] §11 rewritten. The old text said in-app deletion removed only the auth record
      and told users to email for the rest. Deletion is now complete and automatic
- [x] Removed the claim that a user can leave a group in the app. **No such feature
      exists**, and neither does member removal or invite-link revocation
- [x] Age threshold raised from 13 to 18, matching the Terms and DPDP
- [x] Contact updated to the sole proprietorship and support@arkayenlabs.com

Still to do:

- [ ] Mirror into `web/privacy-pamoja.html` and deploy
- [ ] Point the Play Data Safety **account deletion URL** at the deletion section
- [ ] Re-check the Data Safety form against this document, especially the newly
      declared crash logs, diagnostics, email address and phone number
- [ ] Decide on age, height and weight. Collecting data no feature uses is a
      liability with no upside under data-minimisation principles
- [ ] Either build leave-group and invite revocation, or keep both documents quiet
      about them
