// Великая Утроба, Горгон (Б3). В контейнере вторым после hs_core.js.
// Круг C5, r 21. Пасть — конус 9 блоков (полуугол 30° = 60°).
// Сжатие: одно кольцо зоны без плоского урона и отдельно −10% HP раз в секунду.
// Зона лежит в tempdata и снимается в init (через hsInit) и в died.
HS.BASE_HP = 520;
HS.CAP = 14;
HS.ARENA_R = 21;
HS.ENRAGE_SEC = 480;
ORIGIN = [415, 32, 477];   // мировой блок пола. Сдвинул данж — впиши центр этой арены
GATE = hsBox(-2, 0, -12, 3, 2, 3);   // завал Глотки C5_down (413, 465)
var REND_MAX = 5;
var SAFE_R = 13;
var CONE_LEN = 9;
var T_CONE = 1, T_SLAM = 2;

function init(e) {
    var npc = e.npc;
    if (!hsInit(npc)) return;
    if (hsGet(npc, "base_speed", "") == "") hsPut(npc, "base_speed", npc.getAi().getWalkingSpeed());
    npc.getAi().setWalkingSpeed(hsNum(npc, "base_speed"));
    hsPut(npc, "shrink", 0);
    hsPut(npc, "shrink_until", 0);
    hsPut(npc, "frenzy", 0);
    hsPut(npc, "cone_at", hsNow(npc) + 160);
    hsPut(npc, "slam_at", hsNow(npc) + 240);
}

function cd(npc, ticks) { return hsGet(npc, "frenzy", "0") == "1" ? Math.round(ticks * 0.6) : ticks; }

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) { AbilityAPI.cancel(npc); return; }
    var ps = hsFight(npc);
    if (ps == null) return;
    var now = hsNow(npc);
    var hp = npc.getHealth() / npc.getMaxHealth();
    var h = hsHome(npc);
    decayRend(npc, ps, now);

    var shrink = hsNum(npc, "shrink");
    if ((shrink < 1 && hp <= 0.66) || (shrink < 2 && hp <= 0.33)) {
        hsPut(npc, "shrink", shrink + 1);
        hsPut(npc, "shrink_until", now + 400);
        var zone = ZoneAPI.hazardRing(npc, h[0], h[1], h[2], HS.ARENA_R, SAFE_R, 400, 0, 20);
        hsZonePut(npc, zone);
        npc.say("§4Яма сжимается! К центру!");
    }
    if (now < hsNum(npc, "shrink_until") && now % 20 < 10) {
        for (var i = 0; i < ps.length; i++) if (hsFlat(ps[i], h[0], h[2]) > SAFE_R) hsStrike(ps[i], 0.10);
    }
    if (hsGet(npc, "frenzy", "0") == "0" && hp <= 0.25) {
        hsPut(npc, "frenzy", 1);
        npc.getAi().setWalkingSpeed(Math.min(10, hsNum(npc, "base_speed") + 2));
        npc.say("§4Горгон в бешенстве!");
    }

    if (AbilityAPI.isBusy(npc) || npc.getTimers().has(T_CONE) || npc.getTimers().has(T_SLAM)) return;
    var t = npc.getAttackTarget();
    if (t == null || !t.isAlive()) return;
    if (now >= hsNum(npc, "cone_at")) {
        hsPut(npc, "cone_at", now + cd(npc, 240));
        var yaw = hsYawTo(npc, t);
        hsPut(npc, "cone_x", npc.getX());
        hsPut(npc, "cone_z", npc.getZ());
        hsPut(npc, "cone_yaw", yaw);
        TelegraphAPI.cone(npc, npc.getX(), npc.getY(), npc.getZ(), yaw, CONE_LEN, 30.0, 30, HS.RED);
        npc.getTimers().forceStart(T_CONE, 30, false);
        return;
    }
    if (now >= hsNum(npc, "slam_at")) {
        var far = ps[0];
        for (var j = 1; j < ps.length; j++) if (hsFlat(ps[j], npc.getX(), npc.getZ()) > hsFlat(far, npc.getX(), npc.getZ())) far = ps[j];
        hsPut(npc, "slam_at", now + cd(npc, 320));
        hsPut(npc, "slam_x", far.getX());
        hsPut(npc, "slam_z", far.getZ());
        TelegraphAPI.circle(npc, far.getX(), far.getY(), far.getZ(), 5.0, 40, HS.RED);
        hsStart(npc, "jump_slam", far, AbilityAPI.params("chargeTicks", 24, "landRadius", 5.0, "damage", 6.0));
        npc.getTimers().forceStart(T_SLAM, 40, false);
    }
}

function timer(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    var ps = hsPlayers(npc);
    var i;
    if (e.id == T_CONE) {
        var ox = hsNum(npc, "cone_x"), oz = hsNum(npc, "cone_z"), yaw = hsNum(npc, "cone_yaw");
        for (i = 0; i < ps.length; i++) {
            if (hsFlat(ps[i], ox, oz) <= CONE_LEN && Combat.isInFrontCone(ox, oz, yaw, ps[i], 30.0)) hsStrike(ps[i], 0.7);
        }
    }
    if (e.id == T_SLAM) {
        var sx = hsNum(npc, "slam_x"), sz = hsNum(npc, "slam_z");
        for (i = 0; i < ps.length; i++) if (hsFlat(ps[i], sx, sz) <= 5) hsStrike(ps[i], 0.3);
    }
}

function meleeAttack(e) {
    var p = e.target;
    if (p == null || !p.typeOf(1)) return;
    var td = e.npc.getTempdata();
    var key = "rend_" + p.getUUID();
    var n = (td.has(key) ? Number(String(td.get(key))) : 0) + 1;
    td.put("at_" + p.getUUID(), String(hsNow(e.npc)));
    if (n >= REND_MAX) {
        td.put(key, "0");
        hsStrike(p, 0.5);
        p.message("§4Горгон разрывает тебя!");
        return;
    }
    td.put(key, String(n));
    p.message("§cРаздирание: " + n + "/" + REND_MAX + " — отойди, пока не спадёт");
}

function decayRend(npc, ps, now) {
    var td = npc.getTempdata();
    for (var i = 0; i < ps.length; i++) {
        var u = ps[i].getUUID();
        if (!td.has("rend_" + u)) continue;
        var n = Number(String(td.get("rend_" + u)));
        if (n <= 0 || now - Number(String(td.get("at_" + u))) < 60) continue;
        td.put("rend_" + u, String(n - 1));
        td.put("at_" + u, String(now));
    }
}

function damaged(e) {
    hsCap(e, 1);
}

function died(e) {
    var npc = e.npc;
    AbilityAPI.cancel(npc);
    hsZoneClear(npc);
    hsSetGate(npc, false);
    npc.say("§7Утроба затихла. Глотка открыта.");
}
