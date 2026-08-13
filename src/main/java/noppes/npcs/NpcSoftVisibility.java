package noppes.npcs;

import net.minecraft.entity.player.PlayerEntity;
import noppes.npcs.client.ClientNpcVisibility;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;

/**
 * Soft per-player NPC hide from Display Visible + Availability.
 * <p>
 * Display {@code Availability} is the hide condition for every option type (dialog, quest,
 * faction, daytime, scoreboard, level) — NPC is hidden when {@code isAvailable} is true.
 * {@code Visible=Yes/No} only applies when Availability has no options.
 */
public final class NpcSoftVisibility {
    private NpcSoftVisibility() {
    }

    public static boolean isDisplayVisibleTo(final EntityNPCInterface npc, final PlayerEntity player) {
        if (npc == null) {
            return true;
        }
        return isDisplayVisibleTo(npc.display, player);
    }

    public static boolean isDisplayVisibleTo(final DataDisplay display, final PlayerEntity player) {
        if (display == null || player == null) {
            return true;
        }
        if (display.availability.hasOptions()) {
            return !display.availability.isAvailable(player);
        }
        return display.getVisible() != 1;
    }

    /**
     * True when this NPC should be soft-hidden from the player.
     */
    public static boolean isHiddenFrom(final EntityNPCInterface npc, final PlayerEntity player) {
        if (player == null || npc == null) {
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
     * Expanded {@link EntityNPCInterface#isInvisibleTo}: classic Visible=No, server hide
     * flag, and live Availability (all condition types).
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
        if (npc.level != null && npc.level.isClientSide
                && ClientNpcVisibility.isHiddenFromLocalPlayer(npc.getId())) {
            return true;
        }
        return !isDisplayVisibleTo(npc, player);
    }
}
