// =====================================================
// Grey Seer — Серый провидец
// CustomNPC+ JS-скрипт для WFM 1.16.5
//
// Способности:
// 1) Прыжок — телепорт после 3-го удара; на старом месте спавнятся крысы
// 2) Варп-огнемёт — 10s CD, charge 1с → вытянутый конус 3с: 2 чистого урона / 0.5с,
//    стоит на месте, конус медленно доворачивает за целью каста;
//    VFX = wfm:warpfire_flame
// 3) Варп-сгусток — 10s CD, crimson_blob: навес → hazard-лужа,
//    партиклы полёта/приземления = wfm:warpfire_flame
//
// Clone Bank: tab=1, name="rat" (крысы-миньоны)
//
// Точки телепорта по умолчанию (если не заданы через trigger set_tp / clear_tp):
//   var DEFAULT_TELEPORT_POINTS = [ ... ];
// Настройка через команду:
//   /script trigger set_tp 499888 50 -600 499900 50 -580 499870 50 -610
//   /script trigger clear_tp
// Принудительный каст:
//   /script trigger cast leap
//   /script trigger cast warpfire_breath
//   /script trigger cast warpfire_blob
// =====================================================

var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();
var TelegraphAPI = Java.type("noppes.npcs.telegraph.TelegraphAPI");
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");
var AbilityCombatHelper = Java.type("noppes.npcs.abilities.AbilityCombatHelper");
var DamageSource = Java.type("net.minecraft.util.DamageSource");

var RAT_LIFETIME = 200;    // 10 секунд (20 тиков/сек)
var MAX_RATS = 4;
var RAT_SPAWN_ON_LEAP = 4; // сколько крыс оставлять на месте телепорта
var CLONE_TAB = 1;
var CLONE_NAME = "Крыса";

// --- Варп-огнемёт ---
var FLAME_CHARGE_TICKS = 20;        // 1 секунда warning перед струёй
var FLAME_CAST_TICKS = 60;          // 3 секунды активной струи
var FLAME_DAMAGE = 2.0;             // чистый (MAGIC) урон
var FLAME_DAMAGE_INTERVAL = 10;     // 0.5 секунды
var FLAME_LENGTH = 14.0;            // вытянутый конус
var FLAME_HALF_ANGLE = 18.0;        // узкий half-angle
var FLAME_RANGE = 15.0;             // max дистанция для старта каста
var FLAME_PARTICLE_COUNT = 96;      // плотность стрима за тик VFX
var FLAME_PARTICLE_SPEED_MIN = 0.85; // скорость вылета от провидца
var FLAME_PARTICLE_SPEED_MAX = 1.55;
var FLAME_VFX_TIMER_TICKS = 1;      // каждый тик — плавный поворот конуса
var FLAME_PARTICLE_INTERVAL = 5;    // плотность струи (не каждый тик)
var FLAME_PARTICLE = "wfm:warpfire_flame";
var FLAME_TELEGRAPH_COLOR = 0xC0FF3030; // warning red (стандарт attack-zones)
var FLAME_TURN_RATE = 1.2;              // град/тик — медленный трекинг за игроком
var FLAME_TG_RESYNC_DEG = 1.5;          // порог пересоздания telegraph

// --- Варп-сгусток (crimson_blob + warpfire VFX) ---
var BLOB_MAX_RANGE = 40.0;
var BLOB_ZONE_RADIUS = 2.0;
var BLOB_ZONE_SECONDS = 8;
var BLOB_DAMAGE_PER_SECOND = 5.0;
var BLOB_EFFECTS = "minecraft:blindness";
var BLOB_EFFECT_SECONDS = 2;
var BLOB_EFFECT_AMPLIFIER = 0;
var BLOB_PARTICLES = "wfm:warpfire_flame";
var BLOB_LAND_PARTICLES = "wfm:warpfire_flame";
var BLOB_PARTICLE_COUNT = 12;

var FLAME_CASTING_KEY = "wf_casting";
var FLAME_ACTIVE_KEY = "wf_active";     // когда начинается струя (после charge)
var FLAME_END_KEY = "wf_end";
var FLAME_NEXT_DMG_KEY = "wf_next_dmg";
var FLAME_LOCK_X = "wf_lock_x";
var FLAME_LOCK_Y = "wf_lock_y";
var FLAME_LOCK_Z = "wf_lock_z";
var FLAME_YAW_KEY = "wf_yaw";
var FLAME_TG_KEY = "wf_tg";
var FLAME_TG_YAW_KEY = "wf_tg_yaw";
var FLAME_BASE_SPEED_KEY = "wf_base_speed";
var FLAME_FIRED_KEY = "wf_fired";       // звук старта струи один раз
var LEAP_HIT_KEY = "leap_hits";

// Точки телепортации по умолчанию (переопределяются через trigger set_tp)
var DEFAULT_TELEPORT_POINTS = [
    {x: 24280, y: 80, z:-60499},
    {x: 24281, y:80, z:-60476},
    {x: 24263, y:80, z:-60483},
    {x: 24273, y:80, z:-60492}
];
// --- Реестр заклинаний ---
// weight > 0 — участвует в случайном выборе
// weight = 0 — только принудительный каст (реакция на урон)
var SPELLS = {
    rat_swarm: {
        id: "rat_swarm",
        weight: 0,               // только вместе с телепортом (или trigger)
        cooldown: 0,
        enrageCooldown: 0,
        announce: "§cЧувствуете запах крыс?",
        count: RAT_SPAWN_ON_LEAP,
        canCast: function(ctx) {
            return !isCastingBlocked(ctx.npc) && ctx.minions < MAX_RATS;
        },
        cast: function(ctx) {
            // Форс-каст: вокруг текущей позиции (обычный телепорт сам зовёт spawnRatsAt)
            return spawnRatsAt(ctx, ctx.spell.count, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
        }
    },
    leap: {
        id: "leap",
        weight: 0,              // только реакция на урон (после N ударов)
        cooldown: 0,
        enrageCooldown: 180,
        announce: "§8*шорох*",
        hitsNeeded: 3,
        canCast: function(ctx) {
            // Телепорт по хитам — цель не обязательна (точка из tp-листа)
            return !isCastingBlocked(ctx.npc);
        },
        cast: function(ctx) {
            teleportBoss(ctx);
            try {
                ctx.npc.getStoreddata().put(LEAP_HIT_KEY, "0");
            } catch (e) {}
            return 1;
        }
    },
    warpfire_breath: {
        id: "warpfire_breath",
        weight: 10,
        cooldown: 300,          // 10 секунд
        enrageCooldown: 120,
        announce: "§aВарп-пламя!",
        canCast: function(ctx) {
            if (ctx.target == null || !ctx.target.isAlive()) return false;
            if (isCastingBlocked(ctx.npc)) return false;
            var dx = ctx.target.getX() - ctx.npc.getX();
            var dz = ctx.target.getZ() - ctx.npc.getZ();
            return Math.sqrt(dx * dx + dz * dz) <= FLAME_RANGE;
        },
        cast: function(ctx) {
            return startWarpfireBreath(ctx) ? 1 : 0;
        }
    },
    warpfire_blob: {
        id: "warpfire_blob",
        weight: 10,
        cooldown: 30,          // 10 секунд
        enrageCooldown: 100,
        canCast: function(ctx) {
            if (ctx.target == null || !ctx.target.isAlive()) return false;
            if (isCastingBlocked(ctx.npc)) return false;
            var dx = ctx.target.getX() - ctx.npc.getX();
            var dz = ctx.target.getZ() - ctx.npc.getZ();
            return Math.sqrt(dx * dx + dz * dz) <= BLOB_MAX_RANGE;
        },
        cast: function(ctx) {
            return startWarpfireBlob(ctx) ? 1 : 0;
        }
    }
};

// Заклинания, из которых босс выбирает случайное (крысы — только при телепорте)
var SPELL_POOL = ["warpfire_breath", "warpfire_blob"];

// =====================================================
// Утилиты босса
// =====================================================

function isBoss(npc) {
    try {
        return npc != null && npc.getStoreddata().get("grey_seer_boss") == 1;
    } catch (e) {
        return false;
    }
}

/** Скрипт-логика (ИИ/касты/тикеры) — только пока босс жив. */
function canRunScript(npc) {
    try {
        return isBoss(npc) && npc.isAlive();
    } catch (e) {
        return false;
    }
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

function getFloat(data, key, fallback) {
    var v = data.get(key);
    if (v == null) return fallback;
    var n = parseFloat(String(v));
    return isNaN(n) ? fallback : n;
}

function getInt(data, key, fallback) {
    var v = data.get(key);
    if (v == null) return fallback;
    var n = parseInt(String(v), 10);
    return isNaN(n) ? fallback : n;
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
    if (!canRunScript(npc)) return false;
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
    if (!canRunScript(npc)) return false;
    if (isCastingBlocked(npc)) return false;

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

function spawnRatsAt(ctx, count, cx, cy, cz) {
    cleanupMinions(ctx.world, ctx.npc.getPos(), ctx.npc);

    var active = countMinions(ctx.world, ctx.npc.getPos(), ctx.npc);
    if (active >= MAX_RATS) return 0;

    var toSpawn = Math.min(count, MAX_RATS - active);
    var spawned = 0;

    for (var i = 0; i < toSpawn; i++) {
        var angle = (2 * Math.PI / toSpawn) * i + (Math.random() - 0.5) * 0.5;
        var dist = 0.6 + Math.random() * 1.4; // плотнее вокруг точки ухода
        var sx = cx + Math.sin(angle) * dist;
        var sz = cz + Math.cos(angle) * dist;
        if (spawnRat(ctx, sx, cy + 0.5, sz) != null) {
            spawned++;
        }
    }
    return spawned;
}

function spawnRatsAround(ctx, count) {
    return spawnRatsAt(ctx, count, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
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
// Варп-огнемёт — вытянутый конус, channel 3с
// =====================================================

function isWarpfireCasting(npc) {
    try {
        return String(npc.getStoreddata().get(FLAME_CASTING_KEY)) == "1";
    } catch (e) {
        return false;
    }
}

function isAbilityBusy(npc) {
    try {
        return AbilityAPI.isBusy(npc);
    } catch (e) {
        return false;
    }
}

/** Огнемёт или Java-абилка (сгусток) — нельзя стартовать другое. */
function isCastingBlocked(npc) {
    return isWarpfireCasting(npc) || isAbilityBusy(npc);
}

function startWarpfireBlob(ctx) {
    var npc = ctx.npc;
    var target = ctx.target;
    if (target == null || !target.isAlive()) return false;
    if (isCastingBlocked(npc)) return false;

    try {
        return AbilityAPI.start(npc, "crimson_blob", target, AbilityAPI.params(
            "maxRange", BLOB_MAX_RANGE,
            "landRadius", BLOB_ZONE_RADIUS,
            "zoneTicks", Math.floor(BLOB_ZONE_SECONDS * 20),
            "damage", BLOB_DAMAGE_PER_SECOND,
            "damageInterval", 20,
            "effectId", BLOB_EFFECTS,
            "effectDuration", Math.floor(BLOB_EFFECT_SECONDS * 20),
            "effectAmplifier", BLOB_EFFECT_AMPLIFIER,
            "blobParticles", BLOB_PARTICLES,
            "landParticles", BLOB_LAND_PARTICLES,
            "particleCount", BLOB_PARTICLE_COUNT
        ));
    } catch (e) {
        log("grey_seer warpfire_blob ERROR: " + e);
        return false;
    }
}

function computeYawToTarget(npc, target) {
    if (target == null) {
        try {
            return npc.getMCEntity().yRot;
        } catch (e) {
            return 0;
        }
    }
    var dx = target.getX() - npc.getX();
    var dz = target.getZ() - npc.getZ();
    return AbilityCombatHelper.computeYaw(dx, dz);
}

function normalizeYawDelta(delta) {
    while (delta > 180.0) delta -= 360.0;
    while (delta < -180.0) delta += 360.0;
    return delta;
}

/** Плавно приближает текущий yaw к desired, не быстрее maxStep град. */
function approachYaw(current, desired, maxStep) {
    var delta = normalizeYawDelta(desired - current);
    if (delta > maxStep) delta = maxStep;
    if (delta < -maxStep) delta = -maxStep;
    return current + delta;
}

/** Медленно доворачивает конус к attack-target; возвращает актуальный yaw. */
function trackWarpfireYaw(npc, data, yaw) {
    var target = null;
    try {
        target = npc.getAttackTarget();
    } catch (e) {}
    if (target == null || !target.isAlive()) return yaw;

    var desired = computeYawToTarget(npc, target);
    var next = approachYaw(yaw, desired, FLAME_TURN_RATE);
    data.put(FLAME_YAW_KEY, String(next));
    return next;
}

function lockCastStance(npc, data) {
    var ai = npc.getAi();
    if (!data.has(FLAME_BASE_SPEED_KEY)) {
        data.put(FLAME_BASE_SPEED_KEY, String(ai.getWalkingSpeed()));
    }
    // Не трогаем OnAttack/retaliate — только стоп движения + lock позиции
    try {
        ai.setWalkingSpeed(0);
    } catch (e2) {}
    try {
        AbilityCombatHelper.stopNavigation(npc);
    } catch (e3) {}
    try {
        npc.setMotionX(0);
        npc.setMotionY(0);
        npc.setMotionZ(0);
    } catch (e4) {}
}

function unlockCastStance(npc, data) {
    var ai = npc.getAi();
    var speed = getInt(data, FLAME_BASE_SPEED_KEY, 5);
    try {
        ai.setWalkingSpeed(speed);
    } catch (e) {}
}

function holdCastPosition(npc, data) {
    var x = getFloat(data, FLAME_LOCK_X, npc.getX());
    var y = getFloat(data, FLAME_LOCK_Y, npc.getY());
    var z = getFloat(data, FLAME_LOCK_Z, npc.getZ());
    var yaw = getFloat(data, FLAME_YAW_KEY, 0);
    try {
        npc.setPosition(x, y, z);
        npc.setRotation(yaw);
    } catch (e) {}
    // Жёстко фиксируем MC-поворот — AI иначе мгновенно смотрит на цель
    try {
        var mc = npc.getMCEntity();
        if (mc != null) {
            mc.yRot = yaw;
            mc.yRotO = yaw;
            mc.yHeadRot = yaw;
            mc.yBodyRot = yaw;
        }
    } catch (eMc) {}
    try {
        npc.setMotionX(0);
        npc.setMotionY(0);
        npc.setMotionZ(0);
    } catch (e2) {}
    try {
        AbilityCombatHelper.stopNavigation(npc);
    } catch (e3) {}
}

function clearWarpfireTelegraph(data) {
    var tid = data.get(FLAME_TG_KEY);
    if (tid != null && String(tid).length > 0) {
        try {
            TelegraphAPI.remove(String(tid));
        } catch (e) {}
    }
    data.put(FLAME_TG_KEY, "");
}

/** Пересоздаёт cone-telegraph с текущим yaw (без follow — follow = мгновенный snap к AI yRot). */
function syncWarpfireTelegraph(npc, data, yaw, remainingTicks) {
    var hasTg = data.get(FLAME_TG_KEY) != null && String(data.get(FLAME_TG_KEY)).length > 0;
    var lastYaw = getFloat(data, FLAME_TG_YAW_KEY, yaw);
    var delta = Math.abs(normalizeYawDelta(yaw - lastYaw));
    if (hasTg && delta < FLAME_TG_RESYNC_DEG) return;

    clearWarpfireTelegraph(data);
    var life = Math.max(1, remainingTicks | 0);
    try {
        var tid = TelegraphAPI.cone(
            npc,
            npc.getX(), npc.getY(), npc.getZ(),
            yaw,
            FLAME_LENGTH,
            FLAME_HALF_ANGLE,
            life,
            FLAME_TELEGRAPH_COLOR
        );
        data.put(FLAME_TG_KEY, String(tid));
        data.put(FLAME_TG_YAW_KEY, String(yaw));
    } catch (te) {
        log("grey_seer warpfire telegraph sync ERROR: " + te);
    }
}

function endWarpfireBreath(npc, data) {
    if (String(data.get(FLAME_CASTING_KEY)) != "1") return;
    clearWarpfireTelegraph(data);
    unlockCastStance(npc, data);
    data.put(FLAME_CASTING_KEY, "0");
    data.put(FLAME_ACTIVE_KEY, "0");
    data.put(FLAME_END_KEY, "0");
    data.put(FLAME_NEXT_DMG_KEY, "0");
    data.put(FLAME_TG_YAW_KEY, "0");
    data.put(FLAME_FIRED_KEY, "0");
}

function startWarpfireBreath(ctx) {
    var npc = ctx.npc;
    var world = ctx.world;
    var target = ctx.target;
    var data = npc.getStoreddata();
    var now = npc.getAge();
    var yaw = computeYawToTarget(npc, target);
    var activeAt = now + FLAME_CHARGE_TICKS;
    var totalTicks = FLAME_CHARGE_TICKS + FLAME_CAST_TICKS;

    data.put(FLAME_CASTING_KEY, "1");
    data.put(FLAME_ACTIVE_KEY, String(activeAt));
    data.put(FLAME_END_KEY, String(activeAt + FLAME_CAST_TICKS));
    data.put(FLAME_NEXT_DMG_KEY, String(activeAt));
    data.put(FLAME_LOCK_X, String(npc.getX()));
    data.put(FLAME_LOCK_Y, String(npc.getY()));
    data.put(FLAME_LOCK_Z, String(npc.getZ()));
    data.put(FLAME_YAW_KEY, String(yaw));
    data.put(FLAME_TG_YAW_KEY, String(yaw));
    data.put(FLAME_FIRED_KEY, "0");

    lockCastStance(npc, data);
    holdCastPosition(npc, data);

    // Telegraph на charge + active — видимое окно уворота перед струёй
    try {
        var tid = TelegraphAPI.cone(
            npc,
            npc.getX(), npc.getY(), npc.getZ(),
            yaw,
            FLAME_LENGTH,
            FLAME_HALF_ANGLE,
            totalTicks,
            FLAME_TELEGRAPH_COLOR
        );
        data.put(FLAME_TG_KEY, String(tid));
    } catch (te) {
        log("grey_seer warpfire telegraph ERROR: " + te);
    }

    try {
        world.playSoundAt(npc.getPos(), "minecraft:block.fire.ambient", 0.8, 0.5);
    } catch (e) {}

    return true;
}

function isInWarpfireCone(npc, entity, yaw) {
    var dx = entity.getX() - npc.getX();
    var dz = entity.getZ() - npc.getZ();
    var dist = Math.sqrt(dx * dx + dz * dz);
    if (dist > FLAME_LENGTH) return false;
    if (dist < 0.15) return true;

    var rad = (yaw + 90.0) * 0.0174532925;
    var fwdX = Math.cos(rad);
    var fwdZ = Math.sin(rad);
    var toX = dx / dist;
    var toZ = dz / dist;
    var dot = fwdX * toX + fwdZ * toZ;
    var angle = Math.acos(Math.max(-1.0, Math.min(1.0, dot))) * (180.0 / Math.PI);
    return angle <= FLAME_HALF_ANGLE;
}

function dealPureDamage(entity, amount) {
    try {
        var mc = entity.getMCEntity();
        if (mc != null) {
            mc.hurt(DamageSource.MAGIC, amount);
            return;
        }
    } catch (e) {}
    try {
        entity.damage(amount);
    } catch (e2) {}
}

function damageWarpfireCone(npc, world, yaw) {
    var list = world.getNearbyEntities(npc.getPos(), Math.ceil(FLAME_LENGTH + 1), 5);
    var hits = 0;
    for (var i = 0; i < list.length; i++) {
        var ent = list[i];
        if (ent == null || !ent.isAlive()) continue;
        if (!AbilityCombatHelper.isHostileToBoss(npc, ent)) continue;
        if (!isInWarpfireCone(npc, ent, yaw)) continue;
        dealPureDamage(ent, FLAME_DAMAGE);
        hits++;
    }
    return hits;
}

function spawnWarpfireConeParticles(world, npc, yaw) {
    var rad = (yaw + 90.0) * 0.0174532925;
    var fwdX = Math.cos(rad);
    var fwdZ = Math.sin(rad);
    var rightX = -fwdZ;
    var rightZ = fwdX;

    // Точка вылета — грудь/рот провидца, чуть вперёд
    var ox = npc.getX() + fwdX * 0.55;
    var oy = npc.getY() + 1.35;
    var oz = npc.getZ() + fwdZ * 0.55;
    var halfRad = FLAME_HALF_ANGLE * 0.0174532925;
    var count = FLAME_PARTICLE_COUNT;

    for (var i = 0; i < count; i++) {
        // Спавн у источника (крошечный джиттер), полёт — скоростью наружу
        var jx = (Math.random() - 0.5) * 0.22;
        var jy = (Math.random() - 0.5) * 0.18;
        var jz = (Math.random() - 0.5) * 0.22;

        // Направление внутри конуса (yaw ± halfAngle + лёгкий pitch)
        var yawOff = (Math.random() - 0.5) * 2.0 * halfRad;
        var pitchOff = (Math.random() - 0.25) * halfRad * 0.85;
        var cosY = Math.cos(yawOff);
        var sinY = Math.sin(yawOff);
        var dirX = fwdX * cosY + rightX * sinY;
        var dirZ = fwdZ * cosY + rightZ * sinY;
        var dirY = Math.sin(pitchOff);

        // Нормализация горизонтали + pitch
        var flat = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (flat < 0.001) flat = 0.001;
        var cosP = Math.cos(pitchOff);
        dirX = (dirX / flat) * cosP;
        dirZ = (dirZ / flat) * cosP;

        var speed = FLAME_PARTICLE_SPEED_MIN
            + Math.random() * (FLAME_PARTICLE_SPEED_MAX - FLAME_PARTICLE_SPEED_MIN);

        try {
            // count=0: velocity = (dx,dy,dz) * speed — летят от провидца
            world.spawnParticle(FLAME_PARTICLE,
                ox + jx, oy + jy, oz + jz,
                dirX, dirY, dirZ,
                speed, 0);
        } catch (e) {}
    }
}

function tickWarpfireBreath(npc, world) {
    var data = npc.getStoreddata();
    if (String(data.get(FLAME_CASTING_KEY)) != "1") return;

    if (!npc.isAlive()) {
        endWarpfireBreath(npc, data);
        return;
    }

    var now = npc.getAge();
    var activeAt = getInt(data, FLAME_ACTIVE_KEY, 0);
    var endAt = getInt(data, FLAME_END_KEY, 0);
    var remaining = endAt - now;
    var yaw = trackWarpfireYaw(npc, data, getFloat(data, FLAME_YAW_KEY, 0));

    lockCastStance(npc, data);
    holdCastPosition(npc, data);
    syncWarpfireTelegraph(npc, data, yaw, remaining);

    // Charge: только telegraph + поворот, без урона и струи
    if (now < activeAt) return;

    if (String(data.get(FLAME_FIRED_KEY)) != "1") {
        data.put(FLAME_FIRED_KEY, "1");
        try {
            world.playSoundAt(npc.getPos(), "minecraft:item.firecharge.use", 1.0, 0.6);
        } catch (eFire) {}
    }

    if (now % FLAME_PARTICLE_INTERVAL == 0) {
        spawnWarpfireConeParticles(world, npc, yaw);
    }

    if (now >= getInt(data, FLAME_NEXT_DMG_KEY, 0)) {
        damageWarpfireCone(npc, world, yaw);
        data.put(FLAME_NEXT_DMG_KEY, String(now + FLAME_DAMAGE_INTERVAL));
        try {
            world.playSoundAt(npc.getPos(), "minecraft:block.fire.ambient", 0.7, 1.4);
        } catch (e) {}
    }

    if (now >= endAt) {
        endWarpfireBreath(npc, data);
        log("grey_seer: warpfire_breath finished");
    }
}

// =====================================================
// Телепорт (после N ударов)
// =====================================================

function resolveDamageAttacker(event) {
    try {
        var src = event.source;
        if (src != null) {
            if (typeof src.isAlive == "function") {
                if (src.isAlive()) return src;
            } else {
                return src;
            }
        }
    } catch (e0) {}
    try {
        var ds = event.damageSource;
        if (ds != null) {
            if (typeof ds.getImmediateEntity == "function") {
                var imm = ds.getImmediateEntity();
                if (imm != null) return imm;
            }
            if (typeof ds.getTrueSource == "function") {
                var tru = ds.getTrueSource();
                if (tru != null) return tru;
            }
        }
    } catch (e1) {}
    try {
        return event.npc.getAttackTarget();
    } catch (e2) {}
    return null;
}

/** Телепорт, если набрано hitsNeeded и босс не занят кастом / не на CD. */
function tryPendingLeap(npc, attacker) {
    if (npc == null || !canRunScript(npc)) return false;
    if (isCastingBlocked(npc)) return false;
    if (!isSpellReady(npc, SPELLS.leap)) return false;

    var data = npc.getStoreddata();
    var needed = SPELLS.leap.hitsNeeded || 3;
    var hits = getInt(data, LEAP_HIT_KEY, 0);
    if (hits < needed) return false;

    var ctx = buildCastContext(npc, SPELLS.leap);
    if (attacker != null) {
        ctx.target = attacker;
    } else if (ctx.target == null || !ctx.target.isAlive()) {
        try {
            ctx.target = npc.getAttackTarget();
        } catch (e) {}
    }

    npc.say(SPELLS.leap.announce);
    teleportBoss(ctx);
    data.put(LEAP_HIT_KEY, "0");
    setSpellCooldown(npc, SPELLS.leap);
    log("grey_seer: leap after " + needed + " hits");
    return true;
}

function pickRandomTeleportPoint(npc, points) {
    if (points == null || points.length == 0) return null;
    var idx = Math.floor(Math.random() * points.length);
    if (idx >= points.length) idx = points.length - 1;
    return points[idx];
}

function teleportBoss(ctx) {
    var npc = ctx.npc;
    var world = ctx.world;
    var oldX = npc.getX();
    var oldY = npc.getY();
    var oldZ = npc.getZ();
    var point = null;
    var points = getTeleportPoints(npc);

    // Случайная точка из списка (если задан)
    if (points.length > 0) {
        point = pickRandomTeleportPoint(npc, points);
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
            var angle2 = Math.random() * Math.PI * 2;
            var dist2 = 5 + Math.random() * 5;
            point = {
                x: oldX + Math.sin(angle2) * dist2,
                y: oldY,
                z: oldZ + Math.cos(angle2) * dist2
            };
        }
    }

    // Крысы остаются на месте ухода провидца
    var ratCount = SPELLS.rat_swarm.count || RAT_SPAWN_ON_LEAP;
    var spawned = spawnRatsAt(ctx, ratCount, oldX, oldY, oldZ);
    if (spawned > 0) {
        try {
            npc.say(SPELLS.rat_swarm.announce);
        } catch (eSay) {}
        log("grey_seer: spawned " + spawned + " rats at leap origin");
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
    for (var j = 0; j < points.length; j++) {
        stored.put("tp_" + j + "_x", points[j].x);
        stored.put("tp_" + j + "_y", points[j].y);
        stored.put("tp_" + j + "_z", points[j].z);
    }
}

// =====================================================
// События NPC
// =====================================================

function ensureTimers(npc) {
    var timers = npc.getTimers();
    try {
        if (!timers.has(1)) timers.start(1, 20, true); // ИИ
        if (!timers.has(2)) timers.start(2, 20, true); // миньоны
        // VFX-таймер всегда перезапускаем — чтобы подтянуть новый интервал
        try { timers.stop(3); } catch (eStop) {}
        timers.start(3, FLAME_VFX_TIMER_TICKS, true);
    } catch (e) {
        timers.start(1, 20, true);
        timers.start(2, 20, true);
        timers.start(3, FLAME_VFX_TIMER_TICKS, true);
    }
}

function stopScriptTimers(npc) {
    try {
        var timers = npc.getTimers();
        timers.stop(1);
        timers.stop(2);
        timers.stop(3);
    } catch (e) {}
}

function init(event) {
    try {
        var npc = event.npc;
        if (npc == null || !npc.isAlive()) return;

        npc.getStoreddata().put("grey_seer_boss", 1);
        ensureTimers(npc);

        if (npc.getStoreddata().get("_inited") == 1) return;

        // Сохраняем спавн-позицию для точек телепорта по умолчанию
        npc.getStoreddata().put("spawn_x", npc.getX());
        npc.getStoreddata().put("spawn_y", npc.getY());
        npc.getStoreddata().put("spawn_z", npc.getZ());
        npc.getStoreddata().put("_inited", 1);

        log("grey_seer init OK, spells=" + SPELL_POOL.join(", ") +
            ", tp_points=" + getTeleportPoints(npc).length);
    } catch (e) {
        log("grey_seer init ERROR: " + e);
    }
}

function tick(event) {
    if (!canRunScript(event.npc)) return;

    try {
        var npc = event.npc;
        if (isWarpfireCasting(npc)) {
            // страховка между timer-тиками
            holdCastPosition(npc, npc.getStoreddata());
            return;
        }
        if (npc.getAge() % 20 != 0) return;
        if (Math.random() > 0.3) return;
        npc.getWorld().spawnParticle("minecraft:happy_villager",
            npc.getX() + (Math.random() - 0.5) * 4,
            npc.getY() + 0.5 + Math.random() * 2,
            npc.getZ() + (Math.random() - 0.5) * 4,
            0, 0.05, 0, 0, 1);
    } catch (e) {}
}

function timer(event) {
    var npc = event.npc;
    if (!canRunScript(npc)) {
        if (isBoss(npc)) {
            try { endWarpfireBreath(npc, npc.getStoreddata()); } catch (e0) {}
            try { AbilityAPI.cancel(npc); } catch (e1) {}
            stopScriptTimers(npc);
        }
        return;
    }

    if (event.id == 3) {
        try {
            if (isWarpfireCasting(npc)) {
                tickWarpfireBreath(npc, npc.getWorld());
            }
        } catch (e) {
            log("grey_seer warpfire ERROR: " + e);
        }
        return;
    }

    if (event.id == 1) {
        try {
            if (isCastingBlocked(npc)) return;

            // Если хиты уже набраны во время каста — телепорт сразу, как освободился
            tryPendingLeap(npc, npc.getAttackTarget());

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
    if (!canRunScript(event.npc)) return;

    try {
        var npc = event.npc;
        var dmg = 0;
        try {
            dmg = parseFloat(String(event.damage));
        } catch (eDmg) {
            dmg = 0;
        }
        // Считаем любой реальный удар — не зависим от source/каста/CD
        if (!(dmg > 0)) return;

        var data = npc.getStoreddata();
        var needed = SPELLS.leap.hitsNeeded || 3;
        var hits = getInt(data, LEAP_HIT_KEY, 0) + 1;
        if (hits > needed) hits = needed;
        data.put(LEAP_HIT_KEY, String(hits));
        log("grey_seer: leap hits " + hits + "/" + needed);

        var attacker = resolveDamageAttacker(event);
        tryPendingLeap(npc, attacker);
    } catch (e) {
        log("grey_seer damaged ERROR: " + e);
    }
}

function kill(event) {
    if (!canRunScript(event.npc)) return;

    try {
        var npc = event.npc;
        npc.setHealth(Math.min(npc.getHealth() + 15, npc.getMaxHealth()));
        npc.say("§cЕщё один пал!");
    } catch (e) {}
}

function targetLost(event) {
    if (!canRunScript(event.npc)) return;

    try {
        var npc = event.npc;
        if (isWarpfireCasting(npc)) {
            endWarpfireBreath(npc, npc.getStoreddata());
        }
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
        var npc = event.npc;
        var world = npc.getWorld();
        endWarpfireBreath(npc, npc.getStoreddata());
        try { AbilityAPI.cancel(npc); } catch (eCancel) {}
        stopScriptTimers(npc);
        world.broadcast("§c§lСерый Провидец повержен! Да сгинет проклятие!");

        // Убираем всех миньонов
        var types = [2, 3, 5];
        var despawned = 0;
        for (var t = 0; t < types.length; t++) {
            var list = world.getNearbyEntities(npc.getPos(), 60, types[t]);
            for (var i = 0; i < list.length; i++) {
                if (isMinion(list[i], npc)) {
                    removeMinion(list[i], npc, world);
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
    if (!canRunScript(event.npc)) return;

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
