package noppes.npcs.abilities.impl;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;

import java.util.Map;
import java.util.Set;

/**
 * Зомби-огр-свинцеплюй: удар по земле перед собой (AoE + слепота).
 */
public final class ZombieOgreLeadbelcherSlamAbility implements CnpcAbility {
    public static final String ID = "zombie_ogre_leadbelcher_slam";

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
        return AbilityDefaults.zombieOgreLeadbelcherSlam();
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
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 12);
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        faceTarget(active, ctx);

        // Фиксируем точку удара сразу и телеграфируем (чтобы игроки понимали, что сейчас будет).
        final double sx = ctx.npc.getX();
        final double sz = ctx.npc.getZ();
        final double rad = (active.yaw + 90.0) * 0.0174532925;
        final double dirX = Math.cos(rad);
        final double dirZ = Math.sin(rad);
        final double px = sx + dirX * 2.2;
        final double pz = sz + dirZ * 2.2;
        final double py = AbilityCombatHelper.findGroundY(ctx.world, px, pz, ctx.npc.getY());
        active.ex = px;
        active.ey = py;
        active.ez = pz;

        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.zoglin.angry", 0.7F, 0.9F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return doSlam(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 4 == 0) {
            AbilityVfx.spawnChargeParticles(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), false);
        }
        if (active.ticksLeft % 2 == 0) {
            spawnGroundTelegraph(ctx, active.ex, active.ey, active.ez);
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        swingLeftArm(ctx);
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = 1;
        AbilityVfx.spawnStartBurst(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), false);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.ravager.attack", 0.8F, 0.9F);
        return TickResult.CONTINUE;
    }

    private TickResult doSlam(final ActiveAbility active, final AbilityContext ctx) {
        final double rad = (active.yaw + 90.0) * 0.0174532925;
        final double dirX = Math.cos(rad);
        final double dirZ = Math.sin(rad);

        final double px = active.ex;
        final double pz = active.ez;
        final double py = active.ey;

        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 3.0);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 12.0);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 0.9);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.15);

        final AbilityEffectType effectType = AbilityEffectType.fromString(
                ctx.params.getString(AbilityParamKeys.EFFECT_TYPE, "blindness"));
        final int duration = ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 30);
        final int amplifier = ctx.params.getInt(AbilityParamKeys.EFFECT_AMPLIFIER, 0);

        active.hitUuids.clear();
        AbilityVfx.spawnLandBurst(ctx.world, px, py, pz, true);
        ctx.world.playSoundAt(NpcAPI.Instance().getIPos(px, py, pz), "minecraft:entity.generic.explode", 0.9F, 0.95F);

        AbilityCombatHelper.damageNearby(
                active, ctx, px, py + 0.5, pz,
                radius, damage, dirX, dirZ, knockback, knockbackY, false);
        AbilityCombatHelper.applyPotionNearby(
                active, ctx, px, py + 0.5, pz,
                radius, effectType, duration, amplifier);
        return TickResult.FINISHED;
    }

    private static void faceTarget(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null) {
            return;
        }
        final double dx = ctx.target.getX() - ctx.npc.getX();
        final double dz = ctx.target.getZ() - ctx.npc.getZ();
        active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
        ctx.npc.setRotation(active.yaw);
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

    private static void spawnGroundTelegraph(final AbilityContext ctx, final double x, final double y, final double z) {
        try {
            for (int i = 0; i < 10; i++) {
                final double a = (i / 10.0) * Math.PI * 2.0;
                ctx.world.spawnParticle(
                        "minecraft:cloud",
                        x + Math.cos(a) * 1.6,
                        y + 0.12,
                        z + Math.sin(a) * 1.6,
                        0.0, 0.02, 0.0,
                        0.0,
                        1);
            }
            ctx.world.spawnParticle("minecraft:smoke", x, y + 0.15, z, 0.0, 0.03, 0.0, 0.01, 2);
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

