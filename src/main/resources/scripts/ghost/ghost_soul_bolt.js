// =====================================================
// Летающий призрак: навесной soul-болт раз в 10 с.
// Траектория как crimson_blob; при попадании — knockback.
// Механика — Java AbilityAPI id "ghost_soul_bolt".
//
// GUI NPC:
// - Navigation / Movement: Flying
// - OnAttack: Мстить (Revenge) или Panic
// =====================================================

var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");

// -------------------------
// НАСТРОЙКИ
// -------------------------
var ABILITY_ID = "ghost_soul_bolt";
var COOLDOWN_TICKS = 200;       // 10 секунд
var MAX_RANGE = 24.0;

var CHARGE_TICKS = 24;          // ~1.2 с dodge-window (telegraph)
var ACTIVE_TICKS = 14;          // полёт по дуге
var ARC_HEIGHT = 5.0;
var LAND_RADIUS = 2.2;          // AoE / telegraph в точке падения
var HIT_RADIUS = 1.4;           // касание по пути полёта
var DAMAGE = 0.0;               // только откидывание
var KNOCKBACK = 2.2;
var KNOCKBACK_Y = 0.5;

var BLOB_PARTICLES = "minecraft:soul_fire_flame,minecraft:soul,minecraft:end_rod,minecraft:smoke";
var LAND_PARTICLES = "minecraft:soul_fire_flame,minecraft:soul,minecraft:smoke,minecraft:cloud";
var PARTICLE_COUNT = 10;

var NAV_FLYING = 1;
var RETALIATE_REVENGE = 0;

var CD_KEY = "ghost_soul_bolt_cd";

function init(e) {
    var npc = e.npc;
    if (npc == null) return;
    try {
        npc.getAi().setNavigationType(NAV_FLYING);
    } catch (err) {}
}

function tick(e) {
    var npc = e.npc;
    if (npc == null) return;
    if (!npc.isAlive()) {
        AbilityAPI.cancel(npc);
        return;
    }
    if (AbilityAPI.isBusy(npc)) return;

    var world = npc.getWorld();
    var data = npc.getStoreddata();
    var now = world.getTotalTime();
    if (now < getInt(data, CD_KEY)) return;

    var target = resolveTarget(npc, world);
    if (target == null) return;

    var started = AbilityAPI.start(npc, ABILITY_ID, target, AbilityAPI.params(
        "maxRange", MAX_RANGE,
        "chargeTicks", CHARGE_TICKS,
        "activeTicks", ACTIVE_TICKS,
        "arcHeight", ARC_HEIGHT,
        "landRadius", LAND_RADIUS,
        "hitRadius", HIT_RADIUS,
        "damage", DAMAGE,
        "knockback", KNOCKBACK,
        "knockbackY", KNOCKBACK_Y,
        "blobParticles", BLOB_PARTICLES,
        "landParticles", LAND_PARTICLES,
        "particleCount", PARTICLE_COUNT,
        "telegraphColor", 0xC0FF3030
    ));
    if (!started) return;

    data.put(CD_KEY, String(now + COOLDOWN_TICKS));
}

// Точка падения зафиксирована в onStart — не рвать каст при сбросе AI-цели.
function targetLost(e) {
}

function died(e) {
    AbilityAPI.cancel(e.npc);
}

function resolveTarget(npc, world) {
    var t = null;
    try {
        t = npc.getAttackTarget();
    } catch (err) {
        t = null;
    }
    if (t != null && t.isAlive() && flatDistance(npc, t) <= MAX_RANGE) {
        return t;
    }
    return findNearestPlayer(npc, world);
}

function findNearestPlayer(npc, world) {
    var best = null;
    var bestD = MAX_RANGE + 0.01;
    try {
        var players = world.getAllPlayers();
        for (var i = 0; i < players.length; i++) {
            var p = players[i];
            if (p == null || !p.isAlive()) continue;
            try {
                if (typeof p.getGamemode == "function") {
                    var gm = p.getGamemode();
                    if (gm == 1 || gm == 3) continue;
                }
            } catch (eGm) {}
            var d = flatDistance(npc, p);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
    } catch (e) {
        return null;
    }
    return best;
}

function flatDistance(a, b) {
    var dx = a.getX() - b.getX();
    var dz = a.getZ() - b.getZ();
    return Math.sqrt(dx * dx + dz * dz);
}

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)), 10) || 0;
}
