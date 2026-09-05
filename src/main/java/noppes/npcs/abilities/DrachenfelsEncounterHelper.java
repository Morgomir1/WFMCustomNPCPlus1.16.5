package noppes.npcs.abilities;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.potion.Effects;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import noppes.npcs.abilities.impl.CrimsonBlobAbility;
import noppes.npcs.abilities.impl.DfBlackSealAbility;
import noppes.npcs.abilities.impl.DfCarrierSlashAbility;
import noppes.npcs.abilities.impl.DfFalseHostAbility;
import noppes.npcs.abilities.impl.DfFeastSeatsAbility;
import noppes.npcs.abilities.impl.DfImperialPoisonAbility;
import noppes.npcs.abilities.impl.DfLeperBallAbility;
import noppes.npcs.abilities.impl.DfMaskGazeAbility;
import noppes.npcs.abilities.impl.DfNameStealAbility;
import noppes.npcs.abilities.impl.DfNamelessStepAbility;
import noppes.npcs.abilities.impl.DfNamelessWhisperAbility;
import noppes.npcs.abilities.impl.DfRepulseAbility;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.IEntityLiving;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.entity.data.IData;
import noppes.npcs.api.entity.data.INPCAi;
import noppes.npcs.api.entity.data.INPCDisplay;
import noppes.npcs.controllers.VisibilityController;
import noppes.npcs.entity.EntityAbilityZone;
import noppes.npcs.entity.EntityCloneStructureSpawner;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.script.ScriptDataUtil;
import noppes.npcs.zone.ZoneAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Solo Constant Drachenfels encounter: phases, queues, adds, desperation air.
 * Casting of boss spells goes through {@link AbilityAPI}; this helper decides when.
 */
public final class DrachenfelsEncounterHelper {
    public static final String BOSS_TAG = "df_constant";
    public static final String TAG_MONK = "df_monk";
    public static final String TAG_COURT = "df_court";
    public static final String TAG_CULTIST = "df_cultist";
    public static final String TAG_GUARD = "df_guard";
    public static final String TAG_PHANTOM = "df_leper";
    public static final String TAG_FALSE = "df_false";
    public static final String TAG_DESP_SHARD = "df_desp_shard";

    public static final double ARENA_RADIUS = 12.0;

    private static final double PHASE2_RATIO = 0.66;
    private static final double PHASE3_RATIO = 0.33;
    private static final double[] BELL_RATIOS = {0.88, 0.76};
    private static final double[] FALSE_RATIOS = {0.56, 0.46, 0.36};
    private static final double[] DESPERATION_RATIOS = {0.25, 0.15, 0.05};
    private static final double ABSORB_RATIO = 0.133;

    private static final int INVULN_TICKS = 60;
    private static final int SEAL_CD = 200;
    private static final int GAZE_CD = 160;
    private static final int REPULSE_CD = 200;
    private static final int BELL_CD = 400;
    private static final int COURT_CD = 320;
    private static final int GAZE_FAR_TICKS = 8;
    private static final int GAZE_RANGE = 5;
    private static final double REPULSE_TRIGGER = 4.0;
    private static final int STEP_CD = 120;
    private static final int WHISPER_CD = 180;
    private static final int STEAL_CD = 160;
    private static final int CYCLE_LENGTH = 360;
    private static final int FALSE_SHIFT = 30;
    private static final double FALSE_COPY_DIST_MIN = 4.0;
    private static final double FALSE_COPY_DIST_MAX = 10.0;
    private static final double FALSE_COPY_ANGLE_JITTER = 45.0;
    private static final double FALSE_HEAL_PER_COPY = 1.0;
    /** CNPC OnAttack: Panic — chaotic flee from target. */
    private static final int RETALIATE_PANIC = 1;
    private static final int FALSE_PANIC_SPEED = 6;
    private static final double SHARD_TOUCH_DIST = 2.75;
    private static final double DESP_AIR_HEIGHT = 5.0;
    private static final int DESP_RING_INTERVAL = 80;
    private static final int DESP_BLOB_CD = 30;
    private static final int DESP_STUN_TICKS = 200;
    private static final double DESP_SHARD_MIN_DIST = 10.0;
    private static final double DESP_SHARD_SPEED = 0.03;
    private static final double DESP_BLOB_RADIUS = 3.0;

    private static final String PHASE_KEY = "df_phase";
    private static final String ABSORB_KEY = "df_absorb";
    private static final String HOME_X = "df_home_x";
    private static final String HOME_Y = "df_home_y";
    private static final String HOME_Z = "df_home_z";
    private static final String INVULN_UNTIL = "df_invuln_until";
    private static final String TRANSITION = "df_transition";
    private static final String SEAL_READY = "df_seal_ready";
    private static final String GAZE_READY = "df_gaze_ready";
    private static final String REPULSE_READY = "df_repulse_ready";
    private static final String BELL_READY = "df_bell_ready";
    private static final String COURT_READY = "df_court_ready";
    private static final String GAZE_FAR_SINCE = "df_gaze_far";
    private static final String PUDDLE_TX = "df_puddle_tx";
    private static final String PUDDLE_TZ = "df_puddle_tz";
    private static final String PUDDLE_REPATH = "df_puddle_repath";
    private static final int PUDDLE_REPATH_TICKS = 20;
    private static final double PUDDLE_STEP = 0.35;
    private static final double PUDDLE_ARRIVE = 0.75;
    private static final String BELL_FIRED = "df_bell_fired";
    private static final String FALSE_FIRED = "df_false_fired";
    private static final String DESPERATION = "df_desperation";
    private static final String DESPERATION_FIRED = "df_desperation_fired";
    private static final String DESP_RING_AT = "df_desp_ring_at";
    private static final String DESP_RING_ALT = "df_desp_ring_alt";
    private static final String DESP_BLOB_AT = "df_desp_blob_at";
    private static final String DESP_STUN_UNTIL = "df_desp_stun_until";
    private static final String DESP_AIR_Y = "df_desp_air_y";
    private static final String DESP_SPAWNED = "df_desp_spawned";
    private static final String CYCLE_ORIGIN = "df_cycle_origin";
    private static final String CYCLE_SHIFT = "df_cycle_shift";
    private static final String CYCLE_SLOT = "df_cycle_slot";
    private static final String STEP_READY = "df_step_ready";
    private static final String WHISPER_READY = "df_whisper_ready";
    private static final String STEAL_READY = "df_steal_ready";
    private static final String PENDING_FALSE = "df_pending_false";
    private static final String FALSE_ACTIVE = "df_false_active";
    /** Saved {@link INPCDisplay#getVisible()} while false-host hide is active. */
    private static final String FALSE_PREV_VISIBLE = "df_false_prev_vis";
    private static final String FALSE_NEXT_PUDDLE = "df_false_puddle_at";
    /** Semicolon-separated EntityAbilityZone UUIDs left by this false copy. */
    private static final String FALSE_ZONES = "df_false_zones";
    /** Lifetime until explicit remove (illusion death / cleanup). */
    private static final int FALSE_PUDDLE_LIFETIME = 20 * 60 * 60; // 1h cap; cleared earlier on death
    private static final String SPIRIT_MODE = "df_spirit";
    private static final String QUOTE_INTRO = "df_quote_intro";
    /** One-shot guard: death cleanup may run from JS timer + died + Forge handler. */
    private static final String CLEANED = "df_cleaned";
    private static final String ARC_CD = "df_arc_cd";
    private static final int GUARD_SLASH_CD = 100;
    private static final String CLONE_TAB = "df_clone_tab";
    private static final String CLONE_MONK = "df_clone_monk";
    private static final String CLONE_CULTIST = "df_clone_cultist";
    private static final String CLONE_GUARD = "df_clone_guard";
    private static final String CLONE_PHANTOM = "df_clone_phantom";
    private static final String CLONE_FALSE = "df_clone_false";
    private static final String CLONE_SHARD = "df_clone_shard";
    private static final String BOSS_UUID = "df_boss_uuid";
    private static final String PHANTOM_LIFE = "df_phantom_life";
    private static final String ADD_NEXT_ATK = "df_add_atk";
    private static final String INITED = "df_inited";
    private static final String LAST_TICK = "df_tick_at";

    private static final Random RANDOM = new Random();

    private DrachenfelsEncounterHelper() {
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public static void init(final ICustomNpc npc) {
        if (npc == null || isClient(npc)) {
            return;
        }
        final IData data = npc.getStoreddata();
        if (!npc.hasTag(BOSS_TAG)) {
            npc.addTag(BOSS_TAG);
        }
        // JS init / chunk reload must not rewind CDs mid-fight.
        if (ScriptDataUtil.isFlag(data, INITED)) {
            return;
        }
        releaseDesperationFlight(npc);
        ScriptDataUtil.setFlag(data, INITED, true);
        ScriptDataUtil.setFlag(data, CLEANED, false);
        put(data, HOME_X, npc.getX());
        put(data, HOME_Y, npc.getY());
        put(data, HOME_Z, npc.getZ());
        put(data, PHASE_KEY, "1");
        put(data, ABSORB_KEY, "0");
        put(data, INVULN_UNTIL, "0");
        put(data, TRANSITION, "0");
        put(data, BELL_FIRED, "");
        put(data, FALSE_FIRED, "");
        put(data, DESPERATION, "0");
        put(data, DESPERATION_FIRED, "");
        put(data, DESP_RING_AT, "0");
        put(data, DESP_RING_ALT, "0");
        put(data, DESP_BLOB_AT, "0");
        put(data, DESP_STUN_UNTIL, "0");
        put(data, DESP_AIR_Y, "0");
        put(data, DESP_SPAWNED, "0");
        put(data, CYCLE_ORIGIN, "0");
        put(data, CYCLE_SHIFT, "0");
        put(data, CYCLE_SLOT, "0");
        put(data, SPIRIT_MODE, "0");
        put(data, PENDING_FALSE, "0");
        put(data, FALSE_ACTIVE, "0");
        final long now = now(npc);
        put(data, SEAL_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "sealFirstDelay", 40)));
        put(data, GAZE_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "gazeFirstDelay", 80)));
        put(data, REPULSE_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "repulseFirstDelay", 60)));
        put(data, BELL_READY, "0");
        put(data, COURT_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "courtFirstDelay", 80)));
        put(data, GAZE_FAR_SINCE, "0");
        put(data, PUDDLE_TX, "");
        put(data, PUDDLE_TZ, "");
        put(data, PUDDLE_REPATH, "0");
        put(data, STEP_READY, "0");
        put(data, WHISPER_READY, "0");
        put(data, STEAL_READY, "0");
        restoreFullHealth(npc);
        applyPhase1Ai(npc);
        if (!ScriptDataUtil.isFlag(data, QUOTE_INTRO)) {
            say(npc, "Этот замок помнит вас дольше, чем вы — себя.");
            ScriptDataUtil.setFlag(data, QUOTE_INTRO, true);
        }
    }

    public static void configureClones(
            final ICustomNpc npc,
            final int tab,
            final String monk,
            final String cultist,
            final String guard,
            final String phantom,
            final String falseHost,
            final String shard) {
        if (npc == null) {
            return;
        }
        final IData data = npc.getStoreddata();
        put(data, CLONE_TAB, String.valueOf(tab));
        put(data, CLONE_MONK, monk);
        put(data, CLONE_CULTIST, cultist);
        put(data, CLONE_GUARD, guard);
        put(data, CLONE_PHANTOM, phantom);
        put(data, CLONE_FALSE, falseHost);
        put(data, CLONE_SHARD, shard);
    }

    /**
     * JS (Nashorn-safe): {@code Encounter.configure(npc, AbilityAPI.params("sealDamage", 12, ...))}
     * Do not pass bare varargs after npc — Nashorn packs them into one Object[].
     */
    public static void configure(final ICustomNpc npc, final Map<String, Object> keyValues) {
        DrachenfelsConfig.configure(npc, keyValues);
    }

    public static void tick(final ICustomNpc npc) {
        if (npc == null || !npc.isAlive() || isClient(npc)) {
            return;
        }
        // After death cleanup clears df_inited; re-init even if boss tag persisted across respawn.
        if (!isBoss(npc) || !ScriptDataUtil.isFlag(npc.getStoreddata(), INITED)) {
            init(npc);
        }
        final IData data = npc.getStoreddata();
        final long now = now(npc);
        if (now > 0L && ScriptDataUtil.getLong(data, LAST_TICK) == now) {
            return;
        }
        put(data, LAST_TICK, String.valueOf(now));
        tickTransition(npc, data, now);
        if (ScriptDataUtil.isFlag(data, TRANSITION)) {
            return;
        }
        enforcePhaseCap(npc, data);
        if (!isDesperationActive(data) && !isDesperationStunned(data, now)) {
            updatePhase(npc, data, now);
        }
        tickAbsorbVfx(npc, now);
        if (hasLivingFalseHosts(npc)) {
            hideBossForFalseHost(npc);
        } else if (ScriptDataUtil.isFlag(data, FALSE_ACTIVE)
                && !DfFalseHostAbility.ID.equals(AbilityAPI.getActiveId(npc))) {
            restoreBossAfterFalseHost(npc);
        }

        final int phase = ScriptDataUtil.getInt(data, PHASE_KEY);
        if (phase == 3 && !isDesperationActive(data) && !isDesperationStunned(data, now)) {
            tickDesperationTrigger(npc, data, now);
        }
        if (isDesperationActive(data)) {
            resolveCombatTarget(npc);
            tickDesperation(npc, data, now);
            return;
        }
        if (isDesperationStunned(data, now)) {
            tickDesperationStun(npc, data, now);
            return;
        }

        // No survival/adventure players in engage range: stop casts, drop aggro.
        if (!hasNearbyPlayers(npc)) {
            if (AbilityAPI.isBusy(npc)) {
                AbilityAPI.cancel(npc);
            }
            clearAttackTarget(npc);
            tickAdds(npc, data, now);
            tickFalseHosts(npc, data, now);
            tickPhantoms(npc, data);
            return;
        }
        // Keep CNPC AI target on survival/adventure only (drop creative if AI picked them).
        resolveCombatTarget(npc);
        tickAdds(npc, data, now);
        tickFalseHosts(npc, data, now);
        tickPhantoms(npc, data);
        if (phase == 1) {
            tickPhase1(npc, data, now);
        } else if (phase == 2) {
            tickPhase2(npc, data, now);
        } else if (phase == 3) {
            tickPhase3(npc, data, now);
        }
    }

    public static void cleanup(final ICustomNpc npc) {
        if (npc == null) {
            return;
        }
        final IData data = npc.getStoreddata();
        if (ScriptDataUtil.isFlag(data, CLEANED)) {
            return;
        }
        ScriptDataUtil.setFlag(data, CLEANED, true);
        AbilityAPI.cancel(npc);
        releaseDesperationFlight(npc);
        restoreBossAfterFalseHost(npc);
        killTaggedNear(npc, TAG_MONK, TAG_COURT, TAG_CULTIST, TAG_GUARD,
                TAG_PHANTOM, TAG_FALSE, TAG_DESP_SHARD);
        clearOwnerZones(npc);
        // Allow full re-init on CNPC respawn (phase/HP must not stick from previous fight).
        ScriptDataUtil.setFlag(data, INITED, false);
        say(npc, "Замок не умрёт с этим телом.");
    }

    public static void onAbilityEnded(final ICustomNpc npc, final String abilityId) {
        if (npc == null || abilityId == null) {
            return;
        }
        final IData data = npc.getStoreddata();
        // Desperation ring finished → arm 4s gap before next ring (blobs fill the gap).
        if (ScriptDataUtil.isFlag(data, DESPERATION)
                && (DfImperialPoisonAbility.ID.equals(abilityId)
                || DfNamelessWhisperAbility.ID.equals(abilityId))) {
            put(data, DESP_RING_AT, String.valueOf(now(npc)
                    + DrachenfelsConfig.getI(data, "despRingInterval", DESP_RING_INTERVAL)));
        }
        // Ability CDs are armed on cast start (startBossAbility) so intervals match configured CD.
        if (DfFalseHostAbility.ID.equals(abilityId)) {
            final int shift = ScriptDataUtil.getInt(data, CYCLE_SHIFT);
            put(data, CYCLE_SHIFT, String.valueOf(shift + DrachenfelsConfig.getI(data, "falseShift", FALSE_SHIFT)));
            put(data, PENDING_FALSE, "0");
        }
    }

    public static boolean isBoss(final ICustomNpc npc) {
        return npc != null && npc.hasTag(BOSS_TAG);
    }

    public static boolean isInvulnerable(final ICustomNpc npc) {
        if (npc == null) {
            return false;
        }
        final IData data = npc.getStoreddata();
        final long now = now(npc);
        return now < ScriptDataUtil.getLong(data, INVULN_UNTIL)
                || ScriptDataUtil.isFlag(data, TRANSITION)
                || ScriptDataUtil.isFlag(data, FALSE_ACTIVE)
                || ScriptDataUtil.isFlag(data, DESPERATION)
                || now < ScriptDataUtil.getLong(data, DESP_STUN_UNTIL);
    }

    public static float getAbsorb(final ICustomNpc npc) {
        return ScriptDataUtil.getFloat(npc.getStoreddata(), ABSORB_KEY);
    }

    public static void setAbsorb(final ICustomNpc npc, final float value) {
        put(npc.getStoreddata(), ABSORB_KEY, String.valueOf(Math.max(0.0F, value)));
    }

    /** Particle ring while Bell absorb shield is up (boss + phase-1 Court allies). */
    private static void tickAbsorbVfx(final ICustomNpc npc, final long now) {
        // Every other tick keeps it readable without flooding packets.
        if ((now & 1L) != 0L) {
            return;
        }
        tickAbsorbVfxOn(npc);
        for (final ICustomNpc ally : findTagged(npc, TAG_COURT)) {
            if (ally != null && ally.isAlive()) {
                tickAbsorbVfxOn(ally);
            }
        }
    }

    private static void tickAbsorbVfxOn(final ICustomNpc npc) {
        if (npc == null || getAbsorb(npc) <= 0.01F) {
            return;
        }
        AbilityVfx.spawnAbsorbShield(npc.getWorld(), npc.getX(), npc.getY(), npc.getZ());
    }

    /** True for phase-1 Court adds that can wear Bell absorb (not the monk). */
    public static boolean isBellShieldAlly(final ICustomNpc npc) {
        return npc != null && npc.hasTag(TAG_COURT);
    }

    /** Clears Bell absorb on boss and all living Court allies. */
    public static void clearBellAbsorb(final ICustomNpc boss) {
        if (boss == null) {
            return;
        }
        setAbsorb(boss, 0.0F);
        for (final ICustomNpc ally : findTagged(boss, TAG_COURT)) {
            if (ally != null) {
                setAbsorb(ally, 0.0F);
            }
        }
    }


    public static boolean hasLivingFalseHosts(final ICustomNpc npc) {
        return countTaggedNear(npc, TAG_FALSE) > 0;
    }

    public static float phaseHpCap(final ICustomNpc npc) {
        final IData data = npc.getStoreddata();
        final int phase = ScriptDataUtil.getInt(data, PHASE_KEY);
        final float max = (float) npc.getMaxHealth();
        if (phase == 2) {
            return max * (float) DrachenfelsConfig.getD(data, "phase2Ratio", PHASE2_RATIO);
        }
        if (phase >= 3) {
            return max * (float) DrachenfelsConfig.getD(data, "phase3Ratio", PHASE3_RATIO);
        }
        return 0.0F;
    }

    public static double getArenaRadius(final ICustomNpc npc) {
        return DrachenfelsConfig.getD(npc, "arenaRadius", ARENA_RADIUS);
    }

    public static double[] getArenaCenter(final ICustomNpc npc) {
        final IData data = npc.getStoreddata();
        return new double[]{
                ScriptDataUtil.getFloat(data, HOME_X),
                ScriptDataUtil.getFloat(data, HOME_Y),
                ScriptDataUtil.getFloat(data, HOME_Z)
        };
    }

    public static void onMonkDeath(final ICustomNpc monk) {
        clearBellAbsorb(findBossForAdd(monk));
    }

    public static void onDespShardDeath(final ICustomNpc shard) {
        final ICustomNpc boss = findBossForAdd(shard);
        if (boss == null) {
            return;
        }
        final IData data = boss.getStoreddata();
        if (!ScriptDataUtil.isFlag(data, DESPERATION)) {
            return;
        }
        // Dying shard may still be counted until removed — treat as already dead.
        int alive = 0;
        final String dyingUuid = String.valueOf(shard.getUUID());
        for (final ICustomNpc s : findTagged(boss, TAG_DESP_SHARD)) {
            if (s != null && s.isAlive() && !dyingUuid.equals(String.valueOf(s.getUUID()))) {
                alive++;
            }
        }
        if (alive <= 0) {
            succeedDesperation(boss, data, now(boss));
        }
    }

    /** Cardinal / diagonal / half-diagonal salvoes — 4 spirits each. */
    private static final double[][] LEPER_VOLLEY_ANGLES = {
            {0.0, 90.0, 180.0, 270.0},
            {45.0, 135.0, 225.0, 315.0},
            {22.5, 112.5, 202.5, 292.5}
    };

    public static double[][] leperSpawnPoints(final ICustomNpc boss) {
        return leperSpawnPoints(boss, 0);
    }

    /** Spawn points near the boss for telegraph / volley {@code volleyIndex}. */
    public static double[][] leperSpawnPoints(final ICustomNpc boss, final int volleyIndex) {
        final double[] angles = leperVolleyAngles(volleyIndex);
        final double startR = DrachenfelsConfig.getD(boss, "leperStartRadius", 1.5);
        final double bx = boss.getX();
        final double by = boss.getY();
        final double bz = boss.getZ();
        final double[][] out = new double[angles.length][3];
        for (int i = 0; i < angles.length; i++) {
            final double rad = Math.toRadians(angles[i]);
            final double x = bx + Math.cos(rad) * startR;
            final double z = bz + Math.sin(rad) * startR;
            final double y = AbilityCombatHelper.findGroundY(boss.getWorld(), x, z, by)
                    + DrachenfelsConfig.getD(boss, "leperHover", 1.0);
            out[i][0] = x;
            out[i][1] = y;
            out[i][2] = z;
        }
        return out;
    }

    private static double[] leperVolleyAngles(final int volleyIndex) {
        final int idx = Math.floorMod(volleyIndex, LEPER_VOLLEY_ANGLES.length);
        return LEPER_VOLLEY_ANGLES[idx];
    }

    public static void spawnLeperPhantoms(
            final ICustomNpc boss, final int tab, final String cloneName, final double damage) {
        spawnLeperVolley(boss, tab, cloneName, damage, 0);
    }

    public static void spawnLeperPhantoms(
            final ICustomNpc boss,
            final int tab,
            final String cloneName,
            final double damage,
            final List<double[]> markers) {
        // Legacy single-salvo path: treat markers as starts of volley 0 if provided.
        if (markers != null && !markers.isEmpty()) {
            spawnLeperVolley(boss, tab, cloneName, damage, 0);
            return;
        }
        spawnLeperVolley(boss, tab, cloneName, damage, 0);
    }

    /** Spawn one outward salvo of leper spirits (away from the boss). */
    public static void spawnLeperVolley(
            final ICustomNpc boss,
            final int tab,
            final String cloneName,
            final double damage,
            final int volleyIndex) {
        final int life = DrachenfelsConfig.getI(boss, "leperDuration", 70);
        final double startR = DrachenfelsConfig.getD(boss, "leperStartRadius", 1.5);
        final double endR = DrachenfelsConfig.getD(boss, "leperSpawnRadius", 24.0);
        final double hover = DrachenfelsConfig.getD(boss, "leperHover", 1.0);
        final double hitR = DrachenfelsConfig.getD(boss, "leperHitRadius", 1.5);
        final int zoneColor = DrachenfelsConfig.getI(boss, "telegraphColor", 0xC0FF3030);
        final int slowDur = DrachenfelsConfig.getI(boss, "leperSlowDuration", 30);
        final int slowAmp = DrachenfelsConfig.getI(boss, "leperSlowAmp", 1);
        final double[] angles = leperVolleyAngles(volleyIndex);
        final float phantomHp = (float) DrachenfelsConfig.getD(boss, "leperHp", 1.0);
        final double bx = boss.getX();
        final double by = boss.getY();
        final double bz = boss.getZ();
        for (int i = 0; i < angles.length; i++) {
            final double rad = Math.toRadians(angles[i]);
            final double cos = Math.cos(rad);
            final double sin = Math.sin(rad);
            final double sx = bx + cos * startR;
            final double sz = bz + sin * startR;
            final double ex = bx + cos * endR;
            final double ez = bz + sin * endR;
            final double baseY = AbilityCombatHelper.findGroundY(boss.getWorld(), sx, sz, by);
            final double sy = baseY + hover;
            final ICustomNpc phantom = spawnClone(boss, tab, cloneName, sx, sy, sz);
            if (phantom == null) {
                continue;
            }
            tagAdd(phantom, TAG_PHANTOM);
            final IData pd = phantom.getStoreddata();
            put(pd, BOSS_UUID, String.valueOf(boss.getUUID()));
            put(pd, PHANTOM_LIFE, String.valueOf(life));
            put(pd, "df_dmg", String.valueOf(damage));
            put(pd, HOME_X, String.valueOf(sx));
            put(pd, HOME_Y, String.valueOf(sy));
            put(pd, HOME_Z, String.valueOf(sz));
            put(pd, "df_ex", String.valueOf(ex));
            put(pd, "df_ez", String.valueOf(ez));
            put(pd, "df_ang", String.valueOf(angles[i]));
            put(pd, "df_hover", String.valueOf(hover));
            put(pd, "df_base_y", String.valueOf(baseY));
            setAiNone(phantom);
            applyAddHp(phantom, phantomHp);
            pinFlyingNpc(phantom, sx, sy, sz);

            // Red ground hazard follows the spirit (owner = boss for hostile checks).
            final EntityAbilityZone zone = ZoneAPI.hazardCircle(
                    boss, sx, baseY + 0.05, sz, hitR, life + 10, damage, 5);
            if (zone != null) {
                zone.setColor(zoneColor);
                zone.setZoneHeight(2.0f);
                zone.setVisible(true);
                zone.setGroundFill(true);
                zone.setBorder(true);
                zone.setEffect("minecraft:slowness", slowDur, slowAmp);
                put(pd, "df_zone", String.valueOf(zone.getUUID()));
            }
        }
    }

    /** Markers: 3 copy positions + 1 teleport landing. */
    public static void planFalseHost(final ICustomNpc boss, final List<double[]> markers) {
        markers.clear();
        final double[] c = getArenaCenter(boss);
        final double arenaCap = Math.max(1.0, getArenaRadius(boss) - 0.5);
        final double legacyDist = DrachenfelsConfig.getD(boss, "falseCopyDist", FALSE_COPY_DIST_MIN);
        final double distMin = Math.min(
                arenaCap,
                Math.max(1.0, DrachenfelsConfig.getD(boss, "falseCopyDistMin", legacyDist)));
        final double distMax = Math.min(
                arenaCap,
                Math.max(distMin, DrachenfelsConfig.getD(boss, "falseCopyDistMax", FALSE_COPY_DIST_MAX)));
        final double jitter = Math.max(
                0.0, DrachenfelsConfig.getD(boss, "falseCopyAngleJitter", FALSE_COPY_ANGLE_JITTER));
        final double baseAng = RANDOM.nextDouble() * 360.0;
        for (int i = 0; i < 3; i++) {
            final double ang = baseAng + 120.0 * i + (RANDOM.nextDouble() * 2.0 - 1.0) * jitter;
            final double dist = distMin + RANDOM.nextDouble() * Math.max(0.0, distMax - distMin);
            final double rad = Math.toRadians(ang);
            final double x = c[0] + Math.cos(rad) * dist;
            final double z = c[2] + Math.sin(rad) * dist;
            final double y = AbilityCombatHelper.findGroundY(boss.getWorld(), x, z, c[1]);
            markers.add(new double[]{x, y, z});
        }
        final double ring = DrachenfelsConfig.getD(boss, "falseTeleportRing", 5.0);
        final double ang = RANDOM.nextDouble() * Math.PI * 2.0;
        final double x = c[0] + Math.cos(ang) * ring;
        final double z = c[2] + Math.sin(ang) * ring;
        final double y = AbilityCombatHelper.findGroundY(boss.getWorld(), x, z, c[1]);
        markers.add(new double[]{x, y, z});
    }

    public static int executeFalseHost(
            final ICustomNpc boss,
            final int tab,
            final String cloneName,
            final List<double[]> markers) {
        final List<double[]> pts = markers == null ? new ArrayList<>() : markers;
        if (pts.size() < 4) {
            planFalseHost(boss, pts);
        }
        final int copyCount = Math.min(3, pts.size() - 1);
        int spawned = 0;
        for (int i = 0; i < copyCount; i++) {
            final double[] m = pts.get(i);
            final ICustomNpc copy = spawnClone(boss, tab, cloneName, m[0], m[1], m[2]);
            if (copy == null) {
                continue;
            }
            tagAdd(copy, TAG_FALSE);
            put(copy.getStoreddata(), BOSS_UUID, String.valueOf(boss.getUUID()));
            applyAddHp(copy, (float) DrachenfelsConfig.getD(boss, "falseCloneHp", 50.0));
            applyFalseCopyAi(copy, boss.getStoreddata());
            put(copy.getStoreddata(), FALSE_NEXT_PUDDLE, "0");
            put(copy.getStoreddata(), FALSE_ZONES, "");
            spawned++;
        }
        if (spawned <= 0) {
            return 0;
        }
        final double[] land = pts.get(pts.size() - 1);
        boss.setPosition(land[0], land[1], land[2]);
        hideBossForFalseHost(boss);
        return spawned;
    }

    public static void castFalseHost(final ICustomNpc boss, final int tab, final String cloneName) {
        final List<double[]> markers = new ArrayList<>();
        planFalseHost(boss, markers);
        executeFalseHost(boss, tab, cloneName, markers);
    }

    public static void despawnFalseHosts(final ICustomNpc boss) {
        killTaggedNear(boss, TAG_FALSE);
    }

    public static void hideBossForFalseHost(final ICustomNpc boss) {
        if (boss == null) {
            return;
        }
        final IData data = boss.getStoreddata();
        final boolean alreadyHidden = ScriptDataUtil.isFlag(data, FALSE_ACTIVE);
        put(data, FALSE_ACTIVE, "1");
        clearAttackTarget(boss);
        AbilityCombatHelper.stopNavigation(boss);
        try {
            // CNPC overrides Entity.isInvisible() from Display Visible — vanilla
            // setInvisible(true) does not hide the model on clients (esp. dedicated).
            boolean needRefresh = !alreadyHidden;
            final INPCDisplay display = boss.getDisplay();
            if (display != null) {
                if (!alreadyHidden) {
                    put(data, FALSE_PREV_VISIBLE, String.valueOf(display.getVisible()));
                }
                if (display.getVisible() != 1) {
                    display.setVisible(1);
                    needRefresh = true;
                }
            }
            final Object mc = boss.getMCEntity();
            if (mc instanceof Entity) {
                final Entity entity = (Entity) mc;
                entity.setInvulnerable(true);
                entity.setDeltaMovement(0.0, 0.0, 0.0);
                entity.fallDistance = 0.0F;
                if (needRefresh && entity instanceof EntityNPCInterface) {
                    refreshSoftVisibility((EntityNPCInterface) entity);
                }
            }
        } catch (final Exception ignored) {
        }
    }

    public static void restoreBossAfterFalseHost(final ICustomNpc boss) {
        if (boss == null) {
            return;
        }
        final IData data = boss.getStoreddata();
        put(data, FALSE_ACTIVE, "0");
        try {
            final INPCDisplay display = boss.getDisplay();
            final boolean hadPrev = data.has(FALSE_PREV_VISIBLE);
            if (display != null && hadPrev) {
                display.setVisible(ScriptDataUtil.getInt(data, FALSE_PREV_VISIBLE));
                data.remove(FALSE_PREV_VISIBLE);
            }
            final Object mc = boss.getMCEntity();
            if (mc instanceof Entity) {
                final Entity entity = (Entity) mc;
                entity.setInvulnerable(false);
                entity.fallDistance = 0.0F;
                if (hadPrev && entity instanceof EntityNPCInterface) {
                    refreshSoftVisibility((EntityNPCInterface) entity);
                }
            }
        } catch (final Exception ignored) {
        }
    }

    /** Push Display Visible / soft-hide packets to nearby players immediately. */
    private static void refreshSoftVisibility(final EntityNPCInterface npc) {
        if (npc == null || npc.level == null || npc.level.isClientSide) {
            return;
        }
        VisibilityController.instance.trackNpc(npc);
        if (!(npc.level instanceof ServerWorld)) {
            return;
        }
        final ServerWorld world = (ServerWorld) npc.level;
        final AxisAlignedBB box = npc.getBoundingBox().inflate(64.0);
        for (final ServerPlayerEntity player : world.getEntitiesOfClass(ServerPlayerEntity.class, box)) {
            VisibilityController.checkIsVisible(npc, player);
        }
    }

    public static boolean pickNamelessStepTarget(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return false;
        }
        final double[] c = getArenaCenter(ctx.npc);
        final double arenaR = Math.max(1.0, getArenaRadius(ctx.npc) - 0.5);
        final double sx = ctx.npc.getX();
        final double sy = ctx.npc.getY();
        final double sz = ctx.npc.getZ();
        final double px = ctx.target.getX();
        final double pz = ctx.target.getZ();
        double dx = px - sx;
        double dz = pz - sz;
        double len = Math.sqrt(dx * dx + dz * dz);
        final double overshoot = Math.max(0.0, DrachenfelsConfig.getD(ctx.npc, "stepOvershoot", 1.5));
        double x;
        double z;
        if (len < 0.05) {
            final double yaw = Math.toRadians(ctx.npc.getRotation() + 90.0);
            dx = Math.cos(yaw);
            dz = Math.sin(yaw);
            len = 1.0;
            x = sx + dx * (2.0 + overshoot);
            z = sz + dz * (2.0 + overshoot);
        } else {
            dx /= len;
            dz /= len;
            x = px + dx * overshoot;
            z = pz + dz * overshoot;
        }
        final double fromCenter = Math.sqrt((x - c[0]) * (x - c[0]) + (z - c[2]) * (z - c[2]));
        if (fromCenter > arenaR) {
            x = c[0] + ((x - c[0]) / fromCenter) * arenaR;
            z = c[2] + ((z - c[2]) / fromCenter) * arenaR;
        }
        active.sx = sx;
        active.sy = sy;
        active.sz = sz;
        active.ex = x;
        active.ez = z;
        active.ey = AbilityCombatHelper.findGroundY(ctx.world, x, z, c[1]);
        active.yaw = AbilityCombatHelper.computeYaw(active.ex - active.sx, active.ez - active.sz);
        return true;
    }

    // -------------------------------------------------------------------------
    // Phase logic
    // -------------------------------------------------------------------------

    private static void updatePhase(final ICustomNpc npc, final IData data, final long now) {
        final int phase = ScriptDataUtil.getInt(data, PHASE_KEY);
        final double ratio = hpRatio(npc);
        if (phase == 1 && ratio <= DrachenfelsConfig.getD(data, "phase2Ratio", PHASE2_RATIO)) {
            beginTransition(npc, data, now, 2);
        } else if (phase == 2 && ratio <= DrachenfelsConfig.getD(data, "phase3Ratio", PHASE3_RATIO)) {
            beginTransition(npc, data, now, 3);
        }
    }

    private static void beginTransition(
            final ICustomNpc npc, final IData data, final long now, final int nextPhase) {
        AbilityAPI.cancel(npc);
        put(data, TRANSITION, "1");
        final int invuln = DrachenfelsConfig.getI(data, "invulnTicks", INVULN_TICKS);
        put(data, INVULN_UNTIL, String.valueOf(now + invuln));
        put(data, PHASE_KEY, String.valueOf(nextPhase));
        put(data, ABSORB_KEY, "0");
        put(data, PENDING_FALSE, "0");
        put(data, FALSE_ACTIVE, "0");
        put(data, DESPERATION, "0");
        put(data, DESP_STUN_UNTIL, "0");
        put(data, DESP_SPAWNED, "0");
        clearBellAbsorb(npc);
        restoreBossAfterFalseHost(npc);
        killTaggedNear(npc, TAG_MONK, TAG_COURT, TAG_CULTIST, TAG_GUARD,
                TAG_PHANTOM, TAG_FALSE, TAG_DESP_SHARD);
        clearOwnerZones(npc);
        final double[] c = getArenaCenter(npc);
        final double y = AbilityCombatHelper.findGroundY(npc.getWorld(), c[0], c[2], c[1]);
        npc.setPosition(c[0], y, c[2]);
        if (nextPhase == 2) {
            say(npc, "Садитесь. Пир уже накрыт.");
            applyPhase2Ai(npc);
        } else if (nextPhase == 3) {
            say(npc, "Тело — лишь маска. Имя остаётся.");
            put(data, SPIRIT_MODE, "1");
            applySpiritAi(npc);
        }
        put(data, "df_next_phase_ready", String.valueOf(now + invuln));
    }

    private static void tickTransition(final ICustomNpc npc, final IData data, final long now) {
        if (!ScriptDataUtil.isFlag(data, TRANSITION)) {
            return;
        }
        if (now < ScriptDataUtil.getLong(data, "df_next_phase_ready")) {
            AbilityCombatHelper.holdInPlace(npc, npc.getX(), npc.getY(), npc.getZ());
            return;
        }
        put(data, TRANSITION, "0");
        final int phase = ScriptDataUtil.getInt(data, PHASE_KEY);
        if (phase == 2) {
            put(data, CYCLE_ORIGIN, String.valueOf(now));
            put(data, CYCLE_SHIFT, "0");
            put(data, CYCLE_SLOT, "0");
        } else if (phase == 3) {
            put(data, STEP_READY, String.valueOf(now + 40));
            put(data, WHISPER_READY, String.valueOf(now + 60));
            put(data, STEAL_READY, String.valueOf(now + 40));
        }
    }

    private static void enforcePhaseCap(final ICustomNpc npc, final IData data) {
        final float cap = phaseHpCap(npc);
        if (cap <= 0.0F) {
            return;
        }
        if (npc.getHealth() > cap + 0.05F) {
            try {
                final Object mc = npc.getMCEntity();
                if (mc instanceof LivingEntity) {
                    ((LivingEntity) mc).setHealth(cap);
                }
            } catch (final Exception ignored) {
            }
        }
    }

    private static void tickPhase1(final ICustomNpc npc, final IData data, final long now) {
        tickBell(npc, data, now);
        if (!AbilityAPI.isBusy(npc)) {
            final IEntityLiving target = resolveCombatTarget(npc);
            if (target != null && target.isAlive()) {
                // Distance gates must not idle the rotation: skip Gaze/Repulse instantly
                // if their window is closed, then Court + Seal (Seal has no range gate).
                if (!tryGaze(npc, data, now, target)
                        && !tryRepulse(npc, data, now, target)) {
                    tryCourt(npc, data, now);
                    if (now >= ScriptDataUtil.getLong(data, SEAL_READY)) {
                        startBossAbility(
                                npc, DfBlackSealAbility.ID, target, DrachenfelsConfig.sealParams(npc));
                    }
                }
            }
        }
        maintainPhase1Movement(npc, data, now);
    }

    private static void tickBell(final ICustomNpc npc, final IData data, final long now) {
        if (now < ScriptDataUtil.getLong(data, BELL_READY)) {
            return;
        }
        final double ratio = hpRatio(npc);
        final String fired = str(data, BELL_FIRED);
        final double[] bellRatios = DrachenfelsConfig.getRatios(data, "bellRatios", BELL_RATIOS);
        for (int i = 0; i < bellRatios.length; i++) {
            final String mark = DrachenfelsConfig.mark(bellRatios[i]);
            if (fired.contains(mark)) {
                continue;
            }
            if (ratio > bellRatios[i]) {
                continue;
            }
            put(data, BELL_FIRED, fired.isEmpty() ? mark : fired + ";" + mark);
            put(data, BELL_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "bellCd", BELL_CD)));
            sayAbilityQuote(npc, "df_bell");
            applyBellShield(npc, data);
            AbilityVfx.spawnAbsorbShield(npc.getWorld(), npc.getX(), npc.getY(), npc.getZ());
            AbilityVfx.spawnSoulWave(npc.getWorld(), npc.getX(), npc.getY() + 0.2, npc.getZ(), 2.2);
            return;
        }
    }

    private static void applyBellShield(final ICustomNpc npc, final IData data) {
        final float absorb = (float) (npc.getMaxHealth()
                * DrachenfelsConfig.getD(data, "absorbRatio", ABSORB_RATIO));
        setAbsorb(npc, absorb);
        // Same Bell shield on living Court adds (cultists / guards), not the monk.
        for (final ICustomNpc ally : findTagged(npc, TAG_COURT)) {
            if (ally == null || !ally.isAlive()) {
                continue;
            }
            setAbsorb(ally, absorb);
            AbilityVfx.spawnAbsorbShield(ally.getWorld(), ally.getX(), ally.getY(), ally.getZ());
        }
        ICustomNpc monk = findFirstTagged(npc, TAG_MONK);
        if (monk == null) {
            final int tab = ScriptDataUtil.getInt(data, CLONE_TAB);
            final String name = str(data, CLONE_MONK);
            if (name.isEmpty()) {
                return;
            }
            final double x = npc.getX() + 1.5;
            final double z = npc.getZ() + 1.5;
            final double y = AbilityCombatHelper.findGroundY(npc.getWorld(), x, z, npc.getY());
            monk = spawnClone(npc, tab <= 0 ? 1 : tab, name, x, y, z);
            if (monk == null) {
                return;
            }
            tagAdd(monk, TAG_MONK);
            put(monk.getStoreddata(), BOSS_UUID, String.valueOf(npc.getUUID()));
            setAiNone(monk);
            applyAddHp(monk, (float) DrachenfelsConfig.getD(data, "monkHp", 40.0));
        }
    }

    /** If Bell shield is still up (monk alive), new Court adds join under the same absorb. */
    private static void maybeShareBellAbsorb(final ICustomNpc boss, final ICustomNpc add) {
        if (boss == null || add == null || getAbsorb(boss) <= 0.01F) {
            return;
        }
        if (findFirstTagged(boss, TAG_MONK) == null) {
            return;
        }
        setAbsorb(add, getAbsorb(boss));
        AbilityVfx.spawnAbsorbShield(add.getWorld(), add.getX(), add.getY(), add.getZ());
    }

    private static boolean tryGaze(
            final ICustomNpc npc, final IData data, final long now, final IEntityLiving target) {
        final double dist = AbilityCombatHelper.flatDistance(
                npc.getX(), npc.getZ(), target.getX(), target.getZ());
        final double gazeRange = effectiveGazeRange(data);
        if (dist > gazeRange) {
            if (ScriptDataUtil.getLong(data, GAZE_FAR_SINCE) <= 0L) {
                put(data, GAZE_FAR_SINCE, String.valueOf(now));
            }
        } else {
            put(data, GAZE_FAR_SINCE, "0");
            return false;
        }
        final long since = ScriptDataUtil.getLong(data, GAZE_FAR_SINCE);
        int farTicks = DrachenfelsConfig.getI(data, "gazeFarTicks", GAZE_FAR_TICKS);
        if (farTicks > 12) {
            farTicks = GAZE_FAR_TICKS;
        }
        if (since <= 0L || now - since < farTicks) {
            return false;
        }
        if (now < ScriptDataUtil.getLong(data, GAZE_READY)) {
            return false;
        }
        return startBossAbility(npc, DfMaskGazeAbility.ID, target, DrachenfelsConfig.gazeParams(npc));
    }

    private static boolean tryRepulse(
            final ICustomNpc npc, final IData data, final long now, final IEntityLiving target) {
        if (now < ScriptDataUtil.getLong(data, REPULSE_READY)) {
            return false;
        }
        final double trigger = DrachenfelsConfig.getD(data, "repulseTrigger", REPULSE_TRIGGER);
        final double dist = AbilityCombatHelper.flatDistance(
                npc.getX(), npc.getZ(), target.getX(), target.getZ());
        if (dist > trigger) {
            return false;
        }
        return startBossAbility(npc, DfRepulseAbility.ID, target, DrachenfelsConfig.repulseParams(npc));
    }

    private static boolean tryCourt(final ICustomNpc npc, final IData data, final long now) {
        if (now < ScriptDataUtil.getLong(data, COURT_READY)) {
            return false;
        }
        final int court = countTaggedNear(npc, TAG_COURT);
        final int monk = countTaggedNear(npc, TAG_MONK);
        if (court >= 2 || court + monk >= 3) {
            // Keep trying next ticks — do not burn CD when at escort cap.
            return false;
        }
        final int tab = ScriptDataUtil.getInt(data, CLONE_TAB);
        final boolean cultist = RANDOM.nextBoolean();
        final String name = cultist ? str(data, CLONE_CULTIST) : str(data, CLONE_GUARD);
        if (name.isEmpty()) {
            return false;
        }
        final double ang = RANDOM.nextDouble() * Math.PI * 2.0;
        final double x = npc.getX() + Math.cos(ang) * 2.5;
        final double z = npc.getZ() + Math.sin(ang) * 2.5;
        final double y = AbilityCombatHelper.findGroundY(npc.getWorld(), x, z, npc.getY());
        final ICustomNpc add = spawnClone(npc, tab <= 0 ? 1 : tab, name, x, y, z);
        if (add == null) {
            return false;
        }
        put(data, COURT_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "courtCd", COURT_CD)));
        sayAbilityQuote(npc, "df_court");
        tagAdd(add, TAG_COURT);
        tagAdd(add, cultist ? TAG_CULTIST : TAG_GUARD);
        put(add.getStoreddata(), BOSS_UUID, String.valueOf(npc.getUUID()));
        put(add.getStoreddata(), ADD_NEXT_ATK, String.valueOf(now + 20));
        final float hp = cultist
                ? (float) DrachenfelsConfig.getD(data, "cultistHp", 30.0)
                : (float) DrachenfelsConfig.getD(data, "guardHp", 50.0);
        applyAddHp(add, hp);
        maybeShareBellAbsorb(npc, add);
        return false; // court is not an AbilityAPI cast
    }

    private static void tickPhase2(final ICustomNpc npc, final IData data, final long now) {
        tickFalseHostTrigger(npc, data, now);
        if (!AbilityAPI.isBusy(npc)) {
            if (ScriptDataUtil.isFlag(data, PENDING_FALSE)) {
                final IEntityLiving target = resolveCombatTarget(npc);
                startBossAbility(npc, DfFalseHostAbility.ID, target, DrachenfelsConfig.falseHostParams(npc));
            } else {
                long origin = ScriptDataUtil.getLong(data, CYCLE_ORIGIN);
                if (origin <= 0L) {
                    put(data, CYCLE_ORIGIN, String.valueOf(now));
                    origin = now;
                }
                final int shift = ScriptDataUtil.getInt(data, CYCLE_SHIFT);
                final long elapsed = now - origin - shift;
                final int cycleLen = DrachenfelsConfig.getI(data, "cycleLength", CYCLE_LENGTH);
                if (elapsed >= cycleLen) {
                    put(data, CYCLE_ORIGIN, String.valueOf(now));
                    put(data, CYCLE_SHIFT, "0");
                    put(data, CYCLE_SLOT, "0");
                } else {
                    final int slot = ScriptDataUtil.getInt(data, CYCLE_SLOT);
                    final IEntityLiving target = resolveCombatTarget(npc);
                    if (target != null && target.isAlive()) {
                        if (slot == 0 && elapsed >= 0) {
                            if (startBossAbility(
                                    npc,
                                    DfImperialPoisonAbility.ID,
                                    target,
                                    DrachenfelsConfig.imperialParams(npc))) {
                                put(data, CYCLE_SLOT, "1");
                            }
                        } else if (slot == 1
                                && elapsed >= DrachenfelsConfig.getI(data, "cycleFeastAt", 120)) {
                            if (startBossAbility(
                                    npc,
                                    DfFeastSeatsAbility.ID,
                                    target,
                                    DrachenfelsConfig.feastParams(npc))) {
                                put(data, CYCLE_SLOT, "2");
                            }
                        } else if (slot == 2
                                && elapsed >= DrachenfelsConfig.getI(data, "cycleLeperAt", 220)) {
                            if (startBossAbility(
                                    npc,
                                    DfLeperBallAbility.ID,
                                    target,
                                    DrachenfelsConfig.leperParams(npc))) {
                                put(data, CYCLE_SLOT, "3");
                            }
                        }
                    }
                }
            }
        }
        // Same scripted kite as phase 1 (no seal puddles left after transition).
        maintainPhase1Movement(npc, data, now);
    }

    private static void tickFalseHostTrigger(final ICustomNpc npc, final IData data, final long now) {
        final double ratio = hpRatio(npc);
        final String fired = str(data, FALSE_FIRED);
        int count = 0;
        if (!fired.isEmpty()) {
            count = fired.split(";").length;
        }
        final int maxFalse = DrachenfelsConfig.getI(data, "falseMax", 3);
        if (count >= maxFalse) {
            return;
        }
        final double[] falseRatios = DrachenfelsConfig.getRatios(data, "falseRatios", FALSE_RATIOS);
        for (int i = 0; i < falseRatios.length; i++) {
            final String mark = DrachenfelsConfig.mark(falseRatios[i]);
            if (fired.contains(mark)) {
                continue;
            }
            if (ratio > falseRatios[i]) {
                continue;
            }
            put(data, FALSE_FIRED, fired.isEmpty() ? mark : fired + ";" + mark);
            put(data, PENDING_FALSE, "1");
            final String activeId = AbilityAPI.getActiveId(npc);
            if (AbilityAPI.isBusy(npc) && !DfFalseHostAbility.ID.equals(activeId)) {
                AbilityAPI.cancel(npc);
            }
            return;
        }
    }

    // -------------------------------------------------------------------------
    // Desperation Air (25% / 15% / 5%)
    // -------------------------------------------------------------------------

    private static boolean isDesperationActive(final IData data) {
        return ScriptDataUtil.isFlag(data, DESPERATION);
    }

    private static boolean isDesperationStunned(final IData data, final long now) {
        return now < ScriptDataUtil.getLong(data, DESP_STUN_UNTIL);
    }

    private static void tickDesperationTrigger(final ICustomNpc npc, final IData data, final long now) {
        final double ratio = hpRatio(npc);
        final String fired = str(data, DESPERATION_FIRED);
        final double[] ratios = DrachenfelsConfig.getRatios(data, "desperationRatios", DESPERATION_RATIOS);
        for (int i = 0; i < ratios.length; i++) {
            final String mark = DrachenfelsConfig.mark(ratios[i]);
            if (fired.contains(mark)) {
                continue;
            }
            if (ratio > ratios[i]) {
                continue;
            }
            put(data, DESPERATION_FIRED, fired.isEmpty() ? mark : fired + ";" + mark);
            beginDesperation(npc, data, now);
            return;
        }
    }

    private static void beginDesperation(final ICustomNpc npc, final IData data, final long now) {
        AbilityAPI.cancel(npc);
        put(data, DESPERATION, "1");
        put(data, DESP_SPAWNED, "0");
        put(data, SPIRIT_MODE, "0");
        put(data, DESP_STUN_UNTIL, "0");
        put(data, ABSORB_KEY, "0");
        clearBellAbsorb(npc);
        killTaggedNear(npc, TAG_DESP_SHARD, TAG_PHANTOM, TAG_FALSE);
        clearOwnerZones(npc);

        final double[] c = getArenaCenter(npc);
        final double gy = AbilityCombatHelper.findGroundY(npc.getWorld(), c[0], c[2], c[1]);
        final double airY = gy + DrachenfelsConfig.getD(data, "despAirHeight", DESP_AIR_HEIGHT);
        put(data, DESP_AIR_Y, String.valueOf(airY));
        pinDesperationFlight(npc, c[0], airY, c[2]);
        applySpiritAi(npc);

        put(data, DESP_RING_AT, String.valueOf(now));
        put(data, DESP_RING_ALT, "0");
        put(data, DESP_BLOB_AT, String.valueOf(now));
        spawnDesperationShards(npc, data);
        put(data, DESP_SPAWNED, "1");
        say(npc, "Взлетаю. Разбейте осколки — или замок начнётся снова.");
        AbilityVfx.spawnSoulBurst(npc.getWorld(), c[0], airY, c[2], 2.2);
    }

    private static void tickDesperation(final ICustomNpc npc, final IData data, final long now) {
        final double[] c = getArenaCenter(npc);
        final double airY = ScriptDataUtil.getFloat(data, DESP_AIR_Y);
        final double holdY = airY > 0.1 ? airY : c[1] + DESP_AIR_HEIGHT;
        pinDesperationFlight(npc, c[0], holdY, c[2]);

        if (tickDesperationShards(npc, data)) {
            return; // fail triggered
        }

        if (ScriptDataUtil.isFlag(data, DESP_SPAWNED) && countTaggedNear(npc, TAG_DESP_SHARD) <= 0) {
            succeedDesperation(npc, data, now);
            return;
        }

        if (!hasNearbyPlayers(npc)) {
            if (AbilityAPI.isBusy(npc)) {
                AbilityAPI.cancel(npc);
            }
            return;
        }

        if (AbilityAPI.isBusy(npc)) {
            return;
        }

        final IEntityLiving target = resolveCombatTarget(npc);
        if (target == null || !target.isAlive()) {
            return;
        }

        if (now >= ScriptDataUtil.getLong(data, DESP_RING_AT)) {
            final int alt = ScriptDataUtil.getInt(data, DESP_RING_ALT);
            final boolean poison = (alt & 1) == 0;
            final String id = poison ? DfImperialPoisonAbility.ID : DfNamelessWhisperAbility.ID;
            final Map<String, Object> params = poison
                    ? DrachenfelsConfig.imperialParams(npc)
                    : DrachenfelsConfig.whisperParams(npc);
            if (AbilityAPI.start(npc, id, target, params)) {
                put(data, DESP_RING_ALT, String.valueOf(alt + 1));
                // Gap armed in onAbilityEnded; push far so blobs fill until then.
                put(data, DESP_RING_AT, String.valueOf(now + 10_000));
                say(npc, poison
                        ? "Пейте. Яд — вино этого пира."
                        : "Шёпот, который стирает вас.");
            }
            return;
        }

        if (now >= ScriptDataUtil.getLong(data, DESP_BLOB_AT)) {
            if (AbilityAPI.start(npc, CrimsonBlobAbility.ID, target, DrachenfelsConfig.desperationBlobParams(npc))) {
                put(data, DESP_BLOB_AT, String.valueOf(now
                        + DrachenfelsConfig.getI(data, "despBlobCd", DESP_BLOB_CD)));
            }
        }
    }

    /** @return true if fail was triggered (shard touched boss). */
    private static boolean tickDesperationShards(final ICustomNpc boss, final IData data) {
        final double[] c = getArenaCenter(boss);
        final List<ICustomNpc> shards = findTagged(boss, TAG_DESP_SHARD);
        final double touch = DrachenfelsConfig.getD(data, "shardTouchDist", SHARD_TOUCH_DIST);
        final double speed = DrachenfelsConfig.getD(data, "despShardSpeed", DESP_SHARD_SPEED);
        for (final ICustomNpc shard : shards) {
            if (shard == null || !shard.isAlive()) {
                continue;
            }
            pinShardFlight(shard);
            final double dx = boss.getX() - shard.getX();
            final double dz = boss.getZ() - shard.getZ();
            final double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist <= touch) {
                shard.despawn();
                failDesperationToPhase1(boss, data);
                return true;
            }
            final double step = Math.min(speed, Math.max(0.0, dist - touch * 0.35));
            final double nx = shard.getX() + (dx / Math.max(0.01, dist)) * step;
            final double nz = shard.getZ() + (dz / Math.max(0.01, dist)) * step;
            final double ny = AbilityCombatHelper.findGroundY(boss.getWorld(), nx, nz, c[1]);
            shard.setPosition(nx, ny, nz);
            pinShardFlight(shard);
        }
        return false;
    }

    private static void spawnDesperationShards(final ICustomNpc boss, final IData data) {
        final int tab = ScriptDataUtil.getInt(data, CLONE_TAB);
        final String name = str(data, CLONE_SHARD);
        if (name.isEmpty()) {
            return;
        }
        final double[] c = getArenaCenter(boss);
        final double arenaR = Math.max(1.0, getArenaRadius(boss) - 0.25);
        final double minDist = Math.min(
                arenaR, Math.max(1.0, DrachenfelsConfig.getD(data, "despShardMinDist", DESP_SHARD_MIN_DIST)));
        final float hp = (float) DrachenfelsConfig.getD(data, "despShardHp",
                DrachenfelsConfig.getD(data, "shardHp", 20.0));
        final double baseAng = RANDOM.nextDouble() * 360.0;
        final double step = 120.0;
        final double jitter = 25.0;
        for (int i = 0; i < 3; i++) {
            final double ang = baseAng + step * i + (RANDOM.nextDouble() * 2.0 - 1.0) * jitter;
            final double dist = minDist + RANDOM.nextDouble() * Math.max(0.0, arenaR - minDist);
            final double rad = Math.toRadians(ang);
            final double x = c[0] + Math.cos(rad) * dist;
            final double z = c[2] + Math.sin(rad) * dist;
            final double y = AbilityCombatHelper.findGroundY(boss.getWorld(), x, z, c[1]);
            final ICustomNpc shard = spawnClone(boss, tab <= 0 ? 1 : tab, name, x, y, z);
            if (shard == null) {
                continue;
            }
            tagAdd(shard, TAG_DESP_SHARD);
            put(shard.getStoreddata(), BOSS_UUID, String.valueOf(boss.getUUID()));
            setAiNone(shard);
            applyAddHp(shard, hp);
            pinShardFlight(shard);
        }
    }

    private static void failDesperationToPhase1(final ICustomNpc npc, final IData data) {
        AbilityAPI.cancel(npc);
        put(data, DESPERATION, "0");
        put(data, DESP_SPAWNED, "0");
        put(data, DESP_STUN_UNTIL, "0");
        put(data, DESPERATION_FIRED, "");
        put(data, SPIRIT_MODE, "0");
        put(data, ABSORB_KEY, "0");
        clearBellAbsorb(npc);
        killTaggedNear(npc, TAG_MONK, TAG_COURT, TAG_CULTIST, TAG_GUARD,
                TAG_PHANTOM, TAG_FALSE, TAG_DESP_SHARD);
        clearOwnerZones(npc);

        put(data, PHASE_KEY, "1");
        releaseDesperationFlight(npc);
        restoreFullHealth(npc);
        final double[] c = getArenaCenter(npc);
        final double gy = AbilityCombatHelper.findGroundY(npc.getWorld(), c[0], c[2], c[1]);
        npc.setPosition(c[0], gy, c[2]);
        applyPhase1Ai(npc);

        final long now = now(npc);
        put(data, SEAL_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "sealFirstDelay", 40)));
        put(data, GAZE_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "gazeFirstDelay", 80)));
        put(data, REPULSE_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "repulseFirstDelay", 60)));
        put(data, COURT_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "courtFirstDelay", 80)));
        put(data, BELL_READY, "0");
        put(data, BELL_FIRED, "");
        put(data, FALSE_FIRED, "");
        put(data, PENDING_FALSE, "0");
        put(data, FALSE_ACTIVE, "0");
        put(data, CYCLE_ORIGIN, "0");
        put(data, CYCLE_SHIFT, "0");
        put(data, CYCLE_SLOT, "0");
        put(data, GAZE_FAR_SINCE, "0");
        say(npc, "Осколок коснулся. Пир начинается сначала.");
        AbilityVfx.spawnSoulWave(npc.getWorld(), c[0], gy + 0.2, c[2], 3.0);
        AbilityVfx.spawnAbsorbShield(npc.getWorld(), c[0], gy, c[2]);
    }

    private static void succeedDesperation(final ICustomNpc npc, final IData data, final long now) {
        if (!ScriptDataUtil.isFlag(data, DESPERATION)) {
            return;
        }
        AbilityAPI.cancel(npc);
        put(data, DESPERATION, "0");
        put(data, DESP_SPAWNED, "0");
        killTaggedNear(npc, TAG_DESP_SHARD);
        final double[] c = getArenaCenter(npc);
        final double gy = AbilityCombatHelper.findGroundY(npc.getWorld(), c[0], c[2], c[1]);
        releaseDesperationFlight(npc);
        npc.setPosition(c[0], gy, c[2]);
        final int stun = DrachenfelsConfig.getI(data, "despStunTicks", DESP_STUN_TICKS);
        put(data, DESP_STUN_UNTIL, String.valueOf(now + stun));
        applySpiritAi(npc);
        AbilityCombatHelper.holdInPlace(npc, c[0], gy, c[2]);
        say(npc, "Осколки разбиты. Падаю…");
        AbilityVfx.spawnSoulBurst(npc.getWorld(), c[0], gy + 0.5, c[2], 1.8);
    }

    private static void tickDesperationStun(final ICustomNpc npc, final IData data, final long now) {
        final double[] c = getArenaCenter(npc);
        final double gy = AbilityCombatHelper.findGroundY(npc.getWorld(), c[0], c[2], c[1]);
        AbilityCombatHelper.holdInPlace(npc, c[0], gy, c[2]);
        if (AbilityAPI.isBusy(npc)) {
            AbilityAPI.cancel(npc);
        }
        if (now < ScriptDataUtil.getLong(data, DESP_STUN_UNTIL)) {
            return;
        }
        put(data, DESP_STUN_UNTIL, "0");
        put(data, SPIRIT_MODE, "1");
        applySpiritAi(npc);
        put(data, STEP_READY, String.valueOf(now + 40));
        put(data, WHISPER_READY, String.valueOf(now + 60));
        put(data, STEAL_READY, String.valueOf(now + 40));
        say(npc, "Тело — лишь маска. Имя остаётся.");
    }

    private static void tickPhase3(final ICustomNpc npc, final IData data, final long now) {
        if (AbilityAPI.isBusy(npc)) {
            return;
        }
        final IEntityLiving target = resolveCombatTarget(npc);
        if (target == null || !target.isAlive()) {
            return;
        }
        if (AbilityCombatHelper.flatDistance(
                npc.getX(), npc.getZ(), target.getX(), target.getZ())
                <= DrachenfelsConfig.getD(data, "stealRange", 3.0)
                && now >= ScriptDataUtil.getLong(data, STEAL_READY)) {
            if (startBossAbility(npc, DfNameStealAbility.ID, target, DrachenfelsConfig.stealParams(npc))) {
                return;
            }
        }
        if (now >= ScriptDataUtil.getLong(data, WHISPER_READY)) {
            if (startBossAbility(npc, DfNamelessWhisperAbility.ID, target, DrachenfelsConfig.whisperParams(npc))) {
                return;
            }
        }
        if (now >= ScriptDataUtil.getLong(data, STEP_READY)) {
            startBossAbility(npc, DfNamelessStepAbility.ID, target, DrachenfelsConfig.stepParams(npc));
        }
    }

    // -------------------------------------------------------------------------
    // Adds / desperation shards
    // -------------------------------------------------------------------------

    /**
     * Holds the boss on the Desperation Air plane without accumulating gravity
     * velocity or fall distance between encounter and server-end ability ticks.
     */
    private static void pinDesperationFlight(
            final ICustomNpc npc, final double x, final double y, final double z) {
        if (npc == null) {
            return;
        }
        AbilityCombatHelper.stopNavigation(npc);
        try {
            final Object mc = npc.getMCEntity();
            if (mc instanceof Entity) {
                final Entity entity = (Entity) mc;
                entity.setNoGravity(true);
                entity.setDeltaMovement(0.0, 0.0, 0.0);
                entity.fallDistance = 0.0F;
            }
        } catch (final Exception ignored) {
        }
        try {
            npc.setMotionX(0.0);
            npc.setMotionY(0.0);
            npc.setMotionZ(0.0);
            npc.setPosition(x, y, z);
        } catch (final Exception ignored) {
        }
    }

    /** Restores normal gravity and clears stale fall state before landing/reset. */
    private static void releaseDesperationFlight(final ICustomNpc npc) {
        if (npc == null) {
            return;
        }
        try {
            final Object mc = npc.getMCEntity();
            if (mc instanceof Entity) {
                final Entity entity = (Entity) mc;
                entity.setNoGravity(false);
                entity.setDeltaMovement(0.0, 0.0, 0.0);
                entity.fallDistance = 0.0F;
            }
        } catch (final Exception ignored) {
        }
        try {
            npc.setMotionX(0.0);
            npc.setMotionY(0.0);
            npc.setMotionZ(0.0);
        } catch (final Exception ignored) {
        }
    }

    private static void pinShardFlight(final ICustomNpc shard) {
        if (shard == null) {
            return;
        }
        try {
            final Object mc = shard.getMCEntity();
            if (mc instanceof Entity) {
                final Entity entity = (Entity) mc;
                entity.noPhysics = true;
                entity.setNoGravity(true);
                entity.setDeltaMovement(0.0, 0.0, 0.0);
                entity.fallDistance = 0.0F;
            }
        } catch (final Exception ignored) {
        }
        try {
            shard.setMotionX(0.0);
            shard.setMotionY(0.0);
            shard.setMotionZ(0.0);
        } catch (final Exception ignored) {
        }
    }

    /** Apply configurable max/current HP to a spawned add clone. */
    private static void applyAddHp(final ICustomNpc npc, final float hp) {
        if (npc == null || hp <= 0.0F) {
            return;
        }
        try {
            npc.setMaxHealth(hp);
            final Object mc = npc.getMCEntity();
            if (mc instanceof LivingEntity) {
                ((LivingEntity) mc).setHealth(hp);
            }
        } catch (final Exception ignored) {
        }
    }

    private static void healBossFromFalseHosts(final ICustomNpc boss, final IData data, final int livingCopies) {
        if (livingCopies <= 0 || ScriptDataUtil.getInt(data, PHASE_KEY) != 2) {
            return;
        }
        final float heal = (float) (livingCopies
                * DrachenfelsConfig.getD(data, "falseHealPerCopy", FALSE_HEAL_PER_COPY));
        if (heal <= 0.0F) {
            return;
        }
        final float cap = phaseHpCap(boss);
        try {
            final Object mc = boss.getMCEntity();
            if (mc instanceof LivingEntity) {
                final LivingEntity living = (LivingEntity) mc;
                final float before = living.getHealth();
                final float maxHeal = cap > 0.0F ? cap : living.getMaxHealth();
                final float after = Math.min(maxHeal, before + heal);
                if (after <= before + 0.001F) {
                    return;
                }
                living.setHealth(after);
                living.hurtTime = 0;
                living.hurtDuration = 0;
            }
        } catch (final Exception ignored) {
        }
    }

    private static void tickPhantoms(final ICustomNpc boss, final IData data) {
        final List<ICustomNpc> phantoms = findTagged(boss, TAG_PHANTOM);
        final double wiggleAmp = DrachenfelsConfig.getD(data, "leperWiggleAmp", 1.4);
        final double wiggleFreq = DrachenfelsConfig.getD(data, "leperWiggleFreq", 2.5);
        for (final ICustomNpc p : phantoms) {
            final IData pd = p.getStoreddata();
            int life = ScriptDataUtil.getInt(pd, PHANTOM_LIFE);
            final int maxLife = Math.max(1, DrachenfelsConfig.getI(data, "leperDuration", 70));
            life--;
            put(pd, PHANTOM_LIFE, String.valueOf(life));
            if (life <= 0) {
                clearPhantomZone(boss, pd);
                p.despawn();
                continue;
            }
            final double sx = ScriptDataUtil.getFloat(pd, HOME_X);
            final double sz = ScriptDataUtil.getFloat(pd, HOME_Z);
            final double ex = ScriptDataUtil.getFloat(pd, "df_ex");
            final double ez = ScriptDataUtil.getFloat(pd, "df_ez");
            final double hover = ScriptDataUtil.getFloat(pd, "df_hover");
            final double baseY = ScriptDataUtil.getFloat(pd, "df_base_y");
            final double ang = ScriptDataUtil.getFloat(pd, "df_ang");
            final double t = 1.0 - (life / (double) maxLife);
            final double dx = ex - sx;
            final double dz = ez - sz;
            final double len = Math.sqrt(dx * dx + dz * dz);
            double px = 0.0;
            double pz = 1.0;
            if (len > 0.01) {
                px = -dz / len;
                pz = dx / len;
            }
            final double phase = Math.toRadians(ang);
            final double wiggle =
                    Math.sin(t * Math.PI * 2.0 * wiggleFreq + phase) * wiggleAmp
                            + Math.sin(t * Math.PI * 2.0 * wiggleFreq * 1.7 + phase * 0.5)
                                    * wiggleAmp
                                    * 0.35;
            final double x = sx + dx * t + px * wiggle;
            final double z = sz + dz * t + pz * wiggle;
            // Locked flight plane from spawn floor — never re-scan into caves / void.
            final double floorY = baseY > 0.01 ? baseY : ScriptDataUtil.getFloat(pd, HOME_Y);
            final double y = floorY + (hover > 0.01 ? hover : 1.0);
            pinFlyingNpc(p, x, y, z);

            final EntityAbilityZone zone = resolvePhantomZone(boss, pd);
            if (zone != null) {
                zone.moveTo(x, floorY + 0.05, z, 0, 0);
            }
        }
    }

    private static void pinFlyingNpc(
            final ICustomNpc npc, final double x, final double y, final double z) {
        try {
            final Object mc = npc.getMCEntity();
            if (mc instanceof Entity) {
                final Entity entity = (Entity) mc;
                entity.noPhysics = true;
                entity.setNoGravity(true);
                entity.setDeltaMovement(0.0, 0.0, 0.0);
                entity.fallDistance = 0.0F;
            }
        } catch (final Exception ignored) {
        }
        try {
            npc.setMotionX(0.0);
            npc.setMotionY(0.0);
            npc.setMotionZ(0.0);
        } catch (final Exception ignored) {
        }
        npc.setPosition(x, y, z);
    }

    private static EntityAbilityZone resolvePhantomZone(final ICustomNpc boss, final IData pd) {
        final String raw = str(pd, "df_zone");
        if (raw.isEmpty()) {
            return null;
        }
        try {
            final UUID zoneId = UUID.fromString(raw);
            final Object mc = boss.getMCEntity();
            if (!(mc instanceof Entity)) {
                return null;
            }
            final World world = ((Entity) mc).level;
            if (!(world instanceof ServerWorld)) {
                return null;
            }
            final Entity entity = ((ServerWorld) world).getEntity(zoneId);
            return entity instanceof EntityAbilityZone ? (EntityAbilityZone) entity : null;
        } catch (final Exception e) {
            return null;
        }
    }

    private static void clearPhantomZone(final ICustomNpc boss, final IData pd) {
        final EntityAbilityZone zone = resolvePhantomZone(boss, pd);
        if (zone != null) {
            ZoneAPI.remove(zone);
        }
        put(pd, "df_zone", "");
    }

    private static void tickAdds(final ICustomNpc boss, final IData data, final long now) {
        final List<ICustomNpc> cultists = findTagged(boss, TAG_CULTIST);
        for (final ICustomNpc c : cultists) {
            if (c == null || !c.isAlive()) {
                continue;
            }
            final IData cd = c.getStoreddata();
            if (AbilityAPI.isBusy(c) || now < ScriptDataUtil.getLong(cd, ADD_NEXT_ATK)) {
                continue;
            }
            final IEntityLiving target = resolveAddTarget(boss, c);
            if (target == null || !target.isAlive()) {
                continue;
            }
            if (!startAddAbility(c, DfMaskGazeAbility.ID, target, DrachenfelsConfig.gazeParams(boss))) {
                continue;
            }
            put(cd, ADD_NEXT_ATK,
                    String.valueOf(now + DrachenfelsConfig.getI(data, "cultistInterval", 50)));
        }
        final List<ICustomNpc> guards = findTagged(boss, TAG_GUARD);
        for (final ICustomNpc g : guards) {
            if (g == null || !g.isAlive()) {
                continue;
            }
            final IData gd = g.getStoreddata();
            if (AbilityAPI.isBusy(g) || now < ScriptDataUtil.getLong(gd, ADD_NEXT_ATK)) {
                continue;
            }
            final IEntityLiving target = resolveAddTarget(boss, g);
            if (target == null || !target.isAlive()) {
                continue;
            }
            if (!startAddAbility(g, DfCarrierSlashAbility.ID, target, DrachenfelsConfig.guardSlashParams(boss))) {
                continue;
            }
            put(gd, ADD_NEXT_ATK,
                    String.valueOf(now + DrachenfelsConfig.getI(data, "guardInterval", GUARD_SLASH_CD)));
        }
    }

    private static void tickFalseHosts(final ICustomNpc boss, final IData data, final long now) {
        final List<ICustomNpc> copies = findTagged(boss, TAG_FALSE);
        if (copies.isEmpty()) {
            return;
        }
        int livingCopies = 0;
        for (final ICustomNpc copy : copies) {
            if (copy != null && copy.isAlive()) {
                livingCopies++;
            }
        }
        healBossFromFalseHosts(boss, data, livingCopies);
        final double[] center = getArenaCenter(boss);
        final double arenaR = Math.max(1.0, getArenaRadius(boss) - 0.5);
        final int puddleEvery = Math.max(1, DrachenfelsConfig.getI(data, "falsePuddleInterval", 12));
        for (final ICustomNpc copy : copies) {
            if (copy == null || !copy.isAlive()) {
                continue;
            }
            applyFalseCopyAi(copy, data);
            final IData cd = copy.getStoreddata();
            if (now >= ScriptDataUtil.getLong(cd, FALSE_NEXT_PUDDLE)) {
                spawnFalsePuddle(boss, data, copy, copy.getX(), copy.getY(), copy.getZ());
                put(cd, FALSE_NEXT_PUDDLE, String.valueOf(now + puddleEvery));
            }
            final IEntityLiving target = findNearestEngagePlayer(copy);
            if (target == null || !target.isAlive()) {
                clearAttackTarget(copy);
                AbilityCombatHelper.stopNavigation(copy);
                continue;
            }
            try {
                copy.setAttackTarget(target);
            } catch (final Exception ignored) {
            }
            clampFalseCopyToArena(copy, boss.getWorld(), center, arenaR);
        }
    }

    private static void spawnFalsePuddle(
            final ICustomNpc boss,
            final IData data,
            final ICustomNpc copy,
            final double x,
            final double y,
            final double z) {
        final double radius = DrachenfelsConfig.getD(data, "falsePuddleRadius", 1.6);
        // Do not stack another puddle where one already sits under the illusion.
        if (hasAbilityZoneNear(boss, x, z, radius)) {
            return;
        }
        final double damage = DrachenfelsConfig.getD(data, "falsePuddleDamage", 4.0);
        final int interval = Math.max(1, DrachenfelsConfig.getI(data, "falsePuddleDamageInterval", 10));
        // Persist until the illusion dies (or encounter cleanup) — not a short timer.
        final EntityAbilityZone zone = ZoneAPI.hazardCircle(
                boss, x, y + 0.05, z, radius, FALSE_PUDDLE_LIFETIME, damage, interval);
        if (zone == null) {
            return;
        }
        zone.setColor(DrachenfelsConfig.getI(data, "sealZoneColor", 0xC0143C14));
        zone.setZoneHeight(1.5f);
        zone.setVisible(true);
        zone.setGroundFill(true);
        zone.setBorder(true);
        zone.setHealOwner(0.0F);
        appendFalseZone(copy, zone);
    }

    /** True if (x,z) already lies inside an ability zone (no stacked puddle under the copy). */
    private static boolean hasAbilityZoneNear(
            final ICustomNpc boss, final double x, final double z, final double radius) {
        if (boss == null) {
            return false;
        }
        try {
            final Object mc = boss.getMCEntity();
            if (!(mc instanceof Entity)) {
                return false;
            }
            final World world = ((Entity) mc).level;
            if (!(world instanceof ServerWorld)) {
                return false;
            }
            final double pad = Math.max(radius, 2.0) + 1.0;
            final AxisAlignedBB box = new AxisAlignedBB(
                    x - pad, boss.getY() - 4.0, z - pad,
                    x + pad, boss.getY() + 6.0, z + pad);
            final List<EntityAbilityZone> zones =
                    ((ServerWorld) world).getEntitiesOfClass(EntityAbilityZone.class, box);
            for (final EntityAbilityZone zone : zones) {
                if (zone == null || zone.removed) {
                    continue;
                }
                final double zr = Math.max(0.1, zone.getRadius());
                if (AbilityCombatHelper.flatDistance(zone.getX(), zone.getZ(), x, z) <= zr) {
                    return true;
                }
            }
        } catch (final Exception ignored) {
        }
        return false;
    }

    private static void appendFalseZone(final ICustomNpc copy, final EntityAbilityZone zone) {
        if (copy == null || zone == null) {
            return;
        }
        final IData cd = copy.getStoreddata();
        final String id = String.valueOf(zone.getUUID());
        final String prev = str(cd, FALSE_ZONES);
        put(cd, FALSE_ZONES, prev.isEmpty() ? id : prev + ";" + id);
    }

    /** Clears all puddles left by this false-host copy. */
    public static void clearFalseCopyZones(final ICustomNpc copy) {
        if (copy == null) {
            return;
        }
        final IData cd = copy.getStoreddata();
        final String raw = str(cd, FALSE_ZONES);
        put(cd, FALSE_ZONES, "");
        if (raw.isEmpty()) {
            return;
        }
        try {
            final Object mc = copy.getMCEntity();
            if (!(mc instanceof Entity)) {
                return;
            }
            final World world = ((Entity) mc).level;
            if (!(world instanceof ServerWorld)) {
                return;
            }
            final ServerWorld sw = (ServerWorld) world;
            for (final String part : raw.split(";")) {
                if (part == null || part.isEmpty()) {
                    continue;
                }
                try {
                    final Entity entity = sw.getEntity(UUID.fromString(part.trim()));
                    if (entity instanceof EntityAbilityZone) {
                        ZoneAPI.remove((EntityAbilityZone) entity);
                    }
                } catch (final Exception ignored) {
                }
            }
        } catch (final Exception ignored) {
        }
    }

    public static void onFalseCopyDeath(final ICustomNpc copy) {
        clearFalseCopyZones(copy);
    }

    // -------------------------------------------------------------------------
    // AI helpers
    // -------------------------------------------------------------------------

    private static void applyPhase1Ai(final ICustomNpc npc) {
        try {
            final INPCAi ai = npc.getAi();
            // Scripted kite/puddle steps only — vanilla chase fights Gaze range.
            ai.setWalkingSpeed(0);
            ai.setRetaliateType(0);
            ai.setMovingType(0);
        } catch (final Exception ignored) {
        }
        setMoveSpeed(npc, DrachenfelsConfig.getD(npc, "phase1Speed", 0.15));
    }

    private static void applyPhase2Ai(final ICustomNpc npc) {
        // Same scripted kite as phase 1 — not rooted in arena center.
        try {
            final INPCAi ai = npc.getAi();
            ai.setWalkingSpeed(0);
            ai.setRetaliateType(0);
            ai.setMovingType(0);
        } catch (final Exception ignored) {
        }
        setMoveSpeed(npc, DrachenfelsConfig.getD(npc, "phase1Speed", 0.15));
    }

    private static void applySpiritAi(final ICustomNpc npc) {
        try {
            final INPCAi ai = npc.getAi();
            ai.setWalkingSpeed(0);
            ai.setRetaliateType(3);
            ai.setMovingType(0);
        } catch (final Exception ignored) {
        }
        setMoveSpeed(npc, 0.0);
    }

    /**
     * Phase 1: move toward densest seal puddles while keeping kite range from the player.
     * Diving onto player-footprint puddles breaks Gaze (needs dist &gt; gazeRange) and attack target.
     */
    private static void maintainPhase1Movement(final ICustomNpc npc, final IData data, final long now) {
        if (AbilityAPI.isBusy(npc)) {
            return;
        }
        final IEntityLiving keepTarget = resolveCombatTarget(npc);
        final double kite = DrachenfelsConfig.getD(data, "kiteDistance", 6.0);
        // Only rescan zones on repath ticks — casting must stay cheap every tick.
        final boolean needRepath =
                now >= ScriptDataUtil.getLong(data, PUDDLE_REPATH) || str(data, PUDDLE_TX).isEmpty();
        if (needRepath) {
            final List<EntityAbilityZone> puddles = collectSealPuddles(npc);
            if (puddles.isEmpty()) {
                put(data, PUDDLE_TX, "");
                put(data, PUDDLE_TZ, "");
                put(data, PUDDLE_REPATH, String.valueOf(now + PUDDLE_REPATH_TICKS));
                maintainKite(npc, kite);
                restoreCombatTarget(npc, keepTarget);
                return;
            }
            final double[] best = findBestPuddleSpot(npc, puddles, kite);
            if (best == null) {
                put(data, PUDDLE_TX, "");
                put(data, PUDDLE_TZ, "");
                put(data, PUDDLE_REPATH, String.valueOf(now + PUDDLE_REPATH_TICKS));
                maintainKite(npc, kite);
                restoreCombatTarget(npc, keepTarget);
                return;
            }
            put(data, PUDDLE_TX, String.valueOf(best[0]));
            put(data, PUDDLE_TZ, String.valueOf(best[1]));
            put(data, PUDDLE_REPATH, String.valueOf(now + PUDDLE_REPATH_TICKS));
        }
        if (str(data, PUDDLE_TX).isEmpty()) {
            maintainKite(npc, kite);
            restoreCombatTarget(npc, keepTarget);
            return;
        }
        final double tx = ScriptDataUtil.getFloat(data, PUDDLE_TX);
        final double tz = ScriptDataUtil.getFloat(data, PUDDLE_TZ);
        stepToward(npc, tx, tz, PUDDLE_STEP, PUDDLE_ARRIVE);
        restoreCombatTarget(npc, keepTarget);
    }

    private static List<EntityAbilityZone> collectSealPuddles(final ICustomNpc boss) {
        final List<EntityAbilityZone> out = new ArrayList<>();
        try {
            final Object mc = boss.getMCEntity();
            if (!(mc instanceof Entity)) {
                return out;
            }
            final World world = ((Entity) mc).level;
            if (!(world instanceof ServerWorld)) {
                return out;
            }
            final UUID bossUuid = UUID.fromString(String.valueOf(boss.getUUID()));
            final double[] c = getArenaCenter(boss);
            final double arenaR = getArenaRadius(boss) + 4.0;
            final AxisAlignedBB box = new AxisAlignedBB(
                    c[0] - arenaR, c[1] - 8.0, c[2] - arenaR,
                    c[0] + arenaR, c[1] + 8.0, c[2] + arenaR);
            final List<EntityAbilityZone> zones =
                    ((ServerWorld) world).getEntitiesOfClass(EntityAbilityZone.class, box);
            for (final EntityAbilityZone zone : zones) {
                if (zone == null || zone.removed) {
                    continue;
                }
                if (zone.getZoneType() != EntityAbilityZone.ZoneType.HAZARD) {
                    continue;
                }
                if (zone.getShape() != EntityAbilityZone.ZoneShape.CIRCLE) {
                    continue;
                }
                if (zone.getDamage() <= 0.0f) {
                    continue;
                }
                final UUID owner = zone.getOwnerUuid();
                if (owner == null || !owner.equals(bossUuid)) {
                    continue;
                }
                out.add(zone);
            }
        } catch (final Exception ignored) {
        }
        return out;
    }

    /**
     * @return {@code {x, z}} or {@code null} if no safe kite-range puddle spot exists.
     */
    private static double[] findBestPuddleSpot(
            final ICustomNpc npc,
            final List<EntityAbilityZone> puddles,
            final double kite) {
        final double[] c = getArenaCenter(npc);
        final double arenaR = getArenaRadius(npc) - 1.0;
        final IEntityLiving target = npc.getAttackTarget();
        final double curX = npc.getX();
        final double curZ = npc.getZ();
        // Stay on kite ring so Gaze (gazeRange ~5) can fire and Repulse stays a melee punish.
        final double minPlayerDist = Math.max(kite - 0.5, 4.5);
        final double maxPlayerDist = kite + 4.0;

        double bestX = Double.NaN;
        double bestZ = Double.NaN;
        int bestScore = -1;
        double bestTie = Double.NEGATIVE_INFINITY;

        final int n = puddles.size();
        if (n >= 3) {
            double sx = 0.0;
            double sz = 0.0;
            for (final EntityAbilityZone zone : puddles) {
                sx += zone.getX();
                sz += zone.getZ();
            }
            final double[] r0 = tryPuddleCandidate(
                    sx / n, sz / n, c, arenaR, target, kite, minPlayerDist, maxPlayerDist, puddles,
                    curX, curZ, bestScore, bestTie, bestX, bestZ);
            bestX = r0[0];
            bestZ = r0[1];
            bestScore = (int) r0[2];
            bestTie = r0[3];
        }
        for (int i = 0; i < n; i++) {
            final EntityAbilityZone a = puddles.get(i);
            final double[] r1 = tryPuddleCandidate(
                    a.getX(), a.getZ(), c, arenaR, target, kite, minPlayerDist, maxPlayerDist, puddles,
                    curX, curZ, bestScore, bestTie, bestX, bestZ);
            bestX = r1[0];
            bestZ = r1[1];
            bestScore = (int) r1[2];
            bestTie = r1[3];
            for (int j = i + 1; j < n; j++) {
                final EntityAbilityZone b = puddles.get(j);
                final double[] r2 = tryPuddleCandidate(
                        (a.getX() + b.getX()) * 0.5,
                        (a.getZ() + b.getZ()) * 0.5,
                        c, arenaR, target, kite, minPlayerDist, maxPlayerDist, puddles,
                        curX, curZ, bestScore, bestTie, bestX, bestZ);
                bestX = r2[0];
                bestZ = r2[1];
                bestScore = (int) r2[2];
                bestTie = r2[3];
            }
        }
        if (bestScore < 0 || Double.isNaN(bestX)) {
            return null;
        }
        return new double[]{bestX, bestZ};
    }

    private static double[] tryPuddleCandidate(
            final double x,
            final double z,
            final double[] center,
            final double arenaR,
            final IEntityLiving target,
            final double kite,
            final double minPlayerDist,
            final double maxPlayerDist,
            final List<EntityAbilityZone> puddles,
            final double curX,
            final double curZ,
            final int bestScore,
            final double bestTie,
            final double bestX,
            final double bestZ) {
        if (AbilityCombatHelper.flatDistance(x, z, center[0], center[2]) > arenaR) {
            return packPuddleBest(bestX, bestZ, bestScore, bestTie);
        }
        if (target != null && target.isAlive()) {
            final double pd = AbilityCombatHelper.flatDistance(x, z, target.getX(), target.getZ());
            if (pd < minPlayerDist || pd > maxPlayerDist) {
                return packPuddleBest(bestX, bestZ, bestScore, bestTie);
            }
        }
        final int score = scorePuddleCoverage(x, z, puddles);
        if (score <= 0) {
            return packPuddleBest(bestX, bestZ, bestScore, bestTie);
        }
        final double tie = puddleTieBreak(x, z, curX, curZ, target, kite);
        if (score > bestScore || (score == bestScore && tie > bestTie)) {
            return new double[]{x, z, score, tie};
        }
        return packPuddleBest(bestX, bestZ, bestScore, bestTie);
    }

    private static double[] packPuddleBest(
            final double bestX, final double bestZ, final int bestScore, final double bestTie) {
        return new double[]{bestX, bestZ, bestScore, bestTie};
    }

    /** How many seal puddles this standing point is inside / touching. */
    private static int scorePuddleCoverage(
            final double x, final double z, final List<EntityAbilityZone> puddles) {
        int score = 0;
        for (final EntityAbilityZone zone : puddles) {
            final double r = zone.getRadius() + 1.25;
            if (AbilityCombatHelper.flatDistance(x, z, zone.getX(), zone.getZ()) <= r) {
                score++;
            }
        }
        return score;
    }

    /** Prefer kite distance from player, then less travel from current position. */
    private static double puddleTieBreak(
            final double x,
            final double z,
            final double curX,
            final double curZ,
            final IEntityLiving target,
            final double kite) {
        double tie = -AbilityCombatHelper.flatDistance(x, z, curX, curZ) * 0.05;
        if (target != null && target.isAlive()) {
            final double d = AbilityCombatHelper.flatDistance(x, z, target.getX(), target.getZ());
            tie -= Math.abs(d - kite) * 0.25;
        }
        return tie;
    }

    private static void stepToward(
            final ICustomNpc npc,
            final double tx,
            final double tz,
            final double step,
            final double arrive) {
        final double dx = tx - npc.getX();
        final double dz = tz - npc.getZ();
        final double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist <= arrive || dist < 0.01) {
            return;
        }
        final double[] c = getArenaCenter(npc);
        final double arenaR = getArenaRadius(npc);
        final double move = Math.min(step, dist);
        final double nx = npc.getX() + (dx / dist) * move;
        final double nz = npc.getZ() + (dz / dist) * move;
        if (AbilityCombatHelper.flatDistance(nx, nz, c[0], c[2]) > arenaR - 0.5) {
            return;
        }
        final double ny = AbilityCombatHelper.findGroundY(npc.getWorld(), nx, nz, npc.getY());
        npc.setPosition(nx, ny, nz);
    }

    private static void maintainKite(final ICustomNpc npc, final double preferred) {
        final IEntityLiving target = npc.getAttackTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        if (AbilityAPI.isBusy(npc)) {
            return;
        }
        final double dist = AbilityCombatHelper.flatDistance(
                npc.getX(), npc.getZ(), target.getX(), target.getZ());
        final double[] c = getArenaCenter(npc);
        final double arenaR = getArenaRadius(npc);
        double dx;
        double dz;
        if (dist < preferred - 0.75) {
            dx = npc.getX() - target.getX();
            dz = npc.getZ() - target.getZ();
        } else if (dist > preferred + 1.25) {
            dx = target.getX() - npc.getX();
            dz = target.getZ() - npc.getZ();
        } else {
            return;
        }
        final double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) {
            return;
        }
        final double nx = npc.getX() + (dx / len) * PUDDLE_STEP;
        final double nz = npc.getZ() + (dz / len) * PUDDLE_STEP;
        if (AbilityCombatHelper.flatDistance(nx, nz, c[0], c[2]) > arenaR - 1.0) {
            return;
        }
        final double ny = AbilityCombatHelper.findGroundY(npc.getWorld(), nx, nz, npc.getY());
        npc.setPosition(nx, ny, nz);
    }

    private static void setAiNone(final ICustomNpc npc) {
        try {
            final INPCAi ai = npc.getAi();
            ai.setWalkingSpeed(0);
            ai.setRetaliateType(3);
            ai.setMovingType(0);
        } catch (final Exception ignored) {
        }
    }

    /** False-host illusions: CNPC Panic flee from nearest engage player. */
    private static void applyFalseCopyAi(final ICustomNpc copy, final IData bossData) {
        if (copy == null) {
            return;
        }
        final int speed = DrachenfelsConfig.getI(bossData, "falsePanicSpeed", FALSE_PANIC_SPEED);
        try {
            final INPCAi ai = copy.getAi();
            ai.setRetaliateType(RETALIATE_PANIC);
            ai.setWalkingSpeed(speed);
            ai.setMovingType(0);
        } catch (final Exception ignored) {
        }
    }

    private static void clampFalseCopyToArena(
            final ICustomNpc copy,
            final noppes.npcs.api.IWorld world,
            final double[] center,
            final double arenaR) {
        if (copy == null || center == null || center.length < 3) {
            return;
        }
        final double dx = copy.getX() - center[0];
        final double dz = copy.getZ() - center[2];
        final double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist <= arenaR) {
            return;
        }
        final double nx = center[0] + (dx / Math.max(0.01, dist)) * arenaR;
        final double nz = center[2] + (dz / Math.max(0.01, dist)) * arenaR;
        final double ny = AbilityCombatHelper.findGroundY(world, nx, nz, copy.getY());
        copy.setPosition(nx, ny, nz);
        AbilityCombatHelper.stopNavigation(copy);
    }

    private static void setMoveSpeed(final ICustomNpc npc, final double speed) {
        try {
            final Object mc = npc.getMCEntity();
            if (mc instanceof LivingEntity) {
                final LivingEntity living = (LivingEntity) mc;
                if (living.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
                    living.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
                }
            }
        } catch (final Exception ignored) {
        }
    }

    private static void setNoPhysics(final ICustomNpc npc, final boolean value) {
        try {
            final Object mc = npc.getMCEntity();
            if (mc instanceof Entity) {
                ((Entity) mc).noPhysics = value;
            }
        } catch (final Exception ignored) {
        }
    }

    // -------------------------------------------------------------------------
    // Spawn / find / kill
    // -------------------------------------------------------------------------

    /** CustomNPC spawnCycle: 3 = No (не возрождается). */
    private static final int SPAWN_CYCLE_NONE = 3;

    private static ICustomNpc spawnClone(
            final ICustomNpc boss,
            final int tab,
            final String name,
            final double x,
            final double y,
            final double z) {
        try {
            final IEntity spawned = boss.getWorld().spawnClone(x, y, z, tab, name);
            if (spawned instanceof ICustomNpc) {
                disableRespawn(spawned);
                return (ICustomNpc) spawned;
            }
        } catch (final Exception ignored) {
        }
        return null;
    }

    private static void disableRespawn(final IEntity entity) {
        if (entity == null) {
            return;
        }
        try {
            final Object mc = entity.getMCEntity();
            if (mc instanceof EntityNPCInterface) {
                final EntityNPCInterface npc = (EntityNPCInterface) mc;
                npc.stats.spawnCycle = SPAWN_CYCLE_NONE;
                npc.stats.respawnTime = 0;
                npc.killedtime = 0;
                npc.updateClient = true;
            }
        } catch (final Exception ignored) {
        }
    }

    private static void tagAdd(final ICustomNpc npc, final String tag) {
        if (npc != null && !npc.hasTag(tag)) {
            npc.addTag(tag);
        }
    }

    private static int countTaggedNear(final ICustomNpc boss, final String tag) {
        return findTagged(boss, tag).size();
    }

    private static List<ICustomNpc> findTagged(final ICustomNpc boss, final String tag) {
        final List<ICustomNpc> out = new ArrayList<>();
        if (boss == null) {
            return out;
        }
        try {
            final IEntity[] list = boss.getWorld().getNearbyEntities(boss.getPos(), 48, 2);
            for (final IEntity ent : list) {
                if (ent instanceof ICustomNpc && ent.isAlive() && ent.hasTag(tag)) {
                    out.add((ICustomNpc) ent);
                }
            }
        } catch (final Exception ignored) {
        }
        return out;
    }

    private static ICustomNpc findFirstTagged(final ICustomNpc boss, final String tag) {
        final List<ICustomNpc> list = findTagged(boss, tag);
        return list.isEmpty() ? null : list.get(0);
    }

    private static void killTaggedNear(final ICustomNpc boss, final String... tags) {
        for (final String tag : tags) {
            for (final ICustomNpc npc : findTagged(boss, tag)) {
                try {
                    if (npc != null && npc.hasTag(TAG_FALSE)) {
                        clearFalseCopyZones(npc);
                    }
                    if (npc != null && npc.hasTag(TAG_PHANTOM)) {
                        clearPhantomZone(boss, npc.getStoreddata());
                    }
                    npc.despawn();
                } catch (final Exception ignored) {
                }
            }
        }
    }

    private static ICustomNpc findBossForAdd(final ICustomNpc add) {
        if (add == null) {
            return null;
        }
        final String uuid = str(add.getStoreddata(), BOSS_UUID);
        if (!uuid.isEmpty()) {
            try {
                final IEntity[] list = add.getWorld().getNearbyEntities(add.getPos(), 64, 2);
                for (final IEntity ent : list) {
                    if (ent instanceof ICustomNpc && uuid.equals(String.valueOf(ent.getUUID()))) {
                        return (ICustomNpc) ent;
                    }
                }
            } catch (final Exception ignored) {
            }
        }
        try {
            final IEntity[] list = add.getWorld().getNearbyEntities(add.getPos(), 64, 2);
            for (final IEntity ent : list) {
                if (ent instanceof ICustomNpc && ((ICustomNpc) ent).hasTag(BOSS_TAG)) {
                    return (ICustomNpc) ent;
                }
            }
        } catch (final Exception ignored) {
        }
        return null;
    }

    private static void clearOwnerZones(final ICustomNpc boss) {
        try {
            final Object mc = boss.getMCEntity();
            if (!(mc instanceof Entity)) {
                return;
            }
            final World world = ((Entity) mc).level;
            if (!(world instanceof ServerWorld)) {
                return;
            }
            final UUID bossUuid = UUID.fromString(String.valueOf(boss.getUUID()));
            final AxisAlignedBB box = new AxisAlignedBB(
                    boss.getX() - 40, boss.getY() - 10, boss.getZ() - 40,
                    boss.getX() + 40, boss.getY() + 10, boss.getZ() + 40);
            final List<EntityAbilityZone> zones =
                    ((ServerWorld) world).getEntitiesOfClass(EntityAbilityZone.class, box);
            for (final EntityAbilityZone zone : zones) {
                if (zone == null || zone.removed) {
                    continue;
                }
                ZoneAPI.remove(zone);
            }
        } catch (final Exception ignored) {
        }
    }

    // -------------------------------------------------------------------------
    // Utils
    // -------------------------------------------------------------------------

    private static double hpRatio(final ICustomNpc npc) {
        final double max = npc.getMaxHealth();
        if (max <= 0.01) {
            return 1.0;
        }
        return npc.getHealth() / max;
    }

    private static long now(final ICustomNpc npc) {
        try {
            return npc.getWorld().getTotalTime();
        } catch (final Exception e) {
            return 0L;
        }
    }

    private static boolean isClient(final ICustomNpc npc) {
        try {
            final Object mc = npc.getMCEntity();
            return mc instanceof Entity && ((Entity) mc).level != null && ((Entity) mc).level.isClientSide;
        } catch (final Exception e) {
            return true;
        }
    }

    private static void put(final IData data, final String key, final Object value) {
        if (data != null && key != null) {
            data.put(key, String.valueOf(value));
        }
    }

    private static String str(final IData data, final String key) {
        if (data == null || !data.has(key)) {
            return "";
        }
        final Object raw = data.get(key);
        return raw == null ? "" : String.valueOf(raw);
    }

    private static void say(final ICustomNpc npc, final String msg) {
        try {
            npc.say(msg);
        } catch (final Exception ignored) {
        }
    }

    /** Gaze must be valid on the kite ring; old JS left gazeRange=8 ≥ kite=6 and starved the rotation. */
    private static double effectiveGazeRange(final IData data) {
        final double kite = DrachenfelsConfig.getD(data, "kiteDistance", 6.0);
        final double configured = DrachenfelsConfig.getD(data, "gazeRange", GAZE_RANGE);
        if (configured + 0.25 < kite) {
            return configured;
        }
        return Math.max(1.0, kite - 1.0);
    }

    private static boolean startBossAbility(
            final ICustomNpc npc,
            final String abilityId,
            final IEntityLiving target,
            final Map<String, Object> params) {
        if (!hasNearbyPlayers(npc)) {
            return false;
        }
        if (!AbilityAPI.start(npc, abilityId, target, params)) {
            return false;
        }
        armAbilityCooldown(npc, abilityId);
        sayAbilityQuote(npc, abilityId);
        return true;
    }

    private static boolean startAddAbility(
            final ICustomNpc add,
            final String abilityId,
            final IEntityLiving target,
            final Map<String, Object> params) {
        if (add == null || AbilityAPI.isBusy(add)) {
            return false;
        }
        return AbilityAPI.start(add, abilityId, target, params);
    }

    private static IEntityLiving resolveAddTarget(final ICustomNpc boss, final ICustomNpc add) {
        if (boss != null) {
            final IEntityLiving bossTarget = boss.getAttackTarget();
            if (bossTarget != null && bossTarget.isAlive() && isEngageablePlayer(bossTarget)) {
                return bossTarget;
            }
        }
        return findNearestEngagePlayer(add != null ? add : boss);
    }

    /**
     * True if at least one survival/adventure player is within engage range of the boss.
     * Range = max(arenaRadius+4, engageRadius config, Stats aggro). Creative/spectator ignored.
     */
    private static boolean hasNearbyPlayers(final ICustomNpc npc) {
        if (findNearestEngagePlayer(npc) != null) {
            return true;
        }
        if (!ScriptDataUtil.isFlag(npc.getStoreddata(), FALSE_ACTIVE)) {
            return false;
        }
        for (final ICustomNpc copy : findTagged(npc, TAG_FALSE)) {
            if (copy != null && copy.isAlive() && findNearestEngagePlayer(copy) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Combat engage distance from the boss (not CNPC pathing aggro alone).
     * Uses the largest of: arenaRadius+4, optional {@code engageRadius} config,
     * and NPC Stats aggro range — so raising aggro in the CNPC GUI actually works.
     */
    private static double engageRange(final ICustomNpc npc) {
        double range = getArenaRadius(npc) + 4.0;
        range = Math.max(range, DrachenfelsConfig.getD(npc, "engageRadius", 0.0));
        try {
            if (npc.getStats() != null) {
                range = Math.max(range, (double) npc.getStats().getAggroRange());
            }
        } catch (final Exception ignored) {
        }
        return Math.max(1.0, range);
    }

    /** Survival or Adventure only — creative/spectator must not pull aggro. */
    private static boolean isEngageablePlayer(final IEntity ent) {
        if (!(ent instanceof IPlayer) || !ent.isAlive()) {
            return false;
        }
        try {
            final Object mc = ent.getMCEntity();
            if (mc instanceof PlayerEntity) {
                return EntityCloneStructureSpawner.isPlayablePlayer((PlayerEntity) mc);
            }
        } catch (final Exception ignored) {
        }
        try {
            final int gm = ((IPlayer) ent).getGamemode();
            return gm == 0 || gm == 2; // SURVIVAL / ADVENTURE
        } catch (final Exception ignored) {
        }
        return false;
    }

    private static IEntityLiving findNearestEngagePlayer(final ICustomNpc npc) {
        if (npc == null) {
            return null;
        }
        IEntityLiving best = null;
        double bestDist = Double.MAX_VALUE;
        final double range = engageRange(npc);
        try {
            final IEntity[] list = npc.getWorld().getNearbyEntities(
                    npc.getPos(), (int) Math.ceil(range + 1.0), 1);
            for (final IEntity ent : list) {
                if (!isEngageablePlayer(ent)) {
                    continue;
                }
                final double toBoss = AbilityCombatHelper.flatDistance(
                        npc.getX(), npc.getZ(), ent.getX(), ent.getZ());
                if (toBoss > range) {
                    continue;
                }
                if (toBoss < bestDist) {
                    bestDist = toBoss;
                    best = (IEntityLiving) ent;
                }
            }
        } catch (final Exception ignored) {
        }
        return best;
    }

    private static void clearAttackTarget(final ICustomNpc npc) {
        if (npc == null) {
            return;
        }
        try {
            npc.setAttackTarget(null);
        } catch (final Exception ignored) {
        }
    }

    private static void restoreFullHealth(final ICustomNpc npc) {
        if (npc == null) {
            return;
        }
        try {
            final Object mc = npc.getMCEntity();
            if (mc instanceof LivingEntity) {
                final LivingEntity living = (LivingEntity) mc;
                living.setHealth(living.getMaxHealth());
            }
        } catch (final Exception ignored) {
        }
    }

    /** CD from cast start so intervals match JS values (not castLength + CD). */
    private static void armAbilityCooldown(final ICustomNpc npc, final String abilityId) {
        if (npc == null || abilityId == null) {
            return;
        }
        final IData data = npc.getStoreddata();
        final long now = now(npc);
        if (DfBlackSealAbility.ID.equals(abilityId)) {
            put(data, SEAL_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "sealCd", SEAL_CD)));
        } else if (DfMaskGazeAbility.ID.equals(abilityId)) {
            put(data, GAZE_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "gazeCd", GAZE_CD)));
            put(data, GAZE_FAR_SINCE, "0");
        } else if (DfRepulseAbility.ID.equals(abilityId)) {
            put(data, REPULSE_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "repulseCd", REPULSE_CD)));
        } else if (DfNamelessStepAbility.ID.equals(abilityId)) {
            put(data, STEP_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "stepCd", STEP_CD)));
        } else if (DfNamelessWhisperAbility.ID.equals(abilityId)) {
            put(data, WHISPER_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "whisperCd", WHISPER_CD)));
        } else if (DfNameStealAbility.ID.equals(abilityId)) {
            put(data, STEAL_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "stealCd", STEAL_CD)));
        } else if (DfCarrierSlashAbility.ID.equals(abilityId)) {
            put(data, ARC_CD, String.valueOf(now + DrachenfelsConfig.getI(data, "carrierArcInterval", 50)));
        }
    }

    private static IEntityLiving resolveCombatTarget(final ICustomNpc npc) {
        if (npc == null) {
            return null;
        }
        final double range = engageRange(npc);
        final IEntityLiving current = npc.getAttackTarget();
        if (current != null && current.isAlive() && isEngageablePlayer(current)
                && AbilityCombatHelper.flatDistance(
                        npc.getX(), npc.getZ(), current.getX(), current.getZ()) <= range) {
            return current;
        }
        final IEntityLiving nearest = findNearestEngagePlayer(npc);
        if (nearest != null) {
            try {
                npc.setAttackTarget(nearest);
            } catch (final Exception ignored) {
            }
        } else {
            clearAttackTarget(npc);
        }
        return nearest;
    }

    private static void restoreCombatTarget(final ICustomNpc npc, final IEntityLiving target) {
        if (npc == null || target == null || !target.isAlive()) {
            return;
        }
        try {
            final IEntityLiving cur = npc.getAttackTarget();
            if (cur == null || !cur.isAlive()) {
                npc.setAttackTarget(target);
            }
        } catch (final Exception ignored) {
        }
    }

    private static void sayAbilityQuote(final ICustomNpc npc, final String abilityId) {
        if (abilityId == null) {
            return;
        }
        switch (abilityId) {
            case DfBlackSealAbility.ID:
                say(npc, "Печать ложится. Земля запомнит.");
                break;
            case DfMaskGazeAbility.ID:
                say(npc, "Смотрите в маску — и потеряете лицо.");
                break;
            case DfRepulseAbility.ID:
                say(npc, "Прочь с порога замка.");
                break;
            case "df_bell":
                say(npc, "Колокол мёртвых бьёт по вам.");
                break;
            case "df_court":
                say(npc, "Свита склоняется. Вы — нет.");
                break;
            case DfImperialPoisonAbility.ID:
                say(npc, "Пейте. Яд — вино этого пира.");
                break;
            case DfFeastSeatsAbility.ID:
                say(npc, "Садитесь. Места уже заняты смертью.");
                break;
            case DfLeperBallAbility.ID:
                say(npc, "Прокажённые танцуют для вас.");
                break;
            case DfFalseHostAbility.ID:
                say(npc, "Кто из нас хозяин? Угадайте.");
                break;
            case DfNamelessStepAbility.ID:
                say(npc, "Шаг без имени.");
                break;
            case DfNamelessWhisperAbility.ID:
                say(npc, "Шёпот, который стирает вас.");
                break;
            case DfNameStealAbility.ID:
                say(npc, "Ваше имя теперь моё.");
                break;
            case DfCarrierSlashAbility.ID:
                say(npc, "Плоть помнит клинок.");
                break;
            default:
                break;
        }
    }

    private static long parseLong(final String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (final Exception e) {
            return 0L;
        }
    }

    private static double parseDouble(final String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (final Exception e) {
            return 0.0;
        }
    }
}
