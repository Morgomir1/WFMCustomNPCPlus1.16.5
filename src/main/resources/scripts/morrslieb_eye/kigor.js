// kigor.js — Кигор, сайгор ивента «Око Моррслиб». В контейнере скрипта вторым после mo_core.js.
// Видит только свет: несущих осколки Моррслиба и ударивших его (метка на MO.MARK_TICKS). Тёмных не замечает —
// событие target отменяет такую цель, кого бы ни выбрал мод; задевают их только удары по площади.
// Охотник: Око вызывает его клоном за несущим осколки и пишет в storeddata «hunt» его UUID. Гонится только за ним —
// других светящихся бьёт заодно. Шаги ведёт скрипт, не поиск пути мода: прямо к цели, сквозь деревья, заборы
// и стены, по земле вверх и вниз; поворачивается плавно, TURN_DEG за тик. Возврат домой выключен: он переносил NPC.
// От него уходят: сдать осколки у алтаря (ближе MO.ALTAR_R он не ступает и не бьёт) или бросить их — тёмного он
// не видит; оторваться — дальше LOSE_R или вне прямой видимости дальше HIDE_MIN LOSE_TICKS подряд; пережить
// HUNT_TICKS; добыча ушла в креатив или наблюдатели — сразу. Тогда он рассыпается, а Око не шлёт за этим
// игроком нового до паузы MO.HUNT_PAUSE.
// Приёмы, все с самого начала, фаз нет: удар кулаком, топот вокруг себя, глыбы залпом до VOLLEY целей, рывок рогами,
// Взгляд Ока (засвечивает и тянет к нему всех в круге GAZE_R, кого видит; следом — удар оземь по притянутым, после него
// 3 секунды урон по нему ×1,5). Бьёт широко и очень сильно.
// Вой — по сигналу Ока, когда загораются тайники.
// Пожиратель душ: ударив несущего, вырывает часть осколков из инвентаря и лечится; убитый роняет всё съеденное.
// Без добычи (поставлен руками, чтобы настроить и сохранить в клонер) стоит и ничего не делает. Ивент выключили —
// охотник рассыпается. Съеденное роняет только убитый.
var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");
var TelegraphAPI = Java.type("noppes.npcs.telegraph.TelegraphAPI");
var Combat = Java.type("noppes.npcs.abilities.AbilityCombatHelper");
var NpcTurnHelper = Java.type("noppes.npcs.ai.NpcTurnHelper");

// Здоровье при появлении: охотник живёт до полутора минут, убивать его не обязательно.
var BASE_HP = 1500, HP_PER_PLAYER = 75, HP_MAX = 5000;
var CAP = 20, OPEN_MUL = 1.5;
var SIGHT_R = 48;
// Охота: HUNT_TICKS гонится; оторвался — дальше LOSE_R или вне прямой видимости дальше HIDE_MIN — LOSE_TICKS подряд.
var HUNT_TICKS = 1800, LOSE_R = 32, HIDE_MIN = 16, LOSE_TICKS = 120;
// Шаг за тик в погоне: бегущий игрок — около 0,28, под «Скоростью II» — около 0,34. 0,37 — 7,4 блока в секунду.
// Встаёт в CHASE_STOP от цели.
var CHASE_SPEED = 0.37, CHASE_STOP = 3;
// Земля под шагом — от GROUND_UP выше ног до GROUND_DOWN ниже; за тик не выше RISE и не ниже FALL.
var GROUND_UP = 12, GROUND_DOWN = 16, RISE = 0.6, FALL = 1.2;
// Пожиратель душ: доля осколков с удара (не меньше одного) и лечение за осколок — доля здоровья.
var EAT_PART = 0.25, EAT_HEAL = 0.01;
var RED = 0xC0FF0000 | 0, GREEN = 0xC040FF40 | 0;
var STUN_ID = "wfm:stun";

var TURN_DEG = 5;

// Клипы beastmen_cygor.animation.json. Удар совпадает с ключом клипа.
// attack 1.25s — взмах на 0.54s. attack_stomp 1.75s — удар на 1.25s.
// stone_throw / stone_throw_2 2.00s — выпуск камня на 1.25s, круг бьёт на 1.50s.
// attack_ram 2.42s — разгон 1.40s, рывок до 1.80s. attack_roar 3.33s — вой на 2.00s.
// Взгляда Ока в файле нет: кость eye нигде не ключится.
var ANIM_PUNCH = "attack";
var ANIM_STOMP = "attack_stomp";
var ANIM_BOULDER = "stone_throw";
var ANIM_BOULDER_VOLLEY = "stone_throw_2";
var ANIM_DASH = "attack_ram";
var ANIM_HOWL = "attack_roar";
var ANIM_GAZE = "attack_gaze";

// Области приёмов — прежние ×1,15: дальность и длина удара, радиусы топота, глыбы, рывка и взгляда, ширина рывка.
var PUNCH_RANGE = 9.2, PUNCH_LEN = 10.4, PUNCH_HALF = 80, PUNCH_WARN = 12, PUNCH_PCT = 0.45, PUNCH_CD = 40, PUNCH_PUSH = 2;
var PUNCH_RECOVER = 13;
var STOMP_R = 11.5, STOMP_WARN = 25, STOMP_PCT = 0.45, STOMP_CD = 200, STOMP_PUSH = 3;
var STOMP_RECOVER = 10;
var BOULDER_MIN = 8, BOULDER_MAX = 40, BOULDER_R = 6.9, BOULDER_WARN = 30, BOULDER_PCT = 0.45, BOULDER_STUN = 30;
var BOULDER_CD = 160, VOLLEY = 3, THROW_ROOT = 25;
var DASH_MIN = 6, DASH_MAX = 20, DASH_CHARGE = 28, DASH_ACTIVE = 8, DASH_W = 3.45, DASH_DMG = 16.0;
var DASH_PATH_R = 2.1, DASH_HIT_R = 6.9, DASH_PCT = 0.55, DASH_CD = 240, DASH_RECOVER = 10, DASH_PUSH = 2;
var GAZE_R = 23, GAZE_WARN = 20, GAZE_TICKS = 40, GAZE_PULSE = 5, GAZE_OPEN = 60, GAZE_CD = 400;
// Взгляд тянет к нему всех в круге GAZE_R, кого он видит: толчок GAZE_PULL за каждый пульс, пока игрок дальше GAZE_HOLD.
var GAZE_SLOW = 3, GAZE_PULL = 2, GAZE_HOLD = 5;
// Удар оземь следом за взглядом: замах начинается за SLAM_LEAD тиков до конца притяжения, удар — через SLAM_WARN
// (на ударе клипа топота), то есть через полсекунды после последнего рывка к нему. Бьёт всех в SLAM_R.
var SLAM_LEAD = 15, SLAM_WARN = 25, SLAM_R = 8, SLAM_PCT = 0.45, SLAM_PUSH = 3;
var HOWL_WARN = 40, HOWL_R = 64, HOWL_GLOW = 5, HOWL_MARK = 100;
var HOWL_RECOVER = 27;
var WALK_GAP = 30;

var T_HOLD = 1, T_PUNCH = 2, T_STOMP = 3, T_BOULDER = 4, T_DASH_END = 5, T_GAZE = 6, T_GAZE_BEAM = 7;
var T_HOWL = 8, T_RECOVER = 9, T_MOVE = 10, T_SLAM = 11;
var ALL_TIMERS = [T_HOLD, T_PUNCH, T_STOMP, T_BOULDER, T_DASH_END, T_GAZE, T_GAZE_BEAM, T_HOWL, T_RECOVER, T_MOVE, T_SLAM];
// Пока идёт любой из этих — новый приём не начинается. Глыба после выпуска (1.25s) летит сама.
var CAST_TIMERS = [T_PUNCH, T_STOMP, T_DASH_END, T_GAZE, T_GAZE_BEAM, T_HOWL, T_RECOVER, T_SLAM];

function kNow(npc) { return Number(npc.getWorld().getTotalTime()); }
function kGet(npc, k, def) { var d = npc.getStoreddata(); return d.has(k) ? String(d.get(k)) : def; }
function kNum(npc, k) { return Number(kGet(npc, k, "0")); }
function kPut(npc, k, v) { npc.getStoreddata().put(k, String(v)); }
function kYawTo(npc, t) { return Combat.computeYaw(t.getX() - npc.getX(), t.getZ() - npc.getZ()); }

// Таймер CustomNPCs с числом N срабатывает на N+1-е обновление; здесь ticks — сколько тиков на самом деле.
function kTimer(npc, id, ticks, repeat) {
    npc.getTimers().forceStart(id, Math.max(0, ticks - 1), repeat);
}

function init(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    npc.getTimers().clear();
    AbilityAPI.cancel(npc);
    try { NpcTurnHelper.unlockFace(npc); } catch (err) {}
    // Цель дальше дальности агрессии мод бросает; тёмных отсекает событие target.
    npc.getStats().setAggroRange(SIGHT_R);
    npc.getAi().setRetaliateType(0);
    // Сам мод его не водит: шагает скрипт (kMove), возврат домой выключен — он и переносил его на точку.
    npc.getAi().setMovingType(0);
    npc.getAi().setReturnsHome(false);
    npc.getAi().setWalkingSpeed(0);
    try { NpcTurnHelper.apply(npc, TURN_DEG); } catch (err) { log("kigor turn: " + err); }
    var keys = ["eye_open_until", "free_at", "was_busy", "eaten", "lost", "run_seen"];
    for (var i = 0; i < keys.length; i++) kPut(npc, keys[i], 0);
    kPut(npc, "face_yaw", npc.getRotation());
    kPut(npc, "howl_seen", kHowlSignal(npc));
    kTimer(npc, T_MOVE, 1, true);
}

// Здоровье от числа игроков на поле: при появлении и при старте ивента.
function kSetHp(npc) {
    var hp = Math.min(HP_MAX, BASE_HP + HP_PER_PLAYER * moPlayers(npc.getWorld()).length);
    npc.getStats().setMaxHealth(hp);
    npc.setHealth(hp);
}

// --- Свет: кого он видит ---

function kSees(npc, p, marks, now) {
    if (p == null || !p.isAlive() || !p.typeOf(1)) return false;
    return moMarked(marks, p, now) || moShards(p) > 0;
}

function kGlowing(npc, ps, marks, now, r) {
    var out = [];
    for (var i = 0; i < ps.length; i++) {
        var d = moFlat(ps[i], npc.getX(), npc.getZ());
        if (d <= r && kSees(npc, ps[i], marks, now)) out.push(ps[i]);
    }
    return out;
}

function target(e) {
    var w = e.npc.getWorld();
    if (!moRunning(w) || !kSees(e.npc, e.entity, moMarksRead(w), moNow(w))) e.setCanceled(true);
}

// --- Охота ---

// Добыча в креативе или наблюдателе: Кигор её больше не видит и сразу рассыпается.
function kGmHidden(npc) {
    var id = kGet(npc, "hunt", "");
    if (id.length == 0) return false;
    var p = null;
    try { p = npc.getWorld().getEntity(id); } catch (err) {}
    if (p == null || !p.typeOf(1)) return false;
    var mode = p.getGamemode();
    return mode == 1 || mode == 3;
}

// Его добыча, пока светится; иначе null.
function kQuarry(npc, w, marks, now) {
    var id = kGet(npc, "hunt", "");
    if (id.length == 0) return null;
    var p = null;
    try {
        p = w.getEntity(id);
    } catch (err) {}
    return kSees(npc, p, marks, now) ? p : null;
}

// Почему охота кончилась, или null: погасла (сдала, бросила, погибла, вышла), время вышло, оторвалась.
function kEscaped(npc, q, now) {
    if (q == null) return "dark";
    if (now >= kNum(npc, "hunt_until")) return "time";
    var d = moFlat(q, npc.getX(), npc.getZ());
    var away = d > LOSE_R || (d > HIDE_MIN && !npc.canSeeEntity(q));
    kPut(npc, "lost", away ? kNum(npc, "lost") + 10 : 0);
    return kNum(npc, "lost") >= LOSE_TICKS ? "lost" : null;
}

var VANISH_TEXT = {
    dark: "§2Кигор больше не видит твоего света и рассыпается.",
    time: "§2Моррслиб зовёт Кигора назад – он рассыпается.",
    lost: "§2Кигор потерял твой след и рассыпается.",
    off: "§2Моррслиб закрыл глаз – Кигор рассыпается.",
    gm: "§2Кигор не видит тебя и рассыпается."
};

// Рассыпается: добыче — строка и пауза до следующего Кигора, съеденное уходит с ним.
function kVanish(npc, why) {
    if (kGet(npc, "gone", "0") == "1") return;
    kPut(npc, "gone", 1);
    var w = npc.getWorld(), id = kGet(npc, "hunt", "");
    var p = null;
    try {
        if (id.length > 0) p = w.getEntity(id);
    } catch (err) {}
    if (id.length > 0 && why != "off") moHuntCdSet(w, id, p != null ? moShards(p) : 0);
    if (p != null && p.typeOf(1)) p.message(VANISH_TEXT[why]);
    kSound(npc, "minecraft:entity.wolf.howl", 1.0, 0.4);
    w.spawnParticle("happy_villager", npc.getX(), npc.getY() + 2, npc.getZ(), 1.0, 2.0, 1.0, 0, 40);
    w.spawnParticle("cloud", npc.getX(), npc.getY() + 1, npc.getZ(), 1.0, 1.0, 1.0, 0.02, 20);
    AbilityAPI.cancel(npc);
    for (var i = 0; i < ALL_TIMERS.length; i++) npc.getTimers().stop(ALL_TIMERS[i]);
    try { NpcTurnHelper.unlockFace(npc); } catch (err) {}
    npc.despawn();
}

// --- Шаг ---

// Каждый тик — шаг к добыче. Пока идёт приём или его держат на месте — стоит; к алтарю не ступает.
function kMove(npc) {
    if (kBusy(npc) || npc.getTimers().has(T_HOLD)) return;
    var w = npc.getWorld();
    if (!moRunning(w)) return;
    var t = kQuarry(npc, w, moMarksRead(w), kNow(npc));
    if (t == null) return;
    var x = npc.getX(), y = npc.getY(), z = npc.getZ();
    var dx = t.getX() - x, dz = t.getZ() - z, d = Math.sqrt(dx * dx + dz * dz);
    if (d <= CHASE_STOP) return;
    var s = Math.min(CHASE_SPEED, d - CHASE_STOP);
    var nx = x + dx / d * s, nz = z + dz / d * s;
    if (moInSanctuary(nx, nz)) return;
    var g = moGround(w, nx, nz, y, GROUND_UP, GROUND_DOWN);
    var ny = g == null ? y : Math.max(y - FALL, Math.min(y + RISE, g));
    kTurn(npc, Combat.computeYaw(dx, dz));
    Combat.holdInPlace(npc, nx, ny, nz);
    if (ny < y) Combat.clearFallDistance(npc);
}

// Поворот к направлению шага не больше TURN_DEG за тик.
function kTurn(npc, yaw) {
    var cur = Number(kGet(npc, "face_yaw", String(npc.getRotation())));
    var diff = ((yaw - cur) % 360 + 540) % 360 - 180;
    if (diff > TURN_DEG) diff = TURN_DEG;
    if (diff < -TURN_DEG) diff = -TURN_DEG;
    kFace(npc, cur + diff);
}

// --- Общее ---

function kBusy(npc) {
    if (AbilityAPI.isBusy(npc)) return true;
    var t = npc.getTimers();
    for (var i = 0; i < CAST_TIMERS.length; i++) if (t.has(CAST_TIMERS[i])) return true;
    return false;
}

// Плавный поворот как у Горгона: °/тик переживает пересборку AI.
// На касте — lockFace, не setRotation: тот шлёт полный ресинк NPC, а yBodyRot ванила клиенту не отдаёт.
function kFace(npc, yaw) {
    if (yaw == null || isNaN(yaw)) return;
    kPut(npc, "face_yaw", yaw);
    try {
        NpcTurnHelper.lockFace(npc, yaw);
    } catch (err) {
        try { npc.setRotation(yaw); } catch (err2) {}
    }
}

function kAnim(npc, name) {
    if (name == null || name == "") return;
    try {
        npc.syncAnimationsForAll(NpcAPI.createAnimBuilder().playOnce(name));
    } catch (err) {
        log("kigor anim: " + err);
    }
}

function kFreeze(npc, yaw) {
    npc.getAi().setWalkingSpeed(0);
    kPut(npc, "hold_mode", "cast");
    kPut(npc, "hold_x", npc.getX());
    kPut(npc, "hold_y", npc.getY());
    kPut(npc, "hold_z", npc.getZ());
    kPut(npc, "hold_yaw", yaw);
    kFace(npc, yaw);
    kTimer(npc, T_HOLD, 2, true);
}

// Рывок сам везёт тело. Держим только взгляд, иначе holdInPlace гасит dash.
function kFaceOnly(npc, yaw) {
    npc.getAi().setWalkingSpeed(0);
    kPut(npc, "hold_mode", "face");
    kPut(npc, "hold_yaw", yaw);
    kFace(npc, yaw);
    kTimer(npc, T_HOLD, 2, true);
}

function kHold(npc) {
    if (kGet(npc, "hold_mode", "") != "face") {
        Combat.holdInPlace(npc, kNum(npc, "hold_x"), kNum(npc, "hold_y"), kNum(npc, "hold_z"));
    }
    kFace(npc, kNum(npc, "hold_yaw"));
}

function kRelease(npc) {
    npc.getTimers().stop(T_HOLD);
    kPut(npc, "hold_mode", "");
    if (npc.getAi().getRetaliateType() != 0) npc.getAi().setRetaliateType(0);
    try { NpcTurnHelper.unlockFace(npc); } catch (err) { log("kigor unlock: " + err); }
    try { NpcTurnHelper.apply(npc, TURN_DEG); } catch (err) { log("kigor turn: " + err); }
}

function kRecover(npc, ticks) {
    kFreeze(npc, npc.getRotation());
    kTimer(npc, T_RECOVER, ticks, false);
}

// Звук: в 16 блоках — из точки (playSoundAt CustomNPCs дальше 16 не шлёт), дальше до SOUND_R — каждому игроку
// тише с расстоянием, ещё дальше не слышно. Прежде звук шёл каждому игроку поля в уши на полной громкости, и на поле
// радиусом 600 Кигора слышали рядом из любого его угла.
var SOUND_NEAR = 16, SOUND_R = 48;

function kSound(npc, sound, volume, pitch) {
    kSoundAt(npc.getWorld(), npc.getX(), npc.getY(), npc.getZ(), sound, volume, pitch);
}

function kSoundAt(w, x, y, z, sound, volume, pitch) {
    w.playSoundAt(NpcAPI.getIPos(x, y, z), sound, volume, pitch);
    var ps = w.getNearbyEntities(NpcAPI.getIPos(x, y, z), SOUND_R, 1);
    for (var i = 0; i < ps.length; i++) {
        var dx = ps[i].getX() - x, dy = ps[i].getY() - y, dz = ps[i].getZ() - z;
        var dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist <= SOUND_NEAR || dist > SOUND_R) continue;
        ps[i].playSound(sound, volume * (1 - (dist - SOUND_NEAR) / (SOUND_R - SOUND_NEAR)), pitch);
    }
}

// Кого задевают его удары: у алтаря — никого.
function kPlayersAt(npc, x, z, r) {
    var ps = moPlayers(npc.getWorld()), out = [];
    for (var i = 0; i < ps.length; i++) {
        if (moFlat(ps[i], x, z) <= r && !moInSanctuary(ps[i].getX(), ps[i].getZ())) out.push(ps[i]);
    }
    return out;
}

// Удар — доля здоровья, броня не решает. Сначала Пожиратель душ: вырывает осколки и лечится.
function kStrike(npc, p, pct) {
    kEat(npc, p);
    var dmg = p.getMaxHealth() * pct;
    if (p.getHealth() - dmg < 1) {
        p.setHealth(1);
        Combat.dealPureDamage(p, 100, true);
        return;
    }
    p.setHealth(p.getHealth() - dmg);
    Combat.dealPureDamage(p, 0.01, true);
}

function kEat(npc, p) {
    var n = moShards(p);
    if (n <= 0) return;
    var taken = moTakeShards(p, Math.max(1, Math.ceil(n * EAT_PART)));
    if (taken <= 0) return;
    kPut(npc, "eaten", kNum(npc, "eaten") + taken);
    npc.setHealth(Math.min(npc.getMaxHealth(), npc.getHealth() + npc.getMaxHealth() * EAT_HEAL * taken));
    p.message("§2Кигор вырывает из тебя осколков: " + taken + ".");
}

function kPush(p, ox, oz, power) {
    p.knockback(power, Combat.computeYaw(p.getX() - ox, p.getZ() - oz));
}

// --- Удар кулаком ---

function startPunch(npc, t) {
    var yaw = kYawTo(npc, t);
    kFreeze(npc, yaw);
    kAnim(npc, ANIM_PUNCH);
    TelegraphAPI.cone(npc, npc.getX(), npc.getY(), npc.getZ(), yaw, PUNCH_LEN, PUNCH_HALF, PUNCH_WARN, RED);
    kTimer(npc, T_PUNCH, PUNCH_WARN, false);
}

function punchHit(npc) {
    var x = kNum(npc, "hold_x"), z = kNum(npc, "hold_z"), yaw = kNum(npc, "hold_yaw");
    kSound(npc, "minecraft:entity.ravager.attack", 1.0, 0.7);
    var ps = kPlayersAt(npc, x, z, PUNCH_LEN);
    for (var i = 0; i < ps.length; i++) {
        if (!Combat.isInFrontCone(x, z, yaw, ps[i], PUNCH_HALF)) continue;
        kStrike(npc, ps[i], PUNCH_PCT);
        kPush(ps[i], x, z, PUNCH_PUSH);
    }
    kRecover(npc, PUNCH_RECOVER);
}

// --- Топот ---

function startStomp(npc) {
    kFreeze(npc, npc.getRotation());
    kAnim(npc, ANIM_STOMP);
    TelegraphAPI.circle(npc, npc.getX(), npc.getY(), npc.getZ(), STOMP_R, STOMP_WARN, RED);
    kSound(npc, "minecraft:entity.ravager.roar", 1.0, 0.6);
    kTimer(npc, T_STOMP, STOMP_WARN, false);
}

function stompHit(npc) {
    var x = kNum(npc, "hold_x"), y = kNum(npc, "hold_y"), z = kNum(npc, "hold_z");
    npc.getWorld().spawnParticle("explosion", x, y + 0.3, z, 2.0, 0.2, 2.0, 0, 6);
    kSound(npc, "minecraft:entity.generic.explode", 1.0, 0.5);
    var ps = kPlayersAt(npc, x, z, STOMP_R);
    for (var i = 0; i < ps.length; i++) {
        kStrike(npc, ps[i], STOMP_PCT);
        kPush(ps[i], x, z, STOMP_PUSH);
    }
    kRecover(npc, STOMP_RECOVER);
}

// --- Глыба ---

// Глыбы в точки, где стоят цели сейчас: красный круг, через BOULDER_WARN удар и сбивает с ног.
function startBoulders(npc, aims) {
    var pts = [];
    var y = npc.getY();
    for (var i = 0; i < aims.length; i++) {
        var a = aims[i];
        pts.push(a.getX().toFixed(2) + ":" + a.getY().toFixed(2) + ":" + a.getZ().toFixed(2));
        TelegraphAPI.circle(npc, a.getX(), a.getY(), a.getZ(), BOULDER_R, BOULDER_WARN, RED);
        kArc(npc, a.getX(), a.getY(), a.getZ());
    }
    kPut(npc, "boulders", pts.join(";"));
    kFreeze(npc, kYawTo(npc, aims[0]));
    kAnim(npc, aims.length > 1 ? ANIM_BOULDER_VOLLEY : ANIM_BOULDER);
    kSound(npc, "minecraft:entity.ravager.roar", 1.0, 0.8);
    kTimer(npc, T_BOULDER, BOULDER_WARN, false);
    kTimer(npc, T_RECOVER, THROW_ROOT, false);
}

// След глыбы в воздухе: пыль по дуге от него к точке.
function kArc(npc, x, y, z) {
    var w = npc.getWorld(), sx = npc.getX(), sy = npc.getY() + 4, sz = npc.getZ();
    var d = Math.sqrt((x - sx) * (x - sx) + (z - sz) * (z - sz)), h = 4 + d / 4;
    for (var k = 1; k < 10; k++) {
        var f = k / 10;
        w.spawnParticle("cloud", sx + (x - sx) * f, sy + (y - sy) * f + h * 4 * f * (1 - f), sz + (z - sz) * f, 0.2, 0.2, 0.2, 0, 2);
    }
}

function boulderHit(npc) {
    var w = npc.getWorld();
    var pts = kGet(npc, "boulders", "").split(";");
    for (var i = 0; i < pts.length; i++) {
        var c = pts[i].split(":");
        if (c.length < 3) continue;
        var x = Number(c[0]), y = Number(c[1]), z = Number(c[2]);
        kSoundAt(w, x, y, z, "minecraft:entity.generic.explode", 0.8, 0.7);
        w.spawnParticle("explosion", x, y + 0.5, z, 1.0, 0.3, 1.0, 0, 4);
        w.spawnParticle("cloud", x, y + 0.3, z, 1.5, 0.3, 1.5, 0.02, 20);
        var ps = kPlayersAt(npc, x, z, BOULDER_R);
        for (var j = 0; j < ps.length; j++) {
            if (Math.abs(ps[j].getY() - y) > 4) continue;
            kStrike(npc, ps[j], BOULDER_PCT);
            if (ps[j].isAlive()) Combat.applyNamedEffect(ps[j], STUN_ID, BOULDER_STUN, 0);
        }
    }
}

// --- Рывок рогами ---

function startDash(npc, t) {
    var yaw = kYawTo(npc, t);
    var len = moFlat(t, npc.getX(), npc.getZ()) + 1.5;
    if (len > DASH_MAX) len = DASH_MAX;
    var x = npc.getX(), y = npc.getY(), z = npc.getZ();
    var rad = (yaw + 90) * Math.PI / 180;
    npc.getTimers().stop(T_HOLD);
    npc.getAi().setWalkingSpeed(0);
    kFace(npc, yaw);
    var ok = AbilityAPI.start(npc, "dash", t, AbilityAPI.params(
        "chargeTicks", DASH_CHARGE, "activeTicks", DASH_ACTIVE, "distance", len,
        "damage", DASH_DMG, "hitRadius", DASH_PATH_R, "knockback", 1.0, "knockbackY", 0.3));
    if (!ok) {
        kRelease(npc);
        return false;
    }
    kFaceOnly(npc, yaw);
    kAnim(npc, ANIM_DASH);
    TelegraphAPI.line(npc, x, y, z, yaw, len, DASH_W * 2, DASH_CHARGE, RED);
    TelegraphAPI.circle(npc, x + Math.cos(rad) * len, y, z + Math.sin(rad) * len, DASH_HIT_R, DASH_CHARGE + DASH_ACTIVE, RED);
    kSound(npc, "minecraft:entity.ravager.roar", 1.0, 1.2);
    kTimer(npc, T_DASH_END, DASH_CHARGE + DASH_ACTIVE + 2, false);
    return true;
}

function dashEnd(npc) {
    var x = npc.getX(), z = npc.getZ();
    kSound(npc, "minecraft:entity.player.attack.strong", 1.0, 0.6);
    var ps = kPlayersAt(npc, x, z, DASH_HIT_R);
    for (var i = 0; i < ps.length; i++) {
        kStrike(npc, ps[i], DASH_PCT);
        kPush(ps[i], x, z, DASH_PUSH);
    }
    kRecover(npc, DASH_RECOVER);
}

// --- Взгляд Ока ---

function startGaze(npc, t) {
    var yaw = kYawTo(npc, t);
    kFreeze(npc, yaw);
    kAnim(npc, ANIM_GAZE);
    TelegraphAPI.circle(npc, npc.getX(), npc.getY(), npc.getZ(), GAZE_R, GAZE_WARN + GAZE_TICKS, GREEN);
    kSound(npc, "minecraft:block.beacon.activate", 1.0, 0.5);
    npc.say("§2Око Кигора раскрывается.");
    kTimer(npc, T_GAZE, GAZE_WARN, false);
}

function gazeOpen(npc) {
    kPut(npc, "gaze_until", kNow(npc) + GAZE_TICKS);
    kPut(npc, "slam_on", 0);
    kTimer(npc, T_GAZE_BEAM, GAZE_PULSE, true);
    gazePulse(npc);
}

// Взгляд: кто в круге GAZE_R и в прямой видимости — засвечен, замедлен и тянется к нему. За блоком не достаёт.
// Под конец — замах удара оземь.
function gazePulse(npc) {
    var now = kNow(npc), until = kNum(npc, "gaze_until");
    if (now >= until - SLAM_LEAD && kGet(npc, "slam_on", "0") != "1") startSlam(npc);
    if (now >= until) {
        npc.getTimers().stop(T_GAZE_BEAM);
        return;
    }
    var x = kNum(npc, "hold_x"), y = kNum(npc, "hold_y"), z = kNum(npc, "hold_z");
    var w = npc.getWorld();
    for (var k = 0; k < 24; k++) {
        var a = k / 24 * Math.PI * 2;
        w.spawnParticle("happy_villager", x + Math.cos(a) * GAZE_R, y + 0.5, z + Math.sin(a) * GAZE_R, 0.2, 0.2, 0.2, 0, 1);
    }
    w.spawnParticle("end_rod", x, y + 4, z, 0.3, 0.3, 0.3, 0.05, 6);
    var ps = kPlayersAt(npc, x, z, GAZE_R);
    for (var i = 0; i < ps.length; i++) {
        var p = ps[i];
        if (!npc.canSeeEntity(p)) continue;
        moMark(w, p, MO.MARK_TICKS);
        p.addPotionEffect(2, GAZE_SLOW, 1, true);
        if (moFlat(p, x, z) > GAZE_HOLD) p.knockback(GAZE_PULL, Combat.computeYaw(x - p.getX(), z - p.getZ()));
    }
}

// Удар оземь: красный круг SLAM_R, клип топота, на его ударе — всем в круге SLAM_PCT и толчок прочь.
// После него Око открыто GAZE_OPEN тиков.
function startSlam(npc) {
    kPut(npc, "slam_on", 1);
    kAnim(npc, ANIM_STOMP);
    TelegraphAPI.circle(npc, kNum(npc, "hold_x"), kNum(npc, "hold_y"), kNum(npc, "hold_z"), SLAM_R, SLAM_WARN, RED);
    kSound(npc, "minecraft:entity.ravager.roar", 1.0, 0.5);
    kTimer(npc, T_SLAM, SLAM_WARN, false);
}

function slamHit(npc) {
    var x = kNum(npc, "hold_x"), y = kNum(npc, "hold_y"), z = kNum(npc, "hold_z");
    npc.getWorld().spawnParticle("explosion", x, y + 0.3, z, 1.5, 0.2, 1.5, 0, 6);
    kSound(npc, "minecraft:entity.generic.explode", 1.0, 0.5);
    var ps = kPlayersAt(npc, x, z, SLAM_R);
    for (var i = 0; i < ps.length; i++) {
        kStrike(npc, ps[i], SLAM_PCT);
        kPush(ps[i], x, z, SLAM_PUSH);
    }
    kPut(npc, "eye_open_until", kNow(npc) + GAZE_OPEN);
    kRecover(npc, GAZE_OPEN);
    npc.say("§6Око Кигора открыто – бей!");
}

// --- Вой ---

function kHowlSignal(npc) {
    var d = npc.getWorld().getTempdata();
    return d.has(MO_HOWL) ? String(d.get(MO_HOWL)) : "";
}

function startHowl(npc) {
    kFreeze(npc, npc.getRotation());
    kAnim(npc, ANIM_HOWL);
    kSound(npc, "minecraft:entity.wolf.howl", 1.0, 0.4);
    npc.say("§2Кигор воет на Моррслиб!");
    kTimer(npc, T_HOWL, HOWL_WARN, false);
}

// Всех в HOWL_R видно сквозь стены HOWL_GLOW секунд, и Кигор видит их HOWL_MARK тиков.
function howlHit(npc) {
    var w = npc.getWorld();
    var ps = kPlayersAt(npc, npc.getX(), npc.getZ(), HOWL_R);
    for (var i = 0; i < ps.length; i++) {
        ps[i].addPotionEffect(24, HOWL_GLOW, 0, false);
        moMark(w, ps[i], HOWL_MARK);
    }
    kRecover(npc, HOWL_RECOVER);
}

// --- Бой ---

function tryCast(npc, ps, marks, t, now) {
    var d = moFlat(t, npc.getX(), npc.getZ());
    var near = kPlayersAt(npc, npc.getX(), npc.getZ(), STOMP_R);
    if (now >= kNum(npc, "free_at")) {
        if (near.length > 0 && now >= kNum(npc, "stomp_at")) {
            kPut(npc, "stomp_at", now + STOMP_CD);
            startStomp(npc);
            return;
        }
        if (now >= kNum(npc, "gaze_at") && d <= GAZE_R) {
            kPut(npc, "gaze_at", now + GAZE_CD);
            startGaze(npc, t);
            return;
        }
        if (now >= kNum(npc, "boulder_at")) {
            var aims = kBoulderAims(npc, ps, marks, now, VOLLEY);
            if (aims.length > 0) {
                kPut(npc, "boulder_at", now + BOULDER_CD);
                startBoulders(npc, aims);
                return;
            }
        }
        if (now >= kNum(npc, "dash_at") && d >= DASH_MIN && d <= DASH_MAX) {
            kPut(npc, "dash_at", now + DASH_CD);
            if (startDash(npc, t)) return;
        }
    }
    if (d <= PUNCH_RANGE && now >= kNum(npc, "punch_at")) {
        kPut(npc, "punch_at", now + PUNCH_CD);
        startPunch(npc, t);
    }
}

// Цели глыб: видимые на BOULDER_MIN…BOULDER_MAX, самые яркие первыми; у алтаря — никого.
function kBoulderAims(npc, ps, marks, now, n) {
    var list = [];
    for (var i = 0; i < ps.length; i++) {
        var d = moFlat(ps[i], npc.getX(), npc.getZ());
        if (d < BOULDER_MIN || d > BOULDER_MAX || !kSees(npc, ps[i], marks, now)) continue;
        if (moInSanctuary(ps[i].getX(), ps[i].getZ())) continue;
        list.push([moShards(ps[i]), ps[i]]);
    }
    list.sort(function (a, b) { return b[0] - a[0]; });
    var out = [];
    for (var k = 0; k < list.length && k < n; k++) out.push(list[k][1]);
    return out;
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) { AbilityAPI.cancel(npc); return; }
    var w = npc.getWorld(), now = kNow(npc);
    npc.setAttackTarget(null);
    // Без добычи — заготовка для клонера: её ставят руками, чтобы настроить и сохранить. Стоит и ничего не делает.
    if (kGet(npc, "hunt", "").length == 0) return;
    if (kGmHidden(npc)) { kVanish(npc, "gm"); return; }
    if (!moRunning(w)) {
        kVanish(npc, "off");
        return;
    }
    if (kGet(npc, "run_seen", "0") != "1") {
        kPut(npc, "run_seen", 1);
        kPut(npc, "hunt_until", now + HUNT_TICKS);
        kSetHp(npc);
    }
    var marks = moMarksRead(w);
    var q = kQuarry(npc, w, marks, now);
    var why = kEscaped(npc, q, now);
    if (why != null) {
        kVanish(npc, why);
        return;
    }
    var signal = kHowlSignal(npc);
    if (signal != kGet(npc, "howl_seen", "") && !kBusy(npc)) {
        kPut(npc, "howl_seen", signal);
        startHowl(npc);
        return;
    }
    if (kBusy(npc)) {
        kPut(npc, "was_busy", 1);
        return;
    }
    if (kGet(npc, "was_busy", "0") == "1") {
        kPut(npc, "was_busy", 0);
        kPut(npc, "free_at", now + WALK_GAP);
    }
    if (moInSanctuary(q.getX(), q.getZ())) return;
    tryCast(npc, moPlayers(w), marks, q, now);
}

function timer(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    if (kGmHidden(npc)) { kVanish(npc, "gm"); return; }
    var id = e.id;
    if (id == T_HOLD) kHold(npc);
    else if (id == T_PUNCH) punchHit(npc);
    else if (id == T_STOMP) stompHit(npc);
    else if (id == T_BOULDER) boulderHit(npc);
    else if (id == T_DASH_END) dashEnd(npc);
    else if (id == T_GAZE) gazeOpen(npc);
    else if (id == T_GAZE_BEAM) gazePulse(npc);
    else if (id == T_HOWL) howlHit(npc);
    else if (id == T_RECOVER) kRelease(npc);
    else if (id == T_MOVE) kMove(npc);
    else if (id == T_SLAM) slamHit(npc);
}

function meleeAttack(e) {
    e.setCanceled(true);
}

// Вне ивента неуязвим. Ударивший игрок светится MO.MARK_TICKS; урон за удар не больше CAP, при открытом Оке ×1,5.
function damaged(e) {
    var npc = e.npc, w = npc.getWorld();
    if (!moRunning(w)) { e.setCanceled(true); return; }
    var src = e.source;
    if (src != null && src.typeOf(1)) moMark(w, src, MO.MARK_TICKS);
    var mul = kNow(npc) < kNum(npc, "eye_open_until") ? OPEN_MUL : 1;
    e.damage = Math.min(e.damage, CAP) * mul;
}

// Роняет всё съеденное: горстями, чтобы разлетелось. Добыче — пауза до следующего Кигора.
function died(e) {
    var npc = e.npc, w = npc.getWorld(), id = kGet(npc, "hunt", "");
    AbilityAPI.cancel(npc);
    if (id.length > 0) {
        var p = null;
        try { p = w.getEntity(id); } catch (err) {}
        moHuntCdSet(w, id, p != null ? moShards(p) : 0);
    }
    try { NpcTurnHelper.unlockFace(npc); } catch (err) {}
    for (var i = 0; i < ALL_TIMERS.length; i++) npc.getTimers().stop(ALL_TIMERS[i]);
    var n = kNum(npc, "eaten");
    kPut(npc, "eaten", 0);
    while (n > 0) {
        var k = Math.min(8, n);
        moSpill(w, npc.getX(), npc.getY(), npc.getZ(), moMakeShards(w, k));
        n -= k;
    }
    npc.say("§2Кигор падает. Съеденный свет Моррслиба рассыпается по земле.");
}
