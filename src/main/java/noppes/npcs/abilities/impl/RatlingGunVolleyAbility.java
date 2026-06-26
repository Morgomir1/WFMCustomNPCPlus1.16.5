package noppes.npcs.abilities.impl;

import net.minecraft.entity.Entity;
import noppes.npcs.abilities.*;
import noppes.npcs.abilities.integration.WfmIntegration;
import noppes.npcs.api.entity.IProjectile;
import noppes.npcs.api.item.IItemStack;
import noppes.npcs.entity.EntityProjectile;

import java.util.Map;
import java.util.Set;

/**
 * Залп из ратлинг-гана: серия выстрелов {@link wfm.common.entity.projectile.SkavenBulletEntity}
 * по цели (как {@code RatlingGunEntity.RatlingGunSpellGoal}).
 */
public final class RatlingGunVolleyAbility implements CnpcAbility {
    public static final String ID = "ratling_gun_volley";
    private static final String DEFAULT_GUN_ITEM = "wfm:skaven_ratling_gun";
    private static final String FALLBACK_PROJECTILE = "minecraft:iron_nugget";

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
        return AbilityDefaults.ratlingGunVolley();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.FIRST_SHOT_TICK,
                AbilityParamKeys.SHOT_INTERVAL,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.ACCURACY,
                AbilityParamKeys.MAX_RANGE,
                AbilityParamKeys.BULLET_VELOCITY,
                AbilityParamKeys.PROJECTILE_ITEM);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        final double maxRange = ctx.params.getDouble(AbilityParamKeys.MAX_RANGE, 36.0);
        if (AbilityCombatHelper.distanceToTarget(ctx) > maxRange) {
            return false;
        }

        active.jumpStyle = false;
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 60));
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        faceTarget(active, ctx);

        final String gunItemId = ctx.params.getString(AbilityParamKeys.PROJECTILE_ITEM, DEFAULT_GUN_ITEM);
        WfmIntegration.equipRatlingGunForShot(ctx.npc, gunItemId);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase != ActiveAbility.PHASE_ACTIVE) {
            return TickResult.FINISHED;
        }

        AbilityCombatHelper.stopNavigation(ctx.npc);
        faceTarget(active, ctx);

        final int duration = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 60));
        final int burstTick = duration - active.ticksLeft;
        if (shouldFireShot(ctx, burstTick)) {
            fireShot(active, ctx);
        }

        active.ticksLeft--;
        return active.ticksLeft > 0 ? TickResult.CONTINUE : TickResult.FINISHED;
    }

    private static boolean shouldFireShot(final AbilityContext ctx, final int burstTick) {
        final int firstShot = Math.max(0, ctx.params.getInt(AbilityParamKeys.FIRST_SHOT_TICK, 3));
        final int interval = Math.max(1, ctx.params.getInt(AbilityParamKeys.SHOT_INTERVAL, 5));
        if (burstTick < firstShot) {
            return false;
        }
        return (burstTick - firstShot) % interval == 0;
    }

    private void fireShot(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return;
        }

        final int accuracy = ctx.params.getInt(AbilityParamKeys.ACCURACY, 6);
        final float inaccuracy = Math.max(0.05F, accuracy * 0.15F);
        final float damage = (float) ctx.params.getDouble(AbilityParamKeys.DAMAGE, 8.0);
        final float velocity = (float) ctx.params.getDouble(AbilityParamKeys.BULLET_VELOCITY, 6.0);

        AbilityVfx.spawnMuzzleFlash(
                ctx.world,
                ctx.npc.getX(),
                ctx.npc.getY() + 1.2,
                ctx.npc.getZ());

        if (WfmIntegration.performRatlingShot(ctx.npc, ctx.target, inaccuracy, damage, velocity)) {
            return;
        }

        fireFallbackShot(ctx, accuracy, damage);
    }

    private void fireFallbackShot(
            final AbilityContext ctx,
            final int accuracy,
            final float damage) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return;
        }
        IItemStack item = createProjectileItem(ctx, FALLBACK_PROJECTILE);
        if (item == null) {
            return;
        }

        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.generic.explode", 0.5F, 1.8F);
        try {
            final IProjectile projectile = ctx.npc.shootItem(ctx.target, item, accuracy);
            configureProjectile(projectile, damage);
        } catch (final Exception ignored) {
        }
    }

    private static IItemStack createProjectileItem(final AbilityContext ctx, final String itemId) {
        try {
            return ctx.world.createItem(itemId, 1);
        } catch (final Exception ignored) {
            return null;
        }
    }

    private static void configureProjectile(final IProjectile projectile, final double damage) {
        if (projectile == null) {
            return;
        }
        try {
            final Entity mc = projectile.getMCEntity();
            if (mc instanceof EntityProjectile) {
                final EntityProjectile entityProjectile = (EntityProjectile) mc;
                entityProjectile.damage = (float) damage;
                entityProjectile.explosiveDamage = false;
            }
        } catch (final Exception ignored) {
        }
    }

    private static void faceTarget(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return;
        }
        final double dx = ctx.target.getX() - ctx.npc.getX();
        final double dz = ctx.target.getZ() - ctx.npc.getZ();
        active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
        ctx.npc.setRotation(active.yaw);
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        WfmIntegration.restorePistolEquipment(ctx.npc);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        WfmIntegration.restorePistolEquipment(ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
