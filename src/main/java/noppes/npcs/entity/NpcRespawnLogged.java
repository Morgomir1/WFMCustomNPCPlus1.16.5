package noppes.npcs.entity;

/**
 * The per-NPC switch of {@link NpcRespawnLog}: "Log to server" in the respawn window of the NPC stats.
 * {@link noppes.npcs.entity.data.DataStats} implements it through a mixin and keeps it in the stats NBT.
 */
public interface NpcRespawnLogged {
    boolean cnpc$isRespawnLogged();

    void cnpc$setRespawnLogged(boolean logged);
}
