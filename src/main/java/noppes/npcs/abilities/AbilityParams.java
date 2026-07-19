package noppes.npcs.abilities;

import noppes.npcs.shared.common.util.LogWriter;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class AbilityParams {
    private static final Set<String> ALWAYS_ALLOWED = keys(
            AbilityParamKeys.TELEGRAPH,
            AbilityParamKeys.TELEGRAPH_COLOR,
            AbilityParamKeys.TELEGRAPH_FORWARD);

    private final Map<String, Object> values;

    private AbilityParams(final Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new HashMap<>(values));
    }

    public static AbilityParams merge(
            final Map<String, Object> defaults,
            final Map<String, Object> overrides,
            final Set<String> knownKeys) {
        final Map<String, Object> merged = new HashMap<>();
        if (defaults != null) {
            merged.putAll(defaults);
        }
        if (overrides != null) {
            for (final Map.Entry<String, Object> entry : overrides.entrySet()) {
                final String key = entry.getKey();
                if (knownKeys != null && !knownKeys.isEmpty()
                        && !knownKeys.contains(key)
                        && !ALWAYS_ALLOWED.contains(key)) {
                    LogWriter.info("AbilityParams: unknown key ignored: " + key);
                    continue;
                }
                merged.put(key, entry.getValue());
            }
        }
        return new AbilityParams(merged);
    }

    public double getDouble(final String key, final double fallback) {
        final Object value = this.values.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }

    public int getInt(final String key, final int fallback) {
        return (int) Math.round(this.getDouble(key, fallback));
    }

    public boolean getBoolean(final String key, final boolean fallback) {
        final Object value = this.values.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public String getString(final String key, final String fallback) {
        final Object value = this.values.get(key);
        if (value == null) {
            return fallback;
        }
        return String.valueOf(value);
    }

    public static Set<String> keys(final String... keys) {
        final Set<String> set = new HashSet<>();
        if (keys != null) {
            Collections.addAll(set, keys);
        }
        return set;
    }
}
