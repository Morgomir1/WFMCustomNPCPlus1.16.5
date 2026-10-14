// =============================================================================
// Камень Расколотого Рога — асинхронный каркас данжа
// План: herdstone_dungeon.html (масштаб 1.8 SVG = 1 блок)
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
    LAYOUT.push({ lv: lv, id: id, k: k, x: x, z: z, w: w || 1, d: d || 1, mk: mk || "" });
}

function addShaft(fromLv, toLv, id, x, z, w, d) {
    LAYOUT.push({ lv: fromLv, id: id, k: "shaft", to: toLv, x: x, z: z, w: w || 3, d: d || 3, mk: "gate" });
}

// =========================
// LAYOUT — план HTML, локальные блоки
// СЗ рамки локации 1 = (0,0). 350×247 — терновая стена.
// =========================
function fillLayout() {
    if (LAYOUT.length > 0) return;

    // --- локация 1 ---
    add(1, "border", "border", 0, 0, 350, 247, "");
    add(1, "camp_order", "camp_order", -72, 267, 117, 40, "npc");
    add(1, "camp_destr", "camp_destr", 233, 267, 117, 40, "npc");
    add(1, "crossroads", "mark", 139, 287, 1, 1, "gate");
    add(1, "path_order", "path", 48, 267, 90, 4, "");
    add(1, "path_destr", "path", 139, 267, 94, 4, "");
    add(1, "path_gate", "path", 135, 247, 8, 40, "");
    add(1, "A1", "safe", 73, 197, 58, 44, "");
    add(1, "A3", "room", 7, 21, 98, 62, "item");
    add(1, "A5", "room", 157, 86, 116, 83, "");
    add(1, "A5_chapel", "room", 167, 96, 34, 23, "");
    add(1, "A6", "room", 279, 97, 32, 32, "puzzle");
    add(1, "A4", "arena", 228, 12, 111, 63, "boss");
    add(1, "entrance", "room", 90, 242, 24, 9, "gate");
    add(1, "exit_gate", "room", 168, 242, 27, 9, "gate");
    add(1, "gallows", "safe", 171, 213, 20, 20, "gate");
    add(1, "path_A1_A3", "path", 90, 80, 8, 117, "");
    add(1, "path_A3_A5", "path", 105, 80, 52, 8, "");
    add(1, "path_A5_A6", "path", 273, 108, 8, 10, "");
    add(1, "path_A5_A4", "path", 270, 75, 8, 12, "");
    add(1, "ungor_1", "tower", 66, 152, 3, 3, "npc");
    add(1, "ungor_2", "tower", 84, 113, 3, 3, "npc");
    add(1, "ungor_3", "tower", 130, 119, 3, 3, "npc");
    add(1, "ungor_4", "tower", 96, 176, 3, 3, "npc");
    add(1, "S1", "mark", 150, 184, 1, 1, "sc");
    addShaft(1, 2, "A4_down", 320, 29, 3, 3);

    // --- локация 2 ---
    add(2, "B1", "room", 333, 22, 83, 44, "");
    add(2, "B2", "water", 168, 78, 172, 100, "");
    add(2, "B3", "room", 28, 78, 112, 83, "puzzle");
    add(2, "B4", "room", 357, 157, 71, 51, "npc");
    add(2, "B5", "arena", 156, 204, 167, 73, "boss");
    add(2, "path_B1_B2", "corridor", 300, 55, 40, 24, "");
    add(2, "path_B2_B3", "corridor", 140, 100, 30, 12, "");
    add(2, "path_B2_B4", "corridor", 330, 150, 28, 12, "");
    add(2, "path_B4_B5", "corridor", 300, 210, 50, 16, "");
    add(2, "V1", "mark", 196, 106, 1, 1, "valve");
    add(2, "V2", "mark", 311, 111, 1, 1, "valve");
    add(2, "V3", "mark", 251, 167, 1, 1, "valve");
    add(2, "S2", "mark", 403, 117, 1, 1, "sc");
    addShaft(2, 3, "B5_down", 167, 264, 3, 3);

    // --- локация 3 ---
    add(3, "C1", "room", 28, 28, 44, 89, "");
    add(3, "C2", "room", 100, 33, 122, 78, "puzzle");
    add(3, "C3", "warp", 239, 33, 172, 117, "item");
    add(3, "C3a", "room", 247, 167, 62, 31, "");
    add(3, "C4", "secret", 333, 167, 89, 61, "boss");
    add(3, "C5", "arena", 84, 152, 122, 122, "boss");
    add(3, "gut", "secret", 17, 250, 53, 32, "");
    add(3, "path_C1_C2", "corridor", 72, 60, 28, 10, "");
    add(3, "path_C2_C3", "corridor", 222, 60, 18, 10, "");
    add(3, "path_C3_C3a", "corridor", 250, 150, 10, 18, "");
    add(3, "path_C3a_C5", "corridor", 180, 190, 70, 10, "");
    add(3, "S3", "mark", 167, 136, 1, 1, "sc");
    addShaft(3, 4, "C5_down", 146, 248, 3, 3);

    // --- локация 4 ---
    add(4, "D0", "mark", 53, 157, 1, 1, "gate");
    add(4, "D1", "room", 33, 33, 133, 89, "");
    add(4, "D2", "room", 194, 33, 78, 78, "puzzle");
    add(4, "D3", "arena", 231, 119, 172, 172, "boss");
    add(4, "D4", "room", 383, 261, 53, 31, "item");
    add(4, "path_D0_D1", "corridor", 53, 122, 10, 36, "");
    add(4, "path_D1_D2", "corridor", 166, 60, 28, 10, "");
    add(4, "path_D2_D3", "corridor", 250, 100, 20, 20, "");
    add(4, "S4", "mark", 139, 148, 1, 1, "sc");
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
    for (i = 0; i < LAYOUT.length; i++) {
        var rec = LAYOUT[i];
        if (!want[rec.lv]) continue;
        var fn = CUSTOM[rec.k];
        if (fn) {
            fn(jobs, rec, ox, oy, oz);
        } else {
            compileDefault(jobs, rec, ox, oy, oz);
        }
    }
}

function compileDefault(jobs, rec, ox, oy, oz) {
    var k = rec.k;
    if (k == "border") buildBorder(jobs, rec, ox, oy, oz);
    else if (k == "path") buildPath(jobs, rec, ox, oy, oz);
    else if (k == "corridor") buildCorridor(jobs, rec, ox, oy, oz);
    else if (k == "shaft") buildShaft(jobs, rec, ox, oy, oz);
    else if (k == "mark") buildMark(jobs, rec, ox, oy, oz);
    else if (k == "tower") buildTower(jobs, rec, ox, oy, oz);
    else buildBoxRoom(jobs, rec, ox, oy, oz);
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
    if (KIND_FLOOR[rec.k]) return KIND_FLOOR[rec.k];
    return themeOf(rec.lv).floor;
}

function buildBoxRoom(jobs, rec, ox, oy, oz) {
    var x1 = ox + rec.x;
    var z1 = oz + rec.z;
    var x2 = x1 + rec.w - 1;
    var z2 = z1 + rec.d - 1;
    var y0 = floorY(rec.lv, oy);
    var h = roomH(rec.lv);
    var th = themeOf(rec.lv);
    enqueueFill(jobs, x1, y0, z1, x2, y0, z2, floorBlock(rec));
    enqueueFill(jobs, x1, y0 + 1, z1, x2, y0 + h - 1, z1, th.wall);
    enqueueFill(jobs, x1, y0 + 1, z2, x2, y0 + h - 1, z2, th.wall);
    enqueueFill(jobs, x1, y0 + 1, z1, x1, y0 + h - 1, z2, th.wall);
    enqueueFill(jobs, x2, y0 + 1, z1, x2, y0 + h - 1, z2, th.wall);
    if (PLACE_CEILING) enqueueFill(jobs, x1, y0 + h, z1, x2, y0 + h, z2, th.ceil);
    if (rec.mk) enqueueSet(jobs, Math.floor((x1 + x2) / 2), y0 + 1, Math.floor((z1 + z2) / 2), MARK[rec.mk] || MARK.npc);
}

function buildPath(jobs, rec, ox, oy, oz) {
    var x1 = ox + rec.x;
    var z1 = oz + rec.z;
    var x2 = x1 + rec.w - 1;
    var z2 = z1 + rec.d - 1;
    var y0 = floorY(rec.lv, oy);
    enqueueFill(jobs, x1, y0, z1, x2, y0, z2, KIND_FLOOR.path);
}

function buildCorridor(jobs, rec, ox, oy, oz) {
    var x1 = ox + rec.x;
    var z1 = oz + rec.z;
    var x2 = x1 + rec.w - 1;
    var z2 = z1 + rec.d - 1;
    var y0 = floorY(rec.lv, oy);
    var th = themeOf(rec.lv);
    var h = 5;
    enqueueFill(jobs, x1, y0, z1, x2, y0, z2, th.floor);
    enqueueFill(jobs, x1, y0 + 1, z1, x2, y0 + h - 1, z1, th.wall);
    enqueueFill(jobs, x1, y0 + 1, z2, x2, y0 + h - 1, z2, th.wall);
    enqueueFill(jobs, x1, y0 + 1, z1, x1, y0 + h - 1, z2, th.wall);
    enqueueFill(jobs, x2, y0 + 1, z1, x2, y0 + h - 1, z2, th.wall);
    if (PLACE_CEILING) enqueueFill(jobs, x1, y0 + h, z1, x2, y0 + h, z2, th.ceil);
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
