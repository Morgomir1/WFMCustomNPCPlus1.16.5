package noppes.npcs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompoundNBT;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * {@code PacketNpcUpdate} often arrives before the client entity exists (hide→show /
 * StartTracking race). Jar handle() then drops the NBT and the NPC stays on Display
 * defaults ("Noppes" + steve). Queue by entity id until the NPC is in the world.
 */
public final class ClientNpcSpawnData {
    private static final Map<Integer, CompoundNBT> PENDING = new HashMap<>();

    private ClientNpcSpawnData() {
    }

    public static void applyOrQueue(final int entityId, final CompoundNBT data) {
        if (data == null) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            final Entity entity = mc.level.getEntity(entityId);
            if (entity instanceof EntityNPCInterface) {
                ((EntityNPCInterface) entity).readSpawnData(data);
                PENDING.remove(entityId);
                return;
            }
        }
        PENDING.put(entityId, data.copy());
    }

    public static void applyIfPending(final Entity entity) {
        if (!(entity instanceof EntityNPCInterface) || PENDING.isEmpty()) {
            return;
        }
        final CompoundNBT data = PENDING.remove(entity.getId());
        if (data != null) {
            ((EntityNPCInterface) entity).readSpawnData(data);
        }
    }

    public static void tryApplyPending() {
        final Minecraft mc = Minecraft.getInstance();
        if (PENDING.isEmpty() || mc.level == null) {
            return;
        }
        final Iterator<Map.Entry<Integer, CompoundNBT>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<Integer, CompoundNBT> entry = it.next();
            final Entity entity = mc.level.getEntity(entry.getKey());
            if (entity instanceof EntityNPCInterface) {
                ((EntityNPCInterface) entity).readSpawnData(entry.getValue());
                it.remove();
            }
        }
    }

    public static void clear() {
        PENDING.clear();
    }
}
