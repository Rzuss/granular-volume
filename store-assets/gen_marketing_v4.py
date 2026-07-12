"""
Granular Volume - Play Store marketing assets v4.
Philosophy: "Quiet Instrument" (see DESIGN-PHILOSOPHY.md).

Fixes over v3:
  - The floating pill is drawn at its TRUE scale (~19% of screen width, matching
    the real 72dp overlay) instead of an oversized 44%.
  - All explanatory copy lives ABOVE the device. Nothing inside the screen can
    overflow the frame; in-screen text is short, measured, and clipped to bounds.
  - Everything is rendered at 2x and downscaled with LANCZOS for crisp edges.
  - A more realistic device: thin even bezel, punch-hole, status bar, soft shadow.

No em dashes in any caption.
Outputs: feature_graphic_1024x500.png, screenshot_1..6.png
"""
import os, math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = os.path.dirname(os.path.abspath(__file__))
FONTS = r"C:\Windows\Fonts"
SS = 2  # supersample factor

def s(x):  # scale a logical pixel value to the supersampled canvas
    return int(round(x * SS))

# --- Brand palette (taken from the running app: res/values/colors.xml) -------
BG_TOP   = (0x13, 0x1C, 0x30)
BG_BOT   = (0x0B, 0x11, 0x1E)
NAVY     = (0x0E, 0x15, 0x25)
SURFACE  = (0x16, 0x20, 0x3A)
STROKE   = (0x24, 0x30, 0x49)
VIOLET   = (0x6C, 0x63, 0xFF)
VIOLET_D = (0x50, 0x46, 0xE5)
INDIGO   = (0x3A, 0x33, 0xB9)
CYAN     = (0x22, 0xD3, 0xEE)
GREEN    = (0x34, 0xD3, 0x99)
WHITE    = (0xF4, 0xF6, 0xFB)
SOFT     = (0xC7, 0xCB, 0xEC)
MUTED    = (0x9A, 0xA6, 0xBF)
FAINT    = (0x6B, 0x77, 0x93)

# --- Fonts -------------------------------------------------------------------
def _f(name, size):
    try:
        return ImageFont.truetype(os.path.join(FONTS, name), size)
    except Exception:
        return ImageFont.load_default()

def bold(px): return _f("segoeuib.ttf", s(px))
def semi(px): return _f("seguisb.ttf",  s(px))
def reg(px):  return _f("segoeui.ttf",  s(px))
def mono(px): return _f("consola.ttf",  s(px))

# --- Utilities ---------------------------------------------------------------
def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))

def vgradient(w, h, c1, c2):
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        row = lerp(c1, c2, y / max(1, h - 1))
        for x in range(w):
            px[x, y] = row
    return img

def glow(base, cx, cy, radius, color, strength):
    g = Image.new("RGB", base.size, (0, 0, 0))
    gd = ImageDraw.Draw(g)
    gd.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=color)
    g = g.filter(ImageFilter.GaussianBlur(radius // 2))
    return Image.blend(base, g, strength)

def fit_font(d, text, font_fn, start_px, max_w, min_px=20):
    """Return a font sized so `text` fits within max_w (supersampled px)."""
    px = start_px
    while px > min_px:
        f = font_fn(px)
        if d.textlength(text, font=f) <= max_w:
            return f
        px -= 1
    return font_fn(min_px)

def wrap(d, text, font, max_w):
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

# --- Device frame ------------------------------------------------------------
def draw_device(d, x, y, w, h):
    """Realistic phone. Returns the inner screen rect (sx, sy, sw, sh).
    All chrome (corner radius, bezel, camera) scales with the device width so it
    looks correct at any size, from the hero phone to the tiny feature graphic."""
    rad = max(s(18), int(w * 0.125))
    bz = max(s(6), int(w * 0.028))
    bw = max(s(2), int(w * 0.006))   # side-button thickness
    # soft drop shadow
    sh_pad = s(40)
    shadow = Image.new("RGBA", (w + 2 * sh_pad, h + 2 * sh_pad), (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(shadow)
    sdraw.rounded_rectangle([sh_pad, sh_pad + s(14), sh_pad + w, sh_pad + h + s(14)],
                            radius=rad, fill=(0, 0, 0, 150))
    shadow = shadow.filter(ImageFilter.GaussianBlur(s(26)))
    d._image.paste(shadow, (x - sh_pad, y - sh_pad), shadow)

    # body + glass-edge highlight
    d.rounded_rectangle([x, y, x + w, y + h], radius=rad, fill=(11, 13, 22))
    d.rounded_rectangle([x, y, x + w, y + h], radius=rad, outline=(54, 60, 84), width=max(s(1), bw))
    # screen
    sx, sy, sw_, sh_ = x + bz, y + bz, w - 2 * bz, h - 2 * bz
    d.rounded_rectangle([sx, sy, sx + sw_, sy + sh_], radius=int(rad * 0.8), fill=NAVY)
    # punch-hole camera
    cr = max(s(3), int(w * 0.018))
    ccx, ccy = x + w // 2, sy + max(s(12), int(w * 0.052))
    d.ellipse([ccx - cr, ccy - cr, ccx + cr, ccy + cr], fill=(6, 8, 14))
    d.ellipse([ccx - cr // 2, ccy - cr // 2, ccx + cr // 2, ccy + cr // 2], fill=(20, 26, 44))
    # side buttons
    d.rounded_rectangle([x - bw, y + int(h * 0.30), x + bw, y + int(h * 0.30) + int(h * 0.07)],
                        radius=bw, fill=(34, 40, 60))
    d.rounded_rectangle([x - bw, y + int(h * 0.40), x + bw, y + int(h * 0.40) + int(h * 0.11)],
                        radius=bw, fill=(34, 40, 60))
    d.rounded_rectangle([x + w - bw, y + int(h * 0.34), x + w + bw, y + int(h * 0.34) + int(h * 0.09)],
                        radius=bw, fill=(34, 40, 60))
    return sx, sy, sw_, sh_

def status_bar(d, sx, sy, sw, dark_text=False):
    col = (210, 216, 232) if not dark_text else (150, 158, 178)
    d.text((sx + s(26), sy + s(30)), "9:41", font=semi(20), fill=col, anchor="lm")
    # right cluster: signal, wifi, battery
    rx = sx + sw - s(26)
    # battery
    bw, bh = s(34), s(16)
    bx = rx - bw
    by = sy + s(30) - bh // 2
    d.rounded_rectangle([bx, by, bx + bw, by + bh], radius=s(4), outline=col, width=s(2))
    d.rounded_rectangle([bx + s(3), by + s(3), bx + int(bw * 0.7), by + bh - s(3)],
                        radius=s(2), fill=col)
    d.rounded_rectangle([bx + bw + s(1), by + s(4), bx + bw + s(4), by + bh - s(4)],
                        radius=s(2), fill=col)
    # wifi (simple arcs)
    wx = bx - s(40)
    for i, r in enumerate((s(14), s(9), s(4))):
        d.arc([wx - r, sy + s(30) - r, wx + r, sy + s(30) + r], start=215, end=325,
              fill=col, width=s(2))
    d.ellipse([wx - s(2), sy + s(30) + s(6), wx + s(2), sy + s(30) + s(10)], fill=col)
    # signal bars
    gx = wx - s(46)
    for i in range(4):
        bh2 = s(5 + i * 4)
        d.rounded_rectangle([gx + i * s(9), sy + s(30) + s(8) - bh2,
                             gx + i * s(9) + s(5), sy + s(30) + s(8)], radius=s(1), fill=col)

# --- The floating pill (TRUE scale: ~19% of screen width) --------------------
DB_STEPS = [0, -5, -10, -15, -20, -25, -30]  # top -> bottom

def pill_metrics(sw, pill_w=None):
    if pill_w is None:
        pill_w = int(sw * 0.205)         # real overlay is 72dp on a ~360dp screen
    pad     = int(pill_w * 0.12)
    inner_w = pill_w - 2 * pad
    bar_h   = int(inner_w * 0.42)        # real bar is 20dp tall, 48dp wide
    bar_gap = max(s(2), int(inner_w * 0.07))
    chev_h  = int(pill_w * 0.40)
    close_d = int(pill_w * 0.34)
    handle_w = int(pill_w * 0.34)
    handle_h = s(4)
    label_h = int(pill_w * 0.28)
    n = 7
    body_h = (close_d + s(8) + handle_h + s(10) + chev_h + s(4)
              + n * bar_h + (n - 1) * bar_gap + s(4) + chev_h + s(6) + label_h)
    pill_h = body_h + 2 * pad
    return dict(n=n, pill_w=pill_w, pill_h=pill_h, pad=pad, inner_w=inner_w,
                bar_h=bar_h, bar_gap=bar_gap, chev_h=chev_h, close_d=close_d,
                handle_w=handle_w, handle_h=handle_h, label_h=label_h)

def draw_pill(d, px, py, m, active=5, dim_future=True, show_db=True):
    pw, ph, pad = m['pill_w'], m['pill_h'], m['pad']
    inner_w, bar_h, bar_gap = m['inner_w'], m['bar_h'], m['bar_gap']
    chev_h, n = m['chev_h'], m['n']
    cx = px + pw // 2
    rad = int(pw * 0.40)

    # ambient glow behind the pill
    ge = s(26)
    d.rounded_rectangle([px - ge, py - ge, px + pw + ge, py + ph + ge],
                        radius=rad + ge, fill=(108, 99, 255, 30))
    # frosted dark body + hairline
    d.rounded_rectangle([px, py, px + pw, py + ph], radius=rad, fill=(26, 26, 46, 235))
    d.rounded_rectangle([px, py, px + pw, py + ph], radius=rad,
                        outline=(118, 110, 210, 110), width=s(1))

    cy = py + pad
    # close
    cd = m['close_d']
    d.ellipse([cx - cd // 2, cy, cx + cd // 2, cy + cd], fill=(255, 255, 255, 26))
    k = int(cd * 0.22)
    kc = (255, 255, 255, 190)
    mid = cy + cd // 2
    d.line([(cx - k, mid - k), (cx + k, mid + k)], fill=kc, width=s(2))
    d.line([(cx - k, mid + k), (cx + k, mid - k)], fill=kc, width=s(2))
    cy += cd + s(8)
    # handle
    d.rounded_rectangle([cx - m['handle_w'] // 2, cy, cx + m['handle_w'] // 2, cy + m['handle_h']],
                        radius=s(2), fill=(255, 255, 255, 80))
    cy += m['handle_h'] + s(10)
    # up chevron
    cw = int(inner_w * 0.42)
    d.line([(cx - cw, cy + int(chev_h * 0.62)), (cx, cy + int(chev_h * 0.30)),
            (cx + cw, cy + int(chev_h * 0.62))], fill=(238, 240, 255, 235), width=s(3),
           joint="curve")
    cy += chev_h + s(4)
    # step bars
    bars_top = cy
    bx0 = px + pad
    for i in range(n):
        by = cy + i * (bar_h + bar_gap)
        if i == active:
            d.rounded_rectangle([bx0 - s(3), by - s(3), bx0 + inner_w + s(3), by + bar_h + s(3)],
                                radius=int(bar_h * 0.7), fill=(108, 99, 255, 70))
            d.rounded_rectangle([bx0, by, bx0 + inner_w, by + bar_h],
                                radius=int(bar_h * 0.5), fill=(255, 255, 255, 255))
        elif i < active:
            d.rounded_rectangle([bx0, by, bx0 + inner_w, by + bar_h],
                                radius=int(bar_h * 0.5), fill=(255, 255, 255, 120))
        else:
            a = 26 if dim_future else 70
            d.rounded_rectangle([bx0, by, bx0 + inner_w, by + bar_h],
                                radius=int(bar_h * 0.5), fill=(255, 255, 255, a))
    cy += n * bar_h + (n - 1) * bar_gap + s(4)
    # down chevron
    d.line([(cx - cw, cy + int(chev_h * 0.38)), (cx, cy + int(chev_h * 0.70)),
            (cx + cw, cy + int(chev_h * 0.38))], fill=(255, 255, 255, 150), width=s(3),
           joint="curve")
    cy += chev_h + s(6)
    # dB label
    if show_db:
        db = DB_STEPS[active]
        txt = "0 dB" if db == 0 else f"{db} dB"
        d.text((cx, cy + m['label_h'] // 2), txt, font=mono(int(m['label_h'] * 0.62)),
               fill=(206, 198, 255, 210), anchor="mm")
    return bars_top

# --- A realistic "now playing" app behind the pill ---------------------------
def music_app(d, sx, sy, sw, sh):
    d.rounded_rectangle([sx, sy, sx + sw, sy + sh], radius=s(48), fill=(10, 14, 26))
    status_bar(d, sx, sy, sw)
    # header row
    hy = sy + s(70)
    d.line([(sx + s(26), hy), (sx + s(40), hy)], fill=(150, 158, 178), width=s(3))
    d.line([(sx + s(26), hy + s(8)), (sx + s(40), hy + s(8))], fill=(150, 158, 178), width=s(3))
    d.text((sx + sw // 2, hy + s(4)), "Now Playing", font=semi(20), fill=(150, 158, 178), anchor="mm")
    # album art
    ax = sx + int(sw * 0.12)
    aw = int(sw * 0.76)
    ay = sy + s(116)
    ah = aw
    art = Image.new("RGB", (aw, ah), (0, 0, 0))
    adraw = ImageDraw.Draw(art)
    for yy in range(ah):
        adraw.line([(0, yy), (aw, yy)], fill=lerp((58, 50, 120), (32, 40, 86), yy / ah))
    art = art.filter(ImageFilter.GaussianBlur(s(1)))
    mask = Image.new("L", (aw, ah), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, aw, ah], radius=s(22), fill=255)
    d._image.paste(art, (ax, ay), mask)
    # a tasteful note glyph centered in the art
    ncx, ncy = ax + aw // 2, ay + ah // 2
    d.ellipse([ncx - s(30), ncy + s(8), ncx - s(8), ncy + s(30)], fill=(120, 112, 180))
    d.ellipse([ncx + s(14), ncy + s(16), ncx + s(36), ncy + s(38)], fill=(120, 112, 180))
    d.line([(ncx - s(8), ncy + s(20)), (ncx - s(8), ncy - s(28)),
            (ncx + s(36), ncy - s(18)), (ncx + s(36), ncy + s(28))],
           fill=(120, 112, 180), width=s(5), joint="curve")
    # title + artist
    ty = ay + ah + s(26)
    d.text((ax, ty), "Midnight Air", font=semi(26), fill=WHITE, anchor="lm")
    d.text((ax, ty + s(34)), "Low Tide", font=reg(20), fill=MUTED, anchor="lm")
    # progress
    py = ty + s(70)
    d.rounded_rectangle([ax, py, ax + aw, py + s(6)], radius=s(3), fill=(46, 52, 80))
    d.rounded_rectangle([ax, py, ax + int(aw * 0.36), py + s(6)], radius=s(3), fill=VIOLET)
    d.ellipse([ax + int(aw * 0.36) - s(7), py - s(4), ax + int(aw * 0.36) + s(7), py + s(10)],
              fill=WHITE)
    d.text((ax, py + s(24)), "1:24", font=reg(15), fill=FAINT, anchor="lm")
    d.text((ax + aw, py + s(24)), "3:58", font=reg(15), fill=FAINT, anchor="rm")
    # transport controls
    cyy = py + s(70)
    mcx = ax + aw // 2
    for dx, r in ((-s(86), s(13)), (s(86), s(13))):
        # prev/next triangles
        sgn = -1 if dx < 0 else 1
        d.polygon([(mcx + dx - sgn * r, cyy - r), (mcx + dx - sgn * r, cyy + r),
                   (mcx + dx + sgn * r, cyy)], fill=(150, 158, 178))
        d.rounded_rectangle([mcx + dx + sgn * r, cyy - r, mcx + dx + sgn * r + s(3), cyy + r],
                            radius=s(1), fill=(150, 158, 178))
    d.ellipse([mcx - s(30), cyy - s(30), mcx + s(30), cyy + s(30)], fill=VIOLET)
    d.rounded_rectangle([mcx - s(9), cyy - s(11), mcx - s(2), cyy + s(11)], radius=s(2), fill=WHITE)
    d.rounded_rectangle([mcx + s(2), cyy - s(11), mcx + s(9), cyy + s(11)], radius=s(2), fill=WHITE)

def dim_app(d, sx, sy, sw, sh, chrome=True):
    """Plain dark screen for content-focused panels."""
    d.rounded_rectangle([sx, sy, sx + sw, sy + sh], radius=int(sw * 0.11), fill=(10, 14, 26))
    if chrome:
        status_bar(d, sx, sy, sw)

# --- Use-case icons (clean line glyphs) --------------------------------------
def ic_moon(d, cx, cy, r, col, bg):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=col)
    d.ellipse([cx - r + int(r * 0.55), cy - r - int(r * 0.12),
               cx + r + int(r * 0.55), cy + r - int(r * 0.12)], fill=bg)

def ic_phones(d, cx, cy, r, col):
    w = max(s(3), int(r * 0.16))
    d.arc([cx - r, cy - r, cx + r, cy + int(r * 0.2)], start=180, end=360, fill=col, width=w)
    ew, eh = int(r * 0.36), int(r * 0.54)
    d.rounded_rectangle([cx - r, cy - int(r * 0.05), cx - r + ew, cy - int(r * 0.05) + eh],
                        radius=int(ew * 0.45), fill=col)
    d.rounded_rectangle([cx + r - ew, cy - int(r * 0.05), cx + r, cy - int(r * 0.05) + eh],
                        radius=int(ew * 0.45), fill=col)

def ic_book(d, cx, cy, r, col):
    w = max(s(3), int(r * 0.12))
    hw, hh = int(r * 0.95), int(r * 0.8)
    d.line([(cx, cy - hh), (cx, cy + hh)], fill=col, width=w)
    for sgn in (-1, 1):
        d.line([(cx, cy - hh), (cx + sgn * hw, cy - hh + int(hh * 0.22))], fill=col, width=w)
        d.line([(cx + sgn * hw, cy - hh + int(hh * 0.22)), (cx + sgn * hw, cy + hh - int(hh * 0.05))],
               fill=col, width=w)
        d.line([(cx, cy + hh), (cx + sgn * hw, cy + hh - int(hh * 0.05))], fill=col, width=w)

def ic_quiet(d, cx, cy, r, col):
    # speaker with a small mute-ish calm wave
    d.polygon([(cx - r, cy - int(r * 0.28)), (cx - int(r * 0.35), cy - int(r * 0.28)),
               (cx + int(r * 0.1), cy - int(r * 0.62)), (cx + int(r * 0.1), cy + int(r * 0.62)),
               (cx - int(r * 0.35), cy + int(r * 0.28)), (cx - r, cy + int(r * 0.28))], fill=col)
    w = max(s(3), int(r * 0.14))
    d.arc([cx + int(r * 0.0), cy - int(r * 0.42), cx + int(r * 0.9), cy + int(r * 0.42)],
          start=300, end=60, fill=col, width=w)

# --- Screenshot scaffold -----------------------------------------------------
W, H = s(1080), s(1920)
PHONE_W, PHONE_H = s(486), s(966)
PHONE_X = (W - PHONE_W) // 2
PHONE_Y = s(372)

def new_canvas(accent=VIOLET):
    img = vgradient(W, H, BG_TOP, BG_BOT)
    img = glow(img, int(W * 0.70), int(H * 0.32), s(460), accent, 0.18)
    img = glow(img, int(W * 0.16), int(H * 0.80), s(280), INDIGO, 0.12)
    return img

def header(d, lines, sub):
    if len(lines) == 1:
        d.text((W // 2, s(150)), lines[0], font=bold(70), fill=WHITE, anchor="mm")
        suby = s(232)
    else:
        d.text((W // 2, s(126)), lines[0], font=bold(64), fill=WHITE, anchor="mm")
        d.text((W // 2, s(202)), lines[1], font=bold(64), fill=WHITE, anchor="mm")
        suby = s(286)
    d.text((W // 2, suby), sub, font=reg(34), fill=SOFT, anchor="mm")

def footer(d):
    d.text((W // 2, H - s(82)), "Granular Volume", font=semi(32), fill=(244, 246, 251, 220),
           anchor="mm")
    d.text((W // 2, H - s(44)), "Free   .   No ads   .   Open source", font=reg(26),
           fill=(199, 203, 236, 150), anchor="mm")

def build(idx, lines, sub, content_fn, accent=VIOLET):
    img = new_canvas(accent)
    d = ImageDraw.Draw(img, "RGBA")
    d._image = img
    header(d, lines, sub)
    sx, sy, sw, sh = draw_device(d, PHONE_X, PHONE_Y, PHONE_W, PHONE_H)
    content_fn(d, sx, sy, sw, sh)
    footer(d)
    out = img.resize((1080, 1920), Image.LANCZOS)
    out.save(os.path.join(OUT, f"screenshot_{idx}.png"))
    print(f"  OK screenshot_{idx}.png")

# --- Content per screen ------------------------------------------------------
def c_hook(d, sx, sy, sw, sh):
    music_app(d, sx, sy, sw, sh)
    m = pill_metrics(sw)
    px = sx + sw - m['pill_w'] - int(sw * 0.085)
    py = sy + int(sh * 0.27)
    draw_pill(d, px, py, m, active=5)  # -25 dB, low in its track

def c_steps(d, sx, sy, sw, sh):
    dim_app(d, sx, sy, sw, sh)
    m = pill_metrics(sw)
    px = sx + int(sw * 0.60) - m['pill_w'] // 2
    py = sy + int(sh * 0.16)
    bars_top = draw_pill(d, px, py, m, active=4, dim_future=False)
    # dB legend to the LEFT of the pill, measured to stay inside the screen
    labels = ["0 dB", "-5", "-10", "-15", "-20", "-25", "-30 dB"]
    lx = sx + int(sw * 0.12)
    for i, lbl in enumerate(labels):
        ly = bars_top + i * (m['bar_h'] + m['bar_gap']) + m['bar_h'] // 2
        d.text((lx, ly), lbl, font=mono(15), fill=(199, 203, 236, 205), anchor="lm")
    cap = "below the hardware minimum"
    f = fit_font(d, cap, reg, 24, sw - int(sw * 0.16))
    d.text((sx + sw // 2, sy + sh - int(sh * 0.06)), cap, font=f,
           fill=(34, 211, 238, 225), anchor="mm")

def c_usecases(d, sx, sy, sw, sh):
    dim_app(d, sx, sy, sw, sh)
    cards = [("moon", "Sleep"), ("phones", "Headphones"),
             ("book", "Late night"), ("quiet", "Quiet rooms")]
    pad = int(sw * 0.08)
    gap = int(sw * 0.05)
    cw = (sw - 2 * pad - gap) // 2
    ch = int(sh * 0.26)
    vgap = int(sh * 0.04)
    grid_h = 2 * ch + vgap
    top = sy + s(76) + ((sh - s(76)) - grid_h) // 2
    bg = (22, 28, 50)
    for i, (icon, label) in enumerate(cards):
        col, row = i % 2, i // 2
        cxp = sx + pad + col * (cw + gap)
        cyp = top + row * (ch + vgap)
        d.rounded_rectangle([cxp, cyp, cxp + cw, cyp + ch], radius=s(26), fill=bg,
                            outline=(64, 58, 116, 130), width=s(1))
        icx, icy, r = cxp + cw // 2, cyp + int(ch * 0.36), int(ch * 0.17)
        if icon == "moon":     ic_moon(d, icx, icy, r, VIOLET, bg)
        elif icon == "phones": ic_phones(d, icx, icy, r, SOFT)
        elif icon == "book":   ic_book(d, icx, icy, r, SOFT)
        else:                  ic_quiet(d, icx, icy, r, CYAN)
        f = fit_font(d, label, semi, 26, cw - int(cw * 0.18))
        d.text((icx, cyp + int(ch * 0.74)), label, font=f, fill=WHITE, anchor="mm")

def c_drag(d, sx, sy, sw, sh):
    music_app(d, sx, sy, sw, sh)
    m = pill_metrics(sw)
    # ghost start (upper-left of the float area)
    gpx = sx + int(sw * 0.16)
    gpy = sy + int(sh * 0.22)
    d.rounded_rectangle([gpx, gpy, gpx + m['pill_w'], gpy + m['pill_h']],
                        radius=int(m['pill_w'] * 0.40), fill=(108, 99, 255, 34))
    # final position lower-right, fully inside the screen
    px = sx + sw - m['pill_w'] - int(sw * 0.085)
    py = sy + sh - m['pill_h'] - int(sh * 0.10)
    # dashed arrow from ghost to final
    s_cx, s_cy = gpx + m['pill_w'] // 2, gpy + m['pill_h'] // 2
    e_cx, e_cy = px + m['pill_w'] // 2, py - s(6)
    for k in range(0, 16):
        t1, t2 = k / 16.0, (k + 0.55) / 16.0
        ax, ay = s_cx + (e_cx - s_cx) * t1, s_cy + (e_cy - s_cy) * t1
        bx, by = s_cx + (e_cx - s_cx) * t2, s_cy + (e_cy - s_cy) * t2
        d.line([(ax, ay), (bx, by)], fill=(108, 99, 255, 170), width=s(3))
    d.polygon([(e_cx, e_cy + s(4)), (e_cx - s(9), e_cy - s(12)), (e_cx + s(9), e_cy - s(12))],
              fill=(108, 99, 255, 210))
    draw_pill(d, px, py, m, active=4)

def c_noninvasive(d, sx, sy, sw, sh):
    dim_app(d, sx, sy, sw, sh)
    # The phone's OWN volume popup, untouched, at the top right (Android style)
    vw, vh = int(sw * 0.16), int(sh * 0.40)
    vx = sx + sw - vw - int(sw * 0.10)
    vy = sy + int(sh * 0.12)
    d.rounded_rectangle([vx, vy, vx + vw, vy + vh], radius=int(vw * 0.45), fill=(28, 34, 56))
    # filled near top = system at normal level
    fillh = int(vh * 0.62)
    d.rounded_rectangle([vx, vy + (vh - fillh), vx + vw, vy + vh], radius=int(vw * 0.45),
                        fill=(120, 128, 160))
    d.ellipse([vx + vw // 2 - s(6), vy + (vh - fillh) - s(6),
               vx + vw // 2 + s(6), vy + (vh - fillh) + s(6)], fill=WHITE)
    lbl = "System"
    d.text((vx + vw // 2, vy + vh + s(22)), lbl, font=reg(16), fill=MUTED, anchor="mm")
    # the Granular pill on the LEFT, clearly separate
    m = pill_metrics(sw)
    px = sx + int(sw * 0.12)
    py = sy + int(sh * 0.14)
    draw_pill(d, px, py, m, active=5)
    d.text((px + m['pill_w'] // 2, py + m['pill_h'] + s(22)), "Granular",
           font=reg(16), fill=(199, 203, 236, 210), anchor="mm")

def c_trust(d, sx, sy, sw, sh):
    dim_app(d, sx, sy, sw, sh)
    items = ["Completely free", "No advertisements", "No data collected",
             "No internet access", "Open source"]
    lx = sx + int(sw * 0.13)
    cy = sy + int(sh * 0.15)
    fs = 32
    maxw = sw - int(sw * 0.26)
    for text in items:
        f = fit_font(d, text, reg, fs, maxw - s(50))
        r = s(15)
        ccx, ccy = lx + r, cy + r
        d.ellipse([ccx - r, ccy - r, ccx + r, ccy + r], fill=(52, 211, 153, 55))
        d.line([(ccx - int(r * 0.45), ccy), (ccx - int(r * 0.05), ccy + int(r * 0.5)),
                (ccx + int(r * 0.55), ccy - int(r * 0.45))], fill=GREEN, width=s(3))
        d.text((lx + r * 2 + s(20), ccy), text, font=f, fill=WHITE, anchor="lm")
        cy += s(74)
    # FREE badge
    bcx, bcy = sx + sw // 2, sy + int(sh * 0.82)
    d.ellipse([bcx - s(72), bcy - s(72), bcx + s(72), bcy + s(72)], fill=(108, 99, 255, 55))
    d.ellipse([bcx - s(72), bcy - s(72), bcx + s(72), bcy + s(72)], outline=(108, 99, 255, 150),
              width=s(2))
    d.text((bcx, bcy - s(8)), "GPL", font=bold(46), fill=(180, 174, 255), anchor="mm")
    d.text((bcx, bcy + s(36)), "open source", font=reg(22), fill=SOFT, anchor="mm")

# --- Quick Settings tile screen ---------------------------------------------
def ic_speaker_waves(d, cx, cy, r, col):
    """The real tile icon: a speaker with two sound-wave arcs (matches ic_qs_tile.xml)."""
    d.polygon([(cx - r, cy - int(r * 0.30)), (cx - int(r * 0.34), cy - int(r * 0.30)),
               (cx + int(r * 0.12), cy - int(r * 0.66)), (cx + int(r * 0.12), cy + int(r * 0.66)),
               (cx - int(r * 0.34), cy + int(r * 0.30)), (cx - r, cy + int(r * 0.30))], fill=col)
    w = max(s(2), int(r * 0.13))
    d.arc([cx - int(r * 0.10), cy - int(r * 0.44), cx + int(r * 0.62), cy + int(r * 0.44)],
          start=302, end=58, fill=col, width=w)
    d.arc([cx - int(r * 0.10), cy - int(r * 0.78), cx + int(r * 1.02), cy + int(r * 0.78)],
          start=306, end=54, fill=col, width=w)

def ic_wifi(d, cx, cy, r, col):
    w = max(s(2), int(r * 0.13))
    for rr in (int(r * 0.92), int(r * 0.60), int(r * 0.28)):
        d.arc([cx - rr, cy - rr + int(r * 0.2), cx + rr, cy + rr + int(r * 0.2)],
              start=212, end=328, fill=col, width=w)
    d.ellipse([cx - s(3), cy + int(r * 0.5), cx + s(3), cy + int(r * 0.5) + s(6)], fill=col)

def ic_bolt(d, cx, cy, r, col):
    d.polygon([(cx + int(r * 0.12), cy - r), (cx - int(r * 0.5), cy + int(r * 0.12)),
               (cx - int(r * 0.02), cy + int(r * 0.12)), (cx - int(r * 0.12), cy + r),
               (cx + int(r * 0.5), cy - int(r * 0.12)), (cx + int(r * 0.02), cy - int(r * 0.12))],
              fill=col)

def ic_plane(d, cx, cy, r, col):
    d.polygon([(cx, cy - r), (cx + int(r * 0.86), cy + int(r * 0.5)),
               (cx + int(r * 0.20), cy + int(r * 0.28)), (cx + int(r * 0.20), cy + r),
               (cx, cy + int(r * 0.72)), (cx - int(r * 0.20), cy + r),
               (cx - int(r * 0.20), cy + int(r * 0.28)), (cx - int(r * 0.86), cy + int(r * 0.5))],
              fill=col)

def ic_battery(d, cx, cy, r, col):
    bw, bh = int(r * 1.5), int(r * 0.9)
    x0, y0 = cx - bw // 2, cy - bh // 2
    w = max(s(2), int(r * 0.12))
    d.rounded_rectangle([x0, y0, x0 + bw, y0 + bh], radius=s(4), outline=col, width=w)
    d.rounded_rectangle([x0 + bw, cy - int(bh * 0.22), x0 + bw + s(4), cy + int(bh * 0.22)],
                        radius=s(2), fill=col)
    d.rounded_rectangle([x0 + w + s(2), y0 + w + s(2), x0 + int(bw * 0.5), y0 + bh - w - s(2)],
                        radius=s(2), fill=col)

def c_qstile(d, sx, sy, sw, sh):
    dim_app(d, sx, sy, sw, sh)
    # the pulled-down shade panel
    px = sx + s(16)
    pw = sw - s(32)
    py = sy + s(6)
    ph = int(sh * 0.70)
    d.rounded_rectangle([px, py, px + pw, py + ph], radius=s(44), fill=(24, 28, 48, 250))
    d.rounded_rectangle([px, py, px + pw, py + ph], radius=s(44), outline=(70, 66, 120, 90), width=s(1))
    ix = px + s(30)
    # time + date
    d.text((ix, py + s(58)), "9:41", font=bold(46), fill=WHITE, anchor="lm")
    d.text((ix, py + s(104)), "Mon, Jul 8", font=reg(22), fill=MUTED, anchor="lm")
    # brightness slider
    by = py + s(150)
    bx0, bx1 = px + s(28), px + pw - s(28)
    d.rounded_rectangle([bx0, by, bx1, by + s(30)], radius=s(15), fill=(42, 48, 74))
    fillw = int((bx1 - bx0) * 0.62)
    d.rounded_rectangle([bx0, by, bx0 + fillw, by + s(30)], radius=s(15), fill=(150, 158, 190))
    sc = bx0 + fillw
    d.ellipse([sc - s(4), by + s(8), sc + s(4), by + s(22)], fill=(70, 76, 104))
    # tile grid (2 columns of Android-12-style pill tiles)
    tiles = [
        ("gv",   "Granular Volume", "On",  True),
        ("wifi", "Wi-Fi",          "Home", False),
        ("moon", "Do Not Disturb", "Off", False),
        ("bolt", "Flashlight",     "Off", False),
        ("plane","Airplane mode",  "Off", False),
        ("batt", "Battery Saver",  "Off", False),
    ]
    gx0 = px + s(26)
    gtop = by + s(60)
    gw = pw - s(52)
    col_gap = s(16)
    tile_w = (gw - col_gap) // 2
    tile_h = int(sh * 0.088)
    row_gap = s(16)
    for i, (icon, label, status, active) in enumerate(tiles):
        col, row = i % 2, i // 2
        tx = gx0 + col * (tile_w + col_gap)
        ty = gtop + row * (tile_h + row_gap)
        if active:
            # soft glow to draw the eye
            ge = s(14)
            d.rounded_rectangle([tx - ge, ty - ge, tx + tile_w + ge, ty + tile_h + ge],
                                radius=int(tile_h * 0.5) + ge, fill=(108, 99, 255, 40))
            d.rounded_rectangle([tx, ty, tx + tile_w, ty + tile_h], radius=int(tile_h * 0.5),
                                fill=(108, 99, 255, 255))
            chip_bg, glyph_col, lab_col, stat_col = (255, 255, 255, 240), VIOLET, WHITE, (233, 231, 255, 235)
        else:
            d.rounded_rectangle([tx, ty, tx + tile_w, ty + tile_h], radius=int(tile_h * 0.5),
                                fill=(36, 42, 64, 255))
            chip_bg, glyph_col, lab_col, stat_col = (58, 64, 92, 255), (150, 158, 185), SOFT, FAINT
        # leading icon chip
        cr = int(tile_h * 0.30)
        ccx = tx + s(20) + cr
        ccy = ty + tile_h // 2
        d.ellipse([ccx - cr, ccy - cr, ccx + cr, ccy + cr], fill=chip_bg)
        gr = int(cr * 0.62)
        if   icon == "gv":    ic_speaker_waves(d, ccx, ccy, gr, glyph_col)
        elif icon == "wifi":  ic_wifi(d, ccx, ccy, gr, glyph_col)
        elif icon == "moon":  ic_moon(d, ccx, ccy, gr, glyph_col, chip_bg[:3])
        elif icon == "bolt":  ic_bolt(d, ccx, ccy, gr, glyph_col)
        elif icon == "plane": ic_plane(d, ccx, ccy, gr, glyph_col)
        else:                 ic_battery(d, ccx, ccy, gr, glyph_col)
        # label + status, sized to fit the remaining tile width; wrap to two
        # lines (dropping the status line) when the label is too long to fit
        # cleanly on one line, so nothing is ever truncated.
        text_x = ccx + cr + s(14)
        maxw = tx + tile_w - text_x - s(14)
        lf = semi(22)
        if d.textlength(label, font=lf) <= maxw:
            d.text((text_x, ty + int(tile_h * 0.36)), label, font=lf, fill=lab_col, anchor="lm")
            d.text((text_x, ty + int(tile_h * 0.66)), status, font=reg(17), fill=stat_col, anchor="lm")
        else:
            parts = label.split(" ", 1)
            wf = semi(21)
            d.text((text_x, ty + int(tile_h * 0.34)), parts[0], font=wf, fill=lab_col, anchor="lm")
            if len(parts) > 1:
                d.text((text_x, ty + int(tile_h * 0.64)), parts[1], font=wf, fill=lab_col, anchor="lm")

# --- Feature graphic 1024x500 ------------------------------------------------
def make_feature():
    Wg, Hg = s(1024), s(500)
    img = vgradient(Wg, Hg, (0x12, 0x16, 0x2C), (0x20, 0x18, 0x4E))
    img = glow(img, int(Wg * 0.76), int(Hg * 0.42), s(280), VIOLET, 0.30)
    img = glow(img, int(Wg * 0.10), int(Hg * 0.66), s(180), INDIGO, 0.18)
    d = ImageDraw.Draw(img, "RGBA")
    d._image = img
    # mini device
    pw, ph = s(176), s(372)
    px, py = s(92), (Hg - ph) // 2
    sx, sy, sw, sh = draw_device(d, px, py, pw, ph)
    dim_app(d, sx, sy, sw, sh, chrome=False)
    # size the pill so its full height (incl. the dB readout) fits the mini screen
    m = pill_metrics(sw)
    target_h = int(sh * 0.84)
    if m['pill_h'] > target_h:
        m = pill_metrics(sw, pill_w=int(m['pill_w'] * target_h / m['pill_h']))
    ppx = sx + (sw - m['pill_w']) // 2
    ppy = sy + (sh - m['pill_h']) // 2
    draw_pill(d, ppx, ppy, m, active=5, show_db=True)
    # wordmark + tagline
    tx = s(330)
    d.text((tx, s(140)), "Volume Control:", font=semi(44), fill=WHITE, anchor="lm")
    d.text((tx, s(224)), "Quiet Dial", font=bold(72), fill=VIOLET, anchor="lm")
    d.text((tx, s(292)), "Quieter than your phone or tablet allows.", font=reg(28), fill=SOFT, anchor="lm")
    by = s(346)
    bx = tx
    for badge in ["Free", "No ads", "Open source"]:
        bw = int(d.textlength(badge, font=semi(23))) + s(40)
        d.rounded_rectangle([bx, by, bx + bw, by + s(46)], radius=s(23),
                            fill=(108, 99, 255, 45), outline=(108, 99, 255, 160), width=s(1))
        d.text((bx + bw // 2, by + s(23)), badge, font=semi(23), fill=WHITE, anchor="mm")
        bx += bw + s(18)
    img.resize((1024, 500), Image.LANCZOS).save(os.path.join(OUT, "feature_graphic_1024x500.png"))
    print("  OK feature_graphic_1024x500.png")

# --- Main --------------------------------------------------------------------
print("Generating Granular Volume marketing assets v4 (Quiet Instrument)...")
make_feature()
build(1, ["When the lowest setting", "is still too loud"],
      "Granular Volume picks up where Android stops", c_hook)
build(2, ["Quieter than your", "phone allows"],
      "Seven fine steps below the hardware minimum", c_steps, accent=CYAN)
build(3, ["Made for quiet moments"],
      "The volume you reach for at night", c_usecases)
build(4, ["Always within reach"],
      "Drag it anywhere. Close it with one tap.", c_drag)
build(5, ["It touches nothing else"],
      "No button override. No system takeover.", c_noninvasive, accent=CYAN)
build(6, ["Free, private, open"],
      "No ads. No tracking. GPL-3.0.", c_trust)
build(7, ["One tap from", "Quick Settings"],
      "Turn it on or off without opening the app", c_qstile)
print("Done. Assets saved to:", OUT)
