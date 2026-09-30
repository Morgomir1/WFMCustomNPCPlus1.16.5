/**
 * CustomNPCs 1.16.5 — скрипт NPC (вкладка Scripts).
 *
 * После смерти этого NPC запоминает игроков в сферическом радиусе RADIUS
 * от босса, ждёт DELAY_SECONDS секунд и телепортирует только их
 * на DEST_X/Y/Z. Остальной сервер не трогает.
 *
 * Как поставить:
 * 1. Откройте NPC → Advanced → Scripts
 * 2. Включите скрипты, язык JavaScript
 * 3. Вставьте этот файл целиком
 *
 * Чтобы отложенный ТП сработал, NPC должен остаться в мире
 * (включён респавн / труп не удаляется раньше DELAY_SECONDS).
 */

// Куда телепортировать игроков
var DEST_X = 0.5;
var DEST_Y = 64.0;
var DEST_Z = 0.5;

// Через сколько секунд после смерти сделать ТП (можно дробное, например 0.5)
var DELAY_SECONDS = 30;

// Только игроки в этом радиусе от босса в момент смерти
var RADIUS = 10;
var PLAYER_TYPE = 1;

// Свой ключ, если этот скрипт висит на нескольких NPC сразу
var TASK_KEY = "cnpc_death_delayed_tp";
var TIMER_ID = 33731;

function died(event) {
    var npc = event.npc;
    var world = npc.getWorld();
    var ids = collectNearbyPlayerIds(npc);
    if (DELAY_SECONDS <= 0) {
        teleportByIds(world, ids);
        return;
    }
    var ticks = Math.ceil(DELAY_SECONDS * 20);
    var data = world.getTempdata();
    data.put(TASK_KEY, world.getTotalTime() + ticks);
    data.put(TASK_KEY + "_ids", ids.join(","));
    npc.getTimers().forceStart(TIMER_ID, ticks, false);
}

function timer(event) {
    if (event.id == TIMER_ID) {
        tryTeleport(event.npc.getWorld());
    }
}

function tick(event) {
    tryTeleport(event.npc.getWorld());
}

function init(event) {
    tryTeleport(event.npc.getWorld());
}

function tryTeleport(world) {
    var data = world.getTempdata();
    if (!data.has(TASK_KEY)) {
        return;
    }
    if (world.getTotalTime() < data.get(TASK_KEY)) {
        return;
    }
    var raw = data.get(TASK_KEY + "_ids");
    data.remove(TASK_KEY);
    data.remove(TASK_KEY + "_ids");
    teleportByIds(world, ("" + (raw || "")).split(","));
}

function collectNearbyPlayerIds(npc) {
    var world = npc.getWorld();
    var nx = npc.getX();
    var ny = npc.getY();
    var nz = npc.getZ();
    var radiusSq = RADIUS * RADIUS;
    var nearby = world.getNearbyEntities(
        npc.getBlockX(),
        npc.getBlockY(),
        npc.getBlockZ(),
        RADIUS,
        PLAYER_TYPE
    );
    var ids = [];
    var i;
    var player;
    var dx;
    var dy;
    var dz;
    for (i = 0; i < nearby.length; i++) {
        player = nearby[i];
        dx = player.getX() - nx;
        dy = player.getY() - ny;
        dz = player.getZ() - nz;
        if (dx * dx + dy * dy + dz * dz <= radiusSq) {
            ids.push(player.getUUID());
        }
    }
    return ids;
}

function teleportByIds(world, ids) {
    var wanted = {};
    var i;
    var id;
    for (i = 0; i < ids.length; i++) {
        id = ids[i];
        if (id) {
            wanted[id] = true;
        }
    }
    var players = world.getAllPlayers();
    for (i = 0; i < players.length; i++) {
        if (wanted[players[i].getUUID()]) {
            players[i].setPosition(DEST_X, DEST_Y, DEST_Z);
        }
    }
}
