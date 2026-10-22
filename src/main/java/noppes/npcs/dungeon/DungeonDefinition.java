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

    public enum Shape {
        RECT, ELLIPSE, POLY, PATH
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
        public Shape shape;
        public int[] points;
        public int thickness;
        public boolean openAir;
        public int wallHeight;
        public String floorBlock;
        /** Нижний конец спуска. MIN_VALUE — вертикальная шахта в (x, z). */
        public int endX = Integer.MIN_VALUE;
        public int endZ = Integer.MIN_VALUE;

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
            this.shape = Shape.RECT;
            this.points = new int[0];
            this.thickness = 6;
            this.openAir = false;
            this.wallHeight = 0;
            this.floorBlock = "";
        }
    }

    public final String id;
    public final String displayName;
    public final int roomHeight;
    public final int borderHeight;
    public final boolean placeCeiling;
    public final boolean forceload;
    public final boolean clearVolume;
    public final boolean groundPlane;
    public final int clearHeadroom;
    public final String groundBlock;
    public final String groundUnder;
    public final List<String> aliases;
    public final List<Level> levels;
    public final Map<Integer, Theme> themes;
    public final List<Piece> pieces;

    private Piece last;

    public DungeonDefinition(final String id, final String displayName) {
        this.id = id.toLowerCase();
        this.displayName = displayName;
        this.roomHeight = 16;
        this.borderHeight = 4;
        this.placeCeiling = true;
        this.forceload = true;
        this.clearVolume = true;
        this.groundPlane = true;
        this.clearHeadroom = 20;
        this.groundBlock = "minecraft:grass_block";
        this.groundUnder = "minecraft:dirt";
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
        return push(new Piece(lv, id, kind, x, z, w, d, marker, 0));
    }

    public DungeonDefinition addEllipse(final int lv, final String id, final Kind kind, final int x, final int z,
                                        final int w, final int d, final String marker) {
        add(lv, id, kind, x, z, w, d, marker);
        this.last.shape = Shape.ELLIPSE;
        return this;
    }

    public DungeonDefinition addCircle(final int lv, final String id, final Kind kind, final int cx, final int cz,
                                       final int r, final String marker) {
        add(lv, id, kind, cx - r, cz - r, r * 2 + 1, r * 2 + 1, marker);
        this.last.shape = Shape.ELLIPSE;
        return this;
    }

    public DungeonDefinition addPoly(final int lv, final String id, final Kind kind, final String marker,
                                     final int... xz) {
        final int[] bb = DungeonGeometry.bbox(xz, 0);
        add(lv, id, kind, bb[0], bb[1], bb[2], bb[3], marker);
        this.last.shape = Shape.POLY;
        this.last.points = xz;
        return this;
    }

    public DungeonDefinition addPath(final int lv, final String id, final Kind kind, final int width,
                                     final int... xz) {
        final int pad = (width + 1) / 2;
        final int[] bb = DungeonGeometry.bbox(xz, pad);
        add(lv, id, kind, bb[0], bb[1], bb[2], bb[3], "");
        this.last.shape = Shape.PATH;
        this.last.points = xz;
        this.last.thickness = width;
        return this;
    }

    public DungeonDefinition shaft(final int fromLv, final int toLv, final String id, final int x, final int z) {
        return push(new Piece(fromLv, id, Kind.SHAFT, x, z, 3, 3, "gate", toLv));
    }

    /** Спуск между этажами спирали: 3×3 на верхнем этаже и 3×3 на нижнем, пол идёт ступеньками. */
    public DungeonDefinition ramp(final int fromLv, final int toLv, final String id, final int x1, final int z1,
                                  final int x2, final int z2) {
        push(new Piece(fromLv, id, Kind.SHAFT, x1, z1, 3, 3, "gate", toLv));
        this.last.endX = x2;
        this.last.endZ = z2;
        return this;
    }

    public DungeonDefinition air() {
        if (this.last != null) {
            this.last.openAir = true;
        }
        return this;
    }

    public DungeonDefinition walls(final int height) {
        if (this.last != null) {
            this.last.wallHeight = height;
        }
        return this;
    }

    public DungeonDefinition noWalls() {
        if (this.last != null) {
            this.last.wallHeight = -1;
        }
        return this;
    }

    public DungeonDefinition floor(final String block) {
        if (this.last != null) {
            this.last.floorBlock = block == null ? "" : block;
        }
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

    private DungeonDefinition push(final Piece piece) {
        this.pieces.add(piece);
        this.last = piece;
        return this;
    }
}
