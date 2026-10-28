// mo_core.js — общее ядро ивента «Око Моррслиб». В контейнере скрипта ПЕРВЫЙ файл: и у Ока, и у Кигора.
// Тот же файл — единственный скрипт игрока (вкладка Player): на выходе logout роняет осколки под ноги.
// Осколки Моррслиба — предметы с меткой MO.SHARD_TAG в NBT; скрипты сверяют метку, а не тип предмета.
// Считаются и осколки в мешках мода: мешок хранит содержимое в NBT (Pouch.Items).
var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();

var MO = {
    // Поле ивента: центр [x, y, z] и радиус. Игроки дальше — вне ивента. Впиши по готовому полю.
    FIELD: [-484114, 79, -37119],
    FIELD_R: 600,
    // Заглушка, пока нет своего предмета с текстурой. Сменишь — смени и в сделках алтаря.
    SHARD_ID: "wfm:warpstone",
    SHARD_TAG: "MorrsliebShard",
    SHARD_NAME: "§aОсколок Моррслиба",
    SHARD_LORE: ["§7Светится зелёным, пока он при тебе.", "§7Сдаётся у алтаря Моррслиба."],
    // Ударивший Кигора светится столько тиков.
    MARK_TICKS: 260,
    // Яркость: с какого числа осколков начинается каждая ступень.
    TIERS: [1, 5, 12, 25],
    // Алтари [x, y, z]: ближе ALTAR_R Кигор не ступает и никого не бьёт, Око никого там не выдаёт. Впиши по полю.
    ALTARS: [],
    ALTAR_R: 15,
    // Пауза перед следующим Кигором за тем же игроком, в тиках, по ступени яркости 1–4: чем ярче, тем короче.
    HUNT_PAUSE: [1200, 1000, 800, 600]
};

// Данные мира: ход ивента — в постоянных (переживает рестарт), метки ударов и сигнал воя — во временных.
var MO_RUN = "mo_running", MO_MARKS = "mo_marks", MO_HOWL = "mo_howl";
// Кого Око уже видело на поле с осколками, и кого не поймал logout (рестарт) — снять при входе.
var MO_CARRIERS = "mo_carriers", MO_WIPE = "mo_wipe";
// Пауза охоты по игрокам: uuid:до какого тика.
var MO_HUNT_CD = "mo_hunt_cd";
var MO_LOGGED = {};

function moNow(world) { return Number(world.getTotalTime()); }
function moFlat(e, x, z) { var dx = e.getX() - x, dz = e.getZ() - z; return Math.sqrt(dx * dx + dz * dz); }

function moRunning(world) {
    var d = world.getStoreddata();
    return d.has(MO_RUN) && String(d.get(MO_RUN)) == "1";
}

// Одна строка в журнал на причину, чтобы ошибка в тике не забила его.
function moLogOnce(key, text) {
    if (MO_LOGGED[key]) return;
    MO_LOGGED[key] = true;
    log("morrslieb: " + text);
}

// Игроки на поле: живые, не в креативе и не наблюдатели.
function moPlayers(world) {
    var f = MO.FIELD;
    var list = world.getNearbyEntities(NpcAPI.getIPos(f[0], f[1], f[2]), MO.FIELD_R, 1);
    var out = [];
    for (var i = 0; i < list.length; i++) {
        var p = list[i];
        if (!p.isAlive() || p.getGamemode() == 1 || p.getGamemode() == 3) continue;
        if (moFlat(p, f[0], f[2]) <= MO.FIELD_R) out.push(p);
    }
    return out;
}

// --- Осколки ---

function moIsShard(item) {
    if (item == null || item.isEmpty() || String(item.getName()) != MO.SHARD_ID) return false;
    // hasNbt раньше getNbt: getNbt вешает пустой тег на предмет без тега, и такой больше не складывается с обычными.
    return item.hasNbt() && item.getNbt().getBoolean(MO.SHARD_TAG);
}

function moPouchShards(item) {
    if (!item.hasNbt()) return 0;
    var nbt = item.getNbt();
    if (!nbt.has("Pouch")) return 0;
    var pouch = nbt.getCompound("Pouch");
    if (!pouch.has("Items")) return 0;
    var list = pouch.getList("Items", 10);
    var n = 0;
    for (var i = 0; i < list.length; i++) if (moIsShardNbt(list[i])) n += Number(list[i].getByte("Count"));
    return n;
}

// Запись предмета в мешке: {id, Count, tag}.
function moIsShardNbt(e) {
    return String(e.getString("id")) == MO.SHARD_ID && e.has("tag") && e.getCompound("tag").getBoolean(MO.SHARD_TAG);
}

// Сколько осколков у игрока: в инвентаре и в мешках.
function moShards(p) {
    var n = 0;
    try {
        var inv = p.getInventory();
        for (var i = 0; i < inv.getSize(); i++) {
            var it = inv.getSlot(i);
            if (it == null || it.isEmpty()) continue;
            if (moIsShard(it)) n += it.getStackSize();
            else n += moPouchShards(it);
        }
    } catch (err) {
        moLogOnce("count", "осколки не сосчитаны: " + err);
    }
    return n;
}

// Забирает до k осколков из инвентаря (не из мешков); возвращает, сколько забрал.
function moTakeShards(p, k) {
    var taken = 0;
    try {
        var inv = p.getInventory();
        for (var i = 0; i < inv.getSize() && taken < k; i++) {
            var it = inv.getSlot(i);
            if (!moIsShard(it)) continue;
            var take = Math.min(it.getStackSize(), k - taken);
            it.setStackSize(it.getStackSize() - take);
            taken += take;
        }
        if (taken > 0) p.updatePlayerInventory();
    } catch (err) {
        moLogOnce("take", "осколки не забраны: " + err);
    }
    return taken;
}

// Вынимает осколки из мешка (NBT Pouch.Items). Возвращает, сколько вынул.
function moPullPouchShards(item) {
    if (item == null || item.isEmpty() || !item.hasNbt()) return 0;
    var nbt = item.getNbt();
    if (!nbt.has("Pouch")) return 0;
    var pouch = nbt.getCompound("Pouch");
    if (!pouch.has("Items")) return 0;
    var list = pouch.getList("Items", 10), keep = [], n = 0;
    for (var k = 0; k < list.length; k++) {
        if (moIsShardNbt(list[k])) n += Number(list[k].getByte("Count"));
        else keep.push(list[k]);
    }
    if (n == 0) return 0;
    pouch.setList("Items", keep);
    return n;
}

// Забирает все осколки: из инвентаря и из мешков. На землю не кладёт. Возвращает, сколько забрал.
function moTakeAllShards(p) {
    var taken = moTakeShards(p, 1e9);
    try {
        var inv = p.getInventory(), changed = false;
        for (var i = 0; i < inv.getSize(); i++) {
            var n = moPullPouchShards(inv.getSlot(i));
            if (n == 0) continue;
            taken += n;
            changed = true;
        }
        if (changed) p.updatePlayerInventory();
    } catch (err) {
        moLogOnce("wipe", "осколки из мешка не забраны: " + err);
    }
    return taken;
}

// Снимает все осколки и кладёт их под ноги: из инвентаря — тем же стаком, из мешка — новыми.
function moDropAllShards(p) {
    var dropped = 0;
    try {
        var inv = p.getInventory();
        var w = p.getWorld();
        for (var i = 0; i < inv.getSize(); i++) {
            var it = inv.getSlot(i);
            if (!moIsShard(it)) continue;
            var n = it.getStackSize();
            p.dropItem(it.copy());
            it.setStackSize(0);
            dropped += n;
        }
        for (var j = 0; j < inv.getSize(); j++) {
            var fromPouch = moPullPouchShards(inv.getSlot(j));
            var left = fromPouch;
            while (left > 0) {
                var c = Math.min(64, left);
                p.dropItem(moMakeShards(w, c));
                left -= c;
            }
            dropped += fromPouch;
        }
        if (dropped > 0) p.updatePlayerInventory();
    } catch (err) {
        moLogOnce("drop", "осколки не выпали: " + err);
    }
    return dropped;
}

function moMakeShards(world, n) {
    var it = world.createItem(MO.SHARD_ID, n);
    it.setCustomName(MO.SHARD_NAME);
    it.setLore(MO.SHARD_LORE);
    it.getNbt().setBoolean(MO.SHARD_TAG, true);
    return it;
}

// Выдать n осколков: в инвентарь, не влезло — под ноги.
function moGive(p, n) {
    var w = p.getWorld();
    while (n > 0) {
        var k = Math.min(64, n);
        var it = moMakeShards(w, k);
        if (!p.giveItem(it)) p.dropItem(it);
        n -= k;
    }
}

// --- Метки ударов ---

function moMarksRead(world) {
    var d = world.getTempdata();
    var out = {};
    if (!d.has(MO_MARKS)) return out;
    var parts = String(d.get(MO_MARKS)).split(";");
    for (var i = 0; i < parts.length; i++) {
        var kv = parts[i].split(":");
        if (kv.length == 2) out[kv[0]] = Number(kv[1]);
    }
    return out;
}

function moMarksWrite(world, marks) {
    var now = moNow(world), parts = [];
    for (var id in marks) if (marks[id] > now) parts.push(id + ":" + marks[id]);
    world.getTempdata().put(MO_MARKS, parts.join(";"));
}

function moMark(world, p, ticks) {
    var marks = moMarksRead(world);
    var until = moNow(world) + ticks;
    var id = String(p.getUUID());
    if (!(marks[id] >= until)) marks[id] = until;
    moMarksWrite(world, marks);
}

function moMarked(marks, p, now) {
    var until = marks[String(p.getUUID())];
    return until != null && until > now;
}

// --- Выход с осколками ---

function moCsv(world, key) {
    var d = world.getStoreddata();
    if (!d.has(key)) return [];
    var s = String(d.get(key));
    return s.length == 0 ? [] : s.split(",");
}

function moCsvHas(world, key, id) {
    return moCsv(world, key).indexOf(id) >= 0;
}

function moCsvRemove(world, key, id) {
    var list = moCsv(world, key), next = [];
    for (var i = 0; i < list.length; i++) if (list[i] != id) next.push(list[i]);
    world.getStoreddata().put(key, next.join(","));
}

// Игрок ивента: живой, не креатив и не наблюдатель, на поле.
function moInEvent(p) {
    if (p == null || !p.isAlive()) return false;
    if (p.getGamemode() == 1 || p.getGamemode() == 3) return false;
    return moFlat(p, MO.FIELD[0], MO.FIELD[2]) <= MO.FIELD_R;
}

// Выход во время ивента: осколки падают там, где стоял игрок, в том числе вынутые из мешка.
// Уйти с поля раньше не спасает — Око уже записал его в MO_CARRIERS.
// Рестарт сюда не попадает: игрока уже нет, остаток снимает Око при следующем входе.
function logout(e) {
    var p = e.player;
    if (p == null) return;
    var w = p.getWorld();
    if (!moRunning(w)) return;
    var id = String(p.getUUID());
    var tracked = moCsvHas(w, MO_CARRIERS, id);
    if (!tracked && !(moInEvent(p) && moShards(p) > 0)) return;
    var had = moShards(p);
    var dropped = had > 0 ? moDropAllShards(p) : 0;
    if (moShards(p) > 0) return;
    moCsvRemove(w, MO_CARRIERS, id);
    moCsvRemove(w, MO_WIPE, id);
    if (dropped <= 0) return;
    w.spawnParticle("happy_villager", p.getX(), p.getY() + 0.2, p.getZ(), 0.6, 0.4, 0.6, 0, 40);
    var text = "§c" + p.getName() + " вышел с осколками Моррслиба – они выпали на землю. " + dropped + " шт.";
    p.message(text);
    var ps = moPlayers(w);
    for (var i = 0; i < ps.length; i++) if (String(ps[i].getUUID()) != id) ps[i].message(text);
}

// --- Охота ---

function moInSanctuary(x, z) {
    for (var i = 0; i < MO.ALTARS.length; i++) {
        var a = MO.ALTARS[i], dx = x - (a[0] + 0.5), dz = z - (a[2] + 0.5);
        if (dx * dx + dz * dz <= MO.ALTAR_R * MO.ALTAR_R) return true;
    }
    return false;
}

function moHuntCdRead(world) {
    var d = world.getStoreddata(), out = {};
    if (!d.has(MO_HUNT_CD)) return out;
    var parts = String(d.get(MO_HUNT_CD)).split(";");
    for (var i = 0; i < parts.length; i++) {
        var kv = parts[i].split(":");
        if (kv.length == 2) out[kv[0]] = Number(kv[1]);
    }
    return out;
}

// Пауза для игрока после охоты; длина — по числу его осколков сейчас.
function moHuntCdSet(world, id, shards) {
    var now = moNow(world), cds = moHuntCdRead(world), parts = [];
    var t = Math.max(1, moTier(shards));
    cds[id] = now + MO.HUNT_PAUSE[Math.min(t, MO.HUNT_PAUSE.length) - 1];
    for (var k in cds) if (cds[k] > now) parts.push(k + ":" + cds[k]);
    world.getStoreddata().put(MO_HUNT_CD, parts.join(";"));
}

// --- Земля ---

// Сквозь это Кигор проходит, землёй это не считается. Воздух — по isAir: в «stairs» тоже есть «air».
var MO_PASS = /leaves|_log$|_wood$|stem$|hyphae|water|lava|^minecraft:grass$|tall_grass|fern|flower|sapling|vine|bush|roots|mushroom$|sugar_cane|bamboo|kelp|seagrass|lily|torch|button|lever|rail|carpet|snow$|fence|wall$|pane|bars|door|sign|banner|web|tulip|orchid|allium|bluet|daisy|poppy|dandelion|lilac|peony/;

function moSolid(b) {
    if (b == null || b.isAir()) return false;
    return !MO_PASS.test(String(b.getName()));
}

// Верх первого сверху твёрдого блока, над которым не твёрдое, от y + up до y − down; null — нет такого.
function moGround(world, x, z, y, up, down) {
    var bx = Math.floor(x), bz = Math.floor(z), top = Math.floor(y) + up;
    var above = moSolid(world.getBlock(bx, top + 1, bz));
    for (var by = top; by >= Math.floor(y) - down; by--) {
        var solid = moSolid(world.getBlock(bx, by, bz));
        if (solid && !above) return by + 1;
        above = solid;
    }
    return null;
}

// --- Свечение ---

function moTier(n) {
    var t = 0;
    for (var i = 0; i < MO.TIERS.length; i++) if (n >= MO.TIERS[i]) t = i + 1;
    return t;
}

// Зелёные искры над головой; с каждой ступенью их больше, на верхних — столб.
function moGlow(world, p, n, marked) {
    var t = moTier(n);
    if (t == 0 && !marked) return;
    var x = p.getX(), y = p.getY() + 2.3, z = p.getZ();
    if (t == 0) {
        world.spawnParticle("happy_villager", x, y, z, 0.3, 0.2, 0.3, 0, 2);
        return;
    }
    world.spawnParticle("happy_villager", x, y, z, 0.3, 0.2, 0.3, 0, 3 * t);
    if (t >= 3) {
        var h = t == 3 ? 4 : 10;
        for (var k = 1; k <= h; k++) world.spawnParticle("happy_villager", x, y + k, z, 0.1, 0.3, 0.1, 0, 2);
    }
    if (t >= 4) world.spawnParticle("end_rod", x, y + 4, z, 0.1, 4, 0.1, 0.01, 6);
}
