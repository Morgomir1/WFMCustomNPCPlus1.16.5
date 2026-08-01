package noppes.npcs.controllers.data;

import noppes.npcs.db.*;
import noppes.npcs.*;
import net.minecraft.entity.player.*;
import net.minecraft.nbt.*;
import java.util.*;
import noppes.npcs.api.*;
import noppes.npcs.controllers.*;
import noppes.npcs.api.handler.data.*;

public class Dialog implements ICompatibilty, IDialog
{
    public int version;
    @DatabaseColumn(name = "id", type = DatabaseColumn.Type.INT)
    public int id;
    @DatabaseColumn(name = "title", type = DatabaseColumn.Type.VARCHAR)
    public String title;
    @DatabaseColumn(name = "text", type = DatabaseColumn.Type.TEXT)
    public String text;
    @DatabaseColumn(name = "quest", type = DatabaseColumn.Type.INT)
    public int quest;
    @DatabaseColumn(name = "category", type = DatabaseColumn.Type.VARCHAR)
    public String categoryName;
    public final DialogCategory category;
    public HashMap<Integer, DialogOption> options;
    public Availability availability;
    public FactionOptions factionOptions;
    public String sound;
    public String command;
    public PlayerMail mail;
    public boolean hideNPC;
    public boolean showWheel;
    public boolean disableEsc;
    
    public Dialog(final DialogCategory category) {
        this.version = VersionCompatibility.ModRev;
        this.id = -1;
        this.title = "";
        this.text = "";
        this.quest = -1;
        this.options = new HashMap<Integer, DialogOption>();
        this.availability = new Availability();
        this.factionOptions = new FactionOptions();
        this.command = "";
        this.mail = new PlayerMail();
        this.hideNPC = false;
        this.showWheel = false;
        this.disableEsc = false;
        this.category = category;
    }
    
    public boolean hasDialogs(final PlayerEntity player) {
        for (final DialogOption option : this.options.values()) {
            if (option != null && option.optionType == 1 && option.hasDialog() && option.isAvailable(player)) {
                return true;
            }
        }
        return false;
    }
    
    public void readNBT(final CompoundNBT compound) {
        this.id = compound.getInt("DialogId");
        this.readNBTPartial(compound);
    }
    
    public void readNBTPartial(final CompoundNBT compound) {
        this.version = compound.getInt("ModRev");
        VersionCompatibility.CheckAvailabilityCompatibility(this, compound);
        this.title = compound.getString("DialogTitle");
        this.text = compound.getString("DialogText");
        this.quest = compound.getInt("DialogQuest");
        this.sound = compound.getString("DialogSound");
        this.command = compound.getString("DialogCommand");
        this.mail.readNBT(compound.getCompound("DialogMail"));
        this.hideNPC = compound.getBoolean("DialogHideNPC");
        this.showWheel = compound.getBoolean("DialogShowWheel");
        this.disableEsc = compound.getBoolean("DialogDisableEsc");
        final ListNBT options = compound.getList("Options", 10);
        final HashMap<Integer, DialogOption> newoptions = new HashMap<Integer, DialogOption>();
        for (int iii = 0; iii < options.size(); ++iii) {
            final CompoundNBT option = options.getCompound(iii);
            final int opslot = option.getInt("OptionSlot");
            final DialogOption dia = new DialogOption();
            dia.readNBT(option.getCompound("Option"));
            if (dia.hasDialog()) {}
            newoptions.put(opslot, dia);
            dia.slot = opslot;
        }
        this.options = newoptions;
        this.availability.load(compound);
        this.factionOptions.load(compound);
    }
    
    @Override
    public CompoundNBT save(final CompoundNBT compound) {
        compound.putInt("DialogId", this.id);
        return this.writeToNBTPartial(compound);
    }
    
    public CompoundNBT writeToNBTPartial(final CompoundNBT compound) {
        compound.putString("DialogTitle", this.title);
        compound.putString("DialogText", this.text);
        compound.putInt("DialogQuest", this.quest);
        compound.putString("DialogCommand", this.command);
        compound.put("DialogMail", (INBT)this.mail.writeNBT());
        compound.putBoolean("DialogHideNPC", this.hideNPC);
        compound.putBoolean("DialogShowWheel", this.showWheel);
        compound.putBoolean("DialogDisableEsc", this.disableEsc);
        if (this.sound != null && !this.sound.isEmpty()) {
            compound.putString("DialogSound", this.sound);
        }
        final ListNBT options = new ListNBT();
        for (final int opslot : this.options.keySet()) {
            final CompoundNBT listcompound = new CompoundNBT();
            listcompound.putInt("OptionSlot", opslot);
            listcompound.put("Option", (INBT)this.options.get(opslot).writeNBT());
            options.add((INBT)listcompound);
        }
        compound.put("Options", (INBT)options);
        this.availability.save(compound);
        this.factionOptions.save(compound);
        compound.putInt("ModRev", this.version);
        return compound;
    }
    
    public boolean hasQuest() {
        return this.getQuest() != null;
    }
    
    @Override
    public Quest getQuest() {
        if (QuestController.instance == null) {
            return null;
        }
        return QuestController.instance.quests.get(this.quest);
    }
    
    public boolean hasOtherOptions() {
        for (final DialogOption option : this.options.values()) {
            if (option != null && option.optionType != 2) {
                return true;
            }
        }
        return false;
    }
    
    public Dialog copy(final PlayerEntity player) {
        final Dialog dialog = new Dialog(this.category);
        dialog.id = this.id;
        dialog.text = this.text;
        dialog.title = this.title;
        dialog.quest = this.quest;
        dialog.sound = this.sound;
        dialog.mail = this.mail;
        dialog.command = this.command;
        dialog.hideNPC = this.hideNPC;
        dialog.showWheel = this.showWheel;
        dialog.disableEsc = this.disableEsc;
        for (final int slot : this.options.keySet()) {
            final DialogOption option = this.options.get(slot);
            if (!option.isAvailable(player)) {
                continue;
            }
            if (option.optionType == 1 && !option.hasDialog()) {
                continue;
            }
            dialog.options.put(slot, option);
        }
        return dialog;
    }
    
    @Override
    public int getVersion() {
        return this.version;
    }
    
    @Override
    public void setVersion(final int version) {
        this.version = version;
    }
    
    @Override
    public int getId() {
        return this.id;
    }
    
    @Override
    public String getName() {
        return this.title;
    }
    
    @Override
    public List<IDialogOption> getOptions() {
        return new ArrayList<IDialogOption>(this.options.values());
    }
    
    @Override
    public IDialogOption getOption(final int slot) {
        final IDialogOption option = this.options.get(slot);
        if (option == null) {
            throw new CustomNPCsException("There is no DialogOption for slot: " + slot, new Object[0]);
        }
        return option;
    }
    
    @Override
    public IAvailability getAvailability() {
        return this.availability;
    }
    
    @Override
    public IDialogCategory getCategory() {
        return this.category;
    }
    
    @Override
    public void save() {
        DialogController.instance.saveDialog(this.category, this);
    }
    
    @Override
    public void setName(final String name) {
        this.title = name;
    }
    
    @Override
    public String getText() {
        return this.text;
    }
    
    @Override
    public void setText(final String text) {
        this.text = text;
    }
    
    @Override
    public void setQuest(final IQuest quest) {
        if (quest == null) {
            this.quest = -1;
        }
        else {
            if (quest.getId() < 0) {
                throw new CustomNPCsException("Quest id is lower than 0", new Object[0]);
            }
            this.quest = quest.getId();
        }
    }
    
    @Override
    public String getCommand() {
        return this.command;
    }
    
    @Override
    public void setCommand(final String command) {
        this.command = command;
    }
}
