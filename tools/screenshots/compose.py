from PIL import Image, ImageDraw
def thor(top, bottom, out):
    T = Image.open(top).convert('RGB'); B = Image.open(bottom).convert('RGB')
    tw, th = T.size; bw, bh = B.size              # 1400x786, 902x786
    pad = 48; W = tw + 2 * pad
    lid_h = th + 2 * pad; base_h = bh + 2 * pad
    gap = 26
    im = Image.new('RGBA', (W + 40, lid_h + gap + base_h + 40), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    ox, oy = 20, 20
    body = (34, 38, 43)
    d.rounded_rectangle([ox, oy, ox + W, oy + lid_h], 56, fill=body)
    d.rounded_rectangle([ox + pad - 8, oy + pad - 8, ox + pad + tw + 8, oy + pad + th + 8], 22, fill=(10, 12, 14))
    im.paste(T, (ox + pad, oy + pad))
    by = oy + lid_h + gap
    d.rounded_rectangle([ox + 160, oy + lid_h - 4, ox + W - 160, by + 4], 10, fill=(24, 27, 31))
    d.rounded_rectangle([ox, by, ox + W, by + base_h], 56, fill=body)
    bx = ox + (W - bw) // 2
    d.rounded_rectangle([bx - 8, by + pad - 8, bx + bw + 8, by + pad + bh + 8], 22, fill=(10, 12, 14))
    im.paste(B, (bx, by + pad))
    # sticks, D-pad, face buttons on the base
    cxl = ox + (bx - ox) // 2; cxr = bx + bw + (ox + W - bx - bw) // 2
    for cx, cy in ((cxl, by + 230), (cxr, by + base_h - 230)):
        d.ellipse([cx - 78, cy - 78, cx + 78, cy + 78], fill=(20, 23, 27)); d.ellipse([cx - 52, cy - 52, cx + 52, cy + 52], fill=(48, 53, 60))
    cy = by + base_h - 230
    d.rounded_rectangle([cxl - 70, cy - 22, cxl + 70, cy + 22], 8, fill=(20, 23, 27)); d.rounded_rectangle([cxl - 22, cy - 70, cxl + 22, cy + 70], 8, fill=(20, 23, 27))
    cy = by + 230
    for dx, dy in ((0, -62), (-62, 0), (62, 0), (0, 62)):
        d.ellipse([cxr + dx - 27, cy + dy - 27, cxr + dx + 27, cy + dy + 27], fill=(20, 23, 27))
    im = im.resize((im.width // 2, im.height // 2), Image.LANCZOS)
    im.save(out)
thor('home_top.png', 'home_bottom.png', 'thor-home.png')
thor('library_top.png', 'library_bottom.png', 'thor-library.png')
thor('game_top.png', 'game_bottom.png', 'thor-game.png')
thor('ingame_top.png', 'ingame_bottom.png', 'thor-ingame.png')
s = Image.open('settings_top.png'); s.resize((s.width // 2 * 1, s.height // 2 * 1), Image.LANCZOS).save('settings.png')
