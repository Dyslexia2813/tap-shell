#!/usr/bin/env python3
"""Делает иконки лаунчера (adaptive) из картинки.

Использование: make_icons.py <иконка.png> <папка res>
Если файла иконки нет, рисует временную заглушку и пишет предупреждение.
"""
import os
import sys

from PIL import Image, ImageDraw

THEME_BG = (0x0F, 0x17, 0x2A)
# размеры foreground-слоя adaptive icon: 108dp для каждой плотности
DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
SAFE_ZONE = 0.66  # картинка занимает 66% слоя, чтобы маска лаунчера её не обрезала


def placeholder():
    print("::warning::Site icon not found, using a placeholder icon. "
          "Add icon.png (512x512) to the repo root.")
    img = Image.new("RGBA", (512, 512), THEME_BG + (255,))
    d = ImageDraw.Draw(img)
    d.rectangle([116, 116, 396, 190], fill=(255, 255, 255, 255))
    d.rectangle([220, 190, 292, 396], fill=(255, 255, 255, 255))
    return img


def square(img):
    w, h = img.size
    if w == h:
        return img
    side = max(w, h)
    out = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    out.paste(img, ((side - w) // 2, (side - h) // 2))
    return out


def background_color(img):
    """Цвет фона иконки: берём с края картинки, иначе цвет темы сайта."""
    w, h = img.size
    for xy in [(2, h // 2), (w - 3, h // 2), (w // 2, 2), (w // 2, h - 3), (2, 2)]:
        r, g, b, a = img.getpixel(xy)
        if a == 255:
            return (r, g, b)
    return THEME_BG


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    src, res = sys.argv[1], sys.argv[2]
    if os.path.exists(src):
        img = Image.open(src).convert("RGBA")
    else:
        img = placeholder()
    img = square(img)
    bg = background_color(img)

    os.makedirs(os.path.join(res, "values"), exist_ok=True)
    with open(os.path.join(res, "values", "icon_colors.xml"), "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                f'    <color name="ic_launcher_background">#{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}</color>\n'
                '</resources>\n')

    for name, size in DENSITIES.items():
        folder = os.path.join(res, f"mipmap-{name}")
        os.makedirs(folder, exist_ok=True)
        layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        inner = int(size * SAFE_ZONE)
        icon = img.resize((inner, inner), Image.LANCZOS)
        layer.paste(icon, ((size - inner) // 2, (size - inner) // 2), icon)
        layer.save(os.path.join(folder, "ic_launcher_foreground.png"), "PNG")
    print(f"Icons written to {res}, background #{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
