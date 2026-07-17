package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;

import java.util.Map;
import java.util.Set;

public final class DrachenfelsShadowStepAbility implements CnpcAbility {
    public static final String ID = "drachenfels_shadow_step";

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
        return AbilityDefaults.drachenfelsShadowStep();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.DISTANCE,
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y,
                AbilityParamKeys.HIT_RADIUS);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = true;
        if (!AbilityCombatHelper.computeDashEndPoints(active, ctx)) {
            return false;
        }
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 6);
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.enderman.teleport", 0.85F, 0.8F);
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
        if (active.ticksLeft % 2 == 0) {
            AbilityVfx.spawnShadowTrail(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 5);
        active.hitUuids.clear();
        AbilityVfx.spawnSoulBurst(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), 1.2);
        return TickResult.CONTINUE;
    }

    private TickResult tickActive(final ActiveAbility active, final AbilityContext ctx) {
        final int total = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 5);
        if (active.ticksLeft <= 0) {
            finishStep(active, ctx);
            return TickResult.FINISHED;
        }

        final double progress = 1.0 - (active.ticksLeft - 1) / (double) total;
        final double[] point = AbilityCombatHelper.resolveDashPointAtProgress(
                ctx, active.sx, active.sy, active.sz, active.ex, active.ez, progress);
        final double cx = point[0];
        final double cy = point[1];
        final double cz = point[2];

        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setPosition(cx, cy, cz);
        ctx.npc.setRotation(active.yaw);

        final double rad = (active.yaw + 90.0) * 0.0174532925;
        final double knockDirX = Math.cos(rad);
        final double knockDirZ = Math.sin(rad);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 8.0);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 1.1);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.25);
        final double hitRadius = ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 1.5);

        AbilityCombatHelper.damageNearby(
                active, ctx, cx, cy + 1.0, cz,
                hitRadius, damage, knockDirX, knockDirZ, knockback, knockbackY, true);
        AbilityVfx.spawnShadowTrail(ctx.world, cx, cy, cz);
        active.ticksLeft--;
        return TickResult.CONTINUE;
    }

    private void finishStep(final ActiveAbility active, final AbilityContext ctx) {
        final double[] point = AbilityCombatHelper.resolveDashPointAtProgress(
                ctx, active.sx, active.sy, active.sz, active.ex, active.ez, 1.0);
        ctx.npc.setPosition(point[0], point[1], point[2]);
        AbilityVfx.spawnSoulBurst(ctx.world, point[0], point[1], point[2], 1.5);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(point[0], point[1], point[2]),
                "minecraft:entity.enderman.teleport",
                0.9F,
                1.1F);
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
