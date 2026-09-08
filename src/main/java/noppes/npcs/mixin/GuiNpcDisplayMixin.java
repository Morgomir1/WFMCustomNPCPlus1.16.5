package noppes.npcs.mixin;

import noppes.npcs.bridge.WfmDisplayBridge;
import noppes.npcs.client.gui.mainmenu.GuiNpcDisplay;
import noppes.npcs.entity.data.DataDisplay;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNpcDisplay.class, remap = false)
public abstract class GuiNpcDisplayMixin {
    private static final int SHOW_ON_MAP_ID = 17;

    @Shadow
    private DataDisplay display;

    @Inject(method = "func_231160_c_()V", at = @At("TAIL"))
    private void wfm$addShowOnMap(CallbackInfo ci) {
        if (this.display == null) {
            return;
        }
        GuiNpcDisplay self = (GuiNpcDisplay) (Object) this;
        int y = self.guiTop + 200;
        boolean showOnMap = ((WfmDisplayBridge) this.display).wfm$isShowOnMap();
        self.addLabel(new GuiLabel(SHOW_ON_MAP_ID, "display.showOnMap", self.guiLeft + 5, y + 5));
        self.addButton(new GuiButtonNop(self, SHOW_ON_MAP_ID, self.guiLeft + 130, y, 50, 20,
                new String[] { "gui.yes", "gui.no" }, showOnMap ? 0 : 1));
    }

    @Inject(method = "buttonEvent", at = @At("TAIL"))
    private void wfm$onShowOnMapClicked(GuiButtonNop button, CallbackInfo ci) {
        if (this.display == null || button.id != SHOW_ON_MAP_ID) {
            return;
        }
        ((WfmDisplayBridge) this.display).wfm$setShowOnMap(button.getValue() == 0);
    }
}
