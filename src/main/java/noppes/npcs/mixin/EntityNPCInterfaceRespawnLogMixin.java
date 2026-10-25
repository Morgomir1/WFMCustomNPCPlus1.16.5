package noppes.npcs.mixin;

import net.minecraft.util.DamageSource;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.NpcRespawnLog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Writes boss deaths and NPC resets to the server log ({@link NpcRespawnLog}). The NPC's own callers of
 * {@code reset()} — the respawn timer and out-of-world damage on a corpse, which is also {@code /kill} — name
 * themselves here; the packets, the command and the script API do it in their own classes.
 */
@Mixin(EntityNPCInterface.class)
public abstract class EntityNPCInterfaceRespawnLogMixin {

    @Inject(method = "reset", at = @At("HEAD"), remap = false)
    private void cnpc$logReset(final CallbackInfo ci) {
        NpcRespawnLog.onReset((EntityNPCInterface) (Object) this);
    }

    @Inject(method = "die", at = @At("TAIL"))
    private void cnpc$logDeath(final DamageSource source, final CallbackInfo ci) {
        NpcRespawnLog.onDeath((EntityNPCInterface) (Object) this, source);
    }

    @Inject(method = "tickDeath", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityNPCInterface;reset()V", remap = false), require = 1)
    private void cnpc$resetByTimer(final CallbackInfo ci) {
        NpcRespawnLog.expectReset("respawn timer", false);
    }

    @Inject(method = "hurt", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityNPCInterface;reset()V", remap = false), require = 1)
    private void cnpc$resetByVoidDamage(final DamageSource source, final float amount,
            final CallbackInfoReturnable<Boolean> cir) {
        NpcRespawnLog.expectReset("out-of-world damage on the corpse (/kill or the void)", true);
    }
}
