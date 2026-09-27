/**
 * Босс: dash и jump_slam по очереди (как у крысоогра).
 * Без стана после абилки — сразу снова ходит и бьёт.
 * Механика — Java AbilityAPI. Все х-ки ниже.
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");

// =====================================================
// НАСТРОЙКИ
// =====================================================

var TIMER_ID = 704;

/** Пауза между окончанием одной абилки и стартом следующей (тики, 20 = 1 сек). */
var CAST_INTERVAL_TICKS = 100;

/** Порядок кастов. Можно поменять местами или оставить одно умение. */
var SEQUENCE = ["dash", "jump_slam"];

/** HP / maxHP ниже порога → *_LOW_HP. 0 = никогда не включать «низкое HP». */
var LOW_HP_RATIO = 0.3;

/** 1 = telegraph на заряд, 0 = без зоны. */
var TELEGRAPH = 1;
var TELEGRAPH_COLOR = 0xC0FF3030;

// ----- Рывок (dash) -----
var DASH_DISTANCE = 16.0;
var DASH_DISTANCE_LOW_HP = 16.0;
var DASH_CHARGE_TICKS = 20;
var DASH_CHARGE_TICKS_LOW_HP = 20;
var DASH_ACTIVE_TICKS = 7;
var DASH_ACTIVE_TICKS_LOW_HP = 7;
var DASH_DAMAGE = 10.0;
var DASH_DAMAGE_LOW_HP = 14.0;
var DASH_KNOCKBACK = 1.8;
var DASH_KNOCKBACK_LOW_HP = 1.8;
var DASH_KNOCKBACK_Y = 0.35;
var DASH_KNOCKBACK_Y_LOW_HP = 0.35;
var DASH_HIT_RADIUS = 2.5;
var DASH_HIT_RADIUS_LOW_HP = 2.5;

// ----- Прыжок (jump_slam) -----
var JUMP_CHARGE_TICKS = 20;
var JUMP_CHARGE_TICKS_LOW_HP = 20;
var JUMP_ACTIVE_TICKS = 9;
var JUMP_ACTIVE_TICKS_LOW_HP = 9;
var JUMP_DAMAGE = 14.0;
var JUMP_DAMAGE_LOW_HP = 18.0;
var JUMP_KNOCKBACK = 2.2;
var JUMP_KNOCKBACK_LOW_HP = 2.2;
var JUMP_KNOCKBACK_Y = 0.55;
var JUMP_KNOCKBACK_Y_LOW_HP = 0.55;
var JUMP_LAND_RADIUS = 2.8;
var JUMP_LAND_RADIUS_LOW_HP = 2.8;
var JUMP_ARC_HEIGHT = 6.0;
var JUMP_ARC_HEIGHT_LOW_HP = 7.0;
var JUMP_MAX_RANGE = 16.0;
var JUMP_MAX_RANGE_LOW_HP = 16.0;

// =====================================================
// storeddata
// =====================================================
var NEXT_CAST_KEY = "bdjns_next_cast";
var NEXT_ABILITY_KEY = "bdjns_next_ability";

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
    if (!isKnownAbility(abilityId)) {
        abilityId = SEQUENCE[0];
    }

    var lowHp = isLowHp(npc);
    var started = AbilityAPI.start(npc, abilityId, target, buildParams(abilityId, lowHp));
    if (started) {
        data.put(NEXT_CAST_KEY, String(now + CAST_INTERVAL_TICKS));
        data.put(NEXT_ABILITY_KEY, nextAbility(abilityId));
    }
}

function targetLost(event) {
    AbilityAPI.cancel(event.npc);
}

function died(event) {
    AbilityAPI.cancel(event.npc);
}

function isLowHp(npc) {
    if (LOW_HP_RATIO <= 0) return false;
    var maxHp = npc.getMaxHealth();
    if (maxHp <= 0) return false;
    return npc.getHealth() / maxHp < LOW_HP_RATIO;
}

function pick(lowHp, normalVal, lowVal) {
    return lowHp ? lowVal : normalVal;
}

function buildParams(abilityId, lowHp) {
    if (abilityId == "dash") {
        return AbilityAPI.params(
            "telegraph", TELEGRAPH,
            "telegraphColor", TELEGRAPH_COLOR,
            "distance", pick(lowHp, DASH_DISTANCE, DASH_DISTANCE_LOW_HP),
            "chargeTicks", pick(lowHp, DASH_CHARGE_TICKS, DASH_CHARGE_TICKS_LOW_HP),
            "activeTicks", pick(lowHp, DASH_ACTIVE_TICKS, DASH_ACTIVE_TICKS_LOW_HP),
            "damage", pick(lowHp, DASH_DAMAGE, DASH_DAMAGE_LOW_HP),
            "knockback", pick(lowHp, DASH_KNOCKBACK, DASH_KNOCKBACK_LOW_HP),
            "knockbackY", pick(lowHp, DASH_KNOCKBACK_Y, DASH_KNOCKBACK_Y_LOW_HP),
            "hitRadius", pick(lowHp, DASH_HIT_RADIUS, DASH_HIT_RADIUS_LOW_HP)
        );
    }
    return AbilityAPI.params(
        "telegraph", TELEGRAPH,
        "telegraphColor", TELEGRAPH_COLOR,
        "chargeTicks", pick(lowHp, JUMP_CHARGE_TICKS, JUMP_CHARGE_TICKS_LOW_HP),
        "activeTicks", pick(lowHp, JUMP_ACTIVE_TICKS, JUMP_ACTIVE_TICKS_LOW_HP),
        "damage", pick(lowHp, JUMP_DAMAGE, JUMP_DAMAGE_LOW_HP),
        "knockback", pick(lowHp, JUMP_KNOCKBACK, JUMP_KNOCKBACK_LOW_HP),
        "knockbackY", pick(lowHp, JUMP_KNOCKBACK_Y, JUMP_KNOCKBACK_Y_LOW_HP),
        "landRadius", pick(lowHp, JUMP_LAND_RADIUS, JUMP_LAND_RADIUS_LOW_HP),
        "arcHeight", pick(lowHp, JUMP_ARC_HEIGHT, JUMP_ARC_HEIGHT_LOW_HP),
        "maxRange", pick(lowHp, JUMP_MAX_RANGE, JUMP_MAX_RANGE_LOW_HP)
    );
}

function isKnownAbility(id) {
    for (var i = 0; i < SEQUENCE.length; i++) {
        if (SEQUENCE[i] == id) return true;
    }
    return false;
}

function nextAbility(currentId) {
    if (SEQUENCE.length <= 0) return "dash";
    for (var i = 0; i < SEQUENCE.length; i++) {
        if (SEQUENCE[i] == currentId) {
            return SEQUENCE[(i + 1) % SEQUENCE.length];
        }
    }
    return SEQUENCE[0];
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
