"""FOE+'s chummer bucket picture (32 x 32 pixel art), for the chummer HUDs: the server's grey metal bucket with its dark
band and red chum on top, drawn here at twice the detail of the server's 16-pixel textures.

python3 tools/chummer_icon.py [out file]   (default: src/main/resources/assets/foeplus/textures/gui/chummer_bucket.png)
"""
import os, random, sys
from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/foeplus/textures/gui/chummer_bucket.png"
S = 32
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
px = img.load()

OUT_C = (34, 32, 40)
# metal, dark to light
M = [(74, 72, 82), (96, 94, 104), (122, 120, 130), (150, 148, 158), (184, 182, 192), (214, 212, 220)]
BAND = [(44, 42, 50), (58, 56, 66), (76, 74, 86)]
CHUM_DARK, CHUM, CHUM_LIGHT, CHUM_PALE, CHUM_HI = (112, 22, 34), (164, 38, 50), (200, 62, 70), (232, 150, 140), (252, 210, 200)


def metal(t, lift=0):
    """Tone across the bucket (t 0 at the left edge, 1 at the right): lit from the front left."""
    if t < 0.08: i = 1
    elif t < 0.18: i = 2
    elif t < 0.26: i = 3
    elif t < 0.40: i = 4
    elif t < 0.50: i = 3
    elif t < 0.74: i = 2
    elif t < 0.90: i = 1
    else: i = 0
    return M[max(0, min(len(M) - 1, i + lift))]


def put(x, y, c):
    if 0 <= x < S and 0 <= y < S:
        px[x, y] = c + (255,) if len(c) == 3 else c


# body: rows 9..29, 26 wide at the top to 22 at the bottom
TOP, BOT = 9, 29
def span(y):
    k = (y - TOP) / (BOT - TOP)
    half = 13 - 2 * k
    return round(16 - half), round(15 + half)

for y in range(TOP, BOT + 1):
    x0, x1 = span(y)
    for x in range(x0, x1 + 1):
        t = (x - x0) / (x1 - x0)
        put(x, y, metal(t))
    put(x0 - 1, y, OUT_C)
    put(x1 + 1, y, OUT_C)
# a dark foot, then the outline under it
x0, x1 = span(BOT)
for x in range(x0, x1 + 1):
    t = (x - x0) / (x1 - x0)
    put(x, BOT, metal(t, -1) if metal(t) != M[0] else M[0])
    put(x, BOT + 1, OUT_C)
for x in range(x0, x1 + 1):
    t = (x - x0) / (x1 - x0)
    c = metal(t)
    put(x, BOT, (max(0, c[0] - 26), max(0, c[1] - 26), max(0, c[2] - 24)))

# the band: three rows, raised a pixel at each side
for y, tone in ((19, 2), (20, 1), (21, 0)):
    x0, x1 = span(y)
    for x in range(x0 - 1, x1 + 2):
        t = (x - x0) / (x1 - x0)
        hi = 0.18 <= t < 0.40
        put(x, y, BAND[min(2, tone + (1 if hi else 0))])
    put(x0 - 2, y, OUT_C)
    put(x1 + 2, y, OUT_C)
# a light line along the top of the band, a shadow line under it
for x in range(span(18)[0], span(18)[1] + 1):
    t = (x - span(18)[0]) / (span(18)[1] - span(18)[0])
    put(x, 18, metal(t, 1))
for x in range(span(22)[0], span(22)[1] + 1):
    t = (x - span(22)[0]) / (span(22)[1] - span(22)[0])
    put(x, 22, metal(t, -1))

# the rim: an oval lip seen from a little above (outer 28 x 9, two pixels of metal), outlined
cx, cy = 15.5, 6.0
def in_oval(x, y, rx, ry):
    return ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0

RX, RY = 14.0, 4.6
for y in range(0, 13):
    for x in range(0, S):
        if in_oval(x, y, RX, RY):
            if not in_oval(x, y, RX - 2.0, RY - 1.4):
                t = (x - (cx - RX)) / (2 * RX)
                front = y >= cy
                put(x, y, metal(t, 2 if not front else 1))
            else:
                # inside: the far wall (dark metal) at the back, chum filling the rest
                if not in_oval(x, y + 1.6, RX - 2.4, RY - 1.8):
                    put(x, y, M[1])
                else:
                    put(x, y, CHUM)
# outline the lip where it meets the outside
for y in range(0, 13):
    for x in range(0, S):
        if in_oval(x, y, RX, RY):
            for dx, dy in ((1, 0), (-1, 0), (0, -1), (0, 1)):
                nx, ny = x + dx, y + dy
                if not in_oval(nx, ny, RX, RY) and (ny < TOP or not (span(ny)[0] <= nx <= span(ny)[1])):
                    put(x, y, OUT_C)
                    break

# the chum: darker towards the back, lumps and a few pale fish bits
random.seed(5)
for y in range(0, 13):
    for x in range(0, S):
        if px[x, y][:3] == CHUM:
            back = y <= cy - 1
            r = random.random()
            if back:
                c = CHUM_DARK if r < 0.55 else CHUM
            else:
                c = CHUM_DARK if r < 0.18 else CHUM if r < 0.62 else CHUM_LIGHT
            put(x, y, c)
for (x, y) in ((9, 6), (15, 5), (20, 6), (12, 8), (18, 8), (23, 7)):
    if px[x, y][:3] in (CHUM_DARK, CHUM, CHUM_LIGHT):
        put(x, y, CHUM_PALE)
for (x, y) in ((10, 6), (16, 5)):
    if px[x, y][:3] in (CHUM_DARK, CHUM, CHUM_LIGHT, CHUM_PALE):
        put(x, y, CHUM_HI)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
print("saved", OUT)
