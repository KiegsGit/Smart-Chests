#!/usr/bin/env python3
"""Generates the Smart Chests banner and icon (pixel art, nearest-neighbour upscaled).

Usage:  python3 tools/generate_branding.py        (needs Pillow)
Writes: docs/banner.png, docs/icon.png, src/main/resources/assets/smartchests/icon.png
"""
import math
import os
import random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ---------------------------------------------------------------- palette
OUTLINE = (5, 9, 30)
NAVY_TOP = (6, 10, 36)
NAVY_BOT = (20, 34, 98)
CREAM = (255, 244, 220)
PALE = (214, 244, 255)
ICE = (140, 226, 255)
CYAN = (63, 224, 255)
CYAN_D = (30, 167, 217)
BLUE = (43, 108, 255)
BLUE_D = (26, 63, 176)
BODY = (29, 44, 94)
BODY_L = (47, 74, 148)
BODY_D = (18, 28, 66)
STRAP = (27, 111, 201)
STRAP_L = (63, 184, 255)
WOOD = (90, 58, 34)
WOOD_L = (122, 82, 50)
WOOD_D = (58, 36, 20)
GOLD = (255, 205, 80)


class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.im = Image.new("RGBA", (w, h), (0, 0, 0, 255))

    def put(self, x, y, c, a=1.0):
        if 0 <= x < self.w and 0 <= y < self.h:
            if a >= 1.0:
                self.im.putpixel((x, y), tuple(c[:3]) + (255,))
            else:
                r, g, b, _ = self.im.getpixel((x, y))
                self.im.putpixel((x, y), (int(r + (c[0] - r) * a), int(g + (c[1] - g) * a),
                                          int(b + (c[2] - b) * a), 255))

    def rect(self, x, y, w, h, c, a=1.0):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.put(xx, yy, c, a)

    def sky(self):
        bayer = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]
        for y in range(self.h):
            t = y / (self.h - 1)
            for x in range(self.w):
                tt = min(1.0, max(0.0, t + (bayer[y % 4][x % 4] / 16.0 - 0.5) * 0.06))
                self.put(x, y, tuple(int(NAVY_TOP[i] + (NAVY_BOT[i] - NAVY_TOP[i]) * tt) for i in range(3)))

    def glow(self, cx, cy, rx, ry, c, strength=0.45):
        for y in range(int(cy - ry), int(cy + ry) + 1):
            for x in range(int(cx - rx), int(cx + rx) + 1):
                d = math.hypot((x - cx) / rx, (y - cy) / ry)
                if d < 1:
                    a = ((1 - d) ** 2) * strength
                    a = round(a * 24) / 24  # fine steps: no visible hard edge
                    if a > 0:
                        self.put(x, y, c, a)

    def sparkle(self, x, y, size=2, c=CREAM, arm=ICE):
        self.put(x, y, c)
        for i in range(1, size + 1):
            for dx, dy in ((i, 0), (-i, 0), (0, i), (0, -i)):
                self.put(x + dx, y + dy, c if i == 1 else arm, 1.0 if i == 1 else 0.8)

    def stars(self, seed, count, area):
        rnd = random.Random(seed)
        x0, y0, x1, y1 = area
        for _ in range(count):
            x, y = rnd.randint(x0, x1), rnd.randint(y0, y1)
            self.put(x, y, rnd.choice([CREAM, ICE, (120, 150, 255)]), rnd.choice([0.5, 0.8, 1.0]))

    def sprite(self, x, y, rows, pal):
        width = max(len(r) for r in rows)
        for j, row in enumerate(rows):
            for i, ch in enumerate(row.ljust(width, ".")):
                if ch != "." and ch in pal:
                    self.put(x + i, y + j, pal[ch])

    def scaled(self, k):
        return self.im.resize((self.w * k, self.h * k), Image.NEAREST).convert("RGB")


# ---------------------------------------------------------------- font (5x7)
FONT = {
    "S": [".###.", "#...#", "#....", ".###.", "....#", "#...#", ".###."],
    "M": ["#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"],
    "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "R": ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
    "T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
    "C": [".####", "#....", "#....", "#....", "#....", "#....", ".####"],
    "H": ["#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "E": ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
}


def text_mask(word, scale, spacing=1):
    cells = set()
    x = 0
    for ch in word:
        glyph = FONT[ch]
        for gy, row in enumerate(glyph):
            for gx, v in enumerate(row):
                if v == "#":
                    for sy in range(scale):
                        for sx in range(scale):
                            cells.add((x + gx * scale + sx, gy * scale + sy))
        x += (5 + spacing) * scale
    return cells, x - spacing * scale, 7 * scale


def draw_title(cv, word, ox, oy, scale):
    mask, w, h = text_mask(word, scale)
    shadow = set()
    for (x, y) in mask:
        for d in range(1, 3):
            shadow.add((x, y + d))
        shadow.add((x + 1, y + 2))
    shadow -= mask
    both = mask | shadow
    outline = set()
    for (x, y) in both:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                outline.add((x + dx, y + dy))
    outline -= both
    for (x, y) in outline:
        cv.put(ox + x, oy + y, OUTLINE)
    for (x, y) in shadow:
        cv.put(ox + x, oy + y, BLUE if y < h + 1 else BLUE_D)
    for (x, y) in mask:
        t = y / h
        c = CREAM if t < 0.45 else (PALE if t < 0.8 else ICE)
        cv.put(ox + x, oy + y, c)
    return w, h


# ---------------------------------------------------------------- sprites
def chest(cv, x0, y0, w, h, terminal=False, glow=True):
    lid = int(h * 0.42)
    if glow:
        cv.glow(x0 + w / 2, y0 + h / 2, w * 0.95, h * 0.95, CYAN, 0.30 if not terminal else 0.42)

    def inside(x, y):
        if x < 0 or y < 0 or x >= w or y >= h:
            return False
        if y == 0 and (x < 3 or x >= w - 3):
            return False
        if y == 1 and (x < 2 or x >= w - 2):
            return False
        if y == 2 and (x < 1 or x >= w - 1):
            return False
        return True

    rnd = random.Random(w * 31 + h)
    for y in range(h):
        for x in range(w):
            if not inside(x, y):
                continue
            border = not (inside(x - 1, y) and inside(x + 1, y) and inside(x, y - 1) and inside(x, y + 1))
            if border:
                cv.put(x0 + x, y0 + y, OUTLINE)
                continue
            if y == lid - 1:
                c = CYAN
            elif y == lid:
                c = BODY_D
            elif y < lid:
                c = BODY_L if y <= 2 else BODY
            else:
                c = BODY_D if y >= h - 3 else BODY
            if rnd.random() < 0.07 and c in (BODY, BODY_L):
                c = BODY_L if c == BODY else BODY
            if y not in (lid - 1, lid) and (3 <= x <= 4 or w - 5 <= x <= w - 4):
                c = STRAP_L if x in (3, w - 5) else STRAP
            cv.put(x0 + x, y0 + y, c)
    # rivets
    for rx in (3, w - 4):
        for ry in (3, h - 5):
            cv.put(x0 + rx, y0 + ry, ICE)
    cx = w // 2
    if terminal:
        sw, sh = w - 16, max(7, lid - 5)
        sx, sy = x0 + 8, y0 + 4
        cv.rect(sx - 1, sy - 1, sw + 2, sh + 2, OUTLINE)
        cv.rect(sx, sy, sw, sh, (8, 22, 58))
        cv.rect(sx + 1, sy + 1, sw - 2, 1, CYAN)                 # search bar top
        cv.rect(sx + 1, sy + 1, 1, 3, CYAN)
        cv.rect(sx + 3, sy + 2, sw // 2 - 3, 1, ICE)             # typed text
        for i in range(2):                                        # result rows
            cv.rect(sx + 2, sy + 4 + i * 2, 2, 1, GOLD if i == 0 else (90, 230, 150))
            cv.rect(sx + 5, sy + 4 + i * 2, sw - 9 - i * 3, 1, CYAN_D)
        cv.put(sx + sw - 3, sy + 2, ICE)
        cv.put(sx + sw - 2, sy + 3, ICE)
    # latch
    lw, lh = 6, 7
    lx, ly = x0 + cx - lw // 2, y0 + lid - 3
    cv.rect(lx - 1, ly - 1, lw + 2, lh + 2, OUTLINE)
    cv.rect(lx, ly, lw, lh, CYAN)
    cv.rect(lx, ly, lw, 1, PALE)
    cv.rect(lx + 2, ly + 2, 2, 3, OUTLINE)
    cv.put(lx + 2, ly + 2, (8, 22, 58))


DIAMOND = ["..oooooo..", ".oWLLMMDo.", "oWLLLMMMDo", "oLLLMMMDDo", ".oLLMMDDo.", "..oLMMDo..", "...oMMDo..", "....oDo..."]
DIAMOND_P = {"o": OUTLINE, "W": (235, 255, 255), "L": (120, 240, 255), "M": (50, 200, 240), "D": (25, 130, 200)}
EMERALD = ["..oooo..", ".oWLLMo.", "oWLLMMDo", "oLLMMMDo", "oLMMMDDo", "oMMMDDDo", ".oMDDDo.", "..oooo.."]
EMERALD_P = {"o": OUTLINE, "W": (210, 255, 220), "L": (100, 240, 150), "M": (30, 190, 100), "D": (16, 120, 70)}
INGOT = [".ooooooo..", "oWLLLLMMo.", "oLLLMMMDDo", "oMMMMDDDDo", ".oooooooo."]
INGOT_P = {"o": OUTLINE, "W": (255, 250, 200), "L": (255, 220, 100), "M": (240, 175, 40), "D": (180, 110, 20)}
DUST = ["...o.o....", "..oRRRo.o.", ".oRRrRRoRo", "oRRrRRRRRo", ".ooooooooo"]
DUST_P = {"o": OUTLINE, "R": (230, 40, 50), "r": (255, 110, 100)}


def magnifier(cv, x0, y0):
    cx, cy, r = x0 + 6.0, y0 + 6.0, 6.0
    for y in range(y0, y0 + 14):
        for x in range(x0, x0 + 14):
            d = math.hypot(x - cx + 0.5, y - cy + 0.5)
            if d <= r + 0.5 and d > r - 1.6:
                cv.put(x, y, CYAN if d < r - 0.4 else OUTLINE)
            elif d <= r - 1.6:
                cv.put(x, y, (120, 235, 255), 0.35)
    for i in range(5):  # handle
        cv.rect(x0 + 9 + i - 1, y0 + 9 + i - 1, 3, 3, OUTLINE)
    for i in range(5):
        cv.rect(x0 + 9 + i, y0 + 9 + i, 2, 2, WOOD_L if i % 2 else GOLD)
    cv.put(x0 + 3, y0 + 3, (255, 255, 255))
    cv.put(x0 + 4, y0 + 2, (255, 255, 255), 0.7)


def dotted_line(cv, x0, y0, x1, y1, c=CYAN, step=3):
    n = max(abs(x1 - x0), abs(y1 - y0))
    for i in range(0, n + 1, step):
        t = i / max(1, n)
        cv.put(round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), c, 0.9)


def moon(cv, cx, cy, r=7):
    for y in range(cy - r, cy + r + 1):
        for x in range(cx - r, cx + r + 1):
            if math.hypot(x - cx, y - cy) <= r and math.hypot(x - cx - 3, y - cy + 2) > r - 1:
                cv.put(x, y, (255, 236, 170) if x < cx else (240, 205, 120))


def orbit(cv, cx, cy, rx, ry, tilt, front, c1=(110, 140, 255), c2=CYAN):
    for i in range(0, 1440):
        a = i / 1440 * 2 * math.pi
        is_front = math.sin(a) > 0
        if is_front != front:
            continue
        ex, ey = rx * math.cos(a), ry * math.sin(a)
        x = cx + ex * math.cos(tilt) - ey * math.sin(tilt)
        y = cy + ex * math.sin(tilt) + ey * math.cos(tilt)
        t = (math.cos(a) + 1) / 2
        col = tuple(int(c1[k] + (c2[k] - c1[k]) * t) for k in range(3))
        cv.put(round(x), round(y), col, 0.9)


def shelf(cv, x0, y0, w):
    cv.rect(x0, y0, w, 11, OUTLINE)
    cv.rect(x0 + 1, y0 + 1, w - 2, 9, WOOD)
    cv.rect(x0 + 1, y0 + 1, w - 2, 2, WOOD_L)
    cv.rect(x0 + 1, y0 + 8, w - 2, 2, WOOD_D)
    rnd = random.Random(5)
    for _ in range(w // 3):
        cv.put(x0 + rnd.randint(2, w - 3), y0 + rnd.randint(3, 7), WOOD_D if rnd.random() < 0.5 else WOOD_L)


def flowers(cv, x, y, n=1):
    for k in range(n):
        fx, fy = x + k * 6, y - (k % 2)
        cv.rect(fx, fy + 2, 1, 3, (20, 90, 90))
        for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
            cv.put(fx + dx, fy + dy, (110, 200, 255))
        cv.put(fx, fy, GOLD)


# ---------------------------------------------------------------- scenes
def banner():
    W, H = 384, 128
    cv = Canvas(W, H)
    cv.sky()
    cv.glow(292, 84, 110, 60, BLUE, 0.35)
    cv.stars(7, 90, (0, 0, W - 1, 85))
    moon(cv, 22, 14, 8)
    for (x, y, s) in ((16, 30, 1), (120, 12, 2), (176, 40, 3), (214, 14, 1), (372, 26, 2), (150, 100, 1), (352, 60, 1)):
        cv.sparkle(x, y, s)

    # ground / hills (left) and bushes
    for x in range(0, W):
        top = 106 + int(5 * math.sin(x / 21.0) + 3 * math.sin(x / 7.0))
        for y in range(top, H):
            cv.put(x, y, (12, 30, 70) if y > top else (22, 60, 110))
    for x in range(0, 40):
        top = 96 + int(4 * math.sin(x / 5.0))
        for y in range(top, 112):
            cv.put(x, y, (10, 50, 70) if (x + y) % 5 else (14, 66, 84))
    flowers(cv, 10, 108, 3)
    cv.rect(52, 100, 3, 18, WOOD_D)
    cv.rect(44, 106, 28, 2, WOOD)

    # title with orbit ring
    tcx, tcy = 100, 62
    orbit(cv, tcx, tcy, 92, 30, -0.18, front=False)
    w1, _ = draw_title(cv, "SMART", 52, 26, 4)
    w2, _ = draw_title(cv, "CHESTS", 24, 60, 4)
    orbit(cv, tcx, tcy, 92, 30, -0.18, front=True)
    cv.sparkle(178, 32, 4, CREAM, ICE)
    cv.sparkle(40, 100, 3, CREAM, ICE)

    # shelf with the three chests
    sx0 = 214
    shelf(cv, sx0, 108, 164)
    base = 108
    chest(cv, 226, base - 26, 34, 26)
    chest(cv, 268, base - 36, 44, 36, terminal=True)
    chest(cv, 320, base - 26, 34, 26)
    # network links between chests
    for (a, b) in ((260, 268), (312, 320)):
        cv.rect(a, base - 14, b - a, 2, CYAN)
        cv.rect(a, base - 14, b - a, 1, PALE)
    # floating items + dotted lines to chests
    cv.sprite(237, 52, DIAMOND, DIAMOND_P)
    cv.sprite(331, 52, EMERALD, EMERALD_P)
    cv.sprite(358, 74, DUST, DUST_P)
    cv.sprite(201, 74, INGOT, INGOT_P)
    dotted_line(cv, 242, 62, 242, base - 28)
    dotted_line(cv, 335, 62, 335, base - 28)
    magnifier(cv, 283, 36)
    dotted_line(cv, 290, 52, 290, base - 38)
    cv.sparkle(266, 44, 2)
    cv.sparkle(322, 40, 3)
    flowers(cv, 218, 108, 1)
    flowers(cv, 362, 108, 2)
    return cv


def icon():
    W = 64
    cv = Canvas(W, W)
    cv.sky()
    cv.glow(32, 38, 36, 30, BLUE, 0.4)
    cv.stars(3, 26, (0, 0, W - 1, W - 1))
    orbit(cv, 32, 40, 29, 11, -0.35, front=False)
    chest(cv, 11, 26, 42, 32, terminal=True)
    orbit(cv, 32, 40, 29, 11, -0.35, front=True)
    cv.sprite(7, 9, DIAMOND, DIAMOND_P)
    magnifier(cv, 40, 6)
    cv.sparkle(30, 12, 3)
    cv.sparkle(57, 33, 2)
    cv.sparkle(6, 38, 2)
    cv.sparkle(18, 4, 1)
    return cv


# ================================================================ v2: closer to the Little Weather look
from PIL import ImageDraw, ImageFont

FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/google-fonts/Poppins-ExtraBold.ttf",
    "/usr/share/fonts/truetype/google-fonts/Poppins-Black.ttf",
    "/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]


def ttf_mask(word, size):
    """Chunky pixel-letter mask from a bold TrueType font (falls back to the built-in bitmap font)."""
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            font = ImageFont.truetype(path, size)
            l, t, r, b = font.getbbox(word)
            img = Image.new("L", (r - l + 4, b - t + 4), 0)
            ImageDraw.Draw(img).text((2 - l, 2 - t), word, font=font, fill=255)
            cells = {(x, y) for y in range(img.height) for x in range(img.width) if img.getpixel((x, y)) > 120}
            return cells, img.width, img.height
    return text_mask(word, max(2, size // 8))


def draw_mask_title(cv, mask, w, h, ox, oy):
    shadow = set()
    for (x, y) in mask:
        for d in (1, 2, 3):
            shadow.add((x, y + d))
        shadow.add((x + 1, y + 3))
    shadow -= mask
    both = mask | shadow
    outline = set()
    for (x, y) in both:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                outline.add((x + dx, y + dy))
    outline -= both
    for (x, y) in outline:
        cv.put(ox + x, oy + y, OUTLINE)
    for (x, y) in shadow:
        cv.put(ox + x, oy + y, BLUE if y < h + 1 else BLUE_D)
    for (x, y) in mask:
        t = y / max(1, h)
        c = CREAM if t < 0.42 else ((205, 236, 255) if t < 0.72 else (120, 205, 255))
        cv.put(ox + x, oy + y, c)


def sky3(cv, stops):
    bayer = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]
    for y in range(cv.h):
        t = y / (cv.h - 1)
        for x in range(cv.w):
            tt = min(1.0, max(0.0, t + (bayer[y % 4][x % 4] / 16.0 - 0.5) * 0.07))
            for i in range(len(stops) - 1):
                a, ca = stops[i]
                b, cb = stops[i + 1]
                if a <= tt <= b:
                    f = (tt - a) / (b - a)
                    cv.put(x, y, tuple(int(ca[k] + (cb[k] - ca[k]) * f) for k in range(3)))
                    break


def cloud(cv, x, y, w, c1=(66, 56, 150), c2=(98, 86, 190), c3=(30, 28, 90)):
    blobs = [(0.2, 0.6, 0.22), (0.4, 0.4, 0.28), (0.65, 0.5, 0.26), (0.85, 0.65, 0.18)]
    for yy in range(int(w * 0.5)):
        for xx in range(w):
            for (bx, by, br) in blobs:
                if math.hypot(xx - bx * w, yy - by * w * 0.5) < br * w * 0.55 + 1:
                    top = math.hypot(xx - bx * w, yy - by * w * 0.5 + 2) >= br * w * 0.55
                    cv.put(x + xx, y + yy, c2 if top else (c1 if yy < w * 0.3 else c3))
                    break


def beam(cv, cx, y0, y1, half, c=CYAN, strength=0.30):
    for y in range(y0, y1):
        t = (y - y0) / max(1, (y1 - y0))
        hw = half * (0.35 + 0.65 * t)
        a = round(strength * (1 - t) * 12) / 12
        if a <= 0:
            continue
        for x in range(int(cx - hw), int(cx + hw) + 1):
            cv.put(x, y, c, a * (1.0 if abs(x - cx) < hw * 0.5 else 0.5))


def cottage(cv, x, base):
    w = 26
    cv.rect(x, base - 14, w, 14, (44, 34, 78)); cv.rect(x + 1, base - 13, w - 2, 12, (66, 50, 104))
    for i in range(16):  # roof
        cv.rect(x - 2 + i // 2, base - 14 - 15 + i, w + 4 - i, 1, (28, 22, 66) if i % 3 else (40, 30, 90))
    cv.rect(x + 3, base - 10, 5, 5, OUTLINE); cv.rect(x + 4, base - 9, 3, 3, GOLD)
    cv.rect(x + 17, base - 10, 5, 5, OUTLINE); cv.rect(x + 18, base - 9, 3, 3, GOLD)
    cv.rect(x + 11, base - 9, 4, 9, OUTLINE); cv.rect(x + 12, base - 8, 2, 8, (150, 100, 40))
    cv.glow(x + 5, base - 7, 8, 8, GOLD, 0.35); cv.glow(x + 19, base - 7, 8, 8, GOLD, 0.35)
    cv.rect(x + w - 6, base - 34, 3, 8, (28, 22, 66))


def tree(cv, x, base, h=26):
    cv.rect(x + h // 4, base - 6, 3, 8, WOOD_D)
    for i, (dx, dy, r) in enumerate(((0, 0, 7), (-5, 5, 6), (5, 5, 6), (0, 9, 8))):
        for yy in range(-r, r + 1):
            for xx in range(-r, r + 1):
                if xx * xx + yy * yy <= r * r:
                    cv.put(x + h // 4 + dx + xx, base - h + dy + yy, (16, 56, 70) if yy > -r // 2 else (24, 84, 96))


def banner():  # noqa: F811
    W, H = 384, 128
    cv = Canvas(W, H)
    sky3(cv, [(0.0, (8, 10, 40)), (0.45, (22, 24, 92)), (0.78, (58, 44, 138)), (1.0, (96, 70, 170))])
    cv.glow(300, 92, 120, 60, CYAN, 0.30)
    cv.glow(90, 70, 110, 55, (120, 90, 230), 0.22)
    cv.stars(7, 110, (0, 0, W - 1, 80))
    moon(cv, 24, 17, 9)
    cloud(cv, 196, 8, 44); cloud(cv, 300, 4, 54); cloud(cv, 340, 22, 36, (50, 44, 130), (80, 72, 170))
    for (x, y, s) in ((16, 38, 1), (126, 14, 2), (178, 40, 3), (222, 28, 1), (372, 40, 2), (150, 104, 1), (60, 8, 2)):
        cv.sparkle(x, y, s)

    # rolling twilight hills
    for x in range(W):
        top = 104 + int(5 * math.sin(x / 21.0) + 3 * math.sin(x / 7.0))
        for y in range(top, H):
            cv.put(x, y, (14, 28, 74) if y > top + 1 else (36, 70, 130))
    for x in range(0, 70):
        top = 108 + int(3 * math.sin(x / 6.0))
        for y in range(top, H):
            cv.put(x, y, (12, 52, 70) if (x + y) % 6 else (18, 70, 90))
    tree(cv, 4, 104, 30); tree(cv, 28, 108, 22)
    cv.rect(58, 98, 3, 20, WOOD_D); cv.rect(48, 104, 26, 2, WOOD)
    flowers(cv, 12, 116, 4)

    # title with orbit ring
    orbit(cv, 112, 62, 98, 31, -0.16, front=False, c1=(130, 110, 255))
    for word, size, ox, oy in (("SMART", 30, 50, 22), ("CHESTS", 30, 28, 58)):
        mask, w, h = ttf_mask(word, size)
        draw_mask_title(cv, mask, w, h, ox, oy)
    orbit(cv, 112, 62, 98, 31, -0.16, front=True, c1=(130, 110, 255))
    cv.sparkle(196, 34, 4); cv.sparkle(36, 96, 3); cv.sparkle(214, 80, 2)

    # shelf with three chests, beams, floating items
    shelf(cv, 212, 108, 150)
    base = 108
    beam(cv, 243, 46, base - 26, 9); beam(cv, 290, 30, base - 36, 11); beam(cv, 339, 46, base - 26, 9)
    chest(cv, 226, base - 26, 34, 26)
    chest(cv, 268, base - 36, 44, 36, terminal=True)
    chest(cv, 320, base - 26, 34, 26)
    for (a, b) in ((260, 268), (312, 320)):
        cv.rect(a, base - 14, b - a, 2, CYAN); cv.rect(a, base - 14, b - a, 1, PALE)
    cv.sprite(237, 50, DIAMOND, DIAMOND_P)
    cv.sprite(333, 50, EMERALD, EMERALD_P)
    cv.sprite(205, 74, INGOT, INGOT_P)
    cv.sprite(362, 70, DUST, DUST_P)
    magnifier(cv, 283, 30)
    dotted_line(cv, 242, 60, 242, base - 28); dotted_line(cv, 337, 60, 337, base - 28); dotted_line(cv, 290, 46, 290, base - 38)
    cv.sparkle(264, 40, 2); cv.sparkle(318, 36, 3); cv.sparkle(350, 56, 1)
    flowers(cv, 218, 108, 1); flowers(cv, 358, 108, 3)
    cottage(cv, 354, 106)
    return cv


def icon():  # noqa: F811
    W = 64
    cv = Canvas(W, W)
    sky3(cv, [(0.0, (8, 10, 40)), (0.55, (24, 28, 100)), (1.0, (70, 54, 150))])
    cv.glow(32, 40, 38, 32, CYAN, 0.38)
    cv.stars(3, 24, (0, 0, W - 1, W - 1))
    cloud(cv, -6, 44, 30, (38, 34, 110), (66, 58, 150), (22, 20, 70)); cloud(cv, 40, 48, 30, (38, 34, 110), (66, 58, 150), (22, 20, 70))
    beam(cv, 32, 6, 28, 7, strength=0.28)
    orbit(cv, 32, 41, 29, 11, -0.35, front=False, c1=(130, 110, 255))
    chest(cv, 11, 26, 42, 32, terminal=True)
    orbit(cv, 32, 41, 29, 11, -0.35, front=True, c1=(130, 110, 255))
    cv.sprite(7, 9, DIAMOND, DIAMOND_P)
    magnifier(cv, 40, 6)
    cv.sparkle(30, 12, 3); cv.sparkle(57, 33, 2); cv.sparkle(6, 38, 2); cv.sparkle(18, 4, 1)
    return cv



def main():
    docs = os.path.join(ROOT, "docs")
    assets = os.path.join(ROOT, "src", "main", "resources", "assets", "smartchests")
    os.makedirs(docs, exist_ok=True)
    os.makedirs(assets, exist_ok=True)
    banner().scaled(4).save(os.path.join(docs, "banner.png"))
    ic = icon()
    ic.scaled(8).save(os.path.join(docs, "icon.png"))
    ic.scaled(2).save(os.path.join(assets, "icon.png"))
    print("branding written")


if __name__ == "__main__":
    main()
