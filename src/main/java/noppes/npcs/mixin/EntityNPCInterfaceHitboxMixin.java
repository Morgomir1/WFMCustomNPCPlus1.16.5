package noppes.npcs.mixin;

import net.minecraft.entity.EntitySize;
import net.minecraft.entity.Pose;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityNPCInterface.class)
public abstract class EntityNPCInterfaceHitboxMixin {

    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void cnpc$hitboxDimensions(Pose pose, CallbackInfoReturnable<EntitySize> cir) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        EntitySize size = EntitySize.scalable(self.display.getHitboxWidth(), self.display.getHitboxHeight());

        if (self.currentAnimation == 2 || self.currentAnimation == 7 || self.deathTime > 0) {
            size = EntitySize.scalable(0.8f, 0.4f);
        } else if (self.isPassenger() || self.currentAnimation == 1) {
            size = size.scale(1.0f, 0.77f);
        }

        if (self.display.getHitboxState() == 1 || (self.isKilled() && self.stats.hideKilledBody)) {
            size = EntitySize.scalable(1.0E-5f, size.height);
        }

        if (size.width / 2.0f > self.level.getMaxEntityRadius()) {
            self.level.increaseMaxEntityRadius(size.width / 2.0);
        }
        cir.setReturnValue(size);
    }
}
