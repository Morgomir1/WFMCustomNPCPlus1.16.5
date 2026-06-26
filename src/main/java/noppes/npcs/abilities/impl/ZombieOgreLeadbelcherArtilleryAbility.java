package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.abilities.integration.WfmIntegration;
import noppes.npcs.api.NpcAPI;

import java.util.Map;
import java.util.Set;

/**
 * Зомби-огр-свинцеплюй: артиллерийский залп по позициям игроков (ядро свинцеплюя).
 * По умолчанию делает 2 выстрела небольшого радиуса с высоким уроном.
 */
public final class ZombieOgreLeadbelcherArtilleryAbility implements CnpcAbility {
    public static final String ID = "zombie_ogre_leadbelcher_artillery";
    private static final String DEFAULT_GUN_ITEM = "wfm:ogre_leadbelcher_gun";

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
        return AbilityDefaults.zombieOgreLeadbelcherArtillery();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.SHOTS,
                AbilityParamKeys.DISTANCE,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.ACCURACY,
                AbilityParamKeys.MAX_RANGE,
                AbilityParamKeys.PROJECTILE_ITEM,
                AbilityParamKeys.RADIUS);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        final double maxRange = ctx.params.getDouble(AbilityParamKeys.MAX_RANGE, 32.0);
        if (AbilityCombatHelper.distanceToTarget(ctx) > maxRange) {
            return false;
        }

        active.jumpStyle = false;
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 14);
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        faceTarget(active, ctx);

        // Фиксируем точку обстрела в момент старта (чтобы игрок не "уводил" залп).
        if (ctx.target != null) {
            final double tx = ctx.target.getX();
            final double tz = ctx.target.getZ();
            final double ty = AbilityCombatHelper.findGroundY(ctx.world, tx, tz, ctx.target.getY());
            active.ex = tx;
            active.ey = ty;
            active.ez = tz;
        }

        final String gunItemId = ctx.params.getString(AbilityParamKeys.PROJECTILE_ITEM, DEFAULT_GUN_ITEM);
        WfmIntegration.equipLeadbelcherForShot(ctx.npc, gunItemId);
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
        if (active.ticksLeft % 3 == 0) {
            AbilityVfx.spawnMuzzleFlash(ctx.world, ctx.npc.getX(), ctx.npc.getY() + 1.2, ctx.npc.getZ());
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 6);
        active.hitUuids.clear();
        AbilityVfx.spawnStartBurst(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), false);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.ravager.roar", 0.75F, 1.25F);
        return TickResult.CONTINUE;
    }

    private TickResult tickActive(final ActiveAbility active, final AbilityContext ctx) {
        final int total = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 6);
        if (active.ticksLeft <= 0) {
            return TickResult.FINISHED;
        }

        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);

        // Один залп, накрывающий область.
        if (!active.hitUuids.contains("barrage")) {
            fireBarrage(active, ctx);
            active.hitUuids.add("barrage");
        }

        active.ticksLeft--;
        return TickResult.CONTINUE;
    }

    private void fireBarrage(final ActiveAbility active, final AbilityContext ctx) {
        final String gunItemId = ctx.params.getString(AbilityParamKeys.PROJECTILE_ITEM, DEFAULT_GUN_ITEM);
        final int accuracy = ctx.params.getInt(AbilityParamKeys.ACCURACY, 3);
        final float inaccuracy = Math.max(0.05F, accuracy * 0.15F);
        final float damage = (float) ctx.params.getDouble(AbilityParamKeys.DAMAGE, 18.0);
        final int shots = Math.max(1, ctx.params.getInt(AbilityParamKeys.SHOTS, 2));
        final double stripWidth = Math.max(0.0, ctx.params.getDouble(AbilityParamKeys.RADIUS, 2.0));
        final double stripLength = Math.max(0.0, ctx.params.getDouble(AbilityParamKeys.DISTANCE, 10.0));
        final double tx = active.ex;
        final double tz = active.ez;
        final double ty = active.ey;

        AbilityVfx.spawnMuzzleFlash(ctx.world, ctx.npc.getX(), ctx.npc.getY() + 1.2, ctx.npc.getZ());

        // Полоса по направлению взгляда/цели: точки вдоль линии, в каждую точку "роняем сверху" 1 ядро.
        final double rad = (active.yaw + 90.0) * 0.0174532925;
        final double dirX = Math.cos(rad);
        final double dirZ = Math.sin(rad);
        final double sideX = -dirZ;
        final double sideZ = dirX;

        final double step = shots <= 1 ? 0.0 : (stripLength / (double) (shots - 1));
        final double start = -stripLength * 0.5;

        for (int i = 0; i < shots; i++) {
            final double offset = start + step * i;
            final double sideOffset = shots <= 1
                    ? 0.0
                    : (((i % 2 == 0) ? 1.0 : -1.0) * AbilityCombatHelper.random().nextDouble() * stripWidth * 0.5);
            final double cx = tx + dirX * offset + sideX * sideOffset;
            final double cz = tz + dirZ * offset + sideZ * sideOffset;
            final double cy = AbilityCombatHelper.findGroundY(ctx.world, cx, cz, ty);

            if (WfmIntegration.performLeadbelcherArtilleryStrike(
                    ctx.npc, cx, cy, cz, gunItemId, 1, 0.0, inaccuracy, damage)) {
                continue;
            }

            // Fallback без WFM: маленький взрыв AoE в точке полосы.
            AbilityVfx.spawnLandBurst(ctx.world, cx, cy, cz, true);
            ctx.world.playSoundAt(NpcAPI.Instance().getIPos(cx, cy, cz), "minecraft:entity.generic.explode", 0.9F, 1.0F);
            active.hitUuids.clear();
            AbilityCombatHelper.damageNearby(active, ctx, cx, cy + 0.5, cz, 2.6, damage, 0, 0, 0.0, 0.0, false);
        }
    }

    private static void faceTarget(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null) {
            return;
        }
        final double dx = ctx.target.getX() - ctx.npc.getX();
        final double dz = ctx.target.getZ() - ctx.npc.getZ();
        active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
        ctx.npc.setRotation(active.yaw);
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        WfmIntegration.restoreLeadbelcherEquipment(ctx.npc);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        WfmIntegration.restoreLeadbelcherEquipment(ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}

