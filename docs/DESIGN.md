# Design

What Aura Farm is for, how a session feels, the rules behind the form checks (and the real data they come from), and the
visual and brand language. For how it is built, see [ARCHITECTURE.md](ARCHITECTURE.md).

## The product

A daily plank, made fun enough to actually do. The phone watches you plank from the side, tells you when your form is good with a
gold skeleton and an aura that builds, and counts the day once you hold it for a minute. A streak calendar of golden eggs gives you a reason to
come back tomorrow, and notifications nudge you to.

Principles, as they have shown up in decisions so far:

- **Privacy first.** Pose detection runs on the device, the camera picture is never stored, and the app's own code makes no network requests: nothing
  leaves the phone except what you share yourself (a picture of the card, a backup file). There is one caveat; see [Privacy](#privacy).
- **Generous to the person, strict about the shape.** The thresholds that decide "this still counts" are deliberately loose (a
  loose shirt, a phone propped at an angle, a legs-out-of-frame view); what is strict is the *shape* of the body.
- **Fun is a feature.** The chicken, the aura, the sparkles and the sounds are cheesy on purpose.
- **Tune from real data.** The form checks were tuned on real recordings of real planks, and those recordings are the tests.
- **Text files over opaque ones.** So that a pasted log can be analysed and a backup can be read.

## A session

1. **Home.** The streak number (muted until today's plank is in, then gold), a call to action or an affirmation, and the calendar.
2. **Getting ready.** The screen turns landscape. The camera shows as a viewfinder pinned to the left with a ghost skeleton of the target
   position (about 20% opacity electric blue), a dashed frame and crosshair while no one is found, and green corner marks on the camera
   frame's edges. Camera-app controls sit in the strip on the right: a red record button to start, flip camera, close, and a mode strip
   (the exercise). The ghost fades once a plank is seen. Hints speak up when something is wrong (head out of frame, feet out of frame).
3. **The plank.** Hold a plank for about a second and the timer starts. The camera zooms away and fades, and you are left as a gold
   skeleton in a field, anchored to the ground and moving with the phone's orientation, in the middle of the screen. Only a red timer
   pill and a stop capsule stay on screen. The chicken clucks quietly throughout, loudly every 10 s, and squawks at 60 s. The timer then
   counts up in gold. Press stop, or get up.
4. **Ending.** A lapse has to last **3 s** to end the plank (the readings are low-pass filtered so single bad frames don't matter). About
   1 s into a lapse a long, building *bwaaaak* starts and the hint shows a countdown; get back into position and it cuts off. If the
   grace runs out, the plank ends back-dated to when you left it. 60 s or more counts and earns the egg; ending earlier is logged as incomplete and
   you stay in the session to try again.
5. **The win.** A golden egg pops in the field and a banner slides in. Then the screen turns portrait and the plank card is shown, with Share
   and Done.

The camera permission can be refused: the session then works with buttons only. A session can also be used without the camera.

## What counts as a plank

The code is `core/PlankPose.kt`; all numbers are in `PlankTolerances`, with the evidence in comments. In words, for the side of the
body the camera sees best:

| Check | Counts (OK) | Perfect | Why |
|---|---|---|---|
| **Hip angle** (shoulder-hip-ankle, or to the knee if the ankle is out of frame, else not judged) | at least 130° | at least 160° | A real phone recording wobbled around 155° with dips into the 130s. |
| **Thigh line** (shoulder-hip-**knee**) | at least 150° | at least 150° | The ankle angle hid all fours and knees-down: the shin trails out behind, so the ankle line stays long. A good plank reads 150-180° at the knee in three real runs; knees-down frames read 108-144°. A knee plank where the thigh continues the torso still passes. |
| **Body slope** (shoulder to lowest point, against the camera) | at most 28° | not used | It is measured against the camera's horizon, so a phone propped at a tilt reads a straight plank as 12-17°. Perfect is about shape, not tilt. |
| **Forearm propped** (elbow below the shoulder) | at least 0.10 of the torso | at least 0.20 | Tells a plank from lying flat (about 0.0). 0.30 was too strict: a moving sleeve dipped single frames to 0.20-0.29. |
| **Elbow under the shoulder** (sideways offset) | at most 0.9 | at most 0.45 | |
| **In the green zone** | not needed | hip inside it | See below. |

Visibility: a joint below 0.3 is ignored. A leg point *beyond* the frame edge is used only if MediaPipe is at least 0.5 sure and it
is within 0.4 of the edge: MediaPipe extrapolates where a limb must be, which was plausible but noisy on a real run. The picture's aspect ratio is always applied
before measuring an angle.

**The green goal zone.** Once a plank is seen, a corridor (half-width 0.25 of the torso) is locked along the straight line from
the shoulder to the lowest usable leg point (through the hip and on, if the legs are out of frame). It follows you while you settle
and freezes when the timer starts. For a perfect grade the judged hip must be inside it.

**The three form dots** on the skeleton are the shoulder, hip and knee of the judged side, joined by a line: green when the thigh
carries on from the torso, amber as it begins to bend, orange when it is bent as on all fours.

**Things deliberately not done:** counting a plank by a minimum body slope for *perfect*; trusting the ankle alone for the leg
line; making the checks strict about a person's clothing.

### The aura

Two 0..1 signals (`core/AuraMeter.kt`): the *glow* rises within about 2 s of perfect form and falls in about 0.6 s when it slips; the
*streak* rises slowly (still growing at 60 s) and drains steadily. The glow is the halo around the bones; the streak sets how large it gets
and how many sparkles spawn. Only perfect form feeds them.

## The card

After a plank, and from the calendar, a card shows: the plank's duration; a replay of the skeleton you made (with the scene it
happened in) with a timeline you can scrub; the form at each moment (perfect, good, or not a plank); and a hip-angle graph
(150-200° range) with its average, range, how much was perfect and the best streak. The replay is smoothed so it does not flicker, and it stops
at the last frame of *perfect* form before you left it. The data is `core/PlankCard.kt` (`CardTrack`): a Gaussian smoothing of
the hip angle, grade, aura and joints, a streak filter, and the stop rule. Several planks in a day can be swiped between.

## The streak and calendar

The streak counts consecutive days with a completed plank and survives until the end of today. The calendar shows as many weeks as fit
(1 to 5), Sunday first, with a golden egg ring on each day with a completed plank, and a clock mark for the time of day.
Tapping a day opens that day's cards.

## Reminders

Three a day: **6:00**, **13:00** and a **21:00 streak saver**. None is sent once today's plank is done, and the streak saver is only
sent to someone with a streak to lose. The copy is plucky and cluck-flavoured, varies by day so a line never repeats back to back, and
names the streak when it is at stake. **Reminders are off until you turn them on** (menu, then Daily reminder, which also asks for the
notification permission and has a button to send a test cluck).

## Visual language

There are two deliberate vocabularies:

- **Camera app** (the session, while the camera is on). Controls look like the phone's own camera app so what each does is obvious:
  round grey buttons, a white ring with a red dot to start, a red timer pill, a grey capsule with a white square to stop, the
  mode strip of exercises written sideways at the screen edge. Hints are translucent black rounded pills. The corner marks on the
  camera frame are green.
- **Game HUD** (the home screen, the win beat, the done screen). Slanted tiles at one fixed angle, hard offset shadows, bold italic
  capitals, a deep navy backdrop, a bright yellow for the main action, blue for secondary, hot pink for stop, cyan edges; gold for
  anything to do with the streak. The components are in `app/Hud.kt`.

The skeleton is drawn as hand-inked bones (wobbly outlines, knobbly ends), in 3D so it can turn, with a skull placed from the
measured ears and nose. Its colour is the grade: **gold** when perfect, **yellow** when within tolerance, **grey** when not a plank. The side
being judged is drawn full size, the far side thinner and dimmer.

Dark navy is the home screen's colour whatever the system theme says. Other screens follow the system theme.

## Brand

The app is **Aura Farm**. The mascot, **Coach Chicken**, is an upright, limp, gangly rubber chicken in a mid-squeak: long neck, small head
turned to the side, an open "O" mouth with squeak rays, and a bow tie. He is the coach. The icon is a white, slanted **AF** (the F
styled after Fortnite's) with an aura glow and sparkles over a barn silhouette on a blue gradient.

The lore (the "A chicken?" page in the menu): inspired by the rubber chicken with a pulley in the middle, first seen in *The Secret of
Monkey Island*, which Guybrush Threepwood was sure would be completely useless until it turned out to have several uses. The real-world
one is a squeaky rubber chicken with a pivoting neck, bought in Malaysia, that served as an exercise squeaker through covid. The
job: a quiet cluck while you hold, a louder one every ten seconds, a squawk at the minute, and a long building bwaaak if form slips.
*This chicken is not useless either: it is helping you train.* Keep to these facts; an earlier line that was invented was removed.

The sounds are synthesised placeholders (`tools/generate_sounds.py`) and are meant to be replaced by recordings.

## Privacy

What is true and checked: the app has no accounts and no server of its own; the pose model is bundled in the APK; the code in this
repository makes no network requests (a search for network classes finds none; the only two links it opens, *Log an issue* and the *Monkey Island* page, go to your browser); the camera picture is processed in memory and never stored (only the joint positions are, in a plank's replay); everything
you keep is in the app's private storage, and the only things that leave are what you choose to share (a card picture, a backup file).

What is **not** simple: the released APK declares the **`INTERNET`** and `ACCESS_NETWORK_STATE` permissions. They do not come from our code. They are merged in from
**Google's DataTransport library** (`transport-backend-cct`), a dependency of MediaPipe's `tasks-core` (`tasks-vision` leads to `tasks-core` leads to DataTransport), which is
Google's component for sending usage telemetry. **We have not audited what, if anything, it sends.** The honest summary is "our code sends nothing, and a Google
library we depend on holds the permission to".

You can see this yourself: `aapt dump permissions app-release.apk`, and the merge report at
`android/app/build/outputs/logs/manifest-merger-release-report.txt` says which library added each permission.

What would make the stronger claim true is **removing the `INTERNET` permission** (a `tools:node="remove"` line in the manifest) and proving that MediaPipe still
works without it. That needs a device test: if the library tries to reach the network without the permission it may throw instead of failing quietly. It is on the
[roadmap](ROADMAP.md). Until then, do not write "no network" or "nothing ever leaves the phone" in the docs, the release notes or the store listing.

## What is not designed yet

Accessibility (TalkBack labels, contrast checks, reduced-motion), localisation, a light theme for the home screen, tablets and foldables,
and a portrait session from a distance (portrait works from about 2 m away but has not been tested on a person). See
[ROADMAP.md](ROADMAP.md).
