/**
 * CustomNPCs 1.16.5 — скрипт NPC (вкладка Scripts).
 *
 * После смерти этого NPC ждёт DELAY_SECONDS секунд и телепортирует
 * всех онлайн-игроков на DEST_X/Y/Z (тот же мир, что у NPC).
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

// Свой ключ, если этот скрипт висит на нескольких NPC сразу
var TASK_KEY = "cnpc_death_delayed_tp";
var TIMER_ID = 33731;

function died(event) {
    var world = event.npc.getWorld();
    if (DELAY_SECONDS <= 0) {
        teleportAll(world);
        return;
    }
    var ticks = Math.ceil(DELAY_SECONDS * 20);
    world.getTempdata().put(TASK_KEY, world.getTotalTime() + ticks);
    event.npc.getTimers().forceStart(TIMER_ID, ticks, false);
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
    data.remove(TASK_KEY);
    teleportAll(world);
}

function teleportAll(world) {
    var players = world.getAllPlayers();
    var i;
    for (i = 0; i < players.length; i++) {
        players[i].setPosition(DEST_X, DEST_Y, DEST_Z);
    }
}
