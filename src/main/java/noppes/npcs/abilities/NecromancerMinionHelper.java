package noppes.npcs.abilities;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.IEntityLiving;
import noppes.npcs.api.entity.data.IData;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.script.ScriptDataUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class NecromancerMinionHelper {
    public static final String BOSS_FLAG = "necro_boss";
    public static final String STUN_FLAG = "necro_stunned";

    public static final String SPHERE_TAG = "necro_sphere";
    public static final String SKELETON_TAG = "necro_skeleton";

    public static final String OWNER_UUID_KEY = "necro_owner_uuid";
    public static final String PARENT_UUID_KEY = "necro_parent_uuid";
    public static final String ORB_KILLS_KEY = "necro_orb_kills";
    public static final String STUN_UNTIL_KEY = "necro_stun_until";
    public static final String BEAM_COUNT_KEY = "necro_beam_count";
    public static final String BEAM_LENGTH_KEY = "necro_beam_length";
    public static final String SUMMON_INTERVAL_KEY = "necro_summon_interval";

    public static final String CLONE_TAB_KEY = "necro_clone_tab";
    public static final String SPHERE_CLONE_NAME_KEY = "necro_sphere_clone";
    public static final String SKELETON_CLONE_NAME_KEY = "necro_skeleton_clone";

    public static final String NEXT_SUMMON_TICK_KEY = "necro_next_summon_tick";
    public static final String SPAWNED_COUNT_KEY = "necro_spawned_count";

    public static final int DISABLED_SPAWN_CYCLE = 3;
    public static final int DEFAULT_REQUIRED_ORBS = 3;
    public static final int DEFAULT_STUN_TICKS = 15 * 20;
    public static final int DEFAULT_BEAM_COUNT = 1;
    /** Blocks along beam corridor; overridden from necromancer_boss.js. */
    public static final float DEFAULT_BEAM_LENGTH = 16.0F;
    private static final float MIN_BEAM_LENGTH = 4.0F;
    private static final float MAX_BEAM_LENGTH = 48.0F;
    /** Ticks between skeleton waves per sphere; overridden from necromancer_boss.js. */
    public static final int DEFAULT_SUMMON_INTERVAL = 200;
    private static final int MIN_SUMMON_INTERVAL = 20;
    private static final int MAX_SUMMON_INTERVAL = 1200;
    public static final int DEFAULT_CLONE_TAB = 1;

    private NecromancerMinionHelper() {
    }

    public static void configureBoss(
            final ICustomNpc boss,
            final int cloneTab,
            final String sphereCloneName,
            final String skeletonCloneName) {
        if (boss == null) {
            return;
        }
        final IData data = boss.getStoreddata();
        ScriptDataUtil.setFlag(data, BOSS_FLAG, true);
        ScriptDataUtil.setFlag(data, STUN_FLAG, false);
        ScriptDataUtil.putInt(data, ORB_KILLS_KEY, 0);
        ScriptDataUtil.putInt(data, STUN_UNTIL_KEY, 0);
        ScriptDataUtil.putInt(data, BEAM_COUNT_KEY, DEFAULT_BEAM_COUNT);
        ScriptDataUtil.putInt(data, CLONE_TAB_KEY, cloneTab <= 0 ? DEFAULT_CLONE_TAB : cloneTab);
        ScriptDataUtil.putString(data, SPHERE_CLONE_NAME_KEY, safeName(sphereCloneName, "Necromancer Sphere"));
        ScriptDataUtil.putString(data, SKELETON_CLONE_NAME_KEY, safeName(skeletonCloneName, "Necromancer Skeleton"));
    }

    public static int getCloneTab(final ICustomNpc boss) {
        final int tab = ScriptDataUtil.getInt(boss.getStoreddata(), CLONE_TAB_KEY);
        return tab > 0 ? tab : DEFAULT_CLONE_TAB;
    }

    public static String getSphereCloneName(final ICustomNpc boss) {
        return readString(boss.getStoreddata(), SPHERE_CLONE_NAME_KEY, "Necromancer Sphere");
    }

    public static String getSkeletonCloneName(final ICustomNpc boss) {
        return readString(boss.getStoreddata(), SKELETON_CLONE_NAME_KEY, "Necromancer Skeleton");
    }

    public static int getOrbKills(final ICustomNpc boss) {
        return ScriptDataUtil.getInt(boss.getStoreddata(), ORB_KILLS_KEY);
    }

    public static void setOrbKills(final ICustomNpc boss, final int value) {
        ScriptDataUtil.putInt(boss.getStoreddata(), ORB_KILLS_KEY, Math.max(0, value));
    }

    public static int getBeamCount(final ICustomNpc boss) {
        final int count = ScriptDataUtil.getInt(boss.getStoreddata(), BEAM_COUNT_KEY);
        return Math.max(1, Math.min(3, count <= 0 ? DEFAULT_BEAM_COUNT : count));
    }

    public static void setBeamCount(final ICustomNpc boss, final int value) {
        ScriptDataUtil.putInt(boss.getStoreddata(), BEAM_COUNT_KEY, Math.max(1, Math.min(3, value)));
    }

    public static float getBeamLength(final ICustomNpc boss) {
        if (boss == null) {
            return DEFAULT_BEAM_LENGTH;
        }
        final float length = ScriptDataUtil.getFloat(boss.getStoreddata(), BEAM_LENGTH_KEY);
        return clampBeamLength(length <= 0.0F ? DEFAULT_BEAM_LENGTH : length);
    }

    public static void setBeamLength(final ICustomNpc boss, final float value) {
        if (boss == null) {
            return;
        }
        ScriptDataUtil.putFloat(boss.getStoreddata(), BEAM_LENGTH_KEY, clampBeamLength(value));
    }

    private static float clampBeamLength(final float value) {
        return Math.max(MIN_BEAM_LENGTH, Math.min(MAX_BEAM_LENGTH, value));
    }

    public static int getSummonInterval(final ICustomNpc boss) {
        if (boss == null) {
            return DEFAULT_SUMMON_INTERVAL;
        }
        final int interval = ScriptDataUtil.getInt(boss.getStoreddata(), SUMMON_INTERVAL_KEY);
        return clampSummonInterval(interval <= 0 ? DEFAULT_SUMMON_INTERVAL : interval);
    }

    public static void setSummonInterval(final ICustomNpc boss, final int value) {
        if (boss == null) {
            return;
        }
        ScriptDataUtil.putInt(boss.getStoreddata(), SUMMON_INTERVAL_KEY, clampSummonInterval(value));
    }

    private static int clampSummonInterval(final int value) {
        return Math.max(MIN_SUMMON_INTERVAL, Math.min(MAX_SUMMON_INTERVAL, value));
    }

    public static boolean isBossFlagSet(final ICustomNpc boss) {
        return boss != null && ScriptDataUtil.isFlag(boss.getStoreddata(), BOSS_FLAG);
    }

    public static void ensureBossFlag(final ICustomNpc boss) {
        if (boss == null) {
            return;
        }
        ScriptDataUtil.setFlag(boss.getStoreddata(), BOSS_FLAG, true);
    }

    public static void setStunned(final ICustomNpc boss, final boolean stunned, final int stunUntilTick) {
        final IData data = boss.getStoreddata();
        ScriptDataUtil.setFlag(data, STUN_FLAG, stunned);
        ScriptDataUtil.putInt(data, STUN_UNTIL_KEY, Math.max(0, stunUntilTick));
    }

    public static boolean isStunned(final ICustomNpc boss, final int nowTick) {
        if (boss == null) {
            return false;
        }
        final IData data = boss.getStoreddata();
        if (!ScriptDataUtil.isFlag(data, STUN_FLAG)) {
            return false;
        }
        return nowTick < ScriptDataUtil.getInt(data, STUN_UNTIL_KEY);
    }

    public static int getStunUntil(final ICustomNpc boss) {
        return ScriptDataUtil.getInt(boss.getStoreddata(), STUN_UNTIL_KEY);
    }

    public static void resetEncounterState(final ICustomNpc boss) {
        if (boss == null) {
            return;
        }
        setOrbKills(boss, 0);
        setBeamCount(boss, DEFAULT_BEAM_COUNT);
        setStunned(boss, false, 0);
    }

    public static IEntity spawnSphere(
            final ICustomNpc boss,
            final IEntityLiving target,
            final double x,
            final double y,
            final double z) {
        if (boss == null) {
            return null;
        }
        final IEntity spawned = boss.getWorld().spawnClone(
                x,
                y,
                z,
                getCloneTab(boss),
                getSphereCloneName(boss));
        if (spawned == null) {
            return null;
        }
        spawned.addTag(SPHERE_TAG);
        final IData data = spawned.getStoreddata();
        ScriptDataUtil.putString(data, OWNER_UUID_KEY, String.valueOf(boss.getUUID()));
        ScriptDataUtil.putInt(data, SPAWNED_COUNT_KEY, 0);
        ScriptDataUtil.putInt(data, NEXT_SUMMON_TICK_KEY, 0);
        disableRespawn(spawned);
        setTarget(spawned, target);
        return spawned;
    }

    public static IEntity spawnSkeleton(
            final ICustomNpc boss,
            final IEntity sphere,
            final IEntityLiving target,
            final double x,
            final double y,
            final double z) {
        if (boss == null || sphere == null) {
            return null;
        }
        final IEntity spawned = boss.getWorld().spawnClone(
                x,
                y,
                z,
                getCloneTab(boss),
                getSkeletonCloneName(boss));
        if (spawned == null) {
            return null;
        }
        spawned.addTag(SKELETON_TAG);
        final IData data = spawned.getStoreddata();
        ScriptDataUtil.putString(data, OWNER_UUID_KEY, String.valueOf(boss.getUUID()));
        ScriptDataUtil.putString(data, PARENT_UUID_KEY, String.valueOf(sphere.getUUID()));
        disableRespawn(spawned);
        setTarget(spawned, target);
        return spawned;
    }

    public static void disableRespawn(final IEntity entity) {
        if (entity == null) {
            return;
        }
        try {
            final Object mc = entity.getMCEntity();
            if (mc instanceof EntityNPCInterface) {
                final EntityNPCInterface npc = (EntityNPCInterface) mc;
                npc.stats.spawnCycle = DISABLED_SPAWN_CYCLE;
                npc.stats.respawnTime = 0;
                npc.killedtime = 0;
                npc.updateClient = true;
            }
        } catch (final Exception ignored) {
        }
    }

    public static void setTarget(final IEntity entity, final IEntityLiving target) {
        if (entity == null || target == null || !target.isAlive()) {
            return;
        }
        try {
            final Object mc = entity.getMCEntity();
            final Object targetMc = target.getMCEntity();
            if (mc instanceof MobEntity && targetMc instanceof LivingEntity) {
                ((MobEntity) mc).setTarget((LivingEntity) targetMc);
            }
        } catch (final Exception ignored) {
        }
    }

    public static List<IEntity> listOwnedTagged(final ICustomNpc boss, final String tag, final double radius) {
        final List<IEntity> result = new ArrayList<>();
        if (boss == null || tag == null || tag.isEmpty()) {
            return result;
        }
        final int range = (int) Math.ceil(radius + 0.5);
        final IEntity[] list = boss.getWorld().getNearbyEntities(
                NpcAPI.Instance().getIPos(boss.getX(), boss.getY(), boss.getZ()),
                range,
                -1);
        final String ownerId = String.valueOf(boss.getUUID());
        for (final IEntity ent : list) {
            if (ent == null || !ent.isAlive() || !ent.hasTag(tag)) {
                continue;
            }
            if (ownerId.equals(readString(ent.getStoreddata(), OWNER_UUID_KEY, ""))) {
                result.add(ent);
            }
        }
        return result;
    }

    public static boolean hasLivingSpheres(final ICustomNpc boss, final double radius) {
        return !listOwnedTagged(boss, SPHERE_TAG, radius).isEmpty();
    }

    public static void removeBossMinions(final ICustomNpc boss, final double radius) {
        if (boss == null) {
            return;
        }
        for (final IEntity ent : listOwnedTagged(boss, SPHERE_TAG, radius)) {
            discard(ent);
        }
        removeBossSkeletons(boss, radius);
    }

    /** Убирает только скелетов босса (сферы остаются). */
    public static void removeBossSkeletons(final ICustomNpc boss, final double radius) {
        if (boss == null) {
            return;
        }
        for (final IEntity ent : listOwnedTagged(boss, SKELETON_TAG, radius)) {
            discard(ent);
        }
    }

    public static int countChildrenOfSphere(
            final ICustomNpc boss,
            final String sphereId,
            final double radius) {
        if (boss == null || sphereId == null || sphereId.isEmpty()) {
            return 0;
        }
        int count = 0;
        final int range = (int) Math.ceil(radius + 0.5);
        final IEntity[] list = boss.getWorld().getNearbyEntities(
                NpcAPI.Instance().getIPos(boss.getX(), boss.getY(), boss.getZ()),
                range,
                -1);
        for (final IEntity ent : list) {
            if (ent == null || !ent.isAlive() || !ent.hasTag(SKELETON_TAG)) {
                continue;
            }
            if (sphereId.equals(readString(ent.getStoreddata(), PARENT_UUID_KEY, ""))) {
                count++;
            }
        }
        return count;
    }

    public static void removeChildrenOfSphere(final IEntity sphere, final double radius) {
        if (sphere == null) {
            return;
        }
        final IEntity owner = resolveBoss(readString(sphere.getStoreddata(), OWNER_UUID_KEY, ""));
        if (!(owner instanceof ICustomNpc)) {
            return;
        }
        removeChildrenOfSphereUuid((ICustomNpc) owner, String.valueOf(sphere.getUUID()), radius);
    }

    public static void removeChildrenOfSphereUuid(final ICustomNpc boss, final String sphereId, final double radius) {
        if (boss == null || sphereId == null || sphereId.isEmpty()) {
            return;
        }
        final int range = (int) Math.ceil(radius + 0.5);
        final IEntity[] list = boss.getWorld().getNearbyEntities(
                NpcAPI.Instance().getIPos(boss.getX(), boss.getY(), boss.getZ()),
                range,
                -1);
        for (final IEntity ent : list) {
            if (ent == null || !ent.hasTag(SKELETON_TAG)) {
                continue;
            }
            if (sphereId.equals(readString(ent.getStoreddata(), PARENT_UUID_KEY, ""))) {
                discard(ent);
            }
        }
    }

    public static IEntity resolveBoss(final String uuidString) {
        final UUID uuid = parseUuid(uuidString);
        if (uuid == null) {
            return null;
        }
        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        for (final ServerWorld level : server.getAllLevels()) {
            final Entity entity = level.getEntity(uuid);
            if (entity == null) {
                continue;
            }
            try {
                return NpcAPI.Instance().getIEntity(entity);
            } catch (final Exception ignored) {
                return null;
            }
        }
        return null;
    }

    public static void discard(final IEntity entity) {
        if (entity == null) {
            return;
        }
        try {
            final Object mc = entity.getMCEntity();
            if (mc instanceof Entity) {
                ((Entity) mc).remove();
            }
        } catch (final Exception ignored) {
        }
    }

    public static UUID parseUuid(final String value) {
        try {
            return value == null || value.isEmpty() ? null : UUID.fromString(value);
        } catch (final Exception ignored) {
            return null;
        }
    }

    private static String readString(final IData data, final String key, final String fallback) {
        if (data == null || !data.has(key)) {
            return fallback;
        }
        final Object raw = data.get(key);
        if (raw == null) {
            return fallback;
        }
        final String value = String.valueOf(raw);
        return value.isEmpty() ? fallback : value;
    }

    private static String safeName(final String value, final String fallback) {
        if (value == null) {
            return fallback;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
