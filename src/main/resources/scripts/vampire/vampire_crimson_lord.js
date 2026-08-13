/**
 * Босс: Кровавый лорд.
 *
 * Пассивка: +25 HP с каждой автоатаки.
 * Абилки (Java AbilityAPI):
 *   vampire_whirl_slash  — круговой удар, +100 HP за цель
 *   dash                 — рывок к цели (как у других боссов)
 *   vampire_crimson_bats — 2 мыши (клон "Vampire Crimson Bat"), +15 HP боссу за тычку мыши
 *   vampire_blood_ring   — кольцо-аура 10с (следует за боссом, урон в зоне)
 *
 * Фаза 2 (≤50% HP, one-way): скорость ×2, charge и интервал каста /2, crimson-партиклы.
 *
 * GUI: HP ~1000, melee, walking speed ~5, BipedModel.
 * Clone tab 1: имя "Vampire Crimson Bat" (мелкий, быстрый melee).
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");
var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();

var TIMER_ID = 791;
var PHASE_CHECK_ID = 792;

var CAST_INTERVAL_PHASE1 = 70;
var CAST_INTERVAL_PHASE2 = 35;

var PHASE_KEY = "vcl_phase";
var NEXT_CAST_KEY = "vcl_next_cast";
var LAST_ABILITY_KEY = "vcl_last_ability";
var BASE_SPEED_KEY = "vcl_base_speed";
var CD_PREFIX = "vcl_cd_";

var WHIRL_ID = "vampire_whirl_slash";
var DASH_ID = "dash";
var BATS_ID = "vampire_crimson_bats";
var RING_ID = "vampire_blood_ring";
var BAT_TAG = "vampire_crimson_bat";

var MELEE_HEAL = 25;
var BAT_MAX_NEAR = 2;
var RAGE_SPEED_CAP = 10;

var COOLDOWNS = {};
COOLDOWNS[WHIRL_ID] = 55;
COOLDOWNS[DASH_ID] = 70;
COOLDOWNS[BATS_ID] = 180;
COOLDOWNS[RING_ID] = 200;

var QUOTES_PHASE1 = [
    "Твоя кровь будет моей.",
    "Смертные так легко истекают.",
    "Подойди ближе — я голоден."
];
var QUOTES_PHASE2 = [
    "Алая ярость не отступит!",
    "Пей, пока сердце бьётся!",
    "Кровь зовёт — и я отвечаю!"
];

function init(event) {
    var npc = event.npc;
    var data = npc.getStoreddata();
    data.put(PHASE_KEY, "1");
    data.put(LAST_ABILITY_KEY, "");
    try {
        data.put(BASE_SPEED_KEY, String(npc.getAi().getWalkingSpeed()));
    } catch (e) {
        data.put(BASE_SPEED_KEY, "5");
    }
    startTimers(npc);
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

    var data = npc.getStoreddata();
    if (String(data.get(PHASE_KEY)) == "2") {
        tickRageVfx(npc);
    }

    if (AbilityAPI.isBusy(npc)) return;

    var world = npc.getWorld();
    var now = world.getTotalTime();
    if (now < getInt(data, NEXT_CAST_KEY)) return;

    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) return;

    var phase = String(data.get(PHASE_KEY));
    var abilityId = pickAbility(npc, target, data, now);
    if (abilityId == null) return;

    var started = AbilityAPI.start(npc, abilityId, target, buildParams(abilityId, phase));
    if (!started) return;

    data.put(NEXT_CAST_KEY, String(now + getCastInterval(phase)));
    data.put(LAST_ABILITY_KEY, abilityId);
    data.put(CD_PREFIX + abilityId, String(now + getCooldown(abilityId)));

    if (Math.random() < (phase == "2" ? 0.4 : 0.28)) {
        sayQuote(npc, phase);
    }
}

function meleeAttack(event) {
    var npc = event.npc;
    if (npc == null || !npc.isAlive()) return;

    var maxHp = npc.getMaxHealth();
    var cur = npc.getHealth();
    if (maxHp <= 0 || cur >= maxHp) return;

    var next = cur + MELEE_HEAL;
    if (next > maxHp) next = maxHp;
    npc.setHealth(next);
}

function pickAbility(npc, target, data, now) {
    var dist = flatDistance(npc, target);
    var last = String(data.get(LAST_ABILITY_KEY));
    var batsAlive = countBats(npc);

    if (batsAlive < BAT_MAX_NEAR && isCooldownReady(data, now, BATS_ID) && last != BATS_ID) {
        if (Math.random() < 0.32 || dist > 8.0) {
            return BATS_ID;
        }
    }

    if (dist >= 6.0 && dist <= 16.0 && isCooldownReady(data, now, DASH_ID) && last != DASH_ID) {
        return DASH_ID;
    }

    if (dist < 5.5 && isCooldownReady(data, now, WHIRL_ID) && last != WHIRL_ID) {
        return WHIRL_ID;
    }

    if (dist >= 3.0 && dist <= 14.0 && isCooldownReady(data, now, RING_ID) && last != RING_ID) {
        return RING_ID;
    }

    if (isCooldownReady(data, now, WHIRL_ID)) return WHIRL_ID;
    if (isCooldownReady(data, now, DASH_ID)) return DASH_ID;
    if (isCooldownReady(data, now, RING_ID)) return RING_ID;
    if (batsAlive < BAT_MAX_NEAR && isCooldownReady(data, now, BATS_ID)) return BATS_ID;
    return null;
}

function buildParams(abilityId, phase) {
    var tg = 0xC0FF3030;
    if (abilityId == WHIRL_ID) {
        return AbilityAPI.params(
            "telegraphColor", tg,
            "chargeTicks", chargeOf(8, phase),
            "damage", 18.0,
            "radius", 4.5,
            "knockback", 1.2,
            "knockbackY", 0.3,
            "lifeStealPerHit", 100.0
        );
    }
    if (abilityId == DASH_ID) {
        return AbilityAPI.params(
            "telegraphColor", tg,
            "damage", 12.0,
            "distance", 16.0,
            "chargeTicks", chargeOf(10, phase),
            "activeTicks", 7,
            "knockback", 1.6,
            "knockbackY", 0.35,
            "hitRadius", 1.6
        );
    }
    if (abilityId == BATS_ID) {
        return AbilityAPI.params(
            "telegraphColor", tg,
            "chargeTicks", chargeOf(8, phase),
            "radius", 3.0,
            "summonCount", 2,
            "summonRadius", 2.5,
            "maxSummonedNearBoss", 2,
            "cloneTab", 1,
            "cloneName", "Vampire Crimson Bat",
            "lifeStealPerHit", 15.0
        );
    }
    if (abilityId == RING_ID) {
        return AbilityAPI.params(
            "telegraphColor", tg,
            "chargeTicks", chargeOf(8, phase),
            "radius", 7.0,
            "innerRadius", 4.5,
            "damage", 3.0,
            "zoneTicks", 200,
            "damageInterval", 10,
            "zoneColor", 0xC0180810
        );
    }
    return null;
}

function updatePhase(npc) {
    var data = npc.getStoreddata();
    var maxHealth = npc.getMaxHealth();
    if (maxHealth <= 0) return;

    if (String(data.get(PHASE_KEY)) != "2" && npc.getHealth() / maxHealth <= 0.5) {
        data.put(PHASE_KEY, "2");
        npc.say("§4§lКровавая ярость пробудилась!");
        try {
            var world = npc.getWorld();
            var x = npc.getX();
            var y = npc.getY() + 1.2;
            var z = npc.getZ();
            world.spawnParticle("minecraft:crimson_spore", x, y, z, 0.8, 0.5, 0.8, 0.08, 24);
            world.spawnParticle("minecraft:entity_effect", x, y, z, 0.9, 0.1, 0.1, 0, 12);
            world.spawnParticle("minecraft:damage_indicator", x, y, z, 0, 0.2, 0, 0, 10);
        } catch (e) {}
    }

    if (String(data.get(PHASE_KEY)) == "2" && !AbilityAPI.isBusy(npc)) {
        applyRageSpeed(npc, data);
    }
}

function applyRageSpeed(npc, data) {
    try {
        var ai = npc.getAi();
        var base = getInt(data, BASE_SPEED_KEY);
        if (base <= 0) base = ai.getWalkingSpeed();
        var next = base * 2;
        if (next > RAGE_SPEED_CAP) next = RAGE_SPEED_CAP;
        ai.setWalkingSpeed(next);
    } catch (e) {}
}

function tickRageVfx(npc) {
    try {
        var world = npc.getWorld();
        if (world.getTotalTime() % 4 != 0) return;
        var x = npc.getX();
        var y = npc.getY() + 0.9;
        var z = npc.getZ();
        world.spawnParticle("minecraft:crimson_spore", x, y, z, 0.55, 0.45, 0.55, 0.04, 8);
        world.spawnParticle("minecraft:entity_effect", x, y, z, 0.9, 0.08, 0.08, 0, 4);
    } catch (e) {}
}

function countBats(npc) {
    var n = 0;
    try {
        var pos = NpcAPI.getIPos(npc.getX(), npc.getY(), npc.getZ());
        var list = npc.getWorld().getNearbyEntities(pos, 16, 2);
        var i;
        for (i = 0; i < list.length; i++) {
            if (list[i] != null && list[i].hasTag(BAT_TAG)) n++;
        }
    } catch (e) {}
    return n;
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

function chargeOf(base, phase) {
    if (phase != "2") return base;
    var v = Math.floor(base / 2);
    if (v < 1) v = 1;
    return v;
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
