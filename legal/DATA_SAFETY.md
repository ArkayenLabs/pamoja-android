# Pamoja — Play Data Safety reconciliation

Updated 19 September 2026 against the combined release source. This replaces the August answer sheet, whose claims about local-only notifications and no billing data are obsolete. This file is preparation, not evidence that the live Play form has been updated.

## Collection inventory

| Data | Actual use / recipient | User control / visibility |
|---|---|---|
| Name and account user ID | Firebase Auth/Firestore; group membership; account ID also used for RevenueCat identity | Name/ID on group membership records; email/phone are not shown to group members |
| Email / phone | Firebase Authentication for selected sign-in method | Required for the chosen method; alternative methods exist |
| Photos | Firebase Storage profile/group images | Optional; profile group sharing defaults off |
| Fitness / step information | Firebase daily totals and dates; Trail aggregate time windows, joining baselines, credited deltas, revisions and sync timestamps | Health permission optional; joining a Trail is explicit; group totals and leaderboards visible to members; private Trail source records restricted |
| Legacy age/height/weight | Private profile fields supplied by user | Optional; not required for steps; onboarding no longer requires them |
| Purchase history | Google Play, RevenueCat and Firebase billing records: product/status/transaction references/expiry/plan capacity/group sponsorships | Optional purchase; not card or bank credentials; one sponsor may cover one or more groups according to the selected plan |
| App interactions | Firebase Analytics events, including group IDs/names, configured targets, timestamps, duration and classified errors | No actual step readings or account email/phone/UID parameters in typed app events; SDK installation/device data still exists |
| Crash logs / diagnostics | Firebase Crashlytics release crash reporting | Provider retention; no intentional raw health payload |
| Device/installation IDs | Firebase Analytics, Crashlytics, Installations, FCM and App Check | Push registration is active functionality, not a future placeholder |
| Planning and commitments | Firebase scheduled shared goals, answers and personal Trail commitments | Current group members see shared planning; Trail source details remain private |
| Completion card/export | Locally generated and sent only to the app/location selected through Android | No automatic transmission; completion card omits member/group identities and source health history |

For Play's “shared” answers, apply its definitions rather than treating every provider transmission as advertising sharing. Service-provider processing and expected user-initiated sharing have specific exceptions. Review the actual integrations before selecting these exceptions. Do not reuse the previous blanket “Health data never reaches any third party” claim: Firebase processes it for the app and selected group members receive product-visible totals.

## Source anchors

- app/src/main/java/com/pamoja/app/data/analytics/FirebaseAnalyticsManager.kt
- app/src/main/java/com/pamoja/app/data/remote/firebase/FirebaseUserRepositoryImpl.kt
- app/src/main/java/com/pamoja/app/data/remote/firebase/FirebaseAdventureRepository.kt
- app/src/main/java/com/pamoja/app/util/WorkManagerScheduler.kt
- app/src/main/java/com/pamoja/app/notifications/PamojaFirebaseMessagingService.kt
- app/src/main/java/com/pamoja/app/ui/adventure/TrailCompletionShare.kt
- functions-weekly/src/adventure-privacy.ts
- functions/src/index.ts
- app/src/main/AndroidManifest.xml (AD_ID removed; scoped FileProvider)
- legal/PRIVACY_POLICY.md and web/privacy-pamoja.html

## Release gate

- [ ] Publish the revised privacy and deletion pages through the separate website deployment; verify live content.
- [ ] Inspect and reconcile every live Play Data Safety answer, including purchase history, fitness data, photos and identifiers.
- [ ] Verify Health Connect disclosure and background permission wording against the candidate.
- [ ] Verify deletion/export with real authorized accounts; asynchronous Trail cleanup is not an immediate full-provider erasure guarantee.
- [ ] Confirm billing retention and support deletion handling. The in-app flow does not erase all RevenueCat/Google Play/billing records.
- [ ] Confirm Analytics/Crashlytics configuration and retention; do not invent provider retention periods.
- [ ] Preserve no advertising/no sale/no health-to-AI behavior in the shipped candidate.

References: [Play Data Safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469), [RevenueCat customer identities](https://www.revenuecat.com/docs/customers/identifying-customers).
