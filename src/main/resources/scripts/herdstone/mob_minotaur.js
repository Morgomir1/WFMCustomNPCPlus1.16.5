// Минотавр. Третий файл: hs_core.js → hs_mob.js → этот.
// Прыжок и рывок — пресет. Здесь «Кровавое безумие» и общий агр D1.
CASTS = [
    { ability: "jump_slam", cd: 180, min: 5, max: 16, shape: "target", size: 4, hitAt: 40, pct: 0.30,
      params: function () { return AbilityAPI.params("chargeTicks", 24, "landRadius", 4.0, "damage", 8.0); } },
    { ability: "dash", cd: 140, min: 3, max: 12, shape: "line", len: 12, size: 1.8, hitAt: 36, pct: 0.25,
      params: function () { return AbilityAPI.params("chargeTicks", 28, "distance", 12.0, "damage", 8.0); } }
];

function damaged(e) {
    var npc = e.npc;
    var src = e.source;
    if (src == null || !src.isAlive()) return;
    var list = npc.getWorld().getNearbyEntities(npc.getPos(), 24, 2);
    for (var i = 0; i < list.length; i++) {
        var m = list[i];
        if (!m.isAlive() || String(m.getName()) != "Минотавр") continue;
        m.setAttackTarget(src);
    }
}

function died(e) {
    var npc = e.npc;
    var list = npc.getWorld().getNearbyEntities(npc.getPos(), 24, 2);
    for (var i = 0; i < list.length; i++) {
        var m = list[i];
        if (m == npc || !m.isAlive() || String(m.getName()) != "Минотавр") continue;
        m.setHealth(Math.min(m.getMaxHealth(), m.getHealth() + m.getMaxHealth() * 0.2));
        m.addPotionEffect(PotionEffectType_STRENGTH, 30, 0, false);
        m.say("§4Минотавр впадает в кровавое безумие!");
    }
}
