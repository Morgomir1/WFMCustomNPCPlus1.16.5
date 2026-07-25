package noppes.npcs.abilities.impl;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import noppes.npcs.abilities.AbilityCombatHelper;
import noppes.npcs.abilities.AbilityDefaults;
import noppes.npcs.abilities.AbilityEffectType;
import noppes.npcs.abilities.AbilityContext;
import noppes.npcs.abilities.AbilityParamKeys;
import noppes.npcs.abilities.AbilityParams;
import noppes.npcs.abilities.AbilityVfx;
import noppes.npcs.abilities.ActiveAbility;
import noppes.npcs.abilities.CnpcAbility;
import noppes.npcs.abilities.TickResult;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.IEntityLiving;

import java.util.Map;
import java.util.Set;

public final class BarrowSentinelAbility implements CnpcAbility {
    public static final String ID = "barrow_sentinel";

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
        return AbilityDefaults.barrowSentinel();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.DISTANCE,
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.CONE_HALF_ANGLE,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y,
                AbilityParamKeys.EFFECT_TYPE,
                AbilityParamKeys.EFFECT_DURATION,
                AbilityParamKeys.EFFECT_AMPLIFIER,
                AbilityParamKeys.EXECUTE_HP_THRESHOLD,
                AbilityParamKeys.EXECUTE_BONUS_DAMAGE);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = false;
        if (!AbilityCombatHelper.computeDashEndPoints(active, ctx)) {
            return false;
        }
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 12);
        active.hitUuids.clear();
        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.holdInPlace(ctx.npc, active.sx, active.sy, active.sz);
        ctx.npc.setRotation(active.yaw);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:item.shield.block", 0.8F, 0.75F);
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
        // Keep yaw + position locked to telegraph spawn — AI chase would desync warning vs damage.
        AbilityCombatHelper.holdInPlace(ctx.npc, active.sx, active.sy, active.sz);
        ctx.npc.setRotation(active.yaw);

        spawnChargeFx(ctx);
        if (active.ticksLeft % 2 == 0) {
            final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 3.2);
            final double halfAngle = ctx.params.getDouble(AbilityParamKeys.CONE_HALF_ANGLE, 52.0);
            final AbilityEffectType effectType = AbilityEffectType.fromString(
                    ctx.params.getString(AbilityParamKeys.EFFECT_TYPE, AbilityEffectType.SLOWNESS.getId()));
            final int duration = ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 10);
            final int amplifier = ctx.params.getInt(AbilityParamKeys.EFFECT_AMPLIFIER, 0);
            applyPotionInTelegraphCone(active, ctx, radius, halfAngle, effectType, duration, amplifier);
        }

        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }

        // Do not recompute yaw/endpoints — damage must match the charge telegraph cone.
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 5);
        active.hitUuids.clear();

        AbilityVfx.spawnSwordSweep(ctx.world, active.sx, active.sy, active.sz, active.yaw);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(active.sx, active.sy, active.sz),
                "minecraft:entity.player.attack.sweep",
                1.0F,
                0.85F);
        // One strike in the telegraph cone (not along the lunge path).
        damageInCone(active, ctx);
        return TickResult.CONTINUE;
    }

    private TickResult tickActive(final ActiveAbility active, final AbilityContext ctx) {
        final int total = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 5);
        if (active.ticksLeft <= 0) {
            finishSweep(active, ctx);
            return TickResult.FINISHED;
        }

        final double progress = 1.0 - (active.ticksLeft - 1) / (double) total;
        final double cx = active.sx + (active.ex - active.sx) * progress;
        final double cz = active.sz + (active.ez - active.sz) * progress;
        final double cy = AbilityCombatHelper.findGroundY(ctx.world, cx, cz, active.sy);

        AbilityCombatHelper.stopNavigation(ctx.npc);
        AbilityCombatHelper.zeroHorizontalMotion(ctx.npc);
        ctx.npc.setPosition(cx, cy, cz);
        ctx.npc.setRotation(active.yaw);

        // Extra sweeps during lunge (visual only — damage already applied).
        if (active.ticksLeft == total || active.ticksLeft == Math.max(1, total / 2)) {
            AbilityVfx.spawnSwordSweep(ctx.world, cx, cy, cz, active.yaw);
        }
        active.ticksLeft--;
        return TickResult.CONTINUE;
    }

    private void damageInCone(final ActiveAbility active, final AbilityContext ctx) {
        final double x = active.sx;
        final double y = active.sy + 0.8;
        final double z = active.sz;
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 3.2);
        final double halfAngle = ctx.params.getDouble(AbilityParamKeys.CONE_HALF_ANGLE, 52.0);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 11.0);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 0.8);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.15);
        final double executeThreshold = ctx.params.getDouble(AbilityParamKeys.EXECUTE_HP_THRESHOLD, 0.35);
        final double executeBonus = ctx.params.getDouble(AbilityParamKeys.EXECUTE_BONUS_DAMAGE, 7.0);

        final int range = (int) Math.ceil(radius + 0.5);
        final IEntity[] list = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(x, y, z),
                range,
                -1);

        for (final IEntity ent : list) {
            if (!AbilityCombatHelper.isHostileToBoss(ctx.npc, ent)) {
                continue;
            }
            if (!AbilityCombatHelper.isInFrontCone(x, z, active.yaw, ent, halfAngle)) {
                continue;
            }
            final String uuid = String.valueOf(ent.getUUID());
            if (active.hitUuids.contains(uuid)) {
                continue;
            }
            if (AbilityCombatHelper.flatDistance(ent.getX(), ent.getZ(), x, z) > radius) {
                continue;
            }

            float totalDamage = (float) damage;
            final boolean execute = isExecuteTarget(ent, executeThreshold);
            if (execute) {
                totalDamage += (float) executeBonus;
            }

            ent.damage(totalDamage);
            if (ent instanceof IEntityLiving) {
                applyRadialKnockback((IEntityLiving) ent, x, z, knockback, knockbackY);
            }
            AbilityVfx.spawnHitParticle(ctx.world, ent);
            if (execute) {
                spawnExecuteFx(ctx, ent);
            }
            active.hitUuids.add(uuid);
        }
    }

    private void applyPotionInTelegraphCone(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double radius,
            final double halfAngle,
            final AbilityEffectType effectType,
            final int duration,
            final int amplifier) {
        final double x = active.sx;
        final double y = active.sy + 0.7;
        final double z = active.sz;
        final int range = (int) Math.ceil(radius + 0.5);
        final IEntity[] list = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(x, y, z),
                range,
                -1);
        for (final IEntity ent : list) {
            if (!AbilityCombatHelper.isHostileToBoss(ctx.npc, ent)) {
                continue;
            }
            if (!AbilityCombatHelper.isInFrontCone(x, z, active.yaw, ent, halfAngle)) {
                continue;
            }
            if (AbilityCombatHelper.flatDistance(ent.getX(), ent.getZ(), x, z) > radius) {
                continue;
            }
            AbilityCombatHelper.applyEffect(ent, effectType.toMcEffect(), duration, amplifier);
        }
    }

    private boolean isExecuteTarget(final IEntity entity, final double threshold) {
        if (entity == null) {
            return false;
        }
        try {
            final Entity mc = entity.getMCEntity();
            if (mc instanceof LivingEntity) {
                final LivingEntity living = (LivingEntity) mc;
                final float maxHealth = living.getMaxHealth();
                return maxHealth > 0.0F && living.getHealth() / maxHealth <= threshold;
            }
        } catch (final Exception ignored) {
        }
        return false;
    }

    private void applyRadialKnockback(
            final IEntityLiving entity,
            final double fromX,
            final double fromZ,
            final double strength,
            final double lift) {
        double dx = entity.getX() - fromX;
        double dz = entity.getZ() - fromZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.05) {
            dx = 1.0;
            dz = 0.0;
            len = 1.0;
        }
        entity.setMotionX((dx / len) * strength);
        entity.setMotionY(lift);
        entity.setMotionZ((dz / len) * strength);
    }

    private void finishSweep(final ActiveAbility active, final AbilityContext ctx) {
        final double ey = AbilityCombatHelper.findGroundY(ctx.world, active.ex, active.ez, active.sy);
        ctx.npc.setPosition(active.ex, ey, active.ez);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(active.ex, ey, active.ez),
                "minecraft:entity.zombie.attack_iron_door",
                0.75F,
                0.8F);
    }

    private void spawnChargeFx(final AbilityContext ctx) {
        final double x = ctx.npc.getX();
        final double y = ctx.npc.getY();
        final double z = ctx.npc.getZ();
        AbilityVfx.spawnChargeParticles(ctx.world, x, y, z, false);
        safeSpawn(ctx, "minecraft:soul", x, y + 0.9, z, 0.18, 0.08, 0.18, 0.01, 2);
        safeSpawn(ctx, "minecraft:ash", x, y + 0.15, z, 0.2, 0.02, 0.2, 0.01, 3);
    }

    private void spawnExecuteFx(final AbilityContext ctx, final IEntity ent) {
        safeSpawn(ctx, "minecraft:damage_indicator", ent.getX(), ent.getY() + 1.0, ent.getZ(), 0.15, 0.2, 0.15, 0.02, 4);
        safeSpawn(ctx, "minecraft:sweep_attack", ent.getX(), ent.getY() + 0.9, ent.getZ(), 0, 0, 0, 0, 0);
    }

    private void safeSpawn(
            final AbilityContext ctx,
            final String particle,
            final double x,
            final double y,
            final double z,
            final double dx,
            final double dy,
            final double dz,
            final double speed,
            final int count) {
        try {
            ctx.world.spawnParticle(particle, x, y, z, dx, dy, dz, speed, count);
        } catch (final Exception ignored) {
        }
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
