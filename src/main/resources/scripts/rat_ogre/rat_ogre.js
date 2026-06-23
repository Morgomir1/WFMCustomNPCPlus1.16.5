// =====================================================
// Крысоогр — рывок и прыжковая атака
// CustomNPC+ JS-скрипт для WFM 1.16.5
//
// Способность «Разгон»:
//   2 сек подготовки (NPC стоит на месте, в чат — «разгоняется»),
//   затем рывок на 16 блоков по направлению взгляда (~0.35 сек).
//   Враги на пути получают 10 урона и отбрасываются.
//
// Способность «Прыжок»:
//   1.25 сек подготовки, затем полёт ~1 сек по синусоиде:
//   до 5 блоков в высоту, вперёд с боковой волной sin(2π·t).
//   В полёте — GeckoLib-клип attack из wfm:animations/rat_ogre.animation.json.
//   В точке приземления — урон и отбрасывание в радиусе 2.5 блока.
//
// На время кастов NPC не преследует цель.
//
// Таймеры:
//   1 — проверка условий каста (раз в секунду)
//   2 — шаги рывка (каждый тик, только во время dash)
//   3 — шаги прыжка (каждый тик, только во время jump)
//
// Вкладка 2 (rat_ogre_pursuit.js): преследование — отдельный ScriptEngine.
//
// Отладка:
//   /script trigger cast dash
//   /script trigger cast jump
// =====================================================

var DASH_DISTANCE = 16;          // блоков вперёд
var DASH_BLOCKS_PER_STEP = 2;    // блоков за один тик рывка
var DASH_DAMAGE = 10;
var DASH_WINDUP_TICKS = 40;      // 2 секунды
var DASH_COOLDOWN = 160;         // 8 секунд между рывками
var DASH_KNOCKBACK = 3;
var DASH_HIT_RADIUS = 1.8;
var DASH_MIN_RANGE = 3.0;        // не рывок вплотную
var DASH_MAX_RANGE = 14.0;       // не рывок издалека
var DASH_CAST_CHANCE = 0.55;     // шанс при готовности и подходящей дистанции

var JUMP_HEIGHT = 5.0;           // блоков над точкой старта
var JUMP_DISTANCE = 11.0;        // блоков вперёд по горизонтали
var JUMP_SWAY = 1.8;             // амплитуда боковой синусоиды
var JUMP_TICKS = 18;             // длительность полёта (~0.9 сек)
var JUMP_DAMAGE = 14;
var JUMP_WINDUP_TICKS = 25;      // 1.25 секунды
var JUMP_COOLDOWN = 200;         // 10 секунд
var JUMP_KNOCKBACK = 4.5;
var JUMP_LAND_RADIUS = 2.5;
var JUMP_MIN_RANGE = 4.0;
var JUMP_MAX_RANGE = 13.0;
var JUMP_CAST_CHANCE = 0.5;
var JUMP_ATTACK_ANIM = "attack"; // rat_ogre.animation.json

var STATE_IDLE = "idle";
var STATE_WINDUP = "windup";
var STATE_DASHING = "dashing";

var JUMP_IDLE = "idle";
var JUMP_WINDUP = "windup";
var JUMP_FLYING = "flying";

// =====================================================
// Утилиты
// =====================================================

function isRatOgre(npc) {
    return npc.getStoreddata().get("rat_ogre") == 1;
}

function isSameEntity(a, b) {
    if (a == null || b == null) return false;
    try {
        return String(a.getUUID()) == String(b.getUUID());
    } catch (e) {
        return a == b;
    }
}

function getCombatTarget(npc) {
    var target = null;
    try {
        target = npc.getAttackTarget();
    } catch (e) {}
    if (target == null) {
        try {
            target = npc.getAi().getTarget();
        } catch (e2) {}
    }
    if (target != null && target.isAlive()) {
        return target;
    }
    return null;
}

function distSq(ax, az, bx, bz) {
    var dx = ax - bx;
    var dz = az - bz;
    return dx * dx + dz * dz;
}

function getLookDirection(npc) {
    var rot = npc.getRotation() * Math.PI / 180.0;
    return {
        x: -Math.sin(rot),
        z: Math.cos(rot)
    };
}

function faceEntity(npc, target) {
    if (target == null) return;
    var dx = target.getX() - npc.getX();
    var dz = target.getZ() - npc.getZ();
    if (dx * dx + dz * dz < 0.0001) return;
    var yaw = Math.atan2(-dx, dz) * 180.0 / Math.PI;
    lockNpcFacing(npc, yaw);
}

function faceDirection(npc, dirX, dirZ) {
    if (dirX * dirX + dirZ * dirZ < 0.0001) return;
    var yaw = Math.atan2(-dirX, dirZ) * 180.0 / Math.PI;
    lockNpcFacing(npc, yaw);
}

function lockNpcFacing(npc, yaw) {
    npc.setRotation(yaw);
    try {
        var mc = npc.getMCEntity();
        mc.yRot = yaw;
        mc.yRotO = yaw;
        mc.yBodyRot = yaw;
        mc.yBodyRotO = yaw;
        mc.yHeadRot = yaw;
        mc.yHeadRotO = yaw;
    } catch (e) {}
}

function faceSavedTarget(npc, data, yawKey) {
    var target = getSavedDashTarget(npc);
    if (target != null && target.isAlive()) {
        faceEntity(npc, target);
        data.put(yawKey, npc.getRotation());
        return;
    }
    var yaw = data.get(yawKey);
    if (yaw != null) {
        lockNpcFacing(npc, Number(yaw));
    }
}

function faceDashTarget(npc, data) {
    faceSavedTarget(npc, data, "dash_yaw");
}

function faceJumpTarget(npc, data) {
    faceSavedTarget(npc, data, "jump_yaw");
}

function getSavedDashTarget(npc) {
    var uuid = npc.getTempdata().get("saved_target_uuid");
    if (uuid == null) return null;
    return findEntityByUuid(npc.getWorld(), uuid);
}

function findEntityByUuid(world, uuid) {
    try {
        var players = world.getAllPlayers();
        for (var i = 0; i < players.length; i++) {
            if (String(players[i].getUUID()) == String(uuid)) {
                return players[i];
            }
        }
    } catch (e) {}

    var types = [1, 3, 5];
    for (var t = 0; t < types.length; t++) {
        try {
            var all = world.getAllEntities(types[t]);
            for (var j = 0; j < all.length; j++) {
                if (String(all[j].getUUID()) == String(uuid)) {
                    return all[j];
                }
            }
        } catch (e2) {}
    }
    return null;
}

function stopNpcNavigation(npc) {
    try {
        var mc = npc.getMCEntity();
        mc.getNavigation().stop();
    } catch (e) {}
}

function haltNpcMovement(npc) {
    stopNpcNavigation(npc);
    try {
        npc.setMoveForward(0);
        npc.setMoveStrafing(0);
        npc.setMoveVertical(0);
    } catch (e) {}
    zeroMotion(npc);
}

function pauseCombatAI(npc, target) {
    var data = npc.getTempdata();
    var ai = npc.getAi();

    data.put("saved_target_uuid", target.getUUID());
    data.put("saved_walk_speed", ai.getWalkingSpeed());

    try {
        npc.setAttackTarget(null);
    } catch (e) {}
    try {
        ai.setWalkingSpeed(0);
    } catch (e2) {}

    haltNpcMovement(npc);
}

function resumeCombatAI(npc) {
    var data = npc.getTempdata();
    var ai = npc.getAi();
    var world = npc.getWorld();

    var speed = data.get("saved_walk_speed");
    if (speed != null) {
        try {
            ai.setWalkingSpeed(Number(speed));
        } catch (e) {}
        data.remove("saved_walk_speed");
    }

    var uuid = data.get("saved_target_uuid");
    data.remove("saved_target_uuid");
    if (uuid == null) return;

    var target = findEntityByUuid(world, uuid);
    if (target != null && target.isAlive()) {
        try {
            npc.setAttackTarget(target);
        } catch (e2) {}
    }
}
function zeroMotion(entity) {
    try {
        entity.setMotionX(0);
        entity.setMotionY(0);
        entity.setMotionZ(0);
    } catch (e) {}
}

function getGroundY(world, x, z) {
    try {
        var bx = Math.floor(x);
        var bz = Math.floor(z);
        for (var y = 255; y >= 0; y--) {
            var block = world.getBlock(bx, y, bz);
            if (block != null && !block.isAir()) {
                return y + 1;
            }
        }
    } catch (e) {}
    return 64;
}

function safeSetPosition(npc, x, y, z) {
    if (y < 1) {
        y = getGroundY(npc.getWorld(), x, z);
        if (y < 1) y = 64;
    }
    // принудительная загрузка чанка запросом блока
    try {
        npc.getWorld().getBlock(Math.floor(x), 64, Math.floor(z));
    } catch (e) {}
    npc.setPosition(x, y, z);
}

function getDashState(npc) {
    var state = npc.getTempdata().get("dash_state");
    return state == null ? STATE_IDLE : String(state);
}

function setDashState(npc, state) {
    npc.getTempdata().put("dash_state", state);
}

function isDashBusy(npc) {
    var state = getDashState(npc);
    return state == STATE_WINDUP || state == STATE_DASHING;
}

function getJumpState(npc) {
    var state = npc.getTempdata().get("jump_state");
    return state == null ? JUMP_IDLE : String(state);
}

function setJumpState(npc, state) {
    npc.getTempdata().put("jump_state", state);
}

function isJumpBusy(npc) {
    var state = getJumpState(npc);
    return state == JUMP_WINDUP || state == JUMP_FLYING;
}

function isAbilityBusy(npc) {
    return isDashBusy(npc) || isJumpBusy(npc);
}

function isDashReady(npc) {
    var readyAt = npc.getStoreddata().get("dash_cd_until");
    if (readyAt == null) readyAt = 0;
    return npc.getAge() >= readyAt;
}

function setDashCooldown(npc) {
    npc.getStoreddata().put("dash_cd_until", npc.getAge() + DASH_COOLDOWN);
}

function isJumpReady(npc) {
    var readyAt = npc.getStoreddata().get("jump_cd_until");
    if (readyAt == null) readyAt = 0;
    return npc.getAge() >= readyAt;
}

function setJumpCooldown(npc) {
    npc.getStoreddata().put("jump_cd_until", npc.getAge() + JUMP_COOLDOWN);
}

function clearDashHits(data) {
    data.remove("dash_hits");
}

function wasDashHit(data, entity) {
    try {
        var hits = data.get("dash_hits");
        if (hits == null) return false;
        return String(hits).indexOf("|" + entity.getUUID() + "|") >= 0;
    } catch (e) {
        return false;
    }
}

function markDashHit(data, entity) {
    try {
        var hits = data.get("dash_hits");
        if (hits == null) hits = "";
        data.put("dash_hits", String(hits) + "|" + entity.getUUID() + "|");
    } catch (e) {}
}

function isDashVictim(entity, npc) {
    if (entity == null || !entity.isAlive()) return false;
    if (isSameEntity(entity, npc)) return false;

    try {
        if (entity.typeOf(2)) return false; // другие CustomNPC
    } catch (e) {}

    try {
        if (entity.typeOf(1)) return true; // игрок
    } catch (e2) {}

    try {
        if (entity.typeOf(3)) return true; // монстр
    } catch (e3) {}

    try {
        if (entity.typeOf(5) && !entity.typeOf(4)) return true; // живое, не животное
    } catch (e4) {}

    return false;
}

function findDashVictims(world, npc) {
    var victims = [];
    var types = [1, 3, 5];
    var radius = DASH_HIT_RADIUS;
    var x = npc.getX();
    var z = npc.getZ();
    var pos = npc.getPos();

    for (var t = 0; t < types.length; t++) {
        var list = world.getNearbyEntities(pos, radius + 2.0, types[t]);
        for (var i = 0; i < list.length; i++) {
            var entity = list[i];
            if (!isDashVictim(entity, npc)) continue;
            if (distSq(x, z, entity.getX(), entity.getZ()) > radius * radius) continue;
            victims.push(entity);
        }
    }

    return victims;
}

function applyDashHit(npc, entity, data) {
    if (wasDashHit(data, entity)) return;

    markDashHit(data, entity);
    try {
        entity.damage(DASH_DAMAGE, npc);
    } catch (e) {
        try {
            entity.damage(DASH_DAMAGE);
        } catch (e2) {}
    }

    try {
        var yaw = data.get("dash_yaw");
        entity.knockback(DASH_KNOCKBACK, yaw != null ? Number(yaw) : npc.getRotation());
    } catch (e3) {}

    try {
        npc.getWorld().spawnParticle("minecraft:crit",
            entity.getX(), entity.getY() + 1.0, entity.getZ(),
            0, 0, 0, 0, 6);
    } catch (e4) {}
}

function spawnDashParticles(world, x, y, z) {
    try {
        world.spawnParticle("minecraft:cloud", x, y + 0.4, z, 0.1, 0.05, 0.1, 0.02, 4);
        world.spawnParticle("minecraft:sweep_attack", x, y + 0.8, z, 0, 0, 0, 0, 1);
    } catch (e) {}
}

function canStartDash(npc, target) {
    if (!isDashReady(npc)) return false;
    if (isAbilityBusy(npc)) return false;
    if (target == null || !target.isAlive()) return false;

    var dist = Math.sqrt(distSq(npc.getX(), npc.getZ(), target.getX(), target.getZ()));
    return dist >= DASH_MIN_RANGE && dist <= DASH_MAX_RANGE;
}

// =====================================================
// Фазы рывка
// =====================================================

function startDashWindup(npc, target) {
    var data = npc.getTempdata();

    pauseCombatAI(npc, target);
    faceEntity(npc, target);

    data.put("windup_end", npc.getAge() + DASH_WINDUP_TICKS);

    clearDashHits(data);
    setDashState(npc, STATE_WINDUP);

    npc.say("§cКрысоогр разгоняется!");
    log("rat_ogre: windup started");
}

function beginDash(npc) {
    var data = npc.getTempdata();
    var target = getSavedDashTarget(npc);

    if (target != null) {
        faceEntity(npc, target);
    }

    var dir = getLookDirection(npc);

    data.put("dash_dir_x", dir.x);
    data.put("dash_dir_z", dir.z);
    data.put("dash_yaw", npc.getRotation());
    data.put("dash_traveled", 0);
    data.put("dash_start_x", npc.getX());
    data.put("dash_start_z", npc.getZ());

    data.remove("windup_end");

    setDashState(npc, STATE_DASHING);
    npc.getTimers().start(2, 1, true);
    playDashAnimation(npc);

    npc.say("§4*рывок!*");
    log("rat_ogre: dash started, yaw=" + npc.getRotation());
}

function dashStep(npc) {
    var data = npc.getTempdata();
    var world = npc.getWorld();

    var traveled = Number(data.get("dash_traveled"));
    if (isNaN(traveled)) traveled = 0;
    traveled = traveled + DASH_BLOCKS_PER_STEP;

    var startX = Number(data.get("dash_start_x"));
    var startZ = Number(data.get("dash_start_z"));
    var dirX = Number(data.get("dash_dir_x"));
    var dirZ = Number(data.get("dash_dir_z"));

    var nx = startX + dirX * traveled;
    var nz = startZ + dirZ * traveled;
    var ny = getGroundY(world, nx, nz);

    haltNpcMovement(npc);
    safeSetPosition(npc, nx, ny, nz);
    faceDashTarget(npc, data);

    var victims = findDashVictims(world, npc);
    for (var i = 0; i < victims.length; i++) {
        applyDashHit(npc, victims[i], data);
    }

    spawnDashParticles(world, nx, ny, nz);
    data.put("dash_traveled", traveled);

    if (traveled >= DASH_DISTANCE) {
        finishDash(npc);
    }
}

function finishDash(npc) {
    var data = npc.getTempdata();

    try {
        npc.getTimers().stop(2);
    } catch (e) {}

    clearDashHits(data);
    data.remove("dash_dir_x");
    data.remove("dash_dir_z");
    data.remove("dash_yaw");
    data.remove("dash_traveled");
    data.remove("dash_start_x");
    data.remove("dash_start_z");

    setDashState(npc, STATE_IDLE);
    setDashCooldown(npc);
    resumeCombatAI(npc);
    stopAnimations(npc);
    log("rat_ogre: dash finished");
}

function tryCastDash(npc, force) {
    var target = getCombatTarget(npc);
    if (!canStartDash(npc, target)) return false;
    if (!force && Math.random() > DASH_CAST_CHANCE) return false;

    startDashWindup(npc, target);
    return true;
}

// =====================================================
// Фазы прыжковой атаки
// =====================================================

function findJumpVictims(world, npc, radius) {
    var victims = [];
    var types = [1, 3, 5];
    var x = npc.getX();
    var z = npc.getZ();
    var pos = npc.getPos();

    for (var t = 0; t < types.length; t++) {
        var list = world.getNearbyEntities(pos, radius + 1.0, types[t]);
        for (var i = 0; i < list.length; i++) {
            var entity = list[i];
            if (!isDashVictim(entity, npc)) continue;
            if (distSq(x, z, entity.getX(), entity.getZ()) > radius * radius) continue;
            victims.push(entity);
        }
    }

    return victims;
}

function applyJumpLandingHit(npc, entity, data, landX, landZ) {
    try {
        entity.damage(JUMP_DAMAGE, npc);
    } catch (e) {
        try {
            entity.damage(JUMP_DAMAGE);
        } catch (e2) {}
    }

    try {
        var dx = entity.getX() - landX;
        var dz = entity.getZ() - landZ;
        if (dx * dx + dz * dz < 0.0001) {
            var dir = getLookDirection(npc);
            dx = dir.x;
            dz = dir.z;
        }
        var yaw = Math.atan2(-dx, dz) * 180.0 / Math.PI;
        entity.knockback(JUMP_KNOCKBACK, yaw);
    } catch (e3) {}

    try {
        npc.getWorld().spawnParticle("minecraft:crit",
            entity.getX(), entity.getY() + 1.0, entity.getZ(),
            0, 0, 0, 0, 8);
    } catch (e4) {}
}

function spawnJumpTrailParticles(world, x, y, z) {
    try {
        world.spawnParticle("minecraft:cloud", x, y + 0.2, z, 0.05, 0.05, 0.05, 0.01, 2);
    } catch (e) {}
}

function playJumpAttackAnimation(npc) {
    try {
        var api = null;
        try {
            if (typeof API !== "undefined" && API != null && typeof API.createAnimBuilder === "function") {
                api = API;
            }
        } catch (e) {}
        if (api == null) {
            api = Java.type("noppes.npcs.api.NpcAPI").Instance();
        }

        var builder = api.createAnimBuilder();
        builder.playOnce(JUMP_ATTACK_ANIM);
        npc.syncAnimationsForAll(builder);
        log("rat_ogre: jump attack anim sent OK");
    } catch (e) {
        log("rat_ogre: jump attack anim ERROR: " + e);
    }
}

function playDashAnimation(npc) {
    try {
        var api = null;
        try {
            if (typeof API !== "undefined" && API != null && typeof API.createAnimBuilder === "function") {
                api = API;
            }
        } catch (e) {}
        if (api == null) {
            api = Java.type("noppes.npcs.api.NpcAPI").Instance();
        }

        var builder = api.createAnimBuilder();
        builder.loop("walk_chase");
        npc.syncAnimationsForAll(builder, 2.0);
        log("rat_ogre: dash anim walk_chase x2 sent OK");
    } catch (e) {
        log("rat_ogre: dash anim ERROR: " + e);
    }
}

function stopAnimations(npc) {
    try {
        npc.stopManualAnimation();
        log("rat_ogre: manual anim stopped");
    } catch (e) {
        log("rat_ogre: stopManualAnimation ERROR: " + e);
    }
}

function spawnJumpLandingParticles(world, x, y, z) {
    try {
        world.spawnParticle("minecraft:explosion", x, y + 0.3, z, 0, 0, 0, 0, 1);
        world.spawnParticle("minecraft:cloud", x, y + 0.2, z, 0.4, 0.2, 0.4, 0.05, 12);
        world.spawnParticle("minecraft:sweep_attack", x, y + 0.5, z, 0, 0, 0, 0, 2);
        world.spawnParticle("minecraft:crit", x, y + 0.8, z, 0.3, 0.2, 0.3, 0.1, 10);
    } catch (e) {}
}

function canStartJump(npc, target) {
    if (!isJumpReady(npc)) return false;
    if (isAbilityBusy(npc)) return false;
    if (target == null || !target.isAlive()) return false;

    var dist = Math.sqrt(distSq(npc.getX(), npc.getZ(), target.getX(), target.getZ()));
    return dist >= JUMP_MIN_RANGE && dist <= JUMP_MAX_RANGE;
}

function startJumpWindup(npc, target) {
    var data = npc.getTempdata();

    pauseCombatAI(npc, target);
    faceEntity(npc, target);

    data.put("jump_windup_end", npc.getAge() + JUMP_WINDUP_TICKS);
    setJumpState(npc, JUMP_WINDUP);

    npc.say("§cКрысоогр пригибается...");
    log("rat_ogre: jump windup started");
}

function beginJump(npc) {
    var data = npc.getTempdata();
    var target = getSavedDashTarget(npc);

    if (target != null) {
        faceEntity(npc, target);
    }

    var dir = getLookDirection(npc);
    var jumpDist = JUMP_DISTANCE;

    if (target != null) {
        var td = Math.sqrt(distSq(npc.getX(), npc.getZ(), target.getX(), target.getZ()));
        if (td > 1.0) {
            jumpDist = Math.max(JUMP_MIN_RANGE, Math.min(JUMP_DISTANCE, td - 1.0));
        }
    }

    data.put("jump_dir_x", dir.x);
    data.put("jump_dir_z", dir.z);
    data.put("jump_yaw", npc.getRotation());
    faceJumpTarget(npc, data);
    data.put("jump_distance", jumpDist);
    data.put("jump_step", 0);
    data.put("jump_start_x", npc.getX());
    data.put("jump_start_y", npc.getY());
    data.put("jump_start_z", npc.getZ());
    data.remove("jump_windup_end");

    setJumpState(npc, JUMP_FLYING);
    npc.getTimers().start(3, 1, true);
    data.remove("jump_anim_triggered");

    npc.say("§4*прыжок!*");
    log("rat_ogre: jump started, yaw=" + npc.getRotation());
}

function jumpStep(npc) {
    var data = npc.getTempdata();
    var world = npc.getWorld();

    var step = Number(data.get("jump_step"));
    if (isNaN(step)) step = 0;
    step = step + 1;

    var progress = step / JUMP_TICKS;
    if (progress > 1.0) progress = 1.0;

    var startX = Number(data.get("jump_start_x"));
    var startY = Number(data.get("jump_start_y"));
    var startZ = Number(data.get("jump_start_z"));
    var dirX = Number(data.get("jump_dir_x"));
    var dirZ = Number(data.get("jump_dir_z"));
    var jumpDist = Number(data.get("jump_distance"));
    if (isNaN(jumpDist) || jumpDist <= 0) jumpDist = JUMP_DISTANCE;
    var perpX = -dirZ;
    var perpZ = dirX;

    var forward = progress * jumpDist;
    var height = Math.sin(progress * Math.PI) * JUMP_HEIGHT;
    var sway = Math.sin(progress * Math.PI * 2.0) * JUMP_SWAY;

    var nx = startX + dirX * forward + perpX * sway;
    var ny = startY + height;
    var nz = startZ + dirZ * forward + perpZ * sway;

    haltNpcMovement(npc);
    npc.setPosition(nx, ny, nz);
    faceJumpTarget(npc, data);

    if (step == 1 && data.get("jump_anim_triggered") == null) {
        data.put("jump_anim_triggered", 1);
        playJumpAttackAnimation(npc);
    }

    spawnJumpTrailParticles(world, nx, ny, nz);

    data.put("jump_step", step);

    if (step >= JUMP_TICKS) {
        applyJumpLanding(npc);
    }
}

function applyJumpLanding(npc) {
    var data = npc.getTempdata();
    var world = npc.getWorld();
    var landX = npc.getX();
    var landZ = npc.getZ();
    var landY = getGroundY(world, landX, landZ);

    safeSetPosition(npc, landX, landY, landZ);
    faceJumpTarget(npc, data);
    spawnJumpLandingParticles(world, landX, landY, landZ);

    var victims = findJumpVictims(world, npc, JUMP_LAND_RADIUS);
    for (var i = 0; i < victims.length; i++) {
        applyJumpLandingHit(npc, victims[i], data, landX, landZ);
    }

    npc.say("§4*удар!*");
    finishJump(npc);
}

function finishJump(npc) {
    var data = npc.getTempdata();

    try {
        npc.getTimers().stop(3);
    } catch (e) {}

    data.remove("jump_dir_x");
    data.remove("jump_dir_z");
    data.remove("jump_yaw");
    data.remove("jump_distance");
    data.remove("jump_step");
    data.remove("jump_start_x");
    data.remove("jump_start_y");
    data.remove("jump_start_z");
    data.remove("jump_windup_end");
    data.remove("jump_anim_triggered");

    setJumpState(npc, JUMP_IDLE);
    setJumpCooldown(npc);
    resumeCombatAI(npc);
    log("rat_ogre: jump finished");
}

function tryCastJump(npc, force) {
    var target = getCombatTarget(npc);
    if (!canStartJump(npc, target)) return false;
    if (!force && Math.random() > JUMP_CAST_CHANCE) return false;

    startJumpWindup(npc, target);
    return true;
}

function tryCastRandomAbility(npc) {
    var target = getCombatTarget(npc);
    var jumpOk = canStartJump(npc, target);
    var dashOk = canStartDash(npc, target);

    if (jumpOk && dashOk) {
        if (Math.random() < 0.5) {
            if (tryCastJump(npc, false)) return true;
            return tryCastDash(npc, false);
        }
        if (tryCastDash(npc, false)) return true;
        return tryCastJump(npc, false);
    }
    if (jumpOk) return tryCastJump(npc, false);
    if (dashOk) return tryCastDash(npc, false);
    return false;
}

// =====================================================
// События NPC (вкладка 1 — рывок)
// =====================================================

function init(event) {
    try {
        var npc = event.npc;
        npc.getStoreddata().put("rat_ogre", 1);

        if (npc.getStoreddata().get("_dash_inited") == 1) return;

        npc.getTimers().start(1, 20, true);
        npc.getStoreddata().put("_dash_inited", 1);

        log("rat_ogre abilities init OK");
    } catch (e) {
        log("rat_ogre dash init ERROR: " + e);
    }
}

function tick(event) {
    if (!isRatOgre(event.npc)) return;

    try {
        var npc = event.npc;
        var data = npc.getTempdata();
        var state = getDashState(npc);

        if (state == STATE_WINDUP) {
            haltNpcMovement(npc);

            var target = getSavedDashTarget(npc);
            if (target != null) {
                faceEntity(npc, target);
            }

            var windupEnd = data.get("windup_end");
            if (windupEnd != null && npc.getAge() >= windupEnd) {
                beginDash(npc);
            }
            return;
        }

        if (state == STATE_DASHING) {
            haltNpcMovement(npc);
            faceDashTarget(npc, data);
            return;
        }

        var jumpState = getJumpState(npc);
        if (jumpState == JUMP_WINDUP) {
            haltNpcMovement(npc);

            var jumpTarget = getSavedDashTarget(npc);
            if (jumpTarget != null) {
                faceEntity(npc, jumpTarget);
            }

            var jumpWindupEnd = data.get("jump_windup_end");
            if (jumpWindupEnd != null && npc.getAge() >= jumpWindupEnd) {
                beginJump(npc);
            }
            return;
        }

        if (jumpState == JUMP_FLYING) {
            haltNpcMovement(npc);
            faceJumpTarget(npc, data);
        }
    } catch (e) {
        log("rat_ogre tick ERROR: " + e);
    }
}

function timer(event) {
    if (!isRatOgre(event.npc)) return;

    var npc = event.npc;
    var id = event.id;

    try {
        if (id == 1) {
            if (isAbilityBusy(npc)) return;
            tryCastRandomAbility(npc);
            return;
        }

        if (id == 2) {
            if (getDashState(npc) != STATE_DASHING) {
                npc.getTimers().stop(2);
                return;
            }
            dashStep(npc);
            return;
        }

        if (id == 3) {
            if (getJumpState(npc) != JUMP_FLYING) {
                npc.getTimers().stop(3);
                return;
            }
            jumpStep(npc);
        }
    } catch (e) {
        log("rat_ogre timer#" + id + " ERROR: " + e);
        if (id == 2) {
            finishDash(npc);
        }
        if (id == 3) {
            finishJump(npc);
        }
    }
}

function trigger(event) {
    if (!isRatOgre(event.npc)) return;

    var npc = event.npc;
    var id = event.id;
    var args = event.arguments;

    if (id == "cast") {
        if (args != null && args.length >= 1 && String(args[0]) == "dash") {
            if (tryCastDash(npc, true)) {
                npc.say("§aПринудительный рывок.");
            } else {
                npc.say("§cРывок недоступен (кулдаун, нет цели или уже кастует).");
            }
            return;
        }
        if (args != null && args.length >= 1 && String(args[0]) == "jump") {
            if (tryCastJump(npc, true)) {
                npc.say("§aПринудительный прыжок.");
            } else {
                npc.say("§cПрыжок недоступен (кулдаун, нет цели или уже кастует).");
            }
        }
    }
}

function died(event) {
    if (!isRatOgre(event.npc)) return;

    try {
        if (isDashBusy(event.npc)) {
            finishDash(event.npc);
        }
        if (isJumpBusy(event.npc)) {
            finishJump(event.npc);
        }
    } catch (e) {}
}
