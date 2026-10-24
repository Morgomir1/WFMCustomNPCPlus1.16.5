// Грул Кабанья Рука (Б1). В контейнере третьим файлом: hs_core.js, hs_b1.js, этот файл.
// Цикл 45 с: рёв, метка, колья, аркан. Фаза 2 — прыжок на кабана. Фаза 3 — клич и ритуал.
HS.BASE_HP = 220;
HS.ARENA = { shape: "rect", hw: 22, hd: 19 };
HS.ENRAGE_SEC = 360;
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
    b1RefreshLink(npc);
    b1MudTick(npc, ps);
    b1HornTick(npc);
    var ritual = b1WidowTick(npc);
    b1RecoverTick(npc);
    if (!ritual) b1MountTick(npc);
    if (ritual || hsGet(npc, "b1_ritual", "0") == "1") return;
    if (hsGet(npc, "stun", "0") == "1" || hsGet(npc, "b1_mounting", "0") == "1") return;
    gCycle(npc, ps);
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
    var now = hsNow(npc);
    b1WPut(npc, "hs_b1_roar_until", now + 60);
    var from = hsNum(npc, "b1_cycle_from");
    var loop = hsNum(npc, "b1_cycle_loop");
    b1WPut(npc, "hs_b1_next_roar", from + (loop + 1) * 900);
    if (hsGet(npc, "b1_mounted", "0") != "1") {
        npc.getAi().setWalkingSpeed(0);
        npc.getAi().setRetaliateType(3);
    }
    TelegraphAPI.ring(npc, npc.getX(), npc.getY(), npc.getZ(), 18, 1.5, 60, HS.PURPLE);
    npc.getWorld().playSoundAt(npc.getPos(), "minecraft:entity.ravager.roar", 3.0, 0.6);
    npc.getTimers().forceStart(B1_T_ROAR, 60, false);
    npc.say("§5Грул набирает рёв Стада!");
}

function gRoarHit(npc) {
    if (!npc.isAlive()) return;
    var ps = hsPlayers(npc);
    for (var i = 0; i < ps.length; i++) {
        var p = ps[i];
        if (b1InStakeShadow(p, npc)) continue;
        p.clearPotionEffects();
        p.addPotionEffect(PotionEffectType_SLOWNESS, 2, 3, false);
        hsStrike(p, 0.10);
        p.message("§5Рёв Стада срывает с тебя все чары.");
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
    if (room >= 1) w.spawnClone(h[0] + 6, h[1], h[2], HS.TAB, B1_RAIDER);
    if (room >= 2) w.spawnClone(h[0] - 6, h[1], h[2], HS.TAB, B1_RAIDER);
    b1NoteRaiders(npc);
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
        ps[i].message("§6Грул метит §f" + p.getName() + "§6 — кабан пойдёт на него!");
    }
    p.message("§eВстань перед целым колом!");
}

function gStake(npc) {
    if (b1WGet(npc, "hs_b1_locked", "0") == "1") return;
    if (b1StakeCount(npc) >= b1NestLimit(npc)) return;
    if (hsGet(npc, "b1_arm", "0") == "1") return;
    var index = b1FreeIndex(npc);
    if (index < 0) return;
    var s = hsXYZ(npc, B1_NESTS[index]);
    TelegraphAPI.circleAt(npc, s[0] + 0.5, s[1], s[2] + 0.5, 1.4, 60, HS.GOLD);
    hsPut(npc, "b1_nest", index);
    hsPut(npc, "b1_arm", 1);
    hsPut(npc, "b1_arm_dmg", 0);
    hsPut(npc, "b1_arm_cancel", 0);
    npc.getTimers().forceStart(B1_T_STAKE, 60, false);
    npc.say("§6Грул вбивает новый кол!");
}

function gStakePlace(npc) {
    hsPut(npc, "b1_arm", 0);
    if (!npc.isAlive()) return;
    if (hsGet(npc, "b1_arm_cancel", "0") == "1") {
        npc.say("§eКол не вбит!");
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
    hsPut(npc, "b1_lasso_cd", hsNow(npc) + 300);
    npc.getTimers().forceStart(B1_T_LASSO, 20, false);
}

function gLassoPull(npc) {
    if (hsGet(npc, "b1_lasso", "") != "pull") {
        npc.getTimers().stop(B1_T_LASSO);
        return;
    }
    if (hsGet(npc, "b1_lasso_cut", "0") == "1" || hsGet(npc, "stun", "0") == "1") {
        npc.getTimers().stop(B1_T_LASSO);
        hsPut(npc, "b1_lasso", "");
        return;
    }
    var p = b1FindPlayer(npc, hsGet(npc, "b1_lasso_id", ""));
    if (p == null) {
        npc.getTimers().stop(B1_T_LASSO);
        hsPut(npc, "b1_lasso", "");
        return;
    }
    var dx = npc.getX() - p.getX();
    var dz = npc.getZ() - p.getZ();
    var len = Math.sqrt(dx * dx + dz * dz);
    var step = hsNum(npc, "b1_lasso_step") + 1;
    hsPut(npc, "b1_lasso_step", step);
    if (len < 1.5 || step >= 20) {
        npc.getTimers().stop(B1_T_LASSO);
        hsPut(npc, "b1_lasso", "");
        return;
    }
    var move = 0.3;
    if (move > len) move = len;
    p.setPosition(p.getX() + dx / len * move, p.getY(), p.getZ() + dz / len * move);
}

function gExtraLasso(npc, ps) {
    if (b1Phase(npc) != 3) return;
    if (hsGet(npc, "b1_mounted", "0") == "1") return;
    var now = hsNow(npc);
    if (hsNum(npc, "b1_lasso_cd") <= 0) {
        hsPut(npc, "b1_lasso_cd", now + 300);
        return;
    }
    if (now < hsNum(npc, "b1_lasso_cd")) return;
    gLasso(npc, ps);
}

function gTrip(npc, ps) {
    if (hsGet(npc, "b1_mounted", "0") == "1") return;
    if (hsGet(npc, "b1_mounting", "0") == "1") return;
    if (npc.getTimers().has(B1_T_TRIP) || npc.getTimers().has(B1_T_ROAR)) return;
    if (ps == null || ps.length == 0) return;
    var now = hsNow(npc);
    var phase = b1Phase(npc);
    var cd = phase == 3 ? 200 : 240;
    if (now < hsNum(npc, "b1_trip_at")) return;
    var victim = null;
    if (phase != 3) {
        var boar = b1Other(npc);
        if (boar == null || !AbilityAPI.isBusy(boar)) return;
        var sx = hsNum(boar, "ram_x");
        var sz = hsNum(boar, "ram_z");
        var yaw = hsNum(boar, "ram_yaw");
        var len = hsNum(boar, "ram_len");
        if (!(len > 0)) len = 14;
        for (var i = 0; i < ps.length; i++) {
            if (!hsOnLine(ps[i], sx, sz, yaw, len, 1.8)) continue;
            if (hsFlat(npc, ps[i].getX(), ps[i].getZ()) > 6) continue;
            victim = ps[i];
            break;
        }
    } else {
        var best = 6.1;
        for (var j = 0; j < ps.length; j++) {
            var dist = hsFlat(npc, ps[j].getX(), ps[j].getZ());
            if (dist <= 6 && dist < best) {
                best = dist;
                victim = ps[j];
            }
        }
    }
    if (victim == null) return;
    hsPut(npc, "b1_trip_at", now + cd);
    var face = hsYawTo(npc, victim);
    hsPut(npc, "b1_trip_yaw", face);
    hsPut(npc, "b1_trip_x", npc.getX());
    hsPut(npc, "b1_trip_z", npc.getZ());
    TelegraphAPI.cone(npc, npc.getX(), npc.getY(), npc.getZ(), face, 5, 45, 25, HS.GOLD);
    if (hsGet(npc, "b1_mounted", "0") != "1") {
        npc.getAi().setWalkingSpeed(0);
        npc.getAi().setRetaliateType(3);
    }
    npc.getTimers().forceStart(B1_T_TRIP, 25, false);
}

function gTripHit(npc) {
    if (!npc.isAlive()) return;
    var ps = hsPlayersAt(npc, npc.getX(), npc.getY(), npc.getZ(), 6);
    var yaw = hsNum(npc, "b1_trip_yaw");
    var x = hsNum(npc, "b1_trip_x");
    var z = hsNum(npc, "b1_trip_z");
    for (var i = 0; i < ps.length; i++) {
        if (!b1InCone(ps[i], x, z, yaw, 5, 45)) continue;
        hsStrike(ps[i], 0.10);
        ps[i].addPotionEffect(PotionEffectType_SLOWNESS, 1, 3, false);
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
    npc.say("§4Кабаний клич!");
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
    if (e.id == B1_T_MOUNT) {
        b1HoldMount(npc);
        return;
    }
    if (e.id != B1_T_LASSO) return;
    if (hsGet(npc, "b1_lasso", "") == "windup") {
        hsPut(npc, "b1_lasso", "pull");
        hsPut(npc, "b1_lasso_step", 0);
        npc.getTimers().forceStart(B1_T_LASSO, 2, true);
        return;
    }
    gLassoPull(npc);
}

function meleeAttack(e) {
    if (hsGet(e.npc, "b1_mounted", "0") == "1") {
        e.setCanceled(true);
        return;
    }
    if (e.target != null && e.target.typeOf(1)) {
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
    b1OnDied(e.npc);
}
