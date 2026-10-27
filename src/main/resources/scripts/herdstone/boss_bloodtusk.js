// Кровохряк (Б1). В контейнере третьим файлом: hs_core.js, hs_b1.js, этот файл.
// Таран, ярость, серия фазы «Наездник», рикошет и кровавый след фазы «Одиночка».
HS.BASE_HP = 300;
HS.FIXED_SCALE = true;
HS.ARENA = { shape: "rect", hw: 22, hd: 19 };
HS.ENRAGE_SEC = 240;
HS.DISPEL_SEC = 99999;
ORIGIN = [-373224, 101, -13530];
GATE = hsBox(12, 0, -11, 3, 2, 3);
SUMMONS = ["Унгор-рейдер"];

// --- НАСТРОЙКИ СПОСОБНОСТЕЙ ---
var BT_BASE_SPEED = 7;
// Кулдаун тарана по фазам (тики)
var BT_RAM_CD_P1 = 60;
var BT_RAM_CD_P2 = 50;
var BT_RAM_CD_P3 = 40;
var BT_SERIES_LOCK = 100000;   // ram_at на время серии «Наездник»
var BT_SERIES_ORPHAN = 5000;   // серия оборвалась без finish
var BT_FEED_SHORTEN_IF = 25;   // если ram_at далеко — подтянуть после кормёжки
var BT_FEED_SHORTEN_TO = 15;
// Зарядка
var BT_CHARGE_BASE = 30;
var BT_CHARGE_FURY = 5;        // −тики за стак ярости
var BT_CHARGE_MIN = 20;
var BT_SERIES_CHARGE = [30, 25, 20];
var BT_CALL_CHARGE = 30;
var BT_CHASE_CHARGE = 15;
// Длина рывка
var BT_LEN_CHASE = 8;
var BT_LEN_OVER = 4;           // запас за цель
var BT_LEN_MIN = 14;
var BT_LEN_MUD = 4;            // +если линия через грязь
var BT_LEN_MAX = 24;
var BT_LEN_BOUNCE = 10;
// Telegraph / AbilityAPI dash
var BT_LINE_W = 3.0;
var BT_ACTIVE = 8;
var BT_HIT_R = 1.8;
var BT_KB = 0.6;
var BT_KB_Y = 0.15;
var BT_RAM_TIMER_PAD = 10;     // timer = charge + pad
// Урон тарана
var BT_HIT_PCT = 0.35;
var BT_HIT_FURY = 0.10;        // +% за стак ярости
var BT_SIDE_PCT = 0.15;        // боковой удар с седла
var BT_FURY_HIT = 2;
// Кровавый след (фаза 3)
var BT_TRAIL_DMG = 4;
var BT_TRAIL_STEP = 2;
var BT_TRAIL_R = 1.2;
var BT_TRAIL_TICKS = 120;
var BT_TRAIL_PERIOD = 10;
// Рикошет от края
var BT_BOUNCE_MAX = 2;
var BT_BOUNCE_EDGE = 2;
var BT_BOUNCE_WINDUP = 20;
// Догон после тарана (фаза 1)
var BT_CHASE_MIN_DIST = 6;

function init(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    var revived = b1ConsumeRevive(npc);
    if (revived > 0) {
        b1ApplyRevive(npc, revived);
        return;
    }
    if (b1Duplicate(npc)) {
        npc.despawn();
        return;
    }
    if (!hsInit(npc)) return;
    hsPut(npc, "base_speed", BT_BASE_SPEED);
    npc.getAi().setWalkingSpeed(BT_BASE_SPEED);
    b1Release(npc);
    npc.clearPotionEffects();
    hsPut(npc, "stun", 0);
    hsPut(npc, "ram_at", 0);
    hsPut(npc, "widowed", 0);
    hsPut(npc, "b1_fury", 0);
    hsPut(npc, "b1_step", -1);
    hsPut(npc, "b1_bounce", 0);
    hsPut(npc, "b1_mounted", 0);
    hsPut(npc, "b1_face", 0);
    b1ResetWorld(npc);
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) {
        AbilityAPI.cancel(npc);
        return;
    }
    var ps = hsFight(npc);
    if (ps == null) return;
    b1Boot(npc);
    b1ClearStun(npc);
    if (hsGet(npc, "ram_kind", "") == "slide" && !AbilityAPI.isBusy(npc)) {
        hsPut(npc, "ram_kind", "dash");
        b1ResolveRam(npc);
        return;
    }
    b1RefreshLink(npc);
    b1MudTick(npc, ps);
    b1HornTick(npc);
    b1Face(npc);
    b1EdgeTick(npc, ps);
    if (b1WidowTick(npc)) return;
    // Рёв Грула с седла: срываем уже идущий рывок/заряд.
    if (b1RoarBlocks(npc) && (AbilityAPI.isBusy(npc) || npc.getTimers().has(B1_T_RAM)
            || hsGet(npc, "ram_kind", "") != "")) {
        b1InterruptDash(npc);
    }
    // Серия оборвалась без finish — не держим ram_at на +SERIES_LOCK.
    if (hsNum(npc, "b1_step") >= 0
            && !AbilityAPI.isBusy(npc)
            && !npc.getTimers().has(B1_T_RAM)
            && !npc.getTimers().has(B1_T_SLIDE)
            && hsGet(npc, "ram_kind", "") == ""
            && hsNum(npc, "ram_at") > hsNow(npc) + BT_SERIES_ORPHAN) {
        hsPut(npc, "b1_step", -1);
        hsPut(npc, "ram_at", hsNow(npc) + b1RamCd(npc));
    }
    if (hsGet(npc, "b1_feed", "").length >= 32 && hsNum(npc, "ram_at") > hsNow(npc) + BT_FEED_SHORTEN_IF) {
        hsPut(npc, "ram_at", hsNow(npc) + BT_FEED_SHORTEN_TO);
    }
    if (!b1CanAct(npc)) return;
    if (hsGet(npc, "b1_call", "0") == "1") {
        hsPut(npc, "b1_call", 0);
        var grul = b1Other(npc);
        if (grul != null && !b1RoarBlocks(npc) && b1BeginDash(npc, grul, BT_CALL_CHARGE, "call")) {
            b1Say(npc, "Кабаний зов!");
            hsPut(npc, "ram_at", hsNow(npc) + b1RamCd(npc));
        }
        return;
    }
    if (b1RoarBlocks(npc)) return;
    if (hsNow(npc) < hsNum(npc, "ram_at")) return;
    var phase = b1Phase(npc);
    var mounted = hsGet(npc, "b1_mounted", "0") == "1";
    var series = phase == 2 && mounted;
    if (series && hsNum(npc, "b1_step") < 0) hsPut(npc, "b1_step", 0);
    var target = b1Pick(npc, ps);
    if (target == null) return;
    var charge = b1ChargeTicks(npc);
    if (!b1BeginDash(npc, target, charge, series ? "series" : "ram")) return;
    if (series) hsPut(npc, "ram_at", hsNow(npc) + BT_SERIES_LOCK);
    else hsPut(npc, "ram_at", hsNow(npc) + b1RamCd(npc));
}

function b1Face(npc) {
    if (AbilityAPI.isBusy(npc) || npc.getTimers().has(B1_T_SLIDE)) return;
    var target = npc.getAttackTarget();
    if (target != null && target.isAlive()) hsPut(npc, "b1_face", hsYawTo(npc, target));
}

function b1CanAct(npc) {
    if (hsGet(npc, "stun", "0") == "1") return false;
    if (hsNow(npc) < hsNum(npc, "immune_until")) return false;
    if (hsGet(npc, "b1_ritual", "0") == "1") return false;
    if (b1RoarBlocks(npc)) return false;
    if (AbilityAPI.isBusy(npc)) return false;
    if (npc.getTimers().has(B1_T_RAM) || npc.getTimers().has(B1_T_SLIDE)) return false;
    var grul = b1Other(npc);
    if (grul != null && hsGet(grul, "b1_mounting", "0") == "1") return false;
    return true;
}

function b1RamCd(npc) {
    var phase = b1Phase(npc);
    if (phase == 3) return BT_RAM_CD_P3;
    if (phase == 2) return BT_RAM_CD_P2;
    return BT_RAM_CD_P1;
}

function b1ChargeTicks(npc) {
    if (b1Phase(npc) == 2 && hsGet(npc, "b1_mounted", "0") == "1") {
        var step = hsNum(npc, "b1_step");
        if (step < 0) step = 0;
        if (step > 2) step = 2;
        return BT_SERIES_CHARGE[step];
    }
    var charge = BT_CHARGE_BASE - hsNum(npc, "b1_fury") * BT_CHARGE_FURY;
    if (charge < BT_CHARGE_MIN) charge = BT_CHARGE_MIN;
    return charge;
}

function b1Pick(npc, ps) {
    var fed = b1FindPlayer(npc, hsGet(npc, "b1_feed", ""));
    if (fed != null) {
        hsPut(npc, "b1_feed", "");
        return fed;
    }
    if (ps == null || ps.length == 0) return npc.getAttackTarget();
    if (b1Phase(npc) == 2 && hsGet(npc, "b1_mounted", "0") == "1") {
        var avoid = hsGet(npc, "b1_last", "");
        var pool = [];
        for (var i = 0; i < ps.length; i++) {
            if (String(ps[i].getUUID()) != avoid) pool.push(ps[i]);
        }
        if (pool.length == 0) pool = ps;
        return pool[Math.floor(Math.random() * pool.length)];
    }
    var target = npc.getAttackTarget();
    if (target != null && target.isAlive()) return target;
    return ps[0];
}

function b1RamLen(npc, target, kind) {
    if (kind == "chase") return BT_LEN_CHASE;
    var dist = hsFlat(npc, target.getX(), target.getZ());
    var len = dist + BT_LEN_OVER;
    if (len < BT_LEN_MIN) len = BT_LEN_MIN;
    var yaw = hsYawTo(npc, target);
    if (b1LineHitsMud(npc, npc.getX(), npc.getZ(), yaw, len)) len = len + BT_LEN_MUD;
    if (len > BT_LEN_MAX) len = BT_LEN_MAX;
    return len;
}

function b1DrawLine(npc, x, y, z, yaw, len, ticks) {
    b1DropLine(npc);
    var id = TelegraphAPI.line(npc, x, y, z, yaw, len, BT_LINE_W, ticks, HS.RED);
    hsPut(npc, "b1_line", id == null ? "" : String(id));
}

function b1BeginDash(npc, target, charge, kind) {
    return b1FireDash(npc, target, charge, kind);
}

function b1FireDash(npc, target, charge, kind) {
    if (target == null || !target.isAlive()) return false;
    if (b1RoarBlocks(npc)) return false;
    var yaw = hsYawTo(npc, target);
    var len = b1RamLen(npc, target, kind);
    hsPut(npc, "b1_face", yaw);
    hsPut(npc, "ram_x", npc.getX());
    hsPut(npc, "ram_z", npc.getZ());
    hsPut(npc, "ram_yaw", yaw);
    hsPut(npc, "ram_len", len);
    hsPut(npc, "ram_kind", kind);
    hsPut(npc, "ram_target", String(target.getUUID()));
    hsPut(npc, "b1_bounce", 0);
    hsPut(npc, "b1_last", String(target.getUUID()));
    hsPut(npc, "b1_aim", 0);
    b1DrawLine(npc, npc.getX(), npc.getY(), npc.getZ(), yaw, len, charge);
    npc.getAi().setWalkingSpeed(0);
    npc.getAi().setRetaliateType(3);
    Combat.stopNavigation(npc);
    var started = hsStart(npc, "dash", target, AbilityAPI.params(
        "chargeTicks", charge,
        "activeTicks", BT_ACTIVE,
        "distance", len * 1.0,
        "damage", 0,
        "hitRadius", BT_HIT_R,
        "knockback", BT_KB,
        "knockbackY", BT_KB_Y
    ));
    if (!started) {
        b1ClearLine(npc);
        b1Release(npc);
        return false;
    }
    npc.getTimers().forceStart(B1_T_RAM, charge + BT_RAM_TIMER_PAD, false);
    return true;
}

function b1ResolveRam(npc) {
    if (!npc.isAlive()) return;
    var sx = hsNum(npc, "ram_x");
    var sz = hsNum(npc, "ram_z");
    var yaw = hsNum(npc, "ram_yaw");
    var len = hsNum(npc, "ram_len");
    if (!(len > 0)) len = BT_LEN_MIN;
    var pct = BT_HIT_PCT + BT_HIT_FURY * hsNum(npc, "b1_fury");
    var ps = hsPlayersAt(npc, sx, npc.getY(), sz, len + 2);
    for (var i = 0; i < ps.length; i++) {
        if (!hsOnLine(ps[i], sx, sz, yaw, len, BT_HIT_R)) continue;
        hsStrike(ps[i], pct);
        b1AddFury(npc, BT_FURY_HIT);
    }
    if (hsGet(npc, "b1_mounted", "0") == "1") {
        for (var j = 0; j < ps.length; j++) {
            if (hsOnLine(ps[j], sx, sz, yaw, len, BT_HIT_R)) continue;
            if (b1OnSide(ps[j], sx, sz, yaw, len)) hsStrike(ps[j], BT_SIDE_PCT);
        }
    }
    if (b1Phase(npc) == 3) b1Trail(npc, sx, sz, yaw, len);
    if (b1StakeHit(npc)) return;
    if (b1Phase(npc) == 3 && hsNum(npc, "b1_bounce") < BT_BOUNCE_MAX && b1NearEdge(npc)) {
        b1StartBounce(npc);
        return;
    }
    b1FinishSeries(npc);
}

function b1Trail(npc, x, z, yaw, len) {
    var f = b1Forward(yaw);
    var dmg = BT_TRAIL_DMG;
    var n = Math.floor(len / BT_TRAIL_STEP);
    for (var i = 0; i <= n; i++) {
        ZoneAPI.hazardCircle(npc, x + f[0] * i * BT_TRAIL_STEP, npc.getY(), z + f[1] * i * BT_TRAIL_STEP,
            BT_TRAIL_R, BT_TRAIL_TICKS, dmg, BT_TRAIL_PERIOD);
    }
}

function b1StartBounce(npc) {
    var c = hsArenaCenter(npc);
    var a = hsArena();
    var dx = npc.getX() - c[0];
    var dz = npc.getZ() - c[2];
    var adx = dx < 0 ? -dx : dx;
    var adz = dz < 0 ? -dz : dz;
    var f = b1Forward(hsNum(npc, "ram_yaw"));
    var fx = f[0];
    var fz = f[1];
    if (adx >= a.hw - BT_BOUNCE_EDGE) fx = -fx;
    if (adz >= a.hd - BT_BOUNCE_EDGE) fz = -fz;
    var yaw = b1YawFromForward(fx, fz);
    hsPut(npc, "b1_bounce", hsNum(npc, "b1_bounce") + 1);
    hsPut(npc, "ram_yaw", yaw);
    hsPut(npc, "b1_face", yaw);
    hsPut(npc, "ram_x", npc.getX());
    hsPut(npc, "ram_z", npc.getZ());
    hsPut(npc, "ram_len", BT_LEN_BOUNCE);
    hsPut(npc, "b1_slide", 0);
    hsPut(npc, "ram_kind", "windup");
    TelegraphAPI.line(npc, npc.getX(), npc.getY(), npc.getZ(), yaw, BT_LEN_BOUNCE, BT_LINE_W, BT_BOUNCE_WINDUP, HS.RED);
    npc.setRotation(yaw);
    npc.getAi().setWalkingSpeed(0);
    npc.getAi().setRetaliateType(3);
    npc.getTimers().forceStart(B1_T_RAM, BT_BOUNCE_WINDUP, false);
}

function b1MaybeChase(npc) {
    if (hsGet(npc, "stun", "0") == "1") return false;
    if (b1Phase(npc) != 1) return false;
    if (hsGet(npc, "b1_mounted", "0") == "1") return false;
    var target = b1FindPlayer(npc, hsGet(npc, "ram_target", ""));
    if (target == null) {
        var ps = hsPlayers(npc);
        if (ps == null || ps.length == 0) return false;
        target = ps[0];
        var best = hsFlat(npc, target.getX(), target.getZ());
        for (var i = 1; i < ps.length; i++) {
            var dist = hsFlat(npc, ps[i].getX(), ps[i].getZ());
            if (dist < best) {
                best = dist;
                target = ps[i];
            }
        }
    }
    if (hsFlat(npc, target.getX(), target.getZ()) <= BT_CHASE_MIN_DIST) return false;
    return b1BeginDash(npc, target, BT_CHASE_CHARGE, "chase");
}

function b1FinishSeries(npc) {
    if (hsGet(npc, "stun", "0") == "1") return;
    if (b1RoarBlocks(npc)) {
        hsPut(npc, "b1_step", -1);
        hsPut(npc, "ram_at", hsNow(npc) + b1RamCd(npc));
        b1Release(npc);
        return;
    }
    var step = hsNum(npc, "b1_step");
    var kind = hsGet(npc, "ram_kind", "");
    if (hsGet(npc, "b1_mounted", "0") == "1" && step >= 0 && step < 2) {
        hsPut(npc, "b1_step", step + 1);
        var ps = hsPlayers(npc);
        var target = b1Pick(npc, ps);
        if (target != null && b1BeginDash(npc, target, b1ChargeTicks(npc), "series")) return;
    }
    if (kind != "chase" && kind != "call" && b1MaybeChase(npc)) return;
    if (step >= 0 || kind == "chase") {
        hsPut(npc, "b1_step", -1);
        hsPut(npc, "ram_at", hsNow(npc) + b1RamCd(npc));
    }
    b1Release(npc);
}

function timer(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    if (e.id == B1_T_STUN) {
        hsPut(npc, "stun", 0);
        b1Release(npc);
        return;
    }
    if (e.id == B1_T_RITUAL) {
        b1RitualFire(npc);
        return;
    }
    if (e.id != B1_T_RAM) return;
    if (hsGet(npc, "ram_kind", "") == "windup") {
        npc.setRotation(hsNum(npc, "ram_yaw"));
        hsPut(npc, "ram_kind", "slide");
        if (!hsStart(npc, "b1_slide", npc, AbilityAPI.params(
                "activeTicks", BT_ACTIVE, "distance", BT_LEN_BOUNCE, "telegraph", 0))) {
            hsPut(npc, "ram_kind", "dash");
            b1ResolveRam(npc);
        }
        return;
    }
    b1ResolveRam(npc);
}

function damaged(e) {
    var npc = e.npc;
    var mul = hsGet(npc, "stun", "0") == "1" ? 1 : HS.WEAK;
    b1Cap(e, mul);
    if (hsNow(npc) < hsNum(npc, "immune_until")) return;
    b1RitualHurt(e);
}

function died(e) {
    b1Died(e);
}
