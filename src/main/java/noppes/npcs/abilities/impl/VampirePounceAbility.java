package noppes.npcs.abilities.impl;

import net.minecraft.entity.LivingEntity;
import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;

import java.util.Map;
import java.util.Set;

public final class VampirePounceAbility implements CnpcAbility {
    public static final String ID = "vampire_pounce";

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
        return AbilityDefaults.vampirePounce();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y,
                AbilityParamKeys.LAND_RADIUS,
                AbilityParamKeys.ARC_HEIGHT,
                AbilityParamKeys.LIFE_STEAL_PER_HIT);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = true;
        if (!AbilityCombatHelper.computeEndPoints(active, ctx, false)) {
            return false;
        }
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 10);
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:block.beacon.power_select", 0.9F, 0.8F);
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
        if (active.ticksLeft % 4 == 0) {
            AbilityVfx.spawnBloodCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 9);
        active.hitUuids.clear();
        AbilityVfx.spawnBloodBurst(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), 1.6);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.phantom.flap", 0.8F, 1.0F);
        return TickResult.CONTINUE;
    }

    private TickResult tickActive(final ActiveAbility active, final AbilityContext ctx) {
        final int total = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 9);
        if (active.ticksLeft <= 0) {
            ctx.npc.setPosition(active.ex, active.ey, active.ez);
            ctx.npc.setRotation(active.yaw);
            doLanding(active, ctx);
            return TickResult.FINISHED;
        }

        final double t = 1.0 - (active.ticksLeft - 1) / (double) total;
        final double cx = active.sx + (active.ex - active.sx) * t;
        final double cz = active.sz + (active.ez - active.sz) * t;
        final double baseY = active.sy + (active.ey - active.sy) * t;
        final double arcHeight = ctx.params.getDouble(AbilityParamKeys.ARC_HEIGHT, 7.0);
        final double cy = baseY + Math.sin(t * Math.PI) * arcHeight;

        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setPosition(cx, cy, cz);
        ctx.npc.setRotation(active.yaw);
        AbilityVfx.spawnJumpTrail(ctx.world, cx, cy, cz);

        active.ticksLeft--;
        return TickResult.CONTINUE;
    }

    private void doLanding(final ActiveAbility active, final AbilityContext ctx) {
        final double x = active.ex;
        final double y = active.ey;
        final double z = active.ez;
        AbilityVfx.spawnBloodBurst(ctx.world, x, y, z, 2.4);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(x, y, z),
                "minecraft:entity.ravager.attack",
                0.85F,
                0.9F);

        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 12.0);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 1.4);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.35);
        final double landRadius = ctx.params.getDouble(AbilityParamKeys.LAND_RADIUS, 2.6);
        final double lifeSteal = ctx.params.getDouble(AbilityParamKeys.LIFE_STEAL_PER_HIT, 1.5);

        active.hitUuids.clear();
        AbilityCombatHelper.damageNearby(
                active, ctx, x, y + 0.5, z,
                landRadius, damage, 0, 0, knockback, knockbackY, false);
        if (lifeSteal > 0.0) {
            healCaster(ctx, lifeSteal * active.hitUuids.size());
        }
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
