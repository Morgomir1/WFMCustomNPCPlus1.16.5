// =====================================================
// Witch Hunter — Tab 1: ИИ, выбор способности, кулдауны
// CustomNPC+ JS-скрипт для WFM 1.16.5
//
// Управляет ИИ: выбирает случайную готовую способность,
// вызывает функции саб-табов напрямую (общий скоп).
// Следит за cooldown и глобальной задержкой.
//
// Таймеры:
//   1 — ИИ: выбор способности (раз в секунду)
//   2 — шаги залпа арбалета (crossbow)
//   3 — аварийный сброс блокировки
//
// Отладка:
//   /script trigger cast crossbow
//   /script trigger cast fire_powder
//   /script trigger cast pistol
// =====================================================

// === Константы ===
var CROSSBOW_COOLDOWN = 300;
var FIRE_POWDER_COOLDOWN = 320;
var PISTOL_COOLDOWN = 320;
var FIRE_RADIUS = 4.0;
var GLOBAL_ABILITY_GAP = 100;
var WH_DEBUG = true;

var CROSSBOW_SHOTS = 6;
var CROSSBOW_SHOT_INTERVAL = 3;
var CROSSBOW_ACCURACY = 14;
var CROSSBOW_ITEM = "minecraft:arrow";
var CROSSBOW_DAMAGE = 8;
var CROSSBOW_SPREAD = 1.2;

var FIRE_DURATION_TICKS = 100;
var FIRE_DAMAGE = 6;
var FIRE_KNOCKBACK = 2.0;

var PISTOL_PROJ_ITEM = "minecraft:fire_charge";
var PISTOL_ACCURACY = 6;
var PISTOL_DAMAGE = 10;
var PISTOL_FIRE_TICKS = 100;

function whLog(msg) {
    if (WH_DEBUG) log("wh_main: " + msg);
}

// --- Реестр способностей ---

var ABILITIES = [
    {
        id: "crossbow",
        weight: 10,
        cooldown: CROSSBOW_COOLDOWN,
        announce: "§c*заряжает арбалет*",
        canCast: function(npc, target) {
            return target != null && target.isAlive();
        }
    },
    {
        id: "fire_powder",
        weight: 10,
        cooldown: FIRE_POWDER_COOLDOWN,
        announce: "§4*огненный порошок!*",
        canCast: function(npc, target) {
            if (target == null || !target.isAlive()) return false;
            return hasEnemiesInRange(npc, FIRE_RADIUS);
        }
    },
    {
        id: "pistol",
        weight: 10,
        cooldown: PISTOL_COOLDOWN,
        announce: "§6*пистоль!*",
        canCast: function(npc, target) {
            return target != null && target.isAlive();
        }
    }
];

// --- Утилиты ---

function isWitchHunter(npc) {
    return npc.getStoreddata().get("witch_hunter") == 1;
}

function getCombatTarget(npc) {
    var t = null;
    try { t = npc.getAttackTarget(); } catch (e) {}
    if (t == null) try { t = npc.getAi().getTarget(); } catch (e2) {}
    if (t != null && t.isAlive()) return t;
    return null;
}

function isSameEntity(a, b) {
    if (a == null || b == null) return false;
    try { return String(a.getUUID()) == String(b.getUUID()); } catch (e) { return a == b; }
}

function haltNpcMovement(npc) {
    try { npc.getMCEntity().getNavigation().stop(); } catch (e) {}
    try { npc.setMoveForward(0); npc.setMoveStrafing(0); npc.setMoveVertical(0); } catch (e) {}
}

function faceEntity(npc, target) {
    if (target == null) return;
    var dx = target.getX() - npc.getX();
    var dz = target.getZ() - npc.getZ();
    if (dx * dx + dz * dz < 0.0001) return;
    var yaw = Math.atan2(-dx, dz) * 180.0 / Math.PI;
    npc.setRotation(yaw);
    try {
        var mc = npc.getMCEntity();
        mc.yRot = yaw; mc.yRotO = yaw;
        mc.yBodyRot = yaw; mc.yBodyRotO = yaw;
        mc.yHeadRot = yaw; mc.yHeadRotO = yaw;
    } catch (e) {}
}

function hasEnemiesInRange(npc, radius) {
    try {
        var world = npc.getWorld();
        var pos = npc.getPos();
        var x = npc.getX();
        var z = npc.getZ();
        var types = [1, 3, 5];
        for (var t = 0; t < types.length; t++) {
            var list = world.getNearbyEntities(pos, radius + 1.0, types[t]);
            for (var i = 0; i < list.length; i++) {
                var e = list[i];
                if (e == null || !e.isAlive()) continue;
                if (isSameEntity(e, npc)) continue;
                try { if (e.typeOf(2)) continue; } catch (ex) {}
                var dx = e.getX() - x;
                var dz = e.getZ() - z;
                if (dx * dx + dz * dz <= radius * radius) return true;
            }
        }
    } catch (ex) {}
    return false;
}

function findEntityByUuid(world, uuid) {
    if (uuid == null) return null;
    try {
        var players = world.getAllPlayers();
        for (var i = 0; i < players.length; i++) {
            if (String(players[i].getUUID()) == String(uuid)) return players[i];
        }
    } catch (e) {}
    var types = [1, 3, 5];
    for (var t = 0; t < types.length; t++) {
        try {
            var all = world.getAllEntities(types[t]);
            for (var j = 0; j < all.length; j++) {
                if (String(all[j].getUUID()) == String(uuid)) return all[j];
            }
        } catch (e2) {}
    }
    return null;
}

// --- Кулдауны ---

function cdKey(id) { return "wh_cd_" + id; }

function isAbilityReady(npc, ability) {
    var r = npc.getStoreddata().get(cdKey(ability.id));
    if (r == null) r = 0;
    return npc.getAge() >= r;
}

function setAbilityCooldown(npc, ability) {
    npc.getStoreddata().put(cdKey(ability.id), npc.getAge() + ability.cooldown);
}

function isGlobalReady(npc) {
    var r = npc.getStoreddata().get("wh_global_cd");
    if (r == null) r = 0;
    return npc.getAge() >= r;
}

function setGlobalCooldown(npc) {
    npc.getStoreddata().put("wh_global_cd", npc.getAge() + GLOBAL_ABILITY_GAP);
}

function isBusy(npc) {
    return npc.getTempdata().get("wh_busy") == 1;
}

// =====================================================
// Способность 1: Арбалетный залп
// =====================================================

function startCrossbowVolley(npc) {
    var data = npc.getTempdata();
    data.put("cb_shots_fired", 0);
    data.put("cb_target_uuid", "");

    var target = getCombatTarget(npc);
    if (target != null) {
        data.put("cb_target_uuid", target.getUUID());
    }

    npc.getTimers().start(2, CROSSBOW_SHOT_INTERVAL, true);
    whLog("crossbow volley started");
}

function crossbowShotStep(npc) {
    var data = npc.getTempdata();
    var world = npc.getWorld();

    var fired = Number(data.get("cb_shots_fired"));
    if (isNaN(fired)) fired = 0;

    var target = getCombatTarget(npc);
    if (target == null) {
        var uuid = data.get("cb_target_uuid");
        if (uuid != null && String(uuid).length > 0) {
            target = findEntityByUuid(world, uuid);
        }
    }

    if (target != null && target.isAlive()) {
        faceEntity(npc, target);

        var tx = target.getX() + (Math.random() - 0.5) * CROSSBOW_SPREAD;
        var ty = target.getY() + 1.2 + (Math.random() - 0.5) * 0.6;
        var tz = target.getZ() + (Math.random() - 0.5) * CROSSBOW_SPREAD;

        var item = world.createItem(CROSSBOW_ITEM, 1);
        if (item != null) {
            try {
                var proj = npc.shootItem(tx, ty, tz, item, CROSSBOW_ACCURACY);
                if (proj != null) {
                    try {
                        var mc = proj.getMCEntity();
                        mc.damage = CROSSBOW_DAMAGE;
                    } catch (e) {}
                }
            } catch (e) {
                whLog("crossbow shot error: " + e);
            }
        }
    }

    try {
        world.spawnParticle("minecraft:smoke",
            npc.getX(), npc.getY() + 1.0, npc.getZ(),
            (Math.random() - 0.5) * 0.2, 0.05, (Math.random() - 0.5) * 0.2, 0, 3);
    } catch (e) {}

    fired++;
    data.put("cb_shots_fired", fired);

    if (fired >= CROSSBOW_SHOTS) {
        finishCrossbowVolley(npc);
    }
}

function finishCrossbowVolley(npc) {
    try { npc.getTimers().stop(2); } catch (e) {}

    var data = npc.getTempdata();
    data.remove("cb_shots_fired");
    data.remove("cb_target_uuid");

    // Разблокировка
    data.remove("wh_busy");
    data.remove("wh_active_ability");

    whLog("crossbow volley finished");
}

// =====================================================
// Способность 2: Огненный порошок
// =====================================================

function doFirePowderExplosion(npc) {
    var world = npc.getWorld();
    var x = npc.getX();
    var y = npc.getY();
    var z = npc.getZ();

    // Партиклы взрыва
    try {
        world.spawnParticle("minecraft:explosion", x, y + 0.3, z, 0, 0, 0, 0, 1);
        world.spawnParticle("minecraft:smoke", x, y + 0.5, z, 1.5, 0.3, 1.5, 0.05, 30);
        world.spawnParticle("minecraft:flame", x, y + 0.5, z, 1.2, 0.3, 1.2, 0.02, 25);
        world.spawnParticle("minecraft:lava", x, y + 0.1, z, 0.5, 0, 0.5, 0, 10);
        world.spawnParticle("minecraft:campfire_signal_smoke", x, y + 0.5, z, 0.8, 0.2, 0.8, 0.01, 20);
    } catch (e) {
        whLog("fire_powder particles error: " + e);
    }

    try {
        world.playSoundAt(npc.getPos(), "minecraft:entity.generic.explode", 1.5, 0.7);
    } catch (e) {}

    // Поиск и поджигание противников
    var pos = npc.getPos();
    var types = [1, 3, 5];
    var hitCount = 0;

    for (var t = 0; t < types.length; t++) {
        var list = world.getNearbyEntities(pos, FIRE_RADIUS + 1.0, types[t]);
        for (var i = 0; i < list.length; i++) {
            var entity = list[i];
            if (entity == null || !entity.isAlive()) continue;
            if (isSameEntity(entity, npc)) continue;
            try { if (entity.typeOf(2)) continue; } catch (e) {}

            var dx = entity.getX() - x;
            var dz = entity.getZ() - z;
            if (dx * dx + dz * dz > FIRE_RADIUS * FIRE_RADIUS) continue;

            try { entity.damage(FIRE_DAMAGE); } catch (e) {}

            try {
                var mcEnt = entity.getMCEntity();
                if (mcEnt != null) mcEnt.setRemainingFireTicks(FIRE_DURATION_TICKS);
            } catch (e) {}

            try {
                var yaw = Math.atan2(-dx, dz) * 180.0 / Math.PI;
                entity.knockback(FIRE_KNOCKBACK, yaw);
            } catch (e) {}

            hitCount++;
        }
    }

    whLog("fire_powder hit " + hitCount + " targets");

    // Разблокировка
    npc.getTempdata().remove("wh_busy");
    npc.getTempdata().remove("wh_active_ability");
}

// =====================================================
// Способность 3: Пистолет
// =====================================================

function shootPistolFireball(npc) {
    var world = npc.getWorld();
    var target = getCombatTarget(npc);
    if (target == null || !target.isAlive()) {
        npc.getTempdata().remove("wh_busy");
        npc.getTempdata().remove("wh_active_ability");
        return false;
    }

    faceEntity(npc, target);

    var item = world.createItem(PISTOL_PROJ_ITEM, 1);
    if (item == null) item = world.createItem("minecraft:fire_charge", 1);
    if (item == null) {
        npc.getTempdata().remove("wh_busy");
        npc.getTempdata().remove("wh_active_ability");
        return false;
    }

    var proj = null;
    try {
        proj = npc.shootItem(target, item, PISTOL_ACCURACY);
    } catch (e) {
        try {
            proj = npc.shootItem(
                target.getX(), target.getY() + 1.2, target.getZ(),
                item, PISTOL_ACCURACY);
        } catch (e2) {}
    }

    if (proj == null) {
        npc.getTempdata().remove("wh_busy");
        npc.getTempdata().remove("wh_active_ability");
        return false;
    }

    // Настройка MC-полей снаряда
    try {
        var mc = proj.getMCEntity();
        mc.damage = PISTOL_DAMAGE;
        mc.explosiveDamage = false;
        mc.setIs3D(false);
        mc.effect = 0; mc.duration = 0; mc.amplify = 0;
    } catch (e) {
        whLog("pistol mc config error: " + e);
    }

    // enableEvents + tempdata
    try { proj.enableEvents(); } catch (e) { whLog("pistol enableEvents error: " + e); }
    try { proj.getTempdata().put("wh_pistol_proj", 1); } catch (e) {}

    // Партиклы выстрела
    try {
        world.spawnParticle("minecraft:flame",
            npc.getX(), npc.getY() + 1.0, npc.getZ(),
            0.1, 0.1, 0.1, 0.02, 5);
        world.spawnParticle("minecraft:smoke",
            npc.getX(), npc.getY() + 1.0, npc.getZ(),
            0.1, 0.1, 0.1, 0.02, 4);
    } catch (e) {}

    whLog("pistol shot fired");

    // Разблокировка (выстрел мгновенный)
    npc.getTempdata().remove("wh_busy");
    npc.getTempdata().remove("wh_active_ability");
    return true;
}

// =====================================================
// projectileTick — трейл огненного шара
// =====================================================

function projectileTick(event) {
    var proj = event.projectile;
    if (proj == null || proj.getTempdata().get("wh_pistol_proj") != 1) return;

    try {
        var world = proj.getWorld();
        if (Math.random() < 0.4) {
            world.spawnParticle("minecraft:flame",
                proj.getX() + (Math.random() - 0.5) * 0.3,
                proj.getY() + (Math.random() - 0.5) * 0.3,
                proj.getZ() + (Math.random() - 0.5) * 0.3,
                0, 0, 0, 0, 1);
        }
        if (Math.random() < 0.2) {
            world.spawnParticle("minecraft:smoke",
                proj.getX(), proj.getY(), proj.getZ(),
                0, 0, 0, 0, 1);
        }
    } catch (e) {}
}

// =====================================================
// projectileImpact — поджог цели при попадании
// =====================================================

function projectileImpact(event) {
    if (event.type != 0) return;

    var proj = event.projectile;
    if (proj == null || proj.getTempdata().get("wh_pistol_proj") != 1) return;

    var target = event.target;
    if (target == null) return;

    var wrapped = null;
    try {
        wrapped = (typeof target.getMCEntity == "function") ? target : event.API.getIEntity(target);
    } catch (e) {}
    if (wrapped == null) return;

    // Поджог цели
    try {
        var mcTarget = wrapped.getMCEntity();
        if (mcTarget != null) mcTarget.setRemainingFireTicks(PISTOL_FIRE_TICKS);
    } catch (e) {}

    // Партиклы попадания
    try {
        proj.getWorld().spawnParticle("minecraft:flame",
            wrapped.getX(), wrapped.getY() + 1.0, wrapped.getZ(),
            0.3, 0.2, 0.3, 0.02, 8);
        proj.getWorld().spawnParticle("minecraft:smoke",
            wrapped.getX(), wrapped.getY() + 1.0, wrapped.getZ(),
            0.3, 0.2, 0.3, 0.02, 6);
    } catch (e) {}

    whLog("pistol fireball hit " + wrapped.getName());
}

// =====================================================
// ИИ: выбор и каст способности
// =====================================================

function pickRandomAbility(npc) {
    var candidates = [];
    var totalWeight = 0;
    var target = getCombatTarget(npc);

    for (var i = 0; i < ABILITIES.length; i++) {
        var a = ABILITIES[i];
        if (!isAbilityReady(npc, a)) continue;
        if (!a.canCast(npc, target)) continue;
        candidates.push(a);
        totalWeight += a.weight;
    }

    if (candidates.length == 0) return null;
    if (candidates.length == 1) return candidates[0];

    var roll = Math.random() * totalWeight;
    var sum = 0;
    for (var j = 0; j < candidates.length; j++) {
        sum += candidates[j].weight;
        if (roll <= sum) return candidates[j];
    }
    return candidates[candidates.length - 1];
}

function castAbility(npc, ability) {
    var target = getCombatTarget(npc);
    if (!ability.canCast(npc, target)) return false;

    if (ability.announce != null && ability.announce.length > 0) {
        npc.say(ability.announce);
    }

    // Блокируем ИИ
    npc.getTempdata().put("wh_busy", 1);
    npc.getTempdata().put("wh_active_ability", ability.id);

    // Аварийный таймер: если что-то пойдёт не так — снимем блокировку через 5 сек
    npc.getTimers().start(3, 100, false);

    // Кулдауны
    setAbilityCooldown(npc, ability);
    setGlobalCooldown(npc);

    // Прямой вызов нужной функции (общий скоп — все функции доступны)
    if (ability.id == "crossbow") {
        startCrossbowVolley(npc);
    } else if (ability.id == "fire_powder") {
        doFirePowderExplosion(npc);
    } else if (ability.id == "pistol") {
        shootPistolFireball(npc);
    }

    whLog("cast " + ability.id);
    return true;
}

function tryCastRandomAbility(npc) {
    if (isBusy(npc)) return false;
    if (!isGlobalReady(npc)) return false;
    if (getCombatTarget(npc) == null) return false;

    var ability = pickRandomAbility(npc);
    if (ability == null) return false;

    return castAbility(npc, ability);
}

// =====================================================
// События NPC
// =====================================================

function init(event) {
    try {
        var npc = event.npc;
        npc.getStoreddata().put("witch_hunter", 1);

        if (npc.getStoreddata().get("_wh_inited") == 1) return;

        npc.getTimers().start(1, 20, true);
        npc.getStoreddata().put("_wh_inited", 1);

        whLog("init OK");
    } catch (e) {
        whLog("init ERROR: " + e);
    }
}

function tick(event) {
    if (!isWitchHunter(event.npc)) return;

    try {
        if (isBusy(event.npc)) {
            haltNpcMovement(event.npc);
        }
    } catch (e) {}
}

function timer(event) {
    var npc = event.npc;
    if (!isWitchHunter(npc)) return;

    try {
        if (event.id == 1) {
            tryCastRandomAbility(npc);
            return;
        }

        if (event.id == 2) {
            // Проверяем, что это наш таб запустил таймер
            if (npc.getTempdata().get("wh_active_ability") != "crossbow") {
                try { npc.getTimers().stop(2); } catch (e) {}
                return;
            }
            crossbowShotStep(npc);
            return;
        }

        if (event.id == 3) {
            // Аварийный сброс, если что-то зависло
            if (npc.getTempdata().get("wh_busy") == 1) {
                npc.getTempdata().remove("wh_busy");
                npc.getTempdata().remove("wh_active_ability");
                whLog("timer#3: forced unlock (stuck ability)");
            }
            return;
        }
    } catch (e) {
        whLog("timer#" + event.id + " ERROR: " + e);
        if (event.id == 2) {
            npc.getTempdata().remove("wh_busy");
            npc.getTempdata().remove("wh_active_ability");
        }
    }
}

function trigger(event) {
    var npc = event.npc;
    if (!isWitchHunter(npc)) return;

    var args = event.arguments;

    if (event.id == "cast") {
        if (args == null || args.length < 1) {
            npc.say("§cИспользование: /script trigger cast <crossbow|fire_powder|pistol>");
            return;
        }
        var abilityId = String(args[0]);
        for (var i = 0; i < ABILITIES.length; i++) {
            if (ABILITIES[i].id == abilityId) {
                if (castAbility(npc, ABILITIES[i])) {
                    npc.say("§aКаст: " + abilityId);
                } else {
                    npc.say("§cНе удалось кастануть " + abilityId);
                }
                return;
            }
        }
        npc.say("§cНеизвестная способность: " + abilityId);
    }
}

function died(event) {
    var npc = event.npc;
    if (!isWitchHunter(npc)) return;

    try {
        try { npc.getTimers().stop(2); } catch (e) {}
        try { npc.getTimers().stop(3); } catch (e) {}
        npc.getTempdata().remove("wh_busy");
        npc.getTempdata().remove("wh_active_ability");
    } catch (e) {}
}