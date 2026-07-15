package noppes.npcs.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.IServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.template.Template;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import noppes.npcs.entity.EntityCloneStructureSpawner;
import noppes.npcs.shared.common.util.LogWriter;

import java.util.Optional;

/**
 * Structure templates must store/load clone spawners as armed ({@code ManualPlacement=false})
 * so worldgen / structure load replaces the marker with the clone. Hand-placed entities stay
 * UNARMED via chunk NBT (those paths do not go through Template).
 */
@Mixin(Template.class)
public class TemplateMixin {

    private static final String CLONE_SPAWNER_ID = "customnpcs:clone_structure_spawner";

    @Redirect(
            method = "fillEntityList",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;save(Lnet/minecraft/nbt/CompoundNBT;)Z"
            )
    )
    private boolean customnpcs$disarmCloneSpawnerOnStructureSave(final Entity entity, final CompoundNBT nbt) {
        final boolean saved = entity.save(nbt);
        if (entity instanceof EntityCloneStructureSpawner) {
            nbt.putBoolean("ManualPlacement", false);
            nbt.remove("Failed");
            LogWriter.info("TemplateMixin: structure save armed clone spawner clone="
                    + ((EntityCloneStructureSpawner) entity).getCloneName());
        }
        return saved;
    }

    /** Force NBT before EntityType.create / load so ManualPlacement from old templates is ignored. */
    @Inject(method = "createEntityIgnoreException", at = @At("HEAD"))
    private static void customnpcs$armCloneSpawnerNbt(final IServerWorld level, final CompoundNBT nbt,
                                                       final CallbackInfoReturnable<Optional<Entity>> cir) {
        if (CLONE_SPAWNER_ID.equals(nbt.getString("id"))) {
            nbt.putBoolean("ManualPlacement", false);
            nbt.remove("Failed");
        }
    }

    /** Belt-and-suspenders: force the live entity after NBT load (covers any future load quirks). */
    @Inject(method = "createEntityIgnoreException", at = @At("RETURN"))
    private static void customnpcs$armCloneSpawnerEntity(final IServerWorld level, final CompoundNBT nbt,
                                                          final CallbackInfoReturnable<Optional<Entity>> cir) {
        final Optional<Entity> created = cir.getReturnValue();
        if (created == null || !created.isPresent()) {
            return;
        }
        final Entity entity = created.get();
        if (entity instanceof EntityCloneStructureSpawner) {
            final EntityCloneStructureSpawner spawner = (EntityCloneStructureSpawner) entity;
            spawner.armFromStructureTemplate();
            LogWriter.info("TemplateMixin: structure load armed clone spawner clone="
                    + spawner.getCloneName() + " at " + spawner.blockPosition());
        }
    }

    @Redirect(
            method = "createEntityIgnoreException",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/EntityType;create(Lnet/minecraft/nbt/CompoundNBT;Lnet/minecraft/world/World;)Ljava/util/Optional;"
            )
    )
    private static Optional<Entity> customnpcs$armCloneSpawnerOnStructureLoad(final CompoundNBT nbt, final World world) {
        if (CLONE_SPAWNER_ID.equals(nbt.getString("id"))) {
            nbt.putBoolean("ManualPlacement", false);
            nbt.remove("Failed");
        }
        return EntityType.create(nbt, world);
    }
}
