/**
 * Босс: Отродье.
 * Механика — Java AbilityAPI. JS: кулдауны, фазы, выбор скилла, forced-цепочка, реактивные лужи.
 *
 * Фазы:
 *   1 — HP > PHASE2_HP: vomit → fecal_wave, devour (с CD)
 *   2 — HP ≤ PHASE2_HP: только devour_dash без кулдауна / cast interval
 *       если HP снова > PHASE2_HP — возврат в фазу 1
 *
 * Лужи (разлетающийся кал): разовые пороги 1000/800/600/400/200 —
 * каждый порог срабатывает один раз за жизнь босса (хил не сбрасывает).
 * Windup: рык + ярко-красная telegraph-зона → затем зелёные лужи.
 *
 * Кит:
 *   otrodie_hell_vomit / otrodie_fecal_wave / otrodie_devour_dash
 *   otrodie_spreading_filth — OtrodieSpreadingFilthAbility.trigger (не AbilityAPI.start)
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");
var OtrodieSpreadingFilthAbility = Java.type("noppes.npcs.abilities.impl.OtrodieSpreadingFilthAbility");
var TelegraphAPI = Java.type("noppes.npcs.telegraph.TelegraphAPI");

var TIMER_ID = 791;
var CAST_INTERVAL = 70;

var OTRODIE_BOSS_FLAG = "otrodie_boss";
var HP_MARK_KEY = "ot_hp_mark";
var FORCED_ABILITY_KEY = "ot_forced_ability";
var NEXT_CAST_KEY = "ot_next_cast";
var LAST_ABILITY_KEY = "ot_last_ability";
var PHASE_KEY = "ot_phase";
var CD_PREFIX = "ot_cd_";
var PUDDLE_USED_KEY = "ot_puddle_used";
var PUDDLE_PENDING_KEY = "ot_puddle_pending";
var PUDDLE_READY_AT_KEY = "ot_puddle_ready";
var PUDDLE_TG_KEY = "ot_puddle_tg";

var VOMIT_ID = "otrodie_hell_vomit";
var FECAL_ID = "otrodie_fecal_wave";
var DEVOUR_ID = "otrodie_devour_dash";

var PHASE2_HP = 100;
var PUDDLE_THRESHOLDS = [1000, 800, 600, 400, 200];
var PUDDLE_WINDUP_TICKS = 32;
var PUDDLE_WARN_RADIUS = 5.0;

var ZONE_RED = 0xC0FF3030;
var ZONE_RED_BRIGHT = 0xE0FF1010;
var ZONE_GREEN = 0xC0B8FF00;

var COOLDOWNS = {};
COOLDOWNS[VOMIT_ID] = 180;
COOLDOWNS[FECAL_ID] = 0;
COOLDOWNS[DEVOUR_ID] = 110;

function init(event) {
    var npc = event.npc;
    var data = npc.getStoreddata();
    data.put(OTRODIE_BOSS_FLAG, "1");
    data.put(FORCED_ABILITY_KEY, "");
    data.put(LAST_ABILITY_KEY, "");
    data.put(NEXT_CAST_KEY, "0");
    data.put(PUDDLE_PENDING_KEY, "0");
    data.put(PUDDLE_READY_AT_KEY, "0");
    data.put(PUDDLE_TG_KEY, "");
    data.put(HP_MARK_KEY, String(npc.getHealth()));
    data.put(PHASE_KEY, npc.getHealth() <= PHASE2_HP ? "2" : "1");
    initUsedThresholds(data, npc.getHealth());
    startTimers(npc);
}

function timer(event) {
    if (event.id != TIMER_ID) return;

    var npc = event.npc;
    if (!npc.isAlive()) {
        AbilityAPI.cancel(npc);
        clearPuddleWindup(npc);
        return;
    }

    tickPuddleWindup(npc);

    if (AbilityAPI.isBusy(npc)) return;

    var data = npc.getStoreddata();
    var now = npc.getWorld().getTotalTime();
    data.put(HP_MARK_KEY, String(npc.getHealth()));
    var phase = updatePhase(npc, data);

    var forced = String(data.get(FORCED_ABILITY_KEY));

    if (phase == "2") {
        data.put(FORCED_ABILITY_KEY, "");
        var target2 = npc.getAttackTarget();
        if (target2 == null || !target2.isAlive()) return;
        var started2 = AbilityAPI.start(npc, DEVOUR_ID, target2, buildParams(DEVOUR_ID, npc, target2));
        if (!started2) return;
        data.put(LAST_ABILITY_KEY, DEVOUR_ID);
        data.put(NEXT_CAST_KEY, "0");
        return;
    }

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

function updatePhase(npc, data) {
    var hp = npc.getHealth();
    var phase = hp <= PHASE2_HP ? "2" : "1";
    var prev = String(data.get(PHASE_KEY));
    if (prev != phase) {
        data.put(PHASE_KEY, phase);
        if (phase == "2") {
            data.put(FORCED_ABILITY_KEY, "");
            data.put(NEXT_CAST_KEY, "0");
        }
    } else {
        data.put(PHASE_KEY, phase);
    }
    return phase;
}

function pickAbility(npc, target, data, now) {
    var dist = flatDistance(npc, target);
    var last = String(data.get(LAST_ABILITY_KEY));

    if (dist >= 3.5 && dist <= 12.0 && isCooldownReady(data, now, DEVOUR_ID) && last != DEVOUR_ID) {
        if (Math.random() < 0.55) return DEVOUR_ID;
    }

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
            "radius", 3.5,
            "damage", 3.0,
            "damageInterval", 10,
            "breakDamage", 100.0,
            "arcHeight", 5.0,
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
    updatePhase(npc, data);

    var prevHp = getFloat(data, HP_MARK_KEY);
    var currentHp = npc.getHealth();
    try {
        var dmg = parseFloat(String(event.damage));
        if (dmg > 0) {
            var after = currentHp - dmg;
            if (after < currentHp) currentHp = after;
        }
    } catch (e) {}
    if (currentHp < 0) currentHp = 0;

    if (prevHp <= 0) {
        data.put(HP_MARK_KEY, String(currentHp));
        return;
    }

    tryCrossPuddleThresholds(npc, data, prevHp, currentHp);
    data.put(HP_MARK_KEY, String(currentHp));
}

function tryCrossPuddleThresholds(npc, data, prevHp, currentHp) {
    if (String(data.get(PUDDLE_PENDING_KEY)) == "1") return;

    var crossed = [];
    for (var i = 0; i < PUDDLE_THRESHOLDS.length; i++) {
        var t = PUDDLE_THRESHOLDS[i];
        if (isThresholdUsed(data, t)) continue;
        if (prevHp > t && currentHp <= t) {
            crossed.push(t);
        }
    }
    if (crossed.length == 0) return;

    for (var j = 0; j < crossed.length; j++) {
        markThresholdUsed(data, crossed[j]);
    }
    startPuddleWindup(npc, data);
}

function startPuddleWindup(npc, data) {
    var world = npc.getWorld();
    var now = world.getTotalTime();

    data.put(PUDDLE_PENDING_KEY, "1");
    data.put(PUDDLE_READY_AT_KEY, String(now + PUDDLE_WINDUP_TICKS));

    try {
        world.playSoundAt(npc.getPos(), "minecraft:entity.ravager.roar", 1.25, 0.45);
        world.playSoundAt(npc.getPos(), "minecraft:entity.ravager.roar", 0.9, 0.35);
    } catch (e) {}

    try {
        var tid = TelegraphAPI.circle(
            npc,
            npc.getX(),
            npc.getY(),
            npc.getZ(),
            PUDDLE_WARN_RADIUS,
            PUDDLE_WINDUP_TICKS,
            ZONE_RED_BRIGHT
        );
        data.put(PUDDLE_TG_KEY, tid != null ? String(tid) : "");
    } catch (e2) {
        data.put(PUDDLE_TG_KEY, "");
    }
}

function tickPuddleWindup(npc) {
    var data = npc.getStoreddata();
    if (String(data.get(PUDDLE_PENDING_KEY)) != "1") return;

    var now = npc.getWorld().getTotalTime();
    if (now < getInt(data, PUDDLE_READY_AT_KEY)) return;

    clearPuddleTelegraph(data);
    data.put(PUDDLE_PENDING_KEY, "0");
    data.put(PUDDLE_READY_AT_KEY, "0");

    OtrodieSpreadingFilthAbility.trigger(npc, AbilityAPI.params(
        "zoneColor", ZONE_GREEN,
        "radius", 5.0,
        "hitRadius", 2.6
    ));
}

function clearPuddleWindup(npc) {
    var data = npc.getStoreddata();
    clearPuddleTelegraph(data);
    data.put(PUDDLE_PENDING_KEY, "0");
    data.put(PUDDLE_READY_AT_KEY, "0");
}

function clearPuddleTelegraph(data) {
    var tid = String(data.get(PUDDLE_TG_KEY));
    if (tid.length > 0) {
        try { TelegraphAPI.remove(tid); } catch (e) {}
    }
    data.put(PUDDLE_TG_KEY, "");
}

function initUsedThresholds(data, hp) {
    var used = "";
    for (var i = 0; i < PUDDLE_THRESHOLDS.length; i++) {
        var t = PUDDLE_THRESHOLDS[i];
        if (hp <= t) {
            if (used.length > 0) used += ";";
            used += String(t);
        }
    }
    data.put(PUDDLE_USED_KEY, used);
}

function isThresholdUsed(data, threshold) {
    var used = String(data.get(PUDDLE_USED_KEY));
    if (used.length == 0) return false;
    var parts = used.split(";");
    var key = String(threshold);
    for (var i = 0; i < parts.length; i++) {
        if (parts[i] == key) return true;
    }
    return false;
}

function markThresholdUsed(data, threshold) {
    if (isThresholdUsed(data, threshold)) return;
    var used = String(data.get(PUDDLE_USED_KEY));
    if (used.length == 0) {
        data.put(PUDDLE_USED_KEY, String(threshold));
    } else {
        data.put(PUDDLE_USED_KEY, used + ";" + String(threshold));
    }
}

function targetLost(event) {
    AbilityAPI.cancel(event.npc);
    event.npc.getStoreddata().put(FORCED_ABILITY_KEY, "");
}

function died(event) {
    AbilityAPI.cancel(event.npc);
    clearPuddleWindup(event.npc);
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
