/**
 * Босс: охотник на ведьм (Warhammer Fantasy).
 * Механика — Java AbilityAPI. Скрипт выбирает скилл по дистанции и фазе.
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");

var TIMER_ID = 703;
var PHASE_CHECK_ID = 704;
var CAST_INTERVAL_PHASE1 = 80;
var CAST_INTERVAL_PHASE2 = 55;

var PHASE_KEY = "wh_phase";
var NEXT_CAST_KEY = "wh_next_cast";
var LAST_ABILITY_KEY = "wh_last_ability";
var CD_PREFIX = "wh_cd_";

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

var COOLDOWNS = {
    pistol_shot: 40,
    net_throw: 120,
    stake_thrust: 60,
    holy_water_splash: 160,
    burning_brand: 140,
    retreat_dash: 100
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

    var data = npc.getStoreddata();
    var now = npc.getWorld().getTotalTime();
    if (now < getInt(data, NEXT_CAST_KEY)) return;

    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) return;

    var phase = String(data.get(PHASE_KEY));
    var abilityId = pickAbility(npc, target, phase, data, now);
    if (abilityId == null) return;

    var started = AbilityAPI.start(npc, abilityId, target, buildParams(abilityId, phase));
    if (!started) return;

    var interval = phase == "2" ? CAST_INTERVAL_PHASE2 : CAST_INTERVAL_PHASE1;
    data.put(NEXT_CAST_KEY, String(now + interval));
    data.put(LAST_ABILITY_KEY, abilityId);
    data.put(CD_PREFIX + abilityId, String(now + COOLDOWNS[abilityId]));

    if (Math.random() < 0.35) {
        sayQuote(npc, phase);
    }
}

function pickAbility(npc, target, phase, data, now) {
    var dist = flatDistance(npc, target);
    var last = String(data.get(LAST_ABILITY_KEY));

    if (dist < 3.0 && isCooldownReady(data, now, "retreat_dash")
            && (last == "stake_thrust" || last == "burning_brand")) {
        return "retreat_dash";
    }

    if (dist < 4.0) {
        if (phase == "2" && isCooldownReady(data, now, "burning_brand")) {
            return "burning_brand";
        }
        if (isCooldownReady(data, now, "stake_thrust")) {
            return "stake_thrust";
        }
    }

    if (phase == "2" && isCooldownReady(data, now, "holy_water_splash") && Math.random() < 0.45) {
        return "holy_water_splash";
    }

    if (dist >= 5.0 && dist <= 12.0 && isCooldownReady(data, now, "net_throw")) {
        return "net_throw";
    }

    if (dist > 10.0 || !isCooldownReady(data, now, "net_throw")) {
        if (isCooldownReady(data, now, "pistol_shot")) {
            return "pistol_shot";
        }
    }

    if (isCooldownReady(data, now, "net_throw")) return "net_throw";
    if (isCooldownReady(data, now, "pistol_shot")) return "pistol_shot";
    if (isCooldownReady(data, now, "stake_thrust")) return "stake_thrust";
    if (phase == "2" && isCooldownReady(data, now, "holy_water_splash")) return "holy_water_splash";
    if (phase == "2" && isCooldownReady(data, now, "burning_brand")) return "burning_brand";

    return null;
}

function buildParams(abilityId, phase) {
    if (abilityId == "pistol_shot") {
        var dmg = phase == "2" ? 11.0 : 9.0;
        return AbilityAPI.params("telegraphColor", 0xC0FF3030, "damage", dmg, "accuracy", phase == "2" ? 3 : 4);
    }
    if (abilityId == "net_throw") {
        // Круг warning → через 0.5 с (10 тиков) опутывает всех в зоне
        return AbilityAPI.params(
            "telegraphColor", 0xC0FF3030,
            "radius", 3.5,
            "chargeTicks", 10,
            "effectDuration", 60,
            "effectAmplifier", 3
        );
    }
    if (abilityId == "stake_thrust") {
        var thrustDmg = phase == "2" ? 18.0 : 16.0;
        return AbilityAPI.params("telegraphColor", 0xC0FF3030, "damage", thrustDmg, "undeadBonusMultiplier", 1.5);
    }
    if (abilityId == "holy_water_splash") {
        return AbilityAPI.params("telegraphColor", 0xC0FF3030, "damage", 8.0, "undeadBonusMultiplier", 2.0);
    }
    if (abilityId == "burning_brand") {
        return AbilityAPI.params("telegraphColor", 0xC0FF3030, "damagePerTick", phase == "2" ? 4.0 : 3.0, "activeTicks", 12);
    }
    if (abilityId == "retreat_dash") {
        return AbilityAPI.params("telegraphColor", 0xC0FF3030, "distance", 6.0);
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
        npc.say("§c§lЗа Сигмара! Ни пощады еретикам!");
        try {
            npc.getWorld().spawnParticle("minecraft:explosion_emitter",
                npc.getX(), npc.getY() + 1.5, npc.getZ(), 0, 0, 0, 0, 1);
        } catch (e) {}
    }
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
