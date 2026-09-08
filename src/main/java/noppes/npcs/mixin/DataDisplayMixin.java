package noppes.npcs.mixin;

import net.minecraft.nbt.CompoundNBT;
import noppes.npcs.bridge.WfmDisplayBridge;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DataDisplay.class, remap = false)
public abstract class DataDisplayMixin implements WfmDisplayBridge {
    private static final String NBT_SHOW_ON_MAP = "WfmShowOnMap";

    @Shadow
    EntityNPCInterface npc;

    @Unique
    private boolean wfm$showOnMap;

    @Override
    public boolean wfm$isShowOnMap() {
        return this.wfm$showOnMap;
    }

    @Override
    public void wfm$setShowOnMap(boolean value) {
        if (this.wfm$showOnMap == value) {
            return;
        }
        this.wfm$showOnMap = value;
        if (this.npc != null) {
            this.npc.updateClient = true;
        }
    }

    @Inject(method = "save", at = @At("TAIL"))
    private void wfm$writeShowOnMap(CompoundNBT nbttagcompound, CallbackInfoReturnable<CompoundNBT> cir) {
        nbttagcompound.putBoolean(NBT_SHOW_ON_MAP, this.wfm$showOnMap);
    }

    @Inject(method = "readToNBT", at = @At("TAIL"))
    private void wfm$readShowOnMap(CompoundNBT nbttagcompound, CallbackInfo ci) {
        this.wfm$showOnMap = nbttagcompound.getBoolean(NBT_SHOW_ON_MAP);
    }
}
