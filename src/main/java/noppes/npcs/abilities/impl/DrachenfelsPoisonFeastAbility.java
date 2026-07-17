package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;

import java.util.Map;
import java.util.Set;

public final class DrachenfelsPoisonFeastAbility implements CnpcAbility {
    public static final String ID = "drachenfels_poison_feast";

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
        return AbilityDefaults.drachenfelsPoisonFeast();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y,
                AbilityParamKeys.EFFECT_TYPE,
                AbilityParamKeys.EFFECT_DURATION,
                AbilityParamKeys.EFFECT_AMPLIFIER);
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
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:block.brewing_stand.brew", 0.9F, 0.7F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return doFeast(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 3 == 0) {
            AbilityVfx.spawnDarkCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
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

    private TickResult doFeast(final ActiveAbility active, final AbilityContext ctx) {
        final double x = ctx.npc.getX();
        final double y = ctx.npc.getY();
        final double z = ctx.npc.getZ();
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 5.0);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 14.0);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 0.9);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.25);

        final AbilityEffectType effectType = AbilityEffectType.fromString(
                ctx.params.getString(AbilityParamKeys.EFFECT_TYPE, "poison"));
        final int duration = ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 80);
        final int amplifier = ctx.params.getInt(AbilityParamKeys.EFFECT_AMPLIFIER, 1);

        AbilityVfx.spawnPoisonFeastBurst(ctx.world, x, y + 0.2, z, radius);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(x, y, z),
                "minecraft:entity.witch.drink",
                1.0F,
                0.75F);

        active.hitUuids.clear();
        AbilityCombatHelper.damageNearby(
                active, ctx, x, y + 0.5, z,
                radius, damage, 0, 0, knockback, knockbackY, false);
        AbilityCombatHelper.applyPotionNearby(
                active, ctx, x, y + 0.5, z,
                radius, effectType, duration, amplifier);

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
