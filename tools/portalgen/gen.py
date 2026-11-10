"""Generates the 2.3 portal core assets under src/portal: textures, models, blockstates, item definitions,
GUI textures, lang and manual pages. Data (presets, cards, research, recipes, loot, tags, worldgen) is hand written
JSON under src/portal/resources/data. Run: python3 tools/portalgen/gen.py"""
import json, os, re, sys
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
from tex import *

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../..'))
A = os.path.join(ROOT, 'src/portal/resources/assets/simplemachines')
NS = 'simplemachines'

def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')

def save(img, rel):
    p = os.path.join(A, 'textures', rel + '.png')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)

def textures():
    save(recolor(load(SM + 'item/steel_ingot.png'), TITANIUM), 'item/titanium_ingot')
    save(recolor(load(SM + 'item/iron_dust.png'), TITANIUM), 'item/titanium_dust')
    save(recolor(load(SM + 'item/steel_sheet.png'), TITANIUM), 'item/titanium_sheet')
    save(recolor(load(SM + 'item/steel_rod.png'), TITANIUM), 'item/titanium_rod')
    save(recolor(load(MC + 'item/raw_iron.png'), TITANIUM), 'item/raw_titanium')
    save(ore('stone.png', 'iron_ore.png'), 'block/titanium_ore')
    save(ore('deepslate.png', 'deepslate_iron_ore.png'), 'block/deepslate_titanium_ore')
    save(recolor(load(MC + 'block/iron_block.png'), TITANIUM_BLOCK), 'block/titanium_block')
    save(recolor(load(MC + 'block/iron_block.png'), STEEL_BLOCK), 'block/steel_block')
    save(pellet(), 'item/entangled_pellet')
    save(recolor(load(SM + 'item/diamond_core.png'), ENTANGLED), 'item/entangled_core')
    card = load(SM + 'item/link_card.png')
    for name, rp in (('jump_card', TEAL), ('story_card', GOLD), ('nether_card', NETHER), ('end_card', END)):
        save(recolor(card, rp), 'item/' + name)
    save(scanner(), 'item/mob_scanner')
    side = load(SM + 'block/core_side.png')
    top = load(SM + 'block/core_top.png') if os.path.exists(SM + 'block/core_top.png') else side
    save(side, 'block/portal_machine_side')
    save(top, 'block/portal_machine_top')
    save(machine_face(side, ENTANGLED, False, 1, 'gate'), 'block/portal_gate_controller_front')
    save(machine_face(side, ENTANGLED, True, 1, 'gate'), 'block/portal_gate_controller_front_on')
    save(machine_face(side, TEAL, False, 2, 'research'), 'block/research_station_front')
    save(machine_face(side, TEAL, True, 2, 'research'), 'block/research_station_front_on')
    save(field_frames(), 'block/portal_field')
    with open(os.path.join(A, 'textures/block/portal_field.png.mcmeta'), 'w') as f:
        json.dump({'animation': {'frametime': 3, 'interpolate': True}}, f)
    save(crystal(False), 'block/objective_core')
    save(crystal(True), 'block/objective_core_spent')
    save(beacon_tex(), 'block/return_beacon')
    for half in ('lower', 'upper'):
        for low in (False, True):
            name = f'block/return_rift_{half}' + ('_low' if low else '')
            save(rift_frames(half, low), name)
            with open(os.path.join(A, 'textures', name + '.png.mcmeta'), 'w') as f:
                json.dump({'animation': {'frametime': 2 if low else 3, 'interpolate': not low}}, f)
    guis()

def slot(px, sx, sy):
    for y in range(sy - 1, sy + 17):
        for x in range(sx - 1, sx + 17):
            if x == sx - 1 or y == sy - 1:
                c = (55, 55, 55, 255)
            elif x == sx + 16 or y == sy + 16:
                c = (255, 255, 255, 255)
            else:
                c = (139, 139, 139, 255)
            px[x, y] = c

def guis():
    base = load(SM + 'gui/electric_furnace.png')
    for name, slots in (('portal_gate_controller', [(8, 18), (8, 40), (8, 62), (26, 62), (44, 62)]),
                        ('research_station', [(26, 53), (116, 53), (26, 20)])):
        im = base.copy()
        px = im.load()
        for y in range(14, 82):
            for x in range(7, 150):
                px[x, y] = (198, 198, 198, 255)
        for s in slots:
            slot(px, *s)
        save(im, 'gui/' + name)

def cube(name, all_tex=None):
    write(os.path.join(A, f'models/block/{name}.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/{all_tex or name}'}})

def item_def(name, model):
    write(os.path.join(A, f'items/{name}.json'), {'model': {'type': 'minecraft:model', 'model': model}})

def flat(name):
    write(os.path.join(A, f'models/item/{name}.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{name}'}})
    item_def(name, f'{NS}:item/{name}')

def simple_block(name):
    cube(name)
    write(os.path.join(A, f'blockstates/{name}.json'), {'variants': {'': {'model': f'{NS}:block/{name}'}}})
    item_def(name, f'{NS}:block/{name}')

def machine(name):
    for lit in (False, True):
        suffix = '_on' if lit else ''
        write(os.path.join(A, f'models/block/{name}{suffix}.json'), {'parent': 'minecraft:block/orientable', 'textures': {
            'front': f'{NS}:block/{name}_front{suffix}', 'side': f'{NS}:block/portal_machine_side', 'top': f'{NS}:block/portal_machine_top'}})
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    variants = {}
    for f, r in rot.items():
        for lit in ('false', 'true'):
            v = {'model': f'{NS}:block/{name}' + ('_on' if lit == 'true' else '')}
            if r:
                v['y'] = r
            variants[f'facing={f},lit={lit}'] = v
    write(os.path.join(A, f'blockstates/{name}.json'), {'variants': variants})
    item_def(name, f'{NS}:block/{name}')

def models():
    for b in ('titanium_ore', 'deepslate_titanium_ore', 'titanium_block', 'steel_block', 'return_beacon'):
        simple_block(b)
    for i in ('raw_titanium', 'titanium_dust', 'titanium_ingot', 'titanium_sheet', 'titanium_rod', 'entangled_pellet',
              'entangled_core', 'jump_card', 'story_card', 'nether_card', 'end_card', 'mob_scanner'):
        flat(i)
    machine('portal_gate_controller')
    machine('research_station')
    # return beacon: a short pillar
    write(os.path.join(A, 'models/block/return_beacon.json'), {'parent': 'minecraft:block/block', 'textures': {
        'side': f'{NS}:block/return_beacon', 'particle': f'{NS}:block/return_beacon'}, 'elements': [
        {'from': [4, 0, 4], 'to': [12, 14, 12], 'faces': {d: {'texture': '#side', **({'cullface': 'down'} if d == 'down' else {})}
                                                          for d in ('north', 'south', 'east', 'west', 'up', 'down')}}]})
    cube('objective_core')
    cube('objective_core_spent')
    write(os.path.join(A, 'blockstates/objective_core.json'), {'variants': {
        'spent=false': {'model': f'{NS}:block/objective_core'}, 'spent=true': {'model': f'{NS}:block/objective_core_spent'}}})
    item_def('objective_core', f'{NS}:block/objective_core')
    for axis, parent in (('ns', 'nether_portal_ns'), ('ew', 'nether_portal_ew')):
        write(os.path.join(A, f'models/block/portal_field_{axis}.json'), {'parent': f'minecraft:block/{parent}', 'textures': {
            'particle': f'{NS}:block/portal_field', 'portal': f'{NS}:block/portal_field'}})
    write(os.path.join(A, 'blockstates/portal_field.json'), {'variants': {
        'axis=x': {'model': f'{NS}:block/portal_field_ns'}, 'axis=z': {'model': f'{NS}:block/portal_field_ew'}}})
    # return rift: a thin see-through sheet, two blocks tall, no frame
    variants = {}
    for half in ('lower', 'upper'):
        for low in (False, True):
            tex = f'{NS}:block/return_rift_{half}' + ('_low' if low else '')
            for axis, parent in (('ns', 'nether_portal_ns'), ('ew', 'nether_portal_ew')):
                m = f'return_rift_{half}_{axis}' + ('_low' if low else '')
                write(os.path.join(A, f'models/block/{m}.json'), {'parent': f'minecraft:block/{parent}', 'textures': {
                    'particle': tex, 'portal': tex}})
            for axis, mod in (('x', 'ns'), ('z', 'ew')):
                variants[f'axis={axis},half={half},low={str(low).lower()}'] = {
                    'model': f'{NS}:block/return_rift_{half}_{mod}' + ('_low' if low else '')}
    write(os.path.join(A, 'blockstates/return_rift.json'), {'variants': variants})

LANG = {}
def lang():
    L = LANG
    names = {
        'titanium_ore': 'Titanium Ore', 'deepslate_titanium_ore': 'Deepslate Titanium Ore', 'titanium_block': 'Block of Titanium',
        'steel_block': 'Block of Steel', 'portal_gate_controller': 'Portal Gate Controller', 'portal_field': 'Portal Field',
        'objective_core': 'Objective Core', 'return_beacon': 'Return Beacon', 'research_station': 'Research Station',
        'return_rift': 'Return Rift'}
    for k, v in names.items():
        L[f'block.{NS}.{k}'] = v
    items = {
        'raw_titanium': 'Raw Titanium', 'titanium_dust': 'Titanium Dust', 'titanium_ingot': 'Titanium Ingot',
        'titanium_sheet': 'Titanium Plate', 'titanium_rod': 'Titanium Rod', 'entangled_pellet': 'Entangled Pellet',
        'entangled_core': 'Entangled Core', 'jump_card': 'Jump Card', 'story_card': 'Story Card', 'nether_card': 'Nether Card',
        'end_card': 'End Card', 'mob_scanner': 'Mob Scanner'}
    for k, v in items.items():
        L[f'item.{NS}.{k}'] = v
    L[f'itemGroup.{NS}.portals'] = 'Simple Machines: Portals'
    L[f'container.{NS}.portal_gate_controller'] = 'Portal Gate Controller'
    L[f'container.{NS}.research_station'] = 'Research Station'
    with open(os.path.join(os.path.dirname(__file__), 'lang_extra.json')) as f:
        L.update(json.load(f))
    # names of the built-in cards and presets, from their data
    data = os.path.join(ROOT, f'src/portal/resources/data/{NS}/{NS}')
    for kind, prefix in (('portal_cards', 'portal_card'), ('portal_presets', 'portal_preset'), ('portal_research', 'portal_research')):
        base = os.path.join(data, kind)
        for dp, _, fs in os.walk(base):
            for f in fs:
                rel = os.path.relpath(os.path.join(dp, f), base)[:-5].replace(os.sep, '.')
                L[f'{prefix}.{NS}.{rel}'] = json.load(open(os.path.join(dp, f)))['name']
    write(os.path.join(A, 'lang/en_us.json'), dict(sorted(L.items())))

PAGES = [
    ('portal_titanium', 'Titanium', 'A metal above steel',
     'Titanium Ore is rare and found deep in the Overworld, below Y 0. Portal worlds can be much richer in it.\n\n'
     'Titanium is too hard for iron machines. Crush the ore and smelt the dust in steel framed machines (a steel Chassis). '
     'An iron framed machine will not start on it.\n\nPlates, rods and blocks are made the same way as steel ones.',
     [('process', ['#c:ores/titanium'], 'crusher', 'titanium_dust', 2), ('process', ['#c:raw_materials/titanium'], 'crusher', 'titanium_dust', 2),
      ('process', ['titanium_dust'], 'electric_furnace', 'titanium_ingot', 1), ('process', ['#c:ingots/titanium'], 'compressor', 'titanium_sheet', 1), ('process', ['#c:ingots/titanium'], 'rolling_mill', 'titanium_rod', 2),
      ('craft', 'titanium_block')]),
    ('portal_gate', 'Portal Gates', 'Build a frame, power it, jump',
     'Build a rectangle of frame blocks standing upright, with the Portal Gate Controller as one of its blocks. The inside must be empty, '
     'at least 2 wide and 3 tall.\n\nFrames: Iron, Steel or Titanium blocks (Netherite if the server allows it). Wood and other blocks do not form a gate. '
     'Some worlds need a better frame. Bigger frames need more power to open.\n\n'
     'The controller takes HV power only, from HV cables, the HV side of a transformer or an HV relay. '
     'LV or MV power browns it out: it sparks, loses some of its stored energy and will not open. Any LV or MV cable '
     'touching it burns out and can start a fire. A generator placed right against it browns it out too. '
     'Step power up with transformers first.\n\n'
     'Put an Entangled Core and a card in it, and Entangled Pellets for fuel. Each jump uses pellets and a lot of energy; '
     'rich, high tier worlds cost more. Press Open and walk through. A return rift opens where you land: walk into it to come home.\n\n'
     'On some servers the gate is run by the story. It has no card slot and only needs pellets and power; the destination is set for you. '
     'There the gate travels with a ship between star systems. Each world it opens to is a planet of the system the ship is in, '
     'and it stays the same planet every time you go back, until the ship moves on. Then those planets are gone for good. '
     'While the ship is between systems the gate shows In transit and will not open.\n\n'
     'A gate belongs to whoever placed it, like an entangled code. It starts public. Its owner can make it private with a screwdriver: '
     'then only they can open its screen, change its card, open or close it and drop its rift. Hoppers cannot put cards into a private gate. '
     'Anyone can still walk through an open gate or rift.',
     [('craft', 'portal_gate_controller'), ('craft', 'entangled_core'), ('craft', 'entangled_pellet')]),
    ('portal_rift', 'Return Rifts', 'The way home, while the fuel lasts',
     'There is no gate on the far side. The gate you left from holds a rift open beside the spot where you land. Walk into it to go back.\n\n'
     'Holding the rift open costs the gate energy every second and an Entangled Pellet every minute, from its own buffer and pellet slots. '
     'Keep the gate powered and stocked while you are away. Richer worlds cost more to hold.\n\n'
     'A counter at the top of the screen shows how long the fuel will hold the rift open. You see it on the far side and near the gate. '
     'It counts the energy and the pellets the gate has left, whichever runs out first.\n\n'
     'When the fuel is nearly gone the counter turns red and flashes, and the rift flickers and sparks. When it runs out, the rift collapses and you are stranded.\n\n'
     'If the ship leaves the system while you are still out there, the rift is gone as well. If you were logged out at the time, '
     'you are brought back to the gate when you next join.\n\n'
     'The rift closes by itself once everyone who came through has gone back. Close Rift on the gate drops it at once.',
     []),
    ('portal_cards', 'Cards and Worlds', 'Every card is one world',
     'A blank Jump Card makes a random world the first time it is used. After that the card is bound to that world for good and always goes back to it.\n\n'
     'Program a blank card at a Research Station to choose its world type or add ores and mobs you have researched. A bound card cannot be programmed.\n\n'
     'Nether and End Cards open to the Nether and the End.\n\n'
     'Story Cards come from quests. They lead to a world with an objective. Collect it to finish the story. If you come back without it, the next jump makes a fresh world to try again.',
     [('craft', 'jump_card'), ('craft', 'nether_card'), ('craft', 'end_card')]),
    ('portal_research', 'Research', 'Feed it to learn',
     'The Research Station is a powered machine that eats resources. Choose a project with the arrows and feed it the items it asks for. '
     'Research is yours alone, like an entangled code: a station belongs to whoever placed it, and everything it eats counts for its owner. '
     'When a project is done, its world types and resources unlock for you.\n\n'
     'Mob research needs scans. Charge a Mob Scanner in a charger and use it on a mob. Scans count for whoever holds the scanner, '
     'and each player can scan a mob once.\n\n'
     'A station starts public: anyone may use it to imprint cards with what its owner has learned. The owner can make it private with a screwdriver, '
     'and then only they can open it.\n\n'
     'Put a blank Jump Card in the card slot and press Imprint to program it with the chosen project. '
     'A gate also belongs to whoever placed it and picks world types from its owner\'s research.',
     [('craft', 'research_station'), ('craft', 'mob_scanner')]),
]

def recipe(name):
    p = os.path.join(ROOT, f'src/portal/resources/data/{NS}/recipe/{name}.json')
    return json.load(open(p)) if os.path.exists(p) else None

def ing_label(v):
    if isinstance(v, list):
        v = v[0]
    if isinstance(v, dict):
        v = v.get('item') or ('#' + v.get('tag', ''))
    if v.startswith('#'):
        parts = v[1:].split(':')[-1].split('/')
        kinds = {'plates': 'Plate', 'ingots': 'Ingot', 'rods': 'Rod', 'dusts': 'Dust', 'gems': '', 'storage_blocks': 'Block'}
        if len(parts) == 2 and parts[0] in kinds:
            return (parts[1].replace('_', ' ').title() + ' ' + kinds[parts[0]]).strip()
    v = v.lstrip('#')
    n = v.split(':')[-1].split('/')[-1]
    key = f'item.{NS}.{n}'
    return LANG.get(key) or LANG.get(f'block.{NS}.{n}') or n.replace('_', ' ').title()

def step(entry):
    if entry[0] == 'process':
        _, inputs, machine_id, out, count = entry
        fix = lambda s: s if s.startswith('#') or ':' in s else f'{NS}:{s}'
        st = {'kind': 'process', 'inputs': [fix(i) for i in inputs], 'appliance': f'{NS}:{machine_id}',
              'output': {'item': fix(out), 'count': count} if count > 1 else fix(out)}
        return st
    r = recipe(entry[1])
    if not r:
        return None
    shapeless = r.get('type') == 'minecraft:crafting_shapeless'
    if shapeless:
        # shown as a grid, the note says any shape works
        letters, key, cells = 'ABCDEFGHI', {}, []
        for ing in r['ingredients']:
            v = ing if isinstance(ing, str) else (ing.get('item') or '#' + ing.get('tag'))
            ch = next((c for c, x in key.items() if x == v), None) or letters[len(key)]
            key[ch] = v
            cells.append(ch)
        cells += [' '] * (-len(cells) % 3)
        r = dict(r, pattern=[''.join(cells[i:i + 3]) for i in range(0, len(cells), 3)], key=key)
    elif r.get('type') != 'minecraft:crafting_shaped':
        return None
    counts = {}
    for row in r['pattern']:
        for ch in row:
            if ch != ' ':
                counts[ch] = counts.get(ch, 0) + 1
    note = ('Crafting Table, any shape\n' if shapeless else 'Crafting Table\n') + ', '.join(f'{n}x {ing_label(r["key"][c])}' for c, n in counts.items())
    key = {}
    for c, v in r['key'].items():
        key[c] = v if isinstance(v, str) else (v.get('item') or '#' + v.get('tag'))
    out = r['result']
    return {'kind': 'craft_shaped', 'pattern': r['pattern'], 'key': key,
            'output': {'item': out['id'], 'count': out.get('count', 1)}, 'note': note,
            'title': ing_label(out['id'])}

def manual():
    mdir = os.path.join(A, 'simplebooks/books/manual/pages')
    os.makedirs(mdir, exist_ok=True)
    for f in os.listdir(mdir):
        os.remove(os.path.join(mdir, f))
    for i, (pid, title, sub, text, recs) in enumerate(PAGES):
        steps = [s for s in (step(r) for r in recs) if s]
        write(os.path.join(mdir, pid + '.json'), {'section': 'portals', 'sort': i + 1, 'title': title, 'subtitle': sub,
                                                  'text': text, 'right_title': 'Recipes', 'steps': steps})
    write(os.path.join(A, 'simplebooks/books/manual.json'),
          {'sections': [{'id': 'portals', 'title': 'Portals', 'icon': f'{NS}:portal_gate_controller'}]})

def check():
    used = set()
    for dp, _, fs in os.walk(os.path.join(ROOT, 'src/portal/java')):
        for f in fs:
            used |= set(re.findall(r'"((?:message|commands|tooltip|screen|item|itemGroup|container|gui|portal)\.simplemachines\.[a-z_.0-9]+)"',
                                   open(os.path.join(dp, f)).read()))
    main_lang = json.load(open(os.path.join(ROOT, 'src/main/resources/assets/simplemachines/lang/en_us.json')))
    missing = sorted(k for k in used if k not in LANG and k not in main_lang and not k.endswith('.') and not k.endswith('_'))
    if missing:
        print('MISSING LANG', missing)
        sys.exit(1)
    # every em dash or en dash is banned in player text
    for k, v in LANG.items():
        assert '\u2014' not in v and '\u2013' not in v, k

if __name__ == '__main__':
    textures()
    models()
    lang()
    manual()
    check()
    print('ok', len(LANG), 'lang keys')
