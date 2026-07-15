package noppes.npcs.script;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.server.ServerWorld;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.wrapper.WorldWrapper;
import noppes.npcs.entity.EntityCloneStructureSpawner;
import noppes.npcs.shared.common.util.LogWriter;

import java.util.List;

/**
 * Reliable arming of {@link EntityCloneStructureSpawner} from JS block scripts.
 * Avoids Nashorn {@code instanceof}/classloader pitfalls with getNearbyEntities + getMCEntity.
 *
 * <pre>
 * var Helper = Java.type("noppes.npcs.script.CloneStructureSpawnerHelper");
 * Helper.armNearby(world, x, y, z, 48);
 * </pre>
 */
public final class CloneStructureSpawnerHelper {
    private CloneStructureSpawnerHelper() {
    }

    /**
     * @return true if any non-creative, non-spectator player is within range
     */
    public static boolean hasPlayablePlayerNearby(
            final IWorld world,
            final double x,
            final double y,
            final double z,
            final double range) {
        final ServerWorld level = unwrap(world);
        if (level == null) {
            return false;
        }
        final AxisAlignedBB box = new AxisAlignedBB(x - range, y - range, z - range, x + range, y + range, z + range);
        for (final PlayerEntity player : level.getEntitiesOfClass(PlayerEntity.class, box)) {
            if (player != null && !player.isCreative() && !player.isSpectator()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Arms all {@link EntityCloneStructureSpawner} entities in range (clears failed, ManualPlacement=false).
     *
     * @return number of spawners armed
     */
    public static int armNearby(
            final IWorld world,
            final double x,
            final double y,
            final double z,
            final double range) {
        final ServerWorld level = unwrap(world);
        if (level == null) {
            LogWriter.warn("CloneStructureSpawnerHelper.armNearby: world is null/client at "
                    + x + "," + y + "," + z);
            return 0;
        }
        final AxisAlignedBB box = new AxisAlignedBB(x - range, y - range, z - range, x + range, y + range, z + range);
        final List<EntityCloneStructureSpawner> list =
                level.getEntitiesOfClass(EntityCloneStructureSpawner.class, box);
        if (list == null || list.isEmpty()) {
            LogWriter.info("CloneStructureSpawnerHelper: no spawners within " + range
                    + " of " + fmt(x, y, z));
            return 0;
        }
        int armed = 0;
        for (final EntityCloneStructureSpawner spawner : list) {
            if (spawner == null || !spawner.isAlive()) {
                continue;
            }
            final boolean wasManual = spawner.isManualPlacement();
            spawner.arm();
            armed++;
            LogWriter.info("CloneStructureSpawnerHelper: armed spawner clone="
                    + spawner.getCloneName()
                    + " tab=" + spawner.getCloneTab()
                    + " wasUnarmed=" + wasManual
                    + " at " + spawner.blockPosition());
        }
        LogWriter.info("CloneStructureSpawnerHelper: armed " + armed + " spawner(s) near " + fmt(x, y, z));
        return armed;
    }

    /**
     * If a playable (survival/adventure) player is near the center, arms spawners in spawnerRange.
     *
     * @return number of spawners armed, or 0 if no playable player / no spawners
     */
    public static int armIfPlayableNearby(
            final IWorld world,
            final double x,
            final double y,
            final double z,
            final double playerRange,
            final double spawnerRange) {
        if (!hasPlayablePlayerNearby(world, x, y, z, playerRange)) {
            LogWriter.debug("CloneStructureSpawnerHelper: no survival/adventure player within "
                    + playerRange + " of " + fmt(x, y, z));
            return 0;
        }
        return armNearby(world, x, y, z, spawnerRange);
    }

    private static ServerWorld unwrap(final IWorld world) {
        if (world == null) {
            return null;
        }
        if (world instanceof WorldWrapper) {
            return ((WorldWrapper) world).getMCWorld();
        }
        try {
            final Object mc = world.getMCWorld();
            if (mc instanceof ServerWorld) {
                return (ServerWorld) mc;
            }
        } catch (final Exception ignored) {
        }
        return null;
    }

    private static String fmt(final double x, final double y, final double z) {
        return "(" + (int) x + "," + (int) y + "," + (int) z + ")";
    }
}
