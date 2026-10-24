// hs_b1.js — общая логика пары Кровохряк + Грул.
// В GUI между ядром и файлом босса: hs_core → hs_b1 → boss_bloodtusk / boss_grul.
// Подменяет hsEnrage только у этих двоих. Остальные боссы файл не грузят.

var B1_BOAR = "Кровохряк";
var B1_GRUL = "Грул Кабанья Рука";
var B1_RAIDER = "Унгор-рейдер";
var B1_STAKE = "minecraft:dark_oak_log";
var B1_MUD_BLOCK = "minecraft:soul_soil";
// [x, y, z, alongZ]. alongZ=1 — вторая колонка по Z, иначе по X. Кол 2×3.
// Ближе к центру, чем прежние ±16/±18: таран в 14 блоков с середины до них достаёт.
var B1_NESTS = [
    [-8, 1, -6, 0], [7, 1, -6, 0], [-8, 1, 5, 0], [7, 1, 5, 0],
    [-1, 1, -9, 0], [-1, 1, 8, 0], [-11, 1, -1, 1], [10, 1, -1, 1]
];
var B1_OLD_NESTS = [
    [-16, 1, -13], [16, 1, -13], [-16, 1, 13], [16, 1, 13],
    [0, 1, -15], [0, 1, 15], [-18, 1, 0], [18, 1, 0]
];
var B1_MUD = [[7, 0, 5], [-8, 0, -4]];

var B1_T_RAM = 1;
var B1_T_STUN = 2;
var B1_T_SLIDE = 4;
var B1_T_RITUAL = 5;
var B1_T_ROAR = 11;
var B1_T_STAKE = 12;
var B1_T_LASSO = 13;
var B1_T_TRIP = 14;
var B1_T_SLAM = 17;
var B1_T_AIM = 18;
var B1_T_LEAP = 19;
var B1_T_AXE = 20;
var B1_T_FEED = 21;
var B1_T_STOMP = 22;
var B1_ROAR_R = 18;
var HerdstoneMount = Java.type("noppes.npcs.abilities.HerdstoneMount");

function b1Say(npc, text) {
    npc.say("§4[Стадо] §f" + text);
}

function b1Tell(player, text) {
    player.message("§4[Стадо] §f" + text);
}

function b1W(npc) { return npc.getWorld().getStoreddata(); }
function b1WGet(npc, key, def) {
    var data = b1W(npc);
    return data.has(key) ? String(data.get(key)) : def;
}
function b1WPut(npc, key, value) { b1W(npc).put(key, String(value)); }
function b1WDel(npc, key) {
    var data = b1W(npc);
    if (data.has(key)) data.remove(key);
}

function b1Other(npc) {
    var want = String(npc.getName()) == B1_GRUL ? B1_BOAR : B1_GRUL;
    var list = hsFind(npc, want);
    return list.length > 0 ? list[0] : null;
}

function b1Duplicate(npc) {
    var list = hsFind(npc, String(npc.getName()));
    var id = String(npc.getUUID());
    var n = 0;
    for (var i = 0; i < list.length; i++) {
        if (String(list[i].getUUID()) != id) n++;
    }
    return n > 0;
}

function b1HpFrac(npc) {
    var max = npc.getStats().getMaxHealth();
    if (!(max > 0)) return 1;
    return npc.getHealth() / max;
}

function b1Phase(npc) {
    var other = b1Other(npc);
    if (other == null) return 3;
    if (b1WGet(npc, "hs_b1_noremount", "0") == "1") return 1;
    if (b1HpFrac(npc) <= 0.6 || b1HpFrac(other) <= 0.6) return 2;
    return 1;
}

function b1FightN(npc) {
    var n = Number(b1WGet(npc, "hs_b1_fight_n", "1"));
    if (!(n > 0)) n = 1;
    return n;
}

function b1LinkRange(npc) { return b1FightN(npc) <= 1 ? 4 : 8; }

function b1NestLimit(npc) {
    var lim = 2 + Math.floor(b1FightN(npc) / 2);
    if (lim > 4) lim = 4;
    if (lim < 2) lim = 2;
    return lim;
}

function b1Forward(yaw) {
    var rad = (Number(yaw) + 90) * Math.PI / 180;
    return [Math.cos(rad), Math.sin(rad)];
}

function b1YawFromForward(fx, fz) {
    return Math.atan2(fz, fx) * 180 / Math.PI - 90;
}

function b1Mask(npc) {
    var mask = b1WGet(npc, "hs_b1_mask", "00000000");
    while (mask.length < 8) mask += "0";
    return mask.substring(0, 8);
}

function b1SetBit(npc, index, on) {
    var mask = b1Mask(npc);
    var out = "";
    for (var i = 0; i < 8; i++) {
        if (i == index) out += on ? "1" : "0";
        else out += mask.charAt(i);
    }
    b1WPut(npc, "hs_b1_mask", out);
}

function b1StakeCount(npc) {
    var mask = b1Mask(npc);
    var n = 0;
    for (var i = 0; i < 8; i++) if (mask.charAt(i) == "1") n++;
    return n;
}

function b1IsStake(block) {
    if (block == null) return false;
    var name = String(block.getName());
    return name == B1_STAKE || name == "dark_oak_log";
}

function b1IsMud(block) {
    if (block == null) return false;
    var name = String(block.getName());
    return name == B1_MUD_BLOCK || name == "soul_soil";
}

function b1StakeAlongZ(index) {
    var off = B1_NESTS[index];
    return off.length > 3 && off[3] == 1;
}

function b1StakeColumns(npc, index) {
    var p = hsXYZ(npc, B1_NESTS[index]);
    if (b1StakeAlongZ(index)) return [[p[0], p[1], p[2]], [p[0], p[1], p[2] + 1]];
    return [[p[0], p[1], p[2]], [p[0] + 1, p[1], p[2]]];
}

function b1StakeCenter(npc, index) {
    var cols = b1StakeColumns(npc, index);
    return [(cols[0][0] + cols[1][0]) / 2 + 0.5, cols[0][1], (cols[0][2] + cols[1][2]) / 2 + 0.5];
}

function b1PutStake(npc, index) {
    var cols = b1StakeColumns(npc, index);
    var w = npc.getWorld();
    var c, y;
    for (c = 0; c < cols.length; c++) {
        for (y = 0; y < 3; y++) {
            w.setBlock(NpcAPI.getIPos(cols[c][0], cols[c][1] + y, cols[c][2]), B1_STAKE);
        }
    }
    b1SetBit(npc, index, true);
}

function b1RemoveStake(npc, index) {
    var cols = b1StakeColumns(npc, index);
    var w = npc.getWorld();
    var c, y;
    for (c = 0; c < cols.length; c++) {
        for (y = 0; y < 3; y++) {
            w.removeBlock(NpcAPI.getIPos(cols[c][0], cols[c][1] + y, cols[c][2]));
        }
    }
    b1SetBit(npc, index, false);
}

function b1ClearLegacyStakes(npc) {
    var w = npc.getWorld();
    var i, y;
    for (i = 0; i < B1_OLD_NESTS.length; i++) {
        var p = hsXYZ(npc, B1_OLD_NESTS[i]);
        for (y = 0; y < 3; y++) {
            var pos = NpcAPI.getIPos(p[0], p[1] + y, p[2]);
            if (b1IsStake(w.getBlock(p[0], p[1] + y, p[2]))) w.removeBlock(pos);
        }
    }
}

function b1ClearNests(npc) {
    for (var i = 0; i < B1_NESTS.length; i++) b1RemoveStake(npc, i);
    b1ClearLegacyStakes(npc);
    b1WPut(npc, "hs_b1_mask", "00000000");
}

function b1PlaceMud(npc) {
    var w = npc.getWorld();
    for (var i = 0; i < B1_MUD.length; i++) {
        for (var dx = -1; dx <= 1; dx++) {
            for (var dz = -1; dz <= 1; dz++) {
                var p = hsXYZ(npc, [B1_MUD[i][0] + dx, 0, B1_MUD[i][2] + dz]);
                w.setBlock(NpcAPI.getIPos(p[0], p[1], p[2]), B1_MUD_BLOCK);
            }
        }
    }
}

function b1FreeIndex(npc) {
    var mask = b1Mask(npc);
    var free = [];
    for (var i = 0; i < B1_NESTS.length; i++) {
        if (mask.charAt(i) != "1") free.push(i);
    }
    if (free.length == 0) return -1;
    return free[Math.floor(Math.random() * free.length)];
}

function b1FillStakes(npc) {
    b1WPut(npc, "hs_b1_locked", "1");
    for (var i = 0; i < B1_NESTS.length; i++) {
        if (b1Mask(npc).charAt(i) == "1") continue;
        b1PutStake(npc, i);
    }
}

function b1LineHitsMud(npc, x, z, yaw, len) {
    var f = b1Forward(yaw);
    var w = npc.getWorld();
    var by = Math.floor(hsOrigin(npc)[1]);
    var steps = Math.floor(len);
    for (var t = 1; t <= steps; t++) {
        var block = w.getBlock(Math.floor(x + f[0] * t), by, Math.floor(z + f[1] * t));
        if (b1IsMud(block)) return true;
    }
    return false;
}

function b1InStakeShadow(player, grul) {
    var mask = b1Mask(grul);
    var gx = player.getX() - grul.getX();
    var gz = player.getZ() - grul.getZ();
    var gl = Math.sqrt(gx * gx + gz * gz);
    if (gl < 0.2) return false;
    for (var i = 0; i < B1_NESTS.length; i++) {
        if (mask.charAt(i) != "1") continue;
        var center = b1StakeCenter(grul, i);
        var cols = b1StakeColumns(grul, i);
        var near = false;
        var c;
        for (c = 0; c < cols.length; c++) {
            if (hsFlat(player, cols[c][0] + 0.5, cols[c][2] + 0.5) <= 3.0) near = true;
        }
        if (!near) continue;
        var sx = center[0];
        var sz = center[2];
        var vx = sx - grul.getX();
        var vz = sz - grul.getZ();
        var sl = Math.sqrt(vx * vx + vz * vz);
        if (sl < 0.2 || sl > gl + 0.5) continue;
        var dot = (gx * vx + gz * vz) / (gl * sl);
        if (dot > Math.cos(35 * Math.PI / 180)) return true;
    }
    return false;
}

function b1OnSide(p, x, z, yaw, len) {
    var f = b1Forward(yaw);
    var dx = p.getX() - x;
    var dz = p.getZ() - z;
    var along = dx * f[0] + dz * f[1];
    if (along < 0 || along > len) return false;
    var lat = Math.abs(dx * f[1] - dz * f[0]);
    return lat > 1.6 && lat <= 3.6;
}

function b1InCone(p, x, z, yaw, len, halfDeg) {
    var f = b1Forward(yaw);
    var dx = p.getX() - x;
    var dz = p.getZ() - z;
    var along = dx * f[0] + dz * f[1];
    if (along < 0.2 || along > len) return false;
    var lat = Math.abs(dx * f[1] - dz * f[0]);
    var limit = Math.tan(halfDeg * Math.PI / 180) * along;
    if (limit < 0.6) limit = 0.6;
    return lat <= limit;
}

function b1NearEdge(npc) {
    var c = hsArenaCenter(npc);
    var a = hsArena();
    var dx = npc.getX() - c[0];
    var dz = npc.getZ() - c[2];
    if (dx < 0) dx = -dx;
    if (dz < 0) dz = -dz;
    return dx >= a.hw - 2 || dz >= a.hd - 2;
}

function b1AddFury(npc, amount) {
    var boar = String(npc.getName()) == B1_BOAR ? npc : b1Other(npc);
    if (boar == null) return;
    var fury = hsNum(boar, "b1_fury") + amount;
    if (fury > 3) fury = 3;
    if (fury < 0) fury = 0;
    hsPut(boar, "b1_fury", fury);
    boar.getWorld().spawnParticle("soul_fire_flame", boar.getX(), boar.getY() + 1.4, boar.getZ(), 0.3, 0.4, 0.3, 0.02, 4 + fury * 3);
}

function b1BreakFury(boar) {
    if (b1Phase(boar) == 3) {
        var fury = hsNum(boar, "b1_fury") - 1;
        if (fury < 0) fury = 0;
        hsPut(boar, "b1_fury", fury);
        return;
    }
    hsPut(boar, "b1_fury", 0);
}

function b1Release(npc) {
    if (!npc.isAlive()) return;
    if (hsGet(npc, "stun", "0") == "1") return;
    if (hsGet(npc, "b1_ritual", "0") == "1") return;
    if (hsGet(npc, "b1_mounting", "0") == "1") return;
    // Только наездник заморожен. Кабан тоже с b1_mounted=1 — ему скорость нужна.
    if (hsGet(npc, "b1_mounted", "0") == "1") {
        var riding = false;
        try { riding = HerdstoneMount.isRiding(npc); } catch (err) { riding = String(npc.getName()) == B1_GRUL; }
        if (riding) return;
    }
    var speed = hsNum(npc, "base_speed");
    if (!(speed > 0)) speed = 4;
    // Грул с живым кабаном — кит, не мили.
    if (String(npc.getName()) == B1_GRUL && b1Other(npc) != null) {
        npc.getAi().setWalkingSpeed(Math.min(10, speed + 2));
        npc.getAi().setRetaliateType(2);
        return;
    }
    npc.getAi().setWalkingSpeed(speed);
    npc.getAi().setRetaliateType(0);
}

function b1Stun(npc, ticks) {
    AbilityAPI.cancel(npc);
    hsPut(npc, "stun", 1);
    hsPut(npc, "stun_until", hsNow(npc) + ticks);
    npc.getAi().setWalkingSpeed(0);
    npc.getAi().setRetaliateType(3);
    npc.getTimers().stop(B1_T_AIM);
    b1ClearLine(npc);
    if (hsNum(npc, "ram_at") > hsNow(npc) + 10000) hsPut(npc, "ram_at", hsNow(npc) + ticks);
    npc.getTimers().forceStart(B1_T_STUN, ticks, false);
}

function b1DropLine(npc) {
    var old = hsGet(npc, "b1_line", "");
    if (old.length > 2) {
        try { TelegraphAPI.remove(old); } catch (err) {}
    }
    hsPut(npc, "b1_line", "");
}

function b1ClearLine(npc) {
    b1DropLine(npc);
    hsPut(npc, "b1_aim", 0);
}

function b1Feed(npc, player) {
    if (player == null || !player.isAlive()) return;
    var boar = String(npc.getName()) == B1_BOAR ? npc : b1Other(npc);
    if (boar == null) return;
    hsPut(boar, "b1_feed", String(player.getUUID()));
    if (hsNum(boar, "ram_at") > hsNow(boar) + 25) hsPut(boar, "ram_at", hsNow(boar) + 15);
}

function b1OnEdge(npc, player) {
    var c = hsArenaCenter(npc);
    var a = hsArena();
    var dx = player.getX() - c[0];
    var dz = player.getZ() - c[2];
    if (dx < 0) dx = -dx;
    if (dz < 0) dz = -dz;
    return dx >= a.hw - 3 || dz >= a.hd - 3;
}

function b1EdgeTick(npc, ps) {
    if (ps == null || ps.length == 0) return;
    var now = hsNow(npc);
    if (now < Number(b1WGet(npc, "hs_b1_edge_at", "0"))) return;
    b1WPut(npc, "hs_b1_edge_at", now + 20);
    for (var i = 0; i < ps.length; i++) {
        if (!b1OnEdge(npc, ps[i])) continue;
        hsStrike(ps[i], 0.03);
    }
}

// Таймер, запущенный с другого NPC, может не сработать. Срок стана считает сам моб.
function b1ClearStun(npc) {
    if (hsGet(npc, "stun", "0") != "1") return false;
    if (hsNum(npc, "stun_until") > hsNow(npc)) {
        npc.getAi().setWalkingSpeed(0);
        npc.getAi().setRetaliateType(3);
        return true;
    }
    hsPut(npc, "stun", 0);
    b1Release(npc);
    return false;
}

function b1Cap(e, mul) {
    var npc = e.npc;
    if (hsNow(npc) < hsNum(npc, "immune_until")) {
        e.setCanceled(true);
        return;
    }
    if (hsGet(npc, "linked", "0") == "1") mul = mul * 0.5;
    hsCap(e, mul);
}

function b1DropRider(boar) {
    hsPut(boar, "b1_mounted", 0);
    var grul = b1Other(boar);
    if (grul == null) return;
    hsPut(grul, "b1_mounted", 0);
    hsPut(grul, "b1_mounting", 0);
    hsPut(grul, "b1_mount_go", 0);
    hsPut(grul, "b1_mount_wait", 0);
    HerdstoneMount.dismount(grul);
    hsPut(grul, "b1_recover", hsNow(boar) + 300);
    AbilityAPI.cancel(grul);
    b1Say(grul, "Грул слетает с кабана!");
}

function b1Impale(boar) {
    var mounted = hsGet(boar, "b1_mounted", "0") == "1";
    var ticks = b1Phase(boar) == 3 ? 80 : 120;
    var grul = b1Other(boar);
    if (mounted) {
        ticks = 120;
        var until = hsNow(boar) + ticks;
        hsPut(boar, "unlink_until", until);
        if (grul != null) hsPut(grul, "unlink_until", until);
        b1DropRider(boar);
    }
    b1BreakFury(boar);
    hsPut(boar, "b1_step", -1);
    hsPut(boar, "ram_at", hsNow(boar) + ticks);
    boar.getTimers().stop(B1_T_SLIDE);
    boar.getTimers().stop(B1_T_RAM);
    AbilityAPI.cancel(boar);
    b1Stun(boar, ticks);
    if (mounted && grul != null) b1Stun(grul, ticks);
    b1Say(boar, "Кровохряк насадился на кол и оглушён!");
}

function b1StakeHit(boar) {
    var w = boar.getWorld();
    var mask = b1Mask(boar);
    for (var i = 0; i < B1_NESTS.length; i++) {
        if (mask.charAt(i) != "1") continue;
        var cols = b1StakeColumns(boar, i);
        var standing = false;
        var hit = false;
        var c;
        for (c = 0; c < cols.length; c++) {
            if (b1IsStake(w.getBlock(cols[c][0], cols[c][1], cols[c][2]))) standing = true;
            if (hsFlat(boar, cols[c][0] + 0.5, cols[c][2] + 0.5) <= 2.4) hit = true;
        }
        if (!standing) {
            b1SetBit(boar, i, false);
            continue;
        }
        if (!hit) continue;
        b1RemoveStake(boar, i);
        w.playSoundAt(boar.getPos(), "minecraft:block.wood.break", 3.0, 0.5);
        b1Impale(boar);
        return true;
    }
    return false;
}

function b1RefreshLink(npc) {
    var other = b1Other(npc);
    var now = hsNow(npc);
    if (other == null || now < hsNum(npc, "unlink_until")) {
        hsPut(npc, "linked", 0);
        if (other != null) hsPut(other, "linked", 0);
        return;
    }
    var dist = hsFlat(npc, other.getX(), other.getZ());
    var on = dist <= b1LinkRange(npc);
    hsPut(npc, "linked", on ? 1 : 0);
    hsPut(other, "linked", on ? 1 : 0);
    if (String(npc.getName()) != B1_BOAR) return;
    if (on) {
        var mx = (npc.getX() + other.getX()) * 0.5;
        var mz = (npc.getZ() + other.getZ()) * 0.5;
        npc.getWorld().spawnParticle("end_rod", mx, npc.getY() + 1.1, mz, 0.15, 0.25, 0.15, 0.01, 4);
    }
    if (dist <= 16) {
        hsPut(npc, "b1_far", 0);
        return;
    }
    if (b1Phase(npc) == 3) return;
    if (hsGet(npc, "stun", "0") == "1" || hsGet(npc, "b1_mounted", "0") == "1") return;
    if (hsGet(npc, "b1_ritual", "0") == "1" || AbilityAPI.isBusy(npc)) return;
    var far = hsNum(npc, "b1_far");
    if (far <= 0) {
        hsPut(npc, "b1_far", now);
        return;
    }
    if (now - far < 200) return;
    hsPut(npc, "b1_far", now);
    hsPut(npc, "b1_call", 1);
}

function b1ApplyWidow(npc) {
    if (String(npc.getName()) == B1_BOAR) {
        npc.addPotionEffect(PotionEffectType_STRENGTH, 99999, 0, true);
        b1Say(npc, "Кровохряк визжит над телом хозяина!");
        return;
    }
    npc.addPotionEffect(PotionEffectType_STRENGTH, 99999, 1, true);
    var base = hsNum(npc, "base_speed");
    if (!(base > 0)) base = 4;
    npc.getAi().setWalkingSpeed(Math.min(10, base + 2));
    b1Say(npc, "Ты убил моего кабана! Я сам тебя растопчу!");
}

function b1ClearWidow(npc) {
    if (hsGet(npc, "widowed", "0") != "1" && hsNum(npc, "widow_at") <= 0) return;
    hsPut(npc, "widowed", 0);
    hsPut(npc, "widow_at", 0);
    hsPut(npc, "b1_ritual", 0);
    npc.getTimers().stop(B1_T_RITUAL);
    npc.clearPotionEffects();
    b1Release(npc);
}

function b1StartRitual(npc) {
    hsPut(npc, "b1_ritual", 1);
    hsPut(npc, "b1_ritual_dmg", 0);
    npc.getAi().setWalkingSpeed(0);
    npc.getAi().setRetaliateType(3);
    TelegraphAPI.circle(npc, npc.getX(), npc.getY(), npc.getZ(), 4.0, 160, HS.PURPLE);
    npc.getTimers().forceStart(B1_T_RITUAL, 160, false);
    b1Say(npc, "Начинается кровавый ритуал!");
}

function b1FindCorpse(npc, name) {
    var id = b1WGet(npc, "hs_b1_corpse_" + name, "");
    if (id.length >= 32) {
        try {
            var byId = npc.getWorld().getEntity(id);
            if (byId != null && String(byId.getName()) == name && !byId.isAlive()) return byId;
        } catch (err) {}
    }
    var c = hsArenaCenter(npc);
    var list = npc.getWorld().getNearbyEntities(NpcAPI.getIPos(c[0], c[1], c[2]), hsScanRange(), 2);
    for (var i = 0; i < list.length; i++) {
        if (String(list[i].getName()) != name || list[i].isAlive()) continue;
        return list[i];
    }
    return null;
}

function b1ReviveSpawn(npc, partnerName) {
    var corpse = b1FindCorpse(npc, partnerName);
    b1WDel(npc, "hs_b1_corpse_" + partnerName);
    if (corpse == null) return;
    var maxHp = b1WGet(npc, "hs_b1_max_" + partnerName, "100");
    b1WPut(npc, "hs_b1_revive_" + partnerName, maxHp);
    b1WPut(npc, "hs_b1_noremount", "1");
    corpse.reset();
}

function b1RitualFire(npc) {
    if (hsGet(npc, "b1_ritual", "0") != "1") return;
    if (!npc.isAlive()) return;
    hsPut(npc, "b1_ritual", 0);
    var partner = String(npc.getName()) == B1_BOAR ? B1_GRUL : B1_BOAR;
    b1ReviveSpawn(npc, partner);
    b1Release(npc);
    b1Say(npc, "Стадо возвращает павшего!");
}

function b1RitualHurt(e) {
    var npc = e.npc;
    if (hsGet(npc, "b1_ritual", "0") != "1") return;
    var sum = hsNum(npc, "b1_ritual_dmg") + Number(e.damage);
    hsPut(npc, "b1_ritual_dmg", sum);
    var max = npc.getStats().getMaxHealth();
    if (!(max > 0) || sum < max * 0.10) return;
    hsPut(npc, "b1_ritual", 0);
    npc.getTimers().stop(B1_T_RITUAL);
    hsPut(npc, "b1_ritual_cd", hsNow(npc) + 600);
    b1Stun(npc, 60);
    b1Say(npc, "Ритуал сорван!");
}

function b1WidowTick(npc) {
    var other = b1Other(npc);
    if (other != null) {
        if (hsGet(npc, "widowed", "0") == "1" || hsNum(npc, "widow_at") > 0) b1ClearWidow(npc);
        return false;
    }
    if (hsNum(npc, "fight_at") <= 0) return false;
    if (hsGet(npc, "widowed", "0") != "1") {
        hsPut(npc, "widowed", 1);
        if (hsNum(npc, "widow_at") <= 0) hsPut(npc, "widow_at", hsNow(npc));
        b1ApplyWidow(npc);
    }
    if (hsGet(npc, "b1_ritual", "0") == "1") return true;
    if (hsGet(npc, "stun", "0") == "1") return false;
    var now = hsNow(npc);
    if (now < hsNum(npc, "b1_ritual_cd")) return false;
    if (now < hsNum(npc, "widow_at") + 400) return false;
    b1StartRitual(npc);
    return true;
}

function b1ConsumeRevive(npc) {
    var key = "hs_b1_revive_" + String(npc.getName());
    var raw = b1WGet(npc, key, "");
    if (raw.length == 0) return -1;
    b1WDel(npc, key);
    var hp = Number(raw);
    return hp > 0 ? hp : -1;
}

function b1ApplyRevive(npc, maxHp) {
    npc.getTimers().clear();
    AbilityAPI.cancel(npc);
    hsZoneClear(npc);
    npc.getStats().setMaxHealth(maxHp);
    npc.setHealth(Math.max(1, Math.round(maxHp * 0.3)));
    var name = String(npc.getName());
    if (name == B1_GRUL) hsPut(npc, "base_speed", 6);
    else if (name == B1_BOAR) hsPut(npc, "base_speed", 7);
    else if (hsGet(npc, "base_speed", "") == "") hsPut(npc, "base_speed", npc.getAi().getWalkingSpeed());
    npc.getAi().setWalkingSpeed(hsNum(npc, "base_speed"));
    npc.clearPotionEffects();
    hsPut(npc, "stun", 0);
    hsPut(npc, "b1_mounted", 0);
    hsPut(npc, "b1_mounting", 0);
    hsPut(npc, "b1_ritual", 0);
    hsPut(npc, "b1_fury", 0);
    hsPut(npc, "widowed", 0);
    hsPut(npc, "widow_at", 0);
    hsPut(npc, "b1_step", -1);
    var other = b1Other(npc);
    var fight = other != null ? hsNum(other, "fight_at") : hsNow(npc);
    if (!(fight > 0)) fight = hsNow(npc);
    hsPut(npc, "fight_at", fight);
    hsPut(npc, "empty_at", 0);
    hsPut(npc, "dispel_at", hsNow(npc) + HS.DISPEL_SEC * 20);
    b1WPut(npc, "hs_b1_noremount", "1");
    b1WPut(npc, "hs_b1_locked", "0");
    var selfId = String(npc.getUUID());
    var copies = hsFind(npc, String(npc.getName()));
    for (var i = 0; i < copies.length; i++) {
        if (String(copies[i].getUUID()) != selfId) copies[i].despawn();
    }
    if (other != null) b1ClearWidow(other);
    b1Release(npc);
    b1Say(npc, "Павший поднимается на ноги!");
}

function b1Remember(npc) {
    var name = String(npc.getName());
    b1WPut(npc, "hs_b1_max_" + name, npc.getStats().getMaxHealth());
    b1WPut(npc, "hs_b1_home_" + name, npc.getHomeX() + "," + npc.getHomeY() + "," + npc.getHomeZ());
}

function b1Boot(npc, ps) {
    b1Remember(npc);
    if (b1WGet(npc, "hs_b1_seeded", "0") == "1") return;
    b1WPut(npc, "hs_b1_seeded", "1");
    var n = ps != null ? ps.length : 1;
    if (n < 1) n = 1;
    b1WPut(npc, "hs_b1_fight_n", n);
    b1WPut(npc, "hs_b1_locked", "0");
    b1WPut(npc, "hs_b1_mask", "00000000");
    b1WPut(npc, "hs_b1_enrage_from", hsNum(npc, "fight_at"));
    b1WPut(npc, "hs_b1_next_roar", hsNow(npc) + 1);
    var first = b1FreeIndex(npc);
    if (first >= 0) b1PutStake(npc, first);
    var second = b1FreeIndex(npc);
    if (second >= 0) b1PutStake(npc, second);
}

function b1ResetWorld(npc) {
    var other = b1Other(npc);
    if (other != null && hsNum(other, "fight_at") > 0) {
        b1PlaceMud(npc);
        return;
    }
    b1ClearNests(npc);
    b1PlaceMud(npc);
    b1WPut(npc, "hs_b1_seeded", "0");
    b1WPut(npc, "hs_b1_locked", "0");
    b1WPut(npc, "hs_b1_noremount", "0");
    b1WPut(npc, "hs_b1_horns", "");
    b1WPut(npc, "hs_b1_next_roar", "0");
    b1WPut(npc, "hs_b1_roar_until", "0");
    b1WDel(npc, "hs_b1_corpse_" + B1_BOAR);
    b1WDel(npc, "hs_b1_corpse_" + B1_GRUL);
    b1ClearRing(npc);
}

function b1MudTick(npc, ps) {
    if (String(npc.getName()) == B1_GRUL && b1Other(npc) != null) return;
    if (ps == null) return;
    var w = npc.getWorld();
    for (var i = 0; i < ps.length; i++) {
        var p = ps[i];
        var block = w.getBlock(p.getBlockX(), Math.floor(p.getY() - 0.1), p.getBlockZ());
        if (!b1IsMud(block)) continue;
        p.addPotionEffect(PotionEffectType_SLOWNESS, 2, 0, false);
    }
}

function b1NoteRaiders(npc) {
    var list = hsFind(npc, B1_RAIDER);
    if (list.length == 0) return;
    var book = b1WGet(npc, "hs_b1_horns", "");
    var now = hsNow(npc);
    var changed = false;
    for (var i = 0; i < list.length; i++) {
        hsNoRespawn(list[i]);
        var id = String(list[i].getUUID());
        if (book.indexOf(id) >= 0) continue;
        book += id + "," + now + ",0;";
        changed = true;
    }
    if (changed) b1WPut(npc, "hs_b1_horns", book);
}

function b1HornTick(npc) {
    if (String(npc.getName()) == B1_BOAR && b1Other(npc) != null) return;
    b1NoteRaiders(npc);
    var book = b1WGet(npc, "hs_b1_horns", "");
    if (book.length < 8) return;
    var rows = book.split(";");
    var now = hsNow(npc);
    var out = "";
    var dirty = false;
    for (var i = 0; i < rows.length; i++) {
        if (rows[i].length < 8) continue;
        var bits = rows[i].split(",");
        if (bits.length < 3) continue;
        var born = Number(bits[1]);
        var done = bits[2];
        var age = now - born;
        if (done == "0" && age >= 300 && age < 400) {
            var ent = null;
            try { ent = npc.getWorld().getEntity(bits[0]); } catch (err) { ent = null; }
            if (ent != null && ent.isAlive()) {
                npc.getWorld().spawnParticle("angry_villager", ent.getX(), ent.getY() + 2.2, ent.getZ(), 0.2, 0.2, 0.2, 0.01, 2);
            }
        }
        if (done == "0" && age >= 400) {
            b1AddFury(npc, 2);
            if (String(npc.getName()) == B1_GRUL) b1Say(npc, "Рейдер трубит в рог!");
            done = "1";
            dirty = true;
        }
        out += bits[0] + "," + bits[1] + "," + done + ";";
    }
    if (dirty) b1WPut(npc, "hs_b1_horns", out);
}

function b1RoarBlocks(npc) {
    var now = hsNow(npc);
    if (now < Number(b1WGet(npc, "hs_b1_roar_until", "0"))) return true;
    var next = Number(b1WGet(npc, "hs_b1_next_roar", "0"));
    return next > now && next - now <= 60;
}

/** Срывает рывок/слайд кабана — при рёве Грула с седла. */
function b1InterruptDash(boar) {
    if (boar == null || !boar.isAlive()) return;
    AbilityAPI.cancel(boar);
    try {
        boar.getTimers().stop(B1_T_RAM);
        boar.getTimers().stop(B1_T_SLIDE);
        boar.getTimers().stop(B1_T_AIM);
    } catch (err) {}
    hsPut(boar, "ram_kind", "");
    hsPut(boar, "b1_aim", 0);
    b1ClearLine(boar);
    // Серия ставит ram_at ≈ now+100000; без сброса кабан навсегда молчит.
    var now = hsNow(boar);
    if (hsNum(boar, "b1_step") >= 0 || hsNum(boar, "ram_at") > now + 5000) {
        hsPut(boar, "b1_step", -1);
        var cd = 80;
        try { cd = b1RamCd(boar); } catch (err2) {}
        hsPut(boar, "ram_at", now + cd);
    }
}

function b1FindPlayer(npc, id) {
    if (id == null || String(id).length < 32) return null;
    try {
        var p = npc.getWorld().getEntity(String(id));
        if (p != null && p.isAlive()) return p;
    } catch (err) {}
    return null;
}

function b1WalkTo(npc, target) {
    npc.getAi().setRetaliateType(3);
    var speed = hsNum(npc, "base_speed");
    if (!(speed > 0)) speed = 4;
    npc.getAi().setWalkingSpeed(speed);
    try {
        var mc = npc.getMCEntity();
        if (mc != null && mc.getNavigation() != null) {
            mc.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 1.3);
        }
    } catch (err) {}
}

function b1StartMount(grul) {
    var boar = b1Other(grul);
    if (boar == null) return;
    var now = hsNow(grul);
    hsPut(grul, "b1_mounting", 1);
    hsPut(grul, "b1_mounting_at", now);
    hsPut(grul, "b1_mount_done", 1);
    hsPut(grul, "b1_mount_go", 0);
    hsPut(grul, "immune_until", now + 40);
    hsPut(boar, "immune_until", now + 40);
    b1Say(grul, "Грул запрыгивает на кабана!");
    hsStart(grul, "jump_slam", boar, AbilityAPI.params(
        "chargeTicks", 20,
        "activeTicks", 12,
        "landRadius", 3.0,
        "damage", 0,
        "arcHeight", 4,
        "maxRange", 48,
        "knockback", 0.3
    ));
    grul.getTimers().forceStart(B1_T_SLAM, 36, false);
}

function b1SlamLand(grul) {
    hsPut(grul, "b1_mounting", 0);
    hsPut(grul, "b1_mounting_at", 0);
    if (!grul.isAlive() || hsGet(grul, "stun", "0") == "1") return;
    var boar = b1Other(grul);
    if (boar == null || !boar.isAlive()) return;
    var ps = hsPlayersAt(grul, boar.getX(), boar.getY(), boar.getZ(), 3.2);
    for (var i = 0; i < ps.length; i++) hsStrike(ps[i], 0.20);
    if (!HerdstoneMount.mount(grul, boar, 1.6)) {
        hsPut(grul, "b1_mount_done", 0);
        hsPut(grul, "b1_mount_go", 1);
        return;
    }
    hsPut(grul, "b1_mounted", 1);
    hsPut(boar, "b1_mounted", 1);
    hsPut(boar, "b1_step", -1);
    b1MountSilence(grul);
    grul.getAi().setWalkingSpeed(0);
    grul.getAi().setRetaliateType(3);
    b1Release(boar);
}

/** Сброс кастов Грула при посадке. Колья на седле остаются доступны. */
function b1MountSilence(grul) {
    if (grul == null || !grul.isAlive()) return;
    AbilityAPI.cancel(grul);
    try {
        grul.getTimers().stop(B1_T_ROAR);
        grul.getTimers().stop(B1_T_LEAP);
        grul.getTimers().stop(B1_T_STOMP);
    } catch (err) {}
    hsPut(grul, "b1_leap", "");
    hsPut(grul, "b1_stomp", 0);
    hsPut(grul, "b1_haul", 0);
    b1WPut(grul, "hs_b1_roar_until", 0);
}

function b1MountTick(grul) {
    if (String(grul.getName()) != B1_GRUL) return;
    if (hsGet(grul, "b1_mounted", "0") == "1") return;
    if (hsGet(grul, "b1_mounting", "0") == "1") return;
    if (hsGet(grul, "stun", "0") == "1") return;
    if (hsGet(grul, "b1_ritual", "0") == "1") return;
    if (hsGet(grul, "b1_haul", "0") == "1") return;
    if (hsNum(grul, "b1_recover") > hsNow(grul)) return;
    if (b1Phase(grul) != 2) return;
    if (hsGet(grul, "b1_mount_wait", "0") == "1") {
        var boar = b1Other(grul);
        if (boar == null) {
            hsPut(grul, "b1_mount_wait", 0);
        } else if (hsGet(boar, "stun", "0") == "1" || hsGet(boar, "b1_call", "0") == "1"
                || AbilityAPI.isBusy(boar) || boar.getTimers().has(B1_T_RAM)
                || boar.getTimers().has(B1_T_SLIDE)) {
            return;
        } else {
            b1AskRemount(grul);
        }
    }
    if (hsGet(grul, "b1_mount_done", "0") == "1" && hsGet(grul, "b1_mount_go", "0") != "1") return;
    b1StartMount(grul);
}

function b1AskRemount(grul) {
    if (grul == null || !grul.isAlive()) return;
    if (hsGet(grul, "b1_mounted", "0") == "1" || hsGet(grul, "stun", "0") == "1") return;
    if (hsNum(grul, "b1_recover") > hsNow(grul)) return;
    hsPut(grul, "b1_mount_wait", 0);
    hsPut(grul, "b1_mount_go", 1);
}

function b1RecoverTick(grul) {
    var until = hsNum(grul, "b1_recover");
    if (!(until > 0)) return;
    var boar = b1Other(grul);
    if (boar == null) {
        hsPut(grul, "b1_recover", 0);
        return;
    }
    if (hsGet(grul, "stun", "0") == "1") return;
    if (hsNow(grul) < until) return;
    hsPut(grul, "b1_recover", 0);
    if (hsFlat(grul, boar.getX(), boar.getZ()) > 8) {
        hsPut(boar, "b1_call", 1);
        hsPut(grul, "b1_mount_wait", 1);
    } else {
        hsPut(grul, "b1_mount_go", 1);
    }
    b1Release(grul);
}

function b1ClearRing(npc) {
    b1WDel(npc, "hs_b1_ring_at");
    b1WDel(npc, "hs_b1_ring_i");
    b1WDel(npc, "hs_b1_ring_inner");
    b1WDel(npc, "hs_b1_ring_dmg");
    b1WDel(npc, "hs_b1_ring_warn");
    b1WDel(npc, "hs_b1_enrage_from");
}

function b1RingInner(step) {
    var inner = 24 - step * (16 / 12);
    if (inner < 8) inner = 8;
    return inner;
}

function b1RingDamage(npc, ps, inner) {
    if (ps == null) return;
    var now = hsNow(npc);
    if (now < Number(b1WGet(npc, "hs_b1_ring_dmg", "0"))) return;
    b1WPut(npc, "hs_b1_ring_dmg", now + 20);
    var c = hsArenaCenter(npc);
    for (var i = 0; i < ps.length; i++) {
        if (hsFlat(ps[i], c[0], c[2]) > inner) hsStrike(ps[i], 0.15);
    }
}

function b1PlaceRing(npc, inner) {
    var c = hsArenaCenter(npc);
    var zone = ZoneAPI.hazardRing(npc, c[0], c[1], c[2], 30, inner, 140, 0, 20);
    if (zone != null) {
        try { zone.setFireSeconds(1); } catch (err) {}
        try { zone.setColor(HS.RED); } catch (err2) {}
        hsZonePut(npc, zone);
    }
}

function hsEnrage(npc, ps, now) {
    var start = Number(b1WGet(npc, "hs_b1_enrage_from", "0"));
    if (!(start > 0)) start = hsNum(npc, "fight_at");
    if (now < start + 240 * 20) return;
    if (b1WGet(npc, "hs_b1_ring_at", "") == "") {
        b1WPut(npc, "hs_b1_ring_at", now + 100);
        b1WPut(npc, "hs_b1_ring_i", 0);
        b1WPut(npc, "hs_b1_ring_inner", 24);
        b1WPut(npc, "hs_b1_ring_warn", 0);
        b1PlaceRing(npc, 24);
        b1Say(npc, "Стадо не ждёт! Круг сжимается!");
    }
    var inner = Number(b1WGet(npc, "hs_b1_ring_inner", "24"));
    var step = Number(b1WGet(npc, "hs_b1_ring_i", "0"));
    var nextAt = Number(b1WGet(npc, "hs_b1_ring_at", "0"));
    if (step < 12 && b1WGet(npc, "hs_b1_ring_warn", "0") == "0" && now >= nextAt - 40) {
        var c = hsArenaCenter(npc);
        TelegraphAPI.circleAt(npc, c[0], c[1], c[2], b1RingInner(step + 1), 40, HS.RED);
        b1WPut(npc, "hs_b1_ring_warn", 1);
    }
    if (now >= nextAt) {
        if (step < 12) step += 1;
        inner = b1RingInner(step);
        b1WPut(npc, "hs_b1_ring_i", step);
        b1WPut(npc, "hs_b1_ring_inner", inner);
        b1WPut(npc, "hs_b1_ring_at", now + 100);
        b1WPut(npc, "hs_b1_ring_warn", 0);
        b1PlaceRing(npc, inner);
    }
    b1RingDamage(npc, ps, inner);
}

function b1Died(e) {
    var npc = e.npc;
    var other = b1Other(npc);
    if (other != null) {
        b1WPut(npc, "hs_b1_corpse_" + String(npc.getName()), String(npc.getUUID()));
        e.droppedItems = null;
        e.expDropped = 0;
    }
    b1OnDied(npc);
}

function b1OnDied(npc) {
    AbilityAPI.cancel(npc);
    hsZoneClear(npc);
    npc.getTimers().clear();
    var other = b1Other(npc);
    if (String(npc.getName()) == B1_GRUL) {
        b1WPut(npc, "hs_b1_roar_until", 0);
        b1WPut(npc, "hs_b1_next_roar", 0);
        if (other != null) b1FillStakes(npc);
    }
    if (String(npc.getName()) == B1_BOAR && hsGet(npc, "b1_mounted", "0") == "1") b1DropRider(npc);
    else hsPut(npc, "b1_mounted", 0);
    if (other != null) {
        hsPut(other, "b1_mounted", 0);
        hsPut(other, "b1_mounting", 0);
        hsPut(other, "b1_step", -1);
        if (hsNum(other, "ram_at") > hsNow(npc) + 10000) hsPut(other, "ram_at", hsNow(npc) + 40);
        hsPut(other, "widow_at", hsNow(npc));
        return;
    }
    b1ClearRing(npc);
    hsSetGate(npc, false);
    b1Say(npc, "Путь вниз открыт.");
}

function b1ClearRide(npc) {
    if (npc == null) return;
    hsPut(npc, "b1_mounted", 0);
    hsPut(npc, "b1_mounting", 0);
    hsPut(npc, "b1_mount_done", 0);
    hsPut(npc, "b1_mount_go", 0);
    hsPut(npc, "b1_recover", 0);
    hsPut(npc, "b1_haul", 0);
    hsPut(npc, "b1_step", -1);
    hsPut(npc, "b1_ritual", 0);
    hsPut(npc, "widowed", 0);
    hsPut(npc, "widow_at", 0);
    hsPut(npc, "stun", 0);
    hsPut(npc, "stun_until", 0);
    AbilityAPI.cancel(npc);
    npc.getTimers().clear();
}

function b1DismountPair(npc) {
    var grul = null;
    var boar = null;
    var name = String(npc.getName());
    if (name == B1_GRUL) grul = npc;
    else if (name == B1_BOAR) boar = npc;
    var other = b1Other(npc);
    if (other != null) {
        var otherName = String(other.getName());
        if (otherName == B1_GRUL) grul = other;
        else if (otherName == B1_BOAR) boar = other;
    }
    if (grul != null) b1ClearRide(grul);
    if (boar != null) b1ClearRide(boar);
    if (grul != null) {
        try { HerdstoneMount.dismount(grul); } catch (err) {}
        b1Release(grul);
    }
    if (boar != null) b1Release(boar);
}

// Грул-наездник может не получить свой тик. Сбрасываем обоих, иначе он остаётся ниже 60% и фаза 2 не кончается.
function hsOnEmptyReset(npc) {
    var other = b1Other(npc);
    b1DismountPair(npc);
    hsPut(npc, "fight_at", 0);
    hsPut(npc, "empty_at", 0);
    if (other != null) {
        hsPut(other, "fight_at", 0);
        hsPut(other, "empty_at", 0);
    }
    b1WPut(npc, "hs_b1_seeded", "0");
    b1WPut(npc, "hs_b1_noremount", "0");
    npc.reset();
    if (other != null && other.isAlive()) other.reset();
}

// Поводок ядра (телепорт в центр и 25% здоровья) на этой паре выключен.
function hsLeash(npc) {}
