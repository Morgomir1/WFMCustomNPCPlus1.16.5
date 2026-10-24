// Мургрот Туманный Глас (Б2). В контейнере вторым после hs_core.js.
// Скорость 0, On Attack = Мстить, рендж в GUI. Круг B5, r 20.
// Сам по себе неуязвим и не сходит с места: стоит на якоре, двигается только шагом на место тотема.
// Тотемы — его инструмент: стоят весь бой, у каждого своя нота (hs_core.js → murgrot_totem.js).
// Перекличка: Мургрот поёт 3 ноты (после 66% — 4, после 33% — 5), игроки бьют тотемы в том же
// порядке. Неверный тотем бьёт всех рядом с собой (murgrot_totem.js), песнь идёт дальше.
// Повторили — 8 с он захлёбывается: не стреляет и получает урон, но за одно окно не ниже
// следующей трети (66%, 33%, смерть). Не успели — все теряют 40%, он лечится на 10%.
// Шаг сквозь туман: раз в 22 с или если к нему жмутся 6 с — меняется местами с тотемом,
// на старом месте туман с тошнотой. На 50% встают копии, он шагает к тотему; копии поют
// фальшивые ноты на других тотемах.
// Волна гласа: пока он слушает, раз в 4 с от него по земле катится кольцо до края арены.
// Кто стоит на полу, когда оно проходит, теряет 25% и отлетает прочь; прыжок или постамент спасают.
// Копии после init носят имя оригинала, поэтому ищутся по метке, не по имени.
HS.BASE_HP = 260;
HS.ARENA_R = 20;
HS.ENRAGE_SEC = 420;
ORIGIN = [-372989, 78, -13346];   // центр арены на сервере (Антон, 24 октября). Сдвинул данж — впиши свой
GATE = hsBox(8, 0, 8, 3, 2, 3);   // завал B5_down (523, 230) от центра 515,222
SUMMONS = ["Тотем Мургрота", "Мургрот-копия"];

var TOTEM = "Тотем Мургрота";
var COPY = "Мургрот-копия";
var BOSS_TAG = "hs_murgrot";
var COPY_TAG = "hs_murgrot_copy";
// dy 2: строитель кладёт на эти точки блок кости (y пола + 1), тотем встаёт на него, а не в пол.
var TOTEMS = [ [-14, 2, -12], [14, 2, -12], [-14, 2, 12], [14, 2, 12] ];
var NOTES = [
    { pitch: 0.6, color: 0xC0FF8030 | 0, particle: "flame" },
    { pitch: 0.8, color: 0xC040FF40 | 0, particle: "happy_villager" },
    { pitch: 1.0, color: 0xC04080FF | 0, particle: "soul_fire_flame" },
    { pitch: 1.25, color: 0xC0FFFFFF | 0, particle: "end_rod" }
];
var NOTE_SOUND = "minecraft:block.note_block.bell";
var FALSE_PITCH = 0.94;
var SONG_FIRST = 200, SONG_EVERY = 300, NOTE_GAP = 20, LISTEN_BASE = 60, LISTEN_PER = 100;
var SONG_STRIKE = 0.4, WINDOW_TICKS = 160;
var WINDOW_FLOOR = [0.66, 0.66, 0.33, 0];   // по волне: окно не опускает здоровье ниже этой доли
var STEP_FIRST = 300, STEP_EVERY = 440, STEP_GAP = 160, HUG_R = 3, HUG_TICKS = 12;
var FOG_R = 3.5, FOG_TICKS = 200, FOG_COLOR = 0x90A080C0 | 0;
var ROT_FIRST = 100, ROT_EVERY = 300, ROT_TICKS = 80, ROT_R = 6;
var WAVE_FIRST = 30, WAVE_EVERY = 80, WAVE_WINDUP = 20, WAVE_STEP_TICKS = 2, WAVE_STEP = 1.0;
var WAVE_START = 1.5, WAVE_STRIKE = 0.25, WAVE_JUMP = 0.5, WAVE_COLOR = 0xC0B040FF | 0;
var T_NOTE = 1, T_ROT = 2, T_WAVE = 3, T_WAVE_STEP = 4;

function init(e) {
    var npc = e.npc;
    npc.addTag(BOSS_TAG);
    if (!hsInit(npc)) return;
    despawnTagged(npc, COPY_TAG);
    if (hsGet(npc, "base_retaliate", "") == "") hsPut(npc, "base_retaliate", npc.getAi().getRetaliateType());
    npc.getAi().setRetaliateType(hsNum(npc, "base_retaliate"));
    npc.getAi().setWalkingSpeed(0);
    npc.getStats().setResistance(3, 2.0);   // отбрасывание: 2 = совсем не сдвигается
    var home = hsHome(npc);
    setAnchor(npc, home[0], home[1], home[2]);
    hsPut(npc, "wave", 0);
    hsPut(npc, "song", "");
    hsPut(npc, "copies", 0);
    hsPut(npc, "song_at", 0);
    hsPut(npc, "rot_at", 0);
    hsPut(npc, "step_at", 0);
    hsPut(npc, "step_last", 0);
    hsPut(npc, "hug", 0);
    hsPut(npc, "fog_until", 0);
    hsPut(npc, "wave_at", 0);
    hsPut(npc, "wave_told", 0);
}

// Нет клона во вкладке — строка в консоль, бой идёт дальше. Исключение выключило бы весь скрипт.
function spawnSafe(npc, name, spawn) {
    try {
        return spawn();
    } catch (err) {
        log("herdstone: нет клона «" + name + "» во вкладке " + HS.TAB + ": " + err);
        return null;
    }
}

// Живые тотемы по ноте: [нота] = NPC или null. После шагов тотемы стоят не на своих точках.
function totemsByNote(npc) {
    var out = [null, null, null, null];
    var list = hsFind(npc, TOTEM);
    for (var i = 0; i < list.length; i++) {
        var d = list[i].getStoreddata();
        if (!d.has("hs_note")) continue;
        var note = Number(String(d.get("hs_note")));
        if (note >= 0 && note < out.length) out[note] = list[i];
    }
    return out;
}

function raiseTotems(npc) {
    var have = totemsByNote(npc);
    for (var i = 0; i < TOTEMS.length; i++) {
        if (have[i] != null) continue;
        var off = TOTEMS[i];
        var t = spawnSafe(npc, TOTEM, function () { return hsSpawnAt(npc, off, TOTEM); });
        if (t == null) return;
        var d = t.getStoreddata();
        d.put("hs_note", String(i));
        d.put("hs_pitch", String(NOTES[i].pitch));
        d.put("hs_particle", NOTES[i].particle);
    }
}

function songLength(npc) {
    var wave = hsNum(npc, "wave");
    return wave >= 3 ? 5 : (wave == 2 ? 4 : 3);
}

// Соседние ноты не повторяются: два удара по одному тотему подряд неотличимы от одного взмаха.
function makeSong(notes, length) {
    var seq = [];
    for (var i = 0; i < length; i++) {
        var pool = [];
        for (var j = 0; j < notes.length; j++) if (seq.length == 0 || notes[j] != seq[seq.length - 1]) pool.push(notes[j]);
        seq.push(hsRandom(pool));
    }
    return seq;
}

function playNote(npc, totem, note, real) {
    if (totem == null) return;
    var w = npc.getWorld();
    TelegraphAPI.circle(npc, totem.getX(), totem.getY(), totem.getZ(), 2.0, NOTE_GAP, NOTES[note].color);
    w.playSoundAt(totem.getPos(), NOTE_SOUND, 3.0, real ? NOTES[note].pitch : NOTES[note].pitch * FALSE_PITCH);
    if (real) w.spawnParticle(NOTES[note].particle, totem.getX(), totem.getY() + 1.5, totem.getZ(), 0.4, 0.6, 0.4, 0.02, 20);
}

function startSong(npc, now) {
    raiseTotems(npc);
    var have = totemsByNote(npc);
    var notes = [];
    for (var i = 0; i < have.length; i++) if (have[i] != null) notes.push(i);
    hsPut(npc, "song_at", now + SONG_EVERY);
    if (notes.length < 2) {
        log("herdstone: Мургроту не на чем петь — живых тотемов " + notes.length);
        return;
    }
    hsPut(npc, "seq", makeSong(notes, songLength(npc)).join(","));
    hsPut(npc, "note_i", 0);
    hsPut(npc, "song", "sing");
    npc.say("§5Мургрот заводит песнь — слушайте тотемы!");
    npc.getTimers().forceStart(T_NOTE, NOTE_GAP, false);
}

function singNext(npc) {
    var seq = hsGet(npc, "seq", "").split(",");
    var i = hsNum(npc, "note_i");
    var have = totemsByNote(npc);
    var note = Number(seq[i]);
    playNote(npc, have[note], note, true);
    var copies = countTagged(npc, COPY_TAG) + hsCount(npc, COPY);
    for (var c = 0; c < copies; c++) {
        var other = [];
        for (var j = 0; j < have.length; j++) if (j != note && have[j] != null) other.push(j);
        if (other.length > 0) {
            var fake = hsRandom(other);
            playNote(npc, have[fake], fake, false);
        }
    }
    i++;
    hsPut(npc, "note_i", i);
    if (i < seq.length) {
        npc.getTimers().forceStart(T_NOTE, NOTE_GAP, false);
        return;
    }
    hsPut(npc, "song", "listen");
    hsPut(npc, "seq_pos", 0);
    hsPut(npc, "last_note", -1);
    hsPut(npc, "last_at", 0);
    hsPut(npc, "listen_until", hsNow(npc) + LISTEN_BASE + LISTEN_PER * seq.length);
    hsPut(npc, "wave_at", hsNow(npc) + WAVE_FIRST);
    npc.say("§5Повторите песнь: бейте тотемы в том же порядке!");
}

function songFails(npc, now) {
    hsPut(npc, "song", "");
    hsPut(npc, "song_at", now + SONG_EVERY);
    npc.say("§4Песнь не подхвачена! Песнь безумия рвёт вам головы!");
    var ps = hsPlayers(npc);
    for (var i = 0; i < ps.length; i++) {
        hsStrike(ps[i], SONG_STRIKE);
        ps[i].addPotionEffect(PotionEffectType_NAUSEA, 6, 0, false);
    }
    npc.setHealth(Math.min(npc.getMaxHealth(), npc.getHealth() + npc.getMaxHealth() * 0.1));
}

function startWindow(npc, now) {
    hsPut(npc, "song", "stun");
    hsPut(npc, "stun_until", now + WINDOW_TICKS);
    AbilityAPI.cancel(npc);
    npc.getAi().setRetaliateType(3);
    TelegraphAPI.circle(npc, npc.getX(), npc.getY(), npc.getZ(), 2.5, WINDOW_TICKS, NOTES[1].color);
    npc.getWorld().playSoundAt(npc.getPos(), "minecraft:entity.ravager.stunned", 3.0, 0.8);
    npc.say("§aПеснь подхвачена! Мургрот захлёбывается — бейте!");
}

function endWindow(npc, now) {
    hsPut(npc, "song", "");
    hsPut(npc, "song_at", now + SONG_EVERY);
    npc.getAi().setRetaliateType(hsNum(npc, "base_retaliate"));
    npc.say("§7Мургрот приходит в себя.");
}

function windowFloor(npc) {
    var wave = hsNum(npc, "wave");
    if (wave < 0) wave = 0;
    if (wave >= WINDOW_FLOOR.length) wave = WINDOW_FLOOR.length - 1;
    return npc.getMaxHealth() * WINDOW_FLOOR[wave];
}

function setAnchor(npc, x, y, z) {
    hsPut(npc, "anchor_x", x);
    hsPut(npc, "anchor_y", y);
    hsPut(npc, "anchor_z", z);
}

// Толчки игроков и чужие эффекты сдвигают NPC и со скоростью 0 — возвращаем на якорь.
function pin(npc) {
    if (hsGet(npc, "anchor_x", "") == "") return;
    var ax = hsNum(npc, "anchor_x"), ay = hsNum(npc, "anchor_y"), az = hsNum(npc, "anchor_z");
    var dx = npc.getX() - ax, dy = npc.getY() - ay, dz = npc.getZ() - az;
    if (dx * dx + dz * dz > 0.09 || dy > 0.6 || dy < -0.6) npc.setPosition(ax, ay, az);
}

function songTick(npc, now) {
    var st = hsGet(npc, "song", "");
    if (st == "done") startWindow(npc, now);
    else if (st == "fail" || (st == "listen" && now > hsNum(npc, "listen_until"))) songFails(npc, now);
    else if (st == "stun" && now >= hsNum(npc, "stun_until")) endWindow(npc, now);
    else if (st == "" && now >= hsNum(npc, "song_at")) startSong(npc, now);
}

function stepTick(npc, ps, now) {
    var st = hsGet(npc, "song", "");
    if (st == "sing" || st == "stun") return;
    var close = false;
    for (var i = 0; i < ps.length; i++) if (hsFlat(ps[i], npc.getX(), npc.getZ()) <= HUG_R) close = true;
    var hug = close ? hsNum(npc, "hug") + 1 : 0;
    hsPut(npc, "hug", hug);
    if (now - hsNum(npc, "step_last") < STEP_GAP) return;
    if (now < hsNum(npc, "step_at") && hug < HUG_TICKS) return;
    stepTo(npc, now);
}

function stepTo(npc, now) {
    var have = totemsByNote(npc);
    var pool = [];
    for (var j = 0; j < have.length; j++) if (have[j] != null) pool.push(have[j]);
    if (pool.length == 0) return;
    var t = hsRandom(pool);
    var ox = npc.getX(), oy = npc.getY(), oz = npc.getZ();
    var w = npc.getWorld();
    w.playSoundAt(npc.getPos(), "minecraft:entity.illusioner.mirror_move", 2.0, 0.7);
    npc.setPosition(t.getX(), t.getY(), t.getZ());
    setAnchor(npc, t.getX(), t.getY(), t.getZ());
    t.setPosition(ox, oy, oz);
    w.playSoundAt(npc.getPos(), "minecraft:entity.illusioner.mirror_move", 2.0, 0.7);
    var zone = ZoneAPI.hazardCircle(npc, ox, oy, oz, FOG_R, FOG_TICKS, 0, 20);
    if (zone != null) {
        try { zone.setColor(FOG_COLOR); } catch (err) {}
        hsZonePut(npc, zone);
    }
    hsPut(npc, "fog_x", ox);
    hsPut(npc, "fog_y", oy);
    hsPut(npc, "fog_z", oz);
    hsPut(npc, "fog_until", now + FOG_TICKS);
    hsPut(npc, "step_last", now);
    hsPut(npc, "step_at", now + STEP_EVERY);
    hsPut(npc, "hug", 0);
    npc.say("§8Мургрот растворяется в тумане…");
}

function waveTick(npc, now) {
    if (hsGet(npc, "song", "") != "listen" || now < hsNum(npc, "wave_at")) return;
    var timers = npc.getTimers();
    if (timers.has(T_WAVE) || timers.has(T_WAVE_STEP)) return;
    hsPut(npc, "wave_at", now + WAVE_EVERY);
    var floorY = hsArenaCenter(npc)[1];
    TelegraphAPI.circle(npc, npc.getX(), floorY, npc.getZ(), 2.5, WAVE_WINDUP, WAVE_COLOR);
    npc.getWorld().playSoundAt(npc.getPos(), "minecraft:entity.ravager.roar", 3.0, 1.4);
    if (hsGet(npc, "wave_told", "0") == "0") {
        hsPut(npc, "wave_told", 1);
        npc.say("§5Глас Мургрота катится по земле — прыгайте!");
    }
    timers.forceStart(T_WAVE, WAVE_WINDUP, false);
}

function waveRelease(npc) {
    var c = hsArenaCenter(npc);
    hsPut(npc, "wave_x", npc.getX());
    hsPut(npc, "wave_z", npc.getZ());
    hsPut(npc, "wave_r", WAVE_START);
    hsPut(npc, "wave_max", hsFlat(npc, c[0], c[2]) + HS.ARENA_R + 1);
    hsPut(npc, "wave_hit", "");
    npc.getWorld().playSoundAt(npc.getPos(), "minecraft:block.bell.resonate", 3.0, 0.6);
    npc.getTimers().forceStart(T_WAVE_STEP, WAVE_STEP_TICKS, false);
}

// Кольцо шириной в блок; каждого игрока проверяем один раз — в шаг, когда кольцо его пересекло.
// Высота — от ног на полу арены (дом Мургрота): кто в прыжке или на постаменте, тот выше WAVE_JUMP.
function waveStep(npc) {
    var ox = hsNum(npc, "wave_x"), oz = hsNum(npc, "wave_z");
    var floorY = hsArenaCenter(npc)[1];
    var r0 = hsNum(npc, "wave_r"), r1 = r0 + WAVE_STEP;
    TelegraphAPI.ring(npc, ox, floorY, oz, r1 + 0.5, r1 - 0.5, WAVE_STEP_TICKS + 1, WAVE_COLOR);
    var hit = hsGet(npc, "wave_hit", "");
    var ps = hsPlayersAt(npc, ox, floorY, oz, r1 + 1);
    for (var i = 0; i < ps.length; i++) {
        var p = ps[i];
        var d = hsFlat(p, ox, oz);
        if (d < r0 || d > r1) continue;
        var id = String(p.getUUID());
        if (hit.indexOf(id) >= 0) continue;
        hit += id + ";";
        if (p.getY() - floorY >= WAVE_JUMP) continue;
        hsStrike(p, WAVE_STRIKE);
        p.knockback(1, Combat.computeYaw(p.getX() - ox, p.getZ() - oz));
        p.message("§5Волна гласа сбивает тебя с ног!");
    }
    hsPut(npc, "wave_hit", hit);
    hsPut(npc, "wave_r", r1);
    if (r1 < hsNum(npc, "wave_max")) npc.getTimers().forceStart(T_WAVE_STEP, WAVE_STEP_TICKS, false);
}

function fogTick(npc, now) {
    if (now >= hsNum(npc, "fog_until")) return;
    var ps = hsPlayersAt(npc, hsNum(npc, "fog_x"), hsNum(npc, "fog_y"), hsNum(npc, "fog_z"), FOG_R);
    for (var i = 0; i < ps.length; i++) ps[i].addPotionEffect(PotionEffectType_NAUSEA, 3, 0, false);
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    var ps = hsFight(npc);
    if (ps == null) return;
    pin(npc);
    var now = hsNow(npc);
    var hp = npc.getHealth() / npc.getMaxHealth();
    var want = hp > 0.66 ? 1 : (hp > 0.33 ? 2 : 3);
    var wave = hsNum(npc, "wave");
    if (want > wave) {
        hsPut(npc, "wave", want);
        if (wave == 0) {
            hsPut(npc, "rot_at", now + ROT_FIRST);
            hsPut(npc, "song_at", now + SONG_FIRST);
            hsPut(npc, "step_at", now + STEP_FIRST);
            raiseTotems(npc);
            npc.say("§5Тотемы, пойте со мной!");
        } else {
            npc.say("§5Мургрот повышает голос — песнь станет длиннее!");
        }
    }
    songTick(npc, now);
    waveTick(npc, now);
    fogTick(npc, now);

    if (hsGet(npc, "copies", "0") == "0" && hp <= 0.5) {
        hsPut(npc, "copies", 1);
        var w = npc.getWorld();
        var h = hsHome(npc);
        for (var c = 0; c < 3; c++) {
            var a = Math.random() * Math.PI * 2;
            var cx = h[0] + Math.cos(a) * 10, cz = h[2] + Math.sin(a) * 10;
            var copy = spawnSafe(npc, COPY, function () { return w.spawnClone(cx, h[1], cz, HS.TAB, COPY); });
            if (copy == null) break;
            hsNoRespawn(copy);
        }
        stepTo(npc, now);
        for (var b = 0; b < ps.length; b++) ps[b].addPotionEffect(PotionEffectType_BLINDNESS, 3, 0, false);
    }
    if (countTagged(npc, COPY_TAG) > 0 || hsCount(npc, COPY) > 0) {
        npc.getWorld().spawnParticle("witch", npc.getX(), npc.getY() + 2.6, npc.getZ(), 0.3, 0.3, 0.3, 0, 12);
    }
    stepTick(npc, ps, now);
    // Пока он поёт ноты, порча не начинается: их надо слышать и видеть.
    if (hsGet(npc, "song", "") == "sing") return;
    if (npc.getTimers().has(T_ROT) || now < hsNum(npc, "rot_at")) return;
    hsPut(npc, "rot_at", now + ROT_EVERY);
    var p = hsRandom(ps);
    hsPut(npc, "rot_uuid", p.getUUID());
    var id = TelegraphAPI.circle(npc, p.getX(), p.getY(), p.getZ(), ROT_R, ROT_TICKS, HS.RED);
    TelegraphAPI.follow(id, p);
    p.addPotionEffect(PotionEffectType_GLOWING, 4, 0, false);
    p.message("§cНа тебе Порча Хаоса — отойди от всех!");
    npc.getTimers().forceStart(T_ROT, ROT_TICKS, false);
}

function timer(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    if (e.id == T_NOTE && hsGet(npc, "song", "") == "sing") {
        singNext(npc);
        return;
    }
    if (e.id == T_WAVE) {
        waveRelease(npc);
        return;
    }
    if (e.id == T_WAVE_STEP) {
        waveStep(npc);
        return;
    }
    if (e.id == T_ROT) {
        var t = aliveById(npc, "rot_uuid");
        if (t == null) return;
        var hit = hsPlayersAt(npc, t.getX(), t.getY(), t.getZ(), ROT_R);
        for (var j = 0; j < hit.length; j++) hsStrike(hit[j], 0.25 * hit.length);
    }
}

// Урон проходит только в окне и только до следующей трети; дошли до неё — окно кончается.
function damaged(e) {
    var npc = e.npc;
    if (hsGet(npc, "song", "") != "stun") { e.setCanceled(true); return; }
    hsCap(e, 1);
    var room = npc.getHealth() - windowFloor(npc);
    if (room <= 0.01) { e.setCanceled(true); hsPut(npc, "stun_until", 0); return; }
    if (e.damage >= room) {
        e.damage = room;
        hsPut(npc, "stun_until", 0);
    }
}

function died(e) {
    var npc = e.npc;
    AbilityAPI.cancel(npc);
    hsZoneClear(npc);
    despawnTagged(npc, COPY_TAG);
    hsDespawnAll(npc, COPY);
    hsDespawnAll(npc, TOTEM);
    hsSetGate(npc, false);
    npc.say("§7Туман рассеивается. Корни расступаются.");
}

function arenaNpcs(npc) {
    var c = hsArenaCenter(npc);
    return npc.getWorld().getNearbyEntities(NpcAPI.getIPos(c[0], c[1], c[2]), hsScanRange(), 2);
}

function despawnTagged(npc, tag) {
    var list = arenaNpcs(npc);
    var selfId = String(npc.getUUID());
    for (var i = 0; i < list.length; i++) {
        var n = list[i];
        if (!n.isAlive() || String(n.getUUID()) == selfId) continue;
        if (n.hasTag(tag)) n.despawn();
    }
}

function countTagged(npc, tag) {
    var list = arenaNpcs(npc);
    var selfId = String(npc.getUUID());
    var n = 0;
    for (var i = 0; i < list.length; i++) {
        var e = list[i];
        if (!e.isAlive() || String(e.getUUID()) == selfId) continue;
        if (e.hasTag(tag)) n++;
    }
    return n;
}

function aliveById(npc, key) {
    var id = hsGet(npc, key, "");
    if (id.length < 32) return null;
    try {
        var p = npc.getWorld().getEntity(id);
        if (p != null && p.isAlive()) return p;
    } catch (err) {}
    return null;
}
