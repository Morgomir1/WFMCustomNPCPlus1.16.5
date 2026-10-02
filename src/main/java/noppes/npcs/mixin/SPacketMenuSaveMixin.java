package noppes.npcs.mixin;

import noppes.npcs.constants.EnumMenuType;
import noppes.npcs.controllers.LinkedNpcController;
import noppes.npcs.packets.PacketServerBasic;
import noppes.npcs.packets.server.SPacketMenuSave;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Upstream CustomNPCs writes a linked NPC template only when the menu is closed with its X button,
 * so edits closed with Esc/E never reached the other linked NPCs. Every saved menu section of a linked
 * NPC now updates the template the same way the X button does.
 */
@Mixin(value = SPacketMenuSave.class, remap = false)
public abstract class SPacketMenuSaveMixin extends PacketServerBasic {
    @Shadow
    private EnumMenuType type;

    @Inject(method = "handle()V", at = @At("HEAD"), require = 1)
    private void wfm$catchUpLinkedTemplate(CallbackInfo ci) {
        // Take newer template edits first, otherwise this NPC would write its stale sections back.
        if (this.wfm$isLinkedSection() && this.npc.linkedData.time > this.npc.linkedLast) {
            LinkedNpcController.Instance.loadNpcData(this.npc);
        }
    }

    @Inject(method = "handle()V", at = @At("TAIL"), require = 1)
    private void wfm$updateLinkedTemplate(CallbackInfo ci) {
        if (!this.wfm$isLinkedSection()) {
            return;
        }
        LinkedNpcController.Instance.saveNpcData(this.npc);
        // The template now holds this NPC's state. Without this the NPC reloads its own write a second
        // later and loses edits made meanwhile through other packets (roles, jobs, dialogs).
        this.npc.linkedLast = this.npc.linkedData.time;
    }

    @Unique
    private boolean wfm$isLinkedSection() {
        if (this.npc == null || this.npc.linkedData == null) {
            return false;
        }
        return this.type == EnumMenuType.DISPLAY || this.type == EnumMenuType.STATS
                || this.type == EnumMenuType.INVENTORY || this.type == EnumMenuType.AI
                || this.type == EnumMenuType.ADVANCED || this.type == EnumMenuType.MODEL
                || this.type == EnumMenuType.TRANSFORM;
    }
}
