package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.Map;
import java.util.Set;

/** Phase 2: telegraph copies + teleport landing, then false host cast. */
public final class DfFalseHostAbility implements CnpcAbility {
    public static final String ID = "df_false_host";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean requiresTarget() {
        return false;
    }

    @Override
    public boolean cancelsOnTargetLost() {
        return false;
    }

    @Override
    public Map<String, Object> defaultParams() {
        return AbilityDefaults.dfFalseHost();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
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
        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY();
        active.sz = ctx.npc.getZ();
        DrachenfelsEncounterHelper.planFalseHost(ctx.npc, active.markers);
        final int charge = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 20));
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 1.2);
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, 0xC0FF3030);
        for (final double[] m : active.markers) {
            final String id = TelegraphAPI.circle(ctx.npc, m[0], m[1], m[2], radius, charge, color);
            if (id != null && !id.isEmpty()) {
                active.telegraphIds.add(id);
            }
        }
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = charge;
        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.illusioner.prepare_mirror", 1.0F, 0.9F);
        return !active.markers.isEmpty();
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.holdInPlace(ctx.npc, active.sx, active.sy, active.sz);
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            active.ticksLeft--;
            if (active.ticksLeft > 0) {
                return TickResult.CONTINUE;
            }
            AbilityTelegraph.clear(active, ctx);
            final int tab = ctx.params.getInt(AbilityParamKeys.CLONE_TAB, 1);
            final String name = ctx.params.getString(AbilityParamKeys.CLONE_NAME, "Drachenfels False Host");
            final int spawned = DrachenfelsEncounterHelper.executeFalseHost(ctx.npc, tab, name, active.markers);
            if (spawned <= 0) {
                return TickResult.FINISHED;
            }
            active.sx = ctx.npc.getX();
            active.sy = ctx.npc.getY();
            active.sz = ctx.npc.getZ();
            active.phase = ActiveAbility.PHASE_ACTIVE;
            ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.illusioner.mirror_move", 1.0F, 0.9F);
            return TickResult.CONTINUE;
        }
        return DrachenfelsEncounterHelper.hasLivingFalseHosts(ctx.npc)
                ? TickResult.CONTINUE
                : TickResult.FINISHED;
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        DrachenfelsEncounterHelper.restoreBossAfterFalseHost(ctx.npc);
        DrachenfelsEncounterHelper.despawnFalseHosts(ctx.npc);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        DrachenfelsEncounterHelper.onAbilityEnded(ctx.npc, ID);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityTelegraph.clear(active, ctx);
        DrachenfelsEncounterHelper.restoreBossAfterFalseHost(ctx.npc);
        DrachenfelsEncounterHelper.despawnFalseHosts(ctx.npc);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        DrachenfelsEncounterHelper.onAbilityEnded(ctx.npc, ID);
    }
}
