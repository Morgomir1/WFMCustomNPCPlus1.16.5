package noppes.npcs.mixin;

import noppes.npcs.constants.EnumMenuType;
import noppes.npcs.entity.NpcRespawnLog;
import noppes.npcs.entity.NpcRespawnLogged;
import noppes.npcs.packets.PacketServerBasic;
import noppes.npcs.packets.server.SPacketMenuSave;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turning "Log to server" on or off in the NPC stats is itself written to the server log, with the player.
 */
@Mixin(value = SPacketMenuSave.class, remap = false)
public abstract class SPacketMenuSaveRespawnLogMixin extends PacketServerBasic {
    @Shadow
    private EnumMenuType type;

    @Unique
    private boolean cnpc$wasRespawnLogged;

    @Inject(method = "handle()V", at = @At("HEAD"))
    private void cnpc$rememberRespawnLog(final CallbackInfo ci) {
        if (this.type == EnumMenuType.STATS && this.npc != null) {
            this.cnpc$wasRespawnLogged = ((NpcRespawnLogged) this.npc.stats).cnpc$isRespawnLogged();
        }
    }

    @Inject(method = "handle()V", at = @At("RETURN"))
    private void cnpc$logRespawnLogSwitch(final CallbackInfo ci) {
        if (this.type == EnumMenuType.STATS && this.npc != null) {
            NpcRespawnLog.onSwitch(this.npc, this.cnpc$wasRespawnLogged, this.player.getName().getString());
        }
    }
}
