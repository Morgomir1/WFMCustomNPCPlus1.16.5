// =====================================================
// Имперский флагеллянт — «Мученический натиск»
// Увидел цель (агро) -> бег + telegraph follow -> 5 сек -> взрыв.
// NPC погибает при детонации.
// Паттерн как KeeperOfSecretsEntity.spawnBladestormTelegraph:
// один circleFollow на всю длительность (follow в первом пакете).
// =====================================================

var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();
var EntitiesType = Java.type("noppes.npcs.api.constants.EntitiesType");
var TelegraphAPI = Java.type("noppes.npcs.telegraph.TelegraphAPI");
var ZoneAPI = Java.type("noppes.npcs.zone.ZoneAPI");

// CNPC OnAttack: 0=Мстить, 1=Паника, 2=Отступать, 3=Ничего
var RETALIATE_REVENGE = 0;

// -------------------------
// НАСТРОЙКИ
// -------------------------
var CHARGE_TICKS = 100;           // 5 секунд до взрыва
var MAX_CHARGE_SPEED = 14;
var NORMAL_SPEED = 5;             // скорость после взрыва / сброса
var EXPLOSION_RADIUS = 4.5;
var EXPLOSION_DAMAGE = 14.0;
var KNOCKBACK = 1.1;
// Как у Keeper: DEFAULT_COLOR (signed int, без Nashorn-hex проблем)
var TELEGRAPH_COLOR = TelegraphAPI.DEFAULT_COLOR;

var MARTYR_LINES = [
    "§6§lЗа Сигмара! Пусть пламя смоет грех!",
    "§e§lСигмар! Прими мою смерть как подношение!",
    "§c§lКровь — цена искупления! Смотри, как горит нечестие!",
    "§6§lЯ — живая свеча! Горите со мной, безверные!",
    "§e§lСуд Сигмара неотвратим! Я — его молот!",
    "§c§lПокайтесь! Или сгорите вместе со мной!",
    "§6§lМученичество — высшая милость! Сигмар, прими меня!",
    "§e§lПусть моя смерть осветит путь истинным!",
    "§c§lСигмар судит! Я — его гнев, облечённый в плоть!",
    "§6§lСтрадание спасает! А смерть — освобождает!"
];

// -------------------------
// storeddata keys
// -------------------------
var BASE_SPEED_KEY = "efm_base_speed";
var CHARGING_KEY = "efm_charging";
var CHARGE_START_KEY = "efm_charge_start";
var CHARGE_END_KEY = "efm_charge_end";
var TELEGRAPH_KEY = "efm_telegraph";

function init(e) {
    storeBaseSpeed(e.npc.getStoreddata(), e.npc.getAi());
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) {
        clearState(npc, npc.getStoreddata());
        return;
    }

    var data = npc.getStoreddata();
    if (String(data.get(CHARGING_KEY)) == "1") {
        doChargingTick(npc, npc.getWorld(), data);
        return;
    }

    tryStartCharge(npc, data);
}

function target(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    if (String(npc.getStoreddata().get(CHARGING_KEY)) == "1") return;
    tryStartCharge(npc, npc.getStoreddata());
}

function targetLost(e) {
    // Раз уже бросился — не останавливаемся.
}

function died(e) {
    clearState(e.npc, e.npc.getStoreddata());
}

function tryStartCharge(npc, data) {
    if (String(data.get(CHARGING_KEY)) == "1") return;

    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) return;
    if (typeof npc.canSeeEntity == "function" && !npc.canSeeEntity(target)) return;

    startCharge(npc, npc.getWorld(), data);
}

function startCharge(npc, world, data) {
    var now = world.getTotalTime();
    var ai = npc.getAi();

    storeBaseSpeed(data, ai);

    data.put(CHARGING_KEY, "1");
    data.put(CHARGE_START_KEY, String(now));
    data.put(CHARGE_END_KEY, String(now + CHARGE_TICKS));

    try {
        ai.setRetaliateType(RETALIATE_REVENGE);
        ai.setWalkingSpeed(getBaseSpeed(data, ai));
    } catch (err) {}

    try {
        world.playSoundAt(
            NpcAPI.getIPos(npc.getX(), npc.getY() + 1.0, npc.getZ()),
            "minecraft:entity.creeper.primed",
            0.7,
            1.15
        );
    } catch (err2) {}

    // Keeper: один circle на всю длительность + follow с первого пакета.
    spawnChargeTelegraph(npc, data);
}

function spawnChargeTelegraph(npc, data) {
    clearTelegraph(npc, data);
    try {
        // circleFollow: ground Y + followEntityId до broadcast (не circle+follow отдельно)
        var tid = TelegraphAPI.circleFollow(
            npc,
            npc.getX(),
            npc.getY(),
            npc.getZ(),
            EXPLOSION_RADIUS,
            CHARGE_TICKS,
            TELEGRAPH_COLOR
        );
        if (tid != null && String(tid) != "") {
            data.put(TELEGRAPH_KEY, String(tid));
        }
    } catch (err) {
        try { log("efm telegraph fail: " + err); } catch (e2) {}
    }
}

function doChargingTick(npc, world, data) {
    var now = world.getTotalTime();
    var start = getInt(data, CHARGE_START_KEY);
    var end = getInt(data, CHARGE_END_KEY);
    if (start <= 0 || end <= start) {
        clearState(npc, data);
        return;
    }

    var elapsed = now - start;
    var progress = elapsed / CHARGE_TICKS;
    if (progress < 0) progress = 0;
    if (progress > 1) progress = 1;

    applyChargeSpeed(npc, data, progress);
    spawnChargeParticles(npc, world, progress);

    if (now < end) {
        if (progress > 0.65 && Math.random() < 0.4) {
            try {
                world.playSoundAt(npc.getPos(), "minecraft:block.fire.ambient", 0.25, 1.4 + progress * 0.3);
            } catch (err) {}
        }
        return;
    }

    doExplosion(npc, world, data);
}

function applyChargeSpeed(npc, data, progress) {
    var baseSpeed = getBaseSpeed(data, npc.getAi());
    var speed = Math.max(1, Math.round(baseSpeed + (MAX_CHARGE_SPEED - baseSpeed) * progress));
    try {
        npc.getAi().setWalkingSpeed(speed);
    } catch (err) {}
}

function doExplosion(npc, world, data) {
    var x = npc.getX();
    var y = npc.getY();
    var z = npc.getZ();
    var pos = NpcAPI.getIPos(x, y + 0.5, z);

    clearTelegraph(npc, data);
    sayRandomMartyrLine(npc);

    try {
        world.explode(x, y + 0.5, z, EXPLOSION_RADIUS, false, false);
    } catch (err) {}

    try {
        var zone = ZoneAPI.hazardCircle(npc, x, y, z, EXPLOSION_RADIUS, 20, EXPLOSION_DAMAGE * 0.25, 10);
        if (zone != null) {
            zone.setColor(0xC0FF3030 | 0);
            zone.setKnockback(KNOCKBACK * 0.5);
        }
    } catch (zerr) {}

    var list = world.getNearbyEntities(pos, EXPLOSION_RADIUS, EntitiesType.ANY);
    var mcNpc = null;
    try {
        mcNpc = npc.getMCEntity();
    } catch (err2) {}

    for (var i = 0; i < list.length; i++) {
        var ent = list[i];
        if (!isValidExplosionTarget(npc, ent, mcNpc)) continue;

        try {
            ent.damage(EXPLOSION_DAMAGE);
            applyKnockback(npc, ent);
        } catch (err3) {}
    }

    try {
        world.spawnParticle("explosion_emitter", x, y + 1.0, z, 0, 0, 0, 0, 1);
        world.spawnParticle("flame", x, y + 0.5, z, 0.5, 0.35, 0.5, 0.06, 24);
        world.spawnParticle("large_smoke", x, y + 0.5, z, 0.4, 0.3, 0.4, 0.04, 16);
        world.playSoundAt(pos, "minecraft:entity.generic.explode", 1.0, 0.85);
    } catch (err4) {}

    clearState(npc, data);
    restoreNormalSpeed(npc);

    try {
        npc.kill();
    } catch (err5) {
        try {
            npc.setHealth(0);
        } catch (err6) {}
    }
}

function isValidExplosionTarget(npc, ent, mcNpc) {
    if (ent == null || !ent.isAlive()) return false;
    if (String(ent.getUUID()) == String(npc.getUUID())) return false;
    if (flatDistance(npc, ent) > EXPLOSION_RADIUS) return false;

    if (mcNpc != null && typeof mcNpc.isAlliedTo == "function") {
        try {
            if (mcNpc.isAlliedTo(ent.getMCEntity())) return false;
        } catch (err) {}
    }

    var target = npc.getAttackTarget();
    if (target != null && String(target.getUUID()) == String(ent.getUUID())) return true;
    if (isPlayerEntity(ent)) return true;

    if (typeof ent.typeOf == "function" && ent.typeOf(3)) return true;
    if (typeof ent.getType == "function" && ent.getType() == 3) return true;

    return false;
}

function sayRandomMartyrLine(npc) {
    if (MARTYR_LINES.length <= 0) return;
    var idx = Math.floor(Math.random() * MARTYR_LINES.length);
    try {
        npc.say(MARTYR_LINES[idx]);
    } catch (err) {}
}

function applyKnockback(npc, ent) {
    var dx = ent.getX() - npc.getX();
    var dz = ent.getZ() - npc.getZ();
    var len = Math.sqrt(dx * dx + dz * dz);
    if (len < 0.01) {
        dx = 0;
        dz = 1;
        len = 1;
    }
    dx /= len;
    dz /= len;

    try {
        ent.setMotionX(dx * KNOCKBACK);
        ent.setMotionY(0.35);
        ent.setMotionZ(dz * KNOCKBACK);
    } catch (err) {}
}

function spawnChargeParticles(npc, world, progress) {
    var x = npc.getX();
    var y = npc.getY();
    var z = npc.getZ();
    var h = npc.getHeight();
    var count = 2 + Math.floor(progress * 6);

    try {
        for (var i = 0; i < count; i++) {
            var ox = (Math.random() - 0.5) * (0.6 + progress * 0.8);
            var oy = Math.random() * h;
            var oz = (Math.random() - 0.5) * (0.6 + progress * 0.8);

            world.spawnParticle("flame", x + ox, y + oy, z + oz, 0, 0.05 + progress * 0.08, 0, 0.02, 1);
            if (progress > 0.45) {
                world.spawnParticle("smoke", x + ox, y + oy + 0.1, z + oz, 0, 0.03, 0, 0.01, 1);
            }
            if (progress > 0.75 && Math.random() < 0.35) {
                world.spawnParticle("soul_fire_flame", x + ox, y + oy, z + oz, 0, 0.06, 0, 0.02, 1);
            }
        }
    } catch (err) {}
}

function storeBaseSpeed(data, ai) {
    if (!data.has(BASE_SPEED_KEY)) {
        var speed = ai.getWalkingSpeed();
        if (speed <= 0) speed = NORMAL_SPEED;
        data.put(BASE_SPEED_KEY, String(speed));
    }
}

function getBaseSpeed(data, ai) {
    if (data.has(BASE_SPEED_KEY)) {
        var stored = getInt(data, BASE_SPEED_KEY);
        if (stored > 0) return stored;
    }
    try {
        var cur = ai.getWalkingSpeed();
        if (cur > 0) return cur;
    } catch (err) {}
    return NORMAL_SPEED;
}

function clearTelegraph(npc, data) {
    if (!data.has(TELEGRAPH_KEY)) return;
    var tid = String(data.get(TELEGRAPH_KEY));
    try {
        if (npc != null) {
            TelegraphAPI.removeNear(npc, tid);
        } else {
            TelegraphAPI.remove(tid);
        }
    } catch (te) {
        try {
            TelegraphAPI.remove(tid);
        } catch (te2) {}
    }
    data.remove(TELEGRAPH_KEY);
}

function restoreNormalSpeed(npc) {
    try {
        npc.getAi().setWalkingSpeed(NORMAL_SPEED);
    } catch (err) {}
}

function clearState(npc, data) {
    clearTelegraph(npc, data);
    data.put(CHARGING_KEY, "0");
    data.remove(CHARGE_START_KEY);
    data.remove(CHARGE_END_KEY);
    if (npc != null) restoreNormalSpeed(npc);
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

function flatDistance(a, b) {
    var dx = a.getX() - b.getX();
    var dy = a.getY() - b.getY();
    var dz = a.getZ() - b.getZ();
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
}

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}
