"""
Granular Volume - Play Store marketing assets v3 (brand aligned).
Functional led positioning: "Quieter than your phone allows."
Narrative across 6 screenshots: hook -> mechanism -> use cases -> UX ->
non invasive -> trust. Plus a feature graphic with the hero tagline.
No em dashes in any caption.
Outputs: feature_graphic_1024x500.png, screenshot_1..6.png
"""
import os, math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = os.path.dirname(os.path.abspath(__file__))
FONTS = r"C:\Windows\Fonts"

# --- Brand palette -----------------------------------------------------------
NAVY     = (0x0B, 0x0B, 0x1C)
NAVY2    = (0x1A, 0x14, 0x3A)
INDIGO   = (0x3A, 0x33, 0xB9)
VIOLET   = (0x6C, 0x63, 0xFF)
CYAN     = (0x22, 0xD3, 0xEE)
WHITE    = (255, 255, 255)
SOFT_WH  = (0xCC, 0xC8, 0xFF)
MUTED    = (0x9A, 0xA3, 0xC7)
GREEN_CK = (0x6F, 0xE8, 0x9B)

# --- Fonts -------------------------------------------------------------------
def fnt(name, size):
    try:
        return ImageFont.truetype(os.path.join(FONTS, name), size)
    except Exception:
        return ImageFont.load_default()

def bold(size):  return fnt("segoeuib.ttf", size)
def semi(size):  return fnt("seguisb.ttf",  size)
def reg(size):   return fnt("segoeui.ttf",  size)
def mono(size):  return fnt("consolab.ttf", size)

# --- Utilities ---------------------------------------------------------------
def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))

def gradient(w, h, c1, c2, angle=135):
    img = Image.new("RGB", (w, h)); px = img.load()
    rad = math.radians(angle); ca, sa = math.cos(rad), math.sin(rad)
    for y in range(h):
        ty = sa * y / h
        for x in range(w):
            t = max(0.0, min(1.0, ca * x / w + ty))
            px[x, y] = lerp(c1, c2, t)
    return img

def add_glow(base, cx, cy, radius, color, strength=0.30):
    glow = Image.new("RGB", base.size, (0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=color)
    glow = glow.filter(ImageFilter.GaussianBlur(radius // 2))
    return Image.blend(base, glow, strength)

def center(draw, cx, y, text, f, fill, anchor="mm"):
    draw.text((cx, y), text, font=f, fill=fill, anchor=anchor)

def multiline_center(draw, cx, y, lines, f, fill, line_gap):
    for i, ln in enumerate(lines):
        draw.text((cx, y + i * line_gap), ln, font=f, fill=fill, anchor="mm")

# --- Phone frame -------------------------------------------------------------
CORNER_R = 64
SCREEN_R = 52
SCREEN_INSET = 22

def draw_phone_frame(draw, px, py, pw, ph):
    body_col   = (28, 28, 44)
    border_col = (60, 56, 88)
    screen_col = (7,  7,  18)
    draw.rounded_rectangle([px, py, px+pw, py+ph], radius=CORNER_R,
                           fill=body_col, outline=border_col, width=2)
    si = SCREEN_INSET
    sx, sy, sw, sh = px+si, py+si, pw-2*si, ph-2*si
    draw.rounded_rectangle([sx, sy, sx+sw, sy+sh], radius=SCREEN_R, fill=screen_col)
    ccx, ccy = px + pw//2, py + si + 22
    draw.ellipse([ccx-9, ccy-9, ccx+9, ccy+9], fill=(18, 16, 32))
    bx = px - 5
    for by_frac, bh_frac in [(0.28, 0.07), (0.37, 0.07), (0.47, 0.05)]:
        by = py + int(ph * by_frac); bh = int(ph * bh_frac)
        draw.rounded_rectangle([bx, by, bx+8, by+bh], radius=4, fill=(40, 38, 58))
    rx2 = px + pw - 3; pby = py + int(ph * 0.33)
    draw.rounded_rectangle([rx2, pby, rx2+8, pby+int(ph*0.09)], radius=4, fill=(40, 38, 58))
    hby = py + ph - si - 12; hw = int(sw * 0.32); hcx = px + pw // 2
    draw.rounded_rectangle([hcx - hw//2, hby, hcx + hw//2, hby+6], radius=3,
                           fill=(255, 255, 255, 70))
    return sx, sy, sw, sh

# --- Overlay widget ----------------------------------------------------------
DB_STEPS = [0, -5, -10, -15, -20, -25, -30]

def widget_metrics(sw, sh):
    pill_w   = int(sw * 0.44)
    pad      = int(pill_w * 0.12)
    bar_h    = int(sh * 0.048)
    bar_gap  = int(sh * 0.010)
    chev_h   = int(sh * 0.048)
    handle_h = 7
    label_h  = int(sh * 0.038)
    inner_w  = pill_w - 2 * pad
    n = 7
    content_h = (handle_h + 14) + chev_h + n*(bar_h+bar_gap) - bar_gap + chev_h + 14 + label_h + 12
    pill_h = content_h + 2 * pad
    return dict(n=n, pill_w=pill_w, pad=pad, bar_h=bar_h, bar_gap=bar_gap,
                chev_h=chev_h, handle_h=handle_h, inner_w=inner_w, pill_h=pill_h)

def draw_widget(draw, pill_x, pill_y, m, active_idx=4, dim_future=True):
    n, pad, bar_h, bar_gap = m['n'], m['pad'], m['bar_h'], m['bar_gap']
    chev_h, handle_h, inner_w = m['chev_h'], m['handle_h'], m['inner_w']
    pill_w, pill_h = m['pill_w'], m['pill_h']
    cx = pill_x + pill_w // 2
    # glow
    ge = 22
    draw.rounded_rectangle([pill_x-ge, pill_y-ge//2, pill_x+pill_w+ge, pill_y+pill_h+ge//2],
                           radius=48, fill=(108, 99, 255, 38))
    # body
    draw.rounded_rectangle([pill_x, pill_y, pill_x+pill_w, pill_y+pill_h],
                           radius=38, fill=(20, 18, 46, 242))
    draw.rounded_rectangle([pill_x, pill_y, pill_x+pill_w, pill_y+pill_h],
                           radius=38, outline=(110, 100, 210, 90), width=1)
    cy = pill_y + pad
    draw.rounded_rectangle([cx-28, cy, cx+28, cy+handle_h], radius=4, fill=(255,255,255,70))
    cy += handle_h + 14
    draw.line([(cx-20, cy+int(chev_h*0.68)), (cx, cy+int(chev_h*0.28)),
               (cx+20, cy+int(chev_h*0.68))], fill=(255,255,255,225), width=6)
    cy += chev_h
    bars_top = cy
    for i in range(n):
        by = cy + i*(bar_h+bar_gap)
        if i == active_idx:
            draw.rounded_rectangle([pill_x+pad-2, by-2, pill_x+pad+inner_w+2, by+bar_h+2],
                                   radius=10, fill=(108,99,255,60))
            draw.rounded_rectangle([pill_x+pad, by, pill_x+pad+inner_w, by+bar_h],
                                   radius=9, fill=(255,255,255,255))
        elif i < active_idx:
            draw.rounded_rectangle([pill_x+pad, by, pill_x+pad+inner_w, by+bar_h],
                                   radius=9, fill=(255,255,255,120))
        else:
            a = 22 if dim_future else 60
            draw.rounded_rectangle([pill_x+pad, by, pill_x+pad+inner_w, by+bar_h],
                                   radius=9, fill=(255,255,255,a))
    cy += n*(bar_h+bar_gap) - bar_gap + 14
    draw.line([(cx-20, cy+int(chev_h*0.28)), (cx, cy+int(chev_h*0.68)),
               (cx+20, cy+int(chev_h*0.28))], fill=(255,255,255,145), width=6)
    cy += chev_h + 14
    db = DB_STEPS[active_idx]
    draw.text((cx, cy+8), ("0 dB" if db == 0 else f"{db} dB"),
              font=mono(int(bar_h*0.72)), fill=(205,196,255,200), anchor="mm")
    return bars_top

# --- Fake music context ------------------------------------------------------
def draw_music_context(draw, sx, sy, sw, sh):
    draw.rectangle([sx, sy, sx+sw, sy+30], fill=(10, 10, 24))
    # tiny clock + dots to feel real
    draw.text((sx+16, sy+15), "9:41", font=reg(20), fill=(150,150,180), anchor="lm")
    art_y = sy + 44; art_h = int(sh * 0.34)
    art_x = sx + int(sw * 0.12); art_w = int(sw * 0.76)
    draw.rounded_rectangle([art_x, art_y, art_x+art_w, art_y+art_h], radius=18,
                           fill=(44, 40, 78))
    mn_cx, mn_cy = art_x + art_w//2, art_y + art_h//2
    draw.ellipse([mn_cx-26, mn_cy+14, mn_cx-2, mn_cy+36], fill=(84, 76, 138))
    draw.ellipse([mn_cx+8,  mn_cy+20, mn_cx+32, mn_cy+42], fill=(84, 76, 138))
    draw.line([(mn_cx-2, mn_cy+16), (mn_cx-2, mn_cy-20),
               (mn_cx+32, mn_cy-10), (mn_cx+32, mn_cy+22)], fill=(84, 76, 138), width=6)
    t_y = art_y + art_h + 22
    draw.rounded_rectangle([art_x, t_y, art_x+int(art_w*0.62), t_y+20], radius=4,
                           fill=(84,76,138,150))
    draw.rounded_rectangle([art_x, t_y+30, art_x+int(art_w*0.40), t_y+44], radius=4,
                           fill=(64,58,108,130))
    pb_y = t_y + 64
    draw.rounded_rectangle([art_x, pb_y, art_x+art_w, pb_y+6], radius=3, fill=(50,46,85))
    draw.rounded_rectangle([art_x, pb_y, art_x+int(art_w*0.38), pb_y+6], radius=3, fill=VIOLET)
    for cx2, r in [(mn_cx-80, 14), (mn_cx, 20), (mn_cx+80, 14)]:
        cy2 = pb_y + 40
        draw.ellipse([cx2-r, cy2-r, cx2+r, cy2+r], fill=(64,58,108))
    # "Up next" list fills the rest of the screen so the overlay looks like it
    # is genuinely floating over a real, full music app.
    ln_y = pb_y + 78
    draw.rounded_rectangle([art_x, ln_y, art_x+int(art_w*0.34), ln_y+14], radius=4,
                           fill=(70,64,116,140))
    ln_y += 34
    row_h = int(sh*0.052)
    while ln_y < sy + sh - row_h - int(sh*0.03):
        # thumbnail
        draw.rounded_rectangle([art_x, ln_y, art_x+row_h-6, ln_y+row_h-6], radius=8,
                               fill=(40,36,72))
        # two text stubs
        tx0 = art_x + row_h + 6
        draw.rounded_rectangle([tx0, ln_y+4, tx0+int(art_w*0.46), ln_y+16], radius=4,
                               fill=(72,66,118,120))
        draw.rounded_rectangle([tx0, ln_y+24, tx0+int(art_w*0.30), ln_y+34], radius=4,
                               fill=(54,49,92,100))
        ln_y += row_h + int(sh*0.012)

# --- Line icons for use cases ------------------------------------------------
def icon_moon(d, cx, cy, r, col):
    d.ellipse([cx-r, cy-r, cx+r, cy+r], fill=col)
    off = int(r*0.55)
    # cut a bite using background color circle (caller draws on card bg)
    return off

def make_crescent(d, cx, cy, r, col, bg):
    d.ellipse([cx-r, cy-r, cx+r, cy+r], fill=col)
    d.ellipse([cx-r+int(r*0.5), cy-r-int(r*0.15),
               cx+r+int(r*0.5), cy+r-int(r*0.15)], fill=bg)

def icon_headphones(d, cx, cy, r, col):
    # band
    d.arc([cx-r, cy-r, cx+r, cy+r], start=180, end=360, fill=col, width=max(3,int(r*0.18)))
    ew = int(r*0.34); eh = int(r*0.5)
    d.rounded_rectangle([cx-r, cy-int(eh*0.1), cx-r+ew, cy-int(eh*0.1)+eh],
                        radius=int(ew*0.4), fill=col)
    d.rounded_rectangle([cx+r-ew, cy-int(eh*0.1), cx+r, cy-int(eh*0.1)+eh],
                        radius=int(ew*0.4), fill=col)

def icon_book(d, cx, cy, r, col):
    w = int(r*1.3); h = int(r*1.1)
    d.line([(cx, cy-h//2), (cx, cy+h//2)], fill=col, width=max(2,int(r*0.10)))
    for s in (-1, 1):
        d.line([(cx, cy-h//2), (cx+s*w//2, cy-h//2+int(h*0.12))], fill=col, width=max(2,int(r*0.10)))
        d.line([(cx+s*w//2, cy-h//2+int(h*0.12)), (cx+s*w//2, cy+h//2-int(h*0.04))],
               fill=col, width=max(2,int(r*0.10)))
        d.line([(cx, cy+h//2), (cx+s*w//2, cy+h//2-int(h*0.04))], fill=col, width=max(2,int(r*0.10)))
        d.line([(cx, cy-h//2), (cx, cy+h//2)], fill=col, width=max(2,int(r*0.10)))

def icon_waves(d, cx, cy, r, col):
    # speaker
    sw2 = int(r*0.5)
    d.polygon([(cx-r, cy-int(r*0.25)), (cx-int(r*0.4), cy-int(r*0.25)),
               (cx, cy-int(r*0.6)), (cx, cy+int(r*0.6)),
               (cx-int(r*0.4), cy+int(r*0.25)), (cx-r, cy+int(r*0.25))], fill=col)
    for i, rr in enumerate([0.5, 0.85]):
        d.arc([cx-int(r*0.1), cy-int(r*rr), cx+int(r*rr*1.4), cy+int(r*rr)],
              start=300, end=60, fill=col, width=max(2,int(r*0.12)))

# --- Screenshot builder ------------------------------------------------------
def make_screenshot(idx, headline_lines, sub, content_fn, accent=VIOLET):
    W, H = 1080, 1920
    img = gradient(W, H, NAVY, NAVY2)
    img = add_glow(img, int(W*0.68), int(H*0.36), 440, accent, 0.20)
    img = add_glow(img, int(W*0.16), int(H*0.78), 260, INDIGO, 0.14)
    d = ImageDraw.Draw(img, "RGBA")
    # caption
    if len(headline_lines) == 1:
        center(d, W//2, 158, headline_lines[0], bold(72), WHITE)
        sub_y = 248
    else:
        center(d, W//2, 140, headline_lines[0], bold(66), WHITE)
        center(d, W//2, 218, headline_lines[1], bold(66), WHITE)
        sub_y = 300
    center(d, W//2, sub_y, sub, reg(37), SOFT_WH)
    # phone
    phone_w, phone_h = 480, 952
    phone_x = (W - phone_w) // 2
    phone_y = 360
    sx, sy, sw, sh = draw_phone_frame(d, phone_x, phone_y, phone_w, phone_h)
    content_fn(d, sx, sy, sw, sh)
    # brand strip
    center(d, W//2, H - 78, "Granular Volume", bold(34), (255,255,255,210))
    center(d, W//2, H - 40, "Free  .  No ads  .  Open source", reg(28), (200,196,235,150))
    img.save(os.path.join(OUT, f"screenshot_{idx}.png"))
    print(f"  OK screenshot_{idx}.png")

# --- Content functions -------------------------------------------------------
def c_hook(d, sx, sy, sw, sh):
    draw_music_context(d, sx, sy, sw, sh)
    m = widget_metrics(sw, sh)
    px = sx + int(sw*0.54) - m['pill_w']//2
    py = sy + int(sh*0.07)
    draw_widget(d, px, py, m, active_idx=5)  # -25 dB, very quiet

def c_steps(d, sx, sy, sw, sh):
    d.rounded_rectangle([sx, sy, sx+sw, sy+sh], radius=SCREEN_R, fill=(11,9,26,255))
    m = widget_metrics(sw, sh)
    px = sx + int(sw*0.56) - m['pill_w']//2
    py = sy + int(sh*0.085)
    bars_top = draw_widget(d, px, py, m, active_idx=4, dim_future=False)
    labels = ["0 dB","-5","-10","-15","-20","-25","-30 dB"]
    legend_x = sx + int(sw*0.10)
    for i, lbl in enumerate(labels):
        ly = bars_top + i*(m['bar_h']+m['bar_gap']) + m['bar_h']//2
        d.text((legend_x, ly), lbl, font=mono(int(m['bar_h']*0.60)),
               fill=(SOFT_WH[0],SOFT_WH[1],SOFT_WH[2],200), anchor="lm")
    # bracket label "below minimum"
    d.text((sx+sw//2, sy+sh-int(sh*0.07)), "below the hardware minimum",
           font=reg(int(sh*0.030)), fill=(CYAN[0],CYAN[1],CYAN[2],220), anchor="mm")

def _wrap_to_width(d, text, font, max_w):
    words = text.split(" ")
    lines, cur = [], ""
    for w in words:
        trial = w if not cur else cur + " " + w
        if d.textlength(trial, font=font) <= max_w:
            cur = trial
        else:
            if cur:
                lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines

def c_usecases(d, sx, sy, sw, sh):
    d.rounded_rectangle([sx, sy, sx+sw, sy+sh], radius=SCREEN_R, fill=(11,9,26,255))
    cards = [
        ("moon",  "Sleep & white noise"),
        ("phones","Sensitive headphones"),
        ("book",  "Late night reading"),
        ("waves", "Library & quiet rooms"),
    ]
    pad = int(sw*0.075)
    gap = int(sw*0.055)
    cw = (sw - 2*pad - gap) // 2
    ch = int(sh*0.285)
    vgap = int(sh*0.045)
    grid_h = 2*ch + vgap
    top = sy + (sh - grid_h)//2          # vertically centred grid
    bg = (22, 20, 48)
    lbl_font = semi(int(ch*0.105))
    for i, (icon, label) in enumerate(cards):
        col = i % 2; row = i // 2
        cxp = sx + pad + col*(cw+gap)
        cyp = top + row*(ch+vgap)
        d.rounded_rectangle([cxp, cyp, cxp+cw, cyp+ch], radius=28, fill=bg,
                            outline=(64,58,116,130), width=1)
        icx = cxp + cw//2; icy = cyp + int(ch*0.34); r = int(ch*0.155)
        if icon == "moon":
            make_crescent(d, icx, icy, r, VIOLET, bg)
        elif icon == "phones":
            icon_headphones(d, icx, icy, r, SOFT_WH)
        elif icon == "book":
            icon_book(d, icx, icy, r, SOFT_WH)
        elif icon == "waves":
            icon_waves(d, icx, icy, r, CYAN)
        # label wrapped to the card width
        lines = _wrap_to_width(d, label, lbl_font, cw - int(cw*0.14))
        line_gap = int(ch*0.115)
        block_top = cyp + int(ch*0.66)
        for j, ln in enumerate(lines):
            d.text((icx, block_top + j*line_gap), ln, font=lbl_font, fill=WHITE, anchor="mm")

def c_drag(d, sx, sy, sw, sh):
    draw_music_context(d, sx, sy, sw, sh)
    m = widget_metrics(sw, sh)
    # ghost (start) position, faint
    gpx = sx + int(sw*0.50) - m['pill_w']//2
    gpy = sy + int(sh*0.07)
    d.rounded_rectangle([gpx, gpy, gpx+m['pill_w'], gpy+m['pill_h']], radius=38,
                        fill=(108,99,255,28))
    # final position bottom-right
    px = sx + sw - m['pill_w'] - int(sw*0.05)
    py = sy + sh - m['pill_h'] - int(sh*0.05)
    draw_widget(d, px, py, m, active_idx=4)
    # dashed arrow
    s_cx, s_cy = gpx + m['pill_w']//2, gpy + m['pill_h']//2
    e_cx, e_cy = px + m['pill_w']//2, py + m['pill_h']//2 - 10
    for step in range(0, 14):
        t1, t2 = step/14.0, (step+0.55)/14.0
        ax = int(s_cx+(e_cx-s_cx)*t1); ay = int(s_cy+(e_cy-s_cy)*t1)
        bx = int(s_cx+(e_cx-s_cx)*t2); by = int(s_cy+(e_cy-s_cy)*t2)
        d.line([(ax,ay),(bx,by)], fill=(108,99,255,170), width=4)
    d.polygon([(e_cx, e_cy+6),(e_cx-12, e_cy-12),(e_cx+12, e_cy-12)], fill=(108,99,255,210))

def c_noninvasive(d, sx, sy, sw, sh):
    d.rounded_rectangle([sx, sy, sx+sw, sy+sh], radius=SCREEN_R, fill=(11,9,26,255))
    rows = [
        "Keeps your volume buttons",
        "Keeps your system volume panel",
        "Runs alongside your music",
        "Nothing to override, stays stable",
    ]
    lx = sx + int(sw*0.12)
    cy = sy + int(sh*0.24)
    fs = int(sh*0.040)
    for text in rows:
        r = int(fs*0.5)
        ccx, ccy = lx+r, cy+r
        d.ellipse([ccx-r, ccy-r, ccx+r, ccy+r], fill=(CYAN[0],CYAN[1],CYAN[2],45))
        d.line([(ccx-int(r*0.5), ccy),(ccx-int(r*0.08), ccy+int(r*0.5)),
                (ccx+int(r*0.55), ccy-int(r*0.45))], fill=CYAN, width=max(3,int(r*0.34)))
        d.text((lx+r*2+18, ccy), text, font=reg(fs), fill=WHITE, anchor="lm")
        cy += int(fs*2.1)
    d.text((sx+sw//2, sy+int(sh*0.80)), "It sits on top.", font=bold(int(sh*0.048)),
           fill=SOFT_WH, anchor="mm")
    d.text((sx+sw//2, sy+int(sh*0.855)), "Nothing else changes.", font=bold(int(sh*0.048)),
           fill=SOFT_WH, anchor="mm")

def c_trust(d, sx, sy, sw, sh):
    d.rounded_rectangle([sx, sy, sx+sw, sy+sh], radius=SCREEN_R, fill=(11,9,26,255))
    items = ["Completely free","No advertisements","No data collected",
             "No internet access","Open source (GPL-3.0)"]
    lx = sx + int(sw*0.12); cy = sy + int(sh*0.18); fs = int(sh*0.042)
    for text in items:
        r = int(fs*0.5); ccx, ccy = lx+r, cy+r
        d.ellipse([ccx-r, ccy-r, ccx+r, ccy+r], fill=(GREEN_CK[0],GREEN_CK[1],GREEN_CK[2],45))
        d.line([(ccx-int(r*0.5), ccy),(ccx-int(r*0.08), ccy+int(r*0.5)),
                (ccx+int(r*0.55), ccy-int(r*0.45))], fill=GREEN_CK, width=max(3,int(r*0.34)))
        d.text((lx+r*2+18, ccy), text, font=reg(fs), fill=WHITE, anchor="lm")
        cy += int(fs*1.95)
    bcx = sx+sw//2; bcy = sy+int(sh*0.80)
    d.ellipse([bcx-78, bcy-78, bcx+78, bcy+78], fill=(VIOLET[0],VIOLET[1],VIOLET[2],55))
    d.text((bcx, bcy-8), "FREE", font=bold(int(sh*0.085)), fill=VIOLET, anchor="mm")
    d.text((bcx, bcy+44), "forever", font=reg(int(sh*0.036)), fill=SOFT_WH, anchor="mm")

# --- Feature graphic 1024x500 ------------------------------------------------
def make_feature_graphic():
    W, H = 1024, 500
    img = gradient(W, H, NAVY, (0x22, 0x1A, 0x55))
    img = add_glow(img, int(W*0.74), int(H*0.42), 300, VIOLET, 0.32)
    img = add_glow(img, int(W*0.12), int(H*0.62), 190, INDIGO, 0.20)
    d = ImageDraw.Draw(img, "RGBA")
    # mini phone
    ph_w, ph_h = 158, 312
    ph_x, ph_y = 78, (H - ph_h)//2
    d.rounded_rectangle([ph_x, ph_y, ph_x+ph_w, ph_y+ph_h], radius=26,
                        fill=(28,26,48), outline=(64,58,100), width=2)
    si2 = 8
    d.rounded_rectangle([ph_x+si2, ph_y+si2, ph_x+ph_w-si2, ph_y+ph_h-si2],
                        radius=20, fill=(10,8,24))
    d.ellipse([ph_x+ph_w//2-5, ph_y+si2+8, ph_x+ph_w//2+5, ph_y+si2+18], fill=(20,18,35))
    # widget inside mini phone (reuse metrics at mini scale)
    msw, msh = ph_w-2*si2, ph_h-2*si2
    m = widget_metrics(int(msw/0.44*0.62), msh)  # scale so pill ~62% of mini screen
    m['pill_w'] = int(msw*0.60); m['pad'] = int(m['pill_w']*0.12)
    m['inner_w'] = m['pill_w']-2*m['pad']
    px = ph_x+si2+(msw-m['pill_w'])//2; py = ph_y+si2+int(msh*0.07)
    draw_widget(d, px, py, m, active_idx=5)
    # text block
    tx = 290
    d.text((tx, 120), "Granular", font=bold(74), fill=WHITE, anchor="lm")
    d.text((tx, 196), "Volume",   font=bold(74), fill=VIOLET, anchor="lm")
    d.text((tx, 262), "Quieter than your phone allows.", font=reg(34), fill=SOFT_WH, anchor="lm")
    badge_y = 322
    for i, badge in enumerate(["Free", "No ads", "Open source"]):
        bw = 150 if badge != "Open source" else 200
        bx = tx + (i*180 if i < 2 else 360)
        d.rounded_rectangle([bx, badge_y, bx+bw, badge_y+44], radius=22,
                            fill=(VIOLET[0],VIOLET[1],VIOLET[2],45), outline=VIOLET, width=1)
        d.text((bx+bw//2, badge_y+22), badge, font=semi(23), fill=WHITE, anchor="mm")
    img.save(os.path.join(OUT, "feature_graphic_1024x500.png"))
    print("  OK feature_graphic_1024x500.png")

# --- Main --------------------------------------------------------------------
print("Generating Granular Volume marketing assets v3 (brand aligned)...")
make_feature_graphic()
make_screenshot(1, ["When the lowest setting", "is still too loud"],
                "Granular Volume picks up where Android stops", c_hook)
make_screenshot(2, ["Quieter than your", "phone allows"],
                "Seven fine steps below the hardware minimum", c_steps, accent=CYAN)
make_screenshot(3, ["Made for quiet moments"],
                "The volume you reach for at night", c_usecases)
make_screenshot(4, ["Always within reach"],
                "Drag it anywhere. Close it with one tap.", c_drag)
make_screenshot(5, ["It touches nothing else"],
                "No button override. No system takeover.", c_noninvasive, accent=CYAN)
make_screenshot(6, ["Free, private, open"],
                "No ads. No tracking. GPL-3.0.", c_trust)
print("Done. Assets saved to:", OUT)
