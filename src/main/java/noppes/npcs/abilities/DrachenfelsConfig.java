package noppes.npcs.abilities;

import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.data.IData;
import noppes.npcs.script.ScriptDataUtil;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * JS-tunable numeric constants for Constant Drachenfels.
 * Stored as {@code df_c_<key>} in NPC storeddata via {@link #configure}.
 */
public final class DrachenfelsConfig {
    public static final String PREFIX = "df_c_";

    private DrachenfelsConfig() {
    }

    /** Prefer {@link #configure(ICustomNpc, Map)} from Nashorn — varargs after typed arg break. */
    public static void configure(final ICustomNpc npc, final Object... keyValues) {
        configure(npc, AbilityAPI.params(keyValues));
    }

    public static void configure(final ICustomNpc npc, final Map<String, Object> map) {
        if (npc == null || map == null || map.isEmpty()) {
            return;
        }
        final IData data = npc.getStoreddata();
        for (final Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            data.put(PREFIX + e.getKey(), stringifyConfigValue(e.getValue()));
        }
    }

    /**
     * Nashorn passes ARGB hex &gt; 2^31 as Double; store signed 32-bit bits so getI
     * does not clamp them to white ({@code Integer.MAX_VALUE}). Floats stay decimal.
     */
    private static String stringifyConfigValue(final Object v) {
        if (!(v instanceof Number)) {
            return String.valueOf(v);
        }
        final Number n = (Number) v;
        final double d = n.doubleValue();
        if (!Double.isFinite(d) || d != Math.rint(d)) {
            return String.valueOf(d);
        }
        // Whole number: keep low 32 bits (ARGB / small ints).
        return String.valueOf((int) n.longValue());
    }

    public static double getD(final ICustomNpc npc, final String key, final double def) {
        return getD(npc == null ? null : npc.getStoreddata(), key, def);
    }

    public static double getD(final IData data, final String key, final double def) {
        if (data == null || key == null) {
            return def;
        }
        final String full = PREFIX + key;
        if (!data.has(full)) {
            return def;
        }
        return ScriptDataUtil.getFloat(data, full);
    }

    public static int getI(final ICustomNpc npc, final String key, final int def) {
        return getI(npc == null ? null : npc.getStoreddata(), key, def);
    }

    public static int getI(final IData data, final String key, final int def) {
        if (data == null || key == null) {
            return def;
        }
        final String full = PREFIX + key;
        if (!data.has(full)) {
            return def;
        }
        return ScriptDataUtil.getInt(data, full);
    }

    public static double[] getRatios(final IData data, final String key, final double[] def) {
        if (data == null || key == null) {
            return def;
        }
        final String raw = str(data, PREFIX + key);
        if (raw.isEmpty()) {
            return def;
        }
        final String[] parts = raw.split("[,;\\s]+");
        final double[] out = new double[parts.length];
        int n = 0;
        for (int i = 0; i < parts.length; i++) {
            try {
                out[n++] = Double.parseDouble(parts[i].trim());
            } catch (final Exception ignored) {
            }
        }
        if (n == 0) {
            return def;
        }
        if (n == out.length) {
            return out;
        }
        final double[] trimmed = new double[n];
        System.arraycopy(out, 0, trimmed, 0, n);
        return trimmed;
    }

    public static String mark(final double ratio) {
        return String.format(Locale.ROOT, "%.2f", ratio);
    }

    // --- ability param builders ---

    public static Map<String, Object> sealParams(final ICustomNpc npc) {
        final Map<String, Object> m = new HashMap<>();
        m.put(AbilityParamKeys.CHARGE_TICKS, getI(npc, "sealChargeTicks", 30));
        m.put(AbilityParamKeys.ACTIVE_TICKS, getI(npc, "sealActiveTicks", 1));
        m.put(AbilityParamKeys.DAMAGE, getD(npc, "sealDamage", 12.0));
        m.put(AbilityParamKeys.RADIUS, getD(npc, "sealRadius", 2.0));
        m.put(AbilityParamKeys.ZONE_TICKS, getI(npc, "sealZoneTicks", 480));
        m.put(AbilityParamKeys.DAMAGE_INTERVAL, getI(npc, "sealZoneInterval", 10));
        m.put(AbilityParamKeys.DAMAGE_PER_TICK, getD(npc, "sealZoneDamage", 3.0));
        m.put(AbilityParamKeys.TELEGRAPH_COLOR, getI(npc, "telegraphColor", 0xC0FF3030));
        m.put(AbilityParamKeys.ZONE_COLOR, getI(npc, "sealZoneColor", 0xC0143C14));
        m.put(AbilityParamKeys.EFFECT_DURATION, getI(npc, "sealPoisonDuration", 100));
        m.put(AbilityParamKeys.EFFECT_AMPLIFIER, getI(npc, "sealPoisonAmp", 1));
        m.put(AbilityParamKeys.TELEGRAPH, 0);
        m.put(AbilityParamKeys.SUMMON_RADIUS, getD(npc, "sealMinBossDist", 2.0));
        m.put(AbilityParamKeys.SPREAD_RADIUS, getD(npc, "sealMinCircleDist", 4.0));
        return m;
    }

    public static Map<String, Object> gazeParams(final ICustomNpc npc) {
        final Map<String, Object> m = new HashMap<>();
        m.put(AbilityParamKeys.CHARGE_TICKS, getI(npc, "gazeChargeTicks", 20));
        m.put(AbilityParamKeys.ACTIVE_TICKS, getI(npc, "gazeActiveTicks", 15));
        m.put(AbilityParamKeys.DISTANCE, getD(npc, "gazeDistance", 16.0));
        m.put(AbilityParamKeys.HIT_RADIUS, getD(npc, "gazeWidth", 1.5));
        m.put(AbilityParamKeys.DAMAGE, getD(npc, "gazeDamage", 18.0));
        m.put(AbilityParamKeys.TELEGRAPH_COLOR, getI(npc, "telegraphColor", 0xC0FF3030));
        m.put(AbilityParamKeys.TELEGRAPH, 0);
        return m;
    }

    public static Map<String, Object> repulseParams(final ICustomNpc npc) {
        final Map<String, Object> m = new HashMap<>();
        m.put(AbilityParamKeys.CHARGE_TICKS, getI(npc, "repulseChargeTicks", 30));
        m.put(AbilityParamKeys.ACTIVE_TICKS, getI(npc, "repulseActiveTicks", 1));
        m.put(AbilityParamKeys.RADIUS, getD(npc, "repulseRadius", 3.0));
        m.put(AbilityParamKeys.KNOCKBACK, getD(npc, "repulseKnockback", 1.85));
        m.put(AbilityParamKeys.KNOCKBACK_Y, getD(npc, "repulseKnockbackY", 0.42));
        m.put(AbilityParamKeys.TELEGRAPH_COLOR, getI(npc, "telegraphColor", 0xC0FF3030));
        m.put(AbilityParamKeys.TELEGRAPH, 0);
        return m;
    }

    public static Map<String, Object> imperialParams(final ICustomNpc npc) {
        final Map<String, Object> m = new HashMap<>();
        m.put(AbilityParamKeys.CHARGE_TICKS, getI(npc, "imperialChargeTicks", 24));
        m.put(AbilityParamKeys.ACTIVE_TICKS, getI(npc, "imperialActiveTicks", 120));
        m.put(AbilityParamKeys.RADIUS, getD(npc, "imperialArenaRadius", 12.0));
        m.put(AbilityParamKeys.INNER_RADIUS, getD(npc, "imperialThickness", 1.333));
        m.put(AbilityParamKeys.ARC_HEIGHT, getD(npc, "imperialHitHeight", 1.0));
        m.put(AbilityParamKeys.DAMAGE, getD(npc, "imperialDamage", 8.0));
        m.put(AbilityParamKeys.EFFECT_DURATION, getI(npc, "imperialPoisonDuration", 80));
        m.put(AbilityParamKeys.EFFECT_AMPLIFIER, getI(npc, "imperialPoisonAmp", 3));
        m.put(AbilityParamKeys.TRAIL_TICKS, getI(npc, "imperialSlowDuration", 40));
        m.put(AbilityParamKeys.SUMMON_COUNT, getI(npc, "imperialWaveCount", 3));
        m.put(AbilityParamKeys.SHOT_INTERVAL, getI(npc, "imperialWaveInterval", 10));
        m.put(AbilityParamKeys.TELEGRAPH_COLOR, getI(npc, "telegraphColor", 0xC0FF3030));
        m.put(AbilityParamKeys.ZONE_COLOR, getI(npc, "telegraphColor", 0xC0FF3030));
        m.put(AbilityParamKeys.TELEGRAPH, 0);
        return m;
    }

    public static Map<String, Object> feastParams(final ICustomNpc npc) {
        final Map<String, Object> m = new HashMap<>();
        m.put(AbilityParamKeys.CHARGE_TICKS, getI(npc, "feastChargeTicks", 40));
        m.put(AbilityParamKeys.ACTIVE_TICKS, getI(npc, "feastActiveTicks", 1));
        m.put(AbilityParamKeys.RADIUS, getD(npc, "feastSeatRadius", 1.5));
        m.put(AbilityParamKeys.SPREAD_RADIUS, getD(npc, "feastSeatRing", 9.5));
        m.put(AbilityParamKeys.SUMMON_RADIUS, getD(npc, "feastSeatMinBossDist", 2.5));
        m.put(AbilityParamKeys.MAX_RANGE, getD(npc, "feastArenaRadius", 12.0));
        m.put(AbilityParamKeys.DAMAGE, getD(npc, "feastDamage", 14.0));
        m.put(AbilityParamKeys.EFFECT_DURATION, getI(npc, "feastPoisonDuration", 60));
        m.put(AbilityParamKeys.EFFECT_AMPLIFIER, getI(npc, "feastPoisonAmp", 0));
        m.put(AbilityParamKeys.TELEGRAPH_COLOR, getI(npc, "feastColor", 0xC0FFFFFF));
        m.put(AbilityParamKeys.ZONE_COLOR, getI(npc, "telegraphColor", 0xC0FF3030));
        m.put(AbilityParamKeys.TELEGRAPH, 0);
        return m;
    }

    public static Map<String, Object> leperParams(final ICustomNpc npc) {
        final IData data = npc.getStoreddata();
        final Map<String, Object> m = new HashMap<>();
        m.put(AbilityParamKeys.CHARGE_TICKS, getI(npc, "leperChargeTicks", 24));
        m.put(AbilityParamKeys.ACTIVE_TICKS, getI(npc, "leperActiveTicks", 60));
        m.put(AbilityParamKeys.DAMAGE, getD(npc, "leperDamage", 10.0));
        m.put(AbilityParamKeys.RADIUS, getD(npc, "leperHitRadius", 1.0));
        m.put(AbilityParamKeys.SHOT_INTERVAL, getI(npc, "leperVolleyInterval", 18));
        m.put(AbilityParamKeys.SUMMON_COUNT, getI(npc, "leperVolleys", 5));
        m.put(AbilityParamKeys.CLONE_TAB, ScriptDataUtil.getInt(data, "df_clone_tab") <= 0
                ? 1 : ScriptDataUtil.getInt(data, "df_clone_tab"));
        final Object name = data.has("df_clone_phantom") ? data.get("df_clone_phantom") : "Drachenfels Leper Phantom";
        m.put(AbilityParamKeys.CLONE_NAME, String.valueOf(name));
        m.put(AbilityParamKeys.TELEGRAPH_COLOR, getI(npc, "telegraphColor", 0xC0FF3030));
        m.put(AbilityParamKeys.TELEGRAPH, 0);
        return m;
    }

    public static Map<String, Object> falseHostParams(final ICustomNpc npc) {
        final IData data = npc.getStoreddata();
        final Map<String, Object> m = new HashMap<>();
        m.put(AbilityParamKeys.CHARGE_TICKS, getI(npc, "falseChargeTicks", 20));
        m.put(AbilityParamKeys.ACTIVE_TICKS, getI(npc, "falseActiveTicks", 30));
        m.put(AbilityParamKeys.RADIUS, getD(npc, "falseTelegraphRadius", 1.2));
        m.put(AbilityParamKeys.CLONE_TAB, ScriptDataUtil.getInt(data, "df_clone_tab") <= 0
                ? 1 : ScriptDataUtil.getInt(data, "df_clone_tab"));
        final Object name = data.has("df_clone_false") ? data.get("df_clone_false") : "Drachenfels False Host";
        m.put(AbilityParamKeys.CLONE_NAME, String.valueOf(name));
        m.put(AbilityParamKeys.TELEGRAPH_COLOR, getI(npc, "telegraphColor", 0xC0FF3030));
        m.put(AbilityParamKeys.TELEGRAPH, 0);
        return m;
    }

    public static Map<String, Object> stepParams(final ICustomNpc npc) {
        final Map<String, Object> m = new HashMap<>();
        m.put(AbilityParamKeys.CHARGE_TICKS, getI(npc, "stepChargeTicks", 16));
        m.put(AbilityParamKeys.ACTIVE_TICKS, getI(npc, "stepActiveTicks", 10));
        m.put(AbilityParamKeys.DAMAGE, getD(npc, "stepDamage", 12.0));
        m.put(AbilityParamKeys.HIT_RADIUS, getD(npc, "stepWidth", 1.25));
        m.put(AbilityParamKeys.LAND_RADIUS, getD(npc, "stepLandRadius", 1.6));
        m.put(AbilityParamKeys.TELEGRAPH_COLOR, getI(npc, "telegraphColor", 0xC0FF3030));
        m.put(AbilityParamKeys.TELEGRAPH, 0);
        return m;
    }

    public static Map<String, Object> whisperParams(final ICustomNpc npc) {
        final Map<String, Object> m = new HashMap<>();
        m.put(AbilityParamKeys.CHARGE_TICKS, getI(npc, "whisperChargeTicks", 24));
        m.put(AbilityParamKeys.ACTIVE_TICKS, getI(npc, "whisperActiveTicks", 60));
        m.put(AbilityParamKeys.RADIUS, getD(npc, "whisperArenaRadius", 12.0));
        m.put(AbilityParamKeys.INNER_RADIUS, getD(npc, "whisperThickness", 1.333));
        m.put(AbilityParamKeys.ARC_HEIGHT, getD(npc, "whisperHitHeight", 1.0));
        m.put(AbilityParamKeys.EFFECT_DURATION, getI(npc, "whisperBlindDuration", 40));
        m.put(AbilityParamKeys.TRAIL_TICKS, getI(npc, "whisperWitherDuration", 60));
        m.put(AbilityParamKeys.EFFECT_AMPLIFIER, getI(npc, "whisperWitherAmp", 0));
        m.put(AbilityParamKeys.SUMMON_COUNT, getI(npc, "whisperRingCount", 3));
        m.put(AbilityParamKeys.SHOT_INTERVAL, getI(npc, "whisperRingInterval", 40));
        m.put(AbilityParamKeys.TELEGRAPH_COLOR, getI(npc, "telegraphColor", 0xC0FF3030));
        m.put(AbilityParamKeys.ZONE_COLOR, getI(npc, "telegraphColor", 0xC0FF3030));
        m.put(AbilityParamKeys.TELEGRAPH, 0);
        return m;
    }

    public static Map<String, Object> stealParams(final ICustomNpc npc) {
        final Map<String, Object> m = new HashMap<>();
        m.put(AbilityParamKeys.CHARGE_TICKS, getI(npc, "stealChargeTicks", 14));
        m.put(AbilityParamKeys.DAMAGE, getD(npc, "stealDamage", 6.0));
        m.put(AbilityParamKeys.RADIUS, getD(npc, "stealTelegraphRadius", 1.5));
        m.put(AbilityParamKeys.EFFECT_DURATION, getI(npc, "stealWeakDuration", 40));
        m.put(AbilityParamKeys.ACTIVE_TICKS, getI(npc, "stealBlindDuration", 40));
        m.put(AbilityParamKeys.TELEGRAPH_COLOR, getI(npc, "telegraphColor", 0xC0FF3030));
        m.put(AbilityParamKeys.TELEGRAPH, 0);
        return m;
    }

    public static Map<String, Object> carrierSlashParams(final ICustomNpc npc) {
        final Map<String, Object> m = new HashMap<>();
        m.put(AbilityParamKeys.CHARGE_TICKS, getI(npc, "carrierArcCastTicks", 18));
        m.put(AbilityParamKeys.DISTANCE, getD(npc, "carrierArcDistance", 4.5));
        m.put(AbilityParamKeys.RADIUS, getD(npc, "carrierArcNearWidth", 1.35));
        m.put(AbilityParamKeys.CONE_HALF_ANGLE, getD(npc, "carrierArcHalfAngle", 38.0));
        m.put(AbilityParamKeys.DAMAGE, getD(npc, "carrierArcDamage", 15.0));
        m.put(AbilityParamKeys.KNOCKBACK, getD(npc, "carrierArcKnockback", 0.85));
        m.put(AbilityParamKeys.KNOCKBACK_Y, getD(npc, "carrierArcKnockbackY", 0.18));
        m.put(AbilityParamKeys.TELEGRAPH_COLOR, getI(npc, "telegraphColor", 0xC0FF3030));
        m.put(AbilityParamKeys.TELEGRAPH, 0);
        m.put(AbilityParamKeys.PRE_DASH, 0);
        return m;
    }

    /** Phase 1 Court guards: same slash as carrier, but dash toward the player first. */
    public static Map<String, Object> guardSlashParams(final ICustomNpc npc) {
        final Map<String, Object> m = carrierSlashParams(npc);
        m.put(AbilityParamKeys.PRE_DASH, 1);
        m.put(AbilityParamKeys.ACTIVE_TICKS, getI(npc, "guardDashTicks", 8));
        m.put(AbilityParamKeys.MAX_RANGE, getD(npc, "guardDashRange", 10.0));
        m.put(AbilityParamKeys.LAND_RADIUS, getD(npc, "guardDashStandoff", 2.0));
        return m;
    }

    /** Desperation Air blobs: crimson_blob flight → seal-style green puddles R=3. */
    public static Map<String, Object> desperationBlobParams(final ICustomNpc npc) {
        final Map<String, Object> m = new HashMap<>();
        m.put(AbilityParamKeys.CHARGE_TICKS, getI(npc, "despBlobChargeTicks", 12));
        m.put(AbilityParamKeys.ACTIVE_TICKS, getI(npc, "despBlobFlightTicks", 14));
        m.put(AbilityParamKeys.ARC_HEIGHT, getD(npc, "despBlobArcHeight", 5.0));
        m.put(AbilityParamKeys.MAX_RANGE, getD(npc, "engageRadius", 60.0));
        m.put(AbilityParamKeys.LAND_RADIUS, getD(npc, "despBlobRadius", 3.0));
        m.put(AbilityParamKeys.ZONE_TICKS, getI(npc, "despBlobZoneTicks", getI(npc, "sealZoneTicks", 480)));
        m.put(AbilityParamKeys.DAMAGE, getD(npc, "despBlobDamage", getD(npc, "sealZoneDamage", 3.0)));
        m.put(AbilityParamKeys.DAMAGE_INTERVAL, getI(npc, "despBlobDamageInterval", getI(npc, "sealZoneInterval", 10)));
        m.put(AbilityParamKeys.ZONE_COLOR, getI(npc, "sealZoneColor", 0xC0143C14));
        m.put(AbilityParamKeys.EFFECT_ID, "minecraft:poison");
        m.put(AbilityParamKeys.EFFECT_DURATION, getI(npc, "sealPoisonDuration", 100));
        m.put(AbilityParamKeys.EFFECT_AMPLIFIER, getI(npc, "sealPoisonAmp", 1));
        m.put(AbilityParamKeys.TELEGRAPH, 0);
        return m;
    }

    private static String str(final IData data, final String key) {
        if (data == null || !data.has(key)) {
            return "";
        }
        final Object raw = data.get(key);
        return raw == null ? "" : String.valueOf(raw);
    }
}
