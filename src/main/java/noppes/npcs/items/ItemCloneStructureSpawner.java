package noppes.npcs.items;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.stats.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.CustomEntities;
import noppes.npcs.entity.EntityCloneStructureSpawner;

import javax.annotation.Nullable;
import java.util.List;

public class ItemCloneStructureSpawner extends Item {
    public ItemCloneStructureSpawner(final Properties properties) {
        super(properties);
    }

    @Override
    public ActionResult<ItemStack> use(final World world, final PlayerEntity player, final Hand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (!world.isClientSide && player.isCreative()) {
            final BlockPos pos = new BlockPos(player.getX(), player.getEyeY(), player.getZ())
                    .relative(player.getDirection(), 1);
            this.spawnSpawner(world, pos, stack);
        }
        world.playSound(player, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GLASS_PLACE, SoundCategory.NEUTRAL, 0.7f,
                0.4f / (random.nextFloat() * 0.4f + 0.8f));
        player.awardStat(Stats.ITEM_USED.get(this));
        return ActionResult.sidedSuccess(stack, world.isClientSide);
    }

    @Override
    public ActionResultType useOn(final ItemUseContext context) {
        final ActionResultType placed = this.place(new BlockItemUseContext(context));
        if (!placed.consumesAction()) {
            return this.use(context.getLevel(), context.getPlayer(), context.getHand()).getResult();
        }
        return placed;
    }

    private ActionResultType place(final BlockItemUseContext context) {
        if (!context.canPlace()) {
            return ActionResultType.FAIL;
        }
        final World world = context.getLevel();
        final PlayerEntity player = context.getPlayer();
        final BlockPos pos = context.getClickedPos();
        if (!world.isClientSide && player != null && player.isCreative()) {
            this.spawnSpawner(world, pos, context.getItemInHand());
        }
        if (player != null) {
            world.playSound(player, pos, SoundEvents.GLASS_PLACE, SoundCategory.NEUTRAL, 0.7f,
                    0.4f / (random.nextFloat() * 0.4f + 0.8f));
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return ActionResultType.sidedSuccess(world.isClientSide);
    }

    private void spawnSpawner(final World world, final BlockPos pos, final ItemStack stack) {
        final EntityCloneStructureSpawner spawner =
                new EntityCloneStructureSpawner(CustomEntities.entityCloneStructureSpawner, world);
        spawner.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
        spawner.setManualPlacement(true);
        applyItemNbt(spawner, stack);
        world.addFreshEntity(spawner);
    }

    public static void applyItemNbt(final EntityCloneStructureSpawner spawner, final ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTag()) {
            return;
        }
        final CompoundNBT tag = stack.getTag();
        if (tag.contains("CloneName")) {
            spawner.setCloneName(tag.getString("CloneName"));
        }
        if (tag.contains("CloneTab")) {
            spawner.setCloneTab(tag.getInt("CloneTab"));
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void appendHoverText(final ItemStack stack, @Nullable final World level,
                                final List<ITextComponent> tooltip, final ITooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (stack.hasTag()) {
            final CompoundNBT tag = stack.getTag();
            if (tag.contains("CloneName")) {
                tooltip.add(new StringTextComponent("Clone: " + tag.getString("CloneName"))
                        .withStyle(TextFormatting.AQUA));
            }
            if (tag.contains("CloneTab")) {
                tooltip.add(new StringTextComponent("Tab: " + tag.getInt("CloneTab"))
                        .withStyle(TextFormatting.GRAY));
            }
        } else {
            tooltip.add(new StringTextComponent("Creative: place editable clone marker")
                    .withStyle(TextFormatting.DARK_GRAY));
        }
    }
}
