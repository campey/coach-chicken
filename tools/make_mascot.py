#!/usr/bin/env python3
"""Generate the rubber-chicken mascot as an Android vector drawable (and an SVG preview).

Lore: inspired by the rubber chicken with a pulley in the middle, and the squeaky rubber
chicken with a pivot neck found in Malaysia and used as an exercise squeaker during covid.
So: limp rubber body, long neck with a visible pivot joint, open beak mid-squeak.
"""
import sys
from pathlib import Path

YEL, YEL_D, ORANGE, RED, DARK = "#FFD43B", "#F2B705", "#F08C00", "#E03131", "#2B2B2B"


def circle(cx, cy, r):
    return f"M{cx - r},{cy} a{r},{r} 0 1,0 {2 * r},0 a{r},{r} 0 1,0 {-2 * r},0 Z"


# (kind, pathData, color, strokeWidth) -- kind: fill | stroke
SHAPES = [
    ("stroke", "M44,86 L45,95 M42,95 L49,95", ORANGE, 2.6),            # left leg + foot
    ("stroke", "M60,87 L62,95 M58,95 L66,95", ORANGE, 2.6),            # right leg + foot
    ("fill", "M72,60 L88,50 L84,68 Z", YEL_D, 0),                        # tail
    ("fill", "M30,66 C30,52 44,48 56,50 C72,52 80,60 76,72 C72,84 50,88 38,82 C32,78 30,72 30,66 Z", YEL, 0),  # body
    ("fill", "M46,64 C52,78 70,78 74,66 C68,60 52,58 46,64 Z", YEL_D, 0),  # wing
    ("stroke", "M42,56 C40,50 46,44 42,36", YEL, 8.5),                  # neck
    ("fill", circle(42, 46, 5.2), ORANGE, 0),                            # pivot joint
    ("fill", circle(42, 46, 2.2), YEL, 0),
    ("fill", circle(37, 27, 10), YEL, 0),                                # head
    ("fill", circle(32, 17.5, 3.6) + circle(37.5, 15.5, 3.9) + circle(43, 18, 3.4), RED, 0),  # comb
    ("fill", "M28,25 L14,26 L28,30 Z", ORANGE, 0),                       # upper beak
    ("fill", "M28,31 L16,35 L28,35 Z", ORANGE, 0),                       # lower beak (open: squeak!)
    ("fill", circle(34, 37.5, 2.8), RED, 0),                               # wattle
    ("fill", circle(35, 24.5, 2.0), DARK, 0),                            # eye
    ("fill", circle(35.6, 23.8, 0.7), "#FFFFFF", 0),
]


def vector_drawable():
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="108dp" android:height="108dp"',
           '    android:viewportWidth="108" android:viewportHeight="108">']
    for kind, d, color, w in SHAPES:
        if kind == "fill":
            out.append(f'    <path android:fillColor="{color}" android:pathData="{d}" />')
        else:
            out.append(f'    <path android:strokeColor="{color}" android:strokeWidth="{w}" '
                       f'android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{d}" />')
    out.append("</vector>")
    return "\n".join(out) + "\n"


def svg(bg="#6750A4"):
    body = []
    for kind, d, color, w in SHAPES:
        if kind == "fill":
            body.append(f'<path fill="{color}" d="{d}"/>')
        else:
            body.append(f'<path fill="none" stroke="{color}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round" d="{d}"/>')
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="432" height="432">'
            f'<rect width="108" height="108" fill="{bg}"/>' + "".join(body) + "</svg>")


if __name__ == "__main__":
    root = Path(__file__).resolve().parent.parent
    (root / "android/app/src/main/res/drawable").mkdir(parents=True, exist_ok=True)
    (root / "android/app/src/main/res/drawable/ic_chicken.xml").write_text(vector_drawable())
    if len(sys.argv) > 1:
        Path(sys.argv[1]).write_text(svg())
