package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.abilities.event.NecromancerCombatHandler;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.Map;
import java.util.Set;

public final class NecromancerVolleyAbility implements CnpcAbility {
    public static final String ID = "necro_volley";

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
        return AbilityDefaults.necroVolley();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.SHOTS,
                AbilityParamKeys.SHOT_INTERVAL,
                AbilityParamKeys.FIRST_SHOT_TICK,
                AbilityParamKeys.SPREAD_RADIUS,
                AbilityParamKeys.LAND_RADIUS,
                AbilityParamKeys.MAX_RANGE,
                AbilityParamKeys.BLOB_PARTICLES,
                AbilityParamKeys.LAND_PARTICLES,
                AbilityParamKeys.PARTICLE_COUNT,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        if (NecromancerCombatHandler.isStunned(ctx.npc)) {
            return false;
        }
        if (ctx.target == null || !ctx.target.isAlive()) {
            return false;
        }
        final double maxRange = ctx.params.getDouble(AbilityParamKeys.MAX_RANGE, 24.0);
        if (AbilityCombatHelper.distanceToTarget(ctx) > maxRange) {
            return false;
        }

        active.jumpStyle = true;
        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY() + 1.1;
        active.sz = ctx.npc.getZ();
        active.markers.clear();
        active.telegraphIds.clear();
        active.hitUuids.clear();

        pickLandings(active, ctx);
        final int chargeTicks = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 20));
        final double radius = ctx.params.getDouble(AbilityParamKeys.LAND_RADIUS, 1.6);
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, TelegraphAPI.DEFAULT_COLOR);

        for (final double[] m : active.markers) {
            final String id = TelegraphAPI.circle(
                    ctx.npc, m[0], m[1], m[2], radius, chargeTicks, color);
            if (id != null && !id.isEmpty()) {
                active.telegraphIds.add(id);
            }
        }

        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = chargeTicks;
        faceTarget(active, ctx);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.evoker.prepare_attack", 0.8F, 0.7F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (NecromancerCombatHandler.isStunned(ctx.npc)) {
            return TickResult.FINISHED;
        }
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickVolley(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        faceTarget(active, ctx);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 4 == 0) {
            AbilityVfx.spawnDarkCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
            AbilityVfx.spawnSoulCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY() + 0.5, ctx.npc.getZ());
        }
        if (active.ticksLeft % 5 == 0) {
            for (final double[] m : active.markers) {
                AbilityVfx.spawnSoulFogCloud(ctx.world, m[0], m[1] + 0.15, m[2], 0.6F);
            }
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 18));
        active.hitUuids.clear();
        AbilityVfx.spawnSoulBurst(ctx.world, ctx.npc.getX(), ctx.npc.getY() + 0.4, ctx.npc.getZ(), 1.2);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.wither.shoot", 0.8F, 0.85F);
        return TickResult.CONTINUE;
    }

    private TickResult tickVolley(final ActiveAbility active, final AbilityContext ctx) {
        if (active.ticksLeft <= 0) {
            return TickResult.FINISHED;
        }
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);

        final int total = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 18));
        final int elapsed = total - active.ticksLeft;
        final int firstShot = Math.max(0, ctx.params.getInt(AbilityParamKeys.FIRST_SHOT_TICK, 1));
        final int interval = Math.max(1, ctx.params.getInt(AbilityParamKeys.SHOT_INTERVAL, 5));
        if (elapsed >= firstShot && ((elapsed - firstShot) % interval == 0)) {
            final int index = (elapsed - firstShot) / interval;
            if (index >= 0 && index < active.markers.size()) {
                fireOrb(active, ctx, index);
            }
        }

        active.ticksLeft--;
        return TickResult.CONTINUE;
    }

    private void fireOrb(final ActiveAbility active, final AbilityContext ctx, final int index) {
        final double[] m = active.markers.get(index);
        final double x = m[0];
        final double y = m[1];
        final double z = m[2];
        AbilityVfx.spawnSoulThread(
                ctx.world,
                ctx.npc.getX(),
                ctx.npc.getY() + 1.1,
                ctx.npc.getZ(),
                x,
                y + 0.7,
                z);
        AbilityVfx.spawnCrimsonBlob(
                ctx.world,
                x,
                y + 0.8,
                z,
                ctx.params.getString(AbilityParamKeys.BLOB_PARTICLES, ""),
                ctx.params.getInt(AbilityParamKeys.PARTICLE_COUNT, 12));
        AbilityVfx.spawnCrimsonBlobLand(
                ctx.world,
                x,
                y + 0.15,
                z,
                ctx.params.getDouble(AbilityParamKeys.LAND_RADIUS, 1.6),
                ctx.params.getString(AbilityParamKeys.LAND_PARTICLES, ""),
                Math.max(14, ctx.params.getInt(AbilityParamKeys.PARTICLE_COUNT, 12)));
        NecromancerMinionHelper.spawnSphere(ctx.npc, ctx.target, x, y, z);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(x, y, z),
                "minecraft:block.respawn_anchor.deplete",
                0.85F,
                0.8F + index * 0.05F);
    }

    private static void pickLandings(final ActiveAbility active, final AbilityContext ctx) {
        final int shots = Math.max(1, Math.min(5, ctx.params.getInt(AbilityParamKeys.SHOTS, 3)));
        final double spread = Math.max(4.0, ctx.params.getDouble(AbilityParamKeys.SPREAD_RADIUS, 9.0));
        final double originX = ctx.npc.getX();
        final double originZ = ctx.npc.getZ();
        final double minDist = spread * 0.65;
        final double minSeparation = Math.max(4.0, spread * 0.45);
        final double sector = (Math.PI * 2.0) / shots;
        final double baseAngle = AbilityCombatHelper.random().nextDouble() * Math.PI * 2.0;

        for (int i = 0; i < shots; i++) {
            double x = originX;
            double z = originZ;
            boolean placed = false;
            for (int attempt = 0; attempt < 8 && !placed; attempt++) {
                final double angle = baseAngle + sector * i
                        + (AbilityCombatHelper.random().nextDouble() - 0.5) * sector * 0.35;
                final double dist = minDist + AbilityCombatHelper.random().nextDouble() * (spread - minDist);
                x = originX + Math.cos(angle) * dist;
                z = originZ + Math.sin(angle) * dist;
                if (!tooCloseToMarkers(active, x, z, minSeparation)) {
                    placed = true;
                }
            }
            final double y = AbilityCombatHelper.findGroundY(ctx.world, x, z, ctx.npc.getY());
            active.markers.add(new double[]{x, y, z});
        }
    }

    private static boolean tooCloseToMarkers(
            final ActiveAbility active, final double x, final double z, final double minSeparation) {
        final double minSq = minSeparation * minSeparation;
        for (final double[] m : active.markers) {
            final double dx = m[0] - x;
            final double dz = m[2] - z;
            if (dx * dx + dz * dz < minSq) {
                return true;
            }
        }
        return false;
    }

    private static void faceTarget(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null) {
            return;
        }
        final double dx = ctx.target.getX() - ctx.npc.getX();
        final double dz = ctx.target.getZ() - ctx.npc.getZ();
        active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
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
