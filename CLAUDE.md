# CLAUDE.md

Orientation for AI coding assistants (and a quick read for people). It points at the real documentation; it does not repeat it.

## What this is

**Aura Farm** (formerly Coach Chicken) is an Android app that watches you plank with the phone's camera (on-device MediaPipe Pose),
grades your form, times a 60 s hold, draws you as a gold skeleton in a field, and keeps a streak calendar. The mascot, a rubber chicken, is the coach.
Kotlin, Jetpack Compose, CameraX. Start with [README.md](README.md), then [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Layout

- `android/core`: plain Kotlin/JVM. Rules, maths, file formats. **No Android classes.** Unit tests.
- `android/app`: Compose UI, CameraX, MediaPipe, sensors, notifications, files. Screenshot tests (Paparazzi).
- `tools/`: Python (stdlib) generators for the icon, mascot and sounds. `docs/`: the documentation. `coach.py`: an old Python prototype, unused.

## Commands

```
cd android
gradle :core:test                                         # unit tests, fast
gradle :app:testDebugUnitTest --tests '*ScreenshotTest*'  # render screens to PNG; see docs/DEVELOPMENT.md
gradle :app:assembleRelease -PversionName=0.7.0 -PversionCode=700
```

There is **no Gradle wrapper**: use `gradle` (8.14.3). The app is **arm64 only**, so it cannot run on an x86 emulator.

## Rules of the house

1. **Decisions go in `core` with a test; drawing, playing, scheduling and storing go in `app`.** If a rule is hard to test, it is in the wrong place.
2. **Test first, on real data.** The form checks were tuned on real recordings; those are fixtures (`android/core/src/test/resources`). Write the failing
   test from real rows, then change the limit, then run all of `:core:test`. Put the data you used in the comment on the threshold.
3. **Every new screen or animation gets a screenshot test that renders its first and zero states.** A zero-radius gradient once crashed a phone
   at the end of every plank while all the tests passed. Do not render CameraX; pull the UI into a composable that takes plain state.
4. **Never break old data.** File formats are in [docs/DATA.md](docs/DATA.md). New fields are optional with a default; old rows keep loading; the
   backup zip is a promise to the user.
5. **Do not change the application id (`com.aurafarm`).** It strands everyone's data. The Kotlin package `com.coachchicken` is the old name and
   is not what Android sees; leave it.
6. **Do not edit the `RELEASE` file on a branch**: a change to it on any branch starts a release. Releases are cut from `main` ([docs/RELEASING.md](docs/RELEASING.md)).
7. **Never commit** signing keys or passwords, logs or backups from real people, videos, or anything personal. `.gitignore` excludes `*.csv` except
   the fixtures. Do not paste any of it into outside services.
8. **Keep the docs true.** When behaviour changes, update the matching doc in the same change: a threshold or rule is [DESIGN.md](docs/DESIGN.md); a
   file format is [DATA.md](docs/DATA.md); the release process is [RELEASING.md](docs/RELEASING.md); a user-visible change is [CHANGELOG.md](CHANGELOG.md).
9. **Do not claim "no network" or "nothing leaves the phone"** anywhere. Our code makes no requests, but the APK holds the Internet permission through a MediaPipe dependency ([docs/DESIGN.md](docs/DESIGN.md#privacy)).
10. **Do not invent lore.** The mascot's story is in [docs/DESIGN.md](docs/DESIGN.md#brand); an invented line was removed once.
11. Open a pull request only when asked.

## Things that have bitten before, or nearly did

- **Sensor to screen mapping.** The two landscape directions map the rotation sensor to the screen differently; it was wrong once and the world
  showed upside down. It is in `rememberWorldView`; the pose log's `VIEW` lines show the numbers. The pan direction is still unconfirmed on a device.
- **Display rotation changes** (landscape one way to the other) may not change the Compose configuration, so `CameraPreview` watches the display itself (handled; never seen to fail on a device).
- **Moving a composable between two call sites rebuilds it.** A camera drawn in one place until it faded and in another after it restarted the camera.
- **`PlankTolerances` comments are the record** of what each number was tuned on. The thigh-line limit (150°) separates a plank (150-180°) from knees-down (108-144°).
- **Paparazzi cannot run animations, dialogs, the camera or the sensors**: see [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md#screenshot-tests-app).
- **Reminders** use `AlarmManager`, not WorkManager (which delivered late). They are off by default.

## Where things are

| | |
|---|---|
| Form rules, thresholds | `android/core/.../PlankPose.kt`, `PlankTracker.kt` |
| Session logic | `android/app/.../CoachViewModel.kt` |
| Session screen, scene, sensors | `SessionScreen.kt`, `FieldWorld.kt`, `CameraControls.kt`, `Hud.kt` |
| The card | `core/PlankCard.kt`, `app/PlankCard.kt` |
| Formats | `core/PlankRecording.kt`, `Backup.kt`, `SessionLog.kt`, `PoseLog.kt` |
| Roadmap and open decisions | [docs/ROADMAP.md](docs/ROADMAP.md) |
| What to check on a phone | [docs/DEVICE-CHECKLIST.md](docs/DEVICE-CHECKLIST.md) |
