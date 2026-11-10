#!/usr/bin/env python3
"""Generates the Simple Machines 2.3 ship blocks: ShipBlocks.java, blockstates, models, item models,
textures, lang, recipes, loot tables, tags and manual pages, all under src/ship (26.1.2 only).

Run from the repo root: python3 tools/shipgen/gen.py
Hand-written Java lives in src/ship/java/.../ship. Textures are placeholders in the house style;
repaint any PNG and it will not be overwritten unless you pass --textures."""
import json, os, sys, shutil
sys.path.insert(0, os.path.dirname(__file__))
import tex as T
from tex import FINISH

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(ROOT, 'src/ship/resources')
A = os.path.join(RES, 'assets/simplemachines')
D = os.path.join(RES, 'data/simplemachines')
JAVA = os.path.join(ROOT, 'src/ship/java/com/workshopcraft/simplemachines/ship/ShipBlocks.java')
WRITE_TEX = '--textures' in sys.argv or not os.path.isdir(os.path.join(A, 'textures/block'))
NS = 'simplemachines'

def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        if isinstance(data, str):
            f.write(data)
        else:
            json.dump(data, f, indent=2)
            f.write('\n')

TEXTURES = {}   # name -> (PIL image, frametime or None)
def png(name, im, frametime=None):
    TEXTURES[name] = (im, frametime)
    return f'{NS}:{name}'

def fail_dash(s):
    assert '\u2014' not in s and '\u2013' not in s, s
    return s

BLOCKS = []
LANG = {}
RECIPES = {}
def block(name, en, java, model, *, item='block', loot='self', tags=('pickaxe',), recipe=None, tab=True, props='metal'):
    b = dict(name=name, en=fail_dash(en), java=java, model=model, item=item, loot=loot, tags=set(tags), tab=tab, props=props)
    BLOCKS.append(b)
    if recipe:
        RECIPES[name] = recipe
    return b

# ---------- model helpers ----------
FACES = ('north', 'south', 'east', 'west', 'up', 'down')

def el(frm, to, tex, faces=FACES, shade=True, light=None, rot=None, uv=None):
    """tex: '#var' or dict face -> '#var' (missing faces fall back to the 'all' key, or are left out)."""
    e = {'from': list(frm), 'to': list(to), 'faces': {}}
    for f in faces:
        t = tex if isinstance(tex, str) else tex.get(f, tex.get('all'))
        if t is None:
            continue
        face = {'texture': t}
        if uv and f in uv:
            face['uv'] = uv[f]
        elif min(frm + to) < 0 or max(frm + to) > 16:
            face['uv'] = [0, 0, 16, 16]  # auto UVs fall outside the texture for elements past the block
        e['faces'][f] = face
    if not shade:
        e['shade'] = False
    if light:
        e['light_emission'] = light
    if rot:
        e['rotation'] = rot
    return e

def culled(els):
    """Adds cullface to every face that lies on the block's outside, so a solid neighbour hides it instead of z-fighting
    with it (these blocks don't occlude, so the neighbour draws its own face there too)."""
    for e in els:
        for f, face in e['faces'].items():
            on = {'down': e['from'][1] == 0, 'up': e['to'][1] == 16, 'north': e['from'][2] == 0, 'south': e['to'][2] == 16,
                  'west': e['from'][0] == 0, 'east': e['to'][0] == 16}[f]
            if on: face['cullface'] = f
    return els

MODELS = {}
def model(name, parent=None, textures=None, elements=None, ao=True):
    m = {}
    if parent:
        m['parent'] = parent
    if textures:
        m['textures'] = textures
    if elements is not None:
        m['elements'] = elements
    if not ao:
        m['ambientocclusion'] = False
    MODELS[name] = m
    return f'{NS}:block/{name}'

def cube_all(name, texname):
    return model(name, 'minecraft:block/cube_all', {'all': f'{NS}:block/{texname}'})

def shaped(name, textures, elements, ao=True):
    t = dict(textures)
    t.setdefault('particle', t.get('side', next(iter(t.values()))))
    return model(name, 'minecraft:block/block', t, elements, ao)

def tex_ref(n):
    return f'{NS}:block/{n}'

# blockstates
STATES = {}

# connected glass: per face, which way is the top, bottom, left and right edge of the texture
GLASS_EDGES = {
    'north': ('up', 'down', 'east', 'west'), 'south': ('up', 'down', 'west', 'east'),
    'east': ('up', 'down', 'south', 'north'), 'west': ('up', 'down', 'north', 'south'),
    'up': ('north', 'south', 'west', 'east'), 'down': ('south', 'north', 'west', 'east'),
}

def _glass_quad(face, u0, v0, u1, v1):
    """from/to of a flat element on the block face covering texture pixels u0..u1, v0..v1 (default UV layout)."""
    return {
        'north': ([16 - u1, 16 - v1, 0], [16 - u0, 16 - v0, 0]),
        'south': ([u0, 16 - v1, 16], [u1, 16 - v0, 16]),
        'east': ([16, 16 - v1, 16 - u1], [16, 16 - v0, 16 - u0]),
        'west': ([0, 16 - v1, u0], [0, 16 - v0, u1]),
        'up': ([u0, 16, v0], [u1, 16, v1]),
        'down': ([u0, 0, 16 - v1], [u1, 0, 16 - v0]),
    }[face]

def connected_glass(name, b):
    """Multipart glass that joins with itself. Textures: block/<name> (framed, also the item) and block/<name>_pane
    (no frame). Each face is cut into a 3 by 3 grid with a b pixel frame; a frame strip is drawn only on the edges whose
    side is not joined, from the framed texture, and everything else comes from the pane."""
    cube_all(name, name)  # the item and the look of a lone block
    tex = {'frame': tex_ref(name), 'pane': tex_ref(name + '_pane'), 'particle': tex_ref(name)}
    cuts = [(0, b), (b, 16 - b), (16 - b, 16)]
    parts = []
    for face, (top, bot, left, right) in GLASS_EDGES.items():
        for mask in range(16):
            j = {'t': bool(mask & 1), 'b': bool(mask & 2), 'l': bool(mask & 4), 'r': bool(mask & 8)}  # joined
            els = []
            for ci, (v0, v1) in enumerate(cuts):
                for cj, (u0, u1) in enumerate(cuts):
                    fu = (cj == 0 and not j['l']) or (cj == 2 and not j['r'])   # a frame column here
                    fv = (ci == 0 and not j['t']) or (ci == 2 and not j['b'])   # a frame row here
                    uv = [u0, v0, u1, v1]
                    if fu and fv:
                        t = '#frame'
                    elif fu:
                        # a frame running along u's side: take it from the middle of that frame strip
                        t = '#frame'
                        if ci != 1: uv = [u0, b, u1, 2 * b] if ci == 0 else [u0, 16 - 2 * b, u1, 16 - b]
                    elif fv:
                        t = '#frame'
                        if cj != 1: uv = [b, v0, 2 * b, v1] if cj == 0 else [16 - 2 * b, v0, 16 - b, v1]
                    else:
                        t = '#pane'
                    frm, to = _glass_quad(face, u0, v0, u1, v1)
                    els.append({'from': frm, 'to': to, 'faces': {face: {'texture': t, 'uv': uv, 'cullface': face}}})
            m = model(f'glass/{name}/{face}_{mask:x}', 'minecraft:block/block', tex, els)
            # a joined face is never seen (skipped like glass), so it needs no part at all
            parts.append({'when': {top: str(j['t']).lower(), bot: str(j['b']).lower(), left: str(j['l']).lower(),
                                  right: str(j['r']).lower(), face: 'false'}, 'apply': {'model': m}})
    STATES[name] = {'multipart': parts}
HOR = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
ATT = {'up': (0, 0), 'down': (180, 0), 'north': (90, 0), 'south': (90, 180), 'east': (90, 90), 'west': (90, 270)}

def v(m, x=0, y=0):
    d = {'model': m}
    if x: d['x'] = x
    if y: d['y'] = y
    return d

def st_single(name, m):
    STATES[name] = {'variants': {'': v(m)}}

def st_hor(name, m, extra=''):
    STATES[name] = {'variants': {f'facing={f}{extra}': v(m, y=r) for f, r in HOR.items()}}

def st_att(name, m):
    STATES[name] = {'variants': {f'facing={f}': v(m, x=a[0], y=a[1]) for f, a in ATT.items()}}

def st_axis(name, m):
    STATES[name] = {'variants': {'axis=y': v(m), 'axis=z': v(m, x=90), 'axis=x': v(m, x=90, y=90)}}

# ---------- java helpers ----------
def boxes(bs):
    return 'new double[][]{' + ', '.join('{' + ', '.join(f'{float(x):g}' for x in b) + '}' for b in bs) + '}'

def B(e):
    """Model elements from boxes: [(from..to, texdict)] -> elements, and the plain box list for the shape."""
    return [b for b, _ in e]

# ================= SPEC =================
P = {f: FINISH[f] for f in FINISH}
FIN_EN = {'ancient': 'Ancient', 'industrial': 'Industrial', 'clean': 'Clean'}
TOKEN = {'ancient': '#c:plates/copper', 'industrial': '#c:plates/steel', 'clean': 'minecraft:white_dye'}

def shaped_recipe(pattern, key, count=1, out=None):
    return {'type': 'shaped', 'pattern': pattern, 'key': key, 'count': count, 'out': out}
def shapeless(items, count=1):
    return {'type': 'shapeless', 'items': items, 'count': count}
def cut(src, count=1):
    return {'type': 'stonecut', 'from': src, 'count': count}

REPAIRS = []
seed = [100]
def nseed():
    seed[0] += 7
    return seed[0]

for f in FINISH:
    p = P[f]
    E = FIN_EN[f]
    pl = f'{f}_plating'
    png(f'block/{f}_plating', T.plating(p, nseed()))
    png(f'block/{f}_panel', T.panel(p, nseed()))
    png(f'block/{f}_grate', T.grate(p, nseed()))
    png(f'block/{f}_window', T.window(p, nseed()))
    png(f'block/{f}_light', T.lamp(p, nseed()))
    png(f'block/{f}_hull', T.hull(p, nseed()))
    png(f'block/{f}_bulkhead', T.bulkhead(p, nseed()))
    png(f'block/{f}_panel_vent', T.vent(p, nseed()))
    png(f'block/{f}_panel_damaged', T.damaged(p, nseed()))
    png(f'block/{f}_panel_scorched', T.scorched(p, nseed()))
    png(f'block/{f}_porthole', T.porthole(p, nseed()))
    s = nseed()
    png(f'block/{f}_sliding_door_lower', T.door_tex(p, s, 'lower'))
    png(f'block/{f}_sliding_door_upper', T.door_tex(p, s, 'upper'))
    png(f'block/{f}_sliding_door_edge', T.edge_tex(p))
    png(f'item/{f}_sliding_door', T.door_item(p, s))

    for n in ('plating', 'panel', 'panel_vent', 'panel_damaged', 'panel_scorched'):
        name = f'{f}_{n}'
        st_single(name, cube_all(name, name))
    block(pl, f'{E} Plating', 'new Block({p})', None,
          recipe=shaped_recipe(['PPP', 'PXP', 'PPP'], {'P': '#c:plates/iron', 'X': TOKEN[f]}, 8))
    block(f'{f}_panel', f'{E} Panel', 'new Block({p})', None, recipe=cut(pl))
    block(f'{f}_panel_vent', f'{E} Vent Panel', 'new Block({p})', None, recipe=cut(pl))
    block(f'{f}_panel_damaged', f'{E} Damaged Panel', 'new Block({p})', None, recipe=cut(pl))
    block(f'{f}_panel_scorched', f'{E} Scorched Panel', 'new Block({p})', None, recipe=cut(pl))
    st_single(f'{f}_grate', cube_all(f'{f}_grate', f'{f}_grate'))
    block(f'{f}_grate', f'{E} Grate', 'new ShipGlassBlock({p})', None, props='open', recipe=cut(pl))
    png(f'block/{f}_window_pane', T.window_pane(p))
    connected_glass(f'{f}_window', 2)
    block(f'{f}_window', f'{E} Window', 'new ConnectedGlassBlock({p})', None, props='glass',
          recipe=shaped_recipe(['PGP', 'GGG', 'PGP'], {'P': f'{NS}:{pl}', 'G': '#c:glass_blocks'}, 6))
    st_single(f'{f}_porthole', cube_all(f'{f}_porthole', f'{f}_porthole'))
    block(f'{f}_porthole', f'{E} Porthole', 'new Block({p})', None, props='glass',
          recipe=shaped_recipe(['PPP', 'PGP', 'PPP'], {'P': f'{NS}:{pl}', 'G': '#c:glass_blocks'}, 4))
    st_single(f'{f}_light', cube_all(f'{f}_light', f'{f}_light'))
    block(f'{f}_light', f'{E} Light', 'new Block({p}.lightLevel(s -> 15))', None,
          recipe=shaped_recipe(['PGP', 'GRG', 'PGP'], {'P': f'{NS}:{pl}', 'G': '#c:dusts/glowstone', 'R': '#c:dusts/redstone'}, 4))
    st_single(f'{f}_hull', cube_all(f'{f}_hull', f'{f}_hull'))
    block(f'{f}_hull', f'{E} Hull', 'new HullBlock({p})', None, props='hull', loot='none', tags=('hull',))
    st_single(f'{f}_bulkhead', cube_all(f'{f}_bulkhead', f'{f}_bulkhead'))
    block(f'{f}_bulkhead', f'{E} Bulkhead', 'new BulkheadBlock({p})', None, props='bulkhead', tags=('pickaxe', 'bulkhead'),
          recipe=shaped_recipe(['PRP', 'PRP', 'PRP'], {'P': f'{NS}:{pl}', 'R': '#c:rods/steel'}, 6))
    # sliding door: drawn by the renderer
    model(f'{f}_sliding_door', None, {'particle': tex_ref(f'{f}_sliding_door_lower')})
    STATES[f'{f}_sliding_door'] = {'variants': {'': v(f'{NS}:block/{f}_sliding_door')}}
    block(f'{f}_sliding_door', f'{E} Sliding Door', 'new SlidingDoorBlock({p}, SlideBlock.Kind.DOOR)', None, props='door',
          item='door', loot='lower', recipe=shaped_recipe(['PP', 'PR', 'PP'], {'P': f'{NS}:{pl}', 'R': '#c:dusts/redstone'}, 2))
    REPAIRS.append((f'{f}_panel_damaged', f'{f}_panel'))
    REPAIRS.append((f'{f}_panel_scorched', f'{f}_panel'))

I = P['industrial']
IND = f'{NS}:industrial_plating'

# hull boundary and hull glass
png('block/hull_boundary', T.boundary(5))
png('block/hull_glass', T.hull_glass(6))
st_single('hull_boundary', cube_all('hull_boundary', 'hull_boundary'))
png('block/hull_glass_pane', T.hull_glass_pane())
connected_glass('hull_glass', 1)
block('hull_boundary', 'Hull Boundary', 'new HullBlock({p})', None, props='hull', loot='none', tags=('hull',))
block('hull_glass', 'Hull Glass', 'new HullGlassBlock({p})', None, props='hull_glass', loot='none', tags=('hull',))

# special doors
def door_set(name, en, palette, accent, kind, recipe, upper_mark=None):
    pp = dict(palette); pp['accent'] = accent
    s = nseed()
    lo = T.door_tex(pp, s, 'lower'); up = T.door_tex(pp, s, 'upper')
    if upper_mark:
        T.rect(up, 7, 12, 8, 13, upper_mark)
    png(f'block/{name}_lower', lo); png(f'block/{name}_upper', up)
    png(f'block/{name}_edge', T.edge_tex(pp))
    png(f'item/{name}', T.door_item(pp, s))
    model(name, None, {'particle': tex_ref(f'{name}_lower')})
    STATES[name] = {'variants': {'': v(f'{NS}:block/{name}')}}
    block(name, en, f'new SlidingDoorBlock({{p}}, SlideBlock.Kind.{kind})', None, props='door', item='door', loot='lower', recipe=recipe)

door_set('airlock_door', 'Airlock Door', I, T.rgb('#d8302a'), 'AIRLOCK',
         shapeless([f'{NS}:industrial_sliding_door', 'minecraft:comparator']), T.rgb('#5fe07a'))
door_set('locked_door', 'Locked Door', I, T.rgb('#d8302a'), 'LOCKED',
         shapeless([f'{NS}:industrial_sliding_door', 'minecraft:tripwire_hook', '#c:plates/steel']), T.rgb('#ff4040'))

png('block/sliding_hatch', T.hatch_tex(I, nseed()))
png('block/sliding_hatch_edge', T.edge_tex(I))
png('item/sliding_hatch', T.hatch_tex(I, 3))
model('sliding_hatch', None, {'particle': tex_ref('sliding_hatch')})
STATES['sliding_hatch'] = {'variants': {'': v(f'{NS}:block/sliding_hatch')}}
block('sliding_hatch', 'Sliding Hatch', 'new SlidingPanelBlock({p}, SlideBlock.Kind.HATCH)', None, props='door', item='flat',
      recipe=shaped_recipe(['PPP', 'PRP'], {'P': IND, 'R': '#c:dusts/redstone'}, 3))
png('block/blast_shutter', T.shutter_tex(nseed()))
png('block/blast_shutter_edge', T.edge_tex(I))
png('item/blast_shutter', T.shutter_tex(9))
model('blast_shutter', None, {'particle': tex_ref('blast_shutter')})
STATES['blast_shutter'] = {'variants': {'': v(f'{NS}:block/blast_shutter')}}
block('blast_shutter', 'Blast Shutter', 'new SlidingPanelBlock({p}, SlideBlock.Kind.SHUTTER)', None, props='shutter', item='flat',
      recipe=shaped_recipe(['SSS', 'SRS', 'SSS'], {'S': '#c:plates/steel', 'R': '#c:dusts/redstone'}, 4))

# ---- cryopods ----
C = P['clean']
png('block/cryopod_shell', T.panel(C, 21))
png('block/cryopod_glass', T.pod_glass())
png('block/cryopod_frost_glass', T.frost_glass())
png('block/cryopod_base', T.plate(C, 22))
cons = T.screen_base(C, 23)
for x, y, c in ((4, 5, '#5fe07a'), (7, 5, '#4aa6e0'), (10, 5, '#e0b030'), (4, 9, '#4aa6e0')):
    T.rect(cons, x, y, x + 1, y + 1, T.rgb(c))
png('block/cryopod_console', cons)
png('block/cryopod_bed_top', T.frost_glass())
png('item/cryopod_bed', T.panel(C, 24))
png('item/cryopod', T.door_item(C, 25))

sh = {'all': '#shell'}
# cryopod bed: head is north. Tub, mattress, canopy glass, console at the head end.
head = [
    el([0, 0, 0], [16, 6, 16], {'all': '#shell', 'up': '#base'}),
    el([0, 6, 0], [16, 12, 2], {'all': '#console'}),
    el([0, 6, 2], [1, 10, 16], sh), el([15, 6, 2], [16, 10, 16], sh),
    el([1, 6, 2], [15, 7, 16], {'up': '#base'}, faces=('up',)),
    el([1, 10, 2], [15, 10.01, 16], {'all': '#glass'}, faces=('up', 'down')),
]
foot = [
    el([0, 0, 0], [16, 6, 16], {'all': '#shell', 'up': '#base'}),
    el([0, 6, 0], [1, 10, 14], sh), el([15, 6, 0], [16, 10, 14], sh),
    el([0, 6, 14], [16, 10, 16], sh),
    el([1, 6, 0], [15, 7, 14], {'up': '#base'}, faces=('up',)),
    el([1, 10, 0], [15, 10.01, 14], {'all': '#glass'}, faces=('up', 'down')),
]
ctex = {'shell': tex_ref('cryopod_shell'), 'base': tex_ref('cryopod_base'), 'glass': tex_ref('cryopod_bed_top'),
        'console': tex_ref('cryopod_console'), 'particle': tex_ref('cryopod_shell')}
model('cryopod_bed_head', 'minecraft:block/block', ctex, head)
model('cryopod_bed_foot', 'minecraft:block/block', ctex, foot)
STATES['cryopod_bed'] = {'variants': {}}
for fdir, r in HOR.items():
    for part in ('head', 'foot'):
        STATES['cryopod_bed']['variants'][f'facing={fdir},part={part}'] = v(f'{NS}:block/cryopod_bed_{part}', y=r)
block('cryopod_bed', 'Cryopod Bed', 'new CryopodBedBlock({p})', None, props='pod', item='bed', loot='head',
      recipe=shaped_recipe(['GGG', 'PWP', 'PPP'], {'G': '#c:glass_blocks', 'W': '#minecraft:wool', 'P': f'{NS}:clean_plating'}))

# decoration cryopod: two tall, front (north) is glass, the sleeper is drawn by CryopodRenderer
low = [
    el([0, 0, 0], [16, 2, 16], {'all': '#shell', 'up': '#base'}),
    el([0, 2, 14], [16, 16, 16], sh), el([0, 2, 0], [2, 16, 14], sh), el([14, 2, 0], [16, 16, 14], sh),
    el([2, 2, 1], [14, 16, 1.5], {'all': '#glass'}, faces=('north', 'south')),
    el([4, 3, 14], [12, 9, 14], {'north': '#console'}, faces=('north',)),
]
upp = [
    el([0, 14, 0], [16, 16, 16], sh),
    el([0, 0, 14], [16, 14, 16], sh), el([0, 0, 0], [2, 14, 14], sh), el([14, 0, 0], [16, 14, 14], sh),
    el([2, 0, 1], [14, 14, 1.5], {'all': '#glass'}, faces=('north', 'south')),
]
ptex = dict(ctex); ptex['glass'] = tex_ref('cryopod_glass')
model('cryopod_lower', 'minecraft:block/block', ptex, culled(low))
model('cryopod_upper', 'minecraft:block/block', ptex, culled(upp))
STATES['cryopod'] = {'variants': {}}
for fdir, r in HOR.items():
    for half in ('lower', 'upper'):
        STATES['cryopod']['variants'][f'facing={fdir},half={half}'] = v(f'{NS}:block/cryopod_{half}', y=r)
block('cryopod', 'Cryopod', 'new CryopodBlock({p})', None, props='pod', item='door', loot='lower',
      recipe=shaped_recipe(['PGP', 'PGP', 'PCP'], {'P': f'{NS}:clean_plating', 'G': '#c:glass_blocks', 'C': f'{NS}:control_board'}))

# ---- control panels and screens ----
def anim(name, n, fn, ft=4):
    png(f'block/{name}', T.frames(n, fn), ft)

png('block/console_side', T.panel(I, 31))
cf = T.plate(I, 32)
for i, c in enumerate(('#d8302a', '#e0b030', '#5fe07a', '#4aa6e0')):
    T.rect(cf, 2 + i * 3, 4, 3 + i * 3, 5, T.rgb(c))
T.rect(cf, 2, 9, 13, 9, I['seam'])
png('block/console_front', cf)
anim('control_panel_nav_screen', 8, lambda i: T.control_nav(I, 33, i))
anim('control_panel_engineering_screen', 8, lambda i: T.control_eng(I, 34, i))
anim('control_panel_reactor_screen', 8, lambda i: T.control_reactor(I, 35, i), 3)
png('block/broken_control_panel_screen', T.broken_screen(I, 36))

CONSOLE = [[0, 0, 3, 16, 9, 16], [0, 9, 6, 16, 12, 16]]
def console(name, en, screen, recipe, light=True):
    els = [
        el([0, 0, 3], [16, 9, 16], {'all': '#side', 'north': '#front'}),
        el([0, 9, 6], [16, 11, 16], {'all': '#side'}),
        el([1, 10.5, 6.5], [15, 11.5, 15.5], {'all': '#side', 'up': '#screen'}, light=10 if light else None,
           rot={'origin': [8, 11, 11], 'axis': 'x', 'angle': 22.5}),
    ]
    m = shaped(name, {'side': tex_ref('console_side'), 'front': tex_ref('console_front'), 'screen': tex_ref(screen)}, els)
    st_hor(name, m)
    block(name, en, f'new ShapedBlock({{p}}, {boxes(CONSOLE)})', None, props='deco', recipe=recipe)

console('control_panel_nav', 'Navigation Control Panel', 'control_panel_nav_screen',
        shaped_recipe(['GGG', 'RBR', 'PPP'], {'G': '#c:glass_panes', 'R': '#c:dusts/redstone', 'B': f'{NS}:control_board', 'P': IND}))
console('control_panel_engineering', 'Engineering Control Panel', 'control_panel_engineering_screen', cut(f'{NS}:control_panel_nav'))
console('control_panel_reactor', 'Reactor Control Panel', 'control_panel_reactor_screen', cut(f'{NS}:control_panel_nav'))
console('broken_control_panel', 'Broken Control Panel', 'broken_control_panel_screen',
        shapeless([f'{NS}:control_panel_nav', 'minecraft:gunpowder']), light=False)
REPAIRS.append(('broken_control_panel', 'control_panel_nav'))

png('block/screen_frame', T.plate(I, 41))
anim('wall_screen_star_map_screen', 16, T.star_map, 6)
anim('wall_screen_status_screen', 8, T.status, 8)
png('block/broken_wall_screen_screen', T.broken_screen(I, 42))
WALL = [[1, 1, 14, 15, 15, 16]]
def wall_screen(name, en, screen, recipe, light=True):
    els = [el([1, 1, 14], [15, 15, 16], {'all': '#side', 'north': '#screen'})]
    if light:
        els = [el([1, 1, 14.5], [15, 15, 16], {'all': '#side'}),
               el([1, 1, 14], [15, 15, 14.5], {'north': '#screen'}, faces=('north',), light=12)]
    m = shaped(name, {'side': tex_ref('screen_frame'), 'screen': tex_ref(screen)}, els)
    st_hor(name, m)
    block(name, en, f'new ShapedBlock({{p}}, {boxes(WALL)})', None, props='deco', recipe=recipe)

wall_screen('wall_screen_status', 'Status Wall Screen', 'wall_screen_status_screen',
            shaped_recipe(['GGG', 'PBP'], {'G': '#c:glass_panes', 'P': '#c:plates/iron', 'B': f'{NS}:control_board'}, 2))
wall_screen('wall_screen_star_map', 'Star Map Wall Screen', 'wall_screen_star_map_screen', cut(f'{NS}:wall_screen_status'))
wall_screen('broken_wall_screen', 'Broken Wall Screen', 'broken_wall_screen_screen',
            shapeless([f'{NS}:wall_screen_status', 'minecraft:gunpowder']), light=False)
REPAIRS.append(('broken_wall_screen', 'wall_screen_status'))

# jump countdown display: the numbers are drawn by CountdownRenderer on the face at z 13.5
cd = T.plate(I, 43)
T.rect(cd, 1, 2, 14, 13, T.rgb('#0c1418'))
T.bevel(cd, 0, 1, 15, 14, I['dark'], I['light'])
png('block/jump_countdown_display_front', cd)
m = shaped('jump_countdown_display', {'side': tex_ref('screen_frame'), 'front': tex_ref('jump_countdown_display_front')},
           [el([0, 2, 13.5], [16, 14, 16], {'all': '#side', 'north': '#front'})])
st_hor('jump_countdown_display', m)
block('jump_countdown_display', 'Jump Countdown Display', f'new CountdownBlock({{p}}, {boxes([[0, 2, 13.5, 16, 14, 16]])})', None,
      props='deco', recipe=cut(f'{NS}:wall_screen_status'))

# story log terminal: text drawn by TerminalRenderer on the face at z 3, x 1.5 to 14.5, y 3.5 to 14
tf = T.plate(I, 44)
T.rect(tf, 1, 1, 14, 13, T.rgb('#0c1418'))
T.bevel(tf, 0, 0, 15, 14, I['dark'], I['light'])
png('block/story_log_terminal_front', tf)
kb = T.plate(I, 45)
for y in (4, 8, 11):
    for x in range(2, 14, 2):
        T.dot(kb, x, y, I['seam'])
png('block/story_log_terminal_keys', kb)
m = shaped('story_log_terminal', {'side': tex_ref('console_side'), 'front': tex_ref('story_log_terminal_front'),
                                  'keys': tex_ref('story_log_terminal_keys')},
           [el([0, 0, 3], [16, 16, 16], {'all': '#side', 'north': '#front'}),
            el([1, 0, 0], [15, 2.5, 3], {'all': '#side', 'up': '#keys'})])
st_hor('story_log_terminal', m)
block('story_log_terminal', 'Story Log Terminal', f'new TerminalBlock({{p}}, {boxes([[0, 0, 3, 16, 16, 16], [1, 0, 0, 15, 2.5, 3]])})', None,
      props='deco', recipe=shaped_recipe(['PGP', 'PBP', 'PKP'], {'P': IND, 'G': '#c:glass_panes', 'B': f'{NS}:control_board', 'K': 'minecraft:book'}))

# holo table
anim('holo_table_projection', 8, T.holo, 3)
ht = T.panel(I, 46)
T.rect(ht, 4, 4, 11, 11, T.rgb('#1e5f80')); T.rect(ht, 6, 6, 9, 9, T.rgb('#60e0ff'))
png('block/holo_table_top', ht)
m = shaped('holo_table', {'side': tex_ref('console_side'), 'top': tex_ref('holo_table_top'), 'holo': tex_ref('holo_table_projection')},
           [el([2, 0, 2], [14, 12, 14], {'all': '#side'}),
            el([0, 12, 0], [16, 14, 16], {'all': '#side', 'up': '#top'}),
            el([1, 20, 1], [15, 20, 15], {'all': '#holo'}, faces=('up', 'down'), shade=False, light=15),
            el([8, 14, 1], [8, 28, 15], {'all': '#holo'}, faces=('east', 'west'), shade=False, light=15),
            el([1, 14, 8], [15, 28, 8], {'all': '#holo'}, faces=('north', 'south'), shade=False, light=15)], ao=False)
st_hor('holo_table', m)
block('holo_table', 'Bridge Holo Table', f'new ShapedBlock({{p}}.lightLevel(s -> 7), {boxes([[0, 0, 0, 16, 14, 16]])})', None, props='deco',
      recipe=shaped_recipe(['GGG', 'BAB', 'PPP'], {'G': '#c:glass_blocks', 'B': f'{NS}:control_board', 'A': 'minecraft:amethyst_shard', 'P': IND}))

# ---- lights ----
png('block/strip_light', T.strip(I, True))
png('block/strip_light_off', T.strip(I, False))
anim('floor_guide_light', 4, T.guide, 5)
anim('alert_light', 8, T.alert, 2)
def att_light(name, en, tex, bx, recipe, java=None, light='lightLevel(s -> 15)'):
    els = [el(bx[:3], bx[3:], {'all': '#side', 'up': '#light'}, light=15)]
    m = shaped(name, {'side': tex_ref('screen_frame'), 'light': tex_ref(tex)}, els)
    st_att(name, m)
    block(name, en, java or f'new AttachedBlock({{p}}.{light}, {boxes([bx])}, false)', None, props='light', recipe=recipe)

att_light('strip_light', 'Strip Light', 'strip_light', [1, 0, 5, 15, 1.5, 11],
          shaped_recipe(['PPP', 'GGG'], {'P': '#c:plates/iron', 'G': '#c:dusts/glowstone'}, 6))
att_light('floor_guide_light', 'Floor Guide Light', 'floor_guide_light', [4, 0, 2, 12, 1, 14], cut(f'{NS}:strip_light'))
att_light('alert_light', 'Alert Light', 'alert_light', [4, 0, 4, 12, 3, 12], shapeless([f'{NS}:strip_light', '#c:dyes/red']))
# flickering light: lit and unlit models
bx = [1, 0, 5, 15, 1.5, 11]
on = shaped('flickering_light', {'side': tex_ref('screen_frame'), 'light': tex_ref('strip_light')},
            [el(bx[:3], bx[3:], {'all': '#side', 'up': '#light'}, light=15)])
off = shaped('flickering_light_off', {'side': tex_ref('screen_frame'), 'light': tex_ref('strip_light_off')},
             [el(bx[:3], bx[3:], {'all': '#side', 'up': '#light'})])
STATES['flickering_light'] = {'variants': {}}
for fdir, a in ATT.items():
    STATES['flickering_light']['variants'][f'facing={fdir},lit=true'] = v(on, a[0], a[1])
    STATES['flickering_light']['variants'][f'facing={fdir},lit=false'] = v(off, a[0], a[1])
block('flickering_light', 'Flickering Light', f'new FlickerLightBlock({{p}}.lightLevel(s -> s.getValue(FlickerLightBlock.LIT) ? 12 : 0), {boxes([bx])})',
      None, props='light', recipe=shapeless([f'{NS}:strip_light', 'minecraft:gunpowder']))
REPAIRS.append(('flickering_light', 'strip_light'))

# ---- rooms: containers and furniture ----
png('block/locker_front', T.locker_front(I, 51))
png('block/locker_side', T.plate(I, 52))
png('block/supply_crate', T.crate_side(I, 53))
png('block/supply_crate_top', T.panel(I, 54))
png('block/med_cabinet_front', T.med_front(55))
png('block/med_side', T.plate(C, 56))
mat = T.plain(T.rgb('#e8eef4'), 57)
T.bevel(mat, 0, 0, 15, 15, T.rgb('#ffffff'), T.rgb('#b8c4cf'))
png('block/med_mattress', mat)
anim('med_scanner_screen', 8, T.scanner, 3)
png('block/mess_table_top', T.panel(C, 58))
png('block/mess_metal', T.plate(I, 59))

def container(name, en, textures, elements, shape, recipe, rows=3):
    m = shaped(name, textures, elements)
    st_hor(name, m)
    block(name, en, f'new ShipContainerBlock({{p}}, {boxes(shape)})', None, props='deco', recipe=recipe)

container('locker', 'Locker', {'side': tex_ref('locker_side'), 'front': tex_ref('locker_front')},
          [el([1, 0, 2], [15, 16, 16], {'all': '#side', 'north': '#front'})], [[1, 0, 2, 15, 16, 16]],
          shaped_recipe(['PPP', 'P P', 'PPP'], {'P': IND}))
container('supply_crate', 'Supply Crate', {'side': tex_ref('supply_crate'), 'top': tex_ref('supply_crate_top')},
          [el([1, 0, 1], [15, 14, 15], {'all': '#side', 'up': '#top', 'down': '#top'})], [[1, 0, 1, 15, 14, 15]],
          shaped_recipe(['PPP', 'PCP', 'PPP'], {'P': '#c:plates/iron', 'C': '#c:chests'}))
container('med_cabinet', 'Med Cabinet', {'side': tex_ref('med_side'), 'front': tex_ref('med_cabinet_front')},
          [el([1, 2, 8], [15, 16, 16], {'all': '#side', 'north': '#front'})], [[1, 2, 8, 15, 16, 16]],
          shaped_recipe(['PPP', 'PDP', 'PPP'], {'P': f'{NS}:clean_plating', 'D': '#c:dyes/red'}))

def deco(name, en, textures, elements, shape, recipe, solid=True, props='deco', java_cls='ShapedBlock'):
    m = shaped(name, textures, elements)
    st_hor(name, m)
    extra = '' if solid else ', false'
    block(name, en, f'new {java_cls}({{p}}, {boxes(shape)}{extra})', None, props=props, recipe=recipe)

deco('med_bed', 'Med Bed', {'side': tex_ref('med_side'), 'top': tex_ref('med_mattress')},
     [el([0, 3, 0], [16, 7, 16], {'all': '#side'}), el([0, 7, 1], [16, 9, 15], {'all': '#top'}),
      el([1, 0, 1], [3, 3, 3], {'all': '#side'}), el([13, 0, 1], [15, 3, 3], {'all': '#side'}),
      el([1, 0, 13], [3, 3, 15], {'all': '#side'}), el([13, 0, 13], [15, 3, 15], {'all': '#side'})],
     [[0, 0, 0, 16, 9, 16]], shaped_recipe(['WWW', 'PPP'], {'W': '#minecraft:wool', 'P': f'{NS}:clean_plating'}))
deco('med_scanner', 'Med Scanner', {'side': tex_ref('med_side'), 'screen': tex_ref('med_scanner_screen')},
     [el([0, 0, 6], [2, 16, 10], {'all': '#side'}), el([14, 0, 6], [16, 16, 10], {'all': '#side'}),
      el([2, 13, 6], [14, 16, 10], {'all': '#side', 'north': '#screen', 'south': '#screen'}, light=10)],
     [[0, 0, 6, 2, 16, 10], [14, 0, 6, 16, 16, 10], [2, 13, 6, 14, 16, 10]],
     shaped_recipe(['PGP', 'PBP', 'P P'], {'P': f'{NS}:clean_plating', 'G': '#c:glass_panes', 'B': f'{NS}:control_board'}))
deco('mess_table', 'Mess Table', {'side': tex_ref('mess_metal'), 'top': tex_ref('mess_table_top')},
     [el([0, 13, 0], [16, 16, 16], {'all': '#side', 'up': '#top'}), el([6, 0, 6], [10, 13, 10], {'all': '#side'})],
     [[0, 13, 0, 16, 16, 16], [6, 0, 6, 10, 13, 10]],
     shaped_recipe(['PPP', ' R ', ' R '], {'P': f'{NS}:clean_plating', 'R': '#c:rods/iron'}))
deco('mess_bench', 'Mess Bench', {'side': tex_ref('mess_metal'), 'top': tex_ref('mess_table_top')},
     [el([0, 7, 3], [16, 9, 13], {'all': '#side', 'up': '#top'}),
      el([1, 0, 5], [3, 7, 11], {'all': '#side'}), el([13, 0, 5], [15, 7, 11], {'all': '#side'})],
     [[0, 0, 3, 16, 9, 13]], shaped_recipe(['PPP', 'R R'], {'P': f'{NS}:clean_plating', 'R': '#c:rods/iron'}, 2))

# hydroponics
png('block/hydroponics_tray_side', T.tray_tex(False))
png('block/hydroponics_tray_soil', T.tray_tex(True))
tray = model('hydroponics_tray', 'minecraft:block/block',
             {'side': tex_ref('hydroponics_tray_side'), 'soil': tex_ref('hydroponics_tray_soil'), 'particle': tex_ref('hydroponics_tray_side')},
             [el([0, 0, 0], [16, 6, 16], {'all': '#side', 'up': '#soil'})])
CROPS = {'wheat': ('wheat_stage', [1, 3, 5, 7]), 'carrot': ('carrots_stage', [0, 1, 2, 3]),
         'potato': ('potatoes_stage', [0, 1, 2, 3]), 'beetroot': ('beetroots_stage', [0, 1, 2, 3])}
parts = [{'apply': {'model': tray}}]
for crop, (prefix, stages) in CROPS.items():
    for age, stg in enumerate(stages):
        n = f'hydroponics_tray_{crop}_{age}'
        cross = []
        for z in (4, 12):
            cross.append(el([0, 6, z], [16, 22, z], {'all': '#crop'}, faces=('north', 'south'), shade=False))
        for x in (4, 12):
            cross.append(el([x, 6, 0], [x, 22, 16], {'all': '#crop'}, faces=('east', 'west'), shade=False))
        mm = model(n, None, {'crop': f'minecraft:block/{prefix}{stg}', 'particle': f'minecraft:block/{prefix}{stg}'}, cross, ao=False)
        parts.append({'when': {'crop': crop, 'age': str(age)}, 'apply': {'model': mm}})
STATES['hydroponics_tray'] = {'multipart': parts}
block('hydroponics_tray', 'Hydroponics Tray', 'new HydroponicsTrayBlock({p})', None, props='deco',
      recipe=shaped_recipe(['GWG', 'PPP'], {'G': '#c:glass_panes', 'W': 'minecraft:water_bucket', 'P': f'{NS}:clean_plating'}, 2))

# ---- structure: struts, catwalks, ladders, pipes ----
png('block/strut', T.strut_tex(I, 61))
png('block/strut_end', T.panel(I, 62))
def pillar(name, en, half, side, end, recipe, props='deco'):
    a, b = 8 - half, 8 + half
    m = shaped(name, {'side': tex_ref(side), 'end': tex_ref(end)},
               [el([a, 0, a], [b, 16, b], {'all': '#side', 'up': '#end', 'down': '#end'})])
    st_axis(name, m)
    block(name, en, f'new PillarShapeBlock({{p}}, {half})', None, props=props, recipe=recipe)

pillar('strut', 'Strut', 3, 'strut', 'strut_end', shaped_recipe(['R', 'R', 'R'], {'R': '#c:rods/steel'}, 6))
png('block/decor_pipe', T.pipe_tex(T.rgb('#a8643a'), 63, T.rgb('#e0b030')))
png('block/decor_pipe_end', T.panel(dict(base=T.rgb('#a8643a'), light=T.rgb('#c88a5a'), dark=T.rgb('#6a3a20'), seam=T.rgb('#4a2810')), 64))
pillar('decor_pipe', 'Decorative Pipe', 3, 'decor_pipe', 'decor_pipe_end', shaped_recipe(['CCC'], {'C': '#c:plates/copper'}, 6))
png('block/decor_cable', T.cable_tex(65))
pillar('decor_cable', 'Decorative Cable', 1.5, 'decor_cable', 'decor_cable', shapeless([f'{NS}:redstone_copper_cable'], 4))

m = shaped('cross_brace', {'side': tex_ref('strut')},
           [el([-3, 7, 14], [19, 9, 16], {'all': '#side'}, rot={'origin': [8, 8, 15], 'axis': 'z', 'angle': 45}),
            el([-3, 7, 14], [19, 9, 16], {'all': '#side'}, rot={'origin': [8, 8, 15], 'axis': 'z', 'angle': -45})])
st_hor('cross_brace', m)
block('cross_brace', 'Cross Brace', f'new ShapedBlock({{p}}, {boxes([[0, 0, 14, 16, 16, 16]])}, false)', None, props='deco',
      recipe=shaped_recipe(['R R', ' R ', 'R R'], {'R': '#c:rods/steel'}, 4))

m = shaped('catwalk', {'grate': tex_ref('industrial_grate'), 'side': tex_ref('strut')},
           [el([0, 0, 0], [16, 3, 16], {'all': '#side', 'up': '#grate', 'down': '#grate'})])
st_hor('catwalk', m)
block('catwalk', 'Catwalk', f'new ShapedBlock({{p}}, {boxes([[0, 0, 0, 16, 3, 16]])})', None, props='open',
      recipe=shaped_recipe(['BBB'], {'B': 'minecraft:iron_bars'}, 6))
m = shaped('catwalk_railing', {'side': tex_ref('strut')},
           [el([0, 0, 0], [2, 16, 2], {'all': '#side'}), el([14, 0, 0], [16, 16, 2], {'all': '#side'}),
            el([2, 14, 0.5], [14, 16, 1.5], {'all': '#side'}), el([2, 7, 0.5], [14, 8.5, 1.5], {'all': '#side'})])
st_hor('catwalk_railing', m)
block('catwalk_railing', 'Catwalk Railing', f'new ShapedBlock({{p}}, {boxes([[0, 0, 0, 16, 16, 2]])})', None, props='deco',
      recipe=shaped_recipe(['RRR', 'RRR'], {'R': '#c:rods/iron'}, 6))

png('block/ship_ladder', T.ladder_tex(I))
m = model('ship_ladder', None, {'texture': tex_ref('ship_ladder'), 'particle': tex_ref('ship_ladder')},
          [el([0, 0, 15.2], [16, 16, 15.2], {'all': '#texture'}, faces=('north', 'south'), shade=False)], ao=False)
st_hor('ship_ladder', m)
block('ship_ladder', 'Ship Ladder', 'new ShipLadderBlock({p})', None, props='ladder', item='flat_block', tags=('pickaxe', 'climbable'),
      recipe=shaped_recipe(['R R', 'RRR', 'R R'], {'R': '#c:rods/steel'}, 8))

m = shaped('lift_shaft', {'side': tex_ref('strut'), 'ladder': tex_ref('ship_ladder')},
           [el([0, 0, 0], [2, 16, 2], {'all': '#side'}), el([14, 0, 0], [16, 16, 2], {'all': '#side'}),
            el([0, 0, 14], [2, 16, 16], {'all': '#side'}), el([14, 0, 14], [16, 16, 16], {'all': '#side'}),
            el([2, 0, 15.5], [14, 16, 15.5], {'all': '#ladder'}, faces=('north', 'south'), shade=False)])
st_hor('lift_shaft', m)
block('lift_shaft', 'Lift Shaft', f'new ShapedBlock({{p}}, {boxes([[0, 0, 0, 2, 16, 2], [14, 0, 0, 16, 16, 2], [0, 0, 14, 2, 16, 16], [14, 0, 14, 16, 16, 16]])})',
      None, props='open', tags=('pickaxe', 'climbable'), recipe=shaped_recipe(['RRR', 'R R', 'RRR'], {'R': '#c:rods/steel'}, 4))

m = shaped('pipe_bundle', {'side': tex_ref('decor_pipe'), 'end': tex_ref('decor_pipe_end')},
           [el([0, y, 12], [16, y + 4, 16], {'all': '#side', 'east': '#end', 'west': '#end'},
               uv={'north': [0, 6, 16, 10], 'south': [0, 6, 16, 10], 'up': [0, 6, 16, 10], 'down': [0, 6, 16, 10]}) for y in (1, 6, 11)])
st_hor('pipe_bundle', m)
block('pipe_bundle', 'Pipe Bundle', f'new ShapedBlock({{p}}, {boxes([[0, 0, 12, 16, 16, 16]])})', None, props='deco',
      recipe=shaped_recipe(['P', 'P', 'P'], {'P': f'{NS}:decor_pipe'}))
m = shaped('cable_tray', {'side': tex_ref('strut'), 'cable': tex_ref('decor_cable')},
           [el([0, 12, 3], [16, 13, 13], {'all': '#side'}), el([0, 13, 3], [16, 14, 4], {'all': '#side'}),
            el([0, 13, 12], [16, 14, 13], {'all': '#side'}), el([0, 13, 5], [16, 14, 11], {'all': '#cable'})])
st_hor('cable_tray', m)
block('cable_tray', 'Cable Tray', f'new ShapedBlock({{p}}, {boxes([[0, 12, 3, 16, 14, 13]])}, false)', None, props='deco',
      recipe=shaped_recipe(['P P', 'PPP'], {'P': '#c:plates/iron'}, 6))

# ---- damage and hazards ----
png('block/sparking_wires', T.wires_tex(71))
m = shaped('sparking_wires', {'wires': tex_ref('sparking_wires')},
           [el([0, 0.5, 0], [16, 0.5, 16], {'all': '#wires'}, faces=('up', 'down'), shade=False)], ao=False)
st_att('sparking_wires', m)
block('sparking_wires', 'Sparking Wires', f'new SparkingWiresBlock({{p}}.lightLevel(s -> 3), {boxes([[0, 0, 0, 16, 2, 16]])})', None,
      props='hazard', recipe=shapeless([f'{NS}:decor_cable', '#c:dusts/redstone']))
REPAIRS.append(('sparking_wires', 'decor_cable'))
sv = T.vent(I, 72)
T.rect(sv, 3, 3, 12, 12, I['seam'])
for y in (4, 7, 10):
    T.rect(sv, 4, y, 11, y, T.rgb('#1a1c1f'))
png('block/steam_vent_top', sv)
STATES['steam_vent'] = {'variants': {'': v(model('steam_vent', 'minecraft:block/cube_bottom_top',
                                                  {'top': tex_ref('steam_vent_top'), 'side': tex_ref('industrial_panel'), 'bottom': tex_ref('industrial_panel')}))}}
block('steam_vent', 'Steam Vent', 'new SteamVentBlock({p})', None, recipe=shapeless([f'{NS}:industrial_panel_vent', 'minecraft:water_bucket']))
REPAIRS.append(('steam_vent', 'industrial_panel_vent'))
png('block/breached_frosty_panel', T.frost(I, 73))
STATES['breached_frosty_panel'] = {'variants': {'': v(cube_all('breached_frosty_panel', 'breached_frosty_panel'))}}
block('breached_frosty_panel', 'Breached Frosty Panel', 'new FrostyPanelBlock({p})', None,
      recipe=shapeless([f'{NS}:industrial_panel_damaged', 'minecraft:ice']))
REPAIRS.append(('breached_frosty_panel', 'industrial_plating'))
png('block/debris', T.debris_tex(74))
m = shaped('debris', {'debris': tex_ref('debris')}, [el([0, 0.25, 0], [16, 0.25, 16], {'all': '#debris'}, faces=('up', 'down'))], ao=False)
st_hor('debris', m)
block('debris', 'Debris', f'new ShapedBlock({{p}}, {boxes([[0, 0, 0, 16, 1, 16]])}, false)', None, props='rubble',
      recipe=cut(f'{NS}:industrial_panel_damaged', 2))
png('block/scrap', T.damaged(I, 75))
m = shaped('scrap_pile', {'side': tex_ref('scrap'), 'rust': tex_ref('industrial_panel_scorched')},
           [el([2, 0, 3], [9, 3, 9], {'all': '#side'}), el([7, 0, 8], [14, 4, 14], {'all': '#rust'}),
            el([4, 3, 5], [10, 6, 11], {'all': '#side'}, rot={'origin': [7, 4, 8], 'axis': 'y', 'angle': 22.5})])
st_hor('scrap_pile', m)
block('scrap_pile', 'Scrap Pile', f'new ShapedBlock({{p}}, {boxes([[2, 0, 3, 14, 6, 14]])})', None, props='rubble',
      recipe=cut(f'{NS}:industrial_panel_damaged', 1))
REPAIRS.append(('debris', 'minecraft:air'))
REPAIRS.append(('scrap_pile', 'minecraft:air'))

# ---- items ----
png('item/ship_tool', T.wrench_item())
png('item/repair_kit', T.kit_item(T.rgb('#d8dde2'), T.rgb('#d8302a')))
png('item/machine_finish_kit', T.finish_kit_item(T.rgb('#b4b9bf')))
for f in FINISH:
    png(f'item/machine_finish_kit_{f}', T.finish_kit_item(FINISH[f]['accent'] if f != 'industrial' else FINISH[f]['accent']))

# machine finishes: see-through window on the front so the worktop machine shows
for f in FINISH:
    p = P[f]
    fr = T.plate(p, 81)
    T.rect(fr, 3, 3, 12, 9, (0, 0, 0, 0))
    T.bevel(fr, 2, 2, 13, 10, p['dark'], p['light'])
    for x in range(2, 14):
        T.dot(fr, x, 13, p['accent'])
    png(f'block/finish/{f}_machine_front', fr)
    png(f'block/finish/{f}_machine_side', T.panel(p, 82))
    png(f'block/finish/{f}_machine_top', T.plating(p, 83))

# crew skins for the cryopods, offline and bundled
SKINS = {
    'crew_1': ((T.rgb('#5c6066'), T.rgb('#e0b030'), T.rgb('#d8a886')), T.rgb('#3a2a1a'), False),
    'crew_2': ((T.rgb('#c86a2a'), T.rgb('#2b2b2e'), T.rgb('#8a5a3a')), T.rgb('#1a1410'), False),
    'crew_3_slim': ((T.rgb('#2f6f9f'), T.rgb('#d8dde2'), T.rgb('#e8c0a0')), T.rgb('#c89040'), True),
    'crew_4': ((T.rgb('#d8dde2'), T.rgb('#4aa6e0'), T.rgb('#c89870')), T.rgb('#6a6a6a'), False),
    'crew_5_slim': ((T.rgb('#46523f'), T.rgb('#c49a4a'), T.rgb('#6a4430')), T.rgb('#101010'), True),
}
for i, (n, (cols, hair, slim)) in enumerate(SKINS.items()):
    png(f'entity/cryopod/{n}', T.skin(cols, 90 + i, hair, slim))

# ================= WRITERS =================
LANG.update({
    'itemGroup.simplemachines.ship': 'Simple Machines: Ship Blocks',
    'item.simplemachines.start_cryopod': 'Start Cryopod',
    'item.simplemachines.ship_tool': 'Ship Tool',
    'item.simplemachines.repair_kit': 'Repair Kit',
    'item.simplemachines.machine_finish_kit': 'Machine Finish Kit',
    'item.simplemachines.machine_finish_kit.named': 'Machine Finish Kit (%s)',
    'finish.simplemachines.none': 'Plain',
    'finish.simplemachines.ancient': 'Ancient',
    'finish.simplemachines.industrial': 'Industrial',
    'finish.simplemachines.clean': 'Clean',
    'tooltip.simplemachines.ship_tool': 'Use on a bulkhead to take the whole wall down.',
    'tooltip.simplemachines.ship_tool_sneak': 'Sneak to take one block.',
    'tooltip.simplemachines.repair_kit': 'Use on broken ship parts to fix them. Used up.',
    'tooltip.simplemachines.repair_kit_bulkhead': 'Also takes down a bulkhead wall.',
    'tooltip.simplemachines.machine_finish_kit': 'Use on a built machine to change its look. A plain kit takes the finish off.',
    'message.simplemachines.bulkhead_removed': 'Took down %s bulkhead blocks',
    'message.simplemachines.cryopod_linked': 'Spawn point set to this cryopod',
    'message.simplemachines.cryopod_wake': 'You wake up in a cryopod. The ship is very quiet.',
    'message.simplemachines.door_locked': 'Locked',
    'message.simplemachines.repaired': 'Repaired',
    'message.simplemachines.log_locked': 'This log is locked',
    'message.simplemachines.log_written': 'Log saved',
    'message.simplemachines.log_empty': 'The log is empty. Use a book and quill on it to write one.',
    'screen.simplemachines.jump_in': 'JUMP IN',
    'screen.simplemachines.jump': 'JUMP',
    'screen.simplemachines.log_empty': 'NO LOG',
    'commands.simplemachines.not_pod': 'That is not a cryopod',
    'commands.simplemachines.pod': 'Cryopod occupant set to %s',
    'commands.simplemachines.pod_auto': 'This cryopod will pick an occupant from the list when it is placed',
    'commands.simplemachines.not_cryopod': 'That is not a cryopod bed',
    'commands.simplemachines.startpod': 'New players will start in this cryopod bed',
    'commands.simplemachines.area_none': 'No ship areas have a state yet',
    'commands.simplemachines.area_get': 'Area %s is %s',
    'commands.simplemachines.area_void': 'Simple Void areas are read only here. Change them with Simple Void.',
    'commands.simplemachines.area_set': 'Area %s set to %s',
    'commands.simplemachines.not_door': 'That is not a sliding door',
    'commands.simplemachines.unlocked': 'Door unlocked',
    'commands.simplemachines.locked': 'Door opens when area %s is %s',
    'commands.simplemachines.repaired': 'Repaired %s blocks',
    'commands.simplemachines.damaged': 'Broke %s blocks',
    'commands.simplemachines.not_countdown': 'That is not a jump countdown display',
    'commands.simplemachines.countdown': 'Countdown set to %s seconds',
    'commands.simplemachines.not_terminal': 'That is not a story log terminal',
    'commands.simplemachines.log': 'Log written',
    'commands.simplemachines.log_lock': 'Log locked: %s',
})
for b in BLOCKS:
    LANG[f'block.{NS}.{b["name"]}'] = b['en']

PROPS = {
    'metal': 'metal(n)',
    'open': 'open(metal(n))',
    'glass': 'open(metal(n)).strength(2F, 6F)',
    'hull': 'hull(n)',
    'hull_glass': 'open(hull(n))',
    'bulkhead': 'metal(n).strength(8F, 1200F).pushReaction(PushReaction.BLOCK)',
    'door': 'open(metal(n)).strength(4F, 6F).pushReaction(PushReaction.BLOCK)',
    'shutter': 'metal(n).noOcclusion().strength(10F, 1200F).pushReaction(PushReaction.BLOCK)',
    'pod': 'open(metal(n)).strength(2F, 6F).pushReaction(PushReaction.BLOCK)',
    'deco': 'open(metal(n)).strength(2F, 6F)',
    'light': 'open(metal(n)).strength(0.5F)',
    'ladder': 'open(metal(n)).strength(1.5F)',
    'hazard': 'open(metal(n)).strength(0.5F).noCollision()',
    'rubble': 'open(metal(n)).strength(0.5F)',
}

def field(n):
    return n.upper()

def java():
    lines = []
    for b in BLOCKS:
        n = b['name']
        expr = b['java'].replace('{p}', PROPS[b['props']].replace('(n)', f'("{n}")'))
        lines.append(f'        {field(n)} = add(r, "{n}", {expr});')
    items = []
    for b in BLOCKS:
        n = b['name']
        cls = {'door': 'DoubleHighBlockItem', 'bed': 'BedItem'}.get(b['item'], 'BlockItem')
        extra = '.stacksTo(1)' if b['item'] == 'bed' else ''
        items.append(f'        item(r, "{n}", new {cls}({field(n)}, props("{n}"){extra}));')
    decl = '\n'.join(f'    public static Block {field(b["name"])};' for b in BLOCKS)
    return f'''package com.workshopcraft.simplemachines.ship;

// GENERATED by tools/shipgen/gen.py. Do not edit by hand; change the generator and run it again.

import com.workshopcraft.simplemachines.SimpleMachines;
import com.workshopcraft.simplemachines.ship.block.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public final class ShipBlocks {{
    public static final List<Block> ALL = new ArrayList<>();
    public static final List<Item> TAB = new ArrayList<>();
    public static Block ICON;

{decl}

    private ShipBlocks() {{}}

    private static BlockBehaviour.Properties base(String n) {{
        return BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, SimpleMachines.id(n)));
    }}

    private static BlockBehaviour.Properties metal(String n) {{
        return base(n).mapColor(MapColor.METAL).strength(3F, 6F).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }}

    /** Unbreakable in survival; creative players and structures place and break it. */
    private static BlockBehaviour.Properties hull(String n) {{
        return base(n).mapColor(MapColor.COLOR_GRAY).strength(-1F, 3600000F).noLootTable().sound(SoundType.NETHERITE_BLOCK)
                .isValidSpawn((s, l, p, e) -> false).pushReaction(PushReaction.BLOCK);
    }}

    private static BlockBehaviour.Properties open(BlockBehaviour.Properties p) {{
        return p.noOcclusion().isViewBlocking((s, l, pos) -> false).isSuffocating((s, l, pos) -> false).isValidSpawn((s, l, pos, e) -> false);
    }}

    private static Block add(BiConsumer<Identifier, Block> r, String name, Block block) {{
        r.accept(SimpleMachines.id(name), block);
        ALL.add(block);
        return block;
    }}

    private static Item.Properties props(String name) {{
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, SimpleMachines.id(name))).useBlockDescriptionPrefix();
    }}

    private static void item(BiConsumer<Identifier, Item> r, String name, Item item) {{
        r.accept(SimpleMachines.id(name), item);
        TAB.add(item);
    }}

    public static void registerBlocks(BiConsumer<Identifier, Block> r) {{
{chr(10).join(lines)}
        ICON = CRYOPOD_BED;
    }}

    public static void registerItems(BiConsumer<Identifier, Item> r) {{
{chr(10).join(items)}
    }}
}}
'''

def item_models():
    out = {}
    for b in BLOCKS:
        n = b['name']
        if b['item'] in ('door', 'bed', 'flat'):
            out[n] = {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{n}'}}
            MODELS_ITEM[n] = {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{n}'}}
        elif b['item'] == 'flat_block':
            out[n] = {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{n}'}}
            MODELS_ITEM[n] = {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:block/{n}'}}
        else:
            st = STATES[n]
            m = (f'{NS}:block/{n}' if n in MODELS else st['multipart'][0]['apply']['model']) if 'multipart' in st else next(iter(st['variants'].values()))['model']
            out[n] = {'model': {'type': 'minecraft:model', 'model': m}}
    for n in ('ship_tool', 'repair_kit'):
        MODELS_ITEM[n] = {'parent': 'minecraft:item/handheld' if n == 'ship_tool' else 'minecraft:item/generated',
                          'textures': {'layer0': f'{NS}:item/{n}'}}
        out[n] = {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{n}'}}
    MODELS_ITEM['machine_finish_kit'] = {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/machine_finish_kit'}}
    cases = []
    for f in FINISH:
        MODELS_ITEM[f'machine_finish_kit_{f}'] = {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/machine_finish_kit_{f}'}}
        cases.append({'when': f'{NS}:{f}', 'model': {'type': 'minecraft:model', 'model': f'{NS}:item/machine_finish_kit_{f}'}})
    out['machine_finish_kit'] = {'model': {'type': 'minecraft:select', 'property': 'minecraft:component',
                                           'component': f'{NS}:machine_finish', 'cases': cases,
                                           'fallback': {'type': 'minecraft:model', 'model': f'{NS}:item/machine_finish_kit'}}}
    return out
MODELS_ITEM = {}

def ing(s):
    return s

def recipe_json(name, r):
    if r['type'] == 'shaped':
        return {'type': 'minecraft:crafting_shaped', 'category': 'building', 'pattern': r['pattern'], 'key': r['key'],
                'result': {'id': f'{NS}:{name}', 'count': r['count']}}
    if r['type'] == 'shapeless':
        return {'type': 'minecraft:crafting_shapeless', 'category': 'building', 'ingredients': r['items'],
                'result': {'id': f'{NS}:{name}', 'count': r['count']}}
    src = r['from'] if ':' in r['from'] else f'{NS}:{r["from"]}'
    return {'type': 'minecraft:stonecutting', 'ingredient': src, 'result': {'id': f'{NS}:{name}', 'count': r['count']}}

EXTRA_RECIPES = {
    'ship_tool': {'type': 'shaped', 'pattern': [' P ', ' RP', 'R  '], 'key': {'P': '#c:plates/steel', 'R': '#c:rods/steel'}, 'count': 1},
    'repair_kit': {'type': 'shaped', 'pattern': ['SCS', 'RBR'], 'key': {'S': '#c:plates/iron', 'C': '#c:ingots/copper', 'R': '#c:dusts/redstone',
                                                                   'B': f'{NS}:control_board'}, 'count': 2},
    'machine_finish_kit': {'type': 'shapeless', 'items': ['minecraft:paper', '#c:plates/iron'], 'count': 1},
}

def loot(name, kind):
    cond = [{'condition': 'minecraft:survives_explosion'}]
    if kind in ('lower', 'head'):
        prop = {'half': 'lower'} if kind == 'lower' else {'part': 'head'}
        cond.append({'condition': 'minecraft:block_state_property', 'block': f'{NS}:{name}', 'properties': prop})
    return {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'bonus_rolls': 0, 'conditions': cond,
                                                  'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{name}'}]}],
            'random_sequence': f'{NS}:blocks/{name}'}

def label(i):
    if i.startswith('#'):
        return i[1:].split('/')[-1].split(':')[-1].replace('_', ' ').title().rstrip('s') if '/' in i else i[1:].split(':')[-1].replace('_', ' ').title()
    n = i.split(':')[1]
    return LANG.get(f'block.{NS}.{n}') or LANG.get(f'item.{NS}.{n}') or n.replace('_', ' ').title()

def step(name, r=None, title=None):
    r = r or RECIPES.get(name) or EXTRA_RECIPES.get(name)
    out_id = f'{NS}:{name}'
    output = out_id if r['count'] == 1 else {'item': out_id, 'count': r['count']}
    if r['type'] == 'stonecut':
        src = r['from'] if ':' in r['from'] else f'{NS}:{r["from"]}'
        s = {'kind': 'process', 'inputs': [src], 'appliance': 'minecraft:stonecutter', 'output': output, 'note': 'Stonecutter'}
    else:
        if r['type'] == 'shaped':
            pat = [row.ljust(3) for row in r['pattern']] + ['   '] * (3 - len(r['pattern']))
            key = r['key']
        else:
            letters = 'ABCDEFGHI'
            key = {letters[i]: it for i, it in enumerate(r['items'])}
            row = ''.join(letters[i] for i in range(len(r['items'])))
            pat = [row[:3].ljust(3), row[3:6].ljust(3), row[6:9].ljust(3)]
        counts = {}
        for row in pat:
            for ch in row:
                if ch != ' ':
                    counts[ch] = counts.get(ch, 0) + 1
        note = 'Crafting Table\n' + ', '.join(f'{counts[k]}x {label(v)}' for k, v in key.items() if k in counts)
        s = {'kind': 'craft_shaped', 'pattern': pat, 'key': key, 'output': output, 'note': fail_dash(note)}
    s['title'] = title or label(out_id)
    return s

PAGES = [
    ('ship_hull', 'Hull and Boundary', 'The walls nobody gets through',
     'Hull blocks and the Hull Boundary cannot be broken in survival, by explosions, the Wither or the Ender Dragon. '
     'Creative players can place and break them, so they are for map makers and ship structures. There is no survival recipe.\n\n'
     'Hull Glass is the see-through version. Use it for the big bridge windows. Windows and hull glass join up with their own kind: a wall of them is one sheet with a frame only round the outside.\n\n'
     'Plating, panels, grates, windows, lights and portholes come in three finishes: Ancient, Industrial and Clean. '
     'Make the plating, then cut it into the other shapes on a stonecutter.',
     ['industrial_plating', 'industrial_panel', 'industrial_window', 'industrial_light']),
    ('ship_bulkheads', 'Bulkheads', 'Walls you can take down',
     'Bulkheads are tough walls inside the ship. A pickaxe takes a long time. The Ship Tool takes the whole connected wall '
     'down at once and gives you the blocks back; sneak to take just one. A Repair Kit does the same but is used up.\n\n'
     'Servers can set ship_bulkheads_need_tool to true in the Simple Machines server config so only the tool or a kit works.',
     ['industrial_bulkhead', 'ship_tool', 'repair_kit']),
    ('ship_cryopods', 'Cryopods', 'Sleep, and wake up on the ship',
     'A Cryopod Bed is a bed. Sleep in it and it becomes your spawn point, even in places where a normal bed would explode.\n\n'
     'A Start Cryopod (in the creative tab) marks where new players wake up the first time they join. Map makers can also use '
     '/simplemachines startpod on a placed cryopod bed.\n\n'
     'The tall Cryopod is decoration with a sleeper inside. Use /simplemachines pod with a player name, a UUID or skin:crew_1 '
     'to choose who is in it. Pods placed by a structure pick a sleeper from the occupant list in the data pack and config.',
     ['cryopod_bed', 'cryopod']),
    ('ship_doors', 'Doors, Hatches and Shutters', 'Slide open, slide shut',
     'Sliding Doors open with a click or a redstone signal and slide sideways. Two doors side by side open together.\n\n'
     'An Airlock Door closes any other open airlock door nearby when it opens, so only one side is ever open.\n\n'
     'A Locked Door only opens while its area has the right state. Set it with /simplemachines lock. '
     'Keys that start with void: read the area state from Simple Void.\n\n'
     'Sliding Hatches open with the hatches next to them. Blast Shutters only move with redstone, and a whole wall moves together.',
     ['industrial_sliding_door', 'airlock_door', 'locked_door', 'sliding_hatch', 'blast_shutter']),
    ('ship_bridge', 'Bridge and Screens', 'Panels that look busy',
     'Control panels come in Navigation, Engineering and Reactor versions; cut one from another on a stonecutter. '
     'Wall screens show a star map or the ship status. The Holo Table shows a slowly turning hologram.\n\n'
     'The Jump Countdown Display counts down from 30 seconds when it gets a redstone signal, beeps near the end, and gives out '
     'a redstone pulse at zero. /simplemachines countdown sets any time.\n\n'
     'Story Log Terminals hold a written log. Use a book and quill or a written book on one to copy its text in, and use it '
     'with an empty hand to read the log in chat. /simplemachines log can write and lock a log.',
     ['control_panel_nav', 'wall_screen_status', 'holo_table', 'jump_countdown_display', 'story_log_terminal']),
    ('ship_rooms', 'Rooms', 'Med bay, mess and storage',
     'Lockers, Supply Crates and Med Cabinets hold 27 stacks, like a chest. Structures can give them loot tables.\n\n'
     'The Med Bed, Med Scanner, Mess Table and Mess Bench are decoration.\n\n'
     'Hydroponics Trays grow wheat, carrots, potatoes and beetroot without light or water. Plant with the seed, '
     'harvest by using it when it is grown.',
     ['locker', 'supply_crate', 'med_cabinet', 'hydroponics_tray']),
    ('ship_structure', 'Struts, Catwalks and Lights', 'The bits in between',
     'Struts, pipes and cables run along any axis. Catwalks are see-through floors, railings keep you on them. '
     'Ship Ladders and Lift Shafts can be climbed.\n\n'
     'Strip Lights, Floor Guide Lights and Alert Lights stick to any face. Flickering Lights are broken strip lights.',
     ['strut', 'catwalk', 'ship_ladder', 'lift_shaft', 'strip_light']),
    ('ship_damage', 'Damage and Repairs', 'Broken ships, fixed ships',
     'Sparking Wires and Steam Vents hurt to touch, and Breached Frosty Panels chill anyone standing next to them. '
     'Servers can turn the damage off with ship_hazard_damage.\n\n'
     'Use a Repair Kit on a broken block to swap it for the fixed one: damaged and scorched panels, flickering lights, '
     'sparking wires, steam vents, broken screens and panels. Debris is cleared away. Map makers can repair or break a whole '
     'area with /simplemachines repair and /simplemachines damage. Data packs add pairs in simplemachines/repairs.',
     ['repair_kit', 'industrial_panel_damaged', 'sparking_wires', 'flickering_light']),
    ('ship_finishes', 'Machine Finishes', 'A new look for old machines',
     'A Machine Finish Kit changes how a built machine looks without changing what it does. Use it on the machine. '
     'Ancient, Industrial and Clean finishes come with the mod. A plain kit takes the finish off again.\n\n'
     'Resource packs add finishes with a json file in simplemachines/machine_finishes, naming a front, side, back and top '
     'texture. Give a kit for it with the simplemachines:machine_finish component.',
     ['machine_finish_kit']),
]

def manual():
    pages = {}
    for i, (pid, title, sub, text, recs) in enumerate(PAGES):
        steps = []
        for r in recs:
            if r in RECIPES or r in EXTRA_RECIPES:
                steps.append(step(r))
        if not steps:
            steps = [{'kind': 'text', 'title': 'Creative only', 'text': 'Find these in the Ship Blocks creative tab.'}]
        pg = {'section': 'ship', 'sort': i + 1, 'title': fail_dash(title), 'subtitle': fail_dash(sub), 'text': fail_dash(text),
              'right_title': 'Recipes', 'steps': steps}
        if pid == 'ship_hull':
            pg['steps'].insert(0, {'kind': 'text', 'title': 'Hull blocks', 'text': 'Creative only. Find them in the Ship Blocks tab.'})
        pages[pid] = pg
    return pages

def main():
    global TEXTURES
    tex_root = os.path.join(A, 'textures')
    if WRITE_TEX:
        for name, (im, ft) in TEXTURES.items():
            path = os.path.join(tex_root, name + '.png')
            os.makedirs(os.path.dirname(path), exist_ok=True)
            im.save(path)
            if ft:
                write(path + '.mcmeta', {'animation': {'frametime': ft}})
    else:
        for name, (im, ft) in TEXTURES.items():
            path = os.path.join(tex_root, name + '.png')
            if not os.path.exists(path):
                os.makedirs(os.path.dirname(path), exist_ok=True)
                im.save(path)
                if ft:
                    write(path + '.mcmeta', {'animation': {'frametime': ft}})
    # clean generated json folders
    for sub in ('blockstates', 'models/block', 'models/item', 'items'):
        shutil.rmtree(os.path.join(A, sub), ignore_errors=True)
    for sub in ('recipe', 'loot_table'):
        shutil.rmtree(os.path.join(D, sub), ignore_errors=True)
    for n, s in STATES.items():
        write(os.path.join(A, 'blockstates', n + '.json'), s)
    for n, m in MODELS.items():
        write(os.path.join(A, 'models/block', n + '.json'), m)
    for n, m in item_models().items():
        write(os.path.join(A, 'items', n + '.json'), m)
    for n, m in MODELS_ITEM.items():
        write(os.path.join(A, 'models/item', n + '.json'), m)
    for b in BLOCKS:
        assert b['name'] in STATES, b['name']
        if b['loot'] != 'none':
            write(os.path.join(D, 'loot_table/blocks', b['name'] + '.json'), loot(b['name'], b['loot']))
    for n, r in list(RECIPES.items()) + list(EXTRA_RECIPES.items()):
        write(os.path.join(D, 'recipe', n + '.json'), recipe_json(n, r))
    # finish kits for each built in finish
    for f in FINISH:
        write(os.path.join(D, 'recipe', f'machine_finish_kit_{f}.json'),
              {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
               'ingredients': [f'{NS}:machine_finish_kit', f'{NS}:{f}_plating', f'{NS}:{f}_plating'],
               'result': {'id': f'{NS}:machine_finish_kit', 'count': 1, 'components': {f'{NS}:machine_finish': f'{NS}:{f}'}}})
        write(os.path.join(A, 'simplemachines/machine_finishes', f + '.json'),
              {'textures': {'front': f'{NS}:block/finish/{f}_machine_front', 'side': f'{NS}:block/finish/{f}_machine_side',
                            'top': f'{NS}:block/finish/{f}_machine_top'}})
    # lang
    for k, val in LANG.items():
        fail_dash(val)
    write(os.path.join(A, 'lang/en_us.json'), dict(sorted(LANG.items())))
    # tags
    tags = {}
    def tag(path, n):
        tags.setdefault(path, []).append(n)
    for b in BLOCKS:
        n = f'{NS}:{b["name"]}'
        if 'pickaxe' in b['tags']: tag('data/minecraft/tags/block/mineable/pickaxe.json', n)
        if 'climbable' in b['tags']: tag('data/minecraft/tags/block/climbable.json', n)
        if 'hull' in b['tags']:
            for t in ('data/minecraft/tags/block/wither_immune.json', 'data/minecraft/tags/block/dragon_immune.json',
                      'data/simplemachines/tags/block/hull.json'):
                tag(t, n)
        if 'bulkhead' in b['tags']: tag('data/simplemachines/tags/block/bulkheads.json', n)
    tag('data/simplemachines/tags/item/ship_tools.json', f'{NS}:ship_tool')
    tag('data/simplemachines/tags/item/ship_tools.json', f'{NS}:repair_kit')
    tag('data/simplemachines/tags/item/repair_kits.json', f'{NS}:repair_kit')
    shutil.rmtree(os.path.join(RES, 'data/minecraft/tags'), ignore_errors=True)
    shutil.rmtree(os.path.join(D, 'tags'), ignore_errors=True)
    for path, vals in tags.items():
        write(os.path.join(RES, path), {'replace': False, 'values': vals})
    # repairs and occupants
    write(os.path.join(D, 'simplemachines/repairs/ship_blocks.json'),
          {'pairs': [{'broken': f'{NS}:{a}', 'fixed': c if ':' in c else f'{NS}:{c}'} for a, c in REPAIRS]})
    write(os.path.join(D, 'simplemachines/cryopod_occupants/crew.json'),
          {'occupants': [f'skin:{n}' for n in SKINS]})
    # manual
    mdir = os.path.join(A, 'simplebooks/books/manual/pages')
    for f in os.listdir(mdir) if os.path.isdir(mdir) else []:
        os.remove(os.path.join(mdir, f))
    for pid, pg in manual().items():
        write(os.path.join(mdir, pid + '.json'), pg)
    write(os.path.join(A, 'simplebooks/books/manual.json'),
          {'sections': [{'id': 'ship', 'title': 'Ship Blocks', 'icon': f'{NS}:cryopod_bed'}]})
    write(JAVA, java())
    # every translation key the Java uses must be in the lang file
    import re
    used = set()
    for dp, _, fs in os.walk(os.path.join(ROOT, 'src/ship/java')):
        for f in fs:
            used |= set(re.findall(r'"((?:message|commands|tooltip|screen|item|itemGroup)\.simplemachines\.[a-z_.]+)"', open(os.path.join(dp, f)).read()))
    missing = sorted(k for k in used if k not in LANG)
    assert not missing, missing
    print(f'{len(BLOCKS)} blocks, {len(TEXTURES)} textures, {len(RECIPES) + len(EXTRA_RECIPES) + 3} recipes, {len(LANG)} lang keys')

if __name__ == '__main__':
    main()
