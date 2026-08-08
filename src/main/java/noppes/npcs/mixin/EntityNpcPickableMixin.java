package noppes.npcs.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import noppes.npcs.NpcSoftVisibility;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Soft-hidden NPCs must not be mouse-picked.
 * <p>
 * Must target {@link LivingEntity}: it overrides {@code Entity#isPickable()} without calling
 * {@code super}, so a mixin on {@code Entity} never ran for NPCs. Vanilla
 * {@code GameRenderer#pick} filters with {@code isPickable()}.
 */
@Mixin(LivingEntity.class)
public abstract class EntityNpcPickableMixin {

    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void cnpc$softHideNotPickable(final CallbackInfoReturnable<Boolean> cir) {
        final LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof EntityNPCInterface)) {
            return;
        }
        final PlayerEntity player = Minecraft.getInstance().player;
        if (player != null && NpcSoftVisibility.isInvisibleTo((EntityNPCInterface) self, player)) {
            cir.setReturnValue(false);
        }
    }
}
