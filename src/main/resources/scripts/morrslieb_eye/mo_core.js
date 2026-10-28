// mo_core.js — общее ядро ивента «Око Моррслиб». В контейнере скрипта ПЕРВЫЙ файл: и у Ока, и у Кигора.
// Тот же файл — первый в скрипте игрока (вкладка Player), вторым там eye.js: на выходе (logout) и при гибели (died)
// осколки рассыпаются горстями, а тики игроков ведут ивент вместо Ока.
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
// Погибшие, чей инвентарь мод сохранил (оператор, символ богов): «uuid;мир;x;y;z;с какого тика жив».
var MO_FALLEN = "mo_fallen";
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

// Содержимое хранилища в инвентаре: мешок WFM (Pouch.Items) или шалкер (BlockEntityTag.Items). Мешок шалкеров не
// берёт, но шалкер с осколками, носимый в инвентаре, прятал бы свет. null — не хранилище.
function moStore(item) {
    if (item == null || item.isEmpty() || !item.hasNbt()) return null;
    var nbt = item.getNbt();
    var keys = ["Pouch", "BlockEntityTag"];
    for (var i = 0; i < keys.length; i++) {
        if (!nbt.has(keys[i])) continue;
        var holder = nbt.getCompound(keys[i]);
        if (holder.has("Items")) return holder;
    }
    return null;
}

function moPouchShards(item) {
    var holder = moStore(item);
    if (holder == null) return 0;
    var list = holder.getList("Items", 10), n = 0;
    for (var i = 0; i < list.length; i++) if (moIsShardNbt(list[i])) n += Number(list[i].getByte("Count"));
    return n;
}

// Запись предмета в хранилище: {id, Count, tag}.
function moIsShardNbt(e) {
    return String(e.getString("id")) == MO.SHARD_ID && e.has("tag") && e.getCompound("tag").getBoolean(MO.SHARD_TAG);
}

// Сколько осколков у игрока: в инвентаре, в мешках и шалкерах.
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

// Забирает до k осколков: сначала из инвентаря, не хватило — из мешков и шалкеров. Возвращает, сколько забрал.
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
        for (var j = 0; j < inv.getSize() && taken < k; j++) taken += moPullPouchShards(inv.getSlot(j), k - taken);
        if (taken > 0) p.updatePlayerInventory();
    } catch (err) {
        moLogOnce("take", "осколки не забраны: " + err);
    }
    return taken;
}

// Вынимает до k осколков из мешка или шалкера, без k — все. Возвращает, сколько вынул.
function moPullPouchShards(item, k) {
    var holder = moStore(item);
    if (holder == null) return 0;
    var want = k == null ? 1e9 : k;
    var list = holder.getList("Items", 10), keep = [], n = 0;
    for (var i = 0; i < list.length; i++) {
        var e = list[i];
        if (n >= want || !moIsShardNbt(e)) {
            keep.push(e);
            continue;
        }
        var c = Number(e.getByte("Count")), take = Math.min(c, want - n);
        n += take;
        if (take < c) {
            e.setByte("Count", c - take);
            keep.push(e);
        }
    }
    if (n > 0) holder.setList("Items", keep);
    return n;
}

// Забирает все осколки: из инвентаря, мешков и шалкеров. На землю не кладёт. Возвращает, сколько забрал.
function moTakeAllShards(p) {
    return moTakeShards(p, 1e9);
}

// Снимает все осколки и роняет горстями по MO_HANDFUL — разлетаются вокруг. Возвращает, сколько уронил.
var MO_HANDFUL = 4;

function moDropAllShards(p) {
    var n = moTakeShards(p, 1e9);
    moScatter(p.getWorld(), p.getX(), p.getY(), p.getZ(), n);
    return n;
}

// n осколков горстями по MO_HANDFUL в точку.
function moScatter(w, x, y, z, n) {
    for (var left = n; left > 0; left -= MO_HANDFUL) moSpill(w, x, y, z, moMakeShards(w, Math.min(MO_HANDFUL, left)));
}

// Предмет на землю — сущностью мира. dropItem здесь не годится: у живой сущности сервер (Arclight) кладёт предмет
// не на землю, а в список посмертного дропа Bukkit, и у игрока, который не умирает или уже умер, он пропадает.
// Чанк точки не загружен — предмет не встаёт и пропадает.
function moSpill(w, x, y, z, item) {
    try {
        var e = w.createEntity("minecraft:item");
        e.setItem(item);
        e.setPosition(x, y + 0.5, z);
        e.setMotionX((Math.random() - 0.5) * 0.3);
        e.setMotionY(0.25);
        e.setMotionZ((Math.random() - 0.5) * 0.3);
        e.setPickupDelay(20);
        w.spawnEntity(e);
    } catch (err) {
        moLogOnce("drop", "осколки не выпали: " + err);
    }
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
        if (!p.giveItem(it)) moSpill(w, p.getX(), p.getY(), p.getZ(), it);
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

// Смерть во время ивента: осколки рассыпаются там, где игрок погиб, из мешков и шалкеров тоже — и там, где вещи
// при смерти не выпадают. Событие приходит раньше выпадения вещей: из инвентаря их уже сняли, второй раз не выпадут.
// Оператору и носящему символ богов мод сохраняет весь инвентарь ещё до этого события и отдаёт при возрождении —
// тогда осколков здесь уже нет: точка гибели записывается в MO_FALLEN, и Око отнимет их у возрождённого (eFallen).
// В контейнере Кигора своё died перекрывает это; у Ока (NPC) e.player нет.
function died(e) {
    var p = e.player;
    if (p == null) return;
    var w = p.getWorld();
    if (!moRunning(w)) return;
    var id = String(p.getUUID());
    var tracked = moCsvHas(w, MO_CARRIERS, id);
    if (!tracked && moFlat(p, MO.FIELD[0], MO.FIELD[2]) > MO.FIELD_R) return;
    var n = moDropAllShards(p);
    if (n <= 0) {
        if (tracked) moFallenAdd(w, id, String(w.getName()), p.getX(), p.getY(), p.getZ());
        return;
    }
    moCsvRemove(w, MO_CARRIERS, id);
    moFallenLand(w, p.getX(), p.getY(), p.getZ());
    p.message("§cТвои осколки Моррслиба рассыпались: " + n + ".");
}

function moFallenAdd(w, id, world, x, y, z) {
    var list = moCsv(w, MO_FALLEN), next = [];
    for (var i = 0; i < list.length; i++) if (list[i].split(";")[0] != id) next.push(list[i]);
    next.push([id, world, x.toFixed(2), y.toFixed(2), z.toFixed(2), 0].join(";"));
    w.getStoreddata().put(MO_FALLEN, next.join(","));
}

function moFallenLand(w, x, y, z) {
    w.spawnParticle("happy_villager", x, y + 0.5, z, 0.8, 0.5, 0.8, 0, 40);
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
