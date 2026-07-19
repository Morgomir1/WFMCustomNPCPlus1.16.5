package noppes.npcs.telegraph;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.wrapper.WorldWrapper;

/**
 * JS/Java entry points for temporary attack telegraphs.
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
    public static final int DEFAULT_COLOR = 0x80FF3030;
    public static final int DEFAULT_WARNING = 0xC0FF0000;

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
        final World world = worldOf(npc);
        if (world == null) {
            return "";
        }
        final TelegraphInstance inst = new TelegraphInstance(
                TelegraphType.CIRCLE, world, x, y, z, 0, durationTicks);
        inst.radius = (float) Math.max(0.1, radius);
        inst.color = color;
        inst.warningColor = withAlpha(color, 0xC0);
        return TelegraphServer.spawn(world, inst);
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
        final World world = worldOf(npc);
        if (world == null) {
            return "";
        }
        final TelegraphInstance inst = new TelegraphInstance(
                TelegraphType.RING, world, x, y, z, 0, durationTicks);
        inst.radius = (float) Math.max(0.1, outerRadius);
        inst.innerRadius = (float) Math.max(0.0, Math.min(innerRadius, outerRadius - 0.05));
        inst.color = color;
        inst.warningColor = withAlpha(color, 0xC0);
        return TelegraphServer.spawn(world, inst);
    }

    public static String square(
            final ICustomNpc npc,
            final double x,
            final double y,
            final double z,
            final double halfSize,
            final int durationTicks,
            final int color) {
        final World world = worldOf(npc);
        if (world == null) {
            return "";
        }
        final TelegraphInstance inst = new TelegraphInstance(
                TelegraphType.SQUARE, world, x, y, z, 0, durationTicks);
        inst.radius = (float) Math.max(0.1, halfSize);
        inst.color = color;
        inst.warningColor = withAlpha(color, 0xC0);
        return TelegraphServer.spawn(world, inst);
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
        final World world = worldOf(npc);
        if (world == null) {
            return "";
        }
        final TelegraphInstance inst = new TelegraphInstance(
                TelegraphType.CONE, world, x, y, z, yaw, durationTicks);
        inst.length = (float) Math.max(0.1, length);
        inst.angle = (float) Math.max(1.0, halfAngleDeg * 2.0);
        inst.color = color;
        inst.warningColor = withAlpha(color, 0xC0);
        return TelegraphServer.spawn(world, inst);
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
        final World world = worldOf(npc);
        if (world == null) {
            return "";
        }
        final TelegraphInstance inst = new TelegraphInstance(
                TelegraphType.LINE, world, x, y, z, yaw, durationTicks);
        inst.length = (float) Math.max(0.1, length);
        inst.width = (float) Math.max(0.1, width);
        inst.color = color;
        inst.warningColor = withAlpha(color, 0xC0);
        return TelegraphServer.spawn(world, inst);
    }

    public static void follow(final String id, final IEntity entity) {
        if (id == null || id.isEmpty() || entity == null) {
            return;
        }
        try {
            final Entity mc = entity.getMCEntity();
            TelegraphServer.follow(id, mc);
        } catch (final Exception ignored) {
        }
    }

    public static void followNpc(final String id, final ICustomNpc npc) {
        follow(id, npc);
    }

    public static void remove(final String id) {
        TelegraphServer.remove(id);
    }

    public static void removeNear(final ICustomNpc npc, final String id) {
        try {
            TelegraphServer.removeNear(npc == null ? null : npc.getMCEntity(), id);
        } catch (final Exception e) {
            TelegraphServer.remove(id);
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

    private static int withAlpha(final int color, final int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
