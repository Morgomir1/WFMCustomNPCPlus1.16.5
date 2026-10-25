package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.abilities.integration.WfmIntegration;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IProjectile;
import noppes.npcs.api.item.IItemStack;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.Map;
import java.util.Set;

/**
 * Бросок сети: telegraph → charge → летящий снаряд → опутывание в зоне.
 * При {@code summonCount > 1} — несколько точек рядом с целью ({@code spreadRadius}).
 */
public final class NetThrowAbility implements CnpcAbility {
    public static final String ID = "net_throw";
    private static final String FALLBACK_PROJ = "minecraft:lead";
    private static final String WFM_NET_ITEM = "wfm:dwarf_ranger_net";

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
        return AbilityDefaults.netThrow();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.EFFECT_DURATION,
                AbilityParamKeys.EFFECT_AMPLIFIER,
                AbilityParamKeys.PROJECTILE_ITEM,
                AbilityParamKeys.ACCURACY,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR,
                AbilityParamKeys.SUMMON_COUNT,
                AbilityParamKeys.SPREAD_RADIUS);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return false;
        }

        active.jumpStyle = false;
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 10);
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);

        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY() + 1.2;
        active.sz = ctx.npc.getZ();

        pickLandingMarkers(active, ctx);
        if (active.markers.isEmpty()) {
            return false;
        }
        final double[] first = active.markers.get(0);
        active.ex = first[0];
        active.ey = first[1];
        active.ez = first[2];

        final double dx = active.ex - ctx.npc.getX();
        final double dz = active.ez - ctx.npc.getZ();
        active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
        ctx.npc.setRotation(active.yaw);

        if (active.markers.size() > 1 || ctx.params.getInt(AbilityParamKeys.TELEGRAPH, 1) == 0) {
            spawnLandingTelegraphs(active, ctx);
        }

        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.fishing_bobber.throw", 0.9F, 0.8F);
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
        if (active.ticksLeft % 4 == 0) {
            AbilityVfx.spawnNetTrail(
                    ctx.world,
                    ctx.npc.getX(),
                    ctx.npc.getY() + 1.0,
                    ctx.npc.getZ());
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }

        launchNetProjectiles(active, ctx);
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = Math.max(6, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 12));
        return TickResult.CONTINUE;
    }

    private void pickLandingMarkers(final ActiveAbility active, final AbilityContext ctx) {
        active.markers.clear();
        final int count = Math.max(1, Math.min(6, ctx.params.getInt(AbilityParamKeys.SUMMON_COUNT, 1)));
        final double spread = Math.max(0.0, ctx.params.getDouble(AbilityParamKeys.SPREAD_RADIUS, 0.0));
        final double tx = ctx.target.getX();
        final double tz = ctx.target.getZ();
        final double baseY = ctx.target.getY();

        if (count == 1 && spread < 0.5) {
            final double y = AbilityCombatHelper.findGroundY(ctx.world, tx, tz, baseY);
            active.markers.add(new double[]{tx, y, tz});
            return;
        }

        final double rot = AbilityCombatHelper.random().nextDouble() * Math.PI * 2.0;
        for (int i = 0; i < count; i++) {
            final double angle = rot + (Math.PI * 2.0 * i) / count
                    + (AbilityCombatHelper.random().nextDouble() - 0.5) * 0.55;
            final double dist = spread <= 0.05
                    ? 0.0
                    : spread * (0.35 + AbilityCombatHelper.random().nextDouble() * 0.65);
            final double x = tx + Math.cos(angle) * dist;
            final double z = tz + Math.sin(angle) * dist;
            final double y = AbilityCombatHelper.findGroundY(ctx.world, x, z, baseY);
            active.markers.add(new double[]{x, y, z});
        }
    }

    private void spawnLandingTelegraphs(final ActiveAbility active, final AbilityContext ctx) {
        final int charge = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 10));
        final int flight = Math.max(6, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 12));
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 3.5);
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, 0xC0D8C090);
        for (final double[] m : active.markers) {
            final String id = TelegraphAPI.circleAt(
                    ctx.npc, m[0], m[1], m[2], radius, charge + flight, color);
            if (id != null && !id.isEmpty()) {
                active.telegraphIds.add(id);
            }
        }
    }

    private void launchNetProjectiles(final ActiveAbility active, final AbilityContext ctx) {
        final float inaccuracy = Math.max(0.05F, ctx.params.getInt(AbilityParamKeys.ACCURACY, 2) * 0.12F);
        for (final double[] m : active.markers) {
            final double aimX = m[0];
            final double aimY = m[1] + 0.8;
            final double aimZ = m[2];
            final boolean thrown = WfmIntegration.throwNetTowardPoint(
                    ctx.npc, aimX, aimY, aimZ, 1.55F, inaccuracy);
            if (!thrown) {
                shootFallback(ctx, aimX, aimY, aimZ);
            }
        }
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.snowball.throw", 1.0F, 0.75F);
    }

    private void shootFallback(final AbilityContext ctx, final double x, final double y, final double z) {
        try {
            String itemId = ctx.params.getString(AbilityParamKeys.PROJECTILE_ITEM, WFM_NET_ITEM);
            IItemStack item = ctx.world.createItem(itemId, 1);
            if (item == null) {
                item = ctx.world.createItem(FALLBACK_PROJ, 1);
            }
            if (item == null) {
                return;
            }
            final int accuracy = ctx.params.getInt(AbilityParamKeys.ACCURACY, 2);
            final IProjectile proj = ctx.npc.shootItem(x, y, z, item, accuracy);
            if (proj != null) {
                // no-op: pure visual
            }
        } catch (final Exception ignored) {
        }
    }

    private TickResult tickFlight(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);

        final int total = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 12));
        final double t = 1.0 - (active.ticksLeft - 1) / (double) total;
        if (active.ticksLeft % 2 == 0) {
            for (final double[] m : active.markers) {
                final double cx = active.sx + (m[0] - active.sx) * t;
                final double cz = active.sz + (m[2] - active.sz) * t;
                final double baseY = active.sy + (m[1] + 0.5 - active.sy) * t;
                final double cy = baseY + 2.2 * 4.0 * t * (1.0 - t);
                AbilityVfx.spawnNetTrail(ctx.world, cx, cy, cz);
            }
        }

        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }

        landNets(active, ctx);
        return TickResult.FINISHED;
    }

    private void landNets(final ActiveAbility active, final AbilityContext ctx) {
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 3.5);
        final int duration = ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 60);
        final int amplifier = ctx.params.getInt(AbilityParamKeys.EFFECT_AMPLIFIER, 3);

        for (final double[] m : active.markers) {
            final double x = m[0];
            final double y = m[1];
            final double z = m[2];
            AbilityVfx.spawnNetTrail(ctx.world, x, y + 0.5, z);
            ctx.world.playSoundAt(
                    NpcAPI.Instance().getIPos(x, y, z),
                    "minecraft:entity.fishing_bobber.splash",
                    1.0F,
                    1.0F);

            final int ensnared = WfmIntegration.ensnareAroundPoint(ctx.npc, x, y, z, radius, duration);
            if (ensnared < 0) {
                active.hitUuids.clear();
                AbilityCombatHelper.applyPotionNearby(
                        active, ctx, x, y + 0.5, z,
                        radius, AbilityEffectType.SLOWNESS, duration, amplifier);
            }
        }
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
