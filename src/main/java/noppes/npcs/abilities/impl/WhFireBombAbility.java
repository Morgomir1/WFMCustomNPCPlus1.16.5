package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.abilities.integration.WfmIntegration;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.entity.EntityAbilityZone;
import noppes.npcs.telegraph.TelegraphAPI;
import noppes.npcs.zone.ZoneAPI;

import java.util.Map;
import java.util.Set;

/**
 * Огненная бомба: charge → летящая ThrownMine → AoE → scatter → огненные лужи.
 */
public final class WhFireBombAbility implements CnpcAbility {
    public static final String ID = "wh_fire_bomb";

    private static final int PHASE_FLIGHT = ActiveAbility.PHASE_ACTIVE;
    private static final int PHASE_SCATTER = 3;

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
        return AbilityDefaults.whFireBomb();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.SCATTER_TICKS,
                AbilityParamKeys.ARC_HEIGHT,
                AbilityParamKeys.LAND_RADIUS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.MAX_RANGE,
                AbilityParamKeys.SHOTS,
                AbilityParamKeys.SPREAD_RADIUS,
                AbilityParamKeys.HIT_RADIUS,
                AbilityParamKeys.ZONE_TICKS,
                AbilityParamKeys.DAMAGE_INTERVAL,
                AbilityParamKeys.DAMAGE_PER_TICK,
                AbilityParamKeys.FIRE_SECONDS,
                AbilityParamKeys.ZONE_COLOR,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return false;
        }
        final double maxRange = ctx.params.getDouble(AbilityParamKeys.MAX_RANGE, 22.0);
        if (AbilityCombatHelper.distanceToTarget(ctx) > maxRange) {
            return false;
        }

        active.jumpStyle = false;
        active.hitUuids.clear();
        active.markers.clear();
        active.telegraphIds.clear();

        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY() + 1.4;
        active.sz = ctx.npc.getZ();

        active.ex = ctx.target.getX();
        active.ez = ctx.target.getZ();
        active.ey = AbilityCombatHelper.findGroundY(ctx.world, active.ex, active.ez, ctx.target.getY());

        final double dx = active.ex - ctx.npc.getX();
        final double dz = active.ez - ctx.npc.getZ();
        active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
        ctx.npc.setRotation(active.yaw);

        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 20);
        AbilityCombatHelper.stopNavigation(ctx.npc);

        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.tnt.primed", 0.9F, 1.15F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == PHASE_FLIGHT) {
            return tickFlight(active, ctx);
        }
        if (active.phase == PHASE_SCATTER) {
            return tickScatter(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 3 == 0) {
            AbilityVfx.spawnFireRing(
                    ctx.world, ctx.npc.getX(), ctx.npc.getY() + 1.0, ctx.npc.getZ(), 0.8);
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }

        active.phase = PHASE_FLIGHT;
        active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 14));

        // Настоящий летящий снаряд WFM (ThrownMineEntity)
        final boolean thrown = WfmIntegration.throwMineTowardPoint(
                ctx.npc, active.ex, active.ey + 0.4, active.ez, 1.35F, 0.8F);
        if (!thrown) {
            // Fallback без WFM
            shootBombProjectileFallback(ctx, active.ex, active.ey + 0.5, active.ez);
        }
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.blaze.shoot", 0.85F, 0.7F);
        return TickResult.CONTINUE;
    }

    private void shootBombProjectileFallback(
            final AbilityContext ctx,
            final double x,
            final double y,
            final double z) {
        try {
            noppes.npcs.api.item.IItemStack item = ctx.world.createItem("wfm:land_mine", 1);
            if (item == null) {
                item = ctx.world.createItem("minecraft:fire_charge", 1);
            }
            if (item == null) {
                return;
            }
            ctx.npc.shootItem(x, y, z, item, 2);
        } catch (final Exception ignored) {
        }
    }

    private TickResult tickFlight(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);

        // Лёгкий VFX-трейл по расчётной дуге (снаряд летит сам)
        final int total = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 14));
        final double t = 1.0 - (active.ticksLeft - 1) / (double) total;
        final double cx = active.sx + (active.ex - active.sx) * t;
        final double cz = active.sz + (active.ez - active.sz) * t;
        final double baseY = active.sy + (active.ey + 0.3 - active.sy) * t;
        final double arcHeight = ctx.params.getDouble(AbilityParamKeys.ARC_HEIGHT, 6.0);
        final double cy = baseY + arcHeight * 4.0 * t * (1.0 - t);
        if (active.ticksLeft % 2 == 0) {
            AbilityVfx.spawnFireRing(ctx.world, cx, cy, cz, 0.35);
        }

        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }

        detonatePrimary(active, ctx);

        pickScatterMarkers(active, ctx);
        spawnScatterTelegraphs(active, ctx);
        for (final double[] m : active.markers) {
            final boolean ok = WfmIntegration.throwMineTowardPoint(
                    ctx.npc, m[0], m[1] + 0.5, m[2], 0.95F, 1.2F);
            if (!ok) {
                shootBombProjectileFallback(ctx, m[0], m[1] + 0.6, m[2]);
            }
        }

        active.phase = PHASE_SCATTER;
        active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.SCATTER_TICKS, 20));
        return TickResult.CONTINUE;
    }

    private TickResult tickScatter(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        if (active.ticksLeft % 4 == 0) {
            for (final double[] m : active.markers) {
                AbilityVfx.spawnFireRing(ctx.world, m[0], m[1] + 0.3, m[2], 0.4);
            }
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }

        landPuddles(active, ctx);
        AbilityTelegraph.clear(active, ctx);
        return TickResult.FINISHED;
    }

    private void detonatePrimary(final ActiveAbility active, final AbilityContext ctx) {
        final double x = active.ex;
        final double y = active.ey;
        final double z = active.ez;
        final double radius = ctx.params.getDouble(AbilityParamKeys.LAND_RADIUS, 3.5);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 12.0);
        final int fireSeconds = ctx.params.getInt(AbilityParamKeys.FIRE_SECONDS, 3);

        active.hitUuids.clear();
        AbilityCombatHelper.damageNearby(
                active, ctx, x, y + 0.5, z,
                radius, damage, 0, 0, 0.8, 0.25, false);

        // Ignite primary hits
        final int range = (int) Math.ceil(radius + 0.5);
        final noppes.npcs.api.entity.IEntity[] list = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(x, y, z), range, -1);
        for (final noppes.npcs.api.entity.IEntity ent : list) {
            if (active.hitUuids.contains(String.valueOf(ent.getUUID()))) {
                AbilityCombatHelper.igniteEntity(ent, fireSeconds);
            }
        }

        AbilityVfx.spawnLandBurst(ctx.world, x, y, z, false);
        AbilityVfx.spawnFireRing(ctx.world, x, y + 0.2, z, radius);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(x, y, z),
                "minecraft:entity.generic.explode",
                1.0F,
                0.85F);
    }

    private void pickScatterMarkers(final ActiveAbility active, final AbilityContext ctx) {
        active.markers.clear();
        final int count = Math.max(3, Math.min(8, ctx.params.getInt(AbilityParamKeys.SHOTS, 5)));
        final double spread = ctx.params.getDouble(AbilityParamKeys.SPREAD_RADIUS, 5.0);
        final double tx = active.ex;
        final double tz = active.ez;
        final double ty = active.ey;

        for (int i = 0; i < count; i++) {
            final double angle = (Math.PI * 2.0 * i) / count
                    + AbilityCombatHelper.random().nextDouble() * 0.4;
            final double dist = spread * (0.4 + AbilityCombatHelper.random().nextDouble() * 0.6);
            final double x = tx + Math.cos(angle) * dist;
            final double z = tz + Math.sin(angle) * dist;
            final double y = AbilityCombatHelper.findGroundY(ctx.world, x, z, ty);
            active.markers.add(new double[]{x, y, z});
        }
    }

    private void spawnScatterTelegraphs(final ActiveAbility active, final AbilityContext ctx) {
        final int scatterTicks = Math.max(1, ctx.params.getInt(AbilityParamKeys.SCATTER_TICKS, 20));
        final double puddleRadius = ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 1.8);
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, TelegraphAPI.DEFAULT_COLOR);

        for (final double[] m : active.markers) {
            final String id = TelegraphAPI.circle(
                    ctx.npc, m[0], m[1], m[2], puddleRadius, scatterTicks, color);
            if (id != null && !id.isEmpty()) {
                active.telegraphIds.add(id);
            }
        }
    }

    private void landPuddles(final ActiveAbility active, final AbilityContext ctx) {
        final double puddleRadius = ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 1.8);
        final int zoneTicks = ctx.params.getInt(AbilityParamKeys.ZONE_TICKS, 70);
        final double puddleDamage = ctx.params.getDouble(AbilityParamKeys.DAMAGE_PER_TICK, 2.5);
        final int damageInterval = ctx.params.getInt(AbilityParamKeys.DAMAGE_INTERVAL, 15);
        final int zoneColor = ctx.params.getInt(AbilityParamKeys.ZONE_COLOR, 0xC0FF6020);
        final int fireSeconds = ctx.params.getInt(AbilityParamKeys.FIRE_SECONDS, 3);

        int index = 0;
        for (final double[] m : active.markers) {
            final double x = m[0];
            final double y = m[1];
            final double z = m[2];
            final double zoneY = y + 0.05;

            // Burst damage on land
            active.hitUuids.clear();
            AbilityCombatHelper.damageNearby(
                    active, ctx, x, y + 0.4, z,
                    puddleRadius, puddleDamage * 2.0, 0, 0, 0.35, 0.12, false);
            final int range = (int) Math.ceil(puddleRadius + 0.5);
            final noppes.npcs.api.entity.IEntity[] list = ctx.world.getNearbyEntities(
                    NpcAPI.Instance().getIPos(x, y, z), range, -1);
            for (final noppes.npcs.api.entity.IEntity ent : list) {
                if (active.hitUuids.contains(String.valueOf(ent.getUUID()))) {
                    AbilityCombatHelper.igniteEntity(ent, fireSeconds);
                }
            }

            final EntityAbilityZone zone = ZoneAPI.hazardCircle(
                    ctx.npc, x, zoneY, z, puddleRadius, zoneTicks, puddleDamage, damageInterval);
            if (zone != null) {
                zone.setColor(zoneColor);
                zone.setZoneHeight(2.5f);
                zone.setVisible(true);
                zone.setGroundFill(true);
                zone.setBorder(true);
            }

            final Object mine = WfmIntegration.spawnVisualMine(ctx.npc, x, zoneY + 0.15, z);
            if (mine != null) {
                scheduleMineRemove(ctx, mine, 45 + index * 3);
            }

            AbilityVfx.spawnFireRing(ctx.world, x, zoneY + 0.2, z, puddleRadius);
            ctx.world.playSoundAt(
                    NpcAPI.Instance().getIPos(x, y, z),
                    "minecraft:block.fire.extinguish",
                    0.7F,
                    0.6F + index * 0.05F);
            index++;
        }
    }

    private void scheduleMineRemove(final AbilityContext ctx, final Object mine, final int delayTicks) {
        try {
            final Object mc = ctx.npc.getMCEntity();
            if (!(mc instanceof net.minecraft.entity.Entity)) {
                return;
            }
            final net.minecraft.world.World world = ((net.minecraft.entity.Entity) mc).level;
            if (world == null || world.getServer() == null) {
                return;
            }
            final int when = world.getServer().getTickCount() + Math.max(1, delayTicks);
            world.getServer().tell(new net.minecraft.util.concurrent.TickDelayedTask(
                    when,
                    () -> WfmIntegration.removeVisualMine(mine)));
        } catch (final Exception ignored) {
        }
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
