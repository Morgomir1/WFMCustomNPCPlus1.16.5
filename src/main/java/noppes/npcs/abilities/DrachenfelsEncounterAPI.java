package noppes.npcs.abilities;

import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntityLiving;

/**
 * JS-facing facade for Drachenfels dual-boss encounter orchestration.
 * Implementation lives in {@link DrachenfelsEncounterHelper}.
 */
public final class DrachenfelsEncounterAPI {
    public static final String PAIR_TAG = DrachenfelsEncounterHelper.PAIR_TAG;

    private DrachenfelsEncounterAPI() {
    }

    /** role: {@code "body"} or {@code "spirit"} */
    public static void init(final ICustomNpc npc, final String role) {
        DrachenfelsEncounterHelper.init(npc, role);
    }

    /**
     * Arena coords from JS: HP-ritual pillar + 4 flame points.
     * Call after {@link #init} (same values on body and spirit scripts).
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
        DrachenfelsEncounterHelper.configureArena(
                npc, ritualX, ritualY, ritualZ, spiritOffsetY,
                f0x, f0y, f0z, f1x, f1y, f1z, f2x, f2y, f2z, f3x, f3y, f3z);
    }

    /** ~1s PHASE_CHECK: link, bond/revive, flame carousel, boards restore, spacing, return home */
    public static void tickSlow(final ICustomNpc npc) {
        DrachenfelsEncounterHelper.tickSlow(npc);
    }

    /** 1-tick: downed pin, ritual pin/transfer/particles, spawn-height hover */
    public static void tickFast(final ICustomNpc npc) {
        DrachenfelsEncounterHelper.tickFast(npc);
    }

    public static void onTargetLost(final ICustomNpc npc) {
        DrachenfelsEncounterHelper.onTargetLost(npc);
    }

    public static void onDied(final ICustomNpc npc) {
        DrachenfelsEncounterHelper.onDied(npc);
    }

    public static boolean startHpRitual(final ICustomNpc npc) {
        return DrachenfelsEncounterHelper.startHpRitual(npc);
    }

    public static boolean isDowned(final ICustomNpc npc) {
        return DrachenfelsEncounterHelper.isDowned(npc);
    }

    public static boolean isRitualActive(final ICustomNpc npc) {
        return DrachenfelsEncounterHelper.isRitualActive(npc);
    }

    /** true if downed or ritual — JS must not cast */
    public static boolean isBusyForCast(final ICustomNpc npc) {
        return DrachenfelsEncounterHelper.isBusyForCast(npc);
    }

    /** {@code "1"} | {@code "2"} | {@code "bond"} */
    public static String getPhase(final ICustomNpc npc) {
        return DrachenfelsEncounterHelper.getPhase(npc);
    }

    public static String getRole(final ICustomNpc npc) {
        return DrachenfelsEncounterHelper.getRole(npc);
    }

    public static IEntityLiving ensureCombatTarget(final ICustomNpc npc) {
        return DrachenfelsEncounterHelper.ensureCombatTarget(npc);
    }

    public static void cancelAbilities(final ICustomNpc npc) {
        AbilityAPI.cancel(npc);
    }
}
