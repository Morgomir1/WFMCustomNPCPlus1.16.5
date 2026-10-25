package noppes.npcs.mixin;

import net.minecraft.nbt.CompoundNBT;
import noppes.npcs.entity.NpcRespawnLogged;
import noppes.npcs.entity.data.DataStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the "Log to server" switch with the NPC stats, so it travels with the menu, the NPC save and its clones.
 */
@Mixin(value = DataStats.class, remap = false)
public abstract class DataStatsRespawnLogMixin implements NpcRespawnLogged {
    @Unique
    private boolean cnpc$respawnLogged;

    @Override
    public boolean cnpc$isRespawnLogged() {
        return this.cnpc$respawnLogged;
    }

    @Override
    public void cnpc$setRespawnLogged(final boolean logged) {
        this.cnpc$respawnLogged = logged;
    }

    @Inject(method = "readToNBT", at = @At("TAIL"))
    private void cnpc$loadRespawnLog(final CompoundNBT compound, final CallbackInfo ci) {
        this.cnpc$respawnLogged = compound.getBoolean("WfmRespawnLog");
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void cnpc$saveRespawnLog(final CompoundNBT compound, final CallbackInfoReturnable<CompoundNBT> cir) {
        compound.putBoolean("WfmRespawnLog", this.cnpc$respawnLogged);
    }
}
