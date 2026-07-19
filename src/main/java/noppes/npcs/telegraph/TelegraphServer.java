package noppes.npcs.telegraph;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.client.PacketTelegraphRemove;
import noppes.npcs.packets.client.PacketTelegraphSpawn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TelegraphServer {
    private static final Map<String, TelegraphInstance> ACTIVE = new ConcurrentHashMap<>();
    private static final int SEND_RANGE = 64;

    private TelegraphServer() {
    }

    public static String spawn(final World world, final TelegraphInstance instance) {
        if (world == null || world.isClientSide || instance == null || instance.type == TelegraphType.NONE) {
            return "";
        }
        instance.dimension = world.dimension();
        ACTIVE.put(instance.id, instance);
        Packets.sendNearby(world, new BlockPos(instance.x, instance.y, instance.z), SEND_RANGE,
                PacketTelegraphSpawn.from(instance));
        return instance.id;
    }

    public static void remove(final String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        final TelegraphInstance removed = ACTIVE.remove(id);
        if (removed != null) {
            broadcastRemove(removed);
        }
    }

    public static void removeNear(final Entity near, final String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        ACTIVE.remove(id);
        if (near != null) {
            Packets.sendNearby(near, new PacketTelegraphRemove(id));
        } else {
            remove(id);
        }
    }

    public static void follow(final String id, final Entity entity) {
        final TelegraphInstance instance = ACTIVE.get(id);
        if (instance == null || entity == null) {
            return;
        }
        instance.followEntityId = entity.getId();
        instance.dimension = entity.level.dimension();
        Packets.sendNearby(entity, PacketTelegraphSpawn.from(instance));
    }

    public static TelegraphInstance get(final String id) {
        return ACTIVE.get(id);
    }

    public static void tickAll() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        final List<String> toRemove = new ArrayList<>();
        for (final Map.Entry<String, TelegraphInstance> entry : ACTIVE.entrySet()) {
            if (!entry.getValue().tick()) {
                toRemove.add(entry.getKey());
            }
        }
        for (final String id : toRemove) {
            final TelegraphInstance removed = ACTIVE.remove(id);
            if (removed != null) {
                broadcastRemove(removed);
            }
        }
    }

    private static void broadcastRemove(final TelegraphInstance instance) {
        final ServerWorld world = instance.resolveWorld();
        if (world == null) {
            return;
        }
        Packets.sendNearby(world, new BlockPos(instance.x, instance.y, instance.z), SEND_RANGE,
                new PacketTelegraphRemove(instance.id));
    }
}
