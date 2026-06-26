/**
 * Босс: зомби огр-свинцеплюй.
 *
 * Механика — новые Java-абилки через AbilityAPI:
 * - zombie_ogre_leadbelcher_slam: удар по земле перед собой (AoE + слепота)
 * - zombie_ogre_leadbelcher_artillery: артиллерийский залп ядром свинцеплюя по позициям игроков
 * - zombie_ogre_leadbelcher_trample: бежит вперёд, распинывая всех по пути (урон+откид+слепота)
 *
 * Скрипт только решает КОГДА кастовать и с какими params.
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");

var TIMER_ID = 771;

var MAIN_INTERVAL_TICKS = 120;
var NEARSHOT_INTERVAL_TICKS = 80;

var MAIN_NEXT_CAST_KEY = "zolb_main_next";
var NEARSHOT_NEXT_CAST_KEY = "zolb_near_next";

var SLAM_ID = "zombie_ogre_leadbelcher_slam";
var ARTILLERY_ID = "zombie_ogre_leadbelcher_artillery";
var TRAMPLE_ID = "zombie_ogre_leadbelcher_trample";

var GUN_ITEM_ID = "wfm:ogre_leadbelcher_gun";

function init(event) {
    startTimer(event.npc);
}

function timer(event) {
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

    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) return;

    var dist = flatDist(npc.getX(), npc.getZ(), target.getX(), target.getZ());

    // Периодический одиночный выстрел в ближайшего игрока (если рядом).
    if (now >= getInt(data, NEARSHOT_NEXT_CAST_KEY) && dist <= 16.0) {
        AbilityAPI.start(npc, ARTILLERY_ID, target, AbilityAPI.params(
            "projectileItem", GUN_ITEM_ID,
            "shots", 1,
            "distance", 0.0,
            "damage", 14.0,
            "accuracy", 3,
            "maxRange", 22.0,
            "chargeTicks", 8,
            "activeTicks", 4,
            "radius", 0.8
        ));
        data.put(NEARSHOT_NEXT_CAST_KEY, String(now + NEARSHOT_INTERVAL_TICKS));
        return;
    }

    // Основные атаки по кулдауну.
    if (now < getInt(data, MAIN_NEXT_CAST_KEY)) return;

    var chosen = chooseMainAbility(dist);
    AbilityAPI.start(npc, chosen, target, buildParamsFor(chosen, dist));
    data.put(MAIN_NEXT_CAST_KEY, String(now + MAIN_INTERVAL_TICKS));
}

function targetLost(event) {
    AbilityAPI.cancel(event.npc);
}

function died(event) {
    AbilityAPI.cancel(event.npc);
}

function chooseMainAbility(dist) {
    if (dist <= 4.0) return SLAM_ID;
    if (dist <= 10.0) return TRAMPLE_ID;
    return ARTILLERY_ID;
}

function buildParamsFor(abilityId, dist) {
    if (abilityId == SLAM_ID) {
        return AbilityAPI.params(
            "damage", 12.0,
            "radius", 3.0,
            "knockback", 0.9,
            "knockbackY", 0.15,
            "effectType", "blindness",
            "effectDuration", 30,
            "effectAmplifier", 0,
            "chargeTicks", 12
        );
    }
    if (abilityId == TRAMPLE_ID) {
        return AbilityAPI.params(
            "damage", 4.0,
            "hitRadius", 1.9,
            "knockback", 1.15,
            "knockbackY", 0.25,
            "effectType", "blindness",
            "effectDuration", 30,
            "effectAmplifier", 0,
            "distance", dist < 8.0 ? 9.0 : 12.0,
            "chargeTicks", 10,
            "activeTicks", 18
        );
    }
    // ARTILLERY_ID
    return AbilityAPI.params(
        "projectileItem", GUN_ITEM_ID,
        "shots", 6,
        "distance", 12.0,
        "damage", 18.0,
        "accuracy", 3,
        "maxRange", 32.0,
        "chargeTicks", 14,
        "activeTicks", 6,
        "radius", 1.6
    );
}

function startTimer(npc) {
    var timers = npc.getTimers();
    if (timers == null) return;
    if (typeof timers.forceStart == "function") {
        timers.forceStart(TIMER_ID, 1, true);
    } else {
        timers.start(TIMER_ID, 1, true);
    }
}

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}

function flatDist(x1, z1, x2, z2) {
    var dx = x1 - x2;
    var dz = z1 - z2;
    return Math.sqrt(dx * dx + dz * dz);
}

