package noppes.npcs.abilities.impl;

import net.minecraft.entity.Entity;
import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntityLiving;

import java.util.Map;
import java.util.Set;

/**
 * Ghost kamikaze: approach target through blocks → orbit → slam with knockback → die.
 * Movement uses raw {@code setPosition} (no dash wall-clip).
 */
public final class GhostOrbitSlamAbility implements CnpcAbility {
    public static final String ID = "ghost_orbit_slam";
    public static final int PHASE_SLAM = 3;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean requiresTarget() {
        return true;
    }

    @Override
    public boolean cancelsOnTargetLost() {
        return false;
    }

    @Override
    public Map<String, Object> defaultParams() {
        return AbilityDefaults.ghostOrbitSlam();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.APPROACH_SPEED,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.ORBIT_TICKS,
                AbilityParamKeys.ORBIT_SPEED,
                AbilityParamKeys.HOVER_OFFSET,
                AbilityParamKeys.SLAM_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y,
                AbilityParamKeys.HIT_RADIUS);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = true;
        active.hitUuids.clear();
        active.meter = 0.0F;
        active.elapsedTicks = 0;
        rememberTarget(active, ctx);
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = 0;

        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        setNoPhysics(ctx, true);
        zeroMotion(ctx);
        facePoint(ctx, active.ex, active.ez);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.vex.ambient", 0.9F, 0.7F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        zeroMotion(ctx);
        active.elapsedTicks++;

        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickApproach(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickOrbit(active, ctx);
        }
        if (active.phase == PHASE_SLAM) {
            return tickSlam(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickApproach(final ActiveAbility active, final AbilityContext ctx) {
        rememberTarget(active, ctx);
        final double hover = ctx.params.getDouble(AbilityParamKeys.HOVER_OFFSET, 1.0);
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 2.5);
        final double speed = Math.max(0.05, ctx.params.getDouble(AbilityParamKeys.APPROACH_SPEED, 0.45));

        final double tx = active.ex;
        final double ty = active.ey + hover;
        final double tz = active.ez;

        final double nx = ctx.npc.getX();
        final double ny = ctx.npc.getY();
        final double nz = ctx.npc.getZ();
        final double dx = tx - nx;
        final double dy = ty - ny;
        final double dz = tz - nz;
        final double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        final double flat = AbilityCombatHelper.flatDistance(nx, nz, tx, tz);

        if (flat <= radius + 0.15) {
            beginOrbit(active, ctx, nx, nz, tx, tz);
            return TickResult.CONTINUE;
        }

        if (dist < 0.001) {
            beginOrbit(active, ctx, nx, nz, tx, tz);
            return TickResult.CONTINUE;
        }

        final double step = Math.min(speed, dist);
        final double cx = nx + (dx / dist) * step;
        final double cy = ny + (dy / dist) * step;
        final double cz = nz + (dz / dist) * step;
        ghostMove(ctx, cx, cy, cz);
        facePoint(ctx, tx, tz);
        spawnTrail(ctx, cx, cy, cz);
        return TickResult.CONTINUE;
    }

    private void beginOrbit(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double nx,
            final double nz,
            final double tx,
            final double tz) {
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.ORBIT_TICKS, 60));
        active.hitUuids.clear();
        active.meter = (float) (Math.atan2(nz - tz, nx - tx) * 57.2957795);
        AbilityVfx.spawnStartBurst(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), true);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.vex.charge", 0.85F, 1.1F);
    }

    private TickResult tickOrbit(final ActiveAbility active, final AbilityContext ctx) {
        rememberTarget(active, ctx);
        final double hover = ctx.params.getDouble(AbilityParamKeys.HOVER_OFFSET, 1.0);
        final double radius = Math.max(0.5, ctx.params.getDouble(AbilityParamKeys.RADIUS, 2.5));
        final double orbitSpeed = ctx.params.getDouble(AbilityParamKeys.ORBIT_SPEED, 8.0);

        active.meter += (float) orbitSpeed;
        if (active.meter >= 360.0F) {
            active.meter -= 360.0F;
        }
        if (active.meter < 0.0F) {
            active.meter += 360.0F;
        }

        final double rad = active.meter * 0.0174532925;
        final double cx = active.ex + Math.cos(rad) * radius;
        final double cy = active.ey + hover;
        final double cz = active.ez + Math.sin(rad) * radius;
        ghostMove(ctx, cx, cy, cz);
        facePoint(ctx, active.ex, active.ez);
        spawnTrail(ctx, cx, cy, cz);

        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        beginSlam(active, ctx, cx, cy, cz);
        return TickResult.CONTINUE;
    }

    private void beginSlam(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double cx,
            final double cy,
            final double cz) {
        rememberTarget(active, ctx);
        active.sx = cx;
        active.sy = cy;
        active.sz = cz;
        // Slam into the body (not hover height).
        // ex/ey/ez already hold last known target from rememberTarget.
        active.phase = PHASE_SLAM;
        active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.SLAM_TICKS, 6));
        active.hitUuids.clear();
        active.yaw = AbilityCombatHelper.computeYaw(active.ex - cx, active.ez - cz);
        AbilityVfx.spawnStartBurst(ctx.world, cx, cy, cz, true);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(cx, cy, cz),
                "minecraft:entity.vex.hurt",
                1.0F,
                0.6F);
    }

    private TickResult tickSlam(final ActiveAbility active, final AbilityContext ctx) {
        final int total = Math.max(1, ctx.params.getInt(AbilityParamKeys.SLAM_TICKS, 6));
        if (active.ticksLeft <= 0) {
            finishSlam(active, ctx);
            return TickResult.FINISHED;
        }

        // Keep aiming at a live target during slam if possible.
        if (ctx.target != null && ctx.target.isAlive()) {
            active.ex = ctx.target.getX();
            active.ey = ctx.target.getY();
            active.ez = ctx.target.getZ();
        }

        final double progress = 1.0 - (active.ticksLeft - 1) / (double) total;
        final double cx = lerp(active.sx, active.ex, progress);
        final double cy = lerp(active.sy, active.ey, progress);
        final double cz = lerp(active.sz, active.ez, progress);
        ghostMove(ctx, cx, cy, cz);
        facePoint(ctx, active.ex, active.ez);

        final double dx = active.ex - active.sx;
        final double dz = active.ez - active.sz;
        final double flat = Math.sqrt(dx * dx + dz * dz);
        final double knockDirX = flat < 0.001 ? Math.cos((active.yaw + 90.0) * 0.0174532925) : dx / flat;
        final double knockDirZ = flat < 0.001 ? Math.sin((active.yaw + 90.0) * 0.0174532925) : dz / flat;

        applySlamHit(active, ctx, cx, cy, cz, knockDirX, knockDirZ);
        AbilityVfx.spawnShadowTrail(ctx.world, cx, cy, cz);

        active.ticksLeft--;
        if (active.ticksLeft <= 0) {
            finishSlam(active, ctx);
            return TickResult.FINISHED;
        }
        return TickResult.CONTINUE;
    }

    private void finishSlam(final ActiveAbility active, final AbilityContext ctx) {
        final double cx = active.ex;
        final double cy = active.ey;
        final double cz = active.ez;
        ghostMove(ctx, cx, cy, cz);

        final double dx = active.ex - active.sx;
        final double dz = active.ez - active.sz;
        final double flat = Math.sqrt(dx * dx + dz * dz);
        final double knockDirX = flat < 0.001 ? Math.cos((active.yaw + 90.0) * 0.0174532925) : dx / flat;
        final double knockDirZ = flat < 0.001 ? Math.sin((active.yaw + 90.0) * 0.0174532925) : dz / flat;
        applySlamHit(active, ctx, cx, cy, cz, knockDirX, knockDirZ);

        AbilityVfx.spawnSoulBurst(ctx.world, cx, cy, cz, 2.0);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(cx, cy, cz),
                "minecraft:entity.generic.explode",
                0.7F,
                1.4F);

        cleanupGhost(active, ctx);
        try {
            ctx.npc.kill();
        } catch (final Exception ignored) {
            try {
                ctx.npc.setHealth(0);
            } catch (final Exception ignored2) {
            }
        }
    }

    private void applySlamHit(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double x,
            final double y,
            final double z,
            final double knockDirX,
            final double knockDirZ) {
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 14.0);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 2.4);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.55);
        final double hitRadius = ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 1.8);
        AbilityCombatHelper.damageNearby(
                active, ctx, x, y + 0.5, z,
                hitRadius, damage, knockDirX, knockDirZ, knockback, knockbackY, true);
    }

    private void rememberTarget(final ActiveAbility active, final AbilityContext ctx) {
        final IEntityLiving target = ctx.target;
        if (target != null && target.isAlive()) {
            active.ex = target.getX();
            active.ey = target.getY();
            active.ez = target.getZ();
        }
    }

    private static void ghostMove(final AbilityContext ctx, final double x, final double y, final double z) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        zeroMotion(ctx);
        ctx.npc.setPosition(x, y, z);
    }

    private static void facePoint(final AbilityContext ctx, final double tx, final double tz) {
        final float yaw = AbilityCombatHelper.computeYaw(tx - ctx.npc.getX(), tz - ctx.npc.getZ());
        ctx.npc.setRotation(yaw);
    }

    private static void zeroMotion(final AbilityContext ctx) {
        AbilityCombatHelper.zeroHorizontalMotion(ctx.npc);
        try {
            ctx.npc.setMotionY(0.0);
        } catch (final Exception ignored) {
        }
    }

    private static void spawnTrail(final AbilityContext ctx, final double x, final double y, final double z) {
        AbilityVfx.spawnJumpTrail(ctx.world, x, y + 0.4, z);
        AbilityVfx.spawnShadowTrail(ctx.world, x, y, z);
    }

    private static double lerp(final double a, final double b, final double t) {
        return a + (b - a) * t;
    }

    private static void setNoPhysics(final AbilityContext ctx, final boolean value) {
        try {
            final Entity mc = ctx.npc.getMCEntity();
            if (mc != null) {
                mc.noPhysics = value;
            }
        } catch (final Exception ignored) {
        }
    }

    private void cleanupGhost(final ActiveAbility active, final AbilityContext ctx) {
        setNoPhysics(ctx, false);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        cleanupGhost(active, ctx);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        cleanupGhost(active, ctx);
    }
}
