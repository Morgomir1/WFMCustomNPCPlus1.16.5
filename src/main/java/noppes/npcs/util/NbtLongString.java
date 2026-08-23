package noppes.npcs.util;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;

/**
 * NBT {@code String} tags are written with {@code DataOutput.writeUTF}, which
 * rejects Modified UTF-8 payloads longer than 65535 bytes. Large CNPC scripts
 * and console dumps exceed that when sent via {@code PacketBuffer.writeNbt}.
 * <p>
 * Oversized values are stored as an empty primary key plus a {@code key + "Chunks"}
 * list of safe string fragments; readers reassemble transparently.
 */
public final class NbtLongString {
    /** Stay under 65535 with room for Modified UTF-8 expansion. */
    public static final int MAX_CHUNK_UTF_BYTES = 32000;
    public static final int MAX_CONSOLE_CHARS = 8000;

    private NbtLongString() {
    }

    public static int modifiedUtfLength(final String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        int len = 0;
        for (int i = 0; i < s.length(); ++i) {
            final char c = s.charAt(i);
            if (c >= 0x0001 && c <= 0x007F) {
                len += 1;
            } else if (c > 0x07FF) {
                len += 3;
            } else {
                len += 2;
            }
        }
        return len;
    }

    public static void put(final CompoundNBT nbt, final String key, String value) {
        if (value == null) {
            value = "";
        }
        nbt.remove(key + "Chunks");
        if (modifiedUtfLength(value) <= 65000) {
            nbt.putString(key, value);
            return;
        }
        nbt.putString(key, "");
        final ListNBT chunks = new ListNBT();
        int start = 0;
        while (start < value.length()) {
            int end = start;
            int bytes = 0;
            while (end < value.length()) {
                final char c = value.charAt(end);
                final int add;
                if (c >= 0x0001 && c <= 0x007F) {
                    add = 1;
                } else if (c > 0x07FF) {
                    add = 3;
                } else {
                    add = 2;
                }
                if (bytes + add > MAX_CHUNK_UTF_BYTES) {
                    break;
                }
                bytes += add;
                ++end;
            }
            if (end == start) {
                // Should not happen; force progress on pathological input.
                ++end;
            }
            chunks.add(StringNBT.valueOf(value.substring(start, end)));
            start = end;
        }
        nbt.put(key + "Chunks", chunks);
    }

    public static String get(final CompoundNBT nbt, final String key) {
        if (nbt.contains(key + "Chunks", 9)) {
            final ListNBT chunks = nbt.getList(key + "Chunks", 8);
            if (!chunks.isEmpty()) {
                final StringBuilder sb = new StringBuilder(chunks.size() * MAX_CHUNK_UTF_BYTES);
                for (int i = 0; i < chunks.size(); ++i) {
                    sb.append(chunks.getString(i));
                }
                return sb.toString();
            }
        }
        return nbt.getString(key);
    }

    public static String truncateConsole(String message) {
        if (message == null) {
            return "";
        }
        if (message.length() <= MAX_CONSOLE_CHARS) {
            return message;
        }
        return message.substring(0, MAX_CONSOLE_CHARS) + "\n... [console truncated]";
    }

    public static ListNBT saveConsoleMap(final java.util.Map<Long, String> console) {
        final ListNBT list = new ListNBT();
        if (console == null) {
            return list;
        }
        for (final java.util.Map.Entry<Long, String> entry : console.entrySet()) {
            final CompoundNBT compound = new CompoundNBT();
            compound.putLong("Long", entry.getKey());
            put(compound, "String", truncateConsole(entry.getValue()));
            list.add(compound);
        }
        return list;
    }

    public static java.util.TreeMap<Long, String> loadConsoleMap(final ListNBT list) {
        final java.util.TreeMap<Long, String> map = new java.util.TreeMap<>();
        if (list == null) {
            return map;
        }
        for (int i = 0; i < list.size(); ++i) {
            final CompoundNBT compound = list.getCompound(i);
            map.put(compound.getLong("Long"), get(compound, "String"));
        }
        return map;
    }
}
