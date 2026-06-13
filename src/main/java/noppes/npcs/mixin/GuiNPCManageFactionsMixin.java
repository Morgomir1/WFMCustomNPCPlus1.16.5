package noppes.npcs.mixin;

import net.minecraft.util.ResourceLocation;
import noppes.npcs.bridge.WfmFactionBridge;
import noppes.npcs.client.gui.global.GuiNPCManageFactions;
import noppes.npcs.controllers.data.Faction;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wfm.common.data.LOTRLevelData;
import wfm.common.fac.FactionSettingsManager;

@Mixin(value = GuiNPCManageFactions.class, remap = false)
public abstract class GuiNPCManageFactionsMixin {
    @Shadow
    private Faction faction;

    @Inject(method = "func_231160_c_()V", at = @At("TAIL"), remap = false)
    private void wfm$addFactionIdField(CallbackInfo ci) {
        if (this.faction == null || this.faction.id == -1) return;

        GuiNPCManageFactions self = (GuiNPCManageFactions)(Object)this;
        ResourceLocation id = ((WfmFactionBridge)(Object)this.faction).wfm$getFactionId();
        String value = id == null ? "" : id.toString();

        self.addLabel(new GuiLabel(101, "WFM ID", self.guiLeft + 8, self.guiTop + 121));
        self.addTextField(new GuiTextFieldNop(101, self, self.guiLeft + 40, self.guiTop + 116, 136, 20, value));
        self.getTextField(101).setMaxLength(128);
    }

    @Inject(method = "unFocused", at = @At("TAIL"), remap = false)
    private void wfm$onUnfocused(GuiTextFieldNop field, CallbackInfo ci) {
        if (this.faction == null || field.id != 101) return;
        String raw = field.getValue() == null ? "" : field.getValue().trim();
        ResourceLocation rl = raw.isEmpty() ? null : ResourceLocation.tryParse(raw);
        ((WfmFactionBridge)(Object)this.faction).wfm$setFactionId(rl);
    }

    @Inject(method = "save", at = @At("HEAD"), remap = false)
    private void wfm$syncBeforeSave(CallbackInfo ci) {
        if (this.faction == null) return;
        GuiNPCManageFactions self = (GuiNPCManageFactions)(Object)this;
        GuiTextFieldNop field = self.getTextField(101);
        if (field == null) return;

        String raw = field.getValue() == null ? "" : field.getValue().trim();
        ResourceLocation rl = raw.isEmpty() ? null : ResourceLocation.tryParse(raw);
        ((WfmFactionBridge)(Object)this.faction).wfm$setFactionId(rl);
    }


}