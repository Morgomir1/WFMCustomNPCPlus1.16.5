package noppes.npcs.mixin;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.client.PacketNpcVisibleFalse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;

/**
 * Jar {@code playerTracking} does {@code npc.tracking.add(playerId)} <em>before</em>
 * {@code VisibilityController.checkIsVisible}. Soft {@code setVisible} then sees the id
 * already present and skips {@code PacketNpcUpdate}. Membership must be owned only by
 * setVisible / setInvisible.
 * <p>
 * After skipping the pre-add, a first-time hide would not send {@code PacketNpcVisibleFalse}
 * (tracking was empty). Vanilla spawn already put the entity on the client, so send hide
 * when checkIsVisible left the player out of {@code tracking}.
 */
@Mixin(targets = "noppes.npcs.ServerEventsHandler")
public abstract class ServerEventsHandlerTrackingMixin {

    @Redirect(
            method = "playerTracking",
            at = @At(value = "INVOKE", target = "Ljava/util/HashSet;add(Ljava/lang/Object;)Z"),
            remap = false
    )
    private boolean cnpc$skipTrackingPreAdd(final HashSet<?> tracking, final Object playerId) {
        return false;
    }

    @Inject(method = "playerTracking", at = @At("TAIL"), remap = false)
    private void cnpc$sendInitialHide(final PlayerEvent.StartTracking event, final CallbackInfo ci) {
        if (!(event.getTarget() instanceof EntityNPCInterface)
                || !(event.getPlayer() instanceof ServerPlayerEntity)) {
            return;
        }
        final EntityNPCInterface npc = (EntityNPCInterface) event.getTarget();
        final ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
        if (!npc.tracking.contains(player.getId())) {
            Packets.send(player, new PacketNpcVisibleFalse(npc.getId()));
        }
    }
}
