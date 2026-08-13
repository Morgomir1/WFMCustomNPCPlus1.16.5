package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;

import java.util.Map;
import java.util.Set;

/**
 * Circular slash around the caster. Charge 0.4s with circle telegraph,
 * BipedModel yaw-spin (harvest-like), then AoE + heal per hit target.
 */
public final class VampireWhirlSlashAbility implements CnpcAbility {
    public static final String ID = "vampire_whirl_slash";
    private static final float SPIN_DEG_PER_TICK = 100.0F;

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
        return AbilityDefaults.vampireWhirlSlash();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y,
                AbilityParamKeys.LIFE_STEAL_PER_HIT);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = false;
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 8);
        active.hitUuids.clear();
        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        if (ctx.target != null) {
            final double dx = ctx.target.getX() - ctx.npc.getX();
            final double dz = ctx.target.getZ() - ctx.npc.getZ();
            active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
            ctx.npc.setRotation(active.yaw);
        } else {
            active.yaw = ctx.npc.getRotation();
        }
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.player.attack.sweep", 0.95F, 0.7F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return doSlash(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        AbilityCombatHelper.zeroHorizontalMotion(ctx.npc);
        spinYaw(active, ctx);
        if (active.ticksLeft % 2 == 0) {
            AbilityVfx.spawnBloodCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
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

    private TickResult doSlash(final ActiveAbility active, final AbilityContext ctx) {
        spinYaw(active, ctx);
        final double x = ctx.npc.getX();
        final double y = ctx.npc.getY();
        final double z = ctx.npc.getZ();
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 4.5);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 18.0);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 1.2);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.3);
        final double lifeSteal = ctx.params.getDouble(AbilityParamKeys.LIFE_STEAL_PER_HIT, 100.0);

        AbilityVfx.spawnBloodBurst(ctx.world, x, y + 0.2, z, radius);
        AbilityVfx.spawnFeastBloodBurst(ctx.world, x, y + 0.6, z, 8);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(x, y, z),
                "minecraft:entity.player.attack.sweep",
                1.1F,
                0.85F);

        active.hitUuids.clear();
        AbilityCombatHelper.damageNearby(
                active, ctx, x, y + 0.5, z,
                radius, damage, 0, 0, knockback, knockbackY, false);
        if (lifeSteal > 0.0 && !active.hitUuids.isEmpty()) {
            AbilityCombatHelper.healCaster(ctx, lifeSteal * active.hitUuids.size());
        }
        return TickResult.FINISHED;
    }

    private static void spinYaw(final ActiveAbility active, final AbilityContext ctx) {
        active.yaw = ctx.npc.getRotation() + SPIN_DEG_PER_TICK;
        ctx.npc.setRotation(active.yaw);
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
