"""
Bonedle app icon, from one description of the art:

  art/icon.svg                                   master, full-bleed 108 x 108 (Play icon source)
  app/src/main/res/drawable/ic_launcher_background.xml   adaptive icon background (navy + grid)
  app/src/main/res/drawable/ic_launcher_foreground.xml   adaptive icon foreground (the domino)
  app/src/main/res/drawable/ic_launcher_monochrome.xml   themed icon (Android 13+)

A slanted ivory 5|2 domino on a navy board, lifted over a cell outline that glows from teal to
gold, as in the chosen concept. The domino is drawn flat and projected with a rotation followed
by a vertical squash: SVG does it with one transform, VectorDrawable with two nested groups
(rotation inside, scale outside), so both render the same art.

Run: python art/icon.py   (then render the Play icon: see art/README.md)
"""
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res", "drawable")

# App palette (app/src/main/kotlin/app/bonedle/ui/theme/BonedleColors.kt, dark theme).
NAVY_CENTER = "#1C2840"
NAVY_EDGE = "#0B111C"
TILE = "#F3EBDD"
TILE_SIDE = "#CDBFA5"
TILE_SHADE = "#B9AA8E"
DIVIDER = "#3A4252"
PIP = "#1B2230"
GOLD = "#F4B942"
TEAL = "#35C2B0"

SIZE = 108.0
CENTER = 54.0
ROTATION = -35.0  # degrees, the domino's long axis rising to the right
SQUASH = 0.74  # vertical scale after rotation: the board seen from above at an angle
LIFT = 6.0  # how far the glowing cell outline sits below the domino, in screen units
THICKNESS = 7.0  # the domino's edge, in screen units

# Launchers mask the icon tighter than the store does: the launcher foreground keeps the domino
# inside the 66dp safe zone of every mask shape. The Play icon and feature graphic use full size.
LAUNCHER_SCALE = 0.8

# Domino in its own flat coordinates, centred on the origin: 2:1.
W, H, R = 60.0, 30.0, 5.5
PIP_R = 3.0
# Standard die faces on a half of size H x H centred at (cx, 0).
FIVE = [(-1, -1), (1, -1), (0, 0), (-1, 1), (1, 1)]
TWO = [(-1, 1), (1, -1)]
PIP_STEP = 7.8


def rounded_rect(w, h, r, x=0.0, y=0.0):
    """Path data for a rounded rectangle centred on (x, y)."""
    l, t = x - w / 2, y - h / 2
    return (
        f"M{l + r:.3f},{t:.3f}h{w - 2 * r:.3f}a{r:.3f},{r:.3f} 0 0 1 {r:.3f},{r:.3f}"
        f"v{h - 2 * r:.3f}a{r:.3f},{r:.3f} 0 0 1 -{r:.3f},{r:.3f}"
        f"h-{w - 2 * r:.3f}a{r:.3f},{r:.3f} 0 0 1 -{r:.3f},-{r:.3f}"
        f"v-{h - 2 * r:.3f}a{r:.3f},{r:.3f} 0 0 1 {r:.3f},-{r:.3f}z"
    )


def circle(cx, cy, r):
    return f"M{cx - r:.3f},{cy:.3f}a{r:.3f},{r:.3f} 0 1 0 {2 * r:.3f},0a{r:.3f},{r:.3f} 0 1 0 -{2 * r:.3f},0z"


def pips(face, cx):
    return [(cx + dx * PIP_STEP, dy * PIP_STEP) for dx, dy in face]


LEFT_PIPS = pips(FIVE, -W / 4)
RIGHT_PIPS = pips(TWO, W / 4)


def grid_lines(step=24.0, extent=140.0):
    """Board grid in flat coordinates; projected with the domino it lines up with its plane."""
    lines = []
    k = -extent + step / 2
    while k <= extent:
        lines.append(f"M{k:.2f},{-extent:.2f}V{extent:.2f}")
        lines.append(f"M{-extent:.2f},{k:.2f}H{extent:.2f}")
        k += step
    # Offset so a grid cell sits under the domino.
    return "".join(lines)


# ---------------------------------------------------------------- the art as layers
# Each layer: (name, flat path data, fill, stroke, stroke width, alpha, screen translateY).
# Screen translateY is applied after the projection (a lift on the screen).


def layers_background():
    return [
        ("grid", grid_lines(), None, "#05080F", 1.6, 0.55, 0.0),
        ("grid_light", grid_lines(), None, "#FFFFFF", 0.5, 0.05, -1.0),
    ]


def layers_foreground(monochrome=False):
    ink = "#FFFFFF"
    out = []
    # Glowing cell outline under the domino: wide faint strokes, then the crisp one.
    outline = rounded_rect(W + 9, H + 9, R + 4)
    if not monochrome:
        for width, alpha in ((12.0, 0.10), (8.0, 0.16), (5.0, 0.26), (3.2, 0.45)):
            out.append(("glow", outline, None, "gradient", width, alpha, LIFT))
        out.append(("outline", outline, None, "gradient", 2.0, 1.0, LIFT))
    else:
        out.append(("outline", outline, None, ink, 1.6, 1.0, LIFT))
    # The slab: copies of the outline from the bottom face up to the top give its edge.
    steps = 14
    for i in range(steps, 0, -1):
        lift = THICKNESS * i / steps
        fill = (TILE_SHADE if i > steps - 3 else TILE_SIDE) if not monochrome else ink
        out.append(("edge", rounded_rect(W, H, R), fill, None, 0.0, 0.55 if monochrome else 1.0, lift))
    if monochrome:
        # Themed icons are one colour: the spots and the divider are holes in the top face.
        holes = "".join(circle(x, y, PIP_R) for x, y in LEFT_PIPS + RIGHT_PIPS)
        divider = f"M-0.8,{-H / 2 + 4:.3f}h1.6v{H - 8:.3f}h-1.6z"
        out.append(("top_cut", rounded_rect(W, H, R) + holes + divider, ink, None, 0.0, 1.0, 0.0))
        return out
    out.append(("top", rounded_rect(W, H, R), TILE, None, 0.0, 1.0, 0.0))
    out.append(("divider", f"M0,{-H / 2 + 4:.3f}V{H / 2 - 4:.3f}", None, DIVIDER, 1.9, 1.0, 0.0))
    out.append(("pips5", "".join(circle(x, y, PIP_R) for x, y in LEFT_PIPS), PIP, None, 0.0, 1.0, 0.0))
    out.append(("pips2", "".join(circle(x, y, PIP_R) for x, y in RIGHT_PIPS), GOLD, None, 0.0, 1.0, 0.0))
    return out


# ---------------------------------------------------------------- SVG


def svg_layer(foreground, width=108.0, height=108.0):
    """One layer alone, for compositions such as the feature graphic. The background layer can
    be wider than the icon ([width]) and keeps the domino's grid centred."""
    cx = width / 2
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width:g} {height:g}">',
        "<defs>",
        f'<radialGradient id="bg" cx="30%" cy="42%" r="80%"><stop offset="0" stop-color="{NAVY_CENTER}"/>'
        f'<stop offset="1" stop-color="{NAVY_EDGE}"/></radialGradient>',
        f'<linearGradient id="glow" gradientUnits="userSpaceOnUse" x1="{-W / 2:g}" y1="0" x2="{W / 2:g}" y2="0">'
        f'<stop offset="0" stop-color="{TEAL}"/><stop offset="1" stop-color="{GOLD}"/></linearGradient>',
        "</defs>",
    ]
    layers = layers_foreground() if foreground else layers_background()
    if not foreground:
        parts.append(f'<rect width="{width:g}" height="{height:g}" fill="url(#bg)"/>')
    for name, d, fill, stroke, w, alpha, lift in layers:
        t = f"translate({cx:g} {height / 2 + lift:g}) scale(1 {SQUASH:g}) rotate({ROTATION:g})"
        attrs = [f'd="{d}"', f'transform="{t}"', f'fill="{fill or "none"}"']
        if stroke:
            attrs += [f'stroke="{"url(#glow)" if stroke == "gradient" else stroke}"', f'stroke-width="{w:g}"',
                      'stroke-linecap="round"', 'vector-effect="non-scaling-stroke"']
        if alpha < 1:
            attrs.append(f'opacity="{alpha:g}"')
        parts.append("<path " + " ".join(attrs) + "/>")
    parts.append("</svg>")
    return "\n".join(parts) + "\n"


def svg(monochrome=False):
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {SIZE:g} {SIZE:g}" width="512" height="512">',
        "<defs>",
        f'<radialGradient id="bg" cx="50%" cy="42%" r="70%"><stop offset="0" stop-color="{NAVY_CENTER}"/>'
        f'<stop offset="1" stop-color="{NAVY_EDGE}"/></radialGradient>',
        # Gradient along the domino's long axis, in flat coordinates (before the projection).
        f'<linearGradient id="glow" gradientUnits="userSpaceOnUse" x1="{-W / 2:g}" y1="0" x2="{W / 2:g}" y2="0">'
        f'<stop offset="0" stop-color="{TEAL}"/><stop offset="1" stop-color="{GOLD}"/></linearGradient>',
        "</defs>",
        f'<rect width="{SIZE:g}" height="{SIZE:g}" fill="url(#bg)"/>',
    ]

    def emit(layers):
        for _, d, fill, stroke, width, alpha, lift in layers:
            t = f"translate({CENTER:g} {CENTER + lift:g}) scale(1 {SQUASH:g}) rotate({ROTATION:g})"
            attrs = [f'd="{d}"', f'transform="{t}"', f'fill="{fill or "none"}"']
            if stroke:
                attrs.append(f'stroke="{"url(#glow)" if stroke == "gradient" else stroke}"')
                attrs.append(f'stroke-width="{width:g}"')
                attrs.append('stroke-linecap="round"')
                attrs.append('vector-effect="non-scaling-stroke"')
            if alpha < 1:
                attrs.append(f'opacity="{alpha:g}"')
            if _ == "top_cut":
                attrs.append('fill-rule="evenodd"')
            parts.append("<path " + " ".join(attrs) + "/>")

    emit(layers_background())
    emit(layers_foreground(monochrome))
    parts.append("</svg>")
    return "\n".join(parts) + "\n"


# ---------------------------------------------------------------- VectorDrawable

HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by art/icon.py; edit the script, not this file. {what} -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
"""


def stroke_width_flat(width):
    # VectorDrawable scales strokes with the group transform; SVG above does not
    # (non-scaling-stroke). Compensate with the average scale of the projection.
    return width / math.sqrt(SQUASH)


def vector(layers, what, background=None, scale=1.0):
    out = [HEADER.format(what=what)]
    if background:
        out.append(background)
    if scale != 1.0:
        out.append(f'    <group android:pivotX="{CENTER:g}" android:pivotY="{CENTER:g}" android:scaleX="{scale:g}" android:scaleY="{scale:g}">\n')
    for _, d, fill, stroke, width, alpha, lift in layers:
        out.append(
            f'    <group android:translateX="{CENTER:g}" android:translateY="{CENTER + lift:g}" android:scaleY="{SQUASH:g}">\n'
            f'        <group android:rotation="{ROTATION:g}">\n'
        )
        attrs = [f'android:pathData="{d}"']
        if fill:
            attrs.append(f'android:fillColor="{fill}"')
        if alpha < 1:
            attrs.append(f'android:fillAlpha="{alpha:g}"' if fill else f'android:strokeAlpha="{alpha:g}"')
        if _ == "top_cut":
            attrs.append('android:fillType="evenOdd"')
        if stroke and stroke != "gradient":
            attrs += [f'android:strokeColor="{stroke}"', f'android:strokeWidth="{stroke_width_flat(width):.3f}"', 'android:strokeLineCap="round"']
        body = "\n                ".join(attrs)
        if stroke == "gradient":
            out.append(
                f'            <path\n                {body}\n'
                f'                android:strokeWidth="{stroke_width_flat(width):.3f}"\n'
                f'                android:strokeLineCap="round">\n'
                f'                <aapt:attr name="android:strokeColor">\n'
                f'                    <gradient android:type="linear" android:startX="{-W / 2:g}" android:startY="0" android:endX="{W / 2:g}" android:endY="0"'
                f' android:startColor="{TEAL}" android:endColor="{GOLD}" />\n'
                f'                </aapt:attr>\n'
                f'            </path>\n'
            )
        else:
            out.append(f'            <path\n                {body} />\n')
        out.append("        </group>\n    </group>\n")
    if scale != 1.0:
        out.append("    </group>\n")
    out.append("</vector>\n")
    return "".join(out)


BACKGROUND_FILL = f"""    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="54" android:centerY="45.36" android:gradientRadius="75.6"
                android:startColor="{NAVY_CENTER}" android:endColor="{NAVY_EDGE}" />
        </aapt:attr>
    </path>
"""


def main():
    os.makedirs(os.path.join(ROOT, "art"), exist_ok=True)
    with open(os.path.join(ROOT, "art", "icon.svg"), "w", encoding="utf-8", newline="\n") as f:
        f.write(svg())
    # Layers for the feature graphic (art/store/feature-graphic.html): a 1024 x 500 banner is
    # 221 x 108 icon units.
    with open(os.path.join(ROOT, "art", "store", "banner-background.svg"), "w", encoding="utf-8", newline="\n") as f:
        f.write(svg_layer(foreground=False, width=221.2, height=108.0))
    with open(os.path.join(ROOT, "art", "store", "domino.svg"), "w", encoding="utf-8", newline="\n") as f:
        f.write(svg_layer(foreground=True))
    files = {
        "ic_launcher_background.xml": vector(layers_background(), "Navy board with its grid.", BACKGROUND_FILL),
        "ic_launcher_foreground.xml": vector(
            layers_foreground(), "The 5|2 domino over its glowing cell; inside the 66dp safe zone.", scale=LAUNCHER_SCALE
        ),
        "ic_launcher_monochrome.xml": vector(
            layers_foreground(monochrome=True), "Themed icon: the domino and its cell as one shape.", scale=LAUNCHER_SCALE
        ),
    }
    for name, text in files.items():
        with open(os.path.join(RES, name), "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
    print("wrote art/icon.svg and", ", ".join(files))


if __name__ == "__main__":
    main()
