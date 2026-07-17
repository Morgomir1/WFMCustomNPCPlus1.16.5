package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;

import java.util.Map;
import java.util.Set;

/**
 * Long-range soul pulses that punish players fleeing beyond normal skill range.
 * Charge briefly, then fire 1–3 seeking pulses along a line toward the target
 * up to {@code maxRange}/{@code distance} (~40 blocks).
 */
public final class DrachenfelsSoulSeekerAbility implements CnpcAbility {
    public static final String ID = "drachenfels_soul_seeker";

    private static final double SAMPLE_STEP = 4.0;

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
        return AbilityDefaults.drachenfelsSoulSeeker();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.SHOTS,
                AbilityParamKeys.DISTANCE,
                AbilityParamKeys.MAX_RANGE,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.HIT_RADIUS,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = true;
        if (ctx.target == null || !ctx.target.isAlive()) {
            return false;
        }
        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY();
        active.sz = ctx.npc.getZ();
        lockAim(active, ctx);
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 12);
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.wither.ambient", 0.75F, 1.35F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickSeek(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        if (ctx.target != null && ctx.target.isAlive()) {
            lockAim(active, ctx);
        }
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 3 == 0) {
            AbilityVfx.spawnSoulCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
            AbilityVfx.spawnSoulFogCloud(
                    ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), 1.2F);
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 14);
        active.hitUuids.clear();
        AbilityVfx.spawnSoulBurst(ctx.world, ctx.npc.getX(), ctx.npc.getY() + 0.4, ctx.npc.getZ(), 1.4);
        return TickResult.CONTINUE;
    }

    private TickResult tickSeek(final ActiveAbility active, final AbilityContext ctx) {
        if (active.ticksLeft <= 0) {
            return TickResult.FINISHED;
        }

        final int total = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 14);
        final int shots = Math.max(1, Math.min(3, ctx.params.getInt(AbilityParamKeys.SHOTS, 2)));
        final int elapsed = total - active.ticksLeft;
        final int interval = Math.max(1, total / shots);

        AbilityCombatHelper.stopNavigation(ctx.npc);
        if (ctx.target != null && ctx.target.isAlive()) {
            lockAim(active, ctx);
        }
        ctx.npc.setRotation(active.yaw);

        if (elapsed % interval == 0 && elapsed / interval < shots) {
            fireSeekerPulse(active, ctx, elapsed / interval);
        }

        active.ticksLeft--;
        return TickResult.CONTINUE;
    }

    private void fireSeekerPulse(final ActiveAbility active, final AbilityContext ctx, final int shotIndex) {
        final double maxRange = resolveMaxRange(ctx);
        final double sx = ctx.npc.getX();
        final double sy = ctx.npc.getY();
        final double sz = ctx.npc.getZ();

        final double dx = active.ex - sx;
        final double dz = active.ez - sz;
        final double len = Math.sqrt(dx * dx + dz * dz);
        final double nx = len > 0.05 ? dx / len : Math.cos((active.yaw + 90.0) * 0.0174532925);
        final double nz = len > 0.05 ? dz / len : Math.sin((active.yaw + 90.0) * 0.0174532925);
        final double travel = Math.min(maxRange, Math.max(3.0, len > 0.05 ? len : maxRange));

        final double ix = sx + nx * travel;
        final double iz = sz + nz * travel;
        final double iy = AbilityCombatHelper.findGroundY(ctx.world, ix, iz, active.ey) + 0.8;

        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 10.0);
        final double hitRadius = ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 2.2);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 0.65);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.18);

        AbilityVfx.spawnSoulThread(ctx.world, sx, sy + 1.0, sz, ix, iy, iz);

        // One hit per entity per pulse; sample along the beam then final impact.
        active.hitUuids.clear();
        for (double d = SAMPLE_STEP; d < travel - 0.5; d += SAMPLE_STEP) {
            final double cx = sx + nx * d;
            final double cz = sz + nz * d;
            final double cy = AbilityCombatHelper.findGroundY(ctx.world, cx, cz, sy) + 0.7;
            AbilityCombatHelper.damageNearby(
                    active, ctx, cx, cy, cz,
                    hitRadius * 0.85, damage * 0.55, nx, nz, knockback * 0.6, knockbackY, true);
            if ((int) (d / SAMPLE_STEP) % 2 == 0) {
                AbilityVfx.spawnSoulBurst(ctx.world, cx, cy, cz, hitRadius * 0.55);
            }
        }

        AbilityCombatHelper.damageNearby(
                active, ctx, ix, iy, iz,
                hitRadius, damage, nx, nz, knockback, knockbackY, true);
        AbilityVfx.spawnSoulBurst(ctx.world, ix, iy, iz, hitRadius);
        AbilityVfx.spawnSoulFogCloud(ctx.world, ix, iy - 0.3, iz, 1.4F);

        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(ix, iy, iz),
                "minecraft:entity.vex.charge",
                0.85F,
                0.7F + shotIndex * 0.12F);
        ctx.world.playSoundAt(
                ctx.npc.getPos(),
                "minecraft:entity.illusioner.cast_spell",
                0.7F,
                1.1F);
    }

    private static void lockAim(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null) {
            return;
        }
        active.ex = ctx.target.getX();
        active.ez = ctx.target.getZ();
        active.ey = AbilityCombatHelper.findGroundY(ctx.world, active.ex, active.ez, ctx.target.getY());
        final double dx = active.ex - ctx.npc.getX();
        final double dz = active.ez - ctx.npc.getZ();
        active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
    }

    private static double resolveMaxRange(final AbilityContext ctx) {
        final double maxRange = ctx.params.getDouble(AbilityParamKeys.MAX_RANGE, -1.0);
        if (maxRange > 0.0) {
            return maxRange;
        }
        return ctx.params.getDouble(AbilityParamKeys.DISTANCE, 40.0);
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
