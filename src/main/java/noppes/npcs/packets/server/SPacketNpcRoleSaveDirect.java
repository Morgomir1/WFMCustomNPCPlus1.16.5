package noppes.npcs.packets.server;

import noppes.npcs.packets.*;
import net.minecraft.nbt.*;
import noppes.npcs.*;
import net.minecraft.network.*;
import java.io.*;

/**
 * {@link SPacketNpcRoleSave} as a direct packet, like the script, quest and dialog saves: a trader with heavy
 * items in its slots does not fit into the 32767 bytes of a custom payload.
 * <p>
 * It is a class of its own, not a new superclass for SPacketNpcRoleSave: reobfJar reads the ancestors of a
 * class that also exists in the base jar from that jar, and would leave read/write unobfuscated.
 */
public class SPacketNpcRoleSaveDirect extends IPacketServer
{
    private CompoundNBT data;

    public SPacketNpcRoleSaveDirect(final CompoundNBT data) {
        this.data = data;
    }

    public SPacketNpcRoleSaveDirect() {
    }

    @Override
    public boolean requiresNpc() {
        return true;
    }

    @Override
    public CustomNpcsPermissions.Permission getPermission() {
        return CustomNpcsPermissions.NPC_ADVANCED;
    }

    @Override
    public void handle() {
        SPacketNpcRoleSave.apply(this.npc, this.data);
    }

    public void read(final PacketBuffer buf) throws IOException {
        this.data = buf.readNbt();
    }

    public void write(final PacketBuffer buf) throws IOException {
        buf.writeNbt(this.data);
    }
}
