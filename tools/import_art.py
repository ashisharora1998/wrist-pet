#!/usr/bin/env python3
"""Turns your own pet artwork into WristPet's sprite slots.

Input: one folder per pet under incoming/ (or --src), with files named by stage and,
optionally, mood. Any format Pillow reads works (PNG, GIF, WebP, JPG):

    incoming/<pet_id>/
      stage1.png            # required for at least one stage
      stage1_happy.png      # optional: a different pose for a mood
      stage2.gif            # animated GIFs become the idle loop
      stage3.png

Moods: idle, happy, curious, sleep, evolving, playing, eating, attack, angry, sad. Missing moods are animated from the stage's base image
by moving it (bob, hop, tilt, breathe) — the art itself is never redrawn. Missing stages reuse
the nearest stage you provided.

Output: assets/sprites/<pet_id>/stage{1,2,3}/ with every slot the apps and face expect, plus
a preview sheet. Rebuild the apps afterwards to pick the new pet up.

Usage (from the repo root):
    .venv/bin/python tools/import_art.py                 # every pet in incoming/
    .venv/bin/python tools/import_art.py --style smooth  # keep smooth art instead of pixelating

Sprite packs: a pack has one folder per creature with `animations/<state>.gif` (and optionally
`gif/front.gif` or `png/front.png`). Pick an evolution line by listing up to three folders, one
per stage; repeat --line for more pets:

    .venv/bin/python tools/import_art.py --pack ~/Downloads/MyPack \
        --line mypet=001-first,002-second,003-third --line other=010-solo

    .venv/bin/python tools/import_art.py --pack ~/Downloads/MyPack --all   # every folder, one pet each

Pack states map to moods via PACK_STATES (e.g. "surprised" becomes curious). With --all, a
folder named like "001-first-name" becomes the pet "first_name"; --line entries override it.
"""
import argparse
import math
import os
import re
import shutil
import sys
import tempfile
from collections import deque

from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter, ImageSequence

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SIZE = 160          # slot size on the 450x450 face
FIT = 136           # pet's longest side inside the slot; leaves room to hop and tilt
STAGES = (1, 2, 3)
# Same order as Mood in shared/ and `moods` in watchface/build.gradle.kts.
MOODS = ("idle", "happy", "curious", "sleep", "evolving", "playing", "eating", "attack", "angry", "sad")
LAVENDER = (185, 166, 242, 255)
AMBIENT_GREY = (110, 110, 110, 255)
# Pack state names, in order of preference, for each WristPet mood.
PACK_STATES = {
    "idle": ("idle",),
    "happy": ("happy", "playing"),
    "evolving": ("evolving", "evolve"),
    "playing": ("playing", "play"),
    "eating": ("eating", "eat"),
    "attack": ("attack",),
    "angry": ("angry",),
    "sad": ("sad",),
    "curious": ("curious", "surprised"),
    "sleep": ("sleep", "sleeping"),
}
PACK_BASE = (os.path.join("gif", "front.gif"), os.path.join("png", "front.png"))
NAME = re.compile(r"^stage([123])(?:_(" + "|".join(MOODS) + r"))?$")


# ---------- loading and cleanup ----------

def load_frames(path):
    with Image.open(path) as im:
        return [frame.convert("RGBA").copy() for frame in ImageSequence.Iterator(im)]


def remove_background(im, tolerance=40):
    """Flood-fills the background from the border when the image has no transparency."""
    alpha = im.getchannel("A")
    if alpha.getextrema()[0] < 250:
        return im  # already has transparency
    px = im.load()
    w, h = im.size
    seeds = [(x, y) for x in range(w) for y in (0, h - 1)] + [(x, y) for y in range(h) for x in (0, w - 1)]
    bg = px[0, 0][:3]
    close = lambda c: sum(abs(a - b) for a, b in zip(c[:3], bg)) <= tolerance * 3
    seen = set()
    queue = deque(s for s in seeds if close(px[s]))
    while queue:
        x, y = queue.popleft()
        if (x, y) in seen or not (0 <= x < w and 0 <= y < h) or not close(px[x, y]):
            continue
        seen.add((x, y))
        px[x, y] = (0, 0, 0, 0)
        queue.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
    return im


def fit_to_slot(im, style, grid):
    """Crops to the art, scales it to fit the slot and centres it on the bottom of a transparent canvas."""
    box = im.getbbox()
    if box is None:
        raise ValueError("image is empty after removing the background")
    art = im.crop(box)
    scale = FIT / max(art.size)
    if style == "pixel":
        # Shrink onto a coarse grid, then blow up with nearest-neighbour: crisp, even pixels.
        cell = SIZE // grid
        small = art.resize((max(1, round(art.width * scale / cell)), max(1, round(art.height * scale / cell))), Image.LANCZOS)
        small = harden_alpha(small)
        art = small.resize((small.width * cell, small.height * cell), Image.NEAREST)
    else:
        art = art.resize((max(1, round(art.width * scale)), max(1, round(art.height * scale))), Image.LANCZOS)
    canvas = Image.new("RGBA", (SIZE, SIZE))
    canvas.alpha_composite(art, ((SIZE - art.width) // 2, SIZE - art.height - 6))
    return canvas


def harden_alpha(im, threshold=128):
    """Pixel art has no soft edges: each pixel is fully on or off."""
    r, g, b, a = im.split()
    a = a.point(lambda v: 255 if v >= threshold else 0)
    return Image.merge("RGBA", (r, g, b, a))


# ---------- whole-image motion (no redrawing) ----------

def transform(base, dx=0, dy=0, sx=1.0, sy=1.0, angle=0.0, brightness=1.0):
    w, h = base.size
    im = base
    if sx != 1.0 or sy != 1.0:
        scaled = im.resize((max(1, round(w * sx)), max(1, round(h * sy))), Image.NEAREST)
        im = Image.new("RGBA", (w, h))
        # Squash and stretch around the feet so the pet stays grounded.
        im.alpha_composite(scaled, ((w - scaled.width) // 2, h - scaled.height))
    if angle:
        im = im.rotate(angle, resample=Image.NEAREST, center=(w / 2, h - 8))
    if brightness != 1.0:
        r, g, b, a = im.split()
        rgb = ImageEnhance.Brightness(Image.merge("RGB", (r, g, b))).enhance(brightness)
        im = Image.merge("RGBA", (*rgb.split(), a))
    out = Image.new("RGBA", (w, h))
    out.paste(im, (dx, dy), im)
    return out


def glyph(frame, kind, x, y):
    """Small UI marks drawn beside the pet: a '?' for curious, a 'z' for sleep."""
    d = ImageDraw.Draw(frame)
    cell = 4
    shapes = {
        "?": ["0110", "1001", "0010", "0100", "0000", "0100"],
        "z": ["1111", "0010", "0100", "1111"],
    }
    for row, bits in enumerate(shapes[kind]):
        for col, bit in enumerate(bits):
            if bit == "1":
                d.rectangle([x + col * cell, y + row * cell, x + col * cell + cell - 1, y + row * cell + cell - 1], fill=LAVENDER)
    return frame


def animate(base, mood, frames_given):
    if len(frames_given) > 1:
        return frames_given  # use the provided animation as-is
    b = frames_given[0] if frames_given else base
    if mood == "idle":
        return [transform(b), transform(b, sy=1.02), transform(b, dy=-3), transform(b, sy=0.98, sx=1.01)]
    if mood == "happy":
        return [transform(b, sy=0.95, sx=1.03), transform(b, dy=-10), transform(b, dy=-16, angle=4), transform(b, dy=-6, angle=-3)]
    if mood == "curious":
        top = 8
        return [
            transform(b),
            glyph(transform(b, angle=6), "?", 8, top),
            glyph(transform(b, angle=6), "?", 8, top - 2),
            glyph(transform(b, angle=-6), "?", SIZE - 26, top),
        ]
    if mood == "sleep":
        return [
            glyph(transform(b, brightness=0.8), "z", SIZE - 28, 10),
            glyph(transform(b, sy=0.97, sx=1.01, brightness=0.8), "z", SIZE - 24, 4),
        ]
    if mood == "evolving":  # pulse brighter, like a glow
        return [transform(b, brightness=1.0), transform(b, brightness=1.25, sy=1.02), transform(b, brightness=1.5, sy=1.04), transform(b, brightness=1.2)]
    if mood == "playing":
        return [transform(b, dx=-4), transform(b, dy=-8, angle=5), transform(b, dx=4), transform(b, dy=-8, angle=-5)]
    if mood == "eating":  # nodding chomps
        return [transform(b), transform(b, dy=3, sy=0.97), transform(b), transform(b, dy=3, sy=0.97)]
    if mood == "attack":  # wind up and lunge
        return [transform(b, dx=4), transform(b, dx=-8, sx=1.04), transform(b, dx=-4), transform(b)]
    if mood == "angry":  # shake
        return [transform(b, dx=-3), transform(b, dx=3), transform(b, dx=-3), transform(b, dx=3)]
    if mood == "sad":  # droop, dimmed
        return [transform(b, dy=3, sy=0.97, brightness=0.8), transform(b, dy=4, sy=0.96, brightness=0.8)]
    raise ValueError(mood)


def ambient(base):
    """Grey outline traced from the art's silhouette: few lit pixels for the always-on screen."""
    alpha = harden_alpha(base).getchannel("A")
    eroded = alpha.filter(ImageFilter.MinFilter(5))
    edge = ImageChops.subtract(alpha, eroded)
    out = Image.new("RGBA", base.size)
    out.paste(Image.new("RGBA", base.size, AMBIENT_GREY), mask=edge)
    return glyph(out, "z", SIZE - 28, 10)


# ---------- per pet ----------

def import_pet(src_dir, pet_id, style, grid, out_root):
    sources = {}
    for name in sorted(os.listdir(src_dir)):
        stem, _ = os.path.splitext(name)
        m = NAME.match(stem.lower())
        if not m:
            print(f"  skipping {name}: name it stageN.<ext> or stageN_<mood>.<ext>")
            continue
        stage, mood = int(m.group(1)), m.group(2) or "idle"
        frames = [fit_to_slot(remove_background(f), style, grid) for f in load_frames(os.path.join(src_dir, name))]
        sources[(stage, mood)] = frames
    given = sorted({stage for stage, _ in sources})
    if not given:
        print(f"  skipping {pet_id}/: no stage1/stage2/stage3 images in {os.path.abspath(src_dir)}")
        return None

    sheet_rows = []
    for stage in STAGES:
        nearest = min(given, key=lambda s: (abs(s - stage), s))
        base = (sources.get((nearest, "idle")) or next(v for (s, _), v in sources.items() if s == nearest))[0]
        out = os.path.join(out_root, pet_id, f"stage{stage}")
        os.makedirs(out, exist_ok=True)
        for old in os.listdir(out):
            if old.endswith(".png"):
                os.remove(os.path.join(out, old))
        row = []
        for mood in MOODS:
            frames = animate(base, mood, sources.get((nearest, mood), []))
            for i, frame in enumerate(frames, start=1):
                frame.save(os.path.join(out, f"pet_{mood}_{i:02d}.png"))
            row.append(frames[min(1, len(frames) - 1)])
        base.save(os.path.join(out, "pet_complication.png"))
        amb = ambient(base)
        amb.save(os.path.join(out, "pet_sleep_ambient.png"))
        row.append(amb)
        sheet_rows.append(row)
    return sheet_rows


def preview(rows_by_pet, path):
    cols = len(MOODS) + 1
    rows = [row for rows in rows_by_pet.values() for row in rows]
    sheet = Image.new("RGBA", (cols * SIZE, len(rows) * SIZE), (20, 20, 20, 255))
    for r, row in enumerate(rows):
        for c, frame in enumerate(row):
            sheet.alpha_composite(frame, (c * SIZE, r * SIZE))
    sheet.save(path)


def stage_pack_line(pack, folders, staging):
    """Lays a pack's evolution line out as an incoming/ folder (stageN[_mood].ext symlinks)."""
    os.makedirs(staging)
    for stage, folder in enumerate(folders, start=1):
        src = os.path.join(pack, folder)
        if not os.path.isdir(src):
            raise SystemExit(f"--line: no folder {folder!r} in {pack}")
        found = False
        for mood, states in PACK_STATES.items():
            for state in states:
                path = os.path.join(src, "animations", f"{state}.gif")
                if os.path.isfile(path):
                    name = f"stage{stage}.gif" if mood == "idle" else f"stage{stage}_{mood}.gif"
                    os.symlink(path, os.path.join(staging, name))
                    found = True
                    break
        if not os.path.exists(os.path.join(staging, f"stage{stage}.gif")):
            base = next((os.path.join(src, b) for b in PACK_BASE if os.path.isfile(os.path.join(src, b))), None)
            if base:
                os.symlink(base, os.path.join(staging, f"stage{stage}{os.path.splitext(base)[1]}"))
                found = True
        if not found:
            raise SystemExit(f"--line: {folder!r} has no animations/<state>.gif, gif/front.gif or png/front.png")


def all_folders(pack):
    """Every creature folder in a pack as its own pet: "001-first-name" -> "first_name"."""
    lines = {}
    for folder in sorted(os.listdir(pack)):
        if not os.path.isdir(os.path.join(pack, folder)) or folder.startswith("."):
            continue
        pet_id = re.sub(r"[^a-z0-9]+", "_", re.sub(r"^\d+[-_ ]*", "", folder.lower())).strip("_")
        if not pet_id:
            continue
        if pet_id in lines:
            raise SystemExit(f"--all: folders {lines[pet_id][0]!r} and {folder!r} both become pet {pet_id!r}; add a --line for one of them")
        lines[pet_id] = [folder]
    return lines


def parse_lines(values):
    lines = {}
    for value in values:
        pet_id, sep, folders = value.partition("=")
        folders = [f.strip() for f in folders.split(",") if f.strip()]
        if not sep or not re.fullmatch(r"[a-z0-9_]+", pet_id) or not 1 <= len(folders) <= len(STAGES):
            raise SystemExit(f"--line {value!r}: use <pet_id>=<folder>[,<folder>,<folder>] with a lowercase pet_id")
        lines[pet_id] = folders
    return lines


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--src", default=os.path.join(ROOT, "incoming"), help="folder with one subfolder per pet")
    parser.add_argument("--out", default=os.path.join(ROOT, "library"), help="pet library folder (copied to the phone by tools/deploy.sh)")
    parser.add_argument("--style", choices=("pixel", "smooth"), default="pixel")
    parser.add_argument("--grid", type=int, default=40, help="pixel grid across the 160 px slot (pixel style)")
    parser.add_argument("--preview", default=os.path.join(ROOT, "incoming", "preview.png"))
    parser.add_argument("--pack", help="sprite pack folder (one folder per creature with animations/<state>.gif)")
    parser.add_argument("--line", action="append", default=[], help="<pet_id>=<folder>[,<folder>,<folder>] from --pack, one folder per stage")
    parser.add_argument("--all", action="store_true", help="with --pack: import every folder as its own single-stage pet")
    args = parser.parse_args()
    if SIZE % args.grid:
        sys.exit(f"--grid must divide {SIZE} evenly (e.g. 32, 40, 80)")
    if bool(args.pack) != bool(args.line or args.all):
        sys.exit("--pack needs --line and/or --all (and --line/--all need --pack)")

    staging = tempfile.mkdtemp(prefix="wristpet-pack-") if args.pack else None
    try:
        if staging:
            explicit = parse_lines(args.line)
            in_lines = {folder for folders in explicit.values() for folder in folders}
            # Folders used as evolution stages in a --line aren't also imported on their own.
            lines = {pet: f for pet, f in (all_folders(os.path.expanduser(args.pack)) if args.all else {}).items() if f[0] not in in_lines}
            lines.update(explicit)
            for pet_id, folders in lines.items():
                stage_pack_line(os.path.expanduser(args.pack), folders, os.path.join(staging, pet_id))
            args.src = staging
        run(args)
    finally:
        if staging:
            shutil.rmtree(staging, ignore_errors=True)


def run(args):

    rows = {}
    for pet_id in sorted(os.listdir(args.src)):
        src = os.path.join(args.src, pet_id)
        if not os.path.isdir(src):
            continue
        if not re.fullmatch(r"[a-z0-9_]+", pet_id):
            print(f"skipping {pet_id}/: folder names must be lowercase letters, digits and _")
            continue
        print(f"importing {pet_id}")
        result = import_pet(src, pet_id, args.style, args.grid, args.out)
        if result:
            rows[pet_id] = result
    if not rows:
        sys.exit(f"nothing imported: add images like stage1.png to the pet folders in {os.path.abspath(args.src)}")
    os.makedirs(os.path.dirname(args.preview), exist_ok=True)
    preview(rows, args.preview)
    print(f"done: {', '.join(rows)} -> {args.out}\npreview: {args.preview} (rows = stages 1-3, columns = idle, happy, curious, sleep, ambient)")


if __name__ == "__main__":
    main()
