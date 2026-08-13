package noppes.npcs.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import noppes.npcs.constants.EnumMenuType;
import noppes.npcs.controllers.VisibilityController;
import noppes.npcs.packets.PacketServerBasic;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.client.PacketNpcUpdate;
import noppes.npcs.packets.server.SPacketMenuSave;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After Display is saved, immediately re-check per-player visibility so dialog /
 * Visible=No changes apply without waiting for the next soft-visibility tick.
 * <p>
 * {@code npc} lives on {@link PacketServerBasic}, not on {@link SPacketMenuSave} —
 * access it via the superclass, do not {@code @Shadow} it on the packet class.
 */
@Mixin(SPacketMenuSave.class)
public abstract class SPacketMenuSaveVisibilityMixin extends PacketServerBasic {

    @Shadow(remap = false)
    private EnumMenuType type;

    @Inject(method = "handle", at = @At("TAIL"), remap = false)
    private void cnpc$refreshVisibility(final CallbackInfo ci) {
        if (this.type != EnumMenuType.DISPLAY || this.npc == null || this.npc.level == null) {
            return;
        }
        VisibilityController.instance.trackNpc(this.npc);
        Packets.sendNearby(this.npc, new PacketNpcUpdate(this.npc.getId(), this.npc.writeSpawnData()));
        for (final PlayerEntity nearby : this.npc.level.players()) {
            if (nearby instanceof ServerPlayerEntity) {
                VisibilityController.checkIsVisible(this.npc, (ServerPlayerEntity) nearby);
            }
        }
    }
}
