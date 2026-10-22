package noppes.npcs.dungeon;

import noppes.npcs.dungeon.DungeonDefinition.Kind;

/**
 * Сгенерировано tools/herdstone/herdstone_layout.py — правь раскладку там, а не здесь.
 * 1 единица = 1 блок; на картах локаций 1 блок = 1.8 единицы SVG (координаты локальные).
 * В мире этажи не столб: L1 как на плане, L2 и L3 отзеркалены на восток, L3 и L4 — на юг.
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
        d.ramp(1, 2, "A4_down", 292, 26, 421, 26);
    }

    private static void level2(final DungeonDefinition d) {
        d.add(2, "B1", Kind.ROOM, 392, 10, 48, 34, "");
        d.addPath(2, "c_B1_B2", Kind.CORRIDOR, 8, 415, 43, 415, 84);
        d.addPoly(2, "B2", Kind.WATER, "",
                545, 80, 385, 80, 377, 136, 397, 170, 525, 170, 543, 136);
        d.addPath(2, "c_B2_B3", Kind.CORRIDOR, 8, 542, 120, 576, 120);
        d.add(2, "B3", Kind.ROOM, 576, 70, 116, 90, "");
        d.addPath(2, "c_B3_B4", Kind.CORRIDOR, 8, 635, 159, 635, 184, 465, 184);
        d.add(2, "B4", Kind.ROOM, 410, 176, 56, 44, "npc");
        d.addPath(2, "c_B4_B5", Kind.CORRIDOR, 8, 463, 212, 499, 222);
        d.addCircle(2, "B5", Kind.ARENA, 515, 222, 20, "boss");
        d.add(2, "totem_1", Kind.MARK, 529, 210, 1, 1, "totem");
        d.add(2, "totem_2", Kind.MARK, 501, 210, 1, 1, "totem");
        d.add(2, "totem_3", Kind.MARK, 529, 234, 1, 1, "totem");
        d.add(2, "totem_4", Kind.MARK, 501, 234, 1, 1, "totem");
        d.add(2, "pack_1", Kind.MARK, 430, 20, 1, 1, "pack");
        d.add(2, "pack_2", Kind.MARK, 505, 100, 1, 1, "pack");
        d.add(2, "pack_3", Kind.MARK, 415, 110, 1, 1, "pack");
        d.add(2, "pack_4", Kind.MARK, 670, 140, 1, 1, "pack");
        d.add(2, "pack_5", Kind.MARK, 600, 90, 1, 1, "pack");
        d.add(2, "pack_6", Kind.MARK, 437, 205, 1, 1, "pack");
        d.add(2, "elite_1", Kind.MARK, 465, 150, 1, 1, "elite");
        d.add(2, "elite_2", Kind.MARK, 655, 100, 1, 1, "elite");
        d.add(2, "elite_3", Kind.MARK, 615, 135, 1, 1, "elite");
        d.add(2, "elite_4", Kind.MARK, 595, 184, 1, 1, "elite");
        d.add(2, "elite_5", Kind.MARK, 515, 184, 1, 1, "elite");
        d.add(2, "elite_6", Kind.MARK, 635, 118, 1, 1, "elite");
        d.ramp(2, 3, "B5_down", 523, 230, 523, 397);
    }

    private static void level3(final DungeonDefinition d) {
        d.add(3, "C1", Kind.ROOM, 496, 386, 50, 38, "");
        d.addPath(3, "c_C1_C2", Kind.CORRIDOR, 8, 544, 405, 586, 405);
        d.add(3, "C2", Kind.ROOM, 586, 390, 110, 90, "");
        d.addPath(3, "c_C2_C3", Kind.CORRIDOR, 8, 640, 478, 640, 501);
        d.addPoly(3, "C3", Kind.WARP, "",
                695, 609, 515, 609, 500, 549, 525, 499, 675, 499, 695, 539);
        d.addPath(3, "c_C3_C3a", Kind.CORRIDOR, 6, 507, 554, 489, 554);
        d.add(3, "C3a", Kind.ROOM, 451, 540, 40, 32, "");
        d.addPath(3, "c_C3a_C5", Kind.CORRIDOR, 8, 460, 540, 425, 489);
        d.addCircle(3, "C5", Kind.ARENA, 415, 477, 21, "boss");
        d.add(3, "C4", Kind.SECRET, 376, 582, 72, 40, "boss");
        d.addPath(3, "c_C3_C4", Kind.CORRIDOR, 3, 515, 593, 515, 573, 425, 573, 425, 589);
        d.add(3, "menhir_1", Kind.MARK, 431, 611, 1, 1, "menhir");
        d.add(3, "menhir_2", Kind.MARK, 391, 611, 1, 1, "menhir");
        d.add(3, "menhir_3", Kind.MARK, 431, 591, 1, 1, "menhir");
        d.add(3, "menhir_4", Kind.MARK, 391, 591, 1, 1, "menhir");
        d.add(3, "herb_1", Kind.MARK, 665, 589, 1, 1, "herb");
        d.add(3, "herb_2", Kind.MARK, 605, 599, 1, 1, "herb");
        d.add(3, "herb_3", Kind.MARK, 535, 569, 1, 1, "herb");
        d.add(3, "herb_4", Kind.MARK, 575, 514, 1, 1, "herb");
        d.add(3, "fog", Kind.MARK, 605, 559, 1, 1, "fog");
        d.add(3, "pack_1", Kind.MARK, 515, 414, 1, 1, "pack");
        d.add(3, "pack_2", Kind.MARK, 675, 459, 1, 1, "pack");
        d.add(3, "pack_3", Kind.MARK, 610, 409, 1, 1, "pack");
        d.add(3, "pack_4", Kind.MARK, 670, 404, 1, 1, "pack");
        d.add(3, "pack_5", Kind.MARK, 595, 529, 1, 1, "pack");
        d.add(3, "elite_1", Kind.MARK, 640, 434, 1, 1, "elite");
        d.add(3, "elite_2", Kind.MARK, 645, 569, 1, 1, "elite");
        d.add(3, "elite_3", Kind.MARK, 565, 579, 1, 1, "elite");
        d.add(3, "elite_4", Kind.MARK, 470, 556, 1, 1, "elite");
        d.ramp(3, 4, "C5_down", 413, 465, 300, 465);
    }

    private static void level4(final DungeonDefinition d) {
        d.add(4, "D0", Kind.ROOM, 286, 454, 30, 30, "");
        d.addPath(4, "c_D0_D1", Kind.CORRIDOR, 8, 287, 459, 268, 459);
        d.add(4, "D1", Kind.ROOM, 150, 412, 120, 82, "");
        d.add(4, "D1_table", Kind.ROOM, 180, 445, 60, 20, "").floor("minecraft:spruce_planks").noWalls();
        d.addPath(4, "c_D1_D2", Kind.CORRIDOR, 8, 151, 469, 104, 497);
        d.addCircle(4, "D2", Kind.ROOM, 80, 511, 30, "");
        d.addPath(4, "c_D2_D3", Kind.CORRIDOR, 8, 100, 529, 150, 559, 200, 580, 218, 588);
        d.addCircle(4, "D3", Kind.ARENA, 230, 602, 26, "boss").air();
        d.addPath(4, "chan_n", Kind.PATH, 3, 230, 602, 230, 624).floor("minecraft:red_nether_bricks");
        d.addPath(4, "chan_sw", Kind.PATH, 3, 230, 602, 211, 589).floor("minecraft:red_nether_bricks");
        d.addPath(4, "chan_se", Kind.PATH, 3, 230, 602, 249, 589).floor("minecraft:red_nether_bricks");
        d.add(4, "heart_1", Kind.MARK, 230, 624, 1, 1, "heart");
        d.add(4, "heart_2", Kind.MARK, 211, 589, 1, 1, "heart");
        d.add(4, "heart_3", Kind.MARK, 249, 589, 1, 1, "heart");
        d.add(4, "crystal_1", Kind.MARK, 220, 612, 1, 1, "crystal");
        d.add(4, "crystal_2", Kind.MARK, 240, 612, 1, 1, "crystal");
        d.add(4, "crystal_3", Kind.MARK, 220, 592, 1, 1, "crystal");
        d.add(4, "crystal_4", Kind.MARK, 240, 592, 1, 1, "crystal");
        d.add(4, "D4", Kind.ROOM, 164, 599, 40, 28, "item");
        d.addPath(4, "c_D3_D4", Kind.CORRIDOR, 6, 208, 606, 198, 612);
        d.add(4, "exit_up", Kind.MARK, 198, 612, 1, 1, "gate");
        d.add(4, "pack_1", Kind.MARK, 292, 479, 1, 1, "pack");
        d.add(4, "pack_2", Kind.MARK, 210, 429, 1, 1, "pack");
        d.add(4, "pack_3", Kind.MARK, 60, 529, 1, 1, "pack");
        d.add(4, "elite_1", Kind.MARK, 175, 479, 1, 1, "elite");
        d.add(4, "elite_2", Kind.MARK, 250, 479, 1, 1, "elite");
        d.add(4, "elite_3", Kind.MARK, 175, 424, 1, 1, "elite");
        d.add(4, "elite_4", Kind.MARK, 250, 424, 1, 1, "elite");
        d.add(4, "elite_5", Kind.MARK, 80, 511, 1, 1, "elite");
    }
}
