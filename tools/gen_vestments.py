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

# ---------- палитра ----------
OUTL = (70, 42, 8)
D = (150, 98, 14)       # тень золота
M = (212, 158, 26)      # золото
L = (246, 204, 54)      # светлое золото
HL = (255, 238, 140)    # блик
LIN = (168, 138, 92)    # подкладка
LIND = (120, 94, 58)
WH = (238, 234, 224)    # белый подризник
WHD = (204, 198, 186)
WHS = (176, 170, 158)
BTN = (110, 70, 20)
SIL = (236, 236, 240)   # наперсный крест (серебро)
SILD = (170, 170, 182)
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

# --- ВОРОТНИК (слот головы): невысокое кольцо вокруг шеи, сзади выше ---
cube("armorHead", "collar_front", [-5.3, 23.5, -5.3], [10.6, 2, 1])
cube("armorHead", "collar_back", [-5.3, 23.5, 4.3], [10.6, 3.5, 1])
cube("armorHead", "collar_side_l", [4.3, 23.5, -4.3], [1, 2.5, 8.6])
cube("armorHead", "collar_side_r", [-5.3, 23.5, -4.3], [1, 2.5, 8.6], mirror=True, uv="collar_side_l")

# --- ФЕЛОНЬ (слот груди) ---
cube("armorBody", "phel_top", [-5, 16, -3], [10, 9, 6])                       # плечи, спереди до пояса
cube("armorBody", "phel_back", [-5, 1, 2.2], [10, 15, 1], 0.02,
     rotation=[6, 0, 0], pivot=[0, 16, 2.7])                                  # длинная спина почти до пят
cube("armorBody", "phel_side_l", [4.2, 1.5, -3], [1, 11, 6])                  # боковые полы
cube("armorBody", "phel_side_r", [-5.2, 1.5, -3], [1, 11, 6], mirror=True, uv="phel_side_l")
cube("armorBody", "cross_v", [-0.5, 16.5, -3.6], [1, 4, 1])                   # наперсный крест
cube("armorBody", "cross_h", [-1.5, 18.5, -3.55], [3, 1, 1], 0.01)
cube("armorLeftArm", "arm", [4, 15, -2], [4, 10, 4], 0.6)                     # фелонь на руках
cube("armorLeftArm", "cuff", [4, 12, -2], [4, 3, 4], 0.4)                     # поручи
cube("armorRightArm", "arm_r", [-8, 15, -2], [4, 10, 4], 0.6, mirror=True, uv="arm")
cube("armorRightArm", "cuff_r", [-8, 12, -2], [4, 3, 4], 0.4, mirror=True, uv="cuff")

# --- ПОДРИЗНИК с епитрахилью, поясом и палицей (слот ног) ---
cube("armorWaist", "torso", [-4, 12, -2], [8, 4, 4], 0.45)                   # подризник на поясе
cube("armorWaist", "epi_top", [-1.5, 12, -2.95], [3, 4, 0.5])               # епитрахиль из-под фелони
cube("armorWaist", "belt", [-5, 13, -3], [10, 1, 6], 0.02)                    # пояс
SK_L = dict(rotation=[0, 0, -5], pivot=[2, 12, 0])
SK_R = dict(rotation=[0, 0, 5], pivot=[-2, 12, 0])
cube("armorLeftLeg", "skirt", [-0.5, 0, -2.5], [5, 12, 5], 0.05, **SK_L)     # подол расходится наружу
cube("armorLeftLeg", "epi_low", [0, 0.5, -3.1], [2, 11.5, 0.5], **SK_L)      # епитрахиль до низа
cube("armorRightLeg", "skirt_r", [-4.5, 0, -2.5], [5, 12, 5], 0.06, mirror=True, uv="skirt", **SK_R)
cube("armorRightLeg", "epi_low_r", [-2, 0.5, -3.1], [2, 11.5, 0.5], mirror=True, uv="epi_low", **SK_R)
cube("palitsa", "palitsa", [-5.2, 4.5, -3.7], [3, 3, 0.5], 0.0,
     rotation=[0, 0, 45], pivot=[-3.7, 6, -3.45])                              # палица (ромб)

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

def brocade(r, seed=0):
    """Золотая парча с узором из крестиков."""
    fill(r, M)
    x0, y0, w, h = r
    for yy in range(h):
        for xx in range(w):
            gx, gy = (xx + seed) % 5, (yy + seed * 2) % 5
            if (gx == 2 and gy in (1, 2, 3)) or (gy == 2 and gx in (1, 3)):
                px(r, xx, yy, L)
            elif gx == 0 and gy == 0:
                px(r, xx, yy, D)

def small_cross(r, cx, cy, c=HL):
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        px(r, cx + dx, cy + dy, c)

def big_cross(r, cx, top, h, arm_y, arm_w, c=HL, edge=L):
    for y in range(top, top + h):
        px(r, cx, y, c); px(r, cx - 1, y, edge); px(r, cx + 1, y, edge)
    for x in range(cx - arm_w, cx + arm_w + 1):
        px(r, x, arm_y, c); px(r, x, arm_y - 1, edge); px(r, x, arm_y + 1, edge)
    px(r, cx, arm_y, c)

def galloon(r, y):
    """Полоса галуна: светлая середина, тёмные края."""
    row(r, y - 1, D); row(r, y, HL); row(r, y + 1, L)

def edge_outline(r):
    x0, y0, w, h = r
    row(r, 0, L)
    row(r, h - 1, D)

def paint(key, fn):
    size = next(c["size"] for c in CUBES if c["uv_key"] == key)
    fn(faces(key, size))

# --- воротник ---
def p_collar(f):
    for n, r in f.items():
        fill(r, L)
        if r[3] >= 2:
            row(r, 0, HL); row(r, r[3] - 1, D)
        for xx in range(1, r[2] - 1, 3):
            px(r, xx, r[3] // 2, M)
    fill(f["up"], HL)
    fill(f["down"], D)
for k in ("collar_front", "collar_back", "collar_side_l"):
    paint(k, p_collar)

# --- фелонь, верх ---
def p_phel_top(f):
    for n in ("north", "east", "west", "south"):
        brocade(f[n], seed=1)
        galloon(f[n], 2)
        galloon(f[n], 6)
    n = f["north"]
    # цепочка креста — V от плеч к кресту
    for i in range(5):
        px(n, 3 + i // 2, i, SILD)
        px(n, 6 - i // 2, i, SILD)
    row(n, n[3] - 1, HL)           # кайма переднего подола
    row(n, n[3] - 2, L)
    brocade(f["up"], seed=2)
    for xx in range(f["up"][2]): px(f["up"], xx, 0, L)
    fill(f["down"], LIN)
paint("phel_top", p_phel_top)

def p_phel_back(f):
    s = f["south"]
    brocade(s, seed=3)
    col(s, 0, L); col(s, s[2] - 1, L)
    big_cross(s, 5, 2, 9, 5, 3)
    row(s, s[3] - 2, L); row(s, s[3] - 1, HL)
    fill(f["north"], LIN)
    for n in ("east", "west", "up", "down"):
        fill(f[n], L)
paint("phel_back", p_phel_back)

def p_phel_side(f):
    for n in ("east", "west"):
        brocade(f[n], seed=4)
        col(f[n], 0, L); col(f[n], f[n][2] - 1, L)
        row(f[n], f[n][3] - 1, HL)
        small_cross(f[n], 3, 4, L)
    for n in ("north", "south", "up", "down"):
        fill(f[n], L)
    row(f["north"], f["north"][3] - 1, HL)
paint("phel_side_l", p_phel_side)

def p_cross(f):
    for r in f.values(): fill(r, SIL)
    for n in ("north",):
        col(f[n], f[n][2] - 1, SILD)
        row(f[n], f[n][3] - 1, SILD)
paint("cross_v", p_cross)
paint("cross_h", p_cross)

def p_arm(f):
    for n in ("north", "east", "west", "south"):
        brocade(f[n], seed=0)
        galloon(f[n], 2)
        galloon(f[n], 6)
        row(f[n], f[n][3] - 1, HL)
    brocade(f["up"]); fill(f["down"], LIN)
paint("arm", p_arm)

def p_cuff(f):
    for n in ("north", "east", "west", "south"):
        r = f[n]
        fill(r, L); row(r, 0, HL); row(r, r[3] - 1, D)
        px(r, r[2] // 2 - 1, 1, M); px(r, r[2] // 2, 1, HL); px(r, r[2] // 2 + 1, 1, M) if r[2] > 3 else None
    fill(f["up"], L); fill(f["down"], D)
paint("cuff", p_cuff)

# --- подризник, епитрахиль, пояс, палица ---
def p_torso(f):
    for r in f.values():
        fill(r, WH)
        for xx in range(0, r[2], 3): col(r, xx, WHD)
paint("torso", p_torso)

def p_epi(f):
    for r in f.values():
        fill(r, M); col(r, 0, L); col(r, r[2] - 1, L)
        for yy in range(1, r[3], 4): px(r, r[2] // 2, yy, HL)
paint("epi_top", p_epi)

def p_belt(f):
    for r in f.values():
        fill(r, L); row(r, 0, HL)
    n = f["north"]
    px(n, n[2] // 2 - 1, 0, SIL); px(n, n[2] // 2, 0, SIL)
paint("belt", p_belt)

def p_skirt(f):
    for n, r in f.items():
        fill(r, WH)
        for xx in range(1, r[2], 2): col(r, xx, WHD)     # складки
        if n in ("north", "east", "west", "south"):
            row(r, r[3] - 3, D); row(r, r[3] - 2, M); row(r, r[3] - 1, L)   # золотая кайма
            for xx in range(0, r[2], 2): px(r, xx, r[3] - 2, HL)
    fill(f["down"], WHS)
paint("skirt", p_skirt)

def p_epi_low(f):
    for n, r in f.items():
        fill(r, M)
        col(r, r[2] - 1, L)
    n = f["north"]
    for yy in (1, 5, 9):
        px(n, 0, yy, BTN)            # пуговицы по шву
    for yy in (3, 7):
        px(n, 1, yy, HL)
    row(n, n[3] - 1, HL)             # бахрома
    row(n, n[3] - 2, L)
paint("epi_low", p_epi_low)

def p_palitsa(f):
    for r in f.values():
        fill(r, M)
    n = f["north"]
    for i in range(3):
        px(n, i, 0, HL); px(n, 0, i, HL); px(n, i, 2, D); px(n, 2, i, D)
    px(n, 1, 1, HL)
paint("palitsa", p_palitsa)

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
        sx = x if view == "front" else -x
        return (W / 2 + sx * S, (33 - y) * S)
    items = []
    for o, s, fimg, bimg in BODY:
        x0, y0, z0 = o; x1, y1, z1 = x0 + s[0], y0 + s[1], z0 + s[2]
        if view == "front": items.append((z0, (x0, y1, z0), (x1, y1, z0), (x0, y0, z0), fimg))
        else: items.append((-z1, (x1, y1, z1), (x0, y1, z1), (x1, y0, z1), bimg))
    for c in CUBES:
        for n, tl, tr, bl, im in face_quads(c, tex):
            if (view == "front") != (n == "north"): continue
            depth = (tl[2] + tr[2] + bl[2]) / 3
            items.append((depth if view == "front" else -depth, tl, tr, bl, im))
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

front, back = render("front"), render("back")
cw, ch = front.width * 2 + 60, front.height + 70
can = Image.new("RGBA", (cw, ch))
dr = ImageDraw.Draw(can)
for yy in range(ch):
    t = yy / ch
    dr.line([(0, yy), (cw, yy)], fill=(int(140 + 60 * t), int(190 + 30 * t), int(235 - 10 * t), 255))
dr.rectangle([0, ch - 56, cw, ch], fill=(110, 160, 70, 255))
can.alpha_composite(front, (20, 10)); can.alpha_composite(back, (40 + front.width, 10))
fnt = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 26)
for txt, x0 in (("Спереди", 20), ("Сзади", 40 + front.width)):
    tw = dr.textlength(txt, font=fnt)
    dr.text((x0 + front.width / 2 - tw / 2, ch - 46), txt, font=fnt, fill="white", stroke_width=2, stroke_fill=(40, 40, 40))
can.convert("RGB").save(os.path.join(OUT, "preview.png"))
tex.resize((512, 512), Image.NEAREST).save(os.path.join(OUT, "texture_x4.png"))
print("UV:", UV)
