from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src" / "main" / "resources" / "assets" / "matterblueprints" / "textures" / "blocks"
PREVIEW = ROOT / "build" / "texture-preview" / "hosted-machine-blocks.png"
ANIMATED_PREVIEW = ROOT / "build" / "texture-preview" / "hosted-machine-flow.gif"
FRAME_COUNT = 8


def empty() -> Image.Image:
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def casing_base() -> Image.Image:
    """Dense graphite machine-room panel with a GT-style stepped bevel."""
    image = Image.new("RGBA", (16, 16), (31, 36, 43, 255))
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 15, 15), fill=(24, 28, 34, 255))
    draw.line((0, 0, 15, 0), fill=(92, 101, 113, 255))
    draw.line((0, 0, 0, 15), fill=(73, 82, 94, 255))
    draw.line((0, 15, 15, 15), fill=(9, 12, 16, 255))
    draw.line((15, 1, 15, 15), fill=(13, 17, 22, 255))
    draw.rectangle((1, 1, 14, 14), fill=(42, 48, 57, 255))
    draw.line((1, 1, 14, 1), fill=(74, 84, 97, 255))
    draw.line((1, 1, 1, 14), fill=(62, 71, 83, 255))
    draw.line((1, 14, 14, 14), fill=(18, 22, 28, 255))
    draw.line((14, 2, 14, 14), fill=(23, 28, 35, 255))

    # Four recessed service panels; asymmetric highlights keep the face from
    # looking flat when tiled across a large machine-room wall.
    for left, top in ((2, 2), (8, 2), (2, 8), (8, 8)):
        draw.rectangle((left, top, left + 5, top + 5), fill=(29, 34, 41, 255))
        draw.line((left, top, left + 5, top), fill=(59, 68, 79, 255))
        draw.line((left, top, left, top + 5), fill=(52, 61, 72, 255))
        draw.line((left, top + 5, left + 5, top + 5), fill=(17, 21, 27, 255))
        draw.line((left + 5, top + 1, left + 5, top + 5), fill=(20, 25, 31, 255))
        draw.rectangle((left + 1, top + 1, left + 4, top + 4), fill=(35, 41, 49, 255))
        draw.point((left + 1, top + 1), fill=(73, 83, 96, 255))
        draw.point((left + 4, top + 4), fill=(18, 23, 29, 255))

    # Bolts and thin ZPM-blue status accents.
    for point in ((1, 1), (14, 1), (1, 14), (14, 14)):
        draw.point(point, fill=(126, 136, 148, 255))
    draw.line((4, 7, 6, 7), fill=(34, 135, 161, 255))
    draw.line((10, 7, 12, 7), fill=(44, 154, 181, 255))
    return image


def casing_emissive() -> Image.Image:
    image = empty()
    draw = ImageDraw.Draw(image)
    draw.line((4, 7, 6, 7), fill=(47, 203, 233, 255))
    draw.line((10, 7, 12, 7), fill=(61, 220, 244, 255))
    return image


def light_frame(frame: int, emissive: bool) -> Image.Image:
    image = empty() if emissive else casing_base()
    draw = ImageDraw.Draw(image)
    if not emissive:
        draw.rectangle((2, 3, 13, 12), fill=(19, 25, 34, 255))
        draw.line((2, 3, 13, 3), fill=(70, 80, 96, 255))
        draw.line((2, 3, 2, 12), fill=(58, 68, 83, 255))
        draw.line((2, 12, 13, 12), fill=(10, 14, 20, 255))
        draw.line((13, 4, 13, 12), fill=(12, 17, 24, 255))

    cyan = (44, 213, 245, 255)
    cyan_hi = (166, 250, 255, 255)
    violet = (126, 72, 243, 255)
    violet_hi = (213, 181, 255, 255)
    dim_cyan = (25, 103, 128, 255)
    dim_violet = (58, 39, 120, 255)

    # Two data lanes. Bright two-pixel packets move in opposite directions,
    # producing a readable flow even when neighbouring blocks start together.
    for x in range(3, 13):
        draw.point((x, 6), fill=dim_cyan)
        draw.point((x, 9), fill=dim_violet)
    cyan_x = 3 + frame
    violet_x = 12 - frame
    draw.point((cyan_x, 6), fill=cyan_hi)
    draw.point((min(12, cyan_x + 1), 6), fill=cyan)
    draw.point((violet_x, 9), fill=violet_hi)
    draw.point((max(3, violet_x - 1), 9), fill=violet)

    # Small vertical bus joins the lanes and gives each full block a coherent
    # face rather than the appearance of a multipart strip.
    join_x = 4 + ((frame * 3) % 8)
    draw.line((join_x, 7, join_x, 8), fill=cyan if frame % 2 == 0 else violet)
    if not emissive:
        draw.point((3, 4), fill=(88, 101, 117, 255))
        draw.point((12, 11), fill=(30, 36, 45, 255))
    return image


def vertical_light_frame(frame: int, emissive: bool) -> Image.Image:
    """The same data lanes rotated per 16x16 frame, not as one tall animation atlas."""
    return light_frame(frame, emissive).transpose(Image.Transpose.ROTATE_90)


def receiver_top(emissive: bool) -> Image.Image:
    image = empty() if emissive else casing_base()
    draw = ImageDraw.Draw(image)
    if not emissive:
        draw.rectangle((2, 2, 13, 13), fill=(18, 23, 31, 255))
        draw.line((2, 2, 13, 2), fill=(91, 102, 119, 255))
        draw.line((2, 2, 2, 13), fill=(72, 84, 101, 255))
        draw.line((2, 13, 13, 13), fill=(8, 12, 18, 255))
        draw.line((13, 3, 13, 13), fill=(11, 16, 23, 255))
        draw.rectangle((4, 4, 11, 11), fill=(29, 38, 51, 255))
    cyan = (70, 226, 250, 255)
    violet = (161, 102, 255, 255)
    highlight = (213, 253, 255, 255)
    draw.line((5, 4, 10, 4, 11, 5, 11, 10, 10, 11, 5, 11, 4, 10, 4, 5, 5, 4), fill=cyan)
    draw.line((6, 6, 9, 6, 9, 9, 6, 9, 6, 6), fill=violet)
    draw.rectangle((7, 7, 8, 8), fill=highlight)
    return image


def receiver_side(emissive: bool) -> Image.Image:
    image = empty() if emissive else casing_base()
    draw = ImageDraw.Draw(image)
    if not emissive:
        draw.rectangle((3, 4, 12, 11), fill=(17, 22, 30, 255))
        draw.line((3, 4, 12, 4), fill=(83, 94, 109, 255))
        draw.line((3, 4, 3, 11), fill=(65, 76, 91, 255))
        draw.line((3, 11, 12, 11), fill=(9, 13, 18, 255))
        draw.line((12, 5, 12, 11), fill=(11, 16, 22, 255))
    cyan = (57, 220, 247, 255)
    violet = (152, 91, 255, 255)
    draw.line((5, 7, 10, 7), fill=cyan)
    draw.line((5, 8, 10, 8), fill=violet)
    draw.point((4, 7), fill=(177, 248, 255, 255))
    draw.point((11, 8), fill=(220, 190, 255, 255))
    return image


def cooling_fan(emissive: bool) -> Image.Image:
    """Recessed server-rack fan using the same graphite, cyan and violet language as the host casing."""
    image = empty() if emissive else casing_base()
    draw = ImageDraw.Draw(image)
    if not emissive:
        draw.rectangle((2, 2, 13, 13), fill=(14, 18, 24, 255))
        draw.line((2, 2, 13, 2), fill=(76, 87, 101, 255))
        draw.line((2, 2, 2, 13), fill=(62, 73, 87, 255))
        draw.line((2, 13, 13, 13), fill=(7, 10, 14, 255))
        draw.line((13, 3, 13, 13), fill=(9, 13, 18, 255))
        draw.rectangle((4, 4, 11, 11), fill=(25, 31, 39, 255))
        # Four stepped blades read cleanly at 16x16 without becoming a flat cross.
        draw.polygon(((7, 4), (9, 4), (9, 7), (8, 8), (7, 7)), fill=(57, 66, 78, 255))
        draw.polygon(((9, 7), (11, 7), (11, 9), (8, 9), (7, 8)), fill=(48, 57, 69, 255))
        draw.polygon(((7, 9), (8, 8), (9, 9), (9, 11), (7, 11)), fill=(39, 47, 58, 255))
        draw.polygon(((4, 7), (7, 7), (8, 8), (7, 9), (4, 9)), fill=(46, 55, 67, 255))
        for point in ((3, 3), (12, 3), (3, 12), (12, 12)):
            draw.point(point, fill=(111, 123, 138, 255))

    cyan = (67, 224, 248, 255)
    violet = (154, 91, 247, 255)
    highlight = (206, 252, 255, 255)
    draw.rectangle((7, 7, 8, 8), fill=cyan)
    draw.point((7, 7), fill=highlight)
    draw.point((8, 8), fill=violet)
    draw.point((4, 7), fill=cyan)
    draw.point((11, 8), fill=violet)
    return image


def atlas(frames: list[Image.Image]) -> Image.Image:
    image = Image.new("RGBA", (16, 16 * len(frames)), (0, 0, 0, 0))
    for index, frame in enumerate(frames):
        image.paste(frame, (0, index * 16))
    return image


def write_preview(textures: dict[str, Image.Image]) -> None:
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    labels = [
        ("Casing", textures["host_casing.png"]),
        ("Flow light", textures["host_casing_light.png"].crop((0, 0, 16, 16))),
        ("Cooling fan", textures["host_cooling_fan.png"]),
        ("Receiver top", textures["host_receiver_top.png"]),
    ]
    preview = Image.new("RGB", (4 * 160, 196), (18, 21, 27))
    for index, (_, texture) in enumerate(labels):
        enlarged = texture.resize((128, 128), Image.Resampling.NEAREST).convert("RGB")
        preview.paste(enlarged, (16 + index * 160, 16))
    preview.save(PREVIEW, format="PNG", optimize=False)

    animated_frames = []
    light_atlas = textures["host_casing_light.png"]
    for frame in range(FRAME_COUNT):
        animated = preview.copy()
        light = light_atlas.crop((0, frame * 16, 16, (frame + 1) * 16))
        animated.paste(light.resize((128, 128), Image.Resampling.NEAREST).convert("RGB"), (176, 16))
        animated_frames.append(animated)
    animated_frames[0].save(
        ANIMATED_PREVIEW,
        save_all=True,
        append_images=animated_frames[1:] + animated_frames[-2:0:-1],
        duration=100,
        loop=0,
        optimize=False,
    )


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    textures = {
        "host_casing.png": casing_base(),
        "host_casing_emissive.png": casing_emissive(),
        "host_casing_light.png": atlas([light_frame(frame, False) for frame in range(FRAME_COUNT)]),
        "host_casing_light_emissive.png": atlas([light_frame(frame, True) for frame in range(FRAME_COUNT)]),
        "host_casing_light_vertical.png": atlas(
            [vertical_light_frame(frame, False) for frame in range(FRAME_COUNT)]),
        "host_casing_light_vertical_emissive.png": atlas(
            [vertical_light_frame(frame, True) for frame in range(FRAME_COUNT)]),
        "host_receiver_top.png": receiver_top(False),
        "host_receiver_top_emissive.png": receiver_top(True),
        "host_receiver_side.png": receiver_side(False),
        "host_receiver_side_emissive.png": receiver_side(True),
        "host_cooling_fan.png": cooling_fan(False),
        "host_cooling_fan_emissive.png": cooling_fan(True),
    }
    for filename, image in textures.items():
        if image.width != 16 or image.height not in (16, 16 * FRAME_COUNT):
            raise ValueError(f"unexpected texture size for {filename}: {image.size}")
        image.save(OUTPUT / filename, format="PNG", optimize=False)
    write_preview(textures)


if __name__ == "__main__":
    main()
