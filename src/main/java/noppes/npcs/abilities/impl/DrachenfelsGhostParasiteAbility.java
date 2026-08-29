package noppes.npcs.abilities.impl;

import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import noppes.npcs.abilities.AbilityCombatHelper;
import noppes.npcs.abilities.AbilityContext;
import noppes.npcs.abilities.AbilityDefaults;
import noppes.npcs.abilities.AbilityParamKeys;
import noppes.npcs.abilities.AbilityParams;
import noppes.npcs.abilities.AbilityVfx;
import noppes.npcs.abilities.ActiveAbility;
import noppes.npcs.abilities.CnpcAbility;
import noppes.npcs.abilities.NecromancerMinionHelper;
import noppes.npcs.abilities.TickResult;
import noppes.npcs.abilities.event.DrachenfelsGhostGrabHandler;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.entity.data.INPCAi;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Soul launches a killable parasite ghost that homes onto the target.
 * On contact {@link DrachenfelsGhostGrabHandler} holds the victim and deals
 * 2 pure MAGIC damage / second until the ghost dies.
 */
public final class DrachenfelsGhostParasiteAbility implements CnpcAbility {
    public static final String ID = "drachenfels_ghost_parasite";

    private static final String LAUNCHED_MARKER = "launched";
    private static final float GHOST_HP = 40.0F;
    private static final int GHOST_SIZE = 3;
    private static final String DEFAULT_CLONE = "Drachenfels Ghost Parasite";
    private static final String DISPLAY_NAME = "Паразит-призрак";

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
        return AbilityDefaults.drachenfelsGhostParasite();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.APPROACH_SPEED,
                AbilityParamKeys.HIT_RADIUS,
                AbilityParamKeys.LAND_RADIUS,
                AbilityParamKeys.HOVER_OFFSET,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.DAMAGE_INTERVAL,
                AbilityParamKeys.DISTANCE,
                AbilityParamKeys.CLONE_TAB,
                AbilityParamKeys.CLONE_NAME,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return false;
        }
        // Не летим в партнёра / thrall / другого NPC — только игрок в радиусе.
        if (!(ctx.target instanceof IPlayer)) {
            return false;
        }
        try {
            final int gm = ((IPlayer) ctx.target).getGamemode();
            if (gm == 1 || gm == 3) {
                return false;
            }
        } catch (final Exception ignored) {
        }
        final double maxRange = Math.max(8.0, ctx.params.getDouble(AbilityParamKeys.DISTANCE, 40.0));
        if (AbilityCombatHelper.distanceToTarget(ctx) > maxRange) {
            return false;
        }
        try {
            final UUID ownerId = UUID.fromString(String.valueOf(ctx.npc.getUUID()));
            if (DrachenfelsGhostGrabHandler.hasActiveForOwner(ownerId)) {
                return false;
            }
            final UUID victimId = UUID.fromString(String.valueOf(ctx.target.getUUID()));
            if (DrachenfelsGhostGrabHandler.isVictimGrabbed(victimId)) {
                return false;
            }
        } catch (final Exception e) {
            return false;
        }

        active.jumpStyle = false;
        active.hitUuids.clear();
        active.markers.clear();
        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY() + 1.0;
        active.sz = ctx.npc.getZ();
        lockAim(active, ctx);

        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 28));
        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.evoker.prepare_attack", 0.95F, 0.45F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickLaunch(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        AbilityCombatHelper.zeroHorizontalMotion(ctx.npc);
        if (ctx.target != null && ctx.target.isAlive()) {
            lockAim(active, ctx);
        }
        ctx.npc.setRotation(active.yaw);

        if (active.ticksLeft % 3 == 0) {
            AbilityVfx.spawnSoulCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
            AbilityVfx.spawnSoulFogCloud(ctx.world, ctx.npc.getX(), ctx.npc.getY() + 0.6, ctx.npc.getZ(), 1.3F);
        }

        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }

        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = 1;
        active.hitUuids.clear();
        AbilityVfx.spawnSoulBurst(ctx.world, ctx.npc.getX(), ctx.npc.getY() + 0.5, ctx.npc.getZ(), 1.8);
        return TickResult.CONTINUE;
    }

    private TickResult tickLaunch(final ActiveAbility active, final AbilityContext ctx) {
        if (active.hitUuids.contains(LAUNCHED_MARKER)) {
            return TickResult.FINISHED;
        }
        active.hitUuids.add(LAUNCHED_MARKER);

        if (ctx.target == null || !ctx.target.isAlive()) {
            return TickResult.FINISHED;
        }
        if (!(ctx.target instanceof IPlayer)) {
            return TickResult.FINISHED;
        }
        final double maxRange = Math.max(8.0, ctx.params.getDouble(AbilityParamKeys.DISTANCE, 40.0));
        if (AbilityCombatHelper.distanceToTarget(ctx) > maxRange) {
            return TickResult.FINISHED;
        }

        final ICustomNpc ghost = spawnGhost(ctx);
        if (ghost == null) {
            return TickResult.FINISHED;
        }

        final boolean started = DrachenfelsGhostGrabHandler.start(ctx.npc, ctx.target, ghost, ctx.params);
        if (!started) {
            try {
                ghost.despawn();
            } catch (final Exception e) {
                try {
                    final Object mc = ghost.getMCEntity();
                    if (mc instanceof LivingEntity) {
                        ((LivingEntity) mc).remove();
                    }
                } catch (final Exception ignored) {
                }
            }
            return TickResult.FINISHED;
        }

        AbilityVfx.spawnSoulThread(
                ctx.world,
                ctx.npc.getX(), ctx.npc.getY() + 1.0, ctx.npc.getZ(),
                ghost.getX(), ghost.getY() + 0.4, ghost.getZ());
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.vex.ambient", 1.0F, 0.7F);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(ghost.getX(), ghost.getY(), ghost.getZ()),
                "minecraft:entity.phantom.flap",
                0.85F,
                1.4F);
        return TickResult.FINISHED;
    }

    private ICustomNpc spawnGhost(final AbilityContext ctx) {
        final double x = ctx.npc.getX();
        final double y = ctx.npc.getY() + 1.0;
        final double z = ctx.npc.getZ();
        final int tab = ctx.params.getInt(AbilityParamKeys.CLONE_TAB, 1);
        final String cloneName = ctx.params.getString(AbilityParamKeys.CLONE_NAME, DEFAULT_CLONE);

        ICustomNpc ghost = null;
        if (cloneName != null && !cloneName.isEmpty()) {
            try {
                final IEntity spawned = ctx.world.spawnClone(x, y, z, tab, cloneName);
                if (spawned instanceof ICustomNpc) {
                    ghost = (ICustomNpc) spawned;
                }
            } catch (final Exception ignored) {
            }
        }
        if (ghost == null) {
            ghost = spawnFallbackNpc(ctx, x, y, z);
        }
        if (ghost == null) {
            return null;
        }
        configureGhost(ghost, ctx);
        return ghost;
    }

    private static ICustomNpc spawnFallbackNpc(
            final AbilityContext ctx,
            final double x,
            final double y,
            final double z) {
        try {
            final Object mcWorld = ctx.world.getMCWorld();
            if (!(mcWorld instanceof World)) {
                return null;
            }
            final ICustomNpc npc = NpcAPI.Instance().spawnNPC(
                    (World) mcWorld,
                    (int) Math.floor(x),
                    (int) Math.floor(y),
                    (int) Math.floor(z));
            if (npc != null) {
                npc.setPosition(x, y, z);
            }
            return npc;
        } catch (final Exception e) {
            return null;
        }
    }

    private static void configureGhost(final ICustomNpc ghost, final AbilityContext ctx) {
        try {
            ghost.addTag(DrachenfelsGhostGrabHandler.GHOST_TAG);
        } catch (final Exception ignored) {
        }
        // Клон не должен респавниться после смерти (spawnCycle=3).
        NecromancerMinionHelper.disableRespawn(ghost);
        try {
            ghost.getDisplay().setName(DISPLAY_NAME);
            ghost.getDisplay().setSize(GHOST_SIZE);
        } catch (final Exception ignored) {
        }
        try {
            ghost.setMaxHealth(GHOST_HP);
            final Object mc = ghost.getMCEntity();
            if (mc instanceof LivingEntity) {
                ((LivingEntity) mc).setHealth(GHOST_HP);
            }
        } catch (final Exception ignored) {
        }
        try {
            ghost.setFaction(ctx.npc.getFaction().getId());
        } catch (final Exception ignored) {
        }
        try {
            final INPCAi ai = ghost.getAi();
            ai.setWalkingSpeed(0);
            ai.setRetaliateType(3); // Nothing — handler drives movement
            ai.setNavigationType(1); // Flying
            ai.setMovingType(0); // Standing
            ai.setLeapAtTarget(false);
        } catch (final Exception ignored) {
        }
        AbilityCombatHelper.stopNavigation(ghost);
        AbilityCombatHelper.pinLiving(ghost, ghost.getX(), ghost.getY(), ghost.getZ());
        try {
            final Object mc = ghost.getMCEntity();
            if (mc instanceof net.minecraft.entity.Entity) {
                ((net.minecraft.entity.Entity) mc).noPhysics = true;
            }
        } catch (final Exception ignored) {
        }
    }

    private static void lockAim(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null) {
            return;
        }
        active.ex = ctx.target.getX();
        active.ey = ctx.target.getY();
        active.ez = ctx.target.getZ();
        final double dx = active.ex - ctx.npc.getX();
        final double dz = active.ez - ctx.npc.getZ();
        active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
