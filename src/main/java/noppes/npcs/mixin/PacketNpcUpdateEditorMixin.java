package noppes.npcs.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.packets.client.PacketNpcUpdate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code PacketNpcUpdate} writes spawn NBT onto the client entity, including Display.
 * The NPC editor edits that same {@code npc.display} object; applying the packet
 * while the editor is open rolled visibility / Availability back to the last saved server
 * snapshot, so closing the GUI saved the old values.
 */
@Mixin(PacketNpcUpdate.class)
public abstract class PacketNpcUpdateEditorMixin {

    @Shadow
    @Final
    private int id;

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpc$skipOverwriteWhileEditing(final CallbackInfo ci) {
        final Screen screen = Minecraft.getInstance().screen;
        if (!(screen instanceof GuiNPCInterface)) {
            return;
        }
        final GuiNPCInterface gui = (GuiNPCInterface) screen;
        if (gui.npc != null && gui.npc.getId() == this.id) {
            ci.cancel();
        }
    }
}
