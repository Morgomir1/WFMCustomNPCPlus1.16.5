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
import net.minecraft.util.text.StringTextComponent;
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
    private static final int BLOCKS_PER_TICK = 6000;
    private static final int PLACE_FLAGS = 2;
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
        session = new Session((ServerWorld) player.level, player.getUUID(), def.displayName, jobs);
        if (def.forceload) {
            final int[] bb = bounds(def, levels);
            final String cmd = "forceload add " + (ox + bb[0]) + " " + (oz + bb[1]) + " "
                    + (ox + bb[2]) + " " + (oz + bb[3]);
            player.getServer().getCommands().performCommand(player.createCommandSourceStack(), cmd);
        }
        return "§2Каркас «" + def.displayName + "»: §f" + jobs.size() + " шагов §7с "
                + ox + " " + oy + " " + oz;
    }

    public static String cancel() {
        if (session == null) {
            return "§7Генерация не запущена.";
        }
        session = null;
        return "§7Генерация остановлена.";
    }

    public static String status() {
        if (session == null) {
            return "§7Генерация не запущена.";
        }
        return "§e" + session.index + "/" + session.jobs.size() + " §7" + session.name
                + "  блок " + session.jobCursor + "/" + session.currentVolume();
    }

    static List<FillJob> compile(final DungeonDefinition def, final int ox, final int oy, final int oz,
                                 final Set<Integer> levels) {
        final List<FillJob> jobs = new ArrayList<>();
        final List<Piece> pieces = def.pieces;
        for (int i = 0, n = pieces.size(); i < n; i++) {
            final Piece rec = pieces.get(i);
            if (!levels.contains(rec.level)) {
                continue;
            }
            compilePiece(jobs, def, rec, ox, oy, oz);
        }
        return jobs;
    }

    private static void compilePiece(final List<FillJob> jobs, final DungeonDefinition def, final Piece rec,
                                     final int ox, final int oy, final int oz) {
        switch (rec.kind) {
            case BORDER:
                buildBorder(jobs, def, rec, ox, oy, oz);
                break;
            case PATH:
                buildPath(jobs, def, rec, ox, oy, oz);
                break;
            case CORRIDOR:
                buildCorridor(jobs, def, rec, ox, oy, oz);
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
                buildBoxRoom(jobs, def, rec, ox, oy, oz);
                break;
        }
    }

    private static void buildBoxRoom(final List<FillJob> jobs, final DungeonDefinition def, final Piece rec,
                                     final int ox, final int oy, final int oz) {
        final int x1 = ox + rec.x;
        final int z1 = oz + rec.z;
        final int x2 = x1 + rec.w - 1;
        final int z2 = z1 + rec.d - 1;
        final int y0 = floorY(def, rec.level, oy);
        final int h = def.levelById(rec.level).height;
        final Theme th = theme(def, rec.level);
        addFill(jobs, x1, y0, z1, x2, y0, z2, floorBlock(rec, th));
        addFill(jobs, x1, y0 + 1, z1, x2, y0 + h - 1, z1, th.wall);
        addFill(jobs, x1, y0 + 1, z2, x2, y0 + h - 1, z2, th.wall);
        addFill(jobs, x1, y0 + 1, z1, x1, y0 + h - 1, z2, th.wall);
        addFill(jobs, x2, y0 + 1, z1, x2, y0 + h - 1, z2, th.wall);
        if (def.placeCeiling) {
            addFill(jobs, x1, y0 + h, z1, x2, y0 + h, z2, th.ceil);
        }
        if (!rec.marker.isEmpty()) {
            addSet(jobs, (x1 + x2) / 2, y0 + 1, (z1 + z2) / 2, marker(rec.marker));
        }
    }

    private static void buildPath(final List<FillJob> jobs, final DungeonDefinition def, final Piece rec,
                                  final int ox, final int oy, final int oz) {
        final int y0 = floorY(def, rec.level, oy);
        addFill(jobs, ox + rec.x, y0, oz + rec.z, ox + rec.x + rec.w - 1, y0, oz + rec.z + rec.d - 1,
                "minecraft:grass_path");
    }

    private static void buildCorridor(final List<FillJob> jobs, final DungeonDefinition def, final Piece rec,
                                      final int ox, final int oy, final int oz) {
        final int x1 = ox + rec.x;
        final int z1 = oz + rec.z;
        final int x2 = x1 + rec.w - 1;
        final int z2 = z1 + rec.d - 1;
        final int y0 = floorY(def, rec.level, oy);
        final Theme th = theme(def, rec.level);
        final int h = 5;
        addFill(jobs, x1, y0, z1, x2, y0, z2, th.floor);
        addFill(jobs, x1, y0 + 1, z1, x2, y0 + h - 1, z1, th.wall);
        addFill(jobs, x1, y0 + 1, z2, x2, y0 + h - 1, z2, th.wall);
        addFill(jobs, x1, y0 + 1, z1, x1, y0 + h - 1, z2, th.wall);
        addFill(jobs, x2, y0 + 1, z1, x2, y0 + h - 1, z2, th.wall);
        if (def.placeCeiling) {
            addFill(jobs, x1, y0 + h, z1, x2, y0 + h, z2, th.ceil);
        }
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
            case "sc":
                return "minecraft:cyan_wool";
            case "item":
                return "minecraft:lime_wool";
            case "npc":
                return "minecraft:white_wool";
            case "valve":
                return "minecraft:orange_wool";
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

        int place(final ServerWorld world, final int budget) {
            int n = 0;
            while (this.cursor < this.volume && n < budget) {
                final int x = this.x1 + this.cursor % this.dx;
                final int rem = this.cursor / this.dx;
                final int y = this.y1 + rem % this.dy;
                final int z = this.z1 + rem / this.dy;
                world.setBlock(POS.set(x, y, z), this.state, PLACE_FLAGS);
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
        int index;
        int jobCursor;

        Session(final ServerWorld world, final UUID playerId, final String name, final List<FillJob> jobs) {
            this.world = world;
            this.playerId = playerId;
            this.name = name;
            this.jobs = jobs;
        }

        int currentVolume() {
            if (this.index >= this.jobs.size()) {
                return 0;
            }
            return this.jobs.get(this.index).volume;
        }

        void tick() {
            int budget = BLOCKS_PER_TICK;
            while (budget > 0) {
                if (this.index >= this.jobs.size()) {
                    finish();
                    return;
                }
                final FillJob job = this.jobs.get(this.index);
                budget -= job.place(this.world, budget);
                this.jobCursor = job.cursor;
                if (job.done()) {
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
            session = null;
        }
    }
}
