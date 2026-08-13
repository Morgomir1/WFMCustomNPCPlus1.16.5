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

    /** Hearts + blood around the caster when a blood puddle is picked up. */
    public static void spawnBloodHeal(
            final IWorld world,
            final double x,
            final double y,
            final double z) {
        final Random r = AbilityCombatHelper.random();
        for (int i = 0; i < 8; i++) {
            safeSpawn(world, "minecraft:heart",
                    x + (r.nextDouble() - 0.5) * 1.1,
                    y + r.nextDouble() * 0.8,
                    z + (r.nextDouble() - 0.5) * 1.1,
                    0, 0.08, 0, 0, 1);
        }
        for (int i = 0; i < 10; i++) {
            safeSpawn(world, "minecraft:entity_effect",
                    x + (r.nextDouble() - 0.5) * 1.4,
                    y - 0.2 + r.nextDouble() * 1.2,
                    z + (r.nextDouble() - 0.5) * 1.4,
                    0.9, 0.1, 0.1, 0, 1);
        }
        safeSpawn(world, "minecraft:damage_indicator", x, y + 0.4, z, 0, 0.2, 0, 0, 4);
        spawnBloodBurst(world, x, y - 0.4, z, 0.9);
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

    /** Тёмный дым + души по кольцу (annulus), не в центре. */
    public static void spawnDarkSoulRing(
            final IWorld world,
            final double x,
            final double y,
            final double z,
            final double innerRadius,
            final double outerRadius) {
        final Random r = AbilityCombatHelper.random();
        final double inner = Math.max(0.2, innerRadius);
        final double outer = Math.max(inner + 0.4, outerRadius);
        for (int i = 0; i < 6; i++) {
            final double a = r.nextDouble() * Math.PI * 2;
            final double dist = inner + r.nextDouble() * (outer - inner);
            final double px = x + Math.cos(a) * dist;
            final double pz = z + Math.sin(a) * dist;
            final double py = y + 0.15 + r.nextDouble() * 0.55;
            safeSpawn(world, "minecraft:smoke", px, py, pz, 0, 0.04, 0, 0.01, 1);
            safeSpawn(world, "minecraft:soul", px, py + 0.1, pz, 0, 0.05, 0, 0.01, 1);
            if (r.nextDouble() < 0.35) {
                safeSpawn(world, "minecraft:large_smoke", px, py + 0.05, pz, 0, 0.03, 0, 0.01, 1);
            }
        }
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

    /**
     * Огненный взмах мечом по всему прямоугольному коридору удара.
     */
    public static void spawnFlamingStrikeSweep(
            final IWorld world,
            final double sx,
            final double sy,
            final double sz,
            final double ex,
            final double ey,
            final double ez,
            final double halfWidth) {
        final double dx = ex - sx;
        final double dz = ez - sz;
        final double len = Math.sqrt(dx * dx + dz * dz);
        final double fx;
        final double fz;
        if (len < 0.05) {
            fx = 0.0;
            fz = 1.0;
        } else {
            fx = dx / len;
            fz = dz / len;
        }
        final double rx = -fz;
        final double rz = fx;
        final double useLen = Math.max(1.0, len);
        final double width = Math.max(0.6, halfWidth);
        final int steps = Math.max(4, (int) Math.ceil(useLen / 0.55));
        final Random r = AbilityCombatHelper.random();

        for (int i = 0; i <= steps; i++) {
            final double t = i / (double) steps;
            final double cx = sx + dx * t;
            final double cz = sz + dz * t;
            final double cy = sy + (ey - sy) * t + 0.95;

            // Дуга взмаха (sweep) — по центру коридора
            safeSpawn(world, "minecraft:sweep_attack",
                    cx, cy, cz,
                    fx, 0.0, fz, 0.0, 0);

            // Огонь по ширине зоны
            final int sideSamples = Math.max(2, (int) Math.ceil(width * 2.0));
            for (int s = -sideSamples; s <= sideSamples; s++) {
                final double side = (s / (double) sideSamples) * width;
                final double px = cx + rx * side + (r.nextDouble() - 0.5) * 0.15;
                final double pz = cz + rz * side + (r.nextDouble() - 0.5) * 0.15;
                final double py = cy - 0.15 + r.nextDouble() * 0.35;
                safeSpawn(world, "minecraft:flame", px, py, pz, 0, 0.04, 0, 0.015, 1);
                if (s == 0 || (i + s) % 2 == 0) {
                    safeSpawn(world, "minecraft:crit", px, py + 0.1, pz, 0, 0.05, 0, 0.01, 1);
                }
            }
        }

        // Доп. дуги ближе к боссу / к концу удара
        safeSpawn(world, "minecraft:sweep_attack",
                sx + fx * 0.6, sy + 1.05, sz + fz * 0.6,
                fx, 0.0, fz, 0.0, 0);
        safeSpawn(world, "minecraft:sweep_attack",
                ex, ey + 1.1, ez,
                fx, 0.0, fz, 0.0, 0);
    }

    /** Кровавый взмах мечом по коридору удара (как flaming strike, без огня). */
    public static void spawnBloodSlashSweep(
            final IWorld world,
            final double sx,
            final double sy,
            final double sz,
            final double ex,
            final double ey,
            final double ez,
            final double halfWidth) {
        final double dx = ex - sx;
        final double dz = ez - sz;
        final double len = Math.sqrt(dx * dx + dz * dz);
        final double fx;
        final double fz;
        if (len < 0.05) {
            fx = 0.0;
            fz = 1.0;
        } else {
            fx = dx / len;
            fz = dz / len;
        }
        final double rx = -fz;
        final double rz = fx;
        final double useLen = Math.max(1.0, len);
        final double width = Math.max(0.6, halfWidth);
        final int steps = Math.max(4, (int) Math.ceil(useLen / 0.55));
        final Random r = AbilityCombatHelper.random();

        for (int i = 0; i <= steps; i++) {
            final double t = i / (double) steps;
            final double cx = sx + dx * t;
            final double cz = sz + dz * t;
            final double cy = sy + (ey - sy) * t + 0.95;

            safeSpawn(world, "minecraft:sweep_attack",
                    cx, cy, cz,
                    fx, 0.0, fz, 0.0, 0);

            final int sideSamples = Math.max(2, (int) Math.ceil(width * 2.0));
            for (int s = -sideSamples; s <= sideSamples; s++) {
                final double side = (s / (double) sideSamples) * width;
                final double px = cx + rx * side + (r.nextDouble() - 0.5) * 0.15;
                final double pz = cz + rz * side + (r.nextDouble() - 0.5) * 0.15;
                final double py = cy - 0.15 + r.nextDouble() * 0.35;
                safeSpawn(world, "minecraft:entity_effect", px, py, pz, 0.9, 0.1, 0.1, 0, 1);
                if (s == 0 || (i + s) % 2 == 0) {
                    safeSpawn(world, "minecraft:crit", px, py + 0.1, pz, 0, 0.05, 0, 0.01, 1);
                    safeSpawn(world, "minecraft:crimson_spore", px, py, pz, 0, 0.03, 0, 0.01, 1);
                }
            }
        }

        safeSpawn(world, "minecraft:sweep_attack",
                sx + fx * 0.6, sy + 1.05, sz + fz * 0.6,
                fx, 0.0, fz, 0.0, 0);
        safeSpawn(world, "minecraft:sweep_attack",
                ex, ey + 1.1, ez,
                fx, 0.0, fz, 0.0, 0);
    }

    private static final String[] DEFAULT_OTRODIE_VOMIT_PARTICLES = {
            "wfm:nurgle_miasma", "minecraft:smoke", "minecraft:large_smoke",
            "minecraft:ash", "minecraft:witch"
    };

    /**
     * Continuous vomit stream along a lobbed arc (same math as {@code crimson_blob}):
     * lerp XZ/Y + {@code arcHeight * 4 * t * (1 - t)}. Dense clouds along the path +
     * mouth burst with tangent velocity at t≈0.
     */
    public static void spawnOtrodieVomitStream(
            final IWorld world,
            final double sx,
            final double sy,
            final double sz,
            final double ex,
            final double ey,
            final double ez,
            final double arcHeight,
            final String particlesCsv,
            final int countPerType) {
        if (world == null) {
            return;
        }
        final String[] particles = parseParticles(particlesCsv, DEFAULT_OTRODIE_VOMIT_PARTICLES);
        final int count = Math.max(1, Math.min(24, countPerType));
        final Random r = AbilityCombatHelper.random();
        final double dx = ex - sx;
        final double dy = ey - sy;
        final double dz = ez - sz;
        final double flat = Math.sqrt(dx * dx + dz * dz);
        final double arc = Math.max(0.5, arcHeight);
        final int samples = Math.max(4, (int) Math.ceil(Math.max(flat, 2.0) / 1.2));

        // Mouth burst: tangent at t=0 → upward lob toward impact.
        double tvx = dx;
        double tvy = dy + 4.0 * arc;
        double tvz = dz;
        final double tlen = Math.sqrt(tvx * tvx + tvy * tvy + tvz * tvz);
        if (tlen > 0.05) {
            tvx /= tlen;
            tvy /= tlen;
            tvz /= tlen;
        } else {
            tvx = 0.0;
            tvy = 1.0;
            tvz = 0.0;
        }
        for (int i = 0; i < particles.length; i++) {
            for (int n = 0; n < Math.max(3, count / 2); n++) {
                final double jx = (r.nextDouble() - 0.5) * 0.22;
                final double jy = (r.nextDouble() - 0.5) * 0.18;
                final double jz = (r.nextDouble() - 0.5) * 0.22;
                final double yawOff = (r.nextDouble() - 0.5) * 0.28;
                final double cosY = Math.cos(yawOff);
                final double sinY = Math.sin(yawOff);
                final double vx = tvx * cosY - tvz * sinY;
                final double vz = tvz * cosY + tvx * sinY;
                final double vy = tvy + (r.nextDouble() - 0.35) * 0.2;
                final double speed = 0.55 + r.nextDouble() * 0.55;
                safeSpawn(world, particles[i], sx + jx, sy + jy, sz + jz, vx, vy, vz, speed, 0);
            }
        }

        // Dense cloud samples along the parabolic arc (crimson_blob flight path).
        for (int s = 0; s <= samples; s++) {
            final double t = s / (double) samples;
            final double px = sx + dx * t + (r.nextDouble() - 0.5) * 0.28;
            final double pz = sz + dz * t + (r.nextDouble() - 0.5) * 0.28;
            final double baseY = sy + dy * t;
            final double py = baseY + arc * 4.0 * t * (1.0 - t) + (r.nextDouble() - 0.5) * 0.2;
            spawnOtrodieVomitCloud(world, px, py, pz, particlesCsv, Math.max(4, count / 2));
        }
    }

    /** Dense nurgle/miasma cloud (blob intensity). */
    public static void spawnOtrodieVomitCloud(
            final IWorld world,
            final double x,
            final double y,
            final double z,
            final String particlesCsv,
            final int countPerType) {
        final String[] particles = parseParticles(particlesCsv, DEFAULT_OTRODIE_VOMIT_PARTICLES);
        final int count = Math.max(1, Math.min(40, countPerType));
        final double spread = 0.16;
        for (int i = 0; i < particles.length; i++) {
            safeSpawn(world, particles[i], x, y, z, spread, spread, spread, 0.02, count);
        }
    }

    /** Burst по усечённому конусу (fecal wave): nurgle/ash облака вдоль сектора. */
    public static void spawnOtrodieFecalBurst(
            final IWorld world,
            final double apexX,
            final double apexY,
            final double apexZ,
            final float yaw,
            final double halfAngleDeg,
            final double minDist,
            final double maxDist,
            final String particlesCsv,
            final int countPerType) {
        if (world == null) {
            return;
        }
        final String[] particles = parseParticles(particlesCsv, DEFAULT_OTRODIE_VOMIT_PARTICLES);
        final int count = Math.max(1, Math.min(24, countPerType));
        final Random r = AbilityCombatHelper.random();
        final double rad = (yaw + 90.0) * 0.0174532925;
        final double fwdX = Math.cos(rad);
        final double fwdZ = Math.sin(rad);
        final double halfRad = Math.toRadians(Math.max(5.0, halfAngleDeg));
        final int rings = Math.max(3, (int) Math.ceil((maxDist - minDist) / 1.6));
        final int rays = Math.max(5, (int) Math.ceil(halfAngleDeg / 8.0) * 2 + 1);

        for (int ring = 0; ring <= rings; ring++) {
            final double t = ring / (double) rings;
            final double dist = minDist + (maxDist - minDist) * t;
            for (int ray = 0; ray < rays; ray++) {
                final double a = -halfRad + (2.0 * halfRad) * (ray / (double) Math.max(1, rays - 1));
                final double cosA = Math.cos(a);
                final double sinA = Math.sin(a);
                final double dx = fwdX * cosA - fwdZ * sinA;
                final double dz = fwdZ * cosA + fwdX * sinA;
                final double px = apexX + dx * dist + (r.nextDouble() - 0.5) * 0.35;
                final double py = apexY + 0.35 + (r.nextDouble() - 0.5) * 0.4;
                final double pz = apexZ + dz * dist + (r.nextDouble() - 0.5) * 0.35;
                for (int i = 0; i < particles.length; i++) {
                    safeSpawn(world, particles[i], px, py, pz, 0.22, 0.18, 0.22, 0.03,
                            Math.max(2, count / 3));
                }
            }
        }
        // Плотное облако у босса / у дальнего края
        spawnOtrodieVomitCloud(world, apexX + fwdX * minDist, apexY + 0.5, apexZ + fwdZ * minDist,
                particlesCsv, count);
        spawnOtrodieVomitCloud(world, apexX + fwdX * maxDist, apexY + 0.45, apexZ + fwdZ * maxDist,
                particlesCsv, count);
    }

    /** Splash лужи «Разлетающийся кал»: nurgle/smoke/ash кольцо + облако в центре. */
    public static void spawnOtrodiePuddleSplash(
            final IWorld world,
            final double x,
            final double y,
            final double z,
            final double radius,
            final String particlesCsv,
            final int countPerType) {
        if (world == null) {
            return;
        }
        final String[] particles = parseParticles(particlesCsv, DEFAULT_OTRODIE_VOMIT_PARTICLES);
        final int count = Math.max(1, Math.min(40, countPerType));
        final double r = Math.max(0.8, radius);
        final Random rnd = AbilityCombatHelper.random();

        for (int i = 0; i < particles.length; i++) {
            safeSpawn(world, particles[i], x, y, z,
                    r * 0.4, 0.22, r * 0.4, 0.03, count);
        }
        spawnOtrodieVomitCloud(world, x, y + 0.15, z, particlesCsv, Math.max(6, count));

        final int ringPoints = Math.max(6, (int) Math.ceil(r * 3.5));
        for (int p = 0; p < ringPoints; p++) {
            final double a = (Math.PI * 2.0 * p) / ringPoints + rnd.nextDouble() * 0.2;
            final double dist = r * (0.45 + rnd.nextDouble() * 0.55);
            final double px = x + Math.cos(a) * dist;
            final double pz = z + Math.sin(a) * dist;
            final double py = y + rnd.nextDouble() * 0.35;
            for (int i = 0; i < Math.min(3, particles.length); i++) {
                safeSpawn(world, particles[i], px, py, pz, 0.12, 0.18, 0.12, 0.02,
                        Math.max(2, count / 4));
            }
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
