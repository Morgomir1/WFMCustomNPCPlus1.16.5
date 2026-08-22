package noppes.npcs.abilities.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import noppes.npcs.abilities.AbilityCombatHelper;
import noppes.npcs.abilities.AbilityParamKeys;
import noppes.npcs.abilities.AbilityParams;
import noppes.npcs.abilities.AbilityVfx;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.IEntityLiving;
import noppes.npcs.api.IWorld;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drachenfels soul parasite: homing ghost → server-side grab with 2 pure DPS
 * until the ghost NPC is killed (or safety timeout).
 */
@Mod.EventBusSubscriber(modid = "customnpcs", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DrachenfelsGhostGrabHandler {
    public static final String GHOST_TAG = "drachenfels_ghost_parasite";

    private static final int PHASE_SEEK = 1;
    private static final int PHASE_GRAB = 2;
    private static final double DEFAULT_APPROACH = 0.55;
    private static final double DEFAULT_HIT_RADIUS = 1.4;
    private static final double DEFAULT_HOVER = 1.1;
    private static final float DEFAULT_DAMAGE = 2.0F;
    private static final int DEFAULT_DAMAGE_INTERVAL = 20;
    private static final int DEFAULT_FLIGHT_TICKS = 120;
    private static final int DEFAULT_GRAB_TICKS = 600;
    private static final double DEFAULT_MAX_FLIGHT_DIST = 40.0;

    private static final Map<UUID, GrabState> BY_VICTIM = new ConcurrentHashMap<>();

    private DrachenfelsGhostGrabHandler() {
    }

    public static boolean isVictimGrabbed(final UUID victimUuid) {
        return victimUuid != null && BY_VICTIM.containsKey(victimUuid);
    }

    /**
     * Register a spawned parasite ghost that will home onto {@code victim}.
     *
     * @return false if victim already grabbed / invalid args
     */
    public static boolean start(
            final ICustomNpc owner,
            final IEntityLiving victim,
            final ICustomNpc ghost,
            final AbilityParams params) {
        if (owner == null || victim == null || !victim.isAlive() || ghost == null || !ghost.isAlive()) {
            return false;
        }
        final UUID victimId;
        final UUID ghostId;
        final UUID ownerId;
        try {
            victimId = UUID.fromString(String.valueOf(victim.getUUID()));
            ghostId = UUID.fromString(String.valueOf(ghost.getUUID()));
            ownerId = UUID.fromString(String.valueOf(owner.getUUID()));
        } catch (final Exception e) {
            return false;
        }
        if (BY_VICTIM.containsKey(victimId)) {
            return false;
        }

        final double approach = Math.max(0.15, params.getDouble(AbilityParamKeys.APPROACH_SPEED, DEFAULT_APPROACH));
        final double maxDist = Math.max(8.0, params.getDouble(AbilityParamKeys.DISTANCE, DEFAULT_MAX_FLIGHT_DIST));
        final int flightFromDist = (int) Math.ceil(maxDist / approach) + 30;

        final GrabState state = new GrabState();
        state.ownerUuid = ownerId;
        state.victimUuid = victimId;
        state.ghostUuid = ghostId;
        state.phase = PHASE_SEEK;
        state.approachSpeed = approach;
        state.hitRadius = Math.max(0.6, params.getDouble(AbilityParamKeys.HIT_RADIUS, DEFAULT_HIT_RADIUS));
        state.hoverOffset = params.getDouble(AbilityParamKeys.HOVER_OFFSET, DEFAULT_HOVER);
        state.damage = (float) Math.max(0.1, params.getDouble(AbilityParamKeys.DAMAGE, DEFAULT_DAMAGE));
        state.damageInterval = Math.max(1, params.getInt(AbilityParamKeys.DAMAGE_INTERVAL, DEFAULT_DAMAGE_INTERVAL));
        state.flightTicksLeft = Math.max(DEFAULT_FLIGHT_TICKS, flightFromDist);
        state.grabTicksLeft = Math.max(40, params.getInt(AbilityParamKeys.ACTIVE_TICKS, DEFAULT_GRAB_TICKS));
        state.damageCooldown = 0;
        state.holdX = victim.getX();
        state.holdY = victim.getY();
        state.holdZ = victim.getZ();

        BY_VICTIM.put(victimId, state);
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BY_VICTIM.isEmpty()) {
            return;
        }
        final Iterator<Map.Entry<UUID, GrabState>> it = BY_VICTIM.entrySet().iterator();
        while (it.hasNext()) {
            final GrabState state = it.next().getValue();
            if (!tickOne(state)) {
                despawnGhost(state);
                it.remove();
            }
        }
    }

    private static boolean tickOne(final GrabState state) {
        final Entity ghostMc = findEntity(state.ghostUuid);
        final Entity victimMc = findEntity(state.victimUuid);
        if (!(ghostMc instanceof LivingEntity) || !ghostMc.isAlive()) {
            return false;
        }
        if (!(victimMc instanceof LivingEntity) || !victimMc.isAlive()) {
            return false;
        }
        if (victimMc.level == null || victimMc.level.isClientSide) {
            return false;
        }

        final IEntity ghostWrapped = NpcAPI.Instance().getIEntity(ghostMc);
        final IEntity victimWrapped = NpcAPI.Instance().getIEntity(victimMc);
        if (!(ghostWrapped instanceof ICustomNpc) || !(victimWrapped instanceof IEntityLiving)) {
            return false;
        }
        final ICustomNpc ghost = (ICustomNpc) ghostWrapped;
        final IEntityLiving victim = (IEntityLiving) victimWrapped;
        final IWorld world = ghost.getWorld();

        if (state.phase == PHASE_SEEK) {
            return tickSeek(state, ghost, victim, world);
        }
        if (state.phase == PHASE_GRAB) {
            return tickGrab(state, ghost, victim, world);
        }
        return false;
    }

    private static boolean tickSeek(
            final GrabState state,
            final ICustomNpc ghost,
            final IEntityLiving victim,
            final IWorld world) {
        state.flightTicksLeft--;
        if (state.flightTicksLeft <= 0) {
            return false;
        }

        final double tx = victim.getX();
        final double ty = victim.getY() + state.hoverOffset * 0.35;
        final double tz = victim.getZ();
        final double gx = ghost.getX();
        final double gy = ghost.getY();
        final double gz = ghost.getZ();
        final double dx = tx - gx;
        final double dy = ty - gy;
        final double dz = tz - gz;
        final double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist <= state.hitRadius) {
            beginGrab(state, ghost, victim, world);
            return true;
        }

        final double step = Math.min(state.approachSpeed, dist);
        final double nx = gx + (dx / dist) * step;
        final double ny = gy + (dy / dist) * step;
        final double nz = gz + (dz / dist) * step;
        AbilityCombatHelper.pinLiving(ghost, nx, ny, nz);
        ghost.setRotation(AbilityCombatHelper.computeYaw(dx, dz));
        setNoPhysics(ghost, true);

        if (state.flightTicksLeft % 2 == 0) {
            AbilityVfx.spawnSoulFogCloud(world, nx, ny, nz, 0.7F);
            AbilityVfx.spawnSoulThread(world, gx, gy + 0.3, gz, nx, ny + 0.3, nz);
        }
        return true;
    }

    private static void beginGrab(
            final GrabState state,
            final ICustomNpc ghost,
            final IEntityLiving victim,
            final IWorld world) {
        state.phase = PHASE_GRAB;
        state.holdX = victim.getX();
        state.holdY = victim.getY();
        state.holdZ = victim.getZ();
        state.damageCooldown = 0;
        AbilityCombatHelper.pinLiving(victim, state.holdX, state.holdY, state.holdZ);
        AbilityCombatHelper.pinLiving(
                ghost,
                state.holdX,
                state.holdY + state.hoverOffset,
                state.holdZ);
        setNoPhysics(ghost, true);
        AbilityVfx.spawnSoulBurst(world, state.holdX, state.holdY + 0.6, state.holdZ, 1.6);
        world.playSoundAt(
                NpcAPI.Instance().getIPos(state.holdX, state.holdY, state.holdZ),
                "minecraft:entity.vex.charge",
                1.0F,
                0.55F);
        world.playSoundAt(
                NpcAPI.Instance().getIPos(state.holdX, state.holdY, state.holdZ),
                "minecraft:entity.elder_guardian.curse",
                0.7F,
                1.35F);
    }

    private static boolean tickGrab(
            final GrabState state,
            final ICustomNpc ghost,
            final IEntityLiving victim,
            final IWorld world) {
        state.grabTicksLeft--;
        if (state.grabTicksLeft <= 0) {
            return false;
        }

        AbilityCombatHelper.pinLiving(victim, state.holdX, state.holdY, state.holdZ);
        AbilityCombatHelper.pinLiving(
                ghost,
                state.holdX,
                state.holdY + state.hoverOffset,
                state.holdZ);
        setNoPhysics(ghost, true);

        state.damageCooldown++;
        if (state.damageCooldown >= state.damageInterval) {
            state.damageCooldown = 0;
            AbilityCombatHelper.dealPureDamage(victim, state.damage, true);
            AbilityVfx.spawnSoulFogCloud(world, state.holdX, state.holdY + 0.4, state.holdZ, 0.9F);
            world.playSoundAt(
                    NpcAPI.Instance().getIPos(state.holdX, state.holdY, state.holdZ),
                    "minecraft:entity.wither.hurt",
                    0.35F,
                    1.6F);
        } else if (state.grabTicksLeft % 5 == 0) {
            AbilityVfx.spawnSoulFogCloud(world, state.holdX, state.holdY + 0.5, state.holdZ, 0.55F);
        }
        return true;
    }

    private static void despawnGhost(final GrabState state) {
        final Entity ghostMc = findEntity(state.ghostUuid);
        if (ghostMc != null && ghostMc.isAlive()) {
            try {
                ghostMc.remove();
            } catch (final Exception ignored) {
            }
        }
    }

    private static void setNoPhysics(final ICustomNpc npc, final boolean value) {
        try {
            final Entity mc = npc.getMCEntity();
            if (mc != null) {
                mc.noPhysics = value;
            }
        } catch (final Exception ignored) {
        }
    }

    private static Entity findEntity(final UUID uuid) {
        if (uuid == null) {
            return null;
        }
        final net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        for (final ServerWorld world : server.getAllLevels()) {
            final Entity entity = world.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static final class GrabState {
        private UUID ownerUuid;
        private UUID victimUuid;
        private UUID ghostUuid;
        private int phase;
        private double approachSpeed;
        private double hitRadius;
        private double hoverOffset;
        private float damage;
        private int damageInterval;
        private int damageCooldown;
        private int flightTicksLeft;
        private int grabTicksLeft;
        private double holdX;
        private double holdY;
        private double holdZ;
    }
}
