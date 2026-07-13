/**
 * Босс: Кровавый Дракон Валахар, Хранитель Кровавого Двора.
 *
 * Архетип Blood Dragon:
 * - фаза 1: холодный рыцарь-дуэлянт;
 * - фаза 2: кровавая жажда и агрессивные контратаки.
 *
 * Визуальный силуэт: тяжёлый вампирский рыцарь с двуручным клинком.
 * Механика — Java AbilityAPI. JS отвечает только за фазы, кулдауны и выбор скилла.
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");

var TIMER_ID = 782;
var PHASE_CHECK_ID = 783;

var CAST_INTERVAL_PHASE1 = 78;
var CAST_INTERVAL_PHASE2 = 56;

var PHASE_KEY = "bd_phase";
var NEXT_CAST_KEY = "bd_next_cast";
var LAST_ABILITY_KEY = "bd_last_ability";
var FORCED_ABILITY_KEY = "bd_forced_ability";
var CD_PREFIX = "bd_cd_";

var POUNCE_ID = "vampire_pounce";
var SIPHON_ID = "vampire_blood_siphon";
var NOVA_ID = "vampire_blood_nova";
var RIPOSTE_ID = "blood_dragon_riposte";

var QUOTES_PHASE1 = [
    "Только достойная кровь заслуживает моего клинка.",
    "Покажи мне поединок, смертный.",
    "Не позорь бой своим страхом."
];

var QUOTES_PHASE2 = [
    "Я выпью твою силу до последней капли!",
    "Красная жажда требует жертвы!",
    "Теперь будет только кровь и сталь!"
];

function init(event) {
    var data = event.npc.getStoreddata();
    data.put(PHASE_KEY, "1");
    data.put(LAST_ABILITY_KEY, "");
    data.put(FORCED_ABILITY_KEY, "");
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

    if (forced.length > 0 && isCooldownReady(data, now, forced)) {
        abilityId = forced;
    } else {
        abilityId = pickAbility(npc, target, phase, data, now);
    }
    if (abilityId == null) return;

    var started = AbilityAPI.start(npc, abilityId, target, buildParams(abilityId, phase));
    if (!started) return;

    data.put(NEXT_CAST_KEY, String(now + getCastInterval(phase)));
    data.put(LAST_ABILITY_KEY, abilityId);
    data.put(FORCED_ABILITY_KEY, "");
    data.put(CD_PREFIX + abilityId, String(now + getCooldownTicks(abilityId, phase)));

    if (Math.random() < (phase == "2" ? 0.42 : 0.3)) {
        sayQuote(npc, phase);
    }
}

function pickAbility(npc, target, phase, data, now) {
    var dist = flatDistance(npc, target);
    var last = String(data.get(LAST_ABILITY_KEY));

    if (phase == "2" && dist < 3.6 && isCooldownReady(data, now, RIPOSTE_ID)) {
        if (last == NOVA_ID || last == SIPHON_ID || Math.random() < 0.45) {
            return RIPOSTE_ID;
        }
    }

    if (dist < 3.2 && isCooldownReady(data, now, SIPHON_ID)) {
        return SIPHON_ID;
    }

    if (phase == "2" && dist < 5.2 && isCooldownReady(data, now, NOVA_ID) && Math.random() < 0.28) {
        return NOVA_ID;
    }

    if (dist <= 12.0 && isCooldownReady(data, now, POUNCE_ID)) {
        return POUNCE_ID;
    }

    if (phase == "2" && isCooldownReady(data, now, RIPOSTE_ID) && dist < 4.8) {
        return RIPOSTE_ID;
    }

    if (isCooldownReady(data, now, POUNCE_ID)) return POUNCE_ID;
    if (isCooldownReady(data, now, SIPHON_ID)) return SIPHON_ID;
    if (phase == "2" && isCooldownReady(data, now, RIPOSTE_ID)) return RIPOSTE_ID;
    if (phase == "2" && isCooldownReady(data, now, NOVA_ID)) return NOVA_ID;
    return null;
}

function buildParams(abilityId, phase) {
    if (abilityId == POUNCE_ID) {
        if (phase == "2") {
            return AbilityAPI.params(
                "damage", 16.0,
                "chargeTicks", 7,
                "activeTicks", 7,
                "arcHeight", 7.0,
                "landRadius", 3.0,
                "lifeStealPerHit", 2.0,
                "knockback", 1.55,
                "knockbackY", 0.35
            );
        }
        return AbilityAPI.params(
            "damage", 13.0,
            "chargeTicks", 9,
            "activeTicks", 8,
            "arcHeight", 7.0,
            "landRadius", 2.6,
            "lifeStealPerHit", 1.6,
            "knockback", 1.4,
            "knockbackY", 0.35
        );
    }

    if (abilityId == SIPHON_ID) {
        if (phase == "2") {
            return AbilityAPI.params(
                "damagePerTick", 1.0,
                "healPerTick", 0.9,
                "activeTicks", 28,
                "maxRange", 6.0,
                "chargeTicks", 6
            );
        }
        return AbilityAPI.params(
            "damagePerTick", 0.8,
            "healPerTick", 0.6,
            "activeTicks", 38,
            "maxRange", 6.0,
            "chargeTicks", 8
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
            "chargeTicks", 10
        );
    }

    if (abilityId == RIPOSTE_ID) {
        return AbilityAPI.params(
            "damage", phase == "2" ? 16.0 : 14.0,
            "distance", phase == "2" ? 4.8 : 4.2,
            "chargeTicks", phase == "2" ? 6 : 7,
            "activeTicks", 4,
            "radius", phase == "2" ? 2.8 : 2.4,
            "coneHalfAngle", 70.0,
            "knockback", 1.0,
            "knockbackY", 0.24
        );
    }

    return null;
}

function updatePhase(npc) {
    var data = npc.getStoreddata();
    var maxHealth = npc.getMaxHealth();
    if (maxHealth <= 0) return;

    var ratio = npc.getHealth() / maxHealth;
    var newPhase = ratio <= 0.5 ? "2" : "1";
    var oldPhase = String(data.get(PHASE_KEY));
    if (newPhase == oldPhase) return;

    data.put(PHASE_KEY, newPhase);
    if (newPhase == "2") {
        data.put(FORCED_ABILITY_KEY, NOVA_ID);
        npc.say("§4§lКрасная жажда пробудилась!");
    }

    try {
        npc.getWorld().spawnParticle("minecraft:entity_effect",
            npc.getX(), npc.getY() + 1.4, npc.getZ(), 0.9, 0.1, 0.1, 0, 10);
        npc.getWorld().spawnParticle("minecraft:damage_indicator",
            npc.getX(), npc.getY() + 1.0, npc.getZ(), 0, 0.1, 0, 0, 8);
    } catch (e) {}
}

function targetLost(event) {
    AbilityAPI.cancel(event.npc);
}

function died(event) {
    AbilityAPI.cancel(event.npc);
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

function getCastInterval(phase) {
    return phase == "2" ? CAST_INTERVAL_PHASE2 : CAST_INTERVAL_PHASE1;
}

function getCooldownTicks(abilityId, phase) {
    if (abilityId == POUNCE_ID) return phase == "2" ? 62 : 76;
    if (abilityId == SIPHON_ID) return phase == "2" ? 82 : 96;
    if (abilityId == NOVA_ID) return 165;
    if (abilityId == RIPOSTE_ID) return phase == "2" ? 70 : 88;
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

function sayQuote(npc, phase) {
    var quotes = phase == "2" ? QUOTES_PHASE2 : QUOTES_PHASE1;
    var idx = Math.floor(Math.random() * quotes.length);
    npc.say("§7" + quotes[idx]);
}

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}
