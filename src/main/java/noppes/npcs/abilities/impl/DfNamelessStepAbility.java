package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.Map;
import java.util.Set;

/** Phase 3: nameless step dash along a fixed line. */
public final class DfNamelessStepAbility implements CnpcAbility {
    public static final String ID = "df_nameless_step";

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
        return AbilityDefaults.dfNamelessStep();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.HIT_RADIUS,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        if (!DrachenfelsEncounterHelper.pickNamelessStepTarget(active, ctx)) {
            return false;
        }
        active.jumpStyle = true;
        active.hitUuids.clear();
        active.telegraphIds.clear();
        final int charge = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 10));
        final double width = ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 1.0);
        final double dx = active.ex - active.sx;
        final double dz = active.ez - active.sz;
        final double dist = Math.sqrt(dx * dx + dz * dz);
        active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, 0xC0FF3030);
        final String lineId = TelegraphAPI.line(
                ctx.npc, active.sx, active.sy, active.sz, active.yaw, dist, width, charge, color);
        if (lineId != null && !lineId.isEmpty()) {
            active.telegraphId = lineId;
            active.telegraphIds.add(lineId);
        }
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = charge;
        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.enderman.teleport", 0.8F, 0.7F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            AbilityCombatHelper.holdInPlace(ctx.npc, active.sx, active.sy, active.sz);
            ctx.npc.setRotation(active.yaw);
            active.ticksLeft--;
            if (active.ticksLeft > 0) {
                return TickResult.CONTINUE;
            }
            AbilityTelegraph.clear(active, ctx);
            active.phase = ActiveAbility.PHASE_ACTIVE;
            active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 8));
            active.hitUuids.clear();
            return TickResult.CONTINUE;
        }
        return tickMove(active, ctx);
    }

    private TickResult tickMove(final ActiveAbility active, final AbilityContext ctx) {
        final int total = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 8));
        final int elapsed = total - active.ticksLeft;
        final double t = Math.min(1.0, (elapsed + 1) / (double) total);
        final double cx = active.sx + (active.ex - active.sx) * t;
        final double cz = active.sz + (active.ez - active.sz) * t;
        final double cy = AbilityCombatHelper.findGroundY(ctx.world, cx, cz, active.sy);
        ctx.npc.setPosition(cx, cy, cz);
        ctx.npc.setRotation(active.yaw);
        final double halfWidth = ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 1.0) * 0.5;
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 12.0);
        AbilityCombatHelper.damageInCorridor(
                active, ctx,
                active.sx, active.sy + 0.5, active.sz,
                active.ex, active.ey + 0.5, active.ez,
                halfWidth, damage, 0, 0, 0, "", 0, 0);
        AbilityVfx.spawnShadowTrail(ctx.world, cx, cy, cz);
        active.ticksLeft--;
        return active.ticksLeft > 0 ? TickResult.CONTINUE : TickResult.FINISHED;
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
