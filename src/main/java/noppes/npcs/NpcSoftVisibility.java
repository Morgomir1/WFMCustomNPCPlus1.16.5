package noppes.npcs;

import net.minecraft.entity.player.PlayerEntity;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Soft per-player NPC hide.
 * <p>
 * Client render/pick evaluates Availability locally (faction, daytime, dialogs, …) —
 * same rules as {@code DataDisplay.isVisibleTo}. Relying only on rare
 * {@code VisibilityController} packets left conditions stuck after soft-hide replaced
 * {@code removeEntity}.
 */
public final class NpcSoftVisibility {
    private NpcSoftVisibility() {
    }

    /**
     * Same predicate as {@link noppes.npcs.entity.data.DataDisplay#isVisibleTo} but for any
     * {@link PlayerEntity} (client + server). Uses {@link noppes.npcs.controllers.data.Availability#isAvailable}.
     */
    public static boolean isDisplayVisibleTo(final EntityNPCInterface npc, final PlayerEntity player) {
        final boolean available = npc.display.availability.isAvailable(player);
        if (npc.display.getVisible() == 1) {
            return !available;
        }
        return available;
    }

    /**
     * True when EnableInvisibleNpcs would soft-hide this NPC from the player
     * ({@code !display.isVisibleTo} and not spectator / wand).
     */
    public static boolean isHiddenFrom(final EntityNPCInterface npc, final PlayerEntity player) {
        if (!CustomNpcs.EnableInvisibleNpcs || player == null || npc == null) {
            return false;
        }
        if (player.isSpectator()) {
            return false;
        }
        if (player.getMainHandItem().getItem() == CustomItems.wand) {
            return false;
        }
        return !isDisplayVisibleTo(npc, player);
    }

    /**
     * Expanded {@link EntityNPCInterface#isInvisibleTo}: classic Display Visible=No, plus
     * live Availability evaluation on client and server.
     */
    public static boolean isInvisibleTo(final EntityNPCInterface npc, final PlayerEntity player) {
        if (player == null || npc == null) {
            return false;
        }
        if (player.getMainHandItem().getItem() == CustomItems.wand) {
            return false;
        }
        if (player.isSpectator()) {
            return false;
        }
        // Classic Visible=No without Availability options (no EnableInvisibleNpcs needed)
        if (npc.display.getVisible() == 1 && !npc.display.availability.hasOptions()) {
            return true;
        }
        if (!CustomNpcs.EnableInvisibleNpcs) {
            return false;
        }
        return !isDisplayVisibleTo(npc, player);
    }
}
