package noppes.npcs.packets.client;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.client.telegraph.ClientTelegraphManager;
import noppes.npcs.shared.common.PacketBasic;

public class PacketTelegraphRemove extends PacketBasic {
    private final String id;

    public PacketTelegraphRemove(final String id) {
        this.id = id == null ? "" : id;
    }

    public static void encode(final PacketTelegraphRemove msg, final PacketBuffer buf) {
        buf.writeUtf(msg.id);
    }

    public static PacketTelegraphRemove decode(final PacketBuffer buf) {
        return new PacketTelegraphRemove(buf.readUtf());
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void handle() {
        ClientTelegraphManager.remove(this.id);
    }
}
