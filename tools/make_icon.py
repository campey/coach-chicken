#!/usr/bin/env python3
"""Generate the Aura Farm launcher icon as Android vector drawables (stdlib only).

Writes, into android/app/src/main/res/drawable:
  ic_launcher_background.xml   flat blue gradient with a plain barn silhouette
  ic_launcher_foreground.xml   the AF letters (white, slanted, hard navy shadow), a cream aura glow round them and
                               white and gold four-point sparkles
  ic_launcher_monochrome.xml   one-colour version for Android 13+ themed icons (barn outline, letters, 3 sparkles)
Run it from anywhere: python3 tools/make_icon.py

The letters are drawn as polygons (no font), skewed to lean right, in a 108 x 108 adaptive-icon viewport; everything
that matters sits inside the 66 dp safe zone. The F follows the Fortnite-style F: thick stem, angled arm ends, and a
flat foot level with the A's.
"""
from pathlib import Path

LEAN, SCALE, GAP = 0.13, 0.54, 4
# glyph polygons in a 62-unit-tall box; a second polygon in A is its counter (the hole), drawn with the even-odd rule
A = [[(0, 62), (18, 0), (42, 0), (60, 62), (40, 62), (37, 48), (23, 48), (20, 62)], [(26, 36), (34, 36), (30, 20)]]
A_WIDTH = 60
F = [[(0, 0), (55, 0), (51, 17), (23, 17), (23, 28), (45, 28), (41, 43), (23, 43), (23, 62), (0, 62)]]
F_WIDTH = 55
TOP = 42                                   # where the letters start, from the top of the viewport
SHADOW = (2.4, 3.0)                        # offset of the hard shadow, in viewport units
BARN = "M10,108 L10,64 L22,38 L54,18 L86,38 L98,64 L98,108 Z"
NAVY = "#FF082F7A"
GLOW = [(10.0, "26"), (7.0, "3C"), (4.6, "5C"), (2.6, "99")]   # (stroke width, alpha) of the aura layers, cream
# (x, y, radius, colour of the star, colour of its soft halo)
WHITE, CREAM = "#FFFFFFFF", "#59FFF3C4"
YEL, YEL_HALO = None, "#66FFE082"
SPARKLES = [
    (88, 41, 7.5, WHITE, CREAM), (24, 47, 5.2, "#FFFFE000", YEL_HALO), (94, 76, 4.0, WHITE, CREAM),
    (28, 82, 3.6, "#FFFFD54F", YEL_HALO), (60, 36, 3.0, "#FFFFF176", YEL_HALO), (15, 66, 2.6, WHITE, CREAM),
]

HEADER = ('<?xml version="1.0" encoding="utf-8"?>\n'
          '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
          '    xmlns:aapt="http://schemas.android.com/aapt"\n'
          '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n')


def letters(dx=0.0, dy=0.0):
    total = (A_WIDTH + GAP + F_WIDTH + LEAN * 62) * SCALE
    left = 54 - total / 2
    d = []
    for polys, off in ((A, 0), (F, A_WIDTH + GAP)):
        for poly in polys:
            pts = [(left + dx + (x + off + LEAN * (62 - y)) * SCALE, TOP + dy + y * SCALE) for x, y in poly]
            d.append("M" + " L".join("%.2f,%.2f" % p for p in pts) + " Z")
    return " ".join(d)


def star(cx, cy, r, k=0.22):
    q = r * k
    return (f"M{cx:.2f},{cy - r:.2f} L{cx + q:.2f},{cy - q:.2f} L{cx + r:.2f},{cy:.2f} L{cx + q:.2f},{cy + q:.2f} "
            f"L{cx:.2f},{cy + r:.2f} L{cx - q:.2f},{cy + q:.2f} L{cx - r:.2f},{cy:.2f} L{cx - q:.2f},{cy - q:.2f} Z")


def halo(cx, cy, r):
    return f"M{cx - r * 0.9:.2f},{cy:.2f} a{r * 0.9:.2f},{r * 0.9:.2f} 0 1,0 {r * 1.8:.2f},0 a{r * 0.9:.2f},{r * 0.9:.2f} 0 1,0 {-r * 1.8:.2f},0 Z"


def background():
    return HEADER + f'''    <path android:pathData="M0,0 L108,0 L108,108 L0,108 Z">
        <aapt:attr name="android:fillColor">
            <gradient android:startX="0" android:startY="0" android:endX="108" android:endY="108" android:type="linear">
                <item android:offset="0" android:color="#FF1976E8" />
                <item android:offset="1" android:color="#FF5CD6FF" />
            </gradient>
        </aapt:attr>
    </path>
    <path android:fillColor="#FF0B4DB0" android:pathData="{BARN}" />
</vector>
'''


def foreground():
    main = letters()
    out = HEADER
    for w, a in GLOW:
        out += f'    <path android:pathData="{main}" android:strokeColor="#{a}FFF3C4" android:strokeWidth="{w}" android:strokeLineJoin="round" />\n'
    out += f'    <path android:fillColor="{NAVY}" android:pathData="{letters(*SHADOW)}" />\n'
    out += f'    <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{main}" />\n'
    for cx, cy, r, color, halo_color in SPARKLES:
        out += f'    <path android:fillColor="{halo_color}" android:pathData="{halo(cx, cy, r)}" />\n'
        out += f'    <path android:fillColor="{color}" android:pathData="{star(cx, cy, r)}" />\n'
    return out + "</vector>\n"


def monochrome():
    out = HEADER
    out += f'    <path android:pathData="{BARN}" android:strokeColor="#FF000000" android:strokeWidth="2.4" android:strokeLineJoin="round" />\n'
    out += f'    <path android:fillColor="#FF000000" android:fillType="evenOdd" android:pathData="{letters()}" />\n'
    for cx, cy, r, *_ in SPARKLES[:3]:
        out += f'    <path android:fillColor="#FF000000" android:pathData="{star(cx, cy, r)}" />\n'
    return out + "</vector>\n"


if __name__ == "__main__":
    res = Path(__file__).resolve().parent.parent / "android/app/src/main/res/drawable"
    (res / "ic_launcher_background.xml").write_text(background())
    (res / "ic_launcher_foreground.xml").write_text(foreground())
    (res / "ic_launcher_monochrome.xml").write_text(monochrome())
