// Толчок игрока в направлении взгляда NPC (без КД).
// Настройка дальности = PUSH_SPEED (чем больше, тем дальше оттолкнёт).
//
// Установка:
// - CustomNPC+ → NPC → Advanced → Scripts → Enabled
// - Вставьте этот файл в скрипт NPC
//
// Примечание: скрипт выполняется только на сервере (Nashorn ES5.1).
var PUSH_SPEED = 15.5;        // сила/дальность толчка (аналогично setMotion из других скриптов)
var USE_PITCH = true;        // true = учитывать вертикальный угол (взгляд вверх/вниз), false = только по горизонту
var EXTRA_Y = 0.0;           // доп. вертикальный импульс (например 0.1, если нужно чуть приподнимать)
var CANCEL_INTERACT = true;  // отменять взаимодействие (чтобы не открывался диалог/GUI)

// Важно: одиночный setMotion у игрока упирается в лимит синхронизации velocity-пакета (~4.1 на компоненту).
// Поэтому "дальность" увеличиваем не раздувая скорость до 30, а раскладывая толчок на несколько тиков.
// PUSH_SPEED трактуется как "сила" — при значениях > SPEED_CAP толчок станет многотиковым.
var TIMER_ID = 901;
var TIMER_TICK = 1;          // раз в тик
var SPEED_CAP = 3.9;         // безопасный предел на компоненту (чуть ниже теоретического ~4.095)

var ACTIVE_PUSH = {}; // uuid -> { ticksLeft: n, x:..., y:..., z:... }

function init(event) {
    startPushTimer(event.npc);
}

function timer(event) {
    if (Number(event.id) != TIMER_ID) return;
    if (isEmptyMap(ACTIVE_PUSH)) return;

    var npc = event.npc;
    var world = npc.getWorld();
    var players = null;
    try { players = world.getAllPlayers(); } catch (e0) { return; }
    if (players == null) return;

    // Быстрый индекс игроков по UUID
    var byUuid = {};
    for (var i = 0; i < players.length; i++) {
        var p = players[i];
        if (p != null) byUuid[String(p.getUUID())] = p;
    }

    for (var uuid in ACTIVE_PUSH) {
        var st = ACTIVE_PUSH[uuid];
        var pl = byUuid[uuid];
        if (st == null || pl == null || !pl.isAlive()) {
            delete ACTIVE_PUSH[uuid];
            continue;
        }
        if (Number(st.ticksLeft) <= 0) {
            delete ACTIVE_PUSH[uuid];
            continue;
        }

        // На каждый тик даём импульс ограниченной скоростью -> суммарная дистанция растёт с ticksLeft.
        pl.setMotionX(Number(st.x) * Number(st.perTick));
        pl.setMotionY(Number(st.y) * Number(st.perTick) + EXTRA_Y);
        pl.setMotionZ(Number(st.z) * Number(st.perTick));

        st.ticksLeft = Number(st.ticksLeft) - 1;
    }
}

function interact(event) {
    try {
        var npc = event.npc;
        var player = event.player;
        if (npc == null || player == null) return;
        if (!player.isAlive()) return;

        // init может не вызваться (NPC уже в мире без сохранения жезлом) — страхуемся здесь.
        startPushTimer(npc);

        var dir = getNpcLookDir(npc);
        if (dir == null) return;

        // FX на старте толчка (один раз).
        spawnPushFx(npc.getWorld(), player);
        try {
            // "Защита от падения" на 2 секунды: замедленное падение (секунды, не тики).
            player.addPotionEffect(PotionEffectType_SLOW_FALLING, 2, 0, false);
        } catch (eEff) {}

        // Делим на тики, чтобы PUSH_SPEED > SPEED_CAP реально давал большую дальность.
        var strength = Number(PUSH_SPEED);
        if (isNaN(strength) || strength <= 0) strength = 0.1;

        var perTick = strength;
        if (perTick > SPEED_CAP) perTick = SPEED_CAP;

        var ticks = Math.ceil(strength / SPEED_CAP);
        if (ticks < 1) ticks = 1;
        if (ticks > 60) ticks = 60; // защита от экстремальных значений

        var uuid = String(player.getUUID());
        ACTIVE_PUSH[uuid] = {
            ticksLeft: ticks,
            perTick: perTick,
            x: dir.x,
            y: dir.y,
            z: dir.z
        };

        if (CANCEL_INTERACT && event != null) event.setCanceled(true);
    } catch (e) {
        log("[push_look_dir] error: " + e);
        if (e && e.stack) log(e.stack);
    }
}

// На некоторых NPC вместо interact срабатывает dialog — дублируем поведение.
function dialog(event) {
    interact(event);
}

function getNpcLookDir(npc) {
    try {
        // Надёжный способ для CustomNPC+ в 1.16.5: yaw берём из npc.getRotation()
        // (см. референс scripts/rat_ogre/rat_ogre.js → getLookDirection).
        var yawRad = Number(npc.getRotation()) * Math.PI / 180.0;

        var x = -Math.sin(yawRad);
        var z = Math.cos(yawRad);
        var y = 0.0;

        if (USE_PITCH) {
            // Pitch API-обёртка обычно не даёт, поэтому берём из сырого Entity (если поле есть).
            var mc = npc.getMCEntity();
            if (mc != null) {
                var pitchDeg = null;
                try { pitchDeg = Number(mc.xRot); } catch (e0) {}
                if (pitchDeg != null && !isNaN(pitchDeg)) {
                    var pitchRad = pitchDeg * Math.PI / 180.0;
                    // Переопределяем на полноценный 3D-вектор (как у vanilla look vec).
                    var cosP = Math.cos(pitchRad);
                    x = x * cosP;
                    z = z * cosP;
                    y = -Math.sin(pitchRad);
                }
            }
        }

        var len = Math.sqrt(x * x + y * y + z * z);
        if (len < 0.0001) return null;
        return { x: x / len, y: y / len, z: z / len };
    } catch (e) {
        log("[push_look_dir] getLookDir failed: " + e);
        return null;
    }
}

function startPushTimer(npc) {
    if (npc == null) return;
    try {
        var timers = npc.getTimers();
        if (timers == null) return;
        if (typeof timers.forceStart == "function") {
            timers.forceStart(TIMER_ID, TIMER_TICK, true);
        } else {
            timers.start(TIMER_ID, TIMER_TICK, true);
        }
    } catch (e) {
        log("[push_look_dir] startPushTimer failed: " + e);
    }
}

function isEmptyMap(map) {
    for (var k in map) return false;
    return true;
}

function spawnPushFx(world, player) {
    if (world == null || player == null) return;
    try {
        world.playSoundAt(player.getPos(), "minecraft:entity.generic.explode", 0.9, 1.0);
    } catch (eSnd) {}
    try {
        // Взрыв
        world.spawnParticle(
            "minecraft:explosion_emitter",
            player.getX(), player.getY() + 1.0, player.getZ(),
            0.0, 0.0, 0.0, 0.0, 1
        );
    } catch (eP1) {
        // fallback, если explosion_emitter недоступен в этой сборке
        try {
            world.spawnParticle(
                "minecraft:explosion",
                player.getX(), player.getY() + 1.0, player.getZ(),
                0.0, 0.0, 0.0, 0.0, 1
            );
        } catch (eP1b) {}
    }
    try {
        // Дым
        world.spawnParticle(
            "minecraft:large_smoke",
            player.getX(), player.getY() + 1.0, player.getZ(),
            0.25, 0.15, 0.25, 0.02, 12
        );
    } catch (eP2) {
        try {
            world.spawnParticle(
                "minecraft:smoke",
                player.getX(), player.getY() + 1.0, player.getZ(),
                0.25, 0.15, 0.25, 0.02, 12
            );
        } catch (eP2b) {}
    }
}
