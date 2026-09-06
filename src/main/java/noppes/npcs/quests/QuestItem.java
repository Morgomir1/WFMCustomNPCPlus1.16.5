package noppes.npcs.quests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import noppes.npcs.NoppesUtilPlayer;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.NpcMiscInventory;
import noppes.npcs.api.CustomNPCsException;
import noppes.npcs.api.handler.data.IQuestObjective;
import noppes.npcs.util.ValueUtil;

public class QuestItem extends QuestInterface {
    public NpcMiscInventory items = new NpcMiscInventory(QuestObjectiveLimits.ITEM_SLOTS);
    public boolean leaveItems = false;
    public boolean ignoreDamage = false;
    public boolean ignoreNBT = false;

    @Override
    public void readAdditionalSaveData(final CompoundNBT compound) {
        this.items.setFromNBT(compound.getCompound("Items"));
        this.leaveItems = compound.getBoolean("LeaveItems");
        this.ignoreDamage = compound.getBoolean("IgnoreDamage");
        this.ignoreNBT = compound.getBoolean("IgnoreNBT");
    }

    @Override
    public void addAdditionalSaveData(final CompoundNBT compound) {
        compound.put("Items", this.items.getToNBT());
        compound.putBoolean("LeaveItems", this.leaveItems);
        compound.putBoolean("IgnoreDamage", this.ignoreDamage);
        compound.putBoolean("IgnoreNBT", this.ignoreNBT);
    }

    @Override
    public boolean isCompleted(final PlayerEntity player) {
        final List<ItemStack> questItems = NoppesUtilPlayer.countStacks(this.items, this.ignoreDamage, this.ignoreNBT);
        for (final ItemStack reqItem : questItems) {
            if (!NoppesUtilPlayer.compareItems(player, reqItem, this.ignoreDamage, this.ignoreNBT)) {
                return false;
            }
        }
        return true;
    }

    public Map<ItemStack, Integer> getProgressSet(final PlayerEntity player) {
        final HashMap<ItemStack, Integer> map = new HashMap<>();
        final List<ItemStack> questItems = NoppesUtilPlayer.countStacks(this.items, this.ignoreDamage, this.ignoreNBT);
        for (final ItemStack item : questItems) {
            if (NoppesUtilServer.IsItemStackNull(item)) {
                continue;
            }
            map.put(item, 0);
        }
        for (int i = 0; i < player.inventory.getContainerSize(); ++i) {
            final ItemStack item = player.inventory.getItem(i);
            if (NoppesUtilServer.IsItemStackNull(item)) {
                continue;
            }
            for (final Map.Entry<ItemStack, Integer> questItem : map.entrySet()) {
                if (NoppesUtilPlayer.compareItems(questItem.getKey(), item, this.ignoreDamage, this.ignoreNBT)) {
                    map.put(questItem.getKey(), questItem.getValue() + item.getCount());
                }
            }
        }
        return map;
    }

    @Override
    public void handleComplete(final PlayerEntity player) {
        if (this.leaveItems) {
            return;
        }
        block0: for (final ItemStack questitem : this.items.items) {
            if (questitem.isEmpty()) {
                continue;
            }
            int stacksize = questitem.getCount();
            for (int i = 0; i < player.inventory.getContainerSize(); ++i) {
                final ItemStack item = player.inventory.getItem(i);
                if (NoppesUtilServer.IsItemStackNull(item) || !NoppesUtilPlayer.compareItems(item, questitem, this.ignoreDamage, this.ignoreNBT)) {
                    continue;
                }
                final int size = item.getCount();
                if (stacksize - size >= 0) {
                    player.inventory.setItem(i, ItemStack.EMPTY);
                    item.split(size);
                }
                else {
                    item.split(stacksize);
                }
                if ((stacksize -= size) <= 0) {
                    continue block0;
                }
            }
        }
    }

    @Override
    public IQuestObjective[] getObjectives(final PlayerEntity player) {
        final ArrayList<QuestItemObjective> list = new ArrayList<>();
        final List<ItemStack> questItems = NoppesUtilPlayer.countStacks(this.items, this.ignoreDamage, this.ignoreNBT);
        for (final ItemStack stack : questItems) {
            if (!stack.isEmpty()) {
                list.add(new QuestItemObjective(player, stack));
            }
        }
        return list.toArray(new IQuestObjective[list.size()]);
    }

    class QuestItemObjective implements IQuestObjective {
        private final PlayerEntity player;
        private final ItemStack questItem;

        public QuestItemObjective(final PlayerEntity player, final ItemStack item) {
            this.player = player;
            this.questItem = item;
        }

        @Override
        public int getProgress() {
            int count = 0;
            for (int i = 0; i < this.player.inventory.getContainerSize(); ++i) {
                final ItemStack item = this.player.inventory.getItem(i);
                if (NoppesUtilServer.IsItemStackNull(item) || !NoppesUtilPlayer.compareItems(this.questItem, item, QuestItem.this.ignoreDamage, QuestItem.this.ignoreNBT)) {
                    continue;
                }
                count += item.getCount();
            }
            return ValueUtil.CorrectInt(count, 0, this.questItem.getCount());
        }

        @Override
        public void setProgress(final int progress) {
            throw new CustomNPCsException("Cant set the progress of ItemQuests", new Object[0]);
        }

        @Override
        public int getMaxProgress() {
            return this.questItem.getCount();
        }

        @Override
        public boolean isCompleted() {
            return NoppesUtilPlayer.compareItems(this.player, this.questItem, QuestItem.this.ignoreDamage, QuestItem.this.ignoreNBT);
        }

        @Override
        public String getText() {
            return this.getMCText().getString();
        }

        @Override
        public ITextComponent getMCText() {
            return new StringTextComponent("").append(this.questItem.getHoverName()).append(": " + this.getProgress() + "/" + this.getMaxProgress());
        }
    }
}
