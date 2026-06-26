package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.abilities.integration.WfmIntegration;
import noppes.npcs.api.entity.IProjectile;
import noppes.npcs.api.item.IItemStack;

import java.util.Map;
import java.util.Set;

public final class NetThrowAbility implements CnpcAbility {
    public static final String ID = "net_throw";
    private static final String WFM_NET_ITEM = "wfm:dwarf_ranger_net";
    private static final String FALLBACK_NET_ITEM = "minecraft:lead";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean requiresTarget() {
        return true;
    }

    @Override
    public Map<String, Object> defaultParams() {
        return AbilityDefaults.netThrow();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.EFFECT_DURATION,
                AbilityParamKeys.EFFECT_AMPLIFIER,
                AbilityParamKeys.ACCURACY,
                AbilityParamKeys.PROJECTILE_ITEM);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = false;
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 10);
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        if (ctx.target != null) {
            final double dx = ctx.target.getX() - ctx.npc.getX();
            final double dz = ctx.target.getZ() - ctx.npc.getZ();
            active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
            ctx.npc.setRotation(active.yaw);
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
            return throwNet(active, ctx);
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
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = 1;
        return TickResult.CONTINUE;
    }

    private TickResult throwNet(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return TickResult.FINISHED;
        }

        final int accuracy = ctx.params.getInt(AbilityParamKeys.ACCURACY, 6);
        final float inaccuracy = Math.max(0.0F, accuracy * 0.1F);

        if (WfmIntegration.throwDwarfRangerNet(ctx.npc, ctx.target, inaccuracy)) {
            AbilityVfx.spawnNetTrail(
                    ctx.world,
                    ctx.npc.getX(),
                    ctx.npc.getY() + 1.0,
                    ctx.npc.getZ());
            return TickResult.FINISHED;
        }

        return throwFallbackNet(active, ctx, accuracy);
    }

    private TickResult throwFallbackNet(
            final ActiveAbility active,
            final AbilityContext ctx,
            final int accuracy) {
        final double tx = ctx.target.getX();
        final double ty = ctx.target.getY();
        final double tz = ctx.target.getZ();
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 2.0);
        final int duration = ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 60);
        final int amplifier = ctx.params.getInt(AbilityParamKeys.EFFECT_AMPLIFIER, 3);
        final String itemId = ctx.params.getString(AbilityParamKeys.PROJECTILE_ITEM, WFM_NET_ITEM);

        IItemStack item = createItem(ctx, itemId);
        if (item == null) {
            item = createItem(ctx, FALLBACK_NET_ITEM);
        }

        try {
            if (item != null) {
                final IProjectile projectile = ctx.npc.shootItem(ctx.target, item, accuracy);
                if (projectile != null) {
                    AbilityVfx.spawnNetTrail(ctx.world, projectile.getX(), projectile.getY(), projectile.getZ());
                }
            }
        } catch (final Exception ignored) {
        }

        AbilityVfx.spawnNetTrail(ctx.world, tx, ty + 0.5, tz);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.fishing_bobber.splash", 0.9F, 1.0F);

        active.hitUuids.clear();
        AbilityCombatHelper.applyPotionNearby(
                active, ctx, tx, ty + 0.5, tz,
                radius, AbilityEffectType.SLOWNESS, duration, amplifier);

        return TickResult.FINISHED;
    }

    private static IItemStack createItem(final AbilityContext ctx, final String itemId) {
        try {
            return ctx.world.createItem(itemId, 1);
        } catch (final Exception ignored) {
            return null;
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
