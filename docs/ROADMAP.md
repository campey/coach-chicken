# Roadmap

**Status: draft, written at v0.6.0 for the maintainer to edit.** The *Now* items come from checks and gaps we found while
building v0.6.0. Everything after that is a proposal, ordered by how much it unlocks, not a commitment. Sizes are rough:
**S** is under a day, **M** a few days, **L** a week or more.

## Decisions needed

These change what gets built, and only the maintainer can make them:

1. **Licence.** There is none, so by default nobody else may reuse the code, which does not suit "collaborators". Common choices are
   MIT or Apache-2.0 (permissive), or GPL-3.0 (changes must stay open). Not chosen yet.
2. **The next exercise (or two), and timed or counted.** The plank is a timed hold. Push-ups or squats are *reps* and need a different
   kind of timeline and card. See *Next*.
3. **Google Play?** Sideloaded APKs from GitHub work today. Play needs the application id to be final (it now is: `com.aurafarm`), a
   privacy policy, a current target SDK, Play App Signing (which changes how the key is held), and a store listing.
4. **The repository's name.** It is still `coach-chicken`. Renaming it to `aura-farm` keeps old links working (GitHub redirects); the
   app's "Log an issue" link would follow the redirect.
5. **What the on-screen debug readout does.** The `y p r` numbers and `t=` clock were for hunting bugs. Remove, or hide behind a switch?

## Now: hardening v0.6.x

| Item | Size |
|---|---|
| Walk the [device checklist](DEVICE-CHECKLIST.md) and confirm the open item: **which way the scene pans** when the phone turns (never confirmed on a device). Fix the yaw sign if it is backwards. | S |
| Collect pose logs from a few people and body types, and check the **150° thigh-line limit** holds on a good plank (it is tuned on three runs of one person). | S, ongoing |
| Remove or hide the `y p r` readout (decision 5). Keep the `VIEW` lines in the pose log. | S |
| **CI on every pull request**: run `:core:test` and the screenshot tests. Today tests run only when a release is built, so a broken change is found late. | S |
| Make the release workflow use `docs/release-notes/vX.Y.Z.md` as the release body, so notes live in the repository and not in a hand-edit of the release page. | S |
| Update the published **v0.6.0 release page** with the real notes: it still has the generic text and no upgrade steps, and the package change needs them. The text is drafted in [release-notes/v0.6.0.md](release-notes/v0.6.0.md). | S |
| **Audit the network.** The APK holds the Internet permission through MediaPipe's telemetry dependency ([DESIGN.md](DESIGN.md#privacy)). Capture the app's traffic to see what, if anything, it sends; try removing `INTERNET` (`tools:node="remove"`) and test on a device that pose detection and everything else still work; then state the privacy claim precisely. | S-M |
| Add a **Gradle wrapper** (so `./gradlew` works and the version is pinned), a licence (decision 1), and issue templates. | S |

## Next

**Automated full-loop test.** A test that launches the app, starts a session, feeds it a recorded plank, and checks the win beat, the rotation to
portrait, the card and the calendar, so regressions like the win-beat crash are caught before a phone sees them. The agreed shape is two tiers:
a fast one on the JVM (Compose UI test with Robolectric, no emulator) run on every build, and an emulator one on GitHub for releases. Needs:
a test-only way to feed recorded frames into `CoachViewModel.onPose`; and, for the emulator tier, **an x86_64 build**, because the app is arm64
only today and GitHub's emulators are x86. (A development machine without hardware virtualisation cannot run an emulator at all.) **L.**

**A second exercise and the switcher.** The mode strip and the ghost template are ready; the rest is plank-only. Doing it properly means:
move grading, the timeline and the target onto `Exercise`; add `exercise=` to the recording header and show it on the card; make the mode
strip selectable; give each exercise its own ghost. `sessions.csv` already has an `exercise` column. Candidates, roughly easiest first:
*high plank* (straight arms: a different elbow rule, same timer), *side plank* (a different body line, the camera view changes),
*wall sit* (timed, no arms), then the rep-counted ones (*squats*, which the original Python prototype counted, and *push-ups*) which need
a rep counter and a different card. **L** for the first, **M** for each after.

**A second location and a chooser** at session start (the scenes are already pluggable and saved per plank). A barn interior, a beach, night;
a chooser could share the mode-strip area. **M.**

**Restyle the remaining screens** in the game HUD: the past-day cards, the "A chicken?" page, the reminder dialog, the menu. **S-M.**

**Real sounds.** Replace the four synthesised placeholders with recordings of the actual squeaky chicken. Needs the recordings. **S.**

**A pause** during a plank, if wanted. Needs a decision on what pausing does to a 60 s hold. **S-M.**

## Later

- **Stats and history:** totals, best streak, longest plank, share of perfect form, a trend over time, built from the stored recordings.
- **Accessibility:** TalkBack labels for every control, contrast checks, reduced-motion support (the start sequence and the glow are
  motion-heavy). Then localisation.
- **A session that survives leaving the app** (a foreground service). Today leaving the app winds it up.
- **Layouts:** a light theme for the home screen, tablets and foldables, a portrait session from a distance (works from about 2 m, never tested on a person).
- **Storage housekeeping:** a replay is about 50 KB (a year of daily planks is about 18 MB) and nothing is ever pruned. Fine for now; an
  export or archive would be kind later.
- **Form feedback:** spoken or sounded hints when form slips ("pakak"), beyond the existing warning.
- **Optional rename** of the Kotlin package from `com.coachchicken` to match the app. Cosmetic and invasive; only if it bothers contributors.
- **Dependency upkeep:** MediaPipe is pinned at 0.10.21 and target SDK at 35; both need to move on a schedule (Play requires a recent target SDK each year).

## Ideas, not scheduled

Achievements (golden egg tiers), seasonal scenes, extra chicken voice lines, a video of the replay to share, a home-screen widget showing the
streak, backing up to a cloud drive you choose.

## Not planned

Anything that needs a server or an account (leaderboards, sync): it would contradict the privacy principle that the app keeps your data on the phone.
Ads and analytics.
