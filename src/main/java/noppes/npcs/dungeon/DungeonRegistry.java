package noppes.npcs.dungeon;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class DungeonRegistry {
    private static final Map<String, DungeonDefinition> BY_ID = new LinkedHashMap<>();

    static {
        register(HerdstoneDungeon.create());
    }

    private DungeonRegistry() {
    }

    public static void register(final DungeonDefinition def) {
        BY_ID.put(def.id, def);
        for (final String alias : def.aliases()) {
            BY_ID.put(alias.toLowerCase(Locale.ROOT), def);
        }
    }

    public static DungeonDefinition get(final String name) {
        if (name == null) {
            return null;
        }
        return BY_ID.get(name.toLowerCase(Locale.ROOT));
    }

    public static Collection<String> names() {
        return Collections.unmodifiableSet(BY_ID.keySet());
    }
}
