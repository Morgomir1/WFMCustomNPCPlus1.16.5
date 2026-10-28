// eye.js — Око Моррслиба, ведущий ивента. Невидимый неуязвимый NPC в центре поля; вторым файлом после mo_core.js.
// Ведёт время ивента, волны тайников, вскрытие тайников и свечение несущих осколки.
// Запуск и остановка — ПКМ по Оку в креативе. Шифт + ПКМ в креативе — стак осколков (для сделок алтаря).
// Ход ивента лежит в данных мира: после рестарта Око продолжает с того же места.
// Кто был на поле с осколками и вышел, роняет их на месте выхода (скрипт игрока, logout в mo_core.js), из мешка тоже.
// Рестарт logout не вызывает: таких Око при следующем входе лишает осколков, на землю они уже не попадут.

// Тайники: блок [x, y, z] каждого. Сам тайник на поле — декорация строителя; скрипт ведёт только его свет.
var CACHES = [];
var EVENT_MIN = 40;
var FIRST_WAVE = 200, WAVE_EVERY = 3600, WAVE_SIZE = 4;
// Вскрыть: стоять ближе OPEN_R столько тиков; урон сбивает.
var CACHE_SHARDS = 3, OPEN_R = 2.0, OPEN_TICKS = 80;
var WARN_MIN = [10, 5, 1];

var D_LIT = "mo_lit", D_WAVE = "mo_wave_at", D_WARNED = "mo_warned", T_OPEN = "mo_open_";

function eData(npc) { return npc.getWorld().getStoreddata(); }
function eGet(npc, k, def) { var d = eData(npc); return d.has(k) ? String(d.get(k)) : def; }
function ePut(npc, k, v) { eData(npc).put(k, String(v)); }

function eLit(npc) {
    var s = eGet(npc, D_LIT, "");
    var out = [];
    if (s.length == 0) return out;
    var parts = s.split(",");
    for (var i = 0; i < parts.length; i++) out.push(Number(parts[i]));
    return out;
}

function eSetLit(npc, lit) { ePut(npc, D_LIT, lit.join(",")); }

function eTell(ps, text) { for (var i = 0; i < ps.length; i++) ps[i].message(text); }
function eSound(ps, sound, volume, pitch) { for (var i = 0; i < ps.length; i++) ps[i].playSound(sound, volume, pitch); }

function init(e) {
    var npc = e.npc;
    eClearOpening(npc);
}

function interact(e) {
    var p = e.player, npc = e.npc;
    if (p.getGamemode() != 1) return;
    e.setCanceled(true);
    if (p.isSneaking()) {
        moGive(p, 64);
        p.message("§7Стак осколков — для сделок алтаря.");
        return;
    }
    if (moRunning(npc.getWorld())) eStop(npc, "§7[Око Моррслиб] Ивент остановлен.");
    else eStart(npc);
}

function eStart(npc) {
    var w = npc.getWorld(), now = moNow(w);
    ePut(npc, MO_RUN, 1);
    ePut(npc, MO_END, now + EVENT_MIN * 1200);
    ePut(npc, D_WAVE, now + FIRST_WAVE);
    ePut(npc, D_WARNED, "");
    eSetLit(npc, []);
    eClearOpening(npc);
    w.broadcast("§a[Око Моррслиб] §fМоррслиб открыл глаз. Ивент идёт " + EVENT_MIN + " минут: собирайте осколки и сдавайте у алтаря.");
    if (CACHES.length == 0) log("morrslieb: впиши CACHES — без тайников ивенту нечего раздавать");
}

function eStop(npc, text) {
    ePut(npc, MO_RUN, 0);
    ePut(npc, MO_CARRIERS, "");
    eSetLit(npc, []);
    eClearOpening(npc);
    npc.getWorld().broadcast(text);
}

function tick(e) {
    var npc = e.npc, w = npc.getWorld();
    var online = eOnline(w);
    eWipe(npc, online);
    if (!moRunning(w)) return;
    var now = moNow(w);
    var end = Number(eGet(npc, MO_END, "0"));
    if (now >= end) {
        eStop(npc, "§a[Око Моррслиб] §fМоррслиб закрыл глаз. Ивент окончен; осколки сдаются у алтаря, пока он стоит.");
        return;
    }
    var ps = moPlayers(w);
    eWarn(npc, ps, end - now);
    if (now >= Number(eGet(npc, D_WAVE, "0"))) eWave(npc, ps, now);
    var counts = eGlowAll(w, ps, now);
    eTrack(npc, ps, counts, online);
    eCaches(npc, ps, now);
}

function eWarn(npc, ps, left) {
    var warned = eGet(npc, D_WARNED, "");
    for (var i = 0; i < WARN_MIN.length; i++) {
        var m = WARN_MIN[i], key = "," + m + ",";
        if (left > m * 1200 || warned.indexOf(key) >= 0) continue;
        warned += key;
        eTell(ps, "§a[Око Моррслиб] §fДо конца ивента " + m + (m == 1 ? " минута." : " минут."));
    }
    ePut(npc, D_WARNED, warned);
}

// Волна: загораются до WAVE_SIZE тёмных тайников; Кигор, если жив, воет (сигнал через данные мира).
function eWave(npc, ps, now) {
    ePut(npc, D_WAVE, now + WAVE_EVERY);
    var lit = eLit(npc), dark = [];
    for (var i = 0; i < CACHES.length; i++) if (lit.indexOf(i) < 0) dark.push(i);
    var n = 0;
    while (dark.length > 0 && n < WAVE_SIZE) {
        var k = Math.floor(Math.random() * dark.length);
        lit.push(dark[k]);
        dark.splice(k, 1);
        n++;
    }
    if (n == 0) return;
    eSetLit(npc, lit);
    npc.getWorld().getTempdata().put(MO_HOWL, String(now));
    eTell(ps, "§a[Око Моррслиб] §fМоррслиб восходит: загорелись новые тайники.");
    eSound(ps, "minecraft:block.beacon.activate", 1.0, 0.6);
}

// Свечение несущих; возвращает, у кого сколько осколков.
function eGlowAll(w, ps, now) {
    var marks = moMarksRead(w), counts = {};
    for (var i = 0; i < ps.length; i++) {
        var n = moShards(ps[i]);
        counts[String(ps[i].getUUID())] = n;
        moGlow(w, ps[i], n, moMarked(marks, ps[i], now));
    }
    return counts;
}

function eOnline(w) {
    var all = w.getAllPlayers(), out = {};
    for (var i = 0; i < all.length; i++) out[String(all[i].getUUID())] = all[i];
    return out;
}

// Несущие: кто был на поле с осколками и пока их не лишился, где бы ни был.
// Пропал без logout (рестарт) — в список на потерю при входе. Обычный выход снимает сам logout.
function eTrack(npc, ps, counts, online) {
    var w = npc.getWorld();
    var prev = moCsv(w, MO_CARRIERS), wipe = moCsv(w, MO_WIPE), next = [];
    for (var i = 0; i < prev.length; i++) {
        var id = prev[i], p = online[id];
        if (p == null) {
            if (wipe.indexOf(id) < 0) wipe.push(id);
        } else if ((counts[id] != null ? counts[id] : moShards(p)) > 0) {
            next.push(id);
        }
    }
    for (var k = 0; k < ps.length; k++) {
        var uid = String(ps[k].getUUID());
        if (counts[uid] > 0 && next.indexOf(uid) < 0) next.push(uid);
    }
    ePut(npc, MO_CARRIERS, next.join(","));
    ePut(npc, MO_WIPE, wipe.join(","));
}

// Рестарт не вызывает logout, поэтому осколки ещё при игроке. При входе Око их забирает, на землю не кладёт:
// игрок уже стоит на этом месте и сразу подобрал бы кучу.
function eWipe(npc, online) {
    var wipe = moCsv(npc.getWorld(), MO_WIPE);
    if (wipe.length == 0) return;
    var left = [];
    for (var i = 0; i < wipe.length; i++) {
        var p = online[wipe[i]];
        if (p == null) {
            left.push(wipe[i]);
            continue;
        }
        var n = moTakeAllShards(p);
        if (n > 0) p.message("§cСервер оборвал выход с осколками Моррслиба – они пропали, а не выпали. Потеряно: " + n + ".");
    }
    ePut(npc, MO_WIPE, left.join(","));
}

// Свет горящих тайников и вскрытие: кто ближе OPEN_R, тот вскрывает; ушёл или получил урон — сначала.
// У каждого тайника своё вскрытие: несколько тайников можно вскрывать разом.
function eCaches(npc, ps, now) {
    var w = npc.getWorld(), lit = eLit(npc);
    for (var i = 0; i < lit.length; i++) {
        var c = CACHES[lit[i]];
        if (c == null) continue;
        w.spawnParticle("happy_villager", c[0] + 0.5, c[1] + 1.5, c[2] + 0.5, 0.2, 1.2, 0.2, 0, 6);
        w.spawnParticle("end_rod", c[0] + 0.5, c[1] + 3, c[2] + 0.5, 0.05, 2, 0.05, 0.01, 2);
        eOpening(npc, ps, lit[i], c, now);
    }
}

function eOpening(npc, ps, idx, c, now) {
    var td = npc.getTempdata(), key = T_OPEN + idx;
    if (td.has(key)) {
        var o = String(td.get(key)).split(":");   // uuid:с какого тика:здоровье
        var who = null;
        for (var j = 0; j < ps.length; j++) if (String(ps[j].getUUID()) == o[0]) who = ps[j];
        if (who == null) {
            td.remove(key);
        } else if (eDist(who, c) > OPEN_R || who.getHealth() < Number(o[2])) {
            td.remove(key);
            who.message("§cВскрытие сорвано.");
        } else if (now - Number(o[1]) >= OPEN_TICKS) {
            td.remove(key);
            eOpen(npc, ps, who, idx);
            return;
        } else {
            npc.getWorld().spawnParticle("happy_villager", c[0] + 0.5, c[1] + 1.0, c[2] + 0.5, 0.6, 0.4, 0.6, 0, 8);
            return;
        }
    }
    var p = eNearest(ps, c);
    if (p == null) return;
    td.put(key, p.getUUID() + ":" + now + ":" + p.getHealth());
    p.message("§aВскрываешь тайник… Стой рядом и не получай урона.");
}

function eClearOpening(npc) {
    var td = npc.getTempdata();
    for (var i = 0; i < CACHES.length; i++) td.remove(T_OPEN + i);
}

function eDist(p, c) {
    var dx = p.getX() - (c[0] + 0.5), dy = p.getY() - c[1], dz = p.getZ() - (c[2] + 0.5);
    return Math.sqrt(dx * dx + dy * dy * 0.25 + dz * dz);
}

function eNearest(ps, c) {
    if (c == null) return null;
    var best = null, bd = OPEN_R;
    for (var i = 0; i < ps.length; i++) {
        var d = eDist(ps[i], c);
        if (d <= bd) { bd = d; best = ps[i]; }
    }
    return best;
}

function eOpen(npc, ps, p, idx) {
    var lit = eLit(npc);
    lit.splice(lit.indexOf(idx), 1);
    eSetLit(npc, lit);
    moGive(p, CACHE_SHARDS);
    var c = CACHES[idx], w = npc.getWorld();
    w.spawnParticle("happy_villager", c[0] + 0.5, c[1] + 4, c[2] + 0.5, 0.4, 4, 0.4, 0, 60);
    w.spawnParticle("end_rod", c[0] + 0.5, c[1] + 6, c[2] + 0.5, 0.2, 6, 0.2, 0.02, 30);
    eSound(ps, "minecraft:block.beacon.power_select", 1.0, 1.4);
    p.message("§aТайник вскрыт: +" + CACHE_SHARDS + " осколка Моррслиба.");
    eTell(ps, "§a[Око Моррслиб] §fКто-то вскрыл тайник – над ним столб света.");
}
