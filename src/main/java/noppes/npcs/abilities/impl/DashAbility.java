package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;

import java.util.Map;
import java.util.Set;

public final class DashAbility implements CnpcAbility {
    public static final String ID = "dash";

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
        return AbilityDefaults.dash();
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
        active.jumpStyle = false;
        if (!AbilityCombatHelper.computeEndPoints(active, ctx, true)) {
            return false;
        }
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 10);
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:block.beacon.power_select", 0.9F, 1.1F);
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
                    false);
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 7);
        active.hitUuids.clear();
        AbilityVfx.spawnStartBurst(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), false);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.ravager.roar", 0.7F, 1.3F);
        return TickResult.CONTINUE;
    }

    private TickResult tickActive(final ActiveAbility active, final AbilityContext ctx) {
        final int total = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 7);
        if (active.ticksLeft <= 0) {
            finishDash(active, ctx);
            return TickResult.FINISHED;
        }

        final double progress = 1.0 - (active.ticksLeft - 1) / (double) total;
        final double cx = active.sx + (active.ex - active.sx) * progress;
        final double cy = active.sy + (active.ey - active.sy) * progress;
        final double cz = active.sz + (active.ez - active.sz) * progress;

        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setPosition(cx, cy, cz);
        ctx.npc.setRotation(active.yaw);

        final double rad = (active.yaw + 90.0) * 0.0174532925;
        final double knockDirX = Math.cos(rad);
        final double knockDirZ = Math.sin(rad);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 10.0);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 1.8);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.35);
        final double hitRadius = ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 1.6);

        AbilityCombatHelper.damageNearby(
                active, ctx, cx, cy + 1.0, cz,
                hitRadius, damage, knockDirX, knockDirZ, knockback, knockbackY, true);
        AbilityVfx.spawnDashTrail(ctx.world, cx, cy, cz);
        active.ticksLeft--;
        return TickResult.CONTINUE;
    }

    private void finishDash(final ActiveAbility active, final AbilityContext ctx) {
        ctx.npc.setPosition(active.ex, active.ey, active.ez);
        AbilityVfx.spawnLandBurst(ctx.world, active.ex, active.ey, active.ez, false);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(active.ex, active.ey, active.ez),
                "minecraft:entity.generic.explode",
                0.8F,
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
