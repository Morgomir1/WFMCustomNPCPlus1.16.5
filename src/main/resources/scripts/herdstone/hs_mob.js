// hs_mob.js — второй файл после hs_core.js у мобов переходов.
// Пресет (mob_*.js) ниже задаёт CASTS, SLOW_ON_HIT и POISON_ON_HIT.
// CASTS читается только из событий, поэтому пресет можно класть третьим файлом.
// Запись: ability, cd, min, max, shape, len, size, hitAt, pct, params().
// shape: "line" — полоса, "target" — круг на цели, "self" — процентный удар вокруг моба,
//        "" — только урон самой абилки.
// Красный круг weapon_spin рисует сама абилка (telegraph по умолчанию 1, circleFollow на radius).
// Второй TelegraphAPI.circle для shape "self" не ставим.
var CASTS = [];
var SLOW_ON_HIT = 0;
var POISON_ON_HIT = 0;
var T_HIT = 1;

function hsMobInit(npc) {
    if (!npc.isAlive()) return;
    npc.getTimers().clear();
    AbilityAPI.cancel(npc);
    for (var i = 0; i < CASTS.length; i++) hsPut(npc, "cd_" + i, 0);
}

function init(e) {
    hsMobInit(e.npc);
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) { AbilityAPI.cancel(npc); return; }
    if (typeof hsMobExtra == "function") hsMobExtra(npc);
    if (AbilityAPI.isBusy(npc) || npc.getTimers().has(T_HIT)) return;
    var t = npc.getAttackTarget();
    if (t == null || !t.isAlive()) return;
    var dist = hsFlat(t, npc.getX(), npc.getZ());
    var now = hsNow(npc);
    for (var i = 0; i < CASTS.length; i++) {
        var c = CASTS[i];
        if (now < hsNum(npc, "cd_" + i) || dist < c.min || dist > c.max) continue;
        var yaw = hsYawTo(npc, t);
        if (!hsStart(npc, c.ability, t, c.params())) continue;
        hsPut(npc, "cd_" + i, now + c.cd);
        if (c.pct <= 0) return;
        hsPut(npc, "hit_i", i);
        hsPut(npc, "hit_yaw", yaw);
        hsPut(npc, "hit_x", c.shape == "target" ? t.getX() : npc.getX());
        hsPut(npc, "hit_z", c.shape == "target" ? t.getZ() : npc.getZ());
        if (c.shape == "line") TelegraphAPI.line(npc, npc.getX(), npc.getY(), npc.getZ(), yaw, c.len, c.size * 2, c.hitAt, HS.RED);
        if (c.shape == "target") TelegraphAPI.circle(npc, t.getX(), t.getY(), t.getZ(), c.size, c.hitAt, HS.RED);
        npc.getTimers().forceStart(T_HIT, c.hitAt, false);
        return;
    }
}

function timer(e) {
    if (e.id != T_HIT) return;
    var npc = e.npc;
    if (!npc.isAlive()) return;
    var c = CASTS[hsNum(npc, "hit_i")];
    if (c == null || c.pct <= 0) return;
    var x = hsNum(npc, "hit_x"), z = hsNum(npc, "hit_z"), yaw = hsNum(npc, "hit_yaw");
    if (c.shape == "self") { x = npc.getX(); z = npc.getZ(); }
    var reach = c.shape == "line" ? c.len + 1 : c.size;
    var ps = hsPlayersAt(npc, x, npc.getY(), z, reach);
    for (var i = 0; i < ps.length; i++) {
        if (c.shape == "line" && !hsOnLine(ps[i], x, z, yaw, c.len, c.size)) continue;
        hsStrike(ps[i], c.pct);
    }
}

function meleeAttack(e) {
    if (e.target == null || !e.target.typeOf(1)) return;
    if (SLOW_ON_HIT > 0) e.target.addPotionEffect(PotionEffectType_SLOWNESS, SLOW_ON_HIT, 1, false);
    if (POISON_ON_HIT > 0) e.target.addPotionEffect(PotionEffectType_POISON, POISON_ON_HIT, 0, false);
}
