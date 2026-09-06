package noppes.npcs.quests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import noppes.npcs.NBTTags;
import noppes.npcs.api.CustomNPCsException;
import noppes.npcs.api.handler.data.IQuestObjective;
import noppes.npcs.controllers.DialogController;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.controllers.data.PlayerData;

public class QuestDialog extends QuestInterface {
    public HashMap<Integer, Integer> dialogs = new HashMap<>();

    @Override
    public void readAdditionalSaveData(final CompoundNBT compound) {
        this.dialogs = NBTTags.getIntegerIntegerMap(compound.getList("QuestDialogs", 10));
    }

    @Override
    public void addAdditionalSaveData(final CompoundNBT compound) {
        compound.put("QuestDialogs", NBTTags.nbtIntegerIntegerMap(this.dialogs));
    }

    @Override
    public boolean isCompleted(final PlayerEntity player) {
        for (final int dialogId : this.dialogs.values()) {
            if (!PlayerData.get(player).dialogData.dialogsRead.contains(dialogId)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void handleComplete(final PlayerEntity player) {
    }

    @Override
    public IQuestObjective[] getObjectives(final PlayerEntity player) {
        final ArrayList<QuestDialogObjective> list = new ArrayList<>();
        for (final Map.Entry<Integer, Integer> entry : this.dialogs.entrySet()) {
            final Dialog dialog = DialogController.instance.dialogs.get(entry.getValue());
            if (dialog != null) {
                list.add(new QuestDialogObjective(player, dialog));
            }
        }
        return list.toArray(new IQuestObjective[list.size()]);
    }

    class QuestDialogObjective implements IQuestObjective {
        private final PlayerEntity player;
        private final Dialog dialog;

        public QuestDialogObjective(final PlayerEntity player, final Dialog dialog) {
            this.player = player;
            this.dialog = dialog;
        }

        @Override
        public int getProgress() {
            return this.isCompleted() ? 1 : 0;
        }

        @Override
        public void setProgress(final int progress) {
            if (progress < 0 || progress > 1) {
                throw new CustomNPCsException("Progress has to be 0 or 1", new Object[0]);
            }
            final PlayerData data = PlayerData.get(this.player);
            final boolean completed = data.dialogData.dialogsRead.contains(this.dialog.id);
            if (progress == 0 && completed) {
                data.dialogData.dialogsRead.remove(this.dialog.id);
                data.questData.checkQuestCompletion(this.player, 1);
                data.updateClient = true;
            }
            if (progress == 1 && !completed) {
                data.dialogData.dialogsRead.add(this.dialog.id);
                data.questData.checkQuestCompletion(this.player, 1);
                data.updateClient = true;
            }
        }

        @Override
        public int getMaxProgress() {
            return 1;
        }

        @Override
        public boolean isCompleted() {
            final PlayerData data = PlayerData.get(this.player);
            return data.dialogData.dialogsRead.contains(this.dialog.id);
        }

        @Override
        public String getText() {
            return this.getMCText().getString();
        }

        @Override
        public ITextComponent getMCText() {
            return new TranslationTextComponent(this.dialog.title).append(" (").append(new TranslationTextComponent(this.isCompleted() ? "quest.read" : "quest.unread")).append(")");
        }
    }
}
