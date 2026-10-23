// Отто Рейнхардт и Гурхак. Второй файл после hs_core.js.
// interact ставит респаун на койку лагеря. Координаты — локальные от hs_origin.
// Лагерь Порядка: -72,267 размер 117×40. Лагерь Разрушения: 233,267 размер 117×40.
// Койка на 20 блоков внутрь от северо-западного угла, на блок выше пола.
var BED_ORDER = [-52, 1, 287];
var BED_DESTR = [253, 1, 287];

function interact(e) {
    var npc = e.npc;
    var player = e.player;
    var bed = bedFor(String(npc.getName()));
    if (bed == null) return;
    var origin = hsWorldOrigin(npc.getWorld());
    if (origin == null) {
        player.message("§7Застава ещё не построена.");
        return;
    }
    player.setSpawnpoint(origin[0] + bed[0], origin[1] + bed[1], origin[2] + bed[2]);
    player.message("§aТы запомнил эту койку.");
}

function bedFor(name) {
    if (name.indexOf("Отто") >= 0) return BED_ORDER;
    if (name.indexOf("Гурхак") >= 0) return BED_DESTR;
    return null;
}
