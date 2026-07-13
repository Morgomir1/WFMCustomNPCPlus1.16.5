/**
 * Grave Guard: тяжёлая defensive-абилкa для ближнего боя.
 * Механика — Java AbilityAPI. JS отвечает только за кулдаун и момент каста.
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");

var TIMER_ID = 794;
var NEXT_CAST_KEY = "gg_next_cast";
var COOLDOWN_KEY = "gg_cd_barrow_sentinel";
var ABILITY_ID = "barrow_sentinel";
var CAST_INTERVAL = 70;
var ABILITY_COOLDOWN = 110;

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
    if (dist > 5.0) return;

    var started = AbilityAPI.start(npc, ABILITY_ID, target, AbilityAPI.params(
        "distance", dist > 2.8 ? 2.2 : 1.6,
        "chargeTicks", dist < 2.2 ? 10 : 12,
        "activeTicks", 5,
        "damage", 11.0,
        "radius", 3.2,
        "coneHalfAngle", 52.0,
        "knockback", 0.8,
        "knockbackY", 0.15,
        "effectType", "slowness",
        "effectDuration", 10,
        "effectAmplifier", 0,
        "executeHpThreshold", 0.35,
        "executeBonusDamage", 7.0
    ));
    if (!started) return;

    data.put(NEXT_CAST_KEY, String(now + CAST_INTERVAL));
    data.put(COOLDOWN_KEY, String(now + ABILITY_COOLDOWN));

    if (Math.random() < 0.4) {
        npc.say("§8Стой... и умри.");
    }
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
