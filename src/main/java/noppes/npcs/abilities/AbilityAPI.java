package noppes.npcs.abilities;

import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntityLiving;

import java.util.HashMap;
import java.util.Map;

public final class AbilityAPI {
    private AbilityAPI() {
    }

    public static boolean start(final ICustomNpc npc, final String abilityId, final IEntityLiving target) {
        return AbilityRunner.start(npc, abilityId, target, null);
    }

    public static boolean start(
            final ICustomNpc npc,
            final String abilityId,
            final IEntityLiving target,
            final Map<String, Object> overrides) {
        return AbilityRunner.start(npc, abilityId, target, overrides);
    }

    public static Map<String, Object> params(final Object... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return new HashMap<>();
        }
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("AbilityAPI.params requires an even number of arguments (key, value pairs)");
        }
        final Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            final Object key = keyValues[i];
            if (key == null) {
                throw new IllegalArgumentException("AbilityAPI.params: null key at index " + i);
            }
            map.put(String.valueOf(key), keyValues[i + 1]);
        }
        return map;
    }

    public static boolean isBusy(final ICustomNpc npc) {
        return AbilityRunner.isBusy(npc);
    }

    public static String getActiveId(final ICustomNpc npc) {
        return AbilityRunner.getActiveId(npc);
    }

    public static void cancel(final ICustomNpc npc) {
        AbilityRunner.cancel(npc);
    }

    /**
     * Encounter helper for Drachenfels HP ritual: asymmetric HP transfer between body and spirit.
     * Prefer calling from the JS orchestrator each ritual step.
     */
    public static boolean transferDrachenfelsRitualHp(final ICustomNpc a, final ICustomNpc b) {
        return AbilityCombatHelper.transferDrachenfelsRitualHp(a, b);
    }
}
