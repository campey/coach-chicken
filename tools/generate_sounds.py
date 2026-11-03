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


def mix(parts, min_sec=0.0):
    """parts: list of (start_sec, samples, gain); result is at least min_sec long."""
    total = max([int(min_sec * RATE)] + [int(s * RATE) + len(x) for s, x, _ in parts])
    buf = [0.0] * total
    for start, x, g in parts:
        o = int(start * RATE)
        for i, v in enumerate(x):
            buf[o + i] += v * g
    return buf


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
    # Quiet loop: a gentle, irregular murmur of clucks. Loops seamlessly (ends in silence).
    loop = mix([
        (0.00, cluck(), 1.0), (0.17, cluck(0.09, 880, 360), 0.8),
        (1.30, cluck(0.12, 1000, 400), 0.9),
        (2.45, cluck(), 0.7), (2.62, cluck(0.09, 900, 350), 0.9), (2.78, cluck(0.08, 820, 340), 0.6),
        (3.90, cluck(0.12, 960, 390), 0.8),
    ], min_sec=4.6)
    write("cluck_quiet_loop", loop, gain=0.9)
    # A longer, louder, more insistent cluck-cluck-cluck.
    loud = mix([(0.0, cluck(0.14, 1100, 420), 1.0), (0.2, cluck(0.12, 1000, 400), 0.9), (0.38, cluck(0.12, 950, 380), 0.8)])
    write("cluck_loud", loud, gain=1.0)
    write("squawk", squawk(), gain=1.0)


if __name__ == "__main__":
    main()
