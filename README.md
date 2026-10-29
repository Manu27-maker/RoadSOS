# RoadSOS Android app

A **standalone** native app built with **Kotlin + Jetpack Compose** (Material 3). It has no
backend, no server URL and no network permission: triage, storage, responder ranking and driver
geofencing all run on the phone. Contact alerts and driver pushes are **rendered on the phone
but never sent** — there is no SMS, WhatsApp, email or push integration in this build. The React
dashboard in `../frontend` is a separate product and is not linked to this app in any way.

**Scope — location, not open routing.** This app solves **where** the crash is: exact GPS
coordinates, a map link and a nearest-landmark label. It deliberately does **not** solve open
routing: there is no live traffic, no road-closure feed and no route-aware dispatch. Responder
units are ranked by straight-line distance with a flat ETA estimate.

```
android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/wrapper/
├── local.properties          # generated locally, git-ignored
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml              # no INTERNET permission
        ├── java/com/roadsos/app/
        │   ├── MainActivity.kt
        │   ├── MainViewModel.kt             # draft, engine calls, contacts, location
        │   ├── data/                        # models, prefs store, error messages
        │   ├── engine/                      # everything below runs on the device
        │   │   ├── LocalEngine.kt           # facade: report → fuse → store → mock alerts
        │   │   ├── LocalDb.kt               # JSON database + seeded demo data
        │   │   ├── NlpEngine.kt             # keyword triage with safety floors
        │   │   ├── VisionEngine.kt          # photo heuristics + EXIF stripping
        │   │   ├── SeverityFusion.kt        # 65/35 channel fusion
        │   │   ├── DriverSim.kt             # geofence, bearings, VMS board
        │   │   ├── OfflineGeocoder.kt       # landmark table, no network
        │   │   └── GeoMath.kt               # haversine / bearing / destination
        │   ├── ui/                          # Compose theme + shared components
        │   │   └── screens/                 # Report, Drive, Contacts, Setup
        │   └── util/                        # location + geo helpers
        └── res/                             # theme, icons, FileProvider paths
```

## What it does

| Tab | Features |
|---|---|
| **Report** | 4-step wizard: GPS/demo location → voice or typed description + scene tags → camera/gallery photo analysed on-device → review + consent → submit. Success screen shows the fused severity, hazards, suggested units, contact-delivery summary, **I'm safe** and **Call 112**. |
| **Drive** | Lists active incidents from the local database, shows the VMS board and geofence stats, adjusts the alert radius (2–5 km), and broadcasts a simulated driver alert. |
| **Contacts** | Up to 5 emergency contacts stored on-device (SharedPreferences), add/edit/delete with confirmation, channel selection (SMS/WhatsApp/email), simulated test alert. |
| **Setup** | On-device engine status (incidents, active count, photos, storage), reporter profile, runtime permissions, device ID, reset demo data / demo contacts. |

A persistent **112** button is in the top bar on every screen.

## What is real vs simulated (say this on stage)

| Area | On this Android app |
|---|---|
| Triage, fusion, storage, geocoding, ranking, geofence | **Real, on-device.** No server call, no `INTERNET` permission. |
| Contact alerts | **Simulated.** One rendered message per contact with their channel label, shown as `3/3 alerted · mock delivery`. No SMS, WhatsApp, email or push is sent. |
| Driver alerts / VMS / "broadcast" | **Simulated** on the phone (deterministic drivers, radius + bearing). No FCM and no connected cars. |
| Responder "dispatch" | The nearest 3 seeded units ranked by **straight-line distance** with a flat ETA. **No live routing, traffic or road-closure data, and the ranking is not route-aware.** Real dispatch belongs to the web product. |
| Lifecycle strip | Reports filed here advance on a **45 s demo clock** per stage. Seeded incidents keep their curated statuses. |
| Reverse geocoding | **12 landmarks** within 6 km of NH-44 / ORR; outside that it falls back to a coordinate label. Demo scale, not production coverage. |
| Vehicle type / colour / count | **Not detected.** Photo triage is colour/edge heuristic CV, not object detection. `vehiclesCount` is always **0**; the *Multiple vehicles* tag feeds the text rules, not vision. |

This is **not** the React/Flask dashboard in `../frontend` + `../backend`. The two products share
only the demo scenario; the Android app has no server URL and no network code.

## Requirements

- Android Studio (Ladybug or newer) with JDK 17+ (the project is built with JDK 21)
- **Gradle JVM: JDK 17–21.** Gradle 8.11.1 does not support JDK 24/25. Android Studio 2026
  defaults to its bundled JDK 25, so select a JDK 21 explicitly (see troubleshooting below).
- Android SDK Platform 35 and Build-Tools 35+ (`compileSdk = 35`, `minSdk = 26`)
- A device or emulator running Android 8.0+
- No backend, no API key, no internet connection

## 1. Open and run in Android Studio

1. **File → Open…** and select this `android/` folder (not the repository root).
2. Wait for Gradle sync. Android Studio creates `local.properties` with your SDK path.
3. Pick an emulator or a connected phone in the device dropdown.
4. Press **Run ▶** (`app` configuration is created automatically).

Command line alternative:

```bash
cd android
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.roadsos.app/.MainActivity
```

## 2. Release build

```bash
cd android
./gradlew assembleRelease      # Windows: gradlew.bat assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

The release variant is minified and resource-shrunk with R8, which strips the unused
Material icon glyphs: the APK goes from ~16 MB (debug) to ~1.2 MB. For the demo it is
signed with the local debug keystore (see `app/build.gradle.kts`), so it installs on a
phone without any keystore setup — replace that `signingConfig` before publishing anywhere.

Gson reads and writes the on-device JSON database by reflection, so `app/proguard-rules.pro`
keeps the model field names. If a release build ever shows empty incidents or contacts, the
rules are the first thing to check.

## 3. Where the work happens

Nothing is uploaded, so there is no "connection" step. The pipeline is:

| Stage | Implementation | Notes |
|---|---|---|
| Reverse geocode | `OfflineGeocoder` | 12-entry NH-44 / ORR landmark table; falls back to a coordinate label |
| Text triage | `NlpEngine` | 23 weighted keyword rules, clause-scoped negation, injury estimate, safety floors (trapped → ≥55, trapped + fire → ≥80, explosion → ≥85) |
| Photo triage | `VisionEngine` | Luminance, dark ratio, red ratio, saturation, Laplacian edge density → 0–100 score with honest confidence; decoded to ≤1600 px; reporter tags override. **No object detection**: vehicle type/colour and exact counts are not identified. |
| Fusion | `SeverityFusion` | 65% stronger channel + 35% weaker, +5 corroboration, −8 low-confidence penalty, High floor for fire/rollover/entrapment from **either** channel; output clamped to 0–100 |
| Storage | `LocalDb` | `filesDir/roadsos/roadsos.json` (atomic write-then-rename) + `filesDir/roadsos/photos/` (re-encoded, EXIF/GPS stripped) |
| Contacts | `Store` | `SharedPreferences`, max 5, three seeded on first launch |
| Responder ranking | `LocalEngine` | 3 seeded units ranked by haversine distance, ETA at 38 km/h + 2 min mobilisation |
| Driver geofence | `DriverSim` | Deterministic drivers from the incident id, 3 km default radius, ±65° bearing check |
| Alerts | `LocalEngine` | One rendered message per contact (name, channel label, status) + `sent/total`; delivery is simulated, nothing is sent |
| Lifecycle | `LocalEngine` | Reports filed on the phone step Reported → Dispatched → On Scene → Resolved on a 45 s demo clock; seeded incidents keep curated statuses |

Seeded on first launch: 3 incidents (Electronic City crash, Silk Board pile-up, Bommanahalli
skid), 3 responder units and 3 contacts, so the Drive tab is never empty during a demo.

## 4. Demo tips

- **Report** → *Demo location (Silk Board)* works with no permission and always resolves to
  "Near Silk Board Junction, NH-44 / Outer Ring Road".
- To make the emulator report a real Bengaluru fix, set its GPS:

  ```bash
  adb emu geo fix 77.6234 12.9172
  ```

- The three seeded incidents make **Drive** demo-ready immediately: VMS board, in-geofence
  count and the broadcast button.
- **Setup → Reset demo data** restores the three sample incidents and clears reports you filed.

## Permissions

Requested contextually, never at first launch:

- **Location** — only when you tap *Use my GPS*; falls back to the last known fix.
- **Camera** — only when you tap *Take photo*; the gallery works without it.

That is the complete permission list. The app does **not** request `INTERNET`, so nothing can be
uploaded, and it does **not** request `RECORD_AUDIO`: voice descriptions use the keyboard's
system dictation / speech recogniser, which is owned by the keyboard app, not RoadSOS.

The demo location button always works without any permission, which keeps the pitch reliable
indoors.

## Data & privacy

- Reports and photos never leave the device; there is no sync, account or telemetry.
- Photos are decoded, rotated by EXIF, then re-encoded as a clean JPEG, so EXIF/GPS metadata
  is dropped before the image is stored. The camera/gallery **original** is deleted from the
  app cache as soon as analysis succeeds — only the sanitised copy in `filesDir/roadsos/photos/`
  survives.
- Contacts live in `SharedPreferences`, reports in `filesDir/roadsos/roadsos.json`; both are
  removed when the app is uninstalled or *Reset demo data* is tapped.
- Consent is required before a report can be filed, and the 112 button is always one tap away.
- Alerts are queued inside the app for display only. With no `INTERNET` permission and no SMS
  or email code path, a contact message cannot leave the phone.

## Known limitations (be explicit if judges ask)

| Limitation | What it means in this build |
|---|---|
| Contact alerts are mock | Each contact gets a rendered message and a `Queued (mock)` status, but no SMS/WhatsApp/email/push is sent. A production path needs Twilio/WhatsApp Cloud API + consent + delivery receipts. |
| Driver pushes are simulated | Drivers are generated deterministically from the incident id; the radius and ±65° bearing filter are real geometry, but there is no FCM, no connected-car SDK and no real vehicle. |
| No live routing or ambulance dispatch | The three responder units are seeded. Ranking uses real haversine distance with a flat 38 km/h + 2 min mobilisation ETA — **not route-aware**, and there is no traffic or road-closure data. No road network or dispatch service is contacted. |
| Geocoder is demo-scale | `OfflineGeocoder` knows **12 landmarks within 6 km of NH-44 / ORR**. Outside that radius it shows a coordinate label instead of a street address. Not production coverage. |
| Triage is rules + heuristics | 23 English keyword rules with clause-scoped negation, and a luminance/colour/edge CV score. It is explainable and offline, not a trained crash model, and it is English-only. |
| No object detection | The photo engine can hint at fire/smoke, low light and a busy/blocked scene, but it cannot identify vehicle types, colours or reliable counts. `vehiclesCount` is always 0; *Multiple vehicles* is a reporter tag handled by the text rules. Do not present it as detection. |
| Lifecycle is a demo clock | Reports filed on this phone advance every **45 s** per stage so the strip moves during a pitch. Seeded incidents keep their curated statuses. |
| Local-only, single device | Max 5 contacts, one JSON database, no multi-device sync, no responder login. |
| Demo signing | The release APK is signed with the local debug keystore so it installs on any test phone. Replace the signing config before publishing. |

## Verification

The triage and geometry logic has JVM unit tests (16 cases) covering the safety floors, the
0–100 clamp, the documented fusion example, clause-scoped negation and bearing wrap-around:

```bash
cd android
./gradlew testDebugUnitTest        # Windows: gradlew.bat testDebugUnitTest
```

`GeoMathTest` caught a real wrap-around bug — `bearingDelta(10°, 350°)` returned 340° instead
of 20° — which would have hidden drivers approaching from the other side of north.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Incompatible Gradle JVM version` / "Gradle 8.11.1 supports Java 1.8–23" | Android Studio selected its bundled **JDK 25**. Go to **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** and choose **`temurin-21`** (or *Add JDK…* and pick `C:\Program Files\Eclipse Adoptium\jdk-21...`). Click **Apply**, then **File → Sync Project with Gradle Files**. The project also ships `android/.idea/gradle.xml` with `gradleJvm=temurin-21`; close and reopen the project if the IDE still shows JDK 25. |
| CLI build uses the wrong Java | Set `JAVA_HOME` to a JDK 21 before running Gradle, or add `org.gradle.java.home=C:/path/to/jdk-21` to `android/gradle.properties`. Check with `.\gradlew.bat -version`. |
| Gradle sync cannot download 8.11.1 | If `services.gradle.org` is blocked, point Android Studio to a local Gradle 8.11.1 (**Settings → Build Tools → Gradle → Use Gradle from: local installation**). |
| `SDK location not found` | Open the `android/` folder in Android Studio once, or create `local.properties` with `sdk.dir=...`. |
| Camera app missing | Use **Choose from gallery**; the emulator has no camera app by default. |
| No GPS fix indoors | Use *Demo location (Silk Board)*, or `adb emu geo fix 77.6234 12.9172`. |
| Photo looks dark / low confidence | Expected: the heuristic caps confidence at 0.62 for low-light images. Confirm scene tags in step 2 to override. |
