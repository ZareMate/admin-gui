#!/usr/bin/env python3
from pathlib import Path
from PIL import Image, ImageDraw
import math
import random

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/admin_gui/textures/gui"
OUT.mkdir(parents=True, exist_ok=True)

# All UI textures are generated as complete components rather than tiny tiles.
# This keeps the layout deterministic and makes the PNGs directly replaceable
# by resource packs. Text/icons are intentionally never baked into textures.

def noisy(size, base, seed, alpha=255):
    rng = random.Random(seed)
    image = Image.new("RGBA", size)
    px = image.load()
    for y in range(size[1]):
        for x in range(size[0]):
            n = rng.randint(-3, 3)
            px[x, y] = (
                max(0, min(255, base[0] + n)),
                max(0, min(255, base[1] + n)),
                max(0, min(255, base[2] + n)),
                alpha,
            )
    return image


def rounded_panel(size, outer, inner, highlight, shadow, radius=8, seed=1):
    w, h = size
    image = noisy(size, inner, seed)
    d = ImageDraw.Draw(image, "RGBA")

    d.rounded_rectangle(
        (1, 1, w - 2, h - 2),
        radius=radius,
        fill=outer + (255,),
        outline=(32, 20, 11, 255),
        width=3,
    )
    d.rounded_rectangle(
        (5, 5, w - 6, h - 6),
        radius=max(3, radius - 3),
        fill=inner + (255,),
        outline=highlight + (255,),
        width=2,
    )
    d.rounded_rectangle(
        (9, 9, w - 10, h - 10),
        radius=max(2, radius - 5),
        fill=inner + (245,),
    )

    d.line((10, 10, w - 11, 10), fill=highlight + (150,), width=1)
    d.line((10, h - 11, w - 11, h - 11), fill=shadow + (230,), width=2)
    return image


def widget_background(w, h, seed):
    image = rounded_panel(
        (w, h),
        (61, 40, 23),
        (104, 69, 37),
        (220, 159, 84),
        (49, 30, 17),
        radius=7,
        seed=seed,
    )
    d = ImageDraw.Draw(image, "RGBA")

    # Blank title plate. Game text is rendered separately.
    d.rectangle((10, 10, w - 11, 31), fill=(228, 198, 123, 255))
    d.line((12, 11, w - 13, 11), fill=(255, 241, 190, 190), width=1)
    d.line((12, 30, w - 13, 30), fill=(121, 82, 42, 180), width=1)

    return image


def widget_entry(w=254, h=18, seed=41):
    image = noisy((w, h), (213, 181, 109), seed)
    d = ImageDraw.Draw(image, "RGBA")
    d.rectangle((0, 0, w - 1, h - 1), outline=(111, 78, 42, 220), width=1)
    d.line((2, 1, w - 3, 1), fill=(255, 244, 195, 130), width=1)
    d.line((2, h - 1, w - 3, h - 1), fill=(103, 69, 38, 115), width=1)
    return image


def notes_background(w=562, h=156):
    image = rounded_panel(
        (w, h),
        (62, 41, 24),
        (96, 64, 37),
        (218, 158, 84),
        (48, 29, 17),
        radius=8,
        seed=61,
    )
    d = ImageDraw.Draw(image, "RGBA")

    # Header
    d.rectangle((12, 12, w - 13, 42), fill=(216, 183, 110, 255))
    d.line((15, 13, w - 16, 13), fill=(255, 243, 191, 180), width=1)
    d.line((15, 41, w - 16, 41), fill=(113, 77, 40, 190), width=1)

    # Entry cavity
    d.rounded_rectangle(
        (10, 48, w - 11, 144),
        radius=3,
        fill=(73, 48, 27, 225),
        outline=(49, 30, 17, 180),
        width=1,
    )
    return image


def note_entry(seed=301, hover=False):
    base = (112, 79, 46) if not hover else (134, 96, 55)
    image = noisy((405, 26), base, seed)
    d = ImageDraw.Draw(image, "RGBA")
    d.rectangle((0, 0, 404, 25), outline=(63, 40, 23, 255), width=1)
    d.line((3, 1, 400, 1), fill=(182, 145, 91, 210), width=1)
    d.line((3, 24, 400, 24), fill=(58, 36, 21, 220), width=1)
    # Small paper fold at bottom right.
    d.polygon([(394, 19), (404, 19), (404, 25), (399, 25)],
              fill=(58, 37, 22, 170))
    d.line((394, 19, 404, 19), fill=(177, 137, 83, 120), width=1)
    return image


def action_button(w, h, hover=False, seed=501):
    base = (167, 111, 55) if not hover else (193, 139, 74)
    image = noisy((w, h), base, seed)
    d = ImageDraw.Draw(image, "RGBA")
    d.rectangle((0, 0, w - 1, h - 1), outline=(52, 32, 17, 255), width=2)
    d.line((2, 1, w - 3, 1),
           fill=((236 if not hover else 248), (177 if not hover else 193), (96 if not hover else 112), 255),
           width=1)
    d.line((2, h - 2, w - 3, h - 2), fill=(82, 49, 25, 255), width=2)
    return image


def input_texture(w, h, seed=151):
    image = noisy((w, h), (31, 26, 20), seed)
    d = ImageDraw.Draw(image, "RGBA")
    d.rectangle((0, 0, w - 1, h - 1), outline=(142, 95, 47, 255), width=2)
    d.line((2, 2, w - 3, 2), fill=(222, 158, 83, 255), width=1)
    d.line((2, h - 2, w - 3, h - 2), fill=(60, 37, 21, 255), width=1)
    return image


def scrollbar_track(size, seed=701):
    w, h = size
    image = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(image, "RGBA")
    rng = random.Random(seed)
    for y in range(h):
        n = rng.randint(-2, 2)
        d.line((1, y, w - 2, y),
               fill=(max(0, 77 + n), max(0, 50 + n), max(0, 29 + n), 240))
    d.line((1, 0, 1, h - 1), fill=(41, 26, 15, 255), width=1)
    d.line((w - 2, 0, w - 2, h - 1), fill=(135, 89, 44, 150), width=1)
    return image


def scrollbar_thumb(size, hover=False, seed=702):
    w, h = size
    base = (158, 104, 49) if not hover else (191, 135, 67)
    image = noisy(size, base, seed)
    d = ImageDraw.Draw(image, "RGBA")
    d.rectangle((0, 0, w - 1, h - 1), outline=(48, 29, 15, 255), width=2)
    d.line((2, 1, w - 3, 1),
           fill=((226 if not hover else 246), (164 if not hover else 190), (87 if not hover else 105), 255),
           width=1)
    d.line((2, h - 2, w - 3, h - 2), fill=(72, 44, 23, 255), width=2)
    return image


def player_entry(hover=False, selected=False, seed=101):
    if selected:
        base = (247, 225, 146)
    elif hover:
        base = (246, 222, 145)
    else:
        base = (231, 205, 128)

    image = noisy((256, 27), base, seed)
    d = ImageDraw.Draw(image, "RGBA")
    edge = (135, 99, 49) if not selected else (154, 111, 50)
    d.rounded_rectangle((0, 0, 255, 26), radius=3,
                        fill=None, outline=edge + (255,), width=1)
    d.line((3, 1, 251, 1), fill=(255, 246, 194, 210), width=1)
    d.line((3, 25, 251, 25), fill=(157, 118, 62, 185), width=1)
    d.polygon([(244, 20), (255, 20), (255, 26), (249, 26)],
              fill=(190, 151, 78, 150))
    return image


def player_list():
    w, h = 280, 375
    image = rounded_panel((w, h), (57, 37, 21), (87, 58, 33),
                          (214, 153, 78), (45, 28, 16),
                          radius=8, seed=201)
    d = ImageDraw.Draw(image, "RGBA")

    # Search area
    d.rounded_rectangle((12, 12, 267, 42), radius=3,
                        fill=(31, 26, 20, 255),
                        outline=(219, 155, 80, 255), width=2)
    d.line((15, 14, 264, 14), fill=(244, 188, 106, 175), width=1)

    # List cavity
    d.rounded_rectangle((10, 50, 269, 363), radius=4,
                        fill=(83, 54, 30, 255),
                        outline=(54, 33, 19, 200), width=1)
    return image


def full_background():
    W, H = 900, 520
    rng = random.Random(12)
    image = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = image.load()

    # Full-resolution cardboard surface.
    for y in range(H):
        for x in range(W):
            n = rng.randint(-5, 5)
            grain = int(3 * math.sin(y * 0.18) + 2 * math.sin(x * 0.06))
            # Subtle edge darkening, while keeping the center warm.
            dist = min(x, y, W - 1 - x, H - 1 - y)
            edge = max(0, 9 - dist)
            px[x, y] = (
                max(0, min(255, 188 + n + grain - edge)),
                max(0, min(255, 150 + n + grain - edge)),
                max(0, min(255, 99 + n + grain - edge)),
                255 if dist >= 9 else 0,
            )

    # Cardboard fibers.
    d = ImageDraw.Draw(image, "RGBA")
    for y in range(2, H, 5):
        d.line((18, y, W - 19, y), fill=(93, 65, 42, 42), width=1)
        d.line((18, y + 1, W - 19, y + 1), fill=(238, 204, 151, 28), width=1)

    # Only the outer border is opaque; outside remains transparent.
    x0, y0, x1, y1 = 5, 5, W - 6, H - 6
    d.rounded_rectangle((x0, y0, x1, y1), radius=14,
                        outline=(41, 26, 15, 255), width=8)
    d.rounded_rectangle((x0 + 4, y0 + 4, x1 - 4, y1 - 4),
                        radius=11,
                        outline=(103, 67, 32, 255), width=5)
    d.rounded_rectangle((x0 + 6, y0 + 6, x1 - 6, y1 - 6),
                        radius=9,
                        outline=(215, 155, 82, 255), width=2)
    d.rounded_rectangle((x0 + 9, y0 + 9, x1 - 9, y1 - 9),
                        radius=6,
                        outline=(57, 37, 21, 255), width=2)

    # Brass highlight on top/left and darker underside.
    d.line((x0 + 18, y0 + 7, x1 - 18, y0 + 7),
           fill=(244, 195, 118, 220), width=1)
    d.line((x0 + 7, y0 + 18, x0 + 7, y1 - 18),
           fill=(179, 117, 55, 180), width=1)
    d.line((x0 + 18, y1 - 7, x1 - 18, y1 - 7),
           fill=(63, 39, 22, 230), width=2)

    # Four restrained brass/metal fasteners.
    for cx, cy in (
        (x0 + 13, y0 + 13),
        (x1 - 13, y0 + 13),
        (x0 + 13, y1 - 13),
        (x1 - 13, y1 - 13),
    ):
        d.ellipse((cx - 3, cy - 3, cx + 3, cy + 3),
                  fill=(95, 95, 87, 255),
                  outline=(38, 38, 34, 255))
        d.point((cx - 1, cy - 1), fill=(220, 220, 207, 255))

    return image


def header():
    image = noisy((876, 24), (69, 45, 27), 171)
    d = ImageDraw.Draw(image, "RGBA")
    d.rectangle((0, 0, 875, 23), outline=(40, 25, 14, 255), width=2)
    d.line((8, 2, 867, 2), fill=(234, 179, 97, 210), width=1)
    d.line((8, 21, 867, 21), fill=(45, 27, 16, 235), width=2)
    return image


assets = {
    "background.png": full_background(),
    "header.png": header(),
    "player_list.png": player_list(),
    "player_entry.png": player_entry(False, False, 101),
    "player_entry_hover.png": player_entry(True, False, 102),
    "player_entry_selected.png": player_entry(False, True, 103),
    "player_scroll_track.png": scrollbar_track((8, 290), 701),
    "player_scroll_thumb.png": scrollbar_thumb((8, 42), False, 702),
    "player_scroll_thumb_hover.png": scrollbar_thumb((8, 42), True, 703),
    "widget_270x88.png": widget_background(270, 88, 301),
    "widget_270x80.png": widget_background(270, 80, 302),
    "widget_270x45.png": widget_background(270, 45, 303),
    "widget_562x112.png": widget_background(562, 112, 304),
    "widget_entry.png": widget_entry(),
    "notes.png": notes_background(),
    "note_entry.png": note_entry(301, False),
    "note_entry_hover.png": note_entry(302, True),
    "note_edit.png": action_button(45, 18, False, 501),
    "note_edit_hover.png": action_button(45, 18, True, 502),
    "note_remove.png": action_button(20, 18, False, 503),
    "note_remove_hover.png": action_button(20, 18, True, 504),
    "note_input.png": input_texture(254, 26, 551),
    "add_note.png": action_button(84, 26, False, 801),
    "add_note_hover.png": action_button(84, 26, True, 802),
    "notes_scroll_track.png": scrollbar_track((8, 78), 711),
    "notes_scroll_thumb.png": scrollbar_thumb((8, 26), False, 712),
    "notes_scroll_thumb_hover.png": scrollbar_thumb((8, 26), True, 713),
}

# Legacy textures are still generated from the same design language so old
# resource-pack overrides don't suddenly disappear.
assets["player_item.png"] = assets["player_entry.png"]
assets["player_item_selected.png"] = assets["player_entry_selected.png"]
assets["widget.png"] = widget_entry(254, 18, 41)
assets["widget_frame.png"] = assets["widget_270x88"].resize((32, 32), Image.Resampling.LANCZOS)
assets["note.png"] = assets["note_entry.png"].resize((32, 32), Image.Resampling.LANCZOS)
assets["input.png"] = input_texture(32, 32)
assets["button.png"] = action_button(32, 32, False, 141)
assets["button_hover.png"] = action_button(32, 32, True, 142)
assets["scrollbar.png"] = scrollbar_track((8, 32), 701)

for name, image in assets.items():
    image.save(OUT / name)
    print(f"wrote {name}: {image.size}")
