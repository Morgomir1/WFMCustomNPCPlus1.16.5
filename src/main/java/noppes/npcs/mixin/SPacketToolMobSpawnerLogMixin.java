package noppes.npcs.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.World;
import noppes.npcs.entity.NpcRespawnLog;
import noppes.npcs.packets.IPacketServer;
import noppes.npcs.packets.server.SPacketToolMobSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code spawnClone} is where clones enter the world: the cloner tool, {@code world.spawnClone} of scripts and
 * the structure spawner (through the clone controller), NPC spawner jobs. A boss removed on death comes back this
 * way, so its spawn goes to {@link NpcRespawnLog}; the tool names the player.
 */
@Mixin(value = SPacketToolMobSpawner.class, remap = false)
public abstract class SPacketToolMobSpawnerLogMixin {

    @Inject(method = "handle()V", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/packets/server/SPacketToolMobSpawner;spawnClone(Lnet/minecraft/nbt/CompoundNBT;DDDLnet/minecraft/world/World;)Lnet/minecraft/entity/Entity;"))
    private void cnpc$nameToolSpawn(final CallbackInfo ci) {
        final String player = ((IPacketServer) (Object) this).player.getName().getString();
        NpcRespawnLog.expectSpawn("cloner tool, player " + player, true);
    }

    @Inject(method = "spawnClone", at = @At("RETURN"))
    private static void cnpc$logCloneSpawn(final CompoundNBT compound, final double x, final double y, final double z,
            final World world, final CallbackInfoReturnable<Entity> cir) {
        NpcRespawnLog.onSpawn(cir.getReturnValue());
    }
}
