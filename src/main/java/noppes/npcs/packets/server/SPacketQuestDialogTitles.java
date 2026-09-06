package noppes.npcs.packets.server;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import noppes.npcs.CustomNpcsPermissions;
import noppes.npcs.controllers.DialogController;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.packets.PacketServerBasic;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.client.PacketGuiData;
import noppes.npcs.quests.QuestObjectiveLimits;

public class SPacketQuestDialogTitles extends PacketServerBasic {
    private final HashMap<Integer, Integer> dialogs;

    public SPacketQuestDialogTitles(final int dialogId1, final int dialogId2, final int dialogId3) {
        this.dialogs = new HashMap<>();
        if (dialogId1 >= 0) {
            this.dialogs.put(0, dialogId1);
        }
        if (dialogId2 >= 0) {
            this.dialogs.put(1, dialogId2);
        }
        if (dialogId3 >= 0) {
            this.dialogs.put(2, dialogId3);
        }
    }

    public SPacketQuestDialogTitles(final Map<Integer, Integer> dialogs) {
        this.dialogs = dialogs == null ? new HashMap<>() : new HashMap<>(dialogs);
    }

    @Override
    public CustomNpcsPermissions.Permission getPermission() {
        return CustomNpcsPermissions.GLOBAL_QUEST;
    }

    public static void encode(final SPacketQuestDialogTitles msg, final PacketBuffer buf) {
        buf.writeVarInt(msg.dialogs.size());
        for (final Map.Entry<Integer, Integer> entry : msg.dialogs.entrySet()) {
            buf.writeInt(entry.getKey());
            buf.writeInt(entry.getValue());
        }
    }

    public static SPacketQuestDialogTitles decode(final PacketBuffer buf) {
        final int size = buf.readVarInt();
        final HashMap<Integer, Integer> dialogs = new HashMap<>();
        for (int i = 0; i < size; ++i) {
            dialogs.put(buf.readInt(), buf.readInt());
        }
        return new SPacketQuestDialogTitles(dialogs);
    }

    @Override
    protected void handle() {
        final CompoundNBT compound = new CompoundNBT();
        for (final Map.Entry<Integer, Integer> entry : this.dialogs.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            if (entry.getKey() < 0 || entry.getKey() >= QuestObjectiveLimits.MAX) {
                continue;
            }
            final Dialog dialog = DialogController.instance.dialogs.get(entry.getValue());
            if (dialog != null) {
                compound.putString(Integer.toString(entry.getKey()), dialog.title);
            }
        }
        Packets.send(this.player, new PacketGuiData(compound));
    }
}
