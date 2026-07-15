// Scripted Block: при появлении Survival-игрока рядом — вооружает
// EntityCloneStructureSpawner (arm), клоны сам не спавнит.
//
// Установка:
// 1. Поставь Scripted Block в структуру рядом со спавнерами.
// 2. Открой GUI блока → вкладка Tick → вставь этот скрипт целиком.
// 3. Сохрани блок → включи структуру. В креативе спавнеры остаются UNARMED;
//    при заходе Survival-игрока скрипт вызовет arm() у ближайших спавнеров.

var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();
var EntitiesType = Java.type("noppes.npcs.api.constants.EntitiesType");
var EntityCloneStructureSpawner = Java.type("noppes.npcs.entity.EntityCloneStructureSpawner");

// =========================
// НАСТРОЙКИ
// =========================
var PLAYER_RANGE = 32;
var SPAWNER_RANGE = 48;
var COOLDOWN_TICKS = 100;

var CD_KEY = "clone_spawner_arm_cd";

function tick(event) {
    var block = event.block;
    var world = block.getWorld();
    var data = block.getStoreddata();
    var now = world.getTotalTime();

    if (now < getInt(data, CD_KEY)) {
        return;
    }

    var pos = NpcAPI.getIPos(block.getX(), block.getY(), block.getZ());
    if (!hasSurvivalPlayerNearby(world, pos)) {
        return;
    }

    data.put(CD_KEY, String(now + COOLDOWN_TICKS));
    armNearbySpawners(world, pos);
}

function hasSurvivalPlayerNearby(world, pos) {
    var players = world.getNearbyEntities(pos, PLAYER_RANGE, EntitiesType.PLAYER);
    if (players == null) {
        return false;
    }
    var i;
    for (i = 0; i < players.length; i++) {
        var p = players[i];
        if (p != null && typeof p.getGamemode == "function" && p.getGamemode() === 0) {
            return true;
        }
    }
    return false;
}

function armNearbySpawners(world, pos) {
    var list = world.getNearbyEntities(pos, SPAWNER_RANGE, EntitiesType.ANY);
    if (list == null) {
        return;
    }
    var i;
    for (i = 0; i < list.length; i++) {
        var ent = list[i];
        if (ent == null) {
            continue;
        }
        try {
            var mc = ent.getMCEntity();
            if (mc != null && mc instanceof EntityCloneStructureSpawner) {
                mc.arm();
            }
        } catch (e) {
            log("clone_spawner_arm_block: arm failed: " + e);
        }
    }
}

function getInt(data, key) {
    if (!data.has(key)) {
        return 0;
    }
    return parseInt(String(data.get(key)), 10);
}
