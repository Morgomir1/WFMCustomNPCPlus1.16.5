package noppes.npcs.telegraph;

public enum TelegraphType {
    CIRCLE,
    RING,
    LINE,
    CONE,
    SQUARE,
    NONE;

    public static TelegraphType byId(final int id) {
        final TelegraphType[] values = values();
        if (id < 0 || id >= values.length) {
            return NONE;
        }
        return values[id];
    }
}
