"""Hand-style 16x16 pixel textures for the 2.3 ship blocks. Flat colours, 1px bevels and rivets,
like the rest of Simple Machines, so they are easy to repaint. Every texture is one file."""
import random
from PIL import Image

W = 16

def rgb(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)

def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)

def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))

# finish palettes: base, light edge, dark edge, seam, accent, accent2
FINISH = {
    'ancient':    dict(base=rgb('#6f7f6a'), light=rgb('#93a58a'), dark=rgb('#46523f'), seam=rgb('#333b2e'),
                       accent=rgb('#c49a4a'), accent2=rgb('#5fa58f'), glass=rgb('#7fc8b0', 110), glow=rgb('#f2d68a')),
    'industrial': dict(base=rgb('#8a8f96'), light=rgb('#b4b9bf'), dark=rgb('#5c6066'), seam=rgb('#3d4045'),
                       accent=rgb('#e0b030'), accent2=rgb('#2b2b2b'), glass=rgb('#9cc4d8', 100), glow=rgb('#fff2c8')),
    'clean':      dict(base=rgb('#d8dde2'), light=rgb('#f4f6f8'), dark=rgb('#a6adb5'), seam=rgb('#8a929b'),
                       accent=rgb('#4aa6e0'), accent2=rgb('#2f6f9f'), glass=rgb('#cfe8f6', 90), glow=rgb('#e8f8ff')),
}

def new(c=(0, 0, 0, 0), w=W, h=W):
    return Image.new('RGBA', (w, h), c)

def noise(im, amount, seed, box=None):
    r = random.Random(seed)
    px = im.load()
    x0, y0, x1, y1 = box or (0, 0, im.width, im.height)
    for y in range(y0, y1):
        for x in range(x0, x1):
            c = px[x, y]
            if c[3] == 0:
                continue
            f = 1 + (r.random() - 0.5) * amount
            px[x, y] = shade(c, f)

def rect(im, x0, y0, x1, y1, c):
    px = im.load()
    for y in range(max(0, y0), min(im.height, y1 + 1)):
        for x in range(max(0, x0), min(im.width, x1 + 1)):
            px[x, y] = c

def bevel(im, x0, y0, x1, y1, light, dark):
    rect(im, x0, y0, x1, y0, light)
    rect(im, x0, y0, x0, y1, light)
    rect(im, x0, y1, x1, y1, dark)
    rect(im, x1, y0, x1, y1, dark)

def dot(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.load()[x, y] = c

def rivet(im, x, y, p):
    dot(im, x, y, p['light'])
    dot(im, x + 1, y, p['dark'])
    dot(im, x, y + 1, p['dark'])
    dot(im, x + 1, y + 1, p['seam'])

def plate(p, seed, amount=0.08):
    im = new(p['base'])
    noise(im, amount, seed)
    bevel(im, 0, 0, 15, 15, p['light'], p['dark'])
    return im

# ---- finish set ----

def plating(p, seed):
    im = new(p['base'])
    noise(im, 0.08, seed)
    for ox in (0, 8):
        for oy in (0, 8):
            bevel(im, ox, oy, ox + 7, oy + 7, p['light'], p['dark'])
            rivet(im, ox + 2, oy + 2, p) if (ox + oy) % 16 == 0 else rivet(im, ox + 4, oy + 4, p)
    return im

def panel(p, seed):
    im = plate(p, seed)
    bevel(im, 2, 2, 13, 13, p['dark'], p['light'])
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rivet(im, x, y, p)
    return im

def hull(p, seed):
    im = new(shade(p['base'], 0.78))
    noise(im, 0.1, seed)
    d = shade(p['dark'], 0.8)
    for oy in (0, 5, 10):
        bevel(im, 0, oy, 15, oy + 4 if oy < 10 else 15, shade(p['light'], 0.85), d)
    for x in (2, 7, 12):
        for oy in (1, 6, 11):
            dot(im, x, oy + 1, p['seam'])
            dot(im, x + 1, oy + 1, shade(p['light'], 0.9))
    return im

def bulkhead(p, seed):
    im = plate(p, seed)
    for x in (3, 7, 11):
        rect(im, x, 1, x, 12, p['light'])
        rect(im, x + 1, 1, x + 1, 12, p['dark'])
    for x in range(1, 15):
        dot(im, x, 13, p['accent'] if (x // 2) % 2 == 0 else p['accent2'])
        dot(im, x, 14, p['accent2'] if (x // 2) % 2 == 0 else p['accent'])
    return im

def grate(p, seed):
    im = new((0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if x % 4 == 0 or y % 4 == 0 or x == 15 or y == 15:
                im.load()[x, y] = p['base']
    noise(im, 0.1, seed)
    bevel_frame(im, p)
    for y in range(0, 16, 4):
        rect(im, 1, y, 14, y, shade(p['light'], 0.95))
    return im

def bevel_frame(im, p):
    rect(im, 0, 0, 15, 0, p['light']); rect(im, 0, 0, 0, 15, p['light'])
    rect(im, 0, 15, 15, 15, p['dark']); rect(im, 15, 0, 15, 15, p['dark'])

def window(p, seed):
    im = new(p['glass'])
    frame = new((0, 0, 0, 0))
    rect(im, 0, 0, 15, 1, p['base']); rect(im, 0, 14, 15, 15, p['base'])
    rect(im, 0, 0, 1, 15, p['base']); rect(im, 14, 0, 15, 15, p['base'])
    bevel_frame(im, p)
    bevel(im, 1, 1, 14, 14, p['dark'], p['light'])
    g = shade(p['glass'], 1.25)
    for i in range(3, 7):
        dot(im, i, 9 - i, (g[0], g[1], g[2], 150))
    dot(im, 9, 4, (g[0], g[1], g[2], 150)); dot(im, 10, 3, (g[0], g[1], g[2], 150))
    return im

def window_pane(p):
    """The inside of a joined window wall: the glass with no frame, so neighbours read as one sheet."""
    im = new(p['glass'])
    g = shade(p['glass'], 1.25)
    for i in range(3, 7):
        dot(im, i, 9 - i, (g[0], g[1], g[2], 150))
    return im

def porthole(p, seed):
    im = plate(p, seed)
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d < 4.6:
                im.load()[x, y] = p['glass']
            elif d < 5.6:
                im.load()[x, y] = p['dark'] if (x + y) > 15 else p['light']
            elif d < 6.4:
                im.load()[x, y] = p['seam']
    dot(im, 6, 5, (255, 255, 255, 150)); dot(im, 5, 6, (255, 255, 255, 150))
    for x, y in ((1, 1), (13, 1), (1, 13), (13, 13)):
        rivet(im, x, y, p)
    return im

def lamp(p, seed):
    im = plate(p, seed)
    rect(im, 3, 3, 12, 12, p['glow'])
    bevel(im, 2, 2, 13, 13, p['dark'], p['light'])
    g = shade(p['glow'], 1.0)
    rect(im, 4, 4, 11, 11, mix(p['glow'], (255, 255, 255, 255), 0.5))
    for x in range(3, 13):
        dot(im, x, 7, shade(g, 0.92)); dot(im, x, 8, shade(g, 0.92))
    return im

def vent(p, seed):
    im = plate(p, seed)
    for y in (3, 6, 9, 12):
        rect(im, 3, y, 12, y, p['seam'])
        rect(im, 3, y + 1, 12, y + 1, p['light'])
    return im

def damaged(p, seed):
    im = panel(p, seed)
    r = random.Random(seed)
    # a dent and a crack
    rect(im, 9, 3, 12, 6, p['dark']); rect(im, 10, 4, 12, 6, p['seam'])
    x, y = 4, 10
    for _ in range(9):
        dot(im, x, y, p['seam'])
        x += r.choice((1, 1, 0)); y += r.choice((-1, 0, 1))
    dot(im, 13, 13, p['base'])
    return im

def scorched(p, seed):
    im = panel(p, seed)
    px = im.load()
    r = random.Random(seed)
    for y in range(16):
        for x in range(16):
            d = ((x - 9) ** 2 + (y - 10) ** 2) ** 0.5
            if d < 7 + r.random() * 1.5:
                f = 0.35 + d / 12
                px[x, y] = shade(px[x, y], min(1, f))
    return im

# ---- other blocks ----

def boundary(seed):
    p = dict(base=rgb('#2a2d33'), light=rgb('#3c4048'), dark=rgb('#17191d'), seam=rgb('#0f1013'))
    im = plate(p, seed, 0.12)
    for i in range(-16, 16, 6):
        for t in range(16):
            for w in range(2):
                dot(im, t, t + i + w, rgb('#3e3416'))
    bevel(im, 0, 0, 15, 15, p['light'], p['dark'])
    return im

def hull_glass(seed):
    p = FINISH['industrial']
    im = new(rgb('#6a8fa8', 120))
    rect(im, 0, 0, 15, 0, rgb('#2a2d33')); rect(im, 0, 15, 15, 15, rgb('#2a2d33'))
    rect(im, 0, 0, 0, 15, rgb('#2a2d33')); rect(im, 15, 0, 15, 15, rgb('#2a2d33'))
    for i in range(4, 9):
        dot(im, i, 12 - i, rgb('#d8ecf8', 160))
    return im

def hull_glass_pane():
    im = new(rgb('#6a8fa8', 120))
    for i in range(4, 9):
        dot(im, i, 12 - i, rgb('#d8ecf8', 160))
    return im

def frames(n, fn):
    """A vertical strip of n frames for an animated texture."""
    im = new(w=W, h=W * n)
    for i in range(n):
        im.paste(fn(i), (0, W * i))
    return im

def screen_base(p, seed, screen=rgb('#0c1418')):
    im = plate(p, seed)
    rect(im, 2, 2, 13, 13, screen)
    bevel(im, 1, 1, 14, 14, p['dark'], p['light'])
    return im

def control_nav(p, seed, i):
    im = screen_base(p, seed)
    # a heading line sweeps round
    import math
    a = i / 8 * 2 * math.pi
    for t in range(1, 5):
        dot(im, int(7.5 + math.cos(a) * t), int(7.5 + math.sin(a) * t), rgb('#5fe07a'))
    for x, y in ((4, 4), (11, 5), (6, 11), (10, 10)):
        dot(im, x, y, rgb('#2f7f45'))
    dot(im, 7, 7, rgb('#c0ffd0'))
    return im

def control_eng(p, seed, i):
    im = screen_base(p, seed)
    r = random.Random(i * 7 + 3)
    for k, x in enumerate(range(3, 13, 2)):
        h = 2 + (r.randint(1, 7) + i * (k + 1)) % 8
        rect(im, x, 12 - h, x, 12, rgb('#e0b030') if h < 7 else rgb('#ff6040'))
    return im

def control_reactor(p, seed, i):
    im = screen_base(p, seed)
    import math
    level = int(3 + 3 * (1 + math.sin(i / 8 * 2 * math.pi)) / 2)
    rect(im, 5, 12 - level * 1, 10, 12, rgb('#40c8ff'))
    rect(im, 4, 3, 11, 3, rgb('#1e5f80'))
    dot(im, 3 + i % 10, 4, rgb('#a0e8ff'))
    return im

def broken_screen(p, seed):
    im = screen_base(p, seed, rgb('#101010'))
    for x, y in ((5, 4), (6, 5), (7, 5), (8, 6), (8, 7), (9, 8), (10, 8), (6, 9), (5, 10)):
        dot(im, x, y, rgb('#d8d8d8'))
    dot(im, 11, 11, rgb('#303030'))
    return im

def star_map(i):
    im = new(rgb('#050912'))
    r = random.Random(11)
    stars = [(r.randint(1, 14), r.randint(1, 14), r.random()) for _ in range(14)]
    for x, y, b in stars:
        x = (x + i) % 16
        c = rgb('#ffffff') if b > 0.7 else rgb('#8fa8d8')
        dot(im, x, y, c)
    dot(im, (8 + i // 2) % 16, 8, rgb('#ffb040'))
    rect(im, 0, 0, 15, 0, rgb('#3a4250')); rect(im, 0, 15, 15, 15, rgb('#22272f'))
    rect(im, 0, 0, 0, 15, rgb('#3a4250')); rect(im, 15, 0, 15, 15, rgb('#22272f'))
    return im

def status(i):
    im = new(rgb('#0c1418'))
    for row, y in enumerate((3, 6, 9, 12)):
        n = 4 + (row * 3 + i) % 8
        rect(im, 2, y, 2 + n, y, rgb('#5fe07a') if n < 10 else rgb('#ffb040'))
    dot(im, 13, 2, rgb('#ff4040') if i % 2 else rgb('#401010'))
    rect(im, 0, 0, 15, 0, rgb('#3a4250')); rect(im, 0, 15, 15, 15, rgb('#22272f'))
    rect(im, 0, 0, 0, 15, rgb('#3a4250')); rect(im, 15, 0, 15, 15, rgb('#22272f'))
    return im

def guide(i):
    im = new(rgb('#30343a'))
    rect(im, 0, 0, 15, 0, rgb('#4a4f57')); rect(im, 0, 15, 15, 15, rgb('#202328'))
    for k in range(4):
        y = 13 - k * 4
        on = (k == i % 4)
        c = rgb('#7fe8ff') if on else rgb('#1f5f70')
        rect(im, 6, y - 1, 9, y - 1, c)
        dot(im, 5, y, c); dot(im, 10, y, c)
    return im

def alert(i):
    import math
    t = (1 + math.sin(i / 8 * 2 * math.pi)) / 2
    im = new(rgb('#3a3d42'))
    c = mix(rgb('#5a1010'), rgb('#ff3a2a'), t)
    rect(im, 3, 3, 12, 12, c)
    rect(im, 5, 5, 10, 10, mix(c, rgb('#ffd0c0'), t * 0.6))
    bevel(im, 2, 2, 13, 13, rgb('#22252a'), rgb('#5a5f66'))
    return im

def strip(p, lit=True):
    im = new(rgb('#5c6066'))
    bevel(im, 0, 0, 15, 15, rgb('#7a7f86'), rgb('#3d4045'))
    rect(im, 2, 5, 13, 10, rgb('#fff6dc') if lit else rgb('#4a4a44'))
    if lit:
        rect(im, 2, 7, 13, 8, rgb('#ffffff'))
    return im

def holo(i):
    import math
    im = new((0, 0, 0, 0))
    a = i / 8 * 2 * math.pi
    for k in range(24):
        b = k / 24 * 2 * math.pi
        x = 7.5 + math.cos(b) * 6
        y = 7.5 + math.sin(b) * 6 * abs(math.cos(a))
        dot(im, int(x), int(y), rgb('#60e0ff', 170))
    for k in range(12):
        b = k / 12 * 2 * math.pi
        dot(im, int(7.5 + math.cos(b) * 3), int(7.5 + math.sin(b) * 3), rgb('#a0f0ff', 200))
    dot(im, 7, 7, rgb('#ffffff', 220)); dot(im, 8, 8, rgb('#ffffff', 220))
    return im

def scanner(i):
    im = new(rgb('#0c1418'))
    y = 2 + (i * 2) % 12
    rect(im, 2, y, 13, y, rgb('#5fe07a'))
    for x in range(3, 13):
        dot(im, x, 8 + (1 if x % 3 == 0 else 0) * (1 if i % 2 else -1), rgb('#3fa060'))
    bevel_frame(im, FINISH['clean'])
    return im

def plain(c, seed, amount=0.08):
    im = new(c)
    noise(im, amount, seed)
    return im

def strut_tex(p, seed):
    im = new(p['base'])
    noise(im, 0.08, seed)
    rect(im, 0, 0, 1, 15, p['light']); rect(im, 14, 0, 15, 15, p['dark'])
    for y in (3, 11):
        rect(im, 2, y, 13, y, p['seam'])
    return im

def pipe_tex(c, seed, band):
    im = plain(c, seed)
    rect(im, 0, 0, 15, 1, shade(c, 1.2)); rect(im, 0, 14, 15, 15, shade(c, 0.7))
    rect(im, 6, 0, 7, 15, band)
    return im

def cable_tex(seed):
    im = plain(rgb('#2b2b2e'), seed, 0.15)
    for x in range(16):
        dot(im, x, (x * 3) % 16, rgb('#3e3e44'))
    rect(im, 0, 7, 15, 8, rgb('#a83a2a'))
    return im

def wires_tex(seed):
    im = new((0, 0, 0, 0))
    r = random.Random(seed)
    cols = [rgb('#a83a2a'), rgb('#e0b030'), rgb('#2f6f9f'), rgb('#2b2b2e')]
    for k, c in enumerate(cols):
        y = 3 + k * 3
        for x in range(16):
            dot(im, x, y + (1 if (x + k) % 5 == 0 else 0), c)
    for x, y in ((5, 2), (11, 9), (2, 12)):
        dot(im, x, y, rgb('#ffffff')); dot(im, x + 1, y, rgb('#8fd0ff'))
    return im

def debris_tex(seed):
    im = new((0, 0, 0, 0))
    r = random.Random(seed)
    for _ in range(9):
        x, y = r.randint(0, 12), r.randint(0, 12)
        w, h = r.randint(2, 4), r.randint(1, 3)
        c = r.choice([rgb('#8a8f96'), rgb('#5c6066'), rgb('#3d4045'), rgb('#7a5a3a')])
        rect(im, x, y, x + w, y + h, c)
        dot(im, x, y, shade(c, 1.25))
    return im

def frost(p, seed):
    im = damaged(p, seed)
    px = im.load()
    r = random.Random(seed + 1)
    for y in range(16):
        for x in range(16):
            if r.random() < 0.35 or (x + y) % 7 == 0:
                px[x, y] = mix(px[x, y], rgb('#e8f6ff'), 0.6 + r.random() * 0.3)
    rect(im, 6, 6, 9, 9, rgb('#0a0c12'))
    dot(im, 7, 5, rgb('#0a0c12')); dot(im, 10, 8, rgb('#0a0c12'))
    return im

def ladder_tex(p):
    im = new((0, 0, 0, 0))
    rect(im, 1, 0, 2, 15, p['base']); rect(im, 13, 0, 14, 15, p['base'])
    dot_line = p['light']
    for y in (1, 5, 9, 13):
        rect(im, 1, y, 14, y + 1, p['base'])
        rect(im, 1, y, 14, y, dot_line)
    rect(im, 2, 0, 2, 15, p['dark']); rect(im, 14, 0, 14, 15, p['dark'])
    return im

def tray_tex(top=False):
    if top:
        im = new(rgb('#3a6a4a'))
        noise(im, 0.25, 5)
        for x in range(0, 16, 4):
            rect(im, x, 0, x, 15, rgb('#2a4a36'))
        bevel(im, 0, 0, 15, 15, rgb('#d8dde2'), rgb('#a6adb5'))
        return im
    p = FINISH['clean']
    im = plate(p, 3)
    rect(im, 1, 6, 14, 7, rgb('#4aa6e0'))
    return im

def door_tex(p, seed, half):
    im = plate(p, seed)
    rect(im, 7, 0, 8, 15, p['seam'])
    if half == 'upper':
        rect(im, 2, 3, 5, 9, p['glass'][:3] + (255,)); bevel(im, 1, 2, 6, 10, p['dark'], p['light'])
        rect(im, 10, 3, 13, 9, p['glass'][:3] + (255,)); bevel(im, 9, 2, 14, 10, p['dark'], p['light'])
    else:
        for x in range(1, 15):
            dot(im, x, 12, p['accent'] if (x // 2) % 2 == 0 else p['accent2'])
        rect(im, 5, 6, 5, 9, p['light']); rect(im, 10, 6, 10, 9, p['light'])
    return im

def edge_tex(p):
    im = new(p['dark'])
    for y in range(16):
        dot(im, 1, y, p['light'])
    for x in range(16):
        dot(im, x, 1, p['accent'] if (x // 2) % 2 == 0 else p['accent2'])
    return im

def shutter_tex(seed):
    p = FINISH['industrial']
    im = plate(p, seed)
    for y in (2, 6, 10, 14):
        rect(im, 1, y, 14, y, p['seam'])
        rect(im, 1, y - 1, 14, y - 1, p['light'])
    return im

def hatch_tex(p, seed):
    im = panel(p, seed)
    for x in range(3, 13):
        dot(im, x, 7, p['accent'] if (x // 2) % 2 == 0 else p['accent2'])
        dot(im, x, 8, p['accent2'] if (x // 2) % 2 == 0 else p['accent'])
    return im

def pod_glass():
    im = new((0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if (x + y) % 9 == 0 and 2 < x < 13:
                dot(im, x, y, rgb('#d8f0ff', 140))
    rect(im, 0, 0, 15, 0, rgb('#d8dde2')); rect(im, 0, 15, 15, 15, rgb('#a6adb5'))
    rect(im, 0, 0, 0, 15, rgb('#d8dde2')); rect(im, 15, 0, 15, 15, rgb('#a6adb5'))
    return im

def frost_glass():
    im = new(rgb('#bfe4f4', 90))
    r = random.Random(4)
    for _ in range(30):
        dot(im, r.randint(0, 15), r.randint(0, 15), rgb('#f4fbff', 170))
    rect(im, 0, 0, 15, 0, rgb('#d8dde2')); rect(im, 0, 15, 15, 15, rgb('#a6adb5'))
    rect(im, 0, 0, 0, 15, rgb('#d8dde2')); rect(im, 15, 0, 15, 15, rgb('#a6adb5'))
    return im

def locker_front(p, seed):
    im = plate(p, seed)
    for x0 in (1, 8):
        bevel(im, x0, 1, x0 + 6, 14, p['light'], p['dark'])
        for y in (3, 4, 5):
            rect(im, x0 + 2, y, x0 + 4, y, p['seam'])
        dot(im, x0 + 5, 9, p['accent'])
    return im

def crate_side(p, seed):
    im = plate(p, seed)
    rect(im, 1, 1, 14, 2, p['accent']); rect(im, 1, 13, 14, 14, p['accent'])
    for i in range(1, 15):
        dot(im, i, i, p['dark']); dot(im, 15 - i, i, p['dark'])
    return im

def med_front(seed):
    p = FINISH['clean']
    im = plate(p, seed)
    rect(im, 6, 3, 9, 12, rgb('#d8302a')); rect(im, 3, 6, 12, 9, rgb('#d8302a'))
    return im

def kit_item(body, cross):
    im = new((0, 0, 0, 0))
    rect(im, 2, 4, 13, 13, body)
    bevel(im, 2, 4, 13, 13, shade(body, 1.25), shade(body, 0.7))
    rect(im, 6, 2, 9, 4, shade(body, 0.7))
    rect(im, 7, 6, 8, 11, cross); rect(im, 5, 8, 10, 9, cross)
    return im

def wrench_item():
    im = new((0, 0, 0, 0))
    s = rgb('#b4b9bf'); d = rgb('#5c6066'); g = rgb('#e0b030')
    for i in range(3, 12):
        dot(im, i, 15 - i, s); dot(im, i + 1, 15 - i, d)
    rect(im, 10, 1, 14, 2, s); rect(im, 13, 1, 14, 5, s); rect(im, 10, 3, 11, 5, s)
    dot(im, 12, 3, (0, 0, 0, 0)); dot(im, 12, 4, (0, 0, 0, 0))
    rect(im, 2, 12, 4, 14, g); dot(im, 3, 13, shade(g, 0.7))
    return im

def finish_kit_item(c):
    im = new((0, 0, 0, 0))
    can = rgb('#8a8f96')
    rect(im, 3, 6, 12, 14, can)
    bevel(im, 3, 6, 12, 14, rgb('#b4b9bf'), rgb('#5c6066'))
    rect(im, 4, 8, 11, 11, c)
    rect(im, 3, 4, 12, 5, rgb('#5c6066'))
    rect(im, 6, 1, 9, 3, c)
    return im

def door_item(p, seed):
    im = new((0, 0, 0, 0))
    lo, up = door_tex(p, seed, 'lower'), door_tex(p, seed, 'upper')
    im.paste(up.resize((8, 8), Image.NEAREST), (4, 0))
    im.paste(lo.resize((8, 8), Image.NEAREST), (4, 8))
    return im

def skin(colours, seed, hair, slim=False):
    """A 64x64 player skin: hair, face, a jumpsuit with a coloured stripe, boots."""
    suit, stripe, skin_c = colours
    im = new(w=64, h=64)
    def fill(x, y, w, h, c):
        rect(im, x, y, x + w - 1, y + h - 1, c)
    # head: top, bottom, right, front, left, back
    fill(8, 0, 8, 8, hair); fill(16, 0, 8, 8, skin_c)
    fill(0, 8, 8, 8, skin_c); fill(8, 8, 8, 8, skin_c); fill(16, 8, 8, 8, skin_c); fill(24, 8, 8, 8, hair)
    fill(0, 8, 8, 3, hair); fill(16, 8, 8, 3, hair); fill(8, 8, 8, 2, hair)
    # closed eyes, asleep in the pod
    rect(im, 9, 12, 10, 12, shade(skin_c, 0.6)); rect(im, 13, 12, 14, 12, shade(skin_c, 0.6))
    rect(im, 11, 14, 12, 14, shade(skin_c, 0.8))
    aw = 3 if slim else 4
    def limb(x, y, c, cuff, w=4):
        fill(x + 4, y, w, 4, c); fill(x + 4 + w, y, w, 4, cuff)
        fill(x, y + 4, 4, 12, c); fill(x + 4, y + 4, w, 12, c); fill(x + 4 + w, y + 4, 4, 12, c); fill(x + 8 + w, y + 4, w, 12, c)
        fill(x, y + 13, 16 if w == 4 else 14, 3, cuff)
    boots = rgb('#2b2b2e')
    limb(0, 16, suit, boots); limb(16, 48, suit, boots)          # legs
    limb(40, 16, suit, skin_c, aw); limb(32, 48, suit, skin_c, aw)  # arms, hands at the cuff
    # body
    fill(20, 16, 8, 4, suit); fill(28, 16, 8, 4, suit)
    fill(16, 20, 4, 12, suit); fill(20, 20, 8, 12, suit); fill(28, 20, 4, 12, suit); fill(32, 20, 8, 12, suit)
    fill(20, 22, 8, 1, stripe); fill(32, 22, 8, 1, stripe)
    fill(23, 20, 2, 12, shade(suit, 0.85))
    dot(im, 21, 24, stripe); dot(im, 22, 24, stripe)
    noise(im, 0.06, seed)
    return im
