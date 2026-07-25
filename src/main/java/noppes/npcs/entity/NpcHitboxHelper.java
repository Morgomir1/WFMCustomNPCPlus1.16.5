package noppes.npcs.entity;

import net.minecraft.entity.EntitySize;
import net.minecraft.entity.Pose;

/**
 * Absolute hitbox from {@link noppes.npcs.entity.data.DataDisplay} (width/height floats).
 * Does not use display Size — Size is visual model scale only.
 */
public final class NpcHitboxHelper {
    private NpcHitboxHelper() {
    }

    public static EntitySize getDimensions(final EntityNPCInterface npc, final Pose pose) {
        EntitySize size = EntitySize.scalable(npc.display.getHitboxWidth(), npc.display.getHitboxHeight());

        if (npc.currentAnimation == 2 || npc.currentAnimation == 7 || npc.deathTime > 0) {
            size = EntitySize.scalable(0.8f, 0.4f);
        } else if (npc.isPassenger() || npc.currentAnimation == 1) {
            size = size.scale(1.0f, 0.77f);
        }

        if (npc.display.getHitboxState() == 1 || (npc.isKilled() && npc.stats.hideKilledBody)) {
            size = EntitySize.scalable(1.0E-5f, size.height);
        }

        if (size.width / 2.0f > npc.level.getMaxEntityRadius()) {
            npc.level.increaseMaxEntityRadius(size.width / 2.0);
        }
        return size;
    }
}
