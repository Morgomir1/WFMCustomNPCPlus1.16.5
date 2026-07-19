package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.Map;
import java.util.Set;

/**
 * Несколько soul-шаров: на charge — круглые telegraph в точках падения,
 * на active — пролёт VFX и AoE-урон в круге при приземлении.
 * Line/прямоугольные абилки (spirit_barrage, soul_seeker) не затрагивает.
 */
public final class DrachenfelsSoulOrbsAbility implements CnpcAbility {
    public static final String ID = "drachenfels_soul_orbs";

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
        return AbilityDefaults.drachenfelsSoulOrbs();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.SHOTS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.LAND_RADIUS,
                AbilityParamKeys.SPREAD_RADIUS,
                AbilityParamKeys.MAX_RANGE,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return false;
        }
        final double maxRange = ctx.params.getDouble(AbilityParamKeys.MAX_RANGE, 28.0);
        if (AbilityCombatHelper.distanceToTarget(ctx) > maxRange) {
            return false;
        }

        active.jumpStyle = true;
        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY();
        active.sz = ctx.npc.getZ();
        active.markers.clear();
        active.telegraphIds.clear();
        active.hitUuids.clear();

        pickLandings(active, ctx);

        final int chargeTicks = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 30);
        final double landRadius = ctx.params.getDouble(AbilityParamKeys.LAND_RADIUS, 2.5);
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, TelegraphAPI.DEFAULT_COLOR);

        for (final double[] m : active.markers) {
            final String id = TelegraphAPI.circle(
                    ctx.npc, m[0], m[1], m[2], landRadius, chargeTicks, color);
            if (id != null && !id.isEmpty()) {
                active.telegraphIds.add(id);
            }
        }

        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = chargeTicks;
        AbilityCombatHelper.stopNavigation(ctx.npc);
        faceTarget(active, ctx);
        ctx.npc.setRotation(active.yaw);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.evoker.prepare_attack", 0.85F, 0.75F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickOrbs(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        faceTarget(active, ctx);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 4 == 0) {
            AbilityVfx.spawnSoulCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
            AbilityVfx.spawnSoulFogCloud(
                    ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), 1.1F);
        }
        // Pulse markers while charging
        if (active.ticksLeft % 5 == 0) {
            for (final double[] m : active.markers) {
                AbilityVfx.spawnSoulFogCloud(ctx.world, m[0], m[1] + 0.2, m[2], 0.7F);
            }
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 16);
        active.hitUuids.clear();
        AbilityVfx.spawnSoulBurst(ctx.world, ctx.npc.getX(), ctx.npc.getY() + 0.5, ctx.npc.getZ(), 1.2);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.wither.shoot", 0.7F, 1.4F);
        return TickResult.CONTINUE;
    }

    private TickResult tickOrbs(final ActiveAbility active, final AbilityContext ctx) {
        if (active.ticksLeft <= 0 || active.markers.isEmpty()) {
            return TickResult.FINISHED;
        }

        final int total = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 16));
        final int orbs = active.markers.size();
        final int elapsed = total - active.ticksLeft;
        final int interval = Math.max(1, total / orbs);

        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);

        if (elapsed % interval == 0) {
            final int index = elapsed / interval;
            if (index < orbs) {
                landOrb(active, ctx, index);
            }
        }

        active.ticksLeft--;
        return TickResult.CONTINUE;
    }

    private void landOrb(final ActiveAbility active, final AbilityContext ctx, final int index) {
        final double[] m = active.markers.get(index);
        final double lx = m[0];
        final double ly = m[1];
        final double lz = m[2];

        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 9.0);
        final double landRadius = ctx.params.getDouble(AbilityParamKeys.LAND_RADIUS, 2.5);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 0.7);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.22);

        AbilityVfx.spawnSoulThread(
                ctx.world,
                ctx.npc.getX(),
                ctx.npc.getY() + 1.2,
                ctx.npc.getZ(),
                lx,
                ly + 0.6,
                lz);

        // Fresh hit list per orb so one player can be hit by multiple landings if unlucky
        active.hitUuids.clear();
        AbilityCombatHelper.damageNearby(
                active, ctx, lx, ly + 0.4, lz,
                landRadius, damage, 0, 0, knockback, knockbackY, false);

        AbilityVfx.spawnSoulBurst(ctx.world, lx, ly + 0.3, lz, landRadius);
        AbilityVfx.spawnSoulFogCloud(ctx.world, lx, ly + 0.15, lz, 1.2F);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(lx, ly, lz),
                "minecraft:entity.vex.death",
                0.9F,
                0.65F + index * 0.1F);
    }

    private static void pickLandings(final ActiveAbility active, final AbilityContext ctx) {
        final int orbs = Math.max(2, Math.min(6, ctx.params.getInt(AbilityParamKeys.SHOTS, 3)));
        final double spread = ctx.params.getDouble(AbilityParamKeys.SPREAD_RADIUS, 4.5);
        final double tx = ctx.target.getX();
        final double tz = ctx.target.getZ();
        final double ty = AbilityCombatHelper.findGroundY(ctx.world, tx, tz, ctx.target.getY());

        active.ex = tx;
        active.ey = ty;
        active.ez = tz;
        faceTarget(active, ctx);

        // First orb near the target, others in a ring with jitter
        active.markers.add(new double[]{tx, ty, tz});
        for (int i = 1; i < orbs; i++) {
            final double angle = (Math.PI * 2.0 * i) / orbs
                    + AbilityCombatHelper.random().nextDouble() * 0.5;
            final double dist = spread * (0.45 + AbilityCombatHelper.random().nextDouble() * 0.55);
            final double x = tx + Math.cos(angle) * dist;
            final double z = tz + Math.sin(angle) * dist;
            final double y = AbilityCombatHelper.findGroundY(ctx.world, x, z, ty);
            active.markers.add(new double[]{x, y, z});
        }
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
