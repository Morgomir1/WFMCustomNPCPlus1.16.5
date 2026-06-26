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

    public static Map<String, Object> pistolShot() {
        final Map<String, Object> map = new HashMap<>();
        map.put(AbilityParamKeys.CHARGE_TICKS, 8);
        map.put(AbilityParamKeys.DAMAGE, 9.0);
        map.put(AbilityParamKeys.ACCURACY, 4);
        map.put(AbilityParamKeys.MAX_RANGE, 24.0);
        map.put(AbilityParamKeys.PROJECTILE_ITEM, "wfm:empire_pistol");
        return map;
    }

    public static Map<String, Object> netThrow() {
        final Map<String, Object> map = new HashMap<>();
        map.put(AbilityParamKeys.CHARGE_TICKS, 10);
        map.put(AbilityParamKeys.RADIUS, 2.0);
        map.put(AbilityParamKeys.EFFECT_DURATION, 60);
        map.put(AbilityParamKeys.EFFECT_AMPLIFIER, 3);
        map.put(AbilityParamKeys.ACCURACY, 6);
        map.put(AbilityParamKeys.PROJECTILE_ITEM, "wfm:dwarf_ranger_net");
        return map;
    }

    public static Map<String, Object> stakeThrust() {
        final Map<String, Object> map = new HashMap<>();
        map.put(AbilityParamKeys.DISTANCE, 3.5);
        map.put(AbilityParamKeys.CHARGE_TICKS, 6);
        map.put(AbilityParamKeys.ACTIVE_TICKS, 4);
        map.put(AbilityParamKeys.DAMAGE, 16.0);
        map.put(AbilityParamKeys.KNOCKBACK, 1.2);
        map.put(AbilityParamKeys.KNOCKBACK_Y, 0.25);
        map.put(AbilityParamKeys.HIT_RADIUS, 1.1);
        map.put(AbilityParamKeys.UNDEAD_BONUS_MULTIPLIER, 1.5);
        return map;
    }

    public static Map<String, Object> holyWaterSplash() {
        final Map<String, Object> map = new HashMap<>();
        map.put(AbilityParamKeys.CHARGE_TICKS, 14);
        map.put(AbilityParamKeys.DAMAGE, 8.0);
        map.put(AbilityParamKeys.RADIUS, 4.0);
        map.put(AbilityParamKeys.CONE_HALF_ANGLE, 30.0);
        map.put(AbilityParamKeys.EFFECT_DURATION, 100);
        map.put(AbilityParamKeys.EFFECT_AMPLIFIER, 0);
        map.put(AbilityParamKeys.UNDEAD_BONUS_MULTIPLIER, 2.0);
        map.put(AbilityParamKeys.KNOCKBACK, 0.8);
        map.put(AbilityParamKeys.KNOCKBACK_Y, 0.2);
        return map;
    }

    public static Map<String, Object> burningBrand() {
        final Map<String, Object> map = new HashMap<>();
        map.put(AbilityParamKeys.CHARGE_TICKS, 8);
        map.put(AbilityParamKeys.ACTIVE_TICKS, 12);
        map.put(AbilityParamKeys.DAMAGE_PER_TICK, 3.0);
        map.put(AbilityParamKeys.AURA_RADIUS, 3.5);
        map.put(AbilityParamKeys.KNOCKBACK, 0.5);
        map.put(AbilityParamKeys.KNOCKBACK_Y, 0.1);
        return map;
    }

    public static Map<String, Object> retreatDash() {
        final Map<String, Object> map = new HashMap<>();
        map.put(AbilityParamKeys.DISTANCE, 6.0);
        map.put(AbilityParamKeys.CHARGE_TICKS, 5);
        map.put(AbilityParamKeys.ACTIVE_TICKS, 5);
        return map;
    }
}
