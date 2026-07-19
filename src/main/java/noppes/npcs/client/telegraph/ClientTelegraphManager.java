package noppes.npcs.client.telegraph;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.telegraph.TelegraphInstance;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class ClientTelegraphManager {
    private static final Map<String, TelegraphInstance> ACTIVE = new ConcurrentHashMap<>();

    private ClientTelegraphManager() {
    }

    public static void put(final String id, final TelegraphInstance instance) {
        if (id == null || id.isEmpty() || instance == null) {
            return;
        }
        instance.id = id;
        ACTIVE.put(id, instance);
    }

    public static void remove(final String id) {
        if (id != null) {
            ACTIVE.remove(id);
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static boolean hasTelegraphs() {
        return !ACTIVE.isEmpty();
    }

    public static Collection<TelegraphInstance> getTelegraphs() {
        return ACTIVE.values();
    }

    public static void tickClient() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        final World world = Minecraft.getInstance().level;
        ACTIVE.values().removeIf(instance -> {
            if (instance.followEntityId >= 0 && world != null) {
                final Entity entity = world.getEntity(instance.followEntityId);
                if (entity != null) {
                    instance.prevX = instance.x;
                    instance.prevY = instance.y;
                    instance.prevZ = instance.z;
                    instance.prevYaw = instance.yaw;
                    instance.x = entity.getX();
                    instance.z = entity.getZ();
                    instance.y = TelegraphInstance.findGroundY(
                            world, entity.getX(), entity.getY(), entity.getZ(), instance.groundSearchRange);
                    instance.yaw = entity.yRot;
                }
            } else {
                instance.prevX = instance.x;
                instance.prevY = instance.y;
                instance.prevZ = instance.z;
                instance.prevYaw = instance.yaw;
            }
            instance.remainingTicks--;
            final int warnAt = Math.max(1, instance.totalTicks / 4);
            if (!instance.warning && instance.remainingTicks <= warnAt) {
                instance.warning = true;
            }
            return instance.remainingTicks <= 0;
        });
    }
}
