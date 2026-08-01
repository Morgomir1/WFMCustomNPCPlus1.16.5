package noppes.npcs.packets.server;

import net.minecraft.item.*;
import net.minecraft.network.*;
import noppes.npcs.controllers.*;
import noppes.npcs.controllers.data.*;
import noppes.npcs.*;
import noppes.npcs.roles.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.*;
import noppes.npcs.entity.*;
import net.minecraft.nbt.*;
import noppes.npcs.packets.client.*;
import noppes.npcs.packets.*;

public class SPacketDialogSelected extends PacketServerBasic
{
    private final int dialogId;
    private final int optionId;
    
    public SPacketDialogSelected(final int dialogId, final int optionId) {
        this.dialogId = dialogId;
        this.optionId = optionId;
    }
    
    @Override
    public boolean toolAllowed(final ItemStack item) {
        return true;
    }
    
    @Override
    public boolean requiresNpc() {
        return true;
    }
    
    public static void encode(final SPacketDialogSelected msg, final PacketBuffer buf) {
        buf.writeInt(msg.dialogId);
        buf.writeInt(msg.optionId);
    }
    
    public static SPacketDialogSelected decode(final PacketBuffer buf) {
        return new SPacketDialogSelected(buf.readInt(), buf.readInt());
    }
    
    @Override
    protected void handle() {
        final PlayerData data = PlayerData.get((PlayerEntity)this.player);
        if (data.dialogId != this.dialogId) {
            return;
        }
        if (data.dialogId < 0 && this.npc.role.getType() == 7) {
            final String text = ((RoleDialog)this.npc.role).optionsTexts.get(this.optionId);
            if (text != null && !text.isEmpty()) {
                final Dialog d = new Dialog(null);
                d.text = text;
                NoppesUtilServer.openDialog((PlayerEntity)this.player, this.npc, d);
            }
            return;
        }
        final Dialog dialog = DialogController.instance.dialogs.get(data.dialogId);
        if (dialog == null) {
            return;
        }
        if (!dialog.hasDialogs((PlayerEntity)this.player) && !dialog.hasOtherOptions()) {
            this.closeDialog(this.player, this.npc, true);
            return;
        }
        final DialogOption option = dialog.options.get(this.optionId);
        if (option == null || !option.isAvailable((PlayerEntity)this.player)) {
            return;
        }
        if (EventHooks.onNPCDialogOption(this.npc, this.player, dialog, option) || (option.optionType == 1 && !option.hasDialog()) || option.optionType == 2 || option.optionType == 0) {
            this.closeDialog(this.player, this.npc, true);
            return;
        }
        if (option.optionType == 3) {
            this.closeDialog(this.player, this.npc, true);
            if (this.npc.role.getType() == 6) {
                ((RoleCompanion)this.npc.role).interact((PlayerEntity)this.player, true);
            }
            else {
                this.npc.role.interact((PlayerEntity)this.player);
            }
        }
        else if (option.optionType == 1) {
            this.closeDialog(this.player, this.npc, false);
            NoppesUtilServer.openDialog((PlayerEntity)this.player, this.npc, option.getDialog());
        }
        else if (option.optionType == 4) {
            this.closeDialog(this.player, this.npc, true);
            NoppesUtilServer.runCommand((Entity)this.npc, this.npc.getName().getString(), option.command, (PlayerEntity)this.player);
        }
        else {
            this.closeDialog(this.player, this.npc, true);
        }
    }
    
    public void closeDialog(final ServerPlayerEntity player, final EntityNPCInterface npc, final boolean notifyClient) {
        final PlayerData data = PlayerData.get((PlayerEntity)player);
        final Dialog dialog = DialogController.instance.dialogs.get(data.dialogId);
        EventHooks.onNPCDialogClose(npc, player, dialog);
        if (notifyClient) {
            Packets.send(player, new PacketGuiClose(new CompoundNBT()));
        }
        data.dialogId = -1;
    }
}
