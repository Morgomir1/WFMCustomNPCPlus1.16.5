package noppes.npcs.script.vampire;

import noppes.npcs.api.IWorld;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.data.IData;
import noppes.npcs.script.ScriptEntityUtil;

public final class CryptCorpseRegistry {
    public static final String GHOUL_TAG = "crypt_ghoul";
    public static final String EATEN_TAG = "crypt_ghoul_devoured";
    public static final String CORPSE_REGISTRY_KEY = "crypt_ghoul_corpses";
    public static final String EATEN_REGISTRY_KEY = "crypt_ghoul_eaten";
    public static final int CORPSE_TTL_TICKS = 600;
    public static final int ENTITY_TYPE_ANY = -1;

    private CryptCorpseRegistry() {
    }

    public static void registerGhoulCorpse(final IEntity ghoul, final IWorld world) {
        if (ghoul == null || world == null) {
            return;
        }
        final IData registry = world.getTempdata();
        final long now = world.getTotalTime();
        final String entry = ghoul.getUUID() + ","
                + ghoul.getX() + "," + ghoul.getY() + "," + ghoul.getZ() + ","
                + (now + CORPSE_TTL_TICKS);
        final Object existing = registry.get(CORPSE_REGISTRY_KEY);
        final String list = existing != null ? String.valueOf(existing) : "";
        if (list.isEmpty()) {
            registry.put(CORPSE_REGISTRY_KEY, entry);
        } else {
            registry.put(CORPSE_REGISTRY_KEY, list + ";" + entry);
        }
    }

    public static void markEaten(final IWorld world, final String uuid, final double x, final double y, final double z) {
        if (world == null || uuid == null || uuid.isEmpty()) {
            return;
        }
        addEatenUuid(world, uuid);
        removeCorpseMarker(world, uuid);

        final IEntity ent = findEntityByUuidAt(world, uuid, x, y, z);
        if (ent == null) {
            return;
        }
        try {
            ent.addTag(EATEN_TAG);
        } catch (final Exception ignored) {
        }
        try {
            ent.getStoreddata().put("devoured", "1");
        } catch (final Exception ignored) {
        }
    }

    public static void purgeExpired(final IWorld world) {
        final IData registry = world.getTempdata();
        if (!registry.has(CORPSE_REGISTRY_KEY)) {
            return;
        }
        final long now = world.getTotalTime();
        final String raw = String.valueOf(registry.get(CORPSE_REGISTRY_KEY));
        if (raw.isEmpty()) {
            registry.remove(CORPSE_REGISTRY_KEY);
            return;
        }

        final String[] parts = raw.split(";");
        final StringBuilder kept = new StringBuilder();
        for (final String entry : parts) {
            if (entry.isEmpty()) {
                continue;
            }
            final String[] fields = entry.split(",");
            if (fields.length < 5) {
                continue;
            }
            try {
                final long expire = Long.parseLong(fields[4]);
                if (now <= expire) {
                    if (kept.length() > 0) {
                        kept.append(';');
                    }
                    kept.append(entry);
                }
            } catch (final NumberFormatException ignored) {
            }
        }

        if (kept.length() == 0) {
            registry.remove(CORPSE_REGISTRY_KEY);
        } else {
            registry.put(CORPSE_REGISTRY_KEY, kept.toString());
        }
    }

    public static CorpseTarget findCorpseUnderfoot(
            final ICustomNpc horror,
            final IWorld world,
            final double detectRadius,
            final double xzMax,
            final double yMin,
            final double yMax) {
        final CorpseTarget entityCorpse = findCorpseUnderfootEntity(
                horror, world, detectRadius, xzMax, yMin, yMax);
        if (entityCorpse != null) {
            return entityCorpse;
        }
        return findCorpseUnderfootMarker(horror, world, detectRadius, xzMax, yMin, yMax);
    }

    private static CorpseTarget findCorpseUnderfootEntity(
            final ICustomNpc horror,
            final IWorld world,
            final double detectRadius,
            final double xzMax,
            final double yMin,
            final double yMax) {
        final int range = Math.max(1, (int) Math.ceil(detectRadius));
        final IEntity[] nearby = world.getNearbyEntities(
                NpcAPI.Instance().getIPos(horror.getX(), horror.getY(), horror.getZ()),
                range,
                ENTITY_TYPE_ANY);

        final String selfUuid = horror.getUUID();
        CorpseTarget best = null;
        double bestDist = detectRadius + 1.0;

        for (final IEntity other : nearby) {
            if (other == null || selfUuid.equals(other.getUUID()) || other.isAlive()) {
                continue;
            }
            if (!other.hasTag(GHOUL_TAG) || isCorpseEaten(world, other)) {
                continue;
            }

            final double cx = other.getX();
            final double cy = other.getY();
            final double cz = other.getZ();
            if (!ScriptEntityUtil.isStandingOver(horror, cx, cy, cz, xzMax, yMin, yMax)) {
                continue;
            }

            final double dist = ScriptEntityUtil.horizontalDistance(horror.getX(), horror.getZ(), cx, cz);
            if (dist < bestDist) {
                bestDist = dist;
                best = new CorpseTarget(other.getUUID(), cx, cy, cz);
            }
        }
        return best;
    }

    private static CorpseTarget findCorpseUnderfootMarker(
            final ICustomNpc horror,
            final IWorld world,
            final double detectRadius,
            final double xzMax,
            final double yMin,
            final double yMax) {
        final IData registry = world.getTempdata();
        if (!registry.has(CORPSE_REGISTRY_KEY)) {
            return null;
        }

        final long now = world.getTotalTime();
        final String raw = String.valueOf(registry.get(CORPSE_REGISTRY_KEY));
        if (raw.isEmpty()) {
            return null;
        }

        CorpseTarget best = null;
        double bestDist = detectRadius + 1.0;

        for (final String entry : raw.split(";")) {
            if (entry.isEmpty()) {
                continue;
            }
            final String[] fields = entry.split(",");
            if (fields.length < 5) {
                continue;
            }
            final String uuid = fields[0];
            if (isUuidEaten(world, uuid)) {
                continue;
            }
            try {
                final long expire = Long.parseLong(fields[4]);
                if (now > expire) {
                    continue;
                }
                final double cx = Double.parseDouble(fields[1]);
                final double cy = Double.parseDouble(fields[2]);
                final double cz = Double.parseDouble(fields[3]);
                if (!ScriptEntityUtil.isStandingOver(horror, cx, cy, cz, xzMax, yMin, yMax)) {
                    continue;
                }
                final double dist = ScriptEntityUtil.horizontalDistance(horror.getX(), horror.getZ(), cx, cz);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = new CorpseTarget(uuid, cx, cy, cz);
                }
            } catch (final NumberFormatException ignored) {
            }
        }
        return best;
    }

    private static boolean isCorpseEaten(final IWorld world, final IEntity entity) {
        if (entity == null) {
            return false;
        }
        if (isUuidEaten(world, entity.getUUID())) {
            return true;
        }
        try {
            if (entity.hasTag(EATEN_TAG)) {
                return true;
            }
        } catch (final Exception ignored) {
        }
        try {
            final Object devoured = entity.getStoreddata().get("devoured");
            return "1".equals(String.valueOf(devoured));
        } catch (final Exception ignored) {
            return false;
        }
    }

    private static boolean isUuidEaten(final IWorld world, final String uuid) {
        final IData registry = world.getTempdata();
        if (!registry.has(EATEN_REGISTRY_KEY)) {
            return false;
        }
        final String raw = String.valueOf(registry.get(EATEN_REGISTRY_KEY));
        if (raw.isEmpty()) {
            return false;
        }
        return raw.contains(uuid + ";") || raw.equals(uuid);
    }

    private static void addEatenUuid(final IWorld world, final String uuid) {
        if (isUuidEaten(world, uuid)) {
            return;
        }
        final IData registry = world.getTempdata();
        final Object existing = registry.get(EATEN_REGISTRY_KEY);
        final String raw = existing != null ? String.valueOf(existing) : "";
        if (raw.isEmpty()) {
            registry.put(EATEN_REGISTRY_KEY, uuid + ";");
        } else {
            registry.put(EATEN_REGISTRY_KEY, raw + uuid + ";");
        }
    }

    private static IEntity findEntityByUuidAt(
            final IWorld world,
            final String uuid,
            final double x,
            final double y,
            final double z) {
        final int range = 4;
        final IEntity[] nearby = world.getNearbyEntities(
                NpcAPI.Instance().getIPos(x, y, z),
                range,
                ENTITY_TYPE_ANY);
        for (final IEntity ent : nearby) {
            if (ent != null && uuid.equals(ent.getUUID())) {
                return ent;
            }
        }
        return null;
    }

    private static void removeCorpseMarker(final IWorld world, final String uuid) {
        final IData registry = world.getTempdata();
        if (!registry.has(CORPSE_REGISTRY_KEY)) {
            return;
        }
        final String raw = String.valueOf(registry.get(CORPSE_REGISTRY_KEY));
        if (raw.isEmpty()) {
            return;
        }

        final String prefix = uuid + ",";
        final StringBuilder kept = new StringBuilder();
        for (final String entry : raw.split(";")) {
            if (entry.isEmpty() || entry.startsWith(prefix)) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append(';');
            }
            kept.append(entry);
        }

        if (kept.length() == 0) {
            registry.remove(CORPSE_REGISTRY_KEY);
        } else {
            registry.put(CORPSE_REGISTRY_KEY, kept.toString());
        }
    }
}
