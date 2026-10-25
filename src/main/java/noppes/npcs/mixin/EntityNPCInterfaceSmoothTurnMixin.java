package noppes.npcs.mixin;

import net.minecraft.entity.MobEntity;
import noppes.npcs.ai.NpcTurnHelper;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code updateTasks()} снова ставит ванильный {@code MovementController} —
 * сразу после этого возвращаем smooth turn, если он включён через {@link NpcTurnHelper}.
 */
@Mixin(EntityNPCInterface.class)
public abstract class EntityNPCInterfaceSmoothTurnMixin {

    @Inject(method = "updateTasks", at = @At("RETURN"), remap = false)
    private void cnpc$reapplySmoothTurn(final CallbackInfo ci) {
        NpcTurnHelper.ensureInstalled((MobEntity) (Object) this);
    }
}
