package noppes.npcs.mixin;

import noppes.npcs.CustomNpcs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Default interact line is "Hello @p" in CNPC statics and CustomNpcs.cfg.
 * Keep it empty so new NPCs (and a rewritten config) have no stock greeting.
 */
@Mixin(CustomNpcs.class)
public abstract class CustomNpcsDefaultInteractLineMixin {

    @Inject(method = "<clinit>", at = @At("TAIL"), remap = false)
    private static void cnpc$emptyDefaultInteractLineClinit(final CallbackInfo ci) {
        CustomNpcs.DefaultInteractLine = "";
    }

    @Inject(method = "setup", at = @At("TAIL"), remap = false)
    private void cnpc$emptyDefaultInteractLineAfterConfig(final CallbackInfo ci) {
        CustomNpcs.DefaultInteractLine = "";
    }
}
