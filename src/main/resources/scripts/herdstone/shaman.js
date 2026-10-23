// Бра-шаман. Второй файл после hs_core.js.
// Лечит стадо и поёт «Проклятую песнь»: 20 урона за 4 с её срывают.
var HEAL_EVERY = 120;
var SONG_EVERY = 400;
var SONG_TICKS = 80;
var SONG_BREAK = 20;
var SONG_R = 12;
var T_SONG = 1;

function init(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    npc.getTimers().clear();
    hsPut(npc, "heal_at", 0);
    hsPut(npc, "song_at", hsNow(npc) + 200);
    hsPut(npc, "song", 0);
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive() || npc.getAttackTarget() == null) return;
    var now = hsNow(npc);
    if (now >= hsNum(npc, "heal_at")) {
        hsPut(npc, "heal_at", now + HEAL_EVERY);
        var list = npc.getWorld().getNearbyEntities(npc.getPos(), 8, 2);
        for (var i = 0; i < list.length; i++) {
            var a = list[i];
            if (!a.isAlive() || a.getFaction().getId() != npc.getFaction().getId()) continue;
            a.setHealth(Math.min(a.getMaxHealth(), a.getHealth() + a.getMaxHealth() * 0.15));
            a.addPotionEffect(PotionEffectType_RESISTANCE, 5, 0, false);
        }
        npc.getWorld().spawnParticle("happy_villager", npc.getX(), npc.getY() + 1, npc.getZ(), 4, 1, 4, 0.1, 30);
    }
    if (now >= hsNum(npc, "song_at") && !npc.getTimers().has(T_SONG)) {
        hsPut(npc, "song_at", now + SONG_EVERY);
        hsPut(npc, "song", 1);
        hsPut(npc, "song_dmg", 0);
        hsPut(npc, "song_tg", TelegraphAPI.circle(npc, npc.getX(), npc.getY(), npc.getZ(), SONG_R, SONG_TICKS, HS.PURPLE));
        npc.say("§5Шаман заводит Проклятую песнь! Бейте его!");
        npc.getTimers().forceStart(T_SONG, SONG_TICKS, false);
    }
}

function damaged(e) {
    var npc = e.npc;
    if (hsGet(npc, "song", "0") != "1") return;
    var dmg = hsNum(npc, "song_dmg") + e.damage;
    hsPut(npc, "song_dmg", dmg);
    if (dmg < SONG_BREAK) return;
    hsPut(npc, "song", 0);
    npc.getTimers().stop(T_SONG);
    TelegraphAPI.remove(hsGet(npc, "song_tg", ""));
    npc.say("§7Песнь сорвана.");
}

function timer(e) {
    var npc = e.npc;
    if (e.id != T_SONG || !npc.isAlive() || hsGet(npc, "song", "0") != "1") return;
    hsPut(npc, "song", 0);
    var ps = hsPlayersAt(npc, npc.getX(), npc.getY(), npc.getZ(), SONG_R);
    for (var i = 0; i < ps.length; i++) {
        hsStrike(ps[i], 0.35);
        ps[i].addPotionEffect(PotionEffectType_NAUSEA, 6, 0, false);
    }
}
