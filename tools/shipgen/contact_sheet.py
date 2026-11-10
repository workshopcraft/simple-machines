#!/usr/bin/env python3
"""Contact sheet of every 2.3 ship texture: python3 tools/shipgen/contact_sheet.py <out.png>"""
import glob, os, sys
from PIL import Image, ImageDraw
R = 'src/ship/resources/assets/simplemachines/textures'
files = sorted(glob.glob(R + '/block/**/*.png', recursive=True)) + sorted(glob.glob(R + '/item/*.png'))
skins = sorted(glob.glob(R + '/entity/cryopod/*.png'))
S, cols, cw = 64, 10, 150
ch = S + 26
rows = (len(files) + cols - 1) // cols
H = 40 + rows * ch + 40 + 150
im = Image.new('RGBA', (cols * cw, H), (40, 44, 52, 255))
d = ImageDraw.Draw(im)
d.text((8, 8), 'Simple Machines 2.3.0-alpha.1 ship textures (first frame of animated ones). Placeholders for repainting.', fill=(255, 255, 255, 255))
for i, f in enumerate(files):
    t = Image.open(f).convert('RGBA')
    if t.height > t.width:
        t = t.crop((0, 0, t.width, t.width))
    t = t.resize((S, S), Image.NEAREST)
    x = (i % cols) * cw + (cw - S) // 2
    y = 40 + (i // cols) * ch
    bg = Image.new('RGBA', (S, S), (90, 96, 106, 255))
    for yy in range(0, S, 8):
        for xx in range(0, S, 8):
            if (xx // 8 + yy // 8) % 2:
                bg.paste((110, 116, 126, 255), (xx, yy, xx + 8, yy + 8))
    bg.alpha_composite(t)
    im.paste(bg, (x, y))
    name = os.path.relpath(f, R)[:-4].replace('block/', '').replace('item/', 'item:')
    d.text(((i % cols) * cw + 4, y + S + 3), name[:24], fill=(220, 225, 230, 255))
y0 = 40 + rows * ch + 10
d.text((8, y0), 'Bundled cryopod crew skins (64x64 player skin layout):', fill=(255, 255, 255, 255))
for i, f in enumerate(skins):
    im.alpha_composite(Image.open(f).convert('RGBA').resize((128, 128), Image.NEAREST), (10 + i * 150, y0 + 16))
    d.text((10 + i * 150, y0 + 148), os.path.basename(f)[:-4], fill=(255, 255, 255, 255))
im.save(sys.argv[1])
print(len(files), 'textures', im.size)
