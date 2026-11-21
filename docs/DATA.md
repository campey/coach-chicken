# Data formats

Everything is plain text, on purpose: you can read it, paste it, diff it, and use real recordings as test fixtures. The code that
reads and writes each format is in `core` and has a round-trip test. **Every format has to stay readable by newer versions of the
app**; see [Compatibility](#compatibility).

## On the phone

The app stores two things in its private storage (`filesDir`):

| | |
|---|---|
| `sessions.csv` | One row per plank, finished or not. This is what the calendar and the streak are built from. |
| `planks/<start time in epoch ms>.txt` | The replay of one plank (see below). Written when a plank ends. |

The pose log (below) is **not** stored: it lives in memory, is cleared when a camera session opens, and is gone if the app dies.

## Session history: `sessions.csv`

```
startedAt,exercise,durationMs,completed
2026-11-19T19:54:44.762727Z,plank,62301,true
2026-11-03T18:39:14.887363Z,plank,6972,false
```

`startedAt` is an ISO-8601 instant in UTC; `completed` is true when the plank reached the goal (60 s). A session is identified by
its start time and exercise. Header, blank and malformed lines are ignored when reading.

## A plank recording: `planks/<ms>.txt`

The data behind the card's replay. One header line, then one row per sample (about 4 a second, from 10 s before the plank starts
to the end).

```
#plank v1 startedAt=2026-11-19T19:54:44.762727Z aspect=1.3333 mirror=true goalMs=60000 durationMs=62301 completed=true scene=farm target=0.7581;0.5110;-0.1801;0.5691;0.14;R
-5448,1,0.00,,0.403,0.010,0.737,0.250,...        <- t_ms, grade, aura, hip angle, then 19 joints x (x, y)
```

Header keys:

| Key | Meaning |
|---|---|
| `startedAt` | When the plank started (UTC). Also the file's name, in epoch milliseconds. |
| `aspect` | The camera picture's width / height. Landmarks are normalised, so this is needed to measure angles. |
| `mirror` | True for the front camera: the preview is mirrored, so x is flipped for display. |
| `goalMs`, `durationMs`, `completed` | The goal (60 s), how long the plank lasted, and whether it reached the goal. |
| `scene` | The location it happened in. Optional: missing or unknown means `farm`. |
| `target` | The green goal zone when the plank started: `ax;ay;bx;by;halfWidth;side` (two endpoints in normalised image coordinates, the half width in image-height units, and `L` or `R`). Optional. |

Row columns:

| Column | Meaning |
|---|---|
| `t_ms` | Milliseconds relative to the plank start (negative = lead-in). |
| `grade` | 0 `NO_BODY`, 1 `NOT_PLANK`, 2 `OK`, 3 `PERFECT`. |
| `aura` | The glow, 0..1. |
| `hip` | The hip angle in degrees (empty if it could not be measured, or if the legs were out of frame). |
| 38 values | x, y for each of the 19 logged joints, in this order: nose, left and right shoulder, elbow, wrist, hip, knee, ankle, then left and right eye, ear and mouth corner. Empty where a joint was not found. |

Frames with no body store no joints (whatever the model guessed was noise).

## Backup: `aura-farm-backup-<date>.zip`

A plain zip made through the system file picker: `sessions.csv`, `planks/<ms>.txt` (one per replay), and `about.txt` (a short note
with the app version). **Restoring merges and never overwrites or removes anything on the phone**: sessions are unioned by start
time and exercise, and replays are added only if that file does not exist yet. Entries with unexpected names are ignored, and a
file with neither sessions nor replays is rejected. Backups made by Coach Chicken (v0.5 and earlier) restore into Aura Farm
unchanged.

## The pose log

The **Copy log** button puts this on the clipboard. It is how the form checks are tuned from real planks, and how most bugs on
the phone are diagnosed. It holds the last 1000 lines.

```
# Aura Farm pose log. Rows: t_ms,phase,grade,plank_s,aura,tgt_off,aspect,side,lower,body_deg,hip_deg,knee_deg,...
# camera=front on=true app=0.6.0 (t_ms is the on-screen t= clock)
11720,READY,PERFECT,0.17,0.02,0.35,1.333,R,A,0.3,170.7,161.0,0.49,0.08,0.74,0.909,0.584,...
# 13469 PLANK_START
# 13587 VIEW rot=1 up_dev=0,96,0,00,0,29 screen_up=-0,00,0,96 yaw=0 pitch=0(abs -17) roll=0
```

Each data row is one frame (about 4 a second) and is **raw camera output** (not mirrored). The columns:

| Column | Meaning |
|---|---|
| `t_ms`, `phase` | Milliseconds since the camera opened (the on-screen `t=` clock shows the same number, so a screen recording lines up), and `READY` or `PLANKING`. |
| `grade` | `NO_BODY`, `NOT_PLANK`, `OK` or `PERFECT`. |
| `plank_s` | The tracker's filtered plank signal, 0..1. The plank starts above 0.7 and a lapse begins below 0.45. |
| `aura`, `tgt_off` | The glow; and the hip's distance from the green zone (1 = the zone's edge). |
| `aspect`, `side`, `lower` | Picture aspect; the side judged (`L`/`R`); the lowest leg point used: `A` ankle, `K` knee, `H` hip only (legs out of frame). |
| `body_deg` | Angle of the shoulder-to-lowest-point line above horizontal (measured against the camera, so it moves with the phone's tilt). |
| `hip_deg` | Angle at the hip between shoulder and the lowest point: 180 is straight. |
| `knee_deg` | Angle at the hip between shoulder and **knee**: 180 means the thigh continues the torso. Separates a plank from all fours. Empty when no knee is seen. |
| `elbow_drop`, `elbow_off` | How far the elbow is below, and sideways of, the shoulder, as a fraction of the torso. |
| `min_vis` | The lowest MediaPipe visibility among the joints used. |
| `nose_x … rmou_y` | x, y for the 19 joints (same order as in a recording). |

Event lines start with `#` and a time: `TRACKER_STARTED`, `PLANK_START`, `TRACKER_WARNING`, `TRACKER_RECOVERED`,
`TRACKER_ENDED`, `PLANK_END completed=… elapsedMs=…`, `FLIP_CAMERA`, `CAMERA_BOUND display_rot=… front=…` (when the camera attached,
and with which display rotation) and `VIEW …` (every half second: the display rotation, gravity in the phone's axes, where "up" is
on the screen, and the yaw, pitch and roll the scene is using; commas in these numbers are decimal commas in the app's locale).

## Compatibility

- **Never change a column's meaning, and add new columns at the end of a block** where you can. Older rows have fewer values: a
  recording row may carry 26 joint values (before eyes, ears and mouth were recorded), and readers pad the rest.
- New header keys must be optional, with a safe default. `scene` is the model: missing or unknown reads as `farm`.
- A recording that cannot be parsed returns null and is skipped; one bad file must never stop the others loading.
- The backup zip is a promise to the user. Test any change against `BackupTest`, and against a real backup if you have one.
- The real recordings used as test fixtures (`android/core/src/test/resources`, `android/app/src/test/resources/run.txt`) are
  kept in their original form on purpose: they prove old data still loads.
