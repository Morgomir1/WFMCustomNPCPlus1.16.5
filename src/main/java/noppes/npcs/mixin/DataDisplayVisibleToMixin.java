package noppes.npcs.mixin;

import net.minecraft.entity.player.ServerPlayerEntity;
import noppes.npcs.NpcSoftVisibility;
import noppes.npcs.entity.data.DataDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Jar inverts Yes/No around Availability (Yes = show when matched). Display Availability
 * is treated as hide-when-matched for every condition type.
 */
@Mixin(DataDisplay.class)
public abstract class DataDisplayVisibleToMixin {

    @Inject(method = "isVisibleTo(Lnet/minecraft/entity/player/ServerPlayerEntity;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpc$isVisibleTo(final ServerPlayerEntity player, final CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(NpcSoftVisibility.isDisplayVisibleTo((DataDisplay) (Object) this, player));
    }
}
