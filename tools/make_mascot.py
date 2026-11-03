#!/usr/bin/env python3
"""Generate the rubber-chicken mascot as Android vector drawables (plus an SVG preview).

Lore: inspired by the rubber chicken with a pulley in the middle, and the squeaky rubber
chicken with a pivot neck found in Malaysia and used as an exercise squeaker during covid.
Hence: an upright, limp, gangly rubber chicken: long neck, small head turned to the side,
a mid-squeak "O" beak with rays fanning out of it, and a bow tie.

Original artwork (flat shapes, no outlines), drawn in a 56 x 110 space.
  ic_chicken.xml          full body, for the in-app header
  ic_launcher_foreground  head shot, for the adaptive launcher icon
"""
import math
import sys
from pathlib import Path

YEL, YEL_D, ORANGE = "#FFD43B", "#F2B705", "#F08C00"
RED, RED_D, DARK, MOUTH = "#E03131", "#A61E1E", "#2B2B2B", "#7A1F1F"
CX = 24.5                 # body centre line
MOUTH_C = (30.8, 18.0)    # centre of the open mouth; the rays fan out from here


def circle(cx, cy, r):
    return ellipse(cx, cy, r, r)


def ellipse(cx, cy, rx, ry):
    return f"M{cx - rx},{cy} a{rx},{ry} 0 1,0 {2 * rx},0 a{rx},{ry} 0 1,0 {-2 * rx},0 Z"


# (kind, pathData, color, strokeWidth) -- kind: fill | stroke
def collar(y, hw):
    """Red neck band with a subtle bow tie."""
    return [
        ("fill", f"M{CX - hw},{y} C{CX - hw / 2},{y + 3.5} {CX + hw / 2},{y + 3.5} {CX + hw},{y} "
                 f"L{CX + hw},{y + 3} C{CX + hw / 2},{y + 6.5} {CX - hw / 2},{y + 6.5} {CX - hw},{y + 3} Z", RED, 0),
        ("fill", f"M{CX},{y + 4.6} L{CX - 5},{y + 2.2} L{CX - 5},{y + 7} Z", RED_D, 0),
        ("fill", f"M{CX},{y + 4.6} L{CX + 5},{y + 2.2} L{CX + 5},{y + 7} Z", RED_D, 0),
        ("fill", circle(CX, y + 4.6, 1.6), RED_D, 0),
    ]


def rays():
    """Squeak rays fanning out of the mouth, longest straight ahead."""
    out = []
    for deg, r2 in [(-52, 15.5), (-26, 17), (0, 19), (26, 17), (52, 15.5)]:
        a = math.radians(deg)
        x1, y1 = MOUTH_C[0] + 8.5 * math.cos(a), MOUTH_C[1] + 8.5 * math.sin(a)
        x2, y2 = MOUTH_C[0] + r2 * math.cos(a), MOUTH_C[1] + r2 * math.sin(a)
        out.append(("stroke", f"M{x1:.1f},{y1:.1f} L{x2:.1f},{y2:.1f}", ORANGE, 1.7))
    return out


HEAD = [  # small head, three-quarter view turned to face right
    ("fill", ellipse(25.5, 13, 7, 6.8), YEL, 0),                                           # head
    ("fill", ellipse(29, 16, 5, 6), YEL, 0),                                               # muzzle bulge
    ("fill", "M19,8 C17.5,5 16.5,2.5 17.5,0.5 C20.5,1.5 23.5,3.5 26,7.5 Z", RED, 0),       # crest: one swept-back blade
    ("fill", circle(23.8, 10.8, 3.3), DARK, 0), ("fill", circle(23.8, 10.8, 2.6), "#FFFFFF", 0),     # near eye
    ("fill", circle(29.6, 10.3, 2.9), DARK, 0), ("fill", circle(29.6, 10.3, 2.2), "#FFFFFF", 0),     # far eye
    ("fill", circle(24.5, 11.6, 1.2), DARK, 0), ("fill", circle(30.1, 11.0, 1.05), DARK, 0),          # pupils look at the mouth
    ("fill", ellipse(30.5, 17.5, 3.9, 4.8), ORANGE, 0),                                    # beak (an "O")
    ("fill", ellipse(MOUTH_C[0], MOUTH_C[1], 2.4, 3.4), MOUTH, 0),                         # open mouth
    ("fill", ellipse(MOUTH_C[0], MOUTH_C[1] + 1.9, 1.5, 1.1), RED, 0),                     # tongue
]

FULL_BODY = [
    ("fill", "M14.5,99 L24.5,99 L23.5,106 L21.5,104 L19.5,107.5 L17.5,104 L15.5,106 Z", RED, 0),       # left foot
    ("fill", "M25.5,99 L35.5,99 L34.5,106 L32.5,104 L30.5,107.5 L28.5,104 L26.5,106 Z", RED, 0),       # right foot
    ("fill", "M13.5,51 C10.5,61 11.5,72 15,82 L16,100 L23,100 L24,86 L25,86 L26,100 L33,100 L34,82 "
             "C37.5,72 38.5,61 35.5,51 Z", YEL, 0),                                                    # torso + legs
    ("fill", "M33,52 C38,62 37.5,72 34,82 L33,100 L29.5,100 L30.5,84 C33,72 33,62 30.5,52 Z", YEL_D, 0),  # shading
    ("fill", "M14.5,53 C9,56 8,65 11,70 C14,67 16,60 17.5,55 Z", YEL_D, 0),                            # left wing, tucked
    ("fill", "M34.5,53 C40,56 41,65 38,70 C35,67 33,60 31.5,55 Z", YEL_D, 0),                          # right wing, tucked
    ("fill", "M21,18 L28,18 C29,30 31,42 35,50 L14,50 C18,42 20,30 21,18 Z", YEL, 0),                  # neck
    ("fill", "M26.5,18 L28,18 C29,30 31,42 35,50 L32,50 C29,42 27.5,30 26.5,18 Z", YEL_D, 0),          # neck shading
] + collar(47, 10.5) + HEAD + rays()

HEAD_SHOT = [
    ("fill", "M21,18 L28,18 L30,32 L19,32 Z", YEL, 0),                                                 # neck stub
    ("fill", "M26.5,18 L28,18 L30,32 L27,32 Z", YEL_D, 0),
] + collar(28.5, 8.5) + HEAD


def vd_paths(shapes, indent="    "):
    out = []
    for kind, d, color, w in shapes:
        if kind == "fill":
            out.append(f'{indent}<path android:fillColor="{color}" android:pathData="{d}" />')
        else:
            out.append(f'{indent}<path android:strokeColor="{color}" android:strokeWidth="{w}" '
                       f'android:strokeLineCap="round" android:pathData="{d}" />')
    return out


def full_body_xml():
    return "\n".join(['<?xml version="1.0" encoding="utf-8"?>',
                      '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
                      '    android:width="56dp" android:height="110dp"',
                      '    android:viewportWidth="56" android:viewportHeight="110">']
                     + vd_paths(FULL_BODY) + ["</vector>"]) + "\n"


# Head shot spans roughly x 16..37, y 0.5..36 -> scale about its centre (26.5, 18) onto the 108 canvas centre.
HS_SCALE, HS_TX, HS_TY = 2.0, 54 - 26.5 * 2.0, 54 - 18 * 2.0


def launcher_xml():
    return "\n".join(['<?xml version="1.0" encoding="utf-8"?>',
                      '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
                      '    android:width="108dp" android:height="108dp"',
                      '    android:viewportWidth="108" android:viewportHeight="108">',
                      f'    <group android:scaleX="{HS_SCALE}" android:scaleY="{HS_SCALE}" '
                      f'android:translateX="{HS_TX:.2f}" android:translateY="{HS_TY:.2f}">']
                     + vd_paths(HEAD_SHOT, "        ") + ["    </group>", "</vector>"]) + "\n"


def svg_paths(shapes):
    out = []
    for kind, d, color, w in shapes:
        if kind == "fill":
            out.append(f'<path fill="{color}" d="{d}"/>')
        else:
            out.append(f'<path fill="none" stroke="{color}" stroke-width="{w}" stroke-linecap="round" d="{d}"/>')
    return "".join(out)


def preview_svg():
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="900" height="540">'
            '<rect width="300" height="540" fill="#fff"/><rect x="300" width="300" height="540" fill="#1c1b1f"/>'
            '<g transform="translate(20,10) scale(4.6)">' + svg_paths(FULL_BODY) + '</g>'
            '<g transform="translate(320,10) scale(4.6)">' + svg_paths(FULL_BODY) + '</g>'
            '<clipPath id="m"><circle cx="54" cy="54" r="36"/></clipPath>'
            '<g transform="translate(620,100) scale(2.6)"><rect width="108" height="108" fill="#6750A4"/>'
            f'<g clip-path="url(#m)"><g transform="translate({HS_TX},{HS_TY}) scale({HS_SCALE})">'
            + svg_paths(HEAD_SHOT) + '</g></g></g></svg>')


if __name__ == "__main__":
    res = Path(__file__).resolve().parent.parent / "android/app/src/main/res/drawable"
    res.mkdir(parents=True, exist_ok=True)
    (res / "ic_chicken.xml").write_text(full_body_xml())
    (res / "ic_launcher_foreground.xml").write_text(launcher_xml())
    if len(sys.argv) > 1:
        Path(sys.argv[1]).write_text(preview_svg())
