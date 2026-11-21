# Development

## Set up

You need:

- **JDK 17** (CI uses Temurin 17).
- **The Android SDK** with platform 35 and its build tools. Point at it with `ANDROID_HOME`, or `sdk.dir=...` in
  `android/local.properties` (that file is git-ignored).
- **Gradle 8.14.3.** There is **no Gradle wrapper** in the repository, so `./gradlew` does not exist: install Gradle and run
  `gradle`. (Adding a wrapper is on the [roadmap](ROADMAP.md); until then match CI's version.)
- Optional: **Python 3** (standard library only) for the scripts in `tools/`.

```
cd android
gradle :core:test                      # the unit tests (fast; no Android needed)
gradle :core:test :app:assembleDebug   # also builds app/build/outputs/apk/debug/app-debug.apk
gradle :app:testDebugUnitTest --tests '*ScreenshotTest*'   # render the screens to PNGs, no phone or emulator
```

The app is arm64 only, so install it on a real phone (`adb install -r path/to.apk`). It will not run on an x86 emulator.

## Where things go

The rule of the codebase: **decisions go in `android/core` with a test; drawing, playing, scheduling and storing go in
`android/app`.** `core` has no Android dependency and runs on the JVM in seconds. See [ARCHITECTURE.md](ARCHITECTURE.md).

| You are changing | Put it in | Test it with |
|---|---|---|
| What counts as a plank, thresholds | `core/PlankPose.kt` | a unit test, and a replay of real data if you can |
| When a plank starts or ends, warnings | `core/PlankTracker.kt` | `PlankTrackerTest`, and the replay tests |
| A file format | `core/PlankRecording.kt`, `Backup.kt`, `SessionLog.kt`, `PoseLog.kt` | a round-trip test and an old-format test ([DATA.md](DATA.md#compatibility)) |
| A screen or a visual | `app/*.kt` | a screenshot test (below) |
| A notification rule | `core/Reminder.kt` | `ReminderTest` |

## Tests

### Unit tests (`core`)

`gradle :core:test`. Many of them **replay real recordings** instead of hand-made poses: they are the reason the form checks can be
changed without fear. The fixtures live in `android/core/src/test/resources/` (and `android/app/src/test/resources/run.txt`):

| Fixture | What it is |
|---|---|
| `plank_video_landmarks.csv` | MediaPipe landmarks from a side-on plank tutorial video: a plank, a zoom shot where the legs leave the frame, then getting up. |
| `phone_run1_verdicts.csv` | Per-frame verdicts of the original, strict detector on a real phone run (portrait, front camera, loose jersey). |
| `knees_down_run.csv` | Rows of a real 63 s run in which the knees were on the floor for long stretches yet it graded PERFECT. Drove the thigh-line check. |
| `app/.../run.txt` | A full real plank recording (v0.4.2), used by the screenshot tests as the card's replay. |

Real recordings of people are small and are fine as fixtures. **Don't commit anything personal** (names, photographs, a whole
backup). A handful of landmark rows is enough.

### Screenshot tests (`app`)

`ScreenshotTest.kt` renders screens headlessly with [Paparazzi](https://github.com/cashapp/paparazzi) (Android's layout engine on
the JVM). Images land in `android/app/build/reports/paparazzi/debug/images` (open `index.html` there for names). They are for **looking
at** a change, not for pixel-diffing: there is no golden-image comparison.

What it **cannot** do, and what to do instead:

- **No animations run.** A screen renders its first frame. To see a later state, give the composable a parameter for it (the start
  sequence takes a `progress` number; the egg takes a time) and render several values. The tests do exactly this.
- **No camera, no sensors.** Don't put your whole screen's layout inside something that needs CameraX. Pull the part you want to see
  into its own composable that takes plain state (`SideHud` is the example) and render that.
- **Dialogs and popups are not rendered.**
- **It does run real drawing code**, including Android's `RadialGradient`: a zero radius threw, in a test, before it ever crashed a phone. That is what a
  "render the first frame" test is for: **write one for any new screen or animation**, including its zero state.
- Fetching dependencies from Maven Central can hit HTTP 429 (rate limits) on shared networks. Re-run it; it works.

Add a test by copying one in `ScreenshotTest.kt`. Use the real `run.txt` replay when you need a realistic skeleton.

### What no test covers

The camera, the real sensors, screen rotation, sounds, notifications and how it feels. Check these on a phone before a release
with [DEVICE-CHECKLIST.md](DEVICE-CHECKLIST.md). CI only runs the tests when it builds a release (there is no workflow on pull requests yet).

## Tuning the form checks from a pose log

When a plank grades wrongly, the pose log has the evidence. The loop that produced every threshold in `PlankTolerances`:

1. On the phone, **Copy log** straight after the session (it is lost if the app dies). Paste it somewhere.
2. Read it against [the column list](DATA.md#the-pose-log). Find the frames that were wrong: `grade`, then the numbers behind it
   (`hip_deg`, `knee_deg`, `elbow_drop`, `elbow_off`, `lower`). The `t=` clock in a screen recording matches `t_ms`.
3. Work out what separates the wrong frames from the right ones in the data. Do not guess: compute the distribution of the number
   over *good* frames and over *bad* frames, and pick a limit between them with a margin. (For the thigh-line check: good
   frames were 150-180°, knees-down frames 108-144°.) If the groups overlap, the signal is wrong, not the limit.
4. Put a handful of rows from the log into a fixture and write the failing test first (see `KneesDownRunTest`). Then change the limit.
5. Run all of `:core:test`. The older replay tests will tell you what else moved. If one moves for a good reason (the knee check
   noticed a hip lift a second earlier), change its window **and say why in a comment**.
6. Write the data you used into the comment on the threshold. The next person will want to know.

## Building a test APK

```
cd android
gradle :app:assembleRelease -PversionName=0.7.0 -PversionCode=700
# -> app/build/outputs/apk/release/app-release.apk
```

Two things to know:

- **Version code.** Android won't install a lower version code over a higher one. Releases use `major*10000 + minor*100 + patch`
  (0.6.0 is 600), and the defaults in `app/build.gradle.kts` match the latest release. Test builds between releases have used
  `0.<release>.<n>` names like 0.5.17 (code 517); pick a code above the installed one.
- **Signing.** A build installs over an existing install only if it is signed with the same key. Release builds sign with the
  keystore from the `RELEASE_KEYSTORE_*` environment variables if they are set, otherwise with your debug key. A build from the CI
  release key will not install over a build from your own debug key (uninstall first, after a backup).

## Generated assets (`tools/`)

Standard library only. Run from anywhere.

| Script | Produces |
|---|---|
| `python3 tools/make_icon.py` | The launcher icon: `ic_launcher_background`, `_foreground` and `_monochrome` (vector drawables). Edit the constants at the top. |
| `python3 tools/make_mascot.py [preview.svg]` | The chicken, `drawable/ic_chicken.xml` (and an SVG preview). |
| `python3 tools/generate_sounds.py` | The four placeholder sounds in `app/src/main/res/raw/`. Replace them with recordings any time; the app only refers to the file names. |

## Branches, pull requests and commits

- Work on a branch, open a pull request into `main`, and merge it. History so far is merge commits from PRs.
- Commit messages say what and why in the imperative ("Card: stop before the final curve out", "Fix the head flipping upside down...").
- **Do not change the `RELEASE` file on a branch**: a change to it on *any* branch starts a release workflow from that branch. See
  [RELEASING.md](RELEASING.md).
- Don't commit logs, backups or recordings of real people; `.gitignore` already excludes `*.csv` except the test fixtures, and
  `*.mp4`, `*.jks`, `*.keystore`.
- Never commit signing keys or passwords. The release key lives in the maintainer's password manager and in GitHub secrets.

## Common ways to add things

**A form check.** Add the number to `PlankMetrics`, the limit to `PlankTolerances` (with the data in a comment), the condition to
`gradeMetrics`, a column to `PoseLog` (at the end of the metric block), and tests from real rows. Update
[DESIGN.md](DESIGN.md#what-counts-as-a-plank).

**A location.** An entry in `Scene` (`core/PlankRecording.kt`) and a drawing branch in `drawScene` (`app/FieldWorld.kt`). Render it
in a screenshot test. Old recordings keep their scene.

**An exercise.** See [ARCHITECTURE.md](ARCHITECTURE.md#extension-points) and [ROADMAP.md](ROADMAP.md).

**A stored field.** Add it as an optional header key with a default, with a test that an old file still loads. Update [DATA.md](DATA.md).

## Troubleshooting

- *"Not enough space" / the build stalls on dependencies:* clear `android/build` and `android/app/build`; on shared networks re-run on a 429.
- *The app crashes at the end of a session on a phone but the tests pass:* look for a draw call with a zero size or radius; add a
  screenshot test that renders the zero state.
- *The scene is upside down or pans the wrong way:* read the `VIEW` lines in the pose log (see [DATA.md](DATA.md#the-pose-log)); the
  sensor-to-screen mapping is in `rememberWorldView`.
- *Won't install over the old build:* a lower version code, or a different signing key. Uninstall (after a backup) or fix the code.
