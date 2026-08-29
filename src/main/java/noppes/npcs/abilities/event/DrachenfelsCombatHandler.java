package noppes.npcs.abilities.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
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
 * Solo Constant Drachenfels: absorb shield, phase HP caps, vessel immunity,
 * vessel-only player damage, monk death clears absorb.
 */
@Mod.EventBusSubscriber(modid = "customnpcs", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DrachenfelsCombatHandler {
    private DrachenfelsCombatHandler() {
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
        final IEntity wrapped = wrap(target);
        if (!(wrapped instanceof ICustomNpc)) {
            return;
        }
        final ICustomNpc npc = (ICustomNpc) wrapped;

        if (npc.hasTag(DrachenfelsEncounterHelper.TAG_VESSEL)
                || npc.hasTag(DrachenfelsEncounterHelper.TAG_SHARD)) {
            if (!isPlayerDamage(event.getSource().getEntity())) {
                event.setCanceled(true);
                event.setAmount(0.0F);
            }
            return;
        }

        if (!DrachenfelsEncounterHelper.isBoss(npc)) {
            if (npc.hasTag(DrachenfelsEncounterHelper.TAG_MONK)
                    && event.getAmount() >= target.getHealth()) {
                // death handled in LivingDeathEvent
            }
            return;
        }

        if (DrachenfelsEncounterHelper.isInvulnerable(npc)) {
            event.setCanceled(true);
            event.setAmount(0.0F);
            return;
        }

        if (DrachenfelsEncounterHelper.hasLivingVessels(npc)) {
            event.setCanceled(true);
            event.setAmount(0.0F);
            return;
        }

        final float absorb = DrachenfelsEncounterHelper.getAbsorb(npc);
        if (absorb > 0.01F) {
            final float dmg = event.getAmount();
            if (dmg <= absorb) {
                DrachenfelsEncounterHelper.setAbsorb(npc, absorb - dmg);
                event.setCanceled(true);
                event.setAmount(0.0F);
            } else {
                DrachenfelsEncounterHelper.setAbsorb(npc, 0.0F);
                event.setAmount(dmg - absorb);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHeal(final LivingHealEvent event) {
        if (event.isCanceled()) {
            return;
        }
        final LivingEntity living = event.getEntityLiving();
        if (living == null || living.level == null || living.level.isClientSide) {
            return;
        }
        if (!(living instanceof EntityNPCInterface)) {
            return;
        }
        final IEntity wrapped = wrap(living);
        if (!(wrapped instanceof ICustomNpc)) {
            return;
        }
        final ICustomNpc npc = (ICustomNpc) wrapped;
        if (!DrachenfelsEncounterHelper.isBoss(npc)) {
            return;
        }
        final float cap = DrachenfelsEncounterHelper.phaseHpCap(npc);
        if (cap <= 0.0F) {
            return;
        }
        final float allowed = Math.max(0.0F, cap - living.getHealth());
        if (event.getAmount() > allowed) {
            if (allowed <= 0.01F) {
                event.setCanceled(true);
                event.setAmount(0.0F);
            } else {
                event.setAmount(allowed);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onLivingDeath(final LivingDeathEvent event) {
        final LivingEntity living = event.getEntityLiving();
        if (living == null || living.level == null || living.level.isClientSide) {
            return;
        }
        if (!(living instanceof EntityNPCInterface)) {
            return;
        }
        final IEntity wrapped = wrap(living);
        if (!(wrapped instanceof ICustomNpc)) {
            return;
        }
        final ICustomNpc npc = (ICustomNpc) wrapped;
        if (npc.hasTag(DrachenfelsEncounterHelper.TAG_MONK)) {
            DrachenfelsEncounterHelper.onMonkDeath(npc);
            return;
        }
        if (npc.hasTag(DrachenfelsEncounterHelper.TAG_VESSEL)) {
            DrachenfelsEncounterHelper.onVesselDeath(npc);
            return;
        }
        if (npc.hasTag(DrachenfelsEncounterHelper.TAG_SHARD)) {
            DrachenfelsEncounterHelper.onShardDeath(npc);
            return;
        }
        if (DrachenfelsEncounterHelper.isBoss(npc)) {
            DrachenfelsEncounterHelper.cleanup(npc);
        }
    }

    private static boolean isPlayerDamage(final Entity source) {
        if (source instanceof PlayerEntity) {
            return true;
        }
        if (source == null) {
            return false;
        }
        try {
            final Entity root = source.getRootVehicle();
            return root instanceof PlayerEntity;
        } catch (final Exception e) {
            return String.valueOf(source.getClass().getName()).contains("Player");
        }
    }

    private static IEntity wrap(final LivingEntity living) {
        try {
            return NpcAPI.Instance().getIEntity(living);
        } catch (final Exception e) {
            return null;
        }
    }
}
