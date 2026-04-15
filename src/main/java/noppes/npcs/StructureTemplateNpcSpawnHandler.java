package noppes.npcs;

import net.minecraft.entity.Entity;
import net.minecraft.world.gen.feature.template.Template;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.NpcStructureTemplateSpawnFix;
import noppes.npcs.shared.common.util.LogWriter;
import wfm.common.LOTRLog;

/**
 * Исправление ИИ после размещения из шаблона структуры выполняется здесь, а не в
 * {@link net.minecraft.world.gen.feature.template.Template#createEntityIgnoreException}:
 * позже вызываются {@code moveTo}, {@code finalizeSpawn} и снова подмешивается NBT.
 * <p>
 * Размещение сущностей из шаблона всегда проходит через {@link Template#addEntitiesToWorld};
 * по стеку вызова отличаем это от обычной загрузки NPC из чанка (без миксина в ваниль).
 */
@Mod.EventBusSubscriber(modid = CustomNpcs.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StructureTemplateNpcSpawnHandler {
    private static final int STACK_WALK_LIMIT = 48;
    private static final String TEMPLATE_CLASS_NAME = Template.class.getName();

    private StructureTemplateNpcSpawnHandler() {
    }

    private static boolean isJoiningWorldFromStructureTemplatePlacement() {
        final StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        final int n = Math.min(trace.length, STACK_WALK_LIMIT);
        for (int i = 0; i < n; i++) {
            if (TEMPLATE_CLASS_NAME.equals(trace[i].getClassName())) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(final EntityJoinWorldEvent event) {
        //LogWriter.info("ENTITY JOIN WORLD!!!");
        if (event.getWorld().isClientSide()) {
            return;
        }
        if (!isJoiningWorldFromStructureTemplatePlacement()) {
            return;
        }
        final Entity entity = event.getEntity();
        if (!(entity instanceof EntityNPCInterface)) {
            return;
        }
        final ServerWorld level = (ServerWorld) event.getWorld();
        level.getServer().execute(() -> {
            if (!entity.isAlive() || entity.level != level) {
                return;
            }
            NpcStructureTemplateSpawnFix.apply((EntityNPCInterface) entity);
        });
    }
}
