package noppes.npcs.mixin;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import noppes.npcs.command.CmdClone;
import noppes.npcs.entity.NpcRespawnLog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code /noppes clone spawn} and {@code grid} create the entity themselves, past {@code spawnClone} of the tool,
 * so the command's clones are logged here ({@link NpcRespawnLog}).
 */
@Mixin(value = CmdClone.class, remap = false)
public abstract class CmdCloneSpawnLogMixin {

    @Inject(method = "spawnClone", at = @At("HEAD"))
    private static void cnpc$nameCommandSpawn(final CommandContext<CommandSource> context, final BlockPos pos,
            final String newName, final CallbackInfo ci) {
        NpcRespawnLog.expectSpawn("command /noppes clone, by " + context.getSource().getTextName(), true);
    }

    @ModifyArg(method = "spawnClone", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;addFreshEntity(Lnet/minecraft/entity/Entity;)Z", remap = true))
    private static Entity cnpc$logCommandSpawn(final Entity entity) {
        NpcRespawnLog.onSpawn(entity);
        return entity;
    }
}
