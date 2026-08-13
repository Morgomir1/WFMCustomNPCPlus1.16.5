package noppes.npcs.mixin;

import noppes.npcs.constants.EnumAvailabilityDialog;
import noppes.npcs.constants.EnumAvailabilityFactionType;
import noppes.npcs.constants.EnumAvailabilityQuest;
import noppes.npcs.constants.EnumDayTime;
import noppes.npcs.controllers.data.Availability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * GUI writes {@code dialogAvailable} etc. directly and never refreshes the cached
 * {@code hasOptions} flag. Classic Visible=No then treated the NPC as "no conditions"
 * (always hidden / always shown) until the next NBT load.
 */
@Mixin(Availability.class)
public abstract class AvailabilityHasOptionsMixin {

    @Inject(method = "hasOptions", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpc$liveHasOptions(final CallbackInfoReturnable<Boolean> cir) {
        final Availability self = (Availability) (Object) this;
        cir.setReturnValue(
                self.dialogAvailable != EnumAvailabilityDialog.Always
                || self.dialog2Available != EnumAvailabilityDialog.Always
                || self.dialog3Available != EnumAvailabilityDialog.Always
                || self.dialog4Available != EnumAvailabilityDialog.Always
                || self.questAvailable != EnumAvailabilityQuest.Always
                || self.quest2Available != EnumAvailabilityQuest.Always
                || self.quest3Available != EnumAvailabilityQuest.Always
                || self.quest4Available != EnumAvailabilityQuest.Always
                || self.daytime != EnumDayTime.Always
                || self.minPlayerLevel > 0
                || self.factionAvailable != EnumAvailabilityFactionType.Always
                || self.faction2Available != EnumAvailabilityFactionType.Always
                || !self.scoreboardObjective.isEmpty()
                || !self.scoreboard2Objective.isEmpty());
    }
}
