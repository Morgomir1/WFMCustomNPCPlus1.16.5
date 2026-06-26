// =====================================================
// Скавен инженер — "Варп-бомба"
// Дальняя абилка: выстрел предметом -> детонация AoE + яд.
// Требует события: tick + projectileImpact.
// =====================================================

var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();
var EntitiesType = Java.type("noppes.npcs.api.constants.EntitiesType");

// -------------------------
// НАСТРОЙКИ
// -------------------------
var COOLDOWN_TICKS = 120; // 6 секунд
var RANGE = 16.0;
var ACCURACY = 6;
var BOMB_ITEM = "wfm:warpstone";
var DAMAGE = 7.0;
var RADIUS = 3.2;
var POISON_SECONDS = 4;

// -------------------------
// storeddata keys
// -------------------------
var CD_KEY = "sk_eng_cd";

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;

    var world = npc.getWorld();
    var data = npc.getStoreddata();
    var now = world.getTotalTime();

    if (now < getInt(data, CD_KEY)) return;

    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) return;
    if (flatDistance(npc, target) > RANGE) return;
    if (typeof npc.canSeeEntity == "function" && !npc.canSeeEntity(target)) return;

    var item = world.createItem(BOMB_ITEM, 1);
    var proj = npc.shootItem(target, item, ACCURACY);
    if (proj == null) return;

    proj.enableEvents();
    proj.getTempdata().put("sk_warp_bomb", 1);
    try {
        var mc = proj.getMCEntity();
        if (mc != null) {
            if (typeof mc.setOwner == "function") {
                mc.setOwner(npc.getMCEntity());
            } else {
                try { mc.thrower = npc.getMCEntity(); } catch (e) {}
                try { mc.npc = npc.getMCEntity(); } catch (e2) {}
            }
        }
    } catch (e3) {}

    data.put(CD_KEY, String(now + COOLDOWN_TICKS));
}

function projectileImpact(event) {
    if (event.projectile == null) return;
    if (event.projectile.getTempdata().get("sk_warp_bomb") != 1) return;

    var world = event.projectile.getWorld();
    var x = event.projectile.getX();
    var y = event.projectile.getY();
    var z = event.projectile.getZ();

    if (event.type == 0 && event.target != null) {
        var target = wrapImpactTarget(event);
        if (target != null) {
            x = target.getX();
            y = target.getY();
            z = target.getZ();
        }
    }

    doExplosion(world, x, y, z);
}

function doExplosion(world, x, y, z) {
    var pos = NpcAPI.getIPos(x, y, z);
    var list = world.getNearbyEntities(pos, RADIUS, EntitiesType.ANY);
    for (var i = 0; i < list.length; i++) {
        var ent = list[i];
        if (!isPlayerEntity(ent)) continue;
        try {
            ent.damage(DAMAGE);
            ent.addPotionEffect(PotionEffectType_POISON, POISON_SECONDS, 0, false);
        } catch (e) {}
    }
    try {
        world.spawnParticle("minecraft:witch", x, y + 0.2, z, 0.4, 0.15, 0.4, 0.02, 16);
        world.playSoundAt(NpcAPI.getIPos(x, y, z), "minecraft:block.respawn_anchor.charge", 0.8, 1.2);
    } catch (e) {}
}

function wrapImpactTarget(event) {
    var target = event.target;
    if (target == null) return null;
    if (typeof target.getMCEntity == "function") return target;
    return NpcAPI.getIEntity(target);
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
    var dz = a.getZ() - b.getZ();
    return Math.sqrt(dx * dx + dz * dz);
}

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}
