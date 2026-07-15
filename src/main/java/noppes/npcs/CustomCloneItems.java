package noppes.npcs;

import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ObjectHolder;
import noppes.npcs.items.ItemCloneStructureSpawner;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, modid = "customnpcs")
@ObjectHolder("customnpcs")
public class CustomCloneItems {
    @ObjectHolder("clone_structure_spawner")
    public static Item clone_structure_spawner;

    @SubscribeEvent
    public static void registerItems(final RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new ItemCloneStructureSpawner(
                new Item.Properties().stacksTo(1).tab(CustomTabs.tab))
                .setRegistryName("customnpcs", "clone_structure_spawner"));
    }
}
