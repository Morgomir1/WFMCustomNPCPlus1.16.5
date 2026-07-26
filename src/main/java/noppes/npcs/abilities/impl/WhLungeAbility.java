package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Рывок / High Strike: charge → прыжок-выпад → урон + wfm:stun (не блокируется стан-ом).
 */
public final class WhLungeAbility implements CnpcAbility {
    public static final String ID = "wh_lunge";
    private static final String STUN_EFFECT = "wfm:stun";

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
        return AbilityDefaults.whLunge();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.DISTANCE,
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y,
                AbilityParamKeys.HIT_RADIUS,
                AbilityParamKeys.LAND_RADIUS,
                AbilityParamKeys.ARC_HEIGHT,
                AbilityParamKeys.EFFECT_DURATION,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return false;
        }

        active.jumpStyle = true;
        active.markers.clear();
        active.telegraphIds.clear();

        if (!AbilityCombatHelper.computeDashEndPoints(active, ctx)) {
            return false;
        }

        final double tx = ctx.target.getX();
        final double tz = ctx.target.getZ();
        final double ty = AbilityCombatHelper.findGroundY(ctx.world, tx, tz, ctx.target.getY());
        active.markers.add(new double[]{tx, ty, tz});

        spawnChargeTelegraphs(active, ctx);

        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 20);
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.ravager.roar", 0.55F, 1.45F);
        return true;
    }

    private void spawnChargeTelegraphs(final ActiveAbility active, final AbilityContext ctx) {
        final int chargeTicks = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 20));
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, TelegraphAPI.DEFAULT_COLOR);
        final double hitRadius = ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 1.5);
        final double landRadius = ctx.params.getDouble(AbilityParamKeys.LAND_RADIUS, 1.75);
        final double dashLen = Math.sqrt(
                (active.ex - active.sx) * (active.ex - active.sx)
                        + (active.ez - active.sz) * (active.ez - active.sz));
        if (dashLen < 0.5 || active.markers.isEmpty()) {
            return;
        }

        final double[] lock = active.markers.get(0);
        final String lineId = TelegraphAPI.line(
                ctx.npc,
                active.sx,
                active.sy,
                active.sz,
                active.yaw,
                dashLen,
                hitRadius,
                chargeTicks,
                color);
        if (lineId != null && !lineId.isEmpty()) {
            active.telegraphIds.add(lineId);
        }
        final String circleId = TelegraphAPI.circle(
                ctx.npc,
                lock[0],
                lock[1],
                lock[2],
                landRadius,
                chargeTicks,
                color);
        if (circleId != null && !circleId.isEmpty()) {
            active.telegraphIds.add(circleId);
        }
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickActive(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 4 == 0) {
            AbilityVfx.spawnChargeParticles(
                    ctx.world,
                    ctx.npc.getX(),
                    ctx.npc.getY(),
                    ctx.npc.getZ(),
                    true);
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 6);
        active.hitUuids.clear();
        AbilityVfx.spawnStartBurst(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), true);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.player.attack.strong", 1.0F, 0.9F);
        return TickResult.CONTINUE;
    }

    private TickResult tickActive(final ActiveAbility active, final AbilityContext ctx) {
        final int total = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 6);
        if (active.ticksLeft <= 0) {
            finishLunge(active, ctx);
            return TickResult.FINISHED;
        }

        final double progress = 1.0 - (active.ticksLeft - 1) / (double) total;
        final double[] point = AbilityCombatHelper.resolveDashPointAtProgress(
                ctx, active.sx, active.sy, active.sz, active.ex, active.ez, progress);
        final double arcHeight = ctx.params.getDouble(AbilityParamKeys.ARC_HEIGHT, 1.8);
        final double cx = point[0];
        final double cy = point[1] + arcHeight * 4.0 * progress * (1.0 - progress);
        final double cz = point[2];

        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setPosition(cx, cy, cz);
        ctx.npc.setRotation(active.yaw);

        final double rad = (active.yaw + 90.0) * 0.0174532925;
        final double knockDirX = Math.cos(rad);
        final double knockDirZ = Math.sin(rad);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 16.0);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 0.0);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.0);
        final double hitRadius = ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 1.5);

        AbilityCombatHelper.damageNearby(
                active, ctx, cx, cy + 0.5, cz,
                hitRadius, damage, knockDirX, knockDirZ, knockback, knockbackY, true);

        AbilityVfx.spawnDashTrail(ctx.world, cx, cy, cz);
        active.ticksLeft--;
        return TickResult.CONTINUE;
    }

    private void finishLunge(final ActiveAbility active, final AbilityContext ctx) {
        final double[] point = AbilityCombatHelper.resolveDashPointAtProgress(
                ctx, active.sx, active.sy, active.sz, active.ex, active.ez, 1.0);
        ctx.npc.setPosition(point[0], point[1], point[2]);
        AbilityVfx.spawnLandBurst(ctx.world, point[0], point[1], point[2], true);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(point[0], point[1], point[2]),
                "minecraft:entity.player.attack.crit",
                1.0F,
                0.95F);
        applyLandingStun(active, ctx, point[0], point[1], point[2]);
    }

    /**
     * Стан в конце рывка: по попаданию (hitUuids) и/или по кругу приземления.
     * Важно станть hitUuids — knockback во время dash выталкивает из круга.
     */
    private void applyLandingStun(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double landX,
            final double landY,
            final double landZ) {
        final int stunTicks = ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 20);
        if (stunTicks <= 0) {
            return;
        }

        final double landRadius = ctx.params.getDouble(AbilityParamKeys.LAND_RADIUS, 1.75);
        final double lx;
        final double ly;
        final double lz;
        if (!active.markers.isEmpty()) {
            final double[] lock = active.markers.get(0);
            lx = lock[0];
            ly = lock[1];
            lz = lock[2];
        } else {
            lx = landX;
            ly = landY;
            lz = landZ;
        }

        // Широкий поиск: круг телеграфа + точка приземления NPC (после knockback цель уезжает).
        final int range = (int) Math.ceil(Math.max(landRadius, 4.0) + 2.0);
        final IEntity[] nearLock = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(lx, ly, lz), range, -1);
        final IEntity[] nearLand = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(landX, landY, landZ), range, -1);

        final Set<String> stunned = new HashSet<>();
        stunCandidates(active, ctx, nearLock, landRadius, lx, lz, stunTicks, stunned);
        stunCandidates(active, ctx, nearLand, landRadius, landX, landZ, stunTicks, stunned);

        // Цель атаки: если её задел рывок — стан гарантированно.
        if (ctx.target != null && ctx.target.isAlive()) {
            final String tid = String.valueOf(ctx.target.getUUID());
            if (active.hitUuids.contains(tid) && !stunned.contains(tid)) {
                applyStun(ctx, ctx.target, stunTicks);
            }
        }
    }

    private void stunCandidates(
            final ActiveAbility active,
            final AbilityContext ctx,
            final IEntity[] list,
            final double landRadius,
            final double cx,
            final double cz,
            final int stunTicks,
            final Set<String> stunned) {
        if (list == null) {
            return;
        }
        for (final IEntity ent : list) {
            if (!AbilityCombatHelper.isHostileToBoss(ctx.npc, ent)) {
                continue;
            }
            final String id = String.valueOf(ent.getUUID());
            if (stunned.contains(id)) {
                continue;
            }
            final boolean hitByDash = active.hitUuids.contains(id);
            final boolean inCircle =
                    AbilityCombatHelper.flatDistance(ent.getX(), ent.getZ(), cx, cz) <= landRadius;
            if (!hitByDash && !inCircle) {
                continue;
            }
            if (applyStun(ctx, ent, stunTicks)) {
                stunned.add(id);
            }
        }
    }

    private boolean applyStun(final AbilityContext ctx, final IEntity ent, final int stunTicks) {
        final boolean ok = AbilityCombatHelper.applyNamedEffect(ent, STUN_EFFECT, stunTicks, 0);
        if (ok) {
            ctx.world.playSoundAt(
                    NpcAPI.Instance().getIPos(ent.getX(), ent.getY(), ent.getZ()),
                    "wfm:enchantment.pommel_strike_stun",
                    1.2F,
                    1.0F);
        }
        return ok;
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
