package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;

import java.util.Map;
import java.util.Set;

public final class BurningBrandAbility implements CnpcAbility {
    public static final String ID = "burning_brand";

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
        return AbilityDefaults.burningBrand();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.DAMAGE_PER_TICK,
                AbilityParamKeys.AURA_RADIUS,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = true;
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
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:item.flintandsteel.use", 0.9F, 0.9F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickAura(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 3 == 0) {
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
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 12);
        active.hitUuids.clear();
        AbilityVfx.spawnStartBurst(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), true);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:item.firecharge.use", 0.8F, 1.0F);
        return TickResult.CONTINUE;
    }

    private TickResult tickAura(final ActiveAbility active, final AbilityContext ctx) {
        if (active.ticksLeft <= 0) {
            return TickResult.FINISHED;
        }

        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);

        final double x = ctx.npc.getX();
        final double y = ctx.npc.getY();
        final double z = ctx.npc.getZ();
        final double auraRadius = ctx.params.getDouble(AbilityParamKeys.AURA_RADIUS, 3.5);
        final double damagePerTick = ctx.params.getDouble(AbilityParamKeys.DAMAGE_PER_TICK, 3.0);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 0.5);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.1);

        active.hitUuids.clear();
        AbilityCombatHelper.damageNearby(
                active, ctx, x, y + 0.5, z,
                auraRadius, damagePerTick, 0, 0, knockback, knockbackY, false);
        AbilityVfx.spawnFireRing(ctx.world, x, y, z, auraRadius);

        active.ticksLeft--;
        return TickResult.CONTINUE;
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        AbilityVfx.spawnLandBurst(
                ctx.world,
                ctx.npc.getX(),
                ctx.npc.getY(),
                ctx.npc.getZ(),
                true);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:block.fire.ambient", 0.7F, 0.8F);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
