// Кровохряк (Б1). В контейнере третьим файлом: hs_core.js, hs_b1.js, этот файл.
// Таран, ярость, серия фазы «Наездник», рикошет и кровавый след фазы «Одиночка».
HS.BASE_HP = 300;
HS.ARENA = { shape: "rect", hw: 22, hd: 19 };
HS.ENRAGE_SEC = 360;
HS.DISPEL_SEC = 99999;
ORIGIN = [280, 80, 37];
GATE = hsBox(12, 0, -11, 3, 2, 3);
SUMMONS = ["Унгор-рейдер (призыв)"];

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
    if (hsGet(npc, "base_speed", "") == "") hsPut(npc, "base_speed", npc.getAi().getWalkingSpeed());
    b1Release(npc);
    npc.clearPotionEffects();
    hsPut(npc, "stun", 0);
    hsPut(npc, "ram_at", 0);
    hsPut(npc, "mark_until", 0);
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
    b1Boot(npc, ps);
    b1RefreshLink(npc);
    b1MudTick(npc, ps);
    b1HornTick(npc);
    b1Face(npc);
    if (b1WidowTick(npc)) return;
    if (!b1CanAct(npc)) return;
    if (hsGet(npc, "b1_call", "0") == "1") {
        hsPut(npc, "b1_call", 0);
        var grul = b1Other(npc);
        if (grul != null && !b1RoarBlocks(npc) && b1BeginDash(npc, grul, 30, "call")) {
            npc.say("§4Кабаний зов!");
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
    if (series) hsPut(npc, "ram_at", hsNow(npc) + 100000);
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
    if (AbilityAPI.isBusy(npc)) return false;
    if (npc.getTimers().has(B1_T_RAM) || npc.getTimers().has(B1_T_SLIDE)) return false;
    var grul = b1Other(npc);
    if (grul != null && hsGet(grul, "b1_mounting", "0") == "1") return false;
    return true;
}

function b1RamCd(npc) {
    var phase = b1Phase(npc);
    if (phase == 3) return 120;
    if (phase == 2) return 200;
    return 180;
}

function b1ChargeTicks(npc) {
    if (b1Phase(npc) == 2 && hsGet(npc, "b1_mounted", "0") == "1") {
        var step = hsNum(npc, "b1_step");
        if (step < 0) step = 0;
        if (step > 2) step = 2;
        var table = [30, 25, 20];
        return table[step];
    }
    var charge = 40 - hsNum(npc, "b1_fury") * 5;
    if (charge < 20) charge = 20;
    return charge;
}

function b1Pick(npc, ps) {
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
    var marked = b1Marked(npc);
    if (marked != null) return marked;
    var target = npc.getAttackTarget();
    if (target != null && target.isAlive()) return target;
    return ps[0];
}

function b1Marked(npc) {
    if (hsNow(npc) > hsNum(npc, "mark_until")) return null;
    return b1FindPlayer(npc, hsGet(npc, "mark_uuid", ""));
}

function b1BeginDash(npc, target, charge, kind) {
    if (target == null || !target.isAlive()) return false;
    var yaw = hsYawTo(npc, target);
    var len = 14;
    if (b1LineHitsMud(npc, npc.getX(), npc.getZ(), yaw, 14)) len = 18;
    hsPut(npc, "b1_face", yaw);
    hsPut(npc, "ram_x", npc.getX());
    hsPut(npc, "ram_z", npc.getZ());
    hsPut(npc, "ram_yaw", yaw);
    hsPut(npc, "ram_len", len);
    hsPut(npc, "ram_kind", kind);
    hsPut(npc, "b1_bounce", 0);
    hsPut(npc, "b1_last", String(target.getUUID()));
    TelegraphAPI.line(npc, npc.getX(), npc.getY(), npc.getZ(), yaw, len, 3.0, charge, HS.RED);
    if (kind == "series") {
        TelegraphAPI.cone(npc, npc.getX(), npc.getY(), npc.getZ(), yaw, 6, 30, charge, HS.GOLD);
    }
    var started = hsStart(npc, "dash", target, AbilityAPI.params(
        "chargeTicks", charge,
        "activeTicks", 8,
        "distance", len * 1.0,
        "damage", 0,
        "hitRadius", 1.8,
        "knockback", 0.6,
        "knockbackY", 0.15
    ));
    if (!started) return false;
    npc.getTimers().forceStart(B1_T_RAM, charge + 10, false);
    return true;
}

function b1ResolveRam(npc) {
    if (!npc.isAlive()) return;
    var sx = hsNum(npc, "ram_x");
    var sz = hsNum(npc, "ram_z");
    var yaw = hsNum(npc, "ram_yaw");
    var len = hsNum(npc, "ram_len");
    if (!(len > 0)) len = 14;
    var pct = 0.30 + 0.05 * hsNum(npc, "b1_fury");
    var mark = "";
    if (hsNow(npc) <= hsNum(npc, "mark_until")) mark = hsGet(npc, "mark_uuid", "");
    var ps = hsPlayersAt(npc, sx, npc.getY(), sz, len + 2);
    for (var i = 0; i < ps.length; i++) {
        if (!hsOnLine(ps[i], sx, sz, yaw, len, 1.8)) continue;
        hsStrike(ps[i], pct);
        if (mark.length >= 32 && String(ps[i].getUUID()) == mark) b1AddFury(npc, 1);
        else b1AddFury(npc, 2);
    }
    if (hsGet(npc, "b1_mounted", "0") == "1") {
        for (var j = 0; j < ps.length; j++) {
            if (hsOnLine(ps[j], sx, sz, yaw, len, 1.8)) continue;
            if (b1OnSide(ps[j], sx, sz, yaw, len)) hsStrike(ps[j], 0.15);
        }
    }
    if (b1Phase(npc) == 3) b1Trail(npc, sx, sz, yaw, len);
    if (b1StakeHit(npc)) return;
    if (b1Phase(npc) == 3 && hsNum(npc, "b1_bounce") < 2 && b1NearEdge(npc)) {
        b1StartBounce(npc);
        return;
    }
    b1FinishSeries(npc);
}

function b1Trail(npc, x, z, yaw, len) {
    var f = b1Forward(yaw);
    var dmg = 2 * hsScale(npc.getWorld());
    var n = Math.floor(len / 2);
    for (var i = 0; i <= n; i++) {
        ZoneAPI.hazardCircle(npc, x + f[0] * i * 2, npc.getY(), z + f[1] * i * 2, 1.2, 120, dmg, 20);
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
    if (adx >= a.hw - 2) fx = -fx;
    if (adz >= a.hd - 2) fz = -fz;
    var yaw = b1YawFromForward(fx, fz);
    hsPut(npc, "b1_bounce", hsNum(npc, "b1_bounce") + 1);
    hsPut(npc, "ram_yaw", yaw);
    hsPut(npc, "b1_face", yaw);
    hsPut(npc, "ram_x", npc.getX());
    hsPut(npc, "ram_z", npc.getZ());
    hsPut(npc, "ram_len", 10);
    hsPut(npc, "b1_slide", 0);
    hsPut(npc, "ram_kind", "windup");
    TelegraphAPI.line(npc, npc.getX(), npc.getY(), npc.getZ(), yaw, 10, 3.0, 20, HS.RED);
    npc.getAi().setWalkingSpeed(0);
    npc.getAi().setRetaliateType(3);
    npc.getTimers().forceStart(B1_T_RAM, 20, false);
}

function b1Slide(npc) {
    if (hsGet(npc, "ram_kind", "") != "slide") {
        npc.getTimers().stop(B1_T_SLIDE);
        return;
    }
    var step = hsNum(npc, "b1_slide") + 1;
    hsPut(npc, "b1_slide", step);
    var f = b1Forward(hsNum(npc, "ram_yaw"));
    var len = hsNum(npc, "ram_len");
    if (!(len > 0)) len = 10;
    var t = step / 8;
    if (t > 1) t = 1;
    npc.setPosition(hsNum(npc, "ram_x") + f[0] * len * t, npc.getY(), hsNum(npc, "ram_z") + f[1] * len * t);
    npc.setRotation(hsNum(npc, "ram_yaw"));
    if (step < 8) return;
    hsPut(npc, "ram_kind", "dash");
    npc.getTimers().stop(B1_T_SLIDE);
    b1ResolveRam(npc);
}

function b1FinishSeries(npc) {
    if (hsGet(npc, "stun", "0") == "1") return;
    var step = hsNum(npc, "b1_step");
    if (hsGet(npc, "b1_mounted", "0") == "1" && step >= 0 && step < 2) {
        hsPut(npc, "b1_step", step + 1);
        var ps = hsPlayers(npc);
        var target = b1Pick(npc, ps);
        if (target != null && b1BeginDash(npc, target, b1ChargeTicks(npc), "series")) return;
    }
    if (step >= 0) {
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
    if (e.id == B1_T_SLIDE) {
        b1Slide(npc);
        return;
    }
    if (e.id != B1_T_RAM) return;
    if (hsGet(npc, "ram_kind", "") == "windup") {
        hsPut(npc, "ram_kind", "slide");
        hsPut(npc, "b1_slide", 0);
        npc.getTimers().forceStart(B1_T_SLIDE, 1, true);
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
    b1OnDied(e.npc);
}
