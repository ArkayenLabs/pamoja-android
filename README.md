# Pamoja

**Pamoja** is an Android step-tracking app that lets users form groups and work toward a shared weekly step goal together. Steps are read from [Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect) and synced to the cloud via Firebase, so every group member's progress is visible in a real-time leaderboard.

> *"Pamoja"* — Swahili for *"together"*.

---

## Features

- **Anonymous Authentication** — Users sign in anonymously via Firebase Auth. No email/password required; the Firebase SDK persists the session across app restarts and reinstalls.
- **Profile Setup** — Onboarding collects the user's name, age, height, and weight.
- **Health Connect Integration** — Reads daily step counts from Google Health Connect (built-in on Android 14+, available via Play Store on Android 9–13). No foreground service or hardware sensor registration needed.
- **Group Creation & Invite Links** — Users create walking groups with a configurable weekly step target and member cap (default 70,000 steps/week, max 10 members). A `pamoja://join/{groupId}` invite link is generated for sharing.
- **Join via Invite Link** — Members paste an invite link to join a group. The link auto-deactivates when the group reaches its member cap.
- **Real-time Leaderboard** — The group detail screen shows each member's today-steps and weekly-steps, ranked by today's count, with a combined weekly total and progress toward the group target.
- **Admin Controls** — Group admins can update the weekly step target. Non-admin members can also edit the target if the group's `canMembersEditTarget` flag is enabled.
- **Background Step Sync** — A `WorkManager` periodic worker runs every 30 minutes (when network is available and battery is not low) to read today's steps from Health Connect and write them to Firestore.
- **Play In-App Update** — Supports flexible in-app updates via the Play Core library for seamless version upgrades during closed testing.
- **Dark Mode Only** — Premium dark theme with an indigo accent palette, custom Outfit typography, and edge-to-edge display.

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin |
| **UI** | Jetpack Compose + Material 3 |
| **Navigation** | Compose Navigation |
| **Architecture** | MVVM + Clean Architecture (domain / data / ui layers) |
| **Dependency Injection** | Hilt (Dagger) |
| **Authentication** | Firebase Auth (anonymous) |
| **Database** | Cloud Firestore (real-time listeners via `callbackFlow`) |
| **Messaging** | Firebase Cloud Messaging (FCM) — dependency included |
| **Health Data** | Health Connect (`androidx.health.connect:connect-client`) |
| **Background Work** | WorkManager with Hilt worker injection |
| **Local Storage** | Jetpack DataStore Preferences |
| **In-App Updates** | Play In-App Update KTX |
| **Typography** | Outfit font family (bundled TTF files) |
| **Min SDK** | 26 (Android 8.0) |
| **Target / Compile SDK** | 36 |

---

## Project Structure

```
pamoja-android/
├── app/
│   ├── src/main/
│   │   ├── java/com/pamoja/app/
│   │   │   ├── MainActivity.kt          # Entry point — session check, edge-to-edge, in-app update
│   │   │   ├── Pamoja.kt                # Application class — HiltAndroidApp, WorkManager config
│   │   │   │
│   │   │   ├── data/                    # Data layer
│   │   │   │   ├── local/
│   │   │   │   │   ├── health/
│   │   │   │   │   │   ├── HealthConnectReader.kt    # Reads today's steps from Health Connect
│   │   │   │   │   │   ├── StepCounterManager.kt     # Hardware step counter manager (legacy)
│   │   │   │   │   │   ├── StepCounterService.kt     # Step counter service (legacy)
│   │   │   │   │   │   └── StepReader.kt             # Step reader abstraction (legacy)
│   │   │   │   │   └── preferences/
│   │   │   │   │       └── UserPreferences.kt        # DataStore-backed user preferences
│   │   │   │   ├── remote/
│   │   │   │   │   ├── firebase/
│   │   │   │   │   │   ├── FirebaseAuthRepositoryImpl.kt
│   │   │   │   │   │   ├── FirebaseGroupRepositoryImpl.kt
│   │   │   │   │   │   ├── FirebaseStepRepositoryImpl.kt
│   │   │   │   │   │   └── FirebaseUserRepositoryImpl.kt
│   │   │   │   │   └── model/           # Firestore DTOs with toDomain() / fromDomain()
│   │   │   │   │       ├── GroupDto.kt
│   │   │   │   │       ├── MembershipDto.kt
│   │   │   │   │       ├── StepEntryDto.kt
│   │   │   │   │       └── UserDto.kt
│   │   │   │   └── repository/          # (empty — repository impls are in remote/firebase)
│   │   │   │
│   │   │   ├── di/                      # Hilt modules
│   │   │   │   ├── AppModule.kt         # FirebaseAuth, Firestore, UserPreferences, StepCounterManager
│   │   │   │   ├── RepositoryModule.kt  # Binds repository interfaces → Firebase implementations
│   │   │   │   └── WorkerModule.kt      # Provides WorkManager instance
│   │   │   │
│   │   │   ├── domain/                  # Domain layer (pure Kotlin)
│   │   │   │   ├── model/
│   │   │   │   │   ├── Group.kt         # groupId, name, adminId, weeklyTarget, maxMemberCap, inviteLink
│   │   │   │   │   ├── Membership.kt    # userId, groupId, role, canEditTarget
│   │   │   │   │   ├── StepEntry.kt     # userId, stepCount, date
│   │   │   │   │   └── User.kt          # userId, name, photoUrl, age, height, weight, deviceToken
│   │   │   │   ├── repository/          # Repository interfaces
│   │   │   │   │   ├── AuthRepository.kt
│   │   │   │   │   ├── GroupRepository.kt
│   │   │   │   │   ├── StepRepository.kt
│   │   │   │   │   └── UserRepository.kt
│   │   │   │   └── usecase/             # Business logic use cases
│   │   │   │       ├── AuthUseCases.kt      # SignUp, SignIn, SignInAnonymously, SignOut, GetCurrentUser, IsUserLoggedIn
│   │   │   │       ├── GroupUseCases.kt     # CreateGroup, GetGroup, JoinGroup, GetGroupMembers, GetUserGroups, UpdateWeeklyTarget, GetMembership
│   │   │   │       ├── StepUseCases.kt      # SyncTodaySteps, GetGroupStepsForWeek, GetStepsForUser
│   │   │   │       └── UserUseCases.kt      # CreateUser, GetUser, UpdateUser, SaveDeviceToken
│   │   │   │
│   │   │   ├── ui/                      # Presentation layer
│   │   │   │   ├── Navigation.kt        # Screen sealed class (route definitions)
│   │   │   │   ├── PamojaNavGraph.kt    # Compose NavHost wiring all screens
│   │   │   │   ├── CreateOrJoinGroupScreen.kt
│   │   │   │   ├── CreateOrJoinViewModel.kt
│   │   │   │   ├── components/          # (shared composables — currently empty)
│   │   │   │   ├── onboarding/
│   │   │   │   │   ├── WelcomeScreen.kt
│   │   │   │   │   ├── SignInScreen.kt
│   │   │   │   │   ├── SignInViewModel.kt
│   │   │   │   │   ├── ProfileSetupScreen.kt
│   │   │   │   │   ├── OnboardingViewModel.kt
│   │   │   │   │   ├── HealthConnectScreen.kt
│   │   │   │   │   └── HealthConnectViewModel.kt
│   │   │   │   ├── home/
│   │   │   │   │   ├── HomeScreen.kt
│   │   │   │   │   └── HomeViewModel.kt
│   │   │   │   ├── group/
│   │   │   │   │   ├── CreateGroupScreen.kt
│   │   │   │   │   ├── CreateGroupViewModel.kt
│   │   │   │   │   ├── GroupScreen.kt
│   │   │   │   │   └── GroupViewModel.kt
│   │   │   │   ├── invite/
│   │   │   │   │   ├── InviteScreen.kt
│   │   │   │   │   └── InviteViewModel.kt
│   │   │   │   └── theme/
│   │   │   │       ├── Color.kt         # Design system color tokens (indigo, green, amber, etc.)
│   │   │   │       ├── Theme.kt         # Dark-only Material 3 color scheme
│   │   │   │       └── Type.kt          # Outfit font family + full Material 3 type scale
│   │   │   │
│   │   │   ├── util/
│   │   │   │   └── WorkManagerScheduler.kt  # Enqueues/cancels periodic step-sync work
│   │   │   │
│   │   │   └── worker/
│   │   │       └── StepSyncWorker.kt    # Periodic worker — reads HC steps, writes to Firestore
│   │   │
│   │   ├── res/
│   │   │   ├── drawable/                # Adaptive icon foreground/background
│   │   │   ├── font/                    # Outfit TTF weights (Light, Regular, Medium, SemiBold, Bold)
│   │   │   ├── mipmap-*/               # Launcher icons (all densities)
│   │   │   ├── values/                  # strings.xml, colors.xml, themes.xml
│   │   │   └── xml/                     # Backup rules, data extraction rules
│   │   │
│   │   └── AndroidManifest.xml
│   │
│   ├── build.gradle.kts                 # App-level build config (signing, minify, dependencies)
│   ├── proguard-rules.pro               # R8/ProGuard keep rules for Firebase, Hilt, HC, etc.
│   └── google-services.json             # Firebase project config
│
├── build.gradle.kts                     # Root-level plugins
├── settings.gradle.kts                  # Project name "Pamoja", single :app module
├── gradle/
│   └── libs.versions.toml              # Version catalog (AGP 8.13.2, Kotlin 2.0.21, Compose BOM 2025.05.00, etc.)
├── gradle.properties
├── gradlew / gradlew.bat
└── keystore.properties                  # Signing credentials (git-ignored)
```

---

## Architecture

The project follows **Clean Architecture** with three distinct layers:

```
┌─────────────────────────────────────────┐
│  UI Layer (Compose screens + ViewModels)│
│  ↕ exposes StateFlow<UiState>           │
├─────────────────────────────────────────┤
│  Domain Layer (Use Cases + Models)      │
│  ↕ pure Kotlin, no Android imports      │
├─────────────────────────────────────────┤
│  Data Layer (Firebase repos + DTOs)     │
│  ↕ Firestore, Health Connect, DataStore │
└─────────────────────────────────────────┘
```

- **Domain layer** defines repository interfaces and use cases with input validation.
- **Data layer** implements those interfaces with Firebase and Health Connect, using DTO ↔ domain mappers.
- **UI layer** uses Hilt-injected ViewModels that expose `StateFlow<UiState>` to Compose screens.
- **Dependency Injection** via Hilt wires everything together across three modules: `AppModule`, `RepositoryModule`, and `WorkerModule`.

---

## Navigation Flow

```
Welcome ──→ ProfileSetup ──→ HealthConnect ──→ Home
   │                                            │
   └──→ SignIn ────────────────────────────→ Home
                                                │
                                       ┌────────┴────────┐
                                       ↓                  ↓
                                  CreateGroup       GroupScreen
                                       │            (leaderboard)
                                       ↓
                                  InviteScreen
                                       │
                                       ↓
                                  GroupScreen
```

- **Welcome** → first-time users go to profile setup; returning users go to sign-in.
- **Home** → lists all groups the user belongs to; provides options to create or join a group.
- **GroupScreen** → real-time leaderboard with today's and weekly step counts, progress ring, admin controls.

---

## Firestore Collections

| Collection | Document ID | Key Fields |
|---|---|---|
| `users` | `{userId}` | `name`, `age`, `height`, `weight`, `deviceToken` |
| `groups` | `{groupId}` | `name`, `adminId`, `weeklyTarget`, `maxMemberCap`, `inviteLink`, `inviteLinkActive` |
| `memberships` | `{userId}_{groupId}` | `userId`, `groupId`, `role` (`admin`/`member`), `canEditTarget` |
| `steps` | `{userId}_{date}` | `userId`, `stepCount`, `date` (ISO format `YYYY-MM-DD`) |

> **Note:** The `steps` collection requires a composite Firestore index on `userId` + `date` for the weekly group query. When first run, check Logcat for the auto-generated index creation link.

---

## Prerequisites

- **Android Studio** Ladybug (2024.3.1) or newer
- **JDK 11+**
- **Android SDK 36** (compile & target SDK)
- **A Firebase project** with:
  - Anonymous Authentication enabled
  - Cloud Firestore database created
  - `google-services.json` placed in `app/`
- **Health Connect** app installed on the test device (built-in on Android 14+)

---

## Getting Started

1. **Clone the repository:**
   ```bash
   git clone https://github.com/ArkayenLabs/pamoja-android.git
   cd pamoja-android
   ```

2. **Configure Firebase:**
   - Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
   - Enable **Anonymous** sign-in under Authentication → Sign-in method
   - Create a **Cloud Firestore** database
   - Download `google-services.json` and place it in the `app/` directory

3. **Set up signing (release builds):**
   Create a `keystore.properties` file in the project root:
   ```properties
   storeFile=path/to/your/keystore.jks
   storePassword=your_store_password
   keyAlias=your_key_alias
   keyPassword=your_key_password
   ```

4. **Build and run:**
   ```bash
   ./gradlew assembleDebug
   ```
   Or open the project in Android Studio and run on a device/emulator with API 26+.

5. **Create the Firestore composite index:**
   On first run, the group steps query will log an index creation URL in Logcat. Click the link to create the required composite index in the Firebase Console.

---

## Build Variants

| Variant | Minify | Shrink Resources | Signing | Notes |
|---|---|---|---|---|
| `debug` | ❌ | ❌ | Debug keystore | Fast builds, readable stack traces |
| `release` | ✅ | ✅ | Release keystore | R8 full mode with custom ProGuard rules |

---

## Key Design Decisions

- **Anonymous auth over email/password:** Eliminates onboarding friction. Firebase persists the anonymous token across app restarts, so `FirebaseAuth.currentUser` is the single source of truth for session state — not DataStore.
- **Health Connect over raw sensors:** No foreground service needed. Battery-efficient, works reliably across OEM battery optimizers (Samsung, Xiaomi, OnePlus), and data persists even when the app isn't running.
- **WorkManager for background sync:** Periodic 30-minute sync with network and battery constraints. Uses `KEEP` policy so re-entering the group screen doesn't reset the schedule.
- **Firestore real-time listeners:** Group members and step data use `callbackFlow`-based snapshot listeners so the leaderboard updates live without manual refresh.
- **Dark-mode only:** Premium feel with an indigo accent palette, custom Outfit typography, and near-black backgrounds.

---

## License

*No license file included yet. Please add one before distributing.*
