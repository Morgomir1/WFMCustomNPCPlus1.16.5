package noppes.npcs.mixin;

import net.minecraft.nbt.CompoundNBT;
import noppes.npcs.client.gui.mainmenu.GuiNPCInv;
import noppes.npcs.client.gui.mainmenu.GuiNpcAI;
import noppes.npcs.client.gui.mainmenu.GuiNpcDisplay;
import noppes.npcs.client.gui.mainmenu.GuiNpcStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NPC menu tabs request their section from the server when opened and send the whole section back when
 * left. Leaving before the reply arrived sent client placeholders, e.g. 100% drop chances, and a late reply
 * could land on the next opened tab. A tab now accepts only its own section and sends nothing before it.
 */
@Mixin(value = {GuiNpcDisplay.class, GuiNpcStats.class, GuiNpcAI.class, GuiNPCInv.class}, remap = false)
public abstract class NpcMenuServerDataGuardMixin {
    @Unique
    private boolean wfm$serverDataLoaded;

    @Inject(method = "setGuiData(Lnet/minecraft/nbt/CompoundNBT;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void wfm$acceptOwnSectionOnly(CompoundNBT compound, CallbackInfo ci) {
        if (!compound.contains(this.wfm$sectionKey())) {
            ci.cancel();
            return;
        }
        this.wfm$serverDataLoaded = true;
    }

    @Inject(method = "save()V", at = @At("HEAD"), cancellable = true, require = 1)
    private void wfm$skipSaveBeforeServerData(CallbackInfo ci) {
        if (!this.wfm$serverDataLoaded) {
            ci.cancel();
        }
    }

    @Unique
    private String wfm$sectionKey() {
        Object gui = this;
        if (gui instanceof GuiNPCInv) {
            return "NpcInv";
        }
        if (gui instanceof GuiNpcStats) {
            return "MaxHealth";
        }
        if (gui instanceof GuiNpcAI) {
            return "MovementType";
        }
        return "Name";
    }
}
