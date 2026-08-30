package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.Map;
import java.util.Set;

/** Phase 2: telegraph spawn points, then four leper phantoms. */
public final class DfLeperBallAbility implements CnpcAbility {
    public static final String ID = "df_leper_ball";

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
        return AbilityDefaults.dfLeperBall();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.CLONE_TAB,
                AbilityParamKeys.CLONE_NAME,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = false;
        active.markers.clear();
        active.telegraphIds.clear();
        final double[][] points = DrachenfelsEncounterHelper.leperSpawnPoints(ctx.npc);
        final int charge = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 24));
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 1.0);
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, 0xC0FF3030);
        for (int i = 0; i < points.length; i++) {
            final double[] p = points[i];
            active.markers.add(p);
            final String id = TelegraphAPI.circle(ctx.npc, p[0], p[1], p[2], radius, charge, color);
            if (id != null && !id.isEmpty()) {
                active.telegraphIds.add(id);
            }
        }
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = charge;
        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.zombie.infect", 0.85F, 0.7F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.holdInPlace(ctx.npc, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            active.ticksLeft--;
            if (active.ticksLeft > 0) {
                return TickResult.CONTINUE;
            }
            AbilityTelegraph.clear(active, ctx);
            final int tab = ctx.params.getInt(AbilityParamKeys.CLONE_TAB, 1);
            final String name = ctx.params.getString(AbilityParamKeys.CLONE_NAME, "Drachenfels Leper Phantom");
            final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 10.0);
            DrachenfelsEncounterHelper.spawnLeperPhantoms(ctx.npc, tab, name, damage, active.markers);
            active.phase = ActiveAbility.PHASE_ACTIVE;
            active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 60));
            return TickResult.CONTINUE;
        }
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
