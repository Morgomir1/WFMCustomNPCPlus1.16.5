package noppes.npcs.entity.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ExperienceOrbEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.ForgeHooks;
import noppes.npcs.NBTTags;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.api.CustomNPCsException;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.data.INPCInventory;
import noppes.npcs.api.event.NpcEvent;
import noppes.npcs.api.item.IItemStack;
import noppes.npcs.api.wrapper.ItemStackWrapper;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.util.ValueUtil;

public class DataInventory implements IInventory, INPCInventory {
    public Map<Integer, IItemStack> drops = new HashMap<>();
    public Map<Integer, Integer> dropchance = new HashMap<>();
    public Map<Integer, IItemStack> weapons = new HashMap<>();
    public Map<Integer, IItemStack> armor = new HashMap<>();
    private int minExp = 0;
    private int maxExp = 0;
    public int lootMode = 0;
    private final EntityNPCInterface npc;

    public DataInventory(final EntityNPCInterface npc) {
        this.npc = npc;
    }

    public CompoundNBT save(final CompoundNBT nbttagcompound) {
        nbttagcompound.putInt("MinExp", this.minExp);
        nbttagcompound.putInt("MaxExp", this.maxExp);
        nbttagcompound.put("NpcInv", (INBT) NBTTags.nbtIItemStackMap(this.drops));
        nbttagcompound.put("Armor", (INBT) NBTTags.nbtIItemStackMap(this.armor));
        nbttagcompound.put("Weapons", (INBT) NBTTags.nbtIItemStackMap(this.weapons));
        nbttagcompound.put("DropChance", (INBT) NBTTags.nbtIntegerIntegerMap(this.dropchance));
        nbttagcompound.putInt("LootMode", this.lootMode);
        return nbttagcompound;
    }

    public void load(final CompoundNBT nbttagcompound) {
        this.minExp = nbttagcompound.getInt("MinExp");
        this.maxExp = nbttagcompound.getInt("MaxExp");
        this.drops = NBTTags.getIItemStackMap(nbttagcompound.getList("NpcInv", 10));
        this.armor = NBTTags.getIItemStackMap(nbttagcompound.getList("Armor", 10));
        this.weapons = NBTTags.getIItemStackMap(nbttagcompound.getList("Weapons", 10));
        this.dropchance = NBTTags.getIntegerIntegerMap(nbttagcompound.getList("DropChance", 10));
        this.lootMode = nbttagcompound.getInt("LootMode");
    }

    @Override
    public IItemStack getArmor(final int slot) {
        return this.armor.get(slot);
    }

    @Override
    public void setArmor(final int slot, final IItemStack item) {
        this.armor.put(slot, item);
        this.npc.updateClient = true;
    }

    @Override
    public IItemStack getRightHand() {
        return this.weapons.get(0);
    }

    @Override
    public void setRightHand(final IItemStack item) {
        this.weapons.put(0, item);
        this.npc.updateClient = true;
    }

    @Override
    public IItemStack getProjectile() {
        return this.weapons.get(1);
    }

    @Override
    public void setProjectile(final IItemStack item) {
        this.weapons.put(1, item);
        this.npc.updateAI = true;
    }

    @Override
    public IItemStack getLeftHand() {
        return this.weapons.get(2);
    }

    @Override
    public void setLeftHand(final IItemStack item) {
        this.weapons.put(2, item);
        this.npc.updateClient = true;
    }

    @Override
    public IItemStack getDropItem(final int slot) {
        if (slot < 0 || slot > NpcDropInventoryLimits.MAX_DROP_SLOT) {
            throw new CustomNPCsException("Bad slot number: " + slot, new Object[0]);
        }
        final IItemStack item = this.npc.inventory.drops.get(slot);
        if (item == null) {
            return null;
        }
        return NpcAPI.Instance().getIItemStack(item.getMCItemStack());
    }

    @Override
    public void setDropItem(final int slot, final IItemStack item, int chance) {
        if (slot < 0 || slot > NpcDropInventoryLimits.MAX_DROP_SLOT) {
            throw new CustomNPCsException("Bad slot number: " + slot, new Object[0]);
        }
        chance = ValueUtil.CorrectInt(chance, 1, 100);
        if (item == null || item.isEmpty()) {
            this.dropchance.remove(slot);
            this.drops.remove(slot);
        } else {
            this.dropchance.put(slot, chance);
            this.drops.put(slot, item);
        }
    }

    @Override
    public IItemStack[] getItemsRNG() {
        final ArrayList<IItemStack> list = new ArrayList<>();
        for (final int i : this.drops.keySet()) {
            final IItemStack item = this.drops.get(i);
            if (item == null || item.isEmpty()) {
                continue;
            }
            int dchance = 100;
            if (this.dropchance.containsKey(i)) {
                dchance = this.dropchance.get(i);
            }
            final int chance = this.npc.level.random.nextInt(100) + dchance;
            if (chance < 100) {
                continue;
            }
            list.add(item);
        }
        return list.toArray(new IItemStack[list.size()]);
    }

    public void dropStuff(final NpcEvent.DiedEvent event, final Entity entity, final DamageSource damagesource) {
        int var2;
        final ArrayList<ItemEntity> list = new ArrayList<>();
        if (event.droppedItems != null) {
            for (final IItemStack item : event.droppedItems) {
                final ItemEntity e = this.getItemEntity(item.getMCItemStack().copy());
                if (e == null) {
                    continue;
                }
                list.add(e);
            }
        }
        int enchant = 0;
        if (damagesource.getEntity() instanceof PlayerEntity) {
            enchant = EnchantmentHelper.getMobLooting((LivingEntity) damagesource.getEntity());
        }
        if (!ForgeHooks.onLivingDrops((LivingEntity) this.npc, damagesource, list, enchant, true)) {
            for (final ItemEntity item : list) {
                if (this.lootMode == 1 && entity instanceof PlayerEntity) {
                    final PlayerEntity player = (PlayerEntity) entity;
                    item.setPickUpDelay(2);
                    this.npc.level.addFreshEntity(item);
                    final ItemStack stack = item.getItem();
                    final int i = stack.getCount();
                    if (!player.inventory.add(stack)) {
                        continue;
                    }
                    entity.level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundCategory.PLAYERS, 0.2f, ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7f + 1.0f) * 2.0f);
                    player.take(item, i);
                    if (stack.getCount() > 0) {
                        continue;
                    }
                    item.remove();
                    continue;
                }
                this.npc.level.addFreshEntity(item);
            }
        }
        for (int exp = event.expDropped; exp > 0; exp -= var2) {
            var2 = ExperienceOrbEntity.getExperienceValue(exp);
            if (this.lootMode == 1 && entity instanceof PlayerEntity) {
                this.npc.level.addFreshEntity(new ExperienceOrbEntity(entity.level, entity.getX(), entity.getY(), entity.getZ(), var2));
                continue;
            }
            this.npc.level.addFreshEntity(new ExperienceOrbEntity(this.npc.level, this.npc.getX(), this.npc.getY(), this.npc.getZ(), var2));
        }
    }

    public ItemEntity getItemEntity(final ItemStack itemstack) {
        if (itemstack == null || itemstack.isEmpty()) {
            return null;
        }
        final ItemEntity entityitem = new ItemEntity(this.npc.level, this.npc.getX(), this.npc.getY() - (double) 0.3f + (double) this.npc.getEyeHeight(), this.npc.getZ(), itemstack);
        entityitem.setPickUpDelay(40);
        final float f2 = this.npc.getRandom().nextFloat() * 0.5f;
        final float f4 = this.npc.getRandom().nextFloat() * 3.141593f * 2.0f;
        entityitem.setDeltaMovement((double) (-MathHelper.sin(f4) * f2), (double) 0.2f, (double) (MathHelper.cos(f4) * f2));
        return entityitem;
    }

    @Override
    public int getContainerSize() {
        return NpcDropInventoryLimits.CONTAINER_SIZE;
    }

    @Override
    public ItemStack getItem(final int i) {
        if (i < 4) {
            return ItemStackWrapper.MCItem(this.getArmor(i));
        }
        if (i < NpcDropInventoryLimits.DROP_SLOT_OFFSET) {
            return ItemStackWrapper.MCItem(this.weapons.get(i - 4));
        }
        return ItemStackWrapper.MCItem(this.drops.get(i - NpcDropInventoryLimits.DROP_SLOT_OFFSET));
    }

    @Override
    public ItemStack removeItem(int par1, final int limbSwingAmount) {
        final Map<Integer, IItemStack> var3;
        int i = 0;
        if (par1 >= NpcDropInventoryLimits.DROP_SLOT_OFFSET) {
            var3 = this.drops;
            par1 -= NpcDropInventoryLimits.DROP_SLOT_OFFSET;
        } else if (par1 >= 4) {
            var3 = this.weapons;
            par1 -= 4;
            i = 1;
        } else {
            var3 = this.armor;
            i = 2;
        }
        ItemStack var4 = null;
        if (var3.get(par1) != null) {
            if (var3.get(par1).getMCItemStack().getCount() <= limbSwingAmount) {
                var4 = var3.get(par1).getMCItemStack();
                var3.put(par1, null);
            } else {
                var4 = var3.get(par1).getMCItemStack().split(limbSwingAmount);
                if (var3.get(par1).getMCItemStack().getCount() == 0) {
                    var3.put(par1, null);
                }
            }
        }
        if (i == 1) {
            this.weapons = var3;
        }
        if (i == 2) {
            this.armor = var3;
        }
        if (var4 == null) {
            return ItemStack.EMPTY;
        }
        return var4;
    }

    @Override
    public ItemStack removeItemNoUpdate(int par1) {
        final Map<Integer, IItemStack> var2;
        int i = 0;
        if (par1 >= NpcDropInventoryLimits.DROP_SLOT_OFFSET) {
            var2 = this.drops;
            par1 -= NpcDropInventoryLimits.DROP_SLOT_OFFSET;
        } else if (par1 >= 4) {
            var2 = this.weapons;
            par1 -= 4;
            i = 1;
        } else {
            var2 = this.armor;
            i = 2;
        }
        if (var2.get(par1) != null) {
            final ItemStack var3 = var2.get(par1).getMCItemStack();
            var2.put(par1, null);
            if (i == 1) {
                this.weapons = var2;
            }
            if (i == 2) {
                this.armor = var2;
            }
            return var3;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int par1, final ItemStack limbSwingAmountItemStack) {
        final Map<Integer, IItemStack> var3;
        int i = 0;
        if (par1 >= NpcDropInventoryLimits.DROP_SLOT_OFFSET) {
            var3 = this.drops;
            par1 -= NpcDropInventoryLimits.DROP_SLOT_OFFSET;
        } else if (par1 >= 4) {
            var3 = this.weapons;
            par1 -= 4;
            i = 1;
        } else {
            var3 = this.armor;
            i = 2;
        }
        var3.put(par1, NpcAPI.Instance().getIItemStack(limbSwingAmountItemStack));
        if (i == 1) {
            this.weapons = var3;
        }
        if (i == 2) {
            this.armor = var3;
        }
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public boolean stillValid(final PlayerEntity var1) {
        return true;
    }

    @Override
    public boolean canPlaceItem(final int i, final ItemStack itemstack) {
        return true;
    }

    @Override
    public void setChanged() {
    }

    @Override
    public void startOpen(final PlayerEntity player) {
    }

    @Override
    public void stopOpen(final PlayerEntity player) {
    }

    @Override
    public int getExpMin() {
        return this.npc.inventory.minExp;
    }

    @Override
    public int getExpMax() {
        return this.npc.inventory.maxExp;
    }

    @Override
    public int getExpRNG() {
        int exp = this.minExp;
        if (this.maxExp - this.minExp > 0) {
            exp += this.npc.level.random.nextInt(this.maxExp - this.minExp);
        }
        return exp;
    }

    @Override
    public void setExp(int min, final int max) {
        min = Math.min(min, max);
        this.npc.inventory.minExp = min;
        this.npc.inventory.maxExp = max;
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < this.getContainerSize(); ++slot) {
            final ItemStack item = this.getItem(slot);
            if (NoppesUtilServer.IsItemStackNull(item) || item.isEmpty()) {
                continue;
            }
            return false;
        }
        return true;
    }

    @Override
    public void clearContent() {
    }
}
