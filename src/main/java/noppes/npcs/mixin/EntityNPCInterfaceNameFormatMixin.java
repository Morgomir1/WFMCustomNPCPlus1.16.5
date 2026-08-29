package noppes.npcs.mixin;

import net.minecraft.util.text.ITextComponent;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityNPCInterface.class)
public abstract class EntityNPCInterfaceNameFormatMixin {

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void cnpc$formattedName(final CallbackInfoReturnable<ITextComponent> cir) {
        final EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        cir.setReturnValue(npc.display.getFormattedName());
    }
}
