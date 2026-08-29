package noppes.npcs.abilities;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.potion.Effects;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import noppes.npcs.abilities.impl.DfBlackSealAbility;
import noppes.npcs.abilities.impl.DfFalseHostAbility;
import noppes.npcs.abilities.impl.DfFeastSeatsAbility;
import noppes.npcs.abilities.impl.DfImperialPoisonAbility;
import noppes.npcs.abilities.impl.DfLeperBallAbility;
import noppes.npcs.abilities.impl.DfMaskGazeAbility;
import noppes.npcs.abilities.impl.DfNameStealAbility;
import noppes.npcs.abilities.impl.DfNamelessStepAbility;
import noppes.npcs.abilities.impl.DfNamelessWhisperAbility;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.IEntityLiving;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.entity.data.IData;
import noppes.npcs.api.entity.data.INPCAi;
import noppes.npcs.entity.EntityAbilityZone;
import noppes.npcs.script.ScriptDataUtil;
import noppes.npcs.zone.ZoneAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * Solo Constant Drachenfels encounter: phases, queues, adds, vessels, shards.
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
    public static final String TAG_VESSEL = "df_vessel";
    public static final String TAG_SHARD = "df_shard";

    public static final double ARENA_RADIUS = 12.0;

    private static final double PHASE2_RATIO = 0.66;
    private static final double PHASE3_RATIO = 0.33;
    private static final double[] BELL_RATIOS = {0.88, 0.76};
    private static final double[] FALSE_RATIOS = {0.56, 0.46, 0.36};
    private static final double ABSORB_RATIO = 0.133;
    private static final double SHARD_HEAL_RATIO = 0.0267;

    private static final int INVULN_TICKS = 60;
    private static final int CARRIER_TICKS = 240;
    private static final int SEAL_CD = 200;
    private static final int GAZE_CD = 160;
    private static final int BELL_CD = 400;
    private static final int COURT_CD = 320;
    private static final int GAZE_FAR_TICKS = 40;
    private static final int GAZE_RANGE = 8;
    private static final int STEP_CD = 120;
    private static final int WHISPER_CD = 180;
    private static final int STEAL_CD = 160;
    private static final int CYCLE_LENGTH = 360;
    private static final int FALSE_SHIFT = 30;

    private static final String PHASE_KEY = "df_phase";
    private static final String ABSORB_KEY = "df_absorb";
    private static final String HOME_X = "df_home_x";
    private static final String HOME_Y = "df_home_y";
    private static final String HOME_Z = "df_home_z";
    private static final String INVULN_UNTIL = "df_invuln_until";
    private static final String TRANSITION = "df_transition";
    private static final String SEAL_READY = "df_seal_ready";
    private static final String GAZE_READY = "df_gaze_ready";
    private static final String BELL_READY = "df_bell_ready";
    private static final String COURT_READY = "df_court_ready";
    private static final String GAZE_FAR_SINCE = "df_gaze_far";
    private static final String BELL_FIRED = "df_bell_fired";
    private static final String FALSE_FIRED = "df_false_fired";
    private static final String CYCLE_ORIGIN = "df_cycle_origin";
    private static final String CYCLE_SHIFT = "df_cycle_shift";
    private static final String CYCLE_SLOT = "df_cycle_slot";
    private static final String CARRIER_UNTIL = "df_carrier_until";
    private static final String IN_CARRIER = "df_in_carrier";
    private static final String VESSEL_ROUND = "df_vessel_round";
    private static final String STEP_READY = "df_step_ready";
    private static final String WHISPER_READY = "df_whisper_ready";
    private static final String STEAL_READY = "df_steal_ready";
    private static final String PENDING_FALSE = "df_pending_false";
    private static final String SPIRIT_MODE = "df_spirit";
    private static final String QUOTE_INTRO = "df_quote_intro";
    private static final String ARC_CD = "df_arc_cd";
    private static final String ARC_CAST = "df_arc_cast";
    private static final String CLONE_TAB = "df_clone_tab";
    private static final String CLONE_MONK = "df_clone_monk";
    private static final String CLONE_CULTIST = "df_clone_cultist";
    private static final String CLONE_GUARD = "df_clone_guard";
    private static final String CLONE_PHANTOM = "df_clone_phantom";
    private static final String CLONE_FALSE = "df_clone_false";
    private static final String CLONE_VESSEL = "df_clone_vessel";
    private static final String CLONE_SHARD = "df_clone_shard";
    private static final String BOSS_UUID = "df_boss_uuid";
    private static final String VESSEL_SLOT = "df_vessel_slot";
    private static final String SHARD_SPAWN_AT = "df_shard_at";
    private static final String PHANTOM_LIFE = "df_phantom_life";
    private static final String ADD_NEXT_ATK = "df_add_atk";

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
        put(data, HOME_X, npc.getX());
        put(data, HOME_Y, npc.getY());
        put(data, HOME_Z, npc.getZ());
        put(data, PHASE_KEY, "1");
        put(data, ABSORB_KEY, "0");
        put(data, INVULN_UNTIL, "0");
        put(data, TRANSITION, "0");
        put(data, BELL_FIRED, "");
        put(data, FALSE_FIRED, "");
        put(data, CYCLE_ORIGIN, "0");
        put(data, CYCLE_SHIFT, "0");
        put(data, CYCLE_SLOT, "0");
        put(data, IN_CARRIER, "0");
        put(data, CARRIER_UNTIL, "0");
        put(data, VESSEL_ROUND, "0");
        put(data, SPIRIT_MODE, "0");
        put(data, PENDING_FALSE, "0");
        final long now = now(npc);
        put(data, SEAL_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "sealFirstDelay", 40)));
        put(data, GAZE_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "gazeFirstDelay", 80)));
        put(data, BELL_READY, "0");
        put(data, COURT_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "courtFirstDelay", 80)));
        put(data, GAZE_FAR_SINCE, "0");
        put(data, STEP_READY, "0");
        put(data, WHISPER_READY, "0");
        put(data, STEAL_READY, "0");
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
            final String vessel,
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
        put(data, CLONE_VESSEL, vessel);
        put(data, CLONE_SHARD, shard);
    }

    /** JS: {@code Encounter.configure(npc, "sealDamage", 12, "arenaRadius", 12, ...)} */
    public static void configure(final ICustomNpc npc, final Object... keyValues) {
        DrachenfelsConfig.configure(npc, keyValues);
    }

    public static void tick(final ICustomNpc npc) {
        if (npc == null || !npc.isAlive() || isClient(npc)) {
            return;
        }
        if (!isBoss(npc)) {
            init(npc);
        }
        final IData data = npc.getStoreddata();
        final long now = now(npc);
        tickTransition(npc, data, now);
        if (ScriptDataUtil.isFlag(data, TRANSITION)) {
            return;
        }
        enforcePhaseCap(npc, data);
        updatePhase(npc, data, now);
        tickAdds(npc, data, now);
        tickPhantoms(npc, data);
        tickShards(npc, data);
        tickVesselVfx(npc, data, now);
        tickCarrier(npc, data, now);

        final int phase = ScriptDataUtil.getInt(data, PHASE_KEY);
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
        AbilityAPI.cancel(npc);
        killTaggedNear(npc, TAG_MONK, TAG_COURT, TAG_CULTIST, TAG_GUARD,
                TAG_PHANTOM, TAG_FALSE, TAG_VESSEL, TAG_SHARD);
        clearOwnerZones(npc);
        say(npc, "Замок не умрёт с этим телом.");
    }

    public static void onAbilityEnded(final ICustomNpc npc, final String abilityId) {
        if (npc == null || abilityId == null) {
            return;
        }
        final IData data = npc.getStoreddata();
        final long now = now(npc);
        if (DfBlackSealAbility.ID.equals(abilityId)) {
            put(data, SEAL_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "sealCd", SEAL_CD)));
        } else if (DfMaskGazeAbility.ID.equals(abilityId)) {
            put(data, GAZE_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "gazeCd", GAZE_CD)));
        } else if (DfFalseHostAbility.ID.equals(abilityId)) {
            final int shift = ScriptDataUtil.getInt(data, CYCLE_SHIFT);
            put(data, CYCLE_SHIFT, String.valueOf(shift + DrachenfelsConfig.getI(data, "falseShift", FALSE_SHIFT)));
            put(data, PENDING_FALSE, "0");
        } else if (DfNamelessStepAbility.ID.equals(abilityId)) {
            put(data, STEP_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "stepCd", STEP_CD)));
        } else if (DfNamelessWhisperAbility.ID.equals(abilityId)) {
            put(data, WHISPER_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "whisperCd", WHISPER_CD)));
        } else if (DfNameStealAbility.ID.equals(abilityId)) {
            put(data, STEAL_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "stealCd", STEAL_CD)));
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
        return now(npc) < ScriptDataUtil.getInt(data, INVULN_UNTIL)
                || ScriptDataUtil.isFlag(data, TRANSITION);
    }

    public static float getAbsorb(final ICustomNpc npc) {
        return ScriptDataUtil.getFloat(npc.getStoreddata(), ABSORB_KEY);
    }

    public static void setAbsorb(final ICustomNpc npc, final float value) {
        put(npc.getStoreddata(), ABSORB_KEY, String.valueOf(Math.max(0.0F, value)));
    }

    public static boolean hasLivingVessels(final ICustomNpc npc) {
        return countTaggedNear(npc, TAG_VESSEL) > 0;
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
        final ICustomNpc boss = findBossForAdd(monk);
        if (boss != null) {
            setAbsorb(boss, 0.0F);
        }
    }

    public static void onVesselDeath(final ICustomNpc vessel) {
        final ICustomNpc boss = findBossForAdd(vessel);
        if (boss == null) {
            return;
        }
        final IData vData = vessel.getStoreddata();
        final int slot = ScriptDataUtil.getInt(vData, VESSEL_SLOT);
        final double x = vessel.getX();
        final double y = vessel.getY();
        final double z = vessel.getZ();
        scheduleShardSpawn(boss, slot, x, y, z);
        // Dying vessel may still be counted until removed — treat as already dead.
        int alive = 0;
        for (final ICustomNpc v : findTagged(boss, TAG_VESSEL)) {
            if (v != null && v.isAlive() && !String.valueOf(v.getUUID()).equals(String.valueOf(vessel.getUUID()))) {
                alive++;
            }
        }
        if (alive <= 0) {
            startCarrierWindow(boss);
        }
    }

    public static void onShardDeath(final ICustomNpc shard) {
        // no heal — already handled if contact; death just removes
    }

    public static void spawnLeperPhantoms(
            final ICustomNpc boss, final int tab, final String cloneName, final double damage) {
        final double[] c = getArenaCenter(boss);
        final double spawnR = DrachenfelsConfig.getD(boss, "leperSpawnRadius", 11.0);
        final int life = DrachenfelsConfig.getI(boss, "leperDuration", 60);
        final double[] angles = {0.0, 90.0, 180.0, 270.0};
        for (int i = 0; i < angles.length; i++) {
            final double rad = Math.toRadians(angles[i]);
            final double x = c[0] + Math.cos(rad) * spawnR;
            final double z = c[2] + Math.sin(rad) * spawnR;
            final double y = AbilityCombatHelper.findGroundY(boss.getWorld(), x, z, c[1]);
            final ICustomNpc phantom = spawnClone(boss, tab, cloneName, x, y, z);
            if (phantom == null) {
                continue;
            }
            tagAdd(phantom, TAG_PHANTOM);
            put(phantom.getStoreddata(), BOSS_UUID, String.valueOf(boss.getUUID()));
            put(phantom.getStoreddata(), PHANTOM_LIFE, String.valueOf(life));
            put(phantom.getStoreddata(), "df_dmg", String.valueOf(damage));
            put(phantom.getStoreddata(), HOME_X, String.valueOf(x));
            put(phantom.getStoreddata(), HOME_Y, String.valueOf(y));
            put(phantom.getStoreddata(), HOME_Z, String.valueOf(z));
            setNoPhysics(phantom, true);
            setAiNone(phantom);
        }
    }

    public static void castFalseHost(final ICustomNpc boss, final int tab, final String cloneName) {
        final double ox = boss.getX();
        final double oy = boss.getY();
        final double oz = boss.getZ();
        final double copyDist = DrachenfelsConfig.getD(boss, "falseCopyDist", 3.0);
        for (int i = 0; i < 3; i++) {
            final double ang = RANDOM.nextDouble() * Math.PI * 2.0;
            final double x = ox + Math.cos(ang) * copyDist;
            final double z = oz + Math.sin(ang) * copyDist;
            final double y = AbilityCombatHelper.findGroundY(boss.getWorld(), x, z, oy);
            final ICustomNpc copy = spawnClone(boss, tab, cloneName, x, y, z);
            if (copy == null) {
                continue;
            }
            tagAdd(copy, TAG_FALSE);
            put(copy.getStoreddata(), BOSS_UUID, String.valueOf(boss.getUUID()));
            try {
                copy.setMaxHealth(1);
                final Object mc = copy.getMCEntity();
                if (mc instanceof LivingEntity) {
                    ((LivingEntity) mc).setHealth(1.0F);
                }
            } catch (final Exception ignored) {
            }
            setAiNone(copy);
        }
        final double[] c = getArenaCenter(boss);
        final double ring = DrachenfelsConfig.getD(boss, "falseTeleportRing", 5.0);
        final double ang = RANDOM.nextDouble() * Math.PI * 2.0;
        final double x = c[0] + Math.cos(ang) * ring;
        final double z = c[2] + Math.sin(ang) * ring;
        final double y = AbilityCombatHelper.findGroundY(boss.getWorld(), x, z, c[1]);
        boss.setPosition(x, y, z);
    }

    public static void despawnFalseHosts(final ICustomNpc boss) {
        killTaggedNear(boss, TAG_FALSE);
    }

    public static boolean pickNamelessStepTarget(final ActiveAbility active, final AbilityContext ctx) {
        final double[] c = getArenaCenter(ctx.npc);
        final double px = ctx.target != null ? ctx.target.getX() : c[0];
        final double pz = ctx.target != null ? ctx.target.getZ() : c[2];
        final double minPlayer = DrachenfelsConfig.getD(ctx.npc, "stepMinPlayerDist", 5.0);
        final double arenaR = getArenaRadius(ctx.npc);
        for (int i = 0; i < 24; i++) {
            final double ang = RANDOM.nextDouble() * Math.PI * 2.0;
            final double dist = 2.0 + RANDOM.nextDouble() * (arenaR - 2.5);
            final double x = c[0] + Math.cos(ang) * dist;
            final double z = c[2] + Math.sin(ang) * dist;
            if (AbilityCombatHelper.flatDistance(x, z, px, pz) < minPlayer) {
                continue;
            }
            if (AbilityCombatHelper.flatDistance(x, z, c[0], c[2]) > arenaR - 0.5) {
                continue;
            }
            active.sx = ctx.npc.getX();
            active.sy = ctx.npc.getY();
            active.sz = ctx.npc.getZ();
            active.ex = x;
            active.ez = z;
            active.ey = AbilityCombatHelper.findGroundY(ctx.world, x, z, c[1]);
            return true;
        }
        return false;
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
        setAbsorb(npc, 0.0F);
        killTaggedNear(npc, TAG_MONK, TAG_COURT, TAG_CULTIST, TAG_GUARD,
                TAG_PHANTOM, TAG_FALSE, TAG_VESSEL, TAG_SHARD);
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
        if (now < ScriptDataUtil.getInt(data, "df_next_phase_ready")) {
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
            spawnVesselSet(npc, true);
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
        maintainKite(npc, DrachenfelsConfig.getD(data, "kiteDistance", 6.0));
        tickBell(npc, data, now);
        if (AbilityAPI.isBusy(npc)) {
            return;
        }
        final IEntityLiving target = npc.getAttackTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        if (tryGaze(npc, data, now, target)) {
            return;
        }
        if (tryCourt(npc, data, now)) {
            return;
        }
        if (now >= ScriptDataUtil.getInt(data, SEAL_READY)) {
            AbilityAPI.start(npc, DfBlackSealAbility.ID, target, DrachenfelsConfig.sealParams(npc));
        }
    }

    private static void tickBell(final ICustomNpc npc, final IData data, final long now) {
        if (now < ScriptDataUtil.getInt(data, BELL_READY)) {
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
            applyBellShield(npc, data);
            return;
        }
    }

    private static void applyBellShield(final ICustomNpc npc, final IData data) {
        final float absorb = (float) (npc.getMaxHealth()
                * DrachenfelsConfig.getD(data, "absorbRatio", ABSORB_RATIO));
        setAbsorb(npc, absorb);
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
            final float monkHp = (float) DrachenfelsConfig.getD(data, "monkHp", 40.0);
            try {
                monk.setMaxHealth(monkHp);
                final Object mc = monk.getMCEntity();
                if (mc instanceof LivingEntity) {
                    ((LivingEntity) mc).setHealth(monkHp);
                }
            } catch (final Exception ignored) {
            }
        }
    }

    private static boolean tryGaze(
            final ICustomNpc npc, final IData data, final long now, final IEntityLiving target) {
        final double dist = AbilityCombatHelper.flatDistance(
                npc.getX(), npc.getZ(), target.getX(), target.getZ());
        final double gazeRange = DrachenfelsConfig.getD(data, "gazeRange", GAZE_RANGE);
        if (dist > gazeRange) {
            if (ScriptDataUtil.getInt(data, GAZE_FAR_SINCE) <= 0) {
                put(data, GAZE_FAR_SINCE, String.valueOf(now));
            }
        } else {
            put(data, GAZE_FAR_SINCE, "0");
            return false;
        }
        final long since = ScriptDataUtil.getInt(data, GAZE_FAR_SINCE);
        if (since <= 0 || now - since < DrachenfelsConfig.getI(data, "gazeFarTicks", GAZE_FAR_TICKS)) {
            return false;
        }
        if (now < ScriptDataUtil.getInt(data, GAZE_READY)) {
            return false;
        }
        return AbilityAPI.start(npc, DfMaskGazeAbility.ID, target, DrachenfelsConfig.gazeParams(npc));
    }

    private static boolean tryCourt(final ICustomNpc npc, final IData data, final long now) {
        if (now < ScriptDataUtil.getInt(data, COURT_READY)) {
            return false;
        }
        put(data, COURT_READY, String.valueOf(now + DrachenfelsConfig.getI(data, "courtCd", COURT_CD)));
        final int court = countTaggedNear(npc, TAG_COURT);
        final int monk = countTaggedNear(npc, TAG_MONK);
        if (court >= 2 || court + monk >= 3) {
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
        tagAdd(add, TAG_COURT);
        tagAdd(add, cultist ? TAG_CULTIST : TAG_GUARD);
        put(add.getStoreddata(), BOSS_UUID, String.valueOf(npc.getUUID()));
        put(add.getStoreddata(), ADD_NEXT_ATK, String.valueOf(now + 20));
        try {
            final float hp = cultist
                    ? (float) DrachenfelsConfig.getD(data, "cultistHp", 30.0)
                    : (float) DrachenfelsConfig.getD(data, "guardHp", 50.0);
            add.setMaxHealth(hp);
            final Object mc = add.getMCEntity();
            if (mc instanceof LivingEntity) {
                ((LivingEntity) mc).setHealth(hp);
            }
        } catch (final Exception ignored) {
        }
        return false; // court is not an AbilityAPI cast
    }

    private static void tickPhase2(final ICustomNpc npc, final IData data, final long now) {
        AbilityCombatHelper.holdInPlace(npc,
                ScriptDataUtil.getFloat(data, HOME_X),
                ScriptDataUtil.getFloat(data, HOME_Y),
                ScriptDataUtil.getFloat(data, HOME_Z));
        tickFalseHostTrigger(npc, data, now);
        if (AbilityAPI.isBusy(npc)) {
            return;
        }
        if (ScriptDataUtil.isFlag(data, PENDING_FALSE)) {
            final IEntityLiving target = npc.getAttackTarget();
            AbilityAPI.start(npc, DfFalseHostAbility.ID, target, DrachenfelsConfig.falseHostParams(npc));
            return;
        }
        long origin = ScriptDataUtil.getInt(data, CYCLE_ORIGIN);
        if (origin <= 0) {
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
            return;
        }
        final int slot = ScriptDataUtil.getInt(data, CYCLE_SLOT);
        final IEntityLiving target = npc.getAttackTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        if (slot == 0 && elapsed >= 0) {
            if (AbilityAPI.start(npc, DfImperialPoisonAbility.ID, target, DrachenfelsConfig.imperialParams(npc))) {
                put(data, CYCLE_SLOT, "1");
            }
        } else if (slot == 1 && elapsed >= DrachenfelsConfig.getI(data, "cycleFeastAt", 120)) {
            if (AbilityAPI.start(npc, DfFeastSeatsAbility.ID, target, DrachenfelsConfig.feastParams(npc))) {
                put(data, CYCLE_SLOT, "2");
            }
        } else if (slot == 2 && elapsed >= DrachenfelsConfig.getI(data, "cycleLeperAt", 220)) {
            if (AbilityAPI.start(npc, DfLeperBallAbility.ID, target, DrachenfelsConfig.leperParams(npc))) {
                put(data, CYCLE_SLOT, "3");
            }
        }
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
            return;
        }
    }

    private static void tickPhase3(final ICustomNpc npc, final IData data, final long now) {
        final boolean carrier = ScriptDataUtil.isFlag(data, IN_CARRIER);
        final boolean hasVessels = hasLivingVessels(npc);
        if (!carrier && !hasVessels && ScriptDataUtil.getInt(data, VESSEL_ROUND) > 0) {
            // waiting for carrier start handled in vessel death
        }
        if (AbilityAPI.isBusy(npc)) {
            return;
        }
        final IEntityLiving target = npc.getAttackTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        if (hasVessels && AbilityCombatHelper.flatDistance(
                npc.getX(), npc.getZ(), target.getX(), target.getZ())
                <= DrachenfelsConfig.getD(data, "stealRange", 3.0)
                && now >= ScriptDataUtil.getInt(data, STEAL_READY)) {
            if (AbilityAPI.start(npc, DfNameStealAbility.ID, target, DrachenfelsConfig.stealParams(npc))) {
                return;
            }
        }
        if (now >= ScriptDataUtil.getInt(data, WHISPER_READY)) {
            if (AbilityAPI.start(npc, DfNamelessWhisperAbility.ID, target, DrachenfelsConfig.whisperParams(npc))) {
                return;
            }
        }
        if (hasVessels && !carrier && now >= ScriptDataUtil.getInt(data, STEP_READY)) {
            AbilityAPI.start(npc, DfNamelessStepAbility.ID, target, DrachenfelsConfig.stepParams(npc));
        }
        if (carrier) {
            tickCarrierArc(npc, data, now, target);
        }
    }

    private static void startCarrierWindow(final ICustomNpc boss) {
        final IData data = boss.getStoreddata();
        final long now = now(boss);
        put(data, IN_CARRIER, "1");
        put(data, CARRIER_UNTIL, String.valueOf(now + DrachenfelsConfig.getI(data, "carrierTicks", CARRIER_TICKS)));
        put(data, SPIRIT_MODE, "0");
        put(data, ARC_CD, String.valueOf(now + 20));
        put(data, ARC_CAST, "0");
        applyCarrierAi(boss);
        say(boss, "Нет сосуда — нет хозяина. Бейте, пока я ещё плоть.");
    }

    private static void tickCarrier(final ICustomNpc npc, final IData data, final long now) {
        if (!ScriptDataUtil.isFlag(data, IN_CARRIER)) {
            return;
        }
        if (now < ScriptDataUtil.getInt(data, CARRIER_UNTIL)) {
            return;
        }
        if (!npc.isAlive() || npc.getHealth() <= 0.05F) {
            return;
        }
        // Missed window
        put(data, IN_CARRIER, "0");
        put(data, SPIRIT_MODE, "1");
        killTaggedNear(npc, TAG_SHARD);
        applySpiritAi(npc);
        spawnVesselSet(npc, false);
    }

    private static void tickCarrierArc(
            final ICustomNpc npc, final IData data, final long now, final IEntityLiving target) {
        if (AbilityAPI.isBusy(npc)) {
            return;
        }
        final int casting = ScriptDataUtil.getInt(data, ARC_CAST);
        if (casting > 0) {
            put(data, ARC_CAST, String.valueOf(casting - 1));
            AbilityCombatHelper.stopNavigation(npc);
            if (casting == 1) {
                face(npc, target);
                final double damage = DrachenfelsConfig.getD(data, "carrierArcDamage", 15.0);
                final double radius = DrachenfelsConfig.getD(data, "carrierArcRadius", 3.0);
                final IEntity[] list = npc.getWorld().getNearbyEntities(npc.getPos(), 4, -1);
                for (final IEntity ent : list) {
                    if (!AbilityCombatHelper.isHostileToBoss(npc, ent)) {
                        continue;
                    }
                    if (AbilityCombatHelper.flatDistance(
                            npc.getX(), npc.getZ(), ent.getX(), ent.getZ()) > radius) {
                        continue;
                    }
                    if (!isInFront(npc, ent, 70.0)) {
                        continue;
                    }
                    if (!AbilityCombatHelper.dealPureDamage(ent, (float) damage, false)) {
                        ent.damage((float) damage);
                    }
                    AbilityVfx.spawnHitParticle(npc.getWorld(), ent);
                }
                put(data, ARC_CD, String.valueOf(now + DrachenfelsConfig.getI(data, "carrierArcInterval", 50)));
            }
            return;
        }
        if (now < ScriptDataUtil.getInt(data, ARC_CD)) {
            return;
        }
        put(data, ARC_CAST, String.valueOf(DrachenfelsConfig.getI(data, "carrierArcCastTicks", 12)));
    }

    // -------------------------------------------------------------------------
    // Vessels / shards / adds
    // -------------------------------------------------------------------------

    private static void spawnVesselSet(final ICustomNpc boss, final boolean first) {
        final IData data = boss.getStoreddata();
        final int tab = ScriptDataUtil.getInt(data, CLONE_TAB);
        final String name = str(data, CLONE_VESSEL);
        if (name.isEmpty()) {
            return;
        }
        final double[] c = getArenaCenter(boss);
        final int[] slots = first ? new int[]{0, 1, 2} : new int[]{0, 1};
        final int hp = first
                ? DrachenfelsConfig.getI(data, "vesselHpFirst", 45)
                : DrachenfelsConfig.getI(data, "vesselHpRepeat", 30);
        final double vesselRing = DrachenfelsConfig.getD(data, "vesselRing", 10.0);
        final double[] angles = {0.0, 120.0, 240.0};
        for (int i = 0; i < slots.length; i++) {
            final int slot = slots[i];
            final double rad = Math.toRadians(angles[slot]);
            final double x = c[0] + Math.cos(rad) * vesselRing;
            final double z = c[2] + Math.sin(rad) * vesselRing;
            final double y = AbilityCombatHelper.findGroundY(boss.getWorld(), x, z, c[1]);
            final ICustomNpc vessel = spawnClone(boss, tab <= 0 ? 1 : tab, name, x, y, z);
            if (vessel == null) {
                continue;
            }
            tagAdd(vessel, TAG_VESSEL);
            put(vessel.getStoreddata(), BOSS_UUID, String.valueOf(boss.getUUID()));
            put(vessel.getStoreddata(), VESSEL_SLOT, String.valueOf(slot));
            setNoPhysics(vessel, true);
            setAiNone(vessel);
            try {
                vessel.setMaxHealth(hp);
                final Object mc = vessel.getMCEntity();
                if (mc instanceof LivingEntity) {
                    ((LivingEntity) mc).setHealth((float) hp);
                }
            } catch (final Exception ignored) {
            }
        }
        put(data, VESSEL_ROUND, String.valueOf(ScriptDataUtil.getInt(data, VESSEL_ROUND) + 1));
    }

    private static void scheduleShardSpawn(
            final ICustomNpc boss, final int slot, final double x, final double y, final double z) {
        final IData data = boss.getStoreddata();
        final long at = now(boss) + DrachenfelsConfig.getI(data, "shardDelayTicks", 5);
        final String pending = str(data, SHARD_SPAWN_AT);
        final String entry = slot + "," + x + "," + y + "," + z + "," + at;
        put(data, SHARD_SPAWN_AT, pending.isEmpty() ? entry : pending + ";" + entry);
    }

    private static void tickShards(final ICustomNpc boss, final IData data) {
        final long now = now(boss);
        final String pending = str(data, SHARD_SPAWN_AT);
        if (!pending.isEmpty()) {
            final StringBuilder remain = new StringBuilder();
            final String[] parts = pending.split(";");
            for (int i = 0; i < parts.length; i++) {
                final String p = parts[i].trim();
                if (p.isEmpty()) {
                    continue;
                }
                final String[] f = p.split(",");
                if (f.length < 5) {
                    continue;
                }
                final long at = parseLong(f[4]);
                if (now < at) {
                    if (remain.length() > 0) {
                        remain.append(';');
                    }
                    remain.append(p);
                    continue;
                }
                spawnShard(boss, data, parseDouble(f[1]), parseDouble(f[2]), parseDouble(f[3]));
            }
            put(data, SHARD_SPAWN_AT, remain.toString());
        }
        final List<ICustomNpc> shards = findTagged(boss, TAG_SHARD);
        for (final ICustomNpc shard : shards) {
            final double dx = boss.getX() - shard.getX();
            final double dz = boss.getZ() - shard.getZ();
            final double dist = Math.sqrt(dx * dx + dz * dz);
            final double touch = DrachenfelsConfig.getD(data, "shardTouchDist", 1.0);
            if (dist <= touch) {
                healBossFromShard(boss, data);
                shard.despawn();
                continue;
            }
            final double speed = DrachenfelsConfig.getD(data, "shardSpeed", 0.35);
            final double nx = shard.getX() + (dx / Math.max(0.01, dist)) * speed;
            final double nz = shard.getZ() + (dz / Math.max(0.01, dist)) * speed;
            final double ny = AbilityCombatHelper.findGroundY(boss.getWorld(), nx, nz, shard.getY());
            shard.setPosition(nx, ny, nz);
        }
    }

    private static void spawnShard(
            final ICustomNpc boss, final IData data, final double x, final double y, final double z) {
        final int tab = ScriptDataUtil.getInt(data, CLONE_TAB);
        final String name = str(data, CLONE_SHARD);
        if (name.isEmpty()) {
            return;
        }
        final ICustomNpc shard = spawnClone(boss, tab <= 0 ? 1 : tab, name, x, y, z);
        if (shard == null) {
            return;
        }
        tagAdd(shard, TAG_SHARD);
        put(shard.getStoreddata(), BOSS_UUID, String.valueOf(boss.getUUID()));
        setAiNone(shard);
        final float shardHp = (float) DrachenfelsConfig.getD(data, "shardHp", 20.0);
        try {
            shard.setMaxHealth(shardHp);
            final Object mc = shard.getMCEntity();
            if (mc instanceof LivingEntity) {
                ((LivingEntity) mc).setHealth(shardHp);
            }
        } catch (final Exception ignored) {
        }
    }

    private static void healBossFromShard(final ICustomNpc boss, final IData data) {
        final float heal = (float) (boss.getMaxHealth()
                * DrachenfelsConfig.getD(data, "shardHealRatio", SHARD_HEAL_RATIO));
        final float cap = (float) (boss.getMaxHealth()
                * DrachenfelsConfig.getD(data, "phase3Ratio", PHASE3_RATIO));
        try {
            final Object mc = boss.getMCEntity();
            if (mc instanceof LivingEntity) {
                final LivingEntity living = (LivingEntity) mc;
                living.setHealth(Math.min(cap, living.getHealth() + heal));
            }
        } catch (final Exception ignored) {
        }
        if (hasLivingVessels(boss)) {
            put(data, STEP_READY, "0");
        }
    }

    private static void tickVesselVfx(final ICustomNpc boss, final IData data, final long now) {
        if ((now % 8) != 0) {
            return;
        }
        final List<ICustomNpc> vessels = findTagged(boss, TAG_VESSEL);
        for (final ICustomNpc v : vessels) {
            AbilityVfx.spawnSoulThread(
                    boss.getWorld(),
                    v.getX(), v.getY() + 1.5, v.getZ(),
                    boss.getX(), boss.getY() + 1.2, boss.getZ());
        }
    }

    private static void tickPhantoms(final ICustomNpc boss, final IData data) {
        final double[] c = getArenaCenter(boss);
        final List<ICustomNpc> phantoms = findTagged(boss, TAG_PHANTOM);
        for (final ICustomNpc p : phantoms) {
            final IData pd = p.getStoreddata();
            int life = ScriptDataUtil.getInt(pd, PHANTOM_LIFE);
            final int maxLife = Math.max(1, DrachenfelsConfig.getI(data, "leperDuration", 60));
            life--;
            put(pd, PHANTOM_LIFE, String.valueOf(life));
            if (life <= 0) {
                p.despawn();
                continue;
            }
            final double sx = ScriptDataUtil.getFloat(pd, HOME_X);
            final double sz = ScriptDataUtil.getFloat(pd, HOME_Z);
            final double t = (maxLife - life) / (double) maxLife;
            final double x = sx + (c[0] - sx) * t;
            final double z = sz + (c[2] - sz) * t;
            final double y = AbilityCombatHelper.findGroundY(boss.getWorld(), x, z, c[1]);
            p.setPosition(x, y, z);
            final float dmg = ScriptDataUtil.getFloat(pd, "df_dmg");
            final double hitR = DrachenfelsConfig.getD(data, "leperHitRadius", 1.0);
            final IEntity[] near = p.getWorld().getNearbyEntities(p.getPos(), 2, 1);
            for (final IEntity ent : near) {
                if (!(ent instanceof IPlayer) || !ent.isAlive()) {
                    continue;
                }
                if (AbilityCombatHelper.flatDistance(p.getX(), p.getZ(), ent.getX(), ent.getZ()) > hitR) {
                    continue;
                }
                final String hitKey = "df_hit_" + ent.getUUID();
                if (ScriptDataUtil.isFlag(pd, hitKey)) {
                    continue;
                }
                ScriptDataUtil.setFlag(pd, hitKey, true);
                if (!AbilityCombatHelper.dealPureDamage(ent, dmg <= 0 ? 10.0F : dmg, false)) {
                    ent.damage(dmg <= 0 ? 10.0F : dmg);
                }
                AbilityCombatHelper.applyEffect(
                        ent,
                        Effects.MOVEMENT_SLOWDOWN,
                        DrachenfelsConfig.getI(data, "leperSlowDuration", 30),
                        DrachenfelsConfig.getI(data, "leperSlowAmp", 1));
            }
        }
    }

    private static void tickAdds(final ICustomNpc boss, final IData data, final long now) {
        final List<ICustomNpc> cultists = findTagged(boss, TAG_CULTIST);
        for (final ICustomNpc c : cultists) {
            if (now < ScriptDataUtil.getInt(c.getStoreddata(), ADD_NEXT_ATK)) {
                continue;
            }
            put(c.getStoreddata(), ADD_NEXT_ATK,
                    String.valueOf(now + DrachenfelsConfig.getI(data, "cultistInterval", 50)));
            final IEntityLiving target = boss.getAttackTarget();
            if (target == null || !target.isAlive()) {
                continue;
            }
            try {
                c.shootItem(target, c.getWorld().createItem("minecraft:snowball", 1), 40);
            } catch (final Exception ignored) {
            }
            if (AbilityCombatHelper.flatDistance(c.getX(), c.getZ(), target.getX(), target.getZ()) < 12) {
                if (RANDOM.nextFloat() < 0.35F) {
                    final float dmg = (float) DrachenfelsConfig.getD(data, "cultistDamage", 6.0);
                    if (!AbilityCombatHelper.dealPureDamage(target, dmg, false)) {
                        target.damage(dmg);
                    }
                }
            }
        }
        final List<ICustomNpc> guards = findTagged(boss, TAG_GUARD);
        for (final ICustomNpc g : guards) {
            final IData gd = g.getStoreddata();
            final int casting = ScriptDataUtil.getInt(gd, ARC_CAST);
            if (casting > 0) {
                put(gd, ARC_CAST, String.valueOf(casting - 1));
                AbilityCombatHelper.stopNavigation(g);
                if (casting == 1) {
                    final IEntityLiving target = boss.getAttackTarget();
                    if (target != null) {
                        face(g, target);
                    }
                    final double arcR = DrachenfelsConfig.getD(data, "guardArcRadius", 3.0);
                    final float gDmg = (float) DrachenfelsConfig.getD(data, "guardDamage", 10.0);
                    final IEntity[] list = g.getWorld().getNearbyEntities(g.getPos(), 4, -1);
                    for (final IEntity ent : list) {
                        if (!AbilityCombatHelper.isHostileToBoss(boss, ent)) {
                            continue;
                        }
                        if (AbilityCombatHelper.flatDistance(g.getX(), g.getZ(), ent.getX(), ent.getZ()) > arcR) {
                            continue;
                        }
                        if (!isInFront(g, ent, 70.0)) {
                            continue;
                        }
                        if (!AbilityCombatHelper.dealPureDamage(ent, gDmg, false)) {
                            ent.damage(gDmg);
                        }
                    }
                    put(gd, ADD_NEXT_ATK,
                            String.valueOf(now + DrachenfelsConfig.getI(data, "guardInterval", 60)));
                }
                continue;
            }
            if (now < ScriptDataUtil.getInt(gd, ADD_NEXT_ATK)) {
                continue;
            }
            put(gd, ARC_CAST, String.valueOf(DrachenfelsConfig.getI(data, "guardCastTicks", 16)));
        }
    }

    // -------------------------------------------------------------------------
    // AI helpers
    // -------------------------------------------------------------------------

    private static void applyPhase1Ai(final ICustomNpc npc) {
        try {
            final INPCAi ai = npc.getAi();
            ai.setWalkingSpeed(1);
            ai.setRetaliateType(0);
            ai.setMovingType(0);
        } catch (final Exception ignored) {
        }
        setMoveSpeed(npc, DrachenfelsConfig.getD(npc, "phase1Speed", 0.15));
    }

    private static void applyPhase2Ai(final ICustomNpc npc) {
        try {
            final INPCAi ai = npc.getAi();
            ai.setWalkingSpeed(0);
            ai.setRetaliateType(3);
            ai.setMovingType(0);
        } catch (final Exception ignored) {
        }
        setMoveSpeed(npc, 0.0);
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

    private static void applyCarrierAi(final ICustomNpc npc) {
        try {
            final INPCAi ai = npc.getAi();
            ai.setWalkingSpeed(2);
            ai.setRetaliateType(0);
            ai.setMovingType(0);
        } catch (final Exception ignored) {
        }
        setMoveSpeed(npc, DrachenfelsConfig.getD(npc, "carrierSpeed", 0.2));
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
        if (dist < preferred - 1.5) {
            final double dx = npc.getX() - target.getX();
            final double dz = npc.getZ() - target.getZ();
            final double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.01) {
                return;
            }
            final double[] c = getArenaCenter(npc);
            final double arenaR = getArenaRadius(npc);
            double nx = npc.getX() + (dx / len) * 0.35;
            double nz = npc.getZ() + (dz / len) * 0.35;
            if (AbilityCombatHelper.flatDistance(nx, nz, c[0], c[2]) > arenaR - 1.0) {
                return;
            }
            final double ny = AbilityCombatHelper.findGroundY(npc.getWorld(), nx, nz, npc.getY());
            npc.setPosition(nx, ny, nz);
        }
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

    private static void face(final ICustomNpc npc, final IEntityLiving target) {
        if (target == null) {
            return;
        }
        final double dx = target.getX() - npc.getX();
        final double dz = target.getZ() - npc.getZ();
        npc.setRotation(AbilityCombatHelper.computeYaw(dx, dz));
    }

    private static boolean isInFront(final ICustomNpc npc, final IEntity ent, final double halfAngle) {
        final double dx = ent.getX() - npc.getX();
        final double dz = ent.getZ() - npc.getZ();
        final double targetYaw = AbilityCombatHelper.computeYaw(dx, dz);
        double diff = Math.abs(targetYaw - npc.getRotation()) % 360.0;
        if (diff > 180.0) {
            diff = 360.0 - diff;
        }
        return diff <= halfAngle;
    }

    // -------------------------------------------------------------------------
    // Spawn / find / kill
    // -------------------------------------------------------------------------

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
                return (ICustomNpc) spawned;
            }
        } catch (final Exception ignored) {
        }
        return null;
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
