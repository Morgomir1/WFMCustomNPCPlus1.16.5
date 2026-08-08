/**
 * Призрак-камикадзе: агро → подлёт сквозь блоки → орбита → удар + knockback → смерть.
 *
 * Механика — Java AbilityAPI id "ghost_orbit_slam".
 * Скрипт только стартует абилку при наличии цели.
 *
 * GUI NPC:
 * - Navigation / Movement: Flying
 * - OnAttack: Мстить (Revenge)
 * - Hitbox: None (скрипт также ставит hitboxState=1 в init)
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");

var NAV_FLYING = 1;
var HITBOX_NONE = 1;
var RETALIATE_REVENGE = 0;

function init(event) {
    var npc = event.npc;
    try {
        npc.getAi().setNavigationType(NAV_FLYING);
    } catch (err) {}
    try {
        npc.getAi().setRetaliateType(RETALIATE_REVENGE);
    } catch (err2) {}
}

function tick(event) {
    tryStart(event.npc);
}

function target(event) {
    tryStart(event.npc);
}

function tryStart(npc) {
    if (npc == null || !npc.isAlive()) {
        return;
    }
    if (AbilityAPI.isBusy(npc)) {
        return;
    }

    var attackTarget = npc.getAttackTarget();
    if (attackTarget == null || !attackTarget.isAlive()) {
        return;
    }

    AbilityAPI.start(npc, "ghost_orbit_slam", attackTarget, AbilityAPI.params(
        "approachSpeed", 0.45,
        "radius", 2.5,
        "orbitTicks", 60,
        "orbitSpeed", 8.0,
        "hoverOffset", 1.0,
        "slamTicks", 6,
        "damage", 0.0,
        "knockback", 2.4,
        "knockbackY", 0.55,
        "hitRadius", 1.8
    ));
}

function targetLost(event) {
    // Не отменяем: призрак уже в цикле approach/orbit/slam.
}

function died(event) {
    AbilityAPI.cancel(event.npc);
}
