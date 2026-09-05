package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.Map;
import java.util.Set;

/**
 * Phase 2: configurable outward spirit volleys from the boss (4 each, staggered angles),
 * with charge telegraph on the next salvo.
 */
public final class DfLeperBallAbility implements CnpcAbility {
    public static final String ID = "df_leper_ball";
    private static final int DEFAULT_VOLLEYS = 5;
    private static final int DEFAULT_VOLLEY_INTERVAL = 18;

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
                AbilityParamKeys.SHOT_INTERVAL,
                AbilityParamKeys.SUMMON_COUNT,
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
        active.meter = 0.0F; // next volley index to spawn
        active.elapsedTicks = 0;
        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY();
        active.sz = ctx.npc.getZ();
        final int charge = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 24));
        spawnVolleyTelegraphs(active, ctx, 0, charge);
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = charge;
        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.zombie.infect", 0.85F, 0.7F);
        return true;
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
            spawnVolley(active, ctx, 0);
            active.meter = 1.0F;
            final int volleys = volleyCount(ctx);
            if (active.meter >= volleys) {
                return TickResult.FINISHED;
            }
            final int interval = volleyInterval(ctx);
            spawnVolleyTelegraphs(active, ctx, 1, interval);
            active.phase = ActiveAbility.PHASE_ACTIVE;
            active.ticksLeft = interval;
            return TickResult.CONTINUE;
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        AbilityTelegraph.clear(active, ctx);
        final int next = (int) active.meter;
        spawnVolley(active, ctx, next);
        active.meter = next + 1.0F;
        final int volleys = volleyCount(ctx);
        if (active.meter >= volleys) {
            return TickResult.FINISHED;
        }
        final int interval = volleyInterval(ctx);
        spawnVolleyTelegraphs(active, ctx, (int) active.meter, interval);
        active.ticksLeft = interval;
        return TickResult.CONTINUE;
    }

    private static void spawnVolley(
            final ActiveAbility active, final AbilityContext ctx, final int volleyIndex) {
        final int tab = ctx.params.getInt(AbilityParamKeys.CLONE_TAB, 1);
        final String name =
                ctx.params.getString(AbilityParamKeys.CLONE_NAME, "Drachenfels Leper Phantom");
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 10.0);
        DrachenfelsEncounterHelper.spawnLeperVolley(ctx.npc, tab, name, damage, volleyIndex);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.vex.charge", 0.9F, 0.65F);
    }

    private static void spawnVolleyTelegraphs(
            final ActiveAbility active,
            final AbilityContext ctx,
            final int volleyIndex,
            final int durationTicks) {
        active.markers.clear();
        final double[][] points = DrachenfelsEncounterHelper.leperSpawnPoints(ctx.npc, volleyIndex);
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 1.0);
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, 0xC0FF3030);
        final int life = Math.max(1, durationTicks);
        for (int i = 0; i < points.length; i++) {
            final double[] p = points[i];
            active.markers.add(p);
            final String id = TelegraphAPI.circle(ctx.npc, p[0], p[1], p[2], radius, life, color);
            if (id != null && !id.isEmpty()) {
                active.telegraphIds.add(id);
            }
        }
    }

    private static int volleyCount(final AbilityContext ctx) {
        return Math.max(1, ctx.params.getInt(AbilityParamKeys.SUMMON_COUNT, DEFAULT_VOLLEYS));
    }

    private static int volleyInterval(final AbilityContext ctx) {
        return Math.max(8, ctx.params.getInt(AbilityParamKeys.SHOT_INTERVAL, DEFAULT_VOLLEY_INTERVAL));
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        AbilityTelegraph.clear(active, ctx);
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
