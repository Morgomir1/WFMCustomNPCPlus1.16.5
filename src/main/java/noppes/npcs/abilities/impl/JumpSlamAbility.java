package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;

import java.util.Map;
import java.util.Set;

public final class JumpSlamAbility implements CnpcAbility {
    public static final String ID = "jump_slam";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean requiresTarget() {
        return true;
    }

    @Override
    public Map<String, Object> defaultParams() {
        return AbilityDefaults.jumpSlam();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y,
                AbilityParamKeys.LAND_RADIUS,
                AbilityParamKeys.ARC_HEIGHT,
                AbilityParamKeys.MAX_RANGE);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = true;
        if (!AbilityCombatHelper.computeEndPoints(active, ctx, false)) {
            return false;
        }
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 12);
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:block.beacon.power_select", 0.9F, 0.85F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickActive(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 4 == 0) {
            AbilityVfx.spawnChargeParticles(
                    ctx.world,
                    ctx.npc.getX(),
                    ctx.npc.getY(),
                    ctx.npc.getZ(),
                    true);
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 9);
        active.hitUuids.clear();
        AbilityVfx.spawnStartBurst(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), true);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.ravager.roar", 0.7F, 0.9F);
        return TickResult.CONTINUE;
    }

    private TickResult tickActive(final ActiveAbility active, final AbilityContext ctx) {
        final int total = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 9);
        final double arcHeight = ctx.params.getDouble(AbilityParamKeys.ARC_HEIGHT, 6.0);

        if (active.ticksLeft <= 0) {
            final double[] end = AbilityCombatHelper.resolveJumpPointAtProgress(
                    ctx, active.sx, active.sy, active.sz,
                    active.ex, active.ey, active.ez, arcHeight, 1.0);
            settleJumpLanding(active, ctx, end);
            ctx.npc.setPosition(active.ex, active.ey, active.ez);
            ctx.npc.setRotation(active.yaw);
            doLanding(active, ctx);
            return TickResult.FINISHED;
        }

        final double t = 1.0 - (active.ticksLeft - 1) / (double) total;
        final double intendedCx = active.sx + (active.ex - active.sx) * t;
        final double intendedCz = active.sz + (active.ez - active.sz) * t;
        final double baseY = active.sy + (active.ey - active.sy) * t;
        final double intendedCy = baseY + Math.sin(t * Math.PI) * arcHeight;
        final double[] intended = new double[]{intendedCx, intendedCy, intendedCz};
        final double[] point = AbilityCombatHelper.resolveJumpPointAtProgress(
                ctx, active.sx, active.sy, active.sz,
                active.ex, active.ey, active.ez, arcHeight, t);

        AbilityCombatHelper.stopNavigation(ctx.npc);

        if (AbilityCombatHelper.isJumpPointBlocked(point, intended)) {
            settleJumpLanding(active, ctx, point);
            ctx.npc.setPosition(active.ex, active.ey, active.ez);
            ctx.npc.setRotation(active.yaw);
            doLanding(active, ctx);
            return TickResult.FINISHED;
        }

        ctx.npc.setPosition(point[0], point[1], point[2]);
        ctx.npc.setRotation(active.yaw);
        AbilityVfx.spawnJumpTrail(ctx.world, point[0], point[1], point[2]);
        active.ticksLeft--;
        return TickResult.CONTINUE;
    }

    private void settleJumpLanding(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double[] point) {
        active.ex = point[0];
        active.ez = point[2];
        active.ey = AbilityCombatHelper.findGroundY(ctx.world, point[0], point[2], point[1]);
    }

    private void doLanding(final ActiveAbility active, final AbilityContext ctx) {
        final double x = active.ex;
        final double y = active.ey;
        final double z = active.ez;
        AbilityVfx.spawnLandBurst(ctx.world, x, y, z, true);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(x, y, z),
                "minecraft:entity.generic.explode",
                1.0F,
                0.75F);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(x, y, z),
                "minecraft:entity.iron_golem.damage",
                0.9F,
                0.6F);

        active.hitUuids.clear();
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 14.0);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 2.2);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.55);
        final double landRadius = ctx.params.getDouble(AbilityParamKeys.LAND_RADIUS, 2.8);
        AbilityCombatHelper.damageNearby(
                active, ctx, x, y + 0.5, z,
                landRadius, damage, 0, 0, knockback, knockbackY, false);
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
