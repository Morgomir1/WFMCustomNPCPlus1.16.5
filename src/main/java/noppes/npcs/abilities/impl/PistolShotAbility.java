package noppes.npcs.abilities.impl;

import net.minecraft.entity.Entity;
import noppes.npcs.abilities.*;
import noppes.npcs.abilities.integration.WfmIntegration;
import noppes.npcs.api.entity.IProjectile;
import noppes.npcs.api.item.IItemStack;
import noppes.npcs.entity.EntityProjectile;

import java.util.Map;
import java.util.Set;

public final class PistolShotAbility implements CnpcAbility {
    public static final String ID = "pistol_shot";
    private static final String DEFAULT_GUN_ITEM = "wfm:empire_pistol";
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
    public Map<String, Object> defaultParams() {
        return AbilityDefaults.pistolShot();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.ACCURACY,
                AbilityParamKeys.MAX_RANGE,
                AbilityParamKeys.PROJECTILE_ITEM);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        final double maxRange = ctx.params.getDouble(AbilityParamKeys.MAX_RANGE, 24.0);
        if (AbilityCombatHelper.distanceToTarget(ctx) > maxRange) {
            return false;
        }
        active.jumpStyle = false;
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 8);
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        if (ctx.target != null) {
            final double dx = ctx.target.getX() - ctx.npc.getX();
            final double dz = ctx.target.getZ() - ctx.npc.getZ();
            active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
            ctx.npc.setRotation(active.yaw);
        }

        final String gunItemId = ctx.params.getString(AbilityParamKeys.PROJECTILE_ITEM, DEFAULT_GUN_ITEM);
        WfmIntegration.equipPistolForShot(ctx.npc, gunItemId);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return fireShot(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 3 == 0) {
            AbilityVfx.spawnMuzzleFlash(
                    ctx.world,
                    ctx.npc.getX(),
                    ctx.npc.getY() + 1.2,
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

    private TickResult fireShot(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return TickResult.FINISHED;
        }

        final String gunItemId = ctx.params.getString(AbilityParamKeys.PROJECTILE_ITEM, DEFAULT_GUN_ITEM);
        final int accuracy = ctx.params.getInt(AbilityParamKeys.ACCURACY, 4);
        final float inaccuracy = Math.max(0.05F, accuracy * 0.15F);
        final float damage = (float) ctx.params.getDouble(AbilityParamKeys.DAMAGE, 9.0);

        AbilityVfx.spawnMuzzleFlash(
                ctx.world,
                ctx.npc.getX(),
                ctx.npc.getY() + 1.2,
                ctx.npc.getZ());

        if (WfmIntegration.performPistolShot(ctx.npc, ctx.target, gunItemId, inaccuracy, damage)) {
            return TickResult.FINISHED;
        }

        return fireFallbackShot(ctx, accuracy, damage);
    }

    private TickResult fireFallbackShot(
            final AbilityContext ctx,
            final int accuracy,
            final float damage) {
        IItemStack item = createProjectileItem(ctx, FALLBACK_PROJECTILE);
        if (item == null) {
            return TickResult.FINISHED;
        }

        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.generic.explode", 0.5F, 1.8F);
        try {
            final IProjectile projectile = ctx.npc.shootItem(ctx.target, item, accuracy);
            configureProjectile(projectile, damage);
        } catch (final Exception ignored) {
        }
        return TickResult.FINISHED;
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
