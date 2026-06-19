// =====================================================
// Grey Seer — ядро ИИ босса со случайными заклинаниями
// CustomNPC+ JS-скрипт для WFM 1.16.5
//
// ВАЖНО: скрипт только на боссе! Клон "rat" — без скрипта.
// Clone Bank: tab=1, name="rat"
//
// Новое заклинание: добавить в SPELLS и в SPELL_POOL.
// =====================================================

var RAT_LIFETIME = 200;
var MAX_RATS = 12;
var CLONE_TAB = 1;
var CLONE_NAME = "rat";
var POOP_ITEM = "minecraft:cocoa_beans";
var POOP_VOLLEY_MIN = 5;
var POOP_VOLLEY_MAX = 10;
var POOP_HIT_DELAY_TICKS = 12;

// --- Реестр заклинаний ---
// weight > 0 — участвует в случайном выборе
// weight = 0 — только принудительный каст (реакции)
var SPELLS = {
    rat_wave: {
        id: "rat_wave",
        weight: 10,
        cooldown: 240,
        enrageCooldown: 100,
        announce: "§cКрысы, вперёд!",
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
    rat_burst: {
        id: "rat_burst",
        weight: 0,
        cooldown: 160,
        announce: "§cЯрость!",
        count: 3,
        canCast: function(ctx) {
            return ctx.target != null
                && ctx.target.isAlive()
                && ctx.minions < MAX_RATS;
        },
        cast: function(ctx) {
            return spawnRatsAround(ctx, ctx.spell.count);
        }
    },
    teleport_poop: {
        id: "teleport_poop",
        weight: 6,
        cooldown: 320,
        enrageCooldown: 180,
        announce: "§8*хлюп-хлюп*",
        canCast: function(ctx) {
            return ctx.target != null && ctx.target.isAlive();
        },
        cast: function(ctx) {
            if (!teleportBossRandom(ctx)) return 0;
            var rat = spawnRat(ctx, ctx.npc.getX(), ctx.npc.getY() + 0.5, ctx.npc.getZ());
            var volley = castPoopVolley(ctx, POOP_VOLLEY_MIN, POOP_VOLLEY_MAX);
            return (rat != null ? 1 : 0) + volley;
        }
    },
    poop_volley: {
        id: "poop_volley",
        weight: 8,
        cooldown: 200,
        enrageCooldown: 120,
        announce: "§8Волна говна!",
        canCast: function(ctx) {
            return ctx.target != null && ctx.target.isAlive();
        },
        cast: function(ctx) {
            return castPoopVolley(ctx, POOP_VOLLEY_MIN, POOP_VOLLEY_MAX);
        }
    }
};

// Заклинания, из которых босс выбирает случайное
var SPELL_POOL = ["rat_wave", "teleport_poop", "poop_volley"];

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
        minions: countMinions(world, npc.getPos()),
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

function countMinions(world, bossPos) {
    var count = 0;
    var types = [2, 3, 5];
    for (var t = 0; t < types.length; t++) {
        var list = world.getNearbyEntities(bossPos, 50, types[t]);
        for (var i = 0; i < list.length; i++) {
            if (isMinion(list[i])) count++;
        }
    }
    return count;
}

function isMinion(entity) {
    try {
        if (entity.getStoreddata().get("rat_minion") == 1) return true;
    } catch (e) {}
    try {
        return entity.hasTag("rat_minion");
    } catch (e2) {}
    return false;
}

function markMinion(rat, world) {
    rat.addTag("rat_minion");
    rat.getStoreddata().put("rat_minion", 1);
    rat.getStoreddata().put("spawn_tick", getWorldTime(world));
}

function removeMinion(entity, world) {
    try {
        world.spawnParticle("minecraft:poof",
            entity.getX(), entity.getY() + 0.3, entity.getZ(),
            0, 0, 0, 0, 3);
    } catch (e) {}
    try { entity.removeTag("rat_minion"); } catch (e2) {}
    try {
        entity.getStoreddata().remove("rat_minion");
        entity.getStoreddata().remove("spawn_tick");
    } catch (e3) {}
    try {
        entity.kill();
    } catch (e4) {
        try { entity.despawn(); } catch (e5) {}
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

    markMinion(rat, ctx.world);
    try {
        rat.setAttackTarget(ctx.target);
    } catch (e3) {}

    try {
        ctx.world.spawnParticle("minecraft:smoke", sx, sy + 0.5, sz, 0, 0, 0, 0, 5);
    } catch (e4) {}

    return rat;
}

function spawnRatsAround(ctx, count) {
    cleanupMinions(ctx.world, ctx.npc.getPos());

    var active = countMinions(ctx.world, ctx.npc.getPos());
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

// =====================================================
// Волна говна (снаряды + дебаффы)
// =====================================================

function randomTicks(minSec, maxSec) {
    return Math.floor((minSec + Math.random() * (maxSec - minSec)) * 20);
}

function poopPendingKey(uuid) {
    return "poop_pending_" + uuid;
}

function poopPendingTickKey(uuid) {
    return "poop_pending_tick_" + uuid;
}

function isPlayerEntity(entity) {
    try {
        return entity != null && entity.getType() == 1;
    } catch (e) {
        return false;
    }
}

function applyPoopHit(player) {
    var data = player.getStoreddata();
    var hits = data.get("poop_hits");
    if (hits == null) hits = 0;
    hits = hits + 1;
    data.put("poop_hits", hits);

    try {
        player.addPotionEffect(PotionEffectType_BLINDNESS, randomTicks(1, 2), 0, true);
    } catch (e) {}

    if (hits >= 5 && hits % 5 == 0) {
        try {
            player.addPotionEffect(PotionEffectType_POISON, randomTicks(15, 20), 0, false);
            player.addPotionEffect(PotionEffectType_WEAKNESS, randomTicks(15, 20), 0, false);
        } catch (e2) {}
    }

    if (hits >= 15 && hits % 15 == 0) {
        try {
            player.addPotionEffect(PotionEffectType_SLOWNESS, randomTicks(5, 10), 0, false);
        } catch (e3) {}
    }

    log("grey_seer: poop hit #" + hits + " on " + player.getName());
}

function queuePoopHits(boss, target, count) {
    if (!isPlayerEntity(target)) return;
    var uuid = target.getUUID();
    var key = poopPendingKey(uuid);
    var tickKey = poopPendingTickKey(uuid);
    var pending = boss.getTempdata().get(key);
    if (pending == null) pending = 0;
    boss.getTempdata().put(key, pending + count);
    boss.getTempdata().put(tickKey, getWorldTime(boss.getWorld()) + POOP_HIT_DELAY_TICKS);
}

function resolvePoopHit(boss, player, pending) {
    var resolved = 0;
    for (var i = 0; i < pending; i++) {
        if (Math.random() < 0.85) {
            applyPoopHit(player);
            resolved++;
        }
    }
    return resolved;
}

function processPendingPoopHits(npc) {
    var world = npc.getWorld();
    var players = world.getAllPlayers();
    var now = getWorldTime(world);
    var total = 0;

    for (var p = 0; p < players.length; p++) {
        var player = players[p];
        if (player == null || !player.isAlive()) continue;

        var uuid = player.getUUID();
        var pending = npc.getTempdata().get(poopPendingKey(uuid));
        if (pending == null || pending <= 0) continue;

        var dueAt = npc.getTempdata().get(poopPendingTickKey(uuid));
        if (dueAt == null) dueAt = 0;
        if (now < dueAt) continue;

        total += resolvePoopHit(npc, player, pending);
        npc.getTempdata().put(poopPendingKey(uuid), 0);
    }
    return total;
}

function castPoopVolley(ctx, minCount, maxCount) {
    var target = ctx.target;
    if (target == null || !target.isAlive()) return 0;

    var count = minCount + Math.floor(Math.random() * (maxCount - minCount + 1));
    var item = ctx.world.createItem(POOP_ITEM, 1);
    var npc = ctx.npc;
    var world = ctx.world;

    for (var i = 0; i < count; i++) {
        var spread = (Math.random() - 0.5) * 2.0;
        var aimY = target.getY() + 0.8 + Math.random() * 0.8;
        try {
            npc.shootItem(
                target.getX() + spread,
                aimY,
                target.getZ() + spread,
                item,
                6 + Math.floor(Math.random() * 4)
            );
        } catch (e) {
            try {
                npc.shootItem(target, item, 8);
            } catch (e2) {}
        }
    }

    queuePoopHits(npc, target, count);

    try {
        world.spawnParticle("minecraft:spit",
            npc.getX(), npc.getY() + 1.2, npc.getZ(),
            0.4, 0.2, 0.4, 0.05, 12);
        world.spawnParticle("minecraft:falling_spore_blossom",
            target.getX(), target.getY() + 1.5, target.getZ(),
            0.6, 0.3, 0.6, 0.02, 8);
    } catch (e3) {}

    return count;
}

function teleportBossRandom(ctx) {
    var target = ctx.target;
    var npc = ctx.npc;
    var world = ctx.world;
    if (target == null || !target.isAlive()) return false;

    var angle = Math.random() * Math.PI * 2;
    var dist = 5 + Math.random() * 5;
    var tx = target.getX() + Math.sin(angle) * dist;
    var tz = target.getZ() + Math.cos(angle) * dist;
    var ty = target.getY();

    npc.setPosition(tx, ty, tz);

    try {
        world.spawnParticle("minecraft:portal", tx, ty + 1, tz, 0.3, 0.5, 0.3, 0.1, 25);
        world.spawnParticle("minecraft:smoke", tx, ty + 0.2, tz, 0.2, 0.1, 0.2, 0.02, 10);
        world.playSoundAt(npc.getPos(), "minecraft:entity.enderman.teleport", 1.0, 0.8);
    } catch (e) {}

    try {
        npc.setAttackTarget(target);
    } catch (e2) {}

    return true;
}

function cleanupMinions(world, bossPos) {
    var now = getWorldTime(world);
    var cleared = 0;
    var types = [2, 3, 5];

    for (var t = 0; t < types.length; t++) {
        var list = world.getNearbyEntities(bossPos, 50, types[t]);
        for (var i = 0; i < list.length; i++) {
            var ent = list[i];
            if (!isMinion(ent)) continue;

            var spawnTick = 0;
            try {
                spawnTick = ent.getStoreddata().get("spawn_tick");
                if (spawnTick == null) spawnTick = 0;
            } catch (e) {}

            if (!ent.isAlive() || (spawnTick > 0 && (now - spawnTick) >= RAT_LIFETIME)) {
                removeMinion(ent, world);
                cleared++;
            }
        }
    }
    return cleared;
}

// =====================================================
// События NPC
// =====================================================

function init(event) {
    try {
        var npc = event.npc;
        npc.getStoreddata().put("grey_seer_boss", 1);

        if (npc.getStoreddata().get("_inited") == 1) return;

        npc.setName("§c§lПовелитель Крыс");
        npc.getStats().setMaxHealth(300);
        npc.getStats().setResistance(0, 8);

        npc.getTimers().start(1, 20, true); // ИИ: выбор заклинания
        npc.getTimers().start(2, 20, true); // деспавн миньонов
        npc.getTimers().start(3, 5, true);  // обработка попаданий говна
        npc.getStoreddata().put("_inited", 1);

        log("grey_seer init OK, spells=" + SPELL_POOL.join(", "));
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
        npc.getWorld().spawnParticle("minecraft:smoke",
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
            castRandomSpell(npc);
        } catch (e) {
            log("grey_seer AI ERROR: " + e);
        }
        return;
    }

    if (event.id == 2) {
        try {
            var cleared = cleanupMinions(npc.getWorld(), npc.getPos());
            if (cleared > 0) {
                log("grey_seer: despawned " + cleared + " rats");
            }
        } catch (e) {
            log("grey_seer cleanup ERROR: " + e);
        }
        return;
    }

    if (event.id == 3) {
        try {
            processPendingPoopHits(npc);
        } catch (e) {
            log("grey_seer poop hits ERROR: " + e);
        }
    }
}

function projectileImpact(event) {
    if (!isBoss(event.npc)) return;

    try {
        var target = null;
        if (event.entity != null) target = event.entity;
        else if (event.target != null) target = event.target;

        if (!isPlayerEntity(target)) return;

        applyPoopHit(target);

        var key = poopPendingKey(target.getUUID());
        var pending = event.npc.getTempdata().get(key);
        if (pending != null && pending > 0) {
            event.npc.getTempdata().put(key, pending - 1);
        }
    } catch (e) {
        log("grey_seer projectileImpact ERROR: " + e);
    }
}

function damaged(event) {
    if (!isBoss(event.npc)) return;

    try {
        if (Math.random() > 0.25) return;
        if (event.source == null || !event.source.isAlive()) return;

        var npc = event.npc;
        if (!isSpellReady(npc, SPELLS.rat_burst)) return;

        var ctx = buildCastContext(npc, SPELLS.rat_burst);
        ctx.target = event.source;
        if (!SPELLS.rat_burst.canCast(ctx)) return;

        npc.say(SPELLS.rat_burst.announce);
        var spawned = SPELLS.rat_burst.cast(ctx);
        if (spawned > 0) {
            setSpellCooldown(npc, SPELLS.rat_burst);
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

function died(event) {
    if (!isBoss(event.npc)) return;

    try {
        var world = event.npc.getWorld();
        world.broadcast("§c§lПовелитель Крыс повержен!");

        var types = [2, 3, 5];
        var despawned = 0;
        for (var t = 0; t < types.length; t++) {
            var list = world.getNearbyEntities(event.npc.getPos(), 60, types[t]);
            for (var i = 0; i < list.length; i++) {
                if (isMinion(list[i])) {
                    removeMinion(list[i], world);
                    despawned++;
                }
            }
        }
        log("grey_seer died: despawned " + despawned + " rats");
    } catch (e) {
        log("grey_seer died ERROR: " + e);
    }
}
