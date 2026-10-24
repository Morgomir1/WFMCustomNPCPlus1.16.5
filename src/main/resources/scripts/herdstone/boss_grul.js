// Грул Кабанья Рука (Б1). В контейнере третьим файлом: hs_core.js, hs_b1.js, этот файл.
// Цикл 45 с: рёв, метка, колья, аркан. Фаза 2 — прыжок на кабана. Фаза 3 — клич и ритуал.
// Сети кидают унгоры-рейдеры (mob_raider.js), не Грул.
HS.BASE_HP = 220;
HS.ARENA = { shape: "rect", hw: 22, hd: 19 };
HS.ENRAGE_SEC = 240;
HS.DISPEL_SEC = 99999;
ORIGIN = [-373224, 101, -13530];
GATE = hsBox(12, 0, -11, 3, 2, 3);
SUMMONS = ["Унгор-рейдер"];

var B1_MARKS = [0, 140, 300, 440, 600, 740];

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
    hsPut(npc, "widowed", 0);
    hsPut(npc, "b1_mounted", 0);
    hsPut(npc, "b1_mounting", 0);
    hsPut(npc, "b1_mount_done", 0);
    hsPut(npc, "b1_mount_go", 0);
    hsPut(npc, "b1_cycle_from", 0);
    hsPut(npc, "b1_cycle_i", 0);
    hsPut(npc, "b1_cycle_loop", 0);
    hsPut(npc, "b1_squeal", 0);
    hsPut(npc, "b1_lasso_cd", 0);
    b1ResetWorld(npc);
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    var ps = hsFight(npc);
    if (ps == null) return;
    b1Boot(npc, ps);
    b1ClearStun(npc);
    gTripHold(npc);
    b1RefreshLink(npc);
    b1MudTick(npc, ps);
    b1HornTick(npc);
    var ritual = b1WidowTick(npc);
    b1RecoverTick(npc);
    if (!ritual) b1MountTick(npc);
    if (ritual || hsGet(npc, "b1_ritual", "0") == "1") return;
    if (hsGet(npc, "stun", "0") == "1" || hsGet(npc, "b1_mounting", "0") == "1") return;
    b1EdgeTick(npc, ps);
    if (hsGet(npc, "b1_haul", "0") == "1") return;
    var leap = hsGet(npc, "b1_leap", "");
    if (leap == "roar") {
        gLeapStep(npc);
        return;
    }
    if (leap == "gap") {
        if (!AbilityAPI.isBusy(npc) && hsNow(npc) >= hsNum(npc, "b1_leap_from") + 12) gGapLand(npc);
        return;
    }
    gCycle(npc, ps);
    if (!gFarWatch(npc, ps)) gPress(npc, ps);
    gTrip(npc, ps);
    gExtraLasso(npc, ps);
    gSqueal(npc, ps);
}

function gCycle(npc, ps) {
    var now = hsNow(npc);
    if (hsNum(npc, "b1_cycle_from") <= 0) hsPut(npc, "b1_cycle_from", hsNum(npc, "fight_at"));
    var from = hsNum(npc, "b1_cycle_from");
    if (!(from > 0)) return;
    var loop = Math.floor((now - from) / 900);
    if (loop < 0) loop = 0;
    if (loop != hsNum(npc, "b1_cycle_loop")) {
        hsPut(npc, "b1_cycle_loop", loop);
        hsPut(npc, "b1_cycle_i", 0);
    }
    var idx = hsNum(npc, "b1_cycle_i");
    if (idx >= B1_MARKS.length) return;
    var elapsed = now - from - loop * 900;
    if (elapsed < B1_MARKS[idx]) return;
    hsPut(npc, "b1_cycle_i", idx + 1);
    if (idx == 0) gRoar(npc);
    else if (idx == 1 || idx == 3) gMark(npc, ps);
    else if (idx == 2) gStake(npc);
    else if (idx == 4) gLasso(npc, ps);
    else if (idx == 5) {
        gMark(npc, ps);
        gStake(npc);
    }
}

function gRoar(npc) {
    if (hsGet(npc, "b1_mounted", "0") != "1" && hsGet(npc, "b1_leap", "") == "") {
        var c = hsArenaCenter(npc);
        if (hsFlat(npc, c[0], c[2]) > 1.5 && gStartLeap(npc, c[0], c[2], "roar")) return;
    }
    gRoarNow(npc);
}

function gRoarNow(npc) {
    var now = hsNow(npc);
    b1WPut(npc, "hs_b1_roar_until", now + 60);
    var from = hsNum(npc, "b1_cycle_from");
    var loop = hsNum(npc, "b1_cycle_loop");
    b1WPut(npc, "hs_b1_next_roar", from + (loop + 1) * 900);
    if (hsGet(npc, "b1_mounted", "0") != "1") {
        npc.getAi().setWalkingSpeed(0);
        npc.getAi().setRetaliateType(3);
    }
    TelegraphAPI.ring(npc, npc.getX(), npc.getY(), npc.getZ(), B1_ROAR_R, 1.5, 60, HS.PURPLE);
    npc.getWorld().playSoundAt(npc.getPos(), "minecraft:entity.ravager.roar", 3.0, 0.6);
    npc.getTimers().forceStart(B1_T_ROAR, 60, false);
    b1Say(npc, "Грул набирает рёв Стада!");
}

function gRoarHit(npc) {
    if (!npc.isAlive()) return;
    var ps = hsPlayersAt(npc, npc.getX(), npc.getY(), npc.getZ(), B1_ROAR_R);
    for (var i = 0; i < ps.length; i++) {
        var p = ps[i];
        if (b1InStakeShadow(p, npc)) continue;
        p.clearPotionEffects();
        p.addPotionEffect(PotionEffectType_SLOWNESS, 2, 3, false);
        hsStrike(p, 0.10);
        b1Tell(p, "Рёв Стада срывает с тебя все чары.");
    }
    gSummon(npc);
    b1Release(npc);
}

function gSummon(npc) {
    var want = b1FightN(npc) <= 1 ? 1 : 2;
    var room = 4 - hsCount(npc, B1_RAIDER);
    if (room > want) room = want;
    if (room <= 0) return;
    var h = hsHome(npc);
    var w = npc.getWorld();
    var living = hsFind(npc, B1_RAIDER);
    var anchor = gFarthest(npc, hsPlayers(npc));
    var dx = 0;
    var dz = 1;
    var ox = npc.getX();
    var oz = npc.getZ();
    if (anchor != null) {
        ox = anchor.getX();
        oz = anchor.getZ();
        dx = ox - npc.getX();
        dz = oz - npc.getZ();
    }
    var len = Math.sqrt(dx * dx + dz * dz);
    if (len < 0.5) {
        dx = 0;
        dz = 1;
        len = 1;
    }
    var back = 5;
    var x = ox + dx / len * back;
    var z = oz + dz / len * back;
    var spot = gClampArena(npc, x, z);
    var spawned = 0;
    if (!gRaiderNear(living, spot[0], spot[1])) {
        hsNoRespawn(w.spawnClone(spot[0], h[1], spot[1], HS.TAB, B1_RAIDER));
        spawned = 1;
    }
    if (room >= 2) {
        var side = gClampArena(npc, spot[0] + (-dz / len) * 3, spot[1] + (dx / len) * 3);
        if (!gRaiderNear(living, side[0], side[1])) {
            hsNoRespawn(w.spawnClone(side[0], h[1], side[1], HS.TAB, B1_RAIDER));
            spawned = spawned + 1;
        }
    }
    if (spawned > 0) b1NoteRaiders(npc);
}

function gFarthest(npc, ps) {
    if (ps == null || ps.length == 0) return null;
    var far = ps[0];
    var best = hsFlat(npc, far.getX(), far.getZ());
    for (var i = 1; i < ps.length; i++) {
        var dist = hsFlat(npc, ps[i].getX(), ps[i].getZ());
        if (dist > best) {
            best = dist;
            far = ps[i];
        }
    }
    return far;
}

function gClampArena(npc, x, z) {
    var c = hsArenaCenter(npc);
    var a = hsArena();
    var minX = c[0] - a.hw + 2;
    var maxX = c[0] + a.hw - 2;
    var minZ = c[2] - a.hd + 2;
    var maxZ = c[2] + a.hd - 2;
    if (x < minX) x = minX;
    if (x > maxX) x = maxX;
    if (z < minZ) z = minZ;
    if (z > maxZ) z = maxZ;
    return [x, z];
}

function gRaiderNear(living, x, z) {
    for (var i = 0; i < living.length; i++) {
        if (!living[i].isAlive()) continue;
        var dx = living[i].getX() - x;
        var dz = living[i].getZ() - z;
        if (dx * dx + dz * dz < 9) return true;
    }
    return false;
}

function gMark(npc, ps) {
    var boar = b1Other(npc);
    if (boar == null || ps == null || ps.length == 0) return;
    var p = hsRandom(ps);
    p.addPotionEffect(PotionEffectType_GLOWING, 6, 0, false);
    var id = TelegraphAPI.circleAt(npc, p.getX(), p.getY(), p.getZ(), 1.2, 120, HS.RED);
    try { TelegraphAPI.follow(id, p); } catch (err) {}
    hsPut(boar, "mark_uuid", String(p.getUUID()));
    hsPut(boar, "mark_until", hsNow(npc) + 120);
    for (var i = 0; i < ps.length; i++) {
        b1Tell(ps[i], "Грул метит " + p.getName() + " — кабан пойдёт на него!");
    }
    b1Tell(p, "Встань у целого кола — иначе ноги не слушаются!");
}

function gStake(npc) {
    if (b1WGet(npc, "hs_b1_locked", "0") == "1") return;
    if (b1StakeCount(npc) >= b1NestLimit(npc)) return;
    if (hsGet(npc, "b1_arm", "0") == "1") return;
    var index = b1FreeIndex(npc);
    if (index < 0) return;
    var s = b1StakeCenter(npc, index);
    TelegraphAPI.circleAt(npc, s[0], s[1], s[2], 1.8, 60, HS.GOLD);
    hsPut(npc, "b1_nest", index);
    hsPut(npc, "b1_arm", 1);
    hsPut(npc, "b1_arm_dmg", 0);
    hsPut(npc, "b1_arm_cancel", 0);
    npc.getTimers().forceStart(B1_T_STAKE, 60, false);
    b1Say(npc, "Грул вбивает новый кол!");
}

function gStakePlace(npc) {
    hsPut(npc, "b1_arm", 0);
    if (!npc.isAlive()) return;
    if (hsGet(npc, "b1_arm_cancel", "0") == "1") {
        b1Say(npc, "Кол не вбит!");
        return;
    }
    if (b1WGet(npc, "hs_b1_locked", "0") == "1") return;
    if (b1StakeCount(npc) >= b1NestLimit(npc)) return;
    var index = hsNum(npc, "b1_nest");
    if (index < 0 || index > 7) return;
    if (b1Mask(npc).charAt(index) == "1") return;
    b1PutStake(npc, index);
}

function gLasso(npc, ps) {
    if (hsGet(npc, "b1_mounted", "0") == "1") return;
    if (npc.getTimers().has(B1_T_LASSO)) return;
    if (hsNow(npc) < hsNum(npc, "b1_lasso_cd")) return;
    if (ps == null || ps.length == 0) return;
    var far = null;
    var best = 12;
    for (var i = 0; i < ps.length; i++) {
        var dist = hsFlat(npc, ps[i].getX(), ps[i].getZ());
        if (dist > best) {
            best = dist;
            far = ps[i];
        }
    }
    if (far == null) return;
    var yaw = hsYawTo(npc, far);
    TelegraphAPI.line(npc, npc.getX(), npc.getY(), npc.getZ(), yaw, best, 1.2, 20, HS.GOLD);
    hsPut(npc, "b1_lasso_id", String(far.getUUID()));
    hsPut(npc, "b1_lasso", "windup");
    hsPut(npc, "b1_lasso_cut", 0);
    hsPut(npc, "b1_lasso_cd", hsNow(npc) + 240);
    npc.getTimers().forceStart(B1_T_LASSO, 20, false);
}

function gExtraLasso(npc, ps) {
    if (b1Phase(npc) != 3) return;
    if (hsGet(npc, "b1_mounted", "0") == "1") return;
    var now = hsNow(npc);
    if (hsNum(npc, "b1_lasso_cd") <= 0) {
        hsPut(npc, "b1_lasso_cd", now + 240);
        return;
    }
    if (now < hsNum(npc, "b1_lasso_cd")) return;
    gLasso(npc, ps);
}

function gBusy(npc) {
    if (AbilityAPI.isBusy(npc) || b1RoarBlocks(npc)) return true;
    if (npc.getTimers().has(B1_T_LASSO) || npc.getTimers().has(B1_T_AXE) || npc.getTimers().has(B1_T_ROAR)) return true;
    if (npc.getTimers().has(B1_T_TRIP) || hsGet(npc, "b1_leap", "") != "") return true;
    return false;
}

function gFarWatch(npc, ps) {
    if (hsGet(npc, "b1_mounted", "0") == "1" || hsGet(npc, "b1_haul", "0") == "1") return false;
    if (ps == null || ps.length == 0) return false;
    var far = null;
    var best = 12;
    var i;
    for (i = 0; i < ps.length; i++) {
        var dist = hsFlat(npc, ps[i].getX(), ps[i].getZ());
        if (dist > best) {
            best = dist;
            far = ps[i];
        }
    }
    if (far == null) {
        hsPut(npc, "b1_far_since", 0);
        return false;
    }
    var id = String(far.getUUID());
    var now = hsNow(npc);
    if (hsGet(npc, "b1_far_id", "") != id) {
        hsPut(npc, "b1_far_id", id);
        hsPut(npc, "b1_far_since", now);
        return false;
    }
    if (now - hsNum(npc, "b1_far_since") < 60) return false;
    if (gBusy(npc) || now < hsNum(npc, "b1_lasso_cd")) return false;
    gLasso(npc, ps);
    return hsGet(npc, "b1_lasso", "") == "windup";
}

function gPress(npc, ps) {
    if (gBusy(npc)) return;
    if (hsGet(npc, "b1_mounted", "0") == "1") return;
    if (gGapLeap(npc, ps)) return;
    gAxe(npc, ps);
}

function gGapLeap(npc, ps) {
    if (ps == null || ps.length == 0) return false;
    var now = hsNow(npc);
    if (now < hsNum(npc, "b1_gap_at")) return false;
    var near = ps[0];
    var best = hsFlat(npc, near.getX(), near.getZ());
    var i;
    for (i = 1; i < ps.length; i++) {
        var dist = hsFlat(npc, ps[i].getX(), ps[i].getZ());
        if (dist < best) {
            best = dist;
            near = ps[i];
        }
    }
    if (best <= 10) return false;
    if (AbilityAPI.isBusy(npc)) return false;
    TelegraphAPI.circleAt(npc, near.getX(), near.getY(), near.getZ(), 3, 24, 0xC0FF0000);
    if (!hsStart(npc, "jump_slam", near, AbilityAPI.params(
            "chargeTicks", 8,
            "activeTicks", 14,
            "landRadius", 3,
            "damage", 0,
            "arcHeight", 3.2,
            "maxRange", 40,
            "knockback", 0,
            "knockbackY", 0,
            "telegraph", 0))) return false;
    hsPut(npc, "b1_leap", "gap");
    hsPut(npc, "b1_leap_from", now);
    hsPut(npc, "b1_gap_at", now + 200);
    npc.getTimers().forceStart(B1_T_LEAP, 24, false);
    return true;
}

function gAxe(npc, ps) {
    var now = hsNow(npc);
    var since = hsNum(npc, "b1_melee_at");
    if (!(since > 0)) {
        hsPut(npc, "b1_melee_at", now);
        return false;
    }
    if (now - since < 80) return false;
    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) target = gFarthest(npc, ps);
    if (target == null) return false;
    var yaw = hsYawTo(npc, target);
    hsPut(npc, "b1_axe_x", npc.getX());
    hsPut(npc, "b1_axe_z", npc.getZ());
    hsPut(npc, "b1_axe_yaw", yaw);
    TelegraphAPI.line(npc, npc.getX(), npc.getY(), npc.getZ(), yaw, 16, 2.4, 20, 0xC0FF0000);
    npc.getAi().setWalkingSpeed(0);
    npc.getAi().setRetaliateType(3);
    Combat.stopNavigation(npc);
    npc.getTimers().forceStart(B1_T_AXE, 20, false);
    hsPut(npc, "b1_melee_at", now);
    b1Say(npc, "Грул бросает топор!");
    return true;
}

function gAxeHit(npc) {
    if (!npc.isAlive()) return;
    var x = hsNum(npc, "b1_axe_x");
    var z = hsNum(npc, "b1_axe_z");
    var yaw = hsNum(npc, "b1_axe_yaw");
    var ps = hsPlayersAt(npc, x, npc.getY(), z, 18);
    for (var i = 0; i < ps.length; i++) {
        if (!hsOnLine(ps[i], x, z, yaw, 16, 1.2)) continue;
        hsStrike(ps[i], 0.15);
        ps[i].addPotionEffect(PotionEffectType_SLOWNESS, 3, 1, false);
    }
    b1Release(npc);
}

function gStartLeap(npc, x, z, mode) {
    if (hsGet(npc, "b1_leap", "") != "") return false;
    var spot = gClampArena(npc, x, z);
    hsPut(npc, "b1_leap", mode);
    hsPut(npc, "b1_leap_x", spot[0]);
    hsPut(npc, "b1_leap_z", spot[1]);
    hsPut(npc, "b1_leap_sx", npc.getX());
    hsPut(npc, "b1_leap_sy", npc.getY());
    hsPut(npc, "b1_leap_sz", npc.getZ());
    hsPut(npc, "b1_leap_from", hsNow(npc));
    var color = mode == "roar" ? HS.PURPLE : HS.RED;
    var radius = mode == "roar" ? 2 : 3;
    TelegraphAPI.circleAt(npc, spot[0], npc.getY(), spot[1], radius, 24, color);
    npc.getAi().setWalkingSpeed(0);
    npc.getAi().setRetaliateType(3);
    Combat.stopNavigation(npc);
    npc.getTimers().forceStart(B1_T_LEAP, 1, true);
    return true;
}

function gLeapStep(npc) {
    if (hsGet(npc, "b1_leap", "") != "roar") return;
    if (!npc.isAlive() || hsGet(npc, "stun", "0") == "1") {
        gLeapEnd(npc, false);
        return;
    }
    var age = hsNow(npc) - hsNum(npc, "b1_leap_from");
    var dur = 24;
    var t = age / dur;
    if (t > 1) t = 1;
    var x = hsNum(npc, "b1_leap_sx") + (hsNum(npc, "b1_leap_x") - hsNum(npc, "b1_leap_sx")) * t;
    var z = hsNum(npc, "b1_leap_sz") + (hsNum(npc, "b1_leap_z") - hsNum(npc, "b1_leap_sz")) * t;
    var y = hsNum(npc, "b1_leap_sy") + Math.sin(t * Math.PI) * 3.2;
    npc.setPosition(x, y, z);
    Combat.stopNavigation(npc);
    npc.getAi().setWalkingSpeed(0);
    npc.getAi().setRetaliateType(3);
    if (age < dur) return;
    gLeapEnd(npc, true);
}

function gGapLand(npc) {
    if (hsGet(npc, "b1_leap", "") != "gap") return;
    hsPut(npc, "b1_leap", "");
    npc.getTimers().stop(B1_T_LEAP);
    if (!npc.isAlive()) return;
    var ps = hsPlayersAt(npc, npc.getX(), npc.getY(), npc.getZ(), 3);
    for (var i = 0; i < ps.length; i++) hsStrike(ps[i], 0.15);
    b1Release(npc);
}

function gLeapEnd(npc, landed) {
    npc.getTimers().stop(B1_T_LEAP);
    var mode = hsGet(npc, "b1_leap", "");
    hsPut(npc, "b1_leap", "");
    if (!landed || !npc.isAlive()) {
        b1Release(npc);
        return;
    }
    npc.setPosition(hsNum(npc, "b1_leap_x"), hsNum(npc, "b1_leap_sy"), hsNum(npc, "b1_leap_z"));
    if (mode == "roar") gRoarNow(npc);
    else {
        var ps = hsPlayersAt(npc, npc.getX(), npc.getY(), npc.getZ(), 3);
        for (var i = 0; i < ps.length; i++) hsStrike(ps[i], 0.15);
        b1Release(npc);
    }
}

function gTrip(npc, ps) {
    if (hsGet(npc, "b1_mounted", "0") == "1" || hsGet(npc, "b1_haul", "0") == "1") return;
    if (hsGet(npc, "b1_mounting", "0") == "1") return;
    if (AbilityAPI.isBusy(npc)) return;
    if (npc.getTimers().has(B1_T_TRIP) || npc.getTimers().has(B1_T_ROAR)) return;
    if (npc.getTimers().has(B1_T_LASSO) || npc.getTimers().has(B1_T_AXE)) return;
    if (hsGet(npc, "b1_leap", "") != "") return;
    if (ps == null || ps.length == 0) return;
    var now = hsNow(npc);
    var phase = b1Phase(npc);
    var cd = phase == 3 ? 200 : 240;
    if (now < hsNum(npc, "b1_trip_at")) return;
    var victim = null;
    var best = 8.1;
    for (var i = 0; i < ps.length; i++) {
        var dist = hsFlat(npc, ps[i].getX(), ps[i].getZ());
        if (dist <= 8 && dist < best) {
            best = dist;
            victim = ps[i];
        }
    }
    if (victim == null) return;
    var reach = best;
    if (reach < 7) reach = 7;
    if (reach > 8) reach = 8;
    hsPut(npc, "b1_trip_at", now + cd);
    var face = hsYawTo(npc, victim);
    hsPut(npc, "b1_trip_yaw", face);
    hsPut(npc, "b1_trip_x", npc.getX());
    hsPut(npc, "b1_trip_z", npc.getZ());
    hsPut(npc, "b1_trip_len", reach);
    hsPut(npc, "b1_trip_live", 1);
    hsPut(npc, "b1_trip_until", now + 25);
    TelegraphAPI.cone(npc, npc.getX(), npc.getY(), npc.getZ(), face, reach, 45, 25, HS.RED);
    if (hsGet(npc, "b1_mounted", "0") != "1") {
        npc.getAi().setWalkingSpeed(0);
        npc.getAi().setRetaliateType(3);
        Combat.stopNavigation(npc);
    }
    npc.getTimers().forceStart(B1_T_TRIP, 25, false);
}

function gTripHold(npc) {
    if (hsGet(npc, "b1_trip_live", "0") != "1") return;
    if (hsNow(npc) < hsNum(npc, "b1_trip_until")) {
        if (hsGet(npc, "b1_mounted", "0") != "1") {
            npc.getAi().setWalkingSpeed(0);
            npc.getAi().setRetaliateType(3);
            Combat.stopNavigation(npc);
        }
        return;
    }
    gTripHit(npc);
}

function gTripHit(npc) {
    if (!npc.isAlive()) return;
    if (hsGet(npc, "b1_trip_live", "0") != "1") return;
    hsPut(npc, "b1_trip_live", 0);
    if (hsGet(npc, "b1_mounted", "0") == "1" || hsGet(npc, "b1_haul", "0") == "1") {
        b1Release(npc);
        return;
    }
    var yaw = hsNum(npc, "b1_trip_yaw");
    var x = hsNum(npc, "b1_trip_x");
    var z = hsNum(npc, "b1_trip_z");
    var reach = hsNum(npc, "b1_trip_len");
    if (!(reach > 0)) reach = 7;
    var ps = hsPlayersAt(npc, x, npc.getY(), z, reach + 1.5);
    for (var i = 0; i < ps.length; i++) {
        var p = ps[i];
        if (hsFlat(p, x, z) > reach + 0.6) continue;
        if (!Combat.isInFrontCone(x, z, yaw, p, 45)) continue;
        hsStrike(p, 0.10);
        p.addPotionEffect(PotionEffectType_SLOWNESS, 1, 3, false);
    }
    b1Release(npc);
}

function gSqueal(npc, ps) {
    if (b1Phase(npc) != 3) return;
    var now = hsNow(npc);
    if (hsNum(npc, "b1_squeal") <= 0) {
        hsPut(npc, "b1_squeal", now + 600);
        return;
    }
    if (now < hsNum(npc, "b1_squeal")) return;
    hsPut(npc, "b1_squeal", now + 600);
    var list = hsFind(npc, B1_RAIDER);
    if (list.length == 0 || ps == null || ps.length == 0) return;
    b1Say(npc, "Кабаний клич!");
    for (var i = 0; i < list.length; i++) {
        var raider = list[i];
        if (AbilityAPI.isBusy(raider)) continue;
        var nearest = ps[0];
        var best = hsFlat(raider, nearest.getX(), nearest.getZ());
        for (var j = 1; j < ps.length; j++) {
            var dist = hsFlat(raider, ps[j].getX(), ps[j].getZ());
            if (dist < best) {
                best = dist;
                nearest = ps[j];
            }
        }
        hsStart(raider, "dash", nearest, AbilityAPI.params(
            "chargeTicks", 20,
            "activeTicks", 8,
            "distance", 10,
            "damage", 4,
            "hitRadius", 1.2
        ));
    }
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
    if (e.id == B1_T_ROAR) {
        gRoarHit(npc);
        return;
    }
    if (e.id == B1_T_STAKE) {
        gStakePlace(npc);
        return;
    }
    if (e.id == B1_T_TRIP) {
        gTripHit(npc);
        return;
    }
    if (e.id == B1_T_SLAM) {
        b1SlamLand(npc);
        return;
    }
    if (e.id == B1_T_LEAP) {
        if (hsGet(npc, "b1_leap", "") == "gap") gGapLand(npc);
        else gLeapStep(npc);
        return;
    }
    if (e.id == B1_T_AXE) {
        gAxeHit(npc);
        return;
    }
    if (e.id == B1_T_FEED) {
        if (hsGet(npc, "b1_lasso_cut", "0") != "1") {
            b1Feed(npc, b1FindPlayer(npc, hsGet(npc, "b1_lasso_id", "")));
        }
        hsPut(npc, "b1_lasso", "");
        return;
    }
    if (e.id != B1_T_LASSO) return;
    if (hsGet(npc, "b1_lasso", "") != "windup") return;
    var pulled = b1FindPlayer(npc, hsGet(npc, "b1_lasso_id", ""));
    if (pulled == null) {
        hsPut(npc, "b1_lasso", "");
        return;
    }
    hsPut(npc, "b1_lasso", "pull");
    hsPut(npc, "b1_lasso_cut", 0);
    if (!hsStart(npc, "b1_pull", pulled, AbilityAPI.params(
            "activeTicks", 40, "distance", 0.15, "telegraph", 0))) {
        hsPut(npc, "b1_lasso", "");
        return;
    }
    npc.getTimers().forceStart(B1_T_FEED, 40, false);
}

function meleeAttack(e) {
    if (hsGet(e.npc, "b1_mounted", "0") == "1") {
        e.setCanceled(true);
        return;
    }
    if (e.target != null && e.target.typeOf(1)) {
        hsPut(e.npc, "b1_melee_at", hsNow(e.npc));
        e.target.addPotionEffect(PotionEffectType_SLOWNESS, 3, 1, false);
    }
}

function damaged(e) {
    var npc = e.npc;
    b1Cap(e, 1);
    if (hsNow(npc) < hsNum(npc, "immune_until")) return;
    b1RitualHurt(e);
    if (hsGet(npc, "b1_lasso", "") == "pull") hsPut(npc, "b1_lasso_cut", 1);
    if (hsGet(npc, "b1_arm", "0") == "1" && b1FightN(npc) >= 2) {
        var sum = hsNum(npc, "b1_arm_dmg") + Number(e.damage);
        hsPut(npc, "b1_arm_dmg", sum);
        var max = npc.getStats().getMaxHealth();
        if (max > 0 && sum >= max * 0.05) hsPut(npc, "b1_arm_cancel", 1);
    }
}

function died(e) {
    b1Died(e);
}
