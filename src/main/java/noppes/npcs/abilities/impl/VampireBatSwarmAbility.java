package noppes.npcs.abilities.impl;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;

import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class VampireBatSwarmAbility implements CnpcAbility {
    public static final String ID = "vampire_bat_swarm";
    private static final String SPAWN_MARKER = "spawned";
    private static final String MINION_TAG = "vampire_bat_minion";

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
        return AbilityDefaults.vampireBatSwarm();
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
                AbilityParamKeys.SUMMON_COUNT,
                AbilityParamKeys.SUMMON_RADIUS,
                AbilityParamKeys.MAX_SUMMONED_NEAR_BOSS,
                AbilityParamKeys.CLONE_TAB,
                AbilityParamKeys.CLONE_NAME);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = false;
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 10);
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        if (ctx.target != null) {
            final double dx = ctx.target.getX() - ctx.npc.getX();
            final double dz = ctx.target.getZ() - ctx.npc.getZ();
            active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
            ctx.npc.setRotation(active.yaw);
            active.ex = ctx.target.getX();
            active.ez = ctx.target.getZ();
            active.ey = AbilityCombatHelper.findGroundY(ctx.world, active.ex, active.ez, ctx.target.getY());
        }
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.bat.ambient", 0.9F, 0.9F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickSwarm(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 3 == 0) {
            AbilityVfx.spawnBatSmoke(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), 1.4);
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 22);
        active.hitUuids.clear();
        AbilityVfx.spawnBatSmoke(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), 2.2);
        return TickResult.CONTINUE;
    }

    private TickResult tickSwarm(final ActiveAbility active, final AbilityContext ctx) {
        if (active.ticksLeft <= 0) {
            return TickResult.FINISHED;
        }
        final double x = active.ex;
        final double y = active.ey;
        final double z = active.ez;
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 5.0);

        final AbilityEffectType effectType = AbilityEffectType.fromString(
                ctx.params.getString(AbilityParamKeys.EFFECT_TYPE, "blindness"));
        final int duration = ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 40);
        final int amplifier = ctx.params.getInt(AbilityParamKeys.EFFECT_AMPLIFIER, 0);

        AbilityCombatHelper.applyPotionNearby(
                active, ctx, x, y + 0.6, z,
                radius, effectType, duration, amplifier);
        AbilityVfx.spawnBatSmoke(ctx.world, x, y + 0.6, z, radius);

        if (!active.hitUuids.contains(SPAWN_MARKER)) {
            trySpawnMinions(active, ctx, x, y, z);
            active.hitUuids.add(SPAWN_MARKER);
        }

        active.ticksLeft--;
        return TickResult.CONTINUE;
    }

    private void trySpawnMinions(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double x,
            final double y,
            final double z) {
        final String cloneName = ctx.params.getString(AbilityParamKeys.CLONE_NAME, "");
        if (cloneName.isEmpty()) {
            return;
        }

        final int maxNear = ctx.params.getInt(AbilityParamKeys.MAX_SUMMONED_NEAR_BOSS, 6);
        final double countRadius = 12.0;
        if (countNearbyMinions(ctx, x, y, z, countRadius) >= maxNear) {
            return;
        }

        final int count = ctx.params.getInt(AbilityParamKeys.SUMMON_COUNT, 2);
        final double spawnRadius = ctx.params.getDouble(AbilityParamKeys.SUMMON_RADIUS, 4.0);
        final int tab = ctx.params.getInt(AbilityParamKeys.CLONE_TAB, 1);
        final Random random = AbilityCombatHelper.random();

        for (int i = 0; i < count; i++) {
            if (countNearbyMinions(ctx, x, y, z, countRadius) >= maxNear) {
                break;
            }
            final double angle = random.nextDouble() * Math.PI * 2;
            final double dist = spawnRadius * (0.6 + random.nextDouble() * 0.4);
            final double sx = x + Math.cos(angle) * dist;
            final double sz = z + Math.sin(angle) * dist;
            final double sy = y + 1.2 + random.nextDouble() * 1.2;
            spawnMinion(ctx, sx, sy, sz, tab, cloneName);
        }
    }

    private void spawnMinion(
            final AbilityContext ctx,
            final double x,
            final double y,
            final double z,
            final int tab,
            final String cloneName) {
        try {
            final IEntity spawned = ctx.world.spawnClone(x, y, z, tab, cloneName);
            if (spawned == null) {
                return;
            }
            spawned.addTag(MINION_TAG);
            setMinionTarget(spawned, ctx);
            AbilityVfx.spawnBatSmoke(ctx.world, x, y, z, 0.8);
        } catch (final Exception ignored) {
        }
    }

    private void setMinionTarget(final IEntity minion, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return;
        }
        try {
            final Object minionMc = minion.getMCEntity();
            final Object targetMc = ctx.target.getMCEntity();
            if (minionMc instanceof MobEntity && targetMc instanceof LivingEntity) {
                ((MobEntity) minionMc).setTarget((LivingEntity) targetMc);
            }
        } catch (final Exception ignored) {
        }
    }

    private int countNearbyMinions(
            final AbilityContext ctx,
            final double x,
            final double y,
            final double z,
            final double radius) {
        final int range = (int) Math.ceil(radius + 0.5);
        final IEntity[] list = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(x, y, z),
                range,
                -1);
        int count = 0;
        for (final IEntity ent : list) {
            if (!ent.hasTag(MINION_TAG)) {
                continue;
            }
            if (AbilityCombatHelper.flatDistance(ent.getX(), ent.getZ(), x, z) <= radius) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
