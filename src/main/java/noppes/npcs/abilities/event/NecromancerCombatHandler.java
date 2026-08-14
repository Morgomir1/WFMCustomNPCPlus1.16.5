package noppes.npcs.abilities.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.world.World;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import noppes.npcs.CustomEntities;
import noppes.npcs.abilities.AbilityCombatHelper;
import noppes.npcs.abilities.AbilityRunner;
import noppes.npcs.abilities.AbilityVfx;
import noppes.npcs.abilities.NecromancerMinionHelper;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.IEntityLiving;
import noppes.npcs.api.entity.data.IData;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.EntityNecroBeam;
import noppes.npcs.script.ScriptDataUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "customnpcs", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NecromancerCombatHandler {
    private static final int REQUIRED_ORBS = NecromancerMinionHelper.DEFAULT_REQUIRED_ORBS;
    private static final int STUN_TICKS = NecromancerMinionHelper.DEFAULT_STUN_TICKS;
    private static final int POST_STUN_CAST_DELAY = 60;
    private static final int POST_STUN_VOLLEY_COOLDOWN = 160;
    private static final int SPHERE_SUMMON_INTERVAL = 200;
    private static final int SKELETONS_PER_WAVE = 3;
    private static final int MAX_SKELETONS_PER_SPHERE = 9;
    private static final double SUMMON_RADIUS = 2.5;
    private static final double CONTROL_RADIUS = 64.0;
    private static final double KNOCKBACK_RADIUS = 7.0;
    private static final double KNOCKBACK_STRENGTH = 1.6;

    private static final String NEXT_CAST_KEY = "necro_next_cast";
    private static final String LAST_ABILITY_KEY = "necro_last_ability";
    private static final String VOLLEY_CD_KEY = "necro_cd_necro_volley";
    private static final String VOLLEY_ID = "necro_volley";

    private static final Map<UUID, BossState> STATES = new ConcurrentHashMap<>();

    private NecromancerCombatHandler() {
    }

    public static void initBoss(final ICustomNpc boss) {
        if (boss == null) {
            return;
        }
        final UUID uuid = parseUuid(boss);
        if (uuid == null) {
            return;
        }
        NecromancerMinionHelper.ensureBossFlag(boss);
        NecromancerMinionHelper.disableRespawn(boss);
        final BossState state = STATES.computeIfAbsent(uuid, BossState::new);
        // Beams only while fighting and not vulnerable — never on idle init.
        syncBeam(boss, state);
    }

    public static void cleanupBoss(final ICustomNpc boss) {
        if (boss == null) {
            return;
        }
        final UUID uuid = parseUuid(boss);
        if (uuid == null) {
            return;
        }
        final BossState state = STATES.remove(uuid);
        if (state != null) {
            state.ignoreSphereLoss = true;
            state.stunUntilGameTime = 0L;
            state.stunLatched = false;
            state.knownSpheres.clear();
        }
        removeBeam(state);
        NecromancerMinionHelper.removeBossMinions(boss, CONTROL_RADIUS);
        NecromancerMinionHelper.resetEncounterState(boss);
        unfreezeBoss(boss, state);
    }

    public static void onTargetLost(final ICustomNpc boss) {
        AbilityRunner.cancel(boss);
        final UUID uuid = parseUuid(boss);
        if (uuid == null) {
            return;
        }
        final BossState state = STATES.get(uuid);
        removeBeam(state);
    }

    public static boolean isStunned(final ICustomNpc boss) {
        if (boss == null) {
            return false;
        }
        final long now = boss.getWorld().getTotalTime();
        final UUID uuid = parseUuid(boss);
        if (uuid != null) {
            final BossState state = STATES.get(uuid);
            if (state != null && state.stunUntilGameTime > 0L) {
                return now < state.stunUntilGameTime;
            }
        }
        return NecromancerMinionHelper.isStunned(boss, (int) now);
    }

    public static boolean isDamageBlocked(final ICustomNpc boss) {
        return isNecroBoss(boss) && !isStunned(boss);
    }

    public static boolean hasLivingSpheres(final ICustomNpc boss) {
        return boss != null && NecromancerMinionHelper.hasLivingSpheres(boss, CONTROL_RADIUS);
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || STATES.isEmpty()) {
            return;
        }
        for (final BossState state : STATES.values()) {
            tickBoss(state);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttack(final LivingAttackEvent event) {
        if (event.isCanceled()) {
            return;
        }
        final ICustomNpc boss = wrapNecroBoss(event.getEntityLiving());
        if (boss == null) {
            return;
        }
        if (isDamageBlocked(boss)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(final LivingHurtEvent event) {
        if (event.isCanceled()) {
            return;
        }
        final ICustomNpc boss = wrapNecroBoss(event.getEntityLiving());
        if (boss == null) {
            return;
        }
        if (isDamageBlocked(boss)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(final LivingDeathEvent event) {
        final LivingEntity dead = event.getEntityLiving();
        final IEntity wrapped;
        try {
            wrapped = NpcAPI.Instance().getIEntity(dead);
        } catch (final Exception ignored) {
            return;
        }
        if (wrapped == null) {
            return;
        }
        if (wrapped.hasTag(NecromancerMinionHelper.SPHERE_TAG)) {
            onSphereKilled(wrapped);
        }
        final ICustomNpc boss = wrapNecroBoss(dead);
        if (boss != null) {
            cleanupBoss(boss);
        }
    }

    private static void tickBoss(final BossState state) {
        final ICustomNpc boss = resolveBoss(state.bossUuid);
        if (boss == null) {
            return;
        }
        if (!boss.isAlive()) {
            cleanupBoss(boss);
            return;
        }
        NecromancerMinionHelper.ensureBossFlag(boss);
        final long now = boss.getWorld().getTotalTime();
        pollSphereDeaths(boss, state);
        final boolean stunned = isBossStunned(boss, state, now);
        if (stunned) {
            freezeBoss(boss, state);
            AbilityRunner.cancel(boss);
            removeBeam(state);
            if (!state.stunLatched) {
                state.stunLatched = true;
            }
            // Vulnerable window: no invuln aura / beam particles (burst only on startStun).
            return;
        }

        if (state.stunLatched) {
            finishStun(boss, state, now);
        }

        // Invuln body aura only while not vulnerable (beams gated separately by combat).
        if (now % 4L == 0L) {
            AbilityVfx.spawnNecroInvuln(boss.getWorld(), boss.getX(), boss.getY(), boss.getZ());
        }
        syncBeam(boss, state);
        tickSphereSummons(boss, (int) now);
    }

    private static boolean isBossStunned(final ICustomNpc boss, final BossState state, final long now) {
        if (state.stunUntilGameTime > 0L) {
            return now < state.stunUntilGameTime;
        }
        return NecromancerMinionHelper.isStunned(boss, (int) now);
    }

    private static void pollSphereDeaths(final ICustomNpc boss, final BossState state) {
        final List<IEntity> spheres = NecromancerMinionHelper.listOwnedTagged(
                boss, NecromancerMinionHelper.SPHERE_TAG, CONTROL_RADIUS);
        final Set<UUID> living = new HashSet<>();
        for (final IEntity sphere : spheres) {
            final UUID id = parseEntityUuid(sphere);
            if (id != null) {
                living.add(id);
            }
        }
        if (!state.ignoreSphereLoss) {
            for (final UUID previous : state.knownSpheres) {
                if (living.contains(previous) || isEntityStillAlive(previous)) {
                    continue;
                }
                onSphereDisappeared(boss, state, previous);
                if (state.stunLatched || isStunned(boss)) {
                    break;
                }
            }
        }
        state.knownSpheres.clear();
        state.knownSpheres.addAll(living);
        state.ignoreSphereLoss = false;
    }

    private static void onSphereKilled(final IEntity sphere) {
        final UUID sphereUuid = parseEntityUuid(sphere);
        final IEntity owner = NecromancerMinionHelper.resolveBoss(
                String.valueOf(sphere.getStoreddata().get(NecromancerMinionHelper.OWNER_UUID_KEY)));
        if (!(owner instanceof ICustomNpc)) {
            return;
        }
        final ICustomNpc boss = (ICustomNpc) owner;
        if (!boss.isAlive()) {
            return;
        }
        final UUID bossUuid = parseUuid(boss);
        if (bossUuid == null) {
            return;
        }
        NecromancerMinionHelper.ensureBossFlag(boss);
        final BossState state = STATES.computeIfAbsent(bossUuid, BossState::new);
        onSphereDisappeared(boss, state, sphereUuid);
    }

    private static void onSphereDisappeared(
            final ICustomNpc boss,
            final BossState state,
            final UUID sphereUuid) {
        if (state.ignoreSphereLoss) {
            return;
        }
        if (sphereUuid != null && !state.countedSpheres.add(sphereUuid)) {
            return;
        }
        NecromancerMinionHelper.removeChildrenOfSphereUuid(
                boss, sphereUuid == null ? "" : sphereUuid.toString(), 48.0);
        final int kills = NecromancerMinionHelper.getOrbKills(boss) + 1;
        NecromancerMinionHelper.setOrbKills(boss, kills);
        NecromancerMinionHelper.setBeamCount(boss, 1 + kills);
        if (kills >= REQUIRED_ORBS) {
            startStun(boss, state);
        } else {
            syncBeam(boss, state);
        }
    }

    private static void startStun(final ICustomNpc boss, final BossState state) {
        AbilityRunner.cancel(boss);
        final long now = boss.getWorld().getTotalTime();
        state.stunUntilGameTime = now + STUN_TICKS;
        state.stunLatched = true;
        NecromancerMinionHelper.setStunned(boss, true, (int) Math.min(Integer.MAX_VALUE, state.stunUntilGameTime));
        clearBossTarget(boss);
        freezeBoss(boss, state);
        removeBeam(state);
        AbilityVfx.spawnSoulBurst(boss.getWorld(), boss.getX(), boss.getY() + 0.3, boss.getZ(), 2.2);
    }

    private static void finishStun(final ICustomNpc boss, final BossState state, final long now) {
        final IData data = boss.getStoreddata();
        ScriptDataUtil.setFlag(data, NecromancerMinionHelper.STUN_FLAG, false);
        ScriptDataUtil.putInt(data, NecromancerMinionHelper.STUN_UNTIL_KEY, 0);
        ScriptDataUtil.putInt(data, NecromancerMinionHelper.ORB_KILLS_KEY, 0);
        ScriptDataUtil.putInt(data, NecromancerMinionHelper.BEAM_COUNT_KEY, 1);
        // Prevent instant necro_volley right after the vulnerability window ends.
        ScriptDataUtil.putString(data, NEXT_CAST_KEY, String.valueOf(now + POST_STUN_CAST_DELAY));
        ScriptDataUtil.putString(data, VOLLEY_CD_KEY, String.valueOf(now + POST_STUN_VOLLEY_COOLDOWN));
        ScriptDataUtil.putString(data, LAST_ABILITY_KEY, VOLLEY_ID);
        state.stunUntilGameTime = 0L;
        state.countedSpheres.clear();
        state.knownSpheres.clear();
        unfreezeBoss(boss, state);
        knockbackNearby(boss);
        state.stunLatched = false;
        // Beams return only if still in combat after vulnerability ends.
        syncBeam(boss, state);
        AbilityVfx.spawnSoulBurst(boss.getWorld(), boss.getX(), boss.getY() + 0.3, boss.getZ(), 2.8);
    }

    private static void tickSphereSummons(final ICustomNpc boss, final int now) {
        final List<IEntity> spheres = NecromancerMinionHelper.listOwnedTagged(
                boss, NecromancerMinionHelper.SPHERE_TAG, CONTROL_RADIUS);
        for (final IEntity sphere : spheres) {
            final IData data = sphere.getStoreddata();
            final int spawned = ScriptDataUtil.getInt(data, NecromancerMinionHelper.SPAWNED_COUNT_KEY);
            if (spawned >= MAX_SKELETONS_PER_SPHERE) {
                continue;
            }
            // nextTick <= 0: freshly spawned sphere → summon skeletons immediately
            final int nextTick = ScriptDataUtil.getInt(data, NecromancerMinionHelper.NEXT_SUMMON_TICK_KEY);
            if (nextTick > 0 && now < nextTick) {
                continue;
            }
            final IEntityLiving target = boss.getAttackTarget();
            final int toSpawn = Math.min(SKELETONS_PER_WAVE, MAX_SKELETONS_PER_SPHERE - spawned);
            for (int i = 0; i < toSpawn; i++) {
                final double angle = (Math.PI * 2.0 * i) / Math.max(1, toSpawn)
                        + AbilityCombatHelper.random().nextDouble() * 0.35;
                final double dist = SUMMON_RADIUS * (0.45 + AbilityCombatHelper.random().nextDouble() * 0.55);
                final double sx = sphere.getX() + Math.cos(angle) * dist;
                final double sz = sphere.getZ() + Math.sin(angle) * dist;
                final double sy = AbilityCombatHelper.findGroundY(boss.getWorld(), sx, sz, sphere.getY());
                NecromancerMinionHelper.spawnSkeleton(boss, sphere, target, sx, sy, sz);
                AbilityVfx.spawnSoulBurst(boss.getWorld(), sx, sy + 0.2, sz, 0.9);
            }
            ScriptDataUtil.putInt(data, NecromancerMinionHelper.SPAWNED_COUNT_KEY, spawned + toSpawn);
            ScriptDataUtil.putInt(data, NecromancerMinionHelper.NEXT_SUMMON_TICK_KEY, now + SPHERE_SUMMON_INTERVAL);
        }
    }

    /**
     * Passive presence: only while invulnerable combat (has living attack target) and not stunned.
     * Vulnerable / idle → no beams.
     */
    private static void syncBeam(final ICustomNpc boss, final BossState state) {
        if (boss == null || state == null) {
            return;
        }
        if (shouldHaveBeams(boss)) {
            ensureBeam(boss, state);
        } else {
            removeBeam(state);
        }
    }

    private static boolean shouldHaveBeams(final ICustomNpc boss) {
        return boss != null && boss.isAlive() && !isStunned(boss) && hasCombatTarget(boss);
    }

    private static boolean hasCombatTarget(final ICustomNpc boss) {
        if (boss == null) {
            return false;
        }
        try {
            final IEntityLiving target = boss.getAttackTarget();
            return target != null && target.isAlive();
        } catch (final Exception ignored) {
            return false;
        }
    }

    private static void ensureBeam(final ICustomNpc boss, final BossState state) {
        if (boss == null || state == null || !shouldHaveBeams(boss)) {
            return;
        }
        EntityNecroBeam beam = resolveBeam(state.beamUuid);
        if (beam == null) {
            final Object mc = boss.getMCEntity();
            if (!(mc instanceof EntityNPCInterface)) {
                return;
            }
            final World level = ((EntityNPCInterface) mc).level;
            if (CustomEntities.entityNecroBeam == null) {
                return;
            }
            beam = CustomEntities.entityNecroBeam.create(level);
            if (beam == null) {
                beam = new EntityNecroBeam(CustomEntities.entityNecroBeam, level);
            }
            final UUID ownerUuid = parseUuid(boss);
            beam.setOwnerUuid(ownerUuid);
            beam.setOwnerEntityId(((EntityNPCInterface) mc).getId());
            beam.setBeamCount(NecromancerMinionHelper.getBeamCount(boss));
            // Ground-level rectangular zones (not mid-body beacon beams).
            final double groundY = noppes.npcs.telegraph.TelegraphAPI.resolveGroundY(
                    level, boss.getX(), boss.getY(), boss.getZ());
            beam.moveTo(boss.getX(), groundY, boss.getZ(), 0, 0);
            level.addFreshEntity(beam);
            state.beamUuid = beam.getUUID();
        } else {
            beam.setOwnerUuid(parseUuid(boss));
            final Object ownerMc = boss.getMCEntity();
            if (ownerMc instanceof EntityNPCInterface) {
                beam.setOwnerEntityId(((EntityNPCInterface) ownerMc).getId());
            }
            beam.setBeamCount(NecromancerMinionHelper.getBeamCount(boss));
        }
    }

    private static void removeBeam(final BossState state) {
        if (state == null || state.beamUuid == null) {
            return;
        }
        final EntityNecroBeam beam = resolveBeam(state.beamUuid);
        if (beam != null) {
            beam.remove();
        }
        state.beamUuid = null;
    }

    private static void freezeBoss(final ICustomNpc boss, final BossState state) {
        if (boss == null) {
            return;
        }
        try {
            if (state != null && !state.savedAi) {
                state.savedSpeed = boss.getAi().getWalkingSpeed();
                state.savedRetaliate = boss.getAi().getRetaliateType();
                state.savedAi = true;
            }
            boss.getAi().setWalkingSpeed(0);
            boss.getAi().setRetaliateType(3);
        } catch (final Exception ignored) {
        }
        clearBossTarget(boss);
        AbilityCombatHelper.stopNavigation(boss);
        AbilityCombatHelper.zeroHorizontalMotion(boss);
    }

    private static void clearBossTarget(final ICustomNpc boss) {
        if (boss == null) {
            return;
        }
        try {
            final Object mc = boss.getMCEntity();
            if (mc instanceof MobEntity) {
                ((MobEntity) mc).setTarget(null);
            }
        } catch (final Exception ignored) {
        }
    }

    private static void unfreezeBoss(final ICustomNpc boss, final BossState state) {
        if (boss == null) {
            return;
        }
        try {
            if (state != null && state.savedAi) {
                boss.getAi().setWalkingSpeed(state.savedSpeed);
                boss.getAi().setRetaliateType(state.savedRetaliate);
                state.savedAi = false;
                state.savedSpeed = 0;
                state.savedRetaliate = 0;
            }
        } catch (final Exception ignored) {
        }
    }

    private static void knockbackNearby(final ICustomNpc boss) {
        final int range = (int) Math.ceil(KNOCKBACK_RADIUS + 0.5);
        final IEntity[] list = boss.getWorld().getNearbyEntities(
                NpcAPI.Instance().getIPos(boss.getX(), boss.getY(), boss.getZ()),
                range,
                -1);
        for (final IEntity ent : list) {
            if (!(ent instanceof IEntityLiving) || !AbilityCombatHelper.isHostileToBoss(boss, ent)) {
                continue;
            }
            if (AbilityCombatHelper.flatDistance(ent.getX(), ent.getZ(), boss.getX(), boss.getZ()) > KNOCKBACK_RADIUS) {
                continue;
            }
            final IEntityLiving living = (IEntityLiving) ent;
            double dx = living.getX() - boss.getX();
            double dz = living.getZ() - boss.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.05) {
                dx = 1.0;
                dz = 0.0;
                len = 1.0;
            }
            living.setMotionX((dx / len) * KNOCKBACK_STRENGTH);
            living.setMotionY(0.0);
            living.setMotionZ((dz / len) * KNOCKBACK_STRENGTH);
            AbilityVfx.spawnHitParticle(boss.getWorld(), ent);
        }
    }

    private static ICustomNpc wrapNecroBoss(final LivingEntity entity) {
        if (entity == null || entity.level == null || entity.level.isClientSide) {
            return null;
        }
        if (!(entity instanceof EntityNPCInterface)) {
            return null;
        }
        final IEntity wrapped;
        try {
            wrapped = NpcAPI.Instance().getIEntity(entity);
        } catch (final Exception ignored) {
            return null;
        }
        if (!(wrapped instanceof ICustomNpc)) {
            return null;
        }
        final ICustomNpc boss = (ICustomNpc) wrapped;
        return isNecroBoss(boss) ? boss : null;
    }

    private static boolean isNecroBoss(final ICustomNpc boss) {
        if (boss == null) {
            return false;
        }
        if (NecromancerMinionHelper.isBossFlagSet(boss)) {
            return true;
        }
        final UUID uuid = parseUuid(boss);
        return uuid != null && STATES.containsKey(uuid);
    }

    private static ICustomNpc resolveBoss(final UUID uuid) {
        final IEntity ent = NecromancerMinionHelper.resolveBoss(String.valueOf(uuid));
        return ent instanceof ICustomNpc ? (ICustomNpc) ent : null;
    }

    private static EntityNecroBeam resolveBeam(final UUID uuid) {
        final Entity entity = findEntity(uuid);
        return entity instanceof EntityNecroBeam ? (EntityNecroBeam) entity : null;
    }

    private static boolean isEntityStillAlive(final UUID uuid) {
        final Entity entity = findEntity(uuid);
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        if (entity instanceof EntityNPCInterface && ((EntityNPCInterface) entity).isKilled()) {
            return false;
        }
        return true;
    }

    private static Entity findEntity(final UUID uuid) {
        if (uuid == null || ServerLifecycleHooks.getCurrentServer() == null) {
            return null;
        }
        for (final net.minecraft.world.server.ServerWorld level : ServerLifecycleHooks.getCurrentServer().getAllLevels()) {
            final Entity entity = level.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static UUID parseUuid(final ICustomNpc boss) {
        try {
            return UUID.fromString(String.valueOf(boss.getUUID()));
        } catch (final Exception ignored) {
            return null;
        }
    }

    private static UUID parseEntityUuid(final IEntity entity) {
        if (entity == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(entity.getUUID()));
        } catch (final Exception ignored) {
            return null;
        }
    }

    private static final class BossState {
        private final UUID bossUuid;
        private UUID beamUuid;
        private boolean stunLatched;
        private long stunUntilGameTime;
        private boolean savedAi;
        private int savedSpeed;
        private int savedRetaliate;
        private boolean ignoreSphereLoss;
        private final Set<UUID> knownSpheres = new HashSet<>();
        private final Set<UUID> countedSpheres = new HashSet<>();

        private BossState(final UUID bossUuid) {
            this.bossUuid = bossUuid;
        }
    }
}
