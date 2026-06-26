"""
Granular Volume — Play Store marketing assets.
Generates: feature graphic (1024x500) + 4 phone screenshots (1080x1920).
Brand: navy #16162B -> indigo #4A43C9, accent #6C63FF, white text.
"""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = os.path.dirname(os.path.abspath(__file__))
SRC_OVERLAY = os.path.join(os.environ.get("TEMP", "."), "ov_a.png")

NAVY = (0x16, 0x16, 0x2B)
INDIGO = (0x4A, 0x43, 0xC9)
ACCENT = (0x6C, 0x63, 0xFF)
WHITE = (255, 255, 255)

FONTS = r"C:\Windows\Fonts"


def font(name, size):
    return ImageFont.truetype(os.path.join(FONTS, name), size)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def diagonal_gradient(w, h, c1, c2):
    img = Image.new("RGB", (w, h), c1)
    px = img.load()
    mx = (w - 1) + (h - 1)
    for y in range(h):
        for x in range(w):
            px[x, y] = lerp(c1, c2, (x + y) / mx)
    return img


def glow_circle(size, color, blur):
    g = Image.new("RGB", size, (0, 0, 0))
    d = ImageDraw.Draw(g)
    w, h = size
    d.ellipse([w*0.2, h*0.0, w*0.95, h*1.0], fill=color)
    return g.filter(ImageFilter.GaussianBlur(blur))


def draw_bars(draw, cx, base_y, scale, n=5, alpha_grad=True):
    """Ascending rounded volume-step bars centered at cx, sitting on base_y."""
    bar_w = 26 * scale
    gap = bar_w * 0.42
    total = n * bar_w + (n - 1) * gap
    x = cx - total / 2
    corner = bar_w * 0.42
    min_h, max_h = 60 * scale, 150 * scale
    for i in range(n):
        h = min_h + (max_h - min_h) * (i / (n - 1))
        a = int(200 + 55 * (i / (n - 1))) if alpha_grad else 255
        draw.rounded_rectangle([x, base_y - h, x + bar_w, base_y],
                               radius=corner, fill=(255, 255, 255, a))
        x += bar_w + gap


def center_text(draw, cx, y, text, fnt, fill, anchor="mm"):
    draw.text((cx, y), text, font=fnt, fill=fill, anchor=anchor)


# ────────────────────────────────────────────────────────────────────────
# Crop the real overlay widget from the emulator screenshot.
# ────────────────────────────────────────────────────────────────────────
def get_widget():
    if not os.path.exists(SRC_OVERLAY):
        return None
    im = Image.open(SRC_OVERLAY).convert("RGB")
    # Widget pill region on the 2560x1600 home screen (top-left).
    crop = im.crop((120, 210, 320, 980))
    return crop


# ────────────────────────────────────────────────────────────────────────
# Feature graphic 1024x500
# ────────────────────────────────────────────────────────────────────────
def feature_graphic():
    W, H = 1024, 500
    img = diagonal_gradient(W, H, NAVY, INDIGO)
    glow = glow_circle((W, H), ACCENT, 90)
    img = Image.blend(img, Image.composite(glow, img, glow.convert("L")), 0.30)
    d = ImageDraw.Draw(img, "RGBA")

    # Left: bars motif inside a soft rounded badge.
    badge_x = 150
    d.ellipse([badge_x-110, H//2-110, badge_x+110, H//2+110],
              fill=(255, 255, 255, 18))
    draw_bars(d, badge_x, H//2 + 70, scale=0.62)

    # Right: title + tagline.
    tx = 330
    d.text((tx, 175), "Granular Volume", font=font("segoeuib.ttf", 70),
           fill=WHITE, anchor="lm")
    d.text((tx, 250), "Fine-tune the volume between the steps",
           font=font("segoeui.ttf", 34), fill=(0xCF, 0xCC, 0xFF), anchor="lm")
    d.text((tx, 312), "Free  •  No ads  •  No tracking",
           font=font("segoeuib.ttf", 28), fill=WHITE, anchor="lm")
    img.save(os.path.join(OUT, "feature_graphic_1024x500.png"))


# ────────────────────────────────────────────────────────────────────────
# Phone screenshots 1080x1920
# ────────────────────────────────────────────────────────────────────────
def screenshot(idx, headline, sub, builder):
    W, H = 1080, 1920
    img = diagonal_gradient(W, H, NAVY, INDIGO)
    glow = glow_circle((W, H), ACCENT, 160)
    img = Image.blend(img, Image.composite(glow, img, glow.convert("L")), 0.22)
    d = ImageDraw.Draw(img, "RGBA")

    # Headline block (top).
    center_text(d, W//2, 230, headline, font("segoeuib.ttf", 64), WHITE)
    # Wrap sub if needed (simple).
    center_text(d, W//2, 320, sub, font("segoeui.ttf", 36),
                (0xCF, 0xCC, 0xFF))

    builder(img, d, W, H)

    # Footer brand.
    center_text(d, W//2, H-90, "Granular Volume  ·  Free",
                font("segoeuib.ttf", 30), (255, 255, 255, 210))
    img.save(os.path.join(OUT, f"screenshot_{idx}.png"))


def draw_widget_pill(d, cx, top_y, current=4):
    """Faithful replica of the real floating overlay control."""
    pill_w = 300
    pad = 30
    handle_h = 14
    chev_h = 90
    bar_h = 78
    bar_gap = 14
    n = 7
    x0 = cx - pill_w // 2
    inner_w = pill_w - 2 * pad
    content_h = (handle_h + 30) + chev_h + n * bar_h + (n - 1) * bar_gap + chev_h + 70
    pill_h = content_h + 2 * pad
    y1 = top_y + pill_h
    # Pill body (dark glass).
    d.rounded_rectangle([x0, top_y, x0 + pill_w, y1], radius=70,
                        fill=(26, 26, 46, 240))
    cy = top_y + pad
    # Drag handle.
    d.rounded_rectangle([cx - 45, cy, cx + 45, cy + handle_h], radius=7,
                        fill=(255, 255, 255, 90))
    cy += handle_h + 30
    # Up chevron.
    d.line([(cx - 34, cy + chev_h*0.62), (cx, cy + chev_h*0.30),
            (cx + 34, cy + chev_h*0.62)], fill=(255, 255, 255, 238),
           width=12, joint="curve")
    cy += chev_h
    # 7 step bars; index n-1 = top = 0 dB. current = selected index.
    for disp in range(n):
        idx = (n - 1) - disp          # top bar = highest index (0 dB)
        if idx == current:
            a = 255
        elif idx < current:
            a = 128
        else:
            a = 26
        by = cy + disp * (bar_h + bar_gap)
        d.rounded_rectangle([x0 + pad, by, x0 + pad + inner_w, by + bar_h],
                            radius=18, fill=(255, 255, 255, a))
    cy += n * bar_h + (n - 1) * bar_gap + 12
    # Down chevron.
    d.line([(cx - 34, cy + chev_h*0.30), (cx, cy + chev_h*0.62),
            (cx + 34, cy + chev_h*0.30)], fill=(255, 255, 255, 160),
           width=12, joint="curve")
    cy += chev_h
    # dB label.
    d.text((cx, cy + 20), "-10 dB", font=font("consolab.ttf", 30),
           fill=(255, 255, 255, 150), anchor="mm")


def build_widget(img, d, W, H):
    draw_widget_pill(d, W // 2, 470, current=4)


def build_bars_closeup(img, d, W, H):
    draw_bars(d, W//2, H//2 + 320, scale=2.2)
    labels = ["0 dB", "-5", "-10", "-15", "-20", "-25", "-30 dB"]


def build_concept(img, d, W, H):
    # Two columns: "loud" vs "just right"
    y = 760
    d.text((W*0.28, y-120), "Step 1", font=font("segoeui.ttf", 40),
           fill=(255,255,255,200), anchor="mm")
    draw_bars(d, int(W*0.28), y+260, scale=1.0, n=5)
    d.text((W*0.28, y+330), "still too loud", font=font("segoeui.ttf", 34),
           fill=(0xFF,0xB3,0xB3), anchor="mm")

    d.text((W*0.72, y-120), "Sub-steps", font=font("segoeui.ttf", 40),
           fill=(255,255,255,200), anchor="mm")
    draw_bars(d, int(W*0.72), y+260, scale=1.0, n=5)
    d.text((W*0.72, y+330), "just right", font=font("segoeui.ttf", 34),
           fill=(0xB3,0xFF,0xC2), anchor="mm")


def build_free(img, d, W, H):
    cy = H//2 + 120
    d.ellipse([W//2-150, cy-150, W//2+150, cy+150], fill=(255,255,255,16))
    draw_bars(d, W//2, cy+95, scale=0.95)
    items = ["Completely free", "No advertisements",
             "No data collected", "Works on Android 9+"]
    fnt = font("segoeui.ttf", 44)
    for i, t in enumerate(items):
        ty = cy + 360 + i * 90
        # Hand-drawn checkmark (font glyph is unreliable).
        lx = W//2 - 280
        d.line([(lx, ty), (lx + 16, ty + 20), (lx + 46, ty - 26)],
               fill=(0x8C, 0xF5, 0xB0), width=9, joint="curve")
        d.text((lx + 76, ty), t, font=fnt, fill=WHITE, anchor="lm")


feature_graphic()
screenshot(1, "Volume too loud?", "Add precise sub-steps to any device", build_widget)
screenshot(2, "7 precise levels", "0 to −30 dB — one tap each", build_bars_closeup)
screenshot(3, "Between the steps", "Where Android leaves a gap, we don't", build_concept)
screenshot(4, "Yours, free", "No ads. No tracking. Ever.", build_free)
print("Marketing assets generated in", OUT)
