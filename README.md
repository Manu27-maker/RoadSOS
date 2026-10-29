# RoadSOS Android app

A **standalone** native app built with **Kotlin + Jetpack Compose** (Material 3). It has no
backend and no server URL: triage, storage, responder ranking and driver geofencing all run on
the phone. Contact alerts and driver pushes are **rendered on the phone but never sent** — there
is no SMS, WhatsApp, email or push integration in this build. The React dashboard in
`../frontend` is a separate product and is not linked to this app in any way.

**Online when the internet works, offline when it doesn't.** Every feature runs without a
network, and the route falls back to straight-line distance + a flat ETA. Setup's
**“Online mode: live routing”** switch is **on by default in auto mode**. With a validated
internet connection, RoadSOS sends only the crash coordinates and the nearest unit's base
coordinates to Google Directions when a key is configured (traffic-aware ETA), or to the OSRM
public demo server otherwise (real road route, no traffic data). With no internet, or with the
switch forced off, no request is made and the route card labels the offline estimate.

**Cloud photo analysis is automatic too.** With internet and a Groq key, the sanitized photo
(EXIF/GPS already stripped) is sent to a vision model that returns vehicle count, type, colour,
a readable number plate, hazards and severity. The on-device metric analysis always runs first
and is shown when cloud vision is off, unconfigured or unreachable. No fake vehicle details are
ever invented.

**Scope — location first, not route-aware dispatch.** This app solves **where** the crash is:
exact GPS coordinates, a map link and a nearest-landmark label. Responder units are still ranked
by straight-line distance; the route card adds real road geometry/ETA for the nearest unit, but
there is **no road-closure feed and the ranking is not route-aware**.

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
        ├── AndroidManifest.xml              # INTERNET for online routing + cloud vision
        ├── java/com/roadsos/app/
        │   ├── MainActivity.kt
        │   ├── MainViewModel.kt             # draft, engine calls, contacts, location
        │   ├── data/                        # models, prefs store, error messages
        │   ├── engine/                      # everything below runs on the device
        │   │   ├── LocalEngine.kt           # facade: report → fuse → store → mock alerts
        │   │   ├── LocalDb.kt               # JSON database + seeded demo data
        │   │   ├── NlpEngine.kt             # keyword triage with safety floors
        │   │   ├── VisionEngine.kt          # on-device metrics + EXIF stripping
        │   │   ├── CloudVision.kt           # optional Groq vision request
        │   │   ├── VisionJson.kt            # strict, testable model-output parser
        │   │   ├── SeverityFusion.kt        # 65/35 channel fusion
        │   │   ├── DriverSim.kt             # geofence, bearings, VMS board
        │   │   ├── OfflineGeocoder.kt       # landmark table, no network
        │   │   ├── RoutingEngine.kt         # Google Directions / OSRM + offline fallback
        │   │   └── GeoMath.kt               # haversine / bearing / destination
        │   ├── ui/                          # Compose theme + shared components
        │   │   └── screens/                 # Report, Drive, Contacts, Setup
        │   └── util/                        # location + geo helpers
        └── res/                             # theme, icons, FileProvider paths
```

## What it does

| Tab | Features |
|---|---|
| **Report** | 4-step wizard: GPS/demo location → voice or typed description + scene tags → camera/gallery photo analysed on-device (plus cloud vision when online) → review + consent → submit. Success screen shows the fused severity, hazards, suggested units, contact-delivery summary, **I'm safe** and **Call 112**. |
| **Drive** | Lists active incidents from the local database, shows the VMS board and geofence stats, adjusts the alert radius (2–5 km), and broadcasts a simulated driver alert. |
| **Contacts** | Up to 5 emergency contacts stored on-device (SharedPreferences), add/edit/delete with confirmation, channel selection (SMS/WhatsApp/email), simulated test alert. |
| **Setup** | On-device engine status, **Online mode: live routing** switch (auto by default; force-off available), **Online vision** switch + runtime Groq key, reporter profile, runtime permissions, device ID, reset demo data / demo contacts. |

A persistent **112** button is in the top bar on every screen.

## What is real vs simulated (say this on stage)

| Area | On this Android app |
|---|---|
| Triage, fusion, storage, geocoding, ranking, geofence | **Real, on-device.** No server call for these paths. |
| Online routing (automatic) | **Real road data** when the phone has internet. With a key: Google Directions, `departure_time=now`, traffic-aware `duration_in_traffic`. Without a key: OSRM public demo, real road geometry/time but no traffic. Only coordinates leave the phone. No internet or provider failure → labelled offline estimate. The switch can force offline. |
| Cloud photo analysis (automatic) | **Real model output** when the phone has internet and a Groq key. Returns vehicle count, type, colour, readable plate, hazards and severity. Only the sanitized JPEG (EXIF/GPS removed) is sent. Offline / no key / provider failure → on-device metric result with the reason shown. |
| Contact alerts | **Simulated.** One rendered message per contact with their channel label, shown as `3/3 alerted · mock delivery`. No SMS, WhatsApp, email or push is sent. |
| Driver alerts / VMS / "broadcast" | **Simulated** on the phone (deterministic drivers, radius + bearing). No FCM and no connected cars. |
| Responder "dispatch" | The nearest 3 seeded units ranked by **straight-line distance** with a flat ETA. The route card adds Google geometry/traffic ETA for the nearest unit, but the ranking itself is **not route-aware** and there is **no road-closure feed**. Real dispatch belongs to the web product. |
| Lifecycle strip | Reports filed here advance on a **45 s demo clock** per stage. Seeded incidents keep their curated statuses. |
| Reverse geocoding | **12 landmarks** within 6 km of NH-44 / ORR; outside that it falls back to a coordinate label. Demo scale, not production coverage. |
| Vehicle type / colour / count | **Cloud vision only.** A vision model returns them when online with a key; the card labels the source. Offline, the on-device heuristic **does not detect objects** and reports no vehicle details. |

This is **not** the React/Flask dashboard in `../frontend` + `../backend`. The two products share
only the demo scenario; the Android app has no server URL and no backend code. Its network paths
are the online route request (coordinates only) and the optional cloud-vision request (sanitized
JPEG only).

## Requirements

- Android Studio (Ladybug or newer) with JDK 17+ (the project is built with JDK 21)
- **Gradle JVM: JDK 17–21.** Gradle 8.11.1 does not support JDK 24/25. Android Studio 2026
  defaults to its bundled JDK 25, so select a JDK 21 explicitly (see troubleshooting below).
- Android SDK Platform 35 and Build-Tools 35+ (`compileSdk = 35`, `minSdk = 26`)
- A device or emulator running Android 8.0+
- No backend and no API key is required for offline use; a Google Directions key is **optional**
  and only enables live routing when the phone is online

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

## 2b. Online live routing (automatic by default)

The switch lives in **Setup → Online mode: live routing** and is **on by default in auto mode**.
On each report the app checks for a validated internet connection:

- **Internet + Directions key:** request Google Directions and show the live route + traffic ETA.
- **Internet, no key:** request OSRM's public demo server for a real road route + driving time.
  OSRM has **no traffic data**, so the card is labelled `ROAD ROUTE`, not `LIVE TRAFFIC`.
- **Google fails (quota/timeout/non-OK):** try OSRM before giving up.
- **No internet:** skip the request and show the labelled offline estimate immediately.
- **Everything fails:** fall back to the offline estimate with the reason shown.
- **Switch forced off:** never open a socket, regardless of connectivity.

No fake route is drawn. The `engine` field (`google-directions`, `osrm`, `offline-estimate`)
and the card badge always say which source produced the numbers.

To use traffic-aware routing, add the key to the **untracked** `android/local.properties`:

```properties
GOOGLE_DIRECTIONS_API_KEY=your-key-here
```

or set the environment variable `GOOGLE_DIRECTIONS_API_KEY` before building. The Gradle file
injects it as `BuildConfig.DIRECTIONS_API_KEY`; it is never committed. Rebuild after changing it:

```bash
cd android
./gradlew assembleDebug          # or assembleRelease
```

What the app requests when online mode is on, the phone has internet and a key exists:

```
GET https://maps.googleapis.com/maps/api/directions/json
    ?origin=<nearest-unit lat,lng>
    &destination=<crash lat,lng>
    &mode=driving
    &departure_time=now
    &traffic_model=best_guess
    &language=en
    &key=<BuildConfig.DIRECTIONS_API_KEY>
```

The response supplies distance, free-flow duration and (when Google has it)
`duration_in_traffic`; the success screen shows the live ETA, the traffic delay and the route
summary (`via …`).

Without a key (or after a Google failure) the keyless OSRM fallback is used:

```
GET https://router.project-osrm.org/route/v1/driving/<origin lng,lat>;<crash lng,lat>
    ?overview=false&alternatives=false
```

OSRM returns real road distance and driving duration but **no traffic**; the card says so. Only
the same two coordinate pairs are sent. Non-OK statuses, timeouts and quota errors fall through
to `offline-estimate` with the reason shown.

> **Key safety:** an API key compiled into an APK can be extracted. Restrict the key in Google
> Cloud to the Android app (`com.roadsos.app`) and the signing certificate SHA-1, and set a low
> quota. Do not put the key in a public repo or in `BuildConfig` produced from tracked files.

Google Directions needs the **Directions API** enabled on the key. No Maps SDK, Play Services or
billing-dependent map rendering is used, so the app still installs and runs without Google Play
Services.

## 2c. Cloud photo analysis (automatic, key optional)

The switch lives in **Setup → Photo analysis → Online vision: vehicle details** and is **on by
default in auto mode**. The on-device metric analysis always runs; cloud vision is an addition.

On each photo the app checks for a validated internet connection and a key:

- **Internet + key:** send the sanitized JPEG to Groq's OpenAI-compatible vision endpoint and show
  vehicle count, types, colours, a readable number plate, hazards, severity and confidence.
- **No internet / switch off / no key:** show the measured on-device metrics with the reason.
- **Provider error / timeout / unusable JSON:** keep the on-device result and say why.

```
POST https://api.groq.com/openai/v1/chat/completions
Authorization: Bearer <Groq key>
{
  "model": "qwen/qwen3.8-27b",
  "temperature": 0,
  "max_tokens": 700,
  "messages": [ system prompt, user prompt + data:image/jpeg;base64,... ]
}
```

The model is asked for strict JSON: `vehicles_count`, `vehicle_types`, `vehicle_colors`,
`fire_or_smoke`, `overturned_vehicle`, `visible_injuries`, `road_blocked`, `severity`,
`confidence`, `short_summary`, `number_plate`, `low_light`. `VisionJson` validates every field,
clamps the count to 0–20 and confidence to 0–1, normalises aliases (`SUV` → `car`, `lorry` →
`truck`, `gray` → `grey`) and rejects the answer if severity is missing. A rejected answer is
never patched with invented values.

Two ways to provide the key:

1. **Runtime (preferred):** paste it in **Setup → Photo analysis**. It is stored only in this
   app's private `SharedPreferences` and can be removed with one tap.
2. **Build time:** put it in the **untracked** `android/local.properties`:

   ```properties
   GROQ_API_KEY=your-key-here
   GROQ_VISION_MODEL=qwen/qwen3.8-27b
   ```

   or set the same environment variables. Gradle injects them as `BuildConfig.GROQ_API_KEY` and
   `BuildConfig.GROQ_VISION_MODEL`.

> **Key safety:** a key compiled into a public APK can be extracted. Runtime entry is safer for a
> public release; if you embed one, use a dedicated low-quota key and rotate it after the demo.

## 3. Where the work happens

In auto mode the app uploads only two things when the relevant feature is on and the phone is
online: the two coordinate pairs for routing, and the sanitized JPEG for cloud vision. Offline,
nothing is uploaded. The pipeline is:

| Stage | Implementation | Notes |
|---|---|---|
| Reverse geocode | `OfflineGeocoder` | 12-entry NH-44 / ORR landmark table; falls back to a coordinate label |
| Text triage | `NlpEngine` | 23 weighted keyword rules, clause-scoped negation, injury estimate, safety floors (trapped → ≥55, trapped + fire → ≥80, explosion → ≥85) |
| Photo triage | `VisionEngine` + `CloudVision` | On-device luminance, dark ratio, red ratio, saturation and Laplacian edge density → measured metrics + honest confidence (decoded to ≤1600 px, EXIF stripped). When online with a key, `CloudVision` adds vehicle count/type/colour/plate and hazards from a vision model. `VisionJson` rejects malformed answers; reporter tags override. |
| Cloud vision | `CloudVision` | Sanitized JPEG → Groq `chat/completions`; no coordinates, contacts, report text or device ID. Up to 1024 px, 80% JPEG, 15 s connect / 45 s read timeout |
| Photo storage | `VisionEngine` | Re-encoded clean JPEG in app-private storage; original camera/gallery file deleted after success |
| Fusion | `SeverityFusion` | 65% stronger channel + 35% weaker, +5 corroboration, −8 low-confidence penalty, High floor for fire/rollover/entrapment from **either** channel; output clamped to 0–100 |
| Storage | `LocalDb` | `filesDir/roadsos/roadsos.json` (atomic write-then-rename) + `filesDir/roadsos/photos/` (re-encoded, EXIF/GPS stripped) |
| Contacts | `Store` | `SharedPreferences`, max 5, three seeded on first launch |
| Responder ranking | `LocalEngine` | 3 seeded units ranked by haversine distance, ETA at 38 km/h + 2 min mobilisation |
| Optional route | `RoutingEngine` | Nearest available unit → crash. Offline: straight-line + flat ETA. Online with key: Google Directions with `departure_time=now` and traffic-aware duration. Online without key, or after Google failure: OSRM road route with no traffic. Any failure falls back to the offline estimate |
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

Declared in the manifest:

- **`INTERNET` and `ACCESS_NETWORK_STATE`** — normal permissions (no prompt). They exist only for
  online routing and optional cloud vision. With both switches forced **off**, the app never opens
  a socket.
- **Location** — runtime permission, requested only when you tap *Use my GPS*; falls back to the
  last known fix.
- **Camera** — runtime permission, requested only when you tap *Take photo*; the gallery works
  without it.

The app does **not** request `RECORD_AUDIO`: voice descriptions use the keyboard's system
dictation / speech recogniser, which is owned by the keyboard app, not RoadSOS.

The demo location button always works without any permission, which keeps the pitch reliable
indoors.

## Data & privacy

- Every feature works offline, so reports and photos never leave the device when there is no
  internet. There is no sync, account or telemetry.
- In auto mode with internet, routing sends only the crash coordinates and the nearest unit's
  base coordinates, to Google Directions (if a key is configured) or OSRM (keyless fallback).
  Names, contacts, photos, report text and the device ID are not sent. Forcing that switch off
  stops route requests immediately.
- If **Online vision** is on, has internet and a key, the **sanitized JPEG** (EXIF/GPS already
  removed) is sent to Groq. Contacts, report text, coordinates and the device ID are not sent.
  Turning the switch off, or having no key, keeps the photo on the phone.
- A runtime Groq key lives only in this app's private `SharedPreferences`; it is used only as the
  `Authorization` header and can be removed in Setup.
- Photos are decoded, rotated by EXIF, then re-encoded as a clean JPEG, so EXIF/GPS metadata
  is dropped before the image is stored. The camera/gallery **original** is deleted from the
  app cache as soon as analysis succeeds — only the sanitised copy in `filesDir/roadsos/photos/`
  survives.
- Contacts live in `SharedPreferences`, reports in `filesDir/roadsos/roadsos.json`; both are
  removed when the app is uninstalled or *Reset demo data* is tapped.
- Consent is required before a report can be filed, and the 112 button is always one tap away.
- Alerts are queued inside the app for display only. There is no SMS, WhatsApp, email or push
  integration; the only network code paths are `RoutingEngine` and `CloudVision`.

## Known limitations (be explicit if judges ask)

| Limitation | What it means in this build |
|---|---|
| Contact alerts are mock | Each contact gets a rendered message and a `Queued (mock)` status, but no SMS/WhatsApp/email/push is sent. A production path needs Twilio/WhatsApp Cloud API + consent + delivery receipts. |
| Driver pushes are simulated | Drivers are generated deterministically from the incident id; the radius and ±65° bearing filter are real geometry, but there is no FCM, no connected-car SDK and no real vehicle. |
| Online routing is automatic and not route-aware | With internet, Google Directions (when a key exists) supplies a traffic-aware ETA; without a key the OSRM fallback supplies real road geometry/time but **no traffic**. Both cover the **nearest unit only**; the other units stay distance-ranked. Without internet the app falls back to the offline estimate. There is still no road-closure feed, no live incident-aware rerouting, and no guarantee Google traffic reflects an incident that just happened. |
| Online routing needs network (key only for traffic) | With no internet the card labels `OFFLINE ESTIMATE` and uses straight-line distance at 38 km/h + 2 min mobilisation. Without `GOOGLE_DIRECTIONS_API_KEY`, the keyless OSRM demo server still provides a real road route, but the card says `ROAD ROUTE` with no traffic data. A compiled-in key is extractable — restrict it by package + SHA-1. |
| Geocoder is demo-scale | `OfflineGeocoder` knows **12 landmarks within 6 km of NH-44 / ORR**. Outside that radius it shows a coordinate label instead of a street address. Not production coverage. |
| Triage is rules + heuristics | 23 English keyword rules with clause-scoped negation, and a luminance/colour/edge CV score. It is explainable and offline, not a trained crash model, and it is English-only. |
| Cloud vision needs network + key | Without internet, without a Groq key, or if the provider fails, the card shows measured on-device metrics and says why. No vehicle type/colour/count is invented. Model answers are unverified and can be wrong; the reporter's tags override the model flags. |
| Runtime key is extractable from the phone | The key is stored in app-private preferences, not in the APK, but a rooted device can read it. Use a dedicated low-quota key and rotate it after the demo. |
| Offline photo mode has no object detection | The on-device heuristic can hint at fire/smoke, low light and a busy/blocked scene, but it cannot identify vehicle types, colours or counts. Those details require the cloud model. |
| Lifecycle is a demo clock | Reports filed on this phone advance every **45 s** per stage so the strip moves during a pitch. Seeded incidents keep their curated statuses. |
| Local-only, single device | Max 5 contacts, one JSON database, no multi-device sync, no responder login. |
| Demo signing | The release APK is signed with the local debug keystore so it installs on any test phone. Replace the signing config before publishing. |

## Verification

The triage, vision parsing, geometry and routing logic has JVM unit tests (29 cases) covering the
safety floors, the 0–100 clamp, the documented fusion example, clause-scoped negation, bearing
wrap-around, the Directions + OSRM parsers and fallback rules, and the cloud-vision JSON
validator:

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
| Photo shows no vehicle details | Cloud vision needs internet **and** a Groq key. Open **Setup → Photo analysis**, check the switch is on and the key status is green, then tap *Retry photo analysis*. Offline results intentionally show metric details only. |
