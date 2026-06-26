/**
 * Босс-вампир: фазы + прыжок/кровь/летучие мыши.
 * Механика — Java AbilityAPI. Скрипт решает, когда и что кастовать.
 *
 * Clone Bank: tab=CLONE_TAB, name=CLONE_NAME (летучие мыши-миньоны).
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");

var TIMER_ID = 780;
var PHASE_CHECK_ID = 781;

var CAST_INTERVAL_PHASE1 = 80;
var CAST_INTERVAL_PHASE2 = 65;
var CAST_INTERVAL_PHASE3 = 55;

var CLONE_TAB = 1;
var CLONE_NAME = "vampire_bat";

var PHASE_KEY = "vb_phase";
var NEXT_CAST_KEY = "vb_next_cast";
var LAST_ABILITY_KEY = "vb_last_ability";
var FORCED_ABILITY_KEY = "vb_forced";
var CD_PREFIX = "vb_cd_";

var POUNCE_ID = "vampire_pounce";
var SIPHON_ID = "vampire_blood_siphon";
var SWARM_ID = "vampire_bat_swarm";
var NOVA_ID = "vampire_blood_nova";

var COOLDOWNS = {
    vampire_pounce: 80,
    vampire_blood_siphon: 70,
    vampire_bat_swarm: 140,
    vampire_blood_nova: 160
};

function init(event) {
    var data = event.npc.getStoreddata();
    data.put(PHASE_KEY, "1");
    data.put(LAST_ABILITY_KEY, "");
    startTimers(event.npc);
}

function timer(event) {
    if (event.id == PHASE_CHECK_ID) {
        updatePhase(event.npc);
        return;
    }
    if (event.id != TIMER_ID) return;

    var npc = event.npc;
    if (!npc.isAlive()) {
        AbilityAPI.cancel(npc);
        return;
    }
    if (AbilityAPI.isBusy(npc)) return;

    var world = npc.getWorld();
    var data = npc.getStoreddata();
    var now = world.getTotalTime();
    if (now < getInt(data, NEXT_CAST_KEY)) return;

    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) return;

    var phase = String(data.get(PHASE_KEY));
    var forced = String(data.get(FORCED_ABILITY_KEY));
    var abilityId = null;

    if (forced && forced.length > 0 && isCooldownReady(data, now, forced)) {
        abilityId = forced;
    } else {
        abilityId = pickAbility(npc, target, phase, data, now);
    }

    if (abilityId == null) return;

    var started = AbilityAPI.start(npc, abilityId, target, buildParams(abilityId, phase));
    if (!started) return;

    var interval = phase == "3" ? CAST_INTERVAL_PHASE3 : (phase == "2" ? CAST_INTERVAL_PHASE2 : CAST_INTERVAL_PHASE1);
    data.put(NEXT_CAST_KEY, String(now + interval));
    data.put(LAST_ABILITY_KEY, abilityId);
    data.put(FORCED_ABILITY_KEY, "");
    data.put(CD_PREFIX + abilityId, String(now + COOLDOWNS[abilityId]));
}

function targetLost(event) {
    AbilityAPI.cancel(event.npc);
}

function died(event) {
    AbilityAPI.cancel(event.npc);
}

function updatePhase(npc) {
    var data = npc.getStoreddata();
    var health = npc.getHealth();
    var maxHealth = npc.getMaxHealth();
    if (maxHealth <= 0) return;

    var ratio = health / maxHealth;
    var newPhase = ratio <= 0.35 ? "3" : (ratio <= 0.7 ? "2" : "1");
    var oldPhase = String(data.get(PHASE_KEY));
    if (newPhase != oldPhase) {
        data.put(PHASE_KEY, newPhase);
        if (newPhase == "2") {
            data.put(FORCED_ABILITY_KEY, SWARM_ID);
            npc.say("§4Тьма сгущается...");
        } else if (newPhase == "3") {
            data.put(FORCED_ABILITY_KEY, NOVA_ID);
            npc.say("§4Я утолю жажду кровью!");
        }
        try {
            npc.getWorld().spawnParticle("minecraft:entity_effect",
                npc.getX(), npc.getY() + 1.5, npc.getZ(), 0.9, 0.1, 0.1, 0, 8);
        } catch (e) {}
    }
}

function pickAbility(npc, target, phase, data, now) {
    var dist = flatDistance(npc, target);

    if (dist < 4.0) {
        if (phase == "3" && isCooldownReady(data, now, NOVA_ID) && Math.random() < 0.45) {
            return NOVA_ID;
        }
        if (isCooldownReady(data, now, SIPHON_ID)) {
            return SIPHON_ID;
        }
        if (isCooldownReady(data, now, POUNCE_ID)) {
            return POUNCE_ID;
        }
    }

    if (dist <= 12.0) {
        if (phase != "1" && isCooldownReady(data, now, SWARM_ID) && Math.random() < 0.55) {
            return SWARM_ID;
        }
        if (isCooldownReady(data, now, POUNCE_ID)) {
            return POUNCE_ID;
        }
    }

    if (dist > 12.0) {
        if (isCooldownReady(data, now, POUNCE_ID)) {
            return POUNCE_ID;
        }
        if (phase != "1" && isCooldownReady(data, now, SWARM_ID)) {
            return SWARM_ID;
        }
    }

    if (isCooldownReady(data, now, POUNCE_ID)) return POUNCE_ID;
    if (isCooldownReady(data, now, SIPHON_ID)) return SIPHON_ID;
    if (phase != "1" && isCooldownReady(data, now, SWARM_ID)) return SWARM_ID;
    if (phase == "3" && isCooldownReady(data, now, NOVA_ID)) return NOVA_ID;
    return null;
}

function buildParams(abilityId, phase) {
    if (abilityId == POUNCE_ID) {
        var dmg = phase == "3" ? 16.0 : (phase == "2" ? 14.0 : 12.0);
        var charge = phase == "3" ? 7 : (phase == "2" ? 8 : 10);
        var active = phase == "3" ? 7 : (phase == "2" ? 8 : 9);
        var lifeSteal = phase == "3" ? 2.0 : (phase == "2" ? 1.8 : 1.5);
        var radius = phase == "3" ? 3.0 : 2.6;
        return AbilityAPI.params(
            "damage", dmg,
            "chargeTicks", charge,
            "activeTicks", active,
            "arcHeight", 7.0,
            "landRadius", radius,
            "lifeStealPerHit", lifeSteal,
            "knockback", 1.4,
            "knockbackY", 0.35
        );
    }
    if (abilityId == SIPHON_ID) {
        var dmgTick = phase == "3" ? 1.0 : (phase == "2" ? 0.9 : 0.8);
        var healTick = phase == "3" ? 0.8 : (phase == "2" ? 0.7 : 0.6);
        var activeTicks = phase == "3" ? 32 : (phase == "2" ? 36 : 40);
        return AbilityAPI.params(
            "damagePerTick", dmgTick,
            "healPerTick", healTick,
            "activeTicks", activeTicks,
            "maxRange", 6.0,
            "chargeTicks", 8
        );
    }
    if (abilityId == SWARM_ID) {
        var summons = phase == "3" ? 4 : 3;
        return AbilityAPI.params(
            "radius", 5.0,
            "effectType", "blindness",
            "effectDuration", 40,
            "effectAmplifier", 0,
            "summonCount", summons,
            "summonRadius", 4.0,
            "maxSummonedNearBoss", 6,
            "cloneTab", CLONE_TAB,
            "cloneName", CLONE_NAME,
            "chargeTicks", 10,
            "activeTicks", 22
        );
    }
    if (abilityId == NOVA_ID) {
        return AbilityAPI.params(
            "damage", 18.0,
            "radius", 5.5,
            "knockback", 1.2,
            "knockbackY", 0.35,
            "effectType", "weakness",
            "effectDuration", 40,
            "effectAmplifier", 0,
            "chargeTicks", 12
        );
    }
    return null;
}

function startTimers(npc) {
    var timers = npc.getTimers();
    if (timers == null) return;
    if (typeof timers.forceStart == "function") {
        timers.forceStart(TIMER_ID, 1, true);
        timers.forceStart(PHASE_CHECK_ID, 20, true);
    } else {
        timers.start(TIMER_ID, 1, true);
        timers.start(PHASE_CHECK_ID, 20, true);
    }
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
