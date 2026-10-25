package noppes.npcs.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.controllers.IScriptHandler;
import noppes.npcs.controllers.ScriptContainer;
import noppes.npcs.controllers.data.PlayerScriptData;
import noppes.npcs.entity.data.DataScript;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Server log of how bosses die and come back: deaths, every {@link EntityNPCInterface#reset()} (the one call that
 * raises a dead NPC) and clones spawned anew, which is how bosses that are removed on death return.
 * <p>
 * Upstream logs none of it, so a boss raised early with the wand or the remote left no trace. Each caller names
 * itself through {@link #expectReset} or {@link #expectSpawn} right before the call; a reset is written at the head
 * of {@code reset()}, while the NPC still shows its state before it. What is done by hand (wand menu, remote,
 * commands, the cloner, {@code /kill} on a corpse) is logged for any NPC, the respawn timer, scripts and spawners
 * only for bosses.
 */
@Mod.EventBusSubscriber(modid = "customnpcs", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NpcRespawnLog {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String PREFIX = "[NPC respawn] ";
    // NPCs with a boss bar or with at least this respawn time count as bosses.
    private static final int BOSS_RESPAWN_SECONDS = 30 * 60;

    // Server thread only: set right before the call, taken by it, dropped at the end of the tick in any case.
    private static String pendingResetCause;
    private static boolean pendingResetByHand;
    private static String pendingSpawnCause;
    private static boolean pendingSpawnByHand;

    private NpcRespawnLog() {
    }

    /** Names the cause of the reset that follows. {@code byHand} logs it for every NPC, not only for bosses. */
    public static void expectReset(final String cause, final boolean byHand) {
        pendingResetCause = cause;
        pendingResetByHand = byHand;
    }

    /** Names the cause of the clone spawn that follows. {@code byHand} logs it for every NPC. */
    public static void expectSpawn(final String cause, final boolean byHand) {
        pendingSpawnCause = cause;
        pendingSpawnByHand = byHand;
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        // A cause named before a call that then threw must not stick to the next NPC.
        if (event.phase == TickEvent.Phase.END) {
            pendingResetCause = null;
            pendingResetByHand = false;
            pendingSpawnCause = null;
            pendingSpawnByHand = false;
        }
    }

    public static void onReset(final EntityNPCInterface npc) {
        // The client resets its copy on its own and never names a cause.
        if (npc.level == null || npc.level.isClientSide) {
            return;
        }
        String cause = pendingResetCause;
        final boolean byHand = pendingResetByHand;
        pendingResetCause = null;
        pendingResetByHand = false;
        if (!byHand && !isBoss(npc)) {
            return;
        }
        if (cause == null) {
            cause = "unknown caller " + resetCaller();
        }
        LOGGER.info(PREFIX + describe(npc) + " reset by " + cause + ": " + stateBeforeReset(npc));
    }

    public static void onSpawn(final Entity entity) {
        String cause = pendingSpawnCause;
        final boolean byHand = pendingSpawnByHand;
        pendingSpawnCause = null;
        pendingSpawnByHand = false;
        if (!(entity instanceof EntityNPCInterface) || entity.level == null || entity.level.isClientSide) {
            return;
        }
        final EntityNPCInterface npc = (EntityNPCInterface) entity;
        if (!byHand && !isBoss(npc)) {
            return;
        }
        if (cause == null) {
            cause = ScriptContainer.Current != null ? scriptCause() : "caller " + spawnCaller();
        }
        // Its respawn settings are not logged: scripts change them right after the spawn.
        LOGGER.info(PREFIX + describe(npc) + " spawned as a clone by " + cause);
    }

    public static void onDeath(final EntityNPCInterface npc, final DamageSource source) {
        if (npc.level == null || npc.level.isClientSide || !isBoss(npc)) {
            return;
        }
        LOGGER.info(PREFIX + describe(npc) + " died, killed by " + killer(npc, source) + ": " + respawnAfterDeath(npc));
    }

    /** The script running now, for resets and spawns made through the script API. */
    public static String scriptCause() {
        final ScriptContainer script = ScriptContainer.Current;
        if (script == null) {
            return "script";
        }
        final IScriptHandler handler = script.getHandler();
        String line;
        if (handler instanceof DataScript) {
            line = "script of NPC " + describe(((DataScript) handler).getNpc());
        } else if (handler instanceof PlayerScriptData) {
            final IPlayer player = ((PlayerScriptData) handler).getPlayer();
            line = player == null ? "global player script" : "player script of " + player.getName();
        } else if (handler instanceof TileEntity) {
            line = "script of block at " + coords(((TileEntity) handler).getBlockPos());
        } else {
            line = "script " + (handler == null ? "?" : handler.getClass().getSimpleName());
        }
        line += ", event " + ScriptContainer.getCurrentType();
        final String player = eventPlayer(ScriptContainer.getCurrentEvent());
        if (player != null && !(handler instanceof PlayerScriptData)) {
            line += ", player " + player;
        }
        return line;
    }

    // Item, dialog and interact events carry the player in a public field named player.
    private static String eventPlayer(final Object event) {
        if (event == null) {
            return null;
        }
        try {
            final Object player = event.getClass().getField("player").get(event);
            return player instanceof IPlayer ? ((IPlayer) player).getName() : null;
        } catch (final ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static boolean isBoss(final EntityNPCInterface npc) {
        return npc.display.getBossbar() != 0 || npc.stats.respawnTime >= BOSS_RESPAWN_SECONDS;
    }

    private static String describe(final EntityNPCInterface npc) {
        if (npc == null) {
            return "?";
        }
        final String dimension = npc.level == null ? "?" : npc.level.dimension().location().toString();
        return "\"" + npc.display.getName() + "\" at " + coords(npc.blockPosition()) + " in " + dimension;
    }

    private static String coords(final BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static String stateBeforeReset(final EntityNPCInterface npc) {
        if (!npc.isKilled()) {
            return "was alive, " + Math.round(npc.getHealth()) + "/" + Math.round(npc.getMaxHealth()) + " HP";
        }
        if (npc.killedtime <= 0L) {
            return "was dead, no respawn time set";
        }
        final long left = npc.killedtime - System.currentTimeMillis();
        final String due = "was dead, respawn time " + time(npc.killedtime);
        if (left > 0L) {
            return due + ", " + duration(left) + " EARLY";
        }
        return due + ", " + duration(-left) + " after it";
    }

    private static String respawnAfterDeath(final EntityNPCInterface npc) {
        if (npc.stats.spawnCycle == 3 || npc.stats.spawnCycle == 4) {
            return "removed, does not respawn";
        }
        // killedtime is set a tick later in remove(), from the respawn time the death scripts have just chosen.
        final long left = npc.stats.respawnTime * 1000L;
        return "respawn time " + time(System.currentTimeMillis() + left) + ", in " + duration(left)
                + (npc.stats.spawnCycle == 1 ? ", by day only" : npc.stats.spawnCycle == 2 ? ", by night only" : "");
    }

    private static String killer(final EntityNPCInterface npc, final DamageSource source) {
        final String type = source == null ? "?" : source.msgId;
        final LivingEntity credit = npc.getKillCredit();
        Entity entity = source == null ? null : NoppesUtilServer.GetDamageSourcee(source);
        if (entity == null) {
            entity = credit;
        }
        if (entity == null) {
            return type;
        }
        final String name = entity instanceof EntityNPCInterface
                ? "NPC \"" + ((EntityNPCInterface) entity).display.getName() + "\""
                : entity.getName().getString();
        String line = name + " (" + type + ")";
        if (credit instanceof PlayerEntity && credit != entity) {
            line += ", kill credit " + credit.getName().getString();
        }
        return line;
    }

    private static String resetCaller() {
        final StackTraceElement[] stack = new Throwable().getStackTrace();
        for (int i = 0; i + 1 < stack.length; i++) {
            if ("reset".equals(stack[i].getMethodName())
                    && stack[i].getClassName().startsWith("noppes.npcs.entity.Entity")) {
                return frame(stack[i + 1]);
            }
        }
        return "?";
    }

    // The first frame past the clone spawning chain: the spawner job, the structure spawner, Java ability code.
    private static String spawnCaller() {
        for (final StackTraceElement element : new Throwable().getStackTrace()) {
            final String name = element.getClassName();
            if (!name.equals(NpcRespawnLog.class.getName())
                    && !name.equals("noppes.npcs.packets.server.SPacketToolMobSpawner")
                    && !name.equals("noppes.npcs.controllers.ServerCloneController")
                    && !name.equals("noppes.npcs.api.wrapper.WorldWrapper")
                    && !name.equals("noppes.npcs.api.wrapper.EntityWrapper")) {
                return frame(element);
            }
        }
        return "?";
    }

    private static String frame(final StackTraceElement element) {
        return element.getClassName() + "." + element.getMethodName();
    }

    private static String time(final long millis) {
        return Instant.ofEpochMilli(millis).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static String duration(final long millis) {
        final long seconds = millis / 1000L;
        if (seconds >= 3600L) {
            return seconds / 3600L + " h " + String.format("%02d", seconds % 3600L / 60L) + " min";
        }
        if (seconds >= 60L) {
            return seconds / 60L + " min " + String.format("%02d", seconds % 60L) + " s";
        }
        return seconds + " s";
    }
}
