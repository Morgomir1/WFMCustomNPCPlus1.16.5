package noppes.npcs.abilities.impl;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import noppes.npcs.abilities.AbilityCombatHelper;
import noppes.npcs.abilities.AbilityContext;
import noppes.npcs.abilities.AbilityDefaults;
import noppes.npcs.abilities.AbilityParamKeys;
import noppes.npcs.abilities.AbilityParams;
import noppes.npcs.abilities.ActiveAbility;
import noppes.npcs.abilities.CnpcAbility;
import noppes.npcs.abilities.TickResult;
import noppes.npcs.abilities.integration.WfmIntegration;
import noppes.npcs.api.entity.IEntityLiving;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Метание топора: telegraph → визуал WFM Axe Throw → урон по пути туда.
 * Расширяемая абилка (range, timing, item, damagePercent).
 */
public final class AxeThrowAbility implements CnpcAbility {
    public static final String ID = "axe_throw";

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
        return AbilityDefaults.axeThrow();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.MAX_RANGE,
                AbilityParamKeys.DISTANCE,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.OUTBOUND_TICKS,
                AbilityParamKeys.HOLD_TICKS,
                AbilityParamKeys.RETURN_TICKS,
                AbilityParamKeys.ARC_HEIGHT,
                AbilityParamKeys.HIT_RADIUS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.DAMAGE_PERCENT,
                AbilityParamKeys.EFFECT_DURATION,
                AbilityParamKeys.EFFECT_AMPLIFIER,
                AbilityParamKeys.PROJECTILE_ITEM,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return false;
        }
        aim(active, ctx);
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 8));
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        freeze(ctx);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:item.trident.throw", 1.2F, 0.65F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        freeze(ctx);
        ctx.npc.setRotation(active.yaw);
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickFlight(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target != null && ctx.target.isAlive()) {
            aim(active, ctx);
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        final double range = Math.max(4.0, ctx.params.getDouble(AbilityParamKeys.MAX_RANGE, 16.0));
        final double rad = Math.toRadians(active.yaw + 90.0);
        final double dirX = Math.cos(rad);
        final double dirZ = Math.sin(rad);
        final int outbound = Math.max(6, ctx.params.getInt(AbilityParamKeys.OUTBOUND_TICKS, 16));
        final int hold = Math.max(0, ctx.params.getInt(AbilityParamKeys.HOLD_TICKS, 4));
        final int ret = Math.max(4, ctx.params.getInt(AbilityParamKeys.RETURN_TICKS, 10));
        final String itemId = ctx.params.getString(
                AbilityParamKeys.PROJECTILE_ITEM, "wfm:beastmen_gor_battleaxe");
        final float arc = (float) Math.max(0.0, ctx.params.getDouble(AbilityParamKeys.ARC_HEIGHT, 3.5));
        WfmIntegration.startAxeThrowVisual(
                ctx.npc, itemId, dirX, 0.05, dirZ, (float) range, outbound, hold, ret, arc);
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = outbound + hold + ret;
        return TickResult.CONTINUE;
    }

    private TickResult tickFlight(final ActiveAbility active, final AbilityContext ctx) {
        final int outbound = Math.max(6, ctx.params.getInt(AbilityParamKeys.OUTBOUND_TICKS, 16));
        final int hold = Math.max(0, ctx.params.getInt(AbilityParamKeys.HOLD_TICKS, 4));
        final int ret = Math.max(4, ctx.params.getInt(AbilityParamKeys.RETURN_TICKS, 10));
        final int total = outbound + hold + ret;
        final int elapsed = total - active.ticksLeft;
        if (elapsed >= 0 && elapsed <= outbound) {
            damageAlongPath(active, ctx, elapsed, outbound);
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        WfmIntegration.stopAxeThrowVisual(ctx.npc);
        return TickResult.FINISHED;
    }

    private void damageAlongPath(
            final ActiveAbility active,
            final AbilityContext ctx,
            final int elapsed,
            final int outbound) {
        final LivingEntity thrower = toLiving(ctx.npc);
        if (thrower == null) {
            return;
        }
        final double range = Math.max(4.0, ctx.params.getDouble(AbilityParamKeys.MAX_RANGE, 16.0));
        final double t = outbound <= 0 ? 1.0 : elapsed / (double) outbound;
        final double rad = Math.toRadians(active.yaw + 90.0);
        final double dirX = Math.cos(rad);
        final double dirZ = Math.sin(rad);
        final double arc = Math.max(0.0, ctx.params.getDouble(AbilityParamKeys.ARC_HEIGHT, 3.5));
        final Vector3d start = new Vector3d(active.sx, active.sy, active.sz);
        // Та же парабола, что у AxeThrowRenderer: y += arc * 4 * t * (1-t)
        final Vector3d pos = start.add(
                dirX * range * t,
                arc * 4.0 * t * (1.0 - t),
                dirZ * range * t);
        final double hitR = Math.max(0.6, ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 1.2));
        final AxisAlignedBB box = new AxisAlignedBB(
                pos.x - hitR, pos.y - hitR, pos.z - hitR,
                pos.x + hitR, pos.y + hitR, pos.z + hitR);
        final List<LivingEntity> list = thrower.level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                living -> living != null
                        && living.isAlive()
                        && living != thrower
                        && living instanceof PlayerEntity);
        final double pct = ctx.params.getDouble(AbilityParamKeys.DAMAGE_PERCENT, 0.15);
        final float flat = (float) ctx.params.getDouble(AbilityParamKeys.DAMAGE, 0.0);
        final int duration = Math.max(1, ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 60));
        final int amplifier = Math.max(0, ctx.params.getInt(AbilityParamKeys.EFFECT_AMPLIFIER, 1));
        for (final LivingEntity living : list) {
            final String id = living.getStringUUID();
            if (active.hitUuids.contains(id)) {
                continue;
            }
            active.hitUuids.add(id);
            if (pct > 0.001) {
                final float max = living.getMaxHealth();
                final float amount = max * (float) pct;
                if (living.getHealth() - amount < 1.0F) {
                    living.setHealth(1.0F);
                } else {
                    living.setHealth(living.getHealth() - amount);
                }
            } else if (flat > 0.0F) {
                AbilityCombatHelper.dealPureDamage(living, flat);
            }
            living.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN, duration, amplifier));
            living.level.playSound(
                    null,
                    living.getX(),
                    living.getY(),
                    living.getZ(),
                    SoundEvents.PLAYER_ATTACK_STRONG,
                    SoundCategory.HOSTILE,
                    1.0F,
                    0.8F);
        }
    }

    private void aim(final ActiveAbility active, final AbilityContext ctx) {
        final IEntityLiving target = ctx.target;
        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY() + 1.2;
        active.sz = ctx.npc.getZ();
        active.ex = target.getX();
        active.ez = target.getZ();
        active.ey = AbilityCombatHelper.findGroundY(ctx.world, active.ex, active.ez, target.getY());
        active.yaw = AbilityCombatHelper.computeYaw(active.ex - active.sx, active.ez - active.sz);
        final double range = Math.max(4.0, ctx.params.getDouble(AbilityParamKeys.MAX_RANGE, 16.0));
        final double dx = active.ex - active.sx;
        final double dz = active.ez - active.sz;
        final double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 0.2) {
            active.ex = active.sx + dx / len * range;
            active.ez = active.sz + dz / len * range;
            active.ey = AbilityCombatHelper.findGroundY(ctx.world, active.ex, active.ez, active.ey);
        }
    }

    private void freeze(final AbilityContext ctx) {
        try {
            ctx.npc.getAi().setWalkingSpeed(0);
            ctx.npc.getAi().setRetaliateType(3);
        } catch (final Exception ignored) {
        }
        final Entity mc = ctx.npc.getMCEntity();
        if (mc != null) {
            mc.setDeltaMovement(0.0, mc.getDeltaMovement().y, 0.0);
            mc.hasImpulse = true;
        }
    }

    private static LivingEntity toLiving(final IEntityLiving entity) {
        try {
            final Object mc = entity.getMCEntity();
            if (mc instanceof LivingEntity) {
                return (LivingEntity) mc;
            }
        } catch (final Exception ignored) {
        }
        return null;
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        WfmIntegration.stopAxeThrowVisual(ctx.npc);
        release(ctx);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        WfmIntegration.stopAxeThrowVisual(ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        release(ctx);
    }

    private void release(final AbilityContext ctx) {
        try {
            int speed = 4;
            final Object raw = ctx.npc.getStoreddata().get("base_speed");
            if (raw != null) {
                final int parsed = (int) Double.parseDouble(String.valueOf(raw));
                if (parsed > 0) {
                    speed = parsed;
                }
            }
            ctx.npc.getAi().setWalkingSpeed(speed);
            ctx.npc.getAi().setRetaliateType(0);
        } catch (final Exception ignored) {
        }
    }
}
