// hs_core.js — общая библиотека Herdstone. В контейнере скрипта NPC это ПЕРВЫЙ файл.
// Файл босса или моба ниже переопределяет поля HS, CENTER, FLOOR, ORIGIN, GATE, SUMMONS
// и функции init/tick/timer/damaged/died. В ядре событий нет, только функции hs*.
var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");
var TelegraphAPI = Java.type("noppes.npcs.telegraph.TelegraphAPI");
var ZoneAPI = Java.type("noppes.npcs.zone.ZoneAPI");
var Combat = Java.type("noppes.npcs.abilities.AbilityCombatHelper");

var HS = {
    TAB: 2,
    ARENA_R: 20,
    ARENA: null,       // { shape: "rect", hw, hd } — полуширина и полуглубина от центра. null = круг ARENA_R
    CAP: 12,
    WEAK: 0.25,
    BASE_HP: 300,
    DMG: 1,
    ENRAGE_SEC: 360,
    DISPEL_SEC: 50,
    EMPTY_SEC: 15,
    RED: 0xC0FF3030 | 0,
    GOLD: 0xC0FFC030 | 0,
    PURPLE: 0xC0B040FF | 0
};

// Запасная мировая точка [x, y, z], если строитель не записал hs_origin. Пусто = спаун NPC.
var ORIGIN = [];
// Локальный центр арены от точки постройки: [x, dy, z]. dy обычно 0, этаж задаёт FLOOR.
var CENTER = [];
var FLOOR = 1;          // 1..4 → смещение пола 0, -24, -48, -72
var GATE = [];
var GATE_BLOCK = "minecraft:polished_blackstone_bricks";
// Имена клонов, которых init/сброс убирает. Своё имя босса сюда не писать.
var SUMMONS = [];

var HS_ZONE = "hs_zone";

function hsFloorDy(floor) {
    var f = Number(floor);
    if (f == 2) return -24;
    if (f == 3) return -48;
    if (f == 4) return -72;
    return 0;
}

function hsWorldOrigin(world) {
    if (world == null) return null;
    var data = world.getStoreddata();
    if (data == null || !data.has("hs_origin")) return null;
    var parts = String(data.get("hs_origin")).split(",");
    if (parts.length < 3) return null;
    var x = Number(parts[0]);
    var y = Number(parts[1]);
    var z = Number(parts[2]);
    if (isNaN(x) || isNaN(y) || isNaN(z)) return null;
    return [x, y, z];
}

function hsLayoutCenter(npc) {
    if (CENTER != null && CENTER.length >= 3 && hsWorldOrigin(npc.getWorld()) != null) return true;
    return ORIGIN != null && ORIGIN.length >= 3;
}

// Мировой центр арены. Сначала точка постройки + CENTER + этаж, иначе ручной ORIGIN, иначе home.
function hsOrigin(npc) {
    if (CENTER != null && CENTER.length >= 3) {
        var base = hsWorldOrigin(npc.getWorld());
        if (base != null) {
            var dy = CENTER[1];
            if (isNaN(dy)) dy = 0;
            return [base[0] + CENTER[0], base[1] + hsFloorDy(FLOOR) + dy, base[2] + CENTER[2]];
        }
    }
    if (ORIGIN != null && ORIGIN.length >= 3) return ORIGIN;
    return [npc.getHomeX(), npc.getHomeY(), npc.getHomeZ()];
}

function hsXYZ(npc, off) {
    var o = hsOrigin(npc);
    return [o[0] + off[0], o[1] + off[1], o[2] + off[2]];
}

function hsIPosOff(npc, off) {
    var a = hsXYZ(npc, off);
    return NpcAPI.getIPos(a[0], a[1], a[2]);
}

function hsSpawnAt(npc, off, name) {
    var a = hsXYZ(npc, off);
    return npc.getWorld().spawnClone(a[0] + 0.5, a[1], a[2] + 0.5, HS.TAB, name);
}

function hsBox(dx, dy, dz, w, h, d) {
    var out = [];
    var x, y, z;
    for (y = 0; y < h; y++) {
        for (z = 0; z < d; z++) {
            for (x = 0; x < w; x++) out.push([dx + x, dy + y, dz + z]);
        }
    }
    return out;
}

function hsArena() {
    var spec = HS.ARENA;
    if (spec != null && spec.shape == "rect") {
        var hw = Number(spec.hw);
        var hd = Number(spec.hd);
        if (hw > 0 && hd > 0) {
            return { shape: "rect", hw: hw, hd: hd, r: hw > hd ? hw : hd };
        }
    }
    var r = Number(HS.ARENA_R);
    if (!(r > 0)) r = 20;
    return { shape: "circle", hw: r, hd: r, r: r };
}

// Круг — home NPC (как раньше). Прямоугольник — центр раскладки, ноги на полу (origin Y + 1).
function hsArenaCenter(npc) {
    if (hsArena().shape != "rect" || !hsLayoutCenter(npc)) return hsHome(npc);
    var o = hsOrigin(npc);
    return [o[0], o[1] + 1, o[2]];
}

function hsInArena(entity, x, z, pad) {
    var a = hsArena();
    var extra = pad || 0;
    var dx = entity.getX() - x;
    var dz = entity.getZ() - z;
    if (a.shape == "rect") {
        if (dx < 0) dx = -dx;
        if (dz < 0) dz = -dz;
        return dx <= a.hw + extra && dz <= a.hd + extra;
    }
    return Math.sqrt(dx * dx + dz * dz) <= a.r + extra;
}

function hsScanRange() {
    var a = hsArena();
    var half = a.r;
    if (a.shape == "rect") half = Math.sqrt(a.hw * a.hw + a.hd * a.hd);
    return Math.ceil(half) + 10;
}

function hsNow(npc) { return Number(npc.getWorld().getTotalTime()); }
function hsGet(npc, k, def) { var d = npc.getStoreddata(); return d.has(k) ? String(d.get(k)) : def; }
function hsNum(npc, k) { return Number(hsGet(npc, k, "0")); }
function hsPut(npc, k, v) { npc.getStoreddata().put(k, String(v)); }
function hsHome(npc) { return [npc.getHomeX() + 0.5, npc.getHomeY(), npc.getHomeZ() + 0.5]; }
function hsFlat(e, x, z) { var dx = e.getX() - x, dz = e.getZ() - z; return Math.sqrt(dx * dx + dz * dz); }
function hsRandom(arr) { return arr[Math.floor(Math.random() * arr.length)]; }
function hsYawTo(npc, t) { return Combat.computeYaw(t.getX() - npc.getX(), t.getZ() - npc.getZ()); }

function hsPlayersAt(npc, x, y, z, r) {
    var list = npc.getWorld().getNearbyEntities(NpcAPI.getIPos(x, y, z), Math.ceil(r) + 1, 1);
    var out = [];
    for (var i = 0; i < list.length; i++) {
        var p = list[i];
        if (!p.isAlive() || p.getGamemode() == 1 || p.getGamemode() == 3) continue;
        if (hsFlat(p, x, z) <= r) out.push(p);
    }
    return out;
}

// pad — запас за краем арены (поводок ищет вышедших). 0 = строго внутри.
function hsPlayers(npc, pad) {
    var extra = pad || 0;
    var c = hsArenaCenter(npc);
    var a = hsArena();
    var reach = a.r + extra;
    if (a.shape == "rect") {
        var hw = a.hw + extra;
        var hd = a.hd + extra;
        reach = Math.sqrt(hw * hw + hd * hd);
    }
    var list = npc.getWorld().getNearbyEntities(NpcAPI.getIPos(c[0], c[1], c[2]), Math.ceil(reach) + 1, 1);
    var out = [];
    for (var i = 0; i < list.length; i++) {
        var p = list[i];
        if (!p.isAlive() || p.getGamemode() == 1 || p.getGamemode() == 3) continue;
        if (hsInArena(p, c[0], c[2], extra)) out.push(p);
    }
    return out;
}

function hsFind(npc, name) {
    var c = hsArenaCenter(npc);
    var list = npc.getWorld().getNearbyEntities(NpcAPI.getIPos(c[0], c[1], c[2]), hsScanRange(), 2);
    var out = [];
    var wanted = String(name);
    for (var i = 0; i < list.length; i++) {
        if (String(list[i].getName()) == wanted && list[i].isAlive()) out.push(list[i]);
    }
    return out;
}

function hsCount(npc, name) { return hsFind(npc, name).length; }

function hsDespawnAll(npc, name) {
    var list = hsFind(npc, name);
    for (var i = 0; i < list.length; i++) list[i].despawn();
}

function hsDespawnSummons(npc) {
    if (SUMMONS == null) return;
    var selfName = String(npc.getName());
    for (var i = 0; i < SUMMONS.length; i++) {
        var name = String(SUMMONS[i]);
        if (name.length == 0 || name == selfName) continue;
        hsDespawnAll(npc, name);
    }
}

function hsScale(world) {
    var v = HS.DMG;
    if (world != null) {
        var d = world.getStoreddata();
        if (d.has("hs_dmg")) {
            var n = Number(String(d.get("hs_dmg")));
            if (n > 0) v = n;
        }
    }
    return v;
}

function hsStart(npc, id, target, params) {
    if (params != null) {
        var s = hsScale(npc.getWorld());
        var keys = ["damage", "damagePerTick"];
        var i;
        for (i = 0; i < keys.length; i++) {
            var k = keys[i];
            if (params.containsKey(k)) params.put(k, Number(params.get(k)) * s);
        }
        return AbilityAPI.start(npc, id, target, params);
    }
    return AbilityAPI.start(npc, id, target);
}

function hsBossHp(npc, nPlayers) {
    var n = nPlayers < 1 ? 1 : nPlayers;
    return Math.max(1, Math.round(HS.BASE_HP * (0.7 + 0.3 * n) * hsScale(npc.getWorld())));
}

function hsStrike(p, pct) {
    pct = pct * hsScale(p.getWorld());
    var dmg = p.getMaxHealth() * pct;
    if (p.getHealth() - dmg < 1) { hsKill(p); return; }
    p.setHealth(p.getHealth() - dmg);
    p.getWorld().playSoundAt(p.getPos(), "minecraft:entity.player.hurt", 1.0, 0.7);
}

function hsKill(p) {
    p.clearPotionEffects();
    p.setHealth(1);
    Combat.dealPureDamage(p, 100, true);
}

function hsDispel(npc, ps) {
    for (var i = 0; i < ps.length; i++) {
        ps[i].clearPotionEffects();
        ps[i].message("§5Рёв Стада срывает с тебя все чары.");
    }
    npc.getWorld().playSoundAt(npc.getPos(), "minecraft:entity.ravager.roar", 3.0, 0.6);
}

function hsCap(e, mul) {
    if (mul <= 0) { e.setCanceled(true); return; }
    e.damage = Math.min(e.damage, HS.CAP) * mul;
}

function hsOnLine(p, sx, sz, yaw, len, half) {
    var rad = (yaw + 90) * Math.PI / 180;
    var fx = Math.cos(rad), fz = Math.sin(rad);
    var dx = p.getX() - sx, dz = p.getZ() - sz;
    var t = dx * fx + dz * fz;
    if (t < -1 || t > len) return false;
    return Math.abs(dx * fz - dz * fx) <= half;
}

function hsSetGate(npc, closed) {
    var w = npc.getWorld();
    for (var i = 0; i < GATE.length; i++) {
        var pos = hsIPosOff(npc, GATE[i]);
        if (closed) w.setBlock(pos, GATE_BLOCK);
        else w.removeBlock(pos);
    }
}

function hsZonePut(npc, zone) {
    hsZoneClear(npc);
    if (zone == null) return;
    npc.getTempdata().put(HS_ZONE, zone);
}

function hsZoneClear(npc) {
    var data = npc.getTempdata();
    if (data == null || !data.has(HS_ZONE)) return;
    var zone = data.get(HS_ZONE);
    data.remove(HS_ZONE);
    if (zone == null) return;
    try {
        ZoneAPI.remove(zone);
    } catch (err) {
        log("herdstone zone: " + err);
    }
}

// false — NPC мёртв (init пришёл на трупе), дальше не идти.
function hsInit(npc) {
    if (!npc.isAlive()) return false;
    if (CENTER != null && CENTER.length >= 3 && hsWorldOrigin(npc.getWorld()) == null && (ORIGIN == null || ORIGIN.length < 3)) {
        log("herdstone: нет hs_origin, центр арены = home NPC");
    }
    npc.getTimers().clear();
    AbilityAPI.cancel(npc);
    hsZoneClear(npc);
    hsDespawnSummons(npc);
    hsPut(npc, "fight_at", 0);
    hsPut(npc, "empty_at", 0);
    hsPut(npc, "enrage_at", 0);
    var hp0 = hsBossHp(npc, 1);
    npc.getStats().setMaxHealth(hp0);
    npc.setHealth(hp0);
    if (GATE.length > 0) hsSetGate(npc, true);
    return true;
}

function hsFight(npc) {
    var now = hsNow(npc);
    var ps = hsPlayers(npc);
    var fightAt = hsNum(npc, "fight_at");
    if (fightAt == 0) {
        if (ps.length == 0 || npc.getAttackTarget() == null) return null;
        var hp = hsBossHp(npc, ps.length);
        npc.getStats().setMaxHealth(hp);
        npc.setHealth(hp);
        hsPut(npc, "fight_at", now);
        hsPut(npc, "dispel_at", now + HS.DISPEL_SEC * 20);
        return ps;
    }
    if (ps.length == 0) {
        var emptyAt = hsNum(npc, "empty_at");
        if (emptyAt == 0) hsPut(npc, "empty_at", now);
        else if (now - emptyAt > HS.EMPTY_SEC * 20) npc.reset();
        return null;
    }
    hsPut(npc, "empty_at", 0);
    hsLeash(npc);
    if (now >= hsNum(npc, "dispel_at")) {
        hsDispel(npc, ps);
        hsPut(npc, "dispel_at", now + HS.DISPEL_SEC * 20);
    }
    if (now - fightAt > HS.ENRAGE_SEC * 20 && now >= hsNum(npc, "enrage_at")) {
        hsPut(npc, "enrage_at", now + 200);
        npc.say("§4Стадо не ждёт!");
        hsKill(hsRandom(ps));
    }
    return ps;
}

// Кто вышел за край (до 8 блоков) или поднялся выше пола на 4+ — в центр и −25% HP.
function hsLeash(npc) {
    var c = hsArenaCenter(npc);
    var ps = hsPlayers(npc, 8);
    for (var i = 0; i < ps.length; i++) {
        var p = ps[i];
        if (hsInArena(p, c[0], c[2], 0) && p.getY() <= c[1] + 4) continue;
        p.setPosition(c[0], c[1], c[2]);
        hsStrike(p, 0.25);
        p.message("§cСтадо швыряет тебя обратно в круг.");
    }
}
