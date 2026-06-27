// =====================================================
// Имперский флагеллянт — ярость от ран
// Чем меньше HP, тем быстрее бег и сильнее удар.
// Ниже 50% HP: Resistance II (5 сек) + яростные партиклы.
// =====================================================

var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();

// -------------------------
// НАСТРОЙКИ
// -------------------------
var RAGE_HP_RATIO = 0.5;          // порог «ярости»
var RESIST_SECONDS = 5;           // длительность Resistance II
var RESIST_AMPLIFIER = 1;         // 0 = I, 1 = II
var RESIST_REFRESH_TICKS = 80;    // обновление эффекта, пока HP < 50%

var MAX_SPEED_MULT = 2.4;         // множитель скорости при ~0 HP
var MAX_MELEE_MULT = 2.2;         // множитель урона ближнего боя при ~0 HP

var PARTICLE_CHANCE = 0.85;       // шанс спавна пакета партиклов за tick

// -------------------------
// storeddata keys
// -------------------------
var BASE_SPEED_KEY = "ef_base_speed";
var BASE_MELEE_KEY = "ef_base_melee";
var RAGE_KEY = "ef_rage_active";
var RESIST_UNTIL_KEY = "ef_resist_until";

function init(e) {
    storeBaseStats(e.npc.getStoreddata(), e.npc);
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) {
        clearState(npc.getStoreddata());
        return;
    }
    updateFury(npc);
}

function damaged(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    updateFury(npc);
}

function died(e) {
    clearState(e.npc.getStoreddata());
}

function updateFury(npc) {
    var data = npc.getStoreddata();
    var ai = npc.getAi();
    var world = npc.getWorld();

    storeBaseStats(data, npc);

    var maxHp = npc.getMaxHealth();
    if (maxHp <= 0) return;

    var hp = npc.getHealth();
    if (hp < 0) hp = 0;

    var missingRatio = 1.0 - (hp / maxHp);
    if (missingRatio < 0) missingRatio = 0;
    if (missingRatio > 1) missingRatio = 1;

    applyScaledStats(npc, ai, data, missingRatio);

    var hpRatio = hp / maxHp;
    updateRageEffects(npc, world, data, hpRatio);
}

function applyScaledStats(npc, ai, data, missingRatio) {
    var baseSpeed = getBaseSpeed(data, ai);
    var baseMelee = getBaseMelee(data, npc);

    var speedMult = 1.0 + (MAX_SPEED_MULT - 1.0) * missingRatio;
    var meleeMult = 1.0 + (MAX_MELEE_MULT - 1.0) * missingRatio;

    var speed = Math.max(1, Math.round(baseSpeed * speedMult));
    var melee = Math.max(1, Math.round(baseMelee * meleeMult * 10) / 10);

    try {
        ai.setWalkingSpeed(speed);
        npc.getStats().getMelee().setStrength(melee);
    } catch (err) {}
}

function updateRageEffects(npc, world, data, hpRatio) {
    if (hpRatio >= RAGE_HP_RATIO) {
        data.put(RAGE_KEY, "0");
        return;
    }

    var wasRage = String(data.get(RAGE_KEY)) == "1";
    if (!wasRage) {
        data.put(RAGE_KEY, "1");
        try {
            world.playSoundAt(
                NpcAPI.getIPos(npc.getX(), npc.getY() + 1.0, npc.getZ()),
                "minecraft:entity.ravager.roar",
                0.55,
                1.35
            );
        } catch (err) {}
    }

    refreshResistance(npc, world, data);
    spawnRageParticles(npc, world);
}

function refreshResistance(npc, world, data) {
    var now = world.getTotalTime();
    if (now < getInt(data, RESIST_UNTIL_KEY)) return;

    try {
        if (typeof npc.addPotionEffect == "function") {
            npc.addPotionEffect(PotionEffectType_RESISTANCE, RESIST_SECONDS, RESIST_AMPLIFIER, false);
        }
    } catch (err) {}

    data.put(RESIST_UNTIL_KEY, String(now + RESIST_REFRESH_TICKS));
}

function spawnRageParticles(npc, world) {
    if (Math.random() > PARTICLE_CHANCE) return;

    var x = npc.getX();
    var y = npc.getY();
    var z = npc.getZ();
    var h = npc.getHeight();

    try {
        for (var i = 0; i < 4; i++) {
            var ox = (Math.random() - 0.5) * 0.9;
            var oy = Math.random() * h;
            var oz = (Math.random() - 0.5) * 0.9;

            world.spawnParticle("soul_fire_flame", x + ox, y + oy, z + oz, 0, 0.04, 0, 0.02, 1);
            world.spawnParticle("crimson_spore", x + ox, y + oy + 0.2, z + oz, 0, 0.06, 0, 0.01, 1);
        }

        if (Math.random() < 0.35) {
            world.spawnParticle("sweep_attack", x, y + h * 0.55, z, 0, 0, 0, 0, 1);
        }
        if (Math.random() < 0.25) {
            world.spawnParticle("angry_villager", x, y + h + 0.2, z, 0, 0, 0, 0, 1);
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
    data.remove(RAGE_KEY);
    data.remove(RESIST_UNTIL_KEY);
}

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}

function getFloat(data, key) {
    if (!data.has(key)) return 0.0;
    return parseFloat(String(data.get(key)));
}
