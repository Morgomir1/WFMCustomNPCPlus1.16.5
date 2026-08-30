package noppes.npcs.script;

import noppes.npcs.api.entity.data.IData;

public final class ScriptDataUtil {
    private ScriptDataUtil() {
    }

    public static int getInt(final IData data, final String key) {
        final long value = getLong(data, key);
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    /** Absolute world-time / cooldown deadlines (game time can exceed int range). */
    public static long getLong(final IData data, final String key) {
        if (data == null || !data.has(key)) {
            return 0L;
        }
        final Object raw = data.get(key);
        if (raw == null) {
            return 0L;
        }
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        final String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(text);
        } catch (final NumberFormatException ignored) {
            try {
                return (long) Double.parseDouble(text);
            } catch (final NumberFormatException ignored2) {
                return 0L;
            }
        }
    }

    public static float getFloat(final IData data, final String key) {
        if (data == null || !data.has(key)) {
            return 0.0F;
        }
        final Object raw = data.get(key);
        if (raw == null) {
            return 0.0F;
        }
        if (raw instanceof Number) {
            return ((Number) raw).floatValue();
        }
        try {
            return Float.parseFloat(String.valueOf(raw));
        } catch (final NumberFormatException ignored) {
            return 0.0F;
        }
    }

    public static void putInt(final IData data, final String key, final int value) {
        data.put(key, String.valueOf(value));
    }

    public static void putLong(final IData data, final String key, final long value) {
        data.put(key, String.valueOf(value));
    }

    public static void putFloat(final IData data, final String key, final float value) {
        data.put(key, String.valueOf(value));
    }

    public static void putString(final IData data, final String key, final String value) {
        data.put(key, value);
    }

    public static boolean isFlag(final IData data, final String key) {
        if (data == null || !data.has(key)) {
            return false;
        }
        final Object raw = data.get(key);
        if (raw == null) {
            return false;
        }
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        if (raw instanceof Number) {
            return ((Number) raw).doubleValue() != 0.0;
        }
        final String value = String.valueOf(raw).trim();
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    public static void setFlag(final IData data, final String key, final boolean value) {
        data.put(key, value ? "1" : "0");
    }

    public static boolean isCooldownReady(final IData data, final String cdKey, final long now) {
        return now >= getLong(data, cdKey);
    }

    public static void setCooldown(final IData data, final String cdKey, final long now, final int ticks) {
        putLong(data, cdKey, now + ticks);
    }
}
