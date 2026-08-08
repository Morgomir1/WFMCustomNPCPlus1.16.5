package noppes.npcs.packets.client;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.client.ClientNpcVisibility;
import noppes.npcs.shared.common.PacketBasic;

/**
 * Soft-hide: mark NPC hidden from the local player. Never removes the entity from the client world.
 */
public class PacketNpcVisibleFalse extends PacketBasic {
    private final int id;

    public PacketNpcVisibleFalse(final int id) {
        this.id = id;
    }

    public static void encode(final PacketNpcVisibleFalse msg, final PacketBuffer buf) {
        buf.writeInt(msg.id);
    }

    public static PacketNpcVisibleFalse decode(final PacketBuffer buf) {
        return new PacketNpcVisibleFalse(buf.readInt());
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void handle() {
        // Mark by id even if the entity is not loaded yet (first hide / chunk edge).
        ClientNpcVisibility.setHiddenFromLocalPlayer(this.id, true);
    }
}
