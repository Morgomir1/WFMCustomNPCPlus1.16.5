package noppes.npcs.script.vampire;

import noppes.npcs.api.entity.ICustomNpc;

public final class CryptGhoulDeathHelper {
    private CryptGhoulDeathHelper() {
    }

    public static void onDeath(final ICustomNpc ghoul) {
        if (ghoul == null) {
            return;
        }
        if (!ghoul.hasTag(CryptCorpseRegistry.GHOUL_TAG)) {
            ghoul.addTag(CryptCorpseRegistry.GHOUL_TAG);
        }
        CryptCorpseRegistry.registerGhoulCorpse(ghoul, ghoul.getWorld());
    }
}
