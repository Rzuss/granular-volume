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

# --- GitHub social preview card, 1280x640 ------------------------------------
# Same brand primitives as make_feature() above (device chrome, pill widget,
# palette), scaled 1.25x from the 1024x500 feature graphic layout so every
# proportion matches exactly, then padded to GitHub's recommended 640px
# height. Rendered natively at this size (not an upscaled crop) to stay sharp.
def make_social_preview():
    SCALE = 1.25
    Wg, Hg = s(int(1024 * SCALE)), s(int(500 * SCALE))
    img = vgradient(Wg, Hg, (0x12, 0x16, 0x2C), (0x20, 0x18, 0x4E))
    img = glow(img, int(Wg * 0.76), int(Hg * 0.42), s(int(280 * SCALE)), VIOLET, 0.30)
    img = glow(img, int(Wg * 0.10), int(Hg * 0.66), s(int(180 * SCALE)), INDIGO, 0.18)
    d = ImageDraw.Draw(img, "RGBA")
    d._image = img

    pw, ph = s(int(176 * SCALE)), s(int(372 * SCALE))
    px, py = s(int(92 * SCALE)), (Hg - ph) // 2
    sx, sy, sw, sh = draw_device(d, px, py, pw, ph)
    dim_app(d, sx, sy, sw, sh, chrome=False)

    m = pill_metrics(sw)
    target_h = int(sh * 0.84)
    if m['pill_h'] > target_h:
        m = pill_metrics(sw, pill_w=int(m['pill_w'] * target_h / m['pill_h']))
    ppx = sx + (sw - m['pill_w']) // 2
    ppy = sy + (sh - m['pill_h']) // 2
    draw_pill(d, ppx, ppy, m, active=5, show_db=True)

    tx = s(int(330 * SCALE))
    d.text((tx, s(int(150 * SCALE))), "Granular", font=bold(int(72 * SCALE)), fill=WHITE, anchor="lm")
    d.text((tx, s(int(224 * SCALE))), "Volume", font=bold(int(72 * SCALE)), fill=VIOLET, anchor="lm")
    d.text((tx, s(int(292 * SCALE))), "Volume below Android's minimum.", font=reg(int(33 * SCALE)), fill=SOFT, anchor="lm")
    by = s(int(346 * SCALE))
    bx = tx
    for badge in ["Free", "No ads", "Open source", "F-Droid"]:
        bw = int(d.textlength(badge, font=semi(int(23 * SCALE)))) + s(int(40 * SCALE))
        bh = s(int(46 * SCALE))
        d.rounded_rectangle([bx, by, bx + bw, by + bh], radius=bh // 2,
                            fill=(108, 99, 255, 45), outline=(108, 99, 255, 160), width=s(1))
        d.text((bx + bw // 2, by + bh // 2), badge, font=semi(int(23 * SCALE)), fill=WHITE, anchor="mm")
        bx += bw + s(int(18 * SCALE))

    final = img.resize((1280, 625), Image.LANCZOS)
    canvas = Image.new("RGB", (1280, 640), (0x12, 0x16, 0x2C))
    canvas.paste(final, (0, (640 - 625) // 2))
    canvas.save(os.path.join(OUT, "social_preview_1280x640.png"))
    print("  OK social_preview_1280x640.png")

if __name__ == "__main__":
    make_social_preview()
