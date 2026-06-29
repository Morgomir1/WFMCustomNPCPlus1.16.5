package noppes.npcs.script;

import noppes.npcs.api.entity.data.IData;

public final class ScriptDataUtil {
    private ScriptDataUtil() {
    }

    public static int getInt(final IData data, final String key) {
        if (data == null || !data.has(key)) {
            return 0;
        }
        final Object raw = data.get(key);
        if (raw == null) {
            return 0;
        }
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (final NumberFormatException ignored) {
            return 0;
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

    public static void putFloat(final IData data, final String key, final float value) {
        data.put(key, String.valueOf(value));
    }

    public static void putString(final IData data, final String key, final String value) {
        data.put(key, value);
    }

    public static boolean isFlag(final IData data, final String key) {
        return "1".equals(String.valueOf(data.get(key)));
    }

    public static void setFlag(final IData data, final String key, final boolean value) {
        data.put(key, value ? "1" : "0");
    }

    public static boolean isCooldownReady(final IData data, final String cdKey, final long now) {
        return now >= getInt(data, cdKey);
    }

    public static void setCooldown(final IData data, final String cdKey, final long now, final int ticks) {
        putInt(data, cdKey, (int) (now + ticks));
    }
}
