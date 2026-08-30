from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
REFERENCE = ROOT / "reference-textures" / "gtnh-zpm-small-machine"
OUTPUT = ROOT / "src" / "main" / "resources" / "assets" / "matterblueprints" / "textures" / "blocks"


def base(name: str) -> Image.Image:
    return Image.open(REFERENCE / name).convert("RGBA")


def vertical_gradient(
    draw: ImageDraw.ImageDraw,
    box: tuple[int, int, int, int],
    top: tuple[int, int, int, int],
    bottom: tuple[int, int, int, int],
) -> None:
    left, upper, right, lower = box
    height = max(1, lower - upper)
    for y in range(upper, lower + 1):
        amount = (y - upper) / height
        color = tuple(round(a + (b - a) * amount) for a, b in zip(top, bottom))
        draw.line((left, y, right, y), fill=color, width=1)


def transparent() -> Image.Image:
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def make_front() -> Image.Image:
    image = base("MACHINE_ZPM_SIDE.png")
    draw = ImageDraw.Draw(image)

    # Blueprint display: bright top/left bevel, dark bottom/right bevel and a
    # stepped glass gradient recreate the depth of GT's native machine faces.
    draw.rectangle((1, 1, 10, 14), fill=(72, 77, 91, 255))
    draw.line((1, 1, 10, 1), fill=(205, 209, 220, 255), width=1)
    draw.line((1, 1, 1, 14), fill=(174, 180, 194, 255), width=1)
    draw.line((1, 14, 10, 14), fill=(48, 52, 63, 255), width=1)
    draw.line((10, 2, 10, 14), fill=(54, 58, 70, 255), width=1)
    draw.rectangle((2, 2, 9, 13), fill=(35, 42, 51, 255))
    draw.line((2, 2, 9, 2), fill=(101, 109, 126, 255), width=1)
    draw.line((2, 2, 2, 13), fill=(84, 92, 107, 255), width=1)
    draw.line((2, 13, 9, 13), fill=(20, 24, 30, 255), width=1)
    vertical_gradient(draw, (3, 3, 8, 12), (28, 51, 58, 255), (7, 14, 18, 255))
    draw.point((4, 4), fill=(34, 60, 67, 255))
    draw.point((7, 11), fill=(12, 25, 29, 255))

    cyan_dark = (56, 132, 149, 255)
    cyan = (112, 210, 225, 255)
    cyan_highlight = (166, 235, 243, 255)
    # Three linked blueprint blocks, reduced to a one-pixel readable glyph.
    draw.line((3, 8, 3, 10, 5, 10, 5, 8, 3, 8), fill=cyan, width=1)
    draw.line((7, 8, 7, 10, 9, 10, 9, 8, 7, 8), fill=cyan, width=1)
    draw.line((5, 5, 5, 7, 7, 7, 7, 5, 5, 5), fill=cyan, width=1)
    draw.line((4, 7, 8, 7), fill=cyan_dark, width=1)
    draw.point((3, 8), fill=cyan_highlight)
    draw.point((5, 5), fill=cyan_highlight)
    draw.point((7, 8), fill=cyan_highlight)

    # Blueprint cartridge window.
    draw.rectangle((11, 2, 14, 10), fill=(61, 66, 78, 255))
    draw.line((11, 2, 14, 2), fill=(177, 182, 194, 255), width=1)
    draw.line((11, 2, 11, 10), fill=(137, 143, 157, 255), width=1)
    draw.line((11, 10, 14, 10), fill=(39, 43, 52, 255), width=1)
    vertical_gradient(draw, (12, 3, 13, 9), (251, 253, 252, 255), (176, 187, 191, 255))
    draw.point((12, 4), fill=(160, 222, 231, 255))
    draw.point((13, 6), fill=(111, 190, 207, 255))
    draw.point((12, 8), fill=(139, 209, 221, 255))

    # Red/green status lamps.
    draw.rectangle((11, 11, 14, 14), fill=(45, 49, 58, 255))
    draw.line((11, 11, 14, 11), fill=(98, 104, 119, 255), width=1)
    draw.line((11, 14, 14, 14), fill=(25, 28, 34, 255), width=1)
    draw.point((12, 12), fill=(255, 76, 57, 255))
    draw.point((13, 12), fill=(87, 238, 91, 255))
    draw.point((12, 13), fill=(174, 17, 20, 255))
    draw.point((13, 13), fill=(23, 139, 44, 255))
    return image


def make_top() -> Image.Image:
    image = base("MACHINE_ZPM_TOP.png")
    draw = ImageDraw.Draw(image)

    # Recessed blueprint screen with a two-stage metallic bezel.
    draw.rectangle((1, 1, 14, 14), fill=(67, 72, 86, 255))
    draw.line((1, 1, 14, 1), fill=(210, 214, 224, 255), width=1)
    draw.line((1, 1, 1, 14), fill=(176, 183, 196, 255), width=1)
    draw.line((1, 14, 14, 14), fill=(43, 47, 58, 255), width=1)
    draw.line((14, 2, 14, 14), fill=(50, 54, 66, 255), width=1)
    draw.rectangle((2, 2, 13, 12), fill=(31, 43, 51, 255))
    draw.line((2, 2, 13, 2), fill=(104, 119, 133, 255), width=1)
    draw.line((2, 2, 2, 12), fill=(82, 101, 114, 255), width=1)
    vertical_gradient(draw, (3, 3, 12, 11), (67, 111, 124, 255), (31, 61, 70, 255))
    for y in (4, 7, 10):
        for x in (4, 7, 10):
            draw.point((x, y), fill=(75, 122, 134, 255))

    cyan_dark = (65, 143, 157, 255)
    cyan = (118, 218, 230, 255)
    # Simplified machine-plan glyph from the supplied top-face design.
    draw.line((4, 8, 11, 8), fill=cyan, width=1)
    draw.line((3, 9, 3, 10, 12, 10, 12, 9), fill=cyan, width=1)
    draw.line((5, 5, 5, 8, 6, 8, 6, 5), fill=cyan, width=1)
    draw.line((9, 4, 9, 8, 10, 8, 10, 4), fill=cyan, width=1)
    draw.point((6, 6), fill=cyan_dark)
    draw.point((10, 5), fill=cyan_dark)
    draw.point((5, 5), fill=(171, 240, 245, 255))
    draw.point((9, 4), fill=(171, 240, 245, 255))

    # Small scanner/emitter along the front edge of the top panel.
    draw.rectangle((6, 12, 9, 15), fill=(38, 42, 51, 255))
    draw.line((6, 12, 9, 12), fill=(135, 141, 155, 255), width=1)
    draw.point((6, 13), fill=(92, 98, 112, 255))
    draw.point((9, 13), fill=(59, 64, 76, 255))
    draw.rectangle((7, 13, 8, 15), fill=(44, 121, 139, 255))
    draw.point((7, 13), fill=(168, 240, 246, 255))
    draw.point((8, 14), fill=cyan)
    return image


def vent(draw: ImageDraw.ImageDraw, left: int, top: int) -> None:
    draw.rectangle((left, top, left + 4, top + 4), fill=(69, 74, 87, 255))
    draw.line((left, top, left + 4, top), fill=(194, 199, 211, 255), width=1)
    draw.line((left, top, left, top + 4), fill=(157, 164, 178, 255), width=1)
    draw.line((left, top + 4, left + 4, top + 4), fill=(46, 50, 60, 255), width=1)
    draw.line((left + 4, top + 1, left + 4, top + 4), fill=(51, 55, 66, 255), width=1)
    draw.line((left + 1, top + 1, left + 1, top + 3), fill=(8, 10, 12, 255))
    draw.line((left + 2, top + 1, left + 2, top + 3), fill=(25, 28, 34, 255))
    draw.line((left + 3, top + 1, left + 3, top + 3), fill=(5, 7, 9, 255))
    draw.point((left + 1, top + 1), fill=(37, 40, 47, 255))


def make_side() -> Image.Image:
    image = base("MACHINE_ZPM_SIDE.png")
    draw = ImageDraw.Draw(image)

    # Subtle panel seams keep the ZPM noise while adding directional depth.
    draw.line((1, 1, 14, 1), fill=(250, 250, 252, 255), width=1)
    draw.line((1, 14, 14, 14), fill=(174, 177, 188, 255), width=1)
    draw.line((1, 2, 1, 13), fill=(229, 231, 237, 255), width=1)
    draw.line((14, 2, 14, 13), fill=(188, 191, 201, 255), width=1)

    # Four ventilation grilles, matching the supplied side face.
    vent(draw, 1, 2)
    vent(draw, 10, 2)
    vent(draw, 1, 9)
    vent(draw, 10, 9)

    # Central recessed status channel and violet energy indicator.
    draw.rectangle((6, 3, 9, 13), fill=(152, 156, 170, 255))
    draw.line((6, 3, 9, 3), fill=(222, 225, 232, 255), width=1)
    draw.line((6, 3, 6, 13), fill=(199, 203, 213, 255), width=1)
    draw.line((6, 13, 9, 13), fill=(103, 108, 122, 255), width=1)
    draw.line((9, 4, 9, 13), fill=(116, 121, 136, 255), width=1)
    vertical_gradient(draw, (7, 5, 8, 11), (188, 145, 255, 255), (82, 45, 151, 255))
    draw.point((7, 5), fill=(225, 200, 255, 255))
    draw.point((8, 10), fill=(111, 63, 202, 255))
    draw.rectangle((7, 2, 8, 3), fill=(211, 214, 222, 255))
    draw.point((7, 2), fill=(250, 250, 252, 255))
    return image


def make_front_emissive() -> Image.Image:
    image = transparent()
    draw = ImageDraw.Draw(image)
    cyan = (123, 222, 235, 255)
    cyan_highlight = (190, 248, 252, 255)
    draw.line((3, 8, 3, 10, 5, 10, 5, 8, 3, 8), fill=cyan, width=1)
    draw.line((7, 8, 7, 10, 9, 10, 9, 8, 7, 8), fill=cyan, width=1)
    draw.line((5, 5, 5, 7, 7, 7, 7, 5, 5, 5), fill=cyan, width=1)
    draw.line((4, 7, 8, 7), fill=(69, 157, 174, 255), width=1)
    draw.point((3, 8), fill=cyan_highlight)
    draw.point((5, 5), fill=cyan_highlight)
    draw.point((7, 8), fill=cyan_highlight)
    draw.point((12, 4), fill=(178, 237, 244, 255))
    draw.point((13, 6), fill=(119, 206, 220, 255))
    draw.point((12, 8), fill=(145, 220, 231, 255))
    draw.point((12, 12), fill=(255, 82, 61, 255))
    draw.point((13, 12), fill=(95, 255, 101, 255))
    draw.point((12, 13), fill=(211, 25, 26, 255))
    draw.point((13, 13), fill=(30, 179, 52, 255))
    return image


def make_top_emissive() -> Image.Image:
    image = transparent()
    draw = ImageDraw.Draw(image)
    cyan = (130, 231, 240, 255)
    draw.line((4, 8, 11, 8), fill=cyan, width=1)
    draw.line((3, 9, 3, 10, 12, 10, 12, 9), fill=cyan, width=1)
    draw.line((5, 5, 5, 8, 6, 8, 6, 5), fill=cyan, width=1)
    draw.line((9, 4, 9, 8, 10, 8, 10, 4), fill=cyan, width=1)
    draw.point((5, 5), fill=(190, 248, 252, 255))
    draw.point((9, 4), fill=(190, 248, 252, 255))
    draw.rectangle((7, 13, 8, 15), fill=(74, 177, 196, 255))
    draw.point((7, 13), fill=(195, 250, 253, 255))
    draw.point((8, 14), fill=(137, 234, 242, 255))
    return image


def make_side_emissive() -> Image.Image:
    image = transparent()
    draw = ImageDraw.Draw(image)
    vertical_gradient(draw, (7, 5, 8, 11), (225, 195, 255, 255), (119, 67, 220, 255))
    draw.point((7, 5), fill=(244, 229, 255, 255))
    return image


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    textures = {
        "blueprint_configurator_front.png": make_front(),
        "blueprint_configurator_top.png": make_top(),
        "blueprint_configurator_side.png": make_side(),
        "blueprint_configurator_front_emissive.png": make_front_emissive(),
        "blueprint_configurator_top_emissive.png": make_top_emissive(),
        "blueprint_configurator_side_emissive.png": make_side_emissive(),
    }
    for filename, image in textures.items():
        if image.size != (16, 16):
            raise ValueError(f"{filename} is {image.size}, expected 16x16")
        image.save(OUTPUT / filename, format="PNG", optimize=False)


if __name__ == "__main__":
    main()
