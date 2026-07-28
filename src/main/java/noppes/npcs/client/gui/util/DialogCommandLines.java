package noppes.npcs.client.gui.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for multi-line dialog commands stored as a single DialogCommand string
 * (lines joined with {@code \n}).
 */
public final class DialogCommandLines {
    public static final int MAX_COMMANDS = 8;

    private DialogCommandLines() {
    }

    public static List<String> split(final String command) {
        final List<String> lines = new ArrayList<String>();
        if (command == null || command.isEmpty()) {
            lines.add("");
            return lines;
        }
        final String normalized = command.replace("\r\n", "\n").replace('\r', '\n');
        final String[] parts = normalized.split("\n", -1);
        for (final String part : parts) {
            lines.add(part);
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        while (lines.size() > MAX_COMMANDS) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    public static String join(final List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (final String line : lines) {
            if (line == null) {
                continue;
            }
            final String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!first) {
                sb.append('\n');
            }
            sb.append(trimmed);
            first = false;
        }
        return sb.toString();
    }

    public static void ensureEditable(final List<String> lines) {
        if (lines.isEmpty()) {
            lines.add("");
        }
    }
}
