package noppes.npcs.abilities.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.abilities.AbilityCombatHelper;
import noppes.npcs.abilities.impl.VampireCrimsonBatsAbility;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.data.IData;
import noppes.npcs.script.ScriptDataUtil;

/**
 * Each melee hit from a crimson bat minion heals its owner vampire.
 */
@Mod.EventBusSubscriber(modid = "customnpcs", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VampireCrimsonBatHealHandler {
    private static final double MELEE_HIT_RADIUS = 4.0;
    private static final double DEFAULT_HEAL = 15.0;

    private VampireCrimsonBatHealHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(final LivingHurtEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0F) {
            return;
        }
        final LivingEntity target = event.getEntityLiving();
        if (target == null || target.level == null || target.level.isClientSide) {
            return;
        }

        final DamageSource source = event.getSource();
        if (source == null || source.isProjectile()) {
            return;
        }
        final Entity attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity)) {
            return;
        }
        if (attacker.distanceTo(target) > MELEE_HIT_RADIUS) {
            return;
        }

        final IEntity wrapped = NpcAPI.Instance().getIEntity(attacker);
        if (wrapped == null || !wrapped.hasTag(VampireCrimsonBatsAbility.MINION_TAG)) {
            return;
        }

        final IData data = wrapped.getStoreddata();
        if (data == null || !data.has(VampireCrimsonBatsAbility.OWNER_KEY)) {
            return;
        }
        final String ownerId = String.valueOf(data.get(VampireCrimsonBatsAbility.OWNER_KEY));
        if (ownerId.isEmpty()) {
            return;
        }

        IEntity ownerEnt;
        try {
            ownerEnt = wrapped.getWorld().getEntity(ownerId);
        } catch (final Exception e) {
            ownerEnt = null;
        }
        if (ownerEnt == null) {
            ownerEnt = findOwnerNearby(wrapped, ownerId);
        }
        if (!(ownerEnt instanceof ICustomNpc) || !ownerEnt.isAlive()) {
            return;
        }

        double heal = ScriptDataUtil.getFloat(data, VampireCrimsonBatsAbility.HEAL_KEY);
        if (heal <= 0.0) {
            heal = DEFAULT_HEAL;
        }
        try {
            final Object mc = ownerEnt.getMCEntity();
            if (mc instanceof LivingEntity) {
                AbilityCombatHelper.healLiving((LivingEntity) mc, heal);
            }
        } catch (final Exception ignored) {
        }
    }

    private static IEntity findOwnerNearby(final IEntity bat, final String ownerId) {
        try {
            final IEntity[] list = bat.getWorld().getNearbyEntities(
                    NpcAPI.Instance().getIPos(bat.getX(), bat.getY(), bat.getZ()),
                    48,
                    2);
            for (final IEntity ent : list) {
                if (ent != null && ownerId.equals(String.valueOf(ent.getUUID()))) {
                    return ent;
                }
            }
        } catch (final Exception ignored) {
        }
        return null;
    }
}
