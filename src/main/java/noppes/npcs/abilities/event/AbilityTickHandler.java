package noppes.npcs.abilities.event;

import noppes.npcs.abilities.AbilityRunner;
import noppes.npcs.telegraph.TelegraphServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "customnpcs", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AbilityTickHandler {
    private AbilityTickHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        AbilityRunner.tickAll();
        TelegraphServer.tickAll();
    }
}
