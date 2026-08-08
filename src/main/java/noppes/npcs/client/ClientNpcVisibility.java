package noppes.npcs.client;

import java.util.HashSet;
import java.util.Set;

/**
 * Soft-hide for Availability / Invisible NPCs (written/read on the client).
 * Entity stays in the client world; render/interact code checks this flag.
 */
public final class ClientNpcVisibility {
    private static final Set<Integer> HIDDEN_FROM_LOCAL = new HashSet<>();

    private ClientNpcVisibility() {
    }

    public static void setHiddenFromLocalPlayer(final int entityId, final boolean hidden) {
        if (hidden) {
            HIDDEN_FROM_LOCAL.add(entityId);
        } else {
            HIDDEN_FROM_LOCAL.remove(entityId);
        }
    }

    public static boolean isHiddenFromLocalPlayer(final int entityId) {
        return HIDDEN_FROM_LOCAL.contains(entityId);
    }

    public static void clear() {
        HIDDEN_FROM_LOCAL.clear();
    }
}
