package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;

import java.util.Map;
import java.util.Set;

public final class HolyWaterSplashAbility implements CnpcAbility {
    public static final String ID = "holy_water_splash";

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
        return AbilityDefaults.holyWaterSplash();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.CONE_HALF_ANGLE,
                AbilityParamKeys.EFFECT_DURATION,
                AbilityParamKeys.EFFECT_AMPLIFIER,
                AbilityParamKeys.UNDEAD_BONUS_MULTIPLIER,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = false;
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 14);
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        if (ctx.target != null) {
            final double dx = ctx.target.getX() - ctx.npc.getX();
            final double dz = ctx.target.getZ() - ctx.npc.getZ();
            active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
            ctx.npc.setRotation(active.yaw);
        }
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:item.bottle.fill", 0.9F, 0.7F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickSplash(active, ctx);
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
                    false);
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = 1;
        active.hitUuids.clear();
        return TickResult.CONTINUE;
    }

    private TickResult tickSplash(final ActiveAbility active, final AbilityContext ctx) {
        final double x = ctx.npc.getX();
        final double y = ctx.npc.getY();
        final double z = ctx.npc.getZ();
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 4.0);
        final double halfAngle = ctx.params.getDouble(AbilityParamKeys.CONE_HALF_ANGLE, 30.0);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 8.0);
        final double undeadBonus = ctx.params.getDouble(AbilityParamKeys.UNDEAD_BONUS_MULTIPLIER, 2.0);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 0.8);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.2);
        final int effectDuration = ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 100);
        final int effectAmplifier = ctx.params.getInt(AbilityParamKeys.EFFECT_AMPLIFIER, 0);

        AbilityVfx.spawnHolySplash(ctx.world, x, y + 1.0, z);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.generic.splash", 1.0F, 0.9F);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:block.fire.extinguish", 0.8F, 1.2F);

        AbilityCombatHelper.damageInConeWithUndeadBonus(
                active, ctx, x, y + 1.0, z,
                radius, halfAngle, damage, undeadBonus, knockback, knockbackY);
        AbilityCombatHelper.applyPotionInCone(
                ctx, x, y + 1.0, z,
                radius, halfAngle,
                AbilityEffectType.WEAKNESS, effectDuration, effectAmplifier);
        AbilityCombatHelper.applyPotionInCone(
                ctx, x, y + 1.0, z,
                radius, halfAngle,
                AbilityEffectType.SLOWNESS, effectDuration, effectAmplifier);

        return TickResult.FINISHED;
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
