package noppes.npcs.abilities;

import noppes.npcs.abilities.impl.DashAbility;
import noppes.npcs.abilities.impl.JumpSlamAbility;

import java.util.HashMap;
import java.util.Map;

public final class AbilityRegistry {
    private static final Map<String, CnpcAbility> ABILITIES = new HashMap<>();

    static {
        register(new DashAbility());
        register(new JumpSlamAbility());
    }

    private AbilityRegistry() {
    }

    public static void register(final CnpcAbility ability) {
        ABILITIES.put(ability.getId(), ability);
    }

    public static CnpcAbility get(final String id) {
        return ABILITIES.get(id);
    }
}
