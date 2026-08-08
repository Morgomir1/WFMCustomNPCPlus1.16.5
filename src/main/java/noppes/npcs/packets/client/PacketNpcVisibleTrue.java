package noppes.npcs.packets.client;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.client.ClientNpcVisibility;
import noppes.npcs.shared.common.PacketBasic;

/**
 * Soft-show: clear local hide flag only. Never spawns / re-adds the entity —
 * vanilla entity tracking already keeps it on the client (duplicates came from SpawnEntity).
 */
public class PacketNpcVisibleTrue extends PacketBasic {
    private final int id;

    /** Kept for jar call sites: {@code new PacketNpcVisibleTrue(entity)}. */
    public PacketNpcVisibleTrue(final net.minecraft.entity.Entity entity) {
        this.id = entity.getId();
    }

    public PacketNpcVisibleTrue(final int id) {
        this.id = id;
    }

    public static void encode(final PacketNpcVisibleTrue msg, final PacketBuffer buf) {
        buf.writeInt(msg.id);
    }

    public static PacketNpcVisibleTrue decode(final PacketBuffer buf) {
        return new PacketNpcVisibleTrue(buf.readInt());
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void handle() {
        ClientNpcVisibility.setHiddenFromLocalPlayer(this.id, false);
    }
}
