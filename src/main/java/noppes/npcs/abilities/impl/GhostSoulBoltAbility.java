package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;

import java.util.Map;
import java.util.Set;

/**
 * Летающий призрак: charge → навесной soul-сгусток (как crimson_blob) →
 * хит/knockback при касании по пути и в точке приземления.
 */
public final class GhostSoulBoltAbility implements CnpcAbility {
    public static final String ID = "ghost_soul_bolt";

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
        return AbilityDefaults.ghostSoulBolt();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.ARC_HEIGHT,
                AbilityParamKeys.LAND_RADIUS,
                AbilityParamKeys.HIT_RADIUS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y,
                AbilityParamKeys.MAX_RANGE,
                AbilityParamKeys.BLOB_PARTICLES,
                AbilityParamKeys.LAND_PARTICLES,
                AbilityParamKeys.PARTICLE_COUNT,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return false;
        }
        final double maxRange = ctx.params.getDouble(AbilityParamKeys.MAX_RANGE, 24.0);
        if (AbilityCombatHelper.distanceToTarget(ctx) > maxRange) {
            return false;
        }

        active.jumpStyle = false;
        active.hitUuids.clear();

        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY() + 0.6;
        active.sz = ctx.npc.getZ();

        final double tx = ctx.target.getX();
        final double tz = ctx.target.getZ();
        final double ty = AbilityCombatHelper.findGroundY(ctx.world, tx, tz, ctx.target.getY());
        active.ex = tx;
        active.ey = ty;
        active.ez = tz;

        final double dx = tx - ctx.npc.getX();
        final double dz = tz - ctx.npc.getZ();
        active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
        ctx.npc.setRotation(active.yaw);

        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 24);
        AbilityCombatHelper.stopNavigation(ctx.npc);

        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.vex.ambient", 0.9F, 0.7F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickFlight(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 3 == 0) {
            AbilityVfx.spawnSoulCharge(ctx.world, active.sx, active.sy, active.sz);
            spawnBoltVfx(ctx, active.sx, active.sy, active.sz);
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 14));
        active.hitUuids.clear();
        spawnBoltVfx(ctx, active.sx, active.sy, active.sz);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.vex.charge", 0.95F, 0.85F);
        return TickResult.CONTINUE;
    }

    private TickResult tickFlight(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);

        final int total = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 14));
        if (active.ticksLeft <= 0) {
            land(active, ctx);
            return TickResult.FINISHED;
        }

        final double t = 1.0 - (active.ticksLeft - 1) / (double) total;
        final double cx = active.sx + (active.ex - active.sx) * t;
        final double cz = active.sz + (active.ez - active.sz) * t;
        final double baseY = active.sy + (active.ey - active.sy) * t;
        final double arcHeight = ctx.params.getDouble(AbilityParamKeys.ARC_HEIGHT, 5.0);
        final double cy = baseY + arcHeight * 4.0 * t * (1.0 - t);

        spawnBoltVfx(ctx, cx, cy, cz);
        tryHit(active, ctx, cx, cy, cz, true);

        active.ticksLeft--;
        if (active.ticksLeft <= 0) {
            land(active, ctx);
            return TickResult.FINISHED;
        }
        return TickResult.CONTINUE;
    }

    private void land(final ActiveAbility active, final AbilityContext ctx) {
        final double x = active.ex;
        final double y = active.ey;
        final double z = active.ez;
        final double landRadius = ctx.params.getDouble(AbilityParamKeys.LAND_RADIUS, 2.2);
        final String landParticles = ctx.params.getString(AbilityParamKeys.LAND_PARTICLES, "");
        final int particleCount = ctx.params.getInt(AbilityParamKeys.PARTICLE_COUNT, 10);

        AbilityVfx.spawnCrimsonBlobLand(
                ctx.world, x, y + 0.35, z, landRadius, landParticles, Math.max(particleCount, 14));
        AbilityVfx.spawnSoulBurst(ctx.world, x, y + 0.3, z, landRadius);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(x, y, z),
                "minecraft:entity.vex.death",
                1.0F,
                0.65F);

        tryHit(active, ctx, x, y + 0.4, z, false);
    }

    private static void tryHit(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double x,
            final double y,
            final double z,
            final boolean inflight) {
        final double radius = inflight
                ? ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 1.4)
                : ctx.params.getDouble(AbilityParamKeys.LAND_RADIUS, 2.2);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 0.0);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 2.2);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.5);

        AbilityCombatHelper.damageNearby(
                active, ctx, x, y, z,
                radius, damage, 0, 0, knockback, knockbackY, false);
    }

    private static void spawnBoltVfx(final AbilityContext ctx, final double x, final double y, final double z) {
        final String particles = ctx.params.getString(AbilityParamKeys.BLOB_PARTICLES, "");
        final int count = ctx.params.getInt(AbilityParamKeys.PARTICLE_COUNT, 10);
        AbilityVfx.spawnCrimsonBlob(ctx.world, x, y, z, particles, count);
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
