"""
Play Store listing screenshots (spec 12): eight 1080 x 1920 phone screenshots with one benefit
headline each, in the app's palette and fonts. (The feature graphic is feature-graphic.html.)

Inputs: art/store/screens/*.png (real app screens, captured by StoreScreensTest:
  ./gradlew :app:recordRoborazziDebug --tests "*StoreScreensTest*")
Output: art/store/screenshots/NN-name.png

Run: python art/store/compose.py
"""
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
FONTS = os.path.join(ROOT, "app", "src", "main", "res", "font")

BG_TOP = (11, 17, 28)
BG_BOTTOM = (28, 40, 64)
TEXT = (244, 239, 230)
TEXT_SECONDARY = (169, 179, 196)
GOLD = (244, 185, 66)
BEZEL = (5, 8, 15)

# (screen file, gold label, headline). Every claim is something the app does.
SHOTS = [
    ("01-play", "DOMINO LOGIC", "Place every domino. Keep every rule."),
    ("02-home", "DAILY BOARDS", "Three daily boards, the same for everyone"),
    ("03-rules", "SIMPLE RULES", "Easy to learn. Every board has exactly one solution."),
    ("04-hints", "HINTS THAT TEACH", "Stuck? Get the next logical step, explained."),
    ("05-unlimited", "UNLIMITED", "Endless boards at four difficulties"),
    ("06-head-to-head", "CHALLENGE FRIENDS", "Send a board and see who solves it faster"),
    ("07-stats", "STREAKS & STATS", "Build your streak and track every solve"),
    ("08-learn", "PLAYS OFFLINE", "Learn in a minute. No account, no sign-up."),
]


def font(name, size, weight):
    f = ImageFont.truetype(os.path.join(FONTS, name), size)
    f.set_variation_by_name(weight)
    return f


def gradient(w, h, top, bottom):
    img = Image.new("RGB", (w, h), top)
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / (h - 1)
        d.line([(0, y), (w, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))
    return img


def glow(img, center, radius, color, strength):
    layer = Image.new("RGB", img.size, (0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, cy = center
    d.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=color)
    layer = layer.filter(ImageFilter.GaussianBlur(radius / 2))
    return Image.blend(img, Image.composite(layer, img, layer.convert("L")), strength)


def wrap(draw, text, fnt, width):
    lines, line = [], ""
    for word in text.split():
        trial = f"{line} {word}".strip()
        if draw.textlength(trial, font=fnt) <= width:
            line = trial
        else:
            lines.append(line)
            line = word
    lines.append(line)
    return lines


def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return m


def phone(screen, height):
    """The screen in a slim phone frame, with a status bar strip in the screen's colour."""
    status_h = round(height * 0.032)
    screen_h = height - status_h
    screen_w = round(screen.width * screen_h / screen.height)
    shot = screen.resize((screen_w, screen_h), Image.LANCZOS)
    top_color = screen.getpixel((screen.width // 2, 2))
    content = Image.new("RGB", (screen_w, height), top_color)
    content.paste(shot, (0, status_h))
    d = ImageDraw.Draw(content)
    luminance = sum(top_color) / 3
    ink = (40, 44, 52) if luminance > 128 else (230, 232, 236)
    d.text((round(screen_w * 0.07), status_h // 2), "9:41", font=font("dm_sans_variable.ttf", round(status_h * 0.5), "SemiBold"), fill=ink, anchor="lm")
    bezel = round(height * 0.012)
    radius = round(screen_w * 0.11)
    frame = Image.new("RGBA", (screen_w + 2 * bezel, height + 2 * bezel), (0, 0, 0, 0))
    ImageDraw.Draw(frame).rounded_rectangle((0, 0, frame.width - 1, frame.height - 1), radius=radius + bezel, fill=BEZEL + (255,), outline=(52, 64, 86, 255), width=2)
    frame.paste(content, (bezel, bezel), rounded_mask(content.size, radius))
    return frame


def screenshot(name, label, headline, out):
    w, h = 1080, 1920
    img = gradient(w, h, BG_TOP, BG_BOTTOM)
    img = glow(img, (w // 2, 1240), 420, GOLD, 0.18)
    d = ImageDraw.Draw(img)
    label_font = font("dm_sans_variable.ttf", 34, "Bold")
    head_font = font("sora_variable.ttf", 70, "Bold")
    y = 118
    # Letter-spaced gold label.
    spaced = " ".join(label)
    d.text((w // 2, y), spaced, font=label_font, fill=GOLD, anchor="mt")
    y += 70
    for line in wrap(d, headline, head_font, 920):
        d.text((w // 2, y), line, font=head_font, fill=TEXT, anchor="mt")
        y += 86
    screen = Image.open(os.path.join(HERE, "screens", name + ".png")).convert("RGB")
    top = max(y + 46, 470)
    frame = phone(screen, h - top - 64)
    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    x = (w - frame.width) // 2
    ImageDraw.Draw(shadow).rounded_rectangle((x + 10, top + 30, x + frame.width - 10, top + frame.height + 20), radius=80, fill=(0, 0, 0, 150))
    shadow = shadow.filter(ImageFilter.GaussianBlur(36))
    img = Image.alpha_composite(img.convert("RGBA"), shadow)
    img.alpha_composite(frame, (x, top))
    img.convert("RGB").save(out, optimize=True)


def main():
    out_dir = os.path.join(HERE, "screenshots")
    os.makedirs(out_dir, exist_ok=True)
    for name, label, headline in SHOTS:
        screenshot(name, label, headline, os.path.join(out_dir, name + ".png"))
    print(f"wrote {len(SHOTS)} screenshots")


if __name__ == "__main__":
    main()
