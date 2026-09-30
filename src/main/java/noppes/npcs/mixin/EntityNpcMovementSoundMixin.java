package noppes.npcs.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.util.SoundEvent;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.NpcMovementSounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityNpcMovementSoundMixin {

	@Inject(method = "playSound", at = @At("HEAD"), cancellable = true)
	private void cnpc$throttleMovementSounds(SoundEvent sound, float volume, float pitch, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (!(self instanceof EntityNPCInterface)) {
			return;
		}
		if (!NpcMovementSounds.allow(self, sound)) {
			ci.cancel();
		}
	}
}
