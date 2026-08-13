package noppes.npcs.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.nbt.CompoundNBT;
import noppes.npcs.client.ClientNpcSpawnData;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.packets.client.PacketNpcUpdate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Jar {@code PacketNpcUpdate.handle} drops NBT when {@code level.getEntity(id)} is null
 * (common on hide→show). Queue until the NPC exists. Also skip applying while the
 * editor GUI is open so Display edits are not overwritten.
 */
@Mixin(PacketNpcUpdate.class)
public abstract class PacketNpcUpdateEditorMixin {

    @Shadow(remap = false)
    @Final
    private int id;

    @Shadow(remap = false)
    @Final
    private CompoundNBT data;

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpc$applyOrQueueSpawnData(final CallbackInfo ci) {
        final Minecraft mc = Minecraft.getInstance();
        final Screen screen = mc.screen;
        if (screen instanceof GuiNPCInterface) {
            final GuiNPCInterface gui = (GuiNPCInterface) screen;
            if (gui.npc != null && gui.npc.getId() == this.id) {
                ci.cancel();
                return;
            }
        }
        ClientNpcSpawnData.applyOrQueue(this.id, this.data);
        ci.cancel();
    }
}
