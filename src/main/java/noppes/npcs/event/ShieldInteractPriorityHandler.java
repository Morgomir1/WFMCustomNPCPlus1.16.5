package noppes.npcs.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.controllers.data.Faction;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * При щите в offhand отменяет интеракт с CustomNPC, если NPC заагрен на игрока
 * или фракция враждебна — чтобы поднялся щит (клиентский mobInteract иначе
 * возвращает SUCCESS при isAttacking и блокирует useItem).
 */
@Mod.EventBusSubscriber(modid = "customnpcs", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShieldInteractPriorityHandler {
	private ShieldInteractPriorityHandler() {
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		PlayerEntity player = event.getPlayer();
		ItemStack offhandItem = player.getOffhandItem();
		if (!(offhandItem.getItem() instanceof ShieldItem)) {
			return;
		}

		Entity target = event.getTarget();
		if (!(target instanceof EntityNPCInterface)) {
			return;
		}

		EntityNPCInterface npc = (EntityNPCInterface) target;
		if (shouldPreferShield(npc, player)) {
			event.setCanceled(true);
		}
	}

	private static boolean shouldPreferShield(EntityNPCInterface npc, PlayerEntity player) {
		LivingEntity attackTarget = npc.getTarget();
		if (attackTarget == player) {
			return true;
		}
		Faction faction = npc.getFaction();
		return faction != null && faction.isAggressiveToPlayer(player);
	}
}
