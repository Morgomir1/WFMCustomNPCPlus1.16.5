// =====================================================
// Скавен чумной монах — "Кадило чумы"
// 2 сек зарядка (партиклы) -> взрыв пердежа + яд врагам в радиусе. КД 10 сек.
// =====================================================

var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();
var EntitiesType = Java.type("noppes.npcs.api.constants.EntitiesType");

// -------------------------
// НАСТРОЙКИ
// -------------------------
var COOLDOWN_TICKS = 200; // 10 секунд
var CHARGE_TICKS = 40;    // 2 секунды
var DETECT_RANGE = 6.0;
var BURST_RADIUS = 3.5;
var POISON_SECONDS = 3;
// Великий нечистый: LOTRParticles.NURGLE_MIASMA + звуки BileBreathSpellGoal
var FART_PARTICLE = "wfm:nurgle_miasma";
var CHARGE_SOUND = "minecraft:entity.zombie_villager.cure";
var BURST_SOUND = "minecraft:entity.slime.attack";
var CHARGE_SOUND_VOL = 0.8;
var CHARGE_SOUND_PITCH = 0.85;
var BURST_SOUND_VOL = 1.2;
var BURST_SOUND_PITCH = 0.75;

// -------------------------
// storeddata keys
// -------------------------
var CD_KEY = "sk_plague_cd";
var CHARGING_KEY = "sk_plague_charging";
var CHARGE_END_KEY = "sk_plague_charge_end";

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) {
        clearState(npc.getStoreddata());
        return;
    }

    var world = npc.getWorld();
    var data = npc.getStoreddata();
    var now = world.getTotalTime();

    if (String(data.get(CHARGING_KEY)) == "1") {
        doChargingTick(npc, world, data, now);
        return;
    }

    if (now < getInt(data, CD_KEY)) return;
    if (!hasNearbyEnemy(npc, world, DETECT_RANGE)) return;

    startCharge(npc, world, data, now);
}

function hasNearbyEnemy(npc, world, range) {
    var target = npc.getAttackTarget();
    if (target != null && target.isAlive() && flatDistance(npc, target) <= range) {
        if (typeof npc.canSeeEntity != "function" || npc.canSeeEntity(target)) {
            return true;
        }
    }

    var players = world.getAllPlayers();
    for (var i = 0; i < players.length; i++) {
        var player = players[i];
        if (player == null || !player.isAlive()) continue;
        if (flatDistance(npc, player) > range) continue;
        if (typeof npc.canSeeEntity == "function" && !npc.canSeeEntity(player)) continue;
        return true;
    }

    return false;
}

function startCharge(npc, world, data, now) {
    data.put(CHARGING_KEY, "1");
    data.put(CHARGE_END_KEY, String(now + CHARGE_TICKS));
    spawnChargeParticles(world, npc, 12);
    try {
        world.playSoundAt(npc.getPos(), CHARGE_SOUND, CHARGE_SOUND_VOL, CHARGE_SOUND_PITCH);
    } catch (e) {}
}

function doChargingTick(npc, world, data, now) {
    if (!hasNearbyEnemy(npc, world, DETECT_RANGE)) {
        clearState(data);
        return;
    }

    if (now < getInt(data, CHARGE_END_KEY)) {
        spawnChargeParticles(world, npc, 6);
        return;
    }

    if (!hasNearbyEnemy(npc, world, DETECT_RANGE)) {
        clearState(data);
        return;
    }

    doBurst(npc, world, data);
}

function doBurst(npc, world, data) {
    var pos = NpcAPI.getIPos(npc.getX(), npc.getY(), npc.getZ());
    var list = world.getNearbyEntities(pos, BURST_RADIUS, EntitiesType.ANY);
    for (var i = 0; i < list.length; i++) {
        var ent = list[i];
        if (!isValidEnemy(npc, ent)) continue;
        try {
            ent.addPotionEffect(PotionEffectType_POISON, POISON_SECONDS, 0, false);
        } catch (e) {}
    }

    try {
        spawnBurstParticles(world, npc);
        world.playSoundAt(npc.getPos(), BURST_SOUND, BURST_SOUND_VOL, BURST_SOUND_PITCH);
    } catch (e2) {}

    var now = world.getTotalTime();
    data.put(CD_KEY, String(now + COOLDOWN_TICKS));
    clearState(data);
}

function spawnChargeParticles(world, npc, count) {
    try {
        world.spawnParticle(FART_PARTICLE, npc.getX(), npc.getY() + 0.9, npc.getZ(),
            0.35, 0.2, 0.35, 0.04, count);
    } catch (e) {}
}

function spawnBurstParticles(world, npc) {
    var x = npc.getX();
    var y = npc.getY() + 0.6;
    var z = npc.getZ();
    world.spawnParticle(FART_PARTICLE, x, y, z, 0.6, 0.45, 0.6, 0.08, 40);
    world.spawnParticle(FART_PARTICLE, x, y + 0.5, z, 0.45, 0.3, 0.45, 0.06, 32);
    world.spawnParticle(FART_PARTICLE, x, y + 1.0, z, 0.3, 0.15, 0.3, 0.05, 20);
}

function isValidEnemy(npc, ent) {
    if (ent == null || !ent.isAlive()) return false;
    if (String(ent.getUUID()) == String(npc.getUUID())) return false;
    if (flatDistance(npc, ent) > BURST_RADIUS) return false;

    var target = npc.getAttackTarget();
    if (target != null && String(target.getUUID()) == String(ent.getUUID())) return true;

    return isPlayerEntity(ent);
}

function isPlayerEntity(entity) {
    if (entity == null) return false;
    if (typeof entity.typeOf == "function" && entity.typeOf(1)) return true;
    if (typeof entity.getType == "function" && entity.getType() == 1) return true;
    if (typeof entity.getMCEntity == "function") {
        var mc = entity.getMCEntity();
        if (mc != null && String(mc.getClass().getName()).indexOf("ServerPlayerEntity") >= 0) return true;
    }
    return String(entity.getClass().getName()).indexOf("ServerPlayerEntity") >= 0;
}

function clearState(data) {
    data.put(CHARGING_KEY, "0");
    data.put(CHARGE_END_KEY, "0");
}

function flatDistance(a, b) {
    var dx = a.getX() - b.getX();
    var dz = a.getZ() - b.getZ();
    return Math.sqrt(dx * dx + dz * dz);
}

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}
