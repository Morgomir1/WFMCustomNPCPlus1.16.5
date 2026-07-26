/**
 * Босс: охотник на ведьм (Warhammer Fantasy).
 * Механика — Java AbilityAPI. Скрипт: дистанция, фазы, цепочки.
 *
 * Кит:
 *   wh_flaming_strike  — огненный удар (усечённый конус)
 *   net_throw          — сеть → FORCED wh_flaming_crossbow (пистолет)
 *   wh_lunge           — рывок+стан → FORCED wh_flaming_strike
 *   wh_flaming_crossbow — пистолет в левой руке (прицел → выстрел WFM)
 *   wh_fire_bomb       — ульт (фаза 2, HP ≤ 50%)
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");

var TIMER_ID = 703;
var PHASE_CHECK_ID = 704;
var CAST_INTERVAL_PHASE1 = 70;
var CAST_INTERVAL_PHASE2 = 50;

var PHASE_KEY = "wh_phase";
var NEXT_CAST_KEY = "wh_next_cast";
var LAST_ABILITY_KEY = "wh_last_ability";
var FORCED_ABILITY_KEY = "wh_forced_ability";
var CD_PREFIX = "wh_cd_";

var STRIKE_ID = "wh_flaming_strike";
var NET_ID = "net_throw";
var LUNGE_ID = "wh_lunge";
var CROSSBOW_ID = "wh_flaming_crossbow";
var BOMB_ID = "wh_fire_bomb";

var QUOTES_PHASE1 = [
    "Еретик!",
    "Суд Сигмара неизбежен!",
    "Твоя ведьмовская кровь выдаст тебя!"
];
var QUOTES_PHASE2 = [
    "За Сигмара!",
    "Нет спасения!",
    "Огонь очистит твою душу!"
];

var COOLDOWNS = {};
COOLDOWNS[STRIKE_ID] = 55;
COOLDOWNS[NET_ID] = 130;
COOLDOWNS[LUNGE_ID] = 90;
COOLDOWNS[CROSSBOW_ID] = 45;
COOLDOWNS[BOMB_ID] = 220;

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

    var data = npc.getStoreddata();
    var now = npc.getWorld().getTotalTime();
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

    var started = AbilityAPI.start(npc, abilityId, target, buildParams(abilityId, phase, npc, target));
    if (!started) return;

    data.put(FORCED_ABILITY_KEY, "");
    data.put(LAST_ABILITY_KEY, abilityId);
    data.put(CD_PREFIX + abilityId, String(now + getCooldown(abilityId)));

    // Цепочки: сеть → пистолет; рывок → огненный удар
    if (abilityId == NET_ID) {
        data.put(FORCED_ABILITY_KEY, CROSSBOW_ID);
        data.put(NEXT_CAST_KEY, String(now + 5));
    } else if (abilityId == LUNGE_ID) {
        data.put(FORCED_ABILITY_KEY, STRIKE_ID);
        data.put(NEXT_CAST_KEY, String(now + 5));
    } else {
        var interval = phase == "2" ? CAST_INTERVAL_PHASE2 : CAST_INTERVAL_PHASE1;
        data.put(NEXT_CAST_KEY, String(now + interval));
    }

    if (Math.random() < (phase == "2" ? 0.4 : 0.3)) {
        sayQuote(npc, phase);
    }
}

function pickAbility(npc, target, phase, data, now) {
    var dist = flatDistance(npc, target);
    var last = String(data.get(LAST_ABILITY_KEY));

    if (phase == "2" && isCooldownReady(data, now, BOMB_ID) && dist <= 18.0) {
        if (last != BOMB_ID && Math.random() < 0.35) {
            return BOMB_ID;
        }
    }

    if (dist < 5.0) {
        if (isCooldownReady(data, now, LUNGE_ID) && last != LUNGE_ID && Math.random() < 0.45) {
            return LUNGE_ID;
        }
        if (isCooldownReady(data, now, STRIKE_ID)) {
            return STRIKE_ID;
        }
        if (isCooldownReady(data, now, LUNGE_ID)) {
            return LUNGE_ID;
        }
    }

    if (dist >= 5.0 && dist <= 12.0 && isCooldownReady(data, now, NET_ID) && last != NET_ID) {
        return NET_ID;
    }

    if (dist > 8.0 && isCooldownReady(data, now, CROSSBOW_ID)) {
        return CROSSBOW_ID;
    }

    if (isCooldownReady(data, now, NET_ID)) return NET_ID;
    if (isCooldownReady(data, now, CROSSBOW_ID)) return CROSSBOW_ID;
    if (isCooldownReady(data, now, STRIKE_ID)) return STRIKE_ID;
    if (isCooldownReady(data, now, LUNGE_ID)) return LUNGE_ID;
    if (phase == "2" && isCooldownReady(data, now, BOMB_ID)) return BOMB_ID;

    return null;
}

function buildParams(abilityId, phase, npc, target) {
    var tg = 0xC0FF3030;
    if (abilityId == STRIKE_ID) {
        return AbilityAPI.params(
            "telegraphColor", tg,
            "telegraph", 0,
            "chargeTicks", 20,
            "distance", 4.5,
            "radius", 1.35,
            "coneHalfAngle", 38.0,
            "damage", phase == "2" ? 16.0 : 14.0,
            "fireSeconds", 4
        );
    }
    if (abilityId == NET_ID) {
        return AbilityAPI.params(
            "telegraphColor", tg,
            "radius", 2.5,
            "chargeTicks", 20,
            "activeTicks", 12,
            "effectDuration", 40,
            "effectAmplifier", 3,
            "projectileItem", "wfm:dwarf_ranger_net",
            "accuracy", 2
        );
    }
    if (abilityId == LUNGE_ID) {
        return AbilityAPI.params(
            "telegraphColor", tg,
            "telegraph", 0,
            "chargeTicks", 20,
            "activeTicks", 6,
            "distance", 5.5,
            "hitRadius", 1.5,
            "landRadius", 1.75,
            "damage", phase == "2" ? 18.0 : 16.0,
            "arcHeight", 1.8,
            "knockback", 0,
            "knockbackY", 0,
            "effectDuration", 20
        );
    }
    if (abilityId == CROSSBOW_ID) {
        var shotDist = 18.0;
        if (npc != null && target != null) {
            var d = flatDistance(npc, target);
            if (d > 2.0) shotDist = Math.min(24.0, Math.max(6.0, d + 1.0));
        }
        return AbilityAPI.params(
            "telegraphColor", tg,
            "chargeTicks", 10,
            "distance", shotDist,
            "radius", 0.7,
            "damage", phase == "2" ? 12.0 : 10.0,
            "accuracy", 3,
            "rangedItem", "wfm:empire_pistol",
            "meleeItem", "wfm:empire_witch_hunter_rapier"
        );
    }
    if (abilityId == BOMB_ID) {
        return AbilityAPI.params(
            "telegraphColor", tg,
            "chargeTicks", 20,
            "activeTicks", 14,
            "scatterTicks", 20,
            "landRadius", 4.5,
            "damage", phase == "2" ? 14.0 : 12.0,
            "shots", 5,
            "spreadRadius", 5.0,
            "hitRadius", 2.8,
            "zoneTicks", 70,
            "damagePerTick", 2.5,
            "damageInterval", 15,
            "fireSeconds", 3,
            "zoneColor", 0xC0FF6020,
            "arcHeight", 6.0
        );
    }
    return null;
}

function updatePhase(npc) {
    var data = npc.getStoreddata();
    var health = npc.getHealth();
    var maxHealth = npc.getMaxHealth();
    if (maxHealth <= 0) return;

    var ratio = health / maxHealth;
    var newPhase = ratio <= 0.5 ? "2" : "1";
    var oldPhase = String(data.get(PHASE_KEY));
    if (newPhase != oldPhase) {
        data.put(PHASE_KEY, newPhase);
        if (newPhase == "2") {
            npc.say("§c§lЗа Сигмара! Ни пощады еретикам!");
            data.put(FORCED_ABILITY_KEY, BOMB_ID);
            data.put(NEXT_CAST_KEY, String(npc.getWorld().getTotalTime() + 10));
            try {
                npc.getWorld().spawnParticle("minecraft:explosion_emitter",
                    npc.getX(), npc.getY() + 1.5, npc.getZ(), 0, 0, 0, 0, 1);
            } catch (e) {}
        }
    }
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
        timers.forceStart(PHASE_CHECK_ID, 20, true);
    } else {
        timers.start(TIMER_ID, 1, true);
        timers.start(PHASE_CHECK_ID, 20, true);
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

function sayQuote(npc, phase) {
    var quotes = phase == "2" ? QUOTES_PHASE2 : QUOTES_PHASE1;
    var idx = Math.floor(Math.random() * quotes.length);
    npc.say("§7" + quotes[idx]);
}

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}
