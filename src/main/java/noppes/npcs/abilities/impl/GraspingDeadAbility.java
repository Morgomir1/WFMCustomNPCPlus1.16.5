package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.AbilityCombatHelper;
import noppes.npcs.abilities.AbilityContext;
import noppes.npcs.abilities.AbilityDefaults;
import noppes.npcs.abilities.AbilityEffectType;
import noppes.npcs.abilities.AbilityParamKeys;
import noppes.npcs.abilities.AbilityParams;
import noppes.npcs.abilities.AbilityVfx;
import noppes.npcs.abilities.ActiveAbility;
import noppes.npcs.abilities.CnpcAbility;
import noppes.npcs.abilities.TickResult;
import noppes.npcs.api.entity.IEntityLiving;

import java.util.Map;
import java.util.Set;

/**
 * Зомби: «Хватка мертвецов».
 * Контактный контроль (tarpit): сильная медлительность при дистанции почти в упор.
 */
public final class GraspingDeadAbility implements CnpcAbility {
    public static final String ID = "grasping_dead";

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
        return true;
    }

    @Override
    public Map<String, Object> defaultParams() {
        return AbilityDefaults.graspingDead();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.EFFECT_TYPE,
                AbilityParamKeys.EFFECT_DURATION,
                AbilityParamKeys.EFFECT_AMPLIFIER,
                AbilityParamKeys.DAMAGE_PER_TICK);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = false;
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 5);
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        faceTarget(active, ctx);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.zombie.ambient", 0.7F, 0.8F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickHold(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        faceTarget(active, ctx);
        ctx.npc.setRotation(active.yaw);

        if (active.ticksLeft % 2 == 0) {
            AbilityVfx.spawnDecayCloud(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), 1.2F);
        }

        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }

        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 36);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.zombie.attack_wooden_door", 0.7F, 0.75F);
        return TickResult.CONTINUE;
    }

    private TickResult tickHold(final ActiveAbility active, final AbilityContext ctx) {
        if (active.ticksLeft <= 0) {
            return TickResult.FINISHED;
        }
        if (ctx.target == null || !ctx.target.isAlive()) {
            return TickResult.FINISHED;
        }

        AbilityCombatHelper.stopNavigation(ctx.npc);
        faceTarget(active, ctx);
        ctx.npc.setRotation(active.yaw);

        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 1.8);
        final double dist = AbilityCombatHelper.flatDistance(
                ctx.npc.getX(),
                ctx.npc.getZ(),
                ctx.target.getX(),
                ctx.target.getZ());
        if (dist > radius + 0.4) {
            return TickResult.FINISHED;
        }

        final AbilityEffectType effectType = AbilityEffectType.fromString(
                ctx.params.getString(AbilityParamKeys.EFFECT_TYPE, "slowness"));
        final int duration = ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 35);
        final int amplifier = ctx.params.getInt(AbilityParamKeys.EFFECT_AMPLIFIER, 2);

        // Поддерживаем эффект, но не спамим каждый тик.
        if (active.ticksLeft % 10 == 0) {
            AbilityCombatHelper.applyEffect(ctx.target, effectType.toMcEffect(), duration, amplifier);
            try {
                ctx.world.spawnParticle("minecraft:ash",
                        ctx.target.getX(),
                        ctx.target.getY() + 0.2,
                        ctx.target.getZ(),
                        0.2, 0.02, 0.2,
                        0.01,
                        3);
            } catch (final Exception ignored) {
            }
        }

        final double damagePerTick = ctx.params.getDouble(AbilityParamKeys.DAMAGE_PER_TICK, 0.0);
        if (damagePerTick > 0.001 && active.ticksLeft % 10 == 0) {
            ctx.target.damage((float) damagePerTick);
        }

        // Немного «мертвого веса» — лёгкий подъём (мешает спринт-выскальзыванию), без рывков и притягивания.
        if (ctx.target instanceof IEntityLiving && active.ticksLeft % 10 == 0) {
            try {
                ((IEntityLiving) ctx.target).setMotionY(0.02);
            } catch (final Exception ignored) {
            }
        }

        active.ticksLeft--;
        return TickResult.CONTINUE;
    }

    private static void faceTarget(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null) {
            return;
        }
        final double dx = ctx.target.getX() - ctx.npc.getX();
        final double dz = ctx.target.getZ() - ctx.npc.getZ();
        active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}

