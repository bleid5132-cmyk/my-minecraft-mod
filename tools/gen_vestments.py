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
    "palitsa": dict(parent="armorRightLeg", pivot=[-3.7, 6, -3.45]),
}

CUBES = []
def cube(bone, key, origin, size, inflate=0.0, rotation=None, pivot=None, mirror=False, uv=None):
    CUBES.append(dict(bone=bone, key=key, origin=origin, size=size, inflate=inflate,
                      rotation=rotation, pivot=pivot, mirror=mirror, uv_key=uv or key))

# --- ВОРОТНИК (слот головы): кольцо вокруг шеи + выпуклые кромки ---
for k, o, sz, mir, uv in (("collar_front", [-5.3, 23.5, -5.3], [10.6, 2, 1], False, None),
                          ("collar_back", [-5.3, 23.5, 4.3], [10.6, 3.5, 1], False, None),
                          ("collar_side_l", [4.3, 23.5, -4.3], [1, 2.5, 8.6], False, None),
                          ("collar_side_r", [-5.3, 23.5, -4.3], [1, 2.5, 8.6], True, "collar_side_l")):
    cube("armorHead", k, o, sz, 0.0, mirror=mir, uv=uv)
    cube("armorHead", k + "_o", o, sz, 0.2, mirror=mir, uv=(uv or k) + "_o")

# --- ФЕЛОНЬ (слот груди) ---
cube("armorBody", "phel_top", [-5, 16, -3], [10, 9, 6])                       # плечи, перед до пояса
cube("armorBody", "phel_top_o", [-5, 16, -3], [10, 9, 6], 0.22)               # накладной галун и кайма
BACK = dict(rotation=[3, 0, 0], pivot=[0, 25.5, 3.5])
cube("armorBody", "phel_back", [-5.5, 1, 3], [11, 24.5, 1], 0.03, **BACK)    # спина от плеч до пят
cube("armorBody", "phel_back_o", [-5.5, 1, 3], [11, 24.5, 1], 0.22, **BACK)  # кант, вышитый крест, кайма
cube("armorBody", "phel_side_l", [4.2, 1.5, -3], [1, 11, 6])                  # боковые полы
cube("armorBody", "phel_side_l_o", [4.2, 1.5, -3], [1, 11, 6], 0.18)
cube("armorBody", "phel_side_r", [-5.2, 1.5, -3], [1, 11, 6], mirror=True, uv="phel_side_l")
cube("armorBody", "phel_side_r_o", [-5.2, 1.5, -3], [1, 11, 6], 0.18, mirror=True, uv="phel_side_l_o")
cube("armorBody", "cross_v", [-0.5, 16.5, -3.6], [1, 4, 1])                   # наперсный крест
cube("armorBody", "cross_h", [-1.5, 18.5, -3.55], [3, 1, 1], 0.01)
for side, x0, mir in (("", 4, False), ("_r", -8, True)):
    bone = "armorLeftArm" if side == "" else "armorRightArm"
    cube(bone, "sleeve" + side, [x0, 13, -2], [4, 4, 4], 0.4, mirror=mir, uv="sleeve")    # рукав подризника
    cube(bone, "arm" + side, [x0, 16, -2], [4, 9, 4], 0.6, mirror=mir, uv="arm")          # фелонь на руке
    cube(bone, "arm_o" + side, [x0, 16, -2], [4, 9, 4], 0.85, mirror=mir, uv="arm_o")     # её кайма
    cube(bone, "cuff" + side, [x0, 12, -2], [4, 2, 4], 0.5, mirror=mir, uv="cuff")        # поручи
    cube(bone, "cuff_o" + side, [x0, 12, -2], [4, 2, 4], 0.68, mirror=mir, uv="cuff_o")

# --- ПОДРИЗНИК с епитрахилью, поясом и палицей (слот ног) ---
cube("armorWaist", "torso", [-4, 12, -2], [8, 4, 4], 0.45)                   # подризник на поясе
cube("armorWaist", "epi_top", [-1.5, 12, -2.95], [3, 4, 0.5])               # епитрахиль из-под фелони
cube("armorWaist", "belt", [-5, 13, -3], [10, 1, 6], 0.02)                    # пояс
cube("armorWaist", "belt_o", [-5, 13, -3], [10, 1, 6], 0.15)
SK_L = dict(rotation=[0, 0, -5], pivot=[2, 12, 0])
SK_R = dict(rotation=[0, 0, 5], pivot=[-2, 12, 0])
cube("armorLeftLeg", "skirt", [-0.5, 0, -2.5], [5, 12, 5], 0.05, **SK_L)
cube("armorLeftLeg", "skirt_o", [-0.5, 0, -2.5], [5, 12, 5], 0.2, **SK_L)    # накладная кайма подола
cube("armorLeftLeg", "epi_low", [0, 0.5, -3.1], [2, 11.5, 0.5], **SK_L)
cube("armorLeftLeg", "epi_low_o", [0, 0.5, -3.1], [2, 11.5, 0.5], 0.12, **SK_L)
cube("armorRightLeg", "skirt_r", [-4.5, 0, -2.5], [5, 12, 5], 0.06, mirror=True, uv="skirt", **SK_R)
cube("armorRightLeg", "skirt_r_o", [-4.5, 0, -2.5], [5, 12, 5], 0.21, mirror=True, uv="skirt_o", **SK_R)
cube("armorRightLeg", "epi_low_r", [-2, 0.5, -3.1], [2, 11.5, 0.5], mirror=True, uv="epi_low", **SK_R)
cube("armorRightLeg", "epi_low_r_o", [-2, 0.5, -3.1], [2, 11.5, 0.5], 0.12, mirror=True, uv="epi_low_o", **SK_R)
PAL = dict(rotation=[0, 0, 45], pivot=[-3.7, 6, -3.45])
cube("palitsa", "palitsa", [-5.2, 4.5, -3.7], [3, 3, 0.5], 0.0, **PAL)       # палица (ромб)
cube("palitsa", "palitsa_o", [-5.2, 4.5, -3.7], [3, 3, 0.5], 0.12, **PAL)

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

LIGHTER = {G0: G1, G1: G2, G2: G3, G3: G4, G4: G5, G5: G5}

def metal(r, seed=0, motifs=True):
    """Металлическое золото: светлее сверху, тёплая тень снизу, выпуклый узор с искрами,
    диагональный отблеск."""
    x0, y0, w, h = r
    for yy in range(h):
        t = yy / max(1, h - 1)
        base = G3 if (yy == 0 and h >= 4) else (G1 if t > 0.9 and h >= 6 else G2)
        for xx in range(w):
            px(r, xx, yy, base)
    if motifs:
        for yy in range(h):
            for xx in range(w):
                gx, gy = (xx + seed) % 6, (yy + seed * 3) % 6
                if (gx, gy) in ((2, 1), (1, 2), (3, 2), (2, 3)):
                    px(r, xx, yy, G3)
                elif (gx, gy) == (2, 2):
                    px(r, xx, yy, G5)
                elif (gx, gy) in ((3, 3), (2, 4)):
                    px(r, xx, yy, G1)           # тень под узором — эффект тиснения
    for yy in range(h):                          # диагональный отблеск металла
        for xx in range(w):
            if (xx - yy + seed) % 17 == 0:
                c = P[x0 + xx, y0 + yy][:3]
                px(r, xx, yy, LIGHTER.get(c, c))

def band(r, y):
    """Выпуклая полоса галуна в 3 ряда: блик, свет, тень."""
    row(r, y + 1, G3); row(r, y + 2, G1)
    for xx in range(1, r[2], 3): px(r, xx, y + 1, G4)    # бусины

def vband(r, x, c_out=G4, c_in=G1):
    col(r, x, c_out); col(r, x + (1 if x == 0 else -1), c_in)

def paint(key, fn):
    size = next(c["size"] for c in CUBES if c["uv_key"] == key)
    fn(faces(key, size))

SIDES = ("north", "east", "west", "south")

# ---------- воротник ----------
def p_collar(f):
    for n, r in f.items():
        metal(r, seed=1, motifs=r[3] >= 3)
    fill(f["up"], G3); fill(f["down"], G1)
def p_collar_o(f):            # выпуклые кромки сверху и снизу
    for n in SIDES:
        r = f[n]
        if r[3] >= 1: row(r, 0, G3)
        if r[3] >= 2: row(r, r[3] - 1, G1)
        for xx in range(1, r[2], 3): px(r, xx, 0, G4)
    fill(f["up"], G3)
for k in ("collar_front", "collar_back", "collar_side_l"):
    paint(k, p_collar); paint(k + "_o", p_collar_o)

# ---------- фелонь: плечи и перед ----------
def p_phel_top(f):
    for n in SIDES:
        metal(f[n], seed=1)
    n = f["north"]
    for (xx, yy) in ((2, 1), (3, 2), (4, 3)):   # тонкая цепочка креста
        px(n, xx, yy, G4); px(n, 9 - xx, yy, G4)
    metal(f["up"], seed=2)
    fill(f["down"], LIN)
def p_phel_top_o(f):
    for n in SIDES:
        band(f[n], 2)                            # галун на плечах
    n = f["north"]
    row(n, 0, G3)                                # кант по вырезу
    band(n, n[3] - 3)                            # кайма переднего подола
paint("phel_top", p_phel_top); paint("phel_top_o", p_phel_top_o)

# ---------- фелонь: спина от плеч до пят ----------
def p_phel_back(f):
    metal(f["south"], seed=3)
    fill(f["north"], LIN)
    for xx in range(0, f["north"][2], 3): col(f["north"], xx, LIND)
    for n in ("east", "west", "up", "down"):
        fill(f[n], G2)
def p_phel_back_o(f):
    s = f["south"]
    w, h = s[2], s[3]
    col(s, 0, G3); col(s, w - 1, G1)                                      # кант
    band(s, 2)
    cx = w // 2                                  # выпуклый вышитый крест
    for y in range(6, 17):
        px(s, cx, y, G3); px(s, cx + 1, y, G1)
    for x in range(cx - 3, cx + 4):
        px(s, x, 11, G3 if x != cx else G4); px(s, x, 12, G1)
    band(s, h - 4)
    row(s, h - 1, G0)
    for n in ("east", "west"):
        col(f[n], 0, G3)
paint("phel_back", p_phel_back); paint("phel_back_o", p_phel_back_o)

def p_phel_side(f):
    for n in ("east", "west"):
        metal(f[n], seed=4)
    for n in ("north", "south", "up", "down"):
        fill(f[n], G2)
def p_phel_side_o(f):
    for n in ("east", "west"):
        r = f[n]
        col(r, 0, G3); col(r, r[2] - 1, G1)
        band(r, r[3] - 3)
    col(f["north"], 0, G3)
paint("phel_side_l", p_phel_side); paint("phel_side_l_o", p_phel_side_o)

# ---------- наперсный крест ----------
def p_cross(f):
    for r in f.values():
        fill(r, G4)
        col(r, r[2] - 1, G1); row(r, r[3] - 1, G1)
        px(r, 0, 0, G5)
paint("cross_v", p_cross); paint("cross_h", p_cross)

# ---------- руки: рукав подризника, фелонь, поручи ----------
def p_sleeve(f):
    for r in f.values():
        fill(r, WH)
        for xx in range(1, r[2], 3): col(r, xx, WHD)
paint("sleeve", p_sleeve)

def p_arm(f):
    for n in SIDES:
        metal(f[n], seed=0)
    metal(f["up"], seed=2); fill(f["down"], LIN)
def p_arm_o(f):
    for n in SIDES:
        band(f[n], f[n][3] - 3)
paint("arm", p_arm); paint("arm_o", p_arm_o)

def p_cuff(f):
    for n in SIDES:
        metal(f[n], seed=2, motifs=False)
    fill(f["up"], G2); fill(f["down"], G1)
def p_cuff_o(f):
    for n in SIDES:
        r = f[n]
        row(r, 0, G3); row(r, r[3] - 1, G1)
    for n in ("north", "west", "east"):
        r = f[n]
        px(r, r[2] // 2, 0, G4)                             # искорка на поручах
paint("cuff", p_cuff); paint("cuff_o", p_cuff_o)

# ---------- подризник, пояс, епитрахиль, палица ----------
def p_torso(f):
    for r in f.values():
        fill(r, WH)
        for xx in range(2, r[2], 4): col(r, xx, WHD)
paint("torso", p_torso)

def p_epi(f):
    for r in f.values():
        metal(r, seed=5, motifs=False)
        col(r, 0, G4); col(r, r[2] - 1, G1)
paint("epi_top", p_epi)

def p_belt(f):
    for r in f.values():
        metal(r, seed=1, motifs=False)
def p_belt_o(f):
    n = f["north"]
    for xx in (n[2] // 2 - 1, n[2] // 2):     # пряжка
        px(n, xx, 0, G5)
paint("belt", p_belt); paint("belt_o", p_belt_o)

def p_skirt(f):
    for n, r in f.items():
        fill(r, WH)
        for xx in range(2, r[2], 4): col(r, xx, WHD)
    fill(f["down"], WHS)
def p_skirt_o(f):
    for n in SIDES:
        band(f[n], f[n][3] - 3)               # накладная золотая кайма подола
paint("skirt", p_skirt); paint("skirt_o", p_skirt_o)

def p_epi_low(f):
    for n, r in f.items():
        metal(r, seed=6)
def p_epi_low_o(f):
    n = f["north"]
    col(n, n[2] - 1, G4)                       # кант по внешнему краю
    for yy in (2, 6, 10):
        px(n, 0, yy, BTN)                      # пуговицы по шву
    for xx in range(n[2]):                     # бахрома
        px(n, xx, n[3] - 1, G5 if xx % 2 == 0 else G3)
paint("epi_low", p_epi_low); paint("epi_low_o", p_epi_low_o)

def p_palitsa(f):
    for r in f.values():
        fill(r, G2)
    px(f["north"], 1, 1, G3)
def p_palitsa_o(f):
    n = f["north"]
    for i in range(3):
        px(n, i, 0, G4); px(n, 0, i, G4); px(n, i, 2, G1); px(n, 2, i, G1)
    px(n, 1, 1, G5)
paint("palitsa", p_palitsa); paint("palitsa_o", p_palitsa_o)

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
