package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.Map;
import java.util.Set;

/** Phase 1: charge line telegraph, then a soul projectile flies along the gaze. */
public final class DfMaskGazeAbility implements CnpcAbility {
    public static final String ID = "df_mask_gaze";
    private static final int RED = 0xC0FF3030;

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
        return AbilityDefaults.dfMaskGaze();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.DISTANCE,
                AbilityParamKeys.HIT_RADIUS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = false;
        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY();
        active.sz = ctx.npc.getZ();
        faceTarget(active, ctx);
        active.hitUuids.clear();
        active.telegraphIds.clear();
        final int charge = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 20));
        final int activeTicks = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 15));
        final double distance = ctx.params.getDouble(AbilityParamKeys.DISTANCE, 16.0);
        final double width = ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 1.5);
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, RED);
        // Warning from face-lock through the whole cast (charge + flight).
        final int telegraphTicks = charge + activeTicks;
        final String lineId = TelegraphAPI.line(
                ctx.npc, active.sx, active.sy, active.sz, active.yaw, distance, width, telegraphTicks, color);
        if (lineId != null && !lineId.isEmpty()) {
            active.telegraphIds.add(lineId);
            active.telegraphId = lineId;
        }
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = charge;
        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.elder_guardian.curse", 0.85F, 0.9F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.holdInPlace(ctx.npc, active.sx, active.sy, active.sz);
        ctx.npc.setRotation(active.yaw);
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            if (active.ticksLeft % 4 == 0) {
                AbilityVfx.spawnDarkCharge(ctx.world, active.sx, active.sy + 1.2, active.sz);
                AbilityVfx.spawnSoulCharge(ctx.world, active.sx, active.sy + 1.4, active.sz);
            }
            active.ticksLeft--;
            if (active.ticksLeft > 0) {
                return TickResult.CONTINUE;
            }
            lockEndPoint(active, ctx);
            active.phase = ActiveAbility.PHASE_ACTIVE;
            active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 15));
            active.hitUuids.clear();
            // Store total flight ticks in meter for progress interpolation.
            active.meter = active.ticksLeft;
            ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.guardian.attack", 1.0F, 0.7F);
            spawnProjectileAt(active, ctx, 0.0);
            return TickResult.CONTINUE;
        }
        return tickProjectile(active, ctx);
    }

    private TickResult tickProjectile(final ActiveAbility active, final AbilityContext ctx) {
        final int total = Math.max(1, (int) active.meter);
        final double t = 1.0 - (active.ticksLeft - 1) / (double) total;
        spawnProjectileAt(active, ctx, t);
        tryHitProjectile(active, ctx, t);
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        // Final impact at end of gaze line.
        spawnProjectileAt(active, ctx, 1.0);
        tryHitProjectile(active, ctx, 1.0);
        AbilityVfx.spawnSoulBurst(ctx.world, active.ex, active.ey + 0.4, active.ez, 1.4);
        AbilityVfx.spawnSoulThread(
                ctx.world, active.sx, active.sy + 1.4, active.sz, active.ex, active.ey + 1.0, active.ez);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(active.ex, active.ey, active.ez),
                "minecraft:entity.wither.shoot",
                0.7F,
                1.25F);
        return TickResult.FINISHED;
    }

    private static void spawnProjectileAt(
            final ActiveAbility active, final AbilityContext ctx, final double t) {
        final double x = active.sx + (active.ex - active.sx) * t;
        final double z = active.sz + (active.ez - active.sz) * t;
        final double y = active.sy + 1.35 + (active.ey - active.sy) * t * 0.15;
        AbilityVfx.spawnSoulCharge(ctx.world, x, y, z);
        AbilityVfx.spawnShadowTrail(ctx.world, x, y - 0.2, z);
        AbilityVfx.spawnCrimsonBlob(ctx.world, x, y, z, "", 8);
    }

    private static void tryHitProjectile(
            final ActiveAbility active, final AbilityContext ctx, final double t) {
        final double x = active.sx + (active.ex - active.sx) * t;
        final double z = active.sz + (active.ez - active.sz) * t;
        final double y = active.sy + 1.0 + (active.ey - active.sy) * t * 0.15;
        final double hitR = Math.max(0.8, ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 1.5) * 0.65);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 18.0);
        AbilityCombatHelper.damageNearbyPure(
                active, ctx, x, y, z, hitR, damage, 0, 0, 0, 0, false);
    }

    private static void faceTarget(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null) {
            active.yaw = ctx.npc.getRotation();
            return;
        }
        final double dx = ctx.target.getX() - active.sx;
        final double dz = ctx.target.getZ() - active.sz;
        active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
    }

    private static void lockEndPoint(final ActiveAbility active, final AbilityContext ctx) {
        final double distance = ctx.params.getDouble(AbilityParamKeys.DISTANCE, 16.0);
        final double rad = (active.yaw + 90.0) * 0.0174532925;
        active.ex = active.sx + Math.cos(rad) * distance;
        active.ez = active.sz + Math.sin(rad) * distance;
        active.ey = AbilityCombatHelper.findGroundY(ctx.world, active.ex, active.ez, active.sy);
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        DrachenfelsEncounterHelper.onAbilityEnded(ctx.npc, ID);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityTelegraph.clear(active, ctx);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        DrachenfelsEncounterHelper.onAbilityEnded(ctx.npc, ID);
    }
}
