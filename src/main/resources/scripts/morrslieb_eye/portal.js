// portal.js — портал ивента «Око Моррслиба». Отдельный NPC, не Око и не Кигор:
// в его скрипт кладётся только этот файл. Оку и Кигору его не подключать: interact и tick затрут их функции.
// ПКМ: через DELAY_TICKS (30 секунд) игрок встаёт в случайную точку из POINTS.
// Пока ждёт, в чат пишется, сколько секунд осталось до переноса в локацию.
// Кулдаун свой у каждого игрока, COOLDOWN_TICKS (5 минут) с момента ПКМ. Лежит в данных мира,
// поэтому несколько таких NPC делят один кулдаун и он не сбрасывается выходом.
// Очередь переноса — на этом NPC. Чанк NPC должен быть загружен, иначе 30 секунд не оттикают.

var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();

// Куда кидать. Координаты ног: x/z по центру блока — n+0.5. Пустой список портал не открывает.
var POINTS = [
    [-484009, 80, -37116],
    [-484155, 88, -37037],
    [-484155, 88, -37200],
    [-484155, 88, -37037],
    [-484068, 83, -36788]
];

// 30 секунд ожидания, 5 минут кулдауна. Тик = 1/20 секунды.
var DELAY_TICKS = 600;
var COOLDOWN_TICKS = 6000;
// Сколько осталось — в чат: каждые TELL_EVERY секунд, последние TELL_LAST — каждую секунду.
// TELL_EVERY = 1 пишет каждую секунду.
var TELL_EVERY = 5;
var TELL_LAST = 5;

var TELEPORT_SOUND = "minecraft:entity.enderman.teleport";
var QUEUE_KEY = "mo_portal_q";
var CD_KEY = "mo_portal_cd";

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
        player.message("§aПеренос уже идёт. Осталось " + secondsLeft(pending.due, now) + " с.");
        return;
    }

    var until = cdUntil(world, id);
    if (until > now) {
        player.message("§7Око отвернулось. Подожди ещё " + secondsLeft(until, now) + " с.");
        return;
    }

    if (POINTS.length == 0) {
        player.message("§7Портал ещё не отмечен.");
        log("morrslieb: впиши POINTS в portal.js");
        return;
    }

    var waitSec = secondsLeft(now + DELAY_TICKS, now);
    queue.push({ id: id, due: now + DELAY_TICKS, told: waitSec });
    writeQueue(e.npc, queue);
    putCd(world, id, now + COOLDOWN_TICKS, now);
    player.message("§aОко Моррслиба смотрит на тебя. До переноса в локацию " + waitSec + " с.");
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
            var sec = secondsLeft(row.due, now);
            if (row.told != sec && shouldTell(sec)) {
                tellPlayer(world, row.id, "§aДо переноса в локацию осталось " + sec + " с.");
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

// Игрок ушёл из мира или уже мёртв — перенос сгорает, кулдаун с ПКМ остаётся.
function sendPlayer(world, id) {
    if (POINTS.length == 0) return;
    var player = null;
    try { player = world.getEntity(id); } catch (err) { return; }
    if (player == null || !player.isAlive()) return;
    if (typeof player.getType == "function" && player.getType() != 1) return;

    var point = POINTS[Math.floor(Math.random() * POINTS.length)];
    var x = point[0], y = point[1], z = point[2];
    try {
        world.playSoundAt(NpcAPI.getIPos(player.getX(), player.getY(), player.getZ()), TELEPORT_SOUND, 1.0, 1.0);
    } catch (eSnd) {}
    player.setPosition(x, y, z);
    try {
        world.playSoundAt(NpcAPI.getIPos(x, y, z), TELEPORT_SOUND, 1.0, 1.0);
    } catch (eSnd2) {}
    try { player.message("§aМоррслиб перенёс тебя."); } catch (eMsg) {}
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

function tellPlayer(world, id, text) {
    var player = null;
    try { player = world.getEntity(id); } catch (err) { return; }
    if (player == null || !player.isAlive()) return;
    if (typeof player.getType == "function" && player.getType() != 1) return;
    try { player.message(text); } catch (eMsg) {}
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
