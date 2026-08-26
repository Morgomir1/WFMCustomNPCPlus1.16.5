/**
 * Constant Drachenfels — ТЕЛО (rework).
 *
 * Скиллы: body_pull → (forced) curse_puddles
 * Арена / bond / flame: DrachenfelsEncounterAPI (столбы ведёт Душа; body только sync арены).
 * Нужен актуальный jar с DrachenfelsEncounterHelper.
 * Доски арены чинит encounter (Java) при потере агро — см. spirit_rework.
 *
 * GUI: вставить этот скрипт только на Body-NPC.
 * Душа: drachenfels_spirit_rework.js
 *
 * AI: OnAttack=Отступать. Navigation=Flying. Высота полёта = Y спавна (не следует за землёй).
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");
var Encounter = Java.type("noppes.npcs.abilities.DrachenfelsEncounterAPI");

var NPC_ROLE = "body";

// ====== КООРДИНАТЫ АРЕНЫ (те же, что у spirit) ======
var RITUAL_X = 24379.5;
var RITUAL_Y = 28.0;
var RITUAL_Z = -60298.5;
var RITUAL_SPIRIT_DY = 8.0;
var FLAME0_X = 24394.5;
var FLAME0_Y = 28.05;
var FLAME0_Z = -60298.5;
var FLAME1_X = 24379.5;
var FLAME1_Y = 28.05;
var FLAME1_Z = -60314.5;
var FLAME2_X = 24362.5;
var FLAME2_Y = 28.05;
var FLAME2_Z = -60298.5;
var FLAME3_X = 24379.5;
var FLAME3_Y = 28.05;
var FLAME3_Z = -60282.5;

var TIMER_CAST = 841;
var TIMER_SLOW = 842;
var CAST_INTERVAL_1 = 80;
var CAST_INTERVAL_2 = 58;
var CAST_INTERVAL_BOND = 48;

var FORCED_KEY = "df_forced_ability";
var LAST_KEY = "df_last_ability";
var NEXT_CAST_KEY = "df_next_cast";
var CD_PREFIX = "df_cd_";

var BODY_PULL = "drachenfels_body_pull";
var BODY_CURSE = "drachenfels_curse_puddles";

function init(event) {
    var npc = event.npc;
    Encounter.init(npc, NPC_ROLE);
    Encounter.configureArena(
        npc,
        RITUAL_X, RITUAL_Y, RITUAL_Z, RITUAL_SPIRIT_DY,
        FLAME0_X, FLAME0_Y, FLAME0_Z,
        FLAME1_X, FLAME1_Y, FLAME1_Z,
        FLAME2_X, FLAME2_Y, FLAME2_Z,
        FLAME3_X, FLAME3_Y, FLAME3_Z
    );
    var data = npc.getStoreddata();
    data.put(LAST_KEY, "");
    data.put(FORCED_KEY, "");
    data.put(NEXT_CAST_KEY, "0");
    startTimers(npc);
}

function tick(event) {
    startTimers(event.npc);
}

function interact(event) {
    startTimers(event.npc);
}

function timer(event) {
    var npc = event.npc;
    if (!npc.isAlive()) {
        Encounter.cancelAbilities(npc);
        return;
    }

    if (event.id == TIMER_SLOW) {
        Encounter.configureArena(
            npc,
            RITUAL_X, RITUAL_Y, RITUAL_Z, RITUAL_SPIRIT_DY,
            FLAME0_X, FLAME0_Y, FLAME0_Z,
            FLAME1_X, FLAME1_Y, FLAME1_Z,
            FLAME2_X, FLAME2_Y, FLAME2_Z,
            FLAME3_X, FLAME3_Y, FLAME3_Z
        );
        Encounter.tickSlow(npc);
        return;
    }
    if (event.id != TIMER_CAST) return;

    Encounter.tickFast(npc);

    if (Encounter.isBusyForCast(npc)) return;
    if (AbilityAPI.isBusy(npc)) {
        maybeQueueBodyPullFollowUp(npc);
        return;
    }

    var data = npc.getStoreddata();
    var now = npc.getWorld().getTotalTime();
    var phase = String(Encounter.getPhase(npc));
    var forced = String(data.get(FORCED_KEY));

    if (forced.length == 0 && now < getInt(data, NEXT_CAST_KEY)) return;

    var target = Encounter.ensureCombatTarget(npc);
    if (target == null || !target.isAlive()) return;

    var abilityId = null;
    if (forced.length > 0) {
        if (!isBodyAbility(forced)) {
            data.put(FORCED_KEY, "");
            return;
        }
        if (isCooldownReady(data, now, forced)) abilityId = forced;
        else return;
    } else {
        abilityId = pickAbility(npc, target, phase, data, now);
    }
    if (abilityId == null) return;

    var started = AbilityAPI.start(npc, abilityId, target, buildParams(abilityId));
    if (!started) return;

    data.put(FORCED_KEY, "");
    data.put(LAST_KEY, abilityId);
    data.put(CD_PREFIX + abilityId, String(now + getCooldown(abilityId, phase)));
    data.put(NEXT_CAST_KEY, String(now + getCastInterval(phase)));

    if (abilityId == BODY_PULL) {
        data.put(FORCED_KEY, BODY_CURSE);
    }
}

function isBodyAbility(id) {
    return id == BODY_PULL || id == BODY_CURSE;
}

function maybeQueueBodyPullFollowUp(npc) {
    var data = npc.getStoreddata();
    if (String(data.get(LAST_KEY)) != BODY_PULL) return;
    if (String(data.get(FORCED_KEY)) == BODY_CURSE) return;
    if (String(data.get(FORCED_KEY)).length == 0) {
        data.put(FORCED_KEY, BODY_CURSE);
    }
}

function targetLost(event) {
    Encounter.onTargetLost(event.npc);
}

function died(event) {
    Encounter.onDied(event.npc);
}

function damaged(event) {
    // Immortal Bond — DrachenfelsBondHandler
}

function pickAbility(npc, target, phase, data, now) {
    var dist = flatDistance(npc, target);
    var last = String(data.get(LAST_KEY));
    var p2 = phase == "2" || phase == "bond";

    if (dist <= 18.0 && isCooldownReady(data, now, BODY_PULL) && last != BODY_PULL) {
        if (Math.random() < (p2 ? 0.5 : 0.38)) return BODY_PULL;
    }
    if (isCooldownReady(data, now, BODY_CURSE) && last != BODY_CURSE && last != BODY_PULL) {
        if (Math.random() < (p2 ? 0.45 : 0.35)) return BODY_CURSE;
    }
    if (isCooldownReady(data, now, BODY_PULL) && last != BODY_PULL) return BODY_PULL;
    if (isCooldownReady(data, now, BODY_CURSE)) return BODY_CURSE;
    return null;
}

function buildParams(abilityId) {
    if (abilityId == BODY_PULL) {
        return AbilityAPI.params(
            "damage", 15.0,
            "radius", 7.5,
            "maxRange", 18.0,
            "chargeTicks", 16,
            "activeTicks", 36,
            "telegraph", 0,
            "telegraphColor", 0xC0FF3030
        );
    }
    if (abilityId == BODY_CURSE) {
        return AbilityAPI.params(
            "chargeTicks", 24,
            "maxRange", 18.0,
            "summonCount", 3,
            "hitCount", 3,
            "hitRadius", 2.0,
            "zoneTicks", 200,
            "healOnFail", 10.0,
            "spreadRadius", 20.0,
            "telegraph", 0
        );
    }
    return AbilityAPI.params();
}

function getCastInterval(phase) {
    if (phase == "bond") return CAST_INTERVAL_BOND;
    if (phase == "2") return CAST_INTERVAL_2;
    return CAST_INTERVAL_1;
}

function getCooldown(abilityId, phase) {
    var bond = phase == "bond";
    var p2 = phase == "2" || bond;
    if (abilityId == BODY_PULL) return bond ? 150 : (p2 ? 195 : 240);
    if (abilityId == BODY_CURSE) return bond ? 110 : (p2 ? 140 : 180);
    return 120;
}

function startTimers(npc) {
    try {
        var t = npc.getTimers();
        if (t == null) return;
        if (typeof t.forceStart == "function") {
            t.forceStart(TIMER_CAST, 1, true);
            t.forceStart(TIMER_SLOW, 20, true);
        } else {
            if (!t.has(TIMER_CAST)) t.start(TIMER_CAST, 1, true);
            if (!t.has(TIMER_SLOW)) t.start(TIMER_SLOW, 20, true);
        }
    } catch (e) {}
}

function isCooldownReady(data, now, abilityId) {
    return now >= getInt(data, CD_PREFIX + abilityId);
}

function getInt(data, key) {
    try {
        var v = parseInt(String(data.get(key)));
        return isNaN(v) ? 0 : v;
    } catch (e) {
        return 0;
    }
}

function flatDistance(a, b) {
    var dx = a.getX() - b.getX();
    var dz = a.getZ() - b.getZ();
    return Math.sqrt(dx * dx + dz * dz);
}
