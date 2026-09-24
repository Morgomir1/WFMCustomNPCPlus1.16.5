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

    public static final int DROP_SLOT_X0 = 160;
    public static final int DROP_SLOT_Y0 = 16;
    public static final int DROP_ROW_HEIGHT = 21;
    public static final int DROP_COL_GAP = 115;
    public static final int DROP_SLIDER_X0 = 180;
    public static final int DROP_SLIDER_WIDTH = 85;
    public static final int DROP_SLIDER_HEIGHT = 20;

    private NpcDropInventoryLimits() {
    }

    public static int dropSlotX(final int index) {
        return DROP_SLOT_X0 + (index / DROP_ROWS) * DROP_COL_GAP;
    }

    public static int dropSlotY(final int index) {
        return DROP_SLOT_Y0 + (index % DROP_ROWS) * DROP_ROW_HEIGHT;
    }

    public static int dropSliderX(final int index) {
        return DROP_SLIDER_X0 + (index / DROP_ROWS) * DROP_COL_GAP;
    }

    public static int dropSliderY(final int index) {
        return 14 + (index % DROP_ROWS) * DROP_ROW_HEIGHT;
    }
}
