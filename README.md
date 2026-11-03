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
 2. **Session**: the camera shows your skeleton (MediaPipe Pose, on-device; portrait or landscape). Press **Start plank**, or just get into a plank and hold it for about a second, and the timer starts: quiet clucking throughout, a louder cluck every 10s.
    * **The skeleton is drawn as hand-inked bones**: a spine of vertebrae, ribs, a pelvis, and for each arm a humerus with a radius and ulna as plain lines, and for each leg a femur with a tibia and fibula as plain lines; the spine runs along the back, with the ribs wrapping round to the front (wobbly outlines, knobbly ends, a little highlight). The bone colour is the grade: **gold** when the plank is perfect (straight hips, forearm propped, and the hip inside the green zone; the body's tilt against the camera is deliberately not used, so a phone propped at an angle doesn't cost you), plain **yellow** when it is within tolerance (still counts as a plank), and **grey** when it isn't a plank. The side being judged is drawn full size; the far side is thinner and dimmer.
    * **Aura and sparkles** (cheesy on purpose): a pulsing gold glow builds around a perfect plank within seconds, and a slower *streak* meter keeps growing through the whole hold, so the glow gets bigger and twinkling gold-and-white stars spawn along your body, more of them the longer you hold perfect form (still escalating at 60s). It fades when form slips.
    * **Setup hint**: if your head is out of frame the status line says so (move the phone back or turn it to landscape).
    * **Green goal zone**: once a plank is seen, a green corridor along the ideal straight body line (shoulder to ankle; through the hip if your legs are out of frame) is the goal for the yellow. It follows you while you settle, then freezes when the timer starts. The hip being judged turns green while it is inside.
 3. **At 60s**: a squawk, the quiet clucking stops, and the timer counts up past the goal in gold. The plank is *not* stopped: the button becomes **Done**.
 4. **Ending**: press **Stop/Done**, or just get up. The camera low-pass filters its readings (so scattered bad frames inside a good plank, a loose jersey or a wobbly joint, don't matter) and a lapse has to last **3s** before the plank ends. About 1s into a lapse a long, building **"bwaaaak!"** starts and the status shows a countdown; get back into plank and it cuts off. If the 3s runs out the plank ends, back-dated to when you left it. 60s or more counts and earns the egg; ending earlier is logged as incomplete and leaves you in the session to try again.
 In landscape the controls sit along the top (next to the timer) so the bottom of the picture, where your forearms are, stays clear.
 Camera permission refused? The session still works with the buttons. Leaving the app winds the session up.

Later: plank form feedback ("pakak" when it's wrong).

### Build
 * Needs JDK 17+ and the Android SDK (`ANDROID_HOME`, or `sdk.dir` in `android/local.properties`).
 * `cd android && gradle :core:test :app:assembleDebug`, then install `app/build/outputs/apk/debug/app-debug.apk`.
 * `core/` is plain Kotlin (timer cues, calendar grid, log format, pose/plank detection and tracker) with unit tests, including a replay of real recorded landmarks; `app/` is the Compose UI, CameraX and MediaPipe.

### Sounds
The sounds in `app/src/main/res/raw/` are synthesized placeholders from `python tools/generate_sounds.py`. Replace `cluck_quiet_loop.wav`, `cluck_loud.wav`, `squawk.wav` and `bwaaak.wav` (the 2s warning that builds during the grace period) with real recordings any time.

### Pose log
**Copy log** (in the session, or on the home screen afterwards) puts a pasteable CSV on the clipboard: per-frame (4/s) grade (NO_BODY / NOT_PLANK / OK / PERFECT), the filtered plank signal (`plank_s`) and aura, the hip's distance from the green zone (`tgt_off`, 1 = the zone's edge), the numbers behind the grade (body angle, hip angle, elbow ratios; `lower` is A ankle / K knee / H hip only) and the key joints, plus start/stop/warning events. The camera screen shows a small `t=` clock that matches the log's `t_ms`, so a screen recording lines up with a pasted log. Paste it back to tune the plank detection thresholds (`PlankTolerances` in `core/PlankPose.kt`).

## Releases

Download the latest APK from the [Releases page](https://github.com/campey/coach-chicken/releases/latest) and open it on an Android phone (8.0+, 64-bit ARM). You will be asked to allow installing from your browser or file manager.

To cut a release, put the new version tag in the `RELEASE` file (for example `v0.3.1`), commit and push; GitHub Actions runs the tests, builds the APK and publishes the release. Pushing a git tag yourself (`git tag v0.3.1 && git push origin v0.3.1`) works too. A release that already exists is left alone.

By default the APK is signed with a debug key. For a stable key (so phones can update across releases), create one and add four repository secrets:

```
keytool -genkeypair -v -keystore release.jks -alias coachchicken -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks     # paste into the RELEASE_KEYSTORE_BASE64 secret
```

with `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD` as the other three. Keep `release.jks` somewhere safe and out of the repo: if it is lost, existing installs cannot be updated.
