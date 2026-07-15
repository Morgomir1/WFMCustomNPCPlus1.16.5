// Scripted Block: при появлении Survival/Adventure-игрока рядом — вооружает
// EntityCloneStructureSpawner (arm), клоны сам не спавнит.
//
// Установка:
// 1. Поставь Scripted Block в структуру рядом со спавнерами.
// 2. Открой GUI блока → вкладка Tick → вставь этот скрипт целиком
//    (если GUI глючит на длинной вставке — вставляй частями; после патча GuiTextArea обычно ок).
// 3. Включи Scripts Enabled на блоке → сохрани.
// 4. Спавнеры при ручной/структурной постановке UNARMED; при заходе Survival-игрока
//    хелпер вызовет arm(). Спавн клона — когда рядом нет creative (радиус 16 у спавнера).
//
// Логи сервера (ищи "CloneStructureSpawnerHelper" / "clone_spawner_arm_block"):
//  - no playable player → рядом нет survival/adventure
//  - no spawners within … → сущность спавнера не найдена (дальность / чанк)
//  - armed N spawner(s) → arm() прошёл

var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();
var CloneSpawnerHelper = Java.type("noppes.npcs.script.CloneStructureSpawnerHelper");

// =========================
// НАСТРОЙКИ
// =========================
var PLAYER_RANGE = 32;
var SPAWNER_RANGE = 48;
var COOLDOWN_TICKS = 100;
var LOG_IDLE = true;

var CD_KEY = "clone_spawner_arm_cd";
var LAST_LOG_KEY = "clone_spawner_arm_last_log";

function tick(event) {
    var block = event.block;
    if (block == null) {
        log("clone_spawner_arm_block: event.block is null");
        return;
    }
    var world = block.getWorld();
    var data = block.getStoreddata();
    var now = world.getTotalTime();

    if (now < getInt(data, CD_KEY)) {
        return;
    }

    var x = block.getX() + 0.5;
    var y = block.getY() + 0.5;
    var z = block.getZ() + 0.5;

    if (!CloneSpawnerHelper.hasPlayablePlayerNearby(world, x, y, z, PLAYER_RANGE)) {
        maybeLogIdle(data, now, "no playable (survival/adventure) player within "
            + PLAYER_RANGE + " at " + x + "," + y + "," + z);
        return;
    }

    data.put(CD_KEY, String(now + COOLDOWN_TICKS));
    var armed = CloneSpawnerHelper.armNearby(world, x, y, z, SPAWNER_RANGE);
    log("clone_spawner_arm_block: tick armed=" + armed
        + " at " + block.getX() + "," + block.getY() + "," + block.getZ());
}

function maybeLogIdle(data, now, msg) {
    if (!LOG_IDLE) {
        return;
    }
    var last = getInt(data, LAST_LOG_KEY);
    if (now - last < 200) {
        return;
    }
    data.put(LAST_LOG_KEY, String(now));
    log("clone_spawner_arm_block: " + msg);
}

function getInt(data, key) {
    if (!data.has(key)) {
        return 0;
    }
    return parseInt(String(data.get(key)), 10);
}
