# Pamoja

Pamoja is an Android app for walking together. A small group sets one weekly step
goal and works toward it as a team, with every member's contribution visible to
the rest. Steps are read from
[Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect)
on the member's own device and synced through Firebase, so the shared total stays
current without anyone having to report anything.

*Pamoja* is Swahili for *together*.

The product is built around a shared goal rather than a ranking. Group size is
small by design, the week is a fixed window every member agrees on, and the
screens are written so that the person contributing least is not the person the
interface points at.

This repository is the Android client. It is pre-release and has not yet been
published to Google Play.

## Features

**Groups and membership**

- Create a group with a weekly step target, a member cap between 2 and 20, and a
  chosen start day for the week
- Join by invite link, by QR code in person, or by pasting a code
- Member cap enforced atomically, so two people joining the last slot at the same
  moment cannot both get in
- Admin controls for the group name, goal, cap, week start day, photo, and member
  removal, with handover when an admin leaves

**Steps and progress**

- Daily step totals read from Health Connect, aggregated across every app that
  writes them, with no foreground service and no hardware sensor registration
- A background sync scheduled through WorkManager, plus an opportunistic sync
  when the app is resumed
- One shared weekly total per group, over a window every member shares, plus a
  per-member leaderboard showing today and the week so far
- Offline states that say when a figure was last known to be true, rather than
  presenting a stale number as current

**Account and data**

- Sign in with Google, a phone number and SMS code, or email and password
- Profile with an optional photo, and a per-user setting controlling whether that
  photo is visible to other group members
- Export every piece of data the app holds about you, as readable JSON, saved
  wherever you choose through the system document picker
- Delete your account and all associated data, including memberships, step
  history, and stored images

**Notifications**

- Local notifications chosen by an on-device engine that weighs quiet hours,
  frequency, muted categories, and how recently you last engaged
- An in-app activity centre recording what was sent, so a missed notification can
  still be caught up on

## Architecture

Three layers, with dependencies pointing inward:

```
UI (Compose screens, ViewModels)
        |
        v
Domain (use cases, models, repository interfaces)
        |
        v
Data (Firebase, Health Connect, DataStore)
```

- The domain layer is pure Kotlin. No Android imports, no Firebase imports, and
  no user-facing text.
- The data layer implements the domain's repository interfaces and maps Firebase
  errors to a domain error type at its boundary, so nothing above it handles a
  `FirebaseFirestoreException`.
- ViewModels call use cases and expose immutable state as `StateFlow<UiState>`.
  Screens receive lambdas for navigation and never touch a `NavController`.

Every fallible call returns `Result<T>`, and failures are a sealed `AppError`
type matched on by class rather than by message text. User-facing wording for
each error lives in the UI layer, because copy is a design decision.

Real-time data (group members, the user's groups) is exposed as `Flow` backed by
Firestore snapshot listeners.

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose, Material 3 |
| Architecture | Clean Architecture with MVVM |
| Dependency injection | Hilt 2.56.1 |
| Auth | Firebase Auth (Google, phone, email) |
| Database | Cloud Firestore |
| Health data | Health Connect 1.1.0-rc01 |
| Background work | WorkManager with Hilt worker injection |
| Local storage | DataStore Preferences |
| Billing | RevenueCat 10.16.2, integrated but not yet active |
| Crash and analytics | Firebase Crashlytics, Firebase Analytics |
| Integrity | Firebase App Check with Play Integrity |
| Type | Bricolage Grotesque, Plus Jakarta Sans, IBM Plex Mono, via Google Fonts |
| Min SDK | 26 (Android 8.0) |
| Compile and target SDK | 36 |

The build uses a single `:app` module and a Gradle version catalog at
`gradle/libs.versions.toml`. The `namespace` (`com.pamoja.app`) and the
`applicationId` (`com.arkayenlabs.pamoja`) differ deliberately.

## Project structure

```
app/src/main/java/com/pamoja/app/
  data/
    local/health/          Health Connect reader
    local/preferences/     DataStore
    remote/firebase/       Repository implementations
    remote/model/          Firestore DTOs
  di/                      Hilt modules
  domain/
    error/                 AppError
    model/                 Group, Membership, StepEntry, User, WeekWindow
    repository/            Interfaces
    usecase/               Use cases, grouped by entity
  ui/
    theme/                 Design tokens, light and dark
    components/            Shared composables
    <feature>/             One package per feature, screen plus ViewModel
  worker/                  StepSyncWorker
```

Firestore security rules and indexes live at the repository root in
`firestore.rules` and `firestore.indexes.json`. Cloud Storage rules are in
`storage.rules`.

## Data model

| Collection | Document ID | Purpose |
|---|---|---|
| `users` | `{userId}` | Private profile. Readable only by its owner. |
| `groups` | `{groupId}` | Group settings, member count, cached weekly total. |
| `memberships` | `{userId}_{groupId}` | Who is in which group, with the display name, photo, and step totals the leaderboard reads. |
| `steps` | `{userId}_{date}` | One row per person per day. Readable only by its owner. |

Some fields are denormalised onto the membership document on purpose. A member's
display name, photo, and step totals are copied there so that rendering a
leaderboard is a single query rather than one read per member, and so that the
step history itself never has to be readable by anyone other than its owner.

## Security

Access is enforced by Firestore and Cloud Storage rules, not by the client. The
rules are the source of truth and the client is written to satisfy them.

- A user's profile and raw step history are readable only by that user
- Group documents can be read by ID, which the invite flow needs, but cannot be
  listed, so the set of groups cannot be enumerated
- Membership queries are permitted only where the caller belongs to the group
  being queried
- Step entries and published totals are bounded, so an implausible figure cannot
  be written to a leaderboard
- Joining a group is a transaction whose member cap is enforced by the rules as
  well as by the client

Rules changes are exercised against the Firebase emulator by the instrumented
test suite before being deployed. See [Testing](#testing).

## Development environment

- Android Studio Ladybug (2024.3.1) or newer
- JDK 17 for the Gradle toolchain, with the project compiling to JVM target 11
- Android SDK 36
- Node.js, for the Firebase CLI used by the emulator-backed tests

A Firebase project is required, with Authentication (Google, phone, and
email/password), Cloud Firestore, and Cloud Storage enabled. Place its
`google-services.json` in `app/`. That file is not committed.

## Build

```bash
./gradlew assembleDebug
```

A release build additionally needs a `keystore.properties` in the repository
root:

```properties
storeFile=path/to/keystore.jks
storePassword=...
keyAlias=...
keyPassword=...
```

```bash
./gradlew assembleRelease
```

Release builds run R8 with resource shrinking. Debug and release use different
App Check providers, selected by build variant rather than at runtime, so the
debug provider cannot reach a release build.

The RevenueCat public SDK key is read from `local.properties` as
`revenueCatApiKey` and defaults to empty. With no key the SDK is never
configured and every account resolves to the free tier, so the project builds
and runs without one.

## Testing

Unit tests run on the JVM and need nothing else:

```bash
./gradlew test
```

The instrumented suite is emulator-backed and uses no mocks. It drives the real
repository code against a real Firestore emulator loading this repository's real
`firestore.rules`, signed in as real Firebase Auth users, because several
invariants are enforced jointly by the client and the rules and testing either
half against a stand-in for the other proves very little.

Start the emulators first, then run the suite against a connected device or AVD:

```bash
firebase emulators:start --only auth,firestore
```

```bash
./gradlew connectedDebugAndroidTest
```

These cover the member cap under genuine concurrency, weekly step aggregation and
its window boundaries, and who is allowed to read each collection.

Lint runs clean of errors:

```bash
./gradlew lint
```

## Status

Pre-release. The app builds in debug and release, the test suites pass, and the
Firestore rules are deployed. It has not been published to Google Play, and
in-app purchases are integrated but not yet enabled.

## License

No open source license has been applied to this repository.
