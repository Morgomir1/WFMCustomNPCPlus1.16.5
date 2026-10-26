package noppes.npcs.packets.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.client.ClientNpcFaceLock;
import noppes.npcs.shared.common.PacketBasic;

/**
 * Лок/анлок взгляда NPC на клиенте (body + head = yaw). См. {@code NpcTurnHelper.lockFace}.
 */
public class PacketNpcFaceLock extends PacketBasic {
    private final int id;
    private final boolean locked;
    private final float yaw;

    public PacketNpcFaceLock(final int id, final boolean locked, final float yaw) {
        this.id = id;
        this.locked = locked;
        this.yaw = yaw;
    }

    public static void encode(final PacketNpcFaceLock msg, final PacketBuffer buf) {
        buf.writeInt(msg.id);
        buf.writeBoolean(msg.locked);
        buf.writeFloat(msg.yaw);
    }

    public static PacketNpcFaceLock decode(final PacketBuffer buf) {
        return new PacketNpcFaceLock(buf.readInt(), buf.readBoolean(), buf.readFloat());
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void handle() {
        final Minecraft mc = Minecraft.getInstance();
        final long now = mc.level != null ? mc.level.getGameTime() : 0L;
        ClientNpcFaceLock.set(this.id, this.locked, this.yaw, now);
    }
}
