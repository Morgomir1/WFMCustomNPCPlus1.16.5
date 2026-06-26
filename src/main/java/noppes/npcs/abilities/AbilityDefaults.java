package noppes.npcs.abilities;

import java.util.HashMap;
import java.util.Map;

public final class AbilityDefaults {
    private AbilityDefaults() {
    }

    public static Map<String, Object> dash() {
        final Map<String, Object> map = new HashMap<>();
        map.put(AbilityParamKeys.DISTANCE, 16.0);
        map.put(AbilityParamKeys.CHARGE_TICKS, 10);
        map.put(AbilityParamKeys.ACTIVE_TICKS, 7);
        map.put(AbilityParamKeys.DAMAGE, 10.0);
        map.put(AbilityParamKeys.KNOCKBACK, 1.8);
        map.put(AbilityParamKeys.KNOCKBACK_Y, 0.35);
        map.put(AbilityParamKeys.HIT_RADIUS, 1.6);
        return map;
    }

    public static Map<String, Object> jumpSlam() {
        final Map<String, Object> map = new HashMap<>();
        map.put(AbilityParamKeys.CHARGE_TICKS, 12);
        map.put(AbilityParamKeys.ACTIVE_TICKS, 9);
        map.put(AbilityParamKeys.DAMAGE, 14.0);
        map.put(AbilityParamKeys.KNOCKBACK, 2.2);
        map.put(AbilityParamKeys.KNOCKBACK_Y, 0.55);
        map.put(AbilityParamKeys.LAND_RADIUS, 2.8);
        map.put(AbilityParamKeys.ARC_HEIGHT, 6.0);
        return map;
    }
}
