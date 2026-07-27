/**
 * Босс: Отродье.
 * Механика — Java AbilityAPI. JS: кулдауны, выбор скилла, forced-цепочка, реактивные лужи.
 *
 * Кит:
 *   otrodie_hell_vomit      — рвота → Java onEnd ставит FORCED fecal_wave
 *   otrodie_fecal_wave      — только forced (после рвоты)
 *   otrodie_devour_dash     — рывок / пожирание
 *   otrodie_spreading_filth — реактив через OtrodieSpreadingFilthAbility.trigger
 *                             (не AbilityAPI.start — не блокирует текущий каст)
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");
var OtrodieSpreadingFilthAbility = Java.type("noppes.npcs.abilities.impl.OtrodieSpreadingFilthAbility");

var TIMER_ID = 791;
var CAST_INTERVAL = 70;

var OTRODIE_BOSS_FLAG = "otrodie_boss";
var HP_MARK_KEY = "ot_hp_mark";
var PUDDLE_CD_KEY = "ot_puddle_cd";
var FORCED_ABILITY_KEY = "ot_forced_ability";
var NEXT_CAST_KEY = "ot_next_cast";
var LAST_ABILITY_KEY = "ot_last_ability";
var CD_PREFIX = "ot_cd_";

var VOMIT_ID = "otrodie_hell_vomit";
var FECAL_ID = "otrodie_fecal_wave";
var DEVOUR_ID = "otrodie_devour_dash";

var HP_THRESHOLD = 200;
var PUDDLE_CD_TICKS = 400; // 20 с

var ZONE_RED = 0xC0FF3030;
var ZONE_GREEN = 0xC040A030;

var COOLDOWNS = {};
COOLDOWNS[VOMIT_ID] = 180;
COOLDOWNS[FECAL_ID] = 0; // только forced после vomit
COOLDOWNS[DEVOUR_ID] = 110;

function init(event) {
    var npc = event.npc;
    var data = npc.getStoreddata();
    data.put(OTRODIE_BOSS_FLAG, "1");
    data.put(FORCED_ABILITY_KEY, "");
    data.put(LAST_ABILITY_KEY, "");
    data.put(NEXT_CAST_KEY, "0");
    data.put(PUDDLE_CD_KEY, "0");
    data.put(HP_MARK_KEY, String(npc.getHealth()));
    startTimers(npc);
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

    var forced = String(data.get(FORCED_ABILITY_KEY));
    // Forced fecal после рвоты — не ждать обычный cast interval
    if (forced.length == 0 && now < getInt(data, NEXT_CAST_KEY)) return;

    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) return;

    var abilityId = null;
    if (forced.length > 0 && isCooldownReady(data, now, forced)) {
        abilityId = forced;
    } else if (forced.length == 0) {
        abilityId = pickAbility(npc, target, data, now);
    }
    if (abilityId == null) return;

    var started = AbilityAPI.start(npc, abilityId, target, buildParams(abilityId, npc, target));
    if (!started) return;

    data.put(FORCED_ABILITY_KEY, "");
    data.put(LAST_ABILITY_KEY, abilityId);
    data.put(CD_PREFIX + abilityId, String(now + getCooldown(abilityId)));
    data.put(NEXT_CAST_KEY, String(now + CAST_INTERVAL));
}

function pickAbility(npc, target, data, now) {
    var dist = flatDistance(npc, target);
    var last = String(data.get(LAST_ABILITY_KEY));

    // Mid: рывок/пожирание
    if (dist >= 3.5 && dist <= 12.0 && isCooldownReady(data, now, DEVOUR_ID) && last != DEVOUR_ID) {
        if (Math.random() < 0.55) return DEVOUR_ID;
    }

    // Mid/far: адская блевотина (fecal только через forced из Java onEnd)
    if (dist >= 4.0 && dist <= 24.0 && isCooldownReady(data, now, VOMIT_ID) && last != VOMIT_ID) {
        return VOMIT_ID;
    }

    if (dist <= 14.0 && isCooldownReady(data, now, DEVOUR_ID)) return DEVOUR_ID;
    if (isCooldownReady(data, now, VOMIT_ID)) return VOMIT_ID;
    if (isCooldownReady(data, now, DEVOUR_ID)) return DEVOUR_ID;
    return null;
}

function buildParams(abilityId, npc, target) {
    if (abilityId == VOMIT_ID) {
        return AbilityAPI.params(
            "telegraphColor", ZONE_RED,
            "zoneColor", ZONE_RED,
            "chargeTicks", 28,
            "activeTicks", 280,
            "radius", 2.5,
            "damage", 3.0,
            "damageInterval", 10,
            "breakDamage", 100.0,
            "maxRange", 28.0
        );
    }
    if (abilityId == FECAL_ID) {
        return AbilityAPI.params(
            "telegraphColor", ZONE_RED,
            "telegraph", 0,
            "chargeTicks", 24,
            "hitRadius", 2.0,
            "distance", 12.0,
            "coneHalfAngle", 55.0,
            "damage", 14.0
        );
    }
    if (abilityId == DEVOUR_ID) {
        var dashDist = 14.0;
        if (npc != null && target != null) {
            var d = flatDistance(npc, target);
            if (d > 2.0) dashDist = Math.min(16.0, Math.max(6.0, d + 1.0));
        }
        return AbilityAPI.params(
            "telegraphColor", ZONE_RED,
            "telegraph", 0,
            "chargeTicks", 24,
            "distance", dashDist,
            "hitRadius", 1.6,
            "hitCount", 15,
            "healOnFail", 200.0
        );
    }
    return null;
}

function damaged(event) {
    var npc = event.npc;
    if (npc == null || !npc.isAlive()) return;

    var data = npc.getStoreddata();
    var world = npc.getWorld();
    var now = world.getTotalTime();

    var currentHp = npc.getHealth();
    try {
        var dmg = parseFloat(String(event.damage));
        if (dmg > 0) {
            // damaged может сработать до применения урона
            var after = currentHp - dmg;
            if (after < currentHp) currentHp = after;
        }
    } catch (e) {}
    if (currentHp < 0) currentHp = 0;

    var mark = getFloat(data, HP_MARK_KEY);
    if (mark <= 0) {
        data.put(HP_MARK_KEY, String(currentHp));
        return;
    }

    if (mark - currentHp < HP_THRESHOLD) return;
    if (now < getInt(data, PUDDLE_CD_KEY)) return;

    OtrodieSpreadingFilthAbility.trigger(npc, AbilityAPI.params(
        "zoneColor", ZONE_GREEN
    ));
    data.put(PUDDLE_CD_KEY, String(now + PUDDLE_CD_TICKS));
    data.put(HP_MARK_KEY, String(currentHp));
}

function targetLost(event) {
    AbilityAPI.cancel(event.npc);
    event.npc.getStoreddata().put(FORCED_ABILITY_KEY, "");
}

function died(event) {
    AbilityAPI.cancel(event.npc);
    event.npc.getStoreddata().put(FORCED_ABILITY_KEY, "");
}

function startTimers(npc) {
    var timers = npc.getTimers();
    if (timers == null) return;
    if (typeof timers.forceStart == "function") {
        timers.forceStart(TIMER_ID, 1, true);
    } else {
        timers.start(TIMER_ID, 1, true);
    }
}

function getCooldown(abilityId) {
    if (COOLDOWNS[abilityId] != null) return COOLDOWNS[abilityId];
    return 80;
}

function isCooldownReady(data, now, abilityId) {
    var key = CD_PREFIX + abilityId;
    if (!data.has(key)) return true;
    return now >= getInt(data, key);
}

function flatDistance(npc, target) {
    var dx = npc.getX() - target.getX();
    var dz = npc.getZ() - target.getZ();
    return Math.sqrt(dx * dx + dz * dz);
}

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}

function getFloat(data, key) {
    if (!data.has(key)) return 0;
    return parseFloat(String(data.get(key)));
}
