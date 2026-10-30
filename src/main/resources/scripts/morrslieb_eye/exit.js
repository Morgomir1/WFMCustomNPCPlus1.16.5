// exit.js — выход из зоны ивента «Око Моррслиба». Отдельный NPC внутри поля, не Око и не портал:
// в его скрипт — mo_core.js, затем этот файл. Оку, Кигору, порталу и скрипту игрока его не подключать:
// interact и tick затрут их функции, а тики игроков перестанут вести ивент.
// ПКМ в зоне поля: через DELAY_TICKS (30 секунд) игрок встаёт в точку EXIT.
// Пока ждёт, в чат пишется, сколько секунд осталось. Вышел из зоны раньше — перенос отменяется.
// В конце, уже на точке, забирается SHARD_PERCENT процентов осколков. 0 — не забирать.
// Кулдаун свой у каждого игрока, COOLDOWN_TICKS (5 минут) с момента ПКМ. Лежит в данных мира,
// поэтому несколько таких NPC делят один кулдаун и он не сбрасывается выходом. С кулдауном портала не связан.
// Очередь переноса — на этом NPC. Чанк NPC должен быть загружен, иначе 30 секунд не оттикают.

var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();

// Куда высаживать. Одна точка, координаты ног: x/z по центру блока — n+0.5. Пусто — выход закрыт.
var EXIT = [2, 65, 4];

// Свой круг, если выход не со всего поля. Пустой ZONE — центр и радиус берутся из mo_core.js (MO.FIELD, MO.FIELD_R).
var ZONE = [];
var ZONE_R = 0;

// 30 секунд ожидания, 5 минут кулдауна. Тик = 1/20 секунды.
var DELAY_TICKS = 600;
var COOLDOWN_TICKS = 6000;
// Сколько осталось — в чат: каждые TELL_EVERY секунд, последние TELL_LAST — каждую секунду.
// TELL_EVERY = 1 пишет каждую секунду.
var TELL_EVERY = 5;
var TELL_LAST = 5;
// Доля осколков в конце переноса, 0–100. 0 — не забирать.
// Дробь отбрасывается: 30% от 4 осколков — 1. Считаются инвентарь, мешки и шалкеры, как свет на поле.
var SHARD_PERCENT = 0;

var TELEPORT_SOUND = "minecraft:entity.enderman.teleport";
var QUEUE_KEY = "mo_exit_q";
var CD_KEY = "mo_exit_cd";
var EXIT_LOGGED = false;

function interact(e) {
    if (e.npc == null || e.player == null) return;
    e.setCanceled(true);

    var player = e.player;
    if (player.getGamemode() == 3) return;

    var world = e.npc.getWorld();
    var now = Number(world.getTotalTime());
    var id = String(player.getUUID());
    var queue = readQueue(e.npc);

    var pending = findRow(queue, id);
    if (pending != null) {
        player.message("§aВыход уже идёт. Осталось " + secondsLeft(pending.due, now) + " с.");
        return;
    }

    if (EXIT == null || EXIT.length < 3) {
        player.message("§7Выход ещё не отмечен.");
        log("morrslieb: впиши EXIT в exit.js");
        return;
    }

    if (!inEventZone(player)) {
        player.message("§7Око не видит тебя в зоне ивента.");
        return;
    }

    var until = cdUntil(world, id);
    if (until > now) {
        player.message("§7Око отвернулось. Подожди ещё " + secondsLeft(until, now) + " с.");
        return;
    }

    var waitSec = secondsLeft(now + DELAY_TICKS, now);
    queue.push({ id: id, due: now + DELAY_TICKS, told: waitSec });
    writeQueue(e.npc, queue);
    putCd(world, id, now + COOLDOWN_TICKS, now);
    var text = "§aОко Моррслиба закрывает глаз. До выхода из зоны " + waitSec + " с.";
    if (Number(SHARD_PERCENT) > 0) text += " В конце заберёт " + SHARD_PERCENT + "% осколков.";
    player.message(text);
    try { player.playSound("minecraft:block.portal.trigger", 0.6, 1.2); } catch (err) {}
}

function tick(e) {
    if (e.npc == null) return;
    var npc = e.npc;
    var world = npc.getWorld();
    var now = Number(world.getTotalTime());
    var queue = readQueue(npc);
    if (queue.length == 0) return;

    var keep = [];
    var changed = false;
    var i;
    for (i = 0; i < queue.length; i++) {
        var row = queue[i];
        if (now < row.due) {
            var player = playerById(world, row.id);
            if (player != null && !inEventZone(player)) {
                changed = true;
                try { player.message("§7Ты вышел из зоны ивента — перенос отменён."); } catch (err) {}
                continue;
            }
            var sec = secondsLeft(row.due, now);
            if (row.told != sec && shouldTell(sec)) {
                if (player != null) try { player.message("§aДо выхода из зоны осталось " + sec + " с."); } catch (err2) {}
                row.told = sec;
                changed = true;
            }
            keep.push(row);
            continue;
        }
        changed = true;
        sendPlayer(world, row.id);
    }
    if (changed) writeQueue(npc, keep);
}

// Игрок ушёл из мира, уже мёртв или вышел из зоны — перенос сгорает, кулдаун с ПКМ остаётся.
function sendPlayer(world, id) {
    if (EXIT == null || EXIT.length < 3) return;
    var player = playerById(world, id);
    if (player == null) return;
    if (!inEventZone(player)) {
        try { player.message("§7Ты вышел из зоны ивента — перенос отменён."); } catch (eMsg) {}
        return;
    }

    var x = EXIT[0], y = EXIT[1], z = EXIT[2];
    try {
        world.playSoundAt(NpcAPI.getIPos(player.getX(), player.getY(), player.getZ()), TELEPORT_SOUND, 1.0, 1.0);
    } catch (eSnd) {}
    player.setPosition(x, y, z);
    try {
        world.playSoundAt(NpcAPI.getIPos(x, y, z), TELEPORT_SOUND, 1.0, 1.0);
    } catch (eSnd2) {}
    var taken = takeShardShare(player);
    try {
        player.message("§aМоррслиб вывел тебя из зоны.");
        if (taken > 0) player.message("§cОсколков Моррслиба забрано: " + taken + ".");
    } catch (eMsg2) {}
}

function inEventZone(player) {
    if (player == null) return false;
    try {
        var center = (ZONE != null && ZONE.length >= 3) ? ZONE : MO.FIELD;
        var radius = ZONE_R > 0 ? ZONE_R : MO.FIELD_R;
        return moFlat(player, center[0], center[2]) <= radius;
    } catch (err) {
        exitLog("зона не прочитана, первым файлом положи mo_core.js: " + err);
        return false;
    }
}

// Забирает долю осколков уже после переноса. moTakeShards сам обновляет инвентарь игроку.
function takeShardShare(player) {
    var pct = Number(SHARD_PERCENT);
    if (!(pct > 0)) return 0;
    if (pct > 100) pct = 100;
    var n = 0;
    try {
        n = moShards(player);
    } catch (err) {
        exitLog("осколки не сосчитаны, первым файлом положи mo_core.js: " + err);
        return 0;
    }
    if (n <= 0) return 0;
    var k = pct >= 100 ? n : Math.floor(n * pct / 100);
    if (k <= 0) return 0;
    try {
        return moTakeShards(player, k);
    } catch (err2) {
        exitLog("осколки не забраны: " + err2);
        return 0;
    }
}

function exitLog(text) {
    if (EXIT_LOGGED) return;
    EXIT_LOGGED = true;
    log("morrslieb exit: " + text);
}

function playerById(world, id) {
    var player = null;
    try { player = world.getEntity(id); } catch (err) { return null; }
    if (player == null || !player.isAlive()) return null;
    if (typeof player.getType == "function" && player.getType() != 1) return null;
    return player;
}

function secondsLeft(until, now) {
    var sec = Math.ceil((until - now) / 20);
    return sec < 1 ? 1 : sec;
}

function shouldTell(sec) {
    if (sec <= TELL_LAST) return true;
    if (TELL_EVERY <= 1) return true;
    return sec % TELL_EVERY == 0;
}

function findRow(queue, id) {
    var i;
    for (i = 0; i < queue.length; i++) if (queue[i].id == id) return queue[i];
    return null;
}

function readQueue(npc) {
    var data = npc.getStoreddata();
    var raw = data.has(QUEUE_KEY) ? String(data.get(QUEUE_KEY)) : "";
    var out = [];
    if (raw == null || raw.length == 0) return out;
    var parts = String(raw).split(";");
    var i;
    for (i = 0; i < parts.length; i++) {
        var bits = parts[i].split(":");
        if (bits.length < 2) continue;
        var due = parseInt(bits[1], 10);
        var told = bits.length >= 3 ? parseInt(bits[2], 10) : -1;
        if (bits[0].length > 0 && !isNaN(due)) out.push({ id: bits[0], due: due, told: isNaN(told) ? -1 : told });
    }
    return out;
}

function writeQueue(npc, queue) {
    var parts = [];
    var i;
    for (i = 0; i < queue.length; i++) parts.push(queue[i].id + ":" + queue[i].due + ":" + queue[i].told);
    npc.getStoreddata().put(QUEUE_KEY, parts.join(";"));
}

function cdUntil(world, id) {
    var map = readCd(world);
    return map[id] == null ? 0 : map[id];
}

function putCd(world, id, until, now) {
    var map = readCd(world);
    map[id] = until;
    var parts = [];
    var key;
    for (key in map) {
        if (!map.hasOwnProperty(key)) continue;
        if (map[key] <= now) continue;
        parts.push(key + ":" + map[key]);
    }
    world.getStoreddata().put(CD_KEY, parts.join(";"));
}

function readCd(world) {
    var data = world.getStoreddata();
    var raw = data.has(CD_KEY) ? String(data.get(CD_KEY)) : "";
    var rows = parseRows(raw);
    var map = {};
    var i;
    for (i = 0; i < rows.length; i++) map[rows[i].id] = rows[i].due;
    return map;
}

// Строка «uuid:число;…». У uuid дефисы, число после последнего двоеточия.
function parseRows(raw) {
    var out = [];
    if (raw == null || raw.length == 0) return out;
    var parts = String(raw).split(";");
    var i;
    for (i = 0; i < parts.length; i++) {
        var row = parts[i];
        var colon = row.lastIndexOf(":");
        if (colon <= 0) continue;
        var id = row.substring(0, colon);
        var due = parseInt(row.substring(colon + 1), 10);
        if (id.length > 0 && !isNaN(due)) out.push({ id: id, due: due });
    }
    return out;
}
