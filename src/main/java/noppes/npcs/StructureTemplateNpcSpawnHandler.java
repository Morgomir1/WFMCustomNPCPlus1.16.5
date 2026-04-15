package noppes.npcs;

import net.minecraft.entity.Entity;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.NpcStructureTemplateSpawnFix;

/**
 * Исправление ИИ после размещения из шаблона структуры выполняется здесь, а не в
 * {@link net.minecraft.world.gen.feature.template.Template#createEntityIgnoreException}:
 * позже вызываются {@code moveTo}, {@code finalizeSpawn} и снова подмешивается NBT.
 */
@Mod.EventBusSubscriber(modid = CustomNpcs.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StructureTemplateNpcSpawnHandler {
    public static final String PENDING_STRUCTURE_AI_FIX = "WFM_CNPC_STRUCT_AI";

    private StructureTemplateNpcSpawnHandler() {
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(final EntityJoinWorldEvent event) {
        if (event.getWorld().isClientSide()) {
            return;
        }
        final Entity entity = event.getEntity();
        if (!(entity instanceof EntityNPCInterface)) {
            return;
        }
        if (!entity.getPersistentData().getBoolean(PENDING_STRUCTURE_AI_FIX)) {
            return;
        }
        entity.getPersistentData().remove(PENDING_STRUCTURE_AI_FIX);
        final ServerWorld level = (ServerWorld) event.getWorld();
        level.getServer().execute(() -> {
            if (!entity.isAlive() || entity.level != level) {
                return;
            }
            NpcStructureTemplateSpawnFix.apply((EntityNPCInterface) entity);
        });
    }
}
