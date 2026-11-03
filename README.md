# coach-chicken
 
A rep counter to make exercises more fun.

## Getting started (on Windows):

 * Install python 3.10 from https://www.python.org/downloads/release/python-31010/
 * `pip install opencv-python`
 * `pip install mediapipe` (won't work on python 3.11, only 3.6-3.10)
 * `python coach.py`, wait a few seconds, and the camera frames should start displaying with reps and body landmarks.

### Option for pre-recorded test
 * Download a sample video to test with. e.g. I used Juice & Toya's 15-minute workout https://www.youtube.com/watch?v=xqVBoyKXbsA, downloaded and renamed it to 15-min.mp4, this is a good sample video.
 * Change the code to use the video file not capture.
   * comment the VideoCapture(0) line
   * uncomment the VideoCapture loading file (and the 2000 skip loop)
 * `python coach.py` should then open the video and render frames with landmarks over it and a rep counter (current version is only squats).

## Android app (`android/`)

A plank timer that clucks at you, and watches you plank.

### Flow
 1. **Home**: the calendar (golden egg ring per day with a completed plank, 5 weeks, Sunday first) and **Start session**.
 2. **Session**: the camera shows your skeleton (MediaPipe Pose, on-device). Press **Start plank**, or just get into a plank and hold it for 1.5s, and the timer starts: quiet clucking throughout, a louder cluck every 10s.
 3. **At 60s**: a squawk, the quiet clucking stops, and the timer counts up past the goal in gold. The plank is *not* stopped: the button becomes **Done**.
 4. **Ending**: press **Stop/Done**, or just get up (the camera notices after 2s and back-dates the end to when you left the plank). 60s or more counts and earns the egg; ending earlier is logged as incomplete and leaves you in the session to try again.
 Camera permission refused? The session still works with the buttons. Leaving the app winds the session up.

Later: plank form feedback ("pakak" when it's wrong).

### Build
 * Needs JDK 17+ and the Android SDK (`ANDROID_HOME`, or `sdk.dir` in `android/local.properties`).
 * `cd android && gradle :core:test :app:assembleDebug`, then install `app/build/outputs/apk/debug/app-debug.apk`.
 * `core/` is plain Kotlin (timer cues, calendar grid, log format, pose/plank detection and tracker) with unit tests, including a replay of real recorded landmarks; `app/` is the Compose UI, CameraX and MediaPipe.

### Sounds
The sounds in `app/src/main/res/raw/` are synthesized placeholders from `python tools/generate_sounds.py`. Replace `cluck_quiet_loop.wav`, `cluck_loud.wav` and `squawk.wav` with real recordings any time.

### Pose log
**Copy log** (in the session, or on the home screen afterwards) puts a pasteable CSV on the clipboard: per-frame (4/s) detector verdict, the numbers behind it (body angle, hip angle, elbow ratios) and the key joints, plus start/stop events. Paste it back to tune the plank detection thresholds (`readPlank` in `core/PlankPose.kt`).

### Build
 * Needs JDK 17+ and the Android SDK (`ANDROID_HOME`, or `sdk.dir` in `android/local.properties`).
 * `cd android && gradle :core:test :app:assembleDebug`, then install `app/build/outputs/apk/debug/app-debug.apk`.
 * `core/` is plain Kotlin (timer cues, calendar grid, log format) with unit tests; `app/` is the Compose UI.

### Sounds
The sounds in `app/src/main/res/raw/` are synthesized placeholders from `python tools/generate_sounds.py`. Replace `cluck_quiet_loop.wav`, `cluck_loud.wav` and `squawk.wav` with real recordings any time.
