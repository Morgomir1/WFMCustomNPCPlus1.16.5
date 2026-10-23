// Шкала урона данжа. Scripted Block у входа, ПКМ в креативе.
// Пишет world storeddata "hs_dmg". NPC с hs_core читают его в hsScale:
// исходящий урон и HP боссов. Цикл: 0.75 → 1 → 1.25 → 1.5 → 2 → 0.75.
// Без блока достаточно поменять HS.DMG в ядре.
var STEPS = [0.75, 1, 1.25, 1.5, 2];

function interact(e) {
    if (e.player.getGamemode() != 1) {
        e.player.message("§7Шкалу крутит только креатив.");
        return;
    }
    var d = e.block.getWorld().getStoreddata();
    var cur = d.has("hs_dmg") ? Number(String(d.get("hs_dmg"))) : 1;
    var i, next = STEPS[0];
    for (i = 0; i < STEPS.length; i++) {
        if (Math.abs(STEPS[i] - cur) < 0.001) {
            next = STEPS[(i + 1) % STEPS.length];
            break;
        }
    }
    d.put("hs_dmg", String(next));
    e.player.message("§eУрон и HP боссов ×" + next);
}
