package noppes.npcs.abilities;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.IEntityLiving;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Грул сидит на Кровохряке через ванильную посадку, а не через setPosition из скрипта.
 * Связь живёт дольше лимита AbilityRunner, поэтому это отдельный серверный тик.
 */
public final class HerdstoneMount {
    private static final String MOUNTED_KEY = "b1_mounted";
    private static final Map<UUID, Link> LINKS = new ConcurrentHashMap<>();

    private HerdstoneMount() {
    }

    public static boolean mount(final ICustomNpc rider, final IEntityLiving mount, final double seatOffset) {
        if (rider == null || mount == null) {
            return false;
        }
        final Entity riderEnt = rider.getMCEntity();
        final Entity mountEnt = mount.getMCEntity();
        if (!(riderEnt instanceof LivingEntity) || !(mountEnt instanceof LivingEntity)) {
            return false;
        }
        if (riderEnt == mountEnt || !riderEnt.isAlive() || !mountEnt.isAlive()) {
            return false;
        }
        final double seat = seatOffset > 0.2 ? seatOffset : 1.6;
        LINKS.put(riderEnt.getUUID(), new Link(mountEnt.getUUID(), seat));
        if (riderEnt.getVehicle() != mountEnt && !riderEnt.startRiding(mountEnt, true)) {
            LINKS.remove(riderEnt.getUUID());
            return false;
        }
        holdRider(rider);
        return true;
    }

    public static void dismount(final ICustomNpc rider) {
        if (rider == null) {
            return;
        }
        final Entity riderEnt = rider.getMCEntity();
        if (riderEnt == null) {
            return;
        }
        final Link link = LINKS.remove(riderEnt.getUUID());
        final Entity mountEnt = link == null ? riderEnt.getVehicle() : find(riderEnt, link.mountId);
        riderEnt.stopRiding();
        riderEnt.fallDistance = 0.0F;
        putMounted(riderEnt, "0");
        if (mountEnt != null) {
            putMounted(mountEnt, "0");
            if (mountEnt.isAlive()) {
                placeBeside(riderEnt, mountEnt);
            }
        }
    }

    public static boolean isRiding(final ICustomNpc rider) {
        if (rider == null || rider.getMCEntity() == null) {
            return false;
        }
        return LINKS.containsKey(rider.getMCEntity().getUUID());
    }

    public static void tick() {
        if (LINKS.isEmpty()) {
            return;
        }
        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        final List<UUID> drop = new ArrayList<>();
        for (final Map.Entry<UUID, Link> entry : LINKS.entrySet()) {
            final Entity rider = find(server, entry.getKey());
            final Entity mount = find(server, entry.getValue().mountId);
            if (!(rider instanceof LivingEntity) || !(mount instanceof LivingEntity)
                    || !rider.isAlive() || !mount.isAlive()) {
                drop.add(entry.getKey());
                forget(rider, mount);
                continue;
            }
            if (rider.getVehicle() != mount) {
                rider.startRiding(mount, true);
            }
            final double seat = entry.getValue().seat;
            rider.setPos(mount.getX(), mount.getY() + seat, mount.getZ());
            rider.setDeltaMovement(0.0, 0.0, 0.0);
            rider.hasImpulse = true;
            rider.fallDistance = 0.0F;
            final LivingEntity riderLiving = (LivingEntity) rider;
            final LivingEntity mountLiving = (LivingEntity) mount;
            riderLiving.yRot = mountLiving.yRot;
            riderLiving.yRotO = mountLiving.yRot;
            riderLiving.yHeadRot = mountLiving.yHeadRot;
            riderLiving.yBodyRot = mountLiving.yBodyRot;
            final IEntity wrapped = NpcAPI.Instance().getIEntity(rider);
            if (wrapped instanceof ICustomNpc) {
                holdRider((ICustomNpc) wrapped);
            }
        }
        for (int i = 0; i < drop.size(); i++) {
            LINKS.remove(drop.get(i));
        }
    }

    private static void holdRider(final ICustomNpc rider) {
        AbilityCombatHelper.stopNavigation(rider);
        AbilityCombatHelper.zeroHorizontalMotion(rider);
        try {
            rider.getAi().setWalkingSpeed(0);
            rider.getAi().setRetaliateType(3);
        } catch (final Exception ignored) {
        }
    }

    private static void forget(final Entity rider, final Entity mount) {
        if (rider != null) {
            rider.stopRiding();
            rider.fallDistance = 0.0F;
            putMounted(rider, "0");
        }
        if (mount != null) {
            putMounted(mount, "0");
        }
    }

    private static void placeBeside(final Entity rider, final Entity mount) {
        final double rad = Math.toRadians(mount.yRot + 90.0F);
        final double fx = Math.cos(rad);
        final double fz = Math.sin(rad);
        rider.setPos(mount.getX() - fz * 1.6, mount.getY(), mount.getZ() + fx * 1.6);
        rider.setDeltaMovement(0.0, 0.0, 0.0);
        rider.hasImpulse = true;
    }

    private static void putMounted(final Entity entity, final String value) {
        try {
            final IEntity wrapped = NpcAPI.Instance().getIEntity(entity);
            if (wrapped instanceof IEntityLiving) {
                ((IEntityLiving) wrapped).getStoreddata().put(MOUNTED_KEY, value);
            }
        } catch (final Exception ignored) {
        }
    }

    private static Entity find(final Entity near, final UUID id) {
        if (near == null || id == null || !(near.level instanceof ServerWorld)) {
            return null;
        }
        return ((ServerWorld) near.level).getEntity(id);
    }

    private static Entity find(final MinecraftServer server, final UUID id) {
        for (final ServerWorld world : server.getAllLevels()) {
            final Entity entity = world.getEntity(id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static final class Link {
        private final UUID mountId;
        private final double seat;

        private Link(final UUID mountId, final double seat) {
            this.mountId = mountId;
            this.seat = seat;
        }
    }
}
