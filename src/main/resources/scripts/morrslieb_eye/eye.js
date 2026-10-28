// eye.js — Око Моррслиба, ведущий ивента. Невидимый неуязвимый NPC в центре поля; вторым файлом после mo_core.js.
// Ведёт волны тайников, вскрытие и свечение несущих осколки. Ивент включён, пока его не выключат.
// ПКМ по Оку в креативе переключает ивент: вкл/выкл. Сам он не кончается, идёт пока не выключат.
// Шифт + ПКМ в креативе — стак осколков (для сделок алтаря).
// Ход ивента лежит в данных мира: после рестарта Око продолжает с того же места.
// Кто был на поле с осколками и вышел, роняет их на месте выхода (скрипт игрока, logout в mo_core.js), из мешка тоже.
// Рестарт logout не вызывает: таких Око при следующем входе лишает осколков, на землю они уже не попадут.
// Охота: за несущим осколки Око вызывает Кигора клоном из клонера — сначала столб света рядом с игроком, потом он.
// Кигор не бродит по полю (иначе держать загруженными все чанки арены): живёт, пока гонится, и рассыпается.

// Кигор — клон из клонера: вкладка KIGOR_TAB, имя KIGOR_NAME. На поле его не ставить, Око вызывает его само.
var KIGOR_TAB = 1, KIGOR_NAME = "Кигор";
// Одновременно не больше MAX_KIGORS. Несущего ближе GROUP_R к Кигору или к тому, за кем уже идут, Око не выдаёт.
var MAX_KIGORS = 4, GROUP_R = 20;
// Столб в SUMMON_MIN–SUMMON_MAX блоках от игрока, через SUMMON_WARN тиков там встаёт Кигор.
var SUMMON_MIN = 12, SUMMON_MAX = 16, SUMMON_WARN = 40;

// Тайники: блок [x, y, z] каждого. Сам тайник на поле — декорация строителя; скрипт ведёт только его свет.
var CACHES = [
    [-484146, 83, -37422],
    [-483852, 80, -36954],
    [-484124, 83, -36768],
    [-484215, 80, -36855],
    [-484300, 80, -37272],
    [-483852, 80, -37276],
    [-483949, 80, -37119],
    [-484153, 80, -37120]
];
var FIRST_WAVE = 200, WAVE_EVERY = 3600, WAVE_SIZE = 4;
// Вскрыть: стоять ближе OPEN_R столько тиков; урон сбивает.
var CACHE_SHARDS = 3, OPEN_R = 2.0, OPEN_TICKS = 80;
// Старт и конец ивента в глобальный чат. 0 — молчать (тест). Перед боевым ивентом поставь 1.
var GLOBAL_CHAT = 0;

var D_LIT = "mo_lit", D_WAVE = "mo_wave_at", T_OPEN = "mo_open_", T_SUMMONS = "mo_summons";

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
function eBroadcast(w, text) { if (GLOBAL_CHAT == 1) w.broadcast(text); }
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
    if (moRunning(npc.getWorld())) {
        eStop(npc, "§7[Око Моррслиб] Ивент остановлен.");
        if (GLOBAL_CHAT != 1) p.message("§7[Око Моррслиб] Ивент остановлен. Глобальный чат молчит.");
    } else {
        eStart(npc);
        if (GLOBAL_CHAT != 1) p.message("§7[Око Моррслиб] Ивент запущен. Глобальный чат молчит.");
    }
}

function eStart(npc) {
    var w = npc.getWorld(), now = moNow(w);
    ePut(npc, MO_RUN, 1);
    ePut(npc, D_WAVE, now + FIRST_WAVE);
    eSetLit(npc, []);
    eClearOpening(npc);
    eBroadcast(w, "§a[Око Моррслиб] §fМоррслиб открыл глаз. Собирайте осколки и сдавайте у алтаря, пока глаз открыт.");
    if (CACHES.length == 0) log("morrslieb: впиши CACHES — без тайников ивенту нечего раздавать");
}

function eStop(npc, text) {
    ePut(npc, MO_RUN, 0);
    ePut(npc, MO_CARRIERS, "");
    eSetLit(npc, []);
    eClearOpening(npc);
    npc.getTempdata().remove(T_SUMMONS);
    eBroadcast(npc.getWorld(), text);
}

function tick(e) {
    var npc = e.npc, w = npc.getWorld();
    var online = eOnline(w);
    eWipe(npc, online);
    if (!moRunning(w)) return;
    var now = moNow(w);
    var ps = moPlayers(w);
    if (now >= Number(eGet(npc, D_WAVE, "0"))) eWave(npc, ps, now);
    var counts = eGlowAll(w, ps, now);
    eTrack(npc, ps, counts, online);
    eHunt(npc, ps, counts, now);
    eCaches(npc, ps, now);
}

// --- Охота ---

// Самых ярких несущих, за кем никто не идёт, у кого нет паузы и кто не у алтаря, Око выдаёт Кигору.
function eHunt(npc, ps, counts, now) {
    var w = npc.getWorld();
    var summons = eSummonsDue(npc, now);
    var kigors = eKigors(w), cds = moHuntCdRead(w);
    var carriers = [];
    for (var i = 0; i < ps.length; i++) {
        var n = counts[String(ps[i].getUUID())] || 0;
        if (n > 0) carriers.push([n, ps[i]]);
    }
    carriers.sort(function (a, b) { return b[0] - a[0]; });
    for (var k = 0; k < carriers.length && kigors.length + summons.length < MAX_KIGORS; k++) {
        var p = carriers[k][1], id = String(p.getUUID());
        if (cds[id] > now || moInSanctuary(p.getX(), p.getZ()) || eCovered(p, ps, kigors, summons)) continue;
        var at = eSummonPoint(w, p);
        if (at == null) continue;
        var s = { id: id, x: at[0], y: at[1], z: at[2], at: now + SUMMON_WARN };
        summons.push(s);
        eColumn(w, s);
        p.message("§2Моррслиб видит твой свет. За тобой идёт Кигор.");
        p.playSound("minecraft:entity.wolf.howl", 1.0, 0.5);
    }
    npc.getTempdata().put(T_SUMMONS, eSummonsText(summons));
}

// Охотящиеся Кигоры: заготовку для клонера (без добычи) Око не считает.
function eKigors(w) {
    var f = MO.FIELD, out = [];
    var list = w.getNearbyEntities(NpcAPI.getIPos(f[0], f[1], f[2]), MO.FIELD_R, 2);
    for (var i = 0; i < list.length; i++) {
        var k = list[i];
        if (k.isAlive() && String(k.getName()) == KIGOR_NAME && k.getStoreddata().has("hunt")) out.push(k);
    }
    return out;
}

// За ним уже идут: его Кигор (как далеко бы он ни убежал), Кигор рядом или тот, за кем идёт вызов.
function eCovered(p, ps, kigors, summons) {
    var id = String(p.getUUID());
    for (var i = 0; i < kigors.length; i++) {
        var hunt = kigors[i].getStoreddata();
        if (hunt.has("hunt") && String(hunt.get("hunt")) == id) return true;
        if (moFlat(p, kigors[i].getX(), kigors[i].getZ()) <= GROUP_R) return true;
    }
    for (var j = 0; j < summons.length; j++) {
        if (summons[j].id == String(p.getUUID())) return true;
        for (var k = 0; k < ps.length; k++) {
            if (String(ps[k].getUUID()) == summons[j].id && moFlat(p, ps[k].getX(), ps[k].getZ()) <= GROUP_R) return true;
        }
    }
    return false;
}

// Точка на земле в SUMMON_MIN–SUMMON_MAX от игрока, не у алтаря; восемь попыток.
function eSummonPoint(w, p) {
    for (var i = 0; i < 8; i++) {
        var a = Math.random() * Math.PI * 2, d = SUMMON_MIN + Math.random() * (SUMMON_MAX - SUMMON_MIN);
        var x = p.getX() + Math.cos(a) * d, z = p.getZ() + Math.sin(a) * d;
        if (moInSanctuary(x, z)) continue;
        var g = moGround(w, x, z, p.getY(), 8, 12);
        if (g != null) return [x, g, z];
    }
    return null;
}

// Вызовы: наступившие — Кигор встаёт; остальным — столб. Возвращает оставшиеся.
function eSummonsDue(npc, now) {
    var w = npc.getWorld(), td = npc.getTempdata(), left = [];
    var parts = td.has(T_SUMMONS) ? String(td.get(T_SUMMONS)).split(";") : [];
    for (var i = 0; i < parts.length; i++) {
        var v = parts[i].split(":");
        if (v.length < 5) continue;
        var s = { id: v[0], x: Number(v[1]), y: Number(v[2]), z: Number(v[3]), at: Number(v[4]) };
        if (now < s.at) {
            eColumn(w, s);
            left.push(s);
            continue;
        }
        eSpawnKigor(w, s);
    }
    return left;
}

// Столб света там, где встанет Кигор.
function eColumn(w, s) {
    w.spawnParticle("happy_villager", s.x, s.y + 3, s.z, 0.3, 3, 0.3, 0, 30);
    w.spawnParticle("end_rod", s.x, s.y + 6, s.z, 0.1, 6, 0.1, 0.02, 10);
}

function eSpawnKigor(w, s) {
    var k = null;
    try {
        k = w.spawnClone(s.x, s.y, s.z, KIGOR_TAB, KIGOR_NAME);
    } catch (err) {
        moLogOnce("clone", "Кигор не вызван – проверь KIGOR_TAB и KIGOR_NAME: " + err);
    }
    if (k == null) return;
    k.getStoreddata().put("hunt", s.id);
    // Клон не должен вставать после смерти: spawnCycle 3 — «нет».
    try {
        var mc = k.getMCEntity();
        mc.stats.spawnCycle = 3;
        mc.stats.respawnTime = 0;
    } catch (err) {
        moLogOnce("respawn", "у клона Кигора не выключено возрождение: " + err);
    }
    w.spawnParticle("explosion", s.x, s.y + 1, s.z, 1.0, 0.5, 1.0, 0, 6);
}

function eSummonsText(summons) {
    var parts = [];
    for (var i = 0; i < summons.length; i++) {
        var s = summons[i];
        parts.push(s.id + ":" + s.x.toFixed(2) + ":" + s.y.toFixed(2) + ":" + s.z.toFixed(2) + ":" + s.at);
    }
    return parts.join(";");
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
