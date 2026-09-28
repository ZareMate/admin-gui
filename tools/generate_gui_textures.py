from pathlib import Path
from PIL import Image, ImageDraw
import random

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/admin_gui/textures/gui"
OUT.mkdir(parents=True, exist_ok=True)

def paper_tile(base, seed, bottom=None):
    rnd = random.Random(seed)
    im = Image.new("RGBA", (16, 16))
    pix = im.load()
    for y in range(16):
        for x in range(16):
            v = rnd.randint(-4, 4)
            pix[x, y] = tuple(max(0, min(255, c + v)) for c in base) + (255,)
    d = ImageDraw.Draw(im)
    d.line([(0, 0), (15, 0)], fill=tuple(min(255, c + 20) for c in base) + (100,))
    if bottom:
        d.line([(0, 15), (15, 15)], fill=bottom)
    return im

# Seamless cardboard background.
background = paper_tile((188, 150, 99), 12, (115, 83, 54, 100))
bd = ImageDraw.Draw(background)
for y in range(1, 16, 3):
    bd.line([(0, y), (15, y)], fill=(110, 82, 55, 90))
    if y + 1 < 16:
        bd.line([(0, y + 1), (15, y + 1)], fill=(232, 198, 142, 80))

# Dark wooden/brass clipboard border tile.
rnd = random.Random(22)
border = Image.new("RGBA", (16, 16))
bp = border.load()
for y in range(16):
    for x in range(16):
        v = rnd.randint(-10, 10)
        bp[x, y] = (
            max(0, min(255, 74 + v)),
            max(0, min(255, 48 + v)),
            max(0, min(255, 28 + v)),
            255,
        )
bd = ImageDraw.Draw(border)
bd.line([(0, 0), (15, 0)], fill=(24, 18, 13, 255))
bd.line([(0, 1), (15, 1)], fill=(128, 82, 40, 255))
bd.line([(0, 14), (15, 14)], fill=(45, 28, 17, 255))
bd.line([(0, 15), (15, 15)], fill=(18, 13, 9, 255))

assets = {
    "background.png": background,
    "border.png": border,
    "player_item.png": paper_tile((235, 210, 132), 101, (176, 143, 73, 120)),
    "player_item_selected.png": paper_tile((247, 230, 157), 102, (190, 157, 89, 120)),
    "widget.png": paper_tile((211, 178, 109), 111, (150, 112, 60, 120)),
    "widget_frame.png": paper_tile((91, 61, 35), 121, (70, 43, 25, 180)),
    "note.png": paper_tile((108, 78, 47), 131, (69, 46, 28, 190)),
    "button.png": paper_tile((153, 100, 48), 141, (85, 54, 27, 220)),
    "button_hover.png": paper_tile((183, 124, 63), 142, (101, 64, 31, 220)),
    "input.png": paper_tile((35, 29, 22), 151, (12, 10, 8, 255)),
}

for name, image in assets.items():
    image.save(OUT / name)

bar = Image.new("RGBA", (8, 16), (0, 0, 0, 0))
d = ImageDraw.Draw(bar)
d.rectangle([1, 0, 6, 15], fill=(83, 56, 33, 220))
d.line([(1, 0), (6, 0)], fill=(216, 151, 75, 255))
d.line([(1, 1), (6, 1)], fill=(165, 104, 47, 220))
d.line([(1, 14), (6, 14)], fill=(48, 30, 18, 255))
bar.save(OUT / "scrollbar.png")

print(f"Generated {len(assets) + 1} GUI textures in {OUT}")
