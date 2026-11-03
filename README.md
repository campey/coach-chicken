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

A plank timer that clucks at you. Milestones:

 1. **Timer** (this version): 1-minute plank, quiet clucking throughout, a louder cluck every 10s, a squawk at 1:00. Completed planks are logged locally (`sessions.csv` in the app's private storage) and shown as golden egg rings on a 5-week calendar (weeks start Sunday).
 2. Camera preview with pose landmarks (MediaPipe).
 3. Plank form feedback ("pakak" when it's wrong) and auto start/stop when you get down/up.

### Build
 * Needs JDK 17+ and the Android SDK (`ANDROID_HOME`, or `sdk.dir` in `android/local.properties`).
 * `cd android && gradle :core:test :app:assembleDebug`, then install `app/build/outputs/apk/debug/app-debug.apk`.
 * `core/` is plain Kotlin (timer cues, calendar grid, log format) with unit tests; `app/` is the Compose UI.

### Sounds
The sounds in `app/src/main/res/raw/` are synthesized placeholders from `python tools/generate_sounds.py`. Replace `cluck_quiet_loop.wav`, `cluck_loud.wav` and `squawk.wav` with real recordings any time.
