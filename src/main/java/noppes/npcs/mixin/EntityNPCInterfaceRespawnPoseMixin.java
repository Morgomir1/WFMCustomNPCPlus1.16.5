package noppes.npcs.mixin;

import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.play.server.SEntityMetadataPacket;
import net.minecraft.world.server.ServerWorld;
import noppes.npcs.api.constants.AnimationType;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CNPC reuses the same entity on respawn. {@code deathTime} is not synced, and client
 * {@code aiStep} (which copies {@code Animation} into {@code currentAnimation}) is skipped
 * while HP is 0 or while {@link EntityNPCInterface#isSleeping()} is true — so after
 * revive the NPC often keeps sleep/death pose and the corpse hitbox.
 * <p>
 * The client only clears that pose once it learns the NPC is no longer killed. Respawn
 * {@code reset()} moves the NPC back to its start; when a player starts tracking it in that
 * same tick, vanilla {@code TrackedEntity#sendPairingData} sends the full data with
 * {@code SEntityMetadataPacket(..., true)}, which clears the dirty flags before
 * {@code sendChanges}, so players who saw the death never got {@code IsDead=false}: the NPC
 * fought on while rendered as a corpse. The revived state is therefore sent to every tracker.
 */
@Mixin(EntityNPCInterface.class)
public abstract class EntityNPCInterfaceRespawnPoseMixin {

    @Shadow(remap = false)
    protected static DataParameter<Integer> Animation;

    @Inject(method = "tick", at = @At("TAIL"))
    private void cnpc$clearStaleDeathPose(final CallbackInfo ci) {
        final EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.level == null || !npc.level.isClientSide) {
            return;
        }

        boolean changed = false;
        final int synced = npc.getEntityData().get(Animation);
        if (npc.currentAnimation != synced) {
            npc.currentAnimation = synced;
            npc.animationStart = npc.tickCount;
            changed = true;
        }

        if (!npc.isKilled() && npc.currentAnimation != AnimationType.DEATH && npc.deathTime > 0) {
            npc.deathTime = 0;
            changed = true;
        }

        if (changed) {
            npc.refreshDimensions();
        }
    }

    @Inject(method = "reset", at = @At("TAIL"), remap = false)
    private void cnpc$broadcastRevivedState(final CallbackInfo ci) {
        final EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.level instanceof ServerWorld) {
            ((ServerWorld) npc.level).getChunkSource().broadcast(npc,
                    new SEntityMetadataPacket(npc.getId(), npc.getEntityData(), true));
        }
    }
}
