"""Генератор 3D-брони «Облачение священника» для Armor Model API (Fabric 1.20.1).

Создаёт:
  out/priest_vestments.geo.json   — 3D-модель (Bedrock geometry 1.12.0)
  out/priest_vestments.png        — текстура 128x128 (box UV)
  out/preview.png                 — превью спереди и сзади на блочном персонаже
Всё нарисовано с нуля.
"""
import json, math, os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "out")
os.makedirs(OUT, exist_ok=True)

# ---------- палитра: металлическое золото ----------
G0 = (110, 58, 6)       # самая тёмная кромка
G1 = (184, 108, 14)     # тень (тёплая, оранжевая — не коричневая)
G2 = (226, 166, 32)     # основное золото
G3 = (246, 206, 72)     # светлое золото
G4 = (255, 240, 160)    # блик
G5 = (255, 252, 222)    # искра
LIN = (214, 188, 120)   # атласная подкладка
LIND = (176, 148, 90)
WH = (240, 237, 228)    # белый подризник
WHD = (210, 204, 192)
WHS = (180, 174, 162)
BTN = (120, 60, 10)
T = (0, 0, 0, 0)

# ---------- описание модели ----------
# (bone, key, origin, size, inflate, rotation, pivot, mirror, uv_key)
BONES = {
    "bipedHead": dict(parent=None, pivot=[0, 24, 0]),
    "armorHead": dict(parent="bipedHead", pivot=[0, 24, 0]),
    "bipedBody": dict(parent=None, pivot=[0, 24, 0]),
    "armorBody": dict(parent="bipedBody", pivot=[0, 24, 0]),
    "armorWaist": dict(parent="bipedBody", pivot=[0, 24, 0]),
    "bipedLeftArm": dict(parent=None, pivot=[5, 22, 0]),
    "armorLeftArm": dict(parent="bipedLeftArm", pivot=[5, 22, 0]),
    "bipedRightArm": dict(parent=None, pivot=[-5, 22, 0]),
    "armorRightArm": dict(parent="bipedRightArm", pivot=[-5, 22, 0]),
    "bipedLeftLeg": dict(parent=None, pivot=[2, 12, 0]),
    "armorLeftLeg": dict(parent="bipedLeftLeg", pivot=[2, 12, 0]),
    "bipedRightLeg": dict(parent=None, pivot=[-2, 12, 0]),
    "armorRightLeg": dict(parent="bipedRightLeg", pivot=[-2, 12, 0]),
}

CUBES = []
def cube(bone, key, origin, size, inflate=0.0, rotation=None, pivot=None, mirror=False, uv=None):
    CUBES.append(dict(bone=bone, key=key, origin=origin, size=size, inflate=inflate,
                      rotation=rotation, pivot=pivot, mirror=mirror, uv_key=uv or key))

# --- ВОРОТНИК (слот головы): низкий спереди, высокий жёсткий сзади, отогнут наружу ---
for k, o, mir, uv in (("collar_side_l", [4.3, 23.5, -4.3], False, None),
                      ("collar_side_r", [-5.3, 23.5, -4.3], True, "collar_side_l")):
    cube("armorHead", k, o, [1, 3, 8.6], 0.0, mirror=mir, uv=uv)
    cube("armorHead", k + "_o", o, [1, 3, 8.6], 0.2, mirror=mir, uv=(uv or k) + "_o")
CB = dict(rotation=[-12, 0, 0], pivot=[0, 23.5, 4.8])
cube("armorHead", "collar_back", [-5.3, 23.5, 4.3], [10.6, 5, 1], **CB)
cube("armorHead", "collar_back_o", [-5.3, 23.5, 4.3], [10.6, 5, 1], 0.2, **CB)

# --- ФЕЛОНЬ (слот груди): плечи, перед до пояса, спина до пояса, «колокола» на плечах ---
cube("armorBody", "phel_top", [-5, 16, -3], [10, 9, 6])
cube("armorBody", "phel_top_o", [-5, 16, -3], [10, 9, 6], 0.22)
BU = dict(rotation=[8, 0, 0], pivot=[0, 25.5, 3.7])
cube("armorBody", "back_up", [-5.5, 0.5, 3.2], [11, 25, 1], 0.03, **BU)     # цельный плащ от плеч
cube("armorBody", "back_up_o", [-5.5, 0.5, 3.2], [11, 25, 1], 0.22, **BU)
cube("armorBody", "cross_v", [-0.75, 15.5, -3.8], [1.5, 5.5, 1])            # наперсный крест
cube("armorBody", "cross_h", [-2, 18.5, -3.75], [4, 1.5, 1], 0.01)
cube("armorBody", "cross_gem", [-0.5, 18.75, -4.1], [1, 1, 0.5])             # красный камень
for side, mir in (("l", False), ("r", True)):
    bone = "armorLeftArm" if side == "l" else "armorRightArm"
    sx = 1 if side == "l" else -1
    def X(x0, w): return x0 if side == "l" else -(x0 + w)
    SH = dict(rotation=[0, 0, -9 * sx], pivot=[5 * sx, 25, 0])
    cube(bone, "mantle_" + side, [X(3.3, 5.8), 16.5, -3.3], [5.8, 8.8, 6.6], 0.0, mirror=mir, uv="mantle", **SH)
    cube(bone, "mantle_o_" + side, [X(3.3, 5.8), 16.5, -3.3], [5.8, 8.8, 6.6], 0.2, mirror=mir, uv="mantle_o", **SH)
    cube(bone, "sleeve_" + side, [X(4, 4), 13, -2], [4, 5, 4], 0.35, mirror=mir, uv="sleeve")
    CF = dict()
    cube(bone, "cuff_" + side, [X(3.5, 5), 11.3, -2.5], [5, 3, 5], 0.0, mirror=mir, uv="cuff", **CF)
    cube(bone, "cuff_o_" + side, [X(3.5, 5), 11.3, -2.5], [5, 3, 5], 0.18, mirror=mir, uv="cuff_o", **CF)

# --- ПОДРИЗНИК + низ фелони (слот ног) ---
cube("armorWaist", "torso", [-4, 12, -2], [8, 4, 4], 0.45)
cube("armorWaist", "epi_top", [-1.75, 12, -3.05], [3.5, 4, 0.6])
cube("armorWaist", "belt", [-5, 13, -3], [10, 1, 6], 0.02)
cube("armorWaist", "belt_o", [-5, 13, -3], [10, 1, 6], 0.15)
cube("armorWaist", "buckle", [-1, 12.6, -3.45], [2, 1.8, 0.5])
for side, mir in (("l", False), ("r", True)):
    bone = "armorLeftLeg" if side == "l" else "armorRightLeg"
    sx = 1 if side == "l" else -1
    def X(x0, w): return x0 if side == "l" else -(x0 + w)
    hip = [2 * sx, 12, 0]
    e = 0.01 if side == "r" else 0.0          # против мерцания в месте стыка половин
    cube(bone, "skirt_up_" + side, [X(-1.4, 6), 4, -2.6], [6, 8, 5.2], 0.05 + e, mirror=mir, uv="skirt_up",
         rotation=[0, 0, -4 * sx], pivot=hip)
    cube(bone, "skirt_low_" + side, [X(-3.2, 8.2), 0, -3.0], [8.2, 5, 6], 0.1 + e, mirror=mir, uv="skirt_low",
         rotation=[0, 0, -9 * sx], pivot=hip)
    cube(bone, "skirt_low_o_" + side, [X(-3.2, 8.2), 0, -3.0], [8.2, 5, 6], 0.28 + e, mirror=mir, uv="skirt_low_o",
         rotation=[0, 0, -9 * sx], pivot=hip)
    SD = dict(rotation=[0, 0, -7 * sx], pivot=hip)
    cube(bone, "side_low_" + side, [X(4.6, 1), 0.8, -3.2], [1, 11.2, 7.2], 0.0, mirror=mir, uv="side_low", **SD)
    cube(bone, "side_low_o_" + side, [X(4.6, 1), 0.8, -3.2], [1, 11.2, 7.2], 0.18, mirror=mir, uv="side_low_o", **SD)
    EP = dict()                                # епитрахиль висит прямо, без зазора по центру
    cube(bone, "epi_low_" + side, [X(-0.05, 2.45), 0.4, -3.6], [2.45, 11.6, 0.8], 0.0 + e, mirror=mir, uv="epi_low", **EP)
    cube(bone, "epi_low_o_" + side, [X(-0.05, 2.45), 0.4, -3.6], [2.45, 11.6, 0.8], 0.12 + e, mirror=mir, uv="epi_low_o", **EP)
PAL = dict(rotation=[0, 0, 45], pivot=[-4.6, 6.5, -3.9])
cube("armorRightLeg", "palitsa", [-6.6, 4.5, -4.2], [4, 4, 0.6], 0.0, **PAL)
cube("armorRightLeg", "palitsa_o", [-6.6, 4.5, -4.2], [4, 4, 0.6], 0.15, **PAL)
cube("armorRightLeg", "palitsa_gem", [-5.1, 6.0, -4.55], [1, 1, 0.5], 0.0, **PAL)

# ---------- раскладка UV (box UV, размеры округляются вниз, как в библиотеке) ----------
def fsz(s): return [int(math.floor(v)) for v in s]
def region(s):
    w, h, d = fsz(s)
    return 2 * (d + w), d + h

UV = {}
uniq = []
for c in CUBES:
    if c["uv_key"] not in [k for k, _ in uniq]:
        uniq.append((c["uv_key"], c["size"]))
uniq.sort(key=lambda kv: -region(kv[1])[1])
x = y = shelf = 0
for k, s in uniq:
    rw, rh = region(s)
    if x + rw > 128:
        x, y, shelf = 0, y + shelf + 1, 0
    UV[k] = (x, y)
    x += rw + 1
    shelf = max(shelf, rh)
assert y + shelf <= 128, "текстура не влезает в 128x128"

# ---------- рисование текстуры ----------
tex = Image.new("RGBA", (128, 128), T)
P = tex.load()

def faces(key, size):
    """Прямоугольники граней внутри текстуры: name -> (x, y, w, h)."""
    u, v = UV[key]
    w, h, d = fsz(size)
    return {
        "up": (u + d, v, w, d), "down": (u + d + w, v, w, d),
        "east": (u, v + d, d, h), "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h),
    }

def fill(r, c):
    x0, y0, w, h = r
    for yy in range(y0, y0 + h):
        for xx in range(x0, x0 + w):
            P[xx, yy] = c + (255,)

def px(r, x, y, c):
    x0, y0, w, h = r
    if 0 <= x < w and 0 <= y < h:
        P[x0 + x, y0 + y] = c + (255,)

def row(r, y, c):
    for x in range(r[2]): px(r, x, y, c)

def col(r, x, c):
    for y in range(r[3]): px(r, x, y, c)

RED = (196, 36, 48); REDL = (244, 110, 104); REDD = (118, 16, 26)
LINR = (134, 30, 40); LINRD = (98, 20, 30)          # тёмно-красная подкладка
LIGHTER = {G0: G1, G1: G2, G2: G3, G3: G4, G4: G5, G5: G5}
import random as _rnd

def metal(r, seed=0, motifs=True):
    """Объёмная золотая парча: зернистость, светлый верх, тень снизу, тиснёный узор."""
    rng = _rnd.Random(seed * 7919 + r[0] * 131 + r[1])
    x0, y0, w, h = r
    for yy in range(h):
        for xx in range(w):
            q = rng.random()
            c = G1 if q < 0.06 else (G3 if q > 0.95 else G2)
            if yy == 0 and h >= 4: c = G3
            if yy == h - 1 and h >= 5: c = G1
            px(r, xx, yy, c)
    if motifs and w >= 4 and h >= 4:
        for yy in range(h):
            for xx in range(w):
                gx, gy = (xx + seed) % 6, (yy + seed * 3) % 6
                if (gx, gy) in ((2, 1), (1, 2), (3, 2), (2, 3)): px(r, xx, yy, G3)
                elif (gx, gy) == (2, 2): px(r, xx, yy, G4)
                elif (gx, gy) in ((3, 3), (2, 4)): px(r, xx, yy, G1)

def outline(r, top=False, bottom=True, sides=True):
    w, h = r[2], r[3]
    if sides and w >= 3: col(r, 0, G0); col(r, w - 1, G0)
    if bottom and h >= 3: row(r, h - 1, G0)
    if top and h >= 3: row(r, 0, G0)

def band(r, y, outl=True):
    """Накладной галун: светлая нить с бусинами, тень и тёмная обводка."""
    row(r, y, G3); row(r, y + 1, G1)
    for xx in range(1, r[2], 3): px(r, xx, y, G4)
    if outl: row(r, y + 2, G0); row(r, y - 1, G0)

def gem(r):
    fill(r, RED)
    px(r, 0, 0, REDL)
    if r[2] > 1: px(r, r[2] - 1, r[3] - 1, REDD)

def linen(r, folds=4):
    fill(r, WH)
    for xx in range(2, r[2], folds): col(r, xx, WHD)

def paint(key, fn):
    size = next(c["size"] for c in CUBES if c["uv_key"] == key)
    fn(faces(key, size))

SIDES = ("north", "east", "west", "south")
def clear(f):
    pass

# ---------- воротник ----------
def p_collar(f):
    for n in SIDES: metal(f[n], seed=1, motifs=False)
    fill(f["up"], G3); fill(f["down"], LINR)
    for n in SIDES: outline(f[n], bottom=True, sides=False)
def p_collar_o(f):
    for n in SIDES:
        r = f[n]
        row(r, 0, G0)
        if r[3] >= 3: row(r, 1, G3); [px(r, xx, 1, G4) for xx in range(1, r[2], 3)]
    fill(f["up"], G3)
for k in ("collar_side_l", "collar_back"):
    paint(k, p_collar); paint(k + "_o", p_collar_o)
def p_collar_back_extra(f):
    s, n = f["south"], f["north"]
    for r in (s, n):                                  # вышитый крестик на высокой части
        cx = r[2] // 2
        for yy in range(1, 4): px(r, cx, yy, G4)
        px(r, cx - 1, 2, G4); px(r, cx + 1, 2, G4)
paint("collar_back", p_collar_back_extra)

# ---------- фелонь: верх ----------
def p_phel_top(f):
    for n in SIDES: metal(f[n], seed=1)
    n = f["north"]
    for (xx, yy) in ((2, 1), (3, 2), (4, 3)):           # цепочка креста
        px(n, xx, yy, G4); px(n, 9 - xx, yy, G4)
    metal(f["up"], seed=2)
    fill(f["down"], LINR)
def p_phel_top_o(f):
    for n in ("east", "west", "south"):
        band(f[n], 3, outl=False)
    n = f["north"]
    band(n, 3, outl=False)
    band(n, n[3] - 3, outl=False); row(n, n[3] - 1, G0)  # кайма подола с обводкой
    u = f["up"]; row(u, 0, G0)
paint("phel_top", p_phel_top); paint("phel_top_o", p_phel_top_o)

def p_back_up(f):
    s = f["south"]; metal(s, seed=3)
    fill(f["north"], LINR); [col(f["north"], xx, LINRD) for xx in range(0, f["north"][2], 3)]
    for n in ("east", "west", "up", "down"): fill(f[n], G1)
def p_back_up_o(f):
    s = f["south"]; w, h = s[2], s[3]
    col(s, 0, G0); col(s, 1, G3); col(s, w - 1, G0); col(s, w - 2, G1)
    band(s, 3, outl=False)
    cx = w // 2                                          # большой вышитый крест с обводкой
    for y in range(6, 18):
        px(s, cx - 2, y, G0); px(s, cx + 2, y, G0)
    row(s, 18, G0) if False else None
    px(s, cx - 1, 18, G0); px(s, cx, 18, G0); px(s, cx + 1, 18, G0)
    for x in range(cx - 5, cx + 6):
        px(s, x, 7, G0); px(s, x, 11, G0)
    px(s, cx - 5, 8, G0); px(s, cx - 5, 9, G0); px(s, cx - 5, 10, G0)
    px(s, cx + 5, 8, G0); px(s, cx + 5, 9, G0); px(s, cx + 5, 10, G0)
    px(s, cx - 1, 6, G0); px(s, cx, 6, G0); px(s, cx + 1, 6, G0)
    for y in range(7, 18):
        px(s, cx, y, G4); px(s, cx - 1, y, G3); px(s, cx + 1, y, G1)
    band(s, h - 4, outl=False); row(s, h - 2, G1); row(s, h - 1, G0)   # кайма плаща
    for x in range(cx - 4, cx + 5):
        px(s, x, 8, G3); px(s, x, 9, G4); px(s, x, 10, G1)
    px(s, cx, 9, RED); px(s, cx, 8, REDL)
    for n in ("east", "west"): col(f[n], 0, G0)
paint("back_up", p_back_up); paint("back_up_o", p_back_up_o)

def p_cross(f):
    for r in f.values():
        fill(r, G4)
        if r[3] >= 3: row(r, r[3] - 1, G1)
        if r[2] >= 3: col(r, r[2] - 1, G1)
        px(r, 0, 0, G5)
paint("cross_v", p_cross); paint("cross_h", p_cross)
paint("cross_gem", lambda f: [gem(r) for r in f.values()])

# ---------- руки ----------
def p_mantle(f):
    for n in SIDES: metal(f[n], seed=5)
    metal(f["up"], seed=6)
    fill(f["down"], LINR)
def p_mantle_o(f):
    for n in SIDES:
        r = f[n]
        band(r, r[3] - 3, outl=False); row(r, r[3] - 1, G0)
paint("mantle", p_mantle); paint("mantle_o", p_mantle_o)

paint("sleeve", lambda f: [linen(r, 3) for r in f.values()])

def p_cuff(f):
    for n in SIDES: metal(f[n], seed=2, motifs=False)
    fill(f["up"], G1); fill(f["down"], LINR)
def p_cuff_o(f):
    for n in SIDES:
        r = f[n]
        row(r, 0, G0); row(r, r[3] - 1, G0)
        cx = r[2] // 2
        px(r, cx, 1, G4); px(r, cx - 1, 1, G3); px(r, cx + 1, 1, G3)
    for n in ("north", "east", "west"):
        r = f[n]; px(r, r[2] // 2, 1, RED)                # камень на поручах
paint("cuff", p_cuff); paint("cuff_o", p_cuff_o)

# ---------- пояс, епитрахиль ----------
paint("torso", lambda f: [linen(r) for r in f.values()])
def p_epi(f):
    for r in f.values():
        metal(r, seed=5, motifs=False); col(r, 0, G3)
        for yy in range(1, r[3], 3): px(r, r[2] // 2, yy, G4)
paint("epi_top", p_epi)
def p_belt(f):
    for r in f.values(): metal(r, seed=1, motifs=False)
def p_belt_o(f):
    for n in SIDES:
        r = f[n]
        for xx in range(0, r[2], 2): px(r, xx, 0, G0)
paint("belt", p_belt); paint("belt_o", p_belt_o)
def p_buckle(f):
    for r in f.values(): fill(r, G4); outline(r, top=True)
    px(f["north"], 0, 0, RED); px(f["north"], 1, 0, RED)
paint("buckle", p_buckle)

# ---------- подризник ----------
def p_skirt_up(f):
    for r in f.values(): linen(r)
paint("skirt_up", p_skirt_up)
def p_skirt_low(f):
    for n, r in f.items(): linen(r, 3)
    fill(f["down"], WHS)
def p_skirt_low_o(f):
    for n in SIDES:
        r = f[n]
        band(r, r[3] - 3, outl=False); row(r, r[3] - 1, G0)
        row(r, r[3] - 4, G0)
paint("skirt_low", p_skirt_low); paint("skirt_low_o", p_skirt_low_o)

# ---------- низ фелони (сзади и по бокам) ----------
def p_back_low(f):
    metal(f["south"], seed=7)
    fill(f["north"], LINR); [col(f["north"], xx, LINRD) for xx in range(0, f["north"][2], 3)]
    for n in ("east", "west", "up", "down"): fill(f[n], G1)
def p_back_low_o(f):
    s = f["south"]; w, h = s[2], s[3]
    col(s, w - 1, G0); col(s, w - 2, G3)                  # кант по внешнему краю
    band(s, h - 4, outl=False); row(s, h - 2, G1); row(s, h - 1, G0)
    for n in ("east", "west"): col(f[n], 0, G0)

def p_side_low(f):
    for n in ("east", "west"): metal(f[n], seed=8)
    for n in ("north", "south", "up", "down"): fill(f[n], G2)
    fill(f["east"] if False else f["down"], LINR)
def p_side_low_o(f):
    for n in ("east", "west"):
        r = f[n]
        col(r, 0, G0); col(r, 1, G3); col(r, r[2] - 1, G0)
        band(r, r[3] - 3, outl=False); row(r, r[3] - 1, G0)
    col(f["north"], 0, G3)
paint("side_low", p_side_low); paint("side_low_o", p_side_low_o)

# ---------- епитрахиль внизу ----------
def p_epi_low(f):
    for n, r in f.items(): metal(r, seed=6)
    n = f["north"]
    for yy in (2, 7):                                     # вышитые кресты
        px(n, 1, yy, G5); px(n, 1, yy - 1, G4); px(n, 1, yy + 1, G4); px(n, 0, yy, G4)
def p_epi_low_o(f):
    n = f["north"]
    col(n, n[2] - 1, G3)
    for yy in (4, 9):
        px(n, 0, yy, BTN)                                 # пуговицы по шву
    for xx in range(n[2]):                                # бахрома
        px(n, xx, n[3] - 1, G5 if xx % 2 == 0 else G1)
    row(n, n[3] - 2, G0)
paint("epi_low", p_epi_low); paint("epi_low_o", p_epi_low_o)

# ---------- палица ----------
def p_palitsa(f):
    for r in f.values(): metal(r, seed=9, motifs=False)
def p_palitsa_o(f):
    n = f["north"]; w, h = n[2], n[3]
    row(n, 0, G0); row(n, h - 1, G0); col(n, 0, G0); col(n, w - 1, G0)
    for i in range(1, w - 1): px(n, i, 1, G3); px(n, 1, i, G3)
paint("palitsa", p_palitsa); paint("palitsa_o", p_palitsa_o)
paint("palitsa_gem", lambda f: [gem(r) for r in f.values()])

tex.save(os.path.join(OUT, "priest_vestments.png"))

# ---------- geo.json ----------
def r6(v): return round(v, 4)
bones_out = []
for name, b in BONES.items():
    bo = {"name": name, "pivot": b["pivot"]}
    if b["parent"]: bo["parent"] = b["parent"]
    if name == "palitsa":
        pass
    cs = []
    for c in CUBES:
        if c["bone"] != name: continue
        co = {"origin": [r6(v) for v in c["origin"]], "size": [r6(v) for v in c["size"]], "uv": list(UV[c["uv_key"]])}
        if c["inflate"]: co["inflate"] = c["inflate"]
        if c["rotation"]:
            co["pivot"] = c["pivot"]; co["rotation"] = c["rotation"]
        if c["mirror"]: co["mirror"] = True
        cs.append(co)
    if cs: bo["cubes"] = cs
    bones_out.append(bo)
geo = {"format_version": "1.12.0", "minecraft:geometry": [{
    "description": {"identifier": "geometry.priest_vestments", "texture_width": 128, "texture_height": 128,
                    "visible_bounds_width": 4, "visible_bounds_height": 4.5, "visible_bounds_offset": [0, 1.75, 0]},
    "bones": bones_out}]}
names = [b["name"] for b in bones_out]
assert len(names) == len(set(names))
for b in bones_out:
    if b.get("cubes"): assert b.get("parent"), b["name"]
json.dump(geo, open(os.path.join(OUT, "priest_vestments.geo.json"), "w"), indent=2)

# ---------- превью ----------
def rot_bedrock(v, rot):
    """Поворот в пространстве Bedrock по правилам библиотеки: Rz(-rz)·Ry(ry)·Rx(-rx)."""
    x, y, z = v
    rx, ry, rz = [math.radians(a) for a in rot]
    a = -rx; y, z = y * math.cos(a) - z * math.sin(a), y * math.sin(a) + z * math.cos(a)
    a = ry;  x, z = x * math.cos(a) + z * math.sin(a), -x * math.sin(a) + z * math.cos(a)
    a = -rz; x, y = x * math.cos(a) - y * math.sin(a), x * math.sin(a) + y * math.cos(a)
    return (x, y, z)

def face_quads(c, texture):
    ox, oy, oz = c["origin"]; sx, sy, sz = c["size"]; i = c["inflate"]
    x0, y0, z0 = ox - i, oy - i, oz - i
    x1, y1, z1 = ox + sx + i, oy + sy + i, oz + sz + i
    f = faces(c["uv_key"], c["size"])
    quads = {   # TL, TR, BL, BR в координатах текстуры
        "north": ((x0, y1, z0), (x1, y1, z0), (x0, y0, z0), (x1, y0, z0)),
        "south": ((x1, y1, z1), (x0, y1, z1), (x1, y0, z1), (x0, y0, z1)),
        "west": ((x1, y1, z1), (x1, y1, z0), (x1, y0, z1), (x1, y0, z0)),   # грань +x (вид сбоку)
    }
    out = []
    for n, (tl, tr, bl, br) in quads.items():
        if c["mirror"]: tl, tr, bl = tr, tl, br
        if c["rotation"]:
            p = c["pivot"]
            def R(v):
                w = rot_bedrock((v[0] - p[0], v[1] - p[1], v[2] - p[2]), c["rotation"])
                return (w[0] + p[0], w[1] + p[1], w[2] + p[2])
            tl, tr, bl = R(tl), R(tr), R(bl)
        x, y, w, h = f[n]
        if w == 0 or h == 0: continue
        out.append((n, tl, tr, bl, texture.crop((x, y, x + w, y + h))))
    return out

# свой блочный персонаж (седой бородатый священник) — простые цветные блоки
SKIN = (222, 170, 130); HAIR = (150, 146, 140); SHOE = (40, 34, 30); SHIRT = (90, 70, 60)
def solid(w, h, c): return Image.new("RGBA", (w, h), c + (255,))
def head_face(front):
    im = Image.new("RGBA", (8, 8), SKIN + (255,)); d = im.load()
    for yy in range(8):
        for xx in range(8):
            if yy <= 1 or (not front and yy <= 7): d[xx, yy] = HAIR + (255,)
            if front and (yy >= 5 or (yy == 4 and xx in (0, 1, 6, 7)) or (yy == 2 and xx in (0, 7))): d[xx, yy] = HAIR + (255,)
    if front:
        d[1, 3] = (250, 250, 250, 255); d[2, 3] = (40, 40, 50, 255); d[5, 3] = (40, 40, 50, 255); d[6, 3] = (250, 250, 250, 255)
    return im
BODY = [  # (origin, size, front_img, back_img)
    ([-4, 24, -4], [8, 8, 8], head_face(True), head_face(False)),
    ([-4, 12, -2], [8, 12, 4], solid(8, 12, SHIRT), solid(8, 12, SHIRT)),
    ([4, 12, -2], [4, 12, 4], solid(4, 12, SKIN), solid(4, 12, SKIN)),
    ([-8, 12, -2], [4, 12, 4], solid(4, 12, SKIN), solid(4, 12, SKIN)),
    ([0, 0, -2], [4, 12, 4], solid(4, 12, SHOE), solid(4, 12, SHOE)),
    ([-4, 0, -2], [4, 12, 4], solid(4, 12, SHOE), solid(4, 12, SHOE)),
]

S = 16
def render(view):
    W, H = 22 * S, 36 * S
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    def to2d(p):
        x, y, z = p
        sx = x if view == "front" else (-x if view == "back" else -z)
        return (W / 2 + sx * S, (33 - y) * S)
    items = []
    for o, s, fimg, bimg in BODY:
        x0, y0, z0 = o; x1, y1, z1 = x0 + s[0], y0 + s[1], z0 + s[2]
        if view == "front": items.append((z0, (x0, y1, z0), (x1, y1, z0), (x0, y0, z0), fimg))
        elif view == "back": items.append((-z1, (x1, y1, z1), (x0, y1, z1), (x1, y0, z1), bimg))
        else:
            side = bimg if s[0] == 8 and s[1] == 8 else fimg
            items.append((-x1, (x1, y1, z1), (x1, y1, z0), (x1, y0, z1), side.resize((s[2], s[1]))))
    for c in CUBES:
        for n, tl, tr, bl, im in face_quads(c, tex):
            want = {"front": "north", "back": "south", "side": "west"}[view]
            if n != want: continue
            if view == "side":
                depth = -(tl[0] + tr[0] + bl[0]) / 3
            else:
                depth = (tl[2] + tr[2] + bl[2]) / 3
                if view == "back": depth = -depth
            items.append((depth, tl, tr, bl, im))
    items.sort(key=lambda t: -t[0])   # дальние первыми
    for _, tl, tr, bl, im in items:
        P0, P1, P2 = to2d(tl), to2d(tr), to2d(bl)
        w, h = im.size
        # affine: dest -> src
        ax, ay = P1[0] - P0[0], P1[1] - P0[1]
        bx, by = P2[0] - P0[0], P2[1] - P0[1]
        det = ax * by - ay * bx
        if abs(det) < 1e-6: continue
        ia, ib = by / det, -bx / det
        ic, id_ = -ay / det, ax / det
        xs = [P0[0], P1[0], P2[0], P1[0] + bx]; ys = [P0[1], P1[1], P2[1], P1[1] + by]
        bx0, by0 = int(min(xs)), int(min(ys)); bx1, by1 = int(math.ceil(max(xs))), int(math.ceil(max(ys)))
        bw, bh = max(1, bx1 - bx0), max(1, by1 - by0)
        # source u = w*(ia*(X-P0x)+ib*(Y-P0y)), v = h*(ic*(X-P0x)+id*(Y-P0y))
        a = w * ia; b = w * ib; cc = w * (ia * (bx0 - P0[0]) + ib * (by0 - P0[1]))
        d = h * ic; e = h * id_; ff = h * (ic * (bx0 - P0[0]) + id_ * (by0 - P0[1]))
        src = im.convert("RGBA")
        warped = src.transform((bw, bh), Image.AFFINE, (a, b, cc, d, e, ff), resample=Image.NEAREST)
        # маска: только внутри четырёхугольника
        mask = Image.new("L", (bw, bh), 0)
        ImageDraw.Draw(mask).polygon([(P0[0] - bx0, P0[1] - by0), (P1[0] - bx0, P1[1] - by0),
                                      (P1[0] + bx - bx0, P1[1] + by - by0), (P2[0] - bx0, P2[1] - by0)], fill=255)
        alpha = Image.composite(warped.getchannel("A"), Image.new("L", (bw, bh), 0), mask)
        warped.putalpha(alpha)
        img.alpha_composite(warped, (bx0, by0))
    return img

front, back, side = render("front"), render("back"), render("side")
cw, ch = front.width * 3 + 80, front.height + 70
can = Image.new("RGBA", (cw, ch))
dr = ImageDraw.Draw(can)
for yy in range(ch):
    t = yy / ch
    dr.line([(0, yy), (cw, yy)], fill=(int(140 + 60 * t), int(190 + 30 * t), int(235 - 10 * t), 255))
dr.rectangle([0, ch - 56, cw, ch], fill=(110, 160, 70, 255))
can.alpha_composite(front, (20, 10)); can.alpha_composite(back, (40 + front.width, 10)); can.alpha_composite(side, (60 + 2 * front.width, 10))
fnt = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 26)
for txt, x0 in (("Спереди", 20), ("Сзади", 40 + front.width), ("Сбоку", 60 + 2 * front.width)):
    tw = dr.textlength(txt, font=fnt)
    dr.text((x0 + front.width / 2 - tw / 2, ch - 46), txt, font=fnt, fill="white", stroke_width=2, stroke_fill=(40, 40, 40))
can.convert("RGB").save(os.path.join(OUT, "preview.png"))
tex.resize((512, 512), Image.NEAREST).save(os.path.join(OUT, "texture_x4.png"))
print("UV:", UV)
