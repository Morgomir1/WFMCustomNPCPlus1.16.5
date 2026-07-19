// =====================================================
// Скавен инженер — залп из ратлинг-гана (Java AbilityAPI).
// Механика как у wfm RatlingGunEntity: ~3 сек стрельбы, пули SkavenBullet.
// Режимы CNPC OnAttack: стрельба → «Ничего», после залпа → «Отступать» 8 сек → снова стрельба.
// =====================================================

var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");

// CNPC OnAttack: 0=Мстить, 1=Паника, 2=Отступать, 3=Ничего
var RETALIATE_REVENGE = 0;
var RETALIATE_RETREAT = 2;
var RETALIATE_NONE = 3;

var TIMER_ID = 782;
var RETREAT_TICKS = 160; // 8 секунд отступления после залпа

var ABILITY_ID = "ratling_gun_volley";
var GUN_ITEM = "wfm:skaven_ratling_gun";
var MAX_RANGE = 36.0;

// Настройки урона и разброса (передаются в Java-абилку)
// По WFM-щиту (ShieldBlockData) урон = damage пули → 0.5 HP щита за попадание
var DAMAGE = 0.5;
var ACCURACY = 6;           // чем больше — тем шире разброс (inaccuracy = accuracy * 0.15)
var BULLET_VELOCITY = 6.0;
var ACTIVE_TICKS = 60;        // длительность залпа (3 сек)
var FIRST_SHOT_TICK = 3;
var SHOT_INTERVAL = 5;      // выстрел каждые 0.25 сек

var RETREAT_SPEED = 6;

var MODE_KEY = "sk_eng_mode";           // "shoot" | "retreat" | "normal"
var WAS_SHOOTING_KEY = "sk_eng_was_shoot";
var RETREAT_END_KEY = "sk_eng_retreat_end";
var BASE_SPEED_KEY = "sk_eng_base_speed";

function init(event) {
    var npc = event.npc;
    var data = npc.getStoreddata();
    storeBaseSpeed(data, npc.getAi());
    applyNormalMode(npc, data);
    startTimer(npc);
}

function tick(event) {
    var npc = event.npc;
    if (!npc.isAlive()) return;

    var data = npc.getStoreddata();
    var ai = npc.getAi();
    var now = npc.getWorld().getTotalTime();
    storeBaseSpeed(data, ai);
    updateShootingState(npc, data, ai, now);
}

function timer(event) {
    if (Number(event.id) != TIMER_ID) return;

    var npc = event.npc;
    if (!npc.isAlive()) {
        AbilityAPI.cancel(npc);
        return;
    }

    var data = npc.getStoreddata();
    var ai = npc.getAi();
    var now = npc.getWorld().getTotalTime();
    storeBaseSpeed(data, ai);

    // Важно: timer каждый тик — здесь же ловим конец залпа, не ждём tick (раз в 10 тиков).
    updateShootingState(npc, data, ai, now);

    if (AbilityAPI.isBusy(npc)) return;
    if (isRetreating(data, now)) return;

    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) return;
    if (flatDistance(npc, target) > MAX_RANGE) return;
    if (typeof npc.canSeeEntity == "function" && !npc.canSeeEntity(target)) return;

    applyShootingMode(npc, data, npc.getAi());

    AbilityAPI.start(npc, ABILITY_ID, target, AbilityAPI.params(
        "projectileItem", GUN_ITEM,
        "damage", DAMAGE,
        "accuracy", ACCURACY,
        "bulletVelocity", BULLET_VELOCITY,
        "maxRange", MAX_RANGE,
        "activeTicks", ACTIVE_TICKS,
        "firstShotTick", FIRST_SHOT_TICK,
        "shotInterval", SHOT_INTERVAL
    ));

    data.put(WAS_SHOOTING_KEY, "1");
}

function targetLost(event) {
    var npc = event.npc;
    var data = npc.getStoreddata();
    var wasShooting = isShooting(npc) || String(data.get(WAS_SHOOTING_KEY)) == "1";
    AbilityAPI.cancel(npc);
    if (wasShooting) {
        data.put(WAS_SHOOTING_KEY, "0");
        beginRetreat(npc, data, npc.getAi(), npc.getWorld().getTotalTime());
    }
}

function died(event) {
    AbilityAPI.cancel(event.npc);
}

function isShooting(npc) {
    if (!AbilityAPI.isBusy(npc)) return false;
    return String(AbilityAPI.getActiveId(npc)) == ABILITY_ID;
}

function updateShootingState(npc, data, ai, now) {
    if (isShooting(npc)) {
        applyShootingMode(npc, data, ai);
        data.put(WAS_SHOOTING_KEY, "1");
        return;
    }

    if (String(data.get(WAS_SHOOTING_KEY)) == "1") {
        data.put(WAS_SHOOTING_KEY, "0");
        beginRetreat(npc, data, ai, now);
        return;
    }

    if (isRetreating(data, now)) {
        applyRetreatMode(npc, data, ai);
        return;
    }

    if (String(data.get(MODE_KEY)) == "retreat") {
        applyNormalMode(npc, data);
    }
}

function isRetreating(data, now) {
    if (String(data.get(MODE_KEY)) != "retreat") return false;
    return now < getInt(data, RETREAT_END_KEY);
}

function beginRetreat(npc, data, ai, now) {
    applyRetreatMode(npc, data, ai);
    data.put(RETREAT_END_KEY, String(now + RETREAT_TICKS));
}

function storeBaseSpeed(data, ai) {
    if (!data.has(BASE_SPEED_KEY)) {
        try {
            data.put(BASE_SPEED_KEY, String(ai.getWalkingSpeed()));
        } catch (e) {}
    }
}

function getBaseSpeed(data, ai) {
    if (data.has(BASE_SPEED_KEY)) {
        return getInt(data, BASE_SPEED_KEY);
    }
    try {
        return ai.getWalkingSpeed();
    } catch (e) {
        return 5;
    }
}

function applyShootingMode(npc, data, ai) {
    data.put(MODE_KEY, "shoot");
    try {
        ai.setRetaliateType(RETALIATE_NONE);
        ai.setWalkingSpeed(getBaseSpeed(data, ai));
    } catch (e) {}
}

function applyRetreatMode(npc, data, ai) {
    data.put(MODE_KEY, "retreat");
    try {
        ai.setRetaliateType(RETALIATE_RETREAT);
        ai.setWalkingSpeed(RETREAT_SPEED);
    } catch (e) {}
}

function applyNormalMode(npc, data) {
    data.put(MODE_KEY, "normal");
    data.put(RETREAT_END_KEY, "0");
    try {
        var ai = npc.getAi();
        ai.setRetaliateType(RETALIATE_REVENGE);
        ai.setWalkingSpeed(getBaseSpeed(data, ai));
    } catch (e) {}
}

function startTimer(npc) {
    var timers = npc.getTimers();
    if (timers == null) return;
    if (typeof timers.forceStart == "function") {
        timers.forceStart(TIMER_ID, 1, true);
    } else {
        timers.start(TIMER_ID, 1, true);
    }
}

function flatDistance(a, b) {
    var dx = a.getX() - b.getX();
    var dz = a.getZ() - b.getZ();
    return Math.sqrt(dx * dx + dz * dz);
}

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}
