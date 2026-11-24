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

- **Language:** Kotlin 2.0.21, JVM target 11
- **UI:** Jetpack Compose, Material 3, `material-icons-extended`; Compose BOM `2024.09.00`
- **Maps:** Google Maps SDK + `maps-compose` 6.2.1
- **Location:** `play-services-location` 21.3.0 (FusedLocationProviderClient)
- **Persistence:** Room 2.6.1 (KSP) + DataStore Preferences 1.1.1
- **Build:** Gradle 8.13 wrapper, AGP 8.13.2, KSP `2.0.21-1.0.27`
- **SDK:** `compileSdk`/`targetSdk` 36, `minSdk` 31 (Android 12)
- **DI / architecture:** none — no Hilt, no ViewModels. Compose screens call a plain
  `LocationRepository`, and services construct their own instances.

Dependencies are declared in the version catalog `gradle/libs.versions.toml`. Add new libraries
there, not inline in `app/build.gradle.kts`.

There is no CI, no linter config beyond Android Lint, and no meaningful test suite
(`app/src/test` and `app/src/androidTest` contain only the generated examples).

## Commands

Run from the repo root:

```bash
./gradlew assembleDebug          # build the debug APK
./gradlew compileDebugKotlin     # fast type-check
./gradlew installDebug           # install on a connected device/emulator
./gradlew bundleRelease          # release AAB (signed if keystore.properties exists)
./gradlew lint                   # Android Lint
./gradlew test                   # JVM unit tests (template only)
./gradlew connectedAndroidTest   # instrumented tests (template only)
```

Use JDK 17 or newer (JBR 21 is installed on this machine). The Android SDK path comes from
`local.properties`, which is gitignored — never edit or commit it.

### Google Maps API key

`app/build.gradle.kts` applies the Maps secrets plugin, and the manifest injects
`${MAPS_API_KEY}` as `com.google.android.geo.API_KEY`:

- Real key: `MAPS_API_KEY=...` in `local.properties` (gitignored).
- Fallback: `local.defaults.properties` contains `MAPS_API_KEY=YOUR_API_KEY_HERE`, so the build
  succeeds without a key but the map stays blank and the Maps SDK logs an authorization failure.

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
  `ACCESS_BACKGROUND_LOCATION` in a second, separate prompt (Android requires that order).
  Then it gates the UI on the DataStore onboarding flag: `OnboardingScreen` until the flag is
  set, `ParkingScreen` afterwards.
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
  `BluetoothA2dp`/`BluetoothHeadset` connection-state changes, and adapter state changes. On a
  transition to disconnected it looks the MAC address up in Room; if it belongs to a vehicle it
  starts `LocationSaveService` with that vehicle id. Events are debounced per vehicle for 10 s
  (in-memory map, reset when the service is recreated). It calls `stopSelf()` when
  `Permissions.canMonitor` is false and catches `SecurityException` from `startForeground`. Its
  ongoing notification uses the `bluetooth_monitor_channel_v2` channel at `IMPORTANCE_MIN` (silent,
  no status-bar icon, collapsed) and is dismissible; the service keeps running if the user swipes
  it away. The old `bluetooth_monitor_channel` is deleted on startup because Android ignores
  importance changes on an existing channel.
- **`LocationSaveService`** — one-shot foreground service. Refuses to start without location
  permission, requests a single high-accuracy fix, falls back to `lastLocation`, writes a
  `ParkingLocationEntity`, and calls `stopSelf()`.
- **`BootReceiver`** — on `BOOT_COMPLETED`, restarts `BluetoothMonitorService` only if at least
  one vehicle is stored **and** `Permissions.canMonitor` is true.

`ParkingScreen` starts `BluetoothMonitorService` only when the vehicle list is non-empty and every
required permission is granted, and stops it when the last vehicle is removed. Adding the first
vehicle also prompts for `POST_NOTIFICATIONS` on Android 13+; denying it only hides the ongoing
notification, the service keeps running.

### UI

All UI is Compose, split in `ui/`:

- `ParkingScreen.kt` (~980 lines) — full-screen `GoogleMap` with the latest (or a
  history-selected) marker, floating location card (navigate / share / clear), history bottom
  sheet, vehicles bottom sheet, edit dialog, empty state with a manual "save location" button,
  and a permission banner. Re-checks permissions on `RESUMED`.
- `OnboardingScreen.kt` — `HorizontalPager` intro; the final page writes the DataStore flag.
- `ui/theme/` — Material 3 color scheme, typography, `AutoSpotTheme`.

### Data layer

- **`AppDatabase`** — Room, version **3**, database name `parking_database`, singleton via
  `getDatabase(context)`, `exportSchema = true`. Schemas are generated into `app/schemas/` (KSP
  `room.schemaLocation`) and committed. `fallbackToDestructiveMigrationFrom(1, 2)` covers the
  unknown legacy schemas only; from v3 onward every version bump must ship an explicit
  `Migration`, or the app crashes on upgrade.
- **`VehicleEntity`** — `vehicles` table: name, Bluetooth MAC address, Bluetooth display name,
  and a single `isActive` flag.
- **`ParkingLocationEntity`** — `parking_locations` table, foreign key on `vehicleId` with
  `CASCADE` delete and an index on `vehicleId`.
- **DAOs** expose `Flow` queries for UI plus `...Sync` `suspend` variants for services.
- **`LocationRepository`** — the only data facade. Wraps both DAOs and the `settings` DataStore
  (`onboarding_completed` boolean). The active vehicle drives `latestParkingLocation` and
  `allParkingLocations` through `flatMapLatest`.
- Cloud backup / device transfer rules (`res/xml/backup_rules.xml`,
  `res/xml/data_extraction_rules.xml`) include the three database files.

## Conventions

- **Strings:** every user-visible string lives in `res/values/strings.xml` and is read with
  `stringResource` / `getString`. Nine locales exist (`values`, `de`, `es`, `fr`, `it`, `ja`,
  `ko`, `ru`, `zh`) and all currently carry the same 64 strings. When adding or changing a
  string, update **all nine** files; missing translations silently fall back to English. Spanish,
  French and Italian need a `quantity="many"` item in plurals or lint fails.
- **Permissions:** check runtime grants through `util/Permissions`, not raw
  `checkSelfPermission`. Services must stop (`stopSelf`) instead of starting a location-typed
  foreground service without permission.
- **Code identifiers, comments and KDoc:** English. Committed docs: English.
- **State:** composables read `Flow`s with `collectAsState` directly; there is no ViewModel
  layer. Keep it that way unless asked for a refactor.
- **Coroutines:** services own a `CoroutineScope(SupervisorJob() + Dispatchers.IO)` and cancel it
  in `onDestroy`; UI work uses `rememberCoroutineScope`.
- **Versioning:** bump `versionCode` / `versionName` in `app/build.gradle.kts` (`1` / `1.0`
  today) before a store release.
- **Branding assets** live at the repo root: `logo.png`, `logo512.png`, `logo.xcf`,
  `claim.png`, `claim.xcf`, plus `app/src/main/ic_launcher-playstore.png`. `_config.yml` is a
  leftover GitHub Pages theme, not used by the Android build.

## Known issues and traps

Traps and limitations verified in the current tree. Fix them deliberately, not as a side effect.

1. **Static shortcut is package-bound:** `res/xml/shortcuts.xml` hardcodes
   `com.dicren.autospot.NavigateActivity`. If the Android package ever changes, update it there
   too; dynamic shortcuts built by `CreateShortcutActivity` follow the code automatically.
2. **Release builds need `keystore.properties`:** without it they come out unsigned and the store
   rejects them. `autospot-release.keystore` lives at the repo root and is gitignored; never commit
   it or its passwords.
3. **Dependency versions are behind** (AGP 8.13.2, Kotlin 2.0.21, Room 2.6.1). Lint reports newer
   versions as informational warnings; upgrading is a separate task because it can change Compose
   and Room APIs.
4. **No migration tests:** schemas are exported to `app/schemas/`, but `app/src/androidTest` still
   only has the template. A Room `MigrationTestHelper` test is the missing safety net for future
   version bumps.
5. **No Bluetooth on the emulator:** the app is only meaningfully testable on a physical device
   with a paired car. Use `adb logcat -s BluetoothMonitorService LocationSaveService BootReceiver`
   to follow the flow.
6. **Release artifacts** (`app/release/app-release.aab`) and `local.properties` are gitignored;
   do not add them back to version control.

## Working rules for agents

- Do not `git commit`, `git push`, or open PRs unless explicitly asked.
- Never print, copy, or commit `local.properties` (Maps key), `keystore.properties`, or the release
  keystore.
- Keep changes consistent with the existing style: direct Compose + repository, and no new
  frameworks. Leave the `com.dicren.autospot` package as-is; it is the published `applicationId`.
- When architecture, commands, or conventions change, update this file in the same change.
