package noppes.npcs.abilities.impl;

import net.minecraft.entity.player.PlayerEntity;
import noppes.npcs.abilities.AbilityCombatHelper;
import noppes.npcs.abilities.AbilityContext;
import noppes.npcs.abilities.AbilityDefaults;
import noppes.npcs.abilities.AbilityParamKeys;
import noppes.npcs.abilities.AbilityParams;
import noppes.npcs.abilities.AbilityVfx;
import noppes.npcs.abilities.ActiveAbility;
import noppes.npcs.abilities.CnpcAbility;
import noppes.npcs.abilities.TickResult;
import noppes.npcs.abilities.event.DrachenfelsCursePuddlesHandler;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.IEntityLiving;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Body curse: mark up to 3 players for 10s and spawn 3 cleanse puddles.
 * Entering a free puddle clears the mark; otherwise the caster heals 10 HP per failure.
 * Runtime lives in {@link DrachenfelsCursePuddlesHandler} so the ability can finish after cast.
 */
public final class DrachenfelsCursePuddlesAbility implements CnpcAbility {
    public static final String ID = "drachenfels_curse_puddles";

    private static final int DEFAULT_ZONE_COLOR = 0xC040E0D0;
    /** Минимальная дистанция лужи от босса / игроков (flat). */
    private static final double MIN_AWAY_BOSS = 10.0;
    private static final double MIN_AWAY_PLAYER = 8.0;
    private static final int PUDDLE_PLACE_ATTEMPTS = 28;

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
        return AbilityDefaults.drachenfelsCursePuddles();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.MAX_RANGE,
                AbilityParamKeys.HIT_COUNT,
                AbilityParamKeys.SUMMON_COUNT,
                AbilityParamKeys.HIT_RADIUS,
                AbilityParamKeys.SPREAD_RADIUS,
                AbilityParamKeys.ZONE_TICKS,
                AbilityParamKeys.ZONE_COLOR,
                AbilityParamKeys.HEAL_ON_FAIL,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = false;
        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY();
        active.sz = ctx.npc.getZ();
        active.ex = active.sx;
        active.ey = active.sy;
        active.ez = active.sz;
        active.hitUuids.clear();
        active.telegraphIds.clear();
        active.telegraphId = null;

        if (ctx.target != null && ctx.target.isAlive()) {
            final double dx = ctx.target.getX() - ctx.npc.getX();
            final double dz = ctx.target.getZ() - ctx.npc.getZ();
            active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
            ctx.npc.setRotation(active.yaw);
        }

        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 24));
        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);

        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.evoker.prepare_attack", 1.0F, 0.45F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase != ActiveAbility.PHASE_CHARGE) {
            return TickResult.FINISHED;
        }

        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 4 == 0) {
            AbilityVfx.spawnDarkCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
            AbilityVfx.spawnSoulFogCloud(ctx.world, ctx.npc.getX(), ctx.npc.getY() + 0.35, ctx.npc.getZ(), 1.0F);
        }

        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }

        applyCurseAndPuddles(ctx);
        return TickResult.FINISHED;
    }

    private void applyCurseAndPuddles(final AbilityContext ctx) {
        final double selectRange = Math.max(6.0, ctx.params.getDouble(AbilityParamKeys.MAX_RANGE, 18.0));
        final int maxTargets = Math.max(1, Math.min(3, ctx.params.getInt(AbilityParamKeys.HIT_COUNT, 3)));
        final int puddleCount = Math.max(1, Math.min(3, ctx.params.getInt(AbilityParamKeys.SUMMON_COUNT, 3)));
        final double puddleRadius = Math.max(0.9, ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 2.0));
        final double spreadRadius = Math.max(12.0, ctx.params.getDouble(AbilityParamKeys.SPREAD_RADIUS, 20.0));
        final int curseTicks = Math.max(20, ctx.params.getInt(AbilityParamKeys.ZONE_TICKS, 200));
        final float healOnFail = (float) Math.max(0.0, ctx.params.getDouble(AbilityParamKeys.HEAL_ON_FAIL, 10.0));
        final int zoneColor = ctx.params.getInt(AbilityParamKeys.ZONE_COLOR, DEFAULT_ZONE_COLOR);

        final List<IEntityLiving> candidates = collectHostilePlayers(ctx, selectRange);
        if (candidates.isEmpty() && ctx.target != null && ctx.target.isAlive()) {
            try {
                final Object mc = ctx.target.getMCEntity();
                if (mc instanceof PlayerEntity) {
                    candidates.add(ctx.target);
                }
            } catch (final Exception ignored) {
            }
        }
        Collections.shuffle(candidates, AbilityCombatHelper.random());

        final List<UUID> victims = new ArrayList<>();
        for (int i = 0; i < candidates.size() && victims.size() < maxTargets; i++) {
            final IEntityLiving living = candidates.get(i);
            try {
                victims.add(UUID.fromString(String.valueOf(living.getUUID())));
            } catch (final Exception ignored) {
            }
        }
        if (victims.isEmpty()) {
            return;
        }

        final double cx = ctx.npc.getX();
        final double cz = ctx.npc.getZ();
        final double cy = AbilityCombatHelper.findGroundY(ctx.world, cx, cz, ctx.npc.getY());
        final List<double[]> points = pickPuddlePoints(
                ctx.world,
                cx,
                cy,
                cz,
                spreadRadius,
                puddleCount,
                puddleRadius * 2.4,
                candidates);

        final int cursed = DrachenfelsCursePuddlesHandler.start(
                ctx.npc, victims, points, puddleRadius, curseTicks, healOnFail, zoneColor);
        if (cursed <= 0) {
            return;
        }

        AbilityVfx.spawnSoulWave(ctx.world, cx, cy + 0.2, cz, Math.min(spreadRadius * 0.45, 8.0));
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(cx, cy, cz),
                "minecraft:entity.elder_guardian.curse",
                1.0F,
                0.65F);
    }

    private static List<IEntityLiving> collectHostilePlayers(final AbilityContext ctx, final double range) {
        final List<IEntityLiving> out = new ArrayList<>();
        if (ctx == null || ctx.world == null || ctx.npc == null) {
            return out;
        }
        final int search = (int) Math.ceil(range + 0.5);
        final IEntity[] list = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ()),
                search,
                1);
        for (final IEntity ent : list) {
            if (!(ent instanceof IEntityLiving) || !ent.isAlive()) {
                continue;
            }
            if (!AbilityCombatHelper.isHostileToBoss(ctx.npc, ent)) {
                continue;
            }
            try {
                final Object mc = ent.getMCEntity();
                if (!(mc instanceof PlayerEntity) || !((PlayerEntity) mc).isAlive()) {
                    continue;
                }
            } catch (final Exception e) {
                continue;
            }
            if (AbilityCombatHelper.flatDistance(
                    ent.getX(), ent.getZ(), ctx.npc.getX(), ctx.npc.getZ()) > range) {
                continue;
            }
            out.add((IEntityLiving) ent);
        }
        return out;
    }

    /**
     * Случайные точки в кольце [minRing .. spreadRadius], далеко от босса и игроков.
     * Углы полностью случайные (не равномерный веер).
     */
    private static List<double[]> pickPuddlePoints(
            final noppes.npcs.api.IWorld world,
            final double cx,
            final double cy,
            final double cz,
            final double spreadRadius,
            final int count,
            final double minSeparation,
            final List<IEntityLiving> players) {
        final List<double[]> points = new ArrayList<>();
        final double outer = Math.max(spreadRadius, MIN_AWAY_BOSS + 4.0);
        final double minRing = Math.min(outer * 0.55, Math.max(MIN_AWAY_BOSS, outer * 0.4));

        for (int i = 0; i < count; i++) {
            double bestX = cx;
            double bestZ = cz;
            double bestScore = -1.0;
            boolean foundOk = false;

            for (int attempt = 0; attempt < PUDDLE_PLACE_ATTEMPTS; attempt++) {
                final double angle = AbilityCombatHelper.random().nextDouble() * Math.PI * 2.0;
                final double t = AbilityCombatHelper.random().nextDouble();
                // Bias outward: чаще ближе к внешнему кольцу.
                final double dist = minRing + (outer - minRing) * (0.35 + 0.65 * t * t);
                final double x = cx + Math.cos(angle) * dist;
                final double z = cz + Math.sin(angle) * dist;

                if (tooClose(points, x, z, minSeparation)) {
                    continue;
                }
                final double awayBoss = AbilityCombatHelper.flatDistance(x, z, cx, cz);
                if (awayBoss < MIN_AWAY_BOSS) {
                    continue;
                }
                double minPlayer = Double.POSITIVE_INFINITY;
                boolean nearPlayer = false;
                if (players != null) {
                    for (final IEntityLiving p : players) {
                        if (p == null || !p.isAlive()) {
                            continue;
                        }
                        final double d = AbilityCombatHelper.flatDistance(x, z, p.getX(), p.getZ());
                        if (d < minPlayer) {
                            minPlayer = d;
                        }
                        if (d < MIN_AWAY_PLAYER) {
                            nearPlayer = true;
                            break;
                        }
                    }
                }
                if (nearPlayer) {
                    continue;
                }
                // Score: далеко от босса и от ближайшего игрока.
                final double score = awayBoss + (minPlayer == Double.POSITIVE_INFINITY ? 0.0 : minPlayer);
                if (!foundOk || score > bestScore) {
                    bestScore = score;
                    bestX = x;
                    bestZ = z;
                    foundOk = true;
                }
            }

            if (!foundOk) {
                // Fallback: случайный угол на внешнем кольце, максимизируя дистанцию до игроков.
                for (int attempt = 0; attempt < 16; attempt++) {
                    final double angle = AbilityCombatHelper.random().nextDouble() * Math.PI * 2.0;
                    final double dist = outer * (0.75 + AbilityCombatHelper.random().nextDouble() * 0.25);
                    final double x = cx + Math.cos(angle) * dist;
                    final double z = cz + Math.sin(angle) * dist;
                    if (tooClose(points, x, z, minSeparation * 0.7)) {
                        continue;
                    }
                    double minPlayer = Double.POSITIVE_INFINITY;
                    if (players != null) {
                        for (final IEntityLiving p : players) {
                            if (p == null || !p.isAlive()) {
                                continue;
                            }
                            final double d = AbilityCombatHelper.flatDistance(x, z, p.getX(), p.getZ());
                            if (d < minPlayer) {
                                minPlayer = d;
                            }
                        }
                    }
                    final double score = AbilityCombatHelper.flatDistance(x, z, cx, cz)
                            + (minPlayer == Double.POSITIVE_INFINITY ? 0.0 : minPlayer);
                    if (score > bestScore) {
                        bestScore = score;
                        bestX = x;
                        bestZ = z;
                    }
                }
            }

            final double bestY = AbilityCombatHelper.findGroundY(world, bestX, bestZ, cy) + 0.05;
            points.add(new double[]{bestX, bestY, bestZ});
        }
        return points;
    }

    private static boolean tooClose(
            final List<double[]> points,
            final double x,
            final double z,
            final double minSeparation) {
        for (final double[] p : points) {
            if (AbilityCombatHelper.flatDistance(x, z, p[0], p[2]) < minSeparation) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
