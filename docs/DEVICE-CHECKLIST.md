# Device checklist

The automated tests cannot see the camera, the sensors, screen rotation, sounds or notifications. Run through this on a real phone
before a release (and after any change to the session, the scene or the reminders). It takes about fifteen minutes. Keep a copy of the
pose log (**Copy log**) from the plank in step 3: it is the evidence if something looks off.

Record what you tested on at the bottom.

## 1. Installing

- [ ] The new APK installs **over the previous release**, and the calendar, streak and cards are still there.
- [ ] A fresh install asks for camera permission when a session starts; refusing it still lets the session run with the buttons.

## 2. Getting ready

- [ ] The screen turns landscape by itself, in whichever landscape direction the phone is held.
- [ ] The camera frame is pinned to the left, whole (not cropped), with **green corner marks** on its four corners.
- [ ] The **ghost target skeleton** (faint electric blue) is in the frame while no one is found, and **fades** once a plank is seen.
- [ ] The dashed frame and crosshair **disappear** the moment a body is found.
- [ ] **Turn the phone to the other landscape direction** mid-session: the picture and the skeleton are still upright.
- [ ] The controls: red record button, flip, close, "LOG", the mode strip. Flip switches camera; close leaves.
- [ ] Hints: step back so the **head** leaves the frame, and the **feet**: the hint says what to do.

## 3. Planking

- [ ] A **forearm plank** (feet on the floor): the form dots (shoulder, hip, knee) are **green**, the skeleton goes **gold**, a glow builds.
- [ ] A **knee plank** (knees down, hips in line) still counts. **On all fours** and **knees on the floor with hips up** do *not*: dots go orange, the skeleton goes grey.
- [ ] Holding a plank about **1 s** starts the timer by itself. The red record button also starts it.
- [ ] The camera **zooms away smoothly** into the field and the player ends up **centred**, with the green marks around them.
- [ ] Sounds: a quiet cluck loop throughout, a **louder cluck every 10 s**, a **squawk at 60 s**; then the timer counts up in gold.
- [ ] Only the red timer pill and the stop capsule are on screen. They do not cover the player.
- [ ] **Drop your hips** for a couple of seconds: a building **bwaaak** starts about 1 s in and the hint counts down. Get back up and it stops.
- [ ] **Get up and stay up**: the plank ends about 3 s later, and it is back-dated to when you left.
- [ ] Ending before 60 s: logged as incomplete, and you stay in the session to try again. Stop ends it.

## 4. The scene follows the phone *(unverified as of v0.6.0: please check)*

Prop the phone on the floor and start a plank. Then, **slowly**:

- [ ] **Turn the phone to the right:** the barn and trees should slide **left** (you are looking right). *(This direction has never been confirmed. If it is the wrong way round, flip the yaw sign in `rememberWorldView`.)*
- [ ] **Tilt the phone up:** the horizon moves **down**. **Tilt it down:** the horizon moves up.
- [ ] **Roll the phone:** the scene tilts the opposite way, and it never goes upside down.
- [ ] Propped and still: nothing jitters, and the horizon is where it started, not shoved off the screen.
- [ ] The log has `VIEW` lines, a `CAMERA_BOUND` line, and **no gap of a second or more in the frame times** around when the camera fades (a gap means the camera restarted).

## 5. The win

- [ ] After 60 s and getting up: a **golden egg** pops in the field, a **banner** slides in with the time, then the screen **turns to portrait** by itself and the card appears.
- [ ] The app does not crash at any point here. (A crash at exactly this moment has happened before.)
- [ ] **Share** opens the share sheet with a picture of the card. **Done** goes home, and today is on the calendar with the streak updated.

## 6. The card

- [ ] The replay plays, and **restarts** when it scrolls back into view. The scrubber works. The scene behind the skeleton is the farm.
- [ ] The form tile and the hip-angle graph (150-200°) look right. The replay stops at the end of the perfect form.
- [ ] Tap a past day on the calendar: its cards show, and several in one day swipe up and down.

## 7. Home, reminders, data

- [ ] The streak number is muted before today's plank and gold after. The calendar fits the screen (try a short phone or split screen).
- [ ] Menu, **Daily reminder**: turning it on asks for the notification permission; the test button delivers a cluck.
- [ ] Reminders arrive at 6:00 and 13:00 **without opening the app**, are not sent once today's plank is done, and the 21:00 streak saver arrives only if you have a streak. They still arrive after restarting the phone.
- [ ] **Back up data**, then on a fresh install (or after clearing storage) **Restore data**: the planks come back. Restoring the same file twice adds nothing.
- [ ] Restoring a file that is not a backup says so.
- [ ] **Copy pose log** contains the new `knee_deg` column and the `VIEW` lines.

## 8. Lifecycle

- [ ] Leaving the app mid-plank (home button, an incoming call) winds the session up instead of leaving it running.
- [ ] The screen stays on during a session and goes back to normal after.

## Tested on

| Date | Version | Device | Android | Notes |
|---|---|---|---|---|
| 2026-11 | 0.5.x-0.6.0 | Samsung Galaxy S25 | not recorded | Most of the session, card, backup and reminder checks. Not recorded as confirmed: the pan direction in section 4, a portrait session from a distance, and reminders after restarting the phone. |
