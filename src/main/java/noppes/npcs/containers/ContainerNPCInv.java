package noppes.npcs.containers;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import noppes.npcs.CustomContainer;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.NpcDropInventoryLimits;

public class ContainerNPCInv extends Container {
    public ContainerNPCInv(final int containerId, final PlayerInventory playerInventory, final int entityId) {
        super(CustomContainer.container_inv, containerId);
        final EntityNPCInterface npc = (EntityNPCInterface) playerInventory.player.level.getEntity(entityId);
        this.addSlot(new SlotNPCArmor(npc.inventory, 0, 9, 22, EquipmentSlotType.HEAD));
        this.addSlot(new SlotNPCArmor(npc.inventory, 1, 9, 40, EquipmentSlotType.CHEST));
        this.addSlot(new SlotNPCArmor(npc.inventory, 2, 9, 58, EquipmentSlotType.LEGS));
        this.addSlot(new SlotNPCArmor(npc.inventory, 3, 9, 76, EquipmentSlotType.FEET));
        this.addSlot(new Slot((IInventory) npc.inventory, 4, 81, 22));
        this.addSlot(new Slot((IInventory) npc.inventory, 5, 81, 40));
        this.addSlot(new Slot((IInventory) npc.inventory, 6, 81, 58));
        for (int i = 0; i < NpcDropInventoryLimits.DROP_SLOTS; ++i) {
            this.addSlot(new Slot(
                    (IInventory) npc.inventory,
                    i + NpcDropInventoryLimits.DROP_SLOT_OFFSET,
                    NpcDropInventoryLimits.dropSlotX(i),
                    NpcDropInventoryLimits.dropSlotY(i)));
        }
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot((IInventory) playerInventory, col + row * 9 + 9, col * 18 + 8, 113 + row * 18));
            }
        }
        for (int hotbar = 0; hotbar < 9; ++hotbar) {
            this.addSlot(new Slot((IInventory) playerInventory, hotbar, hotbar * 18 + 8, 171));
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
