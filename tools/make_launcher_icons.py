#!/usr/bin/env python3
"""
Renders the KadePOS launcher icons from the master mark in docs/logo/.

Produces:
  * drawable-nodpi/ic_launcher_foreground.png
        the white silhouette used by the adaptive icon on Android 8+
  * mipmap-*/ic_launcher.png and mipmap-*/ic_launcher_round.png
        full square / circular icons for older launchers

Everything is derived from docs/logo/kadepos-mark-source.png so the mark is
never redrawn by hand. Requires Pillow:  pip install Pillow
"""
import os
import sys

try:
    from PIL import Image, ImageChops, ImageDraw, ImageFilter
except ImportError:  # pragma: no cover - developer convenience
    sys.exit("Pillow is required: pip install Pillow")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/src/main/res")
SOURCE = os.path.join(ROOT, "docs/logo/kadepos-mark-source.png")

# density -> launcher icon size in px
SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# Adaptive icon canvas is 108dp; the safe zone is the middle 66dp.
FOREGROUND_CANVAS = 432
FOREGROUND_SAFE = int(FOREGROUND_CANVAS * 66.0 / 108.0)

# "paper & ink" palette, matching ui/theme/Color.kt
INK_DARK = (22, 58, 90)     # #163A5A
INK = (31, 78, 121)         # #1F4E79
INK_LIGHT = (46, 110, 168)  # #2E6EA8
WHITE = (255, 255, 255, 255)


def alpha_from_white(img):
    """Turns a near-white background into transparency, keeping soft edges."""
    rgb = img.convert("RGB")
    white = Image.new("RGB", rgb.size, (255, 255, 255))
    diff = ImageChops.difference(rgb, white).convert("L")
    # 0 = identical to the paper, 255 = fully printed
    alpha = diff.point(lambda p: 0 if p < 24 else min(255, int((p - 24) * 1.6)))
    out = rgb.convert("RGBA")
    out.putalpha(alpha)
    return out


def trimmed(img):
    """Crops to the printed mark, dropping the empty paper around it."""
    bbox = img.getchannel("A").point(lambda p: 255 if p > 8 else 0).getbbox()
    return img.crop(bbox) if bbox else img


def silhouette(img, colour=WHITE):
    """A single-colour version of the mark (used on the coloured tile)."""
    alpha = img.getchannel("A")
    out = Image.new("RGBA", img.size, colour)
    out.putalpha(alpha)
    return out


def gradient(size, top, bottom):
    """Vertical two-stop gradient tile."""
    tile = Image.new("RGB", (size, size))
    draw = ImageDraw.Draw(tile)
    for y in range(size):
        t = y / float(max(1, size - 1))
        draw.line(
            [(0, y), (size, y)],
            fill=(
                int(top[0] + (bottom[0] - top[0]) * t),
                int(top[1] + (bottom[1] - top[1]) * t),
                int(top[2] + (bottom[2] - top[2]) * t),
            ),
        )
    return tile.convert("RGBA")


def fit_square(img, box):
    """Scales `img` so it fits inside `box` px, keeping its aspect ratio."""
    w, h = img.size
    scale = min(float(box) / w, float(box) / h)
    return img.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.LANCZOS)


def centre(base, layer):
    x = (base.width - layer.width) // 2
    y = (base.height - layer.height) // 2
    base.alpha_composite(layer, (x, y))


def circle_mask(size):
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    return mask.filter(ImageFilter.GaussianBlur(size / 220.0))


def main():
    if not os.path.exists(SOURCE):
        sys.exit("Missing %s" % SOURCE)

    mark = trimmed(alpha_from_white(Image.open(SOURCE)))

    # 1. Adaptive icon foreground: white silhouette inside the safe zone.
    fg = Image.new("RGBA", (FOREGROUND_CANVAS, FOREGROUND_CANVAS), (0, 0, 0, 0))
    centre(fg, silhouette(fit_square(mark, FOREGROUND_SAFE)))
    nodpi = os.path.join(RES, "drawable-nodpi")
    os.makedirs(nodpi, exist_ok=True)
    fg.save(os.path.join(nodpi, "ic_launcher_foreground.png"))
    print("wrote drawable-nodpi/ic_launcher_foreground.png")

    # 2. Legacy square / round icons for launchers older than Android 8.
    for density, size in SIZES.items():
        folder = os.path.join(RES, "mipmap-%s" % density)
        os.makedirs(folder, exist_ok=True)

        square = gradient(size, INK_DARK, INK_LIGHT)
        centre(square, silhouette(fit_square(mark, int(size * 0.58))))
        square.save(os.path.join(folder, "ic_launcher.png"))

        round_icon = gradient(size, INK_DARK, INK_LIGHT)
        centre(round_icon, silhouette(fit_square(mark, int(size * 0.58))))
        round_icon.putalpha(circle_mask(size))
        round_icon.save(os.path.join(folder, "ic_launcher_round.png"))
        print("wrote mipmap-%s/ic_launcher{,_round}.png (%dpx)" % (density, size))


if __name__ == "__main__":
    main()
