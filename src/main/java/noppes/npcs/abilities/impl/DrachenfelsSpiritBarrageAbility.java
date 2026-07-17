package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;

import java.util.Map;
import java.util.Set;

public final class DrachenfelsSpiritBarrageAbility implements CnpcAbility {
    public static final String ID = "drachenfels_spirit_barrage";

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
        return AbilityDefaults.drachenfelsSpiritBarrage();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.SHOTS,
                AbilityParamKeys.DISTANCE,
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
        active.ex = ctx.target.getX();
        active.ez = ctx.target.getZ();
        active.ey = AbilityCombatHelper.findGroundY(ctx.world, active.ex, active.ez, ctx.target.getY());
        final double dx = active.ex - active.sx;
        final double dz = active.ez - active.sz;
        active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 10);
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.illusioner.prepare_blindness", 0.9F, 0.8F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickBarrage(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        if (ctx.target != null && ctx.target.isAlive()) {
            active.ex = ctx.target.getX();
            active.ez = ctx.target.getZ();
            active.ey = AbilityCombatHelper.findGroundY(ctx.world, active.ex, active.ez, ctx.target.getY());
            final double dx = active.ex - ctx.npc.getX();
            final double dz = active.ez - ctx.npc.getZ();
            active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
        }
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 3 == 0) {
            AbilityVfx.spawnSoulCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 16);
        active.hitUuids.clear();
        return TickResult.CONTINUE;
    }

    private TickResult tickBarrage(final ActiveAbility active, final AbilityContext ctx) {
        if (active.ticksLeft <= 0) {
            return TickResult.FINISHED;
        }

        final int total = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 16);
        final int shots = Math.max(1, ctx.params.getInt(AbilityParamKeys.SHOTS, 4));
        final int elapsed = total - active.ticksLeft;
        final int interval = Math.max(1, total / shots);

        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);

        if (elapsed % interval == 0 && elapsed / interval < shots) {
            firePulse(active, ctx, elapsed / interval, shots);
        }

        active.ticksLeft--;
        return TickResult.CONTINUE;
    }

    private void firePulse(
            final ActiveAbility active,
            final AbilityContext ctx,
            final int shotIndex,
            final int shots) {
        final double maxDist = ctx.params.getDouble(AbilityParamKeys.DISTANCE, 14.0);
        final double progress = (shotIndex + 1) / (double) shots;
        final double dx = active.ex - active.sx;
        final double dz = active.ez - active.sz;
        final double len = Math.sqrt(dx * dx + dz * dz);
        final double useDist = Math.min(maxDist, Math.max(2.0, len));
        final double nx = len > 0.05 ? dx / len : Math.cos((active.yaw + 90.0) * 0.0174532925);
        final double nz = len > 0.05 ? dz / len : Math.sin((active.yaw + 90.0) * 0.0174532925);

        final double cx = active.sx + nx * useDist * progress;
        final double cz = active.sz + nz * useDist * progress;
        final double cy = AbilityCombatHelper.findGroundY(ctx.world, cx, cz, active.sy) + 0.6;

        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 7.0);
        final double hitRadius = ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 1.8);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 0.5);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.15);

        // each pulse may hit the same target — clear marker between pulses
        active.hitUuids.clear();
        AbilityCombatHelper.damageNearby(
                active, ctx, cx, cy, cz,
                hitRadius, damage, nx, nz, knockback, knockbackY, true);
        AbilityVfx.spawnSoulBurst(ctx.world, cx, cy, cz, hitRadius);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(cx, cy, cz),
                "minecraft:entity.vex.hurt",
                0.7F,
                1.2F + shotIndex * 0.08F);
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
