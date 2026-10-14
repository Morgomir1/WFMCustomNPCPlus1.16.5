// =============================================================================
// Камень Расколотого Рога — асинхронный каркас данжа
// План: herdstone_dungeon.html (масштаб 1.8 SVG = 1 блок).
// Комнаты — эллипсы/полигоны/тропы со схемы, не bounding-box прямоугольники.
//
// Основной способ (после пересборки мода):
//   /cncpdungeon herdstone
//   /cncpdungeon herdstone dry
//   /cncpdungeon herdstone 1
//   /cncpdungeon cancel | status
// Этот JS — запасной вариант через Global Player Scripts (!hsbuild).
//
// Встань в СЗ-угол терновой рамки локации 1 (сетка «0 / 0» на плане).
// Данж уходит +X (восток) и +Z (юг), нижние этажи вниз от ног.
// Лагеря чуть западнее рамки (отрицательный X) и южнее входа.
//
// Чат:
//   !hsbuild              все 4 локации
//   !hsbuild 1            только локация 1
//   !hsbuild 1 2          локации 1 и 2
//   !hsbuild dry [номера] посчитать джобы, ничего не ставить
//   !hsbuild force ...    перезапустить, даже если очередь уже идёт
//   !hscancel             остановить
//   !hsstatus             прогресс
//
// Расширение: add(...) в секции LAYOUT или registerKind("имя", fn).
// =============================================================================

var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();
var ArrayList = Java.type("java.util.ArrayList");

// =========================
// НАСТРОЙКИ
// =========================
var TIMER_ID = 8142;
var JOBS_PER_TICK = 2;
var MAX_FILL = 32768;
var CREATIVE_ONLY = true;
var PLACE_CEILING = true;
var FORCELOAD = true;
var ROOM_HEIGHT = 12;
var BORDER_HEIGHT = 4;

var TD_JOBS = "hs_build_jobs";
var TD_IDX = "hs_build_idx";
var TD_LOCK = "hs_build_lock";
var TD_NAME = "hs_build_name";
var TD_TOTAL = "hs_build_total";

var THEMES = {
    1: { floor: "minecraft:grass_block", wall: "minecraft:dark_oak_log", ceil: "minecraft:dark_oak_leaves" },
    2: { floor: "minecraft:stone_bricks", wall: "minecraft:mossy_stone_bricks", ceil: "minecraft:cracked_stone_bricks" },
    3: { floor: "minecraft:mycelium", wall: "minecraft:dark_oak_wood", ceil: "minecraft:dirt" },
    4: { floor: "minecraft:coarse_dirt", wall: "minecraft:cobblestone", ceil: "minecraft:rooted_dirt" }
};

var KIND_FLOOR = {
    safe: "minecraft:mossy_cobblestone",
    arena: "minecraft:red_nether_bricks",
    water: "minecraft:prismarine_bricks",
    warp: "minecraft:warped_nylium",
    secret: "minecraft:obsidian",
    camp_order: "minecraft:blue_terracotta",
    camp_destr: "minecraft:purple_terracotta",
    path: "minecraft:grass_path"
};

var MARK = {
    boss: "minecraft:red_wool",
    puzzle: "minecraft:yellow_wool",
    sc: "minecraft:cyan_wool",
    item: "minecraft:lime_wool",
    npc: "minecraft:white_wool",
    gate: "minecraft:black_wool",
    valve: "minecraft:orange_wool"
};

// floor = смещение Y от ног игрока (локация 1 = 0)
var LEVELS = [
    { id: 1, name: "Гнилая просека", floor: 0, h: ROOM_HEIGHT },
    { id: 2, name: "Осквернённая крипта", floor: -20, h: ROOM_HEIGHT },
    { id: 3, name: "Корневые глубины", floor: -40, h: ROOM_HEIGHT },
    { id: 4, name: "Провал Камня Стада", floor: -60, h: ROOM_HEIGHT }
];

var LAYOUT = [];
var CUSTOM = {};

function registerKind(name, fn) {
    CUSTOM[name] = fn;
}

function add(lv, id, k, x, z, w, d, mk) {
    LAYOUT.push({
        lv: lv, id: id, k: k, x: x, z: z, w: w || 1, d: d || 1, mk: mk || "",
        shape: "rect", pts: [], thick: 6, air: false, wallH: 0, floor: ""
    });
    return LAYOUT[LAYOUT.length - 1];
}

function lastRec() {
    return LAYOUT[LAYOUT.length - 1];
}

function air() {
    lastRec().air = true;
}

function walls(h) {
    lastRec().wallH = h;
}

function noWalls() {
    lastRec().wallH = -1;
}

function floorOf(block) {
    lastRec().floor = block;
}

function bboxOf(pts, pad) {
    var minx = pts[0];
    var minz = pts[1];
    var maxx = minx;
    var maxz = minz;
    var i;
    for (i = 2; i + 1 < pts.length; i += 2) {
        if (pts[i] < minx) minx = pts[i];
        if (pts[i] > maxx) maxx = pts[i];
        if (pts[i + 1] < minz) minz = pts[i + 1];
        if (pts[i + 1] > maxz) maxz = pts[i + 1];
    }
    return [minx - pad, minz - pad, maxx - minx + 1 + pad * 2, maxz - minz + 1 + pad * 2];
}

function addEllipse(lv, id, k, x, z, w, d, mk) {
    add(lv, id, k, x, z, w, d, mk);
    lastRec().shape = "ellipse";
}

function addCircle(lv, id, k, cx, cz, r, mk) {
    addEllipse(lv, id, k, cx - r, cz - r, r * 2 + 1, r * 2 + 1, mk);
}

function addPoly(lv, id, k, mk, pts) {
    var bb = bboxOf(pts, 0);
    add(lv, id, k, bb[0], bb[1], bb[2], bb[3], mk);
    lastRec().shape = "poly";
    lastRec().pts = pts;
}

function addTrail(lv, id, k, width, pts) {
    var pad = Math.floor((width + 1) / 2);
    var bb = bboxOf(pts, pad);
    add(lv, id, k, bb[0], bb[1], bb[2], bb[3], "");
    lastRec().shape = "path";
    lastRec().pts = pts;
    lastRec().thick = width;
}

function addShaft(fromLv, toLv, id, x, z, w, d) {
    add(fromLv, id, "shaft", x, z, w || 3, d || 3, "gate");
    lastRec().to = toLv;
}

// =========================
// LAYOUT — план HTML, локальные блоки
// СЗ рамки локации 1 = (0,0). 350×247 — терновая стена.
// =========================
function fillLayout() {
    if (LAYOUT.length > 0) return;

    add(1, "border", "border", 0, 0, 350, 247, "");
    add(1, "camp_order", "camp_order", -72, 267, 117, 40, "npc"); air();
    add(1, "camp_destr", "camp_destr", 233, 267, 117, 40, "npc"); air();
    add(1, "crossroads", "mark", 139, 287, 1, 1, "gate");
    addTrail(1, "path_camps", "path", 5, [44, 287, 139, 287, 233, 287]); air();
    addTrail(1, "path_gate", "path", 6, [139, 287, 103, 251]); air();
    add(1, "A1", "safe", 73, 197, 58, 44, ""); air();
    addPoly(1, "A2", "safe", "", [81, 197, 57, 157, 62, 117, 100, 104, 153, 117, 153, 140, 108, 133, 91, 157, 106, 197]);
    air(); floorOf("minecraft:podzol");
    addTrail(1, "path_A2", "path", 6, [
        102, 197, 100, 194, 98, 191, 96, 188, 94, 185, 92, 182, 90, 179, 88, 175, 87, 172,
        85, 169, 84, 166, 83, 163, 82, 160, 81, 157, 80, 154, 80, 151, 80, 148, 79, 145,
        79, 142, 80, 140, 80, 137, 80, 134, 81, 132, 82, 129, 83, 127, 85, 124, 86, 123,
        88, 121, 90, 120, 93, 119, 95, 118, 98, 118, 101, 118, 104, 118, 107, 118, 111, 118,
        114, 119, 117, 119, 121, 120, 125, 121, 128, 122, 132, 123, 136, 124, 139, 125,
        143, 125, 146, 126, 150, 127, 153, 128, 157, 129
    ]); air();
    addEllipse(1, "A3", "room", 7, 21, 98, 62, "item"); air();
    addTrail(1, "path_A3", "path", 5, [79, 109, 62, 83]); air();
    add(1, "A5", "room", 157, 86, 116, 83, ""); air(); walls(3);
    add(1, "A5_chapel", "room", 167, 96, 34, 23, ""); air(); walls(3);
    add(1, "A6", "room", 279, 97, 32, 32, "puzzle"); walls(8);
    addTrail(1, "path_A5_A6", "path", 4, [272, 112, 279, 112]); air();
    add(1, "A4", "arena", 228, 12, 111, 63, "boss"); air(); walls(3);
    addTrail(1, "path_A5_A4", "path", 5, [250, 86, 257, 78]); air();
    add(1, "entrance", "room", 90, 242, 24, 9, "gate"); air(); walls(3);
    add(1, "exit_gate", "room", 168, 242, 27, 9, "gate"); air(); walls(3);
    addCircle(1, "gallows", "safe", 181, 223, 10, "gate"); air();
    addTrail(1, "path_exit", "path", 4, [181, 251, 150, 284]); air(); floorOf("minecraft:coarse_dirt");
    add(1, "ungor_1", "tower", 66, 152, 3, 3, "npc");
    add(1, "ungor_2", "tower", 84, 113, 3, 3, "npc");
    add(1, "ungor_3", "tower", 130, 119, 3, 3, "npc");
    add(1, "ungor_4", "tower", 96, 176, 3, 3, "npc");
    addTrail(1, "S1", "path", 4, [
        161, 164, 160, 167, 158, 169, 157, 172, 156, 174, 154, 176, 153, 178, 151, 180,
        150, 182, 149, 184, 147, 186, 146, 187, 145, 189, 144, 191, 142, 192, 141, 194,
        140, 196, 139, 197, 138, 199, 136, 200, 135, 202, 134, 203, 133, 205, 132, 206, 131, 208
    ]); air(); floorOf("minecraft:cyan_terracotta");
    add(1, "S1_mark", "mark", 150, 184, 1, 1, "sc");
    addShaft(1, 2, "A4_down", 320, 29, 3, 3);

    add(2, "B1", "room", 333, 22, 83, 44, "");
    addPoly(2, "B2", "water", "", [168, 78, 329, 83, 340, 139, 318, 178, 190, 178, 173, 139]);
    add(2, "B3", "room", 28, 78, 112, 83, "puzzle");
    add(2, "B3_reliq", "room", 70, 106, 28, 26, "item");
    add(2, "B4", "room", 357, 157, 71, 51, "npc");
    addEllipse(2, "B5", "arena", 156, 204, 167, 75, "boss");
    addTrail(2, "path_B1_B2", "corridor", 8, [361, 67, 326, 93]);
    addTrail(2, "path_B2_B3", "corridor", 7, [173, 120, 140, 120]);
    addTrail(2, "path_B2_B4", "corridor", 7, [333, 153, 357, 169]);
    addTrail(2, "path_B4_B5", "corridor", 8, [362, 207, 314, 228]);
    add(2, "V1", "mark", 196, 106, 1, 1, "valve");
    add(2, "V2", "mark", 311, 111, 1, 1, "valve");
    add(2, "V3", "mark", 251, 167, 1, 1, "valve");
    addTrail(2, "S2", "corridor", 4, [
        396, 157, 397, 153, 398, 149, 399, 146, 399, 142, 400, 138, 400, 134, 400, 131,
        401, 127, 400, 123, 400, 119, 400, 115, 399, 111, 399, 108, 398, 104, 397, 100,
        396, 96, 395, 93, 394, 89, 392, 85, 391, 82, 389, 78, 387, 75, 385, 71, 383, 68
    ]); floorOf("minecraft:cyan_terracotta");
    add(2, "S2_mark", "mark", 403, 117, 1, 1, "sc");
    addShaft(2, 3, "B5_down", 167, 264, 3, 3);

    add(3, "C1", "room", 28, 28, 44, 89, "");
    add(3, "C2", "room", 100, 33, 122, 78, "puzzle");
    addPoly(3, "C3", "warp", "item", [239, 33, 400, 39, 411, 100, 383, 149, 289, 150, 239, 111]);
    add(3, "C3a", "room", 247, 167, 62, 31, "");
    add(3, "C4", "secret", 333, 167, 89, 61, "boss");
    addCircle(3, "C5", "arena", 146, 213, 61, "boss");
    addCircle(3, "C5_pit", "room", 146, 213, 21, ""); floorOf("minecraft:soul_soil"); noWalls();
    add(3, "gut", "secret", 17, 250, 53, 32, "");
    addTrail(3, "path_C1_C2", "corridor", 6, [72, 72, 100, 72]);
    addTrail(3, "path_C2_C3", "corridor", 6, [222, 72, 239, 72]);
    addTrail(3, "path_C3_C3a", "corridor", 6, [279, 150, 277, 167]);
    addTrail(3, "path_C3a_C5", "corridor", 7, [247, 191, 204, 200]);
    addTrail(3, "path_C3_C4", "corridor", 3, [378, 149, 378, 167]);
    addTrail(3, "S3", "corridor", 4, [
        249, 177, 241, 172, 232, 168, 224, 164, 215, 160, 207, 157, 198, 153, 190, 150,
        181, 147, 173, 144, 165, 142, 157, 139, 148, 137, 141, 135, 133, 132, 125, 130,
        118, 128, 111, 126, 105, 124, 98, 121, 92, 119, 87, 117, 81, 114, 77, 112, 72, 109
    ]); floorOf("minecraft:cyan_terracotta");
    add(3, "S3_mark", "mark", 167, 136, 1, 1, "sc");
    addShaft(3, 4, "C5_down", 146, 278, 3, 3);

    add(4, "D0", "mark", 53, 157, 1, 1, "gate");
    add(4, "D1", "room", 33, 33, 133, 89, "");
    add(4, "D1_table", "room", 61, 61, 78, 28, ""); floorOf("minecraft:spruce_planks"); noWalls();
    addCircle(4, "D2", "room", 233, 72, 39, "puzzle");
    addCircle(4, "D3", "arena", 317, 206, 86, "boss");
    addTrail(4, "chan_n", "path", 3, [317, 206, 317, 128]); floorOf("minecraft:red_nether_bricks");
    addTrail(4, "chan_sw", "path", 3, [317, 206, 250, 244]); floorOf("minecraft:red_nether_bricks");
    addTrail(4, "chan_se", "path", 3, [317, 206, 383, 244]); floorOf("minecraft:red_nether_bricks");
    add(4, "heart_n", "mark", 317, 131, 1, 1, "puzzle");
    add(4, "heart_sw", "mark", 253, 242, 1, 1, "puzzle");
    add(4, "heart_se", "mark", 380, 242, 1, 1, "puzzle");
    add(4, "D4", "room", 383, 261, 53, 31, "item");
    addTrail(4, "path_D0_D1", "corridor", 6, [56, 148, 64, 123]);
    addTrail(4, "path_D1_D2", "corridor", 6, [167, 78, 194, 78]);
    addTrail(4, "path_D2_D3", "corridor", 7, [260, 102, 281, 126]);
    addTrail(4, "S4", "corridor", 4, [
        203, 98, 199, 104, 194, 109, 188, 114, 183, 119, 177, 124, 171, 128, 165, 132,
        159, 136, 152, 140, 146, 143, 139, 146, 133, 149, 126, 151, 120, 153, 113, 155,
        107, 157, 100, 158, 94, 159, 88, 160, 82, 161, 76, 161, 71, 161, 65, 161, 60, 161
    ]); floorOf("minecraft:cyan_terracotta");
    add(4, "S4_mark", "mark", 139, 148, 1, 1, "sc");
    add(4, "exit_up", "mark", 423, 249, 1, 1, "gate");
}

// =========================
// СОБЫТИЯ ИГРОКА
// =========================
function init(e) {
    ensureTimer(e.player);
}

function interact(e) {
    ensureTimer(e.player);
}

function timer(e) {
    if (Number(e.id) != TIMER_ID) return;
    stepBuild(e.player);
}

function chat(e) {
    var msg = String(e.message).replace(/^\s+|\s+$/g, "");
    if (msg.indexOf("!hs") != 0) return;
    e.setCanceled(true);
    var player = e.player;
    if (player == null) return;
    ensureTimer(player);
    if (CREATIVE_ONLY && player.getGamemode() != 1) {
        player.message("§cКаркас данжа: только в креативе.");
        return;
    }
    var parts = msg.split(" ");
    var cmd = parts[0];
    if (cmd == "!hscancel") {
        clearBuild(player.getWorld());
        player.message("§7Генерация остановлена.");
        return;
    }
    if (cmd == "!hsstatus") {
        player.message(statusLine(player.getWorld()));
        return;
    }
    if (cmd != "!hsbuild") {
        player.message("§7!hsbuild [dry] [force] [1-4]  |  !hscancel  |  !hsstatus");
        return;
    }
    var dry = false;
    var force = false;
    var levels = [];
    var i;
    for (i = 1; i < parts.length; i++) {
        var p = parts[i];
        if (p == "dry") dry = true;
        else if (p == "force") force = true;
        else {
            var n = parseInt(p);
            if (n >= 1 && n <= 4) levels.push(n);
        }
    }
    if (levels.length == 0) levels = [1, 2, 3, 4];
    startBuild(player, levels, dry, force);
}

// =========================
// ОЧЕРЕДЬ
// =========================
function startBuild(player, levels, dry, force) {
    var world = player.getWorld();
    var td = world.getTempdata();
    if (!force && !dry && td.get(TD_JOBS) != null) {
        player.message("§cУже идёт генерация. §7!hsstatus §cили §7!hsbuild force");
        return;
    }
    fillLayout();
    var ox = player.getBlockX();
    var oy = player.getBlockY();
    var oz = player.getBlockZ();
    var jobs = new ArrayList();
    compile(jobs, ox, oy, oz, levels);
    if (dry) {
        player.message("§e[dry] §f" + jobs.size() + " джобов, origin " + ox + " " + oy + " " + oz
            + " §7локации " + levels.join(","));
        return;
    }
    td.put(TD_JOBS, jobs);
    td.put(TD_IDX, 0);
    td.put(TD_TOTAL, jobs.size());
    td.put(TD_LOCK, String(player.getUUID()));
    td.put(TD_NAME, String(player.getName()));
    if (FORCELOAD) {
        var bb = boundsOf(levels);
        runCmd(world, "forceload add " + (ox + bb.x1) + " " + (oz + bb.z1) + " " + (ox + bb.x2) + " " + (oz + bb.z2));
    }
    player.message("§2Каркас данжа: §f" + jobs.size() + " шагов §7с " + ox + " " + oy + " " + oz
        + "  локации " + levels.join(","));
    player.message("§8Не выходи с сервера, пока не придёт сообщение о конце.");
}

function stepBuild(player) {
    var world = player.getWorld();
    var td = world.getTempdata();
    var jobs = td.get(TD_JOBS);
    if (jobs == null) return;
    if (!ownsLock(player, td)) return;
    var idx = getIntTd(td, TD_IDX);
    var total = jobs.size();
    var n;
    for (n = 0; n < JOBS_PER_TICK; n++) {
        if (idx >= total) {
            finishBuild(player, world, td, total);
            return;
        }
        try {
            runJob(world, String(jobs.get(idx)));
        } catch (ex) {
            log("hsbuild job " + idx + " fail: " + ex);
        }
        idx++;
    }
    td.put(TD_IDX, idx);
    if (idx % 40 == 0 || idx % 40 == 1) {
        player.message("§7данж: " + idx + "/" + total);
    }
}

function finishBuild(player, world, td, total) {
    player.message("§aКаркас Камня Расколотого Рога готов. §7" + total + " шагов.");
    clearBuild(world);
}

function clearBuild(world) {
    var td = world.getTempdata();
    td.remove(TD_JOBS);
    td.remove(TD_IDX);
    td.remove(TD_LOCK);
    td.remove(TD_NAME);
    td.remove(TD_TOTAL);
}

function ownsLock(player, td) {
    var lock = td.get(TD_LOCK);
    if (lock == null) {
        td.put(TD_LOCK, String(player.getUUID()));
        td.put(TD_NAME, String(player.getName()));
        return true;
    }
    if (String(lock) == String(player.getUUID())) return true;
    var name = td.get(TD_NAME);
    if (name != null) {
        try {
            if (player.getWorld().getPlayer(String(name)) == null) {
                td.put(TD_LOCK, String(player.getUUID()));
                td.put(TD_NAME, String(player.getName()));
                return true;
            }
        } catch (ex) {}
    }
    return false;
}

function statusLine(world) {
    var td = world.getTempdata();
    var jobs = td.get(TD_JOBS);
    if (jobs == null) return "§7Генерация не запущена.";
    return "§e" + getIntTd(td, TD_IDX) + "/" + jobs.size() + " §7lock=" + td.get(TD_NAME);
}

// =========================
// КОМПИЛЯЦИЯ ПЛАНА → ДЖОБЫ
// =========================
function compile(jobs, ox, oy, oz, levels) {
    var want = {};
    var i;
    for (i = 0; i < levels.length; i++) want[levels[i]] = true;
    var openings = [];
    var rooms = [];
    for (i = 0; i < LAYOUT.length; i++) {
        var rec = LAYOUT[i];
        if (!want[rec.lv]) continue;
        if (isWalk(rec)) openings.push(rec);
        else if (isSolidRoom(rec)) rooms.push(rec);
    }
    for (i = 0; i < LAYOUT.length; i++) {
        rec = LAYOUT[i];
        if (!want[rec.lv]) continue;
        var fn = CUSTOM[rec.k];
        if (fn) {
            fn(jobs, rec, ox, oy, oz);
        } else {
            compileDefault(jobs, rec, ox, oy, oz, openings, rooms);
        }
    }
}

function isWalk(rec) {
    return rec.k == "path" || rec.k == "corridor";
}

function isSolidRoom(rec) {
    if (rec.shape == "path" || isWalk(rec)) return false;
    return rec.k != "border" && rec.k != "tower" && rec.k != "mark" && rec.k != "shaft";
}

function compileDefault(jobs, rec, ox, oy, oz, openings, rooms) {
    var k = rec.k;
    if (k == "border") buildBorder(jobs, rec, ox, oy, oz);
    else if (k == "path") rasterFootprint(jobs, rec, ox, oy, oz, openings, rooms, true);
    else if (k == "corridor") rasterFootprint(jobs, rec, ox, oy, oz, openings, rooms, false);
    else if (k == "shaft") buildShaft(jobs, rec, ox, oy, oz);
    else if (k == "mark") buildMark(jobs, rec, ox, oy, oz);
    else if (k == "tower") buildTower(jobs, rec, ox, oy, oz);
    else rasterFootprint(jobs, rec, ox, oy, oz, openings, rooms, rec.air);
}

function levelOf(id) {
    var i;
    for (i = 0; i < LEVELS.length; i++) {
        if (LEVELS[i].id == id) return LEVELS[i];
    }
    return LEVELS[0];
}

function themeOf(lv) {
    return THEMES[lv] || THEMES[1];
}

function floorY(lv, oy) {
    return oy + levelOf(lv).floor;
}

function roomH(lv) {
    return levelOf(lv).h;
}

function floorBlock(rec) {
    if (rec.floor) return rec.floor;
    if (KIND_FLOOR[rec.k]) return KIND_FLOOR[rec.k];
    return themeOf(rec.lv).floor;
}

function shapeContains(rec, x, z) {
    var sh = rec.shape || "rect";
    if (sh == "ellipse") {
        var cx = rec.x + (rec.w - 1) * 0.5;
        var cz = rec.z + (rec.d - 1) * 0.5;
        var rx = rec.w * 0.5;
        var rz = rec.d * 0.5;
        if (rx < 0.5) rx = 0.5;
        if (rz < 0.5) rz = 0.5;
        var dx = (x - cx) / rx;
        var dz = (z - cz) / rz;
        return dx * dx + dz * dz <= 1;
    }
    if (sh == "poly") return insidePoly(rec.pts, x + 0.5, z + 0.5);
    if (sh == "path") return nearPolyline(rec.pts, rec.thick * 0.5, x + 0.5, z + 0.5);
    return x >= rec.x && x < rec.x + rec.w && z >= rec.z && z < rec.z + rec.d;
}

function insidePoly(pts, x, z) {
    if (!pts || pts.length < 6) return false;
    var inside = false;
    var n = pts.length;
    var i;
    var j = n - 2;
    for (i = 0; i < n; i += 2) {
        var zi = pts[i + 1];
        var zj = pts[j + 1];
        if ((zi > z) != (zj > z)) {
            var xi = pts[i];
            var xj = pts[j];
            var xInt = (xj - xi) * (z - zi) / (zj - zi) + xi;
            if (x < xInt) inside = !inside;
        }
        j = i;
    }
    return inside;
}

function nearPolyline(pts, radius, x, z) {
    if (!pts || pts.length < 4) return false;
    var r2 = radius * radius;
    var i;
    for (i = 0; i + 3 < pts.length; i += 2) {
        if (dist2Seg(x, z, pts[i], pts[i + 1], pts[i + 2], pts[i + 3]) <= r2) return true;
    }
    return false;
}

function dist2Seg(px, pz, ax, az, bx, bz) {
    var vx = bx - ax;
    var vz = bz - az;
    var len2 = vx * vx + vz * vz;
    var t = 0;
    if (len2 > 1e-8) {
        t = ((px - ax) * vx + (pz - az) * vz) / len2;
        if (t < 0) t = 0;
        else if (t > 1) t = 1;
    }
    var dx = px - (ax + t * vx);
    var dz = pz - (az + t * vz);
    return dx * dx + dz * dz;
}

function shapePerimeter(rec, x, z) {
    if (!shapeContains(rec, x, z)) return false;
    return !shapeContains(rec, x - 1, z) || !shapeContains(rec, x + 1, z)
        || !shapeContains(rec, x, z - 1) || !shapeContains(rec, x, z + 1);
}

function anyContains(list, lv, x, z) {
    var i;
    for (i = 0; i < list.length; i++) {
        if (list[i].lv == lv && shapeContains(list[i], x, z)) return true;
    }
    return false;
}

function skipWall(rec, x, z, openings, rooms) {
    if (isSolidRoom(rec)) return anyContains(openings, rec.lv, x, z);
    if (isWalk(rec)) return anyContains(rooms, rec.lv, x, z);
    return false;
}

function rasterFootprint(jobs, rec, ox, oy, oz, openings, rooms, openAir) {
    var y0 = floorY(rec.lv, oy);
    var th = themeOf(rec.lv);
    var floor = floorBlock(rec);
    var corridor = rec.k == "corridor";
    var placeWalls = rec.k != "path" && rec.wallH >= 0;
    var defaultH = corridor ? 5 : roomH(rec.lv);
    var h;
    if (rec.wallH > 0) h = rec.wallH;
    else if (openAir) h = 2;
    else h = defaultH;
    var xMin = rec.x;
    var zMin = rec.z;
    var xMax = rec.x + rec.w - 1;
    var zMax = rec.z + rec.d - 1;
    var z;
    var x;
    for (z = zMin; z <= zMax; z++) {
        var floorRun = null;
        var wallRun = null;
        var ceilRun = null;
        for (x = xMin; x <= xMax + 1; x++) {
            var inside = x <= xMax && shapeContains(rec, x, z);
            if (inside) {
                if (floorRun == null) floorRun = x;
            } else if (floorRun != null) {
                enqueueFill(jobs, ox + floorRun, y0, oz + z, ox + x - 1, y0, oz + z, floor);
                floorRun = null;
            }
            var wallHere = placeWalls && inside && shapePerimeter(rec, x, z) && !skipWall(rec, x, z, openings, rooms);
            if (wallHere) {
                if (wallRun == null) wallRun = x;
            } else if (wallRun != null) {
                enqueueFill(jobs, ox + wallRun, y0 + 1, oz + z, ox + x - 1, y0 + h - 1, oz + z, th.wall);
                wallRun = null;
            }
            var ceilHere;
            if (corridor) ceilHere = inside && PLACE_CEILING;
            else ceilHere = inside && PLACE_CEILING && !openAir;
            if (ceilHere) {
                if (ceilRun == null) ceilRun = x;
            } else if (ceilRun != null) {
                enqueueFill(jobs, ox + ceilRun, y0 + h, oz + z, ox + x - 1, y0 + h, oz + z, th.ceil);
                ceilRun = null;
            }
        }
    }
    if (rec.mk) {
        enqueueSet(jobs, ox + rec.x + Math.floor(rec.w / 2), y0 + 1, oz + rec.z + Math.floor(rec.d / 2), MARK[rec.mk] || MARK.npc);
    }
}

function buildBorder(jobs, rec, ox, oy, oz) {
    var x1 = ox + rec.x;
    var z1 = oz + rec.z;
    var x2 = x1 + rec.w - 1;
    var z2 = z1 + rec.d - 1;
    var y0 = floorY(rec.lv, oy);
    var y1 = y0 + BORDER_HEIGHT - 1;
    var wall = "minecraft:dark_oak_log";
    enqueueFill(jobs, x1, y0, z1, x2, y1, z1, wall);
    enqueueFill(jobs, x1, y0, z2, x2, y1, z2, wall);
    enqueueFill(jobs, x1, y0, z1, x1, y1, z2, wall);
    enqueueFill(jobs, x2, y0, z1, x2, y1, z2, wall);
    enqueueFill(jobs, x1, y0 + BORDER_HEIGHT, z1, x2, y0 + BORDER_HEIGHT, z1, "minecraft:barrier");
    enqueueFill(jobs, x1, y0 + BORDER_HEIGHT, z2, x2, y0 + BORDER_HEIGHT, z2, "minecraft:barrier");
    enqueueFill(jobs, x1, y0 + BORDER_HEIGHT, z1, x1, y0 + BORDER_HEIGHT, z2, "minecraft:barrier");
    enqueueFill(jobs, x2, y0 + BORDER_HEIGHT, z1, x2, y0 + BORDER_HEIGHT, z2, "minecraft:barrier");
}

function buildTower(jobs, rec, ox, oy, oz) {
    var x1 = ox + rec.x;
    var z1 = oz + rec.z;
    var x2 = x1 + rec.w - 1;
    var z2 = z1 + rec.d - 1;
    var y0 = floorY(rec.lv, oy);
    enqueueFill(jobs, x1, y0, z1, x2, y0 + 5, z2, "minecraft:dark_oak_log");
    if (rec.mk) enqueueSet(jobs, x1, y0 + 6, z1, MARK[rec.mk] || MARK.npc);
}

function buildMark(jobs, rec, ox, oy, oz) {
    var y0 = floorY(rec.lv, oy);
    enqueueSet(jobs, ox + rec.x, y0 + 1, oz + rec.z, MARK[rec.mk] || MARK.npc);
}

function buildShaft(jobs, rec, ox, oy, oz) {
    var x1 = ox + rec.x;
    var z1 = oz + rec.z;
    var x2 = x1 + rec.w - 1;
    var z2 = z1 + rec.d - 1;
    var yTop = floorY(rec.lv, oy);
    var yBot = floorY(rec.to, oy);
    if (yBot > yTop) {
        var t = yBot;
        yBot = yTop;
        yTop = t;
    }
    enqueueFill(jobs, x1, yBot, z1, x2, yTop + roomH(rec.lv), z2, "minecraft:air");
    enqueueFill(jobs, x1, yBot, z1, x1, yTop, z1, "minecraft:ladder");
    if (rec.mk) enqueueSet(jobs, x1 + 1, yTop + 1, z1 + 1, MARK[rec.mk] || MARK.gate);
}

function boundsOf(levels) {
    var want = {};
    var i;
    for (i = 0; i < levels.length; i++) want[levels[i]] = true;
    var x1 = 0;
    var z1 = 0;
    var x2 = 0;
    var z2 = 0;
    var first = true;
    for (i = 0; i < LAYOUT.length; i++) {
        var rec = LAYOUT[i];
        if (!want[rec.lv]) continue;
        var a = rec.x;
        var b = rec.z;
        var c = rec.x + rec.w;
        var d = rec.z + rec.d;
        if (first) {
            x1 = a;
            z1 = b;
            x2 = c;
            z2 = d;
            first = false;
        } else {
            if (a < x1) x1 = a;
            if (b < z1) z1 = b;
            if (c > x2) x2 = c;
            if (d > z2) z2 = d;
        }
    }
    return { x1: x1, z1: z1, x2: x2, z2: z2 };
}

// =========================
// FILL / SET
// =========================
function enqueueFill(jobs, x1, y1, z1, x2, y2, z2, block) {
    var ax = Math.min(x1, x2);
    var bx = Math.max(x1, x2);
    var ay = Math.min(y1, y2);
    var by = Math.max(y1, y2);
    var az = Math.min(z1, z2);
    var bz = Math.max(z1, z2);
    var vol = (bx - ax + 1) * (by - ay + 1) * (bz - az + 1);
    if (vol <= MAX_FILL) {
        jobs.add("f|" + ax + "|" + ay + "|" + az + "|" + bx + "|" + by + "|" + bz + "|" + block);
        return;
    }
    var dx = bx - ax + 1;
    var dy = by - ay + 1;
    var dz = bz - az + 1;
    if (dx >= dy && dx >= dz) {
        var slice = Math.max(1, Math.floor(MAX_FILL / (dy * dz)));
        var x;
        for (x = ax; x <= bx; x += slice) {
            enqueueFill(jobs, x, ay, az, Math.min(bx, x + slice - 1), by, bz, block);
        }
    } else if (dz >= dy) {
        var sliceZ = Math.max(1, Math.floor(MAX_FILL / (dx * dy)));
        var z;
        for (z = az; z <= bz; z += sliceZ) {
            enqueueFill(jobs, ax, ay, z, bx, by, Math.min(bz, z + sliceZ - 1), block);
        }
    } else {
        var sliceY = Math.max(1, Math.floor(MAX_FILL / (dx * dz)));
        var y;
        for (y = ay; y <= by; y += sliceY) {
            enqueueFill(jobs, ax, y, az, bx, Math.min(by, y + sliceY - 1), bz, block);
        }
    }
}

function enqueueSet(jobs, x, y, z, block) {
    jobs.add("s|" + x + "|" + y + "|" + z + "|" + block);
}

function runJob(world, raw) {
    var p = raw.split("|");
    if (p[0] == "f") {
        runCmd(world, "fill " + p[1] + " " + p[2] + " " + p[3] + " " + p[4] + " " + p[5] + " " + p[6] + " " + p[7] + " replace");
    } else if (p[0] == "s") {
        try {
            world.setBlock(parseInt(p[1]), parseInt(p[2]), parseInt(p[3]), p[4], 0);
        } catch (ex) {
            runCmd(world, "setblock " + p[1] + " " + p[2] + " " + p[3] + " " + p[4]);
        }
    }
}

function runCmd(world, cmd) {
    NpcAPI.executeCommand(world, cmd);
}

function ensureTimer(player) {
    if (player == null) return;
    try {
        var t = player.getTimers();
        if (t == null) return;
        if (typeof t.forceStart == "function") t.forceStart(TIMER_ID, 1, true);
        else if (typeof t.has != "function" || !t.has(TIMER_ID)) t.start(TIMER_ID, 1, true);
    } catch (ex) {
        log("hsbuild timer: " + ex);
    }
}

function getIntTd(td, key) {
    if (!td.has(key)) return 0;
    return parseInt(String(td.get(key)));
}

// Пример расширения: registerKind("pool", function(jobs, rec, ox, oy, oz) { ... });
