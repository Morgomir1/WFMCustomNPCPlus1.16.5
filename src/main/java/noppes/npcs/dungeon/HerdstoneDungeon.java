package noppes.npcs.dungeon;

import noppes.npcs.dungeon.DungeonDefinition.Kind;

public final class HerdstoneDungeon {
    private HerdstoneDungeon() {
    }

    public static DungeonDefinition create() {
        final DungeonDefinition d = new DungeonDefinition("herdstone", "Камень Расколотого Рога")
                .alias("herdstone_dungeon", "hs", "herdherdstone");
        d.level(1, "Гнилая просека", 0);
        d.level(2, "Осквернённая крипта", -20);
        d.level(3, "Корневые глубины", -40);
        d.level(4, "Провал Камня Стада", -60);
        d.theme(1, "minecraft:grass_block", "minecraft:dark_oak_log", "minecraft:dark_oak_leaves");
        d.theme(2, "minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks");
        d.theme(3, "minecraft:mycelium", "minecraft:dark_oak_wood", "minecraft:dirt");
        d.theme(4, "minecraft:coarse_dirt", "minecraft:cobblestone", "minecraft:dirt");

        d.add(1, "border", Kind.BORDER, 0, 0, 350, 247, "");
        d.add(1, "camp_order", Kind.CAMP_ORDER, -72, 267, 117, 40, "npc");
        d.add(1, "camp_destr", Kind.CAMP_DESTR, 233, 267, 117, 40, "npc");
        d.add(1, "crossroads", Kind.MARK, 139, 287, 1, 1, "gate");
        d.add(1, "path_order", Kind.PATH, 48, 267, 90, 4, "");
        d.add(1, "path_destr", Kind.PATH, 139, 267, 94, 4, "");
        d.add(1, "path_gate", Kind.PATH, 135, 247, 8, 40, "");
        d.add(1, "A1", Kind.SAFE, 73, 197, 58, 44, "");
        d.add(1, "A3", Kind.ROOM, 7, 21, 98, 62, "item");
        d.add(1, "A5", Kind.ROOM, 157, 86, 116, 83, "");
        d.add(1, "A5_chapel", Kind.ROOM, 167, 96, 34, 23, "");
        d.add(1, "A6", Kind.ROOM, 279, 97, 32, 32, "puzzle");
        d.add(1, "A4", Kind.ARENA, 228, 12, 111, 63, "boss");
        d.add(1, "entrance", Kind.ROOM, 90, 242, 24, 9, "gate");
        d.add(1, "exit_gate", Kind.ROOM, 168, 242, 27, 9, "gate");
        d.add(1, "gallows", Kind.SAFE, 171, 213, 20, 20, "gate");
        d.add(1, "path_A1_A3", Kind.PATH, 90, 80, 8, 117, "");
        d.add(1, "path_A3_A5", Kind.PATH, 105, 80, 52, 8, "");
        d.add(1, "path_A5_A6", Kind.PATH, 273, 108, 8, 10, "");
        d.add(1, "path_A5_A4", Kind.PATH, 270, 75, 8, 12, "");
        d.add(1, "ungor_1", Kind.TOWER, 66, 152, 3, 3, "npc");
        d.add(1, "ungor_2", Kind.TOWER, 84, 113, 3, 3, "npc");
        d.add(1, "ungor_3", Kind.TOWER, 130, 119, 3, 3, "npc");
        d.add(1, "ungor_4", Kind.TOWER, 96, 176, 3, 3, "npc");
        d.add(1, "S1", Kind.MARK, 150, 184, 1, 1, "sc");
        d.shaft(1, 2, "A4_down", 320, 29);

        d.add(2, "B1", Kind.ROOM, 333, 22, 83, 44, "");
        d.add(2, "B2", Kind.WATER, 168, 78, 172, 100, "");
        d.add(2, "B3", Kind.ROOM, 28, 78, 112, 83, "puzzle");
        d.add(2, "B4", Kind.ROOM, 357, 157, 71, 51, "npc");
        d.add(2, "B5", Kind.ARENA, 156, 204, 167, 73, "boss");
        d.add(2, "path_B1_B2", Kind.CORRIDOR, 300, 55, 40, 24, "");
        d.add(2, "path_B2_B3", Kind.CORRIDOR, 140, 100, 30, 12, "");
        d.add(2, "path_B2_B4", Kind.CORRIDOR, 330, 150, 28, 12, "");
        d.add(2, "path_B4_B5", Kind.CORRIDOR, 300, 210, 50, 16, "");
        d.add(2, "V1", Kind.MARK, 196, 106, 1, 1, "valve");
        d.add(2, "V2", Kind.MARK, 311, 111, 1, 1, "valve");
        d.add(2, "V3", Kind.MARK, 251, 167, 1, 1, "valve");
        d.add(2, "S2", Kind.MARK, 403, 117, 1, 1, "sc");
        d.shaft(2, 3, "B5_down", 167, 264);

        d.add(3, "C1", Kind.ROOM, 28, 28, 44, 89, "");
        d.add(3, "C2", Kind.ROOM, 100, 33, 122, 78, "puzzle");
        d.add(3, "C3", Kind.WARP, 239, 33, 172, 117, "item");
        d.add(3, "C3a", Kind.ROOM, 247, 167, 62, 31, "");
        d.add(3, "C4", Kind.SECRET, 333, 167, 89, 61, "boss");
        d.add(3, "C5", Kind.ARENA, 84, 152, 122, 122, "boss");
        d.add(3, "gut", Kind.SECRET, 17, 250, 53, 32, "");
        d.add(3, "path_C1_C2", Kind.CORRIDOR, 72, 60, 28, 10, "");
        d.add(3, "path_C2_C3", Kind.CORRIDOR, 222, 60, 18, 10, "");
        d.add(3, "path_C3_C3a", Kind.CORRIDOR, 250, 150, 10, 18, "");
        d.add(3, "path_C3a_C5", Kind.CORRIDOR, 180, 190, 70, 10, "");
        d.add(3, "S3", Kind.MARK, 167, 136, 1, 1, "sc");
        d.shaft(3, 4, "C5_down", 146, 248);

        d.add(4, "D0", Kind.MARK, 53, 157, 1, 1, "gate");
        d.add(4, "D1", Kind.ROOM, 33, 33, 133, 89, "");
        d.add(4, "D2", Kind.ROOM, 194, 33, 78, 78, "puzzle");
        d.add(4, "D3", Kind.ARENA, 231, 119, 172, 172, "boss");
        d.add(4, "D4", Kind.ROOM, 383, 261, 53, 31, "item");
        d.add(4, "path_D0_D1", Kind.CORRIDOR, 53, 122, 10, 36, "");
        d.add(4, "path_D1_D2", Kind.CORRIDOR, 166, 60, 10, 10, "");
        d.add(4, "path_D2_D3", Kind.CORRIDOR, 250, 100, 20, 20, "");
        d.add(4, "S4", Kind.MARK, 139, 148, 1, 1, "sc");
        d.add(4, "exit_up", Kind.MARK, 423, 249, 1, 1, "gate");
        return d;
    }
}
