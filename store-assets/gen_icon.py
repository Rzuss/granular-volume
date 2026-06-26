"""
Granular Volume — store + launcher icon generator.
Renders at 4x supersample then downscales for clean anti-aliased edges.
Brand: surface #1A1A2E, accent #6C63FF, white glyph.
Concept: ascending rounded "volume step" bars — conveys granular/fine control.
"""
from PIL import Image, ImageDraw, ImageFilter
import math

SS = 4  # supersample factor


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def vertical_gradient(size, top, bottom):
    img = Image.new("RGB", (size, size), top)
    px = img.load()
    for y in range(size):
        t = y / (size - 1)
        c = lerp(top, bottom, t)
        for x in range(size):
            px[x, y] = c
    return img


def diagonal_gradient(size, c1, c2):
    """Diagonal (top-left -> bottom-right) gradient."""
    img = Image.new("RGB", (size, size), c1)
    px = img.load()
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2 * (size - 1))
            px[x, y] = lerp(c1, c2, t)
    return img


def rounded_mask(size, radius):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return m


def make_icon(out_size, full_bleed=True, rounded=True):
    S = out_size * SS
    # Background: deep navy -> indigo accent, diagonal.
    NAVY = (0x16, 0x16, 0x2B)
    INDIGO = (0x4A, 0x43, 0xC9)
    bg = diagonal_gradient(S, NAVY, INDIGO)

    # Soft radial glow behind the bars (accent).
    glow = Image.new("RGB", (S, S), (0, 0, 0))
    gd = ImageDraw.Draw(glow)
    cx, cy = int(S * 0.52), int(S * 0.46)
    gr = int(S * 0.42)
    gd.ellipse([cx - gr, cy - gr, cx + gr, cy + gr], fill=(0x6C, 0x63, 0xFF))
    glow = glow.filter(ImageFilter.GaussianBlur(S * 0.10))
    bg = Image.blend(bg, Image.composite(glow, bg, glow.convert("L")), 0.35)

    draw = ImageDraw.Draw(bg, "RGBA")

    # Ascending volume-step bars.
    n = 5
    # Geometry within the icon's safe area.
    area_l = S * 0.26
    area_r = S * 0.74
    base_y = S * 0.72          # bottom of bars
    min_h = S * 0.14
    max_h = S * 0.44
    gap_ratio = 0.42
    total_w = area_r - area_l
    bar_w = total_w / (n + (n - 1) * gap_ratio)
    gap = bar_w * gap_ratio
    corner = bar_w * 0.42

    for i in range(n):
        x0 = area_l + i * (bar_w + gap)
        x1 = x0 + bar_w
        h = min_h + (max_h - min_h) * (i / (n - 1))
        y0 = base_y - h
        y1 = base_y
        # Brightest on the tallest bars; subtle fade on the shorter ones.
        a = int(200 + 55 * (i / (n - 1)))
        draw.rounded_rectangle([x0, y0, x1, y1], radius=corner,
                               fill=(255, 255, 255, a))

    # A clean arc "sound sweep" over the bars for recognizability.
    arc_bbox = [S * 0.20, S * 0.16, S * 0.92, S * 0.88]
    draw.arc(arc_bbox, start=-72, end=18, fill=(255, 255, 255, 230),
             width=int(S * 0.022))
    arc_bbox2 = [S * 0.10, S * 0.06, S * 1.02, S * 0.98]
    draw.arc(arc_bbox2, start=-66, end=12, fill=(0xC6, 0xC2, 0xFF, 170),
             width=int(S * 0.018))

    icon = bg
    if rounded:
        radius = int(S * 0.235)  # ~ squircle-ish; Play applies its own mask too
        mask = rounded_mask(S, radius)
        out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        out.paste(icon, (0, 0), mask)
        icon = out
    else:
        icon = icon.convert("RGBA")

    icon = icon.resize((out_size, out_size), Image.LANCZOS)
    return icon


def make_adaptive_foreground(out_size=432):
    """Adaptive-icon foreground: transparent, glyph centered in inner 66% safe zone."""
    S = out_size * SS
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img, "RGBA")

    n = 5
    area_l = S * 0.34
    area_r = S * 0.66
    base_y = S * 0.62
    min_h = S * 0.10
    max_h = S * 0.30
    gap_ratio = 0.42
    total_w = area_r - area_l
    bar_w = total_w / (n + (n - 1) * gap_ratio)
    gap = bar_w * gap_ratio
    corner = bar_w * 0.42
    for i in range(n):
        x0 = area_l + i * (bar_w + gap)
        x1 = x0 + bar_w
        h = min_h + (max_h - min_h) * (i / (n - 1))
        draw.rounded_rectangle([x0, base_y - h, x1, base_y], radius=corner,
                               fill=(255, 255, 255, 255))
    draw.arc([S * 0.28, S * 0.24, S * 0.74, S * 0.70], start=-72, end=18,
             fill=(255, 255, 255, 235), width=int(S * 0.018))
    return img.resize((out_size, out_size), Image.LANCZOS)


import os
OUT = os.path.dirname(os.path.abspath(__file__))

# 512x512 hi-res store icon (full square; Play rounds it itself -> keep square, no transparency).
store = make_icon(512, rounded=False)
store.convert("RGB").save(os.path.join(OUT, "play_icon_512.png"))

# Rounded preview (how it looks masked) for our own reference.
make_icon(512, rounded=True).save(os.path.join(OUT, "icon_rounded_preview.png"))

# Adaptive foreground PNGs for the app launcher (replaces the plain speaker glyph).
fg = make_adaptive_foreground(432)
for dpi, px in [("mdpi", 108), ("hdpi", 162), ("xhdpi", 216),
                ("xxhdpi", 324), ("xxxhdpi", 432)]:
    fg.resize((px, px), Image.LANCZOS).save(
        os.path.join(OUT, f"ic_launcher_foreground_{dpi}.png"))

print("Icons generated in", OUT)
