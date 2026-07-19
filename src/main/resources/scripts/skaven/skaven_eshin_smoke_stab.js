// =====================================================
// Скавен Эшин — "Дымовой укол"
// Зарядка дымом -> телепорт к цели -> удар + слабость.
// Режимы CNPC: «Мстить» / «Отступать» с повышенной скоростью.
// В мщении: если цель дальше 5 блоков — телепорт за спину.
// При получении 5+ урона — отступление на 8 сек, затем снова мщение.
// =====================================================

var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();
var EntitiesType = Java.type("noppes.npcs.api.constants.EntitiesType");
var TelegraphAPI = Java.type("noppes.npcs.telegraph.TelegraphAPI");

// CNPC OnAttack: 0=Мстить, 1=Паника, 2=Отступать, 3=Ничего
var RETALIATE_REVENGE = 0;
var RETALIATE_RETREAT = 2;

// -------------------------
// НАСТРОЙКИ
// -------------------------
var COOLDOWN_TICKS = 160;         // 8 секунд
var RANGE = 8.0;
var CHARGE_TICKS = 20;            // 1 секунда
var BACK_OFFSET = 1.8;
var DAMAGE = 6.0;
var DEBUFF_SECONDS = 4;
var REVENGE_TICKS = 100;          // 5 сек мщения после укола, затем отступление
var DAMAGE_RETREAT_TICKS = 160;   // 8 сек отступления после получения урона
var DAMAGE_RETREAT_THRESHOLD = 5; // порог урона за один удар
var CHASE_TELEPORT_RANGE = 5.0;   // дальше — телепорт за спину в режиме мщения
var RETREAT_SPEED = 8;

// -------------------------
// storeddata keys
// -------------------------
var CD_KEY = "sk_eshin_cd";
var CHARGING_KEY = "sk_eshin_charging";
var CHARGE_END_KEY = "sk_eshin_charge_end";
var MODE_KEY = "sk_eshin_mode";           // "revenge" | "retreat"
var MODE_END_KEY = "sk_eshin_mode_end";
var BASE_SPEED_KEY = "sk_eshin_base_speed";

function init(e) {
    storeBaseSpeed(e.npc.getStoreddata(), e.npc.getAi());
    if (String(e.npc.getStoreddata().get(MODE_KEY)) != "revenge") {
        applyRetreatMode(e.npc, e.npc.getStoreddata(), 0, false);
    }
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) {
        clearState(npc.getStoreddata());
        return;
    }

    var world = npc.getWorld();
    var data = npc.getStoreddata();
    var now = world.getTotalTime();
    var ai = npc.getAi();

    storeBaseSpeed(data, ai);
    updateModeTransition(npc, data, now);

    if (String(data.get(CHARGING_KEY)) == "1") {
        doChargingTick(npc, world, data, now);
        return;
    }

    if (String(data.get(MODE_KEY)) == "revenge") {
        tryRevengeChaseTeleport(npc, world);
    }

    if (now < getInt(data, CD_KEY)) return;

    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) return;
    if (flatDistance(npc, target) > RANGE) return;
    if (typeof npc.canSeeEntity == "function" && !npc.canSeeEntity(target)) return;

    startCharge(npc, world, data, now);
}

function damaged(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    if (parseFloat(String(e.damage)) < DAMAGE_RETREAT_THRESHOLD) return;

    var data = npc.getStoreddata();
    clearChargeState(data);
    applyRetreatMode(npc, data, npc.getWorld().getTotalTime(), true);
}

function storeBaseSpeed(data, ai) {
    if (!data.has(BASE_SPEED_KEY)) {
        data.put(BASE_SPEED_KEY, String(ai.getWalkingSpeed()));
    }
}

function getBaseSpeed(data, ai) {
    if (data.has(BASE_SPEED_KEY)) {
        return getInt(data, BASE_SPEED_KEY);
    }
    return ai.getWalkingSpeed();
}

function applyRevengeMode(npc, data, now, timed) {
    var ai = npc.getAi();
    data.put(MODE_KEY, "revenge");
    data.put(MODE_END_KEY, timed ? String(now + REVENGE_TICKS) : "0");
    try {
        ai.setRetaliateType(RETALIATE_REVENGE);
        ai.setWalkingSpeed(getBaseSpeed(data, ai));
    } catch (err) {}
}

function applyRetreatMode(npc, data, now, timed) {
    var ai = npc.getAi();
    data.put(MODE_KEY, "retreat");
    data.put(MODE_END_KEY, timed ? String(now + DAMAGE_RETREAT_TICKS) : "0");
    try {
        ai.setRetaliateType(RETALIATE_RETREAT);
        ai.setWalkingSpeed(RETREAT_SPEED);
    } catch (err) {}
}

function updateModeTransition(npc, data, now) {
    var mode = String(data.get(MODE_KEY));
    var end = getInt(data, MODE_END_KEY);
    if (end <= 0 || now < end) return;

    if (mode == "revenge") {
        applyRetreatMode(npc, data, now, false);
    } else if (mode == "retreat") {
        applyRevengeMode(npc, data, now, false);
    }
}

function tryRevengeChaseTeleport(npc, world) {
    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) return;
    if (flatDistance(npc, target) <= CHASE_TELEPORT_RANGE) return;
    if (typeof npc.canSeeEntity == "function" && !npc.canSeeEntity(target)) return;
    teleportBehind(npc, target, world, false);
}

function teleportBehind(npc, target, world, withAttack) {
    var yaw = target.getRotation();
    var rad = yaw * 0.0174532925;
    var bx = target.getX() - Math.sin(rad) * BACK_OFFSET;
    var bz = target.getZ() + Math.cos(rad) * BACK_OFFSET;
    var by = target.getY();

    npc.setPosition(bx, by, bz);
    npc.setRotation(yaw);

    if (withAttack) {
        try {
            target.damage(DAMAGE);
            if (typeof target.addPotionEffect == "function") {
                target.addPotionEffect(PotionEffectType_WEAKNESS, DEBUFF_SECONDS, 0, false);
            }
        } catch (err) {}
    }

    try {
        world.spawnParticle("minecraft:poof", bx, by + 0.5, bz, 0.2, 0.1, 0.2, 0.02, 10);
        world.playSoundAt(NpcAPI.getIPos(bx, by, bz), "minecraft:entity.enderman.teleport", 0.6, 1.6);
    } catch (err) {}
}

function startCharge(npc, world, data, now) {
    data.put(CHARGING_KEY, "1");
    try {
        var target = npc.getAttackTarget();
        var yaw = npc.getMCEntity().yRot;
        if (target != null) {
            var dx = target.getX() - npc.getX();
            var dz = target.getZ() - npc.getZ();
            yaw = Math.atan2(-dx, dz) * (180.0 / Math.PI);
        }
        TelegraphAPI.cone(npc, npc.getX(), npc.getY(), npc.getZ(), yaw, 3.5, 35, CHARGE_TICKS, 0xC0FF3030);
    } catch (te) {}
    data.put(CHARGE_END_KEY, String(now + CHARGE_TICKS));
    try {
        world.spawnParticle("minecraft:smoke", npc.getX(), npc.getY() + 1.0, npc.getZ(),
            0.2, 0.05, 0.2, 0.02, 10);
    } catch (err) {}
}

function doChargingTick(npc, world, data, now) {
    if (now < getInt(data, CHARGE_END_KEY)) {
        try {
            world.spawnParticle("minecraft:smoke", npc.getX(), npc.getY() + 0.9, npc.getZ(),
                0.15, 0.02, 0.15, 0.01, 6);
        } catch (err) {}
        return;
    }
    performStab(npc, world, data);
}

function performStab(npc, world, data) {
    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) {
        clearState(data);
        return;
    }

    teleportBehind(npc, target, world, true);

    var now = world.getTotalTime();
    data.put(CD_KEY, String(now + COOLDOWN_TICKS));
    clearChargeState(data);
    applyRevengeMode(npc, data, now, true);
}

function clearChargeState(data) {
    data.put(CHARGING_KEY, "0");
    data.put(CHARGE_END_KEY, "0");
}

function clearState(data) {
    clearChargeState(data);
    data.put(MODE_KEY, "retreat");
    data.put(MODE_END_KEY, "0");
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
