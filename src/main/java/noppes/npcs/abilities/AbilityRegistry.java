package noppes.npcs.abilities;

import noppes.npcs.abilities.impl.*;

import java.util.HashMap;
import java.util.Map;

public final class AbilityRegistry {
    private static final Map<String, CnpcAbility> ABILITIES = new HashMap<>();

    static {
        register(new DashAbility());
        register(new JumpSlamAbility());
        register(new PistolShotAbility());
        register(new NetThrowAbility());
        register(new StakeThrustAbility());
        register(new HolyWaterSplashAbility());
        register(new BurningBrandAbility());
        register(new RetreatDashAbility());
        register(new ZombieOgreLeadbelcherSlamAbility());
        register(new ZombieOgreLeadbelcherArtilleryAbility());
        register(new ZombieOgreLeadbelcherTrampleAbility());
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
