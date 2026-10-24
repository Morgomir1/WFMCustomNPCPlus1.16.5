// Барабанщик C2. Второй файл после hs_core.js.
// Раз в 8 с бафает стадо. На 50% зовёт двух «Гор (призыв)».
// init снимает призыв: сброс и возрождение не оставляют горов.
var DRUM_EVERY = 160;
var DRUM_R = 20;
var ADD = "Гор (призыв)";
SUMMONS = [ADD];

function init(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    hsDespawnSummons(npc);
    hsPut(npc, "drum_at", 0);
    hsPut(npc, "flee_at", 0);
    hsPut(npc, "called", 0);
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) { AbilityAPI.cancel(npc); return; }
    var t = npc.getAttackTarget();
    if (t == null) return;
    var w = npc.getWorld();
    var now = hsNow(npc);
    if (now >= hsNum(npc, "drum_at")) {
        hsPut(npc, "drum_at", now + DRUM_EVERY);
        var list = w.getNearbyEntities(npc.getPos(), DRUM_R, 2);
        for (var i = 0; i < list.length; i++) {
            var a = list[i];
            if (!a.isAlive() || a.getFaction().getId() != npc.getFaction().getId()) continue;
            a.addPotionEffect(PotionEffectType_SPEED, 8, 0, false);
            a.addPotionEffect(PotionEffectType_STRENGTH, 8, 0, false);
        }
        w.playSoundAt(npc.getPos(), "minecraft:block.note_block.basedrum", 4.0, 0.5);
    }
    if (hsGet(npc, "called", "0") == "0" && npc.getHealth() <= npc.getMaxHealth() * 0.5) {
        hsPut(npc, "called", 1);
        hsNoRespawn(w.spawnClone(npc.getX() + 2, npc.getY(), npc.getZ(), HS.TAB, ADD));
        hsNoRespawn(w.spawnClone(npc.getX() - 2, npc.getY(), npc.getZ(), HS.TAB, ADD));
        npc.say("§6БУМ-БУМ! Ко мне, стадо!");
    }
    if (now >= hsNum(npc, "flee_at") && hsFlat(t, npc.getX(), npc.getZ()) < 4 && !AbilityAPI.isBusy(npc)) {
        hsPut(npc, "flee_at", now + 200);
        hsStart(npc, "retreat_dash", t, AbilityAPI.params("distance", 8.0));
    }
}
