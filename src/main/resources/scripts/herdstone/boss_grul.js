// Грул Кабанья Рука (Б1). В контейнере третьим файлом: hs_core.js, hs_b1.js, этот файл.
// Цикл 45 с: рёв и колья. Фаза 2 — прыжок на кабана. Фаза 3 — клич и ритуал.
// Пока кабан жив — кит + удар по земле. Без кабана (ф3) — мили + тот же удар.
// Сети кидают унгоры-рейдеры (mob_raider.js).
HS.BASE_HP = 220;
HS.ARENA = { shape: "rect", hw: 22, hd: 19 };
HS.ENRAGE_SEC = 240;
HS.DISPEL_SEC = 99999;
ORIGIN = [-373224, 101, -13530];
GATE = hsBox(12, 0, -11, 3, 2, 3);
SUMMONS = ["Унгор-рейдер"];

var B1_BEATS = [0, 300, 740];
var G_STOMP_R = 4.5;
var G_STOMP_CHARGE = 30;
var G_STOMP_CD = 100;
var G_STOMP_PCT = 0.35;
var G_STOMP_KB = 1.9;
var G_STOMP_KB_Y = 0.55;
var G_FLEE_SPEED = 8;

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
    hsPut(npc, "base_speed", 6);
    npc.getAi().setWalkingSpeed(6);
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
    hsPut(npc, "b1_stomp", 0);
    hsPut(npc, "b1_stomp_at", 0);
    b1ResetWorld(npc);
    gApplyAi(npc);
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    var ps = hsFight(npc);
    if (ps == null) return;
    b1Boot(npc, ps);
    b1ClearStun(npc);
    gSanity(npc);
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
    // На кабане — только колья; рёв/клич/прыжки выключены.
    if (hsGet(npc, "b1_mounted", "0") == "1") {
        gCycle(npc, ps);
        return;
    }
    if (hsGet(npc, "b1_stomp", "0") == "1") {
        Combat.holdInPlace(npc, hsNum(npc, "b1_stomp_x"), hsNum(npc, "b1_stomp_y"), hsNum(npc, "b1_stomp_z"));
        return;
    }
    gCycle(npc, ps);
    gStompTry(npc);
    gApplyAi(npc);
    gSqueal(npc, ps);
}

/** Без кабана (фаза 3) — дерётся. Пока Кровохряк жив — кит. */
function gShouldFight(npc) {
    return b1Other(npc) == null;
}

function gApplyAi(npc) {
    if (!npc.isAlive()) return;
    if (hsGet(npc, "stun", "0") == "1") return;
    if (hsGet(npc, "b1_mounted", "0") == "1") return;
    if (hsGet(npc, "b1_mounting", "0") == "1") return;
    if (hsGet(npc, "b1_stomp", "0") == "1") return;
    if (hsGet(npc, "b1_leap", "") != "") return;
    if (hsGet(npc, "b1_ritual", "0") == "1") return;
    if (gShouldFight(npc)) {
        var speed = hsNum(npc, "base_speed");
        if (!(speed > 0)) speed = 6;
        npc.getAi().setWalkingSpeed(speed);
        npc.getAi().setRetaliateType(0);
    } else {
        npc.getAi().setWalkingSpeed(G_FLEE_SPEED);
        npc.getAi().setRetaliateType(2);
    }
}

/** Сброс залипших флагов: mounting/leap/haul/arm и рассинхрон седла. */
function gSanity(npc) {
    var now = hsNow(npc);
    if (hsGet(npc, "b1_mounting", "0") == "1") {
        var mountedAt = hsNum(npc, "b1_mounting_at");
        if (!(mountedAt > 0)) hsPut(npc, "b1_mounting_at", now);
        else if (now - mountedAt > 80) {
            hsPut(npc, "b1_mounting", 0);
            hsPut(npc, "b1_mounting_at", 0);
            AbilityAPI.cancel(npc);
            hsPut(npc, "b1_mount_go", 1);
        }
    } else {
        hsPut(npc, "b1_mounting_at", 0);
    }
    var leap = hsGet(npc, "b1_leap", "");
    if (leap != "" && now - hsNum(npc, "b1_leap_from") > 40) {
        hsPut(npc, "b1_leap", "");
        AbilityAPI.cancel(npc);
        b1Release(npc);
    }
    if (hsGet(npc, "b1_haul", "0") == "1") {
        var haulAt = hsNum(npc, "b1_haul_at");
        if (!(haulAt > 0)) hsPut(npc, "b1_haul_at", now);
        else if (!AbilityAPI.isBusy(npc) || now - haulAt > 80) {
            hsPut(npc, "b1_haul", 0);
            hsPut(npc, "b1_haul_at", 0);
            AbilityAPI.cancel(npc);
        }
    } else {
        hsPut(npc, "b1_haul_at", 0);
    }
    if (hsGet(npc, "b1_arm", "0") == "1" && !npc.getTimers().has(B1_T_STAKE)) {
        hsPut(npc, "b1_arm", 0);
    }
    if (hsGet(npc, "b1_stomp", "0") == "1" && !npc.getTimers().has(B1_T_STOMP)) {
        hsPut(npc, "b1_stomp", 0);
        gApplyAi(npc);
    }
    if (hsGet(npc, "b1_mounted", "0") == "1" && AbilityAPI.isBusy(npc)) {
        var busyAt = hsNum(npc, "b1_busy_at");
        if (!(busyAt > 0)) hsPut(npc, "b1_busy_at", now);
        else if (now - busyAt > 50) {
            AbilityAPI.cancel(npc);
            hsPut(npc, "b1_busy_at", 0);
        }
    } else {
        hsPut(npc, "b1_busy_at", 0);
    }
    var riding = false;
    try { riding = HerdstoneMount.isRiding(npc); } catch (err) { riding = false; }
    if (hsGet(npc, "b1_mounted", "0") == "1" && !riding) {
        var boar = b1Other(npc);
        if (boar != null && boar.isAlive() && HerdstoneMount.mount(npc, boar, 1.6)) {
            hsPut(boar, "b1_mounted", 1);
        } else {
            hsPut(npc, "b1_mounted", 0);
            if (boar != null) hsPut(boar, "b1_mounted", 0);
            b1Release(npc);
        }
    } else if (hsGet(npc, "b1_mounted", "0") != "1" && riding) {
        try { HerdstoneMount.dismount(npc); } catch (err2) {}
    }
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
    if (idx >= B1_BEATS.length) return;
    var elapsed = now - from - loop * 900;
    if (elapsed < B1_BEATS[idx]) return;
    hsPut(npc, "b1_cycle_i", idx + 1);
    var mounted = hsGet(npc, "b1_mounted", "0") == "1";
    if (idx == 0) {
        if (!mounted) gRoar(npc);
    } else {
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
    if (hsGet(npc, "b1_mounted", "0") == "1") {
        b1InterruptDash(b1Other(npc));
    } else {
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
    var spot = gFindRaiderSpawn(npc, x, z, living);
    var spawned = 0;
    if (spot != null) {
        hsNoRespawn(w.spawnClone(spot[0], spot[1], spot[2], HS.TAB, B1_RAIDER));
        spawned = 1;
        living = hsFind(npc, B1_RAIDER);
    }
    if (room >= 2) {
        var side = gFindRaiderSpawn(npc, x + (-dz / len) * 3, z + (dx / len) * 3, living);
        if (side != null) {
            hsNoRespawn(w.spawnClone(side[0], side[1], side[2], HS.TAB, B1_RAIDER));
            spawned = spawned + 1;
        }
    }
    if (spawned > 0) b1NoteRaiders(npc);
}

/** Свободная клетка: пол solid, ноги и голова — воздух. Не рядом с живым рейдером. */
function gFindRaiderSpawn(npc, x, z, living) {
    var h = hsHome(npc);
    var y = h[1];
    var w = npc.getWorld();
    var offsets = [
        [0, 0], [2, 0], [-2, 0], [0, 2], [0, -2],
        [3, 2], [-3, 2], [3, -2], [-3, -2], [4, 0], [-4, 0], [0, 4], [0, -4]
    ];
    var i;
    for (i = 0; i < offsets.length; i++) {
        var clamped = gClampArena(npc, x + offsets[i][0], z + offsets[i][1]);
        if (gRaiderNear(living, clamped[0], clamped[1])) continue;
        if (!gCanStand(w, clamped[0], y, clamped[1])) continue;
        return [clamped[0], y, clamped[1]];
    }
    return null;
}

function gCanStand(world, x, y, z) {
    try {
        var bx = Math.floor(x);
        var by = Math.floor(y);
        var bz = Math.floor(z);
        if (!gBlockSolid(world.getBlock(bx, by - 1, bz))) return false;
        if (gBlockSolid(world.getBlock(bx, by, bz))) return false;
        if (gBlockSolid(world.getBlock(bx, by + 1, bz))) return false;
        return true;
    } catch (err) {
        return false;
    }
}

function gBlockSolid(block) {
    if (block == null) return false;
    var name = "";
    try { name = String(block.getName()); } catch (err) { return true; }
    if (name == "minecraft:air" || name == "minecraft:cave_air" || name == "minecraft:void_air") return false;
    if (name.indexOf("tall_grass") >= 0 || name.indexOf("fern") >= 0
            || name.indexOf("flower") >= 0 || name.indexOf("torch") >= 0
            || name.indexOf("carpet") >= 0 || name == "minecraft:snow"
            || name.indexOf("button") >= 0 || name.indexOf("pressure_plate") >= 0
            || name.indexOf("sign") >= 0 || name.indexOf("banner") >= 0
            || name.indexOf("rail") >= 0 || name.indexOf("sapling") >= 0
            || name.indexOf("mushroom") >= 0 || name.indexOf("vine") >= 0
            || name.indexOf("ladder") >= 0 || name.indexOf("web") >= 0
            || name.indexOf("fire") >= 0) return false;
    return true;
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

function gBusy(npc) {
    if (AbilityAPI.isBusy(npc) || b1RoarBlocks(npc)) return true;
    if (npc.getTimers().has(B1_T_ROAR) || npc.getTimers().has(B1_T_STOMP)) return true;
    if (hsGet(npc, "b1_leap", "") != "") return true;
    if (hsGet(npc, "b1_stomp", "0") == "1") return true;
    return false;
}

/** Удар по земле у ног: telegraph → большой % HP + радиальное откидывание. */
function gStompTry(npc) {
    if (gBusy(npc)) return;
    if (hsGet(npc, "b1_mounted", "0") == "1") return;
    if (hsGet(npc, "b1_arm", "0") == "1") return;
    var now = hsNow(npc);
    if (now < hsNum(npc, "b1_stomp_at")) return;
    var x = npc.getX();
    var y = npc.getY();
    var z = npc.getZ();
    hsPut(npc, "b1_stomp", 1);
    hsPut(npc, "b1_stomp_x", x);
    hsPut(npc, "b1_stomp_y", y);
    hsPut(npc, "b1_stomp_z", z);
    hsPut(npc, "b1_stomp_at", now + G_STOMP_CD);
    TelegraphAPI.circleAt(npc, x, y, z, G_STOMP_R, G_STOMP_CHARGE, HS.RED);
    npc.getAi().setWalkingSpeed(0);
    npc.getAi().setRetaliateType(3);
    Combat.stopNavigation(npc);
    Combat.holdInPlace(npc, x, y, z);
    npc.getTimers().forceStart(B1_T_STOMP, G_STOMP_CHARGE, false);
    npc.getWorld().playSoundAt(npc.getPos(), "minecraft:entity.ravager.roar", 1.4, 0.75);
    b1Say(npc, "Грул бьёт копытом о землю!");
}

function gStompHit(npc) {
    hsPut(npc, "b1_stomp", 0);
    if (!npc.isAlive()) return;
    var x = hsNum(npc, "b1_stomp_x");
    var y = hsNum(npc, "b1_stomp_y");
    var z = hsNum(npc, "b1_stomp_z");
    if (!(x != 0 || z != 0)) {
        x = npc.getX();
        y = npc.getY();
        z = npc.getZ();
    }
    var ps = hsPlayersAt(npc, x, y, z, G_STOMP_R);
    for (var i = 0; i < ps.length; i++) {
        var p = ps[i];
        hsStrike(p, G_STOMP_PCT);
        gKnock(p, x, z, G_STOMP_KB, G_STOMP_KB_Y);
    }
    npc.getWorld().playSoundAt(npc.getPos(), "minecraft:entity.generic.explode", 1.6, 0.7);
    gApplyAi(npc);
}

function gKnock(p, cx, cz, str, lift) {
    if (p == null || !p.isAlive()) return;
    var dx = p.getX() - cx;
    var dz = p.getZ() - cz;
    var len = Math.sqrt(dx * dx + dz * dz);
    if (len < 0.05) {
        dx = 1;
        dz = 0;
        len = 1;
    }
    try {
        p.setMotionX((dx / len) * str);
        p.setMotionY(lift);
        p.setMotionZ((dz / len) * str);
    } catch (err) {}
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
    if (e.id == B1_T_SLAM) {
        b1SlamLand(npc);
        return;
    }
    if (e.id == B1_T_STAKE) {
        gStakePlace(npc);
        return;
    }
    if (e.id == B1_T_STOMP) {
        gStompHit(npc);
        return;
    }
    // На седле рёв/прыжок не резолвим; колья — можно.
    if (hsGet(npc, "b1_mounted", "0") == "1") {
        if (e.id == B1_T_ROAR || e.id == B1_T_LEAP) {
            hsPut(npc, "b1_leap", "");
        }
        return;
    }
    if (e.id == B1_T_ROAR) {
        gRoarHit(npc);
        return;
    }
    if (e.id == B1_T_LEAP) {
        gLeapStep(npc);
    }
}

function meleeAttack(e) {
    var npc = e.npc;
    if (hsGet(npc, "b1_mounted", "0") == "1" || !gShouldFight(npc)) {
        e.setCanceled(true);
        return;
    }
    if (e.target != null && e.target.typeOf(1)) {
        hsPut(npc, "b1_melee_at", hsNow(npc));
        e.target.addPotionEffect(PotionEffectType_SLOWNESS, 3, 1, false);
    }
}

function damaged(e) {
    var npc = e.npc;
    b1Cap(e, 1);
    if (hsNow(npc) < hsNum(npc, "immune_until")) return;
    b1RitualHurt(e);
    if (hsGet(npc, "b1_arm", "0") == "1" && b1FightN(npc) >= 2) {
        var sum = hsNum(npc, "b1_arm_dmg") + Number(e.damage);
        hsPut(npc, "b1_arm_dmg", sum);
        var max = npc.getStats().getMaxHealth();
        if (max > 0 && sum >= max * 0.05) hsPut(npc, "b1_arm_cancel", 1);
    }
}

function died(e) {
    AbilityAPI.cancel(e.npc);
    b1Died(e);
}
