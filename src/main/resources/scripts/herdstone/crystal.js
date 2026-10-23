// Варп-кристалл (HP 80, Respawn = Нет, скорость 0, On Attack = Ничего).
// В контейнере вторым после hs_core.js.
// Раз в 5 с снимает 10% HP всем в 4 блоках. Молнию не используем — она поджигает рощу.
function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    var now = hsNow(npc);
    if (now < hsNum(npc, "pulse_at")) return;
    hsPut(npc, "pulse_at", now + 100);
    var w = npc.getWorld();
    w.spawnParticle("happy_villager", npc.getX(), npc.getY() + 1, npc.getZ(), 2, 1, 2, 0.1, 40);
    w.playSoundAt(npc.getPos(), "minecraft:block.beacon.power_select", 2.0, 0.6);
    var ps = hsPlayersAt(npc, npc.getX(), npc.getY(), npc.getZ(), 4);
    for (var i = 0; i < ps.length; i++) hsStrike(ps[i], 0.10);
}
