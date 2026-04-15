package noppes.npcs.mixin;

import java.util.Optional;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.IServerWorld;
import net.minecraft.world.gen.feature.template.Template;
import noppes.npcs.StructureTemplateNpcSpawnHandler;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Template.class)
public abstract class TemplateEntitySpawnMixin {
    @Inject(
        method = "createEntityIgnoreException",
        at = @At("RETURN")
    )
    private static void wfm$fixCustomNpcAiAfterTemplateEntity(
        final IServerWorld world,
        final CompoundNBT tag,
        final CallbackInfoReturnable<Optional<Entity>> cir
    ) {
        final Optional<Entity> opt = cir.getReturnValue();
        if (!opt.isPresent()) {
            return;
        }
        final Entity e = opt.get();
        if (e instanceof EntityNPCInterface) {
            e.getPersistentData().putBoolean(StructureTemplateNpcSpawnHandler.PENDING_STRUCTURE_AI_FIX, true);
        }
    }
}
