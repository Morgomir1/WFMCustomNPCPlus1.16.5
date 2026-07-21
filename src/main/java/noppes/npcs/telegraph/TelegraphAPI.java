package noppes.npcs.telegraph;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.wrapper.WorldWrapper;

/**
 * JS/Java compatibility wrapper over {@link com.wfm.telegraph.TelegraphAPI}.
 *
 * <pre>
 * var TelegraphAPI = Java.type("noppes.npcs.telegraph.TelegraphAPI");
 * var id = TelegraphAPI.circle(npc, x, y, z, radius, durationTicks, 0x80FF3030);
 * TelegraphAPI.cone(npc, x, y, z, yaw, length, halfAngleDeg, durationTicks, color);
 * TelegraphAPI.line(npc, x, y, z, yaw, length, width, durationTicks, color);
 * TelegraphAPI.follow(id, entity);
 * TelegraphAPI.remove(id);
 * </pre>
 */
public final class TelegraphAPI {
    public static final int DEFAULT_COLOR = com.wfm.telegraph.TelegraphAPI.DEFAULT_COLOR;
    public static final int DEFAULT_WARNING = com.wfm.telegraph.TelegraphAPI.DEFAULT_WARNING;

    private TelegraphAPI() {
    }

    public static String circle(
            final ICustomNpc npc,
            final double x,
            final double y,
            final double z,
            final double radius,
            final int durationTicks,
            final int color) {
        return com.wfm.telegraph.TelegraphAPI.circle(worldOf(npc), x, y, z, radius, durationTicks, color);
    }

    public static String ring(
            final ICustomNpc npc,
            final double x,
            final double y,
            final double z,
            final double outerRadius,
            final double innerRadius,
            final int durationTicks,
            final int color) {
        return com.wfm.telegraph.TelegraphAPI.ring(
                worldOf(npc), x, y, z, outerRadius, innerRadius, durationTicks, color);
    }

    public static String square(
            final ICustomNpc npc,
            final double x,
            final double y,
            final double z,
            final double halfSize,
            final int durationTicks,
            final int color) {
        return com.wfm.telegraph.TelegraphAPI.square(worldOf(npc), x, y, z, halfSize, durationTicks, color);
    }

    public static String cone(
            final ICustomNpc npc,
            final double x,
            final double y,
            final double z,
            final float yaw,
            final double length,
            final double halfAngleDeg,
            final int durationTicks,
            final int color) {
        return com.wfm.telegraph.TelegraphAPI.cone(
                worldOf(npc), x, y, z, yaw, length, halfAngleDeg, durationTicks, color);
    }

    public static String line(
            final ICustomNpc npc,
            final double x,
            final double y,
            final double z,
            final float yaw,
            final double length,
            final double width,
            final int durationTicks,
            final int color) {
        return com.wfm.telegraph.TelegraphAPI.line(
                worldOf(npc), x, y, z, yaw, length, width, durationTicks, color);
    }

    public static void follow(final String id, final IEntity entity) {
        if (id == null || id.isEmpty() || entity == null) {
            return;
        }
        try {
            final Entity mc = entity.getMCEntity();
            com.wfm.telegraph.TelegraphAPI.follow(id, mc);
        } catch (final Exception ignored) {
        }
    }

    public static void followNpc(final String id, final ICustomNpc npc) {
        follow(id, npc);
    }

    public static void remove(final String id) {
        com.wfm.telegraph.TelegraphAPI.remove(id);
    }

    public static void removeNear(final ICustomNpc npc, final String id) {
        try {
            com.wfm.telegraph.TelegraphAPI.removeNear(npc == null ? null : npc.getMCEntity(), id);
        } catch (final Exception e) {
            com.wfm.telegraph.TelegraphAPI.remove(id);
        }
    }

    private static World worldOf(final ICustomNpc npc) {
        if (npc == null) {
            return null;
        }
        try {
            if (npc.getWorld() instanceof WorldWrapper) {
                return ((WorldWrapper) npc.getWorld()).getMCWorld();
            }
            final Entity mc = npc.getMCEntity();
            return mc == null ? null : mc.level;
        } catch (final Exception e) {
            return null;
        }
    }
}
