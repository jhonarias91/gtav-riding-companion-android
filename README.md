**EN** | [ES](README_ES.md)

# GTAV Riding Companion for Android

A native Android companion for the [GTBike V](https://www.gtbikev.com/) cycling mod for GTA V. While you ride, the phone sits on the handlebars. From it you can watch live metrics, pick routes, adjust your profile and ride settings, choose which sensors the PC uses, select and follow workouts and download your saved activities. Everything goes through the PC bridge on your home Wi-Fi.

The PC side (Node.js bridge and GTA V adapter) is in a separate repository: **[gtav-riding-companion-bridge](https://github.com/jhonarias91/gtav-riding-companion-bridge)**. This app needs it to talk to the game; set it up first by following [its README](https://github.com/jhonarias91/gtav-riding-companion-bridge#readme).

> **Coming next:** the app will be published for Android, so you won't need to build it yourself, and the bridge will ship as a Windows executable (`.exe`), so you won't need Node.js. Until then, build the app as described below.

> Unofficial community project. Not affiliated with GTBike V, Rockstar Games, Zwift or Garmin.

## Features

| Area | What the app does |
| --- | --- |
| **Ride** | Power with zones, speed, cadence, heart rate, distance, time and elevation. Missing or stale data is shown as missing, never as a false zero. Includes the mod on/off switch and a calibrated game map. |
| **Routes** | Search the PC's route catalog, preview a route on the PC, and apply it (loop or reverse where the route allows) or switch to free ride. |
| **Training** | Workout library, **Select and start** a workout as the mod's Select button does (the mod puts the trainer in ERG; a running workout is replaced after a warning), and live progress. Pause, skip and end stay on the PC. |
| **PC sensors** | For each function (power, trainer control, cadence, heart rate, steering, controls), see the source the PC uses and set **Auto**, **Lock** or **Skip** like the mod's Device Selection. |
| **Find sensors with the phone** | Lists nearby Bluetooth sensors that GTBike V can use and sends your choice to the PC. The PC locks that sensor and connects to it directly. The phone only listens to advertisements and never connects, so riding data does not pass through the phone. |
| **Activities** | Save the current ride like the mod's Save item; browse FIT files saved on the PC with summary, power chart with real gaps, download and share. |
| **Riders** | Leaderboard of the current course, read only (when the bridge runs with `--enable-riders`). |
| **PC settings** | Weight, FTP, max heart rate, trainer difficulty, road feel, autopilot, obstacle avoidance, bike and character. |
| **Help everywhere** | Every important option has a **?** button with an offline explanation. |
| **More** | English and Spanish, light/dark theme, metric or imperial units, connection diagnostics (no tokens or addresses in the copied report), and a demo mode that needs no PC. |

Manual ERG control, target power and workout pause, skip or end are intentionally not part of the app.

## Requirements

- Phone: Android 8.0 (API 26) or later. Scanning the connection QR uses Google Play services; without them, type the address, port and key fingerprint by hand. Bluetooth Low Energy is optional and only needed to find sensors with the phone.
- PC: the [GTAV Riding Companion bridge](https://github.com/jhonarias91/gtav-riding-companion-bridge) v1 running (`node bridge/start-v1.mjs`), GTA V with GTBike V **0.8.0.4** and the companion adapter installed.
- Both devices on the same local network. The PC may be on Ethernet.
- To build: **JDK 17+** and the Android SDK with platform 37 (Android Studio provides both). The Gradle wrapper downloads Gradle 9.6.

## Quick start

1. Set up and start the PC bridge, following the [bridge README](https://github.com/jhonarias91/gtav-riding-companion-bridge#quick-start). From the bridge folder:

   ```powershell
   node bridge/start-v1.mjs
   ```

2. Build and install the debug app on a phone connected by USB, with USB debugging enabled. From this repository:

   ```powershell
   .\gradlew.bat :app:installDebug
   ```

   On macOS or Linux, use `./gradlew :app:installDebug`.

3. Open the app and, on **Connect to your PC**, tap **Scan QR code** and point the camera at the bridge window. The QR carries the address, port, key fingerprint, bridge identity and a one-time pairing key, so the phone verifies the bridge and pairs by itself. Each QR pairs one phone; press Enter in the bridge window to show a new one.

   Without the QR scanner, type the address and port shown in the console, the **Bridge key fingerprint (HTTPS)**, and the six-digit pairing code (press Enter in the console to see the current code).

4. Start GTA V with GTBike V. The **Ride** screen shows each layer separately (bridge, game adapter, mod, ANT+/Bluetooth receivers), so you can see exactly what is missing.

Pairing lasts 30 days. When it expires, the app asks you to scan the QR again.

To explore the app without a PC, tap **Explore with demo data** on the connect screen. Demo data is always labelled as such.

> **Transport.** The bridge serves HTTPS by default and the app pins its public key, rejecting any other certificate. Plain HTTP (`http-dev`) is only for development: the bridge must be started with `--http-dev`, and only debug builds of the app accept it (`app/src/debug/res/xml/network_security_config.xml`). Release builds refuse cleartext and report `TLS_REQUIRED`.

## Permissions

| Permission | Why |
| --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Reach the PC bridge on the LAN and explain disconnections |
| `BLUETOOTH_SCAN` (Android 12+, `neverForLocation`) | Only on **Find sensors with the phone**, to list nearby sensors |
| `ACCESS_FINE_LOCATION`, `BLUETOOTH`, `BLUETOOTH_ADMIN` (Android 11 and earlier only) | Android requires these for any Bluetooth scan on those versions. The app never reads your location. |

The app never requests `BLUETOOTH_CONNECT` and never connects to sensors. The QR scanner is the Google Play services code scanner, so the app does not request the `CAMERA` permission.

## Build and test

```powershell
.\gradlew.bat :app:assembleDebug                                 # debug APK in app\build\outputs\apk\debug
.\gradlew.bat :core:test :bridge-client:test :app:testDebugUnitTest   # JVM tests
.\gradlew.bat :app:lintDebug                                     # Android lint
.\gradlew.bat :app:connectedDebugAndroidTest                     # UI tests on a connected device or emulator
```

`testDebugUnitTest` alone does not run the pure Kotlin modules; include `:core:test` and `:bridge-client:test` as above.

The bridge-client tests encode commands and compare them with the JSON fixtures of the bridge contract. They expect [gtav-riding-companion-bridge](https://github.com/jhonarias91/gtav-riding-companion-bridge) to be cloned next to this repository (`../gtav-riding-companion-bridge`). To use another location, pass `-PbridgeRepo=PATH`.

### Release signing

Release builds are signed only when `keystore.properties` exists in the repository root. Never commit it; it is ignored by `.gitignore`.

```properties
storeFile=..\\keys\\gtbike-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

```powershell
.\gradlew.bat :app:assembleRelease
```

### Help texts

Help copy (English and Spanish) is generated from a contextual help catalog that is kept outside this repository (`../planning/android/AYUDA_CONTEXTUAL.md`). The generated files are committed, so you only need the catalog to change the help. After editing it, run:

```powershell
node tools/generate-help-resources.mjs
```

This rewrites `values*/strings_help.xml` and `ui/help/HelpTopic.kt`.

## Project structure

| Module | Contents |
| --- | --- |
| `core` | Pure Kotlin domain: models mirroring bridge contract v1, rules (mod activation, control blocking, power zones), the Bluetooth sensor classifier, demo repositories. No Android dependencies. |
| `bridge-client` | OkHttp + kotlinx.serialization client for bridge v1: QR and code pairing, polling, commands and operations, HTTPS with a pinned public key. |
| `app` | Jetpack Compose UI (Material 3), Hilt, Navigation, DataStore preferences, Bluetooth scanner. |

Stack: Kotlin 2.2, AGP 9.5, Compose BOM 2026.09, Hilt 2.60, OkHttp 5.5, kotlinx.serialization 1.9. `minSdk` 26, `targetSdk` 36, `compileSdk` 37.

## Design notes

- **The PC is the source of truth.** The app sends typed commands, then waits until the bridge reports the change as observed (`applied`). A lost connection never re-sends a command.
- **Capabilities drive the UI.** Each control is enabled from `GET /api/v1/capabilities`. An unavailable control shows the reason (mod off, game paused, unsupported mod version…) and its help still opens.
- **No fake data.** Values that are missing, stale or not captured are shown as such.

## Roadmap

- **Published Android app.** Install it directly instead of building it from source.
- **Windows executable (`.exe`) for the bridge.** Run the PC side without installing Node.js. See the [bridge repository](https://github.com/jhonarias91/gtav-riding-companion-bridge).

## License

No license has been chosen yet. Until a `LICENSE` file is added, all rights are reserved.
