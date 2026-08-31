#!/usr/bin/env python3
"""
Renders the KadePOS legacy launcher icons (mipmap-*/ic_launcher.webp and
ic_launcher_round.webp) so pre-Android-8 launchers match the adaptive icon.

Uses ImageMagick `convert` only; no Python imaging libs needed.
"""
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/src/main/res")

# density -> launcher icon size in px
SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

TEAL_DARK = "#115E59"
TEAL = "#0F766E"
TEAL_LIGHT = "#14B8A6"
WHITE = "#FFFFFF"
SLATE = "#94A3B8"


def draw(size, rounded):
    """Returns the ImageMagick argument list drawing the icon at `size` px."""
    s = size / 108.0  # design canvas is 108x108

    def u(v):
        return v * s

    args = [
        "-size", "%dx%d" % (size, size),
        "gradient:%s-%s" % (TEAL_DARK, TEAL_LIGHT),
    ]

    # Receipt body (white rounded rect with a torn bottom)
    receipt = [
        "-fill", WHITE, "-stroke", "none",
        "-draw", "roundrectangle %f,%f %f,%f %f,%f" % (u(34), u(28), u(74), u(70), u(3), u(3)),
    ]

    # Torn zigzag bottom edge
    zig = []
    x = 34.0
    step = 5.0
    top = 66.0
    while x < 74.0:
        zig.append("polygon %f,%f %f,%f %f,%f" % (
            u(x), u(top), u(min(x + step / 2, 74)), u(top + 5), u(min(x + step, 74)), u(top)))
        x += step
    for z in zig:
        receipt += ["-draw", z]

    lines = [
        (TEAL, 41, 38, 67, 42),
        (SLATE, 41, 47, 59, 50),
        (SLATE, 41, 54, 63, 57),
        (TEAL_LIGHT, 41, 61, 54, 65),
        (TEAL, 58, 61, 67, 65),
    ]
    line_args = []
    for colour, x1, y1, x2, y2 in lines:
        line_args += [
            "-fill", colour,
            "-draw", "rectangle %f,%f %f,%f" % (u(x1), u(y1), u(x2), u(y2)),
        ]

    args += receipt + line_args

    if rounded:
        # Mask into a circle
        args += [
            "(", "-size", "%dx%d" % (size, size), "xc:none",
            "-fill", "white", "-draw",
            "circle %f,%f %f,%f" % (size / 2.0, size / 2.0, size / 2.0, 0),
            ")",
            "-alpha", "set", "-compose", "DstIn", "-composite",
        ]
    else:
        # Squircle-ish rounded corners like modern launchers
        r = size * 0.22
        args += [
            "(", "-size", "%dx%d" % (size, size), "xc:none",
            "-fill", "white", "-draw",
            "roundrectangle 0,0 %f,%f %f,%f" % (size - 1, size - 1, r, r),
            ")",
            "-alpha", "set", "-compose", "DstIn", "-composite",
        ]
    return args


def main():
    for density, size in SIZES.items():
        outdir = os.path.join(RES, "mipmap-%s" % density)
        os.makedirs(outdir, exist_ok=True)
        for rounded, filename in ((False, "ic_launcher.webp"), (True, "ic_launcher_round.webp")):
            out = os.path.join(outdir, filename)
            cmd = ["convert"] + draw(size, rounded) + ["-define", "webp:lossless=true", out]
            subprocess.run(cmd, check=True)
            print("wrote", os.path.relpath(out, ROOT), "%dx%d" % (size, size))


if __name__ == "__main__":
    sys.exit(main())
