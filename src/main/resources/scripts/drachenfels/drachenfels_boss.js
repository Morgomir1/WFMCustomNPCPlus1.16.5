/**
 * Босс: Constant Drachenfels — парный (тело + дух).
 *
 * Immortal Bond: если убит один, живой воскрешает мёртвого через REVIVE_WINDOW_TICKS,
 * если второго не убили вовремя. Воскрешения без лимита, пока жив партнёр.
 *
 * Роль: storeddata df_role = "body" | "spirit" (или по имени клона).
 * Тег: drachenfels. Clone Bank: Drachenfels Body / Spirit / Thrall.
 *
 * Механика боя — Java AbilityAPI. JS: фазы, CD, выбор скилла, связь пары, revive.
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");
var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();

var TIMER_ID = 841;
var PHASE_CHECK_ID = 842;

var CAST_INTERVAL_PHASE1 = 80;
var CAST_INTERVAL_PHASE2 = 58;
var CAST_INTERVAL_BOND = 48;

var REVIVE_WINDOW_TICKS = 240;
var REVIVE_HP_RATIO = 0.45;
var LINK_RADIUS = 48;
var PAIR_TAG = "drachenfels";

var CLONE_TAB = 1;
var CLONE_BODY = "Drachenfels Body";
var CLONE_SPIRIT = "Drachenfels Spirit";
var CLONE_THRALL = "Drachenfels Thrall";

var ROLE_KEY = "df_role";
var PARTNER_UUID_KEY = "df_partner_uuid";
var PAIR_ID_KEY = "df_pair_id";
var PARTNER_DEAD_KEY = "df_partner_dead";
var REVIVE_UNTIL_KEY = "df_revive_until";
var DEAD_X_KEY = "df_dead_x";
var DEAD_Y_KEY = "df_dead_y";
var DEAD_Z_KEY = "df_dead_z";
var DEAD_ROLE_KEY = "df_dead_role";
var DEAD_CLONE_KEY = "df_dead_clone";
var PHASE_KEY = "df_phase";
var NEXT_CAST_KEY = "df_next_cast";
var LAST_ABILITY_KEY = "df_last_ability";
var FORCED_ABILITY_KEY = "df_forced_ability";
var CD_PREFIX = "df_cd_";
var LINKED_KEY = "df_linked";

var POISON_FEAST_ID = "drachenfels_poison_feast";
var DARK_CLEAVE_ID = "drachenfels_dark_cleave";
var SOUL_REND_ID = "drachenfels_soul_rend";
var SPIRIT_BARRAGE_ID = "drachenfels_spirit_barrage";
var RAISE_THRALLS_ID = "drachenfels_raise_thralls";
var SHADOW_STEP_ID = "drachenfels_shadow_step";

var QUOTES_BODY_1 = [
    "Пир отравлен — так же, как и ваша надежда.",
    "Плоть вечна, пока живёт воля.",
    "Кланяйтесь Тьме, которую не сломить."
];
var QUOTES_BODY_2 = [
    "Вы лишь гости на моём пиру смерти!",
    "Каждый удар питает моё бессмертие!",
    "Империя падёт — я останусь."
];
var QUOTES_SPIRIT_1 = [
    "Я — имя, стёртое смертью.",
    "Дух не держит клинок — он держит судьбу.",
    "Ваши души уже в моём замке."
];
var QUOTES_SPIRIT_2 = [
    "Раздерите их души!",
    "Тело падёт — дух восстанет!",
    "Я был до ваших богов."
];
var QUOTES_BOND = [
    "Связь нерасторжима!",
    "Пока один дышит — второй вернётся!",
    "Смерть — лишь пауза."
];
var QUOTES_REVIVE = [
    "Встань. Мы ещё не закончили.",
    "Смерть отложена — по моей милости.",
    "Плоть и дух снова едины."
];

function init(event) {
    var npc = event.npc;
    var data = npc.getStoreddata();
    ensureRole(npc, data);
    if (!npc.hasTag(PAIR_TAG)) {
        npc.addTag(PAIR_TAG);
    }
    data.put(PHASE_KEY, "1");
    data.put(LAST_ABILITY_KEY, "");
    data.put(FORCED_ABILITY_KEY, "");
    data.put(PARTNER_DEAD_KEY, "0");
    data.put(REVIVE_UNTIL_KEY, "0");
    tryLinkPartner(npc);
    startTimers(npc);
}

function timer(event) {
    if (event.id == PHASE_CHECK_ID) {
        var npc = event.npc;
        if (!npc.isAlive()) return;
        tryLinkPartner(npc);
        updateBondAndPhase(npc);
        tickBondVfx(npc);
        tryCompleteRevive(npc);
        return;
    }
    if (event.id != TIMER_ID) return;

    var npc = event.npc;
    if (!npc.isAlive()) {
        AbilityAPI.cancel(npc);
        return;
    }
    if (AbilityAPI.isBusy(npc)) return;

    var world = npc.getWorld();
    var data = npc.getStoreddata();
    var now = world.getTotalTime();
    if (now < getInt(data, NEXT_CAST_KEY)) return;

    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) return;

    var phase = String(data.get(PHASE_KEY));
    if (phase.length == 0) phase = "1";
    var forced = String(data.get(FORCED_ABILITY_KEY));
    var abilityId = null;

    if (forced.length > 0 && isCooldownReady(data, now, forced)) {
        abilityId = forced;
    } else {
        abilityId = pickAbility(npc, target, phase, data, now);
    }
    if (abilityId == null) return;

    var started = AbilityAPI.start(npc, abilityId, target, buildParams(abilityId, phase, data));
    if (!started) return;

    data.put(NEXT_CAST_KEY, String(now + getCastInterval(phase)));
    data.put(LAST_ABILITY_KEY, abilityId);
    data.put(FORCED_ABILITY_KEY, "");
    data.put(CD_PREFIX + abilityId, String(now + getCooldownTicks(abilityId, phase)));

    if (Math.random() < (phase == "bond" ? 0.5 : (phase == "2" ? 0.4 : 0.28))) {
        sayQuote(npc, phase);
    }
}

function targetLost(event) {
    AbilityAPI.cancel(event.npc);
}

function died(event) {
    var npc = event.npc;
    AbilityAPI.cancel(npc);
    onPartnerDied(npc);
}

function ensureRole(npc, data) {
    var role = String(data.get(ROLE_KEY));
    if (role == "body" || role == "spirit") return;
    var name = "";
    try {
        name = String(npc.getName()).toLowerCase();
    } catch (e) {
        name = "";
    }
    if (name.indexOf("spirit") >= 0 || name.indexOf("дух") >= 0 || name.indexOf("nameless") >= 0) {
        data.put(ROLE_KEY, "spirit");
    } else {
        data.put(ROLE_KEY, "body");
    }
}

function getRole(data) {
    var role = String(data.get(ROLE_KEY));
    return role == "spirit" ? "spirit" : "body";
}

function cloneNameForRole(role) {
    return role == "spirit" ? CLONE_SPIRIT : CLONE_BODY;
}

function tryLinkPartner(npc) {
    var data = npc.getStoreddata();
    var partnerUuid = String(data.get(PARTNER_UUID_KEY));
    if (partnerUuid.length > 0) {
        var existing = findNpcByUuid(npc.getWorld(), partnerUuid);
        if (existing != null && existing.isAlive()) {
            data.put(LINKED_KEY, "1");
            ensurePairId(npc, existing);
            return;
        }
    }

    var world = npc.getWorld();
    var pos = NpcAPI.getIPos(npc.getX(), npc.getY(), npc.getZ());
    var nearby = world.getNearbyEntities(pos, LINK_RADIUS, 2);
    var best = null;
    var bestDist = LINK_RADIUS + 1;
    var myUuid = String(npc.getUUID());
    var myRole = getRole(data);

    for (var i = 0; i < nearby.length; i++) {
        var other = nearby[i];
        if (other == null || !other.isAlive()) continue;
        if (String(other.getUUID()) == myUuid) continue;
        if (!other.hasTag(PAIR_TAG)) continue;
        var od = other.getStoreddata();
        ensureRole(other, od);
        var otherRole = getRole(od);
        if (otherRole == myRole) continue;
        var d = flatDistance(npc, other);
        if (d < bestDist) {
            bestDist = d;
            best = other;
        }
    }

    if (best == null) {
        data.put(LINKED_KEY, "0");
        return;
    }

    linkPair(npc, best);
}

function linkPair(a, b) {
    var da = a.getStoreddata();
    var db = b.getStoreddata();
    da.put(PARTNER_UUID_KEY, String(b.getUUID()));
    db.put(PARTNER_UUID_KEY, String(a.getUUID()));
    da.put(LINKED_KEY, "1");
    db.put(LINKED_KEY, "1");
    ensurePairId(a, b);
}

function ensurePairId(a, b) {
    var da = a.getStoreddata();
    var db = b.getStoreddata();
    var existing = String(da.get(PAIR_ID_KEY));
    if (existing.length > 0) {
        db.put(PAIR_ID_KEY, existing);
        return;
    }
    existing = String(db.get(PAIR_ID_KEY));
    if (existing.length > 0) {
        da.put(PAIR_ID_KEY, existing);
        return;
    }
    var ua = String(a.getUUID());
    var ub = String(b.getUUID());
    var pairId = ua < ub ? ua + "_" + ub : ub + "_" + ua;
    da.put(PAIR_ID_KEY, pairId);
    db.put(PAIR_ID_KEY, pairId);
}

function onPartnerDied(deadNpc) {
    var data = deadNpc.getStoreddata();
    var partnerUuid = String(data.get(PARTNER_UUID_KEY));
    if (partnerUuid.length == 0) return;

    var world = deadNpc.getWorld();
    var partner = findNpcByUuid(world, partnerUuid);
    if (partner == null || !partner.isAlive()) {
        clearPairWorldData(world, String(data.get(PAIR_ID_KEY)));
        return;
    }

    var pairId = String(data.get(PAIR_ID_KEY));
    if (pairId.length == 0) {
        ensurePairId(deadNpc, partner);
        pairId = String(data.get(PAIR_ID_KEY));
    }

    var role = getRole(data);
    var now = world.getTotalTime();
    var pd = partner.getStoreddata();
    pd.put(PARTNER_DEAD_KEY, "1");
    pd.put(REVIVE_UNTIL_KEY, String(now + REVIVE_WINDOW_TICKS));
    pd.put(DEAD_X_KEY, String(deadNpc.getX()));
    pd.put(DEAD_Y_KEY, String(deadNpc.getY()));
    pd.put(DEAD_Z_KEY, String(deadNpc.getZ()));
    pd.put(DEAD_ROLE_KEY, role);
    pd.put(DEAD_CLONE_KEY, cloneNameForRole(role));
    pd.put(PHASE_KEY, "bond");
    pd.put(FORCED_ABILITY_KEY, getBondPressureAbility(getRole(pd)));
    pd.put(NEXT_CAST_KEY, String(now + 10));

    putPairWorldDeath(world, pairId, deadNpc, role);
    partner.say("§5§l" + QUOTES_BOND[Math.floor(Math.random() * QUOTES_BOND.length)]);
}

function getBondPressureAbility(role) {
    return role == "spirit" ? SOUL_REND_ID : POISON_FEAST_ID;
}

function putPairWorldDeath(world, pairId, deadNpc, role) {
    if (pairId.length == 0) return;
    var wd = world.getStoreddata();
    var p = "df_pair_" + pairId + "_";
    wd.put(p + "dead_x", String(deadNpc.getX()));
    wd.put(p + "dead_y", String(deadNpc.getY()));
    wd.put(p + "dead_z", String(deadNpc.getZ()));
    wd.put(p + "dead_role", role);
    wd.put(p + "dead_clone", cloneNameForRole(role));
    wd.put(p + "alive_uuid", String(deadNpc.getStoreddata().get(PARTNER_UUID_KEY)));
}

function clearPairWorldData(world, pairId) {
    if (pairId.length == 0) return;
    var wd = world.getStoreddata();
    var p = "df_pair_" + pairId + "_";
    var keys = ["dead_x", "dead_y", "dead_z", "dead_role", "dead_clone", "alive_uuid"];
    for (var i = 0; i < keys.length; i++) {
        if (wd.has(p + keys[i])) wd.remove(p + keys[i]);
    }
}

function updateBondAndPhase(npc) {
    var data = npc.getStoreddata();
    var partnerDead = String(data.get(PARTNER_DEAD_KEY)) == "1";
    if (partnerDead) {
        var until = getInt(data, REVIVE_UNTIL_KEY);
        var now = npc.getWorld().getTotalTime();
        if (until > 0 && now < until) {
            if (String(data.get(PHASE_KEY)) != "bond") {
                data.put(PHASE_KEY, "bond");
            }
            return;
        }
    }

    var maxHealth = npc.getMaxHealth();
    if (maxHealth <= 0) return;
    var ratio = npc.getHealth() / maxHealth;
    var newPhase = ratio <= 0.5 ? "2" : "1";
    var oldPhase = String(data.get(PHASE_KEY));
    if (oldPhase == "bond" && partnerDead) return;
    if (newPhase == oldPhase) return;

    data.put(PHASE_KEY, newPhase);
    if (newPhase == "2") {
        var role = getRole(data);
        data.put(FORCED_ABILITY_KEY, role == "spirit" ? SPIRIT_BARRAGE_ID : POISON_FEAST_ID);
        npc.say(role == "spirit"
            ? "§5§lБезымянный гнев пробудился!"
            : "§4§lВеликий Чародей раскрывает силу!");
    }
}

function tickBondVfx(npc) {
    var data = npc.getStoreddata();
    if (String(data.get(PARTNER_DEAD_KEY)) != "1") return;
    var now = npc.getWorld().getTotalTime();
    if (now % 10 != 0) return;

    var dx = getFloat(data, DEAD_X_KEY);
    var dy = getFloat(data, DEAD_Y_KEY);
    var dz = getFloat(data, DEAD_Z_KEY);
    var world = npc.getWorld();
    try {
        var steps = 8;
        for (var i = 0; i <= steps; i++) {
            var t = i / steps;
            var x = npc.getX() + (dx - npc.getX()) * t;
            var y = npc.getY() + 1.0 + (dy + 0.5 - npc.getY() - 1.0) * t;
            var z = npc.getZ() + (dz - npc.getZ()) * t;
            world.spawnParticle("minecraft:soul_fire_flame", x, y, z, 0, 0.02, 0, 0, 1);
            if (i % 2 == 0) {
                world.spawnParticle("minecraft:soul", x, y + 0.1, z, 0, 0.02, 0, 0.01, 1);
            }
            if (i == 0 || i == steps || i == Math.floor(steps / 2)) {
                try {
                    world.spawnParticle("wfm:fog", x, y, z, 0.004, 0.002, 0.004, 0, 1);
                } catch (fogErr) {}
            }
        }
        world.spawnParticle("minecraft:soul", dx, dy + 0.8, dz, 0.1, 0.06, 0.1, 0.02, 5);
        world.spawnParticle("minecraft:soul_fire_flame", dx, dy + 0.5, dz, 0.15, 0.08, 0.15, 0.02, 4);
        try {
            world.spawnParticle("wfm:fog", dx, dy + 0.35, dz, 0.2, 0.05, 0.2, 0, 3);
            world.spawnParticle("wfm:fog_wall", dx, dy + 0.15, dz, 0.1, 0.02, 0.1, 0, 1);
        } catch (fogErr2) {}
    } catch (e) {}
}

function tryCompleteRevive(npc) {
    var data = npc.getStoreddata();
    if (String(data.get(PARTNER_DEAD_KEY)) != "1") return;
    if (!npc.isAlive()) return;

    var world = npc.getWorld();
    var now = world.getTotalTime();
    var until = getInt(data, REVIVE_UNTIL_KEY);
    if (until <= 0 || now < until) return;

    var pairId = String(data.get(PAIR_ID_KEY));

    var x = getFloat(data, DEAD_X_KEY);
    var y = getFloat(data, DEAD_Y_KEY);
    var z = getFloat(data, DEAD_Z_KEY);
    var deadRole = String(data.get(DEAD_ROLE_KEY));
    if (deadRole != "spirit") deadRole = "body";
    var cloneName = String(data.get(DEAD_CLONE_KEY));
    if (cloneName.length == 0) cloneName = cloneNameForRole(deadRole);

    var spawned = null;
    try {
        spawned = world.spawnClone(x, y, z, CLONE_TAB, cloneName);
    } catch (e) {
        spawned = null;
    }
    if (spawned == null) {
        log("drachenfels revive failed: clone " + cloneName);
        data.put(PARTNER_DEAD_KEY, "0");
        data.put(REVIVE_UNTIL_KEY, "0");
        return;
    }

    try {
        if (!spawned.hasTag(PAIR_TAG)) spawned.addTag(PAIR_TAG);
    } catch (e2) {}

    var sd = spawned.getStoreddata();
    sd.put(ROLE_KEY, deadRole);
    sd.put(PHASE_KEY, "1");
    sd.put(PARTNER_DEAD_KEY, "0");
    sd.put(REVIVE_UNTIL_KEY, "0");
    sd.put(LAST_ABILITY_KEY, "");
    sd.put(FORCED_ABILITY_KEY, "");
    sd.put(PAIR_ID_KEY, pairId);

    try {
        var maxHp = spawned.getMaxHealth();
        if (maxHp > 0) {
            spawned.setHealth(maxHp * REVIVE_HP_RATIO);
        }
    } catch (e3) {}

    linkPair(npc, spawned);
    startTimers(spawned);

    data.put(PARTNER_DEAD_KEY, "0");
    data.put(REVIVE_UNTIL_KEY, "0");

    var hpPhase = npc.getMaxHealth() > 0 && (npc.getHealth() / npc.getMaxHealth()) <= 0.5 ? "2" : "1";
    data.put(PHASE_KEY, hpPhase);

    npc.say("§5§l" + QUOTES_REVIVE[Math.floor(Math.random() * QUOTES_REVIVE.length)]);
    try {
        world.spawnParticle("minecraft:soul_fire_flame", x, y + 1.0, z, 0.3, 0.6, 0.3, 0.05, 24);
        world.spawnParticle("minecraft:soul", x, y + 0.8, z, 0.35, 0.4, 0.35, 0.04, 16);
        try {
            world.spawnParticle("wfm:fog", x, y + 0.4, z, 0.5, 0.15, 0.5, 0, 8);
            world.spawnParticle("wfm:fog_wall", x, y + 0.2, z, 0.3, 0.08, 0.3, 0, 3);
        } catch (fogErr) {}
        world.playSoundAt(NpcAPI.getIPos(x, y, z), "minecraft:entity.wither.spawn", 0.7, 1.3);
    } catch (e4) {}
}

function pickAbility(npc, target, phase, data, now) {
    var role = getRole(data);
    var dist = flatDistance(npc, target);
    var last = String(data.get(LAST_ABILITY_KEY));

    if (phase == "bond") {
        if (role == "body") {
            if (dist < 4.5 && isCooldownReady(data, now, DARK_CLEAVE_ID)) return DARK_CLEAVE_ID;
            if (isCooldownReady(data, now, POISON_FEAST_ID)) return POISON_FEAST_ID;
            if (isCooldownReady(data, now, SHADOW_STEP_ID)) return SHADOW_STEP_ID;
            if (isCooldownReady(data, now, RAISE_THRALLS_ID)) return RAISE_THRALLS_ID;
        } else {
            if (isCooldownReady(data, now, SOUL_REND_ID)) return SOUL_REND_ID;
            if (dist > 5 && isCooldownReady(data, now, SPIRIT_BARRAGE_ID)) return SPIRIT_BARRAGE_ID;
            if (isCooldownReady(data, now, RAISE_THRALLS_ID)) return RAISE_THRALLS_ID;
            if (isCooldownReady(data, now, SHADOW_STEP_ID)) return SHADOW_STEP_ID;
        }
        return null;
    }

    if (role == "body") {
        if (phase == "2" && dist < 5.5 && isCooldownReady(data, now, POISON_FEAST_ID) && Math.random() < 0.35) {
            return POISON_FEAST_ID;
        }
        if (dist < 4.0 && isCooldownReady(data, now, DARK_CLEAVE_ID)) {
            if (last != DARK_CLEAVE_ID || Math.random() < 0.55) return DARK_CLEAVE_ID;
        }
        if (dist > 7.0 && isCooldownReady(data, now, SHADOW_STEP_ID)) return SHADOW_STEP_ID;
        if (phase == "2" && isCooldownReady(data, now, RAISE_THRALLS_ID) && Math.random() < 0.3) {
            return RAISE_THRALLS_ID;
        }
        if (dist < 6.5 && isCooldownReady(data, now, POISON_FEAST_ID)) return POISON_FEAST_ID;
        if (isCooldownReady(data, now, DARK_CLEAVE_ID)) return DARK_CLEAVE_ID;
        if (isCooldownReady(data, now, SHADOW_STEP_ID)) return SHADOW_STEP_ID;
        if (phase == "2" && isCooldownReady(data, now, RAISE_THRALLS_ID)) return RAISE_THRALLS_ID;
        return null;
    }

    // spirit
    if (phase == "2" && isCooldownReady(data, now, SPIRIT_BARRAGE_ID) && Math.random() < 0.32) {
        return SPIRIT_BARRAGE_ID;
    }
    if (dist < 7.0 && isCooldownReady(data, now, SOUL_REND_ID)) return SOUL_REND_ID;
    if (dist > 6.0 && isCooldownReady(data, now, SPIRIT_BARRAGE_ID)) return SPIRIT_BARRAGE_ID;
    if (dist < 3.5 && isCooldownReady(data, now, SHADOW_STEP_ID)) return SHADOW_STEP_ID;
    if (phase == "2" && isCooldownReady(data, now, RAISE_THRALLS_ID) && Math.random() < 0.28) {
        return RAISE_THRALLS_ID;
    }
    if (isCooldownReady(data, now, SOUL_REND_ID)) return SOUL_REND_ID;
    if (isCooldownReady(data, now, SPIRIT_BARRAGE_ID)) return SPIRIT_BARRAGE_ID;
    if (isCooldownReady(data, now, SHADOW_STEP_ID)) return SHADOW_STEP_ID;
    if (phase == "2" && isCooldownReady(data, now, RAISE_THRALLS_ID)) return RAISE_THRALLS_ID;
    return null;
}

function buildParams(abilityId, phase, data) {
    var bond = phase == "bond";
    var p2 = phase == "2" || bond;

    if (abilityId == POISON_FEAST_ID) {
        return AbilityAPI.params(
            "damage", p2 ? 17.0 : 14.0,
            "radius", p2 ? 5.8 : 5.0,
            "knockback", 0.95,
            "knockbackY", 0.28,
            "effectType", "poison",
            "effectDuration", p2 ? 100 : 80,
            "effectAmplifier", p2 ? 1 : 1,
            "chargeTicks", bond ? 10 : (p2 ? 11 : 14)
        );
    }
    if (abilityId == DARK_CLEAVE_ID) {
        return AbilityAPI.params(
            "damage", p2 ? 16.0 : 13.0,
            "distance", p2 ? 6.2 : 5.5,
            "chargeTicks", bond ? 6 : (p2 ? 7 : 8),
            "activeTicks", 5,
            "radius", p2 ? 2.8 : 2.4,
            "coneHalfAngle", 65.0,
            "knockback", 1.5,
            "knockbackY", 0.32
        );
    }
    if (abilityId == SOUL_REND_ID) {
        return AbilityAPI.params(
            "damage", p2 ? 15.0 : 12.0,
            "radius", p2 ? 7.0 : 6.0,
            "coneHalfAngle", 42.0,
            "knockback", 0.75,
            "knockbackY", 0.22,
            "effectType", "wither",
            "effectDuration", p2 ? 80 : 60,
            "effectAmplifier", 0,
            "chargeTicks", bond ? 8 : (p2 ? 10 : 12)
        );
    }
    if (abilityId == SPIRIT_BARRAGE_ID) {
        return AbilityAPI.params(
            "damage", p2 ? 9.0 : 7.0,
            "shots", p2 ? 5 : 4,
            "distance", 15.0,
            "hitRadius", 1.9,
            "chargeTicks", p2 ? 8 : 10,
            "activeTicks", p2 ? 18 : 16,
            "knockback", 0.55,
            "knockbackY", 0.15
        );
    }
    if (abilityId == RAISE_THRALLS_ID) {
        return AbilityAPI.params(
            "chargeTicks", bond ? 8 : 12,
            "activeTicks", 16,
            "summonCount", bond ? 3 : 2,
            "summonRadius", 3.5,
            "maxSummonedNearBoss", 4,
            "cloneTab", CLONE_TAB,
            "cloneName", CLONE_THRALL,
            "radius", 4.0,
            "effectType", "slowness",
            "effectDuration", 40,
            "effectAmplifier", 0
        );
    }
    if (abilityId == SHADOW_STEP_ID) {
        return AbilityAPI.params(
            "distance", p2 ? 12.0 : 10.0,
            "chargeTicks", bond ? 4 : 6,
            "activeTicks", 5,
            "damage", p2 ? 10.0 : 8.0,
            "knockback", 1.15,
            "knockbackY", 0.28,
            "hitRadius", 1.6
        );
    }
    return null;
}

function startTimers(npc) {
    var timers = npc.getTimers();
    if (timers == null) return;
    if (typeof timers.forceStart == "function") {
        timers.forceStart(TIMER_ID, 1, true);
        timers.forceStart(PHASE_CHECK_ID, 20, true);
    } else {
        timers.start(TIMER_ID, 1, true);
        timers.start(PHASE_CHECK_ID, 20, true);
    }
}

function getCastInterval(phase) {
    if (phase == "bond") return CAST_INTERVAL_BOND;
    if (phase == "2") return CAST_INTERVAL_PHASE2;
    return CAST_INTERVAL_PHASE1;
}

function getCooldownTicks(abilityId, phase) {
    var bond = phase == "bond";
    var p2 = phase == "2" || bond;
    if (abilityId == POISON_FEAST_ID) return bond ? 90 : (p2 ? 120 : 150);
    if (abilityId == DARK_CLEAVE_ID) return bond ? 48 : (p2 ? 58 : 72);
    if (abilityId == SOUL_REND_ID) return bond ? 70 : (p2 ? 85 : 105);
    if (abilityId == SPIRIT_BARRAGE_ID) return p2 ? 100 : 130;
    if (abilityId == RAISE_THRALLS_ID) return bond ? 140 : 180;
    if (abilityId == SHADOW_STEP_ID) return bond ? 55 : (p2 ? 65 : 80);
    return 80;
}

function isCooldownReady(data, now, abilityId) {
    var key = CD_PREFIX + abilityId;
    if (!data.has(key)) return true;
    return now >= getInt(data, key);
}

function sayQuote(npc, phase) {
    var data = npc.getStoreddata();
    var role = getRole(data);
    var quotes;
    if (phase == "bond") {
        quotes = QUOTES_BOND;
    } else if (role == "spirit") {
        quotes = phase == "2" ? QUOTES_SPIRIT_2 : QUOTES_SPIRIT_1;
    } else {
        quotes = phase == "2" ? QUOTES_BODY_2 : QUOTES_BODY_1;
    }
    var idx = Math.floor(Math.random() * quotes.length);
    npc.say("§7" + quotes[idx]);
}

function findNpcByUuid(world, uuid) {
    if (uuid == null || String(uuid).length == 0) return null;
    var want = String(uuid);
    var all = world.getAllEntities(2);
    for (var i = 0; i < all.length; i++) {
        if (String(all[i].getUUID()) == want) return all[i];
    }
    return null;
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

function getFloat(data, key) {
    if (!data.has(key)) return 0;
    return parseFloat(String(data.get(key)));
}
