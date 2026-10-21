#!/usr/bin/env python3
"""Единое описание раскладки данжа «Камень Расколотого Рога».

Из одного списка элементов генерирует:
  * src/main/java/noppes/npcs/dungeon/HerdstoneDungeon.java — строитель (1 единица = 1 блок);
  * SVG-карты локаций в herdstone_dungeon.html между <!-- MAP:Lx --> и <!-- /MAP:Lx -->.

Запуск из корня репозитория:  python3 tools/herdstone/herdstone_layout.py
Проверка без записи:          python3 tools/herdstone/herdstone_layout.py --check
"""
from __future__ import annotations

import argparse
import math
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA_OUT = ROOT / "src/main/java/noppes/npcs/dungeon/HerdstoneDungeon.java"
HTML = ROOT / "herdstone_dungeon.html"

SCALE = 1.8
FRAME_W, FRAME_D = 350, 247

LEVELS = {
    1: dict(name="Гнилая просека", y=-0, offset=(150, 18), view=(800, 580),
            theme=("minecraft:grass_block", "minecraft:dark_oak_log", "minecraft:dark_oak_leaves")),
    2: dict(name="Осквернённая крипта", y=-24, offset=(0, 0), view=(800, 520),
            theme=("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks")),
    3: dict(name="Корневые глубины", y=-48, offset=(0, 0), view=(800, 520),
            theme=("minecraft:mycelium", "minecraft:dark_oak_wood", "minecraft:dirt")),
    4: dict(name="Провал Камня Стада", y=-72, offset=(0, 0), view=(800, 540),
            theme=("minecraft:coarse_dirt", "minecraft:cobblestone", "minecraft:dirt")),
}


@dataclass
class P:
    lv: int
    id: str
    kind: str                 # Kind в DungeonDefinition
    shape: str                # rect | ellipse | circle | poly | path | mark | tower | shaft
    geom: tuple
    marker: str = ""
    air: bool = False
    walls: int | None = None
    floor: str | None = None
    no_walls: bool = False
    label: str | None = None
    sub: tuple = ()
    at: tuple | None = None   # позиция подписи в блоках
    cls: str | None = None    # CSS-класс в SVG вместо вычисленного
    tag: str = ""             # текст в значке (Б1, Ш, S2 ...)
    to_lv: int = 0
    svg: bool = True
    java: bool = True


def rect(lv, id, kind, x, z, w, d, marker="", **kw):
    return P(lv, id, kind, "rect", (x, z, w, d), marker, **kw)


def ellipse(lv, id, kind, x, z, w, d, marker="", **kw):
    return P(lv, id, kind, "ellipse", (x, z, w, d), marker, **kw)


def circle(lv, id, kind, cx, cz, r, marker="", **kw):
    return P(lv, id, kind, "circle", (cx, cz, r), marker, **kw)


def poly(lv, id, kind, pts, marker="", **kw):
    return P(lv, id, kind, "poly", tuple(pts), marker, **kw)


def path(lv, id, kind, width, pts, **kw):
    return P(lv, id, kind, "path", (width, tuple(pts)), **kw)


def mark(lv, id, x, z, marker, **kw):
    return P(lv, id, "MARK", "mark", (x, z), marker, **kw)


def tower(lv, id, x, z):
    return P(lv, id, "TOWER", "tower", (x, z), "npc")


def shaft(lv, to_lv, id, x, z, **kw):
    return P(lv, id, "SHAFT", "shaft", (x, z), "gate", to_lv=to_lv, **kw)


def marks(lv, prefix, marker, pts, tags=None):
    out = []
    for i, (x, z) in enumerate(pts, 1):
        out.append(mark(lv, f"{prefix}_{i}", x, z, marker, tag=(tags[i - 1] if tags else "")))
    return out


# ---------------------------------------------------------------- раскладка
A2_TRAIL = [
    102, 197, 100, 194, 98, 191, 96, 188, 94, 185, 92, 182, 90, 179, 88, 175, 87, 172,
    85, 169, 84, 166, 83, 163, 82, 160, 81, 157, 80, 154, 80, 151, 80, 148, 79, 145,
    79, 142, 80, 140, 80, 137, 80, 134, 81, 132, 82, 129, 83, 127, 85, 124, 86, 123,
    82, 116, 79, 109,
]
S1_TRAIL = [
    161, 164, 160, 167, 158, 169, 157, 172, 156, 174, 154, 176, 153, 178, 151, 180,
    150, 182, 149, 184, 147, 186, 146, 187, 145, 189, 144, 191, 142, 192, 141, 194,
    140, 196, 139, 197, 138, 199, 136, 200, 135, 202, 134, 203, 133, 205, 132, 206,
    129, 208,
]
CYAN = "minecraft:cyan_terracotta"

L1 = [
    rect(1, "border", "BORDER", 0, 0, FRAME_W, FRAME_D, cls="border"),
    rect(1, "camp_order", "CAMP_ORDER", -72, 267, 117, 40, "npc", air=True, cls="ord"),
    rect(1, "camp_destr", "CAMP_DESTR", 233, 267, 117, 40, "npc", air=True, cls="dst"),
    mark(1, "crossroads", 139, 287, "gate"),
    path(1, "path_camps", "PATH", 5, [44, 287, 139, 287, 233, 287], air=True),
    path(1, "path_gate", "PATH", 6, [139, 287, 103, 251], air=True),
    rect(1, "A1", "ROOM", 73, 197, 58, 44, air=True,
         label="A1 Преддверие", sub=("отряд: 2 рубаки + щитоносец",), at=(76, 206)),
    poly(1, "A2", "ROOM", [81, 197, 57, 157, 62, 117, 100, 104, 153, 117, 153, 140, 108, 133, 91, 157, 106, 197],
         air=True, floor="minecraft:podzol", cls="rm",
         label="A2 Тропа вышек", sub=("4 вышки лучников", "3 отряда"), at=(4, 170)),
    path(1, "path_A2", "PATH", 8, A2_TRAIL, air=True),
    ellipse(1, "A3", "ROOM", 7, 21, 98, 62, air=True,
            label="A3 Лагерь унгоров", sub=("элита: бестигор-палач",), at=(22, 17)),
    path(1, "path_A3", "PATH", 8, [79, 109, 60, 76], air=True),
    path(1, "path_A3_A5", "PATH", 8, [100, 55, 125, 70, 145, 88, 160, 100], air=True,
         label="Тропа черепов", sub=("отряд + засада",), at=(112, 56)),
    rect(1, "A5", "ROOM", 157, 86, 116, 83, air=True, walls=5,
         label="A5 Двор обители", sub=("2 элиты: щитоносец-вожак,", "бестигор-палач · отряд"), at=(159, 82)),
    rect(1, "A5_chapel", "ROOM", 167, 96, 34, 23, air=True, walls=5, sub=("часовня",), at=(171, 109)),
    rect(1, "A4", "ARENA", 258, 18, 44, 38, "boss", air=True, walls=5, tag="Б1",
         label="A4 Загон · арена Б1", sub=("44×38 бл. · 4 кола",), at=(200, 9)),
    path(1, "path_A5_A4", "PATH", 6, [262, 90, 270, 70, 278, 55], air=True,
         label="Загонная тропа", sub=("отряд",), at=(281, 76)),
    circle(1, "sinkhole", "SAFE", 230, 27, 26, air=True, java=False, cls="rm sink",
           label="Провал к Б4", sub=("открытый колодец · ночное небо",), at=(168, 62)),
    rect(1, "entrance", "ROOM", 90, 242, 24, 9, "gate", air=True, walls=5, cls="ink", tag="ВХОД"),
    rect(1, "exit_gate", "ROOM", 168, 242, 27, 9, "gate", air=True, walls=5, cls="ink", tag="ВЫХОД"),
    circle(1, "gallows", "SAFE", 181, 223, 10, "gate", air=True,
           label="Подъём Висельника", sub=("↑ из арены Б4",), at=(160, 206)),
    path(1, "path_exit", "PATH", 4, [181, 251, 150, 284], air=True, floor="minecraft:coarse_dirt", cls="out"),
    tower(1, "ungor_1", 66, 152),
    tower(1, "ungor_2", 84, 113),
    tower(1, "ungor_3", 130, 119),
    tower(1, "ungor_4", 96, 176),
    path(1, "S1", "PATH", 4, S1_TRAIL, air=True, floor=CYAN),
    mark(1, "S1_mark", 150, 184, "sc", tag="S1"),
    *marks(1, "stake", "stake", [(264, 24), (296, 24), (264, 50), (296, 50)]),
    *marks(1, "pack", "pack", [(95, 215), (85, 165), (95, 135), (125, 122), (65, 40), (30, 65),
                               (125, 72), (230, 110), (268, 77)]),
    *marks(1, "elite", "elite", [(40, 50), (190, 130), (245, 150)], tags=["П", "Щ", "П"]),
    shaft(1, 2, "A4_down", 292, 26, sub=("спуск в крипту",), at=(266, 62)),
]

L2 = [
    rect(2, "B1", "ROOM", 276, 10, 48, 34,
         label="B1 Спуск", sub=("засада у лестницы",), at=(232, 6)),
    path(2, "c_B1_B2", "CORRIDOR", 8, [300, 43, 300, 84]),
    poly(2, "B2", "WATER", [170, 80, 330, 80, 338, 136, 318, 170, 190, 170, 172, 136],
         label="B2 Затопленные катакомбы", sub=("по колено в воде · 2 отряда", "элита: бра-шаман"), at=(196, 92)),
    path(2, "c_B2_B3", "CORRIDOR", 8, [173, 120, 139, 120]),
    rect(2, "B3", "ROOM", 24, 70, 116, 90,
         label="B3 Оссуарий", sub=("2 шамана + тотем Стада", "2 отряда"), at=(27, 66)),
    path(2, "c_B3_B4", "CORRIDOR", 8, [80, 159, 80, 184, 250, 184],
         label="Галерея костей", sub=("2 центигора — уходи в ниши",), at=(96, 198)),
    rect(2, "B4", "ROOM", 250, 176, 56, 44, "npc",
         label="B4 Притвор", sub=("Шкрик · отряд",), at=(252, 172)),
    path(2, "c_B4_B5", "CORRIDOR", 8, [252, 212, 216, 222]),
    circle(2, "B5", "ARENA", 200, 222, 20, "boss", tag="Б2",
           label="B5 Святилище · арена Б2", sub=("r 20",), at=(150, 198)),
    *marks(2, "totem", "totem", [(186, 210), (214, 210), (186, 234), (214, 234)]),
    path(2, "S2", "CORRIDOR", 4, [305, 182, 342, 178, 344, 120, 342, 60, 322, 40], floor=CYAN),
    mark(2, "S2_mark", 344, 120, "sc", tag="S2"),
    *marks(2, "pack", "pack", [(285, 20), (210, 100), (300, 110), (45, 140), (115, 90), (278, 205)]),
    *marks(2, "elite", "elite", [(250, 150), (60, 100), (100, 135), (120, 184), (200, 184), (80, 118)],
           tags=["Ш", "Ш", "Ш", "Ц", "Ц", "Т"]),
    shaft(2, 3, "B5_down", 190, 230, sub=("корневая шахта → C1",), at=(150, 247)),
]

L3 = [
    rect(3, "C1", "ROOM", 170, 206, 50, 38,
         label="C1 Корневой спуск", sub=("отряд",), at=(174, 202)),
    path(3, "c_C1_C2", "CORRIDOR", 8, [171, 224, 129, 224]),
    rect(3, "C2", "ROOM", 20, 150, 110, 90,
         label="C2 Зал барабанов", sub=("Барабанщик + 3 отряда",), at=(23, 146)),
    path(3, "c_C2_C3", "CORRIDOR", 8, [75, 151, 75, 128]),
    poly(3, "C3", "WARP", [20, 20, 200, 20, 215, 80, 190, 130, 40, 130, 20, 90],
         label="C3 Варп-топь", sub=("туман · святые травы", "2 элиты-отродья · отряд"), at=(24, 15)),
    path(3, "c_C3_C3a", "CORRIDOR", 6, [208, 75, 226, 75]),
    rect(3, "C3a", "ROOM", 225, 58, 40, 32,
         label="C3а Нора", sub=("минотавр",), at=(226, 54)),
    path(3, "c_C3a_C5", "CORRIDOR", 8, [255, 89, 290, 140]),
    circle(3, "C5", "ARENA", 300, 152, 21, "boss", tag="Б3",
           label="C5 Яма · арена Б3", sub=("r 21",), at=(296, 126)),
    rect(3, "C4", "SECRET", 268, 8, 72, 40, "boss", tag="★",
         label="C4 Топь Бормочущего", sub=("секрет · 4 менгира",), at=(270, 58)),
    path(3, "c_C3_C4", "CORRIDOR", 3, [200, 36, 200, 56, 290, 56, 290, 40], cls="pa dot"),
    path(3, "S3", "CORRIDOR", 4, [245, 89, 248, 150, 240, 200, 218, 215], floor=CYAN),
    mark(3, "S3_mark", 246, 150, "sc", tag="S3"),
    *marks(3, "menhir", "menhir", [(284, 18), (324, 18), (284, 38), (324, 38)]),
    *marks(3, "herb", "herb", [(50, 40), (110, 30), (180, 60), (140, 115)]),
    mark(3, "fog", 110, 70, "fog"),
    *marks(3, "pack", "pack", [(200, 215), (40, 170), (105, 220), (45, 225), (120, 100)]),
    *marks(3, "elite", "elite", [(75, 195), (70, 60), (150, 50), (245, 73)], tags=["Б", "О", "О", "М"]),
    shaft(3, 4, "C5_down", 300, 162, sub=("Глотка → D0",), at=(318, 178)),
]

L4 = [
    rect(4, "D0", "ROOM", 286, 146, 30, 30,
         label="D0", sub=("из Глотки",), at=(288, 142)),
    path(4, "c_D0_D1", "CORRIDOR", 8, [287, 170, 268, 170]),
    rect(4, "D1", "ROOM", 150, 136, 120, 82,
         label="D1 Пиршество минотавров", sub=("4 минотавра · отряд",), at=(152, 132)),
    rect(4, "D1_table", "ROOM", 180, 165, 60, 20, floor="minecraft:spruce_planks", no_walls=True,
         sub=("пиршественный стол",), at=(186, 177)),
    path(4, "c_D1_D2", "CORRIDOR", 8, [151, 160, 104, 132]),
    circle(4, "D2", "ROOM", 80, 118, 30,
           label="D2 Круг Вызова", sub=("чемпион + бестигоры",), at=(50, 158)),
    path(4, "c_D2_D3", "CORRIDOR", 8, [100, 100, 150, 70, 200, 49, 218, 41]),
    circle(4, "D3", "ARENA", 230, 27, 26, "boss", tag="Б4", air=True,
           label="D3 Роща · арена Б4", sub=("r 26 · колодец до неба",), at=(175, 73)),
    path(4, "chan_n", "PATH", 3, [230, 27, 230, 5], floor="minecraft:red_nether_bricks", cls="chan"),
    path(4, "chan_sw", "PATH", 3, [230, 27, 211, 40], floor="minecraft:red_nether_bricks", cls="chan"),
    path(4, "chan_se", "PATH", 3, [230, 27, 249, 40], floor="minecraft:red_nether_bricks", cls="chan"),
    mark(4, "stone", 230, 27, "boss", cls="stone", java=False),
    *marks(4, "heart", "heart", [(230, 5), (211, 40), (249, 40)]),
    *marks(4, "crystal", "crystal", [(220, 17), (240, 17), (220, 37), (240, 37)]),
    rect(4, "D4", "ROOM", 164, 3, 40, 28, "item",
         label="D4 Добыча", sub=("после Б4",), at=(120, 11)),
    path(4, "c_D3_D4", "CORRIDOR", 6, [208, 23, 198, 17]),
    mark(4, "exit_up", 198, 17, "gate", sub=("выход",), at=(168, 49)),
    path(4, "S4", "CORRIDOR", 4, [95, 140, 120, 230, 300, 230, 300, 174], floor=CYAN),
    mark(4, "S4_mark", 210, 230, "sc", tag="S4"),
    *marks(4, "pack", "pack", [(292, 150), (210, 200), (60, 100)]),
    *marks(4, "elite", "elite", [(175, 150), (250, 150), (175, 205), (250, 205), (80, 118)],
           tags=["М", "М", "М", "М", "Ч"]),
]

PIECES = L1 + L2 + L3 + L4

ELITE_CAP = {
    1: "Элиты: П — бестигор-палач, Щ — гор-щитоносец (вожак отряда)",
    2: "Элиты: Ш — бра-шаман, Ц — центигор, Т — тотем Стада",
    3: "Элиты: Б — Барабанщик, О — отродье Хаоса, М — минотавр",
    4: "Элиты: М — минотавр, Ч — Чемпион Круга",
}
CAPTIONS = {
    1: "Сетка 10 / 50 блоков · рамка данжа 350×247 · арена Б1 44×38 · переходы A1 → A2 → A3 → Тропа черепов → A5 → A4",
    2: "Порядок: B1 → B2 → B3 → Галерея костей → B4 → арена B5 (r 20) · S2: рычаг в Притворе B4 → B1",
    3: "Порядок: C1 → C2 → C3 → C3а → арена C5 (r 21) · S3: лестница из Норы к C1 · пунктир — потайной лаз в C4 в обход колодца Б4",
    4: "Порядок: D0 → D1 → D2 → арена D3 (r 26, открытый колодец западнее A4) · красные линии — жёлобы · S4: лестница из Круга Вызова к D0",
}


# ---------------------------------------------------------------- проверки
def bbox(p: P):
    g = p.geom
    if p.shape in ("rect", "ellipse"):
        return g[0], g[1], g[0] + g[2], g[1] + g[3]
    if p.shape == "circle":
        return g[0] - g[2], g[1] - g[2], g[0] + g[2] + 1, g[1] + g[2] + 1
    if p.shape == "poly":
        xs, zs = g[0::2], g[1::2]
        return min(xs), min(zs), max(xs), max(zs)
    if p.shape == "path":
        w, pts = g
        pad = (w + 1) // 2
        xs, zs = pts[0::2], pts[1::2]
        return min(xs) - pad, min(zs) - pad, max(xs) + pad, max(zs) + pad
    if p.shape == "shaft":
        return g[0], g[1], g[0] + 3, g[1] + 3
    return g[0], g[1], g[0] + 1, g[1] + 1


def contains(p: P, x: float, z: float) -> bool:
    g = p.geom
    if p.shape == "rect":
        return g[0] <= x < g[0] + g[2] and g[1] <= z < g[1] + g[3]
    if p.shape in ("ellipse",):
        rx, rz = g[2] / 2, g[3] / 2
        cx, cz = g[0] + rx, g[1] + rz
        return ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2 <= 1.0
    if p.shape == "circle":
        return (x - g[0]) ** 2 + (z - g[1]) ** 2 <= (g[2] + 0.5) ** 2
    if p.shape == "poly":
        pts = list(zip(g[0::2], g[1::2]))
        inside = False
        j = len(pts) - 1
        for i in range(len(pts)):
            xi, zi = pts[i]
            xj, zj = pts[j]
            if (zi > z) != (zj > z) and x < (xj - xi) * (z - zi) / (zj - zi) + xi:
                inside = not inside
            j = i
        return inside
    return False


AREA_SHAPES = ("rect", "ellipse", "circle", "poly")


def check() -> list[str]:
    errors = []
    ids = set()
    for p in PIECES:
        key = (p.lv, p.id)
        if key in ids:
            errors.append(f"дубликат id {p.id} на L{p.lv}")
        ids.add(key)
        if p.kind in ("CAMP_ORDER", "CAMP_DESTR") or p.id.startswith(("path_camps", "path_gate", "path_exit")):
            continue
        if p.id in ("crossroads", "entrance", "exit_gate"):
            continue
        x1, z1, x2, z2 = bbox(p)
        if x1 < 0 or z1 < 0 or x2 > FRAME_W or z2 > FRAME_D:
            errors.append(f"L{p.lv} {p.id}: выходит за рамку {FRAME_W}×{FRAME_D}: {x1},{z1}–{x2},{z2}")
    areas = {lv: [p for p in PIECES if p.lv == lv and p.shape in AREA_SHAPES and p.kind != "BORDER"]
             for lv in LEVELS}
    for p in PIECES:
        if p.shape == "path" and not p.id.startswith(("chan_", "path_camps", "path_gate", "path_exit")):
            w, pts = p.geom
            for (x, z) in (pts[0:2], pts[-2:]):
                if not any(contains(a, x, z) for a in areas[p.lv]) and not on_path(p.lv, x, z, skip=p.id):
                    errors.append(f"L{p.lv} {p.id}: конец ({x}, {z}) не попадает ни в одну зону")
        if p.shape == "shaft":
            x, z = p.geom
            for lv in (p.lv, p.to_lv):
                if not any(contains(a, x + 1, z + 1) for a in areas[lv]):
                    errors.append(f"шахта {p.id}: ({x}, {z}) не внутри зоны на L{lv}")
        if p.shape == "mark" and p.marker in ("pack", "elite", "stake", "totem", "heart", "crystal", "herb", "menhir"):
            x, z = p.geom
            if not any(contains(a, x, z) for a in areas[p.lv]) and not on_path(p.lv, x, z):
                errors.append(f"L{p.lv} {p.id}: ({x}, {z}) вне зон и проходов")
    for p in PIECES:
        if p.lv != 4 or p.id != "D3" or p.shape != "circle":
            continue
        blocked = sky_well_hits(p.geom)
        if blocked:
            errors.append("D3 колодец неба пересекает: " + ", ".join(blocked))
    return errors


def ellipse_block(cx, cz, r, x, z) -> bool:
    """Как DungeonGeometry.insideEllipse для addCircle: радиус r+0.5, центр в (cx, cz)."""
    rx = r + 0.5
    dx = (x - cx) / rx
    dz = (z - cz) / rx
    return dx * dx + dz * dz <= 1.0


def path_block(p: P, x: int, z: int) -> bool:
    w, pts = p.geom
    rad = w * 0.5
    px, pz = x + 0.5, z + 0.5
    for i in range(0, len(pts) - 2, 2):
        ax, az, bx, bz = pts[i], pts[i + 1], pts[i + 2], pts[i + 3]
        dx, dz = bx - ax, bz - az
        ll = dx * dx + dz * dz
        t = 0.0 if ll == 0 else max(0.0, min(1.0, ((px - ax) * dx + (pz - az) * dz) / ll))
        if math.hypot(px - (ax + t * dx), pz - (az + t * dz)) <= rad:
            return True
    return False


def upper_block(p: P, x: int, z: int) -> bool:
    if p.shape == "path":
        return path_block(p, x, z)
    if p.shape == "shaft":
        sx, sz = p.geom
        return sx <= x < sx + 3 and sz <= z < sz + 3
    if p.shape in ("mark", "tower") or p.kind == "BORDER" or p.id == "sinkhole":
        return False
    if p.shape == "poly":
        return contains(p, x + 0.5, z + 0.5)
    return contains(p, x, z)


def sky_well_hits(geom) -> list[str]:
    cx, cz, r = geom
    rx = r + 0.5
    x0, x1 = math.floor(cx - rx), math.ceil(cx + rx)
    z0, z1 = math.floor(cz - rx), math.ceil(cz + rx)
    found = []
    seen = set()
    upper = [p for p in PIECES if p.lv in (1, 2, 3)]
    for z in range(z0, z1 + 1):
        for x in range(x0, x1 + 1):
            if not ellipse_block(cx, cz, r, x, z):
                continue
            if x <= 0 or z <= 0 or x >= FRAME_W - 1 or z >= FRAME_D - 1:
                key = "рамка"
                if key not in seen:
                    seen.add(key)
                    found.append(f"{key} ({x}, {z})")
            for p in upper:
                if not upper_block(p, x, z):
                    continue
                key = f"L{p.lv}:{p.id}"
                if key not in seen:
                    seen.add(key)
                    found.append(f"{key} ({x}, {z})")
    return found


def on_path(lv, x, z, skip="") -> bool:
    for p in PIECES:
        if p.lv != lv or p.shape != "path" or p.id == skip:
            continue
        w, pts = p.geom
        for i in range(0, len(pts) - 2, 2):
            ax, az, bx, bz = pts[i], pts[i + 1], pts[i + 2], pts[i + 3]
            dx, dz = bx - ax, bz - az
            ll = dx * dx + dz * dz
            t = 0 if ll == 0 else max(0.0, min(1.0, ((x - ax) * dx + (z - az) * dz) / ll))
            if math.hypot(x - ax - t * dx, z - az - t * dz) <= w / 2 + 1:
                return True
    return False


# ---------------------------------------------------------------- Java
def jnums(nums, indent=16, per_line=16):
    nums = [str(n) for n in nums]
    lines = []
    for i in range(0, len(nums), per_line):
        lines.append(", ".join(nums[i:i + per_line]))
    if len(lines) == 1:
        return lines[0]
    pad = " " * indent
    return ("\n" + pad).join(l + ("," if i < len(lines) - 1 else "") for i, l in enumerate(lines))


def java_piece(p: P) -> str:
    g = p.geom
    k = f"Kind.{p.kind}"
    if p.shape == "rect":
        s = f'd.add({p.lv}, "{p.id}", {k}, {g[0]}, {g[1]}, {g[2]}, {g[3]}, "{p.marker}")'
    elif p.shape == "ellipse":
        s = f'd.addEllipse({p.lv}, "{p.id}", {k}, {g[0]}, {g[1]}, {g[2]}, {g[3]}, "{p.marker}")'
    elif p.shape == "circle":
        s = f'd.addCircle({p.lv}, "{p.id}", {k}, {g[0]}, {g[1]}, {g[2]}, "{p.marker}")'
    elif p.shape == "poly":
        s = f'd.addPoly({p.lv}, "{p.id}", {k}, "{p.marker}",\n                {jnums(g)})'
    elif p.shape == "path":
        w, pts = g
        if len(pts) > 8:
            s = f'd.addPath({p.lv}, "{p.id}", {k}, {w},\n                {jnums(pts)})'
        else:
            s = f'd.addPath({p.lv}, "{p.id}", {k}, {w}, {jnums(pts)})'
    elif p.shape == "mark":
        s = f'd.add({p.lv}, "{p.id}", Kind.MARK, {g[0]}, {g[1]}, 1, 1, "{p.marker}")'
    elif p.shape == "tower":
        s = f'd.add({p.lv}, "{p.id}", Kind.TOWER, {g[0]}, {g[1]}, 3, 3, "{p.marker}")'
    elif p.shape == "shaft":
        s = f'd.shaft({p.lv}, {p.to_lv}, "{p.id}", {g[0]}, {g[1]})'
    else:
        raise ValueError(p.shape)
    if p.air:
        s += ".air()"
    if p.walls is not None:
        s += f".walls({p.walls})"
    if p.floor:
        s += f'\n                .floor("{p.floor}")' if len(s) > 70 else f'.floor("{p.floor}")'
    if p.no_walls:
        s += ".noWalls()"
    return "        " + s + ";"


def java_source() -> str:
    out = [
        "package noppes.npcs.dungeon;",
        "",
        "import noppes.npcs.dungeon.DungeonDefinition.Kind;",
        "",
        "/**",
        " * Сгенерировано tools/herdstone/herdstone_layout.py — правь раскладку там, а не здесь.",
        " * 1 единица = 1 блок; на картах herdstone_dungeon.html 1 блок = 1.8 единицы SVG.",
        " */",
        "public final class HerdstoneDungeon {",
        "    private HerdstoneDungeon() {",
        "    }",
        "",
        "    public static DungeonDefinition create() {",
        '        final DungeonDefinition d = new DungeonDefinition("herdstone", "Камень Расколотого Рога")',
        '                .alias("herdstone_dungeon", "hs", "herdherdstone");',
    ]
    for lv, cfg in LEVELS.items():
        out.append(f'        d.level({lv}, "{cfg["name"]}", {cfg["y"]});')
    for lv, cfg in LEVELS.items():
        f, w, c = cfg["theme"]
        out.append(f'        d.theme({lv}, "{f}", "{w}", "{c}");')
    out.append("")
    for lv in LEVELS:
        out.append(f"        level{lv}(d);")
    out += ["        return d;", "    }"]
    for lv in LEVELS:
        out += ["", f"    private static void level{lv}(final DungeonDefinition d) {{"]
        for p in PIECES:
            if p.lv == lv and p.java:
                out.append(java_piece(p))
        out.append("    }")
    out.append("}")
    return "\n".join(out) + "\n"


# ---------------------------------------------------------------- SVG
def f1(v: float) -> str:
    s = f"{v:.1f}"
    return s[:-2] if s.endswith(".0") else s


class Map:
    def __init__(self, lv):
        self.lv = lv
        self.ox, self.oz = LEVELS[lv]["offset"]

    def x(self, bx):
        return f1(self.ox + bx * SCALE)

    def z(self, bz):
        return f1(self.oz + bz * SCALE)

    def pts(self, flat):
        return " ".join(f"{self.x(flat[i])},{self.z(flat[i + 1])}" for i in range(0, len(flat), 2))


def grid(m: Map) -> str:
    W, D = FRAME_W * SCALE, FRAME_D * SCALE
    parts = [f'<g class="blkgrid" pointer-events="none" transform="translate({m.ox},{m.oz})">']
    for b in range(0, FRAME_W + 1, 10):
        x = b * SCALE
        cls = "grid-major" if b % 50 == 0 else "grid-minor"
        parts.append(f'<line class="{cls}" x1="{x:.1f}" y1="0" x2="{x:.1f}" y2="{D:.1f}"/>')
        if b % 50 == 0:
            parts.append(f'<text class="ax" x="{x:.1f}" y="-7" text-anchor="middle">{b}</text>')
    for b in range(0, FRAME_D + 1, 10):
        z = b * SCALE
        cls = "grid-major" if b % 50 == 0 else "grid-minor"
        parts.append(f'<line class="{cls}" x1="0" y1="{z:.1f}" x2="{W:.1f}" y2="{z:.1f}"/>')
        if b % 50 == 0:
            parts.append(f'<text class="ax" x="-6" y="{z + 3:.1f}" text-anchor="end">{b}</text>')
    parts.append(f'<text class="ax" x="{W / 2:.1f}" y="-18" text-anchor="middle">блоки · 10 / 50</text></g>')
    return "".join(parts)


ROOM_CLS = {"ROOM": "rm", "ARENA": "rm boss", "WATER": "rm water", "WARP": "rm warp",
            "SECRET": "rm sec", "SAFE": "rm safe", "CAMP_ORDER": "ord", "CAMP_DESTR": "dst", "BORDER": "border"}


def area_svg(m: Map, p: P) -> str:
    cls = p.cls or ROOM_CLS.get(p.kind, "rm")
    g = p.geom
    if p.shape == "rect":
        return (f'<rect class="{cls}" x="{m.x(g[0])}" y="{m.z(g[1])}" '
                f'width="{f1(g[2] * SCALE)}" height="{f1(g[3] * SCALE)}"/>')
    if p.shape == "ellipse":
        return (f'<ellipse class="{cls}" cx="{m.x(g[0] + g[2] / 2)}" cy="{m.z(g[1] + g[3] / 2)}" '
                f'rx="{f1(g[2] * SCALE / 2)}" ry="{f1(g[3] * SCALE / 2)}"/>')
    if p.shape == "circle":
        return f'<circle class="{cls}" cx="{m.x(g[0] + .5)}" cy="{m.z(g[1] + .5)}" r="{f1((g[2] + .5) * SCALE)}"/>'
    if p.shape == "poly":
        return f'<polygon class="{cls}" points="{m.pts(g)}"/>'
    raise ValueError(p.shape)


def path_svg(m: Map, p: P) -> str:
    w, pts = p.geom
    if p.id.startswith("S") and p.id[1:].isdigit():
        return f'<polyline class="sc" points="{m.pts(pts)}"/>'
    cls = p.cls or "pa"
    if cls in ("pa",):
        return f'<polyline class="pa" style="stroke-width:{f1(w * SCALE)}" points="{m.pts(pts)}"/>'
    return f'<polyline class="{cls}" points="{m.pts(pts)}"/>'


def center(p: P):
    g = p.geom
    if p.shape in ("rect", "ellipse"):
        return g[0] + g[2] / 2, g[1] + g[3] / 2
    if p.shape == "circle":
        return g[0] + .5, g[1] + .5
    return g[0] + .5, g[1] + .5


def diamond(cx, cy, r, cls, extra=""):
    return (f'<polygon class="{cls}" points="{f1(cx)},{f1(cy - r)} {f1(cx + r)},{f1(cy)} '
            f'{f1(cx)},{f1(cy + r)} {f1(cx - r)},{f1(cy)}"{extra}/>')


def text(cls, x, y, s, anchor=None):
    a = f' text-anchor="{anchor}"' if anchor else ""
    return f'<text class="{cls}" x="{f1(x)}" y="{f1(y)}"{a}>{s}</text>'


def symbol_svg(m: Map, p: P) -> list[str]:
    out = []
    if p.shape == "tower":
        cx, cz = p.geom[0] + 1.5, p.geom[1] + 1.5
        return [f'<circle class="tw" cx="{m.x(cx)}" cy="{m.z(cz)}" r="7"/>']
    if p.shape == "shaft":
        cx, cy = float(m.x(p.geom[0] + 1.5)), float(m.z(p.geom[1] + 1.5))
        return [f'<circle class="ink" cx="{f1(cx)}" cy="{f1(cy)}" r="11"/>', text("w", cx, cy + 4, "↓", "middle")]
    if p.shape != "mark":
        if p.marker == "boss" and p.tag:
            cx, cz = center(p)
            x, y = float(m.x(cx)), float(m.z(cz))
            op = ' opacity=".7"' if p.kind == "SECRET" else ""
            out.append(diamond(x, y, 13, "bm", op))
            out.append(text("w", x, y + 4, p.tag, "middle"))
        elif p.cls == "ink" and p.tag:
            cx, cz = center(p)
            out.append(text("w", float(m.x(cx)), float(m.z(cz)) + 4, p.tag, "middle"))
        elif p.marker == "npc" and p.kind == "ROOM":
            cx, cz = center(p)
            out.append(f'<circle class="ink" cx="{m.x(cx + 10)}" cy="{m.z(cz + 6)}" r="6"/>')
        elif p.marker == "item":
            cx, cz = center(p)
            out.append(f'<rect class="itm" x="{f1(float(m.x(cx)) - 6)}" y="{f1(float(m.z(cz)) - 6)}" width="12" height="12"/>')
        return out
    x, y = float(m.x(p.geom[0] + .5)), float(m.z(p.geom[1] + .5))
    mk = p.marker
    if p.cls == "stone":
        out.append(diamond(x, y, 9, "ink"))
    elif mk == "pack":
        out += [f'<circle class="herd" cx="{f1(x - 4)}" cy="{f1(y + 2)}" r="3"/>',
                f'<circle class="herd" cx="{f1(x + 4)}" cy="{f1(y + 2)}" r="3"/>',
                f'<circle class="herd" cx="{f1(x)}" cy="{f1(y - 4)}" r="3"/>']
    elif mk == "elite":
        out += [f'<circle class="el" cx="{f1(x)}" cy="{f1(y)}" r="8"/>', text("w", x, y + 4, p.tag, "middle")]
    elif mk == "stake":
        out.append(f'<circle class="stake" cx="{f1(x)}" cy="{f1(y)}" r="5"/>')
    elif mk == "totem":
        out.append(f'<rect class="tot" x="{f1(x - 6)}" y="{f1(y - 6)}" width="12" height="12"/>')
    elif mk == "heart":
        out.append(f'<circle class="hrt" cx="{f1(x)}" cy="{f1(y)}" r="7"/>')
    elif mk == "crystal":
        out.append(diamond(x, y, 7, "cr"))
    elif mk == "herb":
        out.append(f'<circle class="itm" cx="{f1(x)}" cy="{f1(y)}" r="5"/>')
    elif mk == "fog":
        out.append(f'<circle class="fog" cx="{f1(x)}" cy="{f1(y)}" r="12"/>')
    elif mk == "menhir":
        out.append(f'<rect class="ink" x="{f1(x - 3)}" y="{f1(y - 6)}" width="6" height="12"/>')
    elif mk == "sc":
        out += [f'<circle class="sm" cx="{f1(x)}" cy="{f1(y)}" r="10"/>', text("w", x, y + 4, p.tag, "middle")]
    elif mk == "gate" and p.id == "exit_up":
        out += [f'<circle class="ink" cx="{f1(x)}" cy="{f1(y)}" r="11"/>', text("w", x, y + 4, "↑", "middle")]
    elif mk == "gate":
        out.append(f'<circle class="ink" cx="{f1(x)}" cy="{f1(y)}" r="6"/>')
    return out


def label_svg(m: Map, p: P) -> list[str]:
    if not p.label and not p.sub:
        return []
    if p.at:
        bx, bz = p.at
    else:
        x1, z1, _, _ = bbox(p)
        bx, bz = x1 + 1, z1 - 2
    x, y = float(m.x(bx)), float(m.z(bz))
    out = []
    if p.label:
        out.append(text("l", x, y, p.label))
        y += 15
    for s in p.sub:
        out.append(text("s", x, y, s))
        y += 13
    return out


def arrival_svg(m: Map) -> list[str]:
    out = []
    for p in PIECES:
        if p.shape == "shaft" and p.to_lv == m.lv:
            cx, cy = float(m.x(p.geom[0] + 1.5)), float(m.z(p.geom[1] + 1.5))
            out += [f'<circle class="ink" cx="{f1(cx)}" cy="{f1(cy)}" r="11"/>', text("w", cx, cy + 4, "↑", "middle")]
    return out


L1_BEFORE = [
    '<rect x="0" y="470" width="800" height="110" fill="var(--paper)" opacity=".55"/>',
    '<text class="s" x="14" y="490">ОПУШКА · вне данжа</text>',
]
L1_AFTER = [
    '<text class="s" x="560" y="456">терновая стена · граница данжа</text>',
    '<rect class="om" x="30" y="508" width="10" height="22"/>',
    '<text class="lo" x="48" y="522">ЛАГЕРЬ ПОРЯДКА</text><text class="s" x="48" y="538">Застава Волчьего Брода</text>'
    '<text class="s" x="48" y="552">Отто · торговец · респаун</text>',
    '<rect class="dm" x="580" y="508" width="10" height="22"/>',
    '<text class="ld" x="598" y="522">ЛАГЕРЬ РАЗРУШЕНИЯ</text><text class="s" x="598" y="538">Чёрный круг</text>'
    '<text class="s" x="598" y="552">Гурхак · торговец · респаун</text>',
    '<text class="s" x="372" y="560">Перекрёсток</text>',
    '<text class="s" x="246" y="490">Врата черепов</text>',
    '<text class="s" x="470" y="492">выход только изнутри, после Б4</text>',
    '<g transform="translate(176,48)"><path class="ink" d="M0 -14 L6 6 L0 2 L-6 6Z"/><text class="s" x="-4" y="22">С</text></g>',
]


def scale_bar(m: Map) -> list[str]:
    vw, vh = LEVELS[m.lv]["view"]
    x0, y0 = (40, 462) if m.lv == 1 else (650, vh - 30)
    return [f'<line class="sl" x1="{x0}" y1="{y0}" x2="{f1(x0 + 50 * SCALE)}" y2="{y0}"/>',
            text("s", x0, y0 - 8, "0 — 50 блоков")]


LEGEND = {
    "ord": ('<rect class="ord" x="2" y="2" width="22" height="12"/>', "лагерь Порядка"),
    "dst": ('<rect class="dst" x="2" y="2" width="22" height="12"/>', "лагерь Разрушения"),
    "border": ('<line class="border" x1="0" y1="8" x2="26" y2="8"/>', "граница данжа (терновая стена)"),
    "gate": ('<rect class="ink" x="1" y="4" width="24" height="8"/>', "вход / выход / ворота"),
    "out": ('<line class="out" x1="1" y1="8" x2="25" y2="8"/>', "путь к выходу (в одну сторону)"),
    "pa": ('<line class="pa" x1="2" y1="8" x2="24" y2="8"/>', "переход (ширина в масштабе)"),
    "dot": ('<line class="pa dot" x1="2" y1="8" x2="24" y2="8"/>', "потайной лаз"),
    "rm": ('<rect class="rm" x="2" y="2" width="22" height="12"/>', "помещение / поляна"),
    "safe": ('<rect class="rm safe" x="2" y="2" width="22" height="12"/>', "безопасная зона"),
    "sink": ('<circle class="rm sink" cx="13" cy="8" r="6"/>', "открытый провал к арене Б4"),
    "water": ('<rect class="rm water" x="2" y="2" width="22" height="12"/>', "затоплено"),
    "warp": ('<rect class="rm warp" x="2" y="2" width="22" height="12"/>', "ядовитый туман"),
    "sec": ('<rect class="rm sec" x="2" y="2" width="22" height="12"/>', "скрытое помещение"),
    "arena": ('<rect class="rm boss" x="2" y="2" width="22" height="12"/>', "арена босса"),
    "boss": ('<polygon class="bm" points="13,1 20,8 13,15 6,8"/>', "босс"),
    "pack": ('<circle class="herd" cx="9" cy="10" r="3"/><circle class="herd" cx="17" cy="10" r="3"/>'
             '<circle class="herd" cx="13" cy="4" r="3"/>', "отряд зверолюдов (3–4 моба)"),
    "elite": ('<circle class="el" cx="13" cy="8" r="7"/>', "элита (буква — тип, см. подпись)"),
    "tw": ('<circle class="tw" cx="13" cy="8" r="5"/>', "вышка унгоров-лучников"),
    "stake": ('<circle class="stake" cx="13" cy="8" r="4"/>', "заострённый кол"),
    "totem": ('<rect class="tot" x="8" y="3" width="10" height="10"/>', "тотем Мургрота"),
    "heart": ('<circle class="hrt" cx="13" cy="8" r="6"/>', "сердце жёлоба"),
    "crystal": ('<polygon class="cr" points="13,1 19,8 13,15 7,8"/>', "варп-кристалл"),
    "stone": ('<polygon class="ink" points="13,1 19,8 13,15 7,8"/>', "Камень Стада"),
    "chan": ('<line class="chan" x1="2" y1="8" x2="24" y2="8"/>', "кровавый жёлоб"),
    "herb": ('<circle class="itm" cx="13" cy="8" r="4"/>', "святая трава"),
    "fog": ('<circle class="fog" cx="13" cy="8" r="6"/>', "источник тумана (скрытый блок)"),
    "menhir": ('<rect class="ink" x="10" y="2" width="6" height="12"/>', "менгир (укрытие)"),
    "npc": ('<circle class="ink" cx="13" cy="8" r="5"/>', "NPC (Шкрик)"),
    "item": ('<rect class="itm" x="8" y="3" width="10" height="10"/>', "добыча"),
    "sc": ('<line class="sc" x1="1" y1="8" x2="25" y2="8"/>', "шорткат (открывается изнутри)"),
    "sm": ('<circle class="sm" cx="13" cy="8" r="6"/>', "рычаг шортката"),
    "shaft": ('<circle class="ink" cx="13" cy="8" r="7"/><text class="w" x="9" y="12" style="font-size:9px">↕</text>',
              "переход между этажами"),
    "north": ('<path class="ink" d="M13 1 L17 13 L13 10 L9 13Z"/>', "север"),
}


def legend_keys(lv) -> list[str]:
    ps = [p for p in PIECES if p.lv == lv]
    keys = []

    def add(k):
        if k not in keys:
            keys.append(k)
    for p in ps:
        if p.cls in ("ord", "dst", "border", "out", "chan"):
            add(p.cls)
        elif p.cls == "rm sink":
            add("sink")
        elif p.cls == "ink" and p.shape == "rect":
            add("gate")
        elif p.cls == "pa dot":
            add("dot")
        elif p.cls == "stone":
            add("stone")
        elif p.shape == "path":
            add("sc" if p.id[0] == "S" and p.id[1:].isdigit() else "pa")
        elif p.shape in AREA_SHAPES:
            add({"ARENA": "arena", "WATER": "water", "WARP": "warp", "SECRET": "sec", "SAFE": "safe"}.get(p.kind, "rm"))
            if p.marker == "boss":
                add("boss")
            if p.marker in ("npc", "item") and p.kind == "ROOM":
                add(p.marker)
        elif p.shape == "tower":
            add("tw")
        elif p.shape == "shaft":
            add("shaft")
        elif p.shape == "mark":
            add({"sc": "sm"}.get(p.marker, p.marker) if p.marker in LEGEND or p.marker == "sc" else "gate")
    if any(p.to_lv == lv for p in PIECES):
        add("shaft")
    if lv == 1:
        add("north")
    order = list(LEGEND)
    return sorted(keys, key=order.index)


def legend_html(lv) -> str:
    spans = "".join(
        f'<span><svg width="26" height="16" viewBox="0 0 26 16" aria-hidden="true">{LEGEND[k][0]}</svg>{LEGEND[k][1]}</span>'
        for k in legend_keys(lv))
    return f'<div class="lg"><b>Условные обозначения</b><div>{spans}</div></div>'


def svg_level(lv) -> str:
    m = Map(lv)
    vw, vh = LEVELS[lv]["view"]
    ps = [p for p in PIECES if p.lv == lv and p.svg]
    o = [f'<svg viewBox="0 0 {vw} {vh}" role="img" aria-label="План локации {lv} · {LEVELS[lv]["name"]}">',
         f'  <rect class="bg" width="{vw}" height="{vh}"/><rect class="gr" width="{vw}" height="{vh}"/>',
         "  " + grid(m)]
    if lv == 1:
        o += ["  " + s for s in L1_BEFORE]
    for p in ps:
        if p.shape == "path" and p.kind == "CORRIDOR" and not (p.id[0] == "S" and p.id[1:].isdigit()):
            o.append("  " + path_svg(m, p))
    for p in ps:
        if p.shape in AREA_SHAPES:
            o.append("  " + area_svg(m, p))
    for p in ps:
        if p.shape == "path" and p.kind == "PATH" and not (p.id[0] == "S" and p.id[1:].isdigit()):
            o.append("  " + path_svg(m, p))
    for p in ps:
        if p.shape == "path" and p.id[0] == "S" and p.id[1:].isdigit():
            o.append("  " + path_svg(m, p))
    for p in ps:
        o += ["  " + s for s in symbol_svg(m, p)]
    o += ["  " + s for s in arrival_svg(m)]
    for p in ps:
        o += ["  " + s for s in label_svg(m, p)]
    if lv == 1:
        o += ["  " + s for s in L1_AFTER]
    o += ["  " + s for s in scale_bar(m)]
    o.append("</svg>")
    cap = f'<div class="cap">{CAPTIONS[lv]}<br>{ELITE_CAP[lv]}</div>'
    return "\n".join(o) + "\n" + cap + "\n" + legend_html(lv)


def inject_maps(html: str) -> str:
    for lv in LEVELS:
        start, end = f"<!-- MAP:L{lv} -->", f"<!-- /MAP:L{lv} -->"
        block = f"{start}\n{svg_level(lv)}\n{end}"
        if start in html:
            html = re.sub(re.escape(start) + r".*?" + re.escape(end), lambda _: block, html, flags=re.S)
            continue
        sec = re.search(rf'<section id="l{lv}">.*?</section>', html, flags=re.S)
        if not sec:
            raise SystemExit(f"нет <section id=\"l{lv}\"> в HTML")
        body = sec.group(0)
        new_body = re.sub(r'<svg viewBox=.*?</svg>\n(?:<div class="cap">.*?</div>\n)?<div class="lg">.*?</div></div>',
                          lambda _: block, body, count=1, flags=re.S)
        if new_body == body:
            raise SystemExit(f"не нашёл карту в секции l{lv}")
        html = html.replace(body, new_body)
    return html


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="только проверить раскладку")
    args = ap.parse_args()
    errors = check()
    for e in errors:
        print("ОШИБКА:", e, file=sys.stderr)
    if errors:
        return 1
    if args.check:
        print("раскладка в порядке")
        return 0
    JAVA_OUT.write_text(java_source(), encoding="utf-8")
    HTML.write_text(inject_maps(HTML.read_text(encoding="utf-8")), encoding="utf-8")
    print(f"записано: {JAVA_OUT.relative_to(ROOT)}, {HTML.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
