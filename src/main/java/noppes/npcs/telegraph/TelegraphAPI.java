package noppes.npcs.telegraph;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
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
 * // Moving caster (Keeper pattern): follow baked into first spawn packet
 * var id2 = TelegraphAPI.circleFollow(npc, x, y, z, radius, durationTicks, TelegraphAPI.DEFAULT_COLOR);
 * TelegraphAPI.cone(npc, x, y, z, yaw, length, halfAngleDeg, durationTicks, color);
 * TelegraphAPI.coneTruncated(npc, x, y, z, yaw, innerRadius, outerRadius, halfAngleDeg, durationTicks, color);
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
        final World world = worldOf(npc);
        final double groundY = resolveGroundY(world, x, y, z);
        return com.wfm.telegraph.TelegraphAPI.circle(world, x, groundY, z, radius, durationTicks, color);
    }

    /**
     * Circle at an already-resolved Y (no upward ground scan).
     * Use when Y came from {@code AbilityCombatHelper.findFeetGroundY} so a low
     * ceiling / overhang cannot steal the telegraph off the floor.
     */
    public static String circleAt(
            final ICustomNpc npc,
            final double x,
            final double y,
            final double z,
            final double radius,
            final int durationTicks,
            final int color) {
        return com.wfm.telegraph.TelegraphAPI.circle(worldOf(npc), x, y, z, radius, durationTicks, color);
    }

    /**
     * Keeper-style: one circle at solid ground Y, follow baked into first spawn packet.
     */
    public static String circleFollow(
            final ICustomNpc npc,
            final double x,
            final double y,
            final double z,
            final double radius,
            final int durationTicks,
            final int color) {
        final World world = worldOf(npc);
        final Entity mc = mcOf(npc);
        final double groundY = resolveGroundY(world, x, y, z);
        return com.wfm.telegraph.TelegraphAPI.circleFollow(
                world, x, groundY, z, radius, durationTicks, color, mc);
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
        final double groundY = resolveGroundY(world, x, y, z);
        return com.wfm.telegraph.TelegraphAPI.ring(
                world, x, groundY, z, outerRadius, innerRadius, durationTicks, color);
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
        final double groundY = resolveGroundY(world, x, y, z);
        return com.wfm.telegraph.TelegraphAPI.square(world, x, groundY, z, halfSize, durationTicks, color);
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
        final double groundY = resolveGroundY(world, x, y, z);
        return com.wfm.telegraph.TelegraphAPI.cone(
                world, x, groundY, z, yaw, length, halfAngleDeg, durationTicks, color);
    }

    /**
     * Усечённый конус (кольцевой сектор): заливка между inner/outer радиусами от вершины, без острия.
     */
    public static String coneTruncated(
            final ICustomNpc npc,
            final double x,
            final double y,
            final double z,
            final float yaw,
            final double innerRadius,
            final double outerRadius,
            final double halfAngleDeg,
            final int durationTicks,
            final int color) {
        final World world = worldOf(npc);
        final double groundY = resolveGroundY(world, x, y, z);
        return com.wfm.telegraph.TelegraphAPI.coneTruncated(
                world, x, groundY, z, yaw, innerRadius, outerRadius, halfAngleDeg, durationTicks, color);
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
        final double groundY = resolveGroundY(world, x, y, z);
        return com.wfm.telegraph.TelegraphAPI.line(
                world, x, groundY, z, yaw, length, width, durationTicks, color);
    }

    public static void follow(final String id, final IEntity entity) {
        if (id == null || id.isEmpty() || entity == null) {
            return;
        }
        final Entity mc = mcOf(entity);
        if (mc == null) {
            return;
        }
        com.wfm.telegraph.TelegraphAPI.follow(id, mc);
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

    /**
     * Same solid-ground Y as KeeperOfSecretsEntity / wfm-attack-telegraphs skill,
     * but does not prefer blocks above {@code startY} (arena ceilings / overhangs).
     */
    public static double resolveGroundY(final World world, final double x, final double startY, final double z) {
        if (world == null) {
            return startY;
        }
        final int from = MathHelper.floor(startY);
        final int minY = Math.max(0, MathHelper.floor(startY) - 8);
        final BlockPos.Mutable pos = new BlockPos.Mutable(x, from, z);
        for (int y = from; y >= minY; y--) {
            pos.setY(y);
            final BlockState state = world.getBlockState(pos);
            if (state.getMaterial().isSolid() && !state.getCollisionShape(world, pos).isEmpty()) {
                final double top = y + state.getCollisionShape(world, pos).max(net.minecraft.util.Direction.Axis.Y);
                if (top > startY + 0.05D) {
                    continue;
                }
                return top + 0.05D;
            }
        }
        return startY;
    }

    private static Entity mcOf(final IEntity entity) {
        if (entity == null) {
            return null;
        }
        try {
            return entity.getMCEntity();
        } catch (final Exception e) {
            return null;
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
