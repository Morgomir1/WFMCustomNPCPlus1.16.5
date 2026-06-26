/**
 * Boss Dash + Jump — тонкий оркестратор (WFM CustomNPC+)
 *
 * Механика dash / jump_slam — в Java: noppes.npcs.abilities.AbilityAPI
 * Этот скрипт: когда кастовать, кулдауны, выбор способности, пер-кастовые params.
 *
 * Установка: NPC → Advanced → Scripts → /script reload
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");

var TIMER_ID = 701;
var TIMER_INTERVAL = 1;
var CAST_CHECK_INTERVAL = 10;

// Дистанции и шанс каста (оркестрация босса)
var DASH_MIN_RANGE = 4.0;
var DASH_MAX_RANGE = 24.0;
var JUMP_MIN_RANGE = 3.0;
var JUMP_MAX_RANGE = 22.0;
var DASH_COOLDOWN_TICKS = 140;
var JUMP_COOLDOWN_TICKS = 180;
var REQUIRE_LINE_OF_SIGHT = true;
var CAST_CHANCE = 0.28;

var DASH_CD_KEY = "bdj_dash_cd";
var JUMP_CD_KEY = "bdj_jump_cd";
var CAST_COUNTER_KEY = "bdj_cast_ctr";

function init(event) {
    try {
        var timers = event.npc.getTimers();
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
        log("boss_dash_jump init ERROR: " + e);
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

        var ctr = getInt(data, CAST_COUNTER_KEY) + 1;
        data.put(CAST_COUNTER_KEY, String(ctr));
        if (ctr < CAST_CHECK_INTERVAL) {
            return;
        }
        data.put(CAST_COUNTER_KEY, "0");
        tryStartAbility(npc, world, data);
    } catch (e) {
        log("boss_dash_jump timer ERROR: " + e);
    }
}

function targetLost(event) {
    AbilityAPI.cancel(event.npc);
}

function died(event) {
    AbilityAPI.cancel(event.npc);
}

function tryStartAbility(npc, world, data) {
    var now = world.getTotalTime();
    var target = getAggroTarget(npc);
    if (target == null) return;
    if (REQUIRE_LINE_OF_SIGHT && !canSeeTarget(npc, target)) return;
    if (Math.random() >= CAST_CHANCE) return;

    var dist = distanceFlat(npc, target);
    var abilityId = pickAbility(data, now, dist);
    if (abilityId == null) return;

    var hpRatio = npc.getHealth() / npc.getMaxHealth();
    var params = buildParams(abilityId, hpRatio);

    if (AbilityAPI.start(npc, abilityId, target, params)) {
        if (abilityId == "dash") {
            data.put(DASH_CD_KEY, String(now + DASH_COOLDOWN_TICKS));
        } else {
            data.put(JUMP_CD_KEY, String(now + JUMP_COOLDOWN_TICKS));
        }
    }
}

function buildParams(abilityId, hpRatio) {
    if (abilityId == "dash") {
        var damage = hpRatio < 0.3 ? 14.0 : 10.0;
        return AbilityAPI.params("damage", damage);
    }
    if (abilityId == "jump_slam") {
        var damage = hpRatio < 0.3 ? 18.0 : 14.0;
        return AbilityAPI.params("damage", damage, "arcHeight", hpRatio < 0.3 ? 7.0 : 6.0);
    }
    return null;
}

function pickAbility(data, now, dist) {
    var dashReady = now >= getInt(data, DASH_CD_KEY);
    var jumpReady = now >= getInt(data, JUMP_CD_KEY);
    var canDash = dashReady && dist >= DASH_MIN_RANGE && dist <= DASH_MAX_RANGE;
    var canJump = jumpReady && dist >= JUMP_MIN_RANGE && dist <= JUMP_MAX_RANGE;

    if (!canDash && !canJump) return null;
    if (canDash && !canJump) return "dash";
    if (canJump && !canDash) return "jump_slam";

    if (dist <= (JUMP_MAX_RANGE + DASH_MIN_RANGE) * 0.5) {
        return Math.random() < 0.55 ? "jump_slam" : "dash";
    }
    return Math.random() < 0.55 ? "dash" : "jump_slam";
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
