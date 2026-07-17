package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;

import java.util.Map;
import java.util.Set;

public final class DrachenfelsSoulRendAbility implements CnpcAbility {
    public static final String ID = "drachenfels_soul_rend";

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
        return AbilityDefaults.drachenfelsSoulRend();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.CONE_HALF_ANGLE,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y,
                AbilityParamKeys.EFFECT_TYPE,
                AbilityParamKeys.EFFECT_DURATION,
                AbilityParamKeys.EFFECT_AMPLIFIER);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = true;
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 12);
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        if (ctx.target != null) {
            final double dx = ctx.target.getX() - ctx.npc.getX();
            final double dz = ctx.target.getZ() - ctx.npc.getZ();
            active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
            ctx.npc.setRotation(active.yaw);
        }
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.vex.ambient", 0.95F, 0.6F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return doRend(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        if (ctx.target != null && ctx.target.isAlive()) {
            final double dx = ctx.target.getX() - ctx.npc.getX();
            final double dz = ctx.target.getZ() - ctx.npc.getZ();
            active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
        }
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 3 == 0) {
            AbilityVfx.spawnSoulCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
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

    private TickResult doRend(final ActiveAbility active, final AbilityContext ctx) {
        final double x = ctx.npc.getX();
        final double y = ctx.npc.getY();
        final double z = ctx.npc.getZ();
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 6.0);
        final double halfAngle = ctx.params.getDouble(AbilityParamKeys.CONE_HALF_ANGLE, 40.0);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 12.0);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 0.7);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.2);

        final AbilityEffectType effectType = AbilityEffectType.fromString(
                ctx.params.getString(AbilityParamKeys.EFFECT_TYPE, "wither"));
        final int duration = ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 60);
        final int amplifier = ctx.params.getInt(AbilityParamKeys.EFFECT_AMPLIFIER, 0);

        AbilityVfx.spawnSoulBurst(ctx.world, x, y + 0.3, z, Math.min(radius, 4.5));
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(x, y, z),
                "minecraft:entity.wither.shoot",
                0.8F,
                1.4F);

        active.hitUuids.clear();
        AbilityCombatHelper.damageInConeWithUndeadBonus(
                active, ctx, x, y + 0.8, z,
                radius, halfAngle, damage, 1.0, knockback, knockbackY);
        AbilityCombatHelper.applyPotionInCone(
                ctx, x, y + 0.8, z,
                radius, halfAngle, effectType, duration, amplifier);

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
