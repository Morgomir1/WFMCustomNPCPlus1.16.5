package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.Map;
import java.util.Set;

/** Phase 2: six safe white seats scattered near boss; arena blast except inside seats. */
public final class DfFeastSeatsAbility implements CnpcAbility {
    public static final String ID = "df_feast_seats";
    private static final int SEAT_COUNT = 6;
    private static final int PLACE_ATTEMPTS = 48;

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
        return AbilityDefaults.dfFeastSeats();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.SPREAD_RADIUS,
                AbilityParamKeys.SUMMON_RADIUS,
                AbilityParamKeys.MAX_RANGE,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.EFFECT_TYPE,
                AbilityParamKeys.EFFECT_DURATION,
                AbilityParamKeys.EFFECT_AMPLIFIER,
                AbilityParamKeys.ZONE_COLOR,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = false;
        active.markers.clear();
        active.telegraphIds.clear();
        active.hitUuids.clear();
        final double[] center = DrachenfelsEncounterHelper.getArenaCenter(ctx.npc);
        final double seatR = ctx.params.getDouble(AbilityParamKeys.RADIUS, 1.5);
        final double arenaR = ctx.params.getDouble(AbilityParamKeys.MAX_RANGE, 12.0);
        final int charge = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 40));
        final int seatColor = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, 0xC0FFFFFF);
        final int dangerColor = ctx.params.getInt(AbilityParamKeys.ZONE_COLOR, 0xC0FF3030);
        final String arenaId = TelegraphAPI.circle(
                ctx.npc, center[0], center[1], center[2], arenaR, charge, dangerColor);
        if (arenaId != null && !arenaId.isEmpty()) {
            active.telegraphIds.add(arenaId);
        }
        pickSeats(active, ctx, center, arenaR, seatR);
        for (final double[] m : active.markers) {
            final String id = TelegraphAPI.circle(ctx.npc, m[0], m[1], m[2], seatR, charge, seatColor);
            if (id != null && !id.isEmpty()) {
                active.telegraphIds.add(id);
            }
        }
        active.sx = center[0];
        active.sy = center[1];
        active.sz = center[2];
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = charge;
        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:block.bell.use", 0.7F, 0.55F);
        return true;
    }

    /**
     * Random seats around the boss within [{@code summonRadius}, {@code spreadRadius}],
     * kept inside the arena and spaced apart so they do not stack.
     */
    private static void pickSeats(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double[] arenaCenter,
            final double arenaR,
            final double seatR) {
        final double bx = ctx.npc.getX();
        final double by = ctx.npc.getY();
        final double bz = ctx.npc.getZ();
        final double maxSpread = Math.max(seatR + 1.0, ctx.params.getDouble(AbilityParamKeys.SPREAD_RADIUS, 9.5));
        final double minBoss = Math.max(0.5, ctx.params.getDouble(AbilityParamKeys.SUMMON_RADIUS, 2.5));
        final double minSep = Math.max(seatR * 2.0 + 0.75, 3.5);
        final double arenaLimit = Math.max(seatR + 0.5, arenaR - seatR - 0.25);
        int placed = 0;
        int attempts = 0;
        while (placed < SEAT_COUNT && attempts < PLACE_ATTEMPTS) {
            attempts++;
            final double ang = AbilityCombatHelper.random().nextDouble() * Math.PI * 2.0;
            final double dist = minBoss + AbilityCombatHelper.random().nextDouble() * Math.max(0.1, maxSpread - minBoss);
            final double x = bx + Math.cos(ang) * dist;
            final double z = bz + Math.sin(ang) * dist;
            if (AbilityCombatHelper.flatDistance(x, z, arenaCenter[0], arenaCenter[2]) > arenaLimit) {
                continue;
            }
            if (!seatClear(active, x, z, minSep)) {
                continue;
            }
            final double y = AbilityCombatHelper.findGroundY(ctx.world, x, z, by);
            active.markers.add(new double[]{x, y, z});
            placed++;
        }
        // Fallback: jittered ring around boss if random packing failed.
        while (placed < SEAT_COUNT) {
            final double ang = (Math.PI * 2.0 * placed) / SEAT_COUNT
                    + AbilityCombatHelper.random().nextDouble() * 0.45;
            final double dist = Math.min(maxSpread, Math.max(minBoss, maxSpread * 0.7));
            final double x = bx + Math.cos(ang) * dist;
            final double z = bz + Math.sin(ang) * dist;
            final double y = AbilityCombatHelper.findGroundY(ctx.world, x, z, by);
            active.markers.add(new double[]{x, y, z});
            placed++;
        }
    }

    private static boolean seatClear(
            final ActiveAbility active, final double x, final double z, final double minSep) {
        for (final double[] m : active.markers) {
            if (AbilityCombatHelper.flatDistance(x, z, m[0], m[2]) < minSep) {
                return false;
            }
        }
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
            detonate(active, ctx);
            active.phase = ActiveAbility.PHASE_ACTIVE;
            active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 1));
            return TickResult.CONTINUE;
        }
        active.ticksLeft--;
        return active.ticksLeft > 0 ? TickResult.CONTINUE : TickResult.FINISHED;
    }

    private void detonate(final ActiveAbility active, final AbilityContext ctx) {
        final double arenaR = ctx.params.getDouble(AbilityParamKeys.MAX_RANGE, 12.0);
        final double seatR = ctx.params.getDouble(AbilityParamKeys.RADIUS, 1.5);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 14.0);
        final int poisonDur = ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 60);
        final int poisonAmp = ctx.params.getInt(AbilityParamKeys.EFFECT_AMPLIFIER, 0);
        final int range = (int) Math.ceil(arenaR + 1.0);
        final IEntity[] list = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(active.sx, active.sy, active.sz), range, -1);
        for (final IEntity ent : list) {
            if (!AbilityCombatHelper.isHostileToBoss(ctx.npc, ent)) {
                continue;
            }
            final double dist = AbilityCombatHelper.flatDistance(
                    ent.getX(), ent.getZ(), active.sx, active.sz);
            if (dist > arenaR) {
                continue;
            }
            if (insideAnySeat(active, ent.getX(), ent.getZ(), seatR)) {
                continue;
            }
            final String id = String.valueOf(ent.getUUID());
            if (active.hitUuids.contains(id)) {
                continue;
            }
            if (!AbilityCombatHelper.dealPureDamage(ent, (float) damage, false)) {
                ent.damage((float) damage);
            }
            AbilityCombatHelper.applyEffect(
                    ent, AbilityEffectType.POISON.toMcEffect(), poisonDur, poisonAmp);
            AbilityVfx.spawnHitParticle(ctx.world, ent);
            active.hitUuids.add(id);
        }
        AbilityVfx.spawnSoulBurst(ctx.world, active.sx, active.sy + 0.3, active.sz, arenaR * 0.5);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(active.sx, active.sy, active.sz),
                "minecraft:entity.generic.explode",
                0.8F,
                0.85F);
    }

    private static boolean insideAnySeat(
            final ActiveAbility active, final double x, final double z, final double seatR) {
        for (final double[] m : active.markers) {
            if (AbilityCombatHelper.flatDistance(x, z, m[0], m[2]) <= seatR) {
                return true;
            }
        }
        return false;
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
