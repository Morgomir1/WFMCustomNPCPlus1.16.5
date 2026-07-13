/**
 * Zombie: «Хватка мертвецов».
 * Механика — Java AbilityAPI. JS отвечает только за кулдаун и условия старта.
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");

var TIMER_ID = 811;
var NEXT_CAST_KEY = "z_next_cast";
var COOLDOWN_KEY = "z_cd_grasping_dead";
var ABILITY_ID = "grasping_dead";

var CAST_INTERVAL = 50;
var ABILITY_COOLDOWN = 120;

function init(event) {
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

    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) return;
    if (typeof npc.canSeeEntity == "function" && !npc.canSeeEntity(target)) return;

    var data = npc.getStoreddata();
    var now = npc.getWorld().getTotalTime();
    if (now < getInt(data, NEXT_CAST_KEY)) return;
    if (now < getInt(data, COOLDOWN_KEY)) return;

    var dist = flatDistance(npc, target);
    if (dist > 2.2) return; // только почти в упор

    var started = AbilityAPI.start(npc, ABILITY_ID, target, AbilityAPI.params(
        "chargeTicks", 5,
        "activeTicks", 34,
        "radius", 1.8,
        "effectType", "slowness",
        "effectDuration", 35,
        "effectAmplifier", 1,
        "damagePerTick", 0.0
    ));
    if (!started) return;

    data.put(NEXT_CAST_KEY, String(now + CAST_INTERVAL));
    data.put(COOLDOWN_KEY, String(now + ABILITY_COOLDOWN));
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

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}

function flatDistance(npc, target) {
    var dx = npc.getX() - target.getX();
    var dz = npc.getZ() - target.getZ();
    return Math.sqrt(dx * dx + dz * dz);
}

