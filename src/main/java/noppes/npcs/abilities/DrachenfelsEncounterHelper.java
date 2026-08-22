package noppes.npcs.abilities;

import net.minecraft.entity.Entity;
import noppes.npcs.api.IPos;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.NpcAPI;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Server-side Drachenfels pair encounter: Immortal Bond, HP ritual, flame carousel,
 * spacing/kite/hover/home, board restore. Casting remains in thin JS.
 */
public final class DrachenfelsEncounterHelper {
    public static final String PAIR_TAG = "drachenfels";

    public static final int REVIVE_WINDOW_TICKS = 300;
    public static final float REVIVE_HP_RATIO = 0.30F;
    public static final double LINK_RADIUS = 48.0;
    public static final double AGRO_RANGE = 56.0;
    public static final double LEASH_RANGE = 72.0;

    private static final int RETALIATE_REVENGE = 0;
    private static final int RETALIATE_RETREAT = 2;
    private static final int RETALIATE_NONE = 3;
    private static final int NAV_GROUND = 0;
    private static final int NAV_FLYING = 1;
    private static final int MOVING_STANDING = 0;
    private static final int VISIBLE_NORMAL = 0;
    private static final int ENTITY_LIVING = 2;

    private static final double BODY_MIN_RANGE = 3.8;
    private static final double BODY_MAX_RANGE = 9.5;
    private static final double SPIRIT_MIN_RANGE = 6.5;
    private static final double SPIRIT_MAX_RANGE = 15.0;
    private static final int KITE_TICKS = 45;
    private static final int KITE_SPEED_BODY = 5;
    private static final int KITE_SPEED_SPIRIT = 6;
    private static final int CASTER_SPEED = 1;
    private static final double HOME_ARRIVE_DIST = 1.8;
    private static final int HOME_RETURN_DELAY_TICKS = 50;
    private static final double HOVER_AMP = 0.22;
    private static final double HOVER_MAX_DRIFT = 1.8;
    /** Душа парит выше; тело — чуть над землёй, чтобы не падать в ямы. */
    private static final double SPIRIT_HOVER_OFFSET = 1.2;
    private static final double BODY_HOVER_OFFSET = 0.4;

    /** Fallback spirit Y offset if script did not call {@link #configureArena}. */
    private static final double DEFAULT_RITUAL_SPIRIT_OFFSET_Y = 5.0;
    private static final int RITUAL_DURATION_TICKS = 80;
    private static final int RITUAL_TRANSFER_INTERVAL = 4;
    private static final int RITUAL_COOLDOWN_TICKS = 700;
    private static final float RITUAL_MIN_HP_GAP = 0.12F;
    private static final String HP_RITUAL_ID = "df_hp_ritual";

    /** Arena coords come from JS via {@link #configureArena} → storeddata. */
    private static final String CFG_RITUAL_X = "df_cfg_ritual_x";
    private static final String CFG_RITUAL_Y = "df_cfg_ritual_y";
    private static final String CFG_RITUAL_Z = "df_cfg_ritual_z";
    private static final String CFG_RITUAL_SPIRIT_DY = "df_cfg_ritual_spirit_dy";
    /** Format: {@code x,y,z;x,y,z;x,y,z;x,y,z} — exactly {@link #FLAME_ZONE_COUNT} points. */
    private static final String CFG_FLAMES = "df_cfg_flames";

    private static final int FLAME_ZONE_COUNT = 4;
    private static final double FLAME_RADIUS = 3.5;
    private static final float FLAME_DAMAGE = 5.0F;
    private static final int FLAME_DAMAGE_INTERVAL = 20;
    private static final int FLAME_FIRE_SECONDS = 3;
    private static final int FLAME_SHIFT_TICKS = 80;
    private static final int FLAME_LIFETIME = 400;
    private static final int FLAME_COLOR = 0xC0FF6020;
    private static final String FLAME_TAG = "drachenfels_flame";
    private static final String FLAME_ACTIVE = "active";
    private static final String FLAME_UUIDS = "uuids";
    private static final String FLAME_SLOTS = "slots";
    private static final String FLAME_NEXT = "next";

    private static final String ROLE_KEY = "df_role";
    private static final String PARTNER_UUID_KEY = "df_partner_uuid";
    private static final String PAIR_ID_KEY = "df_pair_id";
    private static final String PARTNER_DEAD_KEY = "df_partner_dead";
    private static final String REVIVE_UNTIL_KEY = "df_revive_until";
    private static final String DEAD_UUID_KEY = "df_dead_uuid";
    private static final String DEAD_X_KEY = "df_dead_x";
    private static final String DEAD_Y_KEY = "df_dead_y";
    private static final String DEAD_Z_KEY = "df_dead_z";
    private static final String DEAD_ROLE_KEY = "df_dead_role";
    private static final String DOWNED_KEY = "df_downed";
    private static final String DOWNED_X_KEY = "df_downed_x";
    private static final String DOWNED_Y_KEY = "df_downed_y";
    private static final String DOWNED_Z_KEY = "df_downed_z";
    private static final String HOME_X_KEY = "df_home_x";
    private static final String HOME_Y_KEY = "df_home_y";
    private static final String HOME_Z_KEY = "df_home_z";
    private static final String HOVER_Y_KEY = "df_hover_y";
    private static final String SAVED_RETALIATE_KEY = "df_saved_retaliate";
    private static final String SAVED_SPEED_KEY = "df_saved_speed";
    private static final String SAVED_VISIBLE_KEY = "df_saved_visible";
    private static final String PHASE_KEY = "df_phase";
    private static final String NEXT_CAST_KEY = "df_next_cast";
    private static final String FORCED_ABILITY_KEY = "df_forced_ability";
    private static final String LINKED_KEY = "df_linked";
    private static final String KITE_UNTIL_KEY = "df_kite_until";
    private static final String STANCE_READY_KEY = "df_stance_ready";
    private static final String LOST_AGGRO_SINCE_KEY = "df_lost_aggro_since";
    private static final String RITUAL_ACTIVE_KEY = "df_ritual_active";
    private static final String RITUAL_UNTIL_KEY = "df_ritual_until";
    private static final String RITUAL_LEADER_KEY = "df_ritual_leader";
    private static final String CD_PREFIX = "df_cd_";

    private static final String ABILITY_DARK_BLAST = "drachenfels_dark_blast";
    private static final String ABILITY_GHOST_PARASITE = "drachenfels_ghost_parasite";
    private static final String ABILITY_BODY_PULL = "drachenfels_body_pull";

    private static final String[] QUOTES_BOND = {
            "Связь нерасторжима!",
            "Пока один дышит — второй вернётся!",
            "Смерть — лишь пауза."
    };
    private static final String[] QUOTES_REVIVE = {
            "Встань. Мы ещё не закончили.",
            "Смерть отложена — по моей милости.",
            "Двое — одно бессмертие."
    };

    private static final Random RANDOM = new Random();

    private DrachenfelsEncounterHelper() {
    }

    // -------------------------------------------------------------------------
    // Public API surface
    // -------------------------------------------------------------------------

    public static void init(final ICustomNpc npc, final String role) {
        if (npc == null || isClient(npc)) {
            return;
        }
        final IData data = npc.getStoreddata();
        ensureRole(npc, data, role);
        if (!npc.hasTag(PAIR_TAG)) {
            npc.addTag(PAIR_TAG);
        }
        if (isBlank(str(data, HOME_X_KEY))) {
            put(data, HOME_X_KEY, npc.getX());
            put(data, HOME_Y_KEY, npc.getY());
            put(data, HOME_Z_KEY, npc.getZ());
        }
        if (isBlank(str(data, HOVER_Y_KEY))) {
            final float homeY = ScriptDataUtil.getFloat(data, HOME_Y_KEY);
            put(data, HOVER_Y_KEY, homeY + (float) hoverOffsetFor(getRole(data)));
        }
        if (!isDowned(npc)) {
            put(data, PHASE_KEY, "1");
            put(data, PARTNER_DEAD_KEY, "0");
            put(data, REVIVE_UNTIL_KEY, "0");
            put(data, DEAD_UUID_KEY, "");
            put(data, DOWNED_X_KEY, "");
            put(data, DOWNED_Y_KEY, "");
            put(data, DOWNED_Z_KEY, "");
        }
        put(data, FORCED_ABILITY_KEY, "");
        put(data, KITE_UNTIL_KEY, "0");
        put(data, LOST_AGGRO_SINCE_KEY, "0");
        put(data, RITUAL_ACTIVE_KEY, "0");
        put(data, RITUAL_UNTIL_KEY, "0");
        put(data, RITUAL_LEADER_KEY, "0");
        tryLinkPartner(npc);
        applyCasterStance(npc);
        put(data, STANCE_READY_KEY, "1");
    }

    /**
     * Arena world coords from JS (ritual pillar + 4 flame carousel points).
     * Synced to partner when linked. No world XYZ are hardcoded in Java.
     */
    public static void configureArena(
            final ICustomNpc npc,
            final double ritualX,
            final double ritualY,
            final double ritualZ,
            final double spiritOffsetY,
            final double f0x, final double f0y, final double f0z,
            final double f1x, final double f1y, final double f1z,
            final double f2x, final double f2y, final double f2z,
            final double f3x, final double f3y, final double f3z) {
        if (npc == null || isClient(npc)) {
            return;
        }
        final IData data = npc.getStoreddata();
        put(data, CFG_RITUAL_X, ritualX);
        put(data, CFG_RITUAL_Y, ritualY);
        put(data, CFG_RITUAL_Z, ritualZ);
        put(data, CFG_RITUAL_SPIRIT_DY, spiritOffsetY);
        put(data, CFG_FLAMES,
                fmtPoint(f0x, f0y, f0z) + ";"
                        + fmtPoint(f1x, f1y, f1z) + ";"
                        + fmtPoint(f2x, f2y, f2z) + ";"
                        + fmtPoint(f3x, f3y, f3z));
        final ICustomNpc partner = findPartner(npc);
        if (partner != null) {
            copyArenaConfig(data, partner.getStoreddata());
        }
    }

    public static void tickSlow(final ICustomNpc npc) {
        if (npc == null || !npc.isAlive() || isClient(npc)) {
            return;
        }
        if (isDowned(npc)) {
            tickDownedWatchdog(npc);
            return;
        }
        tryLinkPartner(npc);
        ensureCombatTarget(npc);
        updateBondAndPhase(npc);
        tickBondVfx(npc);
        tryCompleteRevive(npc);
        tickFlameCarousel(npc);
        tryRestoreBoardsAndStopFlame(npc);
        if (isRitualActive(npc)) {
            if (isDowned(npc) || !npc.isAlive()) {
                abortHpRitual(npc);
            }
            return;
        }
        tryReturnHomeIfIdle(npc);
        if (hasCombatTarget(npc)) {
            manageSpacing(npc);
        }
    }

    public static void tickFast(final ICustomNpc npc) {
        if (npc == null || isClient(npc)) {
            return;
        }
        if (!npc.isAlive()) {
            AbilityAPI.cancel(npc);
            abortHpRitual(npc);
            tryStopFlameCarouselOnDeath(npc);
            return;
        }
        if (isDowned(npc)) {
            AbilityAPI.cancel(npc);
            abortHpRitual(npc);
            freezeDownedNpc(npc);
            return;
        }
        if (isRitualActive(npc)) {
            tickHpRitual(npc);
            return;
        }
        tickHover(npc);
        if (AbilityAPI.isBusy(npc)) {
            // Во время каста не бегать за целью
            try {
                npc.getAi().setRetaliateType(RETALIATE_NONE);
                npc.getAi().setWalkingSpeed(0);
            } catch (final Exception ignored) {
            }
        } else {
            applyCasterStance(npc);
        }
    }

    public static void onTargetLost(final ICustomNpc npc) {
        if (npc == null || isClient(npc)) {
            return;
        }
        if (isRitualActive(npc)) {
            return;
        }
        AbilityAPI.cancel(npc);
        if (isInBondPhase(npc) || isDowned(npc)) {
            return;
        }
        final IEntityLiving retarget = ensureCombatTarget(npc);
        if (retarget != null) {
            clearLostAggroTimer(npc);
            return;
        }
        markLostAggro(npc);
    }

    public static void onDied(final ICustomNpc npc) {
        if (npc == null || isClient(npc)) {
            return;
        }
        abortHpRitual(npc);
        AbilityAPI.cancel(npc);
        tryStopFlameCarouselOnDeath(npc);

        final IData data = npc.getStoreddata();
        final IWorld world = npc.getWorld();
        if ("1".equals(str(data, PARTNER_DEAD_KEY))) {
            final ICustomNpc downed = findNpcByUuid(world, str(data, DEAD_UUID_KEY));
            if (downed != null && isDowned(downed)) {
                trulyKillDowned(downed);
            }
        }
        final ICustomNpc partner = findPartner(npc);
        if (partner != null && isDowned(partner)) {
            trulyKillDowned(partner);
        }
        clearBondFlags(data);
    }

    /**
     * Lethal decision for LivingHurt: {@code true} = absorb (cancel + downed or already downed),
     * {@code false} = allow true death / non-lethal hit.
     */
    public static boolean absorbLethal(final ICustomNpc npc, final float damage) {
        if (npc == null || isClient(npc) || !(damage > 0.0F)) {
            return false;
        }
        if (isDowned(npc)) {
            pinDownedPosition(npc);
            try {
                npc.setHealth(1.0F);
            } catch (final Exception ignored) {
            }
            return true;
        }
        final float hp = npc.getHealth();
        if (hp - damage > 0.5F) {
            return false;
        }

        tryLinkPartner(npc);
        ICustomNpc partner = findPartner(npc);
        if (partner == null || !partner.isAlive() || isDowned(partner)) {
            tryLinkPartner(npc);
            partner = findPartner(npc);
        }

        final IData data = npc.getStoreddata();
        if (partner != null && partner.isAlive() && isDowned(partner)) {
            trulyKillDowned(partner);
            clearBondFlags(data);
            return false;
        }
        if (partner == null || !partner.isAlive()) {
            clearBondFlags(data);
            return false;
        }

        enterDownedState(npc, partner);
        return true;
    }

    public static boolean startHpRitual(final ICustomNpc npc) {
        if (npc == null || !npc.isAlive() || isClient(npc)) {
            return false;
        }
        final IData data = npc.getStoreddata();
        final long now = npc.getWorld().getTotalTime();
        if (!canStartHpRitual(npc, data, now)) {
            return false;
        }
        final ICustomNpc partner = findPartner(npc);
        if (partner == null || !partner.isAlive() || isDowned(partner)) {
            return false;
        }

        AbilityAPI.cancel(npc);
        AbilityAPI.cancel(partner);

        final long until = now + RITUAL_DURATION_TICKS;
        final long cdUntil = now + RITUAL_COOLDOWN_TICKS;
        markRitualFlags(npc, until, true);
        markRitualFlags(partner, until, false);
        put(data, CD_PREFIX + HP_RITUAL_ID, String.valueOf(cdUntil));
        put(partner.getStoreddata(), CD_PREFIX + HP_RITUAL_ID, String.valueOf(cdUntil));

        final boolean npcIsBody = "body".equals(getRole(data));
        final ICustomNpc bodyNpc = npcIsBody ? npc : partner;
        final ICustomNpc spiritNpc = npcIsBody ? partner : npc;
        final double rx = ritualX(bodyNpc);
        final double ry = ritualY(bodyNpc);
        final double rz = ritualZ(bodyNpc);
        final double spiritDy = ritualSpiritDy(bodyNpc);
        try {
            bodyNpc.setPosition(rx, ry, rz);
            zeroMotion(bodyNpc);
        } catch (final Exception ignored) {
        }
        try {
            final double sy = ry + spiritDy;
            spiritNpc.setPosition(rx, sy, rz);
            zeroMotion(spiritNpc);
            put(spiritNpc.getStoreddata(), HOVER_Y_KEY, sy);
        } catch (final Exception ignored) {
        }
        freezeRitualNpc(bodyNpc);
        freezeRitualNpc(spiritNpc);

        final IWorld world = npc.getWorld();
        try {
            final IPos pos = NpcAPI.Instance().getIPos(rx, ry, rz);
            world.playSoundAt(pos, "minecraft:entity.evoker.prepare_summon", 1.0F, 0.7F);
            world.spawnParticle("minecraft:soul_fire_flame",
                    rx, ry + 1.0, rz, 0.35, 0.5, 0.35, 0.04, 24);
            world.spawnParticle("minecraft:soul",
                    rx, ry + spiritDy + 0.4, rz,
                    0.4, 0.35, 0.4, 0.05, 20);
            try {
                world.spawnParticle("wfm:fog",
                        rx, ry + 0.4, rz, 0.35, 0.08, 0.35, 0, 8);
            } catch (final Exception ignored) {
            }
        } catch (final Exception ignored) {
        }
        spawnRitualParticleLink(bodyNpc, spiritNpc);
        return true;
    }

    public static boolean isDowned(final ICustomNpc npc) {
        return npc != null && "1".equals(str(npc.getStoreddata(), DOWNED_KEY));
    }

    public static boolean isRitualActive(final ICustomNpc npc) {
        return npc != null && "1".equals(str(npc.getStoreddata(), RITUAL_ACTIVE_KEY));
    }

    public static boolean isBusyForCast(final ICustomNpc npc) {
        return isDowned(npc) || isRitualActive(npc);
    }

    public static String getPhase(final ICustomNpc npc) {
        if (npc == null) {
            return "1";
        }
        final String phase = str(npc.getStoreddata(), PHASE_KEY);
        if ("2".equals(phase) || "bond".equals(phase)) {
            return phase;
        }
        return "1";
    }

    public static String getRole(final ICustomNpc npc) {
        if (npc == null) {
            return "body";
        }
        return getRole(npc.getStoreddata());
    }

    public static IEntityLiving ensureCombatTarget(final ICustomNpc npc) {
        if (npc == null || isDowned(npc) || isClient(npc)) {
            return null;
        }
        IEntityLiving cur = null;
        try {
            cur = npc.getAttackTarget();
        } catch (final Exception ignored) {
        }
        // Revenge может повесить целью партнёра / призрака / thrall — для кастов только игроки.
        if (cur != null && cur.isAlive() && isCombatPlayerTarget(cur)) {
            if (distEntityToHome(npc, cur) > LEASH_RANGE) {
                try {
                    npc.setAttackTarget(null);
                } catch (final Exception ignored) {
                }
            } else {
                clearLostAggroTimer(npc);
                return cur;
            }
        } else if (cur != null) {
            try {
                npc.setAttackTarget(null);
            } catch (final Exception ignored) {
            }
        }
        final IEntityLiving best = findValidPlayer(npc, AGRO_RANGE, LEASH_RANGE);
        if (best != null) {
            try {
                npc.setAttackTarget(best);
            } catch (final Exception ignored) {
            }
            clearLostAggroTimer(npc);
            return best;
        }
        markLostAggro(npc);
        return null;
    }

    // -------------------------------------------------------------------------
    // Bond / downed
    // -------------------------------------------------------------------------

    private static void enterDownedState(final ICustomNpc npc, final ICustomNpc partner) {
        abortHpRitual(npc);
        AbilityAPI.cancel(npc);
        final IData data = npc.getStoreddata();
        final double x = npc.getX();
        final double y = npc.getY();
        final double z = npc.getZ();

        put(data, DOWNED_KEY, "1");
        put(data, PARTNER_DEAD_KEY, "0");
        put(data, REVIVE_UNTIL_KEY, "0");
        put(data, KITE_UNTIL_KEY, "0");
        put(data, DOWNED_X_KEY, x);
        put(data, DOWNED_Y_KEY, y);
        put(data, DOWNED_Z_KEY, z);

        try {
            npc.setHealth(1.0F);
        } catch (final Exception ignored) {
        }
        try {
            npc.setAttackTarget(null);
        } catch (final Exception ignored) {
        }
        try {
            npc.setPosition(x, y, z);
        } catch (final Exception ignored) {
        }

        try {
            final INPCAi ai = npc.getAi();
            put(data, SAVED_RETALIATE_KEY, String.valueOf(RETALIATE_NONE));
            put(data, SAVED_SPEED_KEY, String.valueOf(CASTER_SPEED));
            ai.setRetaliateType(RETALIATE_NONE);
            ai.setWalkingSpeed(0);
            ai.setMovingType(MOVING_STANDING);
            ai.setNavigationType("spirit".equals(getRole(data)) ? NAV_FLYING : NAV_GROUND);
        } catch (final Exception ignored) {
        }

        freezeDownedNpc(npc);

        try {
            if (isBlank(str(data, SAVED_VISIBLE_KEY))) {
                put(data, SAVED_VISIBLE_KEY, String.valueOf(npc.getDisplay().getVisible()));
            }
            npc.getDisplay().setVisible(VISIBLE_NORMAL);
        } catch (final Exception ignored) {
        }

        armPartnerBond(partner, npc);

        try {
            partner.say("§5§l" + QUOTES_BOND[RANDOM.nextInt(QUOTES_BOND.length)]);
        } catch (final Exception ignored) {
        }

        final IWorld world = npc.getWorld();
        try {
            world.spawnParticle("minecraft:soul", x, y + 1.0, z, 0.3, 0.5, 0.3, 0.04, 20);
            world.spawnParticle("minecraft:soul_fire_flame", x, y + 0.6, z, 0.25, 0.4, 0.25, 0.03, 16);
            try {
                world.spawnParticle("wfm:fog", x, y + 0.3, z, 0.4, 0.1, 0.4, 0, 6);
            } catch (final Exception ignored) {
            }
            world.playSoundAt(NpcAPI.Instance().getIPos(x, y, z), "minecraft:entity.wither.hurt", 0.8F, 0.6F);
        } catch (final Exception ignored) {
        }
    }

    private static void armPartnerBond(final ICustomNpc partner, final ICustomNpc downedNpc) {
        if (partner == null || downedNpc == null) {
            return;
        }
        final long now = partner.getWorld().getTotalTime();
        final IData dd = downedNpc.getStoreddata();
        final IData pd = partner.getStoreddata();
        final String role = getRole(dd);
        put(pd, PARTNER_DEAD_KEY, "1");
        put(pd, REVIVE_UNTIL_KEY, String.valueOf(now + REVIVE_WINDOW_TICKS));
        put(pd, DEAD_UUID_KEY, String.valueOf(downedNpc.getUUID()));
        put(pd, DEAD_X_KEY, str(dd, DOWNED_X_KEY));
        put(pd, DEAD_Y_KEY, str(dd, DOWNED_Y_KEY));
        put(pd, DEAD_Z_KEY, str(dd, DOWNED_Z_KEY));
        put(pd, DEAD_ROLE_KEY, role);
        put(pd, PHASE_KEY, "bond");
        put(pd, FORCED_ABILITY_KEY, getBondForcedAbility(getRole(pd)));
        put(pd, NEXT_CAST_KEY, String.valueOf(now + 10));
        ensurePairId(downedNpc, partner);
    }

    /** Bond pressure: только скиллы из плана rework. */
    private static String getBondForcedAbility(final String role) {
        return "spirit".equals(role) ? ABILITY_DARK_BLAST : ABILITY_BODY_PULL;
    }

    private static void pinDownedPosition(final ICustomNpc npc) {
        if (npc == null) {
            return;
        }
        final IData data = npc.getStoreddata();
        if (isBlank(str(data, DOWNED_X_KEY))) {
            return;
        }
        final double x = ScriptDataUtil.getFloat(data, DOWNED_X_KEY);
        final double y = ScriptDataUtil.getFloat(data, DOWNED_Y_KEY);
        final double z = ScriptDataUtil.getFloat(data, DOWNED_Z_KEY);
        try {
            npc.setPosition(x, y, z);
            zeroMotion(npc);
        } catch (final Exception ignored) {
        }
    }

    private static void freezeDownedNpc(final ICustomNpc npc) {
        if (npc == null || !isDowned(npc)) {
            return;
        }
        pinDownedPosition(npc);
        try {
            npc.setHealth(1.0F);
        } catch (final Exception ignored) {
        }
        try {
            npc.setAttackTarget(null);
        } catch (final Exception ignored) {
        }
        try {
            final INPCAi ai = npc.getAi();
            ai.setRetaliateType(RETALIATE_NONE);
            ai.setWalkingSpeed(0);
            ai.setMovingType(MOVING_STANDING);
            ai.setNavigationType("spirit".equals(getRole(npc.getStoreddata())) ? NAV_FLYING : NAV_GROUND);
        } catch (final Exception ignored) {
        }
    }

    private static void exitDownedState(final ICustomNpc npc, final float hpRatio) {
        final IData data = npc.getStoreddata();
        final double x = ScriptDataUtil.getFloat(data, DOWNED_X_KEY);
        final double y = ScriptDataUtil.getFloat(data, DOWNED_Y_KEY);
        final double z = ScriptDataUtil.getFloat(data, DOWNED_Z_KEY);

        put(data, DOWNED_KEY, "0");
        put(data, KITE_UNTIL_KEY, "0");
        if (!isBlank(str(data, DOWNED_X_KEY))) {
            try {
                npc.setPosition(x, y, z);
            } catch (final Exception ignored) {
            }
        }
        try {
            final float maxHp = npc.getMaxHealth();
            if (maxHp > 0.0F) {
                npc.setHealth(maxHp * hpRatio);
            }
        } catch (final Exception ignored) {
        }
        try {
            final int vis = data.has(SAVED_VISIBLE_KEY)
                    ? ScriptDataUtil.getInt(data, SAVED_VISIBLE_KEY)
                    : VISIBLE_NORMAL;
            npc.getDisplay().setVisible(vis);
        } catch (final Exception ignored) {
        }
        put(data, DOWNED_X_KEY, "");
        put(data, DOWNED_Y_KEY, "");
        put(data, DOWNED_Z_KEY, "");
        applyCasterStance(npc);
    }

    private static void trulyKillDowned(final ICustomNpc npc) {
        if (npc == null) {
            return;
        }
        final IData data = npc.getStoreddata();
        clearBondFlags(data);
        try {
            final int vis = data.has(SAVED_VISIBLE_KEY)
                    ? ScriptDataUtil.getInt(data, SAVED_VISIBLE_KEY)
                    : VISIBLE_NORMAL;
            npc.getDisplay().setVisible(vis);
        } catch (final Exception ignored) {
        }
        AbilityAPI.cancel(npc);
        try {
            npc.setHealth(0.0F);
        } catch (final Exception ignored) {
        }
        try {
            npc.kill();
        } catch (final Exception ignored) {
        }
    }

    private static void tickDownedWatchdog(final ICustomNpc npc) {
        AbilityAPI.cancel(npc);
        freezeDownedNpc(npc);
        final String myUuid = String.valueOf(npc.getUUID());
        final ICustomNpc partner = findPartner(npc);
        if (partner != null && partner.isAlive() && !isDowned(partner)) {
            final IData pd = partner.getStoreddata();
            if ("1".equals(str(pd, PARTNER_DEAD_KEY)) && myUuid.equals(str(pd, DEAD_UUID_KEY))) {
                return;
            }
            armPartnerBond(partner, npc);
            return;
        }
        if (partner == null || !partner.isAlive()) {
            trulyKillDowned(npc);
        }
    }

    private static void tryCompleteRevive(final ICustomNpc npc) {
        final IData data = npc.getStoreddata();
        if (!"1".equals(str(data, PARTNER_DEAD_KEY))) {
            return;
        }
        if (!npc.isAlive() || isDowned(npc)) {
            return;
        }
        final IWorld world = npc.getWorld();
        final long now = world.getTotalTime();
        final int until = ScriptDataUtil.getInt(data, REVIVE_UNTIL_KEY);
        if (until <= 0 || now < until) {
            return;
        }

        final String deadUuid = str(data, DEAD_UUID_KEY);
        final ICustomNpc downed = findNpcByUuid(world, deadUuid);
        if (downed == null || !downed.isAlive() || !isDowned(downed)) {
            put(data, PARTNER_DEAD_KEY, "0");
            put(data, REVIVE_UNTIL_KEY, "0");
            put(data, DEAD_UUID_KEY, "");
            return;
        }
        if (!deadUuid.equals(String.valueOf(downed.getUUID()))) {
            return;
        }

        exitDownedState(downed, REVIVE_HP_RATIO);
        linkPair(npc, downed);

        final IData sd = downed.getStoreddata();
        put(sd, PHASE_KEY, hpPhase(downed));
        put(sd, PARTNER_DEAD_KEY, "0");
        put(sd, REVIVE_UNTIL_KEY, "0");
        put(sd, DEAD_UUID_KEY, "");
        put(sd, FORCED_ABILITY_KEY, "");

        put(data, PARTNER_DEAD_KEY, "0");
        put(data, REVIVE_UNTIL_KEY, "0");
        put(data, DEAD_UUID_KEY, "");
        put(data, PHASE_KEY, hpPhase(npc));

        try {
            final IEntityLiving target = npc.getAttackTarget();
            if (target != null && target.isAlive()) {
                downed.setAttackTarget(target);
            }
        } catch (final Exception ignored) {
        }

        try {
            npc.say("§5§l" + QUOTES_REVIVE[RANDOM.nextInt(QUOTES_REVIVE.length)]);
        } catch (final Exception ignored) {
        }

        final double x = downed.getX();
        final double y = downed.getY();
        final double z = downed.getZ();
        try {
            world.spawnParticle("minecraft:soul_fire_flame", x, y + 1.0, z, 0.3, 0.6, 0.3, 0.05, 24);
            world.spawnParticle("minecraft:soul", x, y + 0.8, z, 0.35, 0.4, 0.35, 0.04, 16);
            try {
                world.spawnParticle("wfm:fog", x, y + 0.4, z, 0.5, 0.15, 0.5, 0, 8);
                world.spawnParticle("wfm:fog_wall", x, y + 0.2, z, 0.3, 0.08, 0.3, 0, 3);
            } catch (final Exception ignored) {
            }
            world.playSoundAt(NpcAPI.Instance().getIPos(x, y, z), "minecraft:entity.wither.spawn", 0.7F, 1.3F);
        } catch (final Exception ignored) {
        }
    }

    private static void updateBondAndPhase(final ICustomNpc npc) {
        final IData data = npc.getStoreddata();
        if (isDowned(npc)) {
            return;
        }
        final boolean partnerDead = "1".equals(str(data, PARTNER_DEAD_KEY));
        if (partnerDead) {
            final int until = ScriptDataUtil.getInt(data, REVIVE_UNTIL_KEY);
            final long now = npc.getWorld().getTotalTime();
            if (until > 0 && now < until) {
                if (!"bond".equals(str(data, PHASE_KEY))) {
                    put(data, PHASE_KEY, "bond");
                }
                return;
            }
        }
        final float maxHealth = npc.getMaxHealth();
        if (maxHealth <= 0.0F) {
            return;
        }
        final String newPhase = (npc.getHealth() / maxHealth) <= 0.5F ? "2" : "1";
        final String oldPhase = str(data, PHASE_KEY);
        if ("bond".equals(oldPhase) && partnerDead) {
            return;
        }
        if (newPhase.equals(oldPhase)) {
            return;
        }
        put(data, PHASE_KEY, newPhase);
        if ("2".equals(newPhase)) {
            final String role = getRole(data);
            put(data, FORCED_ABILITY_KEY,
                    "spirit".equals(role) ? ABILITY_DARK_BLAST : ABILITY_BODY_PULL);
            try {
                npc.say("spirit".equals(role)
                        ? "§5§lБезымянный гнев пробудился!"
                        : "§4§lВеликий Чародей раскрывает силу!");
            } catch (final Exception ignored) {
            }
        }
    }

    private static void tickBondVfx(final ICustomNpc npc) {
        final IData data = npc.getStoreddata();
        if (!"1".equals(str(data, PARTNER_DEAD_KEY))) {
            return;
        }
        final long now = npc.getWorld().getTotalTime();
        if (now % 10 != 0) {
            return;
        }
        final ICustomNpc downed = findNpcByUuid(npc.getWorld(), str(data, DEAD_UUID_KEY));
        final double dx = downed != null ? downed.getX() : ScriptDataUtil.getFloat(data, DEAD_X_KEY);
        final double dy = downed != null ? downed.getY() : ScriptDataUtil.getFloat(data, DEAD_Y_KEY);
        final double dz = downed != null ? downed.getZ() : ScriptDataUtil.getFloat(data, DEAD_Z_KEY);
        final IWorld world = npc.getWorld();
        try {
            final int steps = 8;
            for (int i = 0; i <= steps; i++) {
                final double t = i / (double) steps;
                final double x = npc.getX() + (dx - npc.getX()) * t;
                final double y = npc.getY() + 1.0 + (dy + 0.5 - npc.getY() - 1.0) * t;
                final double z = npc.getZ() + (dz - npc.getZ()) * t;
                world.spawnParticle("minecraft:soul_fire_flame", x, y, z, 0, 0.02, 0, 0, 1);
                if (i % 2 == 0) {
                    world.spawnParticle("minecraft:soul", x, y + 0.1, z, 0, 0.02, 0, 0.01, 1);
                }
            }
            world.spawnParticle("minecraft:soul", dx, dy + 0.8, dz, 0.1, 0.06, 0.1, 0.02, 5);
            world.spawnParticle("minecraft:soul_fire_flame", dx, dy + 0.5, dz, 0.15, 0.08, 0.15, 0.02, 4);
        } catch (final Exception ignored) {
        }
    }

    private static void clearBondFlags(final IData data) {
        if (data == null) {
            return;
        }
        put(data, DOWNED_KEY, "0");
        put(data, PARTNER_DEAD_KEY, "0");
        put(data, REVIVE_UNTIL_KEY, "0");
        put(data, DEAD_UUID_KEY, "");
        put(data, KITE_UNTIL_KEY, "0");
        put(data, DOWNED_X_KEY, "");
        put(data, DOWNED_Y_KEY, "");
        put(data, DOWNED_Z_KEY, "");
        put(data, FORCED_ABILITY_KEY, "");
        put(data, LOST_AGGRO_SINCE_KEY, "0");
    }

    // -------------------------------------------------------------------------
    // Pair link
    // -------------------------------------------------------------------------

    private static void ensureRole(final ICustomNpc npc, final IData data, final String roleArg) {
        final String configured = normalizeRole(roleArg);
        if (!configured.isEmpty()) {
            put(data, ROLE_KEY, configured);
            return;
        }
        final String existing = str(data, ROLE_KEY);
        if ("body".equals(existing) || "spirit".equals(existing)) {
            return;
        }
        String name = "";
        try {
            name = String.valueOf(npc.getName()).toLowerCase(Locale.ROOT);
        } catch (final Exception ignored) {
        }
        if (name.contains("spirit") || name.contains("дух") || name.contains("nameless")) {
            put(data, ROLE_KEY, "spirit");
        } else {
            put(data, ROLE_KEY, "body");
        }
    }

    private static String normalizeRole(final String role) {
        if (role == null) {
            return "";
        }
        final String r = role.trim().toLowerCase(Locale.ROOT);
        if ("spirit".equals(r) || "дух".equals(r)) {
            return "spirit";
        }
        if ("body".equals(r) || "тело".equals(r)) {
            return "body";
        }
        return "";
    }

    private static String getRole(final IData data) {
        return "spirit".equals(str(data, ROLE_KEY)) ? "spirit" : "body";
    }

    private static ICustomNpc findPartner(final ICustomNpc npc) {
        final String uuid = str(npc.getStoreddata(), PARTNER_UUID_KEY);
        if (uuid.isEmpty()) {
            return null;
        }
        return findNpcByUuid(npc.getWorld(), uuid);
    }

    private static void tryLinkPartner(final ICustomNpc npc) {
        final IData data = npc.getStoreddata();
        if (isDowned(npc)) {
            return;
        }
        final String partnerUuid = str(data, PARTNER_UUID_KEY);
        if (!partnerUuid.isEmpty()) {
            final ICustomNpc existing = findNpcByUuid(npc.getWorld(), partnerUuid);
            if (existing != null && existing.isAlive()) {
                put(data, LINKED_KEY, "1");
                ensurePairId(npc, existing);
                return;
            }
        }

        final IWorld world = npc.getWorld();
        final IPos pos = NpcAPI.Instance().getIPos(npc.getX(), npc.getY(), npc.getZ());
        final IEntity[] nearby = world.getNearbyEntities(pos, (int) LINK_RADIUS, ENTITY_LIVING);
        ICustomNpc best = null;
        double bestDist = LINK_RADIUS + 1.0;
        final String myUuid = String.valueOf(npc.getUUID());
        final String myRole = getRole(data);

        for (final IEntity otherEnt : nearby) {
            if (!(otherEnt instanceof ICustomNpc)) {
                continue;
            }
            final ICustomNpc other = (ICustomNpc) otherEnt;
            if (!other.isAlive() || myUuid.equals(String.valueOf(other.getUUID()))) {
                continue;
            }
            if (!other.hasTag(PAIR_TAG) || isDowned(other)) {
                continue;
            }
            final IData od = other.getStoreddata();
            ensureRole(other, od, null);
            if (getRole(od).equals(myRole)) {
                continue;
            }
            final double d = flatDistance(npc, other);
            if (d < bestDist) {
                bestDist = d;
                best = other;
            }
        }
        if (best == null) {
            put(data, LINKED_KEY, "0");
            return;
        }
        linkPair(npc, best);
    }

    private static void linkPair(final ICustomNpc a, final ICustomNpc b) {
        final IData da = a.getStoreddata();
        final IData db = b.getStoreddata();
        put(da, PARTNER_UUID_KEY, String.valueOf(b.getUUID()));
        put(db, PARTNER_UUID_KEY, String.valueOf(a.getUUID()));
        put(da, LINKED_KEY, "1");
        put(db, LINKED_KEY, "1");
        ensurePairId(a, b);
    }

    private static void ensurePairId(final ICustomNpc a, final ICustomNpc b) {
        final IData da = a.getStoreddata();
        final IData db = b.getStoreddata();
        String existing = str(da, PAIR_ID_KEY);
        if (!existing.isEmpty()) {
            put(db, PAIR_ID_KEY, existing);
            syncArenaConfig(a, b);
            return;
        }
        existing = str(db, PAIR_ID_KEY);
        if (!existing.isEmpty()) {
            put(da, PAIR_ID_KEY, existing);
            syncArenaConfig(a, b);
            return;
        }
        final String ua = String.valueOf(a.getUUID());
        final String ub = String.valueOf(b.getUUID());
        final String pairId = ua.compareTo(ub) < 0 ? ua + "_" + ub : ub + "_" + ua;
        put(da, PAIR_ID_KEY, pairId);
        put(db, PAIR_ID_KEY, pairId);
        syncArenaConfig(a, b);
    }

    private static void syncArenaConfig(final ICustomNpc a, final ICustomNpc b) {
        if (a == null || b == null) {
            return;
        }
        final IData da = a.getStoreddata();
        final IData db = b.getStoreddata();
        if (hasArenaConfig(da)) {
            copyArenaConfig(da, db);
        } else if (hasArenaConfig(db)) {
            copyArenaConfig(db, da);
        }
    }

    private static boolean hasArenaConfig(final IData data) {
        return data != null && !isBlank(str(data, CFG_RITUAL_X)) && !isBlank(str(data, CFG_FLAMES));
    }

    private static void copyArenaConfig(final IData from, final IData to) {
        if (from == null || to == null) {
            return;
        }
        put(to, CFG_RITUAL_X, str(from, CFG_RITUAL_X));
        put(to, CFG_RITUAL_Y, str(from, CFG_RITUAL_Y));
        put(to, CFG_RITUAL_Z, str(from, CFG_RITUAL_Z));
        put(to, CFG_RITUAL_SPIRIT_DY, str(from, CFG_RITUAL_SPIRIT_DY));
        put(to, CFG_FLAMES, str(from, CFG_FLAMES));
    }

    private static String fmtPoint(final double x, final double y, final double z) {
        return x + "," + y + "," + z;
    }

    private static boolean hasFlameConfig(final ICustomNpc npc) {
        return npc != null && !isBlank(str(npc.getStoreddata(), CFG_FLAMES));
    }

    private static double ritualX(final ICustomNpc npc) {
        return arenaCoord(npc, CFG_RITUAL_X, HOME_X_KEY, npc.getX());
    }

    private static double ritualY(final ICustomNpc npc) {
        return arenaCoord(npc, CFG_RITUAL_Y, HOME_Y_KEY, npc.getY());
    }

    private static double ritualZ(final ICustomNpc npc) {
        return arenaCoord(npc, CFG_RITUAL_Z, HOME_Z_KEY, npc.getZ());
    }

    private static double ritualSpiritDy(final ICustomNpc npc) {
        if (npc == null) {
            return DEFAULT_RITUAL_SPIRIT_OFFSET_Y;
        }
        final IData data = npc.getStoreddata();
        if (!isBlank(str(data, CFG_RITUAL_SPIRIT_DY))) {
            return ScriptDataUtil.getFloat(data, CFG_RITUAL_SPIRIT_DY);
        }
        return DEFAULT_RITUAL_SPIRIT_OFFSET_Y;
    }

    private static double arenaCoord(
            final ICustomNpc npc,
            final String cfgKey,
            final String homeKey,
            final double liveFallback) {
        if (npc == null) {
            return liveFallback;
        }
        final IData data = npc.getStoreddata();
        if (!isBlank(str(data, cfgKey))) {
            return ScriptDataUtil.getFloat(data, cfgKey);
        }
        if (!isBlank(str(data, homeKey))) {
            return ScriptDataUtil.getFloat(data, homeKey);
        }
        return liveFallback;
    }

    /** @return {@code [x,y,z]} or {@code null} if slot missing / unconfigured */
    private static double[] flamePoint(final ICustomNpc npc, final int index) {
        if (npc == null || index < 0 || index >= FLAME_ZONE_COUNT) {
            return null;
        }
        final List<String> points = parseList(str(npc.getStoreddata(), CFG_FLAMES));
        if (index >= points.size()) {
            return null;
        }
        final String raw = points.get(index);
        if (raw == null) {
            return null;
        }
        final String[] parts = raw.split(",");
        if (parts.length < 3) {
            return null;
        }
        try {
            return new double[]{
                    Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim())
            };
        } catch (final Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Spacing / hover / home
    // -------------------------------------------------------------------------

    private static void manageSpacing(final ICustomNpc npc) {
        if (npc == null || !npc.isAlive() || isDowned(npc) || isRitualActive(npc)) {
            return;
        }
        if (AbilityAPI.isBusy(npc)) {
            applyCasterStance(npc);
            return;
        }
        final IData data = npc.getStoreddata();
        final long now = npc.getWorld().getTotalTime();
        IEntityLiving target = null;
        try {
            target = npc.getAttackTarget();
        } catch (final Exception ignored) {
        }
        if (target == null || !target.isAlive()) {
            applyCasterStance(npc);
            put(data, KITE_UNTIL_KEY, "0");
            return;
        }
        final String role = getRole(data);
        final double dist = flatDistance(npc, target);
        final double minR = "spirit".equals(role) ? SPIRIT_MIN_RANGE : BODY_MIN_RANGE;
        final double maxR = "spirit".equals(role) ? SPIRIT_MAX_RANGE : BODY_MAX_RANGE;

        if (dist < minR) {
            put(data, KITE_UNTIL_KEY, String.valueOf(now + KITE_TICKS));
            applyKiteStance(npc);
            return;
        }
        if (now < ScriptDataUtil.getInt(data, KITE_UNTIL_KEY)) {
            applyKiteStance(npc);
            return;
        }
        final String forced = str(data, FORCED_ABILITY_KEY);
        if (forced.isEmpty() && dist > maxR) {
            // Дальний gap: Душа — dark blast / parasite; Тело — pull (без старых seeker/step)
            if ("spirit".equals(role)) {
                if (isCooldownReady(data, now, ABILITY_DARK_BLAST)) {
                    put(data, FORCED_ABILITY_KEY, ABILITY_DARK_BLAST);
                } else if (isCooldownReady(data, now, ABILITY_GHOST_PARASITE)) {
                    put(data, FORCED_ABILITY_KEY, ABILITY_GHOST_PARASITE);
                }
            } else if (isCooldownReady(data, now, ABILITY_BODY_PULL)) {
                put(data, FORCED_ABILITY_KEY, ABILITY_BODY_PULL);
            }
        }
        applyCasterStance(npc);
    }

    private static double hoverOffsetFor(final String role) {
        return "spirit".equals(role) ? SPIRIT_HOVER_OFFSET : BODY_HOVER_OFFSET;
    }

    /**
     * Both body and spirit use Flying + soft hover above solid ground.
     * Body stays ~0.4 above terrain so it does not fall into pits.
     */
    private static void tickHover(final ICustomNpc npc) {
        final IData data = npc.getStoreddata();
        if (isDowned(npc) || isRitualActive(npc)) {
            return;
        }
        try {
            npc.getAi().setNavigationType(NAV_FLYING);
        } catch (final Exception ignored) {
        }
        if (AbilityAPI.isBusy(npc)) {
            return;
        }
        try {
            final String role = getRole(data);
            final double x = npc.getX();
            final double z = npc.getZ();
            final double y = npc.getY();
            final double groundY = AbilityCombatHelper.findGroundY(npc.getWorld(), x, z, y);
            final double hoverY = groundY + hoverOffsetFor(role);
            put(data, HOVER_Y_KEY, hoverY);

            final long t = npc.getWorld().getTotalTime();
            final double targetY = hoverY + Math.sin(t * 0.07) * HOVER_AMP;
            final double dy = targetY - y;
            if (y > hoverY + HOVER_MAX_DRIFT) {
                npc.setPosition(x, hoverY + HOVER_AMP, z);
                npc.setMotionY(-0.1);
                return;
            }
            if (y < hoverY - HOVER_MAX_DRIFT) {
                npc.setPosition(x, hoverY - HOVER_AMP, z);
                npc.setMotionY(0.05);
                return;
            }
            double corr = dy * 0.15;
            if (corr > 0.06) {
                corr = 0.06;
            }
            if (corr < -0.06) {
                corr = -0.06;
            }
            npc.setMotionY(corr);
        } catch (final Exception ignored) {
        }
    }

    private static void tryReturnHomeIfIdle(final ICustomNpc npc) {
        if (npc == null || !npc.isAlive() || isDowned(npc) || isInBondPhase(npc)) {
            return;
        }
        if (isRitualActive(npc) || AbilityAPI.isBusy(npc)) {
            return;
        }
        if (hasCombatTarget(npc)) {
            clearLostAggroTimer(npc);
            return;
        }
        if (findPlayerInLeash(npc) != null) {
            ensureCombatTarget(npc);
            return;
        }
        markLostAggro(npc);
        if (!hasLostAggroLongEnough(npc)) {
            return;
        }
        if (distToHomeFlat(npc) <= HOME_ARRIVE_DIST) {
            clearLostAggroTimer(npc);
            tickHover(npc);
            return;
        }
        returnToHome(npc);
    }

    private static void returnToHome(final ICustomNpc npc) {
        if (npc == null || !npc.isAlive() || isDowned(npc) || isInBondPhase(npc)) {
            return;
        }
        if (isInActiveCombat(npc) || findPlayerInLeash(npc) != null) {
            return;
        }
        final IData data = npc.getStoreddata();
        if (!hasHome(data)) {
            return;
        }
        double x = ScriptDataUtil.getFloat(data, HOME_X_KEY);
        double y = ScriptDataUtil.getFloat(data, HOME_Y_KEY);
        double z = ScriptDataUtil.getFloat(data, HOME_Z_KEY);
        final double hoverY = y + hoverOffsetFor(getRole(data));
        put(data, HOVER_Y_KEY, hoverY);
        y = hoverY;
        AbilityAPI.cancel(npc);
        put(data, KITE_UNTIL_KEY, "0");
        put(data, FORCED_ABILITY_KEY, "");
        put(data, NEXT_CAST_KEY, "0");
        put(data, LOST_AGGRO_SINCE_KEY, "0");
        try {
            npc.setAttackTarget(null);
        } catch (final Exception ignored) {
        }
        try {
            npc.setPosition(x, y, z);
            zeroMotion(npc);
        } catch (final Exception ignored) {
        }
        applyCasterStance(npc);
        try {
            final IWorld world = npc.getWorld();
            world.spawnParticle("minecraft:soul_fire_flame", x, y + 0.5, z, 0.2, 0.3, 0.2, 0.02, 8);
        } catch (final Exception ignored) {
        }
    }

    private static void applyCasterStance(final ICustomNpc npc) {
        if (npc == null || isDowned(npc)) {
            return;
        }
        try {
            final INPCAi ai = npc.getAi();
            // Revenge держит агро; NONE каждый тик сбрасывал цель и ломал касты.
            ai.setRetaliateType(RETALIATE_REVENGE);
            ai.setMovingType(MOVING_STANDING);
            ai.setWalkingSpeed(CASTER_SPEED);
            // Body + spirit: Flying, чтобы тело не проваливалось в ямы
            ai.setNavigationType(NAV_FLYING);
            try {
                ai.setLeapAtTarget(false);
            } catch (final Exception ignored) {
            }
        } catch (final Exception ignored) {
        }
    }

    private static void applyKiteStance(final ICustomNpc npc) {
        if (npc == null || isDowned(npc)) {
            return;
        }
        try {
            final INPCAi ai = npc.getAi();
            final String role = getRole(npc.getStoreddata());
            ai.setRetaliateType(RETALIATE_RETREAT);
            ai.setMovingType(MOVING_STANDING);
            ai.setWalkingSpeed("spirit".equals(role) ? KITE_SPEED_SPIRIT : KITE_SPEED_BODY);
            ai.setNavigationType(NAV_FLYING);
        } catch (final Exception ignored) {
        }
    }

    // -------------------------------------------------------------------------
    // HP ritual tick
    // -------------------------------------------------------------------------

    private static boolean canStartHpRitual(final ICustomNpc npc, final IData data, final long now) {
        if (npc == null || data == null) {
            return false;
        }
        if (!"spirit".equals(getRole(data))) {
            return false;
        }
        if (isDowned(npc) || isRitualActive(npc) || "1".equals(str(data, PARTNER_DEAD_KEY))) {
            return false;
        }
        if (!isCooldownReady(data, now, HP_RITUAL_ID)) {
            return false;
        }
        final ICustomNpc partner = findPartner(npc);
        if (partner == null || !partner.isAlive() || isDowned(partner) || isRitualActive(partner)) {
            return false;
        }
        if (AbilityAPI.isBusy(partner) && RANDOM.nextFloat() < 0.35F) {
            return false;
        }
        return Math.abs(hpRatio(npc) - hpRatio(partner)) >= RITUAL_MIN_HP_GAP;
    }

    private static void tickHpRitual(final ICustomNpc npc) {
        if (npc == null || !npc.isAlive()) {
            abortHpRitual(npc);
            return;
        }
        final IData data = npc.getStoreddata();
        if (!isRitualActive(npc)) {
            return;
        }
        final long now = npc.getWorld().getTotalTime();
        final int until = ScriptDataUtil.getInt(data, RITUAL_UNTIL_KEY);
        final ICustomNpc partner = findPartner(npc);
        if (partner == null || !partner.isAlive() || isDowned(partner) || isDowned(npc)) {
            abortHpRitual(npc);
            return;
        }
        if (!isRitualActive(partner)) {
            markRitualFlags(partner, until, false);
        }
        AbilityAPI.cancel(npc);
        freezeRitualNpc(npc);
        if (now >= until) {
            endHpRitual(npc);
            return;
        }
        if (!"1".equals(str(data, RITUAL_LEADER_KEY))) {
            return;
        }
        if (now % 2 == 0) {
            spawnRitualParticleLink(npc, partner);
        }
        if (now % RITUAL_TRANSFER_INTERVAL == 0) {
            AbilityCombatHelper.transferDrachenfelsRitualHp(npc, partner);
        }
    }

    private static void endHpRitual(final ICustomNpc npc) {
        if (npc == null) {
            return;
        }
        final ICustomNpc partner = findPartner(npc);
        clearRitualFlags(npc);
        applyCasterStance(npc);
        if (partner != null) {
            clearRitualFlags(partner);
            applyCasterStance(partner);
            try {
                final IWorld world = npc.getWorld();
                final double mx = (npc.getX() + partner.getX()) * 0.5;
                final double my = (npc.getY() + partner.getY()) * 0.5;
                final double mz = (npc.getZ() + partner.getZ()) * 0.5;
                world.spawnParticle("minecraft:soul", mx, my, mz, 0.4, 0.5, 0.4, 0.06, 18);
                world.playSoundAt(NpcAPI.Instance().getIPos(mx, my, mz),
                        "minecraft:entity.illusioner.cast_spell", 0.9F, 1.1F);
            } catch (final Exception ignored) {
            }
        }
        final long now = npc.getWorld().getTotalTime();
        put(npc.getStoreddata(), NEXT_CAST_KEY, String.valueOf(now + 30));
        if (partner != null) {
            put(partner.getStoreddata(), NEXT_CAST_KEY, String.valueOf(now + 30));
        }
    }

    private static void abortHpRitual(final ICustomNpc npc) {
        if (npc == null) {
            return;
        }
        ICustomNpc partner = null;
        try {
            partner = findPartner(npc);
        } catch (final Exception ignored) {
        }
        if (!isRitualActive(npc) && (partner == null || !isRitualActive(partner))) {
            return;
        }
        clearRitualFlags(npc);
        if (npc.isAlive() && !isDowned(npc)) {
            applyCasterStance(npc);
        }
        if (partner != null) {
            clearRitualFlags(partner);
            if (partner.isAlive() && !isDowned(partner)) {
                applyCasterStance(partner);
            }
        }
    }

    private static void markRitualFlags(final ICustomNpc npc, final long until, final boolean leader) {
        final IData data = npc.getStoreddata();
        put(data, RITUAL_ACTIVE_KEY, "1");
        put(data, RITUAL_UNTIL_KEY, String.valueOf(until));
        put(data, RITUAL_LEADER_KEY, leader ? "1" : "0");
        put(data, KITE_UNTIL_KEY, "0");
        put(data, FORCED_ABILITY_KEY, "");
    }

    private static void clearRitualFlags(final ICustomNpc npc) {
        if (npc == null) {
            return;
        }
        final IData data = npc.getStoreddata();
        put(data, RITUAL_ACTIVE_KEY, "0");
        put(data, RITUAL_UNTIL_KEY, "0");
        put(data, RITUAL_LEADER_KEY, "0");
    }

    private static void freezeRitualNpc(final ICustomNpc npc) {
        if (npc == null) {
            return;
        }
        pinRitualPosition(npc);
        try {
            npc.setAttackTarget(null);
        } catch (final Exception ignored) {
        }
        try {
            final INPCAi ai = npc.getAi();
            ai.setRetaliateType(RETALIATE_NONE);
            ai.setWalkingSpeed(0);
            ai.setMovingType(MOVING_STANDING);
            ai.setNavigationType(NAV_FLYING);
            try {
                ai.setLeapAtTarget(false);
            } catch (final Exception ignored) {
            }
        } catch (final Exception ignored) {
        }
    }

    private static void pinRitualPosition(final ICustomNpc npc) {
        if (npc == null) {
            return;
        }
        final boolean spirit = "spirit".equals(getRole(npc.getStoreddata()));
        final double y = spirit ? ritualY(npc) + ritualSpiritDy(npc) : ritualY(npc);
        try {
            npc.setPosition(ritualX(npc), y, ritualZ(npc));
            zeroMotion(npc);
        } catch (final Exception ignored) {
        }
    }

    private static void spawnRitualParticleLink(final ICustomNpc a, final ICustomNpc b) {
        if (a == null || b == null) {
            return;
        }
        final IWorld world = a.getWorld();
        try {
            final double ax = a.getX();
            final double ay = a.getY() + 1.0;
            final double az = a.getZ();
            final double bx = b.getX();
            final double by = b.getY() + 0.6;
            final double bz = b.getZ();
            final int steps = 10;
            for (int i = 0; i <= steps; i++) {
                final double t = i / (double) steps;
                final double x = ax + (bx - ax) * t;
                final double y = ay + (by - ay) * t;
                final double z = az + (bz - az) * t;
                world.spawnParticle("minecraft:soul_fire_flame", x, y, z, 0, 0.02, 0, 0, 1);
                if (i % 2 == 0) {
                    world.spawnParticle("minecraft:enchant", x, y, z, 0, 0.02, 0, 0.01, 1);
                }
            }
        } catch (final Exception ignored) {
        }
    }

    // -------------------------------------------------------------------------
    // Flame carousel
    // -------------------------------------------------------------------------

    private static void tickFlameCarousel(final ICustomNpc npc) {
        if (npc == null || !npc.isAlive() || !isFlameCarouselLeader(npc)) {
            return;
        }
        if (!isEncounterAggroActive(npc)) {
            return;
        }
        if (!isFlameCarouselActive(npc)) {
            startFlameCarousel(npc);
            return;
        }
        if (refreshFlameZonesLifetime(npc) == 0) {
            startFlameCarousel(npc);
            return;
        }
        final long now = npc.getWorld().getTotalTime();
        long next = 0;
        try {
            next = Long.parseLong(flameTempGet(npc, FLAME_NEXT));
        } catch (final Exception ignored) {
        }
        if (now >= next) {
            shiftFlameCarousel(npc);
        }
    }

    private static boolean startFlameCarousel(final ICustomNpc npc) {
        if (npc == null || !isFlameCarouselLeader(npc) || !hasFlameConfig(npc)) {
            return false;
        }
        stopFlameCarouselEntities(npc);
        final List<String> uuids = new ArrayList<>();
        final List<String> slots = new ArrayList<>();
        final IWorld world = npc.getWorld();
        for (int i = 0; i < FLAME_ZONE_COUNT; i++) {
            final double[] point = flamePoint(npc, i);
            if (point == null) {
                continue;
            }
            final EntityAbilityZone zone = spawnFlameZoneAt(npc, point);
            if (zone == null) {
                continue;
            }
            try {
                uuids.add(zone.getUUID().toString());
                slots.add(String.valueOf(i));
                spawnFlameVfx(world, point);
            } catch (final Exception e) {
                ZoneAPI.remove(zone);
            }
        }
        if (uuids.isEmpty()) {
            return false;
        }
        final long now = world.getTotalTime();
        flameTempPut(npc, FLAME_ACTIVE, "1");
        flameTempPut(npc, FLAME_UUIDS, joinList(uuids));
        flameTempPut(npc, FLAME_SLOTS, joinList(slots));
        flameTempPut(npc, FLAME_NEXT, String.valueOf(now + FLAME_SHIFT_TICKS));
        return true;
    }

    private static int refreshFlameZonesLifetime(final ICustomNpc npc) {
        final List<String> uuids = parseList(flameTempGet(npc, FLAME_UUIDS));
        final List<String> slotsRaw = parseList(flameTempGet(npc, FLAME_SLOTS));
        final IWorld world = npc.getWorld();
        final List<String> alive = new ArrayList<>();
        final List<String> slots = new ArrayList<>();
        final Set<Integer> usedSlots = new HashSet<>();

        for (int i = 0; i < uuids.size(); i++) {
            final EntityAbilityZone zone = resolveFlameZone(world, uuids.get(i));
            if (zone == null || zone.removed) {
                continue;
            }
            try {
                zone.setLifetimeTicks(FLAME_LIFETIME);
            } catch (final Exception ignored) {
            }
            int slot = i;
            try {
                if (i < slotsRaw.size()) {
                    slot = Integer.parseInt(slotsRaw.get(i));
                }
            } catch (final Exception ignored) {
            }
            slot = Math.floorMod(slot, FLAME_ZONE_COUNT);
            if (!usedSlots.add(slot)) {
                continue;
            }
            alive.add(uuids.get(i));
            slots.add(String.valueOf(slot));
        }

        for (int s = 0; s < FLAME_ZONE_COUNT; s++) {
            if (usedSlots.contains(s)) {
                continue;
            }
            final EntityAbilityZone spawned = spawnFlameZoneAt(npc, flamePoint(npc, s));
            if (spawned == null) {
                continue;
            }
            try {
                alive.add(spawned.getUUID().toString());
                slots.add(String.valueOf(s));
                usedSlots.add(s);
                spawnFlameVfx(world, flamePoint(npc, s));
            } catch (final Exception e) {
                ZoneAPI.remove(spawned);
            }
        }
        flameTempPut(npc, FLAME_UUIDS, joinList(alive));
        flameTempPut(npc, FLAME_SLOTS, joinList(slots));
        return alive.size();
    }

    private static void shiftFlameCarousel(final ICustomNpc npc) {
        final IWorld world = npc.getWorld();
        final List<String> uuids = parseList(flameTempGet(npc, FLAME_UUIDS));
        final List<String> slots = parseList(flameTempGet(npc, FLAME_SLOTS));
        if (uuids.isEmpty()) {
            startFlameCarousel(npc);
            return;
        }
        final List<String> newUuids = new ArrayList<>();
        final List<String> newSlots = new ArrayList<>();
        for (int i = 0; i < uuids.size(); i++) {
            EntityAbilityZone zone = resolveFlameZone(world, uuids.get(i));
            int slot = i;
            try {
                if (i < slots.size()) {
                    slot = Integer.parseInt(slots.get(i));
                }
            } catch (final Exception ignored) {
            }
            final int nextSlot = Math.floorMod(slot + 1, FLAME_ZONE_COUNT);
            final double[] point = flamePoint(npc, nextSlot);
            if (point == null) {
                continue;
            }
            if (zone == null) {
                zone = spawnFlameZoneAt(npc, point);
                if (zone == null) {
                    continue;
                }
                newUuids.add(zone.getUUID().toString());
                newSlots.add(String.valueOf(nextSlot));
                spawnFlameVfx(world, point);
                continue;
            }
            try {
                zone.moveTo(point[0], point[1], point[2], 0, 0);
                zone.setLifetimeTicks(FLAME_LIFETIME);
                configureFlameZone(zone);
                newUuids.add(uuids.get(i));
                newSlots.add(String.valueOf(nextSlot));
                spawnFlameVfx(world, point);
            } catch (final Exception e) {
                ZoneAPI.remove(zone);
                final EntityAbilityZone respawn = spawnFlameZoneAt(npc, point);
                if (respawn != null) {
                    newUuids.add(respawn.getUUID().toString());
                    newSlots.add(String.valueOf(nextSlot));
                }
            }
        }
        if (newUuids.isEmpty()) {
            startFlameCarousel(npc);
            return;
        }
        flameTempPut(npc, FLAME_UUIDS, joinList(newUuids));
        flameTempPut(npc, FLAME_SLOTS, joinList(newSlots));
        flameTempPut(npc, FLAME_NEXT, String.valueOf(world.getTotalTime() + FLAME_SHIFT_TICKS));
    }

    private static EntityAbilityZone spawnFlameZoneAt(final ICustomNpc npc, final double[] point) {
        if (npc == null || point == null) {
            return null;
        }
        EntityAbilityZone zone;
        try {
            zone = ZoneAPI.hazardCircle(
                    npc, point[0], point[1], point[2],
                    FLAME_RADIUS, FLAME_LIFETIME, FLAME_DAMAGE, FLAME_DAMAGE_INTERVAL);
        } catch (final Exception e) {
            return null;
        }
        configureFlameZone(zone);
        return zone;
    }

    private static void configureFlameZone(final EntityAbilityZone zone) {
        if (zone == null) {
            return;
        }
        try {
            zone.setColor(FLAME_COLOR);
        } catch (final Exception ignored) {
        }
        try {
            zone.setFireSeconds(FLAME_FIRE_SECONDS);
        } catch (final Exception ignored) {
        }
        try {
            zone.setZoneHeight(2.5F);
        } catch (final Exception ignored) {
        }
        try {
            zone.setVisible(true);
        } catch (final Exception ignored) {
        }
        try {
            zone.setGroundFill(true);
        } catch (final Exception ignored) {
        }
        try {
            zone.setBorder(true);
        } catch (final Exception ignored) {
        }
        try {
            zone.setLifetimeTicks(FLAME_LIFETIME);
        } catch (final Exception ignored) {
        }
        try {
            zone.setDamageInterval(FLAME_DAMAGE_INTERVAL);
        } catch (final Exception ignored) {
        }
        try {
            zone.setDamage(FLAME_DAMAGE);
        } catch (final Exception ignored) {
        }
        try {
            zone.addTag(FLAME_TAG);
        } catch (final Exception ignored) {
        }
    }

    private static void spawnFlameVfx(final IWorld world, final double[] point) {
        if (world == null || point == null) {
            return;
        }
        try {
            world.spawnParticle("minecraft:flame", point[0], point[1] + 0.4, point[2], 0.35, 0.25, 0.35, 0.02, 18);
            world.spawnParticle("minecraft:smoke", point[0], point[1] + 0.6, point[2], 0.3, 0.35, 0.3, 0.01, 10);
            world.playSoundAt(
                    NpcAPI.Instance().getIPos(point[0], point[1], point[2]),
                    "minecraft:block.fire.ambient", 0.85F, 0.9F);
        } catch (final Exception ignored) {
        }
    }

    private static void stopFlameCarouselEntities(final ICustomNpc npc) {
        if (npc == null) {
            return;
        }
        final IWorld world = npc.getWorld();
        for (final String uuid : parseList(flameTempGet(npc, FLAME_UUIDS))) {
            final EntityAbilityZone zone = resolveFlameZone(world, uuid);
            if (zone != null) {
                ZoneAPI.remove(zone);
            }
        }
        try {
            final IPos pos = NpcAPI.Instance().getIPos(ritualX(npc), ritualY(npc), ritualZ(npc));
            final IEntity[] near = world.getNearbyEntities(pos, 48, -1);
            for (final IEntity ent : near) {
                final EntityAbilityZone z = asAbilityZone(ent);
                if (z == null) {
                    continue;
                }
                boolean tagged = false;
                try {
                    tagged = z.getTags().contains(FLAME_TAG);
                } catch (final Exception ignored) {
                }
                if (!tagged) {
                    try {
                        final java.util.UUID owner = z.getOwnerUuid();
                        if (owner != null) {
                            final String ou = owner.toString();
                            if (ou.equals(String.valueOf(npc.getUUID()))) {
                                tagged = true;
                            } else {
                                final ICustomNpc partner = findPartner(npc);
                                if (partner != null && ou.equals(String.valueOf(partner.getUUID()))) {
                                    tagged = true;
                                }
                            }
                        }
                    } catch (final Exception ignored) {
                    }
                }
                if (tagged) {
                    ZoneAPI.remove(z);
                }
            }
        } catch (final Exception ignored) {
        }
    }

    private static void stopFlameCarousel(final ICustomNpc npc) {
        if (npc == null) {
            return;
        }
        if (!isFlameCarouselActive(npc) && parseList(flameTempGet(npc, FLAME_UUIDS)).isEmpty()) {
            return;
        }
        stopFlameCarouselEntities(npc);
        flameTempClear(npc);
    }

    private static void tryStopFlameCarouselOnDeath(final ICustomNpc npc) {
        if (npc == null) {
            return;
        }
        final ICustomNpc partner = findPartner(npc);
        if (partner != null && partner.isAlive() && !isDowned(partner)) {
            if (isEncounterAggroActive(partner) || hasCombatTarget(partner) || findPlayerInLeash(partner) != null) {
                return;
            }
        }
        stopFlameCarousel(npc);
    }

    /**
     * When both body and spirit lost aggro long enough: restore boards + stop flame.
     * Thrall cleanup intentionally skipped.
     */
    private static void tryRestoreBoardsAndStopFlame(final ICustomNpc npc) {
        if (hasCombatTarget(npc) || findPlayerInLeash(npc) != null || isInActiveCombat(npc)) {
            return;
        }
        final ICustomNpc partner = findPartner(npc);
        if (partner != null && partner.isAlive() && !isDowned(partner)) {
            if (hasCombatTarget(partner) || findPlayerInLeash(partner) != null) {
                return;
            }
        }
        if (!hasLostAggroLongEnough(npc)) {
            markLostAggro(npc);
            return;
        }
        if (partner != null && partner.isAlive() && !isDowned(partner) && !hasLostAggroLongEnough(partner)) {
            markLostAggro(partner);
            return;
        }
        AbilityCombatHelper.restoreBrokenBoards(npc);
        stopFlameCarousel(npc);
        if (!isInBondPhase(npc)) {
            tryReturnHomeIfIdle(npc);
        }
        if (partner != null && partner.isAlive() && !isDowned(partner) && !isInBondPhase(partner)) {
            tryReturnHomeIfIdle(partner);
        }
    }

    private static boolean isFlameCarouselActive(final ICustomNpc npc) {
        return "1".equals(flameTempGet(npc, FLAME_ACTIVE));
    }

    private static boolean isEncounterAggroActive(final ICustomNpc npc) {
        if (npc == null) {
            return false;
        }
        if (isInActiveCombat(npc) || findPlayerInLeash(npc) != null) {
            return true;
        }
        final ICustomNpc partner = findPartner(npc);
        if (partner != null && partner.isAlive()) {
            return hasCombatTarget(partner) || findPlayerInLeash(partner) != null || isInActiveCombat(partner);
        }
        return false;
    }

    private static ICustomNpc getFlameCarouselLeader(final ICustomNpc npc) {
        if (npc == null) {
            return null;
        }
        final ICustomNpc partner = findPartner(npc);
        final ICustomNpc body;
        final ICustomNpc spirit;
        if ("spirit".equals(getRole(npc.getStoreddata()))) {
            spirit = npc;
            body = partner;
        } else {
            body = npc;
            spirit = partner;
        }
        if (body != null && body.isAlive() && !isDowned(body)) {
            return body;
        }
        if (spirit != null && spirit.isAlive() && !isDowned(spirit)) {
            return spirit;
        }
        return null;
    }

    private static boolean isFlameCarouselLeader(final ICustomNpc npc) {
        final ICustomNpc leader = getFlameCarouselLeader(npc);
        return leader != null && npc != null
                && String.valueOf(leader.getUUID()).equals(String.valueOf(npc.getUUID()));
    }

    private static String flameEncounterId(final ICustomNpc npc) {
        if (npc == null) {
            return "";
        }
        try {
            final String pair = str(npc.getStoreddata(), PAIR_ID_KEY);
            if (!pair.isEmpty() && !"null".equals(pair) && !"undefined".equals(pair)) {
                return pair;
            }
        } catch (final Exception ignored) {
        }
        try {
            return String.valueOf(npc.getUUID());
        } catch (final Exception e) {
            return "";
        }
    }

    private static String flameTempKey(final ICustomNpc npc, final String suffix) {
        return "df_flame_" + flameEncounterId(npc) + "_" + suffix;
    }

    private static String flameTempGet(final ICustomNpc npc, final String suffix) {
        try {
            return str(npc.getWorld().getTempdata(), flameTempKey(npc, suffix));
        } catch (final Exception e) {
            return "";
        }
    }

    private static void flameTempPut(final ICustomNpc npc, final String suffix, final String value) {
        try {
            npc.getWorld().getTempdata().put(flameTempKey(npc, suffix), value);
        } catch (final Exception ignored) {
        }
    }

    private static void flameTempClear(final ICustomNpc npc) {
        if (npc == null) {
            return;
        }
        try {
            final IData td = npc.getWorld().getTempdata();
            td.remove(flameTempKey(npc, FLAME_ACTIVE));
            td.remove(flameTempKey(npc, FLAME_UUIDS));
            td.remove(flameTempKey(npc, FLAME_SLOTS));
            td.remove(flameTempKey(npc, FLAME_NEXT));
        } catch (final Exception ignored) {
        }
    }

    private static EntityAbilityZone resolveFlameZone(final IWorld world, final String uuid) {
        if (world == null || uuid == null || uuid.isEmpty()) {
            return null;
        }
        try {
            return asAbilityZone(world.getEntity(uuid));
        } catch (final Exception e) {
            return null;
        }
    }

    private static EntityAbilityZone asAbilityZone(final IEntity wrapped) {
        if (wrapped == null) {
            return null;
        }
        try {
            final Object mc = wrapped.getMCEntity();
            if (mc instanceof EntityAbilityZone) {
                return (EntityAbilityZone) mc;
            }
        } catch (final Exception ignored) {
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Combat helpers
    // -------------------------------------------------------------------------

    private static IEntityLiving findValidPlayer(
            final ICustomNpc npc, final double maxNpcDist, final double maxHomeDist) {
        if (npc == null) {
            return null;
        }
        IEntityLiving best = null;
        double bestDist = maxNpcDist + 1.0;
        try {
            for (final IPlayer p : npc.getWorld().getAllPlayers()) {
                if (!isValidCombatPlayer(p)) {
                    continue;
                }
                if (distEntityToHome(npc, p) > maxHomeDist) {
                    continue;
                }
                final double d = flatDistance(npc, p);
                if (d < bestDist) {
                    bestDist = d;
                    best = p;
                }
            }
        } catch (final Exception ignored) {
        }
        return best;
    }

    private static IEntityLiving findPlayerInLeash(final ICustomNpc npc) {
        if (npc == null) {
            return null;
        }
        IEntityLiving best = null;
        double bestDist = LEASH_RANGE + 1.0;
        try {
            for (final IPlayer p : npc.getWorld().getAllPlayers()) {
                if (!isValidCombatPlayer(p)) {
                    continue;
                }
                final double dh = distEntityToHome(npc, p);
                if (dh > LEASH_RANGE) {
                    continue;
                }
                if (dh < bestDist) {
                    bestDist = dh;
                    best = p;
                }
            }
        } catch (final Exception ignored) {
        }
        return best;
    }

    private static boolean isValidCombatPlayer(final IPlayer p) {
        if (p == null || !p.isAlive()) {
            return false;
        }
        try {
            final int gm = p.getGamemode();
            if (gm == 1 || gm == 3) {
                return false;
            }
        } catch (final Exception ignored) {
        }
        return true;
    }

    private static boolean isInActiveCombat(final ICustomNpc npc) {
        return hasCombatTarget(npc) || findValidPlayer(npc, AGRO_RANGE, LEASH_RANGE) != null;
    }

    private static boolean hasCombatTarget(final ICustomNpc npc) {
        try {
            final IEntityLiving t = npc.getAttackTarget();
            return t != null && t.isAlive() && isCombatPlayerTarget(t);
        } catch (final Exception e) {
            return false;
        }
    }

    /** Только выживание/приключение-игроки; не NPC и не креатив/спек. */
    private static boolean isCombatPlayerTarget(final IEntityLiving ent) {
        if (!(ent instanceof IPlayer)) {
            return false;
        }
        return isValidCombatPlayer((IPlayer) ent);
    }

    private static boolean isInBondPhase(final ICustomNpc npc) {
        if (npc == null) {
            return false;
        }
        return "1".equals(str(npc.getStoreddata(), PARTNER_DEAD_KEY)) || isDowned(npc);
    }

    private static void clearLostAggroTimer(final ICustomNpc npc) {
        if (npc == null) {
            return;
        }
        put(npc.getStoreddata(), LOST_AGGRO_SINCE_KEY, "0");
    }

    private static void markLostAggro(final ICustomNpc npc) {
        if (npc == null) {
            return;
        }
        final IData data = npc.getStoreddata();
        if (ScriptDataUtil.getInt(data, LOST_AGGRO_SINCE_KEY) > 0) {
            return;
        }
        put(data, LOST_AGGRO_SINCE_KEY, String.valueOf(npc.getWorld().getTotalTime()));
    }

    private static boolean hasLostAggroLongEnough(final ICustomNpc npc) {
        if (npc == null) {
            return false;
        }
        final int since = ScriptDataUtil.getInt(npc.getStoreddata(), LOST_AGGRO_SINCE_KEY);
        if (since <= 0) {
            return false;
        }
        return npc.getWorld().getTotalTime() - since >= HOME_RETURN_DELAY_TICKS;
    }

    private static boolean hasHome(final IData data) {
        return data != null && data.has(HOME_X_KEY) && !isBlank(str(data, HOME_X_KEY));
    }

    private static double distToHomeFlat(final ICustomNpc npc) {
        final IData data = npc.getStoreddata();
        if (!hasHome(data)) {
            return 0.0;
        }
        final double dx = npc.getX() - ScriptDataUtil.getFloat(data, HOME_X_KEY);
        final double dz = npc.getZ() - ScriptDataUtil.getFloat(data, HOME_Z_KEY);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double distEntityToHome(final ICustomNpc npc, final IEntity ent) {
        final IData data = npc.getStoreddata();
        if (!hasHome(data) || ent == null) {
            return 9999.0;
        }
        final double dx = ent.getX() - ScriptDataUtil.getFloat(data, HOME_X_KEY);
        final double dz = ent.getZ() - ScriptDataUtil.getFloat(data, HOME_Z_KEY);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static ICustomNpc findNpcByUuid(final IWorld world, final String uuid) {
        if (world == null || uuid == null || uuid.isEmpty()) {
            return null;
        }
        try {
            final IEntity[] all = world.getAllEntities(ENTITY_LIVING);
            for (final IEntity ent : all) {
                if (ent instanceof ICustomNpc && uuid.equals(String.valueOf(ent.getUUID()))) {
                    return (ICustomNpc) ent;
                }
            }
        } catch (final Exception ignored) {
        }
        return null;
    }

    private static double flatDistance(final IEntity a, final IEntity b) {
        final double dx = a.getX() - b.getX();
        final double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static float hpRatio(final ICustomNpc npc) {
        try {
            final float max = npc.getMaxHealth();
            if (!(max > 0.0F)) {
                return 1.0F;
            }
            return npc.getHealth() / max;
        } catch (final Exception e) {
            return 1.0F;
        }
    }

    private static String hpPhase(final ICustomNpc npc) {
        return hpRatio(npc) <= 0.5F ? "2" : "1";
    }

    private static boolean isCooldownReady(final IData data, final long now, final String abilityId) {
        return now >= ScriptDataUtil.getInt(data, CD_PREFIX + abilityId);
    }

    private static void zeroMotion(final ICustomNpc npc) {
        try {
            npc.setMotionX(0);
            npc.setMotionY(0);
            npc.setMotionZ(0);
        } catch (final Exception ignored) {
        }
    }

    private static boolean isClient(final ICustomNpc npc) {
        try {
            final Object mc = npc.getMCEntity();
            if (mc instanceof Entity) {
                return ((Entity) mc).level == null || ((Entity) mc).level.isClientSide;
            }
        } catch (final Exception ignored) {
        }
        return false;
    }

    private static String str(final IData data, final String key) {
        if (data == null || !data.has(key)) {
            return "";
        }
        final Object raw = data.get(key);
        return raw == null ? "" : String.valueOf(raw);
    }

    private static void put(final IData data, final String key, final Object value) {
        if (data == null || key == null) {
            return;
        }
        data.put(key, value == null ? "" : String.valueOf(value));
    }

    private static boolean isBlank(final String s) {
        return s == null || s.isEmpty() || "null".equals(s) || "undefined".equals(s);
    }

    private static List<String> parseList(final String raw) {
        final List<String> out = new ArrayList<>();
        if (raw == null || raw.isEmpty() || "null".equals(raw) || "undefined".equals(raw)) {
            return out;
        }
        for (final String part : raw.split(";")) {
            if (part != null && !part.isEmpty()) {
                out.add(part);
            }
        }
        return out;
    }

    private static String joinList(final List<String> arr) {
        if (arr == null || arr.isEmpty()) {
            return "";
        }
        final StringBuilder sb = new StringBuilder(arr.get(0));
        for (int i = 1; i < arr.size(); i++) {
            sb.append(';').append(arr.get(i));
        }
        return sb.toString();
    }
}
