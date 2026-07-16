package noppes.npcs.script;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.GameType;
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
 *
 * Player checks use {@link ServerWorld#players()} + {@link GameType} (not AABB entity scan /
 * {@code abilities.instabuild}), which is more reliable on Arclight/Velocity.
 */
public final class CloneStructureSpawnerHelper {
    private CloneStructureSpawnerHelper() {
    }

    /**
     * @return true if any survival/adventure (playable) player is within range
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
        final double rangeSq = range * range;
        for (final ServerPlayerEntity player : level.players()) {
            if (player == null) {
                continue;
            }
            if (player.distanceToSqr(x, y, z) > rangeSq) {
                continue;
            }
            if (EntityCloneStructureSpawner.isPlayablePlayer(player)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Debug listing of nearby players (GameType + instabuild) for idle logs.
     */
    public static String describeNearbyPlayers(
            final IWorld world,
            final double x,
            final double y,
            final double z,
            final double range) {
        final ServerWorld level = unwrap(world);
        if (level == null) {
            return "world=null";
        }
        final double rangeSq = range * range;
        final StringBuilder sb = new StringBuilder();
        int n = 0;
        for (final ServerPlayerEntity player : level.players()) {
            if (player == null) {
                continue;
            }
            final double dSq = player.distanceToSqr(x, y, z);
            if (dSq > rangeSq) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            final GameType gt = player.gameMode.getGameModeForPlayer();
            sb.append(player.getGameProfile().getName())
                    .append(" dist=")
                    .append(String.format("%.1f", Math.sqrt(dSq)))
                    .append(" gt=")
                    .append(gt.getName())
                    .append(" instabuild=")
                    .append(player.abilities.instabuild)
                    .append(" spectator=")
                    .append(player.isSpectator())
                    .append(" playable=")
                    .append(EntityCloneStructureSpawner.isPlayablePlayer(player));
            n++;
        }
        if (n == 0) {
            return "none of " + level.players().size() + " online within " + (int) range;
        }
        return n + " within range: " + sb;
    }

    /**
     * Arms {@link EntityCloneStructureSpawner} entities that are still UNARMED (ManualPlacement).
     * Already-armed spawners are left alone (not re-counted). {@link EntityCloneStructureSpawner#arm()}
     * also calls {@code trySpawnNow()} immediately.
     *
     * @return number of spawners freshly armed (UNARMED→ARMED), not already-armed recounts
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
        int freshlyArmed = 0;
        int alreadyArmed = 0;
        int spawnedNow = 0;
        for (final EntityCloneStructureSpawner spawner : list) {
            if (spawner == null || !spawner.isAlive()) {
                continue;
            }
            final boolean wasUnarmed = spawner.isManualPlacement();
            if (!wasUnarmed) {
                alreadyArmed++;
                // Retry spawn (e.g. creative left); arm() not called so count stays 0
                if (!spawner.isFailed() && spawner.trySpawnNow()) {
                    spawnedNow++;
                    LogWriter.info("CloneStructureSpawnerHelper: already-ARMED spawned on retry"
                            + " clone=" + spawner.getCloneName()
                            + " tab=" + spawner.getCloneTab()
                            + " at " + spawner.blockPosition());
                } else if (spawner.isAlive()) {
                    LogWriter.info("CloneStructureSpawnerHelper: already ARMED still present"
                            + " blockReason=" + spawner.describeSpawnBlockReason()
                            + " clone=" + spawner.getCloneName()
                            + " tab=" + spawner.getCloneTab()
                            + " at " + spawner.blockPosition());
                }
                continue;
            }
            spawner.arm();
            freshlyArmed++;
            if (!spawner.isAlive()) {
                spawnedNow++;
                LogWriter.info("CloneStructureSpawnerHelper: freshly armed and SPAWNED immediately"
                        + " clone=" + spawner.getCloneName()
                        + " tab=" + spawner.getCloneTab()
                        + " at " + spawner.blockPosition());
            } else {
                LogWriter.info("CloneStructureSpawnerHelper: freshly armed clone="
                        + spawner.getCloneName()
                        + " tab=" + spawner.getCloneTab()
                        + " blockReason=" + spawner.describeSpawnBlockReason()
                        + " at " + spawner.blockPosition());
            }
        }
        LogWriter.info("CloneStructureSpawnerHelper: near " + fmt(x, y, z)
                + " freshlyArmed=" + freshlyArmed
                + " alreadyArmed=" + alreadyArmed
                + " spawnedNow=" + spawnedNow
                + " found=" + list.size());
        return freshlyArmed;
    }

    /**
     * If a playable (survival/adventure) player is near the center, arms spawners in spawnerRange.
     *
     * @return number of freshly armed spawners, or 0 if no playable player / no unarmed spawners
     */
    public static int armIfPlayableNearby(
            final IWorld world,
            final double x,
            final double y,
            final double z,
            final double playerRange,
            final double spawnerRange) {
        if (!hasPlayablePlayerNearby(world, x, y, z, playerRange)) {
            LogWriter.debug("CloneStructureSpawnerHelper: no playable player within "
                    + playerRange + " of " + fmt(x, y, z)
                    + " | " + describeNearbyPlayers(world, x, y, z, playerRange));
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
