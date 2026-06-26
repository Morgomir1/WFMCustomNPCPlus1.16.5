/**
 * Boss Dash/Charge — CustomNPC+ JS (WFM 1.16.5)
 * Рывок вперед на 10 блоков. Фиксация поворота. Урон + отбрасывание врагам на пути.
 */

var DASH_DISTANCE = 10.0;
var DASH_DURATION_TICKS = 10;
var DASH_WARMUP_TICKS = 15;
var DASH_COOLDOWN_TICKS = 100;
var DASH_DAMAGE = 8.0;
var DASH_KNOCKBACK = 2.0;
var DASH_RADIUS = 1.5;

var K_CD = "dash_cd";
var K_PHASE = "dash_phase";
var K_SX = "dash_sx"; var K_SY = "dash_sy"; var K_SZ = "dash_sz";
var K_EX = "dash_ex"; var K_EY = "dash_ey"; var K_EZ = "dash_ez";
var K_YAW = "dash_yaw";
var K_TICK = "dash_tick";

function init(event) {
    var n = event.npc;
    n.setMaxHealth(300.0);
    n.setHealth(300.0);
    n.getStats().setResistance(0, 0.15);
    n.getStats().setResistance(1, 0.15);
    n.getStats().setResistance(2, 0.15);
    n.getStats().setResistance(3, 0.15);
    n.getStats().getMelee().setStrength(12);
    n.getStats().setAggroRange(48);
    n.getStats().setMaxHealth(300);

    n.setName("\u00a74\u00a7l\u0411\u043e\u0435\u0432\u043e\u0439 \u0412\u043e\u0436\u0430\u043a");

    var d = n.getStoreddata();
    d.put(K_CD, 0);
    d.put(K_PHASE, 0);
    d.put(K_TICK, 0);

    n.getTimers().forceStart(1, 20, true);
}

function tick(event) {
    var n = event.npc;
    var d = n.getStoreddata();
    var phase = d.get(K_PHASE);

    var cd = d.get(K_CD);
    if (cd > 0) d.put(K_CD, cd - 1);

    if (phase == 0) return;

    if (phase == 1) {
        var t = d.get(K_TICK);
        d.put(K_TICK, t - 1);
        if (t % 4 == 0) spawnWarmupParticles(n);
        var yaw = d.get(K_YAW);
        n.setRotation(yaw);
        if (t <= 0) {
            d.put(K_PHASE, 2);
            d.put(K_TICK, DASH_DURATION_TICKS);
            d.put(K_CD, DASH_COOLDOWN_TICKS);
            spawnStartBurst(n);
        }
        return;
    }

    if (phase == 2) {
        var t = d.get(K_TICK);
        if (t <= 0) {
            d.put(K_PHASE, 0);
            d.put(K_TICK, 0);
            var ex = d.get(K_EX);
            var ey = d.get(K_EY);
            var ez = d.get(K_EZ);
            n.setPosition(ex, ey, ez);
            spawnEndBurst(n, ex, ey, ez);
            n.getWorld().playSoundAt({x: ex, y: ey, z: ez}, "minecraft:entity.enderman.teleport", 1.0, 0.8);
            return;
        }
        var progress = 1.0 - (t - 1) / DASH_DURATION_TICKS;
        var sx = d.get(K_SX); var sy = d.get(K_SY); var sz = d.get(K_SZ);
        var ex = d.get(K_EX); var ey = d.get(K_EY); var ez = d.get(K_EZ);
        var yaw = d.get(K_YAW);
        var cx = sx + (ex - sx) * progress;
        var cy = sy + (ey - sy) * progress;
        var cz = sz + (ez - sz) * progress;
        n.setPosition(cx, cy, cz);
        n.setRotation(yaw);
        applyDashHitCheck(n, cx, cy, cz);
        spawnDashTrail(n, cx, cy, cz);
        d.put(K_TICK, t - 1);
    }
}

function timer(event) {
    if (event.id != 1) return;
    var n = event.npc;
    var d = n.getStoreddata();
    if (d.get(K_PHASE) != 0) return;
    if (d.get(K_CD) > 0) return;

    var target = n.getAttackTarget();
    if (target == null || !target.isAlive()) return;

    var dx = target.getX() - n.getX();
    var dz = target.getZ() - n.getZ();
    var dist = Math.sqrt(dx * dx + dz * dz);
    if (dist >= 6 && dist <= 18 && Math.random() < 0.2) {
        beginWarmup(n, target, d);
    }
}

function damaged(event) {
    if (event.damage < 8) return;
    var n = event.npc;
    var d = n.getStoreddata();
    if (d.get(K_PHASE) != 0 || d.get(K_CD) > 0) return;
    var src = event.source;
    if (src == null || !src.isAlive()) return;
    beginWarmup(n, src, d);
}

function target(event) {
    var n = event.npc;
    var d = n.getStoreddata();
    if (d.get(K_PHASE) != 0 || d.get(K_CD) > 0) return;
    var t = event.entity;
    if (t == null || !t.isAlive()) return;
    var dx = t.getX() - n.getX();
    var dz = t.getZ() - n.getZ();
    var dist = Math.sqrt(dx * dx + dz * dz);
    if (dist >= 8 && dist <= 18 && Math.random() < 0.3) {
        beginWarmup(n, t, d);
    }
}

function targetLost(event) {
    var d = event.npc.getStoreddata();
    if (d.get(K_PHASE) == 1) d.put(K_PHASE, 0);
}

function beginWarmup(n, target, d) {
    var dx = target.getX() - n.getX();
    var dz = target.getZ() - n.getZ();
    var len = Math.sqrt(dx * dx + dz * dz);
    if (len < 0.1) return;

    var dirX = dx / len;
    var dirZ = dz / len;
    var dashDist = Math.min(DASH_DISTANCE, len + 1);
    var endX = n.getX() + dirX * dashDist;
    var endZ = n.getZ() + dirZ * dashDist;
    var endY = findGroundY(n.getWorld(), endX, endZ, n.getY());
    var yaw = Math.atan2(dirZ, dirX) * 57.2957795 - 90.0;

    d.put(K_PHASE, 1);
    d.put(K_SX, n.getX());
    d.put(K_SY, n.getY());
    d.put(K_SZ, n.getZ());
    d.put(K_EX, endX);
    d.put(K_EY, endY);
    d.put(K_EZ, endZ);
    d.put(K_YAW, yaw);
    d.put(K_TICK, DASH_WARMUP_TICKS);

    n.clearNavigation();
    n.getWorld().playSoundAt(n.getPos(), "minecraft:entity.enderman.teleport", 1.0, 1.2);
}

function applyDashHitCheck(n, cx, cy, cz) {
    var w = n.getWorld();
    var yaw = n.getStoreddata().get(K_YAW);
    var rad = (yaw + 90.0) * 0.0174532925;
    var dirX = Math.cos(rad);
    var dirZ = Math.sin(rad);
    var mcNpc = n.getMCEntity();

    var near = w.getNearbyEntities({x: cx, y: cy + 1.0, z: cz}, DASH_RADIUS + 1.0, 5);

    for (var i = 0; i < near.length; i++) {
        var e = near[i];
        if (e == n || !e.isAlive()) continue;
        if (mcNpc.isAlliedTo(e.getMCEntity())) continue;

        var eDist = Math.sqrt(
            (e.getX() - cx) * (e.getX() - cx) +
            (e.getZ() - cz) * (e.getZ() - cz));
        if (eDist > DASH_RADIUS) continue;

        e.damage(DASH_DAMAGE);
        e.setMotionX(dirX * DASH_KNOCKBACK);
        e.setMotionY(0.5);
        e.setMotionZ(dirZ * DASH_KNOCKBACK);

        w.spawnParticle(ParticleType_CRIT,
            e.getX(), e.getY() + e.getHeight() * 0.5, e.getZ(),
            dirX * 0.3, 0.3, dirZ * 0.3, 0, 6);
    }
}

function findGroundY(w, x, z, startY) {
    var bx = Math.floor(x);
    var bz = Math.floor(z);
    for (var by = Math.floor(startY) + 2; by >= Math.floor(startY) - 6; by--) {
        var b = w.getBlock(bx, by, bz);
        var b1 = w.getBlock(bx, by + 1, bz);
        var b2 = w.getBlock(bx, by + 2, bz);
        if (b != null && isSolid(b) && !isSolid(b1) && !isSolid(b2)) return by + 1.0;
    }
    return startY;
}

function isSolid(block) {
    var n = block.getName();
    return n != "minecraft:air" && n != "minecraft:cave_air" && n != "minecraft:void_air";
}

function spawnWarmupParticles(n) {
    var w = n.getWorld();
    var x = n.getX(); var y = n.getY(); var z = n.getZ();
    for (var i = 0; i < 3; i++) {
        w.spawnParticle(ParticleType_CLOUD,
            x + (Math.random() - 0.5) * 1.2, y + 0.1, z + (Math.random() - 0.5) * 1.2,
            0, 0.05, 0, 0.02, 1);
    }
}

function spawnStartBurst(n) {
    var w = n.getWorld();
    var x = n.getX(); var y = n.getY(); var z = n.getZ();
    for (var i = 0; i < 12; i++) {
        var a = (i / 12) * Math.PI * 2;
        w.spawnParticle(ParticleType_CLOUD,
            x + Math.cos(a) * 1.5, y + 0.2, z + Math.sin(a) * 1.5,
            0, 0.1, 0, 0, 1);
    }
}

function spawnDashTrail(n, x, y, z) {
    var w = n.getWorld();
    for (var i = 0; i < 4; i++) {
        w.spawnParticle(ParticleType_CLOUD,
            x + (Math.random() - 0.5) * 1.0,
            y + Math.random() * 2.0,
            z + (Math.random() - 0.5) * 1.0,
            (Math.random() - 0.5) * 0.1, 0.05, (Math.random() - 0.5) * 0.1, 0, 1);
    }
}

function spawnEndBurst(n, x, y, z) {
    var w = n.getWorld();
    for (var i = 0; i < 15; i++) {
        var a = Math.random() * Math.PI * 2;
        var dist = Math.random() * 2.5;
        w.spawnParticle(ParticleType_EXPLOSION_NORMAL,
            x + Math.cos(a) * dist, y + Math.random() * 2.5, z + Math.sin(a) * dist,
            (Math.random() - 0.5) * 0.2, 0.1, (Math.random() - 0.5) * 0.2, 0, 1);
    }
    for (var i = 0; i < 8; i++) {
        var a = (i / 8) * Math.PI * 2;
        w.spawnParticle(ParticleType_CLOUD,
            x + Math.cos(a) * 2.0, y + 0.2, z + Math.sin(a) * 2.0,
            0, 0.1, 0, 0, 1);
    }
}
