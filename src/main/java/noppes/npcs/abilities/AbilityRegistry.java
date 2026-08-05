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
        register(new VampirePounceAbility());
        register(new VampireBloodSiphonAbility());
        register(new VampireBatSwarmAbility());
        register(new VampireBloodNovaAbility());
        register(new BloodDragonRiposteAbility());
        register(new BarrowSentinelAbility());
        register(new GraspingDeadAbility());
        register(new RatlingGunVolleyAbility());
        register(new DrachenfelsPoisonFeastAbility());
        register(new DrachenfelsDarkCleaveAbility());
        register(new DrachenfelsSoulRendAbility());
        register(new DrachenfelsSpiritBarrageAbility());
        register(new DrachenfelsSoulSeekerAbility());
        register(new DrachenfelsSoulOrbsAbility());
        register(new DrachenfelsRaiseThrallsAbility());
        register(new DrachenfelsShadowStepAbility());
        register(new ShieldBlockAbility());
        register(new CrimsonBlobAbility());
        register(new WhFlamingStrikeAbility());
        register(new WhLungeAbility());
        register(new WhFlamingCrossbowAbility());
        register(new WhFireBombAbility());
        register(new OtrodieHellVomitAbility());
        register(new OtrodieFecalWaveAbility());
        register(new OtrodieDevourDashAbility());
        register(new OtrodieSpreadingFilthAbility());
        register(new GhostOrbitSlamAbility());
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
