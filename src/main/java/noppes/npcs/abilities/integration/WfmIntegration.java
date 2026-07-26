package noppes.npcs.abilities.integration;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntityLiving;

import java.lang.reflect.Method;

public final class WfmIntegration {
    private static final String NET_HELPER_CLASS = "wfm.common.integration.customnpc.CustomNpcNetHelper";
    private static final String GUN_HELPER_CLASS = "wfm.common.integration.customnpc.CustomNpcGunHelper";
    private static final String LEADBELCHER_HELPER_CLASS = "wfm.common.integration.customnpc.CustomNpcLeadbelcherHelper";
    private static final String MINE_HELPER_CLASS = "wfm.common.integration.customnpc.CustomNpcMineHelper";
    private static final String THROW_NET_METHOD = "throwDwarfRangerNet";
    private static final String THROW_NET_TOWARD_POINT_METHOD = "throwNetTowardPoint";
    private static final String ENSNARE_AROUND_POINT_METHOD = "ensnareAroundPoint";
    private static final String EQUIP_PISTOL_METHOD = "equipPistolForShot";
    private static final String RESTORE_EQUIPMENT_METHOD = "restoreEquipment";
    private static final String PERFORM_PISTOL_SHOT_METHOD = "performPistolShot";
    private static final String EQUIP_RATLING_METHOD = "equipRatlingGunForShot";
    private static final String PERFORM_RATLING_SHOT_METHOD = "performRatlingShot";
    private static final String EQUIP_LEADBELCHER_METHOD = "equipLeadbelcherForShot";
    private static final String PERFORM_LEADBELCHER_SHOT_TO_POINT_METHOD = "performLeadbelcherShotTowardPoint";
    private static final String PERFORM_LEADBELCHER_SHOT_AT_TARGET_METHOD = "performLeadbelcherShotAtTarget";
    private static final String PERFORM_LEADBELCHER_ARTILLERY_STRIKE_METHOD = "performLeadbelcherArtilleryStrike";
    private static final String SPAWN_VISUAL_MINE_METHOD = "spawnVisualMine";
    private static final String MOVE_VISUAL_MINE_METHOD = "moveVisualMine";
    private static final String REMOVE_VISUAL_MINE_METHOD = "removeVisualMine";

    private static Boolean netAvailable;
    private static Method throwNetMethod;
    private static Method throwNetTowardPointMethod;
    private static Method ensnareAroundPointMethod;

    private static Boolean gunAvailable;
    private static Method equipPistolMethod;
    private static Method restoreEquipmentMethod;
    private static Method performPistolShotMethod;
    private static Method equipRatlingMethod;
    private static Method performRatlingShotMethod;

    private static Boolean leadbelcherAvailable;
    private static Method equipLeadbelcherMethod;
    private static Method restoreLeadbelcherEquipmentMethod;
    private static Method performLeadbelcherShotToPointMethod;
    private static Method performLeadbelcherShotAtTargetMethod;
    private static Method performLeadbelcherArtilleryStrikeMethod;

    private static Boolean mineAvailable;
    private static Method spawnVisualMineMethod;
    private static Method moveVisualMineMethod;
    private static Method removeVisualMineMethod;

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

    public static boolean isWfmLeadbelcherAvailable() {
        ensureLeadbelcherInitialized();
        return Boolean.TRUE.equals(leadbelcherAvailable);
    }

    public static boolean isWfmMineAvailable() {
        ensureMineInitialized();
        return Boolean.TRUE.equals(mineAvailable);
    }

    /**
     * Спавнит визуальную GunMine без детонации.
     *
     * @return MC Entity или {@code null}
     */
    public static Object spawnVisualMine(final ICustomNpc npc, final double x, final double y, final double z) {
        ensureMineInitialized();
        if (!isWfmMineAvailable() || npc == null || spawnVisualMineMethod == null) {
            return null;
        }
        try {
            final LivingEntity living = toLivingEntity(npc);
            if (living == null) {
                return null;
            }
            final World world = living.level;
            return spawnVisualMineMethod.invoke(null, world, x, y, z);
        } catch (final Exception ignored) {
            return null;
        }
    }

    public static void moveVisualMine(final Object mine, final double x, final double y, final double z) {
        ensureMineInitialized();
        if (!isWfmMineAvailable() || mine == null || moveVisualMineMethod == null) {
            return;
        }
        try {
            moveVisualMineMethod.invoke(null, mine, x, y, z);
        } catch (final Exception ignored) {
        }
    }

    public static void removeVisualMine(final Object mine) {
        ensureMineInitialized();
        if (!isWfmMineAvailable() || mine == null || removeVisualMineMethod == null) {
            return;
        }
        try {
            removeVisualMineMethod.invoke(null, mine);
        } catch (final Exception ignored) {
        }
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

    /**
     * Летящая сеть в точку зоны (telegraph).
     */
    public static boolean throwNetTowardPoint(
            final ICustomNpc npc,
            final double x,
            final double y,
            final double z,
            final float velocity,
            final float inaccuracy) {
        ensureNetInitialized();
        if (!isWfmNetAvailable() || npc == null) {
            return false;
        }
        try {
            final LivingEntity thrower = toLivingEntity(npc);
            if (thrower == null) {
                return false;
            }
            if (throwNetTowardPointMethod != null) {
                final Object result = throwNetTowardPointMethod.invoke(
                        null, thrower, x, y, z, velocity, inaccuracy);
                return result instanceof Boolean && (Boolean) result;
            }
            return false;
        } catch (final Exception ignored) {
            return false;
        }
    }

    /**
     * @return число опутанных целей, или {@code -1} если WFM-хелпер недоступен
     */
    public static int ensnareAroundPoint(
            final ICustomNpc npc,
            final double x,
            final double y,
            final double z,
            final double radius,
            final int durationTicks) {
        ensureNetInitialized();
        if (!isWfmNetAvailable() || npc == null || ensnareAroundPointMethod == null) {
            return -1;
        }
        try {
            final LivingEntity source = toLivingEntity(npc);
            if (source == null) {
                return -1;
            }
            final Object result = ensnareAroundPointMethod.invoke(
                    null, source, x, y, z, radius, durationTicks);
            if (result instanceof Integer) {
                return (Integer) result;
            }
            if (result instanceof Number) {
                return ((Number) result).intValue();
            }
            return 0;
        } catch (final Exception ignored) {
            return -1;
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

    public static boolean equipRatlingGunForShot(final ICustomNpc npc, final String gunItemId) {
        ensureGunInitialized();
        if (!isWfmGunAvailable() || npc == null || equipRatlingMethod == null) {
            return equipPistolForShot(npc, gunItemId);
        }
        try {
            final LivingEntity shooter = toLivingEntity(npc);
            if (shooter == null) {
                return false;
            }
            final Object result = equipRatlingMethod.invoke(null, shooter, gunItemId);
            return result instanceof Boolean && (Boolean) result;
        } catch (final Exception ignored) {
            return equipPistolForShot(npc, gunItemId);
        }
    }

    public static boolean performRatlingShot(
            final ICustomNpc npc,
            final IEntityLiving target,
            final float inaccuracy,
            final float damage,
            final float velocity) {
        ensureGunInitialized();
        if (npc == null || target == null || !target.isAlive()) {
            return false;
        }
        if (performRatlingShotMethod == null) {
            return false;
        }
        try {
            final LivingEntity shooter = toLivingEntity(npc);
            final LivingEntity targetEntity = toLivingEntity(target);
            if (shooter == null || targetEntity == null) {
                return false;
            }
            final Object result = performRatlingShotMethod.invoke(
                    null,
                    shooter,
                    targetEntity,
                    inaccuracy,
                    damage,
                    velocity);
            return result instanceof Boolean && (Boolean) result;
        } catch (final Exception ignored) {
            return false;
        }
    }

    public static boolean equipLeadbelcherForShot(final ICustomNpc npc, final String gunItemId) {
        ensureLeadbelcherInitialized();
        if (!isWfmLeadbelcherAvailable() || npc == null) {
            return false;
        }
        try {
            final LivingEntity shooter = toLivingEntity(npc);
            if (shooter == null) {
                return false;
            }
            final Object result = equipLeadbelcherMethod.invoke(null, shooter, gunItemId);
            return result instanceof Boolean && (Boolean) result;
        } catch (final Exception ignored) {
            return false;
        }
    }

    public static void restoreLeadbelcherEquipment(final ICustomNpc npc) {
        ensureLeadbelcherInitialized();
        if (!isWfmLeadbelcherAvailable() || npc == null) {
            return;
        }
        try {
            final LivingEntity shooter = toLivingEntity(npc);
            if (shooter == null) {
                return;
            }
            restoreLeadbelcherEquipmentMethod.invoke(null, shooter);
        } catch (final Exception ignored) {
        }
    }

    public static boolean performLeadbelcherShotTowardPoint(
            final ICustomNpc npc,
            final double x,
            final double y,
            final double z,
            final String gunItemId,
            final float inaccuracy,
            final float damage) {
        ensureLeadbelcherInitialized();
        if (!isWfmLeadbelcherAvailable() || npc == null) {
            return false;
        }
        try {
            final LivingEntity shooter = toLivingEntity(npc);
            if (shooter == null) {
                return false;
            }
            final Object result = performLeadbelcherShotToPointMethod.invoke(
                    null,
                    shooter,
                    x,
                    y,
                    z,
                    gunItemId,
                    inaccuracy,
                    damage);
            return result instanceof Boolean && (Boolean) result;
        } catch (final Exception ignored) {
            return false;
        }
    }

    public static boolean performLeadbelcherShotAtTarget(
            final ICustomNpc npc,
            final IEntityLiving target,
            final String gunItemId,
            final float inaccuracy,
            final float damage) {
        ensureLeadbelcherInitialized();
        if (!isWfmLeadbelcherAvailable() || npc == null || target == null || !target.isAlive()) {
            return false;
        }
        try {
            final LivingEntity shooter = toLivingEntity(npc);
            final LivingEntity targetEntity = toLivingEntity(target);
            if (shooter == null || targetEntity == null) {
                return false;
            }
            final Object result = performLeadbelcherShotAtTargetMethod.invoke(
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

    public static boolean performLeadbelcherArtilleryStrike(
            final ICustomNpc npc,
            final double x,
            final double y,
            final double z,
            final String gunItemId,
            final int shots,
            final double spreadRadius,
            final float inaccuracy,
            final float damage) {
        ensureLeadbelcherInitialized();
        if (!isWfmLeadbelcherAvailable() || npc == null) {
            return false;
        }
        try {
            final LivingEntity shooter = toLivingEntity(npc);
            if (shooter == null) {
                return false;
            }
            final Object result = performLeadbelcherArtilleryStrikeMethod.invoke(
                    null,
                    shooter,
                    x,
                    y,
                    z,
                    gunItemId,
                    shots,
                    spreadRadius,
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
            try {
                throwNetTowardPointMethod = helper.getMethod(
                        THROW_NET_TOWARD_POINT_METHOD,
                        LivingEntity.class,
                        double.class,
                        double.class,
                        double.class,
                        float.class,
                        float.class);
            } catch (final Exception ignored) {
                throwNetTowardPointMethod = null;
            }
            try {
                ensnareAroundPointMethod = helper.getMethod(
                        ENSNARE_AROUND_POINT_METHOD,
                        LivingEntity.class,
                        double.class,
                        double.class,
                        double.class,
                        double.class,
                        int.class);
            } catch (final Exception ignored) {
                ensnareAroundPointMethod = null;
            }
            netAvailable = true;
        } catch (final Exception e) {
            netAvailable = false;
            throwNetMethod = null;
            throwNetTowardPointMethod = null;
            ensnareAroundPointMethod = null;
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
            try {
                equipRatlingMethod = helper.getMethod(
                        EQUIP_RATLING_METHOD,
                        LivingEntity.class,
                        String.class);
                performRatlingShotMethod = helper.getMethod(
                        PERFORM_RATLING_SHOT_METHOD,
                        LivingEntity.class,
                        LivingEntity.class,
                        float.class,
                        float.class,
                        float.class);
            } catch (final Exception ignored) {
                equipRatlingMethod = null;
                performRatlingShotMethod = null;
            }
            gunAvailable = true;
        } catch (final Exception e) {
            gunAvailable = false;
            equipPistolMethod = null;
            restoreEquipmentMethod = null;
            performPistolShotMethod = null;
            equipRatlingMethod = null;
            performRatlingShotMethod = null;
        }
    }

    private static void ensureLeadbelcherInitialized() {
        if (leadbelcherAvailable != null) {
            return;
        }
        try {
            final Class<?> helper = Class.forName(LEADBELCHER_HELPER_CLASS);
            equipLeadbelcherMethod = helper.getMethod(
                    EQUIP_LEADBELCHER_METHOD,
                    LivingEntity.class,
                    String.class);
            restoreLeadbelcherEquipmentMethod = helper.getMethod(
                    RESTORE_EQUIPMENT_METHOD,
                    LivingEntity.class);
            performLeadbelcherShotToPointMethod = helper.getMethod(
                    PERFORM_LEADBELCHER_SHOT_TO_POINT_METHOD,
                    LivingEntity.class,
                    double.class,
                    double.class,
                    double.class,
                    String.class,
                    float.class,
                    float.class);
            performLeadbelcherShotAtTargetMethod = helper.getMethod(
                    PERFORM_LEADBELCHER_SHOT_AT_TARGET_METHOD,
                    LivingEntity.class,
                    LivingEntity.class,
                    String.class,
                    float.class,
                    float.class);
            performLeadbelcherArtilleryStrikeMethod = helper.getMethod(
                    PERFORM_LEADBELCHER_ARTILLERY_STRIKE_METHOD,
                    LivingEntity.class,
                    double.class,
                    double.class,
                    double.class,
                    String.class,
                    int.class,
                    double.class,
                    float.class,
                    float.class);
            leadbelcherAvailable = true;
        } catch (final Exception e) {
            leadbelcherAvailable = false;
            equipLeadbelcherMethod = null;
            restoreLeadbelcherEquipmentMethod = null;
            performLeadbelcherShotToPointMethod = null;
            performLeadbelcherShotAtTargetMethod = null;
            performLeadbelcherArtilleryStrikeMethod = null;
        }
    }

    private static void ensureMineInitialized() {
        if (mineAvailable != null) {
            return;
        }
        try {
            final Class<?> helper = Class.forName(MINE_HELPER_CLASS);
            spawnVisualMineMethod = helper.getMethod(
                    SPAWN_VISUAL_MINE_METHOD,
                    World.class,
                    double.class,
                    double.class,
                    double.class);
            moveVisualMineMethod = helper.getMethod(
                    MOVE_VISUAL_MINE_METHOD,
                    Entity.class,
                    double.class,
                    double.class,
                    double.class);
            removeVisualMineMethod = helper.getMethod(
                    REMOVE_VISUAL_MINE_METHOD,
                    Entity.class);
            mineAvailable = true;
        } catch (final Exception e) {
            mineAvailable = false;
            spawnVisualMineMethod = null;
            moveVisualMineMethod = null;
            removeVisualMineMethod = null;
        }
    }
}
