package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;

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
                AbilityParamKeys.ARC_HEIGHT,
                AbilityParamKeys.EFFECT_DURATION,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = true;
        if (!AbilityCombatHelper.computeDashEndPoints(active, ctx)) {
            return false;
        }
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 20);
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.ravager.roar", 0.55F, 1.45F);
        return true;
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
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 1.4);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.4);
        final double hitRadius = ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 1.5);
        final int stunTicks = ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 20);

        final Set<String> before = new HashSet<>(active.hitUuids);
        AbilityCombatHelper.damageNearby(
                active, ctx, cx, cy + 0.5, cz,
                hitRadius, damage, knockDirX, knockDirZ, knockback, knockbackY, true);

        applyStunToNewHits(active, ctx, before, stunTicks);
        AbilityVfx.spawnDashTrail(ctx.world, cx, cy, cz);
        active.ticksLeft--;
        return TickResult.CONTINUE;
    }

    private void applyStunToNewHits(
            final ActiveAbility active,
            final AbilityContext ctx,
            final Set<String> before,
            final int stunTicks) {
        if (stunTicks <= 0) {
            return;
        }
        final double hitRadius = ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 1.5) + 2.0;
        final IEntity[] list = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ()),
                (int) Math.ceil(hitRadius),
                -1);
        for (final IEntity ent : list) {
            final String id = String.valueOf(ent.getUUID());
            if (!active.hitUuids.contains(id) || before.contains(id)) {
                continue;
            }
            AbilityCombatHelper.applyNamedEffect(ent, STUN_EFFECT, stunTicks, 0);
            ctx.world.playSoundAt(
                    NpcAPI.Instance().getIPos(ent.getX(), ent.getY(), ent.getZ()),
                    "wfm:enchantment.pommel_strike_stun",
                    1.2F,
                    1.0F);
        }
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
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
