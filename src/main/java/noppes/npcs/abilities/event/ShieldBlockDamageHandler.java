package noppes.npcs.abilities.event;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.abilities.AbilityParamKeys;
import noppes.npcs.abilities.AbilityRunner;
import noppes.npcs.abilities.ActiveAbility;
import noppes.npcs.abilities.impl.ShieldBlockAbility;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Пока активна абилка {@link ShieldBlockAbility}, гасит фронтальный урон по CustomNPC.
 */
@Mod.EventBusSubscriber(modid = "customnpcs", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShieldBlockDamageHandler {
    private ShieldBlockDamageHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttack(final LivingAttackEvent event) {
        if (event.isCanceled()) {
            return;
        }
        final LivingEntity target = event.getEntityLiving();
        if (target == null || target.level.isClientSide || !(target instanceof EntityNPCInterface)) {
            return;
        }

        final ActiveAbility active = AbilityRunner.getActive(target.getUUID());
        if (active == null || !ShieldBlockAbility.ID.equals(active.abilityId)) {
            return;
        }

        final DamageSource source = event.getSource();
        final double blockAngle = active.params.getDouble(AbilityParamKeys.BLOCK_ANGLE, 90.0);
        if (!ShieldBlockAbility.isFrontalHit(target, source, blockAngle)) {
            return;
        }

        event.setCanceled(true);
        ShieldBlockAbility.onBlockedHit(active.context, target);
    }
}
