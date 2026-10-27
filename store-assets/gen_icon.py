# Builds the launcher icon layers of 1.7.7 (speaker with three fading waves, a scale with the two lowest quiet steps lit).
# The 512 px store icon is play_icon_512.png (same art on the background gradient).
#   foreground  = speaker with three fading waves + scale with the two lower quiet steps lit, transparent, inside the 66 dp safe circle
#   monochrome  = same shapes as a single-colour alpha mask (themed icons, Android 13+)
#   background  = the store icon's radial gradient, as a vector (written separately)
# Output: android/app/src/main/res/mipmap-*/ic_launcher_foreground.png and ic_launcher_monochrome.png
import io, os, subprocess, pathlib, tempfile
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'android', 'app', 'src', 'main', 'res')
CH = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
F = 432                      # xxxhdpi foreground canvas (108 dp x 4)
BOX_CX, BOX_CY = 251.5, 253  # centre of the art's bounding box in the 512 store canvas (x 47..456, y 120..386)
S = 0.555                    # farthest art point ~237 px from the centre -> ~132 px, the 264 px safe circle

# The art: speaker with three fading waves, scale with the two lower quiet steps lit
import math
def arc(r, a=48, cx=150, cy=256):
    t = math.radians(a); x = cx + r * math.cos(t)
    return 'M%.1f %.1f A%d %d 0 0 1 %.1f %.1f' % (x, cy - r * math.sin(t), r, r, x, cy + r * math.sin(t))
WAVES = ''.join('<path d="%s" fill="none" stroke="{sp}" stroke-width="13" stroke-linecap="round" opacity="%s"/>' % (arc(r), o)
                for r, o in ((36, '1'), (60, '.6'), (84, '.3')))
BARS = ''.join('<rect x="288" y="%d" width="152" height="10" rx="5"/>' % y for y in (120, 144, 168, 192, 216))
LIT = ''.join('<rect x="288" y="%d" width="152" height="28" rx="14"/>' % y for y in (318, 358))
ART = ('<g transform="translate(4,0)"><g {shadow}>'
       '<path d="M50 218 h36 l58 -56 v188 l-58 -56 h-36 z" fill="{sp}" stroke="{sp}" stroke-width="14" stroke-linejoin="round"/>' + WAVES + '</g>'
       '<g fill="{w}" opacity=".32">' + BARS + '</g>{oglow}'
       '<rect x="276" y="244" width="176" height="14" rx="7" fill="{o}"/>'
       '<rect x="288" y="278" width="152" height="28" rx="14" fill="{w}" opacity=".24"/>{glow}'
       '<g fill="{w}">' + LIT + '</g></g>')
DEFS = ('<linearGradient id="sp" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffffff"/><stop offset="1" stop-color="#e6e3ff"/></linearGradient>'
        '<filter id="gl" x="-50%" y="-200%" width="200%" height="500%"><feGaussianBlur stdDeviation="7"/></filter>'
        '<filter id="og" x="-20%" y="-300%" width="140%" height="700%"><feGaussianBlur stdDeviation="5"/></filter>'
        '<filter id="sh" x="-30%" y="-30%" width="160%" height="160%"><feDropShadow dx="0" dy="8" stdDeviation="10" flood-color="#0b0a2a" flood-opacity=".45"/></filter>')

def svg(mono):
    if mono:   # one colour, no glow or shadow: Android tints the alpha
        art = ART.format(sp='#fff', w='#fff', o='#fff', shadow='', oglow='', glow='')
    else:
        art = ART.format(sp='url(#sp)', w='#fff', o='#ffa53a', shadow='filter="url(#sh)"',
                         oglow='<rect x="276" y="244" width="176" height="14" rx="7" fill="#ffa53a" filter="url(#og)" opacity=".55"/>',
                         glow='<g filter="url(#gl)" opacity=".7" fill="#fff">' + LIT + '</g>')
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d"><defs>%s</defs>'
            '<g transform="translate(%g,%g) scale(%g) translate(%g,%g)">%s</g></svg>'
            % (F, F, F, F, DEFS, F / 2, F / 2, S, -BOX_CX, -BOX_CY, art))

def render(markup, out):
    tmp = tempfile.mkdtemp(); hp = os.path.join(tmp, 'i.html')
    io.open(hp, 'w', encoding='utf-8').write('<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;background:transparent}</style></head><body>' + markup + '</body></html>')
    subprocess.run([CH, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--default-background-color=00000000",
                    "--force-device-scale-factor=1", "--window-size=%d,%d" % (F, F), "--screenshot=" + out, pathlib.Path(hp).as_uri()],
                   capture_output=True, timeout=60)
    return Image.open(out).convert('RGBA')

for name, mono in (('ic_launcher_foreground', False), ('ic_launcher_monochrome', True)):
    big = render(svg(mono), os.path.join(tempfile.mkdtemp(), name + '.png'))
    assert big.getpixel((0, 0))[3] == 0, 'background must be transparent'
    for d, px in (('mdpi', 108), ('hdpi', 162), ('xhdpi', 216), ('xxhdpi', 324), ('xxxhdpi', 432)):
        big.resize((px, px), Image.LANCZOS).save(os.path.join(RES, 'mipmap-' + d, name + '.png'), optimize=True)
    a = big.split()[-1]
    far = max(((x - F / 2) ** 2 + (y - F / 2) ** 2) ** .5 for y in range(0, F, 2) for x in range(0, F, 2) if a.getpixel((x, y)) > 60)
    print(name, 'bbox', a.point(lambda v: 255 if v > 20 else 0).getbbox(), 'farthest solid px from centre %.1f (safe radius 132)' % far)
