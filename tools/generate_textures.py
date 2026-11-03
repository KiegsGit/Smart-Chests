#!/usr/bin/env python3
"""Generates the 16x16 terminal block textures (needs Pillow). Output: assets/smartchests/textures/block/"""
import os, random
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "smartchests", "textures", "block")
os.makedirs(OUT, exist_ok=True)

OUTLINE = (5, 9, 30, 255); BODY = (29, 44, 94, 255); BODY_L = (47, 74, 148, 255); BODY_D = (18, 28, 66, 255)
CYAN = (63, 224, 255, 255); CYAN_D = (30, 167, 217, 255); ICE = (170, 240, 255, 255); SCREEN = (8, 22, 58, 255)
GOLD = (255, 205, 80, 255); GREEN = (90, 230, 150, 255); STRAP = (27, 111, 201, 255)

def base(seed):
    im = Image.new("RGBA", (16, 16), BODY)
    rnd = random.Random(seed)
    for y in range(16):
        for x in range(16):
            r = rnd.random()
            if r < 0.10: im.putpixel((x, y), BODY_L)
            elif r < 0.16: im.putpixel((x, y), BODY_D)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            im.putpixel(p, OUTLINE)
    return im

def rivets(im):
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        im.putpixel(p, ICE)

def side():
    im = base(1)
    for y in range(1, 15):
        for x in (3, 12):
            im.putpixel((x, y), STRAP)
        im.putpixel((4, y), CYAN_D); im.putpixel((11, y), CYAN_D)
    for x in range(1, 15):
        im.putpixel((x, 6), CYAN); im.putpixel((x, 7), BODY_D)
    rivets(im); return im

def top():
    im = base(2)
    for i in range(2, 14):
        for p in ((i, 2), (i, 13), (2, i), (13, i)):
            im.putpixel(p, CYAN_D)
    for y in range(3, 13):
        for x in range(3, 13):
            im.putpixel((x, y), SCREEN)
    for y in range(6, 10):
        for x in range(6, 10):
            im.putpixel((x, y), CYAN)
    im.putpixel((6, 6), ICE); im.putpixel((7, 6), ICE)
    return im

def bottom():
    im = base(3)
    for y in range(3, 13):
        for x in range(3, 13):
            im.putpixel((x, y), BODY_D)
    return im

def front():
    im = base(4)
    for y in range(1, 15):
        for x in (3, 12):
            im.putpixel((x, y), STRAP)
    # screen
    for y in range(2, 10):
        for x in range(5, 11):
            im.putpixel((x, y), SCREEN)
    for x in range(4, 12):
        im.putpixel((x, 1 + 1), CYAN_D) if False else None
    for x in range(5, 11):
        im.putpixel((x, 3), CYAN)          # search bar
    im.putpixel((9, 3), ICE); im.putpixel((10, 3), ICE)
    for x in range(5, 7): im.putpixel((x, 5), GOLD)
    for x in range(7, 10): im.putpixel((x, 5), CYAN_D)
    for x in range(5, 7): im.putpixel((x, 7), GREEN)
    for x in range(7, 11): im.putpixel((x, 7), CYAN_D)
    for x in range(5, 11):
        im.putpixel((x, 10), CYAN)         # seam under the screen
    # latch
    for y in range(11, 14):
        for x in range(6, 10):
            im.putpixel((x, y), CYAN)
    im.putpixel((7, 12), OUTLINE); im.putpixel((8, 12), OUTLINE)
    rivets(im); return im

for name, fn in (("terminal_side", side), ("terminal_top", top), ("terminal_bottom", bottom), ("terminal_front", front)):
    fn().save(os.path.join(OUT, name + ".png"))
print("textures written")
