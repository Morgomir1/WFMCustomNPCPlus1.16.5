package noppes.npcs.controllers.data;

import noppes.npcs.api.handler.data.*;
import noppes.npcs.db.*;
import net.minecraft.nbt.*;
import noppes.npcs.controllers.*;
import net.minecraft.entity.player.*;

public class DialogOption implements IDialogOption
{
    @DatabaseColumn(name = "id", type = DatabaseColumn.Type.INT)
    public int id;
    @DatabaseColumn(name = "dialog", type = DatabaseColumn.Type.INT)
    public int dialogId;
    @DatabaseColumn(name = "option", type = DatabaseColumn.Type.VARCHAR)
    public String option;
    @DatabaseColumn(name = "text", type = DatabaseColumn.Type.TEXT)
    public String title;
    @DatabaseColumn(name = "type", type = DatabaseColumn.Type.SMALLINT)
    public int optionType;
    @DatabaseColumn(name = "color", type = DatabaseColumn.Type.SMALLINT)
    public int optionColor;
    @DatabaseColumn(name = "command", type = DatabaseColumn.Type.TEXT)
    public String command;
    @DatabaseColumn(name = "order", type = DatabaseColumn.Type.SMALLINT)
    public int slot;
    public Availability availability;
    
    public DialogOption() {
        this.id = -1;
        this.dialogId = -1;
        this.option = "Talk";
        this.title = "Talk";
        this.optionType = 1;
        this.optionColor = 14737632;
        this.command = "";
        this.slot = -1;
        this.availability = new Availability();
    }
    
    public void readNBT(final CompoundNBT compound) {
        if (compound == null) {
            return;
        }
        this.title = compound.getString("Title");
        this.dialogId = compound.getInt("Dialog");
        this.optionColor = compound.getInt("DialogColor");
        this.optionType = compound.getInt("OptionType");
        this.command = compound.getString("DialogCommand");
        this.availability.load(compound);
        if (this.optionColor == 0) {
            this.optionColor = 14737632;
        }
    }
    
    public CompoundNBT writeNBT() {
        final CompoundNBT compound = new CompoundNBT();
        compound.putString("Title", this.title);
        compound.putInt("OptionType", this.optionType);
        compound.putInt("Dialog", this.dialogId);
        compound.putInt("DialogColor", this.optionColor);
        compound.putString("DialogCommand", this.command);
        this.availability.save(compound);
        return compound;
    }
    
    public boolean hasDialog() {
        return this.dialogId > 0 && this.optionType == 1 && DialogController.instance.hasDialog(this.dialogId);
    }
    
    public Dialog getDialog() {
        if (!this.hasDialog()) {
            return null;
        }
        return DialogController.instance.dialogs.get(this.dialogId);
    }
    
    public boolean isAvailable(final PlayerEntity player) {
        if (this.optionType == 2) {
            return false;
        }
        if (!this.availability.isAvailable(player)) {
            return false;
        }
        if (this.optionType != 1) {
            return true;
        }
        final Dialog dialog = this.getDialog();
        return dialog != null && dialog.availability.isAvailable(player);
    }
    
    public boolean isValid() {
        return this.optionType != 2 && (this.optionType != 1 || this.hasDialog());
    }
    
    public boolean canClose() {
        return this.optionType != 1 || !this.hasDialog();
    }
    
    @Override
    public int getSlot() {
        return this.slot;
    }
    
    @Override
    public String getName() {
        return this.title;
    }
    
    @Override
    public int getType() {
        return this.optionType;
    }
}
