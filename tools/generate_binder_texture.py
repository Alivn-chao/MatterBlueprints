"""Deterministic C-style binding clamp, native atlas animation and aligned LabPBR maps."""
from pathlib import Path
import json

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/matterblueprints/textures/items"
PREVIEW = ROOT / "build/binder-preview"
SIZE = 16
SEQUENCE = list(range(8)) + list(range(6, 0, -1))
SOURCE = ROOT / "tools/texture_sources/binder-c.png"


def source_sprite() -> Image.Image:
    # Uniform area reduction of the selected concept; no hand-redrawn geometry.
    source = Image.open(SOURCE).convert("RGBA")
    source = source.crop(source.getbbox())
    source.thumbnail((SIZE, SIZE), Image.Resampling.BOX)
    image = Image.new("RGBA", (SIZE, SIZE))
    image.paste(source, ((SIZE - source.width) // 2, (SIZE - source.height) // 2))
    return image


def frame(level: int) -> tuple[Image.Image, Image.Image, Image.Image]:
    image = source_sprite()
    normal = Image.new("RGBA", (SIZE, SIZE), (128, 128, 255, 255))
    specular = Image.new("RGBA", (SIZE, SIZE), (0, 10, 0, 0))
    intensity = 0.80 + 0.20 * level / 7
    for y in range(SIZE):
        for x in range(SIZE):
            r, g, b, a = image.getpixel((x, y))
            if a < 128:
                image.putpixel((x, y), (0, 0, 0, 0))
                continue
            lamp = g > 100 and b > 120 and g > r * 1.5 and b > r * 1.5 and g > b * 0.65
            metal = max(r, g, b) - min(r, g, b) < 35 and r > 80
            # Lift midtones without changing alpha, geometry, or the material classification.
            r, g, b = (round(255 * (value / 255) ** 0.72) for value in (r, g, b))
            if lamp:
                image.putpixel((x, y), (round(r * intensity), round(g * intensity), round(b * intensity), 255))
                specular.putpixel((x, y), (80, 10, 0, round(220 * intensity)))
            else:
                image.putpixel((x, y), (r, g, b, 255))
                specular.putpixel((x, y), (100 if metal else 25, 230 if metal else 10, 0, 0))
    return image, normal, specular

def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    PREVIEW.mkdir(parents=True, exist_ok=True)
    frames = [frame(i) for i in range(8)]
    metadata = {"animation": {"frametime": 3, "frames": SEQUENCE}}
    for index, suffix in enumerate(("", "_n", "_s")):
        sheet = Image.new("RGBA", (SIZE, SIZE * 8))
        for i, images in enumerate(frames):
            sheet.paste(images[index], (0, i * SIZE))
        path = OUTPUT / f"hosted_machine_binder{suffix}.png"
        sheet.save(path)
        path.with_suffix(".png.mcmeta").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    # Presentation outputs are previews only; the game loads the 16px animation atlas above.
    previews = []
    for i in SEQUENCE:
        bg = Image.new("RGBA", (320, 320), (35, 41, 51, 255))
        bg.alpha_composite(frames[i][0].resize((256, 256), Image.Resampling.NEAREST), (32, 32))
        previews.append(bg.convert("RGB"))
    previews[0].save(PREVIEW / "binder-c-breathing.gif", save_all=True, append_images=previews[1:], duration=150, loop=0)
    overview = Image.new("RGB", (840, 390), (30, 36, 46))
    draw = ImageDraw.Draw(overview)
    for i, (index, label) in enumerate(((0, "DIM"), (7, "BRIGHT"))):
        tile = frames[index][0].resize((256, 256), Image.Resampling.NEAREST)
        overview.paste(tile, (24 + i * 296, 55), tile)
        draw.text((24 + i * 296, 26), label, fill=(170, 200, 220))
    draw.text((640, 26), "INVENTORY", fill=(170, 200, 220))
    for i, size in enumerate((16, 32, 64)):
        y = 65 + i * 90
        draw.rectangle((640, y, 711, y + 71), fill=(76, 80, 88), outline=(160, 169, 181))
        tile = frames[7][0].resize((size, size), Image.Resampling.NEAREST)
        overview.paste(tile, (644 + (64 - size) // 2, y + 4 + (64 - size) // 2), tile)
        draw.text((724, y + 28), f"{size}px", fill=(170, 200, 220))
    draw.text((24, 350), "C / LINK CLAMP   -   16px   -   2.1 second breathing loop", fill=(100, 220, 232))
    overview.save(PREVIEW / "binder-c-preview.png")
    comparison = Image.new("RGBA", (584, 320), (30, 36, 46, 255))
    draw = ImageDraw.Draw(comparison)
    draw.text((24, 12), "SELECTED C SOURCE", fill=(170, 200, 220))
    draw.text((312, 12), "16 x 16 / UNIFORM REDUCTION", fill=(170, 200, 220))
    original = Image.open(SOURCE).convert("RGBA")
    original = original.crop(original.getbbox())
    original.thumbnail((256, 256), Image.Resampling.NEAREST)
    comparison.alpha_composite(original, (24 + (256 - original.width) // 2, 44))
    comparison.alpha_composite(frames[7][0].resize((256, 256), Image.Resampling.NEAREST), (312, 44))
    comparison.convert("RGB").save(PREVIEW / "binder-c-source-comparison.png")


if __name__ == "__main__":
    main()
