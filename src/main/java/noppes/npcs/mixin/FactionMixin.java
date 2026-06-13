package noppes.npcs.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.bridge.WfmFactionBridge;
import noppes.npcs.bridge.WfmPledgeOverride;
import noppes.npcs.controllers.data.Faction;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wfm.common.data.LOTRLevelData;
import wfm.common.fac.FactionSettingsManager;

@Mixin(value = Faction.class)
public abstract class FactionMixin implements WfmFactionBridge {
    @Unique
    private ResourceLocation wfm$factionId;

    @Override
    public ResourceLocation wfm$getFactionId() {
        return wfm$factionId;
    }

    @Override
    public void wfm$setFactionId(ResourceLocation id) {
        this.wfm$factionId = id;
    }

    @Inject(method = "writeNBT", at = @At("TAIL"), remap = false)
    private void wfm$writeNbt(CompoundNBT compound, CallbackInfoReturnable<CompoundNBT> cir) {
        if (this.wfm$factionId != null) {
            compound.putString("WfmFactionId", this.wfm$factionId.toString());
        } else {
            compound.remove("WfmFactionId");
        }
    }
    @Inject(method = "readNBT", at = @At("TAIL"), remap = false)
    private void wfm$readNbt(CompoundNBT compound, CallbackInfo ci) {
        if (compound.contains("WfmFactionId", 8)) { // 8 = string
            String raw = compound.getString("WfmFactionId");
            // в 1.16.5 удобнее использовать tryCreate, чтобы не падать на битых данных
            this.wfm$factionId = ResourceLocation.tryParse(raw);
        } else {
            this.wfm$factionId = null;
        }
    }

    private WfmPledgeOverride wfm$pledgeOverride(PlayerEntity player) {
        ResourceLocation id = ((WfmFactionBridge) (Object) this).wfm$getFactionId();
        if (id == null || player == null || player.level == null) {
            return WfmPledgeOverride.NONE;
        }
        wfm.common.fac.Faction fac = FactionSettingsManager.sidedInstance(player.level)
                .getCurrentLoadedFactions()
                .getFactionByName(id);
        if (fac == null) {
            return WfmPledgeOverride.NONE;
        }
        wfm.common.fac.Faction pledge = LOTRLevelData.sidedInstance(player)
                .getData(player)
                .getAlignmentData()
                .getPledgeFaction();
        if (pledge == null) {
            return WfmPledgeOverride.NONE;
        }
        if (fac.isBadRelation(pledge)) {
            return WfmPledgeOverride.HOSTILE;
        }
        if (fac.isAlly(pledge)) {
            return WfmPledgeOverride.ALLY;
        }
        if (fac.isNeutral(pledge)) {
            return WfmPledgeOverride.NEUTRAL_RELATION;
        }
        return WfmPledgeOverride.NONE;
    }

    @Inject(
            method = "isFriendlyToPlayer(Lnet/minecraft/entity/player/PlayerEntity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void wfm$isFriendlyToPlayer(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        switch (wfm$pledgeOverride(player)) {
            case HOSTILE:
                cir.setReturnValue(false);
                return;
            case ALLY:
                cir.setReturnValue(true);
                return;
            case NEUTRAL_RELATION:
                cir.setReturnValue(false);
                return;
            default:
        }
    }

    @Inject(
            method = "isAggressiveToPlayer(Lnet/minecraft/entity/player/PlayerEntity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void wfm$isAggressiveToPlayer(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        switch (wfm$pledgeOverride(player)) {
            case HOSTILE:
                cir.setReturnValue(!player.abilities.instabuild);
                return;
            case ALLY:
            case NEUTRAL_RELATION:
                cir.setReturnValue(false);
                return;
            default:
        }
    }

    @Inject(
            method = "isNeutralToPlayer(Lnet/minecraft/entity/player/PlayerEntity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void wfm$isNeutralToPlayer(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        switch (wfm$pledgeOverride(player)) {
            case HOSTILE:
            case ALLY:
                cir.setReturnValue(false);
                return;
            case NEUTRAL_RELATION:
                cir.setReturnValue(true);
                return;
            default:
        }
    }

    @Inject(
            method = "isAggressiveToNpc(Lnoppes/npcs/entity/EntityNPCInterface;)Z",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void wfm$isAggressiveToNpc(EntityNPCInterface entity, CallbackInfoReturnable<Boolean> cir) {
        boolean vanilla = cir.getReturnValue();
        Boolean extra = wfm$wfmNpcAggressiveExtra(entity);
        if (extra != null) {
            cir.setReturnValue(vanilla || extra);
        }
    }

    /**
     * true — добавить агрессию по WFM; false — не добавлять; null — не трогать (оставить только vanilla).
     */
    private Boolean wfm$wfmNpcAggressiveExtra(EntityNPCInterface entity) {
        if (entity == null || entity.level == null || entity.faction == null) {
            return null;
        }
        ResourceLocation selfRl = ((WfmFactionBridge) (Object) this).wfm$getFactionId();
        ResourceLocation npcRl = ((WfmFactionBridge) (Object) entity.faction).wfm$getFactionId();
        if (selfRl == null || npcRl == null) {
            return null;
        }
        // опционально: явно не считать «агрессией по WFM» случай одной и той же фракции
        if (selfRl.equals(npcRl)) {
            return false;
        }
        wfm.common.fac.Faction selfWfm = FactionSettingsManager.sidedInstance(entity.level)
                .getCurrentLoadedFactions()
                .getFactionByName(selfRl);
        wfm.common.fac.Faction npcWfm = FactionSettingsManager.sidedInstance(entity.level)
                .getCurrentLoadedFactions()
                .getFactionByName(npcRl);
        if (selfWfm == null || npcWfm == null) {
            return null;
        }
        // базовый вариант «враждебны ли фракции друг другу»
        if (selfWfm.isBadRelation(npcWfm)) {
            return true;
        }
        // при необходимости добавь ветки:
        // if (selfWfm.isAlly(npcWfm)) return false;
        // if (selfWfm.isNeutral(npcWfm)) return false;
        return null;
    }

}