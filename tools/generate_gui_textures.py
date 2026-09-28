#!/usr/bin/env python3
from pathlib import Path
from PIL import Image, ImageDraw
import random

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/admin_gui/textures/gui"
OUT.mkdir(parents=True, exist_ok=True)

# 32x32 textures are used as vanilla-style 9-slice components.
# Six-pixel corners/edges stay native-sized; the center adapts to the target.

def noisy(base, seed):
    rng = random.Random(seed)
    image = Image.new("RGBA", (32, 32))
    px = image.load()
    for y in range(32):
        for x in range(32):
            n = rng.randint(-4, 4)
            px[x, y] = (
                max(0, min(255, base[0] + n)),
                max(0, min(255, base[1] + n)),
                max(0, min(255, base[2] + n)),
                255,
            )
    return image

def panel(base, edge, seed):
    image = noisy(base, seed)
    d = ImageDraw.Draw(image)
    d.rectangle((0, 0, 31, 31), outline=edge + (255,), width=2)
    d.line((2, 2, 29, 2), fill=tuple(min(255, c + 24) for c in base) + (255,))
    d.line((2, 29, 29, 29), fill=tuple(max(0, c - 24) for c in base) + (255,))
    return image

def frame(outer, inner, highlight, shadow):
    image = Image.new("RGBA", (32, 32), outer + (255,))
    d = ImageDraw.Draw(image)
    d.rectangle((1, 1, 30, 30), fill=inner + (255,))
    d.rectangle((6, 6, 25, 25), fill=(0, 0, 0, 0))
    d.line((6, 2, 25, 2), fill=highlight + (255,), width=2)
    d.line((2, 6, 2, 25), fill=highlight + (190,))
    d.line((6, 29, 25, 29), fill=shadow + (255,), width=2)
    d.line((29, 6, 29, 25), fill=shadow + (220,))
    return image

def player(selected):
    base = (247, 226, 149) if selected else (235, 210, 132)
    image = noisy(base, 102 if selected else 101)
    d = ImageDraw.Draw(image)
    d.rectangle((0, 0, 31, 31), outline=(126, 99, 51, 255), width=2)
    d.line((2, 2, 29, 2), fill=(255, 248, 202, 230))
    d.line((2, 29, 29, 29), fill=(205, 171, 101, 255))
    d.polygon((25, 31, 31, 25, 31, 31), fill=(198, 166, 92, 180))
    return image

def note():
    image = noisy((108, 78, 47), 51)
    d = ImageDraw.Draw(image)
    d.rectangle((0, 0, 31, 31), outline=(65, 42, 26, 255), width=2)
    d.line((2, 2, 29, 2), fill=(177, 141, 92, 255))
    d.line((2, 29, 29, 29), fill=(63, 40, 25, 255))
    d.polygon((26, 31, 31, 26, 31, 31), fill=(72, 47, 29, 255))
    d.line((26, 31, 31, 26), fill=(170, 128, 76, 220))
    return image

def button(hover=False):
    base = (196, 139, 72) if hover else (170, 113, 56)
    image = noisy(base, 142 if hover else 141)
    d = ImageDraw.Draw(image)
    d.rectangle((0, 0, 31, 31), outline=(76, 47, 25, 255), width=2)
    d.line((2, 2, 29, 2), fill=(237, 178, 102, 255), width=2)
    d.line((2, 29, 29, 29), fill=(92, 55, 27, 255), width=2)
    return image

def input_box():
    image = noisy((34, 29, 23), 151)
    d = ImageDraw.Draw(image)
    d.rectangle((0, 0, 31, 31), outline=(135, 91, 46, 255), width=2)
    d.line((2, 2, 29, 2), fill=(218, 153, 81, 255))
    d.line((2, 29, 29, 29), fill=(63, 39, 22, 255))
    return image

def scrollbar():
    image = Image.new("RGBA", (8, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(image)
    d.rectangle((1, 0, 6, 31), fill=(79, 52, 31, 235))
    d.line((1, 0, 6, 0), fill=(214, 151, 77, 255))
    d.line((1, 1, 6, 1), fill=(159, 101, 47, 230))
    d.line((1, 30, 6, 30), fill=(45, 28, 17, 255))
    return image

background = noisy((188, 150, 99), 12)
d = ImageDraw.Draw(background)
for y in range(1, 32, 4):
    d.line((0, y, 31, y), fill=(111, 82, 55, 90))
    d.line((0, y + 1, 31, y + 1), fill=(235, 202, 148, 70))

assets = {
    "background.png": background,
    "border.png": frame((73, 48, 29), (126, 84, 41), (222, 163, 91), (48, 30, 18)),
    "header.png": panel((71, 48, 29), (46, 30, 18), 171),
    "player_item.png": player(False),
    "player_item_selected.png": player(True),
    "widget.png": panel((224, 194, 120), (122, 91, 49), 41),
    "widget_frame.png": frame((64, 43, 27), (102, 68, 36), (222, 162, 88), (43, 28, 17)),
    "note.png": note(),
    "button.png": button(False),
    "button_hover.png": button(True),
    "input.png": input_box(),
    "scrollbar.png": scrollbar(),
}

for name, image in assets.items():
    image.save(OUT / name)
    print(f"wrote {name} {image.size}")
