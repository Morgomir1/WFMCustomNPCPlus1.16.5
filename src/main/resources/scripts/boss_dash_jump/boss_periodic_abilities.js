/**
 * Босс: периодический каст dash и jump_slam по очереди.
 *
 * Механика — Java AbilityAPI. Скрипт чередует скиллы: dash → jump_slam → dash → ...
 *
 * Установка: NPC → Advanced → Scripts → вставить код → /script reload
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");

var TIMER_ID = 702;
var TIMER_INTERVAL = 1;

// Пауза между попытками каста (тики; 20 ≈ 1 с)
var CAST_INTERVAL_TICKS = 100;

// Минимальная дистанция до цели для обоих скиллов
var MIN_RANGE = 3.0;
var MAX_RANGE = 24.0;
var REQUIRE_LINE_OF_SIGHT = true;

var NEXT_CAST_KEY = "bpa_next_cast";
var LAST_CAST_KEY = "bpa_last_cast";
var NEXT_ABILITY_KEY = "bpa_next_ability";

var ABILITY_SEQUENCE = ["dash", "jump_slam"];

function init(event) {
    try {
        var npc = event.npc;
        var data = npc.getStoreddata();
        if (!data.has(NEXT_ABILITY_KEY)) {
            data.put(NEXT_ABILITY_KEY, ABILITY_SEQUENCE[0]);
        }

        var timers = npc.getTimers();
        if (timers == null) return;
        try {
            if (typeof timers.has == "function" && timers.has(TIMER_ID)) return;
        } catch (eHas) {}
        try {
            if (typeof timers.stop == "function") timers.stop(TIMER_ID);
        } catch (eStop) {}
        if (typeof timers.forceStart == "function") {
            timers.forceStart(TIMER_ID, TIMER_INTERVAL, true);
        } else {
            timers.start(TIMER_ID, TIMER_INTERVAL, true);
        }
    } catch (e) {
        log("boss_periodic_abilities init ERROR: " + e);
    }
}

function timer(event) {
    if (event.id != TIMER_ID) return;
    try {
        var npc = event.npc;
        var world = npc.getWorld();
        var data = npc.getStoreddata();

        if (!npc.isAlive()) {
            AbilityAPI.cancel(npc);
            return;
        }

        if (AbilityAPI.isBusy(npc)) {
            return;
        }

        var now = world.getTotalTime();
        var nextCast = getInt(data, NEXT_CAST_KEY);
        if (now < nextCast) {
            return;
        }

        var target = getAggroTarget(npc);
        if (target == null) return;
        if (REQUIRE_LINE_OF_SIGHT && !canSeeTarget(npc, target)) return;

        var dist = distanceFlat(npc, target);
        if (dist < MIN_RANGE || dist > MAX_RANGE) return;

        var abilityId = String(data.get(NEXT_ABILITY_KEY));
        if (abilityId != "dash" && abilityId != "jump_slam") {
            abilityId = ABILITY_SEQUENCE[0];
        }

        var params = buildParams(abilityId);
        if (!AbilityAPI.start(npc, abilityId, target, params)) {
            return;
        }

        data.put(LAST_CAST_KEY, String(now));
        data.put(NEXT_CAST_KEY, String(now + CAST_INTERVAL_TICKS));
        data.put(NEXT_ABILITY_KEY, getNextInSequence(abilityId));
    } catch (e) {
        log("boss_periodic_abilities timer ERROR: " + e);
    }
}

function targetLost(event) {
    AbilityAPI.cancel(event.npc);
}

function died(event) {
    AbilityAPI.cancel(event.npc);
}

function getNextInSequence(currentId) {
    for (var i = 0; i < ABILITY_SEQUENCE.length; i++) {
        if (ABILITY_SEQUENCE[i] == currentId) {
            return ABILITY_SEQUENCE[(i + 1) % ABILITY_SEQUENCE.length];
        }
    }
    return ABILITY_SEQUENCE[0];
}

function buildParams(abilityId) {
    if (abilityId == "dash") {
        return AbilityAPI.params(
            "damage", 10.0,
            "distance", 16.0,
            "chargeTicks", 10,
            "activeTicks", 7
        );
    }
    return AbilityAPI.params(
        "damage", 14.0,
        "arcHeight", 6.0,
        "landRadius", 2.8,
        "chargeTicks", 12,
        "activeTicks", 9
    );
}

function getAggroTarget(npc) {
    try {
        var target = npc.getAttackTarget();
        if (target == null || !target.isAlive()) return null;
        return target;
    } catch (e) {
        return null;
    }
}

function canSeeTarget(npc, target) {
    try {
        if (typeof npc.canSeeEntity == "function") return npc.canSeeEntity(target);
    } catch (e1) {}
    try {
        if (typeof npc.canNpcSee == "function") return npc.canNpcSee(target);
    } catch (e2) {}
    return true;
}

function distanceFlat(a, b) {
    var dx = a.getX() - b.getX();
    var dz = a.getZ() - b.getZ();
    return Math.sqrt(dx * dx + dz * dz);
}

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}
