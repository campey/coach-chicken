#!/usr/bin/env python3
"""Generate the rubber-chicken mascot as Android vector drawables (plus an SVG preview).

Lore: inspired by the rubber chicken with a pulley in the middle, and the squeaky rubber
chicken with a pivot neck found in Malaysia and used as an exercise squeaker during covid.
Hence: an upright, limp rubber chicken, long neck, mid-squeak "O" beak, and a bow tie.

Original artwork (flat shapes, no outlines), drawn in a 60 x 100 space.
  ic_chicken.xml          full body, for the in-app header
  ic_launcher_foreground  head shot, for the adaptive launcher icon
"""
import sys
from pathlib import Path

YEL, YEL_D, ORANGE = "#FFD43B", "#F2B705", "#F08C00"
RED, RED_D, DARK, MOUTH = "#E03131", "#A61E1E", "#2B2B2B", "#7A1F1F"
CX = 30


def circle(cx, cy, r):
    return f"M{cx - r},{cy} a{r},{r} 0 1,0 {2 * r},0 a{r},{r} 0 1,0 {-2 * r},0 Z"


def ellipse(cx, cy, rx, ry):
    return f"M{cx - rx},{cy} a{rx},{ry} 0 1,0 {2 * rx},0 a{rx},{ry} 0 1,0 {-2 * rx},0 Z"



# (kind, pathData, color, strokeWidth) -- kind: fill | stroke
def collar(y, hw):
    """Red neck band with a subtle bow tie."""
    return [
        ("fill", f"M{CX - hw},{y} C{CX - hw / 2},{y + 4} {CX + hw / 2},{y + 4} {CX + hw},{y} "
                 f"L{CX + hw},{y + 3.5} C{CX + hw / 2},{y + 7.5} {CX - hw / 2},{y + 7.5} {CX - hw},{y + 3.5} Z", RED, 0),
        ("fill", f"M{CX},{y + 5.2} L{CX - 6.5},{y + 2.2} L{CX - 6.5},{y + 8.2} Z", RED_D, 0),
        ("fill", f"M{CX},{y + 5.2} L{CX + 6.5},{y + 2.2} L{CX + 6.5},{y + 8.2} Z", RED_D, 0),
        ("fill", circle(CX, y + 5.2, 1.9), RED_D, 0),
    ]


HEAD = [  # three-quarter view, turned to face right (towards the squeak lines)
    ("fill", ellipse(28.5, 20, 13, 12), YEL, 0),                                           # head
    ("fill", ellipse(34, 23, 8, 9), YEL, 0),                                               # muzzle bulge
    ("fill", "M19,11 C14,5 17,0 21,4 C22,-1 27,0 27,5 C30,2 33,5 30,10 Z", RED, 0),        # crest, swept back
    ("fill", circle(27, 17, 4.8), DARK, 0), ("fill", circle(27, 17, 3.8), "#FFFFFF", 0),   # near eye
    ("fill", circle(36, 16.3, 4.2), DARK, 0), ("fill", circle(36, 16.3, 3.2), "#FFFFFF", 0),  # far eye
    ("fill", circle(28.2, 17.6, 1.8), DARK, 0), ("fill", circle(37, 16.9, 1.6), DARK, 0),
    ("fill", ellipse(36, 27, 6.2, 7.4), ORANGE, 0),                                        # beak (an "O")
    ("fill", ellipse(36.4, 27.6, 3.8, 5.2), MOUTH, 0),                                     # open mouth
    ("fill", ellipse(36.4, 30.4, 2.4, 1.8), RED, 0),                                       # tongue
]

SQUEAK_LINES = [
    ("stroke", "M47,10 L53,7", ORANGE, 1.8),
    ("stroke", "M48,18 L55,18", ORANGE, 1.8),
    ("stroke", "M47,26 L53,29", ORANGE, 1.8),
]

FULL_BODY = [
    ("fill", "M14,91 L27,91 L26,98 L23.5,95.5 L21,99 L18.5,95.5 L15,98 Z", RED, 0),        # left foot
    ("fill", "M33,91 L46,91 L45,98 L42.5,95.5 L40,99 L37.5,95.5 L34,98 Z", RED, 0),        # right foot
    ("fill", "M16,60 C11,70 12,80 15,92 L26,92 L29,79 L31,79 L34,92 L45,92 "
             "C48,80 49,70 44,60 Z", YEL, 0),                                              # torso + legs
    ("fill", "M41,62 C47,72 47,82 44,92 L39,92 C42,82 42,72 38,62 Z", YEL_D, 0),           # shading
    ("fill", "M18,61 C10,63 7,72 12,77 C16,75 19,68 21,63 Z", YEL_D, 0),                   # left wing
    ("fill", "M42,61 C50,63 53,72 48,77 C44,75 41,68 39,63 Z", YEL_D, 0),                  # right wing
    ("fill", "M24,28 L36,28 C37,42 40,52 45,61 L15,61 C20,52 23,42 24,28 Z", YEL, 0),      # neck
    ("fill", "M33,28 L36,28 C37,42 40,52 45,61 L41,61 C37,52 34,42 33,28 Z", YEL_D, 0),    # neck shading
] + collar(56, 14) + HEAD + SQUEAK_LINES

HEAD_SHOT = [
    ("fill", "M24,28 L36,28 L38,42 L22,42 Z", YEL, 0),                                     # neck stub
    ("fill", "M33,28 L36,28 L38,42 L34,42 Z", YEL_D, 0),
] + collar(37, 9) + HEAD


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
                      '    android:width="60dp" android:height="100dp"',
                      '    android:viewportWidth="60" android:viewportHeight="100">']
                     + vd_paths(FULL_BODY) + ["</vector>"]) + "\n"


# Head shot spans roughly x 17..43, y 2..45 -> scale 1.7 about its centre (31, 22) onto the 108 canvas centre.
HS_SCALE, HS_TX, HS_TY = 1.7, 54 - 31 * 1.7, 54 - 22 * 1.7


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
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="900" height="520">'
            '<rect width="300" height="520" fill="#fff"/><rect x="300" width="300" height="520" fill="#1c1b1f"/>'
            '<g transform="translate(30,10) scale(4.9)">' + svg_paths(FULL_BODY) + '</g>'
            '<g transform="translate(330,10) scale(4.9)">' + svg_paths(FULL_BODY) + '</g>'
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
