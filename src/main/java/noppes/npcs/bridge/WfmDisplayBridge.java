package noppes.npcs.bridge;

/**
 * Extra display flags stored on CustomNPCs {@code DataDisplay}.
 */
public interface WfmDisplayBridge {
    boolean wfm$isShowOnMap();

    void wfm$setShowOnMap(boolean value);
}
