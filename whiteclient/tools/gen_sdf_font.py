#!/usr/bin/env python3
"""
Builds signed-distance-field font atlases for the shader text renderer (util/SdfText.java, shaders/core/ui_text.fsh).

usage: gen_sdf_font.py <assets/whiteclient dir>

For each font it writes
  textures/gui/font/sdf_<name>.png     RGBA, R = G = B = distance (0.5 = glyph edge), A = 255
  font/sdf_<name>.metrics              text table read by SdfText at start-up
Needs Pillow (with FreeType + raqm for kerning), numpy and scipy. Run it again only if you change the fonts / character set.
"""
import os, sys
import numpy as np
from PIL import Image, ImageFont, ImageDraw
from scipy.ndimage import distance_transform_edt

ROOT = sys.argv[1]
EM = 48            # texels per em in the atlas
HR = 8             # supersampling for the distance computation
SPREAD = 10        # texels of distance stored on each side of the edge
PAD = SPREAD + 1
CHARS = [chr(c) for c in range(32, 127)] + list("·•…×°→←↑↓")

FONTS = {
    "sans": "instrument_sans.ttf",
    "bold": "instrument_sans_bold.ttf",
    "serif": "instrument_serif.ttf",
    "serif_italic": "instrument_serif_italic.ttf",
}


def glyph_sdf(font_hr, ch):
    """Returns (cell array uint8, xoff, yoff_top, advance) in texel units, or None for blank glyphs."""
    adv = font_hr.getlength(ch) / HR
    l, t, r, b = font_hr.getbbox(ch, anchor="ls")          # relative to pen / baseline, hi-res px
    if r <= l or b <= t:
        return None, 0, 0, adv
    # cell in texels, snapped to the texel grid
    x0 = int(np.floor(l / HR)) - PAD
    y0 = int(np.floor(t / HR)) - PAD
    x1 = int(np.ceil(r / HR)) + PAD
    y1 = int(np.ceil(b / HR)) + PAD
    cw, chh = x1 - x0, y1 - y0
    img = Image.new("L", (cw * HR, chh * HR), 0)
    ImageDraw.Draw(img).text((-x0 * HR, -y0 * HR), ch, font=font_hr, fill=255, anchor="ls")
    m = np.asarray(img) >= 128
    inside = distance_transform_edt(m)
    outside = distance_transform_edt(~m)
    sd = (outside - inside)                                # > 0 outside the glyph (hi-res px)
    # sample at texel centres
    ys = (np.arange(chh) * HR + HR // 2)
    xs = (np.arange(cw) * HR + HR // 2)
    s = sd[np.ix_(ys, xs)] / HR                            # in texels
    v = np.clip(0.5 - s / (2.0 * SPREAD), 0.0, 1.0)
    return (v * 255.0 + 0.5).astype(np.uint8), x0, y0, adv


def build(name, ttf):
    path = os.path.join(ROOT, "font", ttf)
    font_hr = ImageFont.truetype(path, EM * HR, layout_engine=ImageFont.Layout.RAQM)
    font_1 = ImageFont.truetype(path, EM * 64, layout_engine=ImageFont.Layout.RAQM)   # for kerning at fine precision
    glyphs = []
    for ch in CHARS:
        try:
            cell, xo, yo, adv = glyph_sdf(font_hr, ch)
        except Exception as e:                              # glyph missing in this font
            print("  skip", repr(ch), e)
            continue
        glyphs.append((ch, cell, xo, yo, adv))

    # shelf packing into a power-of-two atlas
    width = 512
    while True:
        x = y = rowh = 0
        place = []
        ok = True
        for ch, cell, xo, yo, adv in glyphs:
            if cell is None:
                place.append((0, 0))
                continue
            h, w = cell.shape
            if x + w > width:
                x = 0; y += rowh; rowh = 0
            place.append((x, y))
            x += w; rowh = max(rowh, h)
        height = y + rowh
        if height <= width:
            break
        width *= 2
    H = 1
    while H < height:
        H *= 2
    atlas = np.zeros((H, width), np.uint8)
    lines = []
    asc, desc = font_hr.getmetrics()
    lines.append("font %d %d %d %d %d %d" % (width, H, EM, SPREAD, round(asc / HR), round(desc / HR)))
    for (ch, cell, xo, yo, adv), (px, py) in zip(glyphs, place):
        if cell is None:
            lines.append("g %d 0 0 0 0 0 0 %.3f" % (ord(ch), adv))
            continue
        h, w = cell.shape
        atlas[py:py + h, px:px + w] = cell
        lines.append("g %d %d %d %d %d %d %d %.3f" % (ord(ch), px, py, w, h, xo, yo, adv))
    # kerning for letter / digit / punctuation pairs
    kern = 0
    base = {ch: font_1.getlength(ch) / 64.0 for ch, *_ in glyphs}
    for a, *_ in glyphs:
        if a == " ":
            continue
        for b, *_ in glyphs:
            if b == " ":
                continue
            k = font_1.getlength(a + b) / 64.0 - base[a] - base[b]
            if abs(k) >= 1.0:                                # ignore < 1 texel (0.02 em)
                lines.append("k %d %d %.2f" % (ord(a), ord(b), k))
                kern += 1
    rgba = np.zeros((H, width, 4), np.uint8)
    rgba[..., 0] = rgba[..., 1] = rgba[..., 2] = atlas
    rgba[..., 3] = 255
    out_png = os.path.join(ROOT, "textures", "gui", "font", "sdf_%s.png" % name)
    os.makedirs(os.path.dirname(out_png), exist_ok=True)
    Image.fromarray(rgba, "RGBA").save(out_png, optimize=True)
    out_m = os.path.join(ROOT, "font", "sdf_%s.metrics" % name)
    with open(out_m, "w") as f:
        f.write("\n".join(lines) + "\n")
    print("%s: %d glyphs, %d kerning pairs, atlas %dx%d" % (name, len(glyphs), kern, width, H))


for n, t in FONTS.items():
    build(n, t)
