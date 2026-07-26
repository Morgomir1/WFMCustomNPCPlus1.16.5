/**
 * Босс: dash и jump_slam по очереди.
 * Механика — Java AbilityAPI. Скрипт только запускает и чередует скиллы.
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");

var TIMER_ID = 702;
var CAST_INTERVAL_TICKS = 100;
var NEXT_CAST_KEY = "bpa_next_cast";
var NEXT_ABILITY_KEY = "bpa_next_ability";
var SEQUENCE = ["dash", "jump_slam"];

function init(event) {
    var data = event.npc.getStoreddata();
    if (!data.has(NEXT_ABILITY_KEY)) {
        data.put(NEXT_ABILITY_KEY, SEQUENCE[0]);
    }
    startTimer(event.npc);
}

function timer(event) {
    if (event.id != TIMER_ID) return;

    var npc = event.npc;
    if (!npc.isAlive()) {
        AbilityAPI.cancel(npc);
        return;
    }
    if (AbilityAPI.isBusy(npc)) return;

    var data = npc.getStoreddata();
    var now = npc.getWorld().getTotalTime();
    if (now < getInt(data, NEXT_CAST_KEY)) return;

    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) return;

    var abilityId = String(data.get(NEXT_ABILITY_KEY));
    if (abilityId != "dash" && abilityId != "jump_slam") {
        abilityId = SEQUENCE[0];
    }

    AbilityAPI.start(npc, abilityId, target, buildParams(abilityId));
    data.put(NEXT_CAST_KEY, String(now + CAST_INTERVAL_TICKS));
    data.put(NEXT_ABILITY_KEY, nextAbility(abilityId));
}

function targetLost(event) {
    AbilityAPI.cancel(event.npc);
}

function died(event) {
    AbilityAPI.cancel(event.npc);
}

function startTimer(npc) {
    var timers = npc.getTimers();
    if (timers == null) return;
    if (typeof timers.forceStart == "function") {
        timers.forceStart(TIMER_ID, 1, true);
    } else {
        timers.start(TIMER_ID, 1, true);
    }
}

function nextAbility(currentId) {
    return currentId == "dash" ? "jump_slam" : "dash";
}

function buildParams(abilityId) {
    if (abilityId == "dash") {
        return AbilityAPI.params("telegraphColor", 0xC0FF3030, 
            "damage", 10.0,
            "distance", 16.0,
            "chargeTicks", 10,
            "activeTicks", 7
        );
    }
    return AbilityAPI.params("telegraphColor", 0xC0FF3030, 
        "damage", 14.0,
        "arcHeight", 6.0,
        "landRadius", 2.8,
        "chargeTicks", 12,
        "activeTicks", 9,
        "maxRange", 16.0
    );
}

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}
