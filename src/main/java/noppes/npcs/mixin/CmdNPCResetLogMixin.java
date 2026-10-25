package noppes.npcs.mixin;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandSource;
import noppes.npcs.command.CmdNPC;
import noppes.npcs.entity.NpcRespawnLog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code /noppes npc <name> reset} resets every loaded NPC with that name. The handler is the lambda
 * {@code lambda$register$8} of the base JAR: the only one in {@link CmdNPC} that calls {@code reset()}.
 */
@Mixin(value = CmdNPC.class, remap = false)
public abstract class CmdNPCResetLogMixin {

    @Inject(method = "lambda$register$8", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityNPCInterface;reset()V"))
    private static void cnpc$logCommandReset(final CommandContext<CommandSource> context,
            final CallbackInfoReturnable<Integer> cir) {
        NpcRespawnLog.expectReset("command /noppes npc reset, by " + context.getSource().getTextName(), true);
    }
}
