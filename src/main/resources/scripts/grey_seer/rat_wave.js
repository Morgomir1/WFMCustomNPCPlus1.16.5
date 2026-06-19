// =====================================================
// Grey Seer — Серый провидец
// CustomNPC+ JS-скрипт для WFM 1.16.5
//
// Способности:
// 1) Полчища крыс — 15s CD, призыв 10 крыс-миньонов с таймером жизни
// 2) Прыжок — 15s CD, телепорт в случайную точку + волна искажения
// 3) Волна искажения — 5s CD, 20–30 снарядов, счётчик искажения
//
// Clone Bank: tab=1, name="rat" (крысы-миньоны)
//
// Точки телепорта (можно задать тут или через trigger):
//   var TELEPORT_POINTS = [
//       {x: 499888, y: 50, z: -600},
//       {x: 499900, y: 50, z: -580},
//       {x: 499870, y: 50, z: -610}
//   ];
// Настройка через команду:
//   /script trigger set_tp 499888 50 -600 499900 50 -580 499870 50 -610
//   /script trigger clear_tp
// Принудительный каст:
//   /script trigger cast rat_swarm
//   /script trigger cast distortion_wave
// =====================================================

var RAT_LIFETIME = 200;    // 10 секунд (20 тиков/сек)
var MAX_RATS = 12;
var CLONE_TAB = 1;
var CLONE_NAME = "Крыса";
var DISTORTION_ITEM = "minecraft:air";
var DISTORTION_DAMAGE = 5;

// Точки телепортации (заполняются в init() или через trigger)
   var TELEPORT_POINTS = [
       {x: 499888, y: 50, z: -600},
       {x: 499900, y: 50, z: -580},
       {x: 499870, y: 50, z: -610}
   ];

// --- Реестр заклинаний ---
// weight > 0 — участвует в случайном выборе
// weight = 0 — только принудительный каст (реакция на урон)
var SPELLS = {
    rat_swarm: {
        id: "rat_swarm",
        weight: 10,
        cooldown: 300,          // 15 секунд
        enrageCooldown: 180,
        announce: "§cЧувствуете запах крыс?",
        count: 10,
        canCast: function(ctx) {
            return ctx.target != null
                && ctx.target.isAlive()
                && ctx.minions < MAX_RATS;
        },
        cast: function(ctx) {
            return spawnRatsAround(ctx, ctx.spell.count);
        }
    },
    leap: {
        id: "leap",
        weight: 0,              // только реакция на урон
        cooldown: 300,          // 15 секунд
        enrageCooldown: 180,
        announce: "§8*шорох*",
        distortionCount: 5,     // снарядов в залпе перед прыжком
        canCast: function(ctx) {
            return ctx.target != null && ctx.target.isAlive();
        },
        cast: function(ctx) {
            // Залп искажения в сторону атакующего
            if (ctx.spell.distortionCount > 0 && ctx.target != null) {
                castDistortionWaveToward(ctx, ctx.target, ctx.spell.distortionCount);
            }
            teleportBoss(ctx);
            return ctx.spell.distortionCount;
        }
    },
    distortion_wave: {
        id: "distortion_wave",
        weight: 10,
        cooldown: 100,          // 5 секунд
        enrageCooldown: 60,
        announce: "§5Искажение реальности!",
        minCount: 35,
        maxCount: 40,
        canCast: function(ctx) {
            return ctx.target != null && ctx.target.isAlive();
        },
        cast: function(ctx) {
            var target = getRandomPlayer(ctx.world);
            if (target == null) return 0;
            var count = ctx.spell.minCount + Math.floor(
                Math.random() * (ctx.spell.maxCount - ctx.spell.minCount + 1));
            castDistortionWaveToward(ctx, target, count);
            return count;
        }
    }
};

// Заклинания, из которых босс выбирает случайное
var SPELL_POOL = ["rat_swarm", "distortion_wave"];

// =====================================================
// Утилиты босса
// =====================================================

function isBoss(npc) {
    return npc.getStoreddata().get("grey_seer_boss") == 1;
}

function isEnraged(npc) {
    try {
        var cur = npc.getHealth();
        var max = npc.getMaxHealth();
        return max > 0 && cur < max * 0.3;
    } catch (e) {
        return false;
    }
}

function getWorldTime(world) {
    return world.getTotalTime();
}

function spellCooldownKey(spellId) {
    return "spell_cd_" + spellId;
}

function isSpellReady(npc, spell) {
    var readyAt = npc.getStoreddata().get(spellCooldownKey(spell.id));
    if (readyAt == null) readyAt = 0;
    return npc.getAge() >= readyAt;
}

function setSpellCooldown(npc, spell) {
    var cd = spell.cooldown;
    if (isEnraged(npc) && spell.enrageCooldown != null) {
        cd = spell.enrageCooldown;
    }
    npc.getStoreddata().put(spellCooldownKey(spell.id), npc.getAge() + cd);
}

function buildCastContext(npc, spell) {
    var world = npc.getWorld();
    return {
        npc: npc,
        world: world,
        target: npc.getAttackTarget(),
        minions: countMinions(world, npc.getPos(), npc),
        spell: spell
    };
}

// =====================================================
// Ядро ИИ: выбор и применение заклинания
// =====================================================

function pickRandomSpell(npc) {
    var candidates = [];
    var totalWeight = 0;

    for (var i = 0; i < SPELL_POOL.length; i++) {
        var spell = SPELLS[SPELL_POOL[i]];
        if (spell == null || spell.weight <= 0) continue;
        if (!isSpellReady(npc, spell)) continue;

        var ctx = buildCastContext(npc, spell);
        if (!spell.canCast(ctx)) continue;

        candidates.push(spell);
        totalWeight += spell.weight;
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

function castSpell(npc, spellId) {
    var spell = SPELLS[spellId];
    if (spell == null) return false;
    if (!isSpellReady(npc, spell)) return false;

    var ctx = buildCastContext(npc, spell);
    if (!spell.canCast(ctx)) return false;

    if (spell.announce != null && spell.announce.length > 0) {
        npc.say(spell.announce);
    }

    var result = spell.cast(ctx);
    if (result > 0) {
        setSpellCooldown(npc, spell);
        log("grey_seer: cast " + spell.id + " x" + result);
        return true;
    }
    return false;
}

function castRandomSpell(npc) {
    var spell = pickRandomSpell(npc);
    if (spell == null) return false;

    if (spell.announce != null && spell.announce.length > 0) {
        npc.say(spell.announce);
    }

    var ctx = buildCastContext(npc, spell);
    var result = spell.cast(ctx);
    if (result > 0) {
        setSpellCooldown(npc, spell);
        log("grey_seer: random cast " + spell.id + " x" + result);
        return true;
    }
    return false;
}

// =====================================================
// Миньоны (крысы)
// =====================================================

function countMinions(world, bossPos, boss) {
    var count = 0;
    var types = [2, 3, 5];
    for (var t = 0; t < types.length; t++) {
        var list = world.getNearbyEntities(bossPos, 50, types[t]);
        for (var i = 0; i < list.length; i++) {
            if (isMinion(list[i], boss)) count++;
        }
    }
    return count;
}

function isMinion(entity, boss) {
    // Проверяем по UUID в tempdata босса — это надёжнее, чем storeddata у клона
    try {
        var uuid = entity.getUUID();
        return boss.getTempdata().get("rat_" + uuid) == 1;
    } catch (e) {
        try {
            return entity.hasTag("rat_minion");
        } catch (e2) {
            return false;
        }
    }
}

function markMinion(rat, boss, world) {
    rat.addTag("rat_minion");
    // Храним флаг + время спавна на боссе по UUID крысы
    try {
        var uuid = rat.getUUID();
        boss.getTempdata().put("rat_" + uuid, 1);
        boss.getTempdata().put("rat_spawn_" + uuid, boss.getAge());
    } catch (e) {}
}

function removeMinion(entity, boss, world) {
    try {
        world.spawnParticle("minecraft:poof",
            entity.getX(), entity.getY() + 0.3, entity.getZ(),
            0, 0, 0, 0, 3);
    } catch (e) {}
    // Чистим данные на боссе
    try {
        var uuid = entity.getUUID();
        boss.getTempdata().remove("rat_" + uuid);
        boss.getTempdata().remove("rat_spawn_" + uuid);
    } catch (e) {}
    try { entity.removeTag("rat_minion"); } catch (e2) {}
    try {
        entity.kill();
    } catch (e3) {
        try { entity.despawn(); } catch (e4) {}
    }
}

function spawnRat(ctx, sx, sy, sz) {
    var rat = null;
    try {
        rat = ctx.world.spawnClone(sx, sy, sz, CLONE_TAB, CLONE_NAME);
    } catch (e) {}

    if (rat == null) {
        try {
            rat = ctx.world.createEntity("minecraft:silverfish");
            rat.setPosition(sx, sy, sz);
            rat.setName("§7Крыса");
            ctx.world.spawnEntity(rat);
        } catch (e2) {}
    }

    if (rat == null) return null;

    markMinion(rat, ctx.npc, ctx.world);
    try {
        rat.setAttackTarget(ctx.target);
    } catch (e3) {}

    try {
        ctx.world.spawnParticle("minecraft:smoke", sx, sy + 0.5, sz, 0, 0, 0, 0, 5);
    } catch (e4) {}

    return rat;
}

function spawnRatsAround(ctx, count) {
    cleanupMinions(ctx.world, ctx.npc.getPos(), ctx.npc);

    var active = countMinions(ctx.world, ctx.npc.getPos(), ctx.npc);
    if (active >= MAX_RATS) return 0;

    var toSpawn = Math.min(count, MAX_RATS - active);
    var spawned = 0;
    var npc = ctx.npc;

    for (var i = 0; i < toSpawn; i++) {
        var angle = (2 * Math.PI / toSpawn) * i + (Math.random() - 0.5) * 0.5;
        var dist = 2 + Math.random() * 3;
        var sx = npc.getX() + Math.sin(angle) * dist;
        var sz = npc.getZ() + Math.cos(angle) * dist;
        if (spawnRat(ctx, sx, npc.getY() + 0.5, sz) != null) {
            spawned++;
        }
    }
    return spawned;
}

function cleanupMinions(world, bossPos, boss) {
    var now = boss.getAge();
    var cleared = 0;
    var types = [2, 3, 5];

    for (var t = 0; t < types.length; t++) {
        var list = world.getNearbyEntities(bossPos, 50, types[t]);
        for (var i = 0; i < list.length; i++) {
            var ent = list[i];
            if (!isMinion(ent, boss)) continue;

            // Время жизни считаем по boss.getAge() на момент спавна — хранится в tempdata босса
            var uuid = ent.getUUID();
            var spawnAge = boss.getTempdata().get("rat_spawn_" + uuid);
            if (spawnAge == null) spawnAge = 0;

            var expired = (spawnAge > 0 && (now - spawnAge) >= RAT_LIFETIME);
            if (!ent.isAlive() || expired) {
                removeMinion(ent, boss, world);
                cleared++;
            }
        }
    }
    return cleared;
}

function despawnAllMinions(boss) {
    var world = boss.getWorld();
    var bossPos = boss.getPos();
    var types = [2, 3, 5];
    var despawned = 0;

    for (var t = 0; t < types.length; t++) {
        var list = world.getNearbyEntities(bossPos, 50, types[t]);
        for (var i = 0; i < list.length; i++) {
            if (isMinion(list[i], boss)) {
                removeMinion(list[i], boss, world);
                despawned++;
            }
        }
    }
    return despawned;
}

// =====================================================
// Волна искажения
// =====================================================

function getRandomPlayer(world) {
    var players = world.getAllPlayers();
    var alive = [];
    for (var i = 0; i < players.length; i++) {
        if (players[i] != null && players[i].isAlive()) {
            alive.push(players[i]);
        }
    }
    if (alive.length == 0) return null;
    return alive[Math.floor(Math.random() * alive.length)];
}

function isPlayerEntity(entity) {
    try {
        return entity != null && entity.getType() == 1;
    } catch (e) {
        return false;
    }
}

function applyDistortion(player) {
    var data = player.getTempdata();
    var hits = data.get("distortion_hits");
    if (hits == null) hits = 0;
    hits = hits + 1;
    data.put("distortion_hits", hits);

    try {
        // Каждые 5 попаданий: отравление I + слабость I на 15 секунд
        if (hits >= 5 && hits % 5 == 0) {
            player.addPotionEffect(PotionEffectType_POISON, 300, 0, false);
            player.addPotionEffect(PotionEffectType_WEAKNESS, 300, 0, false);
            player.message("§5Искажение усиливается! (" + hits + ")");
        }

        // Каждые 15 попаданий: медлительность I на 5с + слепота I на 2с
        if (hits >= 15 && hits % 15 == 0) {
            player.addPotionEffect(PotionEffectType_SLOWNESS, 100, 0, false);
            player.addPotionEffect(PotionEffectType_BLINDNESS, 40, 0, false);
            player.message("§5§lИскажение поглощает тебя! (" + hits + ")");
        }
    } catch (e) {
        log("grey_seer: applyDistortion ERROR: " + e);
    }
}

function configureDistortionProjectile(proj, world) {
    try {
        proj.setItem(world.createItem(DISTORTION_ITEM, 1));
        proj.enableEvents();
        proj.getTempdata().put("grey_seer_dist", 1);

        // Сбрасываем эффекты из настроек дальнего боя NPC — иначе дебафф попадает на босса
        var mc = proj.getMCEntity();
        mc.effect = 0;
        mc.duration = 0;
        mc.amplify = 0;
        mc.damage = DISTORTION_DAMAGE;
        mc.explosiveDamage = false;
        mc.setIs3D(false);
    } catch (e) {
        log("grey_seer: configure projectile ERROR: " + e);
    }
}

function shootDistortionAt(npc, world, target, spreadX, spreadZ, aimY) {
    var item = world.createItem(DISTORTION_ITEM, 1);
    var proj = null;

    try {
        proj = npc.shootItem(
            target.getX() + spreadX,
            aimY,
            target.getZ() + spreadZ,
            item,
            16 + Math.floor(Math.random() * 3)
        );
    } catch (e) {
        try {
            proj = npc.shootItem(target, item, 16);
        } catch (e2) {}
    }

    if (proj != null) {
        configureDistortionProjectile(proj, world);
    }
    return proj;
}

function getProjectileBoss(event) {
    try {
        var owner = event.projectile.getMCEntity().getOwner();
        if (owner == null) return null;
        return event.API.getIEntity(owner);
    } catch (e) {
        return null;
    }
}

function wrapImpactTarget(event) {
    var target = event.target;
    if (target == null) return null;
    try {
        if (typeof target.getType == "function") return target;
        return event.API.getIEntity(target);
    } catch (e) {
        return null;
    }
}

function isSameEntity(a, b) {
    try {
        return String(a.getUUID()) == String(b.getUUID());
    } catch (e) {
        return false;
    }
}

function castDistortionWaveToward(ctx, target, count) {
    var npc = ctx.npc;
    var world = ctx.world;

    for (var i = 0; i < count; i++) {
        var spreadX = (Math.random() - 0.5) * 0.2;
        var spreadZ = (Math.random() - 0.5) * 0.2;
        var aimY = target.getY() + 1.2;

        // След из партиклов от босса к цели
        var dx = (target.getX() - npc.getX()) / count;
        var dz = (target.getZ() - npc.getZ()) / count;
        var dy = (aimY - (npc.getY() + 1.2)) / count;

        try {
            world.spawnParticle("minecraft:dragon_breath",
                npc.getX() + dx * i,
                npc.getY() + 1.2 + dy * i,
                npc.getZ() + dz * i,
                0, 0, 0, 0, 1);
            world.spawnParticle("minecraft:portal",
                npc.getX() + dx * i,
                npc.getY() + 1.2 + dy * i,
                npc.getZ() + dz * i,
                0.02, 0.02, 0.02, 0.01, 1);
        } catch (e) {}

        shootDistortionAt(npc, world, target, spreadX, spreadZ, aimY);
    }

    // Финальный партикл-всплеск на месте босса
    try {
        world.spawnParticle("minecraft:dragon_breath",
            npc.getX(), npc.getY() + 1.2, npc.getZ(),
            0.3, 0.2, 0.3, 0.03, 20);
        world.spawnParticle("minecraft:portal",
            npc.getX(), npc.getY() + 1.2, npc.getZ(),
            0.2, 0.1, 0.2, 0.05, 10);
    } catch (e3) {}

    return count;
}

// =====================================================
// Телепорт
// =====================================================

function teleportBoss(ctx) {
    var npc = ctx.npc;
    var world = ctx.world;
    var point = null;

    // Используем заранее заданные точки, если есть
    if (TELEPORT_POINTS.length > 0) {
        point = TELEPORT_POINTS[Math.floor(Math.random() * TELEPORT_POINTS.length)];
    } else {
        // Fallback: случайная позиция рядом с точкой спавна
        var spawnX = npc.getStoreddata().get("spawn_x");
        var spawnY = npc.getStoreddata().get("spawn_y");
        var spawnZ = npc.getStoreddata().get("spawn_z");
        if (spawnX != null && spawnY != null && spawnZ != null) {
            var angle = Math.random() * Math.PI * 2;
            var dist = 4 + Math.random() * 4;
            point = {
                x: spawnX + Math.sin(angle) * dist,
                y: spawnY,
                z: spawnZ + Math.cos(angle) * dist
            };
        } else {
            var angle = Math.random() * Math.PI * 2;
            var dist = 5 + Math.random() * 5;
            point = {
                x: npc.getX() + Math.sin(angle) * dist,
                y: npc.getY(),
                z: npc.getZ() + Math.cos(angle) * dist
            };
        }
    }

    npc.setPosition(point.x, point.y, point.z);

    try {
        world.spawnParticle("minecraft:portal", point.x, point.y + 1, point.z,
            0.3, 0.5, 0.3, 0.1, 25);
        world.spawnParticle("minecraft:smoke", point.x, point.y + 0.2, point.z,
            0.2, 0.1, 0.2, 0.02, 10);
        world.playSoundAt(npc.getPos(), "minecraft:entity.enderman.teleport", 1.0, 0.8);
    } catch (e) {}

    try {
        if (ctx.target != null) npc.setAttackTarget(ctx.target);
    } catch (e2) {}

    return true;
}

// =====================================================
// Сохранение/загрузка точек телепортации в storeddata
// =====================================================

function loadTeleportPoints(npc) {
    TELEPORT_POINTS = [];
    var count = npc.getStoreddata().get("tp_count");
    if (count == null || count <= 0) return;

    for (var i = 0; i < count; i++) {
        var x = npc.getStoreddata().get("tp_" + i + "_x");
        var y = npc.getStoreddata().get("tp_" + i + "_y");
        var z = npc.getStoreddata().get("tp_" + i + "_z");
        if (x != null && y != null && z != null) {
            TELEPORT_POINTS.push({x: x, y: y, z: z});
        }
    }
}

function saveTeleportPoints(npc, points) {
    var stored = npc.getStoreddata();
    // Очищаем старые точки
    var oldCount = stored.get("tp_count");
    if (oldCount != null) {
        for (var i = 0; i < oldCount; i++) {
            stored.remove("tp_" + i + "_x");
            stored.remove("tp_" + i + "_y");
            stored.remove("tp_" + i + "_z");
        }
    }
    // Сохраняем новые
    stored.put("tp_count", points.length);
    for (var i = 0; i < points.length; i++) {
        stored.put("tp_" + i + "_x", points[i].x);
        stored.put("tp_" + i + "_y", points[i].y);
        stored.put("tp_" + i + "_z", points[i].z);
    }
    TELEPORT_POINTS = points.slice();
}

// =====================================================
// События NPC
// =====================================================

function init(event) {
    try {
        var npc = event.npc;
        npc.getStoreddata().put("grey_seer_boss", 1);

        if (npc.getStoreddata().get("_inited") == 1) return;

        // Сохраняем спавн-позицию для точек телепорта по умолчанию
        npc.getStoreddata().put("spawn_x", npc.getX());
        npc.getStoreddata().put("spawn_y", npc.getY());
        npc.getStoreddata().put("spawn_z", npc.getZ());

        // Загружаем точки телепортации (если заданы ранее)
        loadTeleportPoints(npc);

        npc.getTimers().start(1, 20, true); // ИИ: выбор заклинания (каждую секунду)
        npc.getTimers().start(2, 20, true); // очистка миньонов (каждую секунду)
        npc.getStoreddata().put("_inited", 1);

        log("grey_seer init OK, spells=" + SPELL_POOL.join(", ") +
            ", tp_points=" + TELEPORT_POINTS.length);
    } catch (e) {
        log("grey_seer init ERROR: " + e);
    }
}

function tick(event) {
    if (!isBoss(event.npc)) return;

    try {
        var npc = event.npc;
        if (npc.getAge() % 20 != 0) return;
        if (Math.random() > 0.3) return;
        npc.getWorld().spawnParticle("minecraft:dragon_breath",
            npc.getX() + (Math.random() - 0.5) * 4,
            npc.getY() + 0.5 + Math.random() * 2,
            npc.getZ() + (Math.random() - 0.5) * 4,
            0, 0.05, 0, 0, 1);
    } catch (e) {}
}

function timer(event) {
    var npc = event.npc;
    if (!isBoss(npc)) return;

    if (event.id == 1) {
        try {
            // Если нет цели — деспавним всех крыс
            if (npc.getAttackTarget() == null || !npc.getAttackTarget().isAlive()) {
                var allDespawned = despawnAllMinions(npc);
                if (allDespawned > 0) {
                    log("grey_seer: despawned " + allDespawned + " rats (no target)");
                }
            } else {
                castRandomSpell(npc);
            }
        } catch (e) {
            log("grey_seer AI ERROR: " + e);
        }
        return;
    }

    if (event.id == 2) {
        try {
            var cleared = cleanupMinions(npc.getWorld(), npc.getPos(), npc);
            if (cleared > 0) {
                log("grey_seer: despawned " + cleared + " rats");
            }
        } catch (e) {
            log("grey_seer cleanup ERROR: " + e);
        }
        return;
    }
}

function projectileTick(event) {
    try {
        var proj = event.projectile;
        if (proj == null || proj.getTempdata().get("grey_seer_dist") != 1) return;

        var world = proj.getWorld();
        world.spawnParticle("minecraft:dragon_breath",
            proj.getX(), proj.getY(), proj.getZ(),
            0, 0, 0, 0, 1);
        world.spawnParticle("minecraft:portal",
            proj.getX(), proj.getY(), proj.getZ(),
            0.02, 0.02, 0.02, 0.01, 1);
    } catch (e) {}
}

function projectileImpact(event) {
    try {
        // type 0 = попадание в сущность, 1 = в блок
        if (event.type != 0) return;

        var proj = event.projectile;
        if (proj == null || proj.getTempdata().get("grey_seer_dist") != 1) return;

        var boss = getProjectileBoss(event);
        if (boss == null || !isBoss(boss)) return;

        var target = wrapImpactTarget(event);
        if (!isPlayerEntity(target)) return;
        if (isSameEntity(target, boss)) return;

        applyDistortion(target);

        try {
            boss.getWorld().spawnParticle("minecraft:end_rod",
                target.getX(), target.getY() + 1.0, target.getZ(),
                0.1, 0.1, 0.1, 0, 3);
        } catch (e) {}
    } catch (e) {
        log("grey_seer projectileImpact ERROR: " + e);
    }
}

function damaged(event) {
    if (!isBoss(event.npc)) return;

    try {
        if (event.source == null || !event.source.isAlive()) return;

        var npc = event.npc;
        if (!isSpellReady(npc, SPELLS.leap)) return;

        var ctx = buildCastContext(npc, SPELLS.leap);
        ctx.target = event.source; // атакующий
        if (!SPELLS.leap.canCast(ctx)) return;

        npc.say(SPELLS.leap.announce);
        var result = SPELLS.leap.cast(ctx);
        if (result > 0) {
            setSpellCooldown(npc, SPELLS.leap);
            log("grey_seer: leap triggered by " + event.source.getName());
        }
    } catch (e) {
        log("grey_seer damaged ERROR: " + e);
    }
}

function kill(event) {
    if (!isBoss(event.npc)) return;

    try {
        var npc = event.npc;
        npc.setHealth(Math.min(npc.getHealth() + 15, npc.getMaxHealth()));
        npc.say("§cЕщё один пал!");
    } catch (e) {}
}

function targetLost(event) {
    if (!isBoss(event.npc)) return;

    try {
        var npc = event.npc;
        var despawned = despawnAllMinions(npc);
        if (despawned > 0) {
            npc.say("§7Крысы, прочь!");
            log("grey_seer: lost target, despawned " + despawned + " rats");
        }
    } catch (e) {
        log("grey_seer targetLost ERROR: " + e);
    }
}

function died(event) {
    if (!isBoss(event.npc)) return;

    try {
        var world = event.npc.getWorld();
        world.broadcast("§c§lСерый Провидец повержен! Да сгинет проклятие!");

        // Убираем всех миньонов
        var types = [2, 3, 5];
        var despawned = 0;
        for (var t = 0; t < types.length; t++) {
            var list = world.getNearbyEntities(event.npc.getPos(), 60, types[t]);
            for (var i = 0; i < list.length; i++) {
                if (isMinion(list[i], event.npc)) {
                    removeMinion(list[i], event.npc, world);
                    despawned++;
                }
            }
        }
        log("grey_seer died: despawned " + despawned + " rats");
    } catch (e) {
        log("grey_seer died ERROR: " + e);
    }
}

function trigger(event) {
    if (!isBoss(event.npc)) return;

    var npc = event.npc;
    var id = event.id;
    var args = event.arguments;

    if (id == "set_tp") {
        // /script trigger set_tp x1 y1 z1 x2 y2 z2 ...
        if (args == null || args.length < 3 || args.length % 3 != 0) {
            npc.say("§cИспользование: /script trigger set_tp x1 y1 z1 x2 y2 z2 ...");
            return;
        }
        var points = [];
        for (var i = 0; i < args.length; i += 3) {
            points.push({
                x: Number(args[i]),
                y: Number(args[i + 1]),
                z: Number(args[i + 2])
            });
        }
        saveTeleportPoints(npc, points);
        npc.say("§aУстановлено " + points.length + " точек телепортации.");
        log("grey_seer: set " + points.length + " teleport points");
        return;
    }

    if (id == "clear_tp") {
        saveTeleportPoints(npc, []);
        npc.say("§eТочки телепортации очищены. Используются позиции от спавна.");
        log("grey_seer: teleport points cleared");
        return;
    }

    if (id == "cast") {
        // /script trigger cast <spell_id>
        if (args != null && args.length >= 1) {
            var spellId = String(args[0]);
            if (castSpell(npc, spellId)) {
                npc.say("§aКаст: " + spellId);
            } else {
                npc.say("§cНе удалось кастануть " + spellId + " (кулдаун/условия)");
            }
        }
        return;
    }
}