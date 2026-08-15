var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");
var NecroCombat = Java.type("noppes.npcs.abilities.event.NecromancerCombatHandler");
var ScriptData = Java.type("noppes.npcs.script.ScriptDataUtil");

// =========================
// НАСТРОЙКИ
// =========================
// Пассивные лучи неуязвимости: EntityNecroBeam (Java) — удлинённые прямоугольные
// зоны на земле + партиклы. Углы: шаг 360/N (при 3 лучах ровно 120°).
// Вращение ~0.75°/тик. Число лучей растёт с убийствами сфер (1→2→3).
// Лучи есть ТОЛЬКО в бою (есть attack target) и НЕ в уязвимости (stun).
// Без агро / во время vulnerability — лучей нет.
// Аура неуязвимости (spawnNecroInvuln) тоже гасится на время stun.
var TIMER_ID = 861;

var CAST_INTERVAL = 50;
var NEXT_CAST_KEY = "necro_next_cast";
var LAST_ABILITY_KEY = "necro_last_ability";
var CD_PREFIX = "necro_cd_";

var CLONE_TAB_KEY = "necro_clone_tab";
var SPHERE_CLONE_NAME_KEY = "necro_sphere_clone";
var SKELETON_CLONE_NAME_KEY = "necro_skeleton_clone";

var VOLLEY_ID = "necro_volley";
var RINGS_ID = "necro_rings";

var CLONE_TAB = 1;
var SPHERE_CLONE_NAME = "Сфера некроманта";
var SKELETON_CLONE_NAME = "Скелет некроманта";

var VOLLEY_COOLDOWN = 160;
var RINGS_COOLDOWN = 100;

// Блоки «ворот»: воздух пока босс жив, wfm:empire_brick после смерти.
var GATE_BLOCK = "wfm:empire_brick";
var GATE_STATE_KEY = "necro_gate_dead";
var GATE_POSITIONS = [
    [24196, 201, -60367],
    [24197, 201, -60367],
    [24197, 201, -60368],
    [24198, 201, -60368],
    [24196, 200, -60371],
    [24197, 200, -60371],
    [24197, 200, -60372],
    [24198, 200, -60372],
    [24196, 199, -60375],
    [24197, 199, -60375],
    [24197, 199, -60376],
    [24198, 199, -60376]
];

function init(event) {
    configureBoss(event.npc);
    NecroCombat.initBoss(event.npc);
    syncGateBlocks(event.npc.getWorld(), event.npc.getStoreddata(), false);
    startTimers(event.npc);
}

function timer(event) {
    if (event.id != TIMER_ID) return;

    var npc = event.npc;
    if (!npc.isAlive()) {
        AbilityAPI.cancel(npc);
        syncGateBlocks(npc.getWorld(), npc.getStoreddata(), true);
        return;
    }

    configureBoss(npc);
    NecroCombat.initBoss(npc);
    syncGateBlocks(npc.getWorld(), npc.getStoreddata(), false);

    if (AbilityAPI.isBusy(npc)) return;
    if (NecroCombat.isStunned(npc)) return;

    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) return;

    var world = npc.getWorld();
    var data = npc.getStoreddata();
    var now = world.getTotalTime();
    if (now < getInt(data, NEXT_CAST_KEY)) return;

    var abilityId = pickAbility(npc, data, now);
    if (abilityId == null) return;

    var started = startAbility(npc, target, abilityId);
    if (!started) return;

    data.put(LAST_ABILITY_KEY, abilityId);
    data.put(NEXT_CAST_KEY, String(now + CAST_INTERVAL));
    data.put(CD_PREFIX + abilityId, String(now + getCooldown(abilityId)));
}

function damaged(event) {
    if (event.npc == null || !event.npc.isAlive()) return;
    if (NecroCombat.isDamageBlocked(event.npc)) {
        try { event.setCanceled(true); } catch (e) {}
    }
}

function targetLost(event) {
    AbilityAPI.cancel(event.npc);
    NecroCombat.onTargetLost(event.npc);
}

function died(event) {
    AbilityAPI.cancel(event.npc);
    NecroCombat.cleanupBoss(event.npc);
    syncGateBlocks(event.npc.getWorld(), event.npc.getStoreddata(), true);
}

function syncGateBlocks(world, data, bossDead) {
    if (world == null) return;
    var wantDead = bossDead ? "1" : "0";
    if (data != null && String(data.get(GATE_STATE_KEY)) == wantDead) return;

    var i;
    for (i = 0; i < GATE_POSITIONS.length; i++) {
        var p = GATE_POSITIONS[i];
        if (bossDead) {
            world.setBlock(p[0], p[1], p[2], GATE_BLOCK, 0);
        } else {
            world.removeBlock(p[0], p[1], p[2]);
        }
    }
    if (data != null) data.put(GATE_STATE_KEY, wantDead);
}

function configureBoss(npc) {
    var data = npc.getStoreddata();
    ScriptData.setFlag(data, "necro_boss", true);
    ScriptData.putInt(data, CLONE_TAB_KEY, CLONE_TAB);
    ScriptData.putString(data, SPHERE_CLONE_NAME_KEY, SPHERE_CLONE_NAME);
    ScriptData.putString(data, SKELETON_CLONE_NAME_KEY, SKELETON_CLONE_NAME);
}

function pickAbility(npc, data, now) {
    var options = [];
    if (!NecroCombat.hasLivingSpheres(npc) && isCooldownReady(data, now, VOLLEY_ID)) {
        options.push(VOLLEY_ID);
    }
    if (isCooldownReady(data, now, RINGS_ID)) {
        options.push(RINGS_ID);
    }
    if (options.length == 0) return null;
    if (options.length == 1) return options[0];

    var last = String(data.get(LAST_ABILITY_KEY));
    if (last == VOLLEY_ID) return RINGS_ID;
    if (last == RINGS_ID) return VOLLEY_ID;
    return Math.random() < 0.5 ? VOLLEY_ID : RINGS_ID;
}

function startAbility(npc, target, abilityId) {
    if (abilityId == VOLLEY_ID) {
        return AbilityAPI.start(npc, VOLLEY_ID, target, AbilityAPI.params(
            "shots", 3,
            "spreadRadius", 16.0,
            "chargeTicks", 20,
            "activeTicks", 18,
            "telegraph", 0
        ));
    }
    if (abilityId == RINGS_ID) {
        return AbilityAPI.start(npc, RINGS_ID, target, AbilityAPI.params(
            "chargeTicks", 20,
            "damage", 10.0,
            "telegraph", 0
        ));
    }
    return false;
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
    if (abilityId == VOLLEY_ID) return VOLLEY_COOLDOWN;
    if (abilityId == RINGS_ID) return RINGS_COOLDOWN;
    return 80;
}

function isCooldownReady(data, now, abilityId) {
    var key = CD_PREFIX + abilityId;
    if (!data.has(key)) return true;
    return now >= getInt(data, key);
}

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)), 10) || 0;
}
