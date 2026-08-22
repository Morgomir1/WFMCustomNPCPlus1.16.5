package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.Map;
import java.util.Set;

/**
 * Soul dark blast: charge locks a circle under the target, telegraph warns,
 * then deals pure MAGIC damage and breaks only wooden planks (snapshot for restore).
 */
public final class DrachenfelsDarkBlastAbility implements CnpcAbility {
    public static final String ID = "drachenfels_dark_blast";

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
        return AbilityDefaults.drachenfelsDarkBlast();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return false;
        }

        final double tx = ctx.target.getX();
        final double tz = ctx.target.getZ();
        // Feet ground under target — avoid resolveGroundY upward scan latching onto ceiling.
        final double ty = AbilityCombatHelper.findFeetGroundY(
                ctx.world, tx, tz, ctx.target.getY());

        active.jumpStyle = false;
        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY();
        active.sz = ctx.npc.getZ();
        active.ex = tx;
        active.ey = ty;
        active.ez = tz;
        active.hitUuids.clear();
        active.telegraphIds.clear();
        active.telegraphId = null;

        final double dx = tx - ctx.npc.getX();
        final double dz = tz - ctx.npc.getZ();
        active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
        ctx.npc.setRotation(active.yaw);

        final int chargeTicks = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 32));
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = chargeTicks;
        AbilityCombatHelper.stopNavigation(ctx.npc);

        // Явный circle под целью (circleAt — без повторного upward ground scan).
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 3.5);
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, TelegraphAPI.DEFAULT_COLOR);
        final String tid = TelegraphAPI.circleAt(ctx.npc, tx, ty + 0.05, tz, radius, chargeTicks, color);
        if (tid != null && !tid.isEmpty()) {
            active.telegraphId = tid;
            active.telegraphIds.add(tid);
        }

        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.evoker.prepare_attack", 0.9F, 0.55F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return doBlast(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 4 == 0) {
            AbilityVfx.spawnDarkCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
            AbilityVfx.spawnSoulFogCloud(ctx.world, active.ex, active.ey + 0.2, active.ez, 0.85F);
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = 1;
        active.hitUuids.clear();
        return TickResult.CONTINUE;
    }

    private TickResult doBlast(final ActiveAbility active, final AbilityContext ctx) {
        final double x = active.ex;
        final double y = active.ey;
        final double z = active.ez;
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 3.5);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 15.0);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 0.55);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.2);

        AbilityVfx.spawnSoulBurst(ctx.world, x, y + 0.15, z, radius);
        AbilityVfx.spawnDarkCharge(ctx.world, x, y + 0.4, z);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(x, y, z),
                "minecraft:entity.wither.shoot",
                0.85F,
                0.65F);

        active.hitUuids.clear();
        AbilityCombatHelper.damageNearbyPure(
                active, ctx, x, y + 0.5, z,
                radius, damage, 0, 0, knockback, knockbackY, false);

        AbilityCombatHelper.breakWoodenPlanksInRadius(ctx.npc, ctx.world, x, y, z, radius);

        return TickResult.FINISHED;
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        AbilityTelegraph.clear(active, ctx);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityTelegraph.clear(active, ctx);
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
