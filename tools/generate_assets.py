#!/usr/bin/env python3
"""Generates every texture, model, blockstate, recipe, loot table, tag and lang file for Shipwright.
Run from the project root:  python3 tools/generate_assets.py   (needs: pip install pillow)
Edit the palettes / drawing code to restyle the mod, then run it again."""
import json, os, random
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "shipwright")
DATA = os.path.join(ROOT, "data", "shipwright")
NS = "shipwright"

def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)

# ----------------------------------------------------------------------------- textures
def new(color=(0, 0, 0, 0)):
    return Image.new("RGBA", (16, 16), color)

def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)

def noisy(base, amount=0.07, seed=1):
    rnd = random.Random(seed)
    im = new()
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), shade(base, 1 + rnd.uniform(-amount, amount)))
    return im

def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            im.putpixel((x, y), c + (255,) if len(c) == 3 else c)

def border(im, c):
    rect(im, 0, 0, 15, 0, c); rect(im, 0, 15, 15, 15, c)
    rect(im, 0, 0, 0, 15, c); rect(im, 15, 0, 15, 15, c)

def rivets(im, pts, c, hi):
    for x, y in pts:
        im.putpixel((x, y), c + (255,)); im.putpixel((x + 1, y - 1) if y > 0 and x < 15 else (x, y), hi + (255,))

BRASS = (206, 156, 62); BRASS_D = (140, 98, 34); BRASS_L = (238, 200, 108)
IRON = (96, 102, 112); IRON_D = (58, 62, 70); IRON_L = (150, 157, 168)
WOOD = (104, 72, 38); WOOD_D = (70, 46, 22); WOOD_L = (132, 94, 50)
NAVY = (26, 44, 76); COPPER = (190, 112, 66)

def tex(name, im):
    p = os.path.join(ASSETS, "textures", "block", name + ".png")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    im.save(p)

def make_textures():
    # ship core: wooden hull plating with brass frame; top = brass compass rose on navy
    side = noisy(WOOD, 0.06, 3)
    for y in (4, 11):
        rect(side, 1, y, 14, y, WOOD_D)
    border(side, BRASS_D); rect(side, 1, 1, 14, 1, BRASS); rect(side, 1, 14, 14, 14, BRASS_D)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        side.putpixel((x, y), BRASS_L + (255,))
    rect(side, 6, 6, 9, 9, BRASS_D); rect(side, 7, 7, 8, 8, BRASS_L)  # little brass boss
    tex("ship_core_side", side)

    top = noisy(NAVY, 0.05, 4)
    border(top, BRASS_D); rect(top, 1, 1, 14, 1, BRASS)
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if 5.6 < d < 6.8:
                top.putpixel((x, y), BRASS + (255,))
    for i in range(3, 13):  # cross
        top.putpixel((7, i), BRASS_L + (255,)); top.putpixel((8, i), BRASS_L + (255,))
        top.putpixel((i, 7), BRASS_L + (255,)); top.putpixel((i, 8), BRASS_L + (255,))
    for i in range(5, 11):  # diagonals
        top.putpixel((i, i), BRASS + (255,)); top.putpixel((i, 15 - i), BRASS + (255,))
    rect(top, 6, 6, 9, 9, BRASS_D); rect(top, 7, 7, 8, 8, (200, 60, 50))
    tex("ship_core_top", top)

    # engine
    eside = noisy(IRON, 0.05, 5)
    border(eside, IRON_D)
    rect(eside, 1, 7, 14, 8, IRON_D)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13), (2, 5), (13, 5), (2, 10), (13, 10)):
        eside.putpixel((x, y), IRON_L + (255,))
    tex("engine_side", eside)

    etop = noisy(IRON_D, 0.05, 6)
    border(etop, IRON)
    for y in range(3, 14, 2):
        rect(etop, 3, y, 12, y, (20, 22, 26))
    tex("engine_top", etop)

    for lit in (False, True):
        f = noisy(IRON, 0.05, 7)
        border(f, IRON_D)
        rect(f, 3, 3, 12, 12, (30, 31, 36))
        rect(f, 3, 3, 12, 3, IRON_D); rect(f, 3, 3, 3, 12, IRON_D)
        if lit:
            rect(f, 4, 6, 11, 11, (232, 98, 20)); rect(f, 5, 8, 10, 11, (255, 168, 40)); rect(f, 7, 9, 8, 11, (255, 236, 130))
        else:
            rect(f, 4, 6, 11, 11, (44, 40, 40)); rect(f, 5, 9, 10, 11, (60, 54, 52))
        for x in range(4, 12):  # grate bar
            f.putpixel((x, 5), IRON_L + (255,))
        rect(f, 6, 1, 9, 2, BRASS if lit else BRASS_D)  # gauge plate
        tex("engine_front_lit" if lit else "engine_front", f)

    # shaft: banded steel
    sh = noisy(IRON, 0.04, 8)
    for x in range(16):
        sh.putpixel((x, 0), IRON_D + (255,)); sh.putpixel((x, 15), IRON_D + (255,))
        sh.putpixel((x, 4), IRON_L + (255,)); sh.putpixel((x, 11), IRON_L + (255,))
    for y in range(16):
        sh.putpixel((0, y), IRON_D + (255,)); sh.putpixel((15, y), IRON_D + (255,))
        sh.putpixel((5, y), shade(IRON_L, 1.05)); sh.putpixel((10, y), shade(IRON, 0.8))
    tex("shaft", sh)

    # propeller: bronze with bright edge
    pr = noisy(COPPER, 0.05, 9)
    for y in range(16):
        pr.putpixel((0, y), shade(COPPER, 0.65)); pr.putpixel((15, y), shade(COPPER, 0.65))
        for x in range(1, 15):
            pr.putpixel((x, y), shade(COPPER, 0.9 + 0.2 * (1 - abs(x - 7.5) / 7.5)))
    tex("propeller", pr)
    hub = noisy(BRASS, 0.05, 10); border(hub, BRASS_D)
    tex("propeller_hub", hub)

    # wheel
    wood = noisy(WOOD, 0.06, 11)
    for y in range(0, 16, 4):
        rect(wood, 0, y, 15, y, WOOD_D)
    tex("wheel_wood", wood)
    br = noisy(BRASS, 0.05, 12); border(br, BRASS_D)
    tex("wheel_brass", br)

    # anchor + chain
    an = noisy((56, 60, 70), 0.07, 13)
    border(an, (34, 37, 44))
    for y in range(0, 15, 5):
        rect(an, 1, y + 1, 14, y + 1, (84, 90, 102))
    tex("anchor", an)
    ch = new((0, 0, 0, 0))
    for y in range(16):
        link = (y // 3) % 2 == 0
        for x in range(16):
            if (link and 4 <= x <= 11) or (not link and 6 <= x <= 9):
                ch.putpixel((x, y), shade(IRON, 0.85 if (x in (4, 11, 6, 9)) else 1.1))
    tex("anchor_chain", ch)

# ----------------------------------------------------------------------------- models
FACES = ("north", "south", "east", "west", "up", "down")

def box(f, t, tex="0", rot=None):
    el = {"from": list(f), "to": list(t), "faces": {d: {"texture": "#" + tex} for d in FACES}}
    if rot:
        el["rotation"] = rot
    return el

def block_model(name, obj):
    write_json(os.path.join(ASSETS, "models", "block", name + ".json"), obj)

def blockstate(name, obj):
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), obj)

def item_def(name, model_name):
    write_json(os.path.join(ASSETS, "items", name + ".json"),
               {"model": {"type": "minecraft:model", "model": f"{NS}:block/{model_name}"}})

YROT = {"north": 0, "east": 90, "south": 180, "west": 270}

def make_models():
    # ship core
    block_model("ship_core", {"parent": "minecraft:block/cube_column",
                              "textures": {"end": f"{NS}:block/ship_core_top", "side": f"{NS}:block/ship_core_side"}})
    blockstate("ship_core", {"variants": {"": {"model": f"{NS}:block/ship_core"}}})
    item_def("ship_core", "ship_core")

    # engine
    for lit in (False, True):
        block_model("engine_lit" if lit else "engine", {
            "parent": "minecraft:block/orientable",
            "textures": {"top": f"{NS}:block/engine_top", "front": f"{NS}:block/engine_front" + ("_lit" if lit else ""),
                         "side": f"{NS}:block/engine_side"}})
    v = {}
    for lit in (False, True):
        for d, y in YROT.items():
            e = {"model": f"{NS}:block/engine" + ("_lit" if lit else "")}
            if y: e["y"] = y
            v[f"facing={d},lit={'true' if lit else 'false'}"] = e
    blockstate("engine", {"variants": v})
    item_def("engine", "engine")

    # shaft
    block_model("shaft", {
        "parent": "minecraft:block/block",
        "textures": {"0": f"{NS}:block/shaft", "particle": f"{NS}:block/shaft"},
        "elements": [box((6, 0, 6), (10, 16, 10)), box((5, 0, 5), (11, 2, 11)), box((5, 14, 5), (11, 16, 11)),
                     box((5.5, 7, 5.5), (10.5, 9, 10.5))]})
    blockstate("shaft", {"variants": {
        "axis=y": {"model": f"{NS}:block/shaft"},
        "axis=z": {"model": f"{NS}:block/shaft", "x": 90},
        "axis=x": {"model": f"{NS}:block/shaft", "x": 90, "y": 90}}})
    item_def("shaft", "shaft")

    # propeller: 4 animation frames, blades are a 4-blade cross rotated in 22.5 degree steps
    for frame, ang in enumerate((0, 22.5, 45, -22.5)):
        r = lambda: {"angle": ang, "axis": "z", "origin": [8, 8, 8]}
        els = [box((6, 6, 5), (10, 10, 11), "1"),            # hub
               box((7, 7, 11), (9, 9, 13), "1"),             # nose cone
               box((5, 1, 7.5), (11, 15, 8.5), "0", r()),    # blade pair A
               box((1, 5, 7.4), (15, 11, 8.6), "0", r())]    # blade pair B
        block_model(f"propeller_{frame}", {
            "parent": "minecraft:block/block",
            "textures": {"0": f"{NS}:block/propeller", "1": f"{NS}:block/propeller_hub", "particle": f"{NS}:block/propeller"},
            "elements": els})
    pv = {}
    rots = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
    for d, rot in rots.items():
        for frame in range(4):
            pv[f"facing={d},frame={frame}"] = {"model": f"{NS}:block/propeller_{frame}", **rot}
    blockstate("propeller", {"variants": pv})
    item_def("propeller", "propeller_0")

    # wheel: post + 8-spoke wheel with handles
    cy = 10
    els = [box((6, 0, 6), (10, 5, 10), "0"), box((5, 0, 5), (11, 2, 11), "1")]
    els += [box((7, 4, 7.2), (9, 16, 8.8), "0"),
            box((2, cy - 1, 7.2), (14, cy + 1, 8.8), "0"),
            box((2, cy - 1, 7.2), (14, cy + 1, 8.8), "0", {"angle": 45, "axis": "z", "origin": [8, cy, 8]}),
            box((2, cy - 1, 7.2), (14, cy + 1, 8.8), "0", {"angle": -45, "axis": "z", "origin": [8, cy, 8]}),
            box((6.5, cy - 2, 6.5), (9.5, cy + 2, 9.5), "1")]
    for dx, dy in ((6, 0), (-6, 0), (0, 5), (0, -6), (4.2, 4.2), (-4.2, 4.2), (4.2, -4.2), (-4.2, -4.2)):
        x, y = 8 + dx, cy + dy
        if y + 1 > 16: y = 15
        els.append(box((x - 1, y - 1, 6.2), (x + 1, y + 1, 9.8), "1"))
    els += [box((4.5, 14.6, 7.4), (11.5, 15.8, 8.6), "0"), box((4.5, 4.2, 7.4), (11.5, 5.4, 8.6), "0"),
            box((3.6, 6, 7.4), (4.8, 14, 8.6), "0"), box((11.2, 6, 7.4), (12.4, 14, 8.6), "0")]
    block_model("ship_wheel", {"parent": "minecraft:block/block",
                               "textures": {"0": f"{NS}:block/wheel_wood", "1": f"{NS}:block/wheel_brass",
                                            "particle": f"{NS}:block/wheel_wood"}, "elements": els})
    blockstate("ship_wheel", {"variants": {f"facing={d}": ({"model": f"{NS}:block/ship_wheel", "y": y} if y else
                                                           {"model": f"{NS}:block/ship_wheel"}) for d, y in YROT.items()}})
    item_def("ship_wheel", "ship_wheel")

    # anchor: raised and dropped
    def anchor(dy, chain):
        els = [box((5, 14, 5), (11, 16, 11), "1")]  # deck mount
        def b(f, t, tex="0", rot=None):
            return box((f[0], f[1] + dy, f[2]), (t[0], t[1] + dy, t[2]), tex, rot)
        els += [b((7, 3, 7), (9, 13, 9)),                                   # shank
                b((4, 12, 7.2), (12, 14, 8.8)),                             # stock
                b((6.5, 13, 6.5), (9.5, 15, 9.5), "1"),                     # ring
                b((3, 1, 7), (13, 3, 9)),                                   # crown
                b((2.5, 2.5, 7), (7.5, 4.5, 9), "0", {"angle": -22.5, "axis": "z", "origin": [7.5, 3, 8]}),
                b((8.5, 2.5, 7), (13.5, 4.5, 9), "0", {"angle": 22.5, "axis": "z", "origin": [8.5, 3, 8]}),
                b((1.5, 3, 6.5), (3.5, 5.5, 9.5)), b((12.5, 3, 6.5), (14.5, 5.5, 9.5))]  # flukes
        if chain:
            els.append(box((7, 14 + dy, 7.8), (9, 14, 8.2), "2"))
            els.append(box((7.8, 14 + dy, 7), (8.2, 14, 9), "2"))
        return {"parent": "minecraft:block/block",
                "textures": {"0": f"{NS}:block/anchor", "1": f"{NS}:block/wheel_brass", "2": f"{NS}:block/anchor_chain",
                             "particle": f"{NS}:block/anchor"}, "elements": els}
    block_model("anchor", anchor(0, False))
    block_model("anchor_dropped", anchor(-13, True))
    av = {}
    for d, y in YROT.items():
        for dropped in (False, True):
            e = {"model": f"{NS}:block/anchor" + ("_dropped" if dropped else "")}
            if y: e["y"] = y
            av[f"dropped={'true' if dropped else 'false'},facing={d}"] = e
    blockstate("anchor", {"variants": av})
    item_def("anchor", "anchor")

# ----------------------------------------------------------------------------- data
BLOCKS = ["ship_core", "ship_wheel", "engine", "shaft", "propeller", "anchor"]

def make_data():
    for b in BLOCKS:
        write_json(os.path.join(DATA, "loot_table", "blocks", b + ".json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{b}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

    def shaped(name, pattern, key, count=1):
        write_json(os.path.join(DATA, "recipe", name + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern, "key": key,
            "result": {"id": f"{NS}:{name}", "count": count}})
    I = "minecraft:iron_ingot"
    shaped("ship_core", ["IGI", "PCP", "III"], {"I": I, "G": "minecraft:gold_ingot", "P": "#minecraft:planks", "C": "minecraft:compass"})
    shaped("ship_wheel", ["SPS", "PIP", "SPS"], {"S": "minecraft:stick", "P": "#minecraft:planks", "I": I})
    shaped("engine", ["III", "IFI", "IRI"], {"I": I, "F": "minecraft:furnace", "R": "minecraft:redstone"})
    shaped("shaft", ["I", "I", "I"], {"I": I}, 3)
    shaped("propeller", [" I ", "ICI", " I "], {"I": I, "C": "minecraft:copper_ingot"})
    shaped("anchor", ["NIN", " I ", "III"], {"I": I, "N": "minecraft:iron_nugget"})

    write_json(os.path.join(DATA, "tags", "block", "ship_excluded.json"), {"values": [
        "#minecraft:dirt", "#minecraft:sand", "#minecraft:base_stone_overworld", "#minecraft:base_stone_nether",
        "#minecraft:ice", "#minecraft:snow", "minecraft:gravel", "minecraft:clay", "minecraft:bedrock",
        "minecraft:kelp", "minecraft:kelp_plant", "minecraft:seagrass", "minecraft:tall_seagrass"]})
    mc = os.path.join(ROOT, "data", "minecraft", "tags", "block", "mineable")
    write_json(os.path.join(mc, "pickaxe.json"), {"values": [f"{NS}:{b}" for b in BLOCKS if b != "ship_wheel"]})
    write_json(os.path.join(mc, "axe.json"), {"values": [f"{NS}:ship_wheel"]})

    write_json(os.path.join(ASSETS, "lang", "en_us.json"), {
        "block.shipwright.ship_core": "Ship Core",
        "block.shipwright.ship_wheel": "Ship's Wheel",
        "block.shipwright.engine": "Ship Engine",
        "block.shipwright.shaft": "Drive Shaft",
        "block.shipwright.propeller": "Propeller",
        "block.shipwright.anchor": "Ship Anchor",
        "container.shipwright.engine": "Ship Engine (fuel)"})

if __name__ == "__main__":
    make_textures(); make_models(); make_data()
    print("Shipwright assets generated.")
