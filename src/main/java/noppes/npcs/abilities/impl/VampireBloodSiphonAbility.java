package noppes.npcs.abilities.impl;

import net.minecraft.entity.LivingEntity;
import noppes.npcs.abilities.*;

import java.util.Map;
import java.util.Set;

public final class VampireBloodSiphonAbility implements CnpcAbility {
    public static final String ID = "vampire_blood_siphon";

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
        return AbilityDefaults.vampireBloodSiphon();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.DAMAGE_PER_TICK,
                AbilityParamKeys.HEAL_PER_TICK,
                AbilityParamKeys.MAX_RANGE);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
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
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.bat.takeoff", 0.9F, 0.8F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickSiphon(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 3 == 0) {
            AbilityVfx.spawnBloodCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 40);
        active.hitUuids.clear();
        AbilityVfx.spawnBloodBurst(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), 1.4);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.husk.ambient", 0.75F, 0.9F);
        return TickResult.CONTINUE;
    }

    private TickResult tickSiphon(final ActiveAbility active, final AbilityContext ctx) {
        if (active.ticksLeft <= 0) {
            return TickResult.FINISHED;
        }
        if (ctx.target == null || !ctx.target.isAlive()) {
            return TickResult.FINISHED;
        }

        final double maxRange = ctx.params.getDouble(AbilityParamKeys.MAX_RANGE, 6.0);
        final double dist = AbilityCombatHelper.flatDistance(
                ctx.npc.getX(), ctx.npc.getZ(),
                ctx.target.getX(), ctx.target.getZ());
        if (dist > maxRange) {
            return TickResult.FINISHED;
        }

        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE_PER_TICK, 0.8);
        final double heal = ctx.params.getDouble(AbilityParamKeys.HEAL_PER_TICK, 0.6);

        ctx.target.damage((float) damage);
        healCaster(ctx, heal);
        AbilityVfx.spawnBloodBurst(ctx.world, ctx.target.getX(), ctx.target.getY() + 0.6, ctx.target.getZ(), 0.8);

        active.ticksLeft--;
        return TickResult.CONTINUE;
    }

    private void healCaster(final AbilityContext ctx, final double amount) {
        if (amount <= 0.0) {
            return;
        }
        try {
            final Object mc = ctx.npc.getMCEntity();
            if (mc instanceof LivingEntity) {
                final LivingEntity living = (LivingEntity) mc;
                final float healed = Math.min(living.getMaxHealth(), living.getHealth() + (float) amount);
                living.setHealth(healed);
            }
        } catch (final Exception ignored) {
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
