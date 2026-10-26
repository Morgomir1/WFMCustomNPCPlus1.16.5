package noppes.npcs.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.ModelDataShared;
import noppes.npcs.entity.EntityCustomModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

/**
 * Любой {@code updateClient} (в т.ч. {@code npc.setRotation} при смене orientation) шлёт
 * PacketNpcUpdate → {@code readSpawnData} → {@code modelData.load} → {@code setEntity} обнуляет
 * entity. Для GeckoLib-модели это новый {@link EntityCustomModel}: теряется manualAnim и
 * playOnce-клип обрывается на середине. Если модель-сущность та же — оставляем старую.
 */
@Mixin(ModelDataShared.class)
public abstract class ModelDataSharedKeepGeoEntityMixin {
    @Shadow(remap = false)
    protected ResourceLocation entityName;
    @Shadow(remap = false)
    protected LivingEntity entity;
    @Shadow(remap = false)
    public CompoundNBT extra;

    @Unique
    private LivingEntity cnpc$keptEntity;
    @Unique
    private ResourceLocation cnpc$keptName;
    @Unique
    private CompoundNBT cnpc$keptExtra;

    @Inject(method = "load", at = @At("HEAD"), remap = false)
    private void cnpc$rememberGeoEntity(final CompoundNBT compound, final CallbackInfo ci) {
        if (this.entity instanceof EntityCustomModel) {
            this.cnpc$keptEntity = this.entity;
            this.cnpc$keptName = this.entityName;
            this.cnpc$keptExtra = this.extra;
        } else {
            this.cnpc$keptEntity = null;
        }
    }

    @Inject(method = "load", at = @At("RETURN"), remap = false)
    private void cnpc$restoreGeoEntity(final CompoundNBT compound, final CallbackInfo ci) {
        final LivingEntity kept = this.cnpc$keptEntity;
        this.cnpc$keptEntity = null;
        if (kept == null || this.entity != null) {
            return;
        }
        if (Objects.equals(this.cnpc$keptName, this.entityName)
                && Objects.equals(this.cnpc$keptExtra, this.extra)) {
            this.entity = kept;
        }
        this.cnpc$keptName = null;
        this.cnpc$keptExtra = null;
    }
}
