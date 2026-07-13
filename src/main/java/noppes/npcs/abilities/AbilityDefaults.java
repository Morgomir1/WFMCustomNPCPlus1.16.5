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

    public static Map<String, Object> zombieOgreLeadbelcherSlam() {
        final Map<String, Object> map = new HashMap<>();
        map.put(AbilityParamKeys.CHARGE_TICKS, 12);
        map.put(AbilityParamKeys.DAMAGE, 12.0);
        map.put(AbilityParamKeys.RADIUS, 3.0);
        map.put(AbilityParamKeys.KNOCKBACK, 0.9);
        map.put(AbilityParamKeys.KNOCKBACK_Y, 0.15);
        map.put(AbilityParamKeys.EFFECT_TYPE, "blindness");
        map.put(AbilityParamKeys.EFFECT_DURATION, 30);
        map.put(AbilityParamKeys.EFFECT_AMPLIFIER, 0);
        return map;
    }

    public static Map<String, Object> zombieOgreLeadbelcherArtillery() {
        final Map<String, Object> map = new HashMap<>();
        map.put(AbilityParamKeys.CHARGE_TICKS, 14);
        map.put(AbilityParamKeys.ACTIVE_TICKS, 6);
        map.put(AbilityParamKeys.SHOTS, 2);
        map.put(AbilityParamKeys.DISTANCE, 10.0); // длина "полосы" обстрела
        map.put(AbilityParamKeys.DAMAGE, 18.0);
        map.put(AbilityParamKeys.ACCURACY, 3);
        map.put(AbilityParamKeys.MAX_RANGE, 32.0);
        map.put(AbilityParamKeys.PROJECTILE_ITEM, "wfm:ogre_leadbelcher_gun");
        map.put(AbilityParamKeys.RADIUS, 2.0); // ширина "полосы"
        return map;
    }

    public static Map<String, Object> zombieOgreLeadbelcherTrample() {
        final Map<String, Object> map = new HashMap<>();
        map.put(AbilityParamKeys.DISTANCE, 12.0);
        map.put(AbilityParamKeys.CHARGE_TICKS, 10);
        map.put(AbilityParamKeys.ACTIVE_TICKS, 18);
        map.put(AbilityParamKeys.DAMAGE, 4.0);
        map.put(AbilityParamKeys.KNOCKBACK, 1.15);
        map.put(AbilityParamKeys.KNOCKBACK_Y, 0.25);
        map.put(AbilityParamKeys.HIT_RADIUS, 1.9);
        map.put(AbilityParamKeys.EFFECT_TYPE, "blindness");
        map.put(AbilityParamKeys.EFFECT_DURATION, 30);
        map.put(AbilityParamKeys.EFFECT_AMPLIFIER, 0);
        return map;
    }

    public static Map<String, Object> vampirePounce() {
        final Map<String, Object> map = new HashMap<>();
        map.put(AbilityParamKeys.CHARGE_TICKS, 10);
        map.put(AbilityParamKeys.ACTIVE_TICKS, 9);
        map.put(AbilityParamKeys.DAMAGE, 12.0);
        map.put(AbilityParamKeys.KNOCKBACK, 1.4);
        map.put(AbilityParamKeys.KNOCKBACK_Y, 0.35);
        map.put(AbilityParamKeys.LAND_RADIUS, 2.6);
        map.put(AbilityParamKeys.ARC_HEIGHT, 7.0);
        map.put(AbilityParamKeys.LIFE_STEAL_PER_HIT, 1.5);
        return map;
    }

    public static Map<String, Object> vampireBloodSiphon() {
        final Map<String, Object> map = new HashMap<>();
        map.put(AbilityParamKeys.CHARGE_TICKS, 8);
        map.put(AbilityParamKeys.ACTIVE_TICKS, 40);
        map.put(AbilityParamKeys.DAMAGE_PER_TICK, 0.8);
        map.put(AbilityParamKeys.HEAL_PER_TICK, 0.6);
        map.put(AbilityParamKeys.MAX_RANGE, 6.0);
        return map;
    }

    public static Map<String, Object> vampireBatSwarm() {
        final Map<String, Object> map = new HashMap<>();
        map.put(AbilityParamKeys.CHARGE_TICKS, 10);
        map.put(AbilityParamKeys.ACTIVE_TICKS, 22);
        map.put(AbilityParamKeys.RADIUS, 5.0);
        map.put(AbilityParamKeys.EFFECT_TYPE, "blindness");
        map.put(AbilityParamKeys.EFFECT_DURATION, 40);
        map.put(AbilityParamKeys.EFFECT_AMPLIFIER, 0);
        map.put(AbilityParamKeys.SUMMON_COUNT, 2);
        map.put(AbilityParamKeys.SUMMON_RADIUS, 4.0);
        map.put(AbilityParamKeys.MAX_SUMMONED_NEAR_BOSS, 6);
        map.put(AbilityParamKeys.CLONE_TAB, 1);
        map.put(AbilityParamKeys.CLONE_NAME, "vampire_bat");
        return map;
    }

    public static Map<String, Object> vampireBloodNova() {
        final Map<String, Object> map = new HashMap<>();
        map.put(AbilityParamKeys.CHARGE_TICKS, 12);
        map.put(AbilityParamKeys.DAMAGE, 18.0);
        map.put(AbilityParamKeys.RADIUS, 5.5);
        map.put(AbilityParamKeys.KNOCKBACK, 1.2);
        map.put(AbilityParamKeys.KNOCKBACK_Y, 0.35);
        map.put(AbilityParamKeys.EFFECT_TYPE, "weakness");
        map.put(AbilityParamKeys.EFFECT_DURATION, 40);
        map.put(AbilityParamKeys.EFFECT_AMPLIFIER, 0);
        return map;
    }

    public static Map<String, Object> bloodDragonRiposte() {
        final Map<String, Object> map = new HashMap<>();
        map.put(AbilityParamKeys.DISTANCE, 4.4);
        map.put(AbilityParamKeys.CHARGE_TICKS, 7);
        map.put(AbilityParamKeys.ACTIVE_TICKS, 4);
        map.put(AbilityParamKeys.DAMAGE, 14.0);
        map.put(AbilityParamKeys.RADIUS, 2.6);
        map.put(AbilityParamKeys.CONE_HALF_ANGLE, 70.0);
        map.put(AbilityParamKeys.KNOCKBACK, 1.0);
        map.put(AbilityParamKeys.KNOCKBACK_Y, 0.24);
        return map;
    }

    public static Map<String, Object> barrowSentinel() {
        final Map<String, Object> map = new HashMap<>();
        map.put(AbilityParamKeys.DISTANCE, 2.0);
        map.put(AbilityParamKeys.CHARGE_TICKS, 12);
        map.put(AbilityParamKeys.ACTIVE_TICKS, 5);
        map.put(AbilityParamKeys.DAMAGE, 11.0);
        map.put(AbilityParamKeys.RADIUS, 3.2);
        map.put(AbilityParamKeys.CONE_HALF_ANGLE, 52.0);
        map.put(AbilityParamKeys.KNOCKBACK, 0.8);
        map.put(AbilityParamKeys.KNOCKBACK_Y, 0.15);
        map.put(AbilityParamKeys.EFFECT_TYPE, "slowness");
        map.put(AbilityParamKeys.EFFECT_DURATION, 10);
        map.put(AbilityParamKeys.EFFECT_AMPLIFIER, 0);
        map.put(AbilityParamKeys.EXECUTE_HP_THRESHOLD, 0.35);
        map.put(AbilityParamKeys.EXECUTE_BONUS_DAMAGE, 7.0);
        return map;
    }

    public static Map<String, Object> graspingDead() {
        final Map<String, Object> map = new HashMap<>();
        map.put(AbilityParamKeys.CHARGE_TICKS, 5);
        map.put(AbilityParamKeys.ACTIVE_TICKS, 34);
        map.put(AbilityParamKeys.RADIUS, 1.8);
        map.put(AbilityParamKeys.EFFECT_TYPE, "slowness");
        map.put(AbilityParamKeys.EFFECT_DURATION, 35);
        map.put(AbilityParamKeys.EFFECT_AMPLIFIER, 1);
        map.put(AbilityParamKeys.DAMAGE_PER_TICK, 0.0);
        return map;
    }

    public static Map<String, Object> ratlingGunVolley() {
        final Map<String, Object> map = new HashMap<>();
        map.put(AbilityParamKeys.ACTIVE_TICKS, 60);
        map.put(AbilityParamKeys.FIRST_SHOT_TICK, 3);
        map.put(AbilityParamKeys.SHOT_INTERVAL, 5);
        map.put(AbilityParamKeys.DAMAGE, 8.0);
        map.put(AbilityParamKeys.ACCURACY, 6);
        map.put(AbilityParamKeys.MAX_RANGE, 36.0);
        map.put(AbilityParamKeys.BULLET_VELOCITY, 6.0);
        map.put(AbilityParamKeys.PROJECTILE_ITEM, "wfm:skaven_ratling_gun");
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
