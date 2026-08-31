/**
 * Констант Драхенфельс — соло-энкаунтер.
 * Механика в Java. Здесь — клоны и ВСЕ числовые константы.
 *
 * Меняй цифры ниже → Encounter.configure пишет их в storeddata (df_c_*).
 * Длительности эффектов в ТИКАХ (20 тиков = 1с), amp Poison IV = 3.
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");
var Encounter = Java.type("noppes.npcs.abilities.DrachenfelsEncounterHelper");

var TIMER_ID = 881;

// =========================
// КЛОНЫ (имена в CNPC Clone tab)
// =========================
var CLONE_TAB = 1;
var CLONE_MONK = "Монах Дракенфельса";
var CLONE_CULTIST = "Культист Дракенфельса";
var CLONE_GUARD = "Страж Дракенфельса";
var CLONE_PHANTOM = "Фантом Дракенфельса";
var CLONE_FALSE = "Иллюзия Дракенфельса";
var CLONE_VESSEL = "Сосуд Дракенфельса";
var CLONE_SHARD = "Осколок Дракенфельса";

// =========================
// АРЕНА / ФАЗЫ / ГЛОБАЛЬНОЕ
// =========================
var ARENA_RADIUS = 12.0;
// Encounter cast/aggro gate (AbilityAPI). Independent of arena size; also uses max(Stats aggro).
var ENGAGE_RADIUS = 60.0;
var PHASE2_RATIO = 0.66;
var PHASE3_RATIO = 0.33;
var INVULN_TICKS = 60;
var KITE_DISTANCE = 6.0;
var PHASE1_SPEED = 0.15;
var TELEGRAPH_COLOR = 0xC0FF3030 | 0; // signed int (Nashorn: bare 0xC0…… becomes Double → white)
var SEAL_ZONE_COLOR = 0xC0143C14 | 0; // тёмно-зелёные лужи Чёрной Печати
var SEAL_FIRST_DELAY = 40;
var GAZE_FIRST_DELAY = 80;
var COURT_FIRST_DELAY = 80;

// =========================
// ФАЗА 1 — Чёрная Печать
// =========================
var SEAL_CD = 200;
var SEAL_CHARGE_TICKS = 30;
var SEAL_ACTIVE_TICKS = 1;
var SEAL_DAMAGE = 12.0;
var SEAL_RADIUS = 3.0;
// Lifetime so puddles from 3 seals overlap: ~2*(CD + charge + active).
var SEAL_ZONE_TICKS = 780;
var SEAL_ZONE_DAMAGE = 6.0;
var SEAL_ZONE_INTERVAL = 10;
var SEAL_POISON_DURATION = 100; // 5s
var SEAL_POISON_AMP = 1; // Poison II
var SEAL_MIN_BOSS_DIST = 2.0;
var SEAL_MIN_CIRCLE_DIST = 4.0;

// =========================
// ФАЗА 1 — Взгляд Под Маской
// =========================
var GAZE_CD = 100;
// Must be < kiteDistance so Gaze fires on the kite ring instead of idling 8–10s.
var GAZE_RANGE = 5.0;
var GAZE_FAR_TICKS = 8;
var GAZE_CHARGE_TICKS = 20;
var GAZE_ACTIVE_TICKS = 15;
var GAZE_DISTANCE = 16.0;
var GAZE_WIDTH = 1.5;
var GAZE_DAMAGE = 18.0;

// =========================
// ФАЗА 1 — Отторжение (repulse)
// =========================
var REPULSE_CD = 200;
var REPULSE_FIRST_DELAY = 60;
var REPULSE_CHARGE_TICKS = 30;
var REPULSE_ACTIVE_TICKS = 1;
var REPULSE_RADIUS = 5.0;
var REPULSE_KNOCKBACK = 1.85;
var REPULSE_KNOCKBACK_Y = 0.42;
var REPULSE_TRIGGER = 4.0;

// =========================
// ФАЗА 1 — Колокол Мёртвых
// =========================
var BELL_RATIOS = "0.88,0.76";
var BELL_CD = 400;
var ABSORB_RATIO = 0.133;

// =========================
// ФАЗА 1 — Поклон Свиты
// =========================
var COURT_CD = 320;
var CULTIST_INTERVAL = 50;
var GUARD_INTERVAL = 100;
var GUARD_DASH_TICKS = 8;
var GUARD_DASH_RANGE = 10.0;
var GUARD_DASH_STANDOFF = 2.0;

// =========================
// ФАЗА 2 — цикл / Яд / Места / Бал / Ложный Хозяин
// =========================
var CYCLE_LENGTH = 360;
var CYCLE_FEAST_AT = 120;
var CYCLE_LEPER_AT = 220;

var IMPERIAL_CHARGE_TICKS = 24;
var IMPERIAL_ACTIVE_TICKS = 120; // 2x slower expand (was 60)
var IMPERIAL_ARENA_RADIUS = 30.0;
var IMPERIAL_THICKNESS = 1.333; // 1.5x narrower (was 2.0)
var IMPERIAL_HIT_HEIGHT = 1.0; // jumpable: feet above this clear the ring
var IMPERIAL_DAMAGE = 8.0;
var IMPERIAL_POISON_DURATION = 80;
var IMPERIAL_POISON_AMP = 3;
var IMPERIAL_SLOW_DURATION = 40;

var FEAST_CHARGE_TICKS = 40;
var FEAST_ACTIVE_TICKS = 1;
var FEAST_SEAT_RADIUS = 1.5;
var FEAST_SEAT_RING = 9.5; // max scatter from boss (was fixed ring 6)
var FEAST_SEAT_MIN_BOSS_DIST = 5.5;
var FEAST_ARENA_RADIUS = 30.0;
var FEAST_DAMAGE = 14.0;
var FEAST_POISON_DURATION = 60;
var FEAST_POISON_AMP = 0;
var FEAST_COLOR = 0xC0FFFFFF | 0; // только safe seats (белый); арена — TELEGRAPH_COLOR

var LEPER_CHARGE_TICKS = 24;
var LEPER_ACTIVE_TICKS = 60;
var LEPER_DAMAGE = 10.0;
var LEPER_HP = 1.0; // phantoms are hazard carriers; HP for rare player hits
var LEPER_START_RADIUS = 1.5; // spawn near boss
var LEPER_SPAWN_RADIUS = 24.0; // fly outward to this radius
var LEPER_DURATION = 70; // flight ticks per spirit
var LEPER_HIT_RADIUS = 2.0; // red hazard under spirit
var LEPER_SLOW_DURATION = 30;
var LEPER_SLOW_AMP = 1;
var LEPER_VOLLEYS = 3;
var LEPER_VOLLEY_INTERVAL = 18;
var LEPER_WIGGLE_AMP = 1.4;
var LEPER_WIGGLE_FREQ = 2.5;
var LEPER_HOVER = 1.0; // locked flight height above spawn floor

var FALSE_RATIOS = "0.56,0.46,0.36";
var FALSE_MAX = 3;
var FALSE_SHIFT = 30;
var FALSE_CHARGE_TICKS = 20;
var FALSE_ACTIVE_TICKS = 30;
var FALSE_COPY_DIST_MIN = 4.0;
var FALSE_COPY_DIST_MAX = 10.0;
var FALSE_COPY_ANGLE_JITTER = 45.0;
var FALSE_HEAL_PER_COPY = 1.0;
var FALSE_TELEPORT_RING = 5.0;
var FALSE_TELEGRAPH_RADIUS = 1.2;
var FALSE_RUN_STEP = 0.28;
var FALSE_PANIC_SPEED = 6;
var FALSE_PUDDLE_RADIUS = 1.6;
var FALSE_PUDDLE_TICKS = 100;
var FALSE_PUDDLE_DAMAGE = 4.0;
var FALSE_PUDDLE_DAMAGE_INTERVAL = 10;
var FALSE_PUDDLE_INTERVAL = 12;

// =========================
// HP всех аддов (клон-спавн)
// =========================
var MONK_HP = 40.0;
var CULTIST_HP = 30.0;
var GUARD_HP = 50.0;
var FALSE_CLONE_HP = 50.0;
var VESSEL_HP_FIRST = 45.0;
var VESSEL_HP_REPEAT = 30.0;
var SHARD_HP = 20.0;
// LEPER_HP выше в секции Leper Ball

// =========================
// ФАЗА 3 — сосуды / осколки / спеллы / Носитель
// =========================
var VESSEL_RING = 11.5;       // max dist from arena center
var VESSEL_RING_MIN = 10.5;   // min dist from arena center
var VESSEL_ANGLE_JITTER = 30.0; // ±degrees around even spacing
var SHARD_SPEED = 0.06;
var SHARD_HEAL_RATIO = 0.0267;
var SHARD_TOUCH_DIST = 2.75;
var SHARD_DELAY_TICKS = 5;

var STEP_CD = 120;
var STEP_CHARGE_TICKS = 16;
var STEP_ACTIVE_TICKS = 10;
var STEP_DAMAGE = 12.0;
var STEP_WIDTH = 1.25;
var STEP_LAND_RADIUS = 1.6;
var STEP_OVERSHOOT = 1.5;
var STEP_MIN_PLAYER_DIST = 5.0;

var WHISPER_CD = 180;
var WHISPER_CHARGE_TICKS = 24;
var WHISPER_ACTIVE_TICKS = 60;
var WHISPER_ARENA_RADIUS = 30.0;
var WHISPER_THICKNESS = 1.333;
var WHISPER_HIT_HEIGHT = 1.0;
var WHISPER_BLIND_DURATION = 40;
var WHISPER_WITHER_DURATION = 60;
var WHISPER_WITHER_AMP = 0;
var WHISPER_RING_COUNT = 3;
var WHISPER_RING_INTERVAL = 10;

var STEAL_CD = 160;
var STEAL_RANGE = 3.0;
var STEAL_DAMAGE = 6.0;
var STEAL_CHARGE_TICKS = 14;
var STEAL_TELEGRAPH_RADIUS = 1.5;
var STEAL_WEAK_DURATION = 40;
var STEAL_BLIND_DURATION = 40;

var CARRIER_TICKS = 240;
var CARRIER_SPEED = 0.2;
var CARRIER_ARC_DAMAGE = 15.0;
var CARRIER_ARC_DISTANCE = 4.5;
var CARRIER_ARC_NEAR_WIDTH = 1.35;
var CARRIER_ARC_HALF_ANGLE = 38.0;
var CARRIER_ARC_CAST_TICKS = 18;
var CARRIER_ARC_INTERVAL = 50;
var CARRIER_ARC_KNOCKBACK = 0.85;
var CARRIER_ARC_KNOCKBACK_Y = 0.18;

function init(event) {
    var npc = event.npc;
    Encounter.configureClones(
        npc,
        CLONE_TAB,
        CLONE_MONK,
        CLONE_CULTIST,
        CLONE_GUARD,
        CLONE_PHANTOM,
        CLONE_FALSE,
        CLONE_VESSEL,
        CLONE_SHARD
    );
    // Nashorn: varargs после npc ломаются — передаём Map через AbilityAPI.params
    Encounter.configure(npc, AbilityAPI.params(
        "arenaRadius", ARENA_RADIUS,
        "engageRadius", ENGAGE_RADIUS,
        "phase2Ratio", PHASE2_RATIO,
        "phase3Ratio", PHASE3_RATIO,
        "invulnTicks", INVULN_TICKS,
        "kiteDistance", KITE_DISTANCE,
        "phase1Speed", PHASE1_SPEED,
        "telegraphColor", TELEGRAPH_COLOR,
        "sealFirstDelay", SEAL_FIRST_DELAY,
        "gazeFirstDelay", GAZE_FIRST_DELAY,
        "courtFirstDelay", COURT_FIRST_DELAY,

        "sealCd", SEAL_CD,
        "sealChargeTicks", SEAL_CHARGE_TICKS,
        "sealActiveTicks", SEAL_ACTIVE_TICKS,
        "sealDamage", SEAL_DAMAGE,
        "sealRadius", SEAL_RADIUS,
        "sealZoneTicks", SEAL_ZONE_TICKS,
        "sealZoneDamage", SEAL_ZONE_DAMAGE,
        "sealZoneInterval", SEAL_ZONE_INTERVAL,
        "sealZoneColor", SEAL_ZONE_COLOR,
        "sealPoisonDuration", SEAL_POISON_DURATION,
        "sealPoisonAmp", SEAL_POISON_AMP,
        "sealMinBossDist", SEAL_MIN_BOSS_DIST,
        "sealMinCircleDist", SEAL_MIN_CIRCLE_DIST,

        "gazeCd", GAZE_CD,
        "gazeRange", GAZE_RANGE,
        "gazeFarTicks", GAZE_FAR_TICKS,
        "gazeChargeTicks", GAZE_CHARGE_TICKS,
        "gazeActiveTicks", GAZE_ACTIVE_TICKS,
        "gazeDistance", GAZE_DISTANCE,
        "gazeWidth", GAZE_WIDTH,
        "gazeDamage", GAZE_DAMAGE,

        "repulseCd", REPULSE_CD,
        "repulseFirstDelay", REPULSE_FIRST_DELAY,
        "repulseChargeTicks", REPULSE_CHARGE_TICKS,
        "repulseActiveTicks", REPULSE_ACTIVE_TICKS,
        "repulseRadius", REPULSE_RADIUS,
        "repulseKnockback", REPULSE_KNOCKBACK,
        "repulseKnockbackY", REPULSE_KNOCKBACK_Y,
        "repulseTrigger", REPULSE_TRIGGER,

        "bellRatios", BELL_RATIOS,
        "bellCd", BELL_CD,
        "absorbRatio", ABSORB_RATIO,

        "courtCd", COURT_CD,
        "cultistInterval", CULTIST_INTERVAL,
        "guardInterval", GUARD_INTERVAL,
        "guardDashTicks", GUARD_DASH_TICKS,
        "guardDashRange", GUARD_DASH_RANGE,
        "guardDashStandoff", GUARD_DASH_STANDOFF,

        // HP всех аддов
        "monkHp", MONK_HP,
        "cultistHp", CULTIST_HP,
        "guardHp", GUARD_HP,
        "leperHp", LEPER_HP,
        "falseCloneHp", FALSE_CLONE_HP,
        "vesselHpFirst", VESSEL_HP_FIRST,
        "vesselHpRepeat", VESSEL_HP_REPEAT,
        "shardHp", SHARD_HP,

        "cycleLength", CYCLE_LENGTH,
        "cycleFeastAt", CYCLE_FEAST_AT,
        "cycleLeperAt", CYCLE_LEPER_AT,

        "imperialChargeTicks", IMPERIAL_CHARGE_TICKS,
        "imperialActiveTicks", IMPERIAL_ACTIVE_TICKS,
        "imperialArenaRadius", IMPERIAL_ARENA_RADIUS,
        "imperialThickness", IMPERIAL_THICKNESS,
        "imperialHitHeight", IMPERIAL_HIT_HEIGHT,
        "imperialDamage", IMPERIAL_DAMAGE,
        "imperialPoisonDuration", IMPERIAL_POISON_DURATION,
        "imperialPoisonAmp", IMPERIAL_POISON_AMP,
        "imperialSlowDuration", IMPERIAL_SLOW_DURATION,

        "feastChargeTicks", FEAST_CHARGE_TICKS,
        "feastActiveTicks", FEAST_ACTIVE_TICKS,
        "feastSeatRadius", FEAST_SEAT_RADIUS,
        "feastSeatRing", FEAST_SEAT_RING,
        "feastSeatMinBossDist", FEAST_SEAT_MIN_BOSS_DIST,
        "feastArenaRadius", FEAST_ARENA_RADIUS,
        "feastDamage", FEAST_DAMAGE,
        "feastPoisonDuration", FEAST_POISON_DURATION,
        "feastPoisonAmp", FEAST_POISON_AMP,
        "feastColor", FEAST_COLOR,

        "leperChargeTicks", LEPER_CHARGE_TICKS,
        "leperActiveTicks", LEPER_ACTIVE_TICKS,
        "leperDamage", LEPER_DAMAGE,
        "leperStartRadius", LEPER_START_RADIUS,
        "leperSpawnRadius", LEPER_SPAWN_RADIUS,
        "leperDuration", LEPER_DURATION,
        "leperHitRadius", LEPER_HIT_RADIUS,
        "leperSlowDuration", LEPER_SLOW_DURATION,
        "leperSlowAmp", LEPER_SLOW_AMP,
        "leperVolleys", LEPER_VOLLEYS,
        "leperVolleyInterval", LEPER_VOLLEY_INTERVAL,
        "leperWiggleAmp", LEPER_WIGGLE_AMP,
        "leperWiggleFreq", LEPER_WIGGLE_FREQ,
        "leperHover", LEPER_HOVER,

        "falseRatios", FALSE_RATIOS,
        "falseMax", FALSE_MAX,
        "falseShift", FALSE_SHIFT,
        "falseChargeTicks", FALSE_CHARGE_TICKS,
        "falseActiveTicks", FALSE_ACTIVE_TICKS,
        "falseCopyDistMin", FALSE_COPY_DIST_MIN,
        "falseCopyDistMax", FALSE_COPY_DIST_MAX,
        "falseCopyAngleJitter", FALSE_COPY_ANGLE_JITTER,
        "falseHealPerCopy", FALSE_HEAL_PER_COPY,
        "falseTeleportRing", FALSE_TELEPORT_RING,
        "falseTelegraphRadius", FALSE_TELEGRAPH_RADIUS,
        "falseRunStep", FALSE_RUN_STEP,
        "falsePanicSpeed", FALSE_PANIC_SPEED,
        "falsePuddleRadius", FALSE_PUDDLE_RADIUS,
        "falsePuddleTicks", FALSE_PUDDLE_TICKS,
        "falsePuddleDamage", FALSE_PUDDLE_DAMAGE,
        "falsePuddleDamageInterval", FALSE_PUDDLE_DAMAGE_INTERVAL,
        "falsePuddleInterval", FALSE_PUDDLE_INTERVAL,

        "vesselRing", VESSEL_RING,
        "vesselRingMin", VESSEL_RING_MIN,
        "vesselAngleJitter", VESSEL_ANGLE_JITTER,
        "shardSpeed", SHARD_SPEED,
        "shardHealRatio", SHARD_HEAL_RATIO,
        "shardTouchDist", SHARD_TOUCH_DIST,
        "shardDelayTicks", SHARD_DELAY_TICKS,

        "stepCd", STEP_CD,
        "stepChargeTicks", STEP_CHARGE_TICKS,
        "stepActiveTicks", STEP_ACTIVE_TICKS,
        "stepDamage", STEP_DAMAGE,
        "stepWidth", STEP_WIDTH,
        "stepLandRadius", STEP_LAND_RADIUS,
        "stepOvershoot", STEP_OVERSHOOT,
        "stepMinPlayerDist", STEP_MIN_PLAYER_DIST,

        "whisperCd", WHISPER_CD,
        "whisperChargeTicks", WHISPER_CHARGE_TICKS,
        "whisperActiveTicks", WHISPER_ACTIVE_TICKS,
        "whisperArenaRadius", WHISPER_ARENA_RADIUS,
        "whisperThickness", WHISPER_THICKNESS,
        "whisperHitHeight", WHISPER_HIT_HEIGHT,
        "whisperBlindDuration", WHISPER_BLIND_DURATION,
        "whisperWitherDuration", WHISPER_WITHER_DURATION,
        "whisperWitherAmp", WHISPER_WITHER_AMP,
        "whisperRingCount", WHISPER_RING_COUNT,
        "whisperRingInterval", WHISPER_RING_INTERVAL,

        "stealCd", STEAL_CD,
        "stealRange", STEAL_RANGE,
        "stealDamage", STEAL_DAMAGE,
        "stealChargeTicks", STEAL_CHARGE_TICKS,
        "stealTelegraphRadius", STEAL_TELEGRAPH_RADIUS,
        "stealWeakDuration", STEAL_WEAK_DURATION,
        "stealBlindDuration", STEAL_BLIND_DURATION,

        "carrierTicks", CARRIER_TICKS,
        "carrierSpeed", CARRIER_SPEED,
        "carrierArcDamage", CARRIER_ARC_DAMAGE,
        "carrierArcDistance", CARRIER_ARC_DISTANCE,
        "carrierArcNearWidth", CARRIER_ARC_NEAR_WIDTH,
        "carrierArcHalfAngle", CARRIER_ARC_HALF_ANGLE,
        "carrierArcCastTicks", CARRIER_ARC_CAST_TICKS,
        "carrierArcInterval", CARRIER_ARC_INTERVAL,
        "carrierArcKnockback", CARRIER_ARC_KNOCKBACK,
        "carrierArcKnockbackY", CARRIER_ARC_KNOCKBACK_Y
    ));
    Encounter.init(npc);
    startTimers(npc);
}

function timer(event) {
    if (event.id != TIMER_ID) return;
    var npc = event.npc;
    if (!npc.isAlive()) {
        AbilityAPI.cancel(npc);
        Encounter.cleanup(npc);
        return;
    }
    Encounter.tick(npc);
}

function died(event) {
    AbilityAPI.cancel(event.npc);
    Encounter.cleanup(event.npc);
}

function targetLost(event) {
}

function startTimers(npc) {
    try {
        npc.getTimers().forceStart(TIMER_ID, 1, true);
    } catch (e) {
        try {
            npc.getTimers().start(TIMER_ID, 1, true);
        } catch (e2) {}
    }
}
