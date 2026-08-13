package noppes.npcs.abilities.impl;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.data.IData;

import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Summons two small fast bat clones. Melee hits from bats heal the owner
 * via {@link noppes.npcs.abilities.event.VampireCrimsonBatHealHandler}.
 */
public final class VampireCrimsonBatsAbility implements CnpcAbility {
    public static final String ID = "vampire_crimson_bats";
    public static final String MINION_TAG = "vampire_crimson_bat";
    public static final String OWNER_KEY = "vcl_owner";
    public static final String HEAL_KEY = "vcl_bat_heal";
    private static final String SPAWN_MARKER = "spawned";
    private static final float MINION_HP = 50.0F;
    private static final int MINION_SIZE = 3;
    private static final int MINION_WALK_SPEED = 9;

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
        return AbilityDefaults.vampireCrimsonBats();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.SUMMON_COUNT,
                AbilityParamKeys.SUMMON_RADIUS,
                AbilityParamKeys.MAX_SUMMONED_NEAR_BOSS,
                AbilityParamKeys.CLONE_TAB,
                AbilityParamKeys.CLONE_NAME,
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
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.bat.ambient", 1.0F, 0.85F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickSpawn(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 3 == 0) {
            AbilityVfx.spawnBatSmoke(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), 1.4);
            AbilityVfx.spawnBloodCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = 1;
        return TickResult.CONTINUE;
    }

    private TickResult tickSpawn(final ActiveAbility active, final AbilityContext ctx) {
        if (!active.hitUuids.contains(SPAWN_MARKER)) {
            trySpawnMinions(active, ctx);
            active.hitUuids.add(SPAWN_MARKER);
        }
        AbilityVfx.spawnBatSmoke(ctx.world, ctx.npc.getX(), ctx.npc.getY() + 0.4, ctx.npc.getZ(), 2.0);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.bat.takeoff", 0.95F, 1.05F);
        return TickResult.FINISHED;
    }

    private void trySpawnMinions(final ActiveAbility active, final AbilityContext ctx) {
        final String cloneName = ctx.params.getString(AbilityParamKeys.CLONE_NAME, "");
        if (cloneName.isEmpty()) {
            return;
        }

        final double x = ctx.npc.getX();
        final double y = ctx.npc.getY();
        final double z = ctx.npc.getZ();
        final int maxNear = ctx.params.getInt(AbilityParamKeys.MAX_SUMMONED_NEAR_BOSS, 2);
        final double countRadius = 16.0;
        if (countNearbyMinions(ctx, x, y, z, countRadius) >= maxNear) {
            return;
        }

        final int count = ctx.params.getInt(AbilityParamKeys.SUMMON_COUNT, 2);
        final double spawnRadius = ctx.params.getDouble(AbilityParamKeys.SUMMON_RADIUS, 2.5);
        final int tab = ctx.params.getInt(AbilityParamKeys.CLONE_TAB, 1);
        final double heal = ctx.params.getDouble(AbilityParamKeys.LIFE_STEAL_PER_HIT, 15.0);
        final Random random = AbilityCombatHelper.random();

        for (int i = 0; i < count; i++) {
            if (countNearbyMinions(ctx, x, y, z, countRadius) >= maxNear) {
                break;
            }
            final double angle = random.nextDouble() * Math.PI * 2;
            final double dist = spawnRadius * (0.55 + random.nextDouble() * 0.45);
            final double sx = x + Math.cos(angle) * dist;
            final double sz = z + Math.sin(angle) * dist;
            final double sy = AbilityCombatHelper.findGroundY(ctx.world, sx, sz, y);
            spawnMinion(ctx, sx, sy, sz, tab, cloneName, heal);
        }
    }

    private void spawnMinion(
            final AbilityContext ctx,
            final double x,
            final double y,
            final double z,
            final int tab,
            final String cloneName,
            final double heal) {
        try {
            final IEntity spawned = ctx.world.spawnClone(x, y, z, tab, cloneName);
            if (spawned == null) {
                return;
            }
            spawned.addTag(MINION_TAG);
            final IData data = spawned.getStoreddata();
            data.put(OWNER_KEY, String.valueOf(ctx.npc.getUUID()));
            data.put(HEAL_KEY, String.valueOf(heal));
            configureMinion(spawned);
            setMinionTarget(spawned, ctx);
            AbilityVfx.spawnBatSmoke(ctx.world, x, y, z, 0.9);
            AbilityVfx.spawnBloodCharge(ctx.world, x, y, z);
        } catch (final Exception ignored) {
        }
    }

    private static void configureMinion(final IEntity spawned) {
        if (!(spawned instanceof ICustomNpc)) {
            return;
        }
        final ICustomNpc npc = (ICustomNpc) spawned;
        try {
            npc.setMaxHealth(MINION_HP);
            final Object mc = npc.getMCEntity();
            if (mc instanceof LivingEntity) {
                ((LivingEntity) mc).setHealth(MINION_HP);
            }
        } catch (final Exception ignored) {
        }
        try {
            npc.getDisplay().setSize(MINION_SIZE);
        } catch (final Exception ignored) {
        }
        try {
            npc.getAi().setWalkingSpeed(MINION_WALK_SPEED);
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
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
