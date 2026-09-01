from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / "src/main/resources/assets/matterblueprints/textures/blocks"
OUTPUT = ROOT / "build/structure-preview/jiegou-hosted-machine.png"

# Top to bottom, back to front. This is the exact 17x5x5 jiegou.gtbp substitution map.
LAYERS = [
    [
        "LLLLLLLLLLLLLLLLL",
        "LHHHHHHHHHHHHHHHL",
        "LHHHHHHHHHHHHHHHL",
        "LHHHHHHHHHHHHHHHL",
        "LLLLLLLLLLLLLLLLL",
    ],
    [
        "LVVVVVVVVVVVVVVVL",
        "H---------------H",
        "H---------------H",
        "H---------------H",
        "LVVVVVVVVVVVVHHHL",
    ],
    [
        "LHHHHHHHHHHHHHHHL",
        "H---------------H",
        "H---------------H",
        "H---------------H",
        "LHHHHHHHHHHHHH~HL",
    ],
    [
        "LHHHHHHHHHHHHHHHL",
        "H---------------H",
        "H---------------H",
        "H---------------H",
        "LHHHHHHHHHHHHHHHL",
    ],
    [
        "LLLLLLLLLLLLLLLLL",
        "LLLLLLLLLLLLLLLLL",
        "LLLLLLLLLLLLLLLLL",
        "LLLLLLLLLLLLLLLLL",
        "LLLLLLLLLLLLLLLLL",
    ],
]

COLORS = {
    "L": (25, 45, 62),
    "H": (36, 43, 52),
    "V": (22, 28, 36),
    "~": (28, 50, 61),
    "-": (13, 17, 22),
}
HATCH_SAMPLES = {(1, 2): (43, 189, 220), (5, 2): (229, 145, 53), (9, 3): (74, 206, 129), (13, 3): (149, 91, 235)}


def font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    for path in (Path("C:/Windows/Fonts/msyh.ttc"), Path("C:/Windows/Fonts/simhei.ttf")):
        if path.exists():
            return ImageFont.truetype(str(path), size)
    return ImageFont.load_default()


def texture(name: str) -> Image.Image:
    image = Image.open(TEXTURES / name).convert("RGB")
    return image.crop((0, 0, 16, 16))


def hatch_texture(color: tuple[int, int, int]) -> Image.Image:
    image = texture("host_casing.png")
    draw = ImageDraw.Draw(image)
    draw.rectangle((3, 3, 12, 12), fill=(21, 26, 33), outline=(73, 83, 96))
    draw.rectangle((5, 5, 10, 10), fill=tuple(max(0, value // 3) for value in color), outline=color)
    draw.rectangle((7, 7, 8, 8), fill=(208, 251, 255))
    return image


def controller_texture() -> Image.Image:
    image = texture("host_casing.png")
    draw = ImageDraw.Draw(image)
    draw.rectangle((3, 3, 12, 12), fill=(14, 20, 27), outline=(73, 93, 108))
    draw.line((5, 5, 10, 5, 10, 10, 5, 10, 5, 5), fill=(54, 215, 241), width=1)
    draw.rectangle((7, 7, 8, 8), fill=(178, 250, 255))
    draw.point((11, 11), fill=(90, 231, 130))
    return image


def front_texture(code: str, x: int, y: int) -> Image.Image:
    if code == "L":
        return texture("host_casing_light.png")
    if code == "V":
        return texture("host_cooling_fan.png")
    if code == "~":
        return controller_texture()
    if code == "H" and (x, y) in HATCH_SAMPLES:
        return hatch_texture(HATCH_SAMPLES[(x, y)])
    return texture("host_casing.png")


def draw_front(draw: ImageDraw.ImageDraw, image: Image.Image, origin: tuple[int, int], cell: int) -> None:
    ox, oy = origin
    for y, layer in enumerate(LAYERS):
        for x, code in enumerate(layer[4]):
            tile = front_texture(code, x, y).resize((cell, cell), Image.Resampling.NEAREST)
            image.paste(tile, (ox + x * cell, oy + y * cell))
    draw.rectangle((ox - 2, oy - 2, ox + 17 * cell + 1, oy + 5 * cell + 1), outline=(94, 112, 131), width=2)


def shade(code: str, factor: float) -> tuple[int, int, int]:
    return tuple(min(255, int(value * factor)) for value in COLORS[code])


def draw_oblique(draw: ImageDraw.ImageDraw, image: Image.Image, origin: tuple[int, int], cell: int) -> None:
    ox, oy = origin
    sx, sy = 11, -8

    # Top surface, back to front.
    for z in range(5):
        for x in range(17):
            code = LAYERS[0][z][x]
            px = ox + x * cell + (4 - z) * sx
            py = oy + (4 - z) * sy
            polygon = [(px, py), (px + cell, py), (px + cell + sx, py + sy), (px + sx, py + sy)]
            draw.polygon(polygon, fill=shade(code, 1.15), outline=(75, 89, 105))
            if code == "L":
                draw.line((px + 4, py - 2, px + cell - 3, py - 2), fill=(48, 215, 244), width=2)

    # Right short wall.
    for z in range(4, -1, -1):
        for y in range(5):
            code = LAYERS[y][z][16]
            px = ox + 17 * cell + (4 - z) * sx
            py = oy + y * cell + (4 - z) * sy
            polygon = [(px, py), (px + sx, py + sy), (px + sx, py + sy + cell), (px, py + cell)]
            draw.polygon(polygon, fill=shade(code, 0.72), outline=(54, 66, 80))
            if code == "L":
                draw.line((px + 3, py + cell // 2, px + sx - 1, py + sy + cell // 2), fill=(115, 75, 235), width=2)

    # Front wall carries exact pixel textures and optional hatch examples.
    draw_front(draw, image, (ox, oy), cell)


def draw_plan(draw: ImageDraw.ImageDraw, origin: tuple[int, int], rows: list[str], cell: int, title: str) -> None:
    ox, oy = origin
    draw.text((ox, oy - 32), title, font=font(22), fill=(220, 230, 239))
    for z, row in enumerate(rows):
        for x, code in enumerate(row):
            left = ox + x * cell
            top = oy + z * cell
            draw.rectangle((left, top, left + cell - 1, top + cell - 1), fill=COLORS[code], outline=(66, 78, 92))
            if code == "L":
                draw.line((left + 3, top + cell // 2, left + cell - 4, top + cell // 2), fill=(47, 215, 245), width=2)
            elif code == "V":
                draw.ellipse((left + 4, top + 4, left + cell - 5, top + cell - 5), outline=(85, 105, 119), width=2)
            elif code == "~":
                draw.rectangle((left + 4, top + 4, left + cell - 5, top + cell - 5), outline=(53, 215, 241), width=2)


def main() -> None:
    assert len(LAYERS) == 5 and all(len(layer) == 5 for layer in LAYERS)
    assert all(len(row) == 17 for layer in LAYERS for row in layer)
    counts = {code: sum(row.count(code) for layer in LAYERS for row in layer) for code in "LHV-~"}
    assert counts == {"L": 137, "H": 125, "V": 27, "-": 135, "~": 1}

    image = Image.new("RGB", (1280, 720), (15, 19, 25))
    draw = ImageDraw.Draw(image)
    draw.text((54, 28), "多方块机器托管中心 · jiegou 结构替换示意", font=font(32), fill=(225, 235, 243))
    draw.text((56, 72), "17×5×5 · 正面偏右控制器 · 137 灯带 / 27 风扇 / 125 可替换仓位", font=font(19), fill=(129, 158, 180))

    draw_oblique(draw, image, (72, 170), 26)
    draw.text((72, 330), "正面：风扇带 + 深色融合仓室示例", font=font(20), fill=(204, 218, 229))
    draw.text((72, 360), "彩色小面板代表可选仓室；其底板仍使用同一深灰外壳。", font=font(16), fill=(126, 151, 171))

    draw_plan(draw, (690, 152), LAYERS[0], 23, "顶视图（屋顶）")
    draw_plan(draw, (690, 340), LAYERS[4], 23, "底视图（完整灯带基底）")

    legend = [
        ("L", "流动数据灯带"),
        ("V", "同风格散热风扇"),
        ("H", "深灰外壳 / 可替换仓位"),
        ("~", "托管中心控制器"),
    ]
    lx, ly = 690, 548
    draw.text((lx, ly - 32), "图例", font=font(22), fill=(220, 230, 239))
    for index, (code, label) in enumerate(legend):
        x = lx + (index % 2) * 250
        y = ly + (index // 2) * 46
        draw.rectangle((x, y, x + 28, y + 28), fill=COLORS[code], outline=(88, 105, 123), width=2)
        if code == "L":
            draw.line((x + 4, y + 14, x + 24, y + 14), fill=(46, 218, 246), width=3)
        draw.text((x + 40, y + 2), label, font=font(17), fill=(193, 207, 219))

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUTPUT, format="PNG", optimize=False)


if __name__ == "__main__":
    main()
