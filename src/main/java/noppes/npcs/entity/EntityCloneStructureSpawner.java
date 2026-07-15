package noppes.npcs.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.Util;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.network.NetworkHooks;
import noppes.npcs.CustomCloneItems;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.controllers.ServerCloneController;
import noppes.npcs.shared.common.util.LogWriter;

public class EntityCloneStructureSpawner extends Entity {
    private static final DataParameter<String> DATA_CLONE_NAME =
            EntityDataManager.defineId(EntityCloneStructureSpawner.class, DataSerializers.STRING);
    private static final DataParameter<Integer> DATA_CLONE_TAB =
            EntityDataManager.defineId(EntityCloneStructureSpawner.class, DataSerializers.INT);
    private static final DataParameter<Boolean> DATA_MANUAL_PLACEMENT =
            EntityDataManager.defineId(EntityCloneStructureSpawner.class, DataSerializers.BOOLEAN);

    private static final float CREATIVE_RADIUS = 16.0f;
    private static boolean isInvisible = true;

    private boolean failed;
    public double spin;
    public double spinO;

    public EntityCloneStructureSpawner(final EntityType<?> type, final World level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    public void tick() {
        this.noPhysics = true;
        this.spinO = this.spin;
        this.spin += 1.0;
        super.tick();

        if (this.level.isClientSide) {
            return;
        }
        if (this.isManualPlacement() || this.failed) {
            return;
        }
        final String cloneName = this.getCloneName();
        if (cloneName == null || cloneName.isEmpty()) {
            return;
        }
        if (this.hasCreativeNearby()) {
            return;
        }

        final int cloneTab = this.getCloneTab();
        try {
            if (!ServerCloneController.Instance.hasClone(cloneTab, cloneName)) {
                LogWriter.error("CloneStructureSpawner: clone not found tab=" + cloneTab + " name=" + cloneName
                        + " at " + this.blockPosition());
                this.failed = true;
                return;
            }
            final IEntity spawned = NpcAPI.Instance().getClones().spawn(
                    this.getX(), this.getY(), this.getZ(),
                    cloneTab, cloneName,
                    NpcAPI.Instance().getIWorld((ServerWorld) this.level));
            if (spawned == null) {
                LogWriter.error("CloneStructureSpawner: spawn failed tab=" + cloneTab + " name=" + cloneName
                        + " at " + this.blockPosition());
                this.failed = true;
                return;
            }
            this.remove();
        } catch (final Exception e) {
            LogWriter.except(e);
            this.failed = true;
        }
    }

    private boolean hasCreativeNearby() {
        final AxisAlignedBB box = this.getBoundingBox().inflate(CREATIVE_RADIUS);
        for (final PlayerEntity player : this.level.getEntitiesOfClass(PlayerEntity.class, box)) {
            if (player.isCreative()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ActionResultType interact(final PlayerEntity player, final Hand hand) {
        if (this.level.isClientSide) {
            return ActionResultType.SUCCESS;
        }
        if (!player.isCreative()) {
            return ActionResultType.PASS;
        }

        final ItemStack held = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && held.isEmpty()) {
            final boolean manual = !this.isManualPlacement();
            this.setManualPlacement(manual);
            if (!manual) {
                this.failed = false;
            }
            if (manual) {
                player.sendMessage(new StringTextComponent("Clone Structure Spawner: ManualPlacement=true (editing)")
                        .withStyle(TextFormatting.YELLOW), Util.NIL_UUID);
            } else {
                player.sendMessage(new StringTextComponent("Clone Structure Spawner: Armed for structure (ManualPlacement=false)")
                        .withStyle(TextFormatting.GREEN), Util.NIL_UUID);
            }
            return ActionResultType.SUCCESS;
        }

        if (!held.isEmpty() && (held.getItem() == Items.NAME_TAG || held.hasCustomHoverName())) {
            final String name = held.getHoverName().getString().trim();
            if (!name.isEmpty()) {
                this.setCloneName(name);
                this.failed = false;
                player.sendMessage(new StringTextComponent("CloneName set to: " + name)
                        .withStyle(TextFormatting.AQUA), Util.NIL_UUID);
                return ActionResultType.SUCCESS;
            }
        }

        player.sendMessage(new StringTextComponent("Clone Structure Spawner")
                .withStyle(TextFormatting.GOLD), Util.NIL_UUID);
        player.sendMessage(new StringTextComponent("  CloneTab: ").withStyle(TextFormatting.GREEN)
                .append(new StringTextComponent(Integer.toString(this.getCloneTab())).withStyle(TextFormatting.WHITE)), Util.NIL_UUID);
        player.sendMessage(new StringTextComponent("  CloneName: ").withStyle(TextFormatting.GREEN)
                .append(new StringTextComponent(this.getCloneName().isEmpty() ? "(unset)" : this.getCloneName())
                        .withStyle(TextFormatting.WHITE)), Util.NIL_UUID);
        player.sendMessage(new StringTextComponent("  ManualPlacement: ").withStyle(TextFormatting.GREEN)
                .append(new StringTextComponent(Boolean.toString(this.isManualPlacement()))
                        .withStyle(this.isManualPlacement() ? TextFormatting.YELLOW : TextFormatting.RED)), Util.NIL_UUID);
        if (this.failed) {
            player.sendMessage(new StringTextComponent("  Failed: true").withStyle(TextFormatting.RED), Util.NIL_UUID);
        }
        player.sendMessage(new StringTextComponent("Name tag / named item = set CloneName; Shift+empty hand = Arm/Disarm")
                .withStyle(TextFormatting.GRAY), Util.NIL_UUID);
        return ActionResultType.SUCCESS;
    }

    @Override
    public boolean hurt(final DamageSource source, final float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        }
        final Entity attacker = source.getEntity();
        if (attacker instanceof PlayerEntity && ((PlayerEntity) attacker).isCreative()) {
            if (attacker.isShiftKeyDown()) {
                this.remove();
            }
        }
        return false;
    }

    @Override
    public ItemStack getPickedResult(final RayTraceResult target) {
        final ItemStack stack = new ItemStack(CustomCloneItems.clone_structure_spawner);
        final CompoundNBT tag = stack.getOrCreateTag();
        tag.putString("CloneName", this.getCloneName());
        tag.putInt("CloneTab", this.getCloneTab());
        return stack;
    }

    public String getCloneName() {
        return this.entityData.get(DATA_CLONE_NAME);
    }

    public void setCloneName(final String name) {
        this.entityData.set(DATA_CLONE_NAME, name == null ? "" : name);
    }

    public int getCloneTab() {
        return this.entityData.get(DATA_CLONE_TAB);
    }

    public void setCloneTab(final int tab) {
        this.entityData.set(DATA_CLONE_TAB, tab);
    }

    public boolean isManualPlacement() {
        return this.entityData.get(DATA_MANUAL_PLACEMENT);
    }

    public void setManualPlacement(final boolean manual) {
        this.entityData.set(DATA_MANUAL_PLACEMENT, manual);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_CLONE_NAME, "");
        this.entityData.define(DATA_CLONE_TAB, 1);
        this.entityData.define(DATA_MANUAL_PLACEMENT, true);
    }

    @Override
    protected void readAdditionalSaveData(final CompoundNBT nbt) {
        this.setCloneName(nbt.getString("CloneName"));
        if (nbt.contains("CloneTab")) {
            this.setCloneTab(nbt.getInt("CloneTab"));
        }
        if (nbt.contains("ManualPlacement")) {
            this.setManualPlacement(nbt.getBoolean("ManualPlacement"));
        }
        this.failed = nbt.getBoolean("Failed");
    }

    @Override
    protected void addAdditionalSaveData(final CompoundNBT nbt) {
        nbt.putString("CloneName", this.getCloneName());
        nbt.putInt("CloneTab", this.getCloneTab());
        nbt.putBoolean("ManualPlacement", this.isManualPlacement());
        if (this.failed) {
            nbt.putBoolean("Failed", true);
        }
    }

    @Override
    public IPacket<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean ignoreExplosion() {
        return true;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean isInvisible() {
        if (!this.level.isClientSide) {
            return super.isInvisible();
        }
        return isInvisible;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public boolean isInvisibleTo(final PlayerEntity player) {
        if (player.isSpectator() || player.isCreative()) {
            isInvisible = false;
            return false;
        }
        isInvisible = true;
        return true;
    }
}
