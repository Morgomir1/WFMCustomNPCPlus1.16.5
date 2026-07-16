// Scripted Block: при появлении Survival/Adventure-игрока рядом — вооружает
// EntityCloneStructureSpawner (arm + немедленный trySpawnNow).
//
// Установка:
// 1. Поставь Scripted Block в структуру рядом со спавнерами.
// 2. Открой GUI блока → вкладка Tick → вставь этот скрипт целиком
//    (если GUI глючит на длинной вставке — вставляй частями; после патча GuiTextArea обычно ок).
// 3. Включи Scripts Enabled на блоке → сохрани.
// 4. Спавнеры при ручной/структурной постановке UNARMED; при заходе Survival/Adventure
//    хелпер вызовет arm() + trySpawnNow(). Спавн клона — когда рядом нет CREATIVE
//    по GameType (не abilities.instabuild; важно для Arclight/Velocity).
// 5. После успешного спавна сущность остаётся с hasSpawned=true и больше не спавнится.
//    armNearby пропускает hasSpawned; freshlyArmed считает только UNARMED без hasSpawned.
//    Creative Shift+пустая рука на спавнере сбрасывает SPAWNED для повторного теста.
//
// Логи сервера (ищи "CloneStructureSpawnerHelper" / "clone_spawner_arm_block"):
//  - no playable … | players=… → рядом нет survival/adventure (смотри gt=/instabuild=)
//  - no spawners within … → сущность спавнера не найдена (дальность / чанк)
//  - freshlyArmed=N spawnedNow=M alreadySpawned=K → сняли UNARMED / сразу заспавнили / уже SPAWNED
//  - already ARMED still waiting blockReason=… → почему моб ещё не вышел

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
        var detail = CloneSpawnerHelper.describeNearbyPlayers(world, x, y, z, PLAYER_RANGE);
        maybeLogIdle(data, now, "no playable (survival/adventure) player within "
            + PLAYER_RANGE + " at " + x + "," + y + "," + z + " | players=" + detail);
        return;
    }

    data.put(CD_KEY, String(now + COOLDOWN_TICKS));
    // armNearby skips hasSpawned; does not re-arm already-spawned spawners
    var freshlyArmed = CloneSpawnerHelper.armNearby(world, x, y, z, SPAWNER_RANGE);
    log("clone_spawner_arm_block: tick freshlyArmed=" + freshlyArmed
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
