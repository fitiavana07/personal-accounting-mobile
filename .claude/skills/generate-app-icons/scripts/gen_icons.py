"""Template for generating multi-density PNG icons with PIL.

Copy this file to the scratchpad, replace RES_DIR if needed, fill in the
ICONS dict with one draw_xxx(draw) function per icon, then run it. Each
icon is drawn on a 24x24dp canvas (matching this project's existing
ic_nav_*/ic_mode_* icons), supersampled for anti-aliasing, and exported at
the standard mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi scale factors as opaque black
PNGs on a transparent background.

See .claude/skills/generate-app-icons/SKILL.md for when to use this vs. a
hand-authored vector drawable, and for the tinting pattern to wire the
result into a layout.
"""
import os
from PIL import Image, ImageDraw

SS = 20            # supersample factor
BASE = 24           # dp viewport (matches every existing icon in this project)
SIZE = BASE * SS    # working canvas size in px
BLACK = (0, 0, 0, 255)

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

RES_DIR = "app/src/main/res"  # run from the repo root, or make this absolute


def new_canvas():
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def d(v):
    """dp -> supersampled px (PIL needs ints for coords/widths)."""
    return round(v * SS)


# --- Example icon: replace/add with your own draw_xxx(draw) functions ---
def draw_example(draw):
    draw.rounded_rectangle([d(3), d(3), d(21), d(21)], radius=d(2), outline=BLACK, width=d(1.6))
    draw.line([d(6), d(12), d(18), d(12)], fill=BLACK, width=d(1.6))


ICONS = {
    # "ic_feature_name": draw_example,
}


def main():
    for name, drawer in ICONS.items():
        canvas = new_canvas()
        draw = ImageDraw.Draw(canvas)
        drawer(draw)
        for density, factor in DENSITIES.items():
            px = round(BASE * factor)
            resized = canvas.resize((px, px), Image.LANCZOS)
            out_dir = os.path.join(RES_DIR, f"drawable-{density}")
            os.makedirs(out_dir, exist_ok=True)
            out_path = os.path.join(out_dir, f"{name}.png")
            resized.save(out_path)
            print("wrote", out_path, resized.size)


if __name__ == "__main__":
    main()
