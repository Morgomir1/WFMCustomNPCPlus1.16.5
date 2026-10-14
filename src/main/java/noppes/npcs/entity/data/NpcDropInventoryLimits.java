package noppes.npcs.entity.data;

/**
 * Vanilla CustomNPCs capped death-drop item slots at 9.
 * Inventory indices: armor 0-3, weapons 4-6, drops starting at {@link #DROP_SLOT_OFFSET}.
 */
public final class NpcDropInventoryLimits {
    public static final int DROP_SLOTS = 18;
    public static final int DROP_ROWS = 9;
    public static final int DROP_SLOT_OFFSET = 7;
    public static final int MAX_DROP_SLOT = DROP_SLOTS - 1;
    public static final int CONTAINER_SIZE = DROP_SLOT_OFFSET + DROP_SLOTS;

    public static final int DROP_SLOT_X0 = 191;
    public static final int DROP_SLOT_Y0 = 16;
    public static final int DROP_ROW_HEIGHT = 21;
    public static final int DROP_COL_GAP = 115;
    public static final int DROP_CHANCE_X0 = 213;
    public static final int DROP_CHANCE_Y0 = 15;
    public static final int DROP_CHANCE_WIDTH = 45;
    public static final int DROP_CHANCE_HEIGHT = 18;

    private NpcDropInventoryLimits() {
    }

    public static int dropSlotX(final int index) {
        return DROP_SLOT_X0 + (index / DROP_ROWS) * DROP_COL_GAP;
    }

    public static int dropSlotY(final int index) {
        return DROP_SLOT_Y0 + (index % DROP_ROWS) * DROP_ROW_HEIGHT;
    }

    public static int dropChanceX(final int index) {
        return DROP_CHANCE_X0 + (index / DROP_ROWS) * DROP_COL_GAP;
    }

    public static int dropChanceY(final int index) {
        return DROP_CHANCE_Y0 + (index % DROP_ROWS) * DROP_ROW_HEIGHT;
    }
}
