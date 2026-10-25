package noppes.npcs.mixin;

import noppes.npcs.entity.NpcRespawnLog;
import noppes.npcs.packets.PacketServerBasic;
import noppes.npcs.packets.server.SPacketMenuClose;
import noppes.npcs.packets.server.SPacketRemoteNpcReset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The two wand resets: closing the NPC menu with its X button and the Reset button of the remote, which reaches
 * every loaded NPC of the world. Both raise a corpse at once; the log names the player ({@link NpcRespawnLog}).
 */
@Mixin(value = {SPacketMenuClose.class, SPacketRemoteNpcReset.class}, remap = false)
public abstract class NpcResetPacketLogMixin extends PacketServerBasic {

    @Inject(method = "handle()V", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityNPCInterface;reset()V"), require = 1)
    private void cnpc$logWandReset(final CallbackInfo ci) {
        final String how = (Object) this instanceof SPacketRemoteNpcReset ? "wand remote Reset" : "wand menu closed";
        NpcRespawnLog.expectReset(how + ", player " + this.player.getName().getString(), true);
    }
}
