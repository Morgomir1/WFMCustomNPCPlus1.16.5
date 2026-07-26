/**
 * Босс: dash и jump_slam по очереди, урон чуть выше на низком HP.
 * После абилки — стан 2 сек (стоит, не атакует, не кастует).
 * Механика — Java AbilityAPI.
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");
var AbilityCombatHelper = Java.type("noppes.npcs.abilities.AbilityCombatHelper");

// CNPC OnAttack: 0=Мстить, 3=Ничего
var RETALIATE_REVENGE = 0;
var RETALIATE_NONE = 3;

var TIMER_ID = 701;
var CAST_INTERVAL_TICKS = 100;
var STUN_TICKS = 30; // 2 секунды

var NEXT_CAST_KEY = "bdj_next_cast";
var NEXT_ABILITY_KEY = "bdj_next_ability";
var WAS_CASTING_KEY = "bdj_was_cast";
var STUN_END_KEY = "bdj_stun_end";
var BASE_SPEED_KEY = "bdj_base_speed";
var SEQUENCE = ["dash", "jump_slam"];

function init(event) {
    var npc = event.npc;
    var data = npc.getStoreddata();
    if (!data.has(NEXT_ABILITY_KEY)) {
        data.put(NEXT_ABILITY_KEY, SEQUENCE[0]);
    }
    data.put(WAS_CASTING_KEY, "0");
    data.put(STUN_END_KEY, "0");
    storeBaseSpeed(data, npc.getAi());
    startTimer(npc);
}

function timer(event) {
    if (event.id != TIMER_ID) return;

    var npc = event.npc;
    if (!npc.isAlive()) {
        AbilityAPI.cancel(npc);
        return;
    }

    var data = npc.getStoreddata();
    var ai = npc.getAi();
    var now = npc.getWorld().getTotalTime();
    storeBaseSpeed(data, ai);

    // Каждый тик: ловим конец абилки и держим стан (tick раз в 10 тиков — мало).
    updateCastState(npc, data, ai, now);

    if (AbilityAPI.isBusy(npc)) return;
    if (isStunned(data, now)) return;
    if (now < getInt(data, NEXT_CAST_KEY)) return;

    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) return;

    var abilityId = String(data.get(NEXT_ABILITY_KEY));
    if (abilityId != "dash" && abilityId != "jump_slam") {
        abilityId = SEQUENCE[0];
    }

    var lowHp = npc.getHealth() / npc.getMaxHealth() < 0.3;
    var started = AbilityAPI.start(npc, abilityId, target, buildParams(abilityId, lowHp));
    if (started) {
        data.put(WAS_CASTING_KEY, "1");
        data.put(NEXT_CAST_KEY, String(now + CAST_INTERVAL_TICKS));
        data.put(NEXT_ABILITY_KEY, nextAbility(abilityId));
    }
}

function targetLost(event) {
    var npc = event.npc;
    var data = npc.getStoreddata();
    var wasCasting = AbilityAPI.isBusy(npc) || String(data.get(WAS_CASTING_KEY)) == "1";
    AbilityAPI.cancel(npc);
    data.put(WAS_CASTING_KEY, "0");
    if (wasCasting) {
        beginStun(npc, data, npc.getAi(), npc.getWorld().getTotalTime());
    }
}

function died(event) {
    AbilityAPI.cancel(event.npc);
}

function updateCastState(npc, data, ai, now) {
    if (AbilityAPI.isBusy(npc)) {
        data.put(WAS_CASTING_KEY, "1");
        return;
    }

    if (String(data.get(WAS_CASTING_KEY)) == "1") {
        data.put(WAS_CASTING_KEY, "0");
        beginStun(npc, data, ai, now);
        return;
    }

    if (isStunned(data, now)) {
        applyStunStance(npc, data, ai);
        return;
    }

    if (getInt(data, STUN_END_KEY) > 0) {
        endStun(npc, data, ai);
    }
}

function isStunned(data, now) {
    var end = getInt(data, STUN_END_KEY);
    return end > 0 && now < end;
}

function beginStun(npc, data, ai, now) {
    data.put(STUN_END_KEY, String(now + STUN_TICKS));
    applyStunStance(npc, data, ai);
}

function applyStunStance(npc, data, ai) {
    try {
        ai.setRetaliateType(RETALIATE_NONE);
        ai.setWalkingSpeed(0);
    } catch (e) {}
    try {
        AbilityCombatHelper.stopNavigation(npc);
    } catch (e2) {}
    try {
        npc.setMotionX(0);
        npc.setMotionY(0);
        npc.setMotionZ(0);
    } catch (e3) {}
}

function endStun(npc, data, ai) {
    data.put(STUN_END_KEY, "0");
    try {
        ai.setRetaliateType(RETALIATE_REVENGE);
        ai.setWalkingSpeed(getBaseSpeed(data, ai));
    } catch (e) {}
}

function storeBaseSpeed(data, ai) {
    if (data.has(BASE_SPEED_KEY)) return;
    try {
        data.put(BASE_SPEED_KEY, String(ai.getWalkingSpeed()));
    } catch (e) {}
}

function getBaseSpeed(data, ai) {
    if (data.has(BASE_SPEED_KEY)) {
        return getInt(data, BASE_SPEED_KEY);
    }
    try {
        return ai.getWalkingSpeed();
    } catch (e) {
        return 5;
    }
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

function buildParams(abilityId, lowHp) {
    if (abilityId == "dash") {
        return AbilityAPI.params("telegraphColor", 0xC0FF3030,
            "damage", lowHp ? 14.0 : 10.0,
            "chargeTicks", 20,
            "hitRadius", 2.5,
            "distance", 16.0
        );
    }
    return AbilityAPI.params("telegraphColor", 0xC0FF3030,
        "damage", lowHp ? 18.0 : 14.0,
        "chargeTicks", 20,
        "arcHeight", lowHp ? 7.0 : 6.0,
        "maxRange", 16.0
    );
}

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}
