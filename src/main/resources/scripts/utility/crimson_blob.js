// =====================================================
// Универсальная абилка: crimson_blob
// Навес сгустка партиклов → hazard-лужа (ZoneAPI).
// Механика — Java AbilityAPI. JS: кулдаун, старт и тюнинг.
//
// Panic (OnAttack=1): AI часто сбрасывает getAttackTarget —
// цель ищем сами через игроков.
// =====================================================

var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");

// -------------------------
// НАСТРОЙКИ
// -------------------------
var ABILITY_ID = "crimson_blob";
var COOLDOWN_TICKS = 60;
var MAX_RANGE = 20.0;

var ZONE_RADIUS = 2.0;          // радиус лужи / telegraph
var ZONE_SECONDS = 8;           // сколько лежит лужа
var DAMAGE_PER_SECOND = 3.0;    // чистый MAGIC-урон раз в секунду

// Дебаффы зоны: id через ";" (все с одной длительностью/уровнем)
// Примеры: "minecraft:blindness", "minecraft:blindness;minecraft:slowness"
var ZONE_EFFECTS = "minecraft:blindness";
var EFFECT_SECONDS = 2;         // длительность эффекта при каждом тике зоны
var EFFECT_AMPLIFIER = 0;       // уровень эффекта (0 = I)

// Партиклы полёта / приземления (через ","). Пустая строка = дефолт Java.
// Можно коротко: "flame,smoke,ash" или полно: "minecraft:flame,minecraft:soul_fire_flame"
var BLOB_PARTICLES = "minecraft:flame,minecraft:smoke,minecraft:large_smoke,minecraft:ash,minecraft:crit";
var LAND_PARTICLES = "minecraft:large_smoke,minecraft:smoke,minecraft:flame,minecraft:ash,minecraft:explosion";
var PARTICLE_COUNT = 12;        // плотность на тик полёта

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
        "maxRange", MAX_RANGE,
        "landRadius", ZONE_RADIUS,
        "zoneTicks", Math.floor(ZONE_SECONDS * 20),
        "damage", DAMAGE_PER_SECOND,
        "damageInterval", 20,
        "effectId", ZONE_EFFECTS,
        "effectDuration", Math.floor(EFFECT_SECONDS * 20),
        "effectAmplifier", EFFECT_AMPLIFIER,
        "blobParticles", BLOB_PARTICLES,
        "landParticles", LAND_PARTICLES,
        "particleCount", PARTICLE_COUNT
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
