package noppes.npcs.event;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.LogicalSide;
import noppes.npcs.controllers.VisibilityController;

/**
 * Jar {@code ServerTickHandler} only refreshes visibility at rare daytime edges / wand /
 * PlayerData sync. Soft-hide needs frequent re-check so faction / daytime / dialog conditions
 * flip while the player stays in range (entity is no longer removed from the client).
 */
@Mod.EventBusSubscriber(modid = "customnpcs", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SoftVisibilityTickHandler {
    private static final int INTERVAL_TICKS = 20;

    private SoftVisibilityTickHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(final TickEvent.PlayerTickEvent event) {
        if (event.side != LogicalSide.SERVER || event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayerEntity)) {
            return;
        }
        if (event.player.tickCount % INTERVAL_TICKS != 0) {
            return;
        }
        VisibilityController.instance.onUpdate((ServerPlayerEntity) event.player);
    }
}
