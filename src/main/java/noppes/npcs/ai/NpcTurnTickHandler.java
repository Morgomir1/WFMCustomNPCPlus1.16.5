package noppes.npcs.ai;

import net.minecraft.entity.MobEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Страховка: если AI пересобрали без вызова нашего mixin-пути, вернуть контроллеры.
 */
@Mod.EventBusSubscriber(modid = "customnpcs", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NpcTurnTickHandler {
    private NpcTurnTickHandler() {
    }

    @SubscribeEvent
    public static void onLivingUpdate(final LivingEvent.LivingUpdateEvent event) {
        if (!(event.getEntityLiving() instanceof MobEntity)) {
            return;
        }
        final MobEntity mob = (MobEntity) event.getEntityLiving();
        if (mob.level == null || mob.level.isClientSide) {
            return;
        }
        if (!mob.getPersistentData().contains(NpcTurnHelper.DATA_KEY)) {
            return;
        }
        // Раз в 10 тиков — дешево и ловит сброс после updateAI.
        if (mob.tickCount % 10 != 0) {
            return;
        }
        NpcTurnHelper.ensureInstalled(mob);
    }
}
