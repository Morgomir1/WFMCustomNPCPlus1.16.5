package noppes.npcs.abilities;

import noppes.npcs.api.IWorld;
import noppes.npcs.api.entity.IEntity;

import java.util.Random;

public final class AbilityVfx {
    private AbilityVfx() {
    }

    public static void spawnChargeParticles(
            final IWorld world,
            final double x,
            final double y,
            final double z,
            final boolean jumpStyle) {
        final String particle = jumpStyle ? "minecraft:soul_fire_flame" : "minecraft:cloud";
        final Random r = AbilityCombatHelper.random();
        for (int i = 0; i < 3; i++) {
            safeSpawn(world, particle,
                    x + (r.nextDouble() - 0.5) * 1.2,
                    y + 0.1,
                    z + (r.nextDouble() - 0.5) * 1.2,
                    0, 0.05, 0, 0.02, 1);
        }
    }

    public static void spawnBloodCharge(
            final IWorld world,
            final double x,
            final double y,
            final double z) {
        final Random r = AbilityCombatHelper.random();
        for (int i = 0; i < 4; i++) {
            safeSpawn(world, "minecraft:entity_effect",
                    x + (r.nextDouble() - 0.5) * 1.2,
                    y + 0.2 + r.nextDouble() * 0.4,
                    z + (r.nextDouble() - 0.5) * 1.2,
                    0.9, 0.1, 0.1, 0, 1);
        }
    }

    public static void spawnStartBurst(
            final IWorld world,
            final double x,
            final double y,
            final double z,
            final boolean jumpStyle) {
        final String particle = jumpStyle ? "minecraft:soul_fire_flame" : "minecraft:cloud";
        for (int i = 0; i < 10; i++) {
            final double a = (i / 10.0) * Math.PI * 2;
            safeSpawn(world, particle,
                    x + Math.cos(a) * 1.4,
                    y + 0.2,
                    z + Math.sin(a) * 1.4,
                    0, 0.12, 0, 0, 1);
        }
    }

    public static void spawnBloodBurst(
            final IWorld world,
            final double x,
            final double y,
            final double z,
            final double radius) {
        final int count = 12;
        for (int i = 0; i < count; i++) {
            final double a = (i / (double) count) * Math.PI * 2;
            safeSpawn(world, "minecraft:entity_effect",
                    x + Math.cos(a) * radius,
                    y + 0.3,
                    z + Math.sin(a) * radius,
                    0.9, 0.1, 0.1, 0, 1);
        }
        safeSpawn(world, "minecraft:damage_indicator", x, y + 0.8, z, 0, 0, 0, 0, 6);
    }

    public static void spawnBatSmoke(
            final IWorld world,
            final double x,
            final double y,
            final double z,
            final double radius) {
        final Random r = AbilityCombatHelper.random();
        for (int i = 0; i < 10; i++) {
            final double a = r.nextDouble() * Math.PI * 2;
            final double dist = r.nextDouble() * radius;
            safeSpawn(world, "minecraft:smoke",
                    x + Math.cos(a) * dist,
                    y + 0.2 + r.nextDouble(),
                    z + Math.sin(a) * dist,
                    0, 0.04, 0, 0.01, 2);
        }
        safeSpawn(world, "minecraft:large_smoke", x, y + 0.5, z, 0, 0.05, 0, 0.02, 4);
    }

    public static void spawnDashTrail(final IWorld world, final double x, final double y, final double z) {
        final Random r = AbilityCombatHelper.random();
        for (int i = 0; i < 3; i++) {
            safeSpawn(world, "minecraft:cloud",
                    x + (r.nextDouble() - 0.5) * 0.8,
                    y + r.nextDouble() * 1.5,
                    z + (r.nextDouble() - 0.5) * 0.8,
                    0, 0.04, 0, 0, 1);
        }
    }

    public static void spawnJumpTrail(final IWorld world, final double x, final double y, final double z) {
        safeSpawn(world, "minecraft:end_rod", x, y, z, 0, 0, 0, 0, 1);
    }

    public static void spawnLandBurst(
            final IWorld world,
            final double x,
            final double y,
            final double z,
            final boolean jumpStyle) {
        final int count = jumpStyle ? 18 : 12;
        final Random r = AbilityCombatHelper.random();
        for (int i = 0; i < count; i++) {
            final double a = r.nextDouble() * Math.PI * 2;
            final double dist = r.nextDouble() * 2.2;
            safeSpawn(world, "minecraft:explosion",
                    x + Math.cos(a) * dist,
                    y + 0.2 + r.nextDouble() * 1.5,
                    z + Math.sin(a) * dist,
                    0, 0.05, 0, 0, 1);
        }
    }

    public static void spawnHitParticle(final IWorld world, final IEntity ent) {
        try {
            double hy = ent.getY() + 0.9;
            try {
                hy = ent.getY() + ent.getHeight() * 0.5;
            } catch (final Exception ignored) {
            }
            safeSpawn(world, "minecraft:crit", ent.getX(), hy, ent.getZ(), 0, 0.2, 0, 0, 4);
        } catch (final Exception ignored) {
        }
    }

    public static void spawnMuzzleFlash(final IWorld world, final double x, final double y, final double z) {
        final Random r = AbilityCombatHelper.random();
        safeSpawn(world, "minecraft:smoke", x, y, z, 0, 0.05, 0, 0.02, 6);
        safeSpawn(world, "minecraft:large_smoke", x, y, z,
                (r.nextDouble() - 0.5) * 0.1, 0.08, (r.nextDouble() - 0.5) * 0.1, 0.01, 3);
        safeSpawn(world, "minecraft:crit", x, y, z, 0, 0, 0, 0, 2);
    }

    public static void spawnHolySplash(final IWorld world, final double x, final double y, final double z) {
        final Random r = AbilityCombatHelper.random();
        for (int i = 0; i < 16; i++) {
            final double a = r.nextDouble() * Math.PI * 2;
            final double dist = r.nextDouble() * 3.5;
            safeSpawn(world, "minecraft:entity_effect",
                    x + Math.cos(a) * dist,
                    y + 0.2 + r.nextDouble() * 1.5,
                    z + Math.sin(a) * dist,
                    0.9, 0.95, 0.3, 0, 1);
        }
        safeSpawn(world, "minecraft:splash", x, y + 0.5, z, 0, 0.1, 0, 0.05, 12);
    }

    public static void spawnFireRing(final IWorld world, final double x, final double y, final double z, final double radius) {
        final Random r = AbilityCombatHelper.random();
        for (int i = 0; i < 12; i++) {
            final double a = (i / 12.0) * Math.PI * 2;
            safeSpawn(world, "minecraft:flame",
                    x + Math.cos(a) * radius,
                    y + 0.1,
                    z + Math.sin(a) * radius,
                    0, 0.06, 0, 0.02, 1);
        }
        safeSpawn(world, "minecraft:lava",
                x + (r.nextDouble() - 0.5) * radius,
                y + 0.1,
                z + (r.nextDouble() - 0.5) * radius,
                0, 0.02, 0, 0, 2);
    }

    public static void spawnNetTrail(final IWorld world, final double x, final double y, final double z) {
        final Random r = AbilityCombatHelper.random();
        safeSpawn(world, "minecraft:cloud",
                x + (r.nextDouble() - 0.5) * 0.6,
                y + r.nextDouble() * 0.8,
                z + (r.nextDouble() - 0.5) * 0.6,
                0, 0.02, 0, 0, 2);
    }

    public static void spawnFeastBloodBurst(final IWorld world, final double x, final double y, final double z, final int count) {
        final Random r = AbilityCombatHelper.random();
        for (int i = 0; i < count; i++) {
            final double ox = (r.nextDouble() - 0.5) * 1.4;
            final double oy = r.nextDouble() * 0.9;
            final double oz = (r.nextDouble() - 0.5) * 1.4;
            final double vx = (r.nextDouble() - 0.5) * 0.45;
            final double vy = 0.05 + r.nextDouble() * 0.35;
            final double vz = (r.nextDouble() - 0.5) * 0.45;
            safeSpawn(world, "minecraft:damage_indicator", x + ox, y + oy + 0.35, z + oz, vx, vy, vz, 0.02, 1);
            safeSpawn(world, "minecraft:crimson_spore", x + ox, y + oy, z + oz, vx, vy * 0.6, vz, 0.08, 2);
        }
        safeSpawn(world, "minecraft:sweep_attack", x, y + 0.7, z, 0, 0, 0, 0, 1);
        safeSpawn(world, "minecraft:angry_villager", x, y + 1.0, z, 0, 0, 0, 0, 2);
    }

    public static void spawnFeastFinishFlourish(final IWorld world, final double x, final double y, final double z) {
        for (int ring = 0; ring < 3; ring++) {
            final double radius = 0.6 + ring * 0.45;
            for (int a = 0; a < 8; a++) {
                final double angle = a * (Math.PI * 2 / 8);
                final double px = x + Math.cos(angle) * radius;
                final double pz = z + Math.sin(angle) * radius;
                safeSpawn(world, "minecraft:damage_indicator", px, y + 0.5, pz, 0, 0.25, 0, 0.03, 1);
                safeSpawn(world, "minecraft:crimson_spore", px, y + 0.2, pz, 0, 0.08, 0, 0.05, 2);
            }
        }
    }

    public static void spawnDecayCloud(final IWorld world, final double x, final double y, final double z, final float height) {
        final Random r = AbilityCombatHelper.random();
        for (int i = 0; i < 12; i++) {
            final double ox = (r.nextDouble() - 0.5) * 0.9;
            final double oy = r.nextDouble() * height;
            final double oz = (r.nextDouble() - 0.5) * 0.9;
            safeSpawn(world, "minecraft:smoke", x + ox, y + oy, z + oz, 0, 0.06, 0, 0.02, 2);
            safeSpawn(world, "minecraft:cloud", x + ox, y + oy + 0.3, z + oz, 0, 0.04, 0, 0.01, 1);
        }
    }

    /** Зарядка Дракенфельса: огонь душ + песок душ + WFM fog. */
    public static void spawnDarkCharge(final IWorld world, final double x, final double y, final double z) {
        final Random r = AbilityCombatHelper.random();
        for (int i = 0; i < 4; i++) {
            final double ox = (r.nextDouble() - 0.5) * 1.3;
            final double oy = 0.2 + r.nextDouble() * 0.6;
            final double oz = (r.nextDouble() - 0.5) * 1.3;
            safeSpawn(world, "minecraft:soul_fire_flame", x + ox, y + oy, z + oz, 0, 0.05, 0, 0.01, 1);
            safeSpawn(world, "minecraft:soul", x + ox * 0.7, y + oy * 0.5, z + oz * 0.7, 0, 0.04, 0, 0.01, 1);
        }
        spawnWfmFog(world, x, y + 0.35, z, 0.9, 2);
    }

    /** Burst Poison Feast / cleave finish: кольцо души + туман. */
    public static void spawnPoisonFeastBurst(final IWorld world, final double x, final double y, final double z, final double radius) {
        final int count = 14;
        for (int i = 0; i < count; i++) {
            final double a = (i / (double) count) * Math.PI * 2;
            final double px = x + Math.cos(a) * radius;
            final double pz = z + Math.sin(a) * radius;
            safeSpawn(world, "minecraft:soul_fire_flame", px, y + 0.25, pz, 0, 0.1, 0, 0.02, 1);
            safeSpawn(world, "minecraft:soul",
                    x + Math.cos(a) * (radius * 0.7),
                    y + 0.2,
                    z + Math.sin(a) * (radius * 0.7),
                    0, 0.06, 0, 0.015, 1);
        }
        spawnWfmFogRing(world, x, y + 0.3, z, radius * 0.85, 10);
        safeSpawn(world, "minecraft:soul", x, y + 0.5, z, 0.2, 0.12, 0.2, 0.03, 8);
    }

    public static void spawnSoulCharge(final IWorld world, final double x, final double y, final double z) {
        final Random r = AbilityCombatHelper.random();
        for (int i = 0; i < 5; i++) {
            final double ox = (r.nextDouble() - 0.5) * 1.2;
            final double oy = 0.25 + r.nextDouble() * 0.7;
            final double oz = (r.nextDouble() - 0.5) * 1.2;
            safeSpawn(world, "minecraft:soul_fire_flame", x + ox, y + oy, z + oz, 0, 0.05, 0, 0.01, 1);
            safeSpawn(world, "minecraft:soul", x + ox * 0.8, y + oy * 0.6, z + oz * 0.8, 0, 0.04, 0, 0.01, 1);
        }
        spawnWfmFog(world, x, y + 0.4, z, 0.7, 2);
    }

    public static void spawnSoulBurst(final IWorld world, final double x, final double y, final double z, final double radius) {
        final int count = 12;
        for (int i = 0; i < count; i++) {
            final double a = (i / (double) count) * Math.PI * 2;
            final double px = x + Math.cos(a) * radius;
            final double pz = z + Math.sin(a) * radius;
            safeSpawn(world, "minecraft:soul_fire_flame", px, y + 0.35, pz, 0, 0.1, 0, 0.02, 1);
            safeSpawn(world, "minecraft:soul", px, y + 0.45, pz, 0, 0.06, 0, 0.015, 1);
        }
        safeSpawn(world, "minecraft:soul", x, y + 0.8, z, 0.15, 0.08, 0.15, 0.025, 8);
        spawnWfmFogRing(world, x, y + 0.25, z, Math.max(1.2, radius * 0.9), 8);
    }

    public static void spawnShadowTrail(final IWorld world, final double x, final double y, final double z) {
        final Random r = AbilityCombatHelper.random();
        for (int i = 0; i < 3; i++) {
            safeSpawn(world, "minecraft:soul_fire_flame",
                    x + (r.nextDouble() - 0.5) * 0.7,
                    y + r.nextDouble() * 1.4,
                    z + (r.nextDouble() - 0.5) * 0.7,
                    0, 0.04, 0, 0, 1);
            safeSpawn(world, "minecraft:soul",
                    x + (r.nextDouble() - 0.5) * 0.6,
                    y + 0.15 + r.nextDouble() * 0.8,
                    z + (r.nextDouble() - 0.5) * 0.6,
                    0, 0.03, 0, 0.01, 1);
        }
        if (r.nextBoolean()) {
            spawnWfmFog(world, x, y + 0.2, z, 0.35, 1);
        }
    }

    /** Aura thralls: туман + песок душ вместо обычного smoke. */
    public static void spawnSoulFogCloud(final IWorld world, final double x, final double y, final double z, final float height) {
        final Random r = AbilityCombatHelper.random();
        for (int i = 0; i < 10; i++) {
            final double ox = (r.nextDouble() - 0.5) * 1.1;
            final double oy = r.nextDouble() * height;
            final double oz = (r.nextDouble() - 0.5) * 1.1;
            safeSpawn(world, "minecraft:soul", x + ox, y + oy, z + oz, 0, 0.05, 0, 0.015, 1);
            if (i % 2 == 0) {
                safeSpawn(world, "minecraft:soul_fire_flame", x + ox, y + oy + 0.15, z + oz, 0, 0.03, 0, 0.01, 1);
            }
        }
        spawnWfmFog(world, x, y + height * 0.35, z, 1.0, 3);
        spawnWfmFogWall(world, x, y + 0.1, z, 0.5, 1);
    }

    public static void spawnSoulThread(
            final IWorld world,
            final double x1,
            final double y1,
            final double z1,
            final double x2,
            final double y2,
            final double z2) {
        final int steps = 8;
        for (int i = 0; i <= steps; i++) {
            final double t = i / (double) steps;
            final double x = x1 + (x2 - x1) * t;
            final double y = y1 + (y2 - y1) * t + 0.4;
            final double z = z1 + (z2 - z1) * t;
            safeSpawn(world, "minecraft:soul_fire_flame", x, y, z, 0, 0.02, 0, 0, 1);
            if (i % 2 == 0) {
                safeSpawn(world, "minecraft:soul", x, y + 0.1, z, 0, 0.02, 0, 0.01, 1);
            }
            if (i == 0 || i == steps || i == steps / 2) {
                spawnWfmFog(world, x, y, z, 0.25, 1);
            }
        }
    }

    private static void spawnWfmFog(
            final IWorld world,
            final double x,
            final double y,
            final double z,
            final double spread,
            final int count) {
        final Random r = AbilityCombatHelper.random();
        for (int i = 0; i < count; i++) {
            final double ox = (r.nextDouble() - 0.5) * spread;
            final double oz = (r.nextDouble() - 0.5) * spread;
            safeSpawn(world, "wfm:fog",
                    x + ox,
                    y + r.nextDouble() * 0.2,
                    z + oz,
                    (r.nextDouble() - 0.5) * 0.008,
                    0.001 + r.nextDouble() * 0.003,
                    (r.nextDouble() - 0.5) * 0.008,
                    0.0,
                    1);
        }
    }

    private static void spawnWfmFogRing(
            final IWorld world,
            final double x,
            final double y,
            final double z,
            final double radius,
            final int count) {
        for (int i = 0; i < count; i++) {
            final double a = (i / (double) count) * Math.PI * 2;
            safeSpawn(world, "wfm:fog",
                    x + Math.cos(a) * radius,
                    y,
                    z + Math.sin(a) * radius,
                    Math.cos(a) * 0.01,
                    0.002,
                    Math.sin(a) * 0.01,
                    0.0,
                    1);
        }
    }

    private static void spawnWfmFogWall(
            final IWorld world,
            final double x,
            final double y,
            final double z,
            final double spread,
            final int count) {
        final Random r = AbilityCombatHelper.random();
        for (int i = 0; i < count; i++) {
            safeSpawn(world, "wfm:fog_wall",
                    x + (r.nextDouble() - 0.5) * spread,
                    y + r.nextDouble() * 0.4,
                    z + (r.nextDouble() - 0.5) * spread,
                    (r.nextDouble() - 0.5) * 0.01,
                    0.002,
                    (r.nextDouble() - 0.5) * 0.01,
                    0.0,
                    1);
        }
    }

    /**
     * Vanilla charged sword sweeps: several arcs slightly forward (count=0 = oriented).
     */
    public static void spawnSwordSweep(
            final IWorld world,
            final double x,
            final double y,
            final double z,
            final float yaw) {
        final double rad = yaw * 0.0174532925;
        final double fx = -Math.sin(rad);
        final double fz = Math.cos(rad);
        final double rx = -fz;
        final double rz = fx;
        // Three arcs along the swing: near / mid / far
        final double[] forward = {0.45, 0.95, 1.45};
        final double[] side = {-0.35, 0.0, 0.35};
        final double[] height = {0.85, 1.0, 1.15};
        for (int i = 0; i < forward.length; i++) {
            final double px = x + fx * forward[i] + rx * side[i];
            final double pz = z + fz * forward[i] + rz * side[i];
            safeSpawn(world, "minecraft:sweep_attack",
                    px, y + height[i], pz,
                    fx, 0.0, fz, 0.0, 0);
        }
    }

    /** Плотный сгусток (полёт). particlesCsv пустой → дефолтный набор crimson. */
    public static void spawnCrimsonBlob(
            final IWorld world,
            final double x,
            final double y,
            final double z) {
        spawnCrimsonBlob(world, x, y, z, null, 12);
    }

    public static void spawnCrimsonBlob(
            final IWorld world,
            final double x,
            final double y,
            final double z,
            final String particlesCsv,
            final int countPerType) {
        final String[] particles = parseParticles(particlesCsv, DEFAULT_BLOB_PARTICLES);
        final int count = Math.max(1, Math.min(40, countPerType));
        final double spread = 0.14;
        for (int i = 0; i < particles.length; i++) {
            safeSpawn(world, particles[i], x, y, z, spread, spread, spread, 0.02, count);
        }
    }

    /** Burst приземления. */
    public static void spawnCrimsonBlobLand(
            final IWorld world,
            final double x,
            final double y,
            final double z,
            final double radius) {
        spawnCrimsonBlobLand(world, x, y, z, radius, null, 18);
    }

    public static void spawnCrimsonBlobLand(
            final IWorld world,
            final double x,
            final double y,
            final double z,
            final double radius,
            final String particlesCsv,
            final int countPerType) {
        final String[] particles = parseParticles(particlesCsv, DEFAULT_LAND_PARTICLES);
        final int count = Math.max(1, Math.min(48, countPerType));
        final double rClamp = Math.max(0.8, radius);
        for (int i = 0; i < particles.length; i++) {
            safeSpawn(world, particles[i], x, y, z,
                    rClamp * 0.45, 0.25, rClamp * 0.45, 0.03, count);
        }
    }

    private static final String[] DEFAULT_BLOB_PARTICLES = {
            "minecraft:flame", "minecraft:smoke", "minecraft:large_smoke",
            "minecraft:ash", "minecraft:crit"
    };

    private static final String[] DEFAULT_LAND_PARTICLES = {
            "minecraft:large_smoke", "minecraft:smoke", "minecraft:flame",
            "minecraft:ash", "minecraft:explosion"
    };

    private static String[] parseParticles(final String csv, final String[] fallback) {
        if (csv == null || csv.trim().isEmpty()) {
            return fallback;
        }
        final String[] raw = csv.split(",");
        final java.util.ArrayList<String> list = new java.util.ArrayList<>();
        for (int i = 0; i < raw.length; i++) {
            String p = raw[i].trim();
            if (p.isEmpty()) {
                continue;
            }
            if (p.indexOf(':') < 0) {
                p = "minecraft:" + p;
            }
            list.add(p);
        }
        if (list.isEmpty()) {
            return fallback;
        }
        return list.toArray(new String[0]);
    }

    private static void safeSpawn(
            final IWorld world,
            final String particle,
            final double x,
            final double y,
            final double z,
            final double dx,
            final double dy,
            final double dz,
            final double speed,
            final int count) {
        try {
            world.spawnParticle(particle, x, y, z, dx, dy, dz, speed, count);
        } catch (final Exception ignored) {
        }
    }
}
