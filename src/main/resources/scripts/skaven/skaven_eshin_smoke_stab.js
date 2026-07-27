// =====================================================
// Скавен Эшин — "Дымовой укол"
// Зарядка (маленькая красная зона под ногами) ->
// телепорт за спину -> WFM stun 1 сек.
// Режимы CNPC: «Мстить» / «Отступать» с повышенной скоростью.
// В мщении: если цель дальше 5 блоков — телепорт за спину.
// При получении 5+ урона — отступление на 8 сек, затем снова мщение.
// Каждый 3-й удар — уворот (как Acrobatics: green_oak_leaf + wood_elf_teleport).
// =====================================================

var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();
var EntitiesType = Java.type("noppes.npcs.api.constants.EntitiesType");
var TelegraphAPI = Java.type("noppes.npcs.telegraph.TelegraphAPI");
var AbilityCombatHelper = Java.type("noppes.npcs.abilities.AbilityCombatHelper");
var EffectInstance = Java.type("net.minecraft.potion.EffectInstance");
var ForgeRegistries = Java.type("net.minecraftforge.registries.ForgeRegistries");
var ResourceLocation = Java.type("net.minecraft.util.ResourceLocation");

// CNPC OnAttack: 0=Мстить, 1=Паника, 2=Отступать, 3=Ничего
var RETALIATE_REVENGE = 0;
var RETALIATE_RETREAT = 2;
var RETALIATE_NONE = 3;

// -------------------------
// НАСТРОЙКИ
// -------------------------
var COOLDOWN_TICKS = 160;         // 8 секунд
var RANGE = 8.0;
var CHARGE_TICKS = 20;            // 1 секунда
var BACK_OFFSET = 1.8;
var BACK_MIN_OFFSET = 0.6;        // ближе к цели, если за спиной стена
var BACK_CLIP_STEP = 0.25;
var BACK_MAX_Y_DELTA = 2.0;       // не прыгать/падать слишком сильно при поиске пола
var STUN_TICKS = 20;              // 1 секунда WFM stun
var STUN_EFFECT_ID = "wfm:stun";
var STUN_SOUND = "wfm:enchantment.pommel_strike_stun";
var STUN_SOUND_VOLUME = 2.5;
var TELEGRAPH_RADIUS = 1.0;       // маленькая красная зона под скавеном
var TELEGRAPH_COLOR = 0xC0FF3030;
var REVENGE_TICKS = 100;          // 5 сек мщения после укола, затем отступление
var DAMAGE_RETREAT_TICKS = 160;   // 8 сек отступления после получения урона
var DAMAGE_RETREAT_THRESHOLD = 5; // порог урона за один удар
var CHASE_TELEPORT_RANGE = 5.0;   // дальше — телепорт за спину в режиме мщения
var RETREAT_SPEED = 8;
var DODGE_EVERY_HITS = 3;         // уворот от каждого N-го удара
var DODGE_LEAF_PARTICLE = "wfm:green_oak_leaf";
var DODGE_LEAF_COUNT = 16;
var DODGE_SOUND = "wfm:elf.wood_elf_teleport";

// -------------------------
// storeddata keys
// -------------------------
var CD_KEY = "sk_eshin_cd";
var CHARGING_KEY = "sk_eshin_charging";
var CHARGE_END_KEY = "sk_eshin_charge_end";
var TELEGRAPH_KEY = "sk_eshin_telegraph";
var TARGET_UUID_KEY = "sk_eshin_target";
var MODE_KEY = "sk_eshin_mode";           // "revenge" | "retreat"
var MODE_END_KEY = "sk_eshin_mode_end";
var BASE_SPEED_KEY = "sk_eshin_base_speed";
var HIT_COUNT_KEY = "sk_eshin_hit_count";

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

    startCharge(npc, world, data, target, now);
}

function damaged(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;

    var data = npc.getStoreddata();
    var hits = getInt(data, HIT_COUNT_KEY) + 1;
    if (hits >= DODGE_EVERY_HITS) {
        data.put(HIT_COUNT_KEY, "0");
        try { e.setCanceled(true); } catch (err) {}
        spawnAcrobaticsDodgeFx(npc);
        return;
    }
    data.put(HIT_COUNT_KEY, String(hits));

    if (parseFloat(String(e.damage)) < DAMAGE_RETREAT_THRESHOLD) return;

    clearChargeState(data);
    applyRetreatMode(npc, data, npc.getWorld().getTotalTime(), true);
}

function spawnAcrobaticsDodgeFx(npc) {
    try {
        var world = npc.getWorld();
        var x = npc.getX();
        var y = npc.getY();
        var z = npc.getZ();
        var spreadW = 0.3;
        var spreadH = 0.9;
        try {
            var mc = npc.getMCEntity();
            if (mc != null) {
                spreadW = mc.getBbWidth() * 0.5;
                spreadH = mc.getBbHeight() * 0.5;
            }
        } catch (bbErr) {}

        world.spawnParticle(DODGE_LEAF_PARTICLE, x, y + spreadH, z,
            spreadW, spreadH, spreadW, 0.05, DODGE_LEAF_COUNT);
        world.playSoundAt(NpcAPI.getIPos(x, y, z), DODGE_SOUND, 3.5, 0.5 + Math.random());
    } catch (err) {}
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

function applyChargeStance(npc) {
    try {
        npc.getAi().setRetaliateType(RETALIATE_NONE);
        npc.getAi().setWalkingSpeed(0);
    } catch (err) {}
    try {
        AbilityCombatHelper.stopNavigation(npc);
    } catch (err2) {}
    try {
        npc.setMotionX(0);
        npc.setMotionY(0);
        npc.setMotionZ(0);
    } catch (err3) {}
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
    var yaw = Number(target.getRotation());
    var pos = findSafeBehindPos(npc, target, world);
    if (pos == null) return false;

    var bx = pos.x;
    var by = pos.y;
    var bz = pos.z;

    teleportNpc(npc, bx, by, bz, yaw);

    if (withAttack) {
        try {
            if (typeof npc.setAttackTarget == "function") {
                npc.setAttackTarget(target);
            }
        } catch (err) {}
        applyWfmStun(target, world, bx, by, bz, STUN_TICKS);
    }

    try {
        world.spawnParticle("minecraft:poof", bx, by + 0.5, bz, 0.2, 0.1, 0.2, 0.02, 10);
        world.playSoundAt(NpcAPI.getIPos(bx, by, bz), "minecraft:entity.enderman.teleport", 0.6, 1.6);
    } catch (err2) {}
    return true;
}

/**
 * Ищет точку за спиной цели: клип по стенам + noCollision хитбокса NPC.
 * Возвращает {x,y,z} или null, если безопасной точки нет.
 */
function findSafeBehindPos(npc, target, world) {
    var yaw = Number(target.getRotation());
    var rad = yaw * Math.PI / 180.0;
    // За спиной: opposite of look vector
    var dirX = Math.sin(rad);
    var dirZ = -Math.cos(rad);
    var tx = target.getX();
    var ty = target.getY();
    var tz = target.getZ();
    var eyeY = ty + getEntityEyeHeight(target, 1.0);

    var best = null;
    var dist = BACK_MIN_OFFSET;
    while (dist <= BACK_OFFSET + 0.001) {
        var x = tx + dirX * dist;
        var z = tz + dirZ * dist;
        var y = resolveStandY(world, x, z, ty);
        if (y == null || Math.abs(y - ty) > BACK_MAX_Y_DELTA) {
            break;
        }
        if (!canNpcOccupy(npc, world, x, y, z)) {
            break;
        }
        // Стена между целью и точкой = «за стену» — дальше не идём
        if (!hasClearLine(npc, tx, eyeY, tz, x, y + getEntityEyeHeight(npc, 1.0), z)) {
            break;
        }
        best = { x: x, y: y, z: z };
        dist += BACK_CLIP_STEP;
    }
    return best;
}

function resolveStandY(world, x, z, preferY) {
    try {
        return AbilityCombatHelper.findGroundY(world, x, z, preferY);
    } catch (err) {
        return preferY;
    }
}

function getEntityEyeHeight(entity, fallback) {
    try {
        var mc = entity.getMCEntity();
        if (mc != null && typeof mc.getEyeHeight == "function") {
            return mc.getEyeHeight();
        }
        if (mc != null && typeof mc.getBbHeight == "function") {
            return mc.getBbHeight() * 0.85;
        }
    } catch (err) {}
    return fallback;
}

function canNpcOccupy(npc, world, x, y, z) {
    try {
        var mc = npc.getMCEntity();
        if (mc != null && mc.level != null) {
            var moved = mc.getBoundingBox().move(
                x - mc.getX(),
                y - mc.getY(),
                z - mc.getZ()
            );
            return mc.level.noCollision(mc, moved);
        }
    } catch (err) {}
    return canStandAtBlocks(world, x, y, z);
}

function canStandAtBlocks(world, x, y, z) {
    try {
        var footY = Math.floor(y);
        var fx = Math.floor(x);
        var fz = Math.floor(z);
        if (!isSolidBlockName(world.getBlock(fx, footY - 1, fz))) return false;
        if (isSolidBlockName(world.getBlock(fx, footY, fz))) return false;
        if (isSolidBlockName(world.getBlock(fx, footY + 1, fz))) return false;
        return true;
    } catch (err) {
        return false;
    }
}

function isSolidBlockName(block) {
    if (block == null) return false;
    var name = "";
    try { name = String(block.getName()); } catch (err) { return true; }
    if (name == "minecraft:air" || name == "minecraft:cave_air" || name == "minecraft:void_air") {
        return false;
    }
    // Явные полы — solid
    if (name.indexOf("grass_block") >= 0 || name.indexOf("grass_path") >= 0
        || name.indexOf("snow_block") >= 0 || name.indexOf("mycelium") >= 0
        || name.indexOf("podzol") >= 0) {
        return true;
    }
    // Типичный passable
    if (name.indexOf("tall_grass") >= 0 || name.indexOf("fern") >= 0
        || name.indexOf("flower") >= 0 || name.indexOf("torch") >= 0
        || name.indexOf("carpet") >= 0 || name == "minecraft:snow"
        || name.indexOf("button") >= 0 || name.indexOf("pressure_plate") >= 0
        || name.indexOf("sign") >= 0 || name.indexOf("banner") >= 0
        || name.indexOf("rail") >= 0 || name.indexOf("sapling") >= 0
        || name.indexOf("mushroom") >= 0 || name.indexOf("vine") >= 0
        || name.indexOf("ladder") >= 0 || name.indexOf("sugar_cane") >= 0
        || name.indexOf("web") >= 0 || name.indexOf("fire") >= 0) {
        return false;
    }
    return true;
}

/** Raycast COLLIDER: есть ли стена между двумя точками. */
function hasClearLine(npc, x0, y0, z0, x1, y1, z1) {
    try {
        var mc = npc.getMCEntity();
        if (mc == null || mc.level == null) return hasClearLineBlocks(npc.getWorld(), x0, y0, z0, x1, y1, z1);

        var RayTraceContext = Java.type("net.minecraft.world.RayTraceContext");
        var Vector3d = Java.type("net.minecraft.util.math.vector.Vector3d");
        var RayTraceResult = Java.type("net.minecraft.util.math.RayTraceResult");

        var from = new Vector3d(x0, y0, z0);
        var to = new Vector3d(x1, y1, z1);
        var ctx = new RayTraceContext(
            from, to,
            RayTraceContext.BlockMode.COLLIDER,
            RayTraceContext.FluidMode.NONE,
            mc
        );
        var result = mc.level.clip(ctx);
        if (result == null || result.getType() == RayTraceResult.Type.MISS) return true;

        var hit = result.getLocation();
        var dx = hit.x - x1;
        var dy = hit.y - y1;
        var dz = hit.z - z1;
        // Попадание почти в точку назначения — ок (пол/край)
        return (dx * dx + dy * dy + dz * dz) < 0.36;
    } catch (err) {
        try {
            return hasClearLineBlocks(npc.getWorld(), x0, y0, z0, x1, y1, z1);
        } catch (err2) {
            return false;
        }
    }
}

function hasClearLineBlocks(world, x0, y0, z0, x1, y1, z1) {
    var dx = x1 - x0;
    var dy = y1 - y0;
    var dz = z1 - z0;
    var dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (dist < 0.01) return true;
    var steps = Math.max(2, Math.ceil(dist / 0.25));
    for (var i = 1; i < steps; i++) {
        var t = i / steps;
        var bx = Math.floor(x0 + dx * t);
        var by = Math.floor(y0 + dy * t);
        var bz = Math.floor(z0 + dz * t);
        if (isSolidBlockName(world.getBlock(bx, by, bz))) return false;
    }
    return true;
}

function teleportNpc(npc, x, y, z, yaw) {
    try {
        var mc = npc.getMCEntity();
        if (mc != null) {
            try {
                mc.teleportTo(x, y, z);
            } catch (tpErr) {
                npc.setPosition(x, y, z);
            }
            try {
                mc.yRot = yaw;
                mc.yHeadRot = yaw;
                mc.yBodyRot = yaw;
            } catch (rotErr) {}
        } else {
            npc.setPosition(x, y, z);
        }
        npc.setRotation(yaw);
    } catch (err) {
        try {
            npc.setPosition(x, y, z);
            npc.setRotation(yaw);
        } catch (err2) {}
    }

    try {
        npc.setMotionX(0);
        npc.setMotionY(0);
        npc.setMotionZ(0);
    } catch (err3) {}

    try {
        AbilityCombatHelper.stopNavigation(npc);
    } catch (err4) {}
}

function resolveStunEffect() {
    try {
        var WFMEffects = Java.type("wfm.common.effect.WFMEffects");
        var fromWfm = WFMEffects.STUN.get();
        if (fromWfm != null) return fromWfm;
    } catch (e1) {}

    try {
        return ForgeRegistries.POTIONS.getValue(new ResourceLocation("wfm", "stun"));
    } catch (e2) {}

    try {
        return ForgeRegistries.POTIONS.getValue(new ResourceLocation(STUN_EFFECT_ID));
    } catch (e3) {}

    return null;
}

function applyWfmStun(target, world, x, y, z, ticks) {
    try {
        if (target == null) return;

        var effect = resolveStunEffect();
        if (effect == null) {
            log("sk_eshin: stun effect not found: " + STUN_EFFECT_ID);
            return;
        }

        // instanceof LivingEntity в Nashorn часто ложный — проверка в Java-хелпере
        AbilityCombatHelper.applyEffect(target, effect, ticks, 0);

        var living = target.getMCEntity();
        if (living != null) {
            try {
                if (!living.hasEffect(effect)) {
                    living.addEffect(new EffectInstance(effect, ticks, 0, false, true, true));
                }
            } catch (addErr) {
                try {
                    living.addEffect(new EffectInstance(effect, ticks, 0));
                } catch (addErr2) {
                    log("sk_eshin stun add fail: " + addErr2);
                }
            }
        }

        var pitch = (Math.random() - Math.random()) * 0.2 + 1.0;
        world.playSoundAt(NpcAPI.getIPos(x, y, z), STUN_SOUND, STUN_SOUND_VOLUME, pitch);
    } catch (err) {
        try { log("sk_eshin stun fail: " + err); } catch (e2) {}
    }
}

function startCharge(npc, world, data, target, now) {
    data.put(CHARGING_KEY, "1");
    data.put(CHARGE_END_KEY, String(now + CHARGE_TICKS));
    data.put(TARGET_UUID_KEY, String(target.getUUID()));
    clearTelegraph(data);
    applyChargeStance(npc);

    try {
        var tid = TelegraphAPI.circle(
            npc, npc.getX(), npc.getY(), npc.getZ(),
            TELEGRAPH_RADIUS, CHARGE_TICKS, TELEGRAPH_COLOR
        );
        data.put(TELEGRAPH_KEY, String(tid));
        TelegraphAPI.followNpc(tid, npc);
    } catch (te) {}

    try {
        world.spawnParticle("minecraft:smoke", npc.getX(), npc.getY() + 1.0, npc.getZ(),
            0.2, 0.05, 0.2, 0.02, 10);
    } catch (err) {}
}

function doChargingTick(npc, world, data, now) {
    applyChargeStance(npc);

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
    var target = resolveTarget(npc, world, data);
    if (target == null || !target.isAlive()) {
        clearChargeState(data);
        applyRevengeMode(npc, data, world.getTotalTime(), false);
        return;
    }

    var now = world.getTotalTime();
    var teleported = teleportBehind(npc, target, world, true);
    clearChargeState(data);
    if (!teleported) {
        // За спиной стена / нет места — без CD, чтобы можно было повторить
        applyRevengeMode(npc, data, now, false);
        return;
    }

    data.put(CD_KEY, String(now + COOLDOWN_TICKS));
    applyRevengeMode(npc, data, now, true);
}

function resolveTarget(npc, world, data) {
    var uuid = "";
    try {
        if (data.has(TARGET_UUID_KEY)) uuid = String(data.get(TARGET_UUID_KEY));
    } catch (err) {}

    if (uuid && uuid != "" && uuid != "null") {
        try {
            var players = world.getAllPlayers();
            for (var i = 0; i < players.length; i++) {
                if (String(players[i].getUUID()) == uuid) return players[i];
            }
        } catch (err2) {}

        try {
            var near = world.getNearbyEntities(
                NpcAPI.getIPos(npc.getX(), npc.getY(), npc.getZ()),
                48,
                EntitiesType.ANY
            );
            for (var j = 0; j < near.length; j++) {
                if (String(near[j].getUUID()) == uuid) return near[j];
            }
        } catch (err3) {}
    }

    try {
        return npc.getAttackTarget();
    } catch (err4) {
        return null;
    }
}

function clearTelegraph(data) {
    try {
        if (data.has(TELEGRAPH_KEY)) {
            var tid = String(data.get(TELEGRAPH_KEY));
            if (tid && tid != "null" && tid != "" && tid != "0") {
                TelegraphAPI.remove(tid);
            }
            data.remove(TELEGRAPH_KEY);
        }
    } catch (err) {}
}

function clearChargeState(data) {
    data.put(CHARGING_KEY, "0");
    data.put(CHARGE_END_KEY, "0");
    data.put(TARGET_UUID_KEY, "");
    clearTelegraph(data);
}

function clearState(data) {
    clearChargeState(data);
    data.put(MODE_KEY, "retreat");
    data.put(MODE_END_KEY, "0");
    data.put(HIT_COUNT_KEY, "0");
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
