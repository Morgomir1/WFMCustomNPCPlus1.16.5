// =====================================================
// Склеповый вурдалак — сила стаи
// Чем больше других вурдалаков рядом, тем быстрее бег и сильнее удар.
// Скорость — через эффект Speed (сам сбрасывается вне стаи).
//
// Настройка NPC: повесить скрипт в GUI. Тег crypt_ghoul
// выставляется автоматически в init (можно задать и в Advanced).
// =====================================================

var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();
var EntitiesType = Java.type("noppes.npcs.api.constants.EntitiesType");
var CryptGhoulDeath = Java.type("noppes.npcs.script.vampire.CryptGhoulDeathHelper");
var Effects = Java.type("net.minecraft.potion.Effects");

// -------------------------
// НАСТРОЙКИ
// -------------------------
var PACK_TAG = "crypt_ghoul";
var PACK_RADIUS = 12.0;

var MAX_ALLIES_FOR_CAP = 5;       // бонус упирается в потолок при 5+ соседях
var MAX_SPEED_AMPLIFIER = 3;      // Speed IV при полной стае (0=I … 3=IV)
var SPEED_EFFECT_SECONDS = 2;     // обновляется каждый tick (~0.5с)
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
var SPEED_BUFF_KEY = "cg_speed_buff";

// Маркеры трупов — CryptGhoulDeathHelper (Java)

function init(e) {
    var npc = e.npc;
    if (!npc.hasTag(PACK_TAG)) {
        npc.addTag(PACK_TAG);
    }
    var data = npc.getStoreddata();
    storeBaseStats(data, npc);
    restoreBaseSpeed(npc, data);
    clearSpeedEffect(npc);
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) {
        clearState(npc);
        return;
    }
    updatePackFrenzy(npc);
}

function died(e) {
    CryptGhoulDeath.onDeath(e.npc);
    clearState(e.npc);
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

    var meleeMult = 1.0 + (MAX_MELEE_MULT - 1.0) * ratio;
    var melee = Math.max(1, Math.round(baseMelee * meleeMult * 10) / 10);

    try {
        // walking speed всегда база — бафф только через Speed-эффект
        ai.setWalkingSpeed(baseSpeed);
        npc.getStats().getMelee().setStrength(melee);
    } catch (err) {}

    applySpeedEffect(npc, data, ratio);
}

function applySpeedEffect(npc, data, ratio) {
    if (ratio <= 0) {
        if (String(data.get(SPEED_BUFF_KEY)) == "1") {
            clearSpeedEffect(npc);
            data.put(SPEED_BUFF_KEY, "0");
        }
        return;
    }

    var amp = Math.floor(ratio * MAX_SPEED_AMPLIFIER);
    if (amp < 0) amp = 0;
    if (amp > MAX_SPEED_AMPLIFIER) amp = MAX_SPEED_AMPLIFIER;

    try {
        npc.addPotionEffect(PotionEffectType_SPEED, SPEED_EFFECT_SECONDS, amp, true);
        data.put(SPEED_BUFF_KEY, "1");
    } catch (err) {}
}

function clearSpeedEffect(npc) {
    try {
        var mc = npc.getMCEntity();
        if (mc != null) {
            mc.removeEffect(Effects.MOVEMENT_SPEED);
        }
    } catch (err) {}
}

function restoreBaseSpeed(npc, data) {
    try {
        npc.getAi().setWalkingSpeed(getBaseSpeed(data, npc.getAi()));
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
                world.spawnParticle("wfm:warpfire_flame", x + ox, y + oy + 1.15, z + oz, 0, 0.02, 0, 0.01, 1);
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

function clearState(npc) {
    var data = npc.getStoreddata();
    clearSpeedEffect(npc);
    restoreBaseSpeed(npc, data);
    try {
        npc.getStats().getMelee().setStrength(getBaseMelee(data, npc));
    } catch (err) {}
    data.remove(LAST_ALLIES_KEY);
    data.remove(PACK_TIER_KEY);
    data.remove(SPEED_BUFF_KEY);
}

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}

function getFloat(data, key) {
    if (!data.has(key)) return 0.0;
    return parseFloat(String(data.get(key)));
}
