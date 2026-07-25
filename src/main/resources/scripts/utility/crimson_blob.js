// =====================================================
// Универсальная абилка: crimson_blob
// Навес красно-чёрного сгустка → слепая лужа (ZoneAPI).
// Механика — Java AbilityAPI. JS: кулдаун и старт.
//
// Panic (OnAttack=1): AI часто сбрасывает getAttackTarget /
// ломает LOS при бегстве — цель ищем сами через игроков.
// =====================================================

var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");

// -------------------------
// НАСТРОЙКИ
// -------------------------
var ABILITY_ID = "crimson_blob";
var COOLDOWN_TICKS = 80; // 4 секунды
var MAX_RANGE = 20.0;

var CD_KEY = "crimson_blob_cd";

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
        "maxRange", MAX_RANGE
    ));
    if (!started) return;

    data.put(CD_KEY, String(now + COOLDOWN_TICKS));
}

// Точка падения уже зафиксирована в Java onStart — не рвать каст при сбросе AI-цели (Panic).
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
                    if (gm == 1 || gm == 3) continue; // creative / spectator
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
