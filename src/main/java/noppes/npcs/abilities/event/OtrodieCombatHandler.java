package noppes.npcs.abilities.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.abilities.AbilityParamKeys;
import noppes.npcs.abilities.AbilityRunner;
import noppes.npcs.abilities.ActiveAbility;
import noppes.npcs.abilities.impl.ShieldBlockAbility;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.script.ScriptDataUtil;

/**
 * Босс «Отродье»: фронтальный DR 90%, meter урона в спину во время hell vomit,
 * полная неуязвимость + счётчик melee во время devour eat.
 */
@Mod.EventBusSubscriber(modid = "customnpcs", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OtrodieCombatHandler {
    public static final String OTRODIE_BOSS_FLAG = "otrodie_boss";
    public static final String HELL_VOMIT_ID = "otrodie_hell_vomit";
    public static final String DEVOUR_DASH_ID = "otrodie_devour_dash";
    /** Must match {@code OtrodieDevourDashAbility} eat phase. */
    public static final int DEVOUR_PHASE_EAT = 3;

    private static final double FRONT_BLOCK_ANGLE = 180.0;
    private static final float FRONT_DAMAGE_FACTOR = 0.1F;
    private static final double MELEE_HIT_RADIUS = 3.5;
    private static final float DEFAULT_BREAK_DAMAGE = 100.0F;

    private OtrodieCombatHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttack(final LivingAttackEvent event) {
        if (event.isCanceled()) {
            return;
        }
        final LivingEntity target = event.getEntityLiving();
        if (!isOtrodieBoss(target)) {
            return;
        }

        final ActiveAbility active = AbilityRunner.getActive(target.getUUID());
        if (active == null || !DEVOUR_DASH_ID.equals(active.abilityId) || active.phase != DEVOUR_PHASE_EAT) {
            return;
        }

        if (isMeleeHit(target, event.getSource())) {
            active.meter += 1.0F;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(final LivingHurtEvent event) {
        if (event.isCanceled()) {
            return;
        }
        final LivingEntity target = event.getEntityLiving();
        if (!isOtrodieBoss(target)) {
            return;
        }

        final ActiveAbility active = AbilityRunner.getActive(target.getUUID());
        final DamageSource source = event.getSource();
        final float amount = event.getAmount();

        // Devour eat already cancelled in LivingAttackEvent; keep as safety net.
        if (active != null && DEVOUR_DASH_ID.equals(active.abilityId) && active.phase == DEVOUR_PHASE_EAT) {
            event.setCanceled(true);
            return;
        }

        final boolean frontal = ShieldBlockAbility.isFrontalHit(target, source, FRONT_BLOCK_ANGLE);

        if (active != null && HELL_VOMIT_ID.equals(active.abilityId) && !frontal) {
            active.meter += amount;
            final float breakDamage = (float) active.params.getDouble(
                    AbilityParamKeys.BREAK_DAMAGE, DEFAULT_BREAK_DAMAGE);
            if (active.meter >= breakDamage) {
                active.ticksLeft = 0;
            }
            return;
        }

        if (frontal) {
            event.setAmount(amount * FRONT_DAMAGE_FACTOR);
        }
    }

    private static boolean isOtrodieBoss(final LivingEntity target) {
        if (target == null || target.level == null || target.level.isClientSide) {
            return false;
        }
        if (!(target instanceof EntityNPCInterface)) {
            return false;
        }
        final IEntity wrapped = NpcAPI.Instance().getIEntity(target);
        if (!(wrapped instanceof ICustomNpc)) {
            return false;
        }
        return ScriptDataUtil.isFlag(((ICustomNpc) wrapped).getStoreddata(), OTRODIE_BOSS_FLAG);
    }

    private static boolean isMeleeHit(final LivingEntity target, final DamageSource source) {
        if (source == null || source.isProjectile()) {
            return false;
        }
        final Entity attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity)) {
            return false;
        }
        return attacker.distanceTo(target) <= MELEE_HIT_RADIUS;
    }
}
