# Privacy Policy — Pamoja

**Last updated: 19 September 2026**

## 1. Who operates Pamoja

Pamoja is a private group walking app operated by Balaji Thukuntala, sole proprietor trading as Arkayen Labs, Mumbai, Maharashtra, India. Contact: support@arkayenlabs.com.

This policy describes the data used for accounts, step tracking, shared weekly goals, Premium subscriptions and the Together Trail. Some features are introduced gradually; this describes the data they use when available.

## 2. Accounts and profiles

Sign-in uses Firebase Authentication. Google sign-in provides an email address, display name and profile picture URL; email sign-in uses an email address; phone sign-in uses a phone number and an SMS verification code. Firebase handles passwords. We do not receive your plaintext password.

Your account has a stable Firebase user ID. A linked sign-in method opens the same account. Your display name appears in groups. You may add a profile photo and choose whether it is shown to other group members; that sharing option is off by default. Group photos are visible to group members.

Older profiles may contain optional age, height or weight supplied by the user. These fields are private and are not needed to count steps or participate in a group. We do not collect identity documents or precise location.

## 3. Steps and Health Connect

With your permission, Pamoja reads aggregated step counts from Health Connect. It does not read heart rate, sleep, nutrition, routes, exercise sessions or body measurements from Health Connect, and it does not write health records.

For your dashboard and weekly groups, daily step totals and dates are uploaded to our Firebase database. After missed syncs, the app can recover up to seven recent days, subject to permissions and the data available on your phone. Other members of groups you join can see your display name, daily and weekly totals and ranking.

If you deliberately join the Together Trail, the app also reads aggregate steps for server-defined time windows. The service stores your joining baseline, window boundaries and totals, synchronization timestamps and revisions, participation status, and credited contribution. These records prevent earlier steps and retries from being counted again. Private source-window records are not exposed to other members. The group can see shared Trail progress and planning summaries.

Health Connect data is used for visible step tracking, group progress and the Together Trail. It is not sold, used for advertising, sent to a generative AI service or used to train machine learning models. Our use and transfer of Health Connect information follows its Permissions policy, including Limited Use requirements.

## 4. Plans, groups and Premium

We store group names and photos, goals, calendar/timezone settings, membership and organizer roles, joining dates, invitations, weekly summaries, scheduled goals and planning responses. Trail participation may include your chosen weekly commitment or rest preference. Group planning and progress information is shared with current group members; your account email and phone number are not.

Google Play processes purchases. RevenueCat receives purchase information and your app user ID to verify and restore subscriptions. Our backend stores subscription status, transaction/event references, expiry information, plan capacity and the groups supported by a subscription. We do not receive your payment card number or bank credentials. Purchase history is financial information even though we do not process the payment ourselves.

One sponsor can provide Premium access to one or more groups up to the capacity of the selected plan. Group members can see whether their group has access. A personal subscription and a group entitlement are distinct records. A purchase does not change your group role.

## 5. Analytics, diagnostics and notifications

Firebase Analytics records app interactions such as onboarding, group creation, invitation use and sync outcomes. Our app events may contain group IDs, group names, configured goals, timestamps, durations and error categories. They do not include actual step readings, account email, phone number or Firebase user ID parameters. The analytics SDK also processes installation/device information under its own service terms.

Firebase Crashlytics records crash stack traces and device/app diagnostics in release builds. We do not intentionally attach step readings, names, email addresses or phone numbers to crash reports. Firebase App Check and Play Integrity help validate app requests; they do not guarantee that a client-supplied step count cannot be falsified.

Notifications can be generated on the device or delivered through Firebase Cloud Messaging. Push registration associates an installation with your account and app version. Notification preferences, quiet hours and Android permission control delivery. Notifications can contain group activity; lock-screen visibility is also controlled by your Android settings.

## 6. Background work and local storage

Optional background Health Connect access lets the app read steps while closed. Ordinary step work is scheduled at a 15-minute interval and Trail work at an hourly interval, with network and battery constraints. These are requested intervals, not delivery guarantees: Android may delay or stop work. Opening the app or choosing Sync can update sooner. You can decline background access and sync while using the app.

Private app storage holds sign-in state managed by Firebase, preferences, language/theme choices, cached group and step data, sync baselines and notification counters. Home-screen widgets show cached progress and may be visible to anyone who can see your home screen. App data is subject to the backup exclusions in the shipped app.

An earned Trail completion card is generated locally only when you choose to share it. It contains the Trail title and shared goal, without group/member names, account IDs or raw health history. A temporary image is kept in app cache, replaced by the next card and removable by clearing cache. Android grants the app you choose access to that image. We do not send it automatically; the destination app controls any copy you send.

The illustrations and stories are bundled content. Viewing them does not send your health data to an AI image or text generator.

## 7. Service providers and sharing

Google/Firebase provides authentication, Cloud Firestore, Cloud Storage for photos, Cloud Functions, analytics, Crashlytics, App Check, Play Integrity and Cloud Messaging. Health Connect supplies permitted step data; Google Play provides billing and updates. RevenueCat provides subscription verification and entitlement management. Phone verification also uses your mobile carrier.

These providers process data needed to operate their services. Data may be processed outside your country. Their policies apply to their services: https://policies.google.com/privacy and https://www.revenuecat.com/privacy/.

Groups are private and invite-based, with no public group directory. Information you intentionally share through an invitation, export or completion card goes to the recipient you choose. Group members can retain information they have already seen. We do not sell personal or health data or use it for advertising. Pamoja contains no advertising.

## 8. Your controls and deletion

You can change your profile, manage photo visibility, leave a group as a member, pause Trail contribution, change notification preferences, revoke Health Connect access and export your app data from Settings. Turning off access prevents new reads; it does not erase previously synchronized cloud data.

Settings → Delete Account asks you to confirm your identity when needed, then removes memberships, personal step records, the profile and authentication account. Profile-photo deletion is attempted as part of this process. Group ownership can pass to another remaining member; a group with no remaining owner/member can be removed.

Membership deletion triggers server cleanup of personal Trail source records, commitments and attribution, and of personal weekly-review/planning attribution. This cleanup runs asynchronously and can be retried. Already-earned shared aggregate progress can remain for the other members without your personal attribution. Group deletion triggers cleanup of its Trail records.

Deleting the app is not the same as deleting the account. Deleting an account does not cancel a Google Play subscription: manage cancellation in Google Play. Subscription/transaction records held by Google Play, RevenueCat and our billing system are not all erased by the in-app account flow. Contact support for remaining personal-data deletion requests; records necessary for payment disputes, security or legal obligations may need to be retained.

For deletion without the app, use https://www.arkayenlabs.com/privacy/pamoja/delete-account or email support@arkayenlabs.com from the associated address, or include the account phone number. We may verify ownership and aim to respond within 30 days. Do not send passwords or reset links.

Active account data is kept while needed to provide your account and features. Cloud deletion does not remove exports or images already sent to other apps, and service-provider backup/log retention can differ.

## 9. Security and permissions

Data is encrypted in transit and our cloud providers encrypt stored data. Authentication, backend membership checks, Firestore rules and App Check restrict access. No system can guarantee absolute security.

Permissions include Internet access, Health Connect READ_STEPS, optional READ_HEALTH_DATA_IN_BACKGROUND and optional POST_NOTIFICATIONS. Choosing a profile photo uses the system picker; exports use the system document picker; sharing uses the Android share sheet. Pamoja does not request location, microphone or contacts access.

## 10. Age, rights and contact

Pamoja is intended for adults aged 18 and over. If you believe a child has provided personal information, contact us so we can investigate and remove it.

Depending on where you live, you may have rights to access, correct, delete or receive your data, and to object to or restrict processing. Contact support@arkayenlabs.com for requests not covered by in-app controls.

We will update this policy when the product or data practices change and update the date above. Material changes will be communicated where required. Terms: https://www.arkayenlabs.com/terms/pamoja.
