package noppes.npcs.dungeon;

import noppes.npcs.dungeon.DungeonDefinition.Kind;

/**
 * Layout copied from herdstone_dungeon.html (1.8 SVG = 1 block).
 * L1 origin = SVG (150, 18); L2–L4 origin = SVG (0, 0).
 */
public final class HerdstoneDungeon {
    private HerdstoneDungeon() {
    }

    public static DungeonDefinition create() {
        final DungeonDefinition d = new DungeonDefinition("herdstone", "Камень Расколотого Рога")
                .alias("herdstone_dungeon", "hs", "herdherdstone");
        d.level(1, "Гнилая просека", 0);
        d.level(2, "Осквернённая крипта", -24);
        d.level(3, "Корневые глубины", -48);
        d.level(4, "Провал Камня Стада", -72);
        d.theme(1, "minecraft:grass_block", "minecraft:dark_oak_log", "minecraft:dark_oak_leaves");
        d.theme(2, "minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks");
        d.theme(3, "minecraft:mycelium", "minecraft:dark_oak_wood", "minecraft:dirt");
        d.theme(4, "minecraft:coarse_dirt", "minecraft:cobblestone", "minecraft:dirt");

        level1(d);
        level2(d);
        level3(d);
        level4(d);
        return d;
    }

    private static void level1(final DungeonDefinition d) {
        d.add(1, "border", Kind.BORDER, 0, 0, 350, 247, "");
        d.add(1, "camp_order", Kind.CAMP_ORDER, -72, 267, 117, 40, "npc").air();
        d.add(1, "camp_destr", Kind.CAMP_DESTR, 233, 267, 117, 40, "npc").air();
        d.add(1, "crossroads", Kind.MARK, 139, 287, 1, 1, "gate");
        d.addPath(1, "path_camps", Kind.PATH, 5, 44, 287, 139, 287, 233, 287).air();
        d.addPath(1, "path_gate", Kind.PATH, 6, 139, 287, 103, 251).air();
        d.add(1, "A1", Kind.SAFE, 73, 197, 58, 44, "").air();
        d.addPoly(1, "A2", Kind.SAFE, "",
                81, 197, 57, 157, 62, 117, 100, 104, 153, 117, 153, 140, 108, 133, 91, 157, 106, 197)
                .air()
                .floor("minecraft:podzol");
        d.addPath(1, "path_A2", Kind.PATH, 6,
                102, 197, 100, 194, 98, 191, 96, 188, 94, 185, 92, 182, 90, 179, 88, 175, 87, 172,
                85, 169, 84, 166, 83, 163, 82, 160, 81, 157, 80, 154, 80, 151, 80, 148, 79, 145,
                79, 142, 80, 140, 80, 137, 80, 134, 81, 132, 82, 129, 83, 127, 85, 124, 86, 123,
                88, 121, 90, 120, 93, 119, 95, 118, 98, 118, 101, 118, 104, 118, 107, 118, 111, 118,
                114, 119, 117, 119, 121, 120, 125, 121, 128, 122, 132, 123, 136, 124, 139, 125,
                143, 125, 146, 126, 150, 127, 153, 128, 157, 129).air();
        d.addEllipse(1, "A3", Kind.ROOM, 7, 21, 98, 62, "item").air();
        d.addPath(1, "path_A3", Kind.PATH, 5, 79, 109, 62, 83).air();
        d.add(1, "A5", Kind.ROOM, 157, 86, 116, 83, "").air().walls(5);
        d.add(1, "A5_chapel", Kind.ROOM, 167, 96, 34, 23, "").air().walls(5);
        d.add(1, "A6", Kind.ROOM, 279, 97, 32, 32, "puzzle").walls(11);
        d.addPath(1, "path_A5_A6", Kind.PATH, 4, 272, 112, 279, 112).air();
        d.add(1, "A4", Kind.ARENA, 228, 12, 111, 63, "boss").air().walls(5);
        d.addPath(1, "path_A5_A4", Kind.PATH, 5, 250, 86, 257, 78).air();
        d.add(1, "entrance", Kind.ROOM, 90, 242, 24, 9, "gate").air().walls(5);
        d.add(1, "exit_gate", Kind.ROOM, 168, 242, 27, 9, "gate").air().walls(5);
        d.addCircle(1, "gallows", Kind.SAFE, 181, 223, 10, "gate").air();
        d.addPath(1, "path_exit", Kind.PATH, 4, 181, 251, 150, 284).air()
                .floor("minecraft:coarse_dirt");
        d.add(1, "ungor_1", Kind.TOWER, 66, 152, 3, 3, "npc");
        d.add(1, "ungor_2", Kind.TOWER, 84, 113, 3, 3, "npc");
        d.add(1, "ungor_3", Kind.TOWER, 130, 119, 3, 3, "npc");
        d.add(1, "ungor_4", Kind.TOWER, 96, 176, 3, 3, "npc");
        d.addPath(1, "S1", Kind.PATH, 4,
                161, 164, 160, 167, 158, 169, 157, 172, 156, 174, 154, 176, 153, 178, 151, 180,
                150, 182, 149, 184, 147, 186, 146, 187, 145, 189, 144, 191, 142, 192, 141, 194,
                140, 196, 139, 197, 138, 199, 136, 200, 135, 202, 134, 203, 133, 205, 132, 206,
                131, 208).air().floor("minecraft:cyan_terracotta");
        d.add(1, "S1_mark", Kind.MARK, 150, 184, 1, 1, "sc");
        d.shaft(1, 2, "A4_down", 320, 29);
    }

    private static void level2(final DungeonDefinition d) {
        d.add(2, "B1", Kind.ROOM, 333, 22, 83, 44, "");
        d.addPoly(2, "B2", Kind.WATER, "",
                168, 78, 329, 83, 340, 139, 318, 178, 190, 178, 173, 139);
        d.add(2, "B3", Kind.ROOM, 28, 78, 112, 83, "puzzle");
        d.add(2, "B3_reliq", Kind.ROOM, 70, 106, 28, 26, "item");
        d.add(2, "B4", Kind.ROOM, 357, 157, 71, 51, "npc");
        d.addEllipse(2, "B5", Kind.ARENA, 156, 204, 167, 75, "boss");
        d.addPath(2, "path_B1_B2", Kind.CORRIDOR, 8, 361, 67, 326, 93);
        d.addPath(2, "path_B2_B3", Kind.CORRIDOR, 7, 173, 120, 140, 120);
        d.addPath(2, "path_B2_B4", Kind.CORRIDOR, 7, 333, 153, 357, 169);
        d.addPath(2, "path_B4_B5", Kind.CORRIDOR, 8, 362, 207, 314, 228);
        d.add(2, "V1", Kind.MARK, 196, 106, 1, 1, "valve");
        d.add(2, "V2", Kind.MARK, 311, 111, 1, 1, "valve");
        d.add(2, "V3", Kind.MARK, 251, 167, 1, 1, "valve");
        d.addPath(2, "S2", Kind.CORRIDOR, 4,
                396, 157, 397, 153, 398, 149, 399, 146, 399, 142, 400, 138, 400, 134, 400, 131,
                401, 127, 400, 123, 400, 119, 400, 115, 399, 111, 399, 108, 398, 104, 397, 100,
                396, 96, 395, 93, 394, 89, 392, 85, 391, 82, 389, 78, 387, 75, 385, 71, 383, 68)
                .floor("minecraft:cyan_terracotta");
        d.add(2, "S2_mark", Kind.MARK, 403, 117, 1, 1, "sc");
        d.shaft(2, 3, "B5_down", 167, 264);
    }

    private static void level3(final DungeonDefinition d) {
        d.add(3, "C1", Kind.ROOM, 28, 28, 44, 89, "");
        d.add(3, "C2", Kind.ROOM, 100, 33, 122, 78, "puzzle");
        d.addPoly(3, "C3", Kind.WARP, "item",
                239, 33, 400, 39, 411, 100, 383, 149, 289, 150, 239, 111);
        d.add(3, "C3a", Kind.ROOM, 247, 167, 62, 31, "");
        d.add(3, "C4", Kind.SECRET, 333, 167, 89, 61, "boss");
        d.addCircle(3, "C5", Kind.ARENA, 146, 213, 61, "boss");
        d.addCircle(3, "C5_pit", Kind.ROOM, 146, 213, 21, "")
                .floor("minecraft:soul_soil").noWalls();
        d.addPath(3, "path_C1_C2", Kind.CORRIDOR, 6, 72, 72, 100, 72);
        d.addPath(3, "path_C2_C3", Kind.CORRIDOR, 6, 222, 72, 239, 72);
        d.addPath(3, "path_C3_C3a", Kind.CORRIDOR, 6, 279, 150, 277, 167);
        d.addPath(3, "path_C3a_C5", Kind.CORRIDOR, 7, 247, 191, 204, 200);
        d.addPath(3, "path_C3_C4", Kind.CORRIDOR, 3, 378, 149, 378, 167);
        d.addPath(3, "S3", Kind.CORRIDOR, 4,
                249, 177, 241, 172, 232, 168, 224, 164, 215, 160, 207, 157, 198, 153, 190, 150,
                181, 147, 173, 144, 165, 142, 157, 139, 148, 137, 141, 135, 133, 132, 125, 130,
                118, 128, 111, 126, 105, 124, 98, 121, 92, 119, 87, 117, 81, 114, 77, 112, 72, 109)
                .floor("minecraft:cyan_terracotta");
        d.add(3, "S3_mark", Kind.MARK, 167, 136, 1, 1, "sc");
        d.shaft(3, 4, "C5_down", 146, 278);
    }

    private static void level4(final DungeonDefinition d) {
        d.add(4, "D0", Kind.MARK, 53, 157, 1, 1, "gate");
        d.add(4, "D1", Kind.ROOM, 33, 33, 133, 89, "");
        d.add(4, "D1_table", Kind.ROOM, 61, 61, 78, 28, "")
                .floor("minecraft:spruce_planks").noWalls();
        d.addCircle(4, "D2", Kind.ROOM, 233, 72, 39, "puzzle");
        d.addCircle(4, "D3", Kind.ARENA, 317, 206, 86, "boss");
        d.addPath(4, "chan_n", Kind.PATH, 3, 317, 206, 317, 128)
                .floor("minecraft:red_nether_bricks");
        d.addPath(4, "chan_sw", Kind.PATH, 3, 317, 206, 250, 244)
                .floor("minecraft:red_nether_bricks");
        d.addPath(4, "chan_se", Kind.PATH, 3, 317, 206, 383, 244)
                .floor("minecraft:red_nether_bricks");
        d.add(4, "heart_n", Kind.MARK, 317, 131, 1, 1, "puzzle");
        d.add(4, "heart_sw", Kind.MARK, 253, 242, 1, 1, "puzzle");
        d.add(4, "heart_se", Kind.MARK, 380, 242, 1, 1, "puzzle");
        d.add(4, "D4", Kind.ROOM, 383, 261, 53, 31, "item");
        d.addPath(4, "path_D0_D1", Kind.CORRIDOR, 6, 56, 148, 64, 123);
        d.addPath(4, "path_D1_D2", Kind.CORRIDOR, 6, 167, 78, 194, 78);
        d.addPath(4, "path_D2_D3", Kind.CORRIDOR, 7, 260, 102, 281, 126);
        d.addPath(4, "S4", Kind.CORRIDOR, 4,
                203, 98, 199, 104, 194, 109, 188, 114, 183, 119, 177, 124, 171, 128, 165, 132,
                159, 136, 152, 140, 146, 143, 139, 146, 133, 149, 126, 151, 120, 153, 113, 155,
                107, 157, 100, 158, 94, 159, 88, 160, 82, 161, 76, 161, 71, 161, 65, 161, 60, 161)
                .floor("minecraft:cyan_terracotta");
        d.add(4, "S4_mark", Kind.MARK, 139, 148, 1, 1, "sc");
        d.add(4, "exit_up", Kind.MARK, 423, 249, 1, 1, "gate");
    }
}
