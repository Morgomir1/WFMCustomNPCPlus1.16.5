package noppes.npcs.entity.data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Death-drop chance of an NPC inventory slot in hundredths of a percent: 1 is 0.01%, {@link #FULL} is 100%.
 * Vanilla CustomNPCs kept whole percents; they are still written to the legacy "DropChance" tag.
 */
public final class NpcDropChance {
    public static final int PER_PERCENT = 100;
    public static final int FULL = 100 * PER_PERCENT;

    private NpcDropChance() {
    }

    public static int fromPercent(final int percent) {
        return percent * PER_PERCENT;
    }

    /** Whole percent for builds that read only "DropChance": a non-zero chance stays at least 1%. */
    public static int toLegacyPercent(final int hundredths) {
        if (hundredths <= 0) {
            return 0;
        }
        return Math.min(100, Math.max(1, Math.round(hundredths / (float) PER_PERCENT)));
    }

    public static int clamp(final int hundredths) {
        return Math.max(0, Math.min(FULL, hundredths));
    }

    /** Percent typed by a player, "." or "," as decimal separator; -1 when the text is not a number. */
    public static int parsePercent(final String text) {
        final String value = text.trim().replace(',', '.');
        if (value.isEmpty() || value.equals(".")) {
            return -1;
        }
        try {
            final BigDecimal hundredths = new BigDecimal(value).movePointRight(2).setScale(0, RoundingMode.HALF_UP);
            if (hundredths.compareTo(BigDecimal.valueOf(FULL)) > 0) {
                return FULL;
            }
            return clamp(hundredths.intValue());
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    public static String formatPercent(final int hundredths) {
        return BigDecimal.valueOf(hundredths, 2).stripTrailingZeros().toPlainString();
    }
}
