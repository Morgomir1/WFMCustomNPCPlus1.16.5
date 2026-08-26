/**
 * Constant Drachenfels — ДУША (rework).
 *
 * Скиллы: dark_blast, hp_ritual, ghost_parasite
 * Арена / bond / flame-carousel (4 зоны, авто при агре): DrachenfelsEncounterAPI
 * Flame — не каст, а encounter-hazard; ведёт Душа (координаты FLAME0..3).
 * 4 зоны непрерывно едут по кругу FLAME0→1→2→3→0 (~4с на ребро).
 * Нужен актуальный jar с DrachenfelsEncounterHelper (не только вставка JS в GUI).
 * Проверка в survival/adventure: креатив/спек не считаются агро для столбов.
 * Доски после dark blast чинятся в Java при потере агро пары (~2.5с).
 *
 * GUI: вставить этот скрипт только на Spirit-NPC.
 * Тело: drachenfels_body_rework.js
 *
 * AI: OnAttack=Отступать. Navigation=Flying. Высота полёта = Y спавна + RITUAL_SPIRIT_DY (выше тела).
 * Клон призрака (tab 1): "Drachenfels Ghost Parasite"
 * После спавна клону в Java выключается респавн (NecromancerMinionHelper.disableRespawn).
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");
var Encounter = Java.type("noppes.npcs.abilities.DrachenfelsEncounterAPI");

var NPC_ROLE = "spirit";

// ====== КООРДИНАТЫ АРЕНЫ (те же, что у body) ======
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

var DARK_BLAST = "drachenfels_dark_blast";
var GHOST_PARASITE = "drachenfels_ghost_parasite";
var HP_RITUAL = "df_hp_ritual";
var CLONE_GHOST = "Drachenfels Ghost Parasite";

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
        // Повторно пишем арену: init мог не сработать / партнёр без df_cfg_flames
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
    if (AbilityAPI.isBusy(npc)) return;

    var data = npc.getStoreddata();
    var now = npc.getWorld().getTotalTime();
    var phase = String(Encounter.getPhase(npc));
    var forced = String(data.get(FORCED_KEY));

    if (forced.length == 0 && now < getInt(data, NEXT_CAST_KEY)) return;

    var target = Encounter.ensureCombatTarget(npc);
    if (target == null || !target.isAlive()) return;

    var abilityId = null;
    if (forced.length > 0) {
        if (forced == HP_RITUAL) {
            data.put(FORCED_KEY, "");
            if (Encounter.startHpRitual(npc)) {
                data.put(LAST_KEY, HP_RITUAL);
                data.put(NEXT_CAST_KEY, String(now + getCastInterval(phase)));
            }
            return;
        }
        if (!isSpiritAbility(forced)) {
            data.put(FORCED_KEY, "");
            return;
        }
        if (isCooldownReady(data, now, forced)) abilityId = forced;
        else return;
    } else {
        abilityId = pickAbility(npc, target, phase, data, now);
    }
    if (abilityId == null) return;

    if (abilityId == HP_RITUAL) {
        if (!Encounter.startHpRitual(npc)) return;
        data.put(FORCED_KEY, "");
        data.put(LAST_KEY, HP_RITUAL);
        data.put(CD_PREFIX + HP_RITUAL, String(now + 700));
        data.put(NEXT_CAST_KEY, String(now + getCastInterval(phase)));
        return;
    }

    var started = AbilityAPI.start(npc, abilityId, target, buildParams(abilityId));
    if (!started) return;

    data.put(FORCED_KEY, "");
    data.put(LAST_KEY, abilityId);
    data.put(CD_PREFIX + abilityId, String(now + getCooldown(abilityId, phase)));
    data.put(NEXT_CAST_KEY, String(now + getCastInterval(phase)));
}

function isSpiritAbility(id) {
    return id == DARK_BLAST || id == GHOST_PARASITE || id == HP_RITUAL;
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

    if (canTryRitual(npc, data, now, last) && Math.random() < (p2 ? 0.32 : 0.22)) {
        return HP_RITUAL;
    }
    if (dist >= 4.0 && dist <= 16.0 && isCooldownReady(data, now, DARK_BLAST) && last != DARK_BLAST) {
        if (Math.random() < (p2 ? 0.5 : 0.38)) return DARK_BLAST;
    }
    if (dist >= 5.0 && dist <= 18.0 && isCooldownReady(data, now, GHOST_PARASITE) && last != GHOST_PARASITE) {
        if (Math.random() < (p2 ? 0.45 : 0.32)) return GHOST_PARASITE;
    }
    if (isCooldownReady(data, now, DARK_BLAST) && last != DARK_BLAST) return DARK_BLAST;
    if (isCooldownReady(data, now, GHOST_PARASITE)) return GHOST_PARASITE;
    if (canTryRitual(npc, data, now, last)) return HP_RITUAL;
    return null;
}

function canTryRitual(npc, data, now, last) {
    if (last == HP_RITUAL) return false;
    if (!isCooldownReady(data, now, HP_RITUAL)) return false;
    if (Encounter.isRitualActive(npc) || Encounter.isDowned(npc)) return false;
    return true;
}

function buildParams(abilityId) {
    if (abilityId == DARK_BLAST) {
        return AbilityAPI.params(
            "damage", 15.0,
            "radius", 3.5,
            "chargeTicks", 32,
            "telegraph", 0,
            "telegraphColor", 0xC0FF3030
        );
    }
    if (abilityId == GHOST_PARASITE) {
        // telegraph=0: не рисуем line-коридор от distance.
        // Круг под целью — Java GrabHandler на фазе SEEK (пока дух летит).
        return AbilityAPI.params(
            "damage", 2.0,
            "damageInterval", 20,
            "chargeTicks", 28,
            "activeTicks", 600,
            "distance", 40.0,
            "landRadius", 2.0,
            "telegraph", 0,
            "telegraphColor", 0xC0FF3030,
            "cloneTab", 1,
            "cloneName", CLONE_GHOST
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
    if (abilityId == DARK_BLAST) return bond ? 80 : (p2 ? 100 : 125);
    if (abilityId == GHOST_PARASITE) return bond ? 110 : (p2 ? 140 : 180);
    if (abilityId == HP_RITUAL) return 700;
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
