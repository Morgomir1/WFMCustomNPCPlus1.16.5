package noppes.npcs.dungeon;

import noppes.npcs.dungeon.DungeonDefinition.Kind;

/**
 * Сгенерировано tools/herdstone/herdstone_layout.py — правь раскладку там, а не здесь.
 * 1 единица = 1 блок; на картах herdstone_dungeon.html 1 блок = 1.8 единицы SVG.
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
        d.add(1, "A1", Kind.ROOM, 73, 197, 58, 44, "").air();
        d.addPoly(1, "A2", Kind.ROOM, "",
                81, 197, 57, 157, 62, 117, 100, 104, 153, 117, 153, 140, 108, 133, 91, 157,
                106, 197).air()
                .floor("minecraft:podzol");
        d.addPath(1, "path_A2", Kind.PATH, 8,
                102, 197, 100, 194, 98, 191, 96, 188, 94, 185, 92, 182, 90, 179, 88, 175,
                87, 172, 85, 169, 84, 166, 83, 163, 82, 160, 81, 157, 80, 154, 80, 151,
                80, 148, 79, 145, 79, 142, 80, 140, 80, 137, 80, 134, 81, 132, 82, 129,
                83, 127, 85, 124, 86, 123, 82, 116, 79, 109).air();
        d.addEllipse(1, "A3", Kind.ROOM, 7, 21, 98, 62, "").air();
        d.addPath(1, "path_A3", Kind.PATH, 8, 79, 109, 60, 76).air();
        d.addPath(1, "path_A3_A5", Kind.PATH, 8, 100, 55, 125, 70, 145, 88, 160, 100).air();
        d.add(1, "A5", Kind.ROOM, 157, 86, 116, 83, "").air().walls(5);
        d.add(1, "A5_chapel", Kind.ROOM, 167, 96, 34, 23, "").air().walls(5);
        d.add(1, "A4", Kind.ARENA, 258, 18, 44, 38, "boss").air().walls(5);
        d.addPath(1, "path_A5_A4", Kind.PATH, 6, 262, 90, 270, 70, 278, 55).air();
        d.add(1, "entrance", Kind.ROOM, 90, 242, 24, 9, "gate").air().walls(5);
        d.add(1, "exit_gate", Kind.ROOM, 168, 242, 27, 9, "gate").air().walls(5);
        d.addCircle(1, "gallows", Kind.SAFE, 181, 223, 10, "gate").air();
        d.addPath(1, "path_exit", Kind.PATH, 4, 181, 251, 150, 284).air().floor("minecraft:coarse_dirt");
        d.add(1, "ungor_1", Kind.TOWER, 66, 152, 3, 3, "npc");
        d.add(1, "ungor_2", Kind.TOWER, 84, 113, 3, 3, "npc");
        d.add(1, "ungor_3", Kind.TOWER, 130, 119, 3, 3, "npc");
        d.add(1, "ungor_4", Kind.TOWER, 96, 176, 3, 3, "npc");
        d.addPath(1, "S1", Kind.PATH, 4,
                161, 164, 160, 167, 158, 169, 157, 172, 156, 174, 154, 176, 153, 178, 151, 180,
                150, 182, 149, 184, 147, 186, 146, 187, 145, 189, 144, 191, 142, 192, 141, 194,
                140, 196, 139, 197, 138, 199, 136, 200, 135, 202, 134, 203, 133, 205, 132, 206,
                129, 208).air()
                .floor("minecraft:cyan_terracotta");
        d.add(1, "S1_mark", Kind.MARK, 150, 184, 1, 1, "sc");
        d.add(1, "stake_1", Kind.MARK, 264, 24, 1, 1, "stake");
        d.add(1, "stake_2", Kind.MARK, 296, 24, 1, 1, "stake");
        d.add(1, "stake_3", Kind.MARK, 264, 50, 1, 1, "stake");
        d.add(1, "stake_4", Kind.MARK, 296, 50, 1, 1, "stake");
        d.add(1, "pack_1", Kind.MARK, 95, 215, 1, 1, "pack");
        d.add(1, "pack_2", Kind.MARK, 85, 165, 1, 1, "pack");
        d.add(1, "pack_3", Kind.MARK, 95, 135, 1, 1, "pack");
        d.add(1, "pack_4", Kind.MARK, 125, 122, 1, 1, "pack");
        d.add(1, "pack_5", Kind.MARK, 65, 40, 1, 1, "pack");
        d.add(1, "pack_6", Kind.MARK, 30, 65, 1, 1, "pack");
        d.add(1, "pack_7", Kind.MARK, 125, 72, 1, 1, "pack");
        d.add(1, "pack_8", Kind.MARK, 230, 110, 1, 1, "pack");
        d.add(1, "pack_9", Kind.MARK, 268, 77, 1, 1, "pack");
        d.add(1, "elite_1", Kind.MARK, 40, 50, 1, 1, "elite");
        d.add(1, "elite_2", Kind.MARK, 190, 130, 1, 1, "elite");
        d.add(1, "elite_3", Kind.MARK, 245, 150, 1, 1, "elite");
        d.shaft(1, 2, "A4_down", 292, 26);
    }

    private static void level2(final DungeonDefinition d) {
        d.add(2, "B1", Kind.ROOM, 276, 10, 48, 34, "");
        d.addPath(2, "c_B1_B2", Kind.CORRIDOR, 8, 300, 43, 300, 84);
        d.addPoly(2, "B2", Kind.WATER, "",
                170, 80, 330, 80, 338, 136, 318, 170, 190, 170, 172, 136);
        d.addPath(2, "c_B2_B3", Kind.CORRIDOR, 8, 173, 120, 139, 120);
        d.add(2, "B3", Kind.ROOM, 24, 70, 116, 90, "");
        d.addPath(2, "c_B3_B4", Kind.CORRIDOR, 8, 80, 159, 80, 184, 250, 184);
        d.add(2, "B4", Kind.ROOM, 250, 176, 56, 44, "npc");
        d.addPath(2, "c_B4_B5", Kind.CORRIDOR, 8, 252, 212, 216, 222);
        d.addCircle(2, "B5", Kind.ARENA, 200, 222, 20, "boss");
        d.add(2, "totem_1", Kind.MARK, 186, 210, 1, 1, "totem");
        d.add(2, "totem_2", Kind.MARK, 214, 210, 1, 1, "totem");
        d.add(2, "totem_3", Kind.MARK, 186, 234, 1, 1, "totem");
        d.add(2, "totem_4", Kind.MARK, 214, 234, 1, 1, "totem");
        d.addPath(2, "S2", Kind.CORRIDOR, 4,
                305, 182, 342, 178, 344, 120, 342, 60, 322, 40)
                .floor("minecraft:cyan_terracotta");
        d.add(2, "S2_mark", Kind.MARK, 344, 120, 1, 1, "sc");
        d.add(2, "pack_1", Kind.MARK, 285, 20, 1, 1, "pack");
        d.add(2, "pack_2", Kind.MARK, 210, 100, 1, 1, "pack");
        d.add(2, "pack_3", Kind.MARK, 300, 110, 1, 1, "pack");
        d.add(2, "pack_4", Kind.MARK, 45, 140, 1, 1, "pack");
        d.add(2, "pack_5", Kind.MARK, 115, 90, 1, 1, "pack");
        d.add(2, "pack_6", Kind.MARK, 278, 205, 1, 1, "pack");
        d.add(2, "elite_1", Kind.MARK, 250, 150, 1, 1, "elite");
        d.add(2, "elite_2", Kind.MARK, 60, 100, 1, 1, "elite");
        d.add(2, "elite_3", Kind.MARK, 100, 135, 1, 1, "elite");
        d.add(2, "elite_4", Kind.MARK, 120, 184, 1, 1, "elite");
        d.add(2, "elite_5", Kind.MARK, 200, 184, 1, 1, "elite");
        d.add(2, "elite_6", Kind.MARK, 80, 118, 1, 1, "elite");
        d.shaft(2, 3, "B5_down", 190, 230);
    }

    private static void level3(final DungeonDefinition d) {
        d.add(3, "C1", Kind.ROOM, 170, 206, 50, 38, "");
        d.addPath(3, "c_C1_C2", Kind.CORRIDOR, 8, 171, 224, 129, 224);
        d.add(3, "C2", Kind.ROOM, 20, 150, 110, 90, "");
        d.addPath(3, "c_C2_C3", Kind.CORRIDOR, 8, 75, 151, 75, 128);
        d.addPoly(3, "C3", Kind.WARP, "",
                20, 20, 200, 20, 215, 80, 190, 130, 40, 130, 20, 90);
        d.addPath(3, "c_C3_C3a", Kind.CORRIDOR, 6, 208, 75, 226, 75);
        d.add(3, "C3a", Kind.ROOM, 225, 58, 40, 32, "");
        d.addPath(3, "c_C3a_C5", Kind.CORRIDOR, 8, 255, 89, 290, 140);
        d.addCircle(3, "C5", Kind.ARENA, 300, 152, 21, "boss");
        d.add(3, "C4", Kind.SECRET, 268, 8, 72, 40, "boss");
        d.addPath(3, "c_C3_C4", Kind.CORRIDOR, 3, 198, 30, 268, 30);
        d.addPath(3, "S3", Kind.CORRIDOR, 4, 245, 89, 248, 150, 240, 200, 218, 215)
                .floor("minecraft:cyan_terracotta");
        d.add(3, "S3_mark", Kind.MARK, 246, 150, 1, 1, "sc");
        d.add(3, "menhir_1", Kind.MARK, 284, 18, 1, 1, "menhir");
        d.add(3, "menhir_2", Kind.MARK, 324, 18, 1, 1, "menhir");
        d.add(3, "menhir_3", Kind.MARK, 284, 38, 1, 1, "menhir");
        d.add(3, "menhir_4", Kind.MARK, 324, 38, 1, 1, "menhir");
        d.add(3, "herb_1", Kind.MARK, 50, 40, 1, 1, "herb");
        d.add(3, "herb_2", Kind.MARK, 110, 30, 1, 1, "herb");
        d.add(3, "herb_3", Kind.MARK, 180, 60, 1, 1, "herb");
        d.add(3, "herb_4", Kind.MARK, 140, 115, 1, 1, "herb");
        d.add(3, "fog", Kind.MARK, 110, 70, 1, 1, "fog");
        d.add(3, "pack_1", Kind.MARK, 200, 215, 1, 1, "pack");
        d.add(3, "pack_2", Kind.MARK, 40, 170, 1, 1, "pack");
        d.add(3, "pack_3", Kind.MARK, 105, 220, 1, 1, "pack");
        d.add(3, "pack_4", Kind.MARK, 45, 225, 1, 1, "pack");
        d.add(3, "pack_5", Kind.MARK, 120, 100, 1, 1, "pack");
        d.add(3, "elite_1", Kind.MARK, 75, 195, 1, 1, "elite");
        d.add(3, "elite_2", Kind.MARK, 70, 60, 1, 1, "elite");
        d.add(3, "elite_3", Kind.MARK, 150, 50, 1, 1, "elite");
        d.add(3, "elite_4", Kind.MARK, 245, 73, 1, 1, "elite");
        d.shaft(3, 4, "C5_down", 300, 162);
    }

    private static void level4(final DungeonDefinition d) {
        d.add(4, "D0", Kind.ROOM, 286, 146, 30, 30, "");
        d.addPath(4, "c_D0_D1", Kind.CORRIDOR, 8, 287, 170, 268, 170);
        d.add(4, "D1", Kind.ROOM, 150, 136, 120, 82, "");
        d.add(4, "D1_table", Kind.ROOM, 180, 165, 60, 20, "").floor("minecraft:spruce_planks").noWalls();
        d.addPath(4, "c_D1_D2", Kind.CORRIDOR, 8, 151, 160, 104, 132);
        d.addCircle(4, "D2", Kind.ROOM, 80, 118, 30, "");
        d.addPath(4, "c_D2_D3", Kind.CORRIDOR, 8, 86, 92, 112, 62);
        d.addCircle(4, "D3", Kind.ARENA, 122, 44, 26, "boss");
        d.addPath(4, "chan_n", Kind.PATH, 3, 122, 44, 122, 21).floor("minecraft:red_nether_bricks");
        d.addPath(4, "chan_sw", Kind.PATH, 3, 122, 44, 102, 58).floor("minecraft:red_nether_bricks");
        d.addPath(4, "chan_se", Kind.PATH, 3, 122, 44, 142, 58).floor("minecraft:red_nether_bricks");
        d.add(4, "heart_1", Kind.MARK, 122, 22, 1, 1, "heart");
        d.add(4, "heart_2", Kind.MARK, 103, 57, 1, 1, "heart");
        d.add(4, "heart_3", Kind.MARK, 141, 57, 1, 1, "heart");
        d.add(4, "crystal_1", Kind.MARK, 112, 34, 1, 1, "crystal");
        d.add(4, "crystal_2", Kind.MARK, 132, 34, 1, 1, "crystal");
        d.add(4, "crystal_3", Kind.MARK, 112, 54, 1, 1, "crystal");
        d.add(4, "crystal_4", Kind.MARK, 132, 54, 1, 1, "crystal");
        d.add(4, "D4", Kind.ROOM, 158, 20, 40, 30, "item");
        d.addPath(4, "c_D3_D4", Kind.CORRIDOR, 6, 146, 38, 159, 35);
        d.add(4, "exit_up", Kind.MARK, 190, 35, 1, 1, "gate");
        d.addPath(4, "S4", Kind.CORRIDOR, 4, 95, 140, 120, 230, 300, 230, 300, 174)
                .floor("minecraft:cyan_terracotta");
        d.add(4, "S4_mark", Kind.MARK, 210, 230, 1, 1, "sc");
        d.add(4, "pack_1", Kind.MARK, 292, 150, 1, 1, "pack");
        d.add(4, "pack_2", Kind.MARK, 210, 200, 1, 1, "pack");
        d.add(4, "pack_3", Kind.MARK, 60, 100, 1, 1, "pack");
        d.add(4, "elite_1", Kind.MARK, 175, 150, 1, 1, "elite");
        d.add(4, "elite_2", Kind.MARK, 250, 150, 1, 1, "elite");
        d.add(4, "elite_3", Kind.MARK, 175, 205, 1, 1, "elite");
        d.add(4, "elite_4", Kind.MARK, 250, 205, 1, 1, "elite");
        d.add(4, "elite_5", Kind.MARK, 80, 118, 1, 1, "elite");
    }
}
