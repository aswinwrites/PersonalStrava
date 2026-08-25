# Android

## Status: scaffolded, not build-verified

This sandbox has no Android SDK and no network route to `dl.google.com` (the
Google Maven repository that hosts the Android Gradle Plugin, AndroidX, and
Health Connect artifacts) — confirmed by attempting `gradle wrapper` here,
which failed to resolve `com.android.application`. So the Kotlin in
`android/` is written carefully and follows current idiomatic patterns, but
**has not been compiled**. The first thing to do on your machine:

```bash
cd android
# If you don't already have a local Gradle install:
gradle wrapper --gradle-version 8.9   # generates gradlew/gradlew.bat + the wrapper jar
# Or just open the android/ folder in Android Studio — it does this for you.

cp local.properties.example local.properties
# then fill in sdk.dir (Android Studio does this automatically) and
# SUPABASE_URL / SUPABASE_ANON_KEY

./gradlew assembleDebug
```

If anything fails to compile, it's most likely one of: the exact
`supabase-kt` package names (`io.github.jan.supabase.*` — this library's
Maven coordinates and Kotlin package namespace have both changed over
versions; check https://github.com/supabase-community/supabase-kt for the
version current when you build), or the Health Connect `connect-client`
artifact version (`1.1.0-alpha07` was the latest stable-track alpha at
writing time — check for a newer release). Everything under `domain/`
(`GpsProcessor`, `StatsAggregator`) has no Android or third-party
dependency at all, so if the project fails to configure, that package is
the one place you can `kotlinc` or run through a plain Kotlin script to
sanity-check independent of the rest.

## Running the tests that *are* verified

`GpsProcessorTest` and `StatsAggregatorTest`
(`android/app/src/test/kotlin/...`) are plain JUnit, framework-free, testing
`GpsProcessor` and `StatsAggregator` respectively:

```bash
./gradlew testDebugUnitTest
```

These cover the calculations spec section 44 calls out explicitly: GPS
distance (Haversine, checked against a known 1°-of-latitude distance),
moving time (threshold-based, checked against a synthetic stationary vs.
moving segment), elevation gain/loss (checked against a synthetic profile
with sub-noise-floor and real changes mixed), daily step aggregation,
monthly aggregation, and period-over-period percent change.

## APK build & signing

The `release` build type in `app/build.gradle.kts` is signed with Gradle's
default debug keystore (`~/.android/debug.keystore`, auto-created by the
SDK) — deliberately, because this app is sideloaded onto one personal
phone, never distributed through Play (spec section 40: "Do not assume Play
Store signing/distribution is required"). To build and install:

```bash
cd android
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Minification/resource shrinking (`isMinifyEnabled`/`isShrinkResources`) is
on for the release build type; `proguard-rules.pro` has the keep rules
`kotlinx.serialization` and the Supabase/Ktor stack need. If a release
build crashes on launch but a debug build doesn't, check `adb logcat` for a
`ClassNotFoundException`/`NoSuchMethodError` first — that's almost always a
missing keep rule, not a logic bug.

**Switching to a real release keystore later** (e.g. if you ever do want a
signed, updatable APK independent of debug-key churn): generate one with
`keytool -genkey -v -keystore personalstrava.keystore ...`, add a
`keystore.properties` (gitignored — already covered by the root
`.gitignore`), point a new `signingConfigs.release` block at it, and change
`buildTypes.release.signingConfig` to reference it instead of `debug`. No
other file needs to change.

## Permissions

Requested progressively (spec section 39), never all at once on first
launch:

1. On first "Start cycling"/"Start motorcycle" tap: `ACCESS_FINE_LOCATION`,
   with the rationale string `permission_rationale_location`.
2. If the user backgrounds the app mid-permission-flow or Android requires
   it separately (API 29+): `ACCESS_BACKGROUND_LOCATION`, with
   `permission_rationale_background_location`, requested only after fine
   location is already granted (Android refuses to show both in one
   prompt on modern versions anyway).
3. On first Home screen load (to show today's steps): Health Connect's
   `READ_STEPS` permission, with `permission_rationale_health_connect`.
4. On first recording start (Android 13+): `POST_NOTIFICATIONS`, with
   `permission_rationale_notifications`, since the foreground-service
   notification needs it.

The actual permission-request Compose UI (a `PermissionFlow` orchestrator
tying these four requests to their respective trigger points, with a
settings-deep-link fallback when a permission is permanently denied) is
scoped for Phase 2 — the manifest entries, string resources, and the
`HealthConnectManager.hasPermissions()` check that Phase 2 UI will call are
already in place.

## Known Phase 2 gaps

- `ActivityRecordingService` collects locations via `LocationSampleBus` but
  nothing yet writes them to Room or updates a live-metrics UI — that's the
  `RecordingViewModel` + recording screen.
- No route simplification (Ramer–Douglas–Peucker) before the `route_polyline`
  field is populated — the column and sync payload are ready, the
  simplification step isn't wired in yet.
- No WorkManager periodic jobs scheduled yet (sync retry, Health Connect
  ingestion) — `SyncManager` and `HealthConnectManager` are ready to be
  called by one.
- No crash-recovery for an interrupted in-progress activity (spec section
  46) — the Room row would currently just stay `local` with a truncated
  GPS point set, which is *safe* (no data loss) but doesn't yet resume
  recording or prompt the user.
