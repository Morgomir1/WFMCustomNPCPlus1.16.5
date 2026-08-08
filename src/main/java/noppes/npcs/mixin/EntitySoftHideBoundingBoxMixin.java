package noppes.npcs.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.AxisAlignedBB;
import noppes.npcs.NpcSoftVisibility;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Soft-hidden NPCs keep a full server hitbox, but on the client their AABB collapses
 * to a point so HUD mods that raycast {@code World#getEntities} + bounding boxes
 * (e.g. RPG-HUD Entity Inspect) cannot "see" them. Vanilla pick already uses
 * {@code isPickable}; those mods skip that check.
 */
@Mixin(Entity.class)
public abstract class EntitySoftHideBoundingBoxMixin {

    @Inject(method = "getBoundingBox", at = @At("HEAD"), cancellable = true)
    private void cnpc$softHideEmptyClientBb(final CallbackInfoReturnable<AxisAlignedBB> cir) {
        final Entity self = (Entity) (Object) this;
        if (!(self instanceof EntityNPCInterface) || self.level == null || !self.level.isClientSide) {
            return;
        }
        final PlayerEntity player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (NpcSoftVisibility.isInvisibleTo((EntityNPCInterface) self, player)) {
            final double x = self.getX();
            final double y = self.getY();
            final double z = self.getZ();
            cir.setReturnValue(new AxisAlignedBB(x, y, z, x, y, z));
        }
    }
}
