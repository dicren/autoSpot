# AGENTS.md

Guidance for coding agents working in this repository. Keep it up to date whenever the
architecture, build commands, or conventions change.

## What this is

An Android app that remembers where you parked: it watches the Bluetooth connection to a
paired car and, on disconnection, saves the current GPS position. The user can then see the
spot on a map, get driving directions, browse the parking history, and share the location.
Everything is local (Room + DataStore); there are no accounts and no backend.

The official project name is **AutoSpot**, used consistently for the Gradle root project, the XML
themes and the user-facing label. The Android package keeps its own spelling:

| Thing | Value |
|---|---|
| Repository | `github.com/dicren/autoSpot` |
| Gradle root project | `AutoSpot` |
| Android package / `applicationId` | `com.dicren.autospot` |
| App label shown to users | `AutoSpot` |
| XML theme names | `Theme.AutoSpot`, `Theme.AutoSpot.Dialog` |

`com.dicren.autospot` is the published `applicationId`: never rename it, since that would create
a different app on the Play Store and orphan existing installs.

## Tech stack

- **Language:** Kotlin 2.4.21 (AGP 9 built-in Kotlin), JVM target 17
- **UI:** Jetpack Compose, Material 3, `material-icons-extended`; Compose BOM `2026.11.01`
- **Maps:** Google Maps SDK + `maps-compose` 9.0.1
- **Location:** `play-services-location` 21.4.0 (FusedLocationProviderClient)
- **Persistence:** Room 2.8.5 (KSP) + DataStore Preferences 1.3.0
- **Build:** Gradle 9.7.1 wrapper, AGP 9.5.2, KSP 2.3.14, `compileSdk` 37.1
- **SDK:** `compileSdk` 37.1, `targetSdk` 37, `minSdk` 31 (Android 12)
- **DI / architecture:** manual DI (`AutoSpotApp` + `di/AppContainer`), one ViewModel per
  screen (`ParkingViewModel` + `StateFlow<ParkingUiState>`) and plain repositories. No Hilt, no
  Koin, no navigation library.

AGP 9 compiles Kotlin itself: **never apply `org.jetbrains.kotlin.android`**. The Compose compiler
plugin (`org.jetbrains.kotlin.plugin.compose`) is still required and its version must match
Kotlin. The root `build.gradle.kts` pins KGP/KSP through `buildscript` classpath because AGP
otherwise ships older versions.

Dependencies are declared in the version catalog `gradle/libs.versions.toml`. Add new libraries
there, not inline in `app/build.gradle.kts`.

Formatting is enforced by **ktlint through Spotless** (`.editorconfig` holds the settings; the
`function-naming` and `class-naming` rules are disabled because test names are backticked
sentences). CI runs on GitHub Actions: `.github/workflows/ci.yml` runs `spotlessCheck`,
`testDebugUnitTest`, `lintDebug`, `lintVitalRelease`, `assembleDebug` and `assembleRelease`.

Release builds enable R8 and resource shrinking. `proguard-rules.pro` keeps Room entities and the
Maps/Play Services bits; verify any R8 change with a release APK on a device, not just
`assembleRelease`.

## Commands

Run from the repo root:

```bash
./gradlew assembleDebug          # build the debug APK
./gradlew compileDebugKotlin     # fast type-check
./gradlew installDebug           # install on a connected device/emulator
./gradlew assembleRelease        # release APK (minified; signed if keystore.properties exists)
./gradlew bundleRelease          # release AAB (signed if keystore.properties exists)
./gradlew lintDebug              # Android Lint
./gradlew lintVitalRelease       # release-only fatal lint checks
./gradlew testDebugUnitTest      # unit + Robolectric + Compose tests
./gradlew spotlessApply          # format Kotlin with ktlint
./gradlew connectedAndroidTest   # instrumented tests (device required)
```

Use JDK 17 or newer (JBR 21 is installed on this machine). The Android SDK path comes from
`local.properties`, which is gitignored — never edit or commit it.

### Google Maps API key

`app/build.gradle.kts` reads `MAPS_API_KEY` from `local.properties` (falling back to
`local.defaults.properties`) and injects it as the `MAPS_API_KEY` manifest placeholder, which the
manifest uses for `com.google.android.geo.API_KEY`:

- Real key: `MAPS_API_KEY=...` in `local.properties` (gitignored).
- Fallback: `local.defaults.properties` contains `MAPS_API_KEY=YOUR_API_KEY_HERE`, so the build
  succeeds without a key but the map stays blank and the Maps SDK logs an authorization failure.

The old secrets Gradle plugin was removed: it did not populate the unit-test manifest under AGP 9.

### Release signing

Copy `keystore.properties.example` to `keystore.properties` (both at the repo root; the real file
is gitignored) and fill in `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. When that
file exists, `app/build.gradle.kts` registers a `release` signing config and release builds are
signed automatically. Without it, release builds are unsigned — only debug builds work.
`storeFile` is resolved relative to the repo root by default (`autospot-release.keystore`).

## Runtime architecture

### Entry points

- **`MainActivity`** — launcher. Requests `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`,
  then `BLUETOOTH_CONNECT` (API 31+); once fine location is granted it requests
  `ACCESS_BACKGROUND_LOCATION` in a second, separate prompt (Android requires that order). It only
  asks on the first creation of the activity, so denying does not re-prompt on rotation. The
  onboarding flag is collected as `Boolean?`: `null` (not read yet) shows an empty themed surface
  instead of flashing the map before onboarding.
- **`NavigateActivity`** — headless (`Theme.NoDisplay`, `excludeFromRecents`) target of the
  "Navigate to my car" shortcuts. Reads an optional `vehicle_id` extra, falls back to the active
  vehicle, queries Room from a `lifecycleScope` coroutine, and fires
  `google.navigation:q=<lat>,<lon>&mode=w` pinned to `com.google.android.apps.maps`. Shows a toast
  and finishes if Maps is missing or there is no saved location.
- **`CreateShortcutActivity`** — handles `android.intent.action.CREATE_SHORTCUT` (launcher's
  "add to home screen"). Shows a vehicle picker and returns a pinned
  `ShortcutInfoCompat` (`navigate_to_<vehicleId>`) pointing at `NavigateActivity`.

### Services

- **`BluetoothMonitorService`** — sticky foreground service (`foregroundServiceType="location"`)
  that registers a runtime receiver for `ACTION_ACL_CONNECTED/DISCONNECTED`,
  `BluetoothA2dp`/`BluetoothHeadset` connection-state changes, and adapter state changes.
  `bluetooth/BluetoothIntents` reduces the broadcast to a `BluetoothEvent`; the service then looks
  the MAC up in Room and, if it belongs to a vehicle, starts `LocationSaveService` with that id.
  Events pass through `DisconnectionDebouncer` (10 s per vehicle, `Mutex` + injected clock), so
  there is no concurrent access to shared state. It calls `stopSelf()` when
  `Permissions.canMonitor` is false and catches `SecurityException` from `startForeground`. Its
  notification is built by `AppNotifications` (`bluetooth_monitor_channel_v2`, `IMPORTANCE_MIN`,
  monochrome `ic_notification`, dismissible). The old `bluetooth_monitor_channel` is deleted on
  startup because Android ignores importance changes on an existing channel.
- **`LocationSaveService`** — one-shot foreground service. Refuses to start without location
  permission, then runs `SaveParkingLocationUseCase`: a fresh high-accuracy fix bounded by a 15 s
  timeout, falling back to `lastLocation` only if it is younger than 5 minutes. Saves are
  serialised with a `Mutex` and each start stops with `stopSelfResult(startId)`. A failed save
  posts a notification instead of failing silently.
- **`BootReceiver`** — on `BOOT_COMPLETED`, restarts `BluetoothMonitorService` only if at least
  one vehicle is stored **and** `Permissions.canMonitor` is true. It logs
  `ForegroundServiceStartNotAllowedException` explicitly: Android 12+ may refuse the start, and
  the monitor then starts the next time the app is opened.

`ParkingScreen` starts `BluetoothMonitorService` only when the vehicle list is non-empty and every
required permission is granted, and stops it when the last vehicle is removed. When monitoring
starts and `POST_NOTIFICATIONS` is missing (Android 13+), the screen asks for it; denying it only
hides the ongoing notification, the service keeps running.

### UI

All UI is Compose, split in `ui/`:

- `ui/parking/ParkingScreen.kt` — orchestrator: full-screen `GoogleMap` with the latest (or
  history-selected) marker, top banners, vehicle card, navigate FAB, location card, bottom sheets,
  confirmation dialogs and snackbars. It reads `ParkingViewModel.uiState` and sends user actions to
  the ViewModel; it also starts/stops the monitor and requests `POST_NOTIFICATIONS`.
- `ui/parking/ParkingViewModel.kt` + `ParkingUiState.kt` — `StateFlow<ParkingUiState>` built from
  the repositories (`combine` + `flatMapLatest`), one-shot `ParkingEvent`s for snackbars, and the
  action methods. Switching the active vehicle clears the selected history item.
- `ui/parking/components/` — stateless components (`VehicleCard`, `Banners`, `LocationCard`,
  `NoLocationCard`, `HistorySheet`, `VehiclesSheet`, `VehicleEditDialog`) that take state and
  callbacks, which is what the Compose tests render.
- `ui/format/ParkingTimeFormatter.kt` — relative time with an injected `now` and a locale-aware
  absolute fallback.
- `OnboardingScreen.kt` — `HorizontalPager` intro; the final page writes the DataStore flag.
- `ui/theme/` — Material 3 color scheme, typography, `AutoSpotTheme`.

The map is edge-to-edge; the cards apply `WindowInsets.safeDrawing` themselves.

### Data layer

- **`AppDatabase`** — Room, version **4**, database name `parking_database`, singleton via
  `getDatabase(context)`, `exportSchema = true`. Schemas are generated into `app/schemas/` (KSP
  `room.schemaLocation`) and committed. `MIGRATION_3_4` adds the unique Bluetooth index and
  repairs duplicates; `fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2)` covers the
  unknown legacy schemas only. From v3 onward every version bump must ship an explicit
  `Migration`, or the app crashes on upgrade.
- **`VehicleEntity`** — `vehicles` table: name, Bluetooth MAC address (**unique index**), Bluetooth
  display name, and a single `isActive` flag. Room cannot express a partial unique index for the
  active flag, so `VehicleDao` keeps the invariant with `@Transaction` methods.
- **`ParkingLocationEntity`** — `parking_locations` table, foreign key on `vehicleId` with
  `CASCADE` delete and an index on `vehicleId`.
- **DAOs** expose `Flow` queries for UI plus `...Once` `suspend` variants for services.
  `activateOnly`, `insertVehicleAndActivate`, `deleteVehicleAndKeepOneActive` and
  `insertAndPrune` are transactional.
- **Repositories** (`VehicleRepository`, `ParkingRepository`, `SettingsRepository`) are built by
  `di/AppContainer`. `addVehicle` returns `Added`/`Duplicate`; deleting the active vehicle
  activates another; the history is capped at 100 rows per vehicle.
- **`SettingsRepository`** wraps the `settings` DataStore and treats an `IOException` (corrupt
  file) as "not onboarded" instead of crashing.
- Cloud backup / device transfer rules (`res/xml/backup_rules.xml`,
  `res/xml/data_extraction_rules.xml`) include the three database files.

## Conventions

- **Strings:** every user-visible string lives in `res/values/strings.xml` and is read with
  `stringResource` / `getString`. Nine locales exist (`values`, `de`, `es`, `fr`, `it`, `ja`,
  `ko`, `ru`, `zh`) and all currently carry the same 75 strings. When adding or changing a
  string, update **all nine** files; missing translations silently fall back to English. Spanish,
  French and Italian need a `quantity="many"` item in plurals or lint fails.
- **Permissions:** check runtime grants through `util/Permissions`, not raw
  `checkSelfPermission`. Services must stop (`stopSelf`) instead of starting a location-typed
  foreground service without permission. Reading `bondedDevices` must be wrapped: it throws
  `SecurityException` without `BLUETOOTH_CONNECT`.
- **Code identifiers, comments and KDoc:** English. Committed docs: English.
- **State:** screens collect a `StateFlow<UiState>` from their ViewModel with
  `collectAsStateWithLifecycle`; ViewModels never hold `Context` (the factory captures the
  application context) and expose actions as methods. Keep one-shot messages in a `SharedFlow`.
- **Coroutines:** ViewModels own UI work through `viewModelScope`; services own a
  `CoroutineScope(SupervisorJob() + Dispatchers.IO)` and cancel it in `onDestroy`. Do not create
  ad-hoc `CoroutineScope`s for database writes. `rememberCoroutineScope` is only for animations.
- **Versioning:** bump `versionCode` / `versionName` in `app/build.gradle.kts` (`2` / `1.1`
  today) before a store release. Release builds are minified by R8.
- **Branding assets** live at the repo root: `logo.png`, `logo512.png`, `logo.xcf`,
  `claim.png`, `claim.xcf`, plus `app/src/main/ic_launcher-playstore.png`. `_config.yml` is a
  leftover GitHub Pages theme, not used by the Android build.

## Known issues and traps

Traps and limitations verified in the current tree. Fix them deliberately, not as a side effect.

1. **The Bluetooth receiver must stay `RECEIVER_EXPORTED`.** The Bluetooth stack is a separate
   system app, not the system UID, so a `RECEIVER_NOT_EXPORTED` receiver never sees
   `ACL_CONNECTED/DISCONNECTED` and the app silently stops saving locations. Verified on Android
   16. These actions are protected broadcasts, so other apps cannot spoof them.
2. **Static shortcut is package-bound:** `res/xml/shortcuts.xml` hardcodes
   `com.dicren.autospot.NavigateActivity`. If the Android package ever changes, update it there
   too; dynamic shortcuts built by `CreateShortcutActivity` follow the code automatically.
2. **Release builds need `keystore.properties`:** without it they come out unsigned and the store
   rejects them. `autospot-release.keystore` lives at the repo root and is gitignored; never commit
   it or its passwords.
3. **targetSdk is 37 (Android 17).** The Android 17 targeting changes (widget memory caps,
   MessageQueue internals, static-final reflection, local-network permission, SMS OTP, audio,
   orientation on large screens) do not apply to this app. Verified on an API 37 emulator:
   map, monitor, bottom sheets, navigation to Maps and landscape.
4. **Migration schemas are test assets:** `app/build.gradle.kts` adds `app/schemas` to the **debug**
   assets so `MigrationTestHelper` can read them under Robolectric. They never ship in release.
   Every schema bump must keep the exported JSON committed, or `MigrationTest` fails.
5. **No Bluetooth on the emulator:** the app is only meaningfully testable on a physical device
   with a paired car. Use `adb logcat -s BluetoothMonitorService LocationSaveService BootReceiver`
   to follow the flow. You can also simulate a disconnection by adding a connected device (watch,
   earbuds) as a vehicle and turning Bluetooth off with `adb shell cmd bluetooth_manager disable`.
6. **Maps needs `org.apache.http.legacy` on old Play Services.** The manifest declares
   `<uses-library android:name="org.apache.http.legacy" android:required="false" />`. Without it,
   the Maps dynamite module in the API 31/33 emulator images (GMS 2023) crashes with
   `NoClassDefFoundError: org/apache/http/ProtocolVersion`. It is optional, so real devices are
   unaffected. For emulator smoke tests prefer `google_apis_playstore` images.
7. **Release artifacts** (`app/release/app-release.aab`) and `local.properties` are gitignored;
   do not add them back to version control.

## Working rules for agents

- Do not `git commit`, `git push`, or open PRs unless explicitly asked.
- Never print, copy, or commit `local.properties` (Maps key), `keystore.properties`, or the release
  keystore.
- Keep changes consistent with the existing style: direct Compose + repository, and no new
  frameworks. Leave the `com.dicren.autospot` package as-is; it is the published `applicationId`.
- When architecture, commands, or conventions change, update this file in the same change.
