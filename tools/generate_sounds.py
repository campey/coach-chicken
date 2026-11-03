#!/usr/bin/env python3
"""Synthesize placeholder chicken sounds into the app's res/raw (stdlib only).

Replace the generated WAVs with better recordings any time; the app only
refers to the file names (cluck_quiet_loop, cluck_loud, squawk).
"""
import math
import random
import struct
import wave
from pathlib import Path

RATE = 22050
OUT = Path(__file__).resolve().parent.parent / "android/app/src/main/res/raw"
rng = random.Random(7)


def cluck(dur=0.11, f0=950.0, f1=380.0):
    """One 'buk': a fast downward pitch sweep with harmonics and a bit of grit."""
    n = int(dur * RATE)
    out, phase = [], 0.0
    for i in range(n):
        t = i / n
        f = f0 + (f1 - f0) * t
        phase += 2 * math.pi * f / RATE
        s = math.sin(phase) + 0.5 * math.sin(2 * phase) + 0.25 * math.sin(3 * phase)
        s += 0.25 * (rng.random() * 2 - 1)
        env = min(1.0, t * 25) * math.exp(-4.5 * t)
        out.append(s * env)
    return out


def squawk(dur=0.9):
    n = int(dur * RATE)
    out, phase = [], 0.0
    for i in range(n):
        t = i / n
        f = 500 + 700 * math.sin(math.pi * min(1.0, t * 1.6)) + 40 * math.sin(2 * math.pi * 28 * t * dur)
        phase += 2 * math.pi * f / RATE
        saw = (phase / math.pi) % 2 - 1
        s = 0.8 * saw + 0.3 * (rng.random() * 2 - 1) + 0.3 * math.sin(2 * phase)
        env = min(1.0, t * 30) * (1 - t) ** 0.6
        out.append(s * env)
    return out


def bwaaak(dur=2.0):
    """The warning: a long, building 'bwaaaak!' that rises in pitch, loudness, vibrato and roughness,
    then snaps off on a hard 'k'. Length matches the grace period left after the warning starts."""
    n = int(dur * RATE)
    out, phase = [], 0.0
    for i in range(n):
        t = i / n
        f = 300 + 700 * t ** 1.5 + (45 * t) * math.sin(2 * math.pi * (6 + 8 * t) * t * dur)
        phase += 2 * math.pi * f / RATE
        saw = (phase / math.pi) % 2 - 1
        sq = 1.0 if math.sin(phase) > 0 else -1.0
        body = 0.55 * saw + (0.15 + 0.35 * t) * sq + 0.25 * math.sin(2 * phase)
        grit = (0.05 + 0.5 * t) * (rng.random() * 2 - 1)
        env = min(1.0, i / (0.04 * RATE)) * (0.3 + 0.7 * t ** 1.3)
        if t > 0.96:                                   # the 'k': a quick snap, mostly noise
            k = (t - 0.96) / 0.04
            env *= max(0.0, 1 - k)
            grit += 0.8 * (rng.random() * 2 - 1)
        out.append((body + grit) * env)
    return out


def mix(parts, min_sec=0.0):
    """parts: list of (start_sec, samples, gain); result is at least min_sec long."""
    total = max([int(min_sec * RATE)] + [int(s * RATE) + len(x) for s, x, _ in parts])
    buf = [0.0] * total
    for start, x, g in parts:
        o = int(start * RATE)
        for i, v in enumerate(x):
            buf[o + i] += v * g
    return buf


def louden(samples, active_rms_db, ceiling=0.98):
    """Raise the average loudness without clipping: apply gain, then soft-limit (tanh) towards the ceiling.
    Targets the RMS of the *active* parts (ignores near-silence), iterating because the limiter eats some of it."""
    win = int(0.02 * RATE)
    frames = [samples[i:i + win] for i in range(0, len(samples) - win, win)]

    def active_rms(xs):
        vals = [sum(v * v for v in f) / len(f) for f in frames_of(xs)]
        act = [v for v in vals if v > 1e-5]
        return math.sqrt(sum(act) / len(act)) if act else 1e-9

    def frames_of(xs):
        return [xs[i:i + win] for i in range(0, len(xs) - win, win)]

    peak = max(abs(v) for v in samples) or 1.0
    x = [v / peak for v in samples]
    target = 10 ** (active_rms_db / 20)
    gain = 1.0
    for _ in range(40):
        y = [ceiling * math.tanh(v * gain / ceiling) for v in x]
        r = active_rms(y)
        if abs(20 * math.log10(r / target)) < 0.1:
            break
        gain *= target / r
    return y


def write(name, samples, gain=0.9):
    peak = max(1e-9, max(abs(v) for v in samples))
    data = b"".join(struct.pack("<h", int(max(-1, min(1, v / peak * gain)) * 32767)) for v in samples)
    OUT.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT / f"{name}.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(data)


def main():
    # Quiet loop: a steady, irregular murmur of clucks (about a third of the time clucking). Loops seamlessly.
    loop = mix([
        (0.00, cluck(), 1.0), (0.17, cluck(0.09, 880, 360), 0.8), (0.33, cluck(0.08, 820, 340), 0.6),
        (0.80, cluck(0.12, 1000, 400), 0.9), (0.98, cluck(0.09, 900, 350), 0.7),
        (1.55, cluck(0.12, 960, 390), 0.9), (1.72, cluck(), 0.7), (1.88, cluck(0.08, 840, 350), 0.8),
        (2.45, cluck(), 0.8), (2.62, cluck(0.09, 900, 350), 0.9), (2.78, cluck(0.08, 820, 340), 0.6),
        (3.30, cluck(0.12, 980, 400), 0.9), (3.48, cluck(0.09, 880, 360), 0.8),
        (3.95, cluck(0.12, 960, 390), 0.8), (4.12, cluck(0.08, 860, 350), 0.7),
    ], min_sec=4.6)
    write("cluck_quiet_loop", louden(loop, -15.0), gain=0.98)
    # A longer, louder, more insistent cluck-cluck-cluck.
    loud = mix([(0.0, cluck(0.14, 1100, 420), 1.0), (0.2, cluck(0.12, 1000, 400), 0.9), (0.38, cluck(0.12, 950, 380), 0.8)])
    write("cluck_loud", louden(loud, -8.0), gain=0.98)
    write("squawk", louden(squawk(), -8.0), gain=0.98)
    write("bwaaak", louden(bwaaak(), -10.0), gain=0.98)


if __name__ == "__main__":
    main()
