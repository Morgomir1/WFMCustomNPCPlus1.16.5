package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.entity.EntityAbilityZone;
import noppes.npcs.telegraph.TelegraphAPI;
import noppes.npcs.zone.ZoneAPI;

import java.util.Map;
import java.util.Set;

/** Phase 1: three charge circles then burst + lingering hazard puddles. */
public final class DfBlackSealAbility implements CnpcAbility {
    public static final String ID = "df_black_seal";
    /** Dark green for lingering seal puddles (attack telegraphs stay red). */
    private static final int PUDDLE_COLOR = 0xC0143C14;

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
        return AbilityDefaults.dfBlackSeal();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.ZONE_TICKS,
                AbilityParamKeys.DAMAGE_INTERVAL,
                AbilityParamKeys.DAMAGE_PER_TICK,
                AbilityParamKeys.SUMMON_RADIUS,
                AbilityParamKeys.SPREAD_RADIUS,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR,
                AbilityParamKeys.ZONE_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return false;
        }
        active.jumpStyle = false;
        active.markers.clear();
        active.telegraphIds.clear();
        active.hitUuids.clear();
        pickCircles(active, ctx);
        final int charge = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 30));
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 2.0);
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
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.evoker.prepare_attack", 0.9F, 0.65F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.holdInPlace(ctx.npc, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            if (active.ticksLeft % 5 == 0) {
                AbilityVfx.spawnDarkCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
            }
            active.ticksLeft--;
            if (active.ticksLeft > 0) {
                return TickResult.CONTINUE;
            }
            detonate(active, ctx);
            active.phase = ActiveAbility.PHASE_ACTIVE;
            active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 1));
            return TickResult.CONTINUE;
        }
        active.ticksLeft--;
        return active.ticksLeft > 0 ? TickResult.CONTINUE : TickResult.FINISHED;
    }

    private void detonate(final ActiveAbility active, final AbilityContext ctx) {
        AbilityTelegraph.clear(active, ctx);
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 2.0);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 12.0);
        final int zoneTicks = ctx.params.getInt(AbilityParamKeys.ZONE_TICKS, 100);
        final int interval = ctx.params.getInt(AbilityParamKeys.DAMAGE_INTERVAL, 10);
        final double tickDmg = ctx.params.getDouble(AbilityParamKeys.DAMAGE_PER_TICK, 3.0);
        for (final double[] m : active.markers) {
            active.hitUuids.clear();
            AbilityCombatHelper.damageNearbyPure(
                    active, ctx, m[0], m[1] + 0.4, m[2], radius, damage, 0, 0, 0, 0, false);
            final EntityAbilityZone zone = ZoneAPI.hazardCircle(
                    ctx.npc, m[0], m[1] + 0.05, m[2], radius, zoneTicks, tickDmg, interval);
            if (zone != null) {
                final int puddleColor = ctx.params.getInt(AbilityParamKeys.ZONE_COLOR, PUDDLE_COLOR);
                zone.setColor(puddleColor);
                zone.setZoneHeight(2.5f);
            }
            AbilityVfx.spawnSoulBurst(ctx.world, m[0], m[1] + 0.3, m[2], radius);
            ctx.world.playSoundAt(
                    NpcAPI.Instance().getIPos(m[0], m[1], m[2]),
                    "minecraft:entity.wither.break_block",
                    0.75F,
                    0.8F);
        }
    }

    private static void pickCircles(final ActiveAbility active, final AbilityContext ctx) {
        final double ty = AbilityCombatHelper.findGroundY(
                ctx.world, ctx.target.getX(), ctx.target.getZ(), ctx.target.getY());
        active.markers.add(new double[]{ctx.target.getX(), ty, ctx.target.getZ()});
        final double bx = ctx.npc.getX();
        final double bz = ctx.npc.getZ();
        final double by = ctx.npc.getY();
        int placed = 0;
        int attempts = 0;
        while (placed < 2 && attempts < 40) {
            attempts++;
            final double ang = AbilityCombatHelper.random().nextDouble() * Math.PI * 2.0;
            final double dist = 3.0 + AbilityCombatHelper.random().nextDouble() * 8.0;
            final double x = bx + Math.cos(ang) * dist;
            final double z = bz + Math.sin(ang) * dist;
            if (AbilityCombatHelper.flatDistance(x, z, bx, bz)
                    < ctx.params.getDouble(AbilityParamKeys.SUMMON_RADIUS, 2.0)) {
                continue;
            }
            boolean ok = true;
            final double minCircle = ctx.params.getDouble(AbilityParamKeys.SPREAD_RADIUS, 4.0);
            for (final double[] m : active.markers) {
                if (AbilityCombatHelper.flatDistance(x, z, m[0], m[2]) < minCircle) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                continue;
            }
            final double y = AbilityCombatHelper.findGroundY(ctx.world, x, z, by);
            active.markers.add(new double[]{x, y, z});
            placed++;
        }
        while (placed < 2) {
            final double ang = placed * 2.1;
            final double x = bx + Math.cos(ang) * 5.0;
            final double z = bz + Math.sin(ang) * 5.0;
            final double y = AbilityCombatHelper.findGroundY(ctx.world, x, z, by);
            active.markers.add(new double[]{x, y, z});
            placed++;
        }
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
