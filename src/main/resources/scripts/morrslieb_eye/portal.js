// portal.js — портал ивента «Око Моррслиба». Отдельный NPC, не Око и не Кигор:
// в его скрипт кладётся только этот файл. Оку и Кигору его не подключать: interact и tick затрут их функции.
// ПКМ: через DELAY_TICKS (30 секунд) игрок встаёт в случайную точку из POINTS.
// Пока ждёт, в чат пишется, сколько секунд осталось до переноса в локацию.
// Шаг, прыжок или урон срывают перенос. Поворот на месте — нет. Сорвался — кулдауна нет, можно нажать снова.
// Кулдаун свой у каждого игрока, COOLDOWN_TICKS (5 минут) с момента переноса, не с ПКМ. Лежит в данных мира,
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

// 30 секунд ожидания. Кулдаун 5 минут — только после удачного переноса. Тик = 1/20 секунды.
var DELAY_TICKS = 600;
var COOLDOWN_TICKS = 6000;
// Сколько осталось — в чат: каждые TELL_EVERY секунд, последние TELL_LAST — каждую секунду.
// TELL_EVERY = 1 пишет каждую секунду.
var TELL_EVERY = 5;
var TELL_LAST = 5;
// Дальше этого от точки ПКМ — уже движение. Поворот головы точку не меняет.
var MOVE_R = 0.3;

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
    var row = { id: id, due: now + DELAY_TICKS, told: waitSec };
    stamp(row, player);
    queue.push(row);
    writeQueue(e.npc, queue);
    player.message("§aОко Моррслиба смотрит на тебя. До переноса в локацию " + waitSec + " с. Не двигайся и не получай урон.");
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
        var player = findPlayer(world, row.id);
        var why = channelFate(player, row);
        if (why == "hurt" || why == "move") {
            changed = true;
            breakTell(player, why);
            continue;
        }
        if (why == "update") changed = true;
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

// Игрок ушёл из мира или уже мёртв — перенос сгорает, кулдаун не ставится.
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
    var now = Number(world.getTotalTime());
    putCd(world, id, now + COOLDOWN_TICKS, now);
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
    var player = findPlayer(world, id);
    if (player == null || !player.isAlive()) return;
    try { player.message(text); } catch (eMsg) {}
}

function findPlayer(world, id) {
    var player = null;
    try { player = world.getEntity(id); } catch (err) { return null; }
    if (player == null) return null;
    if (typeof player.getType == "function" && player.getType() != 1) return null;
    return player;
}

// Точка и жизни на момент ПКМ. Урон — падение жизней или поглощения, либо новые кадры неуязвимости.
function stamp(row, player) {
    var v = vitals(player);
    row.x = player.getX();
    row.y = player.getY();
    row.z = player.getZ();
    row.hp = v.hp;
    row.abs = v.abs;
    row.iframe = v.iframe;
}

function vitals(player) {
    var hp = Number(player.getHealth());
    var abs = 0;
    var iframe = 0;
    try {
        var mc = player.getMCEntity();
        if (mc != null) {
            abs = Number(mc.getAbsorptionAmount());
            iframe = Number(mc.invulnerableTime);
        }
    } catch (err) {}
    if (isNaN(hp)) hp = 0;
    if (isNaN(abs)) abs = 0;
    if (isNaN(iframe)) iframe = 0;
    return { hp: hp, abs: abs, iframe: iframe };
}

// null — всё тихо. "update" — записать новые жизни. "move" / "hurt" — снять с очереди.
function channelFate(player, row) {
    if (player == null) return null;
    if (!player.isAlive()) return "hurt";
    if (row.x == null) {
        stamp(row, player);
        return "update";
    }
    var v = vitals(player);
    // Поглощение само спадает, когда кончается эффект. Урон — это удар: жизни упали, кадры
    // неуязвимости выросли, или поглощение снял удар, у которого кадры ещё висят.
    if (v.hp < row.hp - 0.05 || v.iframe > row.iframe) return "hurt";
    if (v.abs < row.abs - 0.05 && v.iframe > 0) return "hurt";
    var dx = player.getX() - row.x, dy = player.getY() - row.y, dz = player.getZ() - row.z;
    if (dx * dx + dy * dy + dz * dz > MOVE_R * MOVE_R) return "move";
    var updated = false;
    if (v.hp > row.hp) { row.hp = v.hp; updated = true; }
    if (v.abs > row.abs) { row.abs = v.abs; updated = true; }
    if (v.iframe != row.iframe) { row.iframe = v.iframe; updated = true; }
    return updated ? "update" : null;
}

function breakTell(player, why) {
    if (player == null) return;
    try {
        player.message(why == "hurt" ? "§cПеренос сорван: ты получил урон." : "§cПеренос сорван: ты сдвинулся.");
    } catch (err) {}
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
        if (bits[0].length == 0 || isNaN(due)) continue;
        var row = { id: bits[0], due: due, told: isNaN(told) ? -1 : told, x: null };
        if (bits.length >= 9) {
            row.x = parseFloat(bits[3]);
            row.y = parseFloat(bits[4]);
            row.z = parseFloat(bits[5]);
            row.hp = parseFloat(bits[6]);
            row.abs = parseFloat(bits[7]);
            row.iframe = parseInt(bits[8], 10);
            if (isNaN(row.x) || isNaN(row.hp) || isNaN(row.iframe)) row.x = null;
        }
        out.push(row);
    }
    return out;
}

function writeQueue(npc, queue) {
    var parts = [];
    var i;
    for (i = 0; i < queue.length; i++) parts.push(queueText(queue[i]));
    npc.getStoreddata().put(QUEUE_KEY, parts.join(";"));
}

function queueText(row) {
    var text = row.id + ":" + row.due + ":" + row.told;
    if (row.x == null) return text;
    return text + ":" + row.x.toFixed(2) + ":" + row.y.toFixed(2) + ":" + row.z.toFixed(2)
        + ":" + row.hp.toFixed(2) + ":" + row.abs.toFixed(2) + ":" + row.iframe;
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
