#!/usr/bin/env python3
"""Draws the placeholder pets into the asset slots under assets/sprites/<species>/stageN/.

Art is authored on a 32x32 grid and exported at 5x (160x160 px) with
nearest-neighbour scaling, matching the 160x160 pet slot on the 450x450 face.
Replace any PNG with final artwork of the same size; nothing else changes.
A new species is just a new folder with the same slots.

Usage: python3 tools/gen_placeholder_sprites.py   (from the repo root)
"""
import os
import struct
import zlib

GRID = 32
SCALE = 5

CLEAR = (0, 0, 0, 0)
INK = (26, 28, 30, 255)
CREAM = (242, 235, 217, 255)
MINT = (127, 224, 176, 255)
MINT_DARK = (62, 143, 106, 255)
LAVENDER = (185, 166, 242, 255)
LAVENDER_DARK = (122, 104, 196, 255)
CORAL = (255, 138, 122, 255)
CORAL_DARK = (194, 91, 78, 255)
AMBIENT_GREY = (110, 110, 110, 255)

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SPRITES = os.path.join(ROOT, "assets", "sprites")


def write_png(path, pixels, size):
    raw = b"".join(
        b"\x00" + b"".join(bytes(pixels[y][x]) for x in range(size)) for y in range(size)
    )

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def upscale(grid, scale):
    return [[grid[y // scale][x // scale] for x in range(GRID * scale)] for y in range(GRID * scale)]


class Canvas:
    def __init__(self):
        self.px = [[CLEAR] * GRID for _ in range(GRID)]

    def set(self, x, y, color):
        if 0 <= x < GRID and 0 <= y < GRID:
            self.px[y][x] = color

    def get(self, x, y):
        return self.px[y][x] if 0 <= x < GRID and 0 <= y < GRID else CLEAR


def ellipse(cx, cy, rx, ry):
    return lambda x, y: ((x - cx) / (rx + 0.5)) ** 2 + ((y - cy) / (ry + 0.5)) ** 2 <= 1.0


def union(*shapes):
    return lambda x, y: any(s(x, y) for s in shapes)


# Each species: body shape, palette and per-stage decorations. Bodies grow with the stage.
class Sprout:
    body, edge, belly, cheek = MINT, MINT_DARK, CREAM, CORAL
    size = {1: (7, 6), 2: (9, 8), 3: (11, 9)}

    def shape(self, cx, cy, rx, ry):
        return ellipse(cx, cy, rx, ry)

    def decorate(self, c, stage, cx, top, outline_only):
        leaf = AMBIENT_GREY if outline_only else MINT_DARK
        for i in range(1, 3):
            c.set(15, top - i, leaf)
        for x, y in ((13, top - 3), (14, top - 3), (12, top - 4), (13, top - 4)):
            c.set(x, y, leaf)
        if stage >= 2:
            for x, y in ((16, top - 3), (17, top - 3), (17, top - 4), (18, top - 4)):
                c.set(x, y, leaf)
        if stage >= 3 and not outline_only:
            for x, y in ((15, top - 4), (14, top - 5), (16, top - 5), (15, top - 6)):
                c.set(x, y, CORAL)
            c.set(15, top - 5, CREAM)


class Puff:
    body, edge, belly, cheek = CREAM, LAVENDER_DARK, LAVENDER, CORAL
    size = {1: (7, 5), 2: (9, 6), 3: (11, 7)}

    def shape(self, cx, cy, rx, ry):
        # A cloud: a wide base plus three bumps on top.
        return union(
            ellipse(cx, cy, rx, ry),
            ellipse(cx - rx * 0.5, cy - ry * 0.7, rx * 0.45, ry * 0.6),
            ellipse(cx + rx * 0.45, cy - ry * 0.6, rx * 0.5, ry * 0.65),
            ellipse(cx, cy - ry * 0.95, rx * 0.4, ry * 0.55),
        )

    def decorate(self, c, stage, cx, top, outline_only):
        color = AMBIENT_GREY if outline_only else LAVENDER
        if stage >= 2:  # a little raindrop tuft
            for x, y in ((int(cx), top - 1), (int(cx), top - 2), (int(cx) + 1, top - 2)):
                c.set(x, y, color)
        if stage >= 3 and not outline_only:  # a coral star
            sx = int(cx) + 5
            for x, y in ((sx, top - 2), (sx - 1, top - 1), (sx + 1, top - 1), (sx, top), (sx, top - 1)):
                c.set(x, y, CORAL)


class Pebble:
    body, edge, belly, cheek = CORAL, CORAL_DARK, CREAM, CREAM
    size = {1: (6, 5), 2: (8, 6), 3: (10, 7)}  # claws add width

    def shape(self, cx, cy, rx, ry):
        # A round shell with a claw on each side.
        claw = max(2, rx // 3)
        return union(
            ellipse(cx, cy, rx, ry),
            ellipse(cx - rx - claw + 1, cy - 1, claw, claw),
            ellipse(cx + rx + claw - 1, cy - 1, claw, claw),
        )

    def decorate(self, c, stage, cx, top, outline_only):
        color = AMBIENT_GREY if outline_only else CORAL_DARK
        for dx in (-2, 2):  # eye stalks
            c.set(int(cx) + dx, top - 1, color)
        if stage >= 3 and not outline_only:  # mossy tuft
            for x in range(int(cx) - 2, int(cx) + 3):
                c.set(x, top - 2, MINT)
            c.set(int(cx), top - 3, MINT_DARK)


SPECIES = {"sprout": Sprout(), "puff": Puff(), "pebble": Pebble()}


def draw_pet(species, stage, dy=0, dx=0, eyes="open", eye_dx=0, mouth="smile", outline_only=False, extra=None):
    sp = SPECIES[species]
    c = Canvas()
    rx, ry = sp.size[stage]
    cx, cy = 15.5 + dx, 30 - ry + dy  # feet rest near the bottom of the canvas
    inside = sp.shape(cx, cy, rx, ry)
    edge = AMBIENT_GREY if outline_only else sp.edge

    for y in range(GRID):
        for x in range(GRID):
            if not inside(x, y):
                continue
            on_edge = any(not inside(x + ox, y + oy) for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if on_edge:
                c.set(x, y, edge)
            elif not outline_only:
                c.set(x, y, sp.body)

    if not outline_only:
        belly = ellipse(cx, cy + ry * 0.45, rx * 0.55, ry * 0.4)
        for y in range(GRID):
            for x in range(GRID):
                if belly(x, y) and c.get(x, y) == sp.body:
                    c.set(x, y, sp.belly)

    top = min(y for y in range(GRID) for x in range(GRID) if inside(x, y))
    sp.decorate(c, stage, cx, top, outline_only)

    # Face.
    ey = int(cy - ry * 0.25)
    lx, rxe = int(cx - rx * 0.45) + eye_dx, int(cx + rx * 0.45) + eye_dx
    eye_color = AMBIENT_GREY if outline_only else INK
    for ex in (lx, rxe):
        if eyes == "open":
            for px, py in ((ex, ey), (ex, ey + 1), (ex + 1, ey), (ex + 1, ey + 1)):
                c.set(px, py, eye_color)
            if not outline_only:
                c.set(ex, ey, CREAM)  # highlight
        elif eyes == "closed":
            c.set(ex, ey + 1, eye_color)
            c.set(ex + 1, ey + 1, eye_color)
        elif eyes == "happy":  # ^ ^
            c.set(ex, ey + 1, eye_color)
            c.set(ex + 1, ey, eye_color)
            c.set(ex + 2, ey + 1, eye_color)
        elif eyes in ("angry", "sad"):
            c.set(ex, ey + 1, eye_color)
            c.set(ex + 1, ey + 1, eye_color)
            # Brows: slanted in for grumpy, slanted out for sad.
            inward = (ex == lx) == (eyes == "angry")
            c.set(ex, ey - (1 if inward else 2), eye_color)
            c.set(ex + 1, ey - (2 if inward else 1), eye_color)

    if not outline_only:
        c.set(lx - 1, ey + 2, sp.cheek)
        c.set(rxe + 2, ey + 2, sp.cheek)
        mx = int(cx) + eye_dx
        if mouth == "smile":
            c.set(mx - 1, ey + 3, INK)
            c.set(mx, ey + 4, INK)
            c.set(mx + 1, ey + 3, INK)
        elif mouth == "open":
            for x, y in ((mx - 1, ey + 3), (mx, ey + 3), (mx + 1, ey + 3), (mx, ey + 4)):
                c.set(x, y, CORAL if sp.body != CORAL else INK)
        elif mouth == "o":
            c.set(mx, ey + 3, INK)

    if extra:
        extra(c, cx, cy, rx, ry)
    return c.px


def question_mark(side):
    def draw(c, cx, cy, rx, ry):
        x = int(cx + side * (rx + 2))
        y = int(cy - ry - 2)
        for px, py in ((x, y), (x + 1, y), (x + 2, y + 1), (x + 1, y + 2), (x + 1, y + 4)):
            c.set(px, py, LAVENDER)
    return draw


def zzz(color, shift=0):
    def draw(c, cx, cy, rx, ry):
        x, y = int(cx + rx) - 1 + shift, max(0, int(cy - ry - 5) - shift)
        for px, py in ((x, y), (x + 1, y), (x + 2, y), (x + 3, y), (x + 2, y + 1), (x + 1, y + 2),
                       (x, y + 3), (x + 1, y + 3), (x + 2, y + 3), (x + 3, y + 3)):
            c.set(px, py, color)
    return draw


def sparkles(phase):
    def draw(c, cx, cy, rx, ry):
        spots = [(int(cx - rx - 2), int(cy - ry)), (int(cx + rx + 2), int(cy - ry // 2)), (int(cx - rx), int(cy + 2)), (int(cx + rx), int(cy - ry - 3))]
        for i, (x, y) in enumerate(spots):
            if (i + phase) % 2 == 0:
                for px, py in ((x, y), (x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                    c.set(px, py, LAVENDER if i % 2 else CREAM)
    return draw


def berry(c, cx, cy, rx, ry):
    x, y = int(cx) + 3, int(cy + ry * 0.1)
    for px, py in ((x, y), (x + 1, y), (x, y + 1), (x + 1, y + 1)):
        c.set(px, py, CORAL)
    c.set(x + 1, y - 1, MINT_DARK)


def power_lines(c, cx, cy, rx, ry):
    x = int(cx - rx - 3)
    for y in (int(cy - 3), int(cy), int(cy + 3)):
        for px in range(x - 3, x):
            c.set(px, y, LAVENDER)


def grumble(c, cx, cy, rx, ry):
    x, y = int(cx + rx), int(cy - ry - 3)  # a little steam puff
    for px, py in ((x, y), (x + 1, y - 1), (x + 2, y), (x + 1, y + 1)):
        c.set(px, py, CORAL)


def tear(c, cx, cy, rx, ry):
    x, y = int(cx - rx * 0.45), int(cy - ry * 0.25) + 3
    c.set(x, y, LAVENDER)
    c.set(x, y + 1, LAVENDER)


def frames_for(species, stage):
    pet = lambda **kw: draw_pet(species, stage, **kw)
    return {
        "pet_idle": [pet(), pet(), pet(dy=-1), pet(eyes="closed")],
        "pet_happy": [
            pet(eyes="happy", mouth="open"),
            pet(dy=-2, eyes="happy", mouth="open"),
            pet(dy=-3, eyes="happy", mouth="open"),
            pet(dy=-1, eyes="happy", mouth="open"),
        ],
        "pet_curious": [
            pet(mouth="o"),
            pet(eye_dx=-1, mouth="o", extra=question_mark(-1)),
            pet(eye_dx=-1, mouth="o", extra=question_mark(-1)),
            pet(eye_dx=1, mouth="o", extra=question_mark(1)),
        ],
        "pet_sleep": [
            pet(eyes="closed", mouth="none", extra=zzz(LAVENDER)),
            pet(dy=1, eyes="closed", mouth="none", extra=zzz(LAVENDER, shift=1)),
        ],
        "pet_evolving": [pet(eyes="happy", mouth="open", extra=sparkles(i)) for i in range(4)],
        "pet_playing": [
            pet(dx=-1, eyes="happy", mouth="open"),
            pet(dy=-2, eyes="happy", mouth="open"),
            pet(dx=1, eyes="happy", mouth="open"),
            pet(dy=-2, eyes="happy", mouth="open"),
        ],
        "pet_eating": [
            pet(mouth="open", extra=berry),
            pet(mouth="smile", extra=berry),
            pet(mouth="open"),
            pet(eyes="happy", mouth="smile"),
        ],
        "pet_attack": [
            pet(dx=1, eyes="angry", mouth="o"),
            pet(dx=-2, eyes="angry", mouth="open", extra=power_lines),
            pet(dx=-1, eyes="angry", mouth="open", extra=power_lines),
            pet(eyes="happy", mouth="smile"),
        ],
        "pet_angry": [
            pet(eyes="angry", mouth="o", extra=grumble),
            pet(dx=1, eyes="angry", mouth="o"),
            pet(eyes="angry", mouth="o", extra=grumble),
            pet(dx=-1, eyes="angry", mouth="o"),
        ],
        "pet_sad": [
            pet(dy=1, eyes="sad", mouth="o", extra=tear),
            pet(dy=1, eyes="sad", mouth="o"),
        ],
    }


def main():
    for species in SPECIES:
        for stage in (1, 2, 3):
            out = os.path.join(SPRITES, species, f"stage{stage}")
            for name, frames in frames_for(species, stage).items():
                for i, grid in enumerate(frames, start=1):
                    write_png(os.path.join(out, f"{name}_{i:02d}.png"), upscale(grid, SCALE), GRID * SCALE)
            write_png(os.path.join(out, "pet_complication.png"), upscale(draw_pet(species, stage), SCALE), GRID * SCALE)
            ambient = draw_pet(species, stage, eyes="closed", mouth="none", outline_only=True, extra=zzz(AMBIENT_GREY))
            write_png(os.path.join(out, "pet_sleep_ambient.png"), upscale(ambient, SCALE), GRID * SCALE)

    # Watch face picker preview and app icons (not swappable slots).
    preview = [[(0, 0, 0, 255)] * 450 for _ in range(450)]
    pet = upscale(draw_pet("sprout", 1), SCALE)
    for y in range(160):
        for x in range(160):
            if pet[y][x][3]:
                preview[220 + y][145 + x] = pet[y][x]
    write_png(os.path.join(ROOT, "watchface", "src", "main", "res", "drawable-nodpi", "preview.png"), preview, 450)
    for app in ("app", "mobile"):
        write_png(os.path.join(ROOT, app, "src", "main", "res", "drawable-nodpi", "ic_pet.png"), upscale(draw_pet("sprout", 1), 3), GRID * 3)


if __name__ == "__main__":
    main()
