package noppes.npcs.mixin;

import net.minecraft.entity.EntitySize;
import net.minecraft.entity.Pose;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.NpcHitboxHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityNPCInterface.class)
public abstract class EntityNPCInterfaceHitboxMixin {

    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void cnpc$hitboxDimensions(Pose pose, CallbackInfoReturnable<EntitySize> cir) {
        cir.setReturnValue(NpcHitboxHelper.getDimensions((EntityNPCInterface) (Object) this, pose));
    }
}
