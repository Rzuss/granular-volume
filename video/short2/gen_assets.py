#!/usr/bin/env python3
"""Generate all still assets for the vertical TikTok cut (1080x1920)."""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
FONTS = os.path.join(HERE, "..", "assets", "inter_x", "extras", "ttf")
ICON = os.path.join(HERE, "..", "..", "store-assets", "play_icon_512.png")
OUT = os.path.join(HERE, "assets")
os.makedirs(OUT, exist_ok=True)

W, H = 1080, 1920
# brand palette
BG0 = (10, 14, 29)      # #0a0e1d
BG1 = (18, 24, 51)      # #121833
ACCENT = (108, 99, 255) # #6C63FF
ACCENT2 = (34, 211, 238)# #22d3ee
TXT = (244, 246, 255)   # #f4f6ff
MUTED = (154, 163, 199) # #9aa3c7

def font(name, size):
    return ImageFont.truetype(os.path.join(FONTS, name), size)

BOLD = lambda s: font("Inter-Bold.ttf", s)
SEMI = lambda s: font("Inter-SemiBold.ttf", s)
REG  = lambda s: font("Inter-Regular.ttf", s)

# phone placement (must match build script)
PH_W, PH_H = 713, 1584
PH_X, PH_Y = 184, 286
RADIUS = 40

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

# ---------------------------------------------------------------- background
def make_bg():
    img = Image.new("RGB", (W, H), BG0)
    px = img.load()
    # vertical gradient BG1 (top) -> BG0 (bottom)
    for y in range(H):
        c = lerp(BG1, BG0, min(1.0, y / (H * 0.55)))
        for x in range(W):
            px[x, y] = c
    # soft radial accent glow behind where the phone sits
    glow = Image.new("L", (W, H), 0)
    gd = ImageDraw.Draw(glow)
    cx, cy = W // 2, PH_Y + PH_H // 2 - 120
    gd.ellipse([cx - 520, cy - 620, cx + 520, cy + 620], fill=90)
    glow = glow.filter(ImageFilter.GaussianBlur(180))
    tint = Image.new("RGB", (W, H), ACCENT)
    img = Image.composite(Image.blend(img, tint, 0.5), img, glow)
    # subtle top hairline of accent2 energy
    top = Image.new("L", (W, H), 0)
    td = ImageDraw.Draw(top)
    td.ellipse([W * 0.2, -420, W * 0.8, 200], fill=70)
    top = top.filter(ImageFilter.GaussianBlur(120))
    tint2 = Image.new("RGB", (W, H), ACCENT2)
    img = Image.composite(Image.blend(img, tint2, 0.35), img, top)
    # vignette
    vig = Image.new("L", (W, H), 0)
    vd = ImageDraw.Draw(vig)
    vd.rectangle([0, 0, W, H], fill=0)
    vd.ellipse([-W * 0.35, -H * 0.15, W * 1.35, H * 1.15], fill=255)
    vig = vig.filter(ImageFilter.GaussianBlur(220))
    dark = Image.new("RGB", (W, H), (2, 4, 12))
    img = Image.composite(img, dark, vig)
    img.save(os.path.join(OUT, "bg.png"))

# ---------------------------------------------------------------- phone mask
def make_mask():
    m = Image.new("L", (PH_W, PH_H), 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle([0, 0, PH_W - 1, PH_H - 1], radius=RADIUS, fill=255)
    m.save(os.path.join(OUT, "phone_mask.png"))

# phone glow/shadow plate (baked separately, overlaid under phone)
def make_phone_glow():
    g = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    # drop shadow
    sh = Image.new("L", (W, H), 0)
    sd = ImageDraw.Draw(sh)
    sd.rounded_rectangle([PH_X - 6, PH_Y + 26, PH_X + PH_W + 6, PH_Y + PH_H + 30],
                         radius=RADIUS + 6, fill=180)
    sh = sh.filter(ImageFilter.GaussianBlur(40))
    black = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    g = Image.composite(black, g, sh)
    # accent rim glow
    rim = Image.new("L", (W, H), 0)
    rd = ImageDraw.Draw(rim)
    rd.rounded_rectangle([PH_X - 10, PH_Y - 10, PH_X + PH_W + 10, PH_Y + PH_H + 10],
                         radius=RADIUS + 10, outline=255, width=22)
    rim = rim.filter(ImageFilter.GaussianBlur(26))
    acc = Image.new("RGBA", (W, H), ACCENT + (255,))
    g = Image.composite(acc, g, rim)
    g.save(os.path.join(OUT, "phone_glow.png"))

# ---------------------------------------------------------------- captions
def draw_text_center(d, cx, y, text, fnt, fill, shadow=True, ls=0):
    # measure with optional letter spacing
    if ls:
        widths = [d.textlength(ch, font=fnt) + ls for ch in text]
        total = sum(widths) - ls
        x = cx - total / 2
        for ch, w in zip(text, widths):
            if shadow:
                d.text((x + 3, y + 4), ch, font=fnt, fill=(0, 0, 0, 150))
            d.text((x, y), ch, font=fnt, fill=fill)
            x += w
    else:
        w = d.textlength(text, font=fnt)
        x = cx - w / 2
        if shadow:
            d.text((x + 3, y + 4), text, font=fnt, fill=(0, 0, 0, 150))
        d.text((x, y), text, font=fnt, fill=fill)

def draw_pill(d, cx, cy, text, fnt, fg, bg, padx=34, pady=16):
    w = d.textlength(text, font=fnt)
    asc, desc = fnt.getmetrics()
    hh = asc + desc
    x0, y0 = cx - w / 2 - padx, cy - hh / 2 - pady
    x1, y1 = cx + w / 2 + padx, cy + hh / 2 + pady
    d.rounded_rectangle([x0, y0, x1, y1], radius=(y1 - y0) / 2, fill=bg)
    d.text((cx - w / 2, cy - hh / 2 - 2), text, font=fnt, fill=fg)

def caption(name, lines):
    """lines: list of (text, font, fill, ls). Rendered as a top band, stacked."""
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # translucent top scrim for legibility
    scrim = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(scrim)
    sd.rectangle([0, 0, W, 300], fill=(6, 9, 20, 150))
    scrim = scrim.filter(ImageFilter.GaussianBlur(30))
    img = Image.alpha_composite(img, scrim)
    d = ImageDraw.Draw(img)
    y = 70
    for (text, fnt, fill, ls) in lines:
        draw_text_center(d, W // 2, y, text, fnt, fill, shadow=True, ls=ls)
        asc, desc = fnt.getmetrics()
        y += asc + desc + 6
    img.save(os.path.join(OUT, name))

ORANGE = (255, 152, 0)

def make_captions():
    # A: hook, the exact two lines that carried the first short
    caption("capA.png", [
        ("Your phone's lowest volume", SEMI(58), TXT, 1),
        ("is STILL too loud", BOLD(76), ACCENT2, 2),
    ])
    # B: problem
    caption("capB.png", [
        ("This is as low as", SEMI(58), TXT, 1),
        ("Android will go.", BOLD(72), TXT, 1),
    ])
    # C: the turn
    caption("capC.png", [
        ("One tap.", BOLD(72), TXT, 1),
        ("A tiny dial appears.", SEMI(58), TXT, 1),
    ])
    # D: the new reveal, one slider carrying the whole range
    caption("capD.png", [
        ("One slider.", BOLD(72), TXT, 1),
        ("Your WHOLE volume range.", SEMI(58), ACCENT2, 1),
    ])
    # E: the payoff, crossing the device-minimum line
    caption("capE.png", [
        ("Past the orange line,", SEMI(58), ORANGE, 1),
        ("BELOW the minimum", BOLD(80), ACCENT2, 2),
    ])
    # F: the broken-buttons audience, one beat
    caption("capF.png", [
        ("Volume buttons broken?", BOLD(68), TXT, 1),
        ("Set it on screen instead.", SEMI(58), TXT, 1),
    ])
    # G: close
    caption("capG.png", [
        ("Tuck it in a corner.", SEMI(58), TXT, 1),
        ("Forget it's there.", BOLD(68), TXT, 1),
    ])

# ---------------------------------------------------------------- CTA end card
def make_cta():
    img = Image.open(os.path.join(OUT, "bg.png")).convert("RGBA")
    d = ImageDraw.Draw(img)
    # app icon
    try:
        ic = Image.open(ICON).convert("RGBA").resize((240, 240), Image.LANCZOS)
        # rounded
        m = Image.new("L", (240, 240), 0)
        ImageDraw.Draw(m).rounded_rectangle([0, 0, 239, 239], radius=54, fill=255)
        # glow
        glow = Image.new("L", (W, H), 0)
        ImageDraw.Draw(glow).ellipse([W//2-190, 520-190, W//2+190, 520+190], fill=120)
        glow = glow.filter(ImageFilter.GaussianBlur(90))
        acc = Image.new("RGBA", (W, H), ACCENT + (255,))
        img = Image.composite(acc, img, glow)
        img.paste(ic, (W // 2 - 120, 400), m)
        d = ImageDraw.Draw(img)
    except Exception as e:
        print("icon skip:", e)
    draw_text_center(d, W // 2, 690, "Volume Control", BOLD(84), TXT, ls=1)
    draw_text_center(d, W // 2, 786, "Quiet Dial", BOLD(84), ACCENT2, ls=1)
    draw_text_center(d, W // 2, 916,
                     "The whole volume range, on your screen.", SEMI(42), MUTED, ls=1)
    # feature pills row
    pills = ["Free", "No ads", "No tracking", "Open source"]
    pf = SEMI(34)
    gap = 22
    widths = [d.textlength(p, font=pf) + 56 for p in pills]
    total = sum(widths) + gap * (len(pills) - 1)
    x = W / 2 - total / 2
    for p, w in zip(pills, widths):
        cx = x + w / 2
        draw_pill(d, cx, 1050, p, pf, TXT, ACCENT + (120,), padx=28, pady=14)
        x += w + gap
    # store line
    draw_text_center(d, W // 2, 1300, "Get it on", SEMI(40), MUTED, ls=1)
    draw_text_center(d, W // 2, 1360, "Google Play  &  F-Droid", BOLD(58), TXT, ls=1)
    draw_text_center(d, W // 2, 1470, "search:  Quiet Dial", SEMI(44), ACCENT2, ls=2)
    img.convert("RGB").save(os.path.join(OUT, "cta.png"))

if __name__ == "__main__":
    make_bg()
    make_mask()
    make_phone_glow()
    make_captions()
    make_cta()
    print("assets written to", OUT)
