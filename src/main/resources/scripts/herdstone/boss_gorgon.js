// Великая Утроба, Горгон (Б3). В контейнере вторым после hs_core.js.
// Круг C5, r 21. Пасть — конус 9 блоков (полуугол 30° = 60°).
// Сжатие: hazardRing (урон 0) — внешний ARENA_R, внутренний safeR линейно от HP;
// вне safeR −10% HP раз в секунду. Зона в tempdata, сброс в hsInit / died.
HS.BASE_HP = 520;
HS.CAP = 14;
HS.ARENA_R = 21;
HS.ENRAGE_SEC = 480;
ORIGIN = [-373089, 53, -13091];   // мировой блок пола. Сдвинул данж — впиши центр этой арены
GATE = hsBox(-2, 0, -12, 3, 2, 3);   // завал Глотки C5_down (413, 465)
var SAFE_R = 13;          // safeR при 0% HP
var RING_MIN_W = 0.75;    // минимальная толщина кольца для отрисовки
var CONE_LEN = 9;
var T_CONE = 1, T_SLAM = 2;
// Клипы из wfm:animations/beastmen_gorgon.animation.json (как у GorgonEntity).
var ANIM_CONE = "attack3";   // 1.5s ≈ telegraph 30 тиков
var ANIM_SLAM = "attack2";   // 1.25s
// °/тик — как WFM AbstractAnimatedEntity / GorgonEntity (SmoothMovementController).
var TURN_DEG = 5;
var NpcTurnHelper = Java.type("noppes.npcs.ai.NpcTurnHelper");

function init(e) {
    var npc = e.npc;
    if (!hsInit(npc)) return;
    if (hsGet(npc, "base_speed", "") == "") hsPut(npc, "base_speed", npc.getAi().getWalkingSpeed());
    // 0 = Мстить: преследует цель. Урон мили режется в meleeAttack.
    npc.getAi().setRetaliateType(0);
    npc.getAi().setWalkingSpeed(hsNum(npc, "base_speed"));
    try { NpcTurnHelper.apply(npc, TURN_DEG); } catch (err) { log("gorgon turn: " + err); }
    hsPut(npc, "shrink", 0);
    hsPut(npc, "frenzy", 0);
    hsPut(npc, "cone_at", hsNow(npc) + 160);
    hsPut(npc, "slam_at", hsNow(npc) + 240);
}

function cd(npc, ticks) { return hsGet(npc, "frenzy", "0") == "1" ? Math.round(ticks * 0.6) : ticks; }

// GeckoLib playOnce → PacketSyncAnimation → EntityCustomModel.manualAnim.
function gorgonAnim(npc, name) {
    if (name == null || name == "") return;
    try {
        npc.syncAnimationsForAll(NpcAPI.createAnimBuilder().playOnce(name));
    } catch (err) {
        log("gorgon anim: " + err);
    }
}

// safeR: ARENA_R при полном HP → SAFE_R при 0%.
function gorgonSafeR(hp) {
    var t = hp;
    if (t < 0) t = 0;
    if (t > 1) t = 1;
    return SAFE_R + (HS.ARENA_R - SAFE_R) * t;
}

function gorgonZoneGet(npc) {
    var td = npc.getTempdata();
    if (td == null || !td.has("hs_zone")) return null;
    var z = td.get("hs_zone");
    try {
        if (z == null || z.removed) {
            td.remove("hs_zone");
            return null;
        }
    } catch (err) {
        td.remove("hs_zone");
        return null;
    }
    return z;
}

// Обновляет кольцо: outer = ARENA_R, inner = safeR (с мин. толщиной для видимости).
function gorgonUpdateRing(npc, h, safeR) {
    var outer = HS.ARENA_R;
    var inner = safeR;
    if (inner > outer - RING_MIN_W) inner = outer - RING_MIN_W;
    var zone = gorgonZoneGet(npc);
    if (zone == null) {
        zone = ZoneAPI.hazardRing(npc, h[0], h[1], h[2], outer, inner, 200, 0, 20);
        if (zone == null) return;
        zone.setColor(HS.RED);
        hsZonePut(npc, zone);
        return;
    }
    zone.setRadius(outer);
    zone.setInnerRadius(inner);
    zone.setLifetimeTicks(200);
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) { AbilityAPI.cancel(npc); return; }
    var ps = hsFight(npc);
    if (ps == null) return;
    var now = hsNow(npc);
    var hp = npc.getHealth() / npc.getMaxHealth();
    var h = hsHome(npc);

    var safeR = gorgonSafeR(hp);
    gorgonUpdateRing(npc, h, safeR);

    var shrink = hsNum(npc, "shrink");
    if ((shrink < 1 && hp <= 0.66) || (shrink < 2 && hp <= 0.33)) {
        hsPut(npc, "shrink", shrink + 1);
        npc.say("§4Яма сжимается! К центру!");
    }
    if (safeR < HS.ARENA_R - 0.25 && now % 20 < 10) {
        for (var i = 0; i < ps.length; i++) if (hsFlat(ps[i], h[0], h[2]) > safeR) hsStrike(ps[i], 0.10);
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
        gorgonAnim(npc, ANIM_CONE);
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
        gorgonAnim(npc, ANIM_SLAM);
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

// Преследование остаётся (Retaliate 0), обычный удар отменяем — урон только от абилок.
function meleeAttack(e) {
    e.setCanceled(true);
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
