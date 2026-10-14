package noppes.npcs.dungeon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DungeonDefinition {
    public enum Kind {
        ROOM, SAFE, ARENA, WATER, WARP, SECRET,
        CAMP_ORDER, CAMP_DESTR, PATH, CORRIDOR,
        BORDER, TOWER, MARK, SHAFT
    }

    public static final class Level {
        public final int id;
        public final String name;
        public final int floorOffset;
        public final int height;

        public Level(final int id, final String name, final int floorOffset, final int height) {
            this.id = id;
            this.name = name;
            this.floorOffset = floorOffset;
            this.height = height;
        }
    }

    public static final class Theme {
        public final String floor;
        public final String wall;
        public final String ceil;

        public Theme(final String floor, final String wall, final String ceil) {
            this.floor = floor;
            this.wall = wall;
            this.ceil = ceil;
        }
    }

    public static final class Piece {
        public final int level;
        public final String id;
        public final Kind kind;
        public final int x;
        public final int z;
        public final int w;
        public final int d;
        public final String marker;
        public final int toLevel;

        public Piece(final int level, final String id, final Kind kind, final int x, final int z,
                     final int w, final int d, final String marker, final int toLevel) {
            this.level = level;
            this.id = id;
            this.kind = kind;
            this.x = x;
            this.z = z;
            this.w = w;
            this.d = d;
            this.marker = marker == null ? "" : marker;
            this.toLevel = toLevel;
        }
    }

    public final String id;
    public final String displayName;
    public final int roomHeight;
    public final int borderHeight;
    public final boolean placeCeiling;
    public final boolean forceload;
    public final List<String> aliases;
    public final List<Level> levels;
    public final Map<Integer, Theme> themes;
    public final List<Piece> pieces;

    public DungeonDefinition(final String id, final String displayName) {
        this.id = id.toLowerCase();
        this.displayName = displayName;
        this.roomHeight = 12;
        this.borderHeight = 4;
        this.placeCeiling = true;
        this.forceload = true;
        this.aliases = new ArrayList<>();
        this.aliases.add(this.id);
        this.levels = new ArrayList<>();
        this.themes = new HashMap<>();
        this.pieces = new ArrayList<>();
    }

    public DungeonDefinition alias(final String... names) {
        for (int i = 0; i < names.length; i++) {
            this.aliases.add(names[i].toLowerCase());
        }
        return this;
    }

    public DungeonDefinition level(final int id, final String name, final int floorOffset) {
        this.levels.add(new Level(id, name, floorOffset, this.roomHeight));
        return this;
    }

    public DungeonDefinition theme(final int level, final String floor, final String wall, final String ceil) {
        this.themes.put(level, new Theme(floor, wall, ceil));
        return this;
    }

    public DungeonDefinition add(final int lv, final String id, final Kind kind, final int x, final int z,
                                 final int w, final int d, final String marker) {
        this.pieces.add(new Piece(lv, id, kind, x, z, w, d, marker, 0));
        return this;
    }

    public DungeonDefinition shaft(final int fromLv, final int toLv, final String id, final int x, final int z) {
        this.pieces.add(new Piece(fromLv, id, Kind.SHAFT, x, z, 3, 3, "gate", toLv));
        return this;
    }

    public Level levelById(final int id) {
        for (int i = 0, n = this.levels.size(); i < n; i++) {
            final Level lv = this.levels.get(i);
            if (lv.id == id) {
                return lv;
            }
        }
        return this.levels.isEmpty() ? new Level(1, "?", 0, this.roomHeight) : this.levels.get(0);
    }

    public List<String> aliases() {
        return Collections.unmodifiableList(this.aliases);
    }
}
