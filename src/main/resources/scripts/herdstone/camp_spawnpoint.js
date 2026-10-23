// Отто Рейнхардт и Гурхак. Второй файл после hs_core.js.
// interact ставит респаун на койку лагеря. Это мировые координаты блока койки.
// Лагерь Порядка: -72,267 размер 117×40. Лагерь Разрушения: 233,267 размер 117×40.
// Койка на 20 блоков внутрь от северо-западного угла, на блок выше пола.
// Числа ниже — для каркаса, построенного от входа 0, 80, 0. Сдвинул данж — впиши блок койки.
var BED_ORDER = [-52, 81, 287];
var BED_DESTR = [253, 81, 287];

function interact(e) {
    var npc = e.npc;
    var player = e.player;
    var bed = bedFor(String(npc.getName()));
    if (bed == null) return;
    player.setSpawnpoint(bed[0], bed[1], bed[2]);
    player.message("§aТы запомнил эту койку.");
}

function bedFor(name) {
    if (name.indexOf("Отто") >= 0) return BED_ORDER;
    if (name.indexOf("Гурхак") >= 0) return BED_DESTR;
    return null;
}
