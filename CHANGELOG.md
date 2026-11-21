# Changelog

What changed in each release, newest first. The full text of each release, as it appears on the GitHub release page, is in
[docs/release-notes/](docs/release-notes/). The format follows [Keep a Changelog](https://keepachangelog.com/). Versions follow
`MAJOR.MINOR.PATCH`; see [docs/RELEASING.md](docs/RELEASING.md).

## [Unreleased]

### Added
- Project documentation: architecture, data formats, design and form rules, development, releasing, a device checklist and a roadmap.

### Changed
- The README describes Aura Farm, not the Python prototype (which moved to `docs/PROTOTYPE.md`).
- Local builds default to version 0.6.0 / code 600, so they install over the released app.
- Build-error logs and a bytecode cache that had been committed are no longer tracked.
- `tools/make_icon.py` generates the launcher icon (and `tools/make_mascot.py` no longer overwrites it).

## [0.6.0] - 2026-11-19

The app is now **Aura Farm**, with a new session, a smarter form check and a new look. The rubber chicken is still the coach.

### Added
- **A new session.** The screen turns landscape on its own. The camera is a viewfinder pinned to the left with green corner marks on
  the frame, a faint blue ghost skeleton to get into, and camera-app controls on the right (a red record button, flip, close and a mode strip).
- **A scene.** When the plank starts, the camera zooms away and you are a gold skeleton in a field with a barn, standing on the ground,
  steered by the phone's orientation, centred on screen. Only a red timer and a stop button stay on screen.
- **A win beat**: a golden egg, a victory banner, then the screen turns portrait for the card. The card shows the scene the plank
  happened in, and each plank remembers its location.
- **Form dots** (shoulder, hip, knee, joined by a line) and a **thigh-line check**: all fours and knees-on-the-floor planks no longer count.
- **Daily reminders** at 6:00, 13:00 and a 21:00 streak saver, delivered by the system alarm so they arrive without opening the app. Off until you turn them on.
- **Back up and restore** of every plank and replay, through the menu.
- **A game-style look** for the home and streak screens, the win beat and the done screen: slanted tiles, bold italic type, a navy backdrop.
- A new **icon**: a white, slanted AF with an aura glow and sparkles over a barn silhouette.
- Pose log: a `knee_deg` column, `VIEW` lines (sensor and scene values), `CAMERA_BOUND` lines, and room for 1000 lines.

### Changed
- **New name, new package: Aura Farm, `com.aurafarm`.** It installs *next to* Coach Chicken. Back up in the old app, restore in the new one, then uninstall the old one. See the release notes.
- The release APK is `aura-farm-vX.Y.Z.apk`, and the version code now comes from the version (`0.6.0` is 600).
- The session is landscape-only while the camera is on.

### Known issues
- Which way the scene pans when you turn the phone has not been confirmed on a device.
- The 150° thigh-line limit is tuned on three runs of one person.
- A portrait session from a distance has not been tested.
- The `y p r` debug numbers still show on the session screen.
- The app holds the Internet permission, merged in from the MediaPipe library's telemetry component (our own code makes no network requests). Not yet audited; removal is on the roadmap.

## [0.5.0] - 2026-11-04

### Added
- **A plank card** for every plank: a replay you can scrub, the form at each moment, and a hip-angle graph, with a 3D skeleton whose skull is placed from
  the measured ears and nose. Shown on a new done screen (the card, Share and a big Done) and from the calendar, where several planks in a day swipe up and down.
- **A streak screen** in the style of a language-learning app: a big streak number, a call to action or an affirmation, then the calendar, which fits as many weeks as the screen allows.
- A title bar with a menu: **Copy pose log**, **A chicken?** (the mascot's lore, with a link to *The Secret of Monkey Island*) and **Log an issue**.
- Headless screenshot tests of the main screens.

### Known issues
- The APK's version code is 3 (a release-workflow quirk), below the 0.4.x test builds, so it will not install over them. Fixed in 0.6.0.

## [0.3.0] - 2026-11-03

The first Android release, named **Coach Chicken**.

### Added
- A plank timer that clucks: a quiet cluck throughout, a loud one every 10 seconds and a squawk at 60 seconds.
- A calendar with a golden egg ring on each day with a completed plank.
- **Camera mode**: on-device pose detection (MediaPipe) that starts and ends the plank by itself, with a 3-second grace and a building *bwaaak* if your form slips.
- The skeleton drawn as hand-inked bones, gold when the form is perfect, yellow when it is within tolerance and grey when it is not a plank; a gold aura and sparkles that build as you hold; a green goal zone for your hips.
- The pose log (**Copy log**) for tuning the form checks.
- The rubber-chicken mascot.
