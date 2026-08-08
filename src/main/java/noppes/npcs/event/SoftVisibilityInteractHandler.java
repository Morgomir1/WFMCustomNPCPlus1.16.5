package noppes.npcs.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.NpcSoftVisibility;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Extra gate for soft-hidden / Visible=No NPCs: cancel interact and attack when the
 * NPC is invisible to this player (wand / spectator excluded via
 * {@link NpcSoftVisibility#isInvisibleTo}).
 */
@Mod.EventBusSubscriber(modid = "customnpcs", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SoftVisibilityInteractHandler {
    private SoftVisibilityInteractHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(final PlayerInteractEvent.EntityInteract event) {
        if (event.getWorld().isClientSide()) {
            return;
        }
        if (shouldBlock(event.getTarget(), event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    /** Melee / AttackEntity packet — blocks before {@code EntityNPCInterface#hurt}. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttackEntity(final AttackEntityEvent event) {
        final PlayerEntity player = event.getPlayer();
        if (player.level.isClientSide) {
            return;
        }
        if (shouldBlock(event.getTarget(), player)) {
            event.setCanceled(true);
        }
    }

    private static boolean shouldBlock(final Entity target, final PlayerEntity player) {
        return target instanceof EntityNPCInterface
                && NpcSoftVisibility.isInvisibleTo((EntityNPCInterface) target, player);
    }
}
