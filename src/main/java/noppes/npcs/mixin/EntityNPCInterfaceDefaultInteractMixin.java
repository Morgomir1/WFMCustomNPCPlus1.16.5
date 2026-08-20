package noppes.npcs.mixin;

import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stock CNPC fills interact line 0 with {@code CustomNpcs.DefaultInteractLine} ("Hello @p").
 * New NPCs should have no interact phrase; saved/cloned NPCs restore lines from NBT after this.
 */
@Mixin(EntityNPCInterface.class)
public abstract class EntityNPCInterfaceDefaultInteractMixin {

    @Inject(method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;)V",
            at = @At("TAIL"), remap = false)
    private void cnpc$clearDefaultInteractLine(final CallbackInfo ci) {
        final EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        self.advanced.interactLines.lines.clear();
    }
}
