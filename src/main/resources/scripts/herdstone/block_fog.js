// Туман C3. Скрытый Scripted Block под полом топи, без hs_core.js.
// Раз в 10 тиков: игроки в 16 блоках получают тошноту на 3 с и иссушение на 2 с.
// Креатив и наблюдатель не травятся. Чистота от святой травы — ключ hs_clean_until.
var EntitiesType = Java.type("noppes.npcs.api.constants.EntitiesType");

function tick(e) {
    var w = e.block.getWorld();
    var now = Number(w.getTotalTime());
    var list = w.getNearbyEntities(e.block.getPos(), 16, EntitiesType.PLAYER);
    var i;
    for (i = 0; i < list.length; i++) {
        var p = list[i];
        if (p == null || !p.isAlive()) continue;
        var mode = p.getGamemode();
        if (mode == 1 || mode == 3) continue;
        var d = p.getStoreddata();
        if (d.has("hs_clean_until") && now < Number(String(d.get("hs_clean_until")))) continue;
        p.addPotionEffect(PotionEffectType_NAUSEA, 3, 0, true);
        p.addPotionEffect(PotionEffectType_WITHER, 2, 0, true);
    }
}
