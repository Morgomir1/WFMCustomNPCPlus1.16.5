// =====================================================
// Склеповый вурдалак — сила стаи
// Чем больше других вурдалаков рядом, тем быстрее бег и сильнее удар.
//
// Настройка NPC: повесить скрипт в GUI. Тег crypt_ghoul
// выставляется автоматически в init (можно задать и в Advanced).
// =====================================================

var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();
var EntitiesType = Java.type("noppes.npcs.api.constants.EntitiesType");
var CryptGhoulDeath = Java.type("noppes.npcs.script.vampire.CryptGhoulDeathHelper");

// -------------------------
// НАСТРОЙКИ
// -------------------------
var PACK_TAG = "crypt_ghoul";
var PACK_RADIUS = 12.0;

var MAX_ALLIES_FOR_CAP = 5;       // бонус упирается в потолок при 5+ соседях
var MAX_SPEED_MULT = 1.75;        // скорость при полной стае (×1.75 от базы)
var MAX_MELEE_MULT = 1.6;         // урон ближнего боя при полной стае

var PARTICLE_MIN_ALLIES = 2;      // партиклы, когда рядом ≥ N других вурдалаков
var PARTICLE_CHANCE = 0.45;

// -------------------------
// storeddata keys
// -------------------------
var BASE_SPEED_KEY = "cg_base_speed";
var BASE_MELEE_KEY = "cg_base_melee";
var LAST_ALLIES_KEY = "cg_last_allies";
var PACK_TIER_KEY = "cg_pack_tier";

// Маркеры трупов — CryptGhoulDeathHelper (Java)

function init(e) {
    var npc = e.npc;
    if (!npc.hasTag(PACK_TAG)) {
        npc.addTag(PACK_TAG);
    }
    storeBaseStats(npc.getStoreddata(), npc);
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) {
        clearState(npc.getStoreddata());
        return;
    }
    updatePackFrenzy(npc);
}

function died(e) {
    CryptGhoulDeath.onDeath(e.npc);
    clearState(e.npc.getStoreddata());
}

function updatePackFrenzy(npc) {
    var data = npc.getStoreddata();
    var ai = npc.getAi();
    var world = npc.getWorld();

    storeBaseStats(data, npc);

    var allies = countNearbyAllies(npc, world);
    var ratio = allies / MAX_ALLIES_FOR_CAP;
    if (ratio < 0) ratio = 0;
    if (ratio > 1) ratio = 1;

    applyScaledStats(npc, ai, data, ratio);
    updatePackFeedback(npc, world, data, allies, ratio);
}

function countNearbyAllies(npc, world) {
    var pos = NpcAPI.getIPos(npc.getX(), npc.getY(), npc.getZ());
    var nearby = world.getNearbyEntities(pos, PACK_RADIUS, EntitiesType.ANY);

    var selfUuid = String(npc.getUUID());
    var count = 0;

    for (var i = 0; i < nearby.length; i++) {
        var other = nearby[i];
        if (other == null || !other.isAlive()) continue;
        if (String(other.getUUID()) == selfUuid) continue;
        if (typeof other.hasTag != "function") continue;
        if (!other.hasTag(PACK_TAG)) continue;
        count++;
    }

    return count;
}

function applyScaledStats(npc, ai, data, ratio) {
    var baseSpeed = getBaseSpeed(data, ai);
    var baseMelee = getBaseMelee(data, npc);

    var speedMult = 1.0 + (MAX_SPEED_MULT - 1.0) * ratio;
    var meleeMult = 1.0 + (MAX_MELEE_MULT - 1.0) * ratio;

    var speed = Math.max(1, Math.round(baseSpeed * speedMult));
    var melee = Math.max(1, Math.round(baseMelee * meleeMult * 10) / 10);

    try {
        ai.setWalkingSpeed(speed);
        npc.getStats().getMelee().setStrength(melee);
    } catch (err) {}
}

function updatePackFeedback(npc, world, data, allies, ratio) {
    data.put(LAST_ALLIES_KEY, String(allies));

    var tier = 0;
    if (allies >= 1) tier = 1;
    if (allies >= 3) tier = 2;
    if (allies >= MAX_ALLIES_FOR_CAP) tier = 3;

    var prevTier = getInt(data, PACK_TIER_KEY);
    if (tier > prevTier && tier > 0) {
        try {
            world.playSoundAt(
                NpcAPI.getIPos(npc.getX(), npc.getY() + 1.0, npc.getZ()),
                "minecraft:entity.wolf.growl",
                0.35 + tier * 0.1,
                0.75 + tier * 0.08
            );
        } catch (err) {}
    }
    data.put(PACK_TIER_KEY, String(tier));

    if (allies < PARTICLE_MIN_ALLIES) return;
    if (Math.random() > PARTICLE_CHANCE) return;
    spawnPackParticles(npc, world, tier);
}

function spawnPackParticles(npc, world, tier) {
    var x = npc.getX();
    var y = npc.getY();
    var z = npc.getZ();
    var h = npc.getHeight();
    var count = 2 + tier;

    try {
        for (var i = 0; i < count; i++) {
            var ox = (Math.random() - 0.5) * 0.8;
            var oy = Math.random() * h;
            var oz = (Math.random() - 0.5) * 0.8;
            world.spawnParticle("smoke", x + ox, y + oy, z + oz, 0, 0.03, 0, 0.01, 1);
            if (tier >= 2) {
                world.spawnParticle("soul_fire_flame", x + ox, y + oy + 0.15, z + oz, 0, 0.02, 0, 0.01, 1);
            }
        }
    } catch (err) {}
}

function storeBaseStats(data, npc) {
    var ai = npc.getAi();

    if (!data.has(BASE_SPEED_KEY)) {
        data.put(BASE_SPEED_KEY, String(ai.getWalkingSpeed()));
    }
    if (!data.has(BASE_MELEE_KEY)) {
        var melee = 5;
        try {
            melee = npc.getStats().getMelee().getStrength();
        } catch (err) {}
        if (melee <= 0) melee = 5;
        data.put(BASE_MELEE_KEY, String(melee));
    }
}

function getBaseSpeed(data, ai) {
    if (data.has(BASE_SPEED_KEY)) {
        return getInt(data, BASE_SPEED_KEY);
    }
    return ai.getWalkingSpeed();
}

function getBaseMelee(data, npc) {
    if (data.has(BASE_MELEE_KEY)) {
        return getFloat(data, BASE_MELEE_KEY);
    }
    try {
        return npc.getStats().getMelee().getStrength();
    } catch (err) {
        return 5.0;
    }
}

function clearState(data) {
    data.remove(LAST_ALLIES_KEY);
    data.remove(PACK_TIER_KEY);
}

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}

function getFloat(data, key) {
    if (!data.has(key)) return 0.0;
    return parseFloat(String(data.get(key)));
}
