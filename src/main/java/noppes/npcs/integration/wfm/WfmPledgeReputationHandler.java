package noppes.npcs.integration.wfm;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.controllers.FactionController;
import noppes.npcs.controllers.data.Faction;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.PlayerFactionData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import wfm.common.event.PledgeAllegianceChangedEvent;
import wfm.common.fac.Allegiance;

/**
 * Syncs CustomNPC alliance faction points when the player changes WFM pledge allegiance.
 */
@Mod.EventBusSubscriber(modid = "customnpcs", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WfmPledgeReputationHandler {
	private static final Logger LOGGER = LogManager.getLogger();

	private WfmPledgeReputationHandler() {
	}

	@SubscribeEvent
	public static void onPledgeAllegianceChanged(final PledgeAllegianceChangedEvent event) {
		final ServerPlayerEntity player = event.getPlayer();
		if (player == null || player.level.isClientSide) {
			return;
		}

		final Allegiance allegiance = event.getAllegiance();
		final int points = event.getAllianceReputationPoints();
		final int hostilePoints = -points;

		int orderPoints = 0;
		int destructionPoints = 0;
		int neutralPoints = 0;
		if (allegiance == Allegiance.ORDER) {
			orderPoints = points;
			destructionPoints = hostilePoints;
		} else if (allegiance == Allegiance.DESTRUCTION) {
			destructionPoints = points;
			orderPoints = hostilePoints;
		} else if (allegiance == Allegiance.NEUTRAL) {
			neutralPoints = points;
		}

		setFactionPoints(player, event.getOrderFactionName(), orderPoints);
		setFactionPoints(player, event.getDestructionFactionName(), destructionPoints);
		setFactionPoints(player, event.getNeutralFactionName(), neutralPoints);

		PlayerData.get(player).save(true);
	}

	private static void setFactionPoints(final PlayerEntity player, final String factionName, final int points) {
		if (factionName == null || factionName.isEmpty()) {
			return;
		}
		final Faction faction = FactionController.instance.getFactionFromName(factionName);
		if (faction == null) {
			LOGGER.warn("WFM pledge sync: CustomNPC faction '{}' not found", factionName);
			return;
		}
		final PlayerFactionData factionData = PlayerData.get(player).factionData;
		factionData.factionData.put(faction.id, points);
	}
}
