package noppes.npcs.abilities.impl;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import noppes.npcs.abilities.*;
import noppes.npcs.abilities.integration.WfmIntegration;
import noppes.npcs.api.entity.data.INPCInventory;
import noppes.npcs.api.item.IItemStack;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Выстрел из пистолета (левая рука): charge с поднятой рукой → WFM BulletEntity + gun_launch.
 * Id оставлен {@code wh_flaming_crossbow} для совместимости со скриптами.
 */
public final class WhFlamingCrossbowAbility implements CnpcAbility {
    public static final String ID = "wh_flaming_crossbow";
    private static final String DEFAULT_PISTOL = "wfm:empire_pistol";
    private static final String DEFAULT_MELEE = "wfm:empire_witch_hunter_rapier";

    private static final ConcurrentHashMap<UUID, IItemStack> SAVED_LEFT = new ConcurrentHashMap<>();

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
        return AbilityDefaults.whFlamingCrossbow();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.DISTANCE,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.MAX_RANGE,
                AbilityParamKeys.ACCURACY,
                AbilityParamKeys.FIRE_SECONDS,
                AbilityParamKeys.RANGED_ITEM,
                AbilityParamKeys.MELEE_ITEM,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        final double maxRange = ctx.params.getDouble(AbilityParamKeys.MAX_RANGE, 24.0);
        if (ctx.target == null || !ctx.target.isAlive()) {
            return false;
        }
        if (AbilityCombatHelper.distanceToTarget(ctx) > maxRange) {
            return false;
        }

        active.jumpStyle = false;
        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY();
        active.sz = ctx.npc.getZ();

        final double dx = ctx.target.getX() - active.sx;
        final double dz = ctx.target.getZ() - active.sz;
        active.yaw = AbilityCombatHelper.computeYaw(dx, dz);

        final double dist = ctx.params.getDouble(AbilityParamKeys.DISTANCE, 18.0);
        final double len = Math.sqrt(dx * dx + dz * dz);
        final double nx = len > 0.05 ? dx / len : 0.0;
        final double nz = len > 0.05 ? dz / len : 1.0;
        active.ex = active.sx + nx * dist;
        active.ez = active.sz + nz * dist;
        active.ey = AbilityCombatHelper.findGroundY(ctx.world, active.ex, active.ez, active.sy);

        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 10);
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);

        equipPistolLeftHand(ctx);
        raiseLeftArm(ctx);
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
        raiseLeftArm(ctx);
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
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 10.0);
        final double halfWidth = ctx.params.getDouble(AbilityParamKeys.RADIUS, 0.7);
        final int fireSeconds = ctx.params.getInt(AbilityParamKeys.FIRE_SECONDS, 0);
        final int accuracy = ctx.params.getInt(AbilityParamKeys.ACCURACY, 3);
        final float inaccuracy = Math.max(0.05F, accuracy * 0.12F);
        final String gunId = ctx.params.getString(AbilityParamKeys.RANGED_ITEM, DEFAULT_PISTOL);

        final double ox = ctx.npc.getX();
        final double oy = ctx.npc.getY() + 1.0;
        final double oz = ctx.npc.getZ();

        // Точка прицела: живая цель (глаза), иначе конец telegraph на высоте глаз NPC
        final double aimX;
        final double aimY;
        final double aimZ;
        if (ctx.target != null && ctx.target.isAlive()) {
            aimX = ctx.target.getX();
            aimY = ctx.target.getY() + ctx.target.getEyeHeight() * 0.9;
            aimZ = ctx.target.getZ();
        } else {
            aimX = active.ex;
            aimY = ctx.npc.getY() + 1.4;
            aimZ = active.ez;
        }

        stopRaisingArm(ctx);
        swingLeftArm(ctx);
        AbilityVfx.spawnMuzzleFlash(ctx.world, ox, oy + 0.2, oz);

        // Сначала в цель (надёжнее), затем в точку telegraph
        boolean shot = false;
        if (ctx.target != null && ctx.target.isAlive()) {
            shot = WfmIntegration.performPistolShot(
                    ctx.npc, ctx.target, gunId, inaccuracy, (float) damage);
        }
        if (!shot) {
            shot = WfmIntegration.performPistolShotTowardPoint(
                    ctx.npc, aimX, aimY, aimZ, gunId, inaccuracy, (float) damage);
        }

        // Hitscan по коридору telegraph — основной урон (пуля может мимо / faction cancel)
        active.hitUuids.clear();
        AbilityCombatHelper.damageInCorridor(
                active, ctx,
                ox, oy, oz,
                aimX, aimY, aimZ,
                halfWidth, damage, 0.35, 0.08,
                fireSeconds, null, 0, 0);

        if (!shot) {
            ctx.world.playSoundAt(ctx.npc.getPos(), "wfm:item.gunpowder_gun_launch", 1.0F, 1.0F);
        }

        restoreLeftHand(ctx);
        return TickResult.FINISHED;
    }

    private void equipPistolLeftHand(final AbilityContext ctx) {
        final String gunId = ctx.params.getString(AbilityParamKeys.RANGED_ITEM, DEFAULT_PISTOL);
        WfmIntegration.equipPistolForShot(ctx.npc, gunId);
        try {
            final INPCInventory inv = ctx.npc.getInventory();
            if (inv == null) {
                return;
            }
            final UUID uuid = UUID.fromString(String.valueOf(ctx.npc.getUUID()));
            final IItemStack current = inv.getLeftHand();
            if (current != null && !current.isEmpty()) {
                SAVED_LEFT.put(uuid, current);
            }
            final IItemStack pistol = ctx.world.createItem(gunId, 1);
            if (pistol != null) {
                inv.setLeftHand(pistol);
            }
        } catch (final Exception ignored) {
        }
    }

    private void restoreLeftHand(final AbilityContext ctx) {
        stopRaisingArm(ctx);
        WfmIntegration.restorePistolEquipment(ctx.npc);
        try {
            final INPCInventory inv = ctx.npc.getInventory();
            if (inv == null) {
                return;
            }
            final UUID uuid = UUID.fromString(String.valueOf(ctx.npc.getUUID()));
            final IItemStack saved = SAVED_LEFT.remove(uuid);
            if (saved != null) {
                inv.setLeftHand(saved);
            }
            // Рапира остаётся в правой; на случай если WFM restore затронул main hand
            final String meleeId = ctx.params.getString(AbilityParamKeys.MELEE_ITEM, DEFAULT_MELEE);
            final IItemStack right = inv.getRightHand();
            if (right == null || right.isEmpty()) {
                final IItemStack melee = ctx.world.createItem(meleeId, 1);
                if (melee != null) {
                    inv.setRightHand(melee);
                }
            }
        } catch (final Exception ignored) {
        }
    }

    private static void raiseLeftArm(final AbilityContext ctx) {
        try {
            final Entity mc = ctx.npc.getMCEntity();
            if (!(mc instanceof LivingEntity)) {
                return;
            }
            final LivingEntity living = (LivingEntity) mc;
            if (living.getOffhandItem().isEmpty()) {
                return;
            }
            if (living.isUsingItem() && living.getUsedItemHand() == Hand.OFF_HAND) {
                return;
            }
            if (living.isUsingItem()) {
                living.stopUsingItem();
            }
            living.startUsingItem(Hand.OFF_HAND);
        } catch (final Exception ignored) {
        }
    }

    private static void stopRaisingArm(final AbilityContext ctx) {
        try {
            final Entity mc = ctx.npc.getMCEntity();
            if (mc instanceof LivingEntity && ((LivingEntity) mc).isUsingItem()) {
                ((LivingEntity) mc).stopUsingItem();
            }
        } catch (final Exception ignored) {
        }
    }

    private static void swingLeftArm(final AbilityContext ctx) {
        try {
            final Entity mc = ctx.npc.getMCEntity();
            if (mc instanceof LivingEntity) {
                ((LivingEntity) mc).swing(Hand.OFF_HAND, true);
            }
        } catch (final Exception ignored) {
        }
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        restoreLeftHand(ctx);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        restoreLeftHand(ctx);
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
