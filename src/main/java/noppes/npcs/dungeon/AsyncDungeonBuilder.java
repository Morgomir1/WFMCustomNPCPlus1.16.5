package noppes.npcs.dungeon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import noppes.npcs.dungeon.DungeonDefinition.Kind;
import noppes.npcs.dungeon.DungeonDefinition.Level;
import noppes.npcs.dungeon.DungeonDefinition.Piece;
import noppes.npcs.dungeon.DungeonDefinition.Theme;

@Mod.EventBusSubscriber(modid = "customnpcs", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AsyncDungeonBuilder {
    private static final int BLOCKS_PER_TICK = 400;
    /** Потолок итераций: воздух→воздух не считается placement, иначе clear AABB вешает тик. */
    private static final int CHECKS_PER_TICK = 1200;
    private static final long NANOS_PER_TICK = 8_000_000L;
    /** UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE — без соседних апдейтов, иначе watchdog. */
    private static final int PLACE_FLAGS = 2 | 16;
    private static final BlockPos.Mutable POS = new BlockPos.Mutable();

    private static Session session;

    private AsyncDungeonBuilder() {
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || session == null) {
            return;
        }
        session.tick();
    }

    public static String start(final ServerPlayerEntity player, final DungeonDefinition def, final Set<Integer> levels,
                               final boolean dry, final boolean force) {
        if (!force && session != null) {
            return "§cУже идёт генерация. /cncpdungeon status  или  /cncpdungeon cancel";
        }
        if (force) {
            cancel();
        }
        final int ox = player.blockPosition().getX();
        final int oy = player.blockPosition().getY();
        final int oz = player.blockPosition().getZ();
        final List<FillJob> jobs = compile(def, ox, oy, oz, levels);
        if (dry) {
            return "§e[dry] §f" + jobs.size() + " шагов, origin " + ox + " " + oy + " " + oz
                    + " §7" + def.displayName;
        }
        final ServerWorld world = (ServerWorld) player.level;
        session = new Session(world, player.getUUID(), def.displayName, jobs);
        if (def.forceload) {
            session.forceArea(bounds(def, levels), ox, oz);
        }
        return "§2Каркас «" + def.displayName + "»: §f" + jobs.size() + " шагов §7с "
                + ox + " " + oy + " " + oz + " §8(чанки грузятся без подвисания тика)";
    }

    public static String cancel() {
        if (session == null) {
            return "§7Генерация не запущена.";
        }
        session.releaseChunks();
        session = null;
        return "§7Генерация остановлена.";
    }

    public static String status() {
        if (session == null) {
            return "§7Генерация не запущена.";
        }
        return "§e" + session.index + "/" + session.jobs.size() + " §7" + session.name
                + "  §f" + session.percent() + "%" + session.remainingText()
                + "  блок " + session.jobCursor + "/" + session.currentVolume();
    }

    static List<FillJob> compile(final DungeonDefinition def, final int ox, final int oy, final int oz,
                                 final Set<Integer> levels) {
        final List<FillJob> jobs = new ArrayList<>();
        prepareSite(jobs, def, ox, oy, oz, levels);
        final List<Piece> pieces = def.pieces;
        final List<Piece> openings = new ArrayList<>();
        final List<Piece> rooms = new ArrayList<>();
        for (int i = 0, n = pieces.size(); i < n; i++) {
            final Piece rec = pieces.get(i);
            if (!levels.contains(rec.level)) {
                continue;
            }
            if (DungeonGeometry.isWalk(rec)) {
                openings.add(rec);
            } else if (DungeonGeometry.isSolidRoom(rec)) {
                rooms.add(rec);
            }
        }
        for (int i = 0, n = pieces.size(); i < n; i++) {
            final Piece rec = pieces.get(i);
            if (!levels.contains(rec.level)) {
                continue;
            }
            compilePiece(jobs, def, rec, ox, oy, oz, openings, rooms);
        }
        carveSkyWells(jobs, def, pieces, ox, oy, oz, levels);
        return jobs;
    }

    private static void prepareSite(final List<FillJob> jobs, final DungeonDefinition def, final int ox,
                                    final int oy, final int oz, final Set<Integer> levels) {
        final int[] bb = bounds(def, levels);
        final int x1 = ox + bb[0];
        final int z1 = oz + bb[1];
        final int x2 = ox + bb[2] - 1;
        final int z2 = oz + bb[3] - 1;
        if (x2 < x1 || z2 < z1) {
            return;
        }
        if (def.clearVolume) {
            final int[] yr = clearYRange(def, oy, levels);
            addClear(jobs, x1, yr[0], z1, x2, yr[1], z2);
        }
        if (def.groundPlane && levels.contains(1)) {
            final Set<Integer> only1 = new HashSet<>();
            only1.add(1);
            final int[] g = bounds(def, only1);
            final int gx1 = ox + g[0];
            final int gz1 = oz + g[1];
            final int gx2 = ox + g[2] - 1;
            final int gz2 = oz + g[3] - 1;
            final int y0 = floorY(def, 1, oy);
            if (gx2 >= gx1 && gz2 >= gz1) {
                addFill(jobs, gx1, y0 - 1, gz1, gx2, y0 - 1, gz2, def.groundUnder);
                addFill(jobs, gx1, y0, gz1, gx2, y0, gz2, def.groundBlock);
            }
        }
    }

    private static int[] clearYRange(final DungeonDefinition def, final int oy, final Set<Integer> levels) {
        int yMin = Integer.MAX_VALUE;
        int yMax = Integer.MIN_VALUE;
        final List<Level> all = def.levels;
        for (int i = 0, n = all.size(); i < n; i++) {
            final Level lv = all.get(i);
            if (!levels.contains(lv.id)) {
                continue;
            }
            final int y0 = oy + lv.floorOffset;
            if (y0 < yMin) {
                yMin = y0;
            }
            int top = y0 + lv.height;
            if (lv.id == 1) {
                top = Math.max(top, y0 + def.clearHeadroom);
            }
            if (top > yMax) {
                yMax = top;
            }
        }
        if (def.groundPlane && levels.contains(1)) {
            final int under = floorY(def, 1, oy) - 1;
            if (under < yMin) {
                yMin = under;
            }
        }
        if (yMin < 1) {
            yMin = 1;
        }
        if (yMax > 255) {
            yMax = 255;
        }
        if (yMax < yMin) {
            yMax = yMin;
        }
        return new int[] { yMin, yMax };
    }

    private static void addClear(final List<FillJob> jobs, final int x1, final int y1, final int z1,
                                 final int x2, final int y2, final int z2) {
        final int ya = Math.min(y1, y2);
        final int yb = Math.max(y1, y2);
        for (int y = ya; y <= yb; y++) {
            addFill(jobs, x1, y, z1, x2, y, z2, "minecraft:air");
        }
    }

    private static void compilePiece(final List<FillJob> jobs, final DungeonDefinition def, final Piece rec,
                                     final int ox, final int oy, final int oz, final List<Piece> openings,
                                     final List<Piece> rooms) {
        switch (rec.kind) {
            case BORDER:
                buildBorder(jobs, def, rec, ox, oy, oz);
                break;
            case PATH:
                rasterFootprint(jobs, def, rec, ox, oy, oz, openings, rooms, true);
                break;
            case CORRIDOR:
                rasterFootprint(jobs, def, rec, ox, oy, oz, openings, rooms, false);
                break;
            case SHAFT:
                buildShaft(jobs, def, rec, ox, oy, oz);
                break;
            case MARK:
                buildMark(jobs, def, rec, ox, oy, oz);
                break;
            case TOWER:
                buildTower(jobs, def, rec, ox, oy, oz);
                break;
            default:
                rasterFootprint(jobs, def, rec, ox, oy, oz, openings, rooms, rec.openAir);
                break;
        }
    }

    private static void rasterFootprint(final List<FillJob> jobs, final DungeonDefinition def, final Piece rec,
                                        final int ox, final int oy, final int oz, final List<Piece> openings,
                                        final List<Piece> rooms, final boolean openAir) {
        final int y0 = floorY(def, rec.level, oy);
        final Theme th = theme(def, rec.level);
        final String floor = floorBlock(rec, th);
        final boolean corridor = rec.kind == Kind.CORRIDOR;
        final boolean placeWalls = rec.kind != Kind.PATH && rec.wallHeight >= 0;
        final int defaultH = corridor ? 7 : def.levelById(rec.level).height;
        final int h = rec.wallHeight > 0 ? rec.wallHeight : (openAir ? 4 : defaultH);
        final int xMin = rec.x;
        final int zMin = rec.z;
        final int xMax = rec.x + rec.w - 1;
        final int zMax = rec.z + rec.d - 1;
        for (int z = zMin; z <= zMax; z++) {
            int floorRun = Integer.MIN_VALUE;
            int wallRun = Integer.MIN_VALUE;
            int ceilRun = Integer.MIN_VALUE;
            for (int x = xMin; x <= xMax + 1; x++) {
                final boolean inside = x <= xMax && DungeonGeometry.contains(rec, x, z);
                if (inside) {
                    if (floorRun == Integer.MIN_VALUE) {
                        floorRun = x;
                    }
                } else if (floorRun != Integer.MIN_VALUE) {
                    addFill(jobs, ox + floorRun, y0, oz + z, ox + x - 1, y0, oz + z, floor);
                    floorRun = Integer.MIN_VALUE;
                }
                final boolean wallHere = placeWalls && inside && DungeonGeometry.perimeter(rec, x, z)
                        && !skipWall(rec, x, z, openings, rooms);
                if (wallHere) {
                    if (wallRun == Integer.MIN_VALUE) {
                        wallRun = x;
                    }
                } else if (wallRun != Integer.MIN_VALUE) {
                    addFill(jobs, ox + wallRun, y0 + 1, oz + z, ox + x - 1, y0 + h - 1, oz + z, th.wall);
                    wallRun = Integer.MIN_VALUE;
                }
                final boolean ceilHere = inside && def.placeCeiling && !openAir && !corridor;
                final boolean corrCeil = inside && def.placeCeiling && corridor;
                if (ceilHere || corrCeil) {
                    if (ceilRun == Integer.MIN_VALUE) {
                        ceilRun = x;
                    }
                } else if (ceilRun != Integer.MIN_VALUE) {
                    addFill(jobs, ox + ceilRun, y0 + h, oz + z, ox + x - 1, y0 + h, oz + z, th.ceil);
                    ceilRun = Integer.MIN_VALUE;
                }
            }
        }
        if (!rec.marker.isEmpty()) {
            addSet(jobs, ox + rec.x + rec.w / 2, y0 + 1, oz + rec.z + rec.d / 2, marker(rec.marker));
        }
    }

    /** Открытые арены ниже поверхности: колодец после всех комнат, иначе поздний кусок кладёт крышу обратно. */
    private static void carveSkyWells(final List<FillJob> jobs, final DungeonDefinition def, final List<Piece> pieces,
                                      final int ox, final int oy, final int oz, final Set<Integer> levels) {
        for (int i = 0, n = pieces.size(); i < n; i++) {
            final Piece rec = pieces.get(i);
            if (!levels.contains(rec.level) || !rec.openAir || rec.kind != Kind.ARENA || rec.level <= 1) {
                continue;
            }
            final int y0 = floorY(def, rec.level, oy);
            final int h = rec.wallHeight > 0 ? rec.wallHeight : 4;
            carveSkyWell(jobs, def, rec, ox, oy, oz, y0, h);
        }
    }

    /**
     * Воздух от верха стен арены до границы постройки (y 255) и парапет на полу L1.
     * clearHeadroom сюда не годится: выше него остаются холмы и кроны, и из арены не видно неба.
     */
    private static void carveSkyWell(final List<FillJob> jobs, final DungeonDefinition def, final Piece rec,
                                     final int ox, final int oy, final int oz, final int y0, final int h) {
        final int yTop = 255;
        int yFrom = y0 + h;
        if (yFrom < 0) {
            yFrom = 0;
        }
        if (yFrom > yTop) {
            return;
        }
        final int xMin = rec.x;
        final int zMin = rec.z;
        final int xMax = rec.x + rec.w - 1;
        final int zMax = rec.z + rec.d - 1;
        for (int z = zMin; z <= zMax; z++) {
            int airRun = Integer.MIN_VALUE;
            for (int x = xMin; x <= xMax + 1; x++) {
                final boolean inside = x <= xMax && DungeonGeometry.contains(rec, x, z);
                if (inside) {
                    if (airRun == Integer.MIN_VALUE) {
                        airRun = x;
                    }
                } else if (airRun != Integer.MIN_VALUE) {
                    addClear(jobs, ox + airRun, yFrom, oz + z, ox + x - 1, yTop, oz + z);
                    airRun = Integer.MIN_VALUE;
                }
            }
        }
        final int y1 = floorY(def, 1, oy);
        if (y1 < 0 || y1 > yTop - 3) {
            return;
        }
        for (int z = zMin; z <= zMax; z++) {
            int rimRun = Integer.MIN_VALUE;
            for (int x = xMin; x <= xMax + 1; x++) {
                final boolean inside = x <= xMax && DungeonGeometry.contains(rec, x, z);
                final boolean rim = inside && DungeonGeometry.perimeter(rec, x, z);
                if (rim) {
                    if (rimRun == Integer.MIN_VALUE) {
                        rimRun = x;
                    }
                } else if (rimRun != Integer.MIN_VALUE) {
                    addFill(jobs, ox + rimRun, y1, oz + z, ox + x - 1, y1 + 3, oz + z, "minecraft:dark_oak_log");
                    rimRun = Integer.MIN_VALUE;
                }
            }
        }
    }

    private static boolean skipWall(final Piece rec, final int x, final int z, final List<Piece> openings,
                                    final List<Piece> rooms) {
        if (DungeonGeometry.isSolidRoom(rec)) {
            return DungeonGeometry.anyContains(openings, rec.level, x, z);
        }
        if (DungeonGeometry.isWalk(rec)) {
            return DungeonGeometry.anyContains(rooms, rec.level, x, z);
        }
        return false;
    }

    private static void buildBorder(final List<FillJob> jobs, final DungeonDefinition def, final Piece rec,
                                    final int ox, final int oy, final int oz) {
        final int x1 = ox + rec.x;
        final int z1 = oz + rec.z;
        final int x2 = x1 + rec.w - 1;
        final int z2 = z1 + rec.d - 1;
        final int y0 = floorY(def, rec.level, oy);
        final int y1 = y0 + def.borderHeight - 1;
        addFill(jobs, x1, y0, z1, x2, y1, z1, "minecraft:dark_oak_log");
        addFill(jobs, x1, y0, z2, x2, y1, z2, "minecraft:dark_oak_log");
        addFill(jobs, x1, y0, z1, x1, y1, z2, "minecraft:dark_oak_log");
        addFill(jobs, x2, y0, z1, x2, y1, z2, "minecraft:dark_oak_log");
        final int by = y0 + def.borderHeight;
        addFill(jobs, x1, by, z1, x2, by, z1, "minecraft:barrier");
        addFill(jobs, x1, by, z2, x2, by, z2, "minecraft:barrier");
        addFill(jobs, x1, by, z1, x1, by, z2, "minecraft:barrier");
        addFill(jobs, x2, by, z1, x2, by, z2, "minecraft:barrier");
    }

    private static void buildTower(final List<FillJob> jobs, final DungeonDefinition def, final Piece rec,
                                   final int ox, final int oy, final int oz) {
        final int x1 = ox + rec.x;
        final int z1 = oz + rec.z;
        final int y0 = floorY(def, rec.level, oy);
        addFill(jobs, x1, y0, z1, x1 + rec.w - 1, y0 + 5, z1 + rec.d - 1, "minecraft:dark_oak_log");
        if (!rec.marker.isEmpty()) {
            addSet(jobs, x1, y0 + 6, z1, marker(rec.marker));
        }
    }

    private static void buildMark(final List<FillJob> jobs, final DungeonDefinition def, final Piece rec,
                                  final int ox, final int oy, final int oz) {
        addSet(jobs, ox + rec.x, floorY(def, rec.level, oy) + 1, oz + rec.z, marker(rec.marker));
    }

    private static void buildShaft(final List<FillJob> jobs, final DungeonDefinition def, final Piece rec,
                                   final int ox, final int oy, final int oz) {
        final int x1 = ox + rec.x;
        final int z1 = oz + rec.z;
        int yTop = floorY(def, rec.level, oy);
        int yBot = floorY(def, rec.toLevel, oy);
        if (yBot > yTop) {
            final int t = yBot;
            yBot = yTop;
            yTop = t;
        }
        addFill(jobs, x1, yBot, z1, x1 + rec.w - 1, yTop + def.levelById(rec.level).height, z1 + rec.d - 1,
                "minecraft:air");
        addFill(jobs, x1, yBot, z1, x1, yTop, z1, "minecraft:ladder");
        addSet(jobs, x1 + 1, yTop + 1, z1 + 1, marker("gate"));
    }

    private static int floorY(final DungeonDefinition def, final int level, final int oy) {
        return oy + def.levelById(level).floorOffset;
    }

    private static Theme theme(final DungeonDefinition def, final int level) {
        final Theme t = def.themes.get(level);
        return t != null ? t : new Theme("minecraft:stone", "minecraft:stone", "minecraft:stone");
    }

    private static String floorBlock(final Piece rec, final Theme th) {
        if (rec.floorBlock != null && !rec.floorBlock.isEmpty()) {
            return rec.floorBlock;
        }
        switch (rec.kind) {
            case SAFE:
                return "minecraft:mossy_cobblestone";
            case ARENA:
                return "minecraft:red_nether_bricks";
            case WATER:
                return "minecraft:prismarine_bricks";
            case WARP:
                return "minecraft:warped_nylium";
            case SECRET:
                return "minecraft:obsidian";
            case CAMP_ORDER:
                return "minecraft:blue_terracotta";
            case CAMP_DESTR:
                return "minecraft:purple_terracotta";
            default:
                return th.floor;
        }
    }

    private static String marker(final String key) {
        switch (key) {
            case "boss":
                return "minecraft:red_wool";
            case "puzzle":
                return "minecraft:yellow_wool";
            case "item":
                return "minecraft:lime_wool";
            case "npc":
            case "pack":
                return "minecraft:white_wool";
            case "valve":
            case "elite":
                return "minecraft:orange_wool";
            case "stake":
                return "minecraft:dark_oak_log";
            case "totem":
            case "menhir":
                return "minecraft:bone_block";
            case "heart":
                return "minecraft:redstone_block";
            case "crystal":
                return "minecraft:emerald_block";
            case "herb":
                return "minecraft:lime_wool";
            case "fog":
                return "minecraft:green_wool";
            default:
                return "minecraft:black_wool";
        }
    }

    private static int[] bounds(final DungeonDefinition def, final Set<Integer> levels) {
        int x1 = 0;
        int z1 = 0;
        int x2 = 0;
        int z2 = 0;
        boolean first = true;
        final List<Piece> pieces = def.pieces;
        for (int i = 0, n = pieces.size(); i < n; i++) {
            final Piece rec = pieces.get(i);
            if (!levels.contains(rec.level)) {
                continue;
            }
            final int a = rec.x;
            final int b = rec.z;
            final int c = rec.x + rec.w;
            final int d = rec.z + rec.d;
            if (first) {
                x1 = a;
                z1 = b;
                x2 = c;
                z2 = d;
                first = false;
            } else {
                if (a < x1) {
                    x1 = a;
                }
                if (b < z1) {
                    z1 = b;
                }
                if (c > x2) {
                    x2 = c;
                }
                if (d > z2) {
                    z2 = d;
                }
            }
        }
        return new int[] { x1, z1, x2, z2 };
    }

    private static void addFill(final List<FillJob> jobs, final int x1, final int y1, final int z1,
                                final int x2, final int y2, final int z2, final String block) {
        jobs.add(new FillJob(x1, y1, z1, x2, y2, z2, resolve(block)));
    }

    private static void addSet(final List<FillJob> jobs, final int x, final int y, final int z, final String block) {
        jobs.add(new FillJob(x, y, z, x, y, z, resolve(block)));
    }

    private static BlockState resolve(final String id) {
        final Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        if (block == null) {
            return Blocks.STONE.defaultBlockState();
        }
        return block.defaultBlockState();
    }

    public static Set<Integer> parseLevels(final String extra, final DungeonDefinition def) {
        final Set<Integer> levels = new HashSet<>();
        if (extra != null && !extra.isEmpty()) {
            final String[] parts = extra.trim().split("\\s+");
            for (int i = 0; i < parts.length; i++) {
                try {
                    final int n = Integer.parseInt(parts[i]);
                    if (n >= 1 && n <= 4) {
                        levels.add(n);
                    }
                } catch (final NumberFormatException ignored) {
                }
            }
        }
        if (levels.isEmpty()) {
            final List<Level> all = def.levels;
            for (int i = 0, n = all.size(); i < n; i++) {
                levels.add(all.get(i).id);
            }
        }
        return levels;
    }

    public static boolean hasFlag(final String extra, final String flag) {
        if (extra == null) {
            return false;
        }
        final String[] parts = extra.trim().toLowerCase(Locale.ROOT).split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            if (flag.equals(parts[i])) {
                return true;
            }
        }
        return false;
    }

    private static final class FillJob {
        final int x1;
        final int y1;
        final int z1;
        final int dx;
        final int dy;
        final int dz;
        final int volume;
        final BlockState state;
        int cursor;
        boolean waitingForChunk;

        FillJob(final int xa, final int ya, final int za, final int xb, final int yb, final int zb,
                final BlockState state) {
            this.x1 = Math.min(xa, xb);
            this.y1 = Math.min(ya, yb);
            this.z1 = Math.min(za, zb);
            this.dx = Math.abs(xb - xa) + 1;
            this.dy = Math.abs(yb - ya) + 1;
            this.dz = Math.abs(zb - za) + 1;
            this.volume = this.dx * this.dy * this.dz;
            this.state = state;
        }

        int place(final Session owner, final int budget, final long deadlineNanos) {
            final ServerWorld world = owner.world;
            this.waitingForChunk = false;
            int n = 0;
            int checks = 0;
            int lastCx = Integer.MIN_VALUE;
            int lastCz = Integer.MIN_VALUE;
            Chunk chunk = null;
            while (this.cursor < this.volume && n < budget && checks < CHECKS_PER_TICK) {
                if ((checks & 15) == 0 && System.nanoTime() >= deadlineNanos) {
                    break;
                }
                checks++;
                final int x = this.x1 + this.cursor % this.dx;
                final int rem = this.cursor / this.dx;
                final int y = this.y1 + rem % this.dy;
                final int z = this.z1 + rem / this.dy;
                if (y < 0 || y > 255) {
                    this.cursor++;
                    continue;
                }
                final int cx = x >> 4;
                final int cz = z >> 4;
                if (cx != lastCx || cz != lastCz) {
                    chunk = world.getChunkSource().getChunkNow(cx, cz);
                    lastCx = cx;
                    lastCz = cz;
                    if (chunk == null) {
                        owner.forceChunk(cx, cz);
                        this.waitingForChunk = true;
                        break;
                    }
                }
                POS.set(x, y, z);
                final BlockState old = chunk.getBlockState(POS);
                if (old == this.state) {
                    this.cursor++;
                    continue;
                }
                world.setBlock(POS, this.state, PLACE_FLAGS);
                this.cursor++;
                n++;
            }
            return n;
        }

        boolean done() {
            return this.cursor >= this.volume;
        }
    }

    private static final class Session {
        final ServerWorld world;
        final UUID playerId;
        final String name;
        final List<FillJob> jobs;
        /** Чанки, которые эта постройка держит загруженными; снимаются в конце и при отмене. */
        final Set<Long> forcedChunks = new HashSet<>();
        final long totalVolume;
        final long startNanos = System.nanoTime();
        long finishedVolume;
        int index;
        int jobCursor;

        Session(final ServerWorld world, final UUID playerId, final String name, final List<FillJob> jobs) {
            this.world = world;
            this.playerId = playerId;
            this.name = name;
            this.jobs = jobs;
            long volume = 0L;
            for (final FillJob job : jobs) {
                volume += job.volume;
            }
            this.totalVolume = volume;
        }

        void forceArea(final int[] bb, final int ox, final int oz) {
            final int cx1 = (ox + bb[0]) >> 4;
            final int cz1 = (oz + bb[1]) >> 4;
            final int cx2 = (ox + bb[2] - 1) >> 4;
            final int cz2 = (oz + bb[3] - 1) >> 4;
            for (int cx = cx1; cx <= cx2; cx++) {
                for (int cz = cz1; cz <= cz2; cz++) {
                    forceChunk(cx, cz);
                }
            }
        }

        /** Тикет принудительной загрузки: чанк грузится и генерируется вне тика, синхронной загрузки нет. */
        void forceChunk(final int cx, final int cz) {
            final ChunkPos pos = new ChunkPos(cx, cz);
            if (this.forcedChunks.add(pos.toLong())) {
                this.world.getChunkSource().updateChunkForced(pos, true);
            }
        }

        void releaseChunks() {
            for (final long packed : this.forcedChunks) {
                this.world.getChunkSource().updateChunkForced(new ChunkPos(packed), false);
            }
            this.forcedChunks.clear();
        }

        long processedVolume() {
            final long current = this.index < this.jobs.size() ? this.jobs.get(this.index).cursor : 0L;
            return this.finishedVolume + current;
        }

        int percent() {
            return this.totalVolume <= 0L ? 100 : (int) (processedVolume() * 100L / this.totalVolume);
        }

        /** Оценка по средней скорости с начала постройки: зависит от местности и генерации чанков. */
        String remainingText() {
            final long processed = processedVolume();
            final double elapsedSeconds = (System.nanoTime() - this.startNanos) / 1.0E9D;
            if (processed <= 0L || elapsedSeconds < 1.0D) {
                return "";
            }
            final long seconds = Math.round((this.totalVolume - processed) / (processed / elapsedSeconds));
            return String.format(Locale.ROOT, " §7осталось ~%d:%02d", seconds / 60L, seconds % 60L);
        }

        int currentVolume() {
            if (this.index >= this.jobs.size()) {
                return 0;
            }
            return this.jobs.get(this.index).volume;
        }

        void tick() {
            final long deadline = System.nanoTime() + NANOS_PER_TICK;
            int budget = BLOCKS_PER_TICK;
            while (budget > 0 && System.nanoTime() < deadline) {
                if (this.index >= this.jobs.size()) {
                    finish();
                    return;
                }
                final FillJob job = this.jobs.get(this.index);
                final int placed = job.place(this, budget, deadline);
                this.jobCursor = job.cursor;
                budget -= placed;
                if (job.waitingForChunk) {
                    return;
                }
                if (placed == 0 && !job.done()) {
                    return;
                }
                if (job.done()) {
                    this.finishedVolume += job.volume;
                    this.index++;
                    this.jobCursor = 0;
                    if (this.index > 0 && this.index % 25 == 0) {
                        final ServerPlayerEntity player = this.world.getServer().getPlayerList().getPlayer(this.playerId);
                        if (player != null) {
                            player.sendMessage(new StringTextComponent("§7данж: " + this.index + "/"
                                    + this.jobs.size()), Util.NIL_UUID);
                        }
                    }
                }
            }
        }

        void finish() {
            final ServerPlayerEntity player = this.world.getServer().getPlayerList().getPlayer(this.playerId);
            if (player != null) {
                player.sendMessage(new StringTextComponent("§aКаркас «" + this.name + "» готов. §7"
                        + this.jobs.size() + " шагов."), Util.NIL_UUID);
            }
            releaseChunks();
            session = null;
        }
    }
}
