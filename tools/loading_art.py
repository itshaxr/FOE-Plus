"""FOE+'s loading screen pictures: the F, O and E, the "+", and the tagline, drawn large and smooth (FOE+ makes them the
right size for your screen as it starts).

python3 tools/loading_art.py [out dir]   (default: src/main/resources/assets/foeplus/loading)

The letters are FOE+'s own: plain geometric strokes of one weight with round ends, drawn here (no font). The tagline
uses Inter SemiBold (SIL Open Font License).
"""
import os, sys
from PIL import Image, ImageDraw, ImageFont

OUT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/foeplus/loading"
H = 512                 # cap height of the finished letters
SS = 4                  # drawn this many times bigger, then made smaller: smooth edges
W = round(H * 0.215)    # stroke weight
WHITE = (244, 247, 251, 255)
INTER = "/usr/share/fonts/opentype/inter/Inter-SemiBold.otf"


def canvas(w, h):
    img = Image.new("RGBA", (w * SS, h * SS), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def bar(d, x0, y0, x1, y1, fill, round_left=False, round_right=True):
    """A bar W tall; round ends where asked."""
    s = SS
    r = (y1 - y0) / 2
    d.rectangle([(x0 + (r if round_left else 0)) * s, y0 * s, (x1 - (r if round_right else 0)) * s, y1 * s], fill=fill)
    if round_left:
        d.ellipse([x0 * s, y0 * s, (x0 + 2 * r) * s, y1 * s], fill=fill)
    if round_right:
        d.ellipse([(x1 - 2 * r) * s, y0 * s, x1 * s, y1 * s], fill=fill)


def stem(d, fill, bottom_round=True):
    s = SS
    r = W / 2
    d.rectangle([0, r * s, W * s, (H - (r if bottom_round else 0)) * s], fill=fill)
    d.ellipse([0, 0, W * s, W * s], fill=fill)
    if bottom_round:
        d.ellipse([0, (H - W) * s, W * s, H * s], fill=fill)


def finish(img, w, h, name):
    img = img.resize((w, h), Image.LANCZOS)
    img.save(os.path.join(OUT, name), optimize=True)
    return img


def letter_f():
    w = round(H * 0.60)
    img, d = canvas(w, H)
    stem(d, WHITE)
    bar(d, 0, 0, w, W, WHITE, round_left=True)
    mid = round(H * 0.44)
    bar(d, 0, mid, round(w * 0.84), mid + W, WHITE)
    return finish(img, w, H, "f.png")


def letter_o():
    w = H
    img, d = canvas(w, H)
    s = SS
    d.ellipse([0, 0, w * s, H * s], fill=WHITE)
    d.ellipse([W * s, W * s, (w - W) * s, (H - W) * s], fill=(0, 0, 0, 0))
    return finish(img, w, H, "o.png")


def letter_e():
    w = round(H * 0.62)
    img, d = canvas(w, H)
    stem(d, WHITE)
    bar(d, 0, 0, w, W, WHITE, round_left=True)
    mid = round((H - W) / 2)
    bar(d, 0, mid, round(w * 0.86), mid + W, WHITE)
    bar(d, 0, H - W, w, H, WHITE, round_left=True)
    return finish(img, w, H, "e.png")


def plus():
    """The "+", white (FOE+ tints it): arms of the letters' weight, round ends."""
    size = round(H * 0.62)
    img, d = canvas(size, size)
    t = W * 0.92
    a = (size - t) / 2
    bar(d, 0, a, size, a + t, WHITE, round_left=True)
    s = SS
    r = t / 2
    d.rectangle([a * s, r * s, (a + t) * s, (size - r) * s], fill=WHITE)
    d.ellipse([a * s, 0, (a + t) * s, t * s], fill=WHITE)
    d.ellipse([a * s, (size - t) * s, (a + t) * s, size * s], fill=WHITE)
    return finish(img, size, size, "plus.png")


def tagline(text="FOR FISHONMC", px=96, tracking=0.32):
    font = ImageFont.truetype(INTER, px * SS)
    widths = [font.getlength(c) for c in text]
    gap = px * SS * tracking
    total = sum(widths) + gap * (len(text) - 1)
    asc, desc = font.getmetrics()
    w, h = int(total / SS) + 4, int((asc + desc) / SS) + 4
    img = Image.new("RGBA", (w * SS, h * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    x = 2 * SS
    for c, cw in zip(text, widths):
        d.text((x, 2 * SS), c, font=font, fill=WHITE)
        x += cw + gap
    img = img.crop(img.getbbox())
    return finish(img, img.width // SS, img.height // SS, "tagline.png")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for f in (letter_f, letter_o, letter_e, plus, tagline):
        im = f()
        print(f.__name__, im.size)
