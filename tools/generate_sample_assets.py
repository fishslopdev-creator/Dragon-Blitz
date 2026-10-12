#!/usr/bin/env python3
"""Generates the original sample assets (models, textures, animations, sounds).

Everything here is created from scratch: box geometry in the Bedrock/GeckoLib
"minecraft:geometry" format (opens in Blockbench), procedurally painted textures
laid out to match each cube's box UV, keyframe animations, and synthesized sound
effects (ffmpeg). Nothing is extracted from any other game.

    python3 tools/generate_sample_assets.py

Requires Pillow; sounds additionally need ffmpeg on PATH (skipped otherwise).
"""
import json
import math
import os
import random
import shutil
import subprocess

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "fabric", "src", "main", "resources", "assets", "playtime")
random.seed(7)


def out(*parts):
    path = os.path.join(ASSETS, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


# --------------------------------------------------------------------------- models

class Model:
    """Collects bones/cubes and packs box UVs onto a texture."""

    def __init__(self, name, tex_w, tex_h):
        self.name, self.tex_w, self.tex_h = name, tex_w, tex_h
        self.bones = []
        self.paint = []  # (uv, size, palette)
        self.cursor_u, self.cursor_v, self.row_h = 0, 0, 0

    def _alloc(self, w, h, d):
        need_w, need_h = 2 * (d + w), d + h
        if self.cursor_u + need_w > self.tex_w:
            self.cursor_u, self.cursor_v, self.row_h = 0, self.cursor_v + self.row_h, 0
        if self.cursor_v + need_h > self.tex_h:
            raise ValueError(f"{self.name}: texture too small")
        uv = [self.cursor_u, self.cursor_v]
        self.cursor_u += need_w
        self.row_h = max(self.row_h, need_h)
        return uv

    def bone(self, name, pivot, parent=None, rotation=None):
        b = {"name": name, "pivot": pivot, "cubes": []}
        if parent:
            b["parent"] = parent
        if rotation:
            b["rotation"] = rotation
        self.bones.append(b)
        return b

    def cube(self, bone, origin, size, palette, inflate=0.0):
        w, h, d = (max(1, int(math.ceil(s))) for s in size)
        uv = self._alloc(w, h, d)
        c = {"origin": origin, "size": size, "uv": uv}
        if inflate:
            c["inflate"] = inflate
        bone["cubes"].append(c)
        self.paint.append((uv, (w, h, d), palette))

    def geometry(self):
        return {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": "geometry." + self.name, "texture_width": self.tex_w,
                            "texture_height": self.tex_h, "visible_bounds_width": 4, "visible_bounds_height": 5,
                            "visible_bounds_offset": [0, 2, 0]},
            "bones": self.bones}]}

    def texture(self):
        img = Image.new("RGBA", (self.tex_w, self.tex_h), (0, 0, 0, 0))
        px = img.load()
        for (u, v), (w, h, d), palette in self.paint:
            base, accent, style = palette
            for y in range(v, v + d + h):
                for x in range(u, u + 2 * (d + w)):
                    if (y < v + d) and not (u + d <= x < u + d + 2 * w):
                        continue  # unused corners of the box layout
                    px[x, y] = shade(base, accent, style, x - u, y - v, w, h, d)
        return img


def shade(base, accent, style, x, y, w, h, d):
    n = random.randint(-10, 10)
    r, g, b = base
    if style == "wood":
        stripe = (x * 3 + y) % 7 == 0
        r, g, b = (accent if stripe else base)
    elif style == "curtain":
        fold = (x % 4) in (0, 1)
        r, g, b = (accent if fold else base)
    elif style == "metal":
        if (x + y) % 9 == 0:
            r, g, b = accent
    elif style == "stripes":
        r, g, b = accent if (y // 2) % 2 == 0 else base
    elif style == "face":
        # eyes and a stitched grin on the front face (second column block of the box layout)
        fx, fy = x - d, y - d
        if 0 <= fx < w and 0 <= fy < h:
            if fy in (h // 3,) and fx in (w // 4, 3 * w // 4):
                r, g, b = accent
            elif fy == 2 * h // 3 and w // 5 <= fx <= 4 * w // 5:
                r, g, b = accent if fx % 2 == 0 else (20, 10, 10)
    elif style == "glow":
        if (x + y) % 3 == 0:
            r, g, b = accent
    clamp = lambda c: max(0, min(255, c + n))
    return (clamp(r), clamp(g), clamp(b), 255)


IVORY = ((222, 210, 180), (180, 160, 120), "plain")
RED_CURTAIN = ((122, 18, 28), (88, 10, 18), "curtain")
WOOD = ((96, 62, 36), (70, 44, 24), "wood")
BRASS = ((182, 140, 58), (230, 200, 110), "metal")
STRING = ((210, 210, 200), (240, 240, 230), "plain")
FACE = ((236, 226, 206), (20, 12, 12), "face")
DARK = ((34, 30, 36), (60, 54, 64), "metal")


def playwright():
    m = Model("the_playwright", 128, 128)
    root = m.bone("root", [0, 0, 0])
    hips = m.bone("hips", [0, 26, 0], "root")
    m.cube(hips, [-5, 24, -3], [10, 4, 6], WOOD)
    for side, x in (("left", 2), ("right", -4)):
        thigh = m.bone(side + "_leg", [x + 1, 26, 0], "hips")
        m.cube(thigh, [x, 13, -1], [2, 13, 2], DARK)
        shin = m.bone(side + "_shin", [x + 1, 13, 0], side + "_leg")
        m.cube(shin, [x, 0, -1], [2, 13, 2], DARK)
        m.cube(shin, [x - 0.5, 0, -2], [3, 1, 4], BRASS)
    body = m.bone("body", [0, 28, 0], "hips")
    m.cube(body, [-8, 28, -4], [16, 18, 8], WOOD)               # theatre box
    m.cube(body, [-7, 30, -4.5], [14, 13, 1], RED_CURTAIN)       # curtains
    m.cube(body, [-9, 46, -5], [18, 3, 10], BRASS)               # proscenium top
    m.cube(body, [-9, 27, -5], [18, 1, 10], BRASS)               # stage lip
    for side, x, sign in (("left", 8, 1), ("right", -10, -1)):
        arm = m.bone(side + "_arm", [x + 1, 45, 0], "body")
        m.cube(arm, [x, 30, -1], [2, 16, 2], DARK)
        fore = m.bone(side + "_forearm", [x + 1, 30, 0], side + "_arm")
        m.cube(fore, [x, 16, -1], [2, 14, 2], DARK)
        hand = m.bone(side + "_hand", [x + 1, 16, 0], side + "_forearm")
        m.cube(hand, [x - 1, 11, -2], [4, 5, 4], IVORY)
    neck = m.bone("neck", [0, 49, 0], "body")
    m.cube(neck, [-1, 49, -1], [2, 6, 2], BRASS)
    head = m.bone("head", [0, 55, 0], "neck")
    m.cube(head, [-4, 55, -4], [8, 9, 8], FACE)
    bar = m.bone("control_bar", [0, 66, 0], "head")
    m.cube(bar, [-10, 66, -1], [20, 2, 2], WOOD)
    m.cube(bar, [-1, 66, -8], [2, 2, 16], WOOD)
    for i, (sx, sz) in enumerate(((-9, 0), (9, 0), (0, -7), (0, 7))):
        s = m.bone("string_%d" % i, [sx, 66, sz], "control_bar")
        m.cube(s, [sx - 0.25, 50, sz - 0.25], [0.5, 16, 0.5], STRING)
    return m


def pulse_hand():
    m = Model("pulse_hand", 64, 64)
    TEAL = ((25, 200, 170), (180, 255, 240), "glow")
    GREY = ((70, 76, 84), (110, 118, 128), "metal")
    root = m.bone("hand", [0, 0, 0])
    m.cube(root, [-3, 0, -2], [6, 6, 4], GREY)
    m.cube(root, [-2, 1, -3], [4, 4, 1], TEAL)                    # emitter disk
    for i, x in enumerate((-3, -1.5, 0, 1.5)):
        f = m.bone("finger_%d" % i, [x + 0.75, 6, 0], "hand")
        m.cube(f, [x, 6, -1], [1.5, 4, 2], TEAL if i == 1 else GREY)
    thumb = m.bone("thumb", [3, 3, 0], "hand")
    m.cube(thumb, [3, 2, -1], [2, 3, 2], GREY)
    coil = m.bone("coil", [0, -2, 0], "hand")
    m.cube(coil, [-2, -3, -2], [4, 3, 4], TEAL)
    return m


def grabpack():
    m = Model("grabpack_2", 64, 64)
    PACK = ((60, 64, 72), (230, 160, 40), "stripes")
    BLUE = ((47, 107, 219), (120, 170, 250), "plain")
    RED = ((211, 49, 43), (250, 120, 110), "plain")
    pack = m.bone("pack", [0, 18, 2])
    m.cube(pack, [-4, 14, 2], [8, 10, 4], PACK)
    m.cube(pack, [-3, 24, 3], [6, 2, 2], DARK)
    for side, x, col in (("left", 4, BLUE), ("right", -6, RED)):
        launcher = m.bone(side + "_launcher", [x + 1, 18, -2], "pack")
        m.cube(launcher, [x, 16, -8], [2, 3, 8], DARK)
        handb = m.bone(side + "_hand", [x + 1, 17, -9], side + "_launcher")
        m.cube(handb, [x - 1, 15.5, -11], [4, 4, 3], col)
        cable = m.bone(side + "_cable", [x + 1, 17.5, -8], side + "_launcher")
        m.cube(cable, [x + 0.75, 17.25, -8], [0.5, 0.5, 0.5], DARK)
    return m


def write_model(model, tex_rel):
    with open(out("geo", model.name + ".geo.json"), "w") as fh:
        json.dump(model.geometry(), fh, indent=1)
    model.texture().save(out("textures", *tex_rel))


# --------------------------------------------------------------------------- animations

def kf(pairs):
    return {"%.2f" % t: v for t, v in pairs}


def playwright_animations():
    walk_len = 1.2
    def swing(amp, phase=0.0, n=6):
        return kf([(walk_len * i / n, [amp * math.sin(2 * math.pi * i / n + phase), 0, 0]) for i in range(n + 1)])
    anims = {
        "animation.the_playwright.idle": {"loop": True, "animation_length": 3.0, "bones": {
            "control_bar": {"rotation": kf([(0, [0, 0, -4]), (1.5, [0, 0, 4]), (3.0, [0, 0, -4])])},
            "head": {"rotation": kf([(0, [0, -6, 0]), (1.5, [0, 6, 0]), (3.0, [0, -6, 0])])},
            "body": {"position": kf([(0, [0, 0, 0]), (1.5, [0, -0.4, 0]), (3.0, [0, 0, 0])])}}},
        "animation.the_playwright.walk": {"loop": True, "animation_length": walk_len, "bones": {
            "left_leg": {"rotation": swing(28)}, "right_leg": {"rotation": swing(28, math.pi)},
            "left_shin": {"rotation": swing(14, math.pi / 2)}, "right_shin": {"rotation": swing(14, 3 * math.pi / 2)},
            "left_arm": {"rotation": swing(18, math.pi)}, "right_arm": {"rotation": swing(18)},
            "control_bar": {"rotation": swing(8, math.pi / 2)}}},
        "animation.the_playwright.run": {"loop": True, "animation_length": 0.7, "bones": {
            "left_leg": {"rotation": kf([(0, [40, 0, 0]), (0.35, [-40, 0, 0]), (0.7, [40, 0, 0])])},
            "right_leg": {"rotation": kf([(0, [-40, 0, 0]), (0.35, [40, 0, 0]), (0.7, [-40, 0, 0])])},
            "body": {"rotation": kf([(0, [12, 0, 0]), (0.7, [12, 0, 0])])},
            "left_arm": {"rotation": kf([(0, [-50, 0, -10]), (0.35, [30, 0, -10]), (0.7, [-50, 0, -10])])},
            "right_arm": {"rotation": kf([(0, [30, 0, 10]), (0.35, [-50, 0, 10]), (0.7, [30, 0, 10])])}}},
        "animation.the_playwright.sprint": {"loop": True, "animation_length": 0.5, "bones": {
            "left_leg": {"rotation": kf([(0, [55, 0, 0]), (0.25, [-55, 0, 0]), (0.5, [55, 0, 0])])},
            "right_leg": {"rotation": kf([(0, [-55, 0, 0]), (0.25, [55, 0, 0]), (0.5, [-55, 0, 0])])},
            "body": {"rotation": kf([(0, [22, 0, 0]), (0.5, [22, 0, 0])])},
            "head": {"rotation": kf([(0, [-15, 0, 0]), (0.5, [-15, 0, 0])])}}},
        "animation.the_playwright.search": {"loop": True, "animation_length": 2.4, "bones": {
            "head": {"rotation": kf([(0, [10, -50, 0]), (1.2, [10, 50, 0]), (2.4, [10, -50, 0])])},
            "right_arm": {"rotation": kf([(0, [-70, 0, 0]), (2.4, [-70, 0, 0])])}}},
        "animation.the_playwright.turn": {"loop": False, "animation_length": 0.5, "bones": {
            "head": {"rotation": kf([(0, [0, 0, 0]), (0.2, [0, 70, 0]), (0.5, [0, 0, 0])])}}},
        "animation.the_playwright.attack": {"loop": False, "animation_length": 0.8, "bones": {
            "left_arm": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-150, 0, -20]), (0.55, [-40, 0, 0]), (0.8, [0, 0, 0])])},
            "right_arm": {"rotation": kf([(0, [0, 0, 0]), (0.3, [-150, 0, 20]), (0.55, [-40, 0, 0]), (0.8, [0, 0, 0])])},
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.45, [25, 0, 0]), (0.8, [0, 0, 0])])}}},
        "animation.the_playwright.stunned": {"loop": True, "animation_length": 1.0, "bones": {
            "control_bar": {"rotation": kf([(0, [0, 0, -25]), (0.5, [0, 0, 25]), (1.0, [0, 0, -25])])},
            "head": {"rotation": kf([(0, [30, 0, 15]), (0.5, [30, 0, -15]), (1.0, [30, 0, 15])])}}},
        "animation.the_playwright.curtain_call": {"loop": False, "animation_length": 2.0, "bones": {
            "body": {"rotation": kf([(0, [0, 0, 0]), (0.6, [40, 0, 0]), (1.4, [40, 0, 0]), (2.0, [0, 0, 0])])},
            "head": {"rotation": kf([(0, [0, 0, 0]), (1.0, [0, 180, 0]), (2.0, [0, 360, 0])])}}},
    }
    return {"format_version": "1.8.0", "animations": anims}


def pulse_hand_animations():
    return {"format_version": "1.8.0", "animations": {
        "animation.pulse_hand.idle": {"loop": True, "animation_length": 2.0, "bones": {
            "coil": {"scale": kf([(0, [1, 1, 1]), (1.0, [1.08, 1.08, 1.08]), (2.0, [1, 1, 1])])}}},
        "animation.pulse_hand.equip": {"loop": False, "animation_length": 0.4, "bones": {
            "hand": {"rotation": kf([(0, [-90, 0, 0]), (0.4, [0, 0, 0])]), "position": kf([(0, [0, -6, 0]), (0.4, [0, 0, 0])])}}},
        "animation.pulse_hand.fire": {"loop": False, "animation_length": 0.3, "bones": {
            "hand": {"position": kf([(0, [0, 0, 0]), (0.1, [0, 0, 2]), (0.3, [0, 0, 0])])},
            **{"finger_%d" % i: {"rotation": kf([(0, [0, 0, 0]), (0.15, [-35, 0, 0]), (0.3, [-35, 0, 0])])} for i in range(4)}}},
        "animation.pulse_hand.pulse": {"loop": False, "animation_length": 0.6, "bones": {
            "coil": {"scale": kf([(0, [1, 1, 1]), (0.15, [1.6, 1.6, 1.6]), (0.6, [1, 1, 1])])},
            **{"finger_%d" % i: {"rotation": kf([(0, [-35, 0, 0]), (0.15, [20, 0, 0]), (0.6, [0, 0, 0])])} for i in range(4)}}},
        "animation.pulse_hand.retract": {"loop": False, "animation_length": 0.3, "bones": {
            **{"finger_%d" % i: {"rotation": kf([(0, [0, 0, 0]), (0.3, [-60, 0, 0])])} for i in range(4)}}},
    }}


def generic_hand_animations():
    return {"format_version": "1.8.0", "animations": {
        "animation.hand.idle": {"loop": True, "animation_length": 2.0, "bones": {}},
        "animation.hand.equip": {"loop": False, "animation_length": 0.4, "bones": {
            "hand": {"rotation": kf([(0, [-90, 0, 0]), (0.4, [0, 0, 0])])}}},
        "animation.hand.fire": {"loop": False, "animation_length": 0.25, "bones": {
            **{"finger_%d" % i: {"rotation": kf([(0, [0, 0, 0]), (0.25, [-30, 0, 0])])} for i in range(4)}}},
        "animation.hand.use": {"loop": False, "animation_length": 0.4, "bones": {
            **{"finger_%d" % i: {"rotation": kf([(0, [-30, 0, 0]), (0.2, [-80, 0, 0]), (0.4, [-30, 0, 0])])} for i in range(4)}}},
        "animation.hand.retract": {"loop": False, "animation_length": 0.3, "bones": {
            **{"finger_%d" % i: {"rotation": kf([(0, [-30, 0, 0]), (0.3, [0, 0, 0])])} for i in range(4)}}},
    }}


def hand_texture_variant(name, rgb):
    """Recolours the generic hand: same geometry, distinct colour per hand."""
    m = Model("hand_" + name, 64, 64)
    PAL = (rgb, tuple(min(255, c + 60) for c in rgb), "plain")
    GREY = ((70, 76, 84), (110, 118, 128), "metal")
    root = m.bone("hand", [0, 0, 0])
    m.cube(root, [-3, 0, -2], [6, 6, 4], PAL)
    for i, x in enumerate((-3, -1.5, 0, 1.5)):
        f = m.bone("finger_%d" % i, [x + 0.75, 6, 0], "hand")
        m.cube(f, [x, 6, -1], [1.5, 4, 2], PAL)
    thumb = m.bone("thumb", [3, 3, 0], "hand")
    m.cube(thumb, [3, 2, -1], [2, 3, 2], PAL)
    cuff = m.bone("cuff", [0, -2, 0], "hand")
    m.cube(cuff, [-2.5, -2, -2], [5, 2, 4], GREY)
    return m


def icon(name, rgb):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    light = tuple(min(255, c + 70) for c in rgb)
    d.ellipse([3, 3, 29, 29], fill=(24, 22, 28, 230), outline=light, width=2)
    d.rectangle([11, 14, 21, 24], fill=rgb)
    for i, x in enumerate((11, 14, 17, 20)):
        d.rectangle([x, 7 + (i % 2), x + 1, 14], fill=rgb)
    d.rectangle([21, 16, 24, 19], fill=rgb)
    if name == "pulse_hand":
        d.ellipse([13, 16, 19, 22], fill=(200, 255, 245))
    img.save(out("textures", "gui", "hands", name + ".png"))


HANDS = {
    "blue_hand": (47, 107, 219), "red_hand": (211, 49, 43), "green_hand": (53, 180, 74),
    "purple_hand": (138, 63, 209), "flare_hand": (242, 138, 30), "omni_hand": (232, 232, 240),
    "pressure_hand": (63, 182, 201), "conductive_hand": (227, 198, 41), "pulse_hand": (25, 230, 193),
}


# --------------------------------------------------------------------------- GUI art

def menu_background():
    w, h = 480, 270
    img = Image.new("RGB", (w, h), (12, 10, 14))
    d = ImageDraw.Draw(img)
    for y in range(h):  # dim vertical gradient
        c = int(10 + 18 * y / h)
        d.line([(0, y), (w, y)], fill=(c, c - 2, c + 2))
    for x in range(0, w, 60):  # factory girders
        d.rectangle([x, 0, x + 6, h], fill=(26, 24, 30))
        for y in range(0, h, 24):
            d.line([(x, y), (x + 30, y + 24)], fill=(32, 30, 36))
    d.rectangle([0, h - 60, w, h], fill=(20, 18, 22))  # conveyor
    for x in range(0, w, 12):
        d.rectangle([x, h - 58, x + 8, h - 54], fill=(40, 38, 44))
    for x, col in ((120, (200, 60, 60)), (300, (60, 110, 220))):  # hanging lamps
        d.line([(x, 0), (x, 70)], fill=(50, 48, 52))
        d.polygon([(x - 14, 70), (x + 14, 70), (x + 8, 60), (x - 8, 60)], fill=(60, 58, 62))
        for r in range(60, 0, -6):
            a = int(18 * (1 - r / 60))
            d.ellipse([x - r, 70 - r // 3, x + r, 70 + r], outline=tuple(min(255, c // 4 + a) for c in col))
    img.save(out("textures", "gui", "menu", "factory_background.png"))


def wheel():
    s = 128
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([2, 2, s - 3, s - 3], fill=(18, 16, 22, 210), outline=(230, 200, 120, 255), width=2)
    d.ellipse([40, 40, s - 41, s - 41], fill=(0, 0, 0, 0), outline=(120, 110, 90, 255), width=2)
    for i in range(8):
        a = 2 * math.pi * i / 8
        d.line([(s / 2 + 26 * math.cos(a), s / 2 + 26 * math.sin(a)),
                (s / 2 + 62 * math.cos(a), s / 2 + 62 * math.sin(a))], fill=(90, 84, 70, 255), width=1)
    img.save(out("textures", "gui", "hand_wheel.png"))


def block_textures():
    specs = {
        "player_setup_console": ((60, 70, 90), (230, 160, 40)), "door_controller": ((70, 70, 76), (200, 60, 60)),
        "keycard_reader": ((50, 54, 60), (47, 107, 219)), "hand_scanner": ((50, 54, 60), (53, 180, 74)),
        "power_generator": ((90, 80, 50), (240, 200, 60)), "fuse_box": ((80, 84, 90), (220, 180, 40)),
        "electrical_node": ((60, 60, 66), (120, 200, 255)), "timed_switch": ((70, 66, 60), (240, 140, 40)),
        "puzzle_controller": ((70, 50, 90), (200, 120, 255)), "chase_trigger": ((90, 30, 30), (255, 80, 80)),
        "checkpoint": ((40, 80, 50), (120, 240, 140)), "monster_spawn_marker": ((60, 20, 60), (240, 60, 240)),
        "monster_route_marker": ((60, 40, 20), (255, 170, 60)), "story_event_trigger": ((30, 50, 90), (120, 170, 255)),
        "objective_trigger": ((30, 70, 70), (100, 240, 230)), "chapter_transition": ((90, 80, 30), (255, 230, 120)),
        "trigger_volume": ((40, 40, 40), (200, 200, 200)), "access_terminal": ((30, 40, 50), (80, 240, 120)),
        "pulse_receiver": ((40, 60, 60), (25, 230, 193)), "connection_device": ((60, 60, 70), (230, 200, 120)),
    }
    for name, (base, accent) in specs.items():
        img = Image.new("RGB", (16, 16), base)
        d = ImageDraw.Draw(img)
        d.rectangle([0, 0, 15, 15], outline=tuple(max(0, c - 25) for c in base))
        d.rectangle([4, 4, 11, 11], outline=accent)
        d.rectangle([6, 6, 9, 9], fill=accent)
        for _ in range(12):
            x, y = random.randrange(16), random.randrange(16)
            px = img.getpixel((x, y))
            img.putpixel((x, y), tuple(max(0, min(255, c + random.randint(-12, 12))) for c in px))
        img.save(out("textures", "block", name + ".png"))
    for item, col in (("power_core", (25, 230, 193)), ("fuse", (220, 180, 40)), ("blue_access_card", (47, 107, 219)),
                      ("red_access_card", (211, 49, 43)), ("high_security_card", (30, 30, 30)),
                      ("maintenance_key", (176, 141, 60)), ("laboratory_key", (154, 208, 216))):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        if "card" in item:
            d.rectangle([2, 4, 13, 11], fill=col, outline=(240, 240, 240))
            d.rectangle([3, 5, 6, 7], fill=(230, 210, 120))
        elif "key" in item:
            d.ellipse([2, 5, 7, 10], outline=col, width=2)
            d.line([(7, 8), (14, 8)], fill=col, width=2)
            d.line([(12, 8), (12, 11)], fill=col)
        else:
            d.rectangle([4, 2, 11, 13], fill=(60, 60, 66), outline=col)
            d.rectangle([6, 5, 9, 10], fill=col)
        img.save(out("textures", "item", item + ".png"))


# --------------------------------------------------------------------------- sounds

SOUNDS = {
    "pulse_hand.fire": "sine=f=220:d=0.25,afade=t=out:st=0.15:d=0.1,asetrate=44100*1.5",
    "pulse_hand.pulse": "sine=f=90:d=0.5,afade=t=out:st=0.1:d=0.4",
    "pulse_hand.attach": "sine=f=1320:d=0.08",
    "grabpack.fire": "anoisesrc=d=0.18:c=pink:a=0.6,afade=t=out:st=0.05:d=0.13",
    "grabpack.attach": "sine=f=660:d=0.05",
    "grabpack.retract": "anoisesrc=d=0.3:c=brown:a=0.5,afade=t=in:d=0.1,afade=t=out:st=0.2:d=0.1",
    "hand.pickup": "sine=f=523:d=0.12[a];sine=f=659:d=0.12[b];sine=f=784:d=0.25[c];[a][b][c]concat=n=3:v=0:a=1",
    "alarm": "sine=f=740:d=0.5[a];sine=f=554:d=0.5[b];[a][b]concat=n=2:v=0:a=1,aloop=loop=1:size=44100",
    "menu.factory_hum": "sine=f=55:d=6[a];anoisesrc=d=6:c=brown:a=0.08[b];[a][b]amix=inputs=2",
    "door.open": "anoisesrc=d=0.9:c=brown:a=0.7,lowpass=f=600,afade=t=out:st=0.6:d=0.3",
    "door.close": "anoisesrc=d=0.5:c=brown:a=0.8,lowpass=f=400,afade=t=out:st=0.3:d=0.2",
    "door.denied": "sine=f=180:d=0.35,afade=t=out:st=0.25:d=0.1",
    "puzzle.complete": "sine=f=523:d=0.6[a];sine=f=659:d=0.6[b];sine=f=784:d=0.6[c];[a][b][c]amix=inputs=3,afade=t=out:st=0.3:d=0.3",
    "puzzle.fail": "sine=f=110:d=0.5,afade=t=out:st=0.3:d=0.2",
    "scanner.accept": "sine=f=880:d=0.1[a];sine=f=1175:d=0.15[b];[a][b]concat=n=2:v=0:a=1",
    "scanner.deny": "sine=f=150:d=0.3",
    "reader.accept": "sine=f=988:d=0.12",
    "reader.deny": "sine=f=165:d=0.3",
    "socket.insert": "sine=f=330:d=0.06[a];anoisesrc=d=0.1:a=0.4[b];[a][b]concat=n=2:v=0:a=1",
    "generator.overload": "anoisesrc=d=0.8:c=white:a=0.5,highpass=f=2000,afade=t=out:st=0.4:d=0.4",
    "pulse.receiver": "sine=f=1760:d=0.2,afade=t=out:st=0.1:d=0.1",
    "machine.conveyor_start": "sine=f=70:d=1.5[a];anoisesrc=d=1.5:c=brown:a=0.3[b];[a][b]amix=inputs=2,afade=t=in:d=0.6",
    "monster.the_playwright.alert": "sine=f=82:d=1.2[a];sine=f=87:d=1.2[b];[a][b]amix=inputs=2,afade=t=out:st=0.8:d=0.4",
    "monster.the_playwright.step": "anoisesrc=d=0.12:c=brown:a=0.9,lowpass=f=200",
    "monster.the_playwright.attack": "anoisesrc=d=0.4:c=pink:a=0.9,afade=t=out:st=0.2:d=0.2",
    "monster.the_playwright.ambient": "sine=f=41:d=3,tremolo=f=3:d=0.6",
    "tape.line_safety": "anoisesrc=d=3:c=pink:a=0.15[a];sine=f=200:d=3,tremolo=f=6[b];[a][b]amix=inputs=2",
}


def sounds():
    if shutil.which("ffmpeg") is None:
        print("ffmpeg not found; skipping sounds")
        return
    sounds_json = {}
    for event, graph in SOUNDS.items():
        rel = event.replace(".", "/")
        path = out("sounds", rel + ".ogg")
        cmd = ["ffmpeg", "-y", "-loglevel", "error", "-filter_complex", graph, "-ac", "1", "-ar", "44100",
               "-c:a", "libvorbis", "-q:a", "3", path]
        subprocess.run(cmd, check=True)
        entry = {"sounds": ["playtime:" + rel]}
        if event in ("alarm", "menu.factory_hum") or event.endswith("ambient"):
            entry["sounds"] = [{"name": "playtime:" + rel, "stream": event == "menu.factory_hum"}]
        sounds_json[event] = entry
    with open(out("sounds.json"), "w") as fh:
        json.dump(sounds_json, fh, indent=2)


def main():
    pw = playwright()
    write_model(pw, ("entity", "monsters", "the_playwright.png"))
    with open(out("animations", "monsters", "the_playwright.animation.json"), "w") as fh:
        json.dump(playwright_animations(), fh, indent=1)
    ph = pulse_hand()
    os.makedirs(os.path.join(ASSETS, "geo", "hands"), exist_ok=True)
    with open(out("geo", "hands", "pulse_hand.geo.json"), "w") as fh:
        json.dump(ph.geometry(), fh, indent=1)
    ph.texture().save(out("textures", "entity", "hands", "pulse_hand.png"))
    with open(out("animations", "hands", "pulse_hand.animation.json"), "w") as fh:
        json.dump(pulse_hand_animations(), fh, indent=1)
    with open(out("animations", "hands", "hand.animation.json"), "w") as fh:
        json.dump(generic_hand_animations(), fh, indent=1)
    for name, rgb in HANDS.items():
        icon(name, rgb)
        if name == "pulse_hand":
            continue
        m = hand_texture_variant(name, rgb)
        with open(out("geo", "hands", name + ".geo.json"), "w") as fh:
            json.dump(m.geometry(), fh, indent=1)
        m.texture().save(out("textures", "entity", "hands", name + ".png"))
    gp = grabpack()
    write_model(gp, ("entity", "grabpack_2.png"))
    menu_background()
    wheel()
    block_textures()
    sounds()
    print("assets written to", os.path.normpath(ASSETS))


if __name__ == "__main__":
    main()
