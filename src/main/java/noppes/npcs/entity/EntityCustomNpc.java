package noppes.npcs.entity;

import net.minecraft.entity.CreatureEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Pose;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.World;
import noppes.npcs.CustomNpcs;
import noppes.npcs.ModelData;
import noppes.npcs.ModelPartData;
import noppes.npcs.client.EntityUtil;
import noppes.npcs.constants.EnumParts;

public class EntityCustomNpc extends EntityNPCFlying {
   public ModelData modelData = new ModelData();

   public EntityCustomNpc(EntityType<? extends CreatureEntity> type, World world) {
      super(type, world);
      if (!CustomNpcs.EnableDefaultEyes) {
         this.modelData.eyes.type = -1;
      }
   }

   public void readAdditionalSaveData(CompoundNBT compound) {
      if (compound.contains("NpcModelData")) {
         this.modelData.load(compound.getCompound("NpcModelData"));
      }

      super.readAdditionalSaveData(compound);
   }

   public void addAdditionalSaveData(CompoundNBT compound) {
      super.addAdditionalSaveData(compound);
      compound.put("NpcModelData", this.modelData.save());
   }

   public boolean saveAsPassenger(CompoundNBT compound) {
      boolean bo = super.saveAsPassenger(compound);
      if (bo) {
         String s = this.getEncodeId();
         if (s.equals("minecraft:customnpcs.customnpc")) {
            compound.putString("id", "customnpcs:customnpc");
         }
      }

      return bo;
   }

   public void tick() {
      super.tick();
      if (this.isClientSide()) {
         ModelPartData particles = this.modelData.getPartData(EnumParts.PARTICLES);
         if (particles != null && !this.isKilled()) {
            CustomNpcs.proxy.spawnParticle(this, "ModelData", new Object[]{this.modelData, particles});
         }

         LivingEntity entity = this.modelData.getEntity(this);
         if (entity != null) {
            try {
               entity.tick();
            } catch (Exception var4) {
            }

            EntityUtil.Copy(this, entity);
         }
      }

      this.modelData.eyes.update(this);
   }

   public boolean startRiding(Entity par1Entity, boolean force) {
      boolean b = super.startRiding(par1Entity, force);
      this.refreshDimensions();
      return b;
   }

   public void refreshDimensions() {
      Entity entity = this.modelData.getEntity(this);
      if (entity != null) {
         entity.refreshDimensions();
      }

      super.refreshDimensions();
   }

   public EntitySize getDimensions(Pose pos) {
      Entity entity = this.modelData.getEntity(this);
      if (entity instanceof EntityNPCInterface) {
         return entity.getDimensions(pos);
      }
      // Absolute hitbox W/H from display — not Size, not cloned-entity Size scale
      return NpcHitboxHelper.getDimensions(this, pos);
   }
}
