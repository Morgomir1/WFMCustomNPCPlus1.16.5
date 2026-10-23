// Бормочущий, Джабберслайт (секрет C4). В контейнере вторым после hs_core.js.
// Зал 72×40. Круг r 22 не дотягивается до менгиров (дальний на ≈23.7) и даёт поводок
// в укрытии. Прямоугольник покрывает пол. Менгиры — метки строителя от центра маркера.
HS.BASE_HP = 380;
HS.ARENA = { shape: "rect", hw: 36, hd: 20 };
HS.ENRAGE_SEC = 480;
CENTER = [412, 0, 602];
FLOOR = 3;
ORIGIN = [];
var MENHIRS = [ [19, 0, 9], [-21, 0, 9], [19, 0, -11], [-21, 0, -11] ];
var COVER = 2.5;
var GAZE_EVERY = 240;
var T_GAZE = 1;

function init(e) {
    var npc = e.npc;
    if (!hsInit(npc)) return;
    hsPut(npc, "gaze_at", hsNow(npc) + 200);
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    var ps = hsFight(npc);
    if (ps == null || npc.getTimers().has(T_GAZE)) return;
    var now = hsNow(npc);
    if (now < hsNum(npc, "gaze_at")) return;
    hsPut(npc, "gaze_at", now + GAZE_EVERY);
    for (var i = 0; i < MENHIRS.length; i++) {
        var m = hsXYZ(npc, MENHIRS[i]);
        TelegraphAPI.circle(npc, m[0] + 0.5, m[1], m[2] + 0.5, COVER, 40, HS.GOLD);
    }
    npc.say("§2Бормочущий поднимает взгляд… К менгирам!");
    npc.getTimers().forceStart(T_GAZE, 40, false);
}

function timer(e) {
    var npc = e.npc;
    if (e.id != T_GAZE || !npc.isAlive()) return;
    var ps = hsPlayers(npc);
    for (var i = 0; i < ps.length; i++) {
        var safe = false;
        for (var j = 0; j < MENHIRS.length; j++) {
            var m = hsXYZ(npc, MENHIRS[j]);
            if (hsFlat(ps[i], m[0] + 0.5, m[2] + 0.5) <= COVER) safe = true;
        }
        if (safe) continue;
        hsStrike(ps[i], 0.7);
        ps[i].addPotionEffect(PotionEffectType_NAUSEA, 6, 0, false);
    }
}

function meleeAttack(e) {
    if (e.target != null && e.target.typeOf(1)) e.target.addPotionEffect(PotionEffectType_POISON, 4, 1, false);
}

function damaged(e) {
    hsCap(e, 1);
}

function died(e) {
    AbilityAPI.cancel(e.npc);
    hsZoneClear(e.npc);
}
