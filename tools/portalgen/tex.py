"""Placeholder textures for the 2.3 portal core. Most are recolours of existing Simple Machines or vanilla art,
so they sit with the house style; the rest are drawn by hand in the same flat, bevelled way."""
import colorsys, math, random
from PIL import Image

SM = '/workspace/mods/sm-multi/src/main/resources/assets/simplemachines/textures/'
MC = '/tmp/src2612/assets/minecraft/textures/'

def load(path):
    return Image.open(path).convert('RGBA')

def lum(c):
    return (0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]) / 255.0

def recolor(im, ramp, mask=None):
    """Maps each pixel's brightness onto a colour ramp (dark to light). mask(x, y, c) picks which pixels change."""
    out = im.copy()
    px = out.load()
    src = im.load()
    for y in range(im.height):
        for x in range(im.width):
            c = src[x, y]
            if c[3] == 0 or (mask and not mask(x, y, c)):
                continue
            t = lum(c) * (len(ramp) - 1)
            i = min(int(t), len(ramp) - 2)
            f = t - i
            a, b = ramp[i], ramp[i + 1]
            px[x, y] = tuple(int(a[k] + (b[k] - a[k]) * f) for k in range(3)) + (c[3],)
    return out

def hexc(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))

def ramp(*hexes):
    return [hexc(h) for h in hexes]

TITANIUM = ramp('#2c3440', '#5b6878', '#8e9cad', '#bccad8', '#e6eef6', '#ffffff')
TITANIUM_BLOCK = ramp('#3a4450', '#5f6c7c', '#8494a6', '#a6b6c6', '#c4d2de', '#e2ebf2')
TITANIUM_ORE = ramp('#24344a', '#46628a', '#7494bc', '#a2c0dc', '#d0e2f2', '#f0f8ff')
STEEL_BLOCK = ramp('#1f2429', '#3f474f', '#5d6770', '#7f8a94', '#a7b1ba', '#d0d8de')
ENTANGLED = ramp('#1c0b2e', '#3e1a6b', '#6a2fb0', '#9a5ce0', '#c99cff', '#f0e2ff')
GOLD = ramp('#3b2405', '#7a4e0e', '#b98322', '#e5b84a', '#ffe48a', '#fff6d0')
NETHER = ramp('#2a0606', '#5c1010', '#922018', '#c84a2a', '#f08a4a', '#ffd2a0')
END = ramp('#0f1a12', '#23402a', '#3f7a4c', '#7fb87a', '#d6e8a0', '#fbffe0')
TEAL = ramp('#06232a', '#0d4a55', '#16808e', '#36b8c2', '#8ae6e6', '#e0ffff')

def ore(stone_name, ore_name):
    """Keeps the stone and recolours the ore specks titanium."""
    stone = load(MC + 'block/' + stone_name)
    o = load(MC + 'block/' + ore_name)
    sp = stone.load()
    return recolor(o, TITANIUM_ORE, lambda x, y, c: sum(abs(c[k] - sp[x, y][k]) for k in range(3)) > 30)

def pellet():
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 8.5)
            if d < 4.6:
                t = max(0.0, min(1.0, 1 - d / 4.6 + (7.5 - x + 8.5 - y) * 0.05))
                px[x, y] = tuple(int(c) for c in ENTANGLED[min(5, 1 + int(t * 4))]) + (255,)
            elif d < 5.4:
                px[x, y] = ENTANGLED[0] + (255,)
    px[6, 6] = (255, 255, 255, 255)
    return im

def scanner():
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    body, light, dark = hexc('#5d6770'), hexc('#a7b1ba'), hexc('#2c3238')
    for y in range(4, 15):
        for x in range(3, 11):
            px[x, y] = (light if x == 3 or y == 4 else dark if x == 10 or y == 14 else body) + (255,)
    for y in range(6, 10):
        for x in range(5, 9):
            px[x, y] = hexc('#36b8c2' if (x + y) % 3 else '#8ae6e6') + (255,)
    for y in range(2, 6):
        px[11, y] = dark + (255,)
        px[12, y] = light + (255,)
    px[12, 1] = hexc('#ff5252') + (255,)
    px[5, 12] = hexc('#43a047') + (255,)
    px[7, 12] = hexc('#ffd54f') + (255,)
    return im

def machine_face(base, glyph_ramp, lit, seed, kind):
    """A Simple Machines style machine face: the core side texture with a framed window."""
    im = base.copy()
    px = im.load()
    r = random.Random(seed)
    for y in range(3, 13):
        for x in range(3, 13):
            edge = x in (3, 12) or y in (3, 12)
            if edge:
                px[x, y] = (40, 44, 50, 255)
                continue
            if kind == 'gate':
                d = math.hypot(x - 7.5, y - 7.5)
                a = math.atan2(y - 7.5, x - 7.5)
                t = (math.sin(a * 3 + d * 1.3) + 1) / 2
                t = t * (1.0 if lit else 0.35)
            else:
                t = (0.25 + 0.6 * ((x * 3 + y * 5 + r.randint(0, 3)) % 7) / 7) * (1.0 if lit else 0.4)
                if kind == 'research' and y in (6, 9) and 4 < x < 11:
                    t = 0.95 if lit else 0.5
            px[x, y] = glyph_ramp[min(5, int(t * 5.99))] + (255,)
    return im

def field_frames(n=8):
    im = Image.new('RGBA', (16, 16 * n))
    px = im.load()
    for f in range(n):
        for y in range(16):
            for x in range(16):
                v = math.sin((x + f * 2) * 0.7) + math.sin((y - f) * 0.6) + math.sin((x + y + f * 3) * 0.35)
                t = (v + 3) / 6
                c = ENTANGLED[1 + int(t * 3.99)]
                px[x, f * 16 + y] = c + (int(120 + t * 90),)
    return im

def crystal(spent):
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    rp = ramp('#1a1a22', '#2b2d3a', '#3a3e52', '#4b5068', '#5c6280', '#6e7598') if spent else TEAL
    base = hexc('#3a3e45')
    for y in range(16):
        for x in range(16):
            px[x, y] = (base if (x + y) % 5 else hexc('#30343a')) + (255,)
    for y in range(2, 14):
        w = 5 - abs(y - 8) * 5 // 6
        for x in range(8 - w, 8 + w):
            t = 0.4 + 0.6 * (1 - abs(x - 7.5) / max(1, w)) - (y - 2) * 0.02
            px[x, y] = rp[max(0, min(5, int(t * 5.99)))] + (255,)
    for x in range(16):
        px[x, 0] = px[x, 15] = hexc('#22262b') + (255,)
    for y in range(16):
        px[0, y] = px[15, y] = hexc('#22262b') + (255,)
    return im

def beacon_tex():
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            px[x, y] = hexc('#2c3238' if edge else ('#5d6770' if (x // 4 + y // 4) % 2 else '#7f8a94')) + (255,)
    for y in range(3, 13):
        for x in range(6, 10):
            px[x, y] = hexc('#8ae6e6' if (y % 3) else '#e0ffff') + (255,)
    return im
