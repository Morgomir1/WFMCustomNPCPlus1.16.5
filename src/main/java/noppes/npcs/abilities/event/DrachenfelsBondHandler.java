package noppes.npcs.abilities.event;

import net.minecraft.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.abilities.DrachenfelsEncounterHelper;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Immortal Bond: lethal hits on tagged Drachenfels NPCs are absorbed into downed state
 * when the partner can still revive them.
 */
@Mod.EventBusSubscriber(modid = "customnpcs", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DrachenfelsBondHandler {
    private DrachenfelsBondHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(final LivingHurtEvent event) {
        if (event.isCanceled()) {
            return;
        }
        final LivingEntity target = event.getEntityLiving();
        if (target == null || target.level == null || target.level.isClientSide) {
            return;
        }
        if (!(target instanceof EntityNPCInterface)) {
            return;
        }
        final IEntity wrapped;
        try {
            wrapped = NpcAPI.Instance().getIEntity(target);
        } catch (final Exception e) {
            return;
        }
        if (!(wrapped instanceof ICustomNpc)) {
            return;
        }
        final ICustomNpc npc = (ICustomNpc) wrapped;
        if (!npc.hasTag(DrachenfelsEncounterHelper.PAIR_TAG)) {
            return;
        }
        if (DrachenfelsEncounterHelper.absorbLethal(npc, event.getAmount())) {
            event.setCanceled(true);
            event.setAmount(0.0F);
        }
    }
}
