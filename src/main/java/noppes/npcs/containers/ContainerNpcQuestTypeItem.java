package noppes.npcs.containers;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import noppes.npcs.CustomContainer;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.quests.QuestItem;
import noppes.npcs.quests.QuestObjectiveLimits;

public class ContainerNpcQuestTypeItem extends Container {
    public ContainerNpcQuestTypeItem(final int containerId, final PlayerInventory playerInventory) {
        super(CustomContainer.container_questtypeitem, containerId);
        final Quest quest = NoppesUtilServer.getEditingQuest(playerInventory.player);
        final QuestItem questItem = (QuestItem) quest.questInterface;
        for (int i = 0; i < QuestObjectiveLimits.ITEM_SLOTS; ++i) {
            this.addSlot(new Slot(questItem.items, i, QuestObjectiveLimits.itemSlotX(i), QuestObjectiveLimits.itemSlotY(i)));
        }
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, QuestObjectiveLimits.PLAYER_INV_Y + row * 18));
            }
        }
        for (int hotbar = 0; hotbar < 9; ++hotbar) {
            this.addSlot(new Slot(playerInventory, hotbar, 8 + hotbar * 18, QuestObjectiveLimits.HOTBAR_Y));
        }
    }

    @Override
    public ItemStack quickMoveStack(final PlayerEntity player, final int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(final PlayerEntity player) {
        return true;
    }
}
