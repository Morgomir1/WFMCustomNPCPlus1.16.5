package noppes.npcs.packets.server;

import noppes.npcs.packets.*;
import net.minecraft.nbt.*;
import noppes.npcs.*;
import noppes.npcs.entity.*;
import net.minecraft.network.*;

/**
 * Saves the whole role of the edited NPC through the Forge channel. The client no longer sends it this way
 * (see {@link Packets#sendServer}): the channel travels inside a vanilla custom payload, which the server
 * refuses above 32767 bytes. The message stays registered so that the channel ids do not move and an older
 * client is still understood.
 */
public class SPacketNpcRoleSave extends PacketServerBasic
{
    private CompoundNBT data;

    public SPacketNpcRoleSave(final CompoundNBT data) {
        this.data = data;
    }

    @Override
    public boolean requiresNpc() {
        return true;
    }

    @Override
    public CustomNpcsPermissions.Permission getPermission() {
        return CustomNpcsPermissions.NPC_ADVANCED;
    }

    public static void encode(final SPacketNpcRoleSave msg, final PacketBuffer buf) {
        buf.writeNbt(msg.data);
    }

    public static SPacketNpcRoleSave decode(final PacketBuffer buf) {
        return new SPacketNpcRoleSave(buf.readNbt());
    }

    /** The same save as a direct packet, which has no 32767-byte limit. */
    public SPacketNpcRoleSaveDirect asDirectPacket() {
        return new SPacketNpcRoleSaveDirect(this.data);
    }

    static void apply(final EntityNPCInterface npc, final CompoundNBT data) {
        npc.role.load(data);
        npc.updateClient = true;
    }

    @Override
    protected void handle() {
        apply(this.npc, this.data);
    }
}
