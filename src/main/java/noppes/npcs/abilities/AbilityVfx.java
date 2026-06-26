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
