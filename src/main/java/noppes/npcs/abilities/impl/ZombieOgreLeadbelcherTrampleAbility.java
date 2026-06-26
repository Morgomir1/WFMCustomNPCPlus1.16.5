package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;

import java.util.Map;
import java.util.Set;

/**
 * Зомби-огр-свинцеплюй: бежит вперёд, распинывая всех по пути (урон небольшой + откид + слепота).
 */
public final class ZombieOgreLeadbelcherTrampleAbility implements CnpcAbility {
    public static final String ID = "zombie_ogre_leadbelcher_trample";

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
        return AbilityDefaults.zombieOgreLeadbelcherTrample();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.DISTANCE,
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y,
                AbilityParamKeys.HIT_RADIUS,
                AbilityParamKeys.EFFECT_TYPE,
                AbilityParamKeys.EFFECT_DURATION,
                AbilityParamKeys.EFFECT_AMPLIFIER);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = false;
        if (!AbilityCombatHelper.computeDashEndPoints(active, ctx)) {
            return false;
        }
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 10);
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
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

    @Override
    public void onEnd(ActiveAbility active, AbilityContext ctx) {

    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 4 == 0) {
            AbilityVfx.spawnChargeParticles(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), false);
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 12);
        AbilityVfx.spawnStartBurst(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), false);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.ravager.step", 0.9F, 0.85F);
        return TickResult.CONTINUE;
    }

    private TickResult tickActive(final ActiveAbility active, final AbilityContext ctx) {
        final int total = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 12);
        if (active.ticksLeft <= 0) {
            finish(active, ctx);
            return TickResult.FINISHED;
        }

        final double progress = 1.0 - (active.ticksLeft - 1) / (double) total;
        final double cx = active.sx + (active.ex - active.sx) * progress;
        final double cz = active.sz + (active.ez - active.sz) * progress;
        final double cy = AbilityCombatHelper.findGroundY(ctx.world, cx, cz, active.sy);

        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setPosition(cx, cy, cz);
        ctx.npc.setRotation(active.yaw);

        final double rad = (active.yaw + 90.0) * 0.0174532925;
        final double knockDirX = Math.cos(rad);
        final double knockDirZ = Math.sin(rad);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 4.0);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 1.15);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.25);
        final double hitRadius = ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 1.9);

        final AbilityEffectType effectType = AbilityEffectType.fromString(
                ctx.params.getString(AbilityParamKeys.EFFECT_TYPE, "blindness"));
        final int duration = ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 30);
        final int amplifier = ctx.params.getInt(AbilityParamKeys.EFFECT_AMPLIFIER, 0);

        AbilityCombatHelper.damageNearby(
                active, ctx, cx, cy + 1.0, cz,
                hitRadius, damage, knockDirX, knockDirZ, knockback, knockbackY, true);
        AbilityCombatHelper.applyPotionNearby(
                active, ctx, cx, cy + 1.0, cz,
                hitRadius, effectType, duration, amplifier);

        AbilityVfx.spawnDashTrail(ctx.world, cx, cy, cz);
        if (active.ticksLeft % 4 == 0) {
            ctx.world.playSoundAt(NpcAPI.Instance().getIPos(cx, cy, cz), "minecraft:entity.iron_golem.step", 0.75F, 0.7F);
        }

        active.ticksLeft--;
        return TickResult.CONTINUE;
    }

    private void finish(final ActiveAbility active, final AbilityContext ctx) {
        final double ey = AbilityCombatHelper.findGroundY(ctx.world, active.ex, active.ez, active.sy);
        ctx.npc.setPosition(active.ex, ey, active.ez);
        AbilityVfx.spawnLandBurst(ctx.world, active.ex, ey, active.ez, false);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(active.ex, ey, active.ez),
                "minecraft:entity.generic.explode",
                0.65F,
                1.2F);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}

