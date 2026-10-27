// mo_core.js — общее ядро ивента «Око Моррслиб». В контейнере скрипта ПЕРВЫЙ файл: и у Ока, и у Кигора.
// Осколки Моррслиба — предметы с меткой MO.SHARD_TAG в NBT; скрипты сверяют метку, а не тип предмета.
// Считаются и осколки в мешках мода: мешок хранит содержимое в NBT (Pouch.Items).
var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();

var MO = {
    // Поле ивента: центр [x, y, z] и радиус. Игроки дальше — вне ивента. Впиши по готовому полю.
    FIELD: [0, 64, 0],
    FIELD_R: 120,
    // Заглушка, пока нет своего предмета с текстурой. Сменишь — смени и в сделках алтаря.
    SHARD_ID: "minecraft:prismarine_shard",
    SHARD_TAG: "MorrsliebShard",
    SHARD_NAME: "§aОсколок Моррслиба",
    SHARD_LORE: ["§7Светится зелёным, пока он при тебе.", "§7Сдаётся у алтаря Моррслиба."],
    // Ударивший Кигора светится столько тиков.
    MARK_TICKS: 260,
    // Яркость: с какого числа осколков начинается каждая ступень.
    TIERS: [1, 5, 12, 25]
};

// Данные мира: ход ивента — в постоянных (переживает рестарт), метки ударов и сигнал воя — во временных.
var MO_RUN = "mo_running", MO_END = "mo_end_at", MO_MARKS = "mo_marks", MO_HOWL = "mo_howl";
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

// Забирает все осколки: из инвентаря и из мешков. Возвращает, сколько забрал.
function moTakeAllShards(p) {
    var taken = moTakeShards(p, 1e9);
    try {
        var inv = p.getInventory(), changed = false;
        for (var i = 0; i < inv.getSize(); i++) {
            var it = inv.getSlot(i);
            if (it == null || it.isEmpty() || !it.hasNbt()) continue;
            var nbt = it.getNbt();
            if (!nbt.has("Pouch")) continue;
            var pouch = nbt.getCompound("Pouch");
            if (!pouch.has("Items")) continue;
            var list = pouch.getList("Items", 10), keep = [], n = 0;
            for (var k = 0; k < list.length; k++) {
                if (moIsShardNbt(list[k])) n += Number(list[k].getByte("Count"));
                else keep.push(list[k]);
            }
            if (n == 0) continue;
            pouch.setList("Items", keep);
            taken += n;
            changed = true;
        }
        if (changed) p.updatePlayerInventory();
    } catch (err) {
        moLogOnce("wipe", "осколки из мешка не забраны: " + err);
    }
    return taken;
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
