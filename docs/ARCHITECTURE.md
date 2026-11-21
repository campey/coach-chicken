# Architecture

Aura Farm is one Android app (`android/app`) on top of one plain-Kotlin library (`android/core`).

```
android/
  core/   plain Kotlin/JVM, no Android classes: the rules, the maths and the file formats. Unit tested on the JVM.
  app/    Android: Compose UI, CameraX, MediaPipe, sensors, notifications, files. Screenshot tested on the JVM.
tools/    Python scripts that generate the mascot, the launcher icon and the placeholder sounds
docs/     these documents
```

**The rule that keeps this workable:** anything that decides something goes in `core` and gets a test; `app` only draws,
plays, schedules and stores. If you find yourself importing `android.*` in `core`, the logic belongs one layer up or the
Android part should be passed in. `core` is what makes the form checks tunable against real recordings (see
[DEVELOPMENT.md](DEVELOPMENT.md#tuning-the-form-checks-from-a-pose-log)).

## How a camera frame becomes a plank

```
CameraX ImageAnalysis ──► PoseAnalyzer ──► CoachViewModel.onPose(landmarks, aspect)
 (single-thread executor)  MediaPipe Pose         │
                           Landmarker (lite,      ├─ gradePlank()          core/PlankPose   NO_BODY / NOT_PLANK / OK / PERFECT
                           video mode), upright   ├─ PlankTracker.update() core/PlankTracker hysteresis: STARTED / WARNING / RECOVERED / ENDED
                           bitmap, 33 landmarks   ├─ AuraMeter.update()    core/AuraMeter    the gold glow and the streak meter
                                                  ├─ lockTarget()          core/PlankTarget  the green goal zone
                                                  ├─ PoseLog.addFrame()    core/PoseLog      the pasteable CSV (4 Hz)
                                                  ├─ PlankRecorder.add()   core/PlankRecording  the replay (4 Hz, with a 10 s lead-in)
                                                  └─► UiState.pose (PoseUi) ──► Compose: SessionScreen / PoseOverlay
```

- `PoseAnalyzer` (`app`) converts each frame to an upright bitmap, runs MediaPipe, and calls back with normalised landmarks
  (x right, y down, 0..1) and the picture's aspect ratio. Landmarks can be outside 0..1: MediaPipe extrapolates limbs that leave
  the frame.
- `gradePlank` (`core/PlankPose.kt`) picks the better-seen side of the body, measures it, and grades it. **All thresholds live
  in `PlankTolerances`**, each with a comment saying what real data it was tuned on. See [DESIGN.md](DESIGN.md#what-counts-as-a-plank).
- `PlankTracker` turns noisy per-frame readings into stable events: a low-pass filtered signal, a 1 s hold to start, a 3 s grace
  to end, a warning 1 s into a lapse, and recovery. It never sees landmarks, only `NO_BODY` / `NOT_PLANK` / `PLANK`.
- `CoachViewModel` owns the session. It starts the timer on `STARTED`, starts the building *bwaaak* on `WARNING`, and on `ENDED`
  ends the plank **back-dated to when you first left it**, not when the grace ran out.

## The session state machine

`Phase`: `HOME` → `READY` (camera on, waiting for you) → `PLANKING` (timer running) → `DONE` (completed) or back to `READY`
(ended before 60 s, logged as incomplete). `closeSession()` returns to `HOME`.

- `PlankTimeline` (`core`) says what sound happens when: a loud cluck every 10 s, a squawk at 60 s. The quiet cluck loop is not a
  cue. The timer **keeps going past 60 s** (the clock counts up in gold); reaching 60 s is what makes the plank count.
- A tick every 100 ms (`TICK_MS`) advances the clock and plays the cues. `SoundPlayer` plays them.
- The activity declares `configChanges` so rotating does not recreate it and lose the session.
- Leaving the foreground winds the session up (`onAppStopped`); there is no foreground service yet.

## Screens

| | |
|---|---|
| `MainActivity` | The Compose host. Chooses a screen from `Phase`, the open day, and the lore page. Owns the file pickers for backup and restore. |
| `SessionScreen` | The camera session. Locks the screen to landscape while the camera is on. A camera-app HUD (`CameraControls.kt`). |
| `FieldWorld` | The start sequence, the scenes (`drawScene`), and the phone-orientation sensor (`rememberWorldView`). |
| `WinBeat` / `DoneScreen` | `DoneFlow`: a win beat in landscape, then locks portrait and shows the card with Share and Done. |
| `PlankCard` | The card: replay, timeline, form tile, hip graph. Used on the done screen and for past days. |
| `CalendarView`, `MainActivity.HomeScreen` | The streak and the calendar. |
| `BoneArt` | The 3D skeleton: bones as procedural shapes drawn back to front, with a skull placed from the measured ears and nose. |
| `Hud.kt`, `CameraControls.kt` | The two visual vocabularies: the game-style tiles and buttons, and the camera-app controls. See [DESIGN.md](DESIGN.md#visual-language). |

### The start sequence

`StartSequenceLayers` draws four layers from a single progress number (0 to 1): the scene, the camera, the dim position layer
(dashed frame, crosshair), and the skeleton. Progress 0 is "looking for you". When the plank starts it animates to 1 over 2.4 s: the
camera dips, pops, zooms at you and fades; the scene fades in; the capture frame slides from the left of the screen to the middle.
The skeleton keeps living inside the same 4:3 frame throughout, which is why the green corner marks stay meaningful once the
camera is gone.

### The scene follows the phone

`rememberWorldView` reads the rotation vector sensor. **Yaw** (turning) is relative to where the session opened and limited to ±60°;
**pitch** is relative to how the phone was propped (a propped phone leans back, so absolute pitch would push the horizon off
screen) and limited to ±20°; **roll** comes from where gravity points on the screen and is limited to ±20°. The mapping from the
sensor's axes to the screen's depends on the display rotation, and the two landscape directions are different, so it is spelled out
in code (`ROTATION_90` and `ROTATION_270`). It was wrong once and showed the world upside down; the pose log now records the sensor
values (`VIEW` lines) so this can be checked from a log.

## Extension points

- **A new location** (`Scene` in `core/PlankRecording.kt`): add an entry, then a branch in `drawScene` (`app/FieldWorld.kt`). Each
  plank saves its scene in its recording and the card draws that one, so old planks keep their place. Unknown or missing ids fall
  back to the farm.
- **A new exercise** (`Exercise` in `core/Exercise.kt`): today an exercise is a name and a ghost template (joint positions in the
  frame, head on the left) shown while getting into position. Judging, timing and the card are still plank-only. The mode strip in
  the HUD lists `Exercises.all` and has nothing to switch yet. Doing this properly means moving the grading and the timeline onto the
  exercise; see [ROADMAP.md](ROADMAP.md).

## Persistence

Plain files in the app's private storage (`filesDir`): `sessions.csv` and `planks/<epoch millis>.txt`. Formats in
[DATA.md](DATA.md). Backup is a zip of those two things through the system file picker. There is no database.

## Notifications

`Reminders.kt` schedules the next of three daily slots with `AlarmManager.setAndAllowWhileIdle` (WorkManager was tried first and
delivered late: jobs were deferred until the app was opened). A receiver posts the notification, unless today's plank is done (or,
for the 21:00 streak saver, there is no streak to lose), then schedules the next one. A boot receiver reschedules after a restart.
The rules (`ReminderSlot`, `shouldSend`, `nextSlot`, `ReminderCopy`) are in `core/Reminder.kt` and tested.

## Permissions

`CAMERA` (refused is fine: the session works with the buttons), `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`. The app's own code makes no network
requests and talks to no server, but **the built APK also declares `INTERNET` and `ACCESS_NETWORK_STATE`**, merged in from Google's DataTransport library
via MediaPipe; see [DESIGN.md](DESIGN.md#privacy). The model file (`pose_landmarker_lite.task`) is bundled in `assets/`.

## Build facts worth knowing

- arm64 only (`abiFilters`): MediaPipe's native libraries are large, so **x86 emulators cannot run the app**.
- `isMinifyEnabled = false`: MediaPipe and CameraX use reflection, and a 30 MB sideloaded APK is fine.
- Kotlin and Gradle versions are pinned in `android/build.gradle.kts`; dependency versions in `android/app/build.gradle.kts`.
- The Kotlin package is still `com.coachchicken` (the old name); the *application id* is `com.aurafarm`. They are different things
  and only the application id is what Android sees. See [RELEASING.md](RELEASING.md#the-package-name-change).
