// Мургрот Туманный Глас (Б2). В контейнере вторым после hs_core.js.
// Скорость 0, On Attack = Мстить, рендж в GUI. Круг B5, r 20.
// Тотемы в SUMMONS: init их снимает, иначе после сброса он сразу неуязвим.
// Копии после init носят имя оригинала, поэтому ищутся по метке, не по имени.
HS.BASE_HP = 260;
HS.ARENA_R = 20;
HS.ENRAGE_SEC = 420;
ORIGIN = [515, 56, 222];   // этаж 2: пол на 24 ниже входа 0,80,0
GATE = hsBox(8, 0, 8, 3, 2, 3);   // завал B5_down (523, 230) от центра 515,222
SUMMONS = ["Тотем Мургрота", "Мургрот-копия"];

var TOTEM = "Тотем Мургрота";
var COPY = "Мургрот-копия";
var BOSS_TAG = "hs_murgrot";
var COPY_TAG = "hs_murgrot_copy";
var TOTEMS = [ [-14, 0, -12], [14, 0, -12], [-14, 0, 12], [14, 0, 12] ];
var SONG_EVERY = 500, SONG_TICKS = 100, SONG_BREAK = 60;
var ROT_EVERY = 400, ROT_TICKS = 80, ROT_R = 6;
var T_SONG = 1, T_ROT = 2;

function init(e) {
    var npc = e.npc;
    npc.addTag(BOSS_TAG);
    if (!hsInit(npc)) return;
    despawnTagged(npc, COPY_TAG);
    hsPut(npc, "wave", 0);
    hsPut(npc, "immune", 0);
    hsPut(npc, "song", 0);
    hsPut(npc, "copies", 0);
    hsPut(npc, "song_at", 0);
    hsPut(npc, "rot_at", 0);
}

function raiseTotems(npc) {
    var w = npc.getWorld();
    for (var i = 0; i < TOTEMS.length; i++) {
        var p = hsXYZ(npc, TOTEMS[i]);
        var near = w.getNearbyEntities(NpcAPI.getIPos(p[0], p[1], p[2]), 2, 2);
        var alive = false;
        for (var j = 0; j < near.length; j++) if (String(near[j].getName()) == TOTEM && near[j].isAlive()) alive = true;
        if (!alive) hsSpawnAt(npc, TOTEMS[i], TOTEM);
    }
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    var ps = hsFight(npc);
    if (ps == null) return;
    var now = hsNow(npc);
    var hp = npc.getHealth() / npc.getMaxHealth();
    var want = hp > 0.66 ? 1 : (hp > 0.33 ? 2 : 3);
    if (want > hsNum(npc, "wave")) {
        hsPut(npc, "wave", want);
        raiseTotems(npc);
        npc.say("§5Тотемы, встаньте! Мургрот не падёт!");
    }
    var immune = hsCount(npc, TOTEM) > 0;
    hsPut(npc, "immune", immune ? 1 : 0);
    if (immune) return;

    if (hsGet(npc, "copies", "0") == "0" && hp <= 0.5) {
        hsPut(npc, "copies", 1);
        var w = npc.getWorld();
        var h = hsHome(npc);
        for (var c = 0; c < 3; c++) {
            var a = Math.random() * Math.PI * 2;
            w.spawnClone(h[0] + Math.cos(a) * 10, h[1], h[2] + Math.sin(a) * 10, HS.TAB, COPY);
        }
        var a2 = Math.random() * Math.PI * 2;
        npc.setPosition(h[0] + Math.cos(a2) * 10, h[1], h[2] + Math.sin(a2) * 10);
        for (var b = 0; b < ps.length; b++) ps[b].addPotionEffect(PotionEffectType_BLINDNESS, 3, 0, false);
    }
    if (countTagged(npc, COPY_TAG) > 0 || hsCount(npc, COPY) > 0) {
        npc.getWorld().spawnParticle("witch", npc.getX(), npc.getY() + 2.6, npc.getZ(), 0.3, 0.3, 0.3, 0, 12);
    }
    if (npc.getTimers().has(T_SONG) || npc.getTimers().has(T_ROT)) return;
    if (now >= hsNum(npc, "song_at")) {
        hsPut(npc, "song_at", now + SONG_EVERY);
        hsPut(npc, "song", 1);
        hsPut(npc, "song_dmg", 0);
        var h2 = hsHome(npc);
        hsPut(npc, "song_tg", TelegraphAPI.circle(npc, h2[0], h2[1], h2[2], HS.ARENA_R, SONG_TICKS, HS.PURPLE));
        npc.say("§5Песнь безумия! (сбейте §f" + SONG_BREAK + "§5 урона за 5 с)");
        npc.getTimers().forceStart(T_SONG, SONG_TICKS, false);
        return;
    }
    if (now >= hsNum(npc, "rot_at")) {
        hsPut(npc, "rot_at", now + ROT_EVERY);
        var p = hsRandom(ps);
        hsPut(npc, "rot_uuid", p.getUUID());
        var id = TelegraphAPI.circle(npc, p.getX(), p.getY(), p.getZ(), ROT_R, ROT_TICKS, HS.RED);
        TelegraphAPI.follow(id, p);
        p.addPotionEffect(PotionEffectType_GLOWING, 4, 0, false);
        p.message("§cНа тебе Порча Хаоса — отойди от всех!");
        npc.getTimers().forceStart(T_ROT, ROT_TICKS, false);
    }
}

function timer(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    if (e.id == T_SONG && hsGet(npc, "song", "0") == "1") {
        hsPut(npc, "song", 0);
        var ps = hsPlayers(npc);
        for (var i = 0; i < ps.length; i++) {
            hsStrike(ps[i], 0.5);
            ps[i].addPotionEffect(PotionEffectType_NAUSEA, 6, 0, false);
        }
        npc.setHealth(Math.min(npc.getMaxHealth(), npc.getHealth() + npc.getMaxHealth() * 0.1));
    }
    if (e.id == T_ROT) {
        var t = aliveById(npc, "rot_uuid");
        if (t == null) return;
        var hit = hsPlayersAt(npc, t.getX(), t.getY(), t.getZ(), ROT_R);
        for (var j = 0; j < hit.length; j++) hsStrike(hit[j], 0.25 * hit.length);
    }
}

function damaged(e) {
    var npc = e.npc;
    if (hsGet(npc, "immune", "0") == "1") { e.setCanceled(true); return; }
    hsCap(e, 1);
    if (hsGet(npc, "song", "0") != "1") return;
    var dmg = hsNum(npc, "song_dmg") + e.damage;
    hsPut(npc, "song_dmg", dmg);
    if (dmg < SONG_BREAK) return;
    hsPut(npc, "song", 0);
    npc.getTimers().stop(T_SONG);
    TelegraphAPI.remove(hsGet(npc, "song_tg", ""));
    npc.say("§7Песнь сорвана!");
}

function died(e) {
    var npc = e.npc;
    AbilityAPI.cancel(npc);
    hsZoneClear(npc);
    despawnTagged(npc, COPY_TAG);
    hsDespawnAll(npc, COPY);
    hsDespawnAll(npc, TOTEM);
    hsSetGate(npc, false);
    npc.say("§7Туман рассеивается. Корни расступаются.");
}

function arenaNpcs(npc) {
    var c = hsArenaCenter(npc);
    return npc.getWorld().getNearbyEntities(NpcAPI.getIPos(c[0], c[1], c[2]), hsScanRange(), 2);
}

function despawnTagged(npc, tag) {
    var list = arenaNpcs(npc);
    var selfId = String(npc.getUUID());
    for (var i = 0; i < list.length; i++) {
        var n = list[i];
        if (!n.isAlive() || String(n.getUUID()) == selfId) continue;
        if (n.hasTag(tag)) n.despawn();
    }
}

function countTagged(npc, tag) {
    var list = arenaNpcs(npc);
    var selfId = String(npc.getUUID());
    var n = 0;
    for (var i = 0; i < list.length; i++) {
        var e = list[i];
        if (!e.isAlive() || String(e.getUUID()) == selfId) continue;
        if (e.hasTag(tag)) n++;
    }
    return n;
}

function aliveById(npc, key) {
    var id = hsGet(npc, key, "");
    if (id.length < 32) return null;
    try {
        var p = npc.getWorld().getEntity(id);
        if (p != null && p.isAlive()) return p;
    } catch (err) {}
    return null;
}
