package noppes.npcs.abilities.integration;

import net.minecraft.entity.LivingEntity;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntityLiving;

import java.lang.reflect.Method;

public final class WfmIntegration {
    private static final String NET_HELPER_CLASS = "wfm.common.integration.customnpc.CustomNpcNetHelper";
    private static final String GUN_HELPER_CLASS = "wfm.common.integration.customnpc.CustomNpcGunHelper";
    private static final String THROW_NET_METHOD = "throwDwarfRangerNet";
    private static final String EQUIP_PISTOL_METHOD = "equipPistolForShot";
    private static final String RESTORE_EQUIPMENT_METHOD = "restoreEquipment";
    private static final String PERFORM_PISTOL_SHOT_METHOD = "performPistolShot";

    private static Boolean netAvailable;
    private static Method throwNetMethod;

    private static Boolean gunAvailable;
    private static Method equipPistolMethod;
    private static Method restoreEquipmentMethod;
    private static Method performPistolShotMethod;

    private WfmIntegration() {
    }

    public static boolean isWfmNetAvailable() {
        ensureNetInitialized();
        return Boolean.TRUE.equals(netAvailable);
    }

    public static boolean isWfmGunAvailable() {
        ensureGunInitialized();
        return Boolean.TRUE.equals(gunAvailable);
    }

    public static boolean throwDwarfRangerNet(
            final ICustomNpc npc,
            final IEntityLiving target,
            final float inaccuracy) {
        ensureNetInitialized();
        if (!isWfmNetAvailable() || npc == null || target == null || !target.isAlive()) {
            return false;
        }
        try {
            final LivingEntity thrower = toLivingEntity(npc);
            final LivingEntity targetEntity = toLivingEntity(target);
            if (thrower == null || targetEntity == null) {
                return false;
            }
            final Object result = throwNetMethod.invoke(null, thrower, targetEntity, inaccuracy);
            return result instanceof Boolean && (Boolean) result;
        } catch (final Exception ignored) {
            return false;
        }
    }

    public static boolean equipPistolForShot(final ICustomNpc npc, final String gunItemId) {
        ensureGunInitialized();
        if (!isWfmGunAvailable() || npc == null) {
            return false;
        }
        try {
            final LivingEntity shooter = toLivingEntity(npc);
            if (shooter == null) {
                return false;
            }
            final Object result = equipPistolMethod.invoke(null, shooter, gunItemId);
            return result instanceof Boolean && (Boolean) result;
        } catch (final Exception ignored) {
            return false;
        }
    }

    public static void restorePistolEquipment(final ICustomNpc npc) {
        ensureGunInitialized();
        if (!isWfmGunAvailable() || npc == null) {
            return;
        }
        try {
            final LivingEntity shooter = toLivingEntity(npc);
            if (shooter == null) {
                return;
            }
            restoreEquipmentMethod.invoke(null, shooter);
        } catch (final Exception ignored) {
        }
    }

    public static boolean performPistolShot(
            final ICustomNpc npc,
            final IEntityLiving target,
            final String gunItemId,
            final float inaccuracy,
            final float damage) {
        ensureGunInitialized();
        if (!isWfmGunAvailable() || npc == null || target == null || !target.isAlive()) {
            return false;
        }
        try {
            final LivingEntity shooter = toLivingEntity(npc);
            final LivingEntity targetEntity = toLivingEntity(target);
            if (shooter == null || targetEntity == null) {
                return false;
            }
            final Object result = performPistolShotMethod.invoke(
                    null,
                    shooter,
                    targetEntity,
                    gunItemId,
                    inaccuracy,
                    damage);
            return result instanceof Boolean && (Boolean) result;
        } catch (final Exception ignored) {
            return false;
        }
    }

    private static LivingEntity toLivingEntity(final IEntityLiving entity) {
        try {
            final Object mc = entity.getMCEntity();
            if (mc instanceof LivingEntity) {
                return (LivingEntity) mc;
            }
        } catch (final Exception ignored) {
        }
        return null;
    }

    private static void ensureNetInitialized() {
        if (netAvailable != null) {
            return;
        }
        try {
            final Class<?> helper = Class.forName(NET_HELPER_CLASS);
            throwNetMethod = helper.getMethod(
                    THROW_NET_METHOD,
                    LivingEntity.class,
                    LivingEntity.class,
                    float.class);
            netAvailable = true;
        } catch (final Exception e) {
            netAvailable = false;
            throwNetMethod = null;
        }
    }

    private static void ensureGunInitialized() {
        if (gunAvailable != null) {
            return;
        }
        try {
            final Class<?> helper = Class.forName(GUN_HELPER_CLASS);
            equipPistolMethod = helper.getMethod(
                    EQUIP_PISTOL_METHOD,
                    LivingEntity.class,
                    String.class);
            restoreEquipmentMethod = helper.getMethod(
                    RESTORE_EQUIPMENT_METHOD,
                    LivingEntity.class);
            performPistolShotMethod = helper.getMethod(
                    PERFORM_PISTOL_SHOT_METHOD,
                    LivingEntity.class,
                    LivingEntity.class,
                    String.class,
                    float.class,
                    float.class);
            gunAvailable = true;
        } catch (final Exception e) {
            gunAvailable = false;
            equipPistolMethod = null;
            restoreEquipmentMethod = null;
            performPistolShotMethod = null;
        }
    }
}
