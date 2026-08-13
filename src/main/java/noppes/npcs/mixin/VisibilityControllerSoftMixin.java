package noppes.npcs.mixin;

import net.minecraft.entity.player.ServerPlayerEntity;
import noppes.npcs.CustomItems;
import noppes.npcs.NpcSoftVisibility;
import noppes.npcs.controllers.VisibilityController;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Jar {@code VisibilityController} no-ops unless {@code EnableInvisibleNpcs} is set.
 * Soft-hide does not need that flag (it existed for {@code removeEntity}). Always
 * re-check Display Availability so After-dialog hide works.
 */
@Mixin(VisibilityController.class)
public abstract class VisibilityControllerSoftMixin {

    @Shadow(remap = false)
    private Map<Integer, EntityNPCInterface> trackedEntityHashTable;

    @Inject(method = "onUpdate", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpc$onUpdateAlways(final ServerPlayerEntity player, final CallbackInfo ci) {
        if (player == null || this.trackedEntityHashTable == null) {
            ci.cancel();
            return;
        }
        for (final EntityNPCInterface npc : this.trackedEntityHashTable.values()) {
            VisibilityController.checkIsVisible(npc, player);
        }
        ci.cancel();
    }

    @Inject(method = "checkIsVisible", at = @At("HEAD"), cancellable = true, remap = false)
    private static void cnpc$checkAlways(final EntityNPCInterface npc, final ServerPlayerEntity playerMP,
            final CallbackInfo ci) {
        if (npc == null || playerMP == null) {
            ci.cancel();
            return;
        }
        if (NpcSoftVisibility.isDisplayVisibleTo(npc, playerMP) || playerMP.isSpectator()
                || playerMP.getMainHandItem().getItem() == CustomItems.wand) {
            npc.setVisible(playerMP);
        } else {
            npc.setInvisible(playerMP);
        }
        ci.cancel();
    }
}
