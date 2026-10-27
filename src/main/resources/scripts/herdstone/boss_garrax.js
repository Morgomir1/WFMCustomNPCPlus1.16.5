// Гарракс Расколотый Рог (Б4), думбулл с двумя топорами. В контейнере вторым после hs_core.js.
// Финал данжа и бой против него самого: неуязвимых фаз, сердец и кристаллов нет, бить можно всегда.
// Сложность — в темпе и связках; окна — когда он шатается после вихря и когда оба топора в полёте.
// Фаза 1 (выше 66%): перекрёстный удар (слева, справа, третий — накрест прямо перед собой), вихрь,
//   Раскол — волна по полосе перед ним с оглушением WFM, за ней рывок в оглушённого; рывок к дальнему.
// Фаза 2 (66–33%): плюс бросок топора-бумеранга (пока топор летит — размах оставшимся вокруг себя)
//   и прыжок на Камень Стада: от Камня бегут кольца по земле (прыгать), середину арены заливает тьма.
// Фаза 3 (33% и ниже), Ночь Тайн: всё на треть чаще, вихрь длиннее и кончается Расколом в три стороны,
//   оба топора в полёт (безоружен — топчет), варп-молнии всё гуще; раз в минуту удары на 15% сильнее.
// Обычной атаки мода нет (meleeAttack отменяется): он идёт к цели, а бьёт скриптом.
// Визуальный размер GeckoLib — display.setGeckoScale (1 = файл модели). setSize его не меняет.
// Устье — пробка, которую ставит строитель, а не всё кольцо барьера.
// Кольцо (y входа +1…+4, между r+1 и r+3) остаётся. Скрипт открывает только
// южный проём 3×3×3: cx-1…+1, y1+1…+3, cz+27…+29. Плюс столбик у выхода на винт.
HS.BASE_HP = 1800;
HS.FIXED_SCALE = true;
HS.CAP = 14;
HS.ARENA_R = 26;
HS.ENRAGE_SEC = 99999;   // мягкая ярость ядра выключена — своя, rageTick
HS.DISPEL_SEC = 99999;   // «Рёв Стада» ядра снимал бы и оглушение Раскола
ORIGIN = [-373274, 29, -12965];   // мировой блок пола в центре арены (вход данжа -373504, 101, -13567). Сдвинул данж — впиши свой
GATE = hsBox(-32, 0, 10, 1, 2, 1);
GATE = GATE.concat(hsBox(-1, 73, 27, 3, 3, 3));
GATE_BLOCK = "minecraft:barrier";
// Прежний Гарракс звал горов, сердца и кристаллы; init снимает остатки, если они есть.
SUMMONS = ["Гор (призыв)", "Сердце жёлоба", "Варп-кристалл"];

// Цвет «предупреждения» библиотеки подсказок: в конце жизни она перекрашивает подсказку в него,
// так что короткие подсказки этого цвета не мигают.
var RED = 0xC0FF0000 | 0;
var STUN_ID = "wfm:stun";
var OPEN_MUL = 1.5;          // урон по нему, пока шатается или безоружен
var RAGE_EVERY = 1200, RAGE_STEP = 0.15;
// После любого приёма столько тиков он только идёт к цели и рубит (перекрёстный удар), без других приёмов.
var WALK_GAP = 40, WALK_GAP3 = 30;

// Перекрёстный удар: слева и справа (конусы, повёрнутые на COMBO_SIDE), третий — накрест по полосе.
var COMBO_RANGE = 6, COMBO_LEN = 6.5, COMBO_SIDE = 35, COMBO_HALF = 60, COMBO_PCT = 0.25;
var COMBO_T1 = 12, COMBO_T2 = 8, COMBO_T3 = 14, COMBO_RECOVER = 12, COMBO_CD = 60;
var CROSS_LEN = 8, CROSS_W = 1.6, CROSS_PCT = 0.45;
// Вихрь: weapon_spin мода крутит модель и рисует круг, а ведёт его скрипт — каждый тик шаг SPIN_SPEED к цели
// (медленнее бега игрока, быстрее шага), до SPIN_REACH от неё; в стену не входит, вдоль неё скользит. После —
// шатается. Урон — доля здоровья раз в SPIN_PULSE тиков всем в SPIN_R; свой плоский удар мода через броню слаб.
var SPIN_CHARGE = 20, SPIN_ACTIVE = 70, SPIN_ACTIVE3 = 100, SPIN_R = 5.0, SPIN_DMG = 1.0;
var SPIN_PCT = 0.18, SPIN_PULSE = 10, SPIN_SPEED = 0.25, SPIN_SPEED3 = 0.28, SPIN_REACH = 1.5;
// Сам мод везёт по прямой со скоростью approachSpeed, меньше 0,05 за тик не даёт. Способности мода ходят в конце
// серверного тика, после таймеров: скрипт каждый тик ставит его на своё место, мод добавляет те же 0,05 — сноса нет.
var SPIN_APPROACH = 0.05, SPIN_CD = 360, DIZZY_TICKS = 30;
// Шаг скрипта (вихрь, выход из угла): рамка Гарракса при setSize(10) — 1,2 на 3,6, Combat.canStandAt меряет 0,6.
// Поэтому свободны должны быть и место, и края корпуса (STEP_HALF в стороны) и передний край на STEP_EDGE.
var STEP_HALF = 0.5, STEP_EDGE = 1.0;
// Застрял: поиск пути CustomNPCs для такой рамки требует проход в 2 блока и в углах ступенчатой стены встаёт.
// Стоит на месте UNSTICK_AFTER проверок подряд (по 10 тиков, полторы секунды), а цель дальше удара — скрипт ведёт
// его к цели сам UNSTICK_TICKS тиков, дальше снова ИИ.
var UNSTICK_AFTER = 3, UNSTICK_TICKS = 20, UNSTICK_SPEED = 0.25, UNSTICK_MOVED = 0.3;
// Раскол: полоса шириной 6 и длиной 18, волна бежит по ней по 2 блока за 2 тика.
var QUAKE_CHARGE = 16, QUAKE_CHARGE_FAST = 10, QUAKE_LEN = 18, QUAKE_W = 3, QUAKE_STEP = 2;
var QUAKE_PCT = 0.3, QUAKE_STUN = 40, QUAKE_CD = 300, QUAKE_RECOVER = 10;
// Рывок с ударом: к самому дальнему (или к оглушённому Расколом), в конце — круг вокруг себя.
var DASH_CHARGE = 18, DASH_CHARGE_FOLLOW = 10, FOLLOW_DASH_DELAY = 4;
var DASH_ACTIVE = 8, DASH_MIN = 6, DASH_MAX = 22, DASH_W = 1.5, DASH_DMG = 8.0;
var DASH_HIT_R = 3.5, DASH_PCT = 0.4, DASH_CD = 240, DASH_RECOVER = 10, DASH_MIN_TARGET = 7;
// Бросок топора (axe_throw, бумеранг на THROW_LEN) и размах оставшимся вокруг себя. Мод бьёт кубом THROW_HIT_R
// вокруг самого топора, а топор летит по параболе высотой arcHeight (по умолчанию 3,5): на середине пути он шёл
// над головой. THROW_ARC — почти плоско, на высоте груди.
var THROW_CHARGE = 10, THROW_OUT = 14, THROW_HOLD = 4, THROW_BACK = 10, THROW_LEN = 20, THROW_HIT_R = 1.6;
var THROW_ARC = 0.4, THROW_MIN = 6, THROW_PCT = 0.25, THROW_CD = 240;
var QUICK_T = 8, SWEEP_AT = THROW_CHARGE + 6, SWEEP_R = 4.5, SWEEP_PCT = 0.3;
// Прыжок на Камень, кольца по земле и тьма вокруг Камня.
var LEAP_CHARGE = 30, LEAP_R = 3, LEAP_PCT = 0.35, LEAP_CD = 520, LEAP_RECOVER = 20;
// Кольцо — одна зона мода на всё время, ей каждый тик растёт радиус (подсказки, рисуемые заново, мерцали),
// и по её краю идёт ударная волна из частиц — через тик, чтобы не слать лишнего.
var RINGS = 3, RING_GAP = 16, RING_STEP = 0.5, RING_PCT = 0.55, RING_JUMP = 0.5;
// Тьма: круг VOID_R вокруг Камня (больше трети арены). Жёлтый с начала прыжка, тьма — через VOID_WARN после посадки.
var VOID_R = 17, VOID_WARN = 30, VOID_TICKS = 160, VOID_PCT = 0.08, VOID_EVERY = 10, VOID_COLOR = 0xC0400070 | 0;
// Оба топора в полёт: настоящий axe_throw в одного, второй топор скриптом; безоружен — топчет.
var STORM_LEN = 20, STORM_HIT_R = 1.5, STORM_PCT = 0.25, STORM_CD = 400, STORM_SPREAD = 25;
var STOMP_AT = 4, STOMP_R = 4, STOMP_PCT = 0.35;
// Варп-молнии Ночи Тайн: подсказка, затем удар; число растёт.
var BOLTS_EVERY = 80, BOLT_WARN = 30, BOLT_R = 5, BOLT_PCT = 0.35, BOLTS_MIN = 2, BOLTS_MAX = 6, BOLTS_GROW = 400;

var T_HOLD = 1, T_COMBO = 2, T_SPIN_END = 3, T_QUAKE = 4, T_QUAKE_STEP = 5, T_DASH_END = 6;
var T_QUICK = 7, T_LEAP = 8, T_RINGS = 9, T_VOID = 10, T_STORM = 11, T_STOMP = 12;
var T_BOLTS = 13, T_FORCE_DASH = 14, T_RECOVER = 15, T_SWEEP = 16, T_SPIN_GUARD = 17, T_UNSTICK = 18;
var ALL_TIMERS = [T_HOLD, T_COMBO, T_SPIN_END, T_QUAKE, T_QUAKE_STEP, T_DASH_END, T_QUICK, T_LEAP, T_RINGS,
    T_VOID, T_STORM, T_STOMP, T_BOLTS, T_FORCE_DASH, T_RECOVER, T_SWEEP, T_SPIN_GUARD, T_UNSTICK];
// Пока идёт любой из этих — новый приём не начинается.
var CAST_TIMERS = [T_COMBO, T_SPIN_END, T_QUAKE, T_QUAKE_STEP, T_DASH_END, T_QUICK, T_LEAP,
    T_FORCE_DASH, T_RECOVER];

// Клипы GeckoLib-модели Гарракса, как у Горгона: время от начала клипа совпадает с таймерами приёмов.
// Что в каждом – herdstone_garrax_animations.md в корне репозитория.
// Пока клипов нет в модели — держи false: вместо них взмахи рук (клип Attack из окна модели).
var GARRAX_NEW_ANIMS = false;
var ANIM_COMBO = "attack_combo";            // 2.30s: удары 0.60 и 1.00, накрест 1.70
var ANIM_SPIN = "spin_start";               // 1.00s, дальше ANIM_SPIN_LOOP до конца вихря
var ANIM_SPIN_LOOP = "spin_loop";           // 0.50s; саму модель крутит мод, 42° за тик
var ANIM_DIZZY = "dizzy";                   // 1.50s
var ANIM_QUAKE = "attack_quake";            // 2.20s: удар в землю 0.80, волна до 1.70; тройной — в 1.6 раза быстрее
var ANIM_DASH = "attack_dash";              // 1.90s: рывок 0.90–1.30, удар 1.40
var ANIM_DASH_QUICK = "attack_dash_quick";  // 1.50s: рывок 0.50–0.90, удар 1.00
var ANIM_THROW = "attack_throw";            // 2.00s: бросок 0.50, размах 1.20, топор в руке 1.90
var ANIM_STORM = "attack_storm";            // 2.00s: бросок обоих 0.50, топот 1.10, топоры в руках 1.90
var ANIM_LEAP = "attack_leap";              // 2.50s: приземление на Камень 1.50
var SPIN_LOOP_TICKS = 10;

function init(e) {
    var npc = e.npc;
    if (!hsInit(npc)) return;
    gRingsClear(npc);
    if (hsGet(npc, "base_speed", "") == "") hsPut(npc, "base_speed", npc.getAi().getWalkingSpeed());
    npc.getAi().setWalkingSpeed(hsNum(npc, "base_speed"));
    npc.getAi().setRetaliateType(0);
    npc.getDisplay().setSize(10);
    npc.getStats().setResistance(3, 2.0);   // отбрасывание: 2 = не сдвигается
    var keys = ["started", "rage", "dizzy_until", "unarmed_until", "combo_step"];
    for (var i = 0; i < keys.length; i++) hsPut(npc, keys[i], 0);
    hsPut(npc, "phase", 1);
}

// --- Общее ---

function gPhase(npc) {
    var hp = npc.getHealth() / npc.getMaxHealth();
    return hp > 0.66 ? 1 : (hp > 0.33 ? 2 : 3);
}

function cd(npc, ticks) {
    return hsNum(npc, "phase") >= 3 ? Math.round(ticks * 0.7) : ticks;
}

function gSpeed(npc) {
    var spd = hsNum(npc, "base_speed");
    if (hsNum(npc, "phase") >= 3) spd = Math.min(10, spd + 1);
    return spd;
}

// Камень Стада — центр арены по ORIGIN, там сходятся жёлобы. Высота — пол под ногами Гарракса.
function gStone(npc) {
    var o = hsOrigin(npc);
    return [o[0] + 0.5, o[2] + 0.5];
}

// Удары Гарракса — доля здоровья с яростью; крошечный урон через hurt даёт клиенту вздрогнуть.
function gStrike(npc, p, pct) {
    hsStrike(p, pct * (1 + RAGE_STEP * hsNum(npc, "rage")));
    if (p.isAlive()) Combat.dealPureDamage(p, 0.01, true);
}

function gPush(p, ox, oz, power) {
    p.knockback(power, Combat.computeYaw(p.getX() - ox, p.getZ() - oz));
}

// playSoundAt CustomNPCs слышен только в 16 блоках от точки, а арена радиусом 26.
function arenaSound(npc, sound, volume, pitch) {
    var c = hsArenaCenter(npc);
    var list = npc.getWorld().getNearbyEntities(NpcAPI.getIPos(c[0], c[1], c[2]), HS.ARENA_R + 16, 1);
    for (var i = 0; i < list.length; i++) list[i].playSound(sound, volume, pitch);
}

// speed — скорость клипа (1 — как нарисован), loops — сколько раз после него повторить ANIM_SPIN_LOOP.
function gAnim(npc, name, speed, loops) {
    if (!GARRAX_NEW_ANIMS) return;
    try {
        var b = NpcAPI.createAnimBuilder().playOnce(name);
        if (loops > 0) b.addRepeatingAnimation(ANIM_SPIN_LOOP, loops);
        npc.syncAnimationsForAll(b, speed > 0 ? speed : 1.0);
    } catch (err) {
        log("garrax anim: " + err);
    }
}

// Таймер CustomNPCs с числом N срабатывает на N+1-е обновление (Timer.update: «if (ticks-- > 0) return»), повторный —
// раз в N+1 тиков: «каждый тик» с 1 шёл раз в два. Здесь ticks — сколько тиков на самом деле.
function gTimer(npc, id, ticks, repeat) {
    npc.getTimers().forceStart(id, Math.max(0, ticks - 1), repeat);
}

function swingBoth(npc) {
    npc.swingMainhand();
    npc.swingOffhand();
}

// Стоит на месте лицом в одну сторону, пока идёт приём. Режим ответа на атаку не трогаем: CustomNPCs
// пересобирает ИИ после его смены раз в секунду, и после каждого приёма он стоял бы, не идя к цели.
function gFreeze(npc, yaw) {
    npc.getAi().setWalkingSpeed(0);
    hsPut(npc, "hold_x", npc.getX());
    hsPut(npc, "hold_y", npc.getY());
    hsPut(npc, "hold_z", npc.getZ());
    hsPut(npc, "hold_yaw", yaw);
    npc.setRotation(yaw);
    gTimer(npc, T_HOLD, 2, true);
}

function gHold(npc) {
    Combat.holdInPlace(npc, hsNum(npc, "hold_x"), hsNum(npc, "hold_y"), hsNum(npc, "hold_z"));
    npc.setRotation(hsNum(npc, "hold_yaw"));
}

function gRelease(npc) {
    npc.getTimers().stop(T_HOLD);
    npc.getAi().setWalkingSpeed(gSpeed(npc));
    gAiBack(npc);
}

// Бросок и вихрь мода на время полёта ставят «на атаку — ничего»; вернуть, только если не вернули.
function gAiBack(npc) {
    if (npc.getAi().getRetaliateType() != 0) npc.getAi().setRetaliateType(0);
}

function gRecover(npc, ticks) {
    gFreeze(npc, npc.getRotation());
    gTimer(npc, T_RECOVER, ticks, false);
}

function gBusy(npc, now) {
    if (AbilityAPI.isBusy(npc) || now < hsNum(npc, "dizzy_until")) return true;
    var t = npc.getTimers();
    for (var i = 0; i < CAST_TIMERS.length; i++) if (t.has(CAST_TIMERS[i])) return true;
    return false;
}

function nearest(npc, ps, r) {
    var best = null, bd = r;
    for (var i = 0; i < ps.length; i++) {
        var d = hsFlat(ps[i], npc.getX(), npc.getZ());
        if (d <= bd) { bd = d; best = ps[i]; }
    }
    return best;
}

function farthest(npc, ps, r) {
    var best = null, bd = -1;
    for (var i = 0; i < ps.length; i++) {
        var d = hsFlat(ps[i], npc.getX(), npc.getZ());
        if (d > bd && d <= r) { bd = d; best = ps[i]; }
    }
    return best;
}

function byId(npc, id) {
    if (id == null || String(id).length < 32) return null;
    try {
        var e = npc.getWorld().getEntity(String(id));
        if (e != null && e.isAlive()) return e;
    } catch (err) {}
    return null;
}

// Вдоль полосы и поперёк неё от точки (x, z) по yaw.
function along(p, x, z, yaw) {
    var rad = (yaw + 90) * Math.PI / 180;
    var dx = p.getX() - x, dz = p.getZ() - z;
    return [dx * Math.cos(rad) + dz * Math.sin(rad), Math.abs(dx * Math.sin(rad) - dz * Math.cos(rad))];
}

// --- Перекрёстный удар ---

function startCombo(npc, t) {
    var yaw = hsYawTo(npc, t);
    gFreeze(npc, yaw);
    hsPut(npc, "combo_step", 1);
    TelegraphAPI.cone(npc, npc.getX(), npc.getY(), npc.getZ(), yaw - COMBO_SIDE, COMBO_LEN, COMBO_HALF, COMBO_T1, RED);
    gAnim(npc, ANIM_COMBO);
    gTimer(npc, T_COMBO, COMBO_T1, false);
}

function comboStep(npc) {
    var step = hsNum(npc, "combo_step");
    var x = hsNum(npc, "hold_x"), y = hsNum(npc, "hold_y"), z = hsNum(npc, "hold_z"), yaw = hsNum(npc, "hold_yaw");
    var ps = hsPlayers(npc);
    var i;
    if (step == 1 || step == 2) {
        var side = step == 1 ? yaw - COMBO_SIDE : yaw + COMBO_SIDE;
        if (step == 1) npc.swingMainhand(); else npc.swingOffhand();
        arenaSound(npc, "minecraft:entity.player.attack.sweep", 1.0, 0.6);
        for (i = 0; i < ps.length; i++) {
            if (hsFlat(ps[i], x, z) <= COMBO_LEN && Combat.isInFrontCone(x, z, side, ps[i], COMBO_HALF)) gStrike(npc, ps[i], COMBO_PCT);
        }
        if (step == 1) {
            hsPut(npc, "combo_step", 2);
            TelegraphAPI.cone(npc, x, y, z, yaw + COMBO_SIDE, COMBO_LEN, COMBO_HALF, COMBO_T2, RED);
            gTimer(npc, T_COMBO, COMBO_T2, false);
        } else {
            hsPut(npc, "combo_step", 3);
            TelegraphAPI.line(npc, x, y, z, yaw, CROSS_LEN, CROSS_W * 2, COMBO_T3, RED);
            gTimer(npc, T_COMBO, COMBO_T3, false);
        }
        return;
    }
    swingBoth(npc);
    arenaSound(npc, "minecraft:entity.player.attack.strong", 1.0, 0.5);
    for (i = 0; i < ps.length; i++) {
        if (!hsOnLine(ps[i], x, z, yaw, CROSS_LEN, CROSS_W)) continue;
        gStrike(npc, ps[i], CROSS_PCT);
        gPush(ps[i], x, z, 1);
    }
    hsPut(npc, "combo_step", 0);
    gTimer(npc, T_RECOVER, COMBO_RECOVER, false);
}

// --- Вихрь ---

function startSpin(npc, t, phase) {
    var active = phase >= 3 ? SPIN_ACTIVE3 : SPIN_ACTIVE;
    var ok = hsStart(npc, "weapon_spin", t, AbilityAPI.params(
        "chargeTicks", SPIN_CHARGE, "activeTicks", active, "radius", SPIN_R,
        "damagePerTick", SPIN_DMG, "damageInterval", 5, "approachSpeed", SPIN_APPROACH,
        "knockback", 0.4, "knockbackY", 0.1));
    if (!ok) return false;
    var now = hsNow(npc);
    hsPut(npc, "spin_triple", phase >= 3 ? 1 : 0);
    hsPut(npc, "spin_speed", phase >= 3 ? SPIN_SPEED3 : SPIN_SPEED);
    hsPut(npc, "spin_t", String(t.getUUID()));
    // Мод шагает в конце тиков с SPIN_CHARGE-го по SPIN_CHARGE + active - 1 от старта.
    hsPut(npc, "spin_from", now + SPIN_CHARGE);
    hsPut(npc, "spin_pulse_at", now + SPIN_CHARGE);
    hsPut(npc, "spin_last", now + SPIN_CHARGE + active - 1);
    hsPut(npc, "spin_x", npc.getX());
    hsPut(npc, "spin_y", npc.getY());
    hsPut(npc, "spin_z", npc.getZ());
    // Клип не должен кончиться раньше вихря: его сменит dizzy или Раскол.
    gAnim(npc, ANIM_SPIN, 1, Math.ceil((active + 2) / SPIN_LOOP_TICKS));
    arenaSound(npc, "minecraft:entity.ravager.roar", 1.0, 1.1);
    gTimer(npc, T_SPIN_END, SPIN_CHARGE + active + 2, false);
    gTimer(npc, T_SPIN_GUARD, 1, true);
    return true;
}

// Каждый тик вихря: урон и шаг к цели.
function spinGuard(npc) {
    if (!AbilityAPI.isBusy(npc)) {
        npc.getTimers().stop(T_SPIN_GUARD);
        return;
    }
    spinPulse(npc);
    var now = hsNow(npc);
    if (now < hsNum(npc, "spin_from") || now > hsNum(npc, "spin_last")) return;
    var x = hsNum(npc, "spin_x"), y = hsNum(npc, "spin_y"), z = hsNum(npc, "spin_z");
    var t = spinTarget(npc);
    if (t != null) {
        var at = gSteer(npc, x, y, z, t.getX(), t.getZ(), hsNum(npc, "spin_speed"), SPIN_REACH);
        if (at != null) {
            x = at[0];
            y = at[1];
            z = at[2];
        }
    }
    hsPut(npc, "spin_x", x);
    hsPut(npc, "spin_y", y);
    hsPut(npc, "spin_z", z);
    Combat.holdInPlace(npc, x, y, z);
}

// Цель вихря — та, на кого он начал, пока жива и на арене; иначе ближайший.
function spinTarget(npc) {
    var c = hsArenaCenter(npc);
    var t = byId(npc, hsGet(npc, "spin_t", ""));
    if (t != null && hsInArena(t, c[0], c[2], 0)) return t;
    t = nearest(npc, hsPlayers(npc), 99);
    if (t != null) hsPut(npc, "spin_t", String(t.getUUID()));
    return t;
}

// Шаг к точке (tx, tz) не больше step и не ближе reach. Прямо; не вышло — под 45° в ту сторону, куда обходил прошлый
// раз, потом в другую, под 90°, вдоль осей (если вдоль оси шаг не крошечный: такой выигрывал бы у обхода и он полз бы
// у колонны). Сторону помнит, чтобы не метаться. null — шагнуть некуда или незачем.
function gSteer(npc, x, y, z, tx, tz, step, reach) {
    var dx = tx - x, dz = tz - z, d = Math.sqrt(dx * dx + dz * dz);
    if (d <= reach) return null;
    var len = Math.min(step, d - reach);
    var ux = dx / d * len, uz = dz / d * len;
    var side = hsNum(npc, "steer_side") < 0 ? -1 : 1;
    var turns = [0, 45 * side, -45 * side, 90 * side, -90 * side, "x", "z"];
    for (var i = 0; i < turns.length; i++) {
        var mx, mz;
        if (turns[i] == "x") { mx = ux; mz = 0; }
        else if (turns[i] == "z") { mx = 0; mz = uz; }
        else {
            var a = turns[i] * Math.PI / 180;
            mx = ux * Math.cos(a) - uz * Math.sin(a);
            mz = ux * Math.sin(a) + uz * Math.cos(a);
        }
        if (Math.sqrt(mx * mx + mz * mz) < len * 0.3) continue;
        var at = gFree(npc, x, y, z, mx, mz);
        if (at == null) continue;
        if (typeof turns[i] == "number" && turns[i] != 0) hsPut(npc, "steer_side", turns[i] > 0 ? 1 : -1);
        return at;
    }
    return null;
}

// Сдвиг на (mx, mz), если там помещается корпус целиком (середина и края на STEP_HALF) и свободен передний край;
// ступенька в блок — вверх или вниз.
function gFree(npc, x, y, z, mx, mz) {
    var m = Math.sqrt(mx * mx + mz * mz);
    if (m < 0.001) return null;
    var w = npc.getWorld();
    var nx = x + mx, nz = z + mz;
    var pts = [[0, 0], [STEP_HALF, 0], [-STEP_HALF, 0], [0, STEP_HALF], [0, -STEP_HALF], [mx / m * STEP_EDGE, mz / m * STEP_EDGE]];
    var dys = [0, 1, -1];
    for (var i = 0; i < dys.length; i++) {
        var ny = y + dys[i], ok = true;
        for (var k = 0; k < pts.length && ok; k++) ok = Combat.canStandAt(w, nx + pts[k][0], ny, nz + pts[k][1]);
        if (ok) return [nx, ny, nz];
    }
    return null;
}

// Проверка раз в 10 тиков: стоит, хотя свободен и цель дальше удара — ведём к цели сами UNSTICK_TICKS тиков.
function stuckCheck(npc, t, now) {
    var x = npc.getX(), z = npc.getZ();
    var moved = Math.sqrt(Math.pow(x - hsNum(npc, "last_x"), 2) + Math.pow(z - hsNum(npc, "last_z"), 2));
    hsPut(npc, "last_x", x);
    hsPut(npc, "last_z", z);
    if (npc.getTimers().has(T_UNSTICK)) return;
    if (moved >= UNSTICK_MOVED || hsFlat(t, x, z) <= COMBO_RANGE) {
        hsPut(npc, "still", 0);
        return;
    }
    hsPut(npc, "still", hsNum(npc, "still") + 1);
    if (hsNum(npc, "still") < UNSTICK_AFTER) return;
    hsPut(npc, "still", 0);
    hsPut(npc, "unstick_until", now + UNSTICK_TICKS);
    gTimer(npc, T_UNSTICK, 1, true);
}

function unstickStep(npc, now) {
    var t = npc.getAttackTarget();
    if (now > hsNum(npc, "unstick_until") || gBusy(npc, now) || t == null || !t.isAlive()) {
        npc.getTimers().stop(T_UNSTICK);
        return;
    }
    var at = gSteer(npc, npc.getX(), npc.getY(), npc.getZ(), t.getX(), t.getZ(), UNSTICK_SPEED, COMBO_RANGE - 1);
    if (at == null) {
        npc.getTimers().stop(T_UNSTICK);
        return;
    }
    npc.setPosition(at[0], at[1], at[2]);
}

function spinPulse(npc) {
    var now = hsNow(npc);
    if (now < hsNum(npc, "spin_pulse_at") || now > hsNum(npc, "spin_last")) return;
    hsPut(npc, "spin_pulse_at", hsNum(npc, "spin_pulse_at") + SPIN_PULSE);
    var ps = hsPlayersAt(npc, npc.getX(), npc.getY(), npc.getZ(), SPIN_R);
    for (var i = 0; i < ps.length; i++) gStrike(npc, ps[i], SPIN_PCT);
}

function spinEnd(npc) {
    npc.getTimers().stop(T_SPIN_GUARD);
    if (hsGet(npc, "spin_triple", "0") == "1") {
        var yaw = npc.getRotation();
        var t = nearest(npc, hsPlayers(npc), 99);
        if (t != null) yaw = hsYawTo(npc, t);
        startQuake(npc, [yaw, yaw + 120, yaw + 240], QUAKE_CHARGE_FAST, true);
        return;
    }
    startDizzy(npc);
}

function startDizzy(npc) {
    hsPut(npc, "dizzy_until", hsNow(npc) + DIZZY_TICKS);
    gRecover(npc, DIZZY_TICKS);
    gAnim(npc, ANIM_DIZZY);
    arenaSound(npc, "minecraft:entity.ravager.stunned", 1.0, 0.8);
    npc.getWorld().spawnParticle("crit", npc.getX(), npc.getY() + 3.2, npc.getZ(), 0.6, 0.3, 0.6, 0.05, 20);
    npc.say("§6Гарракс шатается!");
}

// --- Раскол ---

function startQuake(npc, yaws, charge, dizzyAfter) {
    var x = npc.getX(), y = npc.getY(), z = npc.getZ();
    gFreeze(npc, yaws[0]);
    hsPut(npc, "q_x", x);
    hsPut(npc, "q_y", y);
    hsPut(npc, "q_z", z);
    hsPut(npc, "q_yaws", yaws.join(","));
    hsPut(npc, "q_r", 0);
    hsPut(npc, "q_hit", "");
    hsPut(npc, "q_stunned", "");
    hsPut(npc, "q_dizzy", dizzyAfter ? 1 : 0);
    for (var i = 0; i < yaws.length; i++) TelegraphAPI.line(npc, x, y, z, yaws[i], QUAKE_LEN, QUAKE_W * 2, charge, RED);
    gAnim(npc, ANIM_QUAKE, QUAKE_CHARGE / charge);
    arenaSound(npc, "minecraft:entity.ravager.roar", 1.0, 0.7);
    gTimer(npc, T_QUAKE, charge, false);
}

function quakeRelease(npc) {
    swingBoth(npc);
    arenaSound(npc, "minecraft:entity.generic.explode", 1.0, 0.6);
    gTimer(npc, T_QUAKE_STEP, 2, true);
}

function quakeStep(npc) {
    var x = hsNum(npc, "q_x"), y = hsNum(npc, "q_y"), z = hsNum(npc, "q_z");
    var yaws = hsGet(npc, "q_yaws", "").split(",");
    var r0 = hsNum(npc, "q_r"), r1 = r0 + QUAKE_STEP;
    var hit = hsGet(npc, "q_hit", ""), stunned = hsGet(npc, "q_stunned", "");
    var ps = hsPlayers(npc);
    var w = npc.getWorld();
    for (var k = 0; k < yaws.length; k++) {
        var yaw = Number(yaws[k]);
        var rad = (yaw + 90) * Math.PI / 180;
        var fx = Math.cos(rad), fz = Math.sin(rad);
        for (var s = -1; s <= 1; s++) {
            w.spawnParticle("cloud", x + fx * r1 - fz * s * QUAKE_W, y + 0.2, z + fz * r1 + fx * s * QUAKE_W, 0.3, 0.1, 0.3, 0.02, 4);
        }
        w.spawnParticle("explosion", x + fx * r1, y + 0.5, z + fz * r1, 0, 0, 0, 0, 1);
        for (var i = 0; i < ps.length; i++) {
            var id = String(ps[i].getUUID());
            if (hit.indexOf(id) >= 0) continue;
            var a = along(ps[i], x, z, yaw);
            if (a[0] <= r0 || a[0] > r1 || a[1] > QUAKE_W) continue;
            hit += id + ";";
            gStrike(npc, ps[i], QUAKE_PCT);
            if (!ps[i].isAlive()) continue;
            Combat.applyNamedEffect(ps[i], STUN_ID, QUAKE_STUN, 0);
            ps[i].message("§6Раскол сбивает тебя с ног!");
            stunned += id + ";";
        }
    }
    hsPut(npc, "q_hit", hit);
    hsPut(npc, "q_stunned", stunned);
    hsPut(npc, "q_r", r1);
    if (r1 < QUAKE_LEN) return;
    npc.getTimers().stop(T_QUAKE_STEP);
    if (hsGet(npc, "q_dizzy", "0") == "1") {
        startDizzy(npc);
        return;
    }
    var target = null, bd = 999;
    var ids = stunned.split(";");
    for (var j = 0; j < ids.length; j++) {
        var p = byId(npc, ids[j]);
        if (p == null) continue;
        var d = hsFlat(p, npc.getX(), npc.getZ());
        if (d < bd) { bd = d; target = p; }
    }
    if (target != null) {
        hsPut(npc, "force_dash", String(target.getUUID()));
        gTimer(npc, T_FORCE_DASH, FOLLOW_DASH_DELAY, false);
        return;
    }
    gTimer(npc, T_RECOVER, QUAKE_RECOVER, false);
}

// --- Рывок с ударом ---

function startDash(npc, t, charge) {
    var yaw = hsYawTo(npc, t);
    var len = hsFlat(npc, t.getX(), t.getZ()) + 1.5;
    if (len < DASH_MIN) len = DASH_MIN;
    if (len > DASH_MAX) len = DASH_MAX;
    var x = npc.getX(), y = npc.getY(), z = npc.getZ();
    var rad = (yaw + 90) * Math.PI / 180;
    npc.getTimers().stop(T_HOLD);
    npc.getAi().setWalkingSpeed(0);
    npc.setRotation(yaw);
    var ok = hsStart(npc, "dash", t, AbilityAPI.params(
        "chargeTicks", charge, "activeTicks", DASH_ACTIVE, "distance", len,
        "damage", DASH_DMG, "hitRadius", 1.8, "knockback", 1.0, "knockbackY", 0.3));
    if (!ok) {
        gRelease(npc);
        return false;
    }
    TelegraphAPI.line(npc, x, y, z, yaw, len, DASH_W * 2, charge, RED);
    TelegraphAPI.circle(npc, x + Math.cos(rad) * len, y, z + Math.sin(rad) * len, DASH_HIT_R, charge + DASH_ACTIVE, RED);
    gAnim(npc, charge < DASH_CHARGE ? ANIM_DASH_QUICK : ANIM_DASH);
    arenaSound(npc, "minecraft:entity.ravager.roar", 1.0, 1.3);
    gTimer(npc, T_DASH_END, charge + DASH_ACTIVE + 2, false);
    return true;
}

function dashEnd(npc) {
    var x = npc.getX(), y = npc.getY(), z = npc.getZ();
    swingBoth(npc);
    arenaSound(npc, "minecraft:entity.player.attack.strong", 1.0, 0.6);
    var ps = hsPlayersAt(npc, x, y, z, DASH_HIT_R);
    for (var i = 0; i < ps.length; i++) {
        gStrike(npc, ps[i], DASH_PCT);
        gPush(ps[i], x, z, 1);
    }
    gRecover(npc, DASH_RECOVER);
}

// --- Удар вокруг себя: размах оставшимся топором или топот безоружного ---

function startAround(npc, r, pct) {
    hsPut(npc, "ar_x", npc.getX());
    hsPut(npc, "ar_y", npc.getY());
    hsPut(npc, "ar_z", npc.getZ());
    hsPut(npc, "ar_r", r);
    hsPut(npc, "ar_pct", pct);
    TelegraphAPI.circle(npc, npc.getX(), npc.getY(), npc.getZ(), r, QUICK_T, RED);
    gTimer(npc, T_QUICK, QUICK_T, false);
}

function aroundHit(npc) {
    var x = hsNum(npc, "ar_x"), y = hsNum(npc, "ar_y"), z = hsNum(npc, "ar_z");
    npc.swingMainhand();
    arenaSound(npc, "minecraft:entity.player.attack.knockback", 1.0, 0.6);
    var ps = hsPlayersAt(npc, x, y, z, hsNum(npc, "ar_r"));
    for (var i = 0; i < ps.length; i++) {
        gStrike(npc, ps[i], hsNum(npc, "ar_pct"));
        gPush(ps[i], x, z, 1);
    }
}

// --- Бросок топора ---

function throwAxe(npc, t) {
    return hsStart(npc, "axe_throw", t, AbilityAPI.params(
        "chargeTicks", THROW_CHARGE, "maxRange", THROW_LEN, "hitRadius", THROW_HIT_R,
        "outboundTicks", THROW_OUT, "holdTicks", THROW_HOLD, "returnTicks", THROW_BACK, "arcHeight", THROW_ARC,
        "damagePercent", THROW_PCT * (1 + RAGE_STEP * hsNum(npc, "rage")),
        "effectDuration", 40, "effectAmplifier", 1));
}

function startThrow(npc, t) {
    if (!throwAxe(npc, t)) return false;
    gAnim(npc, ANIM_THROW);
    gTimer(npc, T_SWEEP, SWEEP_AT, false);
    return true;
}

// --- Прыжок на Камень, кольца, тьма ---

function startLeap(npc) {
    var s = gStone(npc);
    var y = npc.getY();
    hsPut(npc, "floor_y", y);
    gFreeze(npc, npc.getRotation());
    TelegraphAPI.circle(npc, s[0], y, s[1], LEAP_R, LEAP_CHARGE, RED);
    gAnim(npc, ANIM_LEAP);
    arenaSound(npc, "minecraft:entity.ravager.roar", 1.0, 0.5);
    npc.say("§4Кровь – Камню!");
    gTimer(npc, T_LEAP, LEAP_CHARGE, false);
    startVoid(npc, s, y, hsNow(npc));
}

// Куда встать у Камня: сам центр или до трёх блоков выше; занято всё — остаётся где стоял.
function leapSpot(npc, s, y) {
    var w = npc.getWorld();
    for (var dy = 0; dy <= 3; dy++) {
        if (Combat.canStandAt(w, s[0], y + dy, s[1])) return [s[0], y + dy, s[1]];
    }
    return [npc.getX(), npc.getY(), npc.getZ()];
}

function leapLand(npc, now) {
    var s = gStone(npc), y = hsNum(npc, "floor_y");
    var at = leapSpot(npc, s, y);
    npc.setPosition(at[0], at[1], at[2]);
    hsPut(npc, "hold_x", at[0]);
    hsPut(npc, "hold_y", at[1]);
    hsPut(npc, "hold_z", at[2]);
    swingBoth(npc);
    npc.getWorld().spawnParticle("explosion", s[0], y + 0.5, s[1], 1.5, 0.3, 1.5, 0, 8);
    arenaSound(npc, "minecraft:entity.generic.explode", 1.0, 0.5);
    var ps = hsPlayersAt(npc, s[0], y, s[1], LEAP_R);
    for (var i = 0; i < ps.length; i++) {
        gStrike(npc, ps[i], LEAP_PCT);
        gPush(ps[i], s[0], s[1], 1);
    }
    hsPut(npc, "rings_from", now);
    hsPut(npc, "ring_r", "0,0,0");
    hsPut(npc, "ring_hit", "||");
    gTimer(npc, T_RINGS, 1, true);
    gTimer(npc, T_RECOVER, LEAP_RECOVER, false);
}

// Кольца от Камня, по одному раз в RING_GAP тиков; на полу — удар и толчок, в прыжке — мимо.
function ringsStep(npc, now) {
    var s = gStone(npc), y = hsNum(npc, "floor_y");
    var rs = hsGet(npc, "ring_r", "0,0,0").split(",");
    var hits = hsGet(npc, "ring_hit", "||").split("|");
    var from = hsNum(npc, "rings_from");
    var ps = hsPlayers(npc);
    var left = 0;
    for (var k = 0; k < RINGS; k++) {
        var r0 = Number(rs[k]);
        if (r0 >= HS.ARENA_R + 1) continue;
        left++;
        if (now < from + k * RING_GAP) continue;
        var r1 = r0 + RING_STEP;
        var zone = gRing(npc, k);
        if (zone == null && r0 == 0) {
            zone = ZoneAPI.hazardRing(npc, s[0], y + 0.05, s[1], 0.5, 0.05, 200, 0, 999);
            if (zone != null) {
                zone.setColor(RED);
                npc.getTempdata().put("g_ring" + k, zone);
            }
        }
        if (zone != null) {
            zone.setRadius(r1 + 0.5);
            zone.setInnerRadius(Math.max(0.05, r1 - 1));
        }
        if (Math.round(r1 * 2) % 2 == 0) shockwave(npc, s[0], y, s[1], r1);
        for (var i = 0; i < ps.length; i++) {
            var id = String(ps[i].getUUID());
            if (hits[k].indexOf(id) >= 0) continue;
            var d = hsFlat(ps[i], s[0], s[1]);
            if (d <= r0 || d > r1) continue;
            hits[k] += id + ";";
            if (ps[i].getY() - y >= RING_JUMP) continue;
            gStrike(npc, ps[i], RING_PCT);
            gPush(ps[i], s[0], s[1], 1);
        }
        rs[k] = r1;
        if (r1 >= HS.ARENA_R + 1) gRingClear(npc, k);
    }
    hsPut(npc, "ring_r", rs.join(","));
    hsPut(npc, "ring_hit", hits.join("|"));
    if (left == 0) npc.getTimers().stop(T_RINGS);
}

function gRing(npc, k) {
    var td = npc.getTempdata();
    if (td == null || !td.has("g_ring" + k)) return null;
    var zone = td.get("g_ring" + k);
    try {
        if (zone == null || zone.removed) {
            td.remove("g_ring" + k);
            return null;
        }
    } catch (err) {
        td.remove("g_ring" + k);
        return null;
    }
    return zone;
}

function gRingClear(npc, k) {
    var zone = gRing(npc, k);
    if (zone == null) return;
    npc.getTempdata().remove("g_ring" + k);
    try {
        ZoneAPI.remove(zone);
    } catch (err) {
        log("garrax ring: " + err);
    }
}

function gRingsClear(npc) {
    for (var k = 0; k < RINGS; k++) gRingClear(npc, k);
}

// Кольцо пыли и искр на радиусе r, разлетается наружу. Точек — примерно через 2 блока, не больше 36.
function shockwave(npc, x, y, z, r) {
    var w = npc.getWorld();
    var n = Math.max(8, Math.min(36, Math.ceil(2 * Math.PI * r / 2)));
    for (var i = 0; i < n; i++) {
        var a = (i + Math.random() * 0.3) / n * Math.PI * 2;
        var c = Math.cos(a), sn = Math.sin(a);
        w.spawnParticle("cloud", x + c * r, y + 0.15, z + sn * r, c * 0.3, 0.03, sn * 0.3, 1.0, 0);
        if (i % 3 == 0) w.spawnParticle("crit", x + c * r, y + 0.4, z + sn * r, c * 0.4, 0.15, sn * 0.4, 1.0, 0);
    }
}

// Тьма вокруг Камня: зона мода показывает её (с начала прыжка — жёлтым), урон долей здоровья — скриптом.
function startVoid(npc, s, y, now) {
    var on = now + LEAP_CHARGE + VOID_WARN;
    var zone = ZoneAPI.hazardCircle(npc, s[0], y + 0.05, s[1], VOID_R, on - now + VOID_TICKS, 0, 999);
    if (zone != null) {
        zone.setColor(HS.GOLD);
        hsZonePut(npc, zone);
    }
    hsPut(npc, "void_on", on);
    hsPut(npc, "void_until", on + VOID_TICKS);
    hsPut(npc, "void_lit", 0);
    hsPut(npc, "void_hit_at", on);
    gTimer(npc, T_VOID, 1, true);
}

function voidStep(npc, now) {
    if (now >= hsNum(npc, "void_until")) {
        npc.getTimers().stop(T_VOID);
        hsZoneClear(npc);
        return;
    }
    if (now < hsNum(npc, "void_on")) return;
    if (hsGet(npc, "void_lit", "0") != "1") {
        hsPut(npc, "void_lit", 1);
        var td = npc.getTempdata();
        try {
            if (td != null && td.has(HS_ZONE)) td.get(HS_ZONE).setColor(VOID_COLOR);
        } catch (err) {
            log("garrax void: " + err);
        }
        arenaSound(npc, "minecraft:entity.wither.ambient", 1.0, 0.6);
        npc.say("§5Камень пьёт свет – вокруг него тьма!");
    }
    if (now < hsNum(npc, "void_hit_at")) return;
    hsPut(npc, "void_hit_at", hsNum(npc, "void_hit_at") + VOID_EVERY);
    var s = gStone(npc);
    var ps = hsPlayers(npc);
    for (var i = 0; i < ps.length; i++) {
        if (hsFlat(ps[i], s[0], s[1]) <= VOID_R) gStrike(npc, ps[i], VOID_PCT);
    }
}

// --- Оба топора в полёт ---

function startStorm(npc, ps, t) {
    if (!throwAxe(npc, t)) return false;
    var other = null, bd = -1;
    for (var i = 0; i < ps.length; i++) {
        if (String(ps[i].getUUID()) == String(t.getUUID())) continue;
        var d = hsFlat(ps[i], npc.getX(), npc.getZ());
        if (d > bd && d <= STORM_LEN) { bd = d; other = ps[i]; }
    }
    // Второго нет — второй топор идёт рядом с первым, вилкой.
    var yaw = other != null ? hsYawTo(npc, other) : hsYawTo(npc, t) + STORM_SPREAD;
    var x = npc.getX(), y = npc.getY(), z = npc.getZ();
    var now = hsNow(npc), release = now + THROW_CHARGE, flight = THROW_OUT + THROW_HOLD + THROW_BACK;
    hsPut(npc, "st_x", x);
    hsPut(npc, "st_y", y);
    hsPut(npc, "st_z", z);
    hsPut(npc, "st_yaw", yaw);
    hsPut(npc, "st_release", release);
    hsPut(npc, "st_hit", "|");
    hsPut(npc, "st_prev", 0);
    hsPut(npc, "unarmed_until", release + flight);
    TelegraphAPI.line(npc, x, y, z, yaw, STORM_LEN, STORM_HIT_R * 2, THROW_CHARGE + flight, RED);
    gAnim(npc, ANIM_STORM);
    npc.say("§4Гарракс бросает оба топора!");
    gTimer(npc, T_STORM, 2, true);
    gTimer(npc, T_STOMP, THROW_CHARGE + STOMP_AT, false);
    return true;
}

// Второй топор летит туда и обратно по своей полосе; бьёт раз на каждом пролёте.
function stormStep(npc, now) {
    var e = now - hsNum(npc, "st_release");
    if (e < 0) return;
    if (e > THROW_OUT + THROW_HOLD + THROW_BACK) {
        npc.getTimers().stop(T_STORM);
        return;
    }
    var dist, pass;
    if (e <= THROW_OUT) { dist = STORM_LEN * e / THROW_OUT; pass = 0; }
    else if (e <= THROW_OUT + THROW_HOLD) { dist = STORM_LEN; pass = 0; }
    else { dist = STORM_LEN * (1 - (e - THROW_OUT - THROW_HOLD) / THROW_BACK); pass = 1; }
    var x = hsNum(npc, "st_x"), y = hsNum(npc, "st_y"), z = hsNum(npc, "st_z"), yaw = hsNum(npc, "st_yaw");
    var rad = (yaw + 90) * Math.PI / 180;
    var px = x + Math.cos(rad) * dist, pz = z + Math.sin(rad) * dist;
    var w = npc.getWorld();
    w.spawnParticle("sweep_attack", px, y + 1.2, pz, 0.2, 0.2, 0.2, 0, 1);
    w.spawnParticle("crit", px, y + 1.2, pz, 0.3, 0.3, 0.3, 0.1, 6);
    // На обратном пути топор пролетает 4 блока за шаг: бьёт всех на отрезке от прошлого шага до этого.
    var prev = hsNum(npc, "st_prev"), lo = Math.min(prev, dist) - STORM_HIT_R, hi = Math.max(prev, dist) + STORM_HIT_R;
    hsPut(npc, "st_prev", dist);
    var hits = hsGet(npc, "st_hit", "|").split("|");
    var ps = hsPlayers(npc);
    for (var i = 0; i < ps.length; i++) {
        var id = String(ps[i].getUUID());
        if (hits[pass].indexOf(id) >= 0) continue;
        var a = along(ps[i], x, z, yaw);
        if (a[0] < lo || a[0] > hi || a[1] > STORM_HIT_R) continue;
        hits[pass] += id + ";";
        gStrike(npc, ps[i], STORM_PCT);
    }
    hsPut(npc, "st_hit", hits.join("|"));
}

// --- Варп-молнии ---

function boltsTick(npc, ps, phase, now) {
    if (phase < 3 || now < hsNum(npc, "bolts_at") || npc.getTimers().has(T_BOLTS)) return;
    hsPut(npc, "bolts_at", now + BOLTS_EVERY);
    var n = BOLTS_MIN + Math.floor((now - hsNum(npc, "p3_at")) / BOLTS_GROW);
    if (n > BOLTS_MAX) n = BOLTS_MAX;
    var c = hsArenaCenter(npc);
    var y = npc.getY();
    var pts = [];
    for (var i = 0; i < n; i++) {
        var x, z;
        if (i % 2 == 0 && ps.length > 0) {
            var p = hsRandom(ps);
            x = p.getX();
            z = p.getZ();
        } else {
            var a = Math.random() * Math.PI * 2, r = Math.random() * (HS.ARENA_R - 4);
            x = c[0] + Math.cos(a) * r;
            z = c[2] + Math.sin(a) * r;
        }
        pts.push(x.toFixed(2) + ":" + z.toFixed(2));
        TelegraphAPI.circle(npc, x, y, z, BOLT_R, BOLT_WARN, HS.PURPLE);
    }
    hsPut(npc, "bolts", pts.join(";"));
    hsPut(npc, "bolts_y", y);
    gTimer(npc, T_BOLTS, BOLT_WARN, false);
}

function boltsHit(npc) {
    var y = hsNum(npc, "bolts_y");
    var pts = hsGet(npc, "bolts", "").split(";");
    var w = npc.getWorld();
    arenaSound(npc, "minecraft:entity.lightning_bolt.thunder", 0.8, 1.2);
    for (var i = 0; i < pts.length; i++) {
        var xz = pts[i].split(":");
        if (xz.length < 2) continue;
        var x = Number(xz[0]), z = Number(xz[1]);
        w.spawnParticle("end_rod", x, y + 2, z, 0.2, 2.0, 0.2, 0.05, 30);
        w.spawnParticle("explosion", x, y + 0.3, z, 0, 0, 0, 0, 1);
        var ps = hsPlayersAt(npc, x, y, z, BOLT_R);
        for (var j = 0; j < ps.length; j++) gStrike(npc, ps[j], BOLT_PCT);
    }
}

// --- Фазы и ярость ---

function enterPhase(npc, phase, now) {
    hsPut(npc, "phase", phase);
    if (phase == 2) {
        hsPut(npc, "throw_at", now + 40);
        hsPut(npc, "leap_at", now + 100);
        npc.say("§4Хватит игр. Камень хочет крови!");
    }
    if (phase >= 3) {
        hsPut(npc, "p3_at", now);
        hsPut(npc, "storm_at", now + 120);
        hsPut(npc, "bolts_at", now + 60);
        hsPut(npc, "rage_at", now + RAGE_EVERY);
        if (hsNum(npc, "throw_at") == 0) hsPut(npc, "throw_at", now + 40);
        if (hsNum(npc, "leap_at") == 0) hsPut(npc, "leap_at", now + 200);
        npc.say("§5Ночь Тайн! Над колодцем гаснут звёзды.");
    }
}

function rageTick(npc, phase, now) {
    if (phase < 3 || now < hsNum(npc, "rage_at")) return;
    hsPut(npc, "rage", hsNum(npc, "rage") + 1);
    hsPut(npc, "rage_at", now + RAGE_EVERY);
    npc.say("§4Ярость Гарракса растёт.");
}

function startFight(npc, now) {
    hsPut(npc, "started", 1);
    hsPut(npc, "combo_at", now + 20);
    hsPut(npc, "quake_at", now + 100);
    hsPut(npc, "dash_at", now + 160);
    hsPut(npc, "spin_at", now + 240);
    var keys = ["throw_at", "leap_at", "storm_at", "bolts_at", "rage_at", "p3_at", "free_at", "was_busy"];
    for (var i = 0; i < keys.length; i++) hsPut(npc, keys[i], 0);
}

function tryCast(npc, ps, t, phase, now) {
    if (now < hsNum(npc, "free_at")) {
        tryCombo(npc, t, now);
        return;
    }
    if (phase >= 2 && now >= hsNum(npc, "leap_at")) {
        hsPut(npc, "leap_at", now + cd(npc, LEAP_CD));
        startLeap(npc);
        return;
    }
    if (phase >= 3 && now >= hsNum(npc, "storm_at")) {
        hsPut(npc, "storm_at", now + cd(npc, STORM_CD));
        if (startStorm(npc, ps, t)) return;
    }
    if (now >= hsNum(npc, "spin_at")) {
        hsPut(npc, "spin_at", now + cd(npc, SPIN_CD));
        if (startSpin(npc, t, phase)) return;
    }
    if (now >= hsNum(npc, "quake_at") && hsFlat(t, npc.getX(), npc.getZ()) <= QUAKE_LEN) {
        hsPut(npc, "quake_at", now + cd(npc, QUAKE_CD));
        startQuake(npc, [hsYawTo(npc, t)], QUAKE_CHARGE, false);
        return;
    }
    if (phase >= 2 && now >= hsNum(npc, "throw_at")) {
        var aim = farthest(npc, ps, THROW_LEN - 1);
        if (aim != null && hsFlat(aim, npc.getX(), npc.getZ()) >= THROW_MIN) {
            hsPut(npc, "throw_at", now + cd(npc, THROW_CD));
            if (startThrow(npc, aim)) return;
        }
    }
    var far = farthest(npc, ps, 99);
    if (now >= hsNum(npc, "dash_at") && far != null && hsFlat(far, npc.getX(), npc.getZ()) >= DASH_MIN_TARGET) {
        hsPut(npc, "dash_at", now + cd(npc, DASH_CD));
        if (startDash(npc, far, DASH_CHARGE)) return;
    }
    tryCombo(npc, t, now);
}

function tryCombo(npc, t, now) {
    if (now < hsNum(npc, "combo_at") || hsFlat(t, npc.getX(), npc.getZ()) > COMBO_RANGE) return;
    hsPut(npc, "combo_at", now + cd(npc, COMBO_CD));
    startCombo(npc, t);
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) { AbilityAPI.cancel(npc); return; }
    var ps = hsFight(npc);
    if (ps == null) return;
    var now = hsNow(npc);
    if (hsGet(npc, "started", "0") != "1") startFight(npc, now);
    var phase = gPhase(npc);
    if (phase > hsNum(npc, "phase")) enterPhase(npc, phase, now);
    phase = hsNum(npc, "phase");
    rageTick(npc, phase, now);
    boltsTick(npc, ps, phase, now);
    if (gBusy(npc, now)) {
        hsPut(npc, "was_busy", 1);
        return;
    }
    if (hsGet(npc, "was_busy", "0") == "1") {
        hsPut(npc, "was_busy", 0);
        hsPut(npc, "free_at", now + (phase >= 3 ? WALK_GAP3 : WALK_GAP));
        gAiBack(npc);
    }
    var t = npc.getAttackTarget();
    if (t == null || !t.isAlive() || !t.typeOf(1)) {
        t = nearest(npc, ps, 99);
        if (t == null) return;
        // Цель пропала, пока ИИ был выключен приёмом: без неё он уходит к месту старта (Return to start).
        npc.setAttackTarget(t);
    }
    tryCast(npc, ps, t, phase, now);
    if (!gBusy(npc, now)) stuckCheck(npc, t, now);
}

function timer(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    var now = hsNow(npc);
    var id = e.id;
    if (id == T_HOLD) gHold(npc);
    else if (id == T_COMBO) comboStep(npc);
    else if (id == T_SPIN_END) spinEnd(npc);
    else if (id == T_SPIN_GUARD) spinGuard(npc);
    else if (id == T_UNSTICK) unstickStep(npc, now);
    else if (id == T_QUAKE) quakeRelease(npc);
    else if (id == T_QUAKE_STEP) quakeStep(npc);
    else if (id == T_DASH_END) dashEnd(npc);
    else if (id == T_QUICK) aroundHit(npc);
    else if (id == T_LEAP) leapLand(npc, now);
    else if (id == T_RINGS) ringsStep(npc, now);
    else if (id == T_VOID) voidStep(npc, now);
    else if (id == T_STORM) stormStep(npc, now);
    else if (id == T_BOLTS) boltsHit(npc);
    else if (id == T_RECOVER) gRelease(npc);
    else if (id == T_SWEEP) startAround(npc, SWEEP_R, SWEEP_PCT);
    else if (id == T_STOMP) startAround(npc, STOMP_R, STOMP_PCT);
    else if (id == T_FORCE_DASH) {
        var victim = byId(npc, hsGet(npc, "force_dash", ""));
        if (victim == null || !startDash(npc, victim, DASH_CHARGE_FOLLOW)) gRelease(npc);
    }
}

function meleeAttack(e) {
    e.setCanceled(true);
}

function damaged(e) {
    var npc = e.npc, now = hsNow(npc);
    var open = now < hsNum(npc, "dizzy_until") || now < hsNum(npc, "unarmed_until");
    hsCap(e, open ? OPEN_MUL : 1);
}

function died(e) {
    var npc = e.npc;
    AbilityAPI.cancel(npc);
    for (var i = 0; i < ALL_TIMERS.length; i++) npc.getTimers().stop(ALL_TIMERS[i]);
    hsZoneClear(npc);
    gRingsClear(npc);
    hsSetGate(npc, false);
    npc.say("§7Камень трескается. Устье колодца открыто.");
}
