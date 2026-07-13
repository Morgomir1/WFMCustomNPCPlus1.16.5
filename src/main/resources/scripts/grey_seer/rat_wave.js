// =====================================================
// Grey Seer — Серый провидец
// CustomNPC+ JS-скрипт для WFM 1.16.5
//
// Способности:
// 1) Полчища крыс — 15s CD, призыв 10 крыс-миньонов с таймером жизни
// 2) Прыжок — 15s CD, телепорт в самую далёкую точку
// 3) Волна проклятия — 5s CD, залп 10–15 снарядов, стаки проклятия в тегах игрока
//    5 стаков: отравление I (5с), 10: слабость I (15с), 15: замедление I (10с) + сброс стаков
//
// Clone Bank: tab=1, name="rat" (крысы-миньоны)
//
// Точки телепорта по умолчанию (если не заданы через trigger set_tp / clear_tp):
//   var DEFAULT_TELEPORT_POINTS = [
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
var MAX_RATS = 13;
var CLONE_TAB = 1;
var CLONE_NAME = "Крыса";
var CURSE_PROJECTILE_ITEM = "wfm:warpstone";
var CURSE_PROJECTILE_DAMAGE = 5;
var CURSE_TAG_PREFIX = "grey_seer_curse_";
var CURSE_VOLLEY_MIN = 10;
var CURSE_VOLLEY_MAX = 15;
var CURSE_THRESHOLD_POISON = 5;
var CURSE_THRESHOLD_WEAKNESS = 10;
var CURSE_THRESHOLD_SLOWNESS = 15;
var CURSE_POISON_DURATION = 5;      // секунды (addPotionEffect умножает на 20 тиков сам)
var CURSE_WEAKNESS_DURATION = 15; // секунды
var CURSE_SLOWNESS_DURATION = 10;   // секунды
var CURSE_TRAIL_STEPS = 5;
var CURSE_TRAIL_SPREAD = 0.12;
var CURSE_DEBUG = true;  // отладочные логи попаданий проклятия (выключить после теста)

function curseDebug(msg) {
    if (CURSE_DEBUG) log("grey_seer curse: " + msg);
}

function getEntityDebugName(entity) {
    try {
        if (entity == null) return "null";
        if (typeof entity.getName == "function") return String(entity.getName());
        return String(entity.getClass().getName());
    } catch (e) {
        return "?";
    }
}

// Точки телепортации по умолчанию (переопределяются через trigger set_tp)
var DEFAULT_TELEPORT_POINTS = [
    {x: 500171, y: 81, z: -1055},
    {x: 500160, y:88, z:-1050},
    {x: 500163, y:87, z:-1034},
    {x:500153, y: 87, z:-1037}
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
        canCast: function(ctx) {
            return ctx.target != null && ctx.target.isAlive();
        },
        cast: function(ctx) {
            teleportBoss(ctx);
            return 1;
        }
    },
    distortion_wave: {
        id: "distortion_wave",
        weight: 10,
        cooldown: 100,          // 5 секунд
        enrageCooldown: 60,
        announce: "§2Проклятие!",
        canCast: function(ctx) {
            return ctx.target != null && ctx.target.isAlive();
        },
        cast: function(ctx) {
            var count = CURSE_VOLLEY_MIN + Math.floor(
                Math.random() * (CURSE_VOLLEY_MAX - CURSE_VOLLEY_MIN + 1));
            return castCurseVolleyToward(ctx, ctx.target, count);
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
// Волна проклятия — залп снарядов
// =====================================================

function isPlayerEntity(entity) {
    try {
        if (entity == null) return false;
        // IPlayer / IEntity wrapper (CustomNPC type id = 1)
        if (typeof entity.typeOf == "function" && entity.typeOf(1)) return true;
        if (typeof entity.getType == "function" && entity.getType() == 1) return true;
        // Сырой MC-Entity: getType() возвращает EntityType, не число
        if (typeof entity.getMCEntity == "function") {
            var mc = entity.getMCEntity();
            return mc != null && String(mc.getClass().getName()).indexOf("ServerPlayerEntity") >= 0;
        }
        return String(entity.getClass().getName()).indexOf("ServerPlayerEntity") >= 0;
    } catch (e) {
        return false;
    }
}

function isSameEntity(a, b) {
    try {
        return String(a.getUUID()) == String(b.getUUID());
    } catch (e) {
        return false;
    }
}

function getCurseStacks(player) {
    try {
        var stored = player.getStoreddata().get("grey_seer_curse_stacks");
        if (stored != null) {
            var n = parseInt(stored, 10);
            if (!isNaN(n) && n > 0) return n;
        }
        // Совместимость со старыми тегами
        var tags = player.getTags();
        for (var i = 0; i < tags.length; i++) {
            var tag = String(tags[i]);
            if (tag.indexOf(CURSE_TAG_PREFIX) == 0) {
                var n2 = parseInt(tag.substring(CURSE_TAG_PREFIX.length), 10);
                return isNaN(n2) ? 0 : n2;
            }
        }
    } catch (e) {}
    return 0;
}

function setCurseStacks(player, stacks) {
    try {
        var data = player.getStoreddata();
        if (stacks > 0) {
            data.put("grey_seer_curse_stacks", stacks);
            player.addTag(CURSE_TAG_PREFIX + stacks);
        } else {
            data.remove("grey_seer_curse_stacks");
        }
        var tags = player.getTags();
        for (var i = 0; i < tags.length; i++) {
            var tag = String(tags[i]);
            if (tag.indexOf(CURSE_TAG_PREFIX) == 0 && tag != CURSE_TAG_PREFIX + stacks) {
                player.removeTag(tag);
            }
        }
    } catch (e) {
        log("grey_seer: setCurseStacks ERROR: " + e);
    }
}

function applyCurseThresholdEffects(player, stacks) {
    try {
        if (stacks == CURSE_THRESHOLD_POISON) {
            player.addPotionEffect(PotionEffectType_POISON, CURSE_POISON_DURATION, 0, false);
            curseDebug("threshold POISON on " + getEntityDebugName(player) + " (stacks=" + stacks + ")");
        }
        if (stacks == CURSE_THRESHOLD_WEAKNESS) {
            player.addPotionEffect(PotionEffectType_WEAKNESS, CURSE_WEAKNESS_DURATION, 0, false);
            curseDebug("threshold WEAKNESS on " + getEntityDebugName(player) + " (stacks=" + stacks + ")");
        }
        if (stacks >= CURSE_THRESHOLD_SLOWNESS) {
            player.addPotionEffect(PotionEffectType_SLOWNESS, CURSE_SLOWNESS_DURATION, 0, false);
            curseDebug("threshold SLOWNESS + reset on " + getEntityDebugName(player) + " (stacks=" + stacks + ")");
            setCurseStacks(player, 0);
            return 0;
        }
    } catch (e) {
        log("grey_seer: applyCurseThresholdEffects ERROR: " + e);
    }
    return stacks;
}

function addCurseStack(player) {
    var before = getCurseStacks(player);
    var stacks = before + 1;
    setCurseStacks(player, stacks);
    var after = applyCurseThresholdEffects(player, stacks);
    curseDebug("stack " + getEntityDebugName(player) + " " + before + " -> " + after
        + ", storeddata=" + player.getStoreddata().get("grey_seer_curse_stacks"));
    return after;
}

function spawnGreenCurseParticle(world, x, y, z, count) {
    if (count == null) count = 1;
    try {
        for (var i = 0; i < count; i++) {
            var ox = (Math.random() - 0.5) * CURSE_TRAIL_SPREAD;
            var oy = (Math.random() - 0.5) * CURSE_TRAIL_SPREAD;
            var oz = (Math.random() - 0.5) * CURSE_TRAIL_SPREAD;
            world.spawnParticle("minecraft:happy_villager",
                x + ox, y + oy, z + oz, 0, 0, 0, 0, 2);
            world.spawnParticle("minecraft:entity_effect",
                x + ox, y + oy, z + oz, 0.15, 0.85, 0.2, 0, 2);
        }
    } catch (e) {}
}

function spawnCurseTrailParticles(world, x1, y1, z1, x2, y2, z2) {
    for (var step = 1; step <= CURSE_TRAIL_STEPS; step++) {
        var t = step / CURSE_TRAIL_STEPS;
        spawnGreenCurseParticle(
            world,
            x1 + (x2 - x1) * t,
            y1 + (y2 - y1) * t,
            z1 + (z2 - z1) * t,
            2
        );
    }
}

function bindProjectileOwner(mc, bossNpc) {
    if (mc == null || bossNpc == null) return;

    var bossMc = bossNpc.getMCEntity();
    if (bossMc == null) return;

    if (typeof mc.setOwner == "function") {
        try {
            mc.setOwner(bossMc);
            return;
        } catch (eSetOwner) {}
    }

    // Прямая запись полей в Nashorn часто read-only — не логируем ожидаемый отказ
    try { mc.thrower = bossMc; } catch (eThrower) {}
    try { mc.npc = bossMc; } catch (eNpc) {}
}

function configureCurseProjectile(proj, world, bossNpc) {
    try {
        proj.enableEvents();
        proj.getTempdata().put("grey_seer_curse_proj", 1);
        if (bossNpc != null) {
            proj.getTempdata().put("grey_seer_boss_uuid", String(bossNpc.getUUID()));
        }

        try {
            proj.setItem(world.createItem(CURSE_PROJECTILE_ITEM, 1));
        } catch (eItem) {
            proj.setItem(world.createItem("minecraft:emerald", 1));
        }

        var mc = proj.getMCEntity();
        if (mc != null) {
            mc.effect = 0;
            mc.duration = 0;
            mc.amplify = 0;
            mc.damage = CURSE_PROJECTILE_DAMAGE;
            mc.explosiveDamage = false;
            mc.setIs3D(false);
            bindProjectileOwner(mc, bossNpc);
        }
    } catch (e) {
        log("grey_seer: configureCurseProjectile ERROR: " + e);
    }
}

function shootCurseAt(npc, world, target, spreadX, spreadZ, aimY) {
    var item = world.createItem(CURSE_PROJECTILE_ITEM, 1);
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
        configureCurseProjectile(proj, world, npc);
    }
    return proj;
}

function castCurseVolleyToward(ctx, target, count) {
    var npc = ctx.npc;
    var world = ctx.world;
    var aimY = target.getY() + 1.2;

    for (var i = 0; i < count; i++) {
        var spreadX = (Math.random() - 0.5) * 1.2;
        var spreadZ = (Math.random() - 0.5) * 1.2;

        spawnGreenCurseParticle(world, npc.getX(), npc.getY() + 1.2, npc.getZ(), 2);
        shootCurseAt(npc, world, target, spreadX, spreadZ, aimY);
    }

    try {
        world.spawnParticle("minecraft:happy_villager",
            npc.getX(), npc.getY() + 1.2, npc.getZ(),
            0.3, 0.2, 0.3, 0, 25);
        world.spawnParticle("minecraft:entity_effect",
            npc.getX(), npc.getY() + 1.2, npc.getZ(),
            0.15, 0.85, 0.2, 0, 15);
    } catch (e3) {}

    return count;
}

function findBossNpcByUuid(world, uuidStr) {
    if (uuidStr == null || String(uuidStr).length == 0) return null;
    try {
        var all = world.getAllEntities(2);
        for (var i = 0; i < all.length; i++) {
            if (String(all[i].getUUID()) == String(uuidStr)) return all[i];
        }
    } catch (e) {}
    return null;
}

function wrapMcEntityBoss(event, mcEntity) {
    if (mcEntity == null) return null;
    try {
        var boss = event.API.getIEntity(mcEntity);
        if (boss != null && isBoss(boss)) return boss;
    } catch (e) {}
    return null;
}

function getProjectileBoss(event) {
    try {
        var mc = event.projectile.getMCEntity();
        var world = event.projectile.getWorld();

        // EntityProjectile.thrower — прямое поле (getOwner() для NPC часто null)
        var fromThrower = wrapMcEntityBoss(event, mc.thrower);
        if (fromThrower != null) return fromThrower;

        // EntityProjectile.npc — выставляется при выстреле NPC
        var fromNpc = wrapMcEntityBoss(event, mc.npc);
        if (fromNpc != null) return fromNpc;

        // throwerName у снаряда = UUID стрелка (NPC или игрок)
        var throwerUuid = mc.throwerName;
        if (throwerUuid != null && String(throwerUuid).length > 0) {
            var fromName = findBossNpcByUuid(world, throwerUuid);
            if (fromName != null && isBoss(fromName)) return fromName;
        }

        var bossUuid = event.projectile.getTempdata().get("grey_seer_boss_uuid");
        if (bossUuid != null) {
            var fromTemp = findBossNpcByUuid(world, bossUuid);
            if (fromTemp != null && isBoss(fromTemp)) return fromTemp;
        }

        curseDebug("boss lookup failed: thrower=" + (mc.thrower == null ? "null" : "set")
            + ", npc=" + (mc.npc == null ? "null" : "set")
            + ", throwerName=" + throwerUuid
            + ", tempUuid=" + bossUuid);
    } catch (e) {
        curseDebug("boss lookup ERROR: " + e);
    }
    return null;
}

function wrapImpactTarget(event) {
    var target = event.target;
    if (target == null) return null;
    try {
        // IEntity wrapper имеет getMCEntity(); сырой MC-Entity — нет
        if (typeof target.getMCEntity == "function") return target;
        return event.API.getIEntity(target);
    } catch (e) {
        return null;
    }
}

// =====================================================
// Телепорт
// =====================================================

function pickFarthestTeleportPoint(npc, points) {
    if (points == null || points.length == 0) return null;

    var curX = npc.getX();
    var curY = npc.getY();
    var curZ = npc.getZ();
    var best = points[0];
    var bestDistSq = -1;

    for (var i = 0; i < points.length; i++) {
        var p = points[i];
        var dx = p.x - curX;
        var dy = p.y - curY;
        var dz = p.z - curZ;
        var distSq = dx * dx + dy * dy + dz * dz;
        if (distSq > bestDistSq) {
            bestDistSq = distSq;
            best = p;
        }
    }
    return best;
}

function teleportBoss(ctx) {
    var npc = ctx.npc;
    var world = ctx.world;
    var point = null;
    var points = getTeleportPoints(npc);

    // Используем заранее заданные точки, если есть
    if (points.length > 0) {
        point = pickFarthestTeleportPoint(npc, points);
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

function cloneTeleportPoints(points) {
    var out = [];
    for (var i = 0; i < points.length; i++) {
        out.push({
            x: Number(points[i].x),
            y: Number(points[i].y),
            z: Number(points[i].z)
        });
    }
    return out;
}

function getTeleportPoints(npc) {
    var stored = npc.getStoreddata();
    var count = stored.get("tp_count");

    // clear_tp явно ставит tp_count = 0 → fallback к спавну
    if (count != null) {
        if (count <= 0) return [];

        var points = [];
        for (var i = 0; i < count; i++) {
            var x = stored.get("tp_" + i + "_x");
            var y = stored.get("tp_" + i + "_y");
            var z = stored.get("tp_" + i + "_z");
            if (x != null && y != null && z != null) {
                points.push({x: Number(x), y: Number(y), z: Number(z)});
            }
        }
        return points;
    }

    // tp_count не задан — используем точки из скрипта
    return cloneTeleportPoints(DEFAULT_TELEPORT_POINTS);
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

        npc.getTimers().start(1, 20, true); // ИИ: выбор заклинания (каждую секунду)
        npc.getTimers().start(2, 20, true); // очистка миньонов (каждую секунду)
        npc.getStoreddata().put("_inited", 1);

        log("grey_seer init OK, spells=" + SPELL_POOL.join(", ") +
            ", tp_points=" + getTeleportPoints(npc).length);
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
        npc.getWorld().spawnParticle("minecraft:happy_villager",
            npc.getX() + (Math.random() - 0.5) * 4,
            npc.getY() + 0.5 + Math.random() * 2,
            npc.getZ() + (Math.random() - 0.5) * 4,
            0, 0.05, 0, 0, 1);
    } catch (e) {}
}

function projectileTick(event) {
    try {
        var proj = event.projectile;
        if (proj == null || proj.getTempdata().get("grey_seer_curse_proj") != 1) return;

        var world = proj.getWorld();
        var x = proj.getX();
        var y = proj.getY();
        var z = proj.getZ();
        var data = proj.getTempdata();

        var lx = data.get("trail_x");
        var ly = data.get("trail_y");
        var lz = data.get("trail_z");

        if (lx != null && ly != null && lz != null) {
            spawnCurseTrailParticles(world, lx, ly, lz, x, y, z);
        } else {
            spawnGreenCurseParticle(world, x, y, z, 3);
        }

        data.put("trail_x", x);
        data.put("trail_y", y);
        data.put("trail_z", z);
    } catch (e) {}
}

function projectileImpact(event) {
    try {
        if (event.type != 0) return; // 0 = сущность, 1 = блок

        var proj = event.projectile;
        if (proj == null || proj.getTempdata().get("grey_seer_curse_proj") != 1) {
            curseDebug("impact skip: not grey_seer curse projectile");
            return;
        }

        curseDebug("impact entity hit, proj@" + proj.getX().toFixed(1) + "," + proj.getZ().toFixed(1));

        var boss = getProjectileBoss(event);
        if (boss == null) {
            curseDebug("impact skip: boss not found");
            return;
        }
        if (!isBoss(boss)) {
            curseDebug("impact skip: owner " + getEntityDebugName(boss) + " is not grey_seer boss");
            return;
        }

        var rawTarget = event.target;
        var target = wrapImpactTarget(event);
        if (target == null) {
            curseDebug("impact skip: wrapImpactTarget failed, raw="
                + (rawTarget == null ? "null" : rawTarget.getClass().getName()));
            return;
        }
        if (!isPlayerEntity(target)) {
            curseDebug("impact skip: not a player, wrapped="
                + getEntityDebugName(target) + ", raw="
                + (rawTarget == null ? "null" : rawTarget.getClass().getName()));
            return;
        }
        if (isSameEntity(target, boss)) {
            curseDebug("impact skip: target is boss");
            return;
        }

        curseDebug("HIT player " + getEntityDebugName(target)
            + " uuid=" + target.getUUID()
            + " pos=" + target.getX().toFixed(1) + "," + target.getY().toFixed(1) + "," + target.getZ().toFixed(1));

        addCurseStack(target);

        try {
            spawnGreenCurseParticle(boss.getWorld(), target.getX(), target.getY() + 1.0, target.getZ(), 4);
        } catch (e) {}
    } catch (e) {
        log("grey_seer projectileImpact ERROR: " + e);
    }
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