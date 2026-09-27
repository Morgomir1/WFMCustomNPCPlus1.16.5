/**
 * Босс: удар мечом по области (как у охотника на ведьм) + кручение с оружием.
 * Модель: WFM BipedModel — крутится всё тело и руки с предметом.
 * Без стана после каста. Все х-ки ниже.
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");

// =====================================================
// НАСТРОЙКИ
// =====================================================

var TIMER_ID = 705;

/** Пауза между абилками (тики, 20 = 1 сек). */
var CAST_INTERVAL_TICKS = 80;

/** Порядок. Можно оставить одно умение. */
var SEQUENCE = ["wh_flaming_strike", "weapon_spin"];

var TELEGRAPH_COLOR = 0xC0FF3030;

// ----- Удар мечом (усечённый конус, как у охотника) -----
var STRIKE_CHARGE_TICKS = 20;
var STRIKE_DISTANCE = 4.5;
var STRIKE_NEAR_HALF_WIDTH = 1.35;
var STRIKE_CONE_HALF_ANGLE = 38.0;
var STRIKE_DAMAGE = 14.0;
var STRIKE_KNOCKBACK = 0.9;
var STRIKE_KNOCKBACK_Y = 0.2;
/** 0 = без огня. У охотника обычно 4. */
var STRIKE_FIRE_SECONDS = 0;

// ----- Кручение с оружием -----
var SPIN_CHARGE_TICKS = 16;
var SPIN_ACTIVE_TICKS = 40;
var SPIN_DAMAGE = 4.0;
var SPIN_DAMAGE_INTERVAL = 5;
var SPIN_RADIUS = 3.5;
var SPIN_KNOCKBACK = 0.45;
var SPIN_KNOCKBACK_Y = 0.12;
/** Градусов за тик. 42 ≈ полный оборот за ~0.4 сек. */
var SPIN_DEG_PER_TICK = 42.0;
/** Скорость полёта по прямой во время спина (блоков/тик). Курс фиксируется в старт спина. */
var SPIN_MOVE_SPEED = 0.4;

// =====================================================
// storeddata
// =====================================================
var NEXT_CAST_KEY = "bss_next_cast";
var NEXT_ABILITY_KEY = "bss_next_ability";

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

    var started = AbilityAPI.start(npc, abilityId, target, buildParams(abilityId));
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

function buildParams(abilityId) {
    if (abilityId == "wh_flaming_strike") {
        return AbilityAPI.params(
            "telegraphColor", TELEGRAPH_COLOR,
            "telegraph", 0,
            "chargeTicks", STRIKE_CHARGE_TICKS,
            "distance", STRIKE_DISTANCE,
            "radius", STRIKE_NEAR_HALF_WIDTH,
            "coneHalfAngle", STRIKE_CONE_HALF_ANGLE,
            "damage", STRIKE_DAMAGE,
            "knockback", STRIKE_KNOCKBACK,
            "knockbackY", STRIKE_KNOCKBACK_Y,
            "fireSeconds", STRIKE_FIRE_SECONDS
        );
    }
    return AbilityAPI.params(
        "telegraph", 1,
        "telegraphColor", TELEGRAPH_COLOR,
        "chargeTicks", SPIN_CHARGE_TICKS,
        "activeTicks", SPIN_ACTIVE_TICKS,
        "damagePerTick", SPIN_DAMAGE,
        "damageInterval", SPIN_DAMAGE_INTERVAL,
        "radius", SPIN_RADIUS,
        "knockback", SPIN_KNOCKBACK,
        "knockbackY", SPIN_KNOCKBACK_Y,
        "orbitSpeed", SPIN_DEG_PER_TICK,
        "approachSpeed", SPIN_MOVE_SPEED
    );
}

function isKnownAbility(id) {
    for (var i = 0; i < SEQUENCE.length; i++) {
        if (SEQUENCE[i] == id) return true;
    }
    return false;
}

function nextAbility(currentId) {
    if (SEQUENCE.length <= 0) return "wh_flaming_strike";
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
