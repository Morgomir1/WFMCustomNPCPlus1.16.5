/**
 * Босс: только dash.
 * Механика — Java AbilityAPI (DashAbility + авто-telegraph line по distance).
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");

var TIMER_ID = 703;
var CAST_INTERVAL_TICKS = 600; // 30 сек
var NEXT_CAST_KEY = "bdo_next_cast";

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

    var data = npc.getStoreddata();
    var now = npc.getWorld().getTotalTime();
    if (now < getInt(data, NEXT_CAST_KEY)) return;

    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) return;

    var lowHp = npc.getHealth() / npc.getMaxHealth() < 0.3;
    AbilityAPI.start(npc, "dash", target, AbilityAPI.params(
        "telegraphColor", 0xC0FF3030,
        "damage", lowHp ? 14.0 : 10.0,
        "distance", 16.0,
        "chargeTicks", 40,
        "activeTicks", 7,
        "hitRadius", 2.5
    ));
    data.put(NEXT_CAST_KEY, String(now + CAST_INTERVAL_TICKS));
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
