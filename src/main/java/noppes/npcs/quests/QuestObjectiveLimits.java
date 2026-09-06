package noppes.npcs.quests;

/**
 * Vanilla CustomNPCs capped dialog/kill/manual/item quest editors at 3 entries.
 * These limits are used by the overlaid editors and item inventory size.
 */
public final class QuestObjectiveLimits {
    public static final int MAX = 16;
    public static final int AMOUNT_ID_OFFSET = 16;
    public static final int MIN_EDITOR_SLOTS = 3;

    public static final int ITEM_SLOTS = 12;
    public static final int ITEM_COLUMNS = 4;
    public static final int ITEM_SLOT_X = 8;
    public static final int ITEM_SLOT_Y = 50;
    public static final int ITEM_SLOT_SIZE = 18;
    public static final int PLAYER_INV_Y = 113;
    public static final int HOTBAR_Y = 171;

    private QuestObjectiveLimits() {
    }

    public static int editorSlots(final int usedCount) {
        if (usedCount <= 0) {
            return MIN_EDITOR_SLOTS;
        }
        return Math.min(MAX, Math.max(MIN_EDITOR_SLOTS, usedCount + 1));
    }

    public static int itemSlotX(final int index) {
        return ITEM_SLOT_X + (index % ITEM_COLUMNS) * ITEM_SLOT_SIZE;
    }

    public static int itemSlotY(final int index) {
        return ITEM_SLOT_Y + (index / ITEM_COLUMNS) * ITEM_SLOT_SIZE;
    }
}
