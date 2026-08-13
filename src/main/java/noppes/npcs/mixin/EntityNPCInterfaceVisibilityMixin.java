package noppes.npcs.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import noppes.npcs.NpcSoftVisibility;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.client.PacketNpcUpdate;
import noppes.npcs.packets.client.PacketNpcVisibleFalse;
import noppes.npcs.packets.client.PacketNpcVisibleTrue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Soft per-player visibility: expand {@code isInvisibleTo} for Availability, and block
 * player interact / hurt via {@link NpcSoftVisibility#isInvisibleTo} (same as render/pick).
 * NPC targeting of soft-hidden players still uses {@link NpcSoftVisibility#isHiddenFrom}
 * (EnableInvisibleNpcs Availability rules).
 * <p>
 * Also replaces jar {@code setVisible}/{@code setInvisible}: no SpawnEntity / removeEntity,
 * only soft-hide flag packets (duplicates came from re-spawn).
 */
@Mixin(EntityNPCInterface.class)
public abstract class EntityNPCInterfaceVisibilityMixin {

    @Inject(method = "setInvisible", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpc$setInvisibleSoft(final ServerPlayerEntity player, final CallbackInfo ci) {
        final EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        self.tracking.remove(player.getId());
        Packets.send(player, new PacketNpcVisibleFalse(self.getId()));
        ci.cancel();
    }

    @Inject(method = "setVisible", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpc$setVisibleSoft(final ServerPlayerEntity player, final CallbackInfo ci) {
        final EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        final boolean newlyShown = self.tracking.add(player.getId());
        if (!newlyShown) {
            // Already shown: do NOT spam PacketNpcUpdate. VisibilityController runs every
            // second for wand holders and was overwriting GuiNpcDisplay edits, so save()
            // sent stale NpcVisible / Availability back to the server.
            ci.cancel();
            return;
        }
        Packets.send(player, new PacketNpcVisibleTrue(self.getId()));
        Packets.send(player, new PacketNpcUpdate(self.getId(), self.writeSpawnData()));
        ci.cancel();
    }

    @Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
    private void cnpc$isInvisibleTo(final PlayerEntity player, final CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(NpcSoftVisibility.isInvisibleTo((EntityNPCInterface) (Object) this, player));
    }

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void cnpc$mobInteractVisibility(final PlayerEntity player, final Hand hand,
            final CallbackInfoReturnable<ActionResultType> cir) {
        final EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        if (self.level.isClientSide) {
            return;
        }
        // Same predicate as render/pick — classic Visible=No and Availability soft-hide.
        if (NpcSoftVisibility.isInvisibleTo(self, player)) {
            cir.setReturnValue(ActionResultType.FAIL);
        }
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void cnpc$hurtVisibility(final DamageSource damagesource, final float amount,
            final CallbackInfoReturnable<Boolean> cir) {
        final EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        if (self.level.isClientSide) {
            return;
        }
        final Entity entity = NoppesUtilServer.GetDamageSourcee(damagesource);
        // isInvisibleTo (not isHiddenFrom): must block hits when NPC is invisible to the
        // attacker even without EnableInvisibleNpcs (classic Display Visible=No).
        if (entity instanceof PlayerEntity
                && NpcSoftVisibility.isInvisibleTo(self, (PlayerEntity) entity)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void cnpc$setTargetVisibility(final LivingEntity entity, final CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity)) {
            return;
        }
        final EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        if (NpcSoftVisibility.isHiddenFrom(self, (PlayerEntity) entity)) {
            ci.cancel();
        }
    }

    /** onAttack bypasses {@code setTarget} via {@code super.setTarget}. CNPC-only method. */
    @Inject(method = "onAttack", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpc$onAttackVisibility(final LivingEntity entity, final CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity)) {
            return;
        }
        final EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        if (NpcSoftVisibility.isHiddenFrom(self, (PlayerEntity) entity)) {
            ci.cancel();
        }
    }

    /** Drop target if the player became soft-hidden mid-fight (Availability change). */
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void cnpc$clearHiddenTarget(final CallbackInfo ci) {
        final EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        if (self.level.isClientSide) {
            return;
        }
        final LivingEntity target = self.getTarget();
        if (target instanceof PlayerEntity
                && NpcSoftVisibility.isHiddenFrom(self, (PlayerEntity) target)) {
            self.setTarget(null);
        }
    }
}
