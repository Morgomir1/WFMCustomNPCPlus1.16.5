package noppes.npcs.abilities.impl;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;

import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class DrachenfelsRaiseThrallsAbility implements CnpcAbility {
    public static final String ID = "drachenfels_raise_thralls";
    private static final String SPAWN_MARKER = "spawned";
    private static final String MINION_TAG = "drachenfels_thrall";

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
        return AbilityDefaults.drachenfelsRaiseThralls();
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
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 12);
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
        } else {
            active.ex = ctx.npc.getX();
            active.ey = ctx.npc.getY();
            active.ez = ctx.npc.getZ();
        }
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.evoker.prepare_summon", 0.95F, 0.7F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickRaise(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 3 == 0) {
            AbilityVfx.spawnDarkCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
            AbilityVfx.spawnSoulCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY() + 0.5, ctx.npc.getZ());
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 18);
        active.hitUuids.clear();
        AbilityVfx.spawnSoulBurst(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), 2.0);
        return TickResult.CONTINUE;
    }

    private TickResult tickRaise(final ActiveAbility active, final AbilityContext ctx) {
        if (active.ticksLeft <= 0) {
            return TickResult.FINISHED;
        }

        final double x = ctx.npc.getX();
        final double y = ctx.npc.getY();
        final double z = ctx.npc.getZ();
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 4.0);
        final AbilityEffectType effectType = AbilityEffectType.fromString(
                ctx.params.getString(AbilityParamKeys.EFFECT_TYPE, "slowness"));
        final int duration = ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 40);
        final int amplifier = ctx.params.getInt(AbilityParamKeys.EFFECT_AMPLIFIER, 0);

        AbilityCombatHelper.applyPotionNearby(
                active, ctx, x, y + 0.5, z,
                radius, effectType, duration, amplifier);
        if (active.ticksLeft % 4 == 0) {
            AbilityVfx.spawnSoulFogCloud(ctx.world, x, y, z, 1.6F);
        }

        if (!active.hitUuids.contains(SPAWN_MARKER)) {
            trySpawnThralls(active, ctx, x, y, z);
            active.hitUuids.add(SPAWN_MARKER);
        }

        active.ticksLeft--;
        return TickResult.CONTINUE;
    }

    private void trySpawnThralls(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double x,
            final double y,
            final double z) {
        final String cloneName = ctx.params.getString(AbilityParamKeys.CLONE_NAME, "");
        if (cloneName.isEmpty()) {
            return;
        }

        final int maxNear = ctx.params.getInt(AbilityParamKeys.MAX_SUMMONED_NEAR_BOSS, 4);
        final double countRadius = 14.0;
        if (countNearbyThralls(ctx, x, y, z, countRadius) >= maxNear) {
            return;
        }

        final int count = ctx.params.getInt(AbilityParamKeys.SUMMON_COUNT, 2);
        final double spawnRadius = ctx.params.getDouble(AbilityParamKeys.SUMMON_RADIUS, 3.5);
        final int tab = ctx.params.getInt(AbilityParamKeys.CLONE_TAB, 1);
        final Random random = AbilityCombatHelper.random();

        for (int i = 0; i < count; i++) {
            if (countNearbyThralls(ctx, x, y, z, countRadius) >= maxNear) {
                break;
            }
            final double angle = random.nextDouble() * Math.PI * 2;
            final double dist = spawnRadius * (0.55 + random.nextDouble() * 0.45);
            final double sx = x + Math.cos(angle) * dist;
            final double sz = z + Math.sin(angle) * dist;
            final double sy = AbilityCombatHelper.findGroundY(ctx.world, sx, sz, y);
            spawnThrall(ctx, sx, sy, sz, tab, cloneName);
        }
    }

    private void spawnThrall(
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
            setThrallTarget(spawned, ctx);
            AbilityVfx.spawnSoulBurst(ctx.world, x, y, z, 0.9);
            ctx.world.playSoundAt(
                    NpcAPI.Instance().getIPos(x, y, z),
                    "minecraft:entity.zombie.infect",
                    0.8F,
                    0.9F);
        } catch (final Exception ignored) {
        }
    }

    private void setThrallTarget(final IEntity thrall, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return;
        }
        try {
            final Object thrallMc = thrall.getMCEntity();
            final Object targetMc = ctx.target.getMCEntity();
            if (thrallMc instanceof MobEntity && targetMc instanceof LivingEntity) {
                ((MobEntity) thrallMc).setTarget((LivingEntity) targetMc);
            }
        } catch (final Exception ignored) {
        }
    }

    private int countNearbyThralls(
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
