package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.entity.EntityAbilityZone;
import noppes.npcs.zone.ZoneAPI;

import java.util.Map;
import java.util.Set;

/**
 * Навесной сгусток партиклов: charge → пролёт по дуге → hazard-зона
 * (настраиваемые дебаффы + MAGIC DPS) в точке приземления.
 */
public final class CrimsonBlobAbility implements CnpcAbility {
    public static final String ID = "crimson_blob";
    private static final int DEFAULT_ZONE_COLOR = 0xC0801010;
    private static final String DEFAULT_EFFECT = "minecraft:blindness";

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
        return AbilityDefaults.crimsonBlob();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.ARC_HEIGHT,
                AbilityParamKeys.LAND_RADIUS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.MAX_RANGE,
                AbilityParamKeys.ZONE_TICKS,
                AbilityParamKeys.DAMAGE_INTERVAL,
                AbilityParamKeys.EFFECT_ID,
                AbilityParamKeys.EFFECT_DURATION,
                AbilityParamKeys.EFFECT_AMPLIFIER,
                AbilityParamKeys.ZONE_COLOR,
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
        final double maxRange = ctx.params.getDouble(AbilityParamKeys.MAX_RANGE, 20.0);
        if (AbilityCombatHelper.distanceToTarget(ctx) > maxRange) {
            return false;
        }

        active.jumpStyle = false;
        active.hitUuids.clear();

        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY() + 1.2;
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
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 20);
        AbilityCombatHelper.stopNavigation(ctx.npc);

        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.blaze.shoot", 0.85F, 0.55F);
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
            spawnBlobVfx(ctx, active.sx, active.sy, active.sz);
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 14));
        spawnBlobVfx(ctx, active.sx, active.sy, active.sz);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.wither.shoot", 0.75F, 0.7F);
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

        spawnBlobVfx(ctx, cx, cy, cz);
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
        final double zoneY = y + 0.05;

        final double radius = ctx.params.getDouble(AbilityParamKeys.LAND_RADIUS, 2.0);
        final int zoneTicks = ctx.params.getInt(AbilityParamKeys.ZONE_TICKS, 160);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 3.0);
        final int damageInterval = ctx.params.getInt(AbilityParamKeys.DAMAGE_INTERVAL, 20);
        final String effectId = ctx.params.getString(AbilityParamKeys.EFFECT_ID, DEFAULT_EFFECT);
        final int effectDuration = ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 40);
        final int effectAmplifier = ctx.params.getInt(AbilityParamKeys.EFFECT_AMPLIFIER, 0);
        final int zoneColor = ctx.params.getInt(AbilityParamKeys.ZONE_COLOR, DEFAULT_ZONE_COLOR);
        final String landParticles = ctx.params.getString(AbilityParamKeys.LAND_PARTICLES, "");
        final int particleCount = ctx.params.getInt(AbilityParamKeys.PARTICLE_COUNT, 12);

        AbilityVfx.spawnCrimsonBlobLand(
                ctx.world, x, zoneY + 0.25, z, radius, landParticles, Math.max(particleCount, 16));
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(x, zoneY, z),
                "minecraft:entity.generic.explode",
                0.7F,
                0.55F);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(x, zoneY, z),
                "minecraft:block.respawn_anchor.deplete",
                0.9F,
                0.8F);

        final EntityAbilityZone zone = ZoneAPI.hazardCircle(
                ctx.npc, x, zoneY, z, radius, zoneTicks, damage, damageInterval);
        if (zone != null) {
            zone.setColor(zoneColor);
            zone.setZoneHeight(3.0f);
            zone.setEffect(effectId, effectDuration, effectAmplifier);
            zone.setVisible(true);
            zone.setGroundFill(true);
            zone.setBorder(true);
        }
    }

    private static void spawnBlobVfx(final AbilityContext ctx, final double x, final double y, final double z) {
        final String particles = ctx.params.getString(AbilityParamKeys.BLOB_PARTICLES, "");
        final int count = ctx.params.getInt(AbilityParamKeys.PARTICLE_COUNT, 12);
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
