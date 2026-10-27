// Грул Кабанья Рука (Б1). В контейнере третьим файлом: hs_core.js, hs_b1.js, этот файл.
// Цикл 45 с: рёв и колья. Фаза 2 — прыжок на кабана. Фаза 3 — клич и ритуал.
// Удар по земле: удар у ног + прыжок на игроков (ф1 — ограничена дальность, ф3 один — без лимита).
// Во время рёва и ритуала стомп не кастует. Сети — унгоры-рейдеры (mob_raider.js).
HS.BASE_HP = 300;
HS.FIXED_SCALE = true;
HS.ARENA = { shape: "rect", hw: 22, hd: 19 };
HS.ENRAGE_SEC = 240;
HS.DISPEL_SEC = 99999;
ORIGIN = [-373224, 101, -13530];
GATE = hsBox(12, 0, -11, 3, 2, 3);
SUMMONS = ["Унгор-рейдер"];

// --- НАСТРОЙКИ СПОСОБНОСТЕЙ ---
var G_BASE_SPEED = 6;
var G_FLEE_SPEED = 8;
// Цикл рёв/колья (тики): биты внутри петли CYCLE_LEN
var B1_BEATS = [0, 300, 740];
var G_CYCLE_LEN = 900;
// Рёв Стада
var G_ROAR_TICKS = 60;
var G_ROAR_RING_W = 1.5;
var G_ROAR_SOUND_VOL = 3.0;
var G_ROAR_SOUND_PITCH = 0.6;
var G_ROAR_SLOW_SEC = 2;
var G_ROAR_SLOW_AMP = 3;
var G_ROAR_PCT = 0.10;
var G_ROAR_LEAP_DIST = 1.5;    // если дальше центра — прыжок, потом рёв
// Призыв рейдеров
var G_SUMMON_MAX = 4;
var G_SUMMON_COUNT = 1;
var G_SUMMON_BACK = 5;
var G_SUMMON_SIDE = 3;
var G_SUMMON_NEAR = 9;         // sq-дистанция «рядом с живым»
var G_ARENA_PAD = 2;
// Колья
var G_STAKE_R = 1.8;
var G_STAKE_CHARGE = 60;
// Удар по земле + прыжок
var G_STOMP_R = 4.5;
var G_STOMP_CHARGE = 30;
var G_STOMP_CD = 100;
var G_STOMP_PCT = 0.35;
var G_STOMP_KB = 1.9;
var G_STOMP_KB_Y = 0.55;
var G_STOMP_SOUND_VOL = 1.4;
var G_STOMP_SOUND_PITCH = 0.75;
var G_STOMP_HIT_VOL = 1.6;
var G_STOMP_HIT_PITCH = 0.7;
var G_STOMP_LEAP_MAX_P1 = 10;  // ф1: макс. дальность прыжка на игрока (ф3 — без лимита)
// Прыжок к центру перед рёвом
var G_LEAP_TICKS = 24;
var G_LEAP_ARC = 3.2;
var G_LEAP_R_ROAR = 2;
var G_LEAP_R_OTHER = 3;
var G_LEAP_LAND_PCT = 0.15;
var G_LEAP_LAND_R = 3;
// Кабаний клич (фаза 3) — dash рейдеров
var G_SQUEAL_CD = 600;
var G_SQUEAL_CHARGE = 20;
var G_SQUEAL_ACTIVE = 8;
var G_SQUEAL_DIST = 10;
var G_SQUEAL_DMG = 4;
var G_SQUEAL_HIT_R = 1.2;
// Sanity / mount
var G_MOUNT_H = 1.6;
var G_SANITY_MOUNT = 80;
var G_SANITY_LEAP = 40;
var G_SANITY_HAUL = 80;
var G_SANITY_BUSY = 50;
// Мили (без кабана)
var G_MELEE_SLOW_SEC = 3;
var G_MELEE_SLOW_AMP = 1;

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
    hsPut(npc, "base_speed", G_BASE_SPEED);
    npc.getAi().setWalkingSpeed(G_BASE_SPEED);
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
    hsPut(npc, "b1_stomp_leap", 0);
    b1ResetWorld(npc);
    gApplyAi(npc);
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    var ps = hsFight(npc);
    if (ps == null) return;
    b1Boot(npc);
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
    if (leap != "") {
        // stomp — jump_slam (AbilityAPI). roar — ручная дуга к центру.
        if (leap == "roar") gLeapStep(npc);
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
    if (npc.getTimers().has(B1_T_ROAR)) {
        Combat.holdInPlace(npc, hsNum(npc, "b1_roar_x"), hsNum(npc, "b1_roar_y"), hsNum(npc, "b1_roar_z"));
        npc.getAi().setWalkingSpeed(0);
        npc.getAi().setRetaliateType(3);
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
    if (npc.getTimers().has(B1_T_ROAR)) return;
    if (gShouldFight(npc)) {
        var speed = hsNum(npc, "base_speed");
        if (!(speed > 0)) speed = G_BASE_SPEED;
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
        else if (now - mountedAt > G_SANITY_MOUNT) {
            hsPut(npc, "b1_mounting", 0);
            hsPut(npc, "b1_mounting_at", 0);
            AbilityAPI.cancel(npc);
            hsPut(npc, "b1_mount_go", 1);
        }
    } else {
        hsPut(npc, "b1_mounting_at", 0);
    }
    var leap = hsGet(npc, "b1_leap", "");
    if (leap != "" && now - hsNum(npc, "b1_leap_from") > G_SANITY_LEAP) {
        hsPut(npc, "b1_leap", "");
        AbilityAPI.cancel(npc);
        b1Release(npc);
    }
    if (hsGet(npc, "b1_haul", "0") == "1") {
        var haulAt = hsNum(npc, "b1_haul_at");
        if (!(haulAt > 0)) hsPut(npc, "b1_haul_at", now);
        else if (!AbilityAPI.isBusy(npc) || now - haulAt > G_SANITY_HAUL) {
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
        hsPut(npc, "b1_stomp_leap", 0);
        gApplyAi(npc);
    }
    if (hsGet(npc, "b1_mounted", "0") == "1" && AbilityAPI.isBusy(npc)) {
        var busyAt = hsNum(npc, "b1_busy_at");
        if (!(busyAt > 0)) hsPut(npc, "b1_busy_at", now);
        else if (now - busyAt > G_SANITY_BUSY) {
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
        if (boar != null && boar.isAlive() && HerdstoneMount.mount(npc, boar, G_MOUNT_H)) {
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
    var loop = Math.floor((now - from) / G_CYCLE_LEN);
    if (loop < 0) loop = 0;
    if (loop != hsNum(npc, "b1_cycle_loop")) {
        hsPut(npc, "b1_cycle_loop", loop);
        hsPut(npc, "b1_cycle_i", 0);
    }
    var idx = hsNum(npc, "b1_cycle_i");
    if (idx >= B1_BEATS.length) return;
    var elapsed = now - from - loop * G_CYCLE_LEN;
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
        if (hsFlat(npc, c[0], c[2]) > G_ROAR_LEAP_DIST && gStartLeap(npc, c[0], c[2], "roar")) return;
    }
    gRoarNow(npc);
}

function gRoarNow(npc) {
    var now = hsNow(npc);
    b1WPut(npc, "hs_b1_roar_until", now + G_ROAR_TICKS);
    var from = hsNum(npc, "b1_cycle_from");
    var loop = hsNum(npc, "b1_cycle_loop");
    b1WPut(npc, "hs_b1_next_roar", from + (loop + 1) * G_CYCLE_LEN);
    hsPut(npc, "b1_roar_x", npc.getX());
    hsPut(npc, "b1_roar_y", npc.getY());
    hsPut(npc, "b1_roar_z", npc.getZ());
    if (hsGet(npc, "b1_mounted", "0") == "1") {
        b1InterruptDash(b1Other(npc));
    } else {
        npc.getAi().setWalkingSpeed(0);
        npc.getAi().setRetaliateType(3);
        Combat.stopNavigation(npc);
        Combat.holdInPlace(npc, npc.getX(), npc.getY(), npc.getZ());
    }
    TelegraphAPI.ring(npc, npc.getX(), npc.getY(), npc.getZ(), B1_ROAR_R, G_ROAR_RING_W, G_ROAR_TICKS, HS.PURPLE);
    npc.getWorld().playSoundAt(npc.getPos(), "minecraft:entity.ravager.roar", G_ROAR_SOUND_VOL, G_ROAR_SOUND_PITCH);
    npc.getTimers().forceStart(B1_T_ROAR, G_ROAR_TICKS, false);
    b1Say(npc, "Грул набирает рёв Стада!");
}

function gRoarHit(npc) {
    if (!npc.isAlive()) return;
    var ps = hsPlayersAt(npc, npc.getX(), npc.getY(), npc.getZ(), B1_ROAR_R);
    for (var i = 0; i < ps.length; i++) {
        var p = ps[i];
        if (b1InStakeShadow(p, npc)) continue;
        p.clearPotionEffects();
        p.addPotionEffect(PotionEffectType_SLOWNESS, G_ROAR_SLOW_SEC, G_ROAR_SLOW_AMP, false);
        hsStrike(p, G_ROAR_PCT);
        b1Tell(p, "Рёв Стада срывает с тебя все чары.");
    }
    gSummon(npc);
    b1Release(npc);
}

function gSummon(npc) {
    var want = G_SUMMON_COUNT;
    var room = G_SUMMON_MAX - hsCount(npc, B1_RAIDER);
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
    var x = ox + dx / len * G_SUMMON_BACK;
    var z = oz + dz / len * G_SUMMON_BACK;
    var spot = gFindRaiderSpawn(npc, x, z, living);
    var spawned = 0;
    if (spot != null) {
        hsNoRespawn(w.spawnClone(spot[0], spot[1], spot[2], HS.TAB, B1_RAIDER));
        spawned = 1;
        living = hsFind(npc, B1_RAIDER);
    }
    if (room >= 2) {
        var side = gFindRaiderSpawn(npc, x + (-dz / len) * G_SUMMON_SIDE, z + (dx / len) * G_SUMMON_SIDE, living);
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
    var minX = c[0] - a.hw + G_ARENA_PAD;
    var maxX = c[0] + a.hw - G_ARENA_PAD;
    var minZ = c[2] - a.hd + G_ARENA_PAD;
    var maxZ = c[2] + a.hd - G_ARENA_PAD;
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
        if (dx * dx + dz * dz < G_SUMMON_NEAR) return true;
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
    TelegraphAPI.circleAt(npc, s[0], s[1], s[2], G_STAKE_R, G_STAKE_CHARGE, HS.GOLD);
    hsPut(npc, "b1_nest", index);
    hsPut(npc, "b1_arm", 1);
    npc.getTimers().forceStart(B1_T_STAKE, G_STAKE_CHARGE, false);
    b1Say(npc, "Грул вбивает новый кол!");
}

function gStakePlace(npc) {
    hsPut(npc, "b1_arm", 0);
    if (!npc.isAlive()) return;
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

/** Удар у ног + jump_slam на игрока (ф1 — maxRange, ф3 — полный). Не кастует при рёве/ритуале. */
function gStompTry(npc) {
    if (gBusy(npc)) return;
    if (hsGet(npc, "b1_mounted", "0") == "1") return;
    if (hsGet(npc, "b1_arm", "0") == "1") return;
    if (hsGet(npc, "b1_ritual", "0") == "1") return;
    var now = hsNow(npc);
    if (now < hsNum(npc, "b1_stomp_at")) return;
    var x = npc.getX();
    var y = npc.getY();
    var z = npc.getZ();
    var target = gStompPickTarget(npc);
    hsPut(npc, "b1_stomp", 1);
    hsPut(npc, "b1_stomp_x", x);
    hsPut(npc, "b1_stomp_y", y);
    hsPut(npc, "b1_stomp_z", z);
    hsPut(npc, "b1_stomp_at", now + G_STOMP_CD);
    if (target != null) {
        hsPut(npc, "b1_stomp_leap", 1);
        var warn = gStompLeapAim(npc, target);
        TelegraphAPI.circleAt(npc, warn[0], y, warn[1], G_STOMP_R, G_STOMP_CHARGE + G_LEAP_TICKS, HS.RED);
    } else {
        hsPut(npc, "b1_stomp_leap", 0);
    }
    TelegraphAPI.circleAt(npc, x, y, z, G_STOMP_R, G_STOMP_CHARGE, HS.RED);
    npc.getAi().setWalkingSpeed(0);
    npc.getAi().setRetaliateType(3);
    Combat.stopNavigation(npc);
    Combat.holdInPlace(npc, x, y, z);
    npc.getTimers().forceStart(B1_T_STOMP, G_STOMP_CHARGE, false);
    npc.getWorld().playSoundAt(npc.getPos(), "minecraft:entity.ravager.roar", G_STOMP_SOUND_VOL, G_STOMP_SOUND_PITCH);
    if (target != null) b1Say(npc, "Грул бьёт землю и прыгает на добычу!");
    else b1Say(npc, "Грул бьёт копытом о землю!");
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
    gStompBlast(npc, x, y, z);
    if (hsGet(npc, "b1_stomp_leap", "0") == "1") {
        hsPut(npc, "b1_stomp_leap", 0);
        if (gStartStompLeap(npc, gStompPickTarget(npc))) return;
    }
    gApplyAi(npc);
}

/** Урон и откидывание в круге стомпа. */
function gStompBlast(npc, x, y, z) {
    var ps = hsPlayersAt(npc, x, y, z, G_STOMP_R);
    for (var i = 0; i < ps.length; i++) {
        var p = ps[i];
        hsStrike(p, G_STOMP_PCT);
        gKnock(p, x, z, G_STOMP_KB, G_STOMP_KB_Y);
    }
    npc.getWorld().playSoundAt(NpcAPI.getIPos(x, y, z), "minecraft:entity.generic.explode", G_STOMP_HIT_VOL, G_STOMP_HIT_PITCH);
}

/** Ближайший игрок на арене. */
function gStompPickTarget(npc) {
    var ps = hsPlayers(npc);
    if (ps == null || ps.length == 0) return null;
    var nearest = ps[0];
    var best = hsFlat(npc, nearest.getX(), nearest.getZ());
    var i;
    for (i = 1; i < ps.length; i++) {
        var dist = hsFlat(npc, ps[i].getX(), ps[i].getZ());
        if (dist < best) {
            best = dist;
            nearest = ps[i];
        }
    }
    return nearest;
}

/** Точка приземления для telegraph (с учётом лимита ф1). */
function gStompLeapAim(npc, target) {
    var maxDist = gShouldFight(npc) ? 0 : G_STOMP_LEAP_MAX_P1;
    var tx = target.getX();
    var tz = target.getZ();
    var dist = hsFlat(npc, tx, tz);
    if (maxDist > 0 && dist > maxDist) {
        var dx = tx - npc.getX();
        var dz = tz - npc.getZ();
        var len = Math.sqrt(dx * dx + dz * dz);
        if (len >= 0.05) {
            tx = npc.getX() + dx / len * maxDist;
            tz = npc.getZ() + dz / len * maxDist;
        }
    }
    return gClampArena(npc, tx, tz);
}

/** Прыжок стомпа через AbilityAPI jump_slam (как посадка на кабана). */
function gStartStompLeap(npc, target) {
    if (target == null || !target.isAlive()) return false;
    if (AbilityAPI.isBusy(npc) || hsGet(npc, "b1_leap", "") != "") return false;
    var charge = 1;
    var active = G_LEAP_TICKS;
    var maxRange = gShouldFight(npc) ? 48 : G_STOMP_LEAP_MAX_P1;
    var now = hsNow(npc);
    hsPut(npc, "b1_leap", "stomp");
    hsPut(npc, "b1_leap_from", now);
    hsPut(npc, "immune_until", now + charge + active + 20);
    if (!hsStart(npc, "jump_slam", target, AbilityAPI.params(
            "chargeTicks", charge,
            "activeTicks", active,
            "landRadius", G_STOMP_R,
            "damage", 0,
            "arcHeight", G_LEAP_ARC,
            "maxRange", maxRange,
            "knockback", 0,
            "knockbackY", 0
        ))) {
        hsPut(npc, "b1_leap", "");
        return false;
    }
    npc.getAi().setWalkingSpeed(0);
    npc.getAi().setRetaliateType(3);
    npc.getTimers().forceStart(B1_T_LEAP, charge + active + 2, false);
    return true;
}

function gStompLeapLand(npc) {
    if (hsGet(npc, "b1_leap", "") != "stomp") return;
    hsPut(npc, "b1_leap", "");
    gClearFall(npc);
    if (!npc.isAlive()) {
        b1Release(npc);
        return;
    }
    gStompBlast(npc, npc.getX(), npc.getY(), npc.getZ());
    b1Release(npc);
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
    if (AbilityAPI.isBusy(npc)) return false;
    var spot = gClampArena(npc, x, z);
    var now = hsNow(npc);
    hsPut(npc, "b1_leap", mode);
    hsPut(npc, "b1_leap_x", spot[0]);
    hsPut(npc, "b1_leap_z", spot[1]);
    hsPut(npc, "b1_leap_sx", npc.getX());
    hsPut(npc, "b1_leap_sy", npc.getY());
    hsPut(npc, "b1_leap_sz", npc.getZ());
    hsPut(npc, "b1_leap_from", now);
    hsPut(npc, "immune_until", now + G_LEAP_TICKS + 20);
    var color = mode == "roar" ? HS.PURPLE : HS.RED;
    var radius = mode == "roar" ? G_LEAP_R_ROAR : G_LEAP_R_OTHER;
    TelegraphAPI.circleAt(npc, spot[0], npc.getY(), spot[1], radius, G_LEAP_TICKS, color);
    npc.getAi().setWalkingSpeed(0);
    npc.getAi().setRetaliateType(3);
    Combat.stopNavigation(npc);
    npc.getTimers().forceStart(B1_T_LEAP, 1, true);
    return true;
}

function gLeapStep(npc) {
    if (hsGet(npc, "b1_leap", "") == "") return;
    if (!npc.isAlive() || hsGet(npc, "stun", "0") == "1") {
        gLeapEnd(npc, false);
        return;
    }
    var age = hsNow(npc) - hsNum(npc, "b1_leap_from");
    var dur = G_LEAP_TICKS;
    var t = age / dur;
    if (t > 1) t = 1;
    var x = hsNum(npc, "b1_leap_sx") + (hsNum(npc, "b1_leap_x") - hsNum(npc, "b1_leap_sx")) * t;
    var z = hsNum(npc, "b1_leap_sz") + (hsNum(npc, "b1_leap_z") - hsNum(npc, "b1_leap_sz")) * t;
    var y = hsNum(npc, "b1_leap_sy") + Math.sin(t * Math.PI) * G_LEAP_ARC;
    npc.setPosition(x, y, z);
    gClearFall(npc);
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
    gClearFall(npc);
    if (!landed || !npc.isAlive()) {
        b1Release(npc);
        return;
    }
    npc.setPosition(hsNum(npc, "b1_leap_x"), hsNum(npc, "b1_leap_sy"), hsNum(npc, "b1_leap_z"));
    gClearFall(npc);
    if (mode == "roar") {
        gRoarNow(npc);
        return;
    }
    var ps = hsPlayersAt(npc, npc.getX(), npc.getY(), npc.getZ(), G_LEAP_LAND_R);
    for (var i = 0; i < ps.length; i++) hsStrike(ps[i], G_LEAP_LAND_PCT);
    b1Release(npc);
}

/** Сброс fallDistance (роар-дуга; стомп идёт через jump_slam + immune_until). */
function gClearFall(npc) {
    try { Combat.clearFallDistance(npc); } catch (err) {
        try {
            var mc = npc.getMCEntity();
            if (mc != null) mc.fallDistance = 0.0;
        } catch (err2) {}
    }
}

function gSqueal(npc, ps) {
    if (b1Phase(npc) != 3) return;
    var now = hsNow(npc);
    if (hsNum(npc, "b1_squeal") <= 0) {
        hsPut(npc, "b1_squeal", now + G_SQUEAL_CD);
        return;
    }
    if (now < hsNum(npc, "b1_squeal")) return;
    hsPut(npc, "b1_squeal", now + G_SQUEAL_CD);
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
            "chargeTicks", G_SQUEAL_CHARGE,
            "activeTicks", G_SQUEAL_ACTIVE,
            "distance", G_SQUEAL_DIST,
            "damage", G_SQUEAL_DMG,
            "hitRadius", G_SQUEAL_HIT_R
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
        if (hsGet(npc, "b1_leap", "") == "stomp") {
            gStompLeapLand(npc);
            return;
        }
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
        e.target.addPotionEffect(PotionEffectType_SLOWNESS, G_MELEE_SLOW_SEC, G_MELEE_SLOW_AMP, false);
    }
}

function damaged(e) {
    var npc = e.npc;
    b1Cap(e, 1);
    if (hsNow(npc) < hsNum(npc, "immune_until")) return;
    b1RitualHurt(e);
}

function died(e) {
    AbilityAPI.cancel(e.npc);
    b1Died(e);
}
