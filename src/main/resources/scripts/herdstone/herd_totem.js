// Тотем Стада. Один этот файл, без hs_core.js.
// Скорость 0, On Attack = Ничего. Сопротивление II своим в 8 блоках.
function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    var list = npc.getWorld().getNearbyEntities(npc.getPos(), 8, 2);
    for (var i = 0; i < list.length; i++) {
        var a = list[i];
        if (a == npc || !a.isAlive() || a.getFaction().getId() != npc.getFaction().getId()) continue;
        a.addPotionEffect(PotionEffectType_RESISTANCE, 2, 1, true);
    }
    npc.getWorld().spawnParticle("crimson_spore", npc.getX(), npc.getY() + 1, npc.getZ(), 4, 0.5, 4, 0, 20);
}
