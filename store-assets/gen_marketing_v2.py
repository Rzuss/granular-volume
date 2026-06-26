"""
Granular Volume — Play Store marketing assets v2.
Professional phone-frame screenshots + feature graphic.
Outputs: feature_graphic_1024x500.png, screenshot_1..5.png
"""
import os, math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = os.path.dirname(os.path.abspath(__file__))
FONTS = r"C:\Windows\Fonts"

# ─── Brand palette ───────────────────────────────────────────────────────────
NAVY     = (0x0D, 0x0D, 0x1E)
INDIGO   = (0x3A, 0x33, 0xB9)
VIOLET   = (0x6C, 0x63, 0xFF)
WHITE    = (255, 255, 255)
SOFT_WH  = (0xCC, 0xC8, 0xFF)
GREEN_CK = (0x7A, 0xF5, 0xA0)
RED_BAD  = (0xFF, 0x80, 0x80)

# ─── Font helpers ─────────────────────────────────────────────────────────────
def fnt(name, size):
    for f in [name]:
        try:
            return ImageFont.truetype(os.path.join(FONTS, f), size)
        except Exception:
            pass
    return ImageFont.load_default()

def bold(size):  return fnt("segoeuib.ttf", size)
def reg(size):   return fnt("segoeui.ttf",  size)
def mono(size):  return fnt("consolab.ttf", size)

# ─── Drawing utilities ────────────────────────────────────────────────────────
def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))

def gradient(w, h, c1, c2, angle=135):
    img = Image.new("RGB", (w, h))
    px  = img.load()
    rad = math.radians(angle)
    ca, sa = math.cos(rad), math.sin(rad)
    for y in range(h):
        for x in range(w):
            t = max(0.0, min(1.0, ca * x / w + sa * y / h))
            px[x, y] = lerp(c1, c2, t)
    return img

def add_glow(base, cx, cy, radius, color, strength=0.35):
    glow = Image.new("RGB", base.size, (0, 0, 0))
    gd   = ImageDraw.Draw(glow)
    r = radius
    gd.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)
    glow = glow.filter(ImageFilter.GaussianBlur(radius // 2))
    return Image.blend(base, glow, strength)

def center(draw, cx, y, text, f, fill, anchor="mm"):
    draw.text((cx, y), text, font=f, fill=fill, anchor=anchor)

# ─── Phone frame ─────────────────────────────────────────────────────────────
PHONE_W, PHONE_H = 540, 1040   # inner mockup size (will be scaled per use)
SCREEN_INSET = 22
CORNER_R     = 64
SCREEN_R     = 52

def draw_phone_frame(draw, px, py, pw, ph):
    """Dark Android device frame. Returns (sx,sy,sw,sh) = inner screen rect."""
    body_col   = (28, 28, 44)
    border_col = (52, 48, 72)
    screen_col = (7,  7,  18)

    # Phone body
    draw.rounded_rectangle([px, py, px+pw, py+ph],
                           radius=CORNER_R, fill=body_col, outline=border_col, width=2)
    # Screen
    si = SCREEN_INSET
    sx, sy, sw, sh = px+si, py+si, pw-2*si, ph-2*si
    draw.rounded_rectangle([sx, sy, sx+sw, sy+sh], radius=SCREEN_R, fill=screen_col)

    # Punch-hole camera
    ccx, ccy = px + pw//2, py + si + 22
    draw.ellipse([ccx-10, ccy-10, ccx+10, ccy+10], fill=(18, 16, 32))

    # Volume buttons (left)
    bx = px - 5
    for by_frac, bh_frac in [(0.28, 0.07), (0.37, 0.07), (0.47, 0.05)]:
        by = py + int(ph * by_frac)
        bh = int(ph * bh_frac)
        draw.rounded_rectangle([bx, by, bx+8, by+bh], radius=4, fill=(40, 38, 58))

    # Power button (right)
    rx2 = px + pw - 3
    pby = py + int(ph * 0.33)
    draw.rounded_rectangle([rx2, pby, rx2+8, pby+int(ph*0.09)], radius=4, fill=(40, 38, 58))

    # Home bar
    hby = py + ph - si - 10
    hw  = int(sw * 0.32)
    hcx = px + pw // 2
    draw.rounded_rectangle([hcx - hw//2, hby, hcx + hw//2, hby+6], radius=3,
                           fill=(255, 255, 255, 60))

    return sx, sy, sw, sh

# ─── Overlay widget ───────────────────────────────────────────────────────────
DB_STEPS  = [0, -5, -10, -15, -20, -25, -30]  # index 0 = top = loudest

def draw_overlay_widget(draw, sx, sy, sw, sh, active_idx=4):
    """Draw the actual GranularVolume pill inside screen rect (sx,sy,sw,sh).
    active_idx: which of the 7 steps is lit (0=0dB at top … 6=-30dB at bottom).
    """
    n         = 7
    pill_w    = int(sw * 0.44)
    pad       = int(pill_w * 0.12)
    bar_h     = int(sh * 0.048)
    bar_gap   = int(sh * 0.010)
    chev_h    = int(sh * 0.048)
    handle_h  = 7
    label_h   = int(sh * 0.038)
    inner_w   = pill_w - 2 * pad

    content_h = (handle_h + 14) + chev_h + n*(bar_h+bar_gap) - bar_gap + chev_h + 14 + label_h + 12
    pill_h    = content_h + 2 * pad

    # Position pill: right-of-centre, near top
    pill_x = sx + int(sw * 0.54) - pill_w // 2
    pill_y = sy + int(sh * 0.07)
    cx     = pill_x + pill_w // 2

    # Outer glow
    glow_expand = 22
    draw.rounded_rectangle(
        [pill_x - glow_expand, pill_y - glow_expand//2,
         pill_x + pill_w + glow_expand, pill_y + pill_h + glow_expand//2],
        radius=48, fill=(108, 99, 255, 35))

    # Pill body — dark glass
    draw.rounded_rectangle([pill_x, pill_y, pill_x+pill_w, pill_y+pill_h],
                           radius=38, fill=(20, 18, 46, 240))
    # Pill border highlight
    draw.rounded_rectangle([pill_x, pill_y, pill_x+pill_w, pill_y+pill_h],
                           radius=38, outline=(100, 90, 200, 80), width=1)

    cy = pill_y + pad

    # Drag handle
    draw.rounded_rectangle([cx-28, cy, cx+28, cy+handle_h],
                           radius=4, fill=(255, 255, 255, 65))
    cy += handle_h + 14

    # Up chevron ∧
    draw.line([(cx-20, cy+int(chev_h*0.68)), (cx, cy+int(chev_h*0.28)),
               (cx+20, cy+int(chev_h*0.68))],
              fill=(255, 255, 255, 220), width=6)
    cy += chev_h

    # 7 step bars (top = 0 dB = index 0)
    for i in range(n):
        by = cy + i * (bar_h + bar_gap)
        if i == active_idx:
            # Active: bright + glow
            draw.rounded_rectangle(
                [pill_x+pad-2, by-2, pill_x+pad+inner_w+2, by+bar_h+2],
                radius=10, fill=(108, 99, 255, 55))
            draw.rounded_rectangle([pill_x+pad, by, pill_x+pad+inner_w, by+bar_h],
                                   radius=9, fill=(255, 255, 255, 255))
        elif i < active_idx:
            draw.rounded_rectangle([pill_x+pad, by, pill_x+pad+inner_w, by+bar_h],
                                   radius=9, fill=(255, 255, 255, 115))
        else:
            draw.rounded_rectangle([pill_x+pad, by, pill_x+pad+inner_w, by+bar_h],
                                   radius=9, fill=(255, 255, 255, 22))

    cy += n * (bar_h + bar_gap) - bar_gap + 14

    # Down chevron ∨
    draw.line([(cx-20, cy+int(chev_h*0.28)), (cx, cy+int(chev_h*0.68)),
               (cx+20, cy+int(chev_h*0.28))],
              fill=(255, 255, 255, 140), width=6)
    cy += chev_h + 14

    # dB label
    db_val = DB_STEPS[active_idx]
    label  = "0 dB" if db_val == 0 else f"{db_val} dB"
    draw.text((cx, cy + 8), label,
              font=mono(int(bar_h * 0.72)),
              fill=(200, 190, 255, 180), anchor="mm")

# ─── Fake "music app" context drawn inside the screen ─────────────────────────
def draw_music_context(draw, sx, sy, sw, sh):
    """Draw a minimal music-player background so the screen looks occupied."""
    # Status bar hint
    draw.rectangle([sx, sy, sx+sw, sy+28], fill=(10, 10, 24))

    # Album art area (top half)
    art_y = sy + 38
    art_h = int(sh * 0.34)
    art_x = sx + int(sw * 0.12)
    art_w = int(sw * 0.76)
    draw.rounded_rectangle([art_x, art_y, art_x+art_w, art_y+art_h],
                           radius=18, fill=(40, 36, 72))
    # Music note motif
    mn_cx, mn_cy = art_x + art_w//2, art_y + art_h//2
    draw.ellipse([mn_cx-26, mn_cy+14, mn_cx-2, mn_cy+36], fill=(80, 72, 130))
    draw.ellipse([mn_cx+8,  mn_cy+20, mn_cx+32, mn_cy+42], fill=(80, 72, 130))
    draw.line([(mn_cx-2, mn_cy+16), (mn_cx-2, mn_cy-20),
               (mn_cx+32, mn_cy-10), (mn_cx+32, mn_cy+22)],
              fill=(80, 72, 130), width=6)

    # Song title & artist stubs
    t_y = art_y + art_h + 22
    draw.rounded_rectangle([art_x, t_y, art_x + int(art_w*0.65), t_y+20],
                           radius=4, fill=(80, 72, 130, 120))
    draw.rounded_rectangle([art_x, t_y+30, art_x + int(art_w*0.42), t_y+44],
                           radius=4, fill=(60, 55, 100, 100))

    # Progress bar
    pb_y = t_y + 64
    draw.rounded_rectangle([art_x, pb_y, art_x+art_w, pb_y+6],
                           radius=3, fill=(50, 46, 85))
    draw.rounded_rectangle([art_x, pb_y, art_x+int(art_w*0.38), pb_y+6],
                           radius=3, fill=VIOLET)

    # Playback controls (prev / play / next dots)
    for i, (cx2, r) in enumerate([(mn_cx-80, 14), (mn_cx, 20), (mn_cx+80, 14)]):
        cy2 = pb_y + 40
        draw.ellipse([cx2-r, cy2-r, cx2+r, cy2+r], fill=(60, 55, 100))


# ─── Screenshot builder ────────────────────────────────────────────────────────
def make_screenshot(idx, headline, sub, content_fn, filename=None):
    W, H = 1080, 1920
    img = gradient(W, H, NAVY, (0x1E, 0x18, 0x40))
    img = add_glow(img, int(W*0.65), int(H*0.40), 420, VIOLET, 0.22)
    d   = ImageDraw.Draw(img, "RGBA")

    # ── top caption ──────────────────────────────────
    center(d, W//2, 155, headline, bold(70), WHITE)
    center(d, W//2, 240, sub,      reg(38),  SOFT_WH)

    # ── phone mockup ────────────────────────────────
    phone_w = 480
    phone_h = 960
    phone_x = (W - phone_w) // 2
    phone_y = 300
    sx, sy, sw, sh = draw_phone_frame(d, phone_x, phone_y, phone_w, phone_h)

    # Content inside screen
    content_fn(d, sx, sy, sw, sh)

    # ── bottom brand strip ───────────────────────────
    center(d, W//2, H - 80, "Granular Volume  ·  Free  ·  No ads",
           bold(32), (255, 255, 255, 180))

    fname = filename or f"screenshot_{idx}.png"
    img.save(os.path.join(OUT, fname))
    print(f"  OK {fname}")


# ─── Screenshot content functions ──────────────────────────────────────────────

def content_widget_demo(d, sx, sy, sw, sh):
    """Screenshot 1: widget in action over music player."""
    draw_music_context(d, sx, sy, sw, sh)
    draw_overlay_widget(d, sx, sy, sw, sh, active_idx=4)  # -10 dB highlighted


def content_all_steps(d, sx, sy, sw, sh):
    """Screenshot 2: show all 7 steps with dB labels."""
    n      = 7
    labels = ["0 dB", "-5 dB", "-10 dB", "-15 dB", "-20 dB", "-25 dB", "-30 dB"]
    # dark music context behind
    draw_music_context(d, sx, sy, sw, sh)
    draw_overlay_widget(d, sx, sy, sw, sh, active_idx=2)  # -10 highlighted

    # dB legend on left side of screen
    bar_h  = int(sh * 0.048)
    bar_gap = int(sh * 0.010)
    pill_w  = int(sw * 0.44)
    pad     = int(pill_w * 0.12)
    chev_h  = int(sh * 0.048)
    handle_h = 7
    pill_x  = sx + int(sw * 0.54) - pill_w // 2
    pill_y  = sy + int(sh * 0.07)
    cy_start = pill_y + int(pill_w * 0.12) + handle_h + 14 + chev_h

    legend_x = sx + 8
    for i, lbl in enumerate(labels):
        ly = cy_start + i * (bar_h + bar_gap) + bar_h // 2
        d.text((legend_x, ly), lbl,
               font=mono(int(bar_h * 0.62)),
               fill=(SOFT_WH[0], SOFT_WH[1], SOFT_WH[2], 190),
               anchor="lm")


def content_drag(d, sx, sy, sw, sh):
    """Screenshot 3: widget positioned at bottom-left to show drag freedom."""
    draw_music_context(d, sx, sy, sw, sh)

    # Draw widget at bottom-left position
    n         = 7
    pill_w    = int(sw * 0.44)
    pad       = int(pill_w * 0.12)
    bar_h     = int(sh * 0.048)
    bar_gap   = int(sh * 0.010)
    chev_h    = int(sh * 0.048)
    handle_h  = 7
    label_h   = int(sh * 0.038)
    inner_w   = pill_w - 2 * pad
    content_h = (handle_h + 14) + chev_h + n*(bar_h+bar_gap) - bar_gap + chev_h + 14 + label_h + 12
    pill_h    = content_h + 2 * pad

    # pill at bottom-right corner
    pill_x = sx + sw - pill_w - int(sw * 0.05)
    pill_y = sy + sh - pill_h - int(sh * 0.06)
    cx2    = pill_x + pill_w // 2

    # glow
    d.rounded_rectangle(
        [pill_x - 20, pill_y - 10, pill_x + pill_w + 20, pill_y + pill_h + 10],
        radius=48, fill=(108, 99, 255, 35))
    # body
    d.rounded_rectangle([pill_x, pill_y, pill_x+pill_w, pill_y+pill_h],
                        radius=38, fill=(20, 18, 46, 230))
    d.rounded_rectangle([pill_x, pill_y, pill_x+pill_w, pill_y+pill_h],
                        radius=38, outline=(100, 90, 200, 80), width=1)

    cy = pill_y + pad
    d.rounded_rectangle([cx2-28, cy, cx2+28, cy+handle_h], radius=4,
                        fill=(255, 255, 255, 65))
    cy += handle_h + 14
    d.line([(cx2-20, cy+int(chev_h*0.68)), (cx2, cy+int(chev_h*0.28)),
            (cx2+20, cy+int(chev_h*0.68))], fill=(255, 255, 255, 220), width=6)
    cy += chev_h
    for i in range(n):
        by = cy + i * (bar_h + bar_gap)
        fill_a = 255 if i == 4 else (115 if i < 4 else 22)
        d.rounded_rectangle([pill_x+pad, by, pill_x+pad+inner_w, by+bar_h],
                            radius=9, fill=(255, 255, 255, fill_a))
    cy += n * (bar_h + bar_gap) - bar_gap + 14
    d.line([(cx2-20, cy+int(chev_h*0.28)), (cx2, cy+int(chev_h*0.68)),
            (cx2+20, cy+int(chev_h*0.28))], fill=(255, 255, 255, 140), width=6)
    cy += chev_h + 14
    d.text((cx2, cy + 8), "-10 dB", font=mono(int(bar_h * 0.72)),
           fill=(200, 190, 255, 180), anchor="mm")

    # dashed drag arrow
    start_cx = sx + int(sw * 0.54)
    start_cy = sy + int(sh * 0.07) + pill_h // 2
    end_cx   = cx2
    end_cy   = pill_y + pill_h // 2
    for step in range(0, 12):
        t1, t2 = step / 12.0, (step + 0.55) / 12.0
        ax = int(start_cx + (end_cx - start_cx) * t1)
        ay = int(start_cy + (end_cy - start_cy) * t1)
        bx2b = int(start_cx + (end_cx - start_cx) * t2)
        by2b = int(start_cy + (end_cy - start_cy) * t2)
        d.line([(ax, ay), (bx2b, by2b)], fill=(108, 99, 255, 160), width=4)
    # Arrowhead
    d.polygon([(end_cx, end_cy-14), (end_cx-10, end_cy+10), (end_cx+10, end_cy+10)],
              fill=(108, 99, 255, 200))


def content_free(d, sx, sy, sw, sh):
    """Screenshot 4: free/privacy pitch."""
    # Soft background inside screen
    d.rounded_rectangle([sx, sy, sx+sw, sy+sh], radius=SCREEN_R,
                        fill=(12, 10, 28, 255))

    items = [
        (GREEN_CK, "Completely free"),
        (GREEN_CK, "No advertisements"),
        (GREEN_CK, "No data collected"),
        (GREEN_CK, "No internet access"),
        (GREEN_CK, "Works on Android 9+"),
    ]
    lx  = sx + int(sw * 0.10)
    cy2 = sy + int(sh * 0.20)
    fsize = int(sh * 0.046)
    for col, text in items:
        # Check circle
        r = int(fsize * 0.44)
        ccx2, ccy2 = lx + r, cy2 + r
        d.ellipse([ccx2-r, ccy2-r, ccx2+r, ccy2+r], fill=(col[0], col[1], col[2], 40))
        # Checkmark
        d.line([(ccx2-int(r*0.55), ccy2), (ccx2-int(r*0.10), ccy2+int(r*0.52)),
                (ccx2+int(r*0.55), ccy2-int(r*0.48))],
               fill=col, width=int(r*0.38))
        # Text
        d.text((lx + r*2 + 16, ccy2), text, font=reg(fsize),
               fill=WHITE, anchor="lm")
        cy2 += int(fsize * 1.9)

    # Big "FREE" badge
    bd_cx = sx + sw // 2
    bd_cy = sy + int(sh * 0.80)
    d.ellipse([bd_cx - 72, bd_cy - 72, bd_cx + 72, bd_cy + 72],
              fill=(VIOLET[0], VIOLET[1], VIOLET[2], 50))
    d.text((bd_cx, bd_cy - 10), "FREE", font=bold(int(sh * 0.09)),
           fill=VIOLET, anchor="mm")
    d.text((bd_cx, bd_cy + 42), "forever", font=reg(int(sh * 0.038)),
           fill=SOFT_WH, anchor="mm")


def content_problem(d, sx, sy, sw, sh):
    """Screenshot 5: before/after — Android steps vs sub-steps."""
    d.rounded_rectangle([sx, sy, sx+sw, sy+sh], radius=SCREEN_R,
                        fill=(12, 10, 28, 255))

    mid_x = sx + sw // 2
    col_y = sy + int(sh * 0.14)

    # Left column: Android built-in
    lx = sx + int(sw * 0.10)
    lw = int(sw * 0.34)
    d.text((lx + lw//2, col_y), "Android", font=bold(int(sh*0.036)),
           fill=(200, 200, 200), anchor="mm")
    d.text((lx + lw//2, col_y + int(sh*0.054)), "only 15 steps",
           font=reg(int(sh*0.030)), fill=(160, 160, 160), anchor="mm")
    # Big jump bar
    bh_big = int(sh * 0.22)
    bx = lx + int(lw * 0.20)
    bw = int(lw * 0.60)
    by1 = sy + int(sh * 0.62) - bh_big
    d.rounded_rectangle([bx, by1, bx+bw, by1+bh_big], radius=10,
                        fill=(200, 80, 80, 200))
    d.text((bx + bw//2, sy + int(sh * 0.68)), "step 1", font=reg(int(sh*0.028)),
           fill=(255, 140, 140), anchor="mm")
    d.text((bx + bw//2, sy + int(sh * 0.72)), "too loud", font=reg(int(sh*0.028)),
           fill=(255, 140, 140), anchor="mm")

    # Divider
    div_x = mid_x - 1
    d.line([(div_x, sy + int(sh*0.08)), (div_x, sy + int(sh*0.88))],
           fill=(80, 70, 120), width=1)

    # Right column: Granular sub-steps
    rx = mid_x + int(sw * 0.04)
    rw = int(sw * 0.38)
    d.text((rx + rw//2, col_y), "Granular", font=bold(int(sh*0.036)),
           fill=SOFT_WH, anchor="mm")
    d.text((rx + rw//2, col_y + int(sh*0.054)), "7 sub-steps",
           font=reg(int(sh*0.030)), fill=SOFT_WH, anchor="mm")

    # Multiple small bars ascending
    n2 = 7
    max_bh = int(sh * 0.22)
    min_bh = int(sh * 0.04)
    bw2 = int(rw / (n2 + (n2-1)*0.3))
    gap2 = int(bw2 * 0.30)
    bottom_y = sy + int(sh * 0.82)
    bx2 = rx + (rw - (n2 * bw2 + (n2-1)*gap2)) // 2
    for i in range(n2):
        bh2 = int(min_bh + (max_bh - min_bh) * i / (n2-1))
        is_active = (i == 4)
        fill = (108, 99, 255, 220) if is_active else (100, 90, 190, 160)
        d.rounded_rectangle([bx2, bottom_y - bh2, bx2 + bw2, bottom_y],
                            radius=5, fill=fill)
        bx2 += bw2 + gap2

    d.text((rx + rw//2, sy + int(sh * 0.86)), "just right", font=reg(int(sh*0.028)),
           fill=GREEN_CK, anchor="mm")


# ─── Feature graphic 1024×500 ─────────────────────────────────────────────────
def make_feature_graphic():
    W, H = 1024, 500
    img = gradient(W, H, NAVY, (0x22, 0x1A, 0x55))
    img = add_glow(img, int(W*0.72), int(H*0.45), 280, VIOLET, 0.30)
    img = add_glow(img, int(W*0.15), int(H*0.60), 180, INDIGO, 0.20)
    d   = ImageDraw.Draw(img, "RGBA")

    # ── Mini phone mockup (left side) ──────────────────────────────
    ph_w, ph_h = 160, 320
    ph_x, ph_y = 72, (H - ph_h) // 2
    # Phone body
    d.rounded_rectangle([ph_x, ph_y, ph_x+ph_w, ph_y+ph_h],
                        radius=26, fill=(28, 26, 48), outline=(60, 55, 95), width=2)
    # Screen
    si2 = 8
    d.rounded_rectangle([ph_x+si2, ph_y+si2, ph_x+ph_w-si2, ph_y+ph_h-si2],
                        radius=20, fill=(10, 8, 24))
    # Camera
    d.ellipse([ph_x+ph_w//2-5, ph_y+si2+8, ph_x+ph_w//2+5, ph_y+si2+18],
              fill=(20, 18, 35))
    # Widget pill inside mini phone
    n2 = 7
    mp_sw = ph_w - 2*si2
    mp_sh = ph_h - 2*si2
    mp_sx = ph_x + si2
    mp_sy = ph_y + si2
    mp_pill_w = int(mp_sw * 0.62)
    mp_pad    = int(mp_pill_w * 0.12)
    mp_bar_h  = int(mp_sh * 0.050)
    mp_bar_gap = 3
    mp_chev_h  = int(mp_sh * 0.050)
    mp_inner_w = mp_pill_w - 2*mp_pad
    mp_content_h = (6 + 10) + mp_chev_h + n2*(mp_bar_h+mp_bar_gap) - mp_bar_gap + mp_chev_h + 10 + 14
    mp_pill_h = mp_content_h + 2*mp_pad
    mp_pill_x = mp_sx + (mp_sw - mp_pill_w) // 2
    mp_pill_y = mp_sy + int(mp_sh * 0.06)
    mp_cx     = mp_pill_x + mp_pill_w // 2

    d.rounded_rectangle([mp_pill_x, mp_pill_y, mp_pill_x+mp_pill_w, mp_pill_y+mp_pill_h],
                        radius=16, fill=(20, 18, 46, 235))
    mcy = mp_pill_y + mp_pad
    d.rounded_rectangle([mp_cx-14, mcy, mp_cx+14, mcy+6], radius=3, fill=(255,255,255,60))
    mcy += 6 + 10
    d.line([(mp_cx-10, mcy+int(mp_chev_h*0.65)), (mp_cx, mcy+int(mp_chev_h*0.28)),
            (mp_cx+10, mcy+int(mp_chev_h*0.65))], fill=(255,255,255,200), width=3)
    mcy += mp_chev_h
    for i in range(n2):
        mby = mcy + i*(mp_bar_h + mp_bar_gap)
        fa  = 255 if i==4 else (110 if i<4 else 20)
        d.rounded_rectangle([mp_pill_x+mp_pad, mby, mp_pill_x+mp_pad+mp_inner_w, mby+mp_bar_h],
                            radius=4, fill=(255,255,255,fa))
    mcy += n2*(mp_bar_h+mp_bar_gap) - mp_bar_gap + 10
    d.line([(mp_cx-10, mcy+int(mp_chev_h*0.28)), (mp_cx, mcy+int(mp_chev_h*0.65)),
            (mp_cx+10, mcy+int(mp_chev_h*0.28))], fill=(255,255,255,130), width=3)

    # ── Right text block ────────────────────────────────────────────
    tx = 280
    # App name
    d.text((tx, 128), "Granular", font=bold(72), fill=WHITE, anchor="lm")
    d.text((tx, 200), "Volume",   font=bold(72), fill=VIOLET, anchor="lm")
    # Tagline
    d.text((tx, 258), "Fine-tune the volume between the steps",
           font=reg(32), fill=SOFT_WH, anchor="lm")
    # Badges
    badge_y = 315
    for i, badge in enumerate(["Free", "No ads", "No tracking"]):
        bx2 = tx + i * 190
        d.rounded_rectangle([bx2, badge_y, bx2+165, badge_y+42], radius=21,
                            fill=(VIOLET[0], VIOLET[1], VIOLET[2], 40),
                            outline=VIOLET, width=1)
        d.text((bx2 + 82, badge_y + 21), badge, font=bold(22),
               fill=WHITE, anchor="mm")

    # Vertical bar steps motif (right edge)
    n3 = 5
    bar_scale = 0.62
    bw3 = int(26 * bar_scale)
    bg3 = int(bw3 * 0.45)
    bx3 = W - 90 - (n3 * bw3 + (n3-1)*bg3)
    min_bh2, max_bh2 = int(60*bar_scale), int(150*bar_scale)
    base_y = H // 2 + int(90 * bar_scale)
    for i in range(n3):
        bh3 = min_bh2 + int((max_bh2 - min_bh2) * i / (n3-1))
        a3  = 140 + int(115 * i/(n3-1))
        d.rounded_rectangle([bx3, base_y-bh3, bx3+bw3, base_y],
                            radius=int(bw3*0.42), fill=(255, 255, 255, a3))
        bx3 += bw3 + bg3

    fname = "feature_graphic_1024x500.png"
    img.save(os.path.join(OUT, fname))
    print(f"  OK {fname}")


# ─── Main ─────────────────────────────────────────────────────────────────────
print("Generating Granular Volume marketing assets v2...")

make_feature_graphic()

make_screenshot(1,
    "Volume too loud?",
    "Fine-tune below Android's lowest step",
    content_widget_demo)

make_screenshot(2,
    "7 precise levels",
    "From 0 dB down to −30 dB — one tap",
    content_all_steps)

make_screenshot(3,
    "Drag anywhere",
    "Place it anywhere, it stays out of the way",
    content_drag)

make_screenshot(4,
    "Yours — free forever",
    "No ads. No tracking. No data collected.",
    content_free)

make_screenshot(5,
    "Where Android stops,\nwe keep going",
    "Sub-steps between every hardware level",
    content_problem)

print("\nDone! Assets saved to:", OUT)
