/**
 * Вампир: кровавый рывок + пассивки крови.
 *
 * Пассивки:
 *   — вампиризм: +25 HP с каждой автоатаки;
 *   — чем больше рядом игроков с потерянным HP, тем быстрее бег
 *     и тем чаще автоатака (меньше delay setAttackSpeed);
 *   — при 50% и 10% HP (по разу за бой): невидимость, стоп,
 *     5 мышей; удар мыши хилит вампира на 10. Все мыши убиты — снова бой.
 *
 * Абилки — Java AbilityAPI:
 *   vampire_blood_dash  — homing dash без уворота + лужи крови;
 *   vampire_blood_slash — удар конусом мечом (как wh_flaming_strike).
 *
 * GUI Clone tab 1: имя "Vampire Crimson Bat".
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");
var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();
var Effects = Java.type("net.minecraft.potion.Effects");
var EffectInstance = Java.type("net.minecraft.potion.EffectInstance");

var TIMER_ID = 721;
var CAST_INTERVAL_TICKS = 50;
var DASH_MIN_RANGE = 3.0;
var DASH_MAX_RANGE = 16.0;
var SLASH_MAX_RANGE = 5.5;
var NEXT_CAST_KEY = "vbd_next_cast";
var LAST_ABILITY_KEY = "vbd_last";
var CD_PREFIX = "vbd_cd_";

var DASH_ID = "vampire_blood_dash";
var SLASH_ID = "vampire_blood_slash";

var COOLDOWNS = {};
COOLDOWNS[DASH_ID] = 80;
COOLDOWNS[SLASH_ID] = 50;

var ENTITY_PLAYER = 1;
var ENTITY_NPC = 2;
var GAMEMODE_SPECTATOR = 3;
var RETALIATE_REVENGE = 0;
var RETALIATE_NONE = 3;
var VISIBLE_NORMAL = 0;
var VISIBLE_HIDDEN = 1;

var PASSIVE_RADIUS = 12.0;
var MAX_WOUNDED = 4;
var SPEED_PER_WOUNDED = 1;
var WALK_SPEED_CAP = 10;
var ATK_REDUCE_PER = 3;
var ATK_SPEED_MIN = 5;

var BASE_SPEED_KEY = "vbd_base_speed";
var BASE_ATK_KEY = "vbd_base_atk";
var LAST_WOUNDED_KEY = "vbd_wounded";
var MELEE_HEAL = 25;

var HIDING_KEY = "vbd_hiding";
var USED_50_KEY = "vbd_mice_50";
var USED_10_KEY = "vbd_mice_10";
var HIDE_START_KEY = "vbd_hide_start";
var HIDE_X_KEY = "vbd_hide_x";
var HIDE_Y_KEY = "vbd_hide_y";
var HIDE_Z_KEY = "vbd_hide_z";
var SAVED_VISIBLE_KEY = "vbd_saved_vis";
var SAVED_RETALIATE_KEY = "vbd_saved_ret";
var BATS_STARTED_KEY = "vbd_bats_started";

var BAT_TAG = "vampire_crimson_bat";
var BAT_OWNER_KEY = "vcl_owner";
var BAT_COUNT = 5;
var BAT_HEAL = 10.0;
var BAT_CLONE_TAB = 1;
var BAT_CLONE_NAME = "Vampire Crimson Bat";
var BAT_COUNT_RADIUS = 24;
var HIDE_GRACE_TICKS = 30;
var HIDE_MAX_TICKS = 600;

function init(event) {
    var npc = event.npc;
    var data = npc.getStoreddata();
    storeBaseStats(data, npc);
    data.put(USED_50_KEY, "0");
    data.put(USED_10_KEY, "0");
    if (String(data.get(HIDING_KEY)) == "1") {
        endMiceHide(npc, data);
    }
    startTimer(npc);
}

function timer(event) {
    if (event.id != TIMER_ID) return;

    var npc = event.npc;
    if (!npc.isAlive()) {
        AbilityAPI.cancel(npc);
        restoreBaseStats(npc);
        return;
    }

    var data = npc.getStoreddata();
    if (String(data.get(HIDING_KEY)) == "1") {
        tickMiceHide(npc, data);
        return;
    }

    tryStartMiceThreshold(npc, data);

    if (String(data.get(HIDING_KEY)) == "1") return;

    updateBloodFrenzy(npc);

    if (AbilityAPI.isBusy(npc)) return;

    var now = npc.getWorld().getTotalTime();
    if (now < getInt(data, NEXT_CAST_KEY)) return;

    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) return;

    var abilityId = pickAbility(npc, target, data, now);
    if (abilityId == null) return;

    var started = AbilityAPI.start(npc, abilityId, target, buildParams(abilityId));
    if (!started) return;

    data.put(LAST_ABILITY_KEY, abilityId);
    data.put(CD_PREFIX + abilityId, String(now + getCooldown(abilityId)));
    data.put(NEXT_CAST_KEY, String(now + CAST_INTERVAL_TICKS));
}

function pickAbility(npc, target, data, now) {
    var dist = distance(npc, target);
    var last = String(data.get(LAST_ABILITY_KEY));

    if (dist < SLASH_MAX_RANGE && isCooldownReady(data, now, SLASH_ID) && last != SLASH_ID) {
        return SLASH_ID;
    }
    if (dist >= DASH_MIN_RANGE && dist <= DASH_MAX_RANGE && isCooldownReady(data, now, DASH_ID) && last != DASH_ID) {
        return DASH_ID;
    }
    if (dist < SLASH_MAX_RANGE && isCooldownReady(data, now, SLASH_ID)) return SLASH_ID;
    if (dist >= DASH_MIN_RANGE && dist <= DASH_MAX_RANGE && isCooldownReady(data, now, DASH_ID)) return DASH_ID;
    return null;
}

function buildParams(abilityId) {
    if (abilityId == SLASH_ID) {
        return AbilityAPI.params(
            "telegraphColor", 0xC0FF3030,
            "damage", 14.0,
            "chargeTicks", 20,
            "distance", 4.5,
            "radius", 1.35,
            "coneHalfAngle", 38.0,
            "knockback", 0.9,
            "knockbackY", 0.2
        );
    }
    return AbilityAPI.params(
        "telegraphColor", 0xC0FF3030,
        "damage", 14.0,
        "chargeTicks", 18,
        "activeTicks", 6,
        "hitRadius", 2.2,
        "radius", 1.8,
        "zoneTicks", 50,
        "trailTicks", 160,
        "puddleInterval", 8,
        "healPerTick", 15.0,
        "zoneColor", 0xC0B01018
    );
}

function isCooldownReady(data, now, abilityId) {
    return now >= getInt(data, CD_PREFIX + abilityId);
}

function getCooldown(abilityId) {
    if (COOLDOWNS[abilityId] != null) return COOLDOWNS[abilityId];
    return CAST_INTERVAL_TICKS;
}

function meleeAttack(event) {
    var npc = event.npc;
    if (npc == null || !npc.isAlive()) return;
    if (String(npc.getStoreddata().get(HIDING_KEY)) == "1") return;

    var maxHp = npc.getMaxHealth();
    var cur = npc.getHealth();
    if (maxHp <= 0 || cur >= maxHp) return;

    var next = cur + MELEE_HEAL;
    if (next > maxHp) next = maxHp;
    npc.setHealth(next);

    try {
        var world = npc.getWorld();
        var x = npc.getX();
        var y = npc.getY() + 1.0;
        var z = npc.getZ();
        world.spawnParticle("minecraft:heart", x, y, z, 0.25, 0.35, 0.25, 0, 3);
        world.spawnParticle("minecraft:entity_effect", x, y, z, 0.9, 0.08, 0.08, 0, 4);
        world.playSoundAt(NpcAPI.getIPos(x, y, z), "minecraft:entity.generic.drink", 0.55, 0.85);
    } catch (e) {}
}

function targetLost(event) {
    if (String(event.npc.getStoreddata().get(HIDING_KEY)) == "1") return;
    AbilityAPI.cancel(event.npc);
}

function died(event) {
    AbilityAPI.cancel(event.npc);
    endMiceHide(event.npc, event.npc.getStoreddata());
    restoreBaseStats(event.npc);
}

function tryStartMiceThreshold(npc, data) {
    var maxHp = npc.getMaxHealth();
    if (maxHp <= 0) return;
    var ratio = npc.getHealth() / maxHp;

    if (ratio <= 0.5 && String(data.get(USED_50_KEY)) != "1") {
        if (beginMiceHide(npc, data, USED_50_KEY)) return;
    }
    if (ratio <= 0.1 && String(data.get(USED_10_KEY)) != "1") {
        beginMiceHide(npc, data, USED_10_KEY);
    }
}

function beginMiceHide(npc, data, flagKey) {
    var target = findCastTarget(npc);
    if (target == null) return false;

    AbilityAPI.cancel(npc);
    data.put(flagKey, "1");
    data.put(HIDING_KEY, "1");
    data.put(BATS_STARTED_KEY, "0");
    data.put(HIDE_START_KEY, String(npc.getWorld().getTotalTime()));
    data.put(HIDE_X_KEY, String(npc.getX()));
    data.put(HIDE_Y_KEY, String(npc.getY()));
    data.put(HIDE_Z_KEY, String(npc.getZ()));

    try {
        var ai = npc.getAi();
        if (!data.has(SAVED_RETALIATE_KEY)) {
            data.put(SAVED_RETALIATE_KEY, String(ai.getRetaliateType()));
        }
        ai.setRetaliateType(RETALIATE_NONE);
        ai.setWalkingSpeed(0);
    } catch (e) {}

    applyInvisibility(npc, data);
    holdHideSpot(npc, data);
    spawnBloodMice(npc, target, data);

    try {
        var world = npc.getWorld();
        var x = npc.getX();
        var y = npc.getY() + 0.6;
        var z = npc.getZ();
        world.spawnParticle("minecraft:large_smoke", x, y, z, 0.6, 0.5, 0.6, 0.04, 18);
        world.spawnParticle("minecraft:crimson_spore", x, y, z, 0.5, 0.4, 0.5, 0.03, 16);
        world.playSoundAt(NpcAPI.getIPos(x, y, z), "minecraft:entity.bat.takeoff", 1.0, 0.7);
        world.playSoundAt(NpcAPI.getIPos(x, y, z), "minecraft:entity.illusioner.mirror_move", 0.8, 0.85);
    } catch (e2) {}
    return true;
}

function tickMiceHide(npc, data) {
    holdHideSpot(npc, data);
    applyInvisibility(npc, data);
    try {
        var ai = npc.getAi();
        ai.setRetaliateType(RETALIATE_NONE);
        ai.setWalkingSpeed(0);
    } catch (e) {}

    var now = npc.getWorld().getTotalTime();
    var started = getInt(data, HIDE_START_KEY);
    var elapsed = now - started;

    if (String(data.get(BATS_STARTED_KEY)) != "1") {
        var target = findCastTarget(npc);
        if (target != null) spawnBloodMice(npc, target, data);
    }

    if (elapsed < HIDE_GRACE_TICKS) return;

    var bats = countOwnedBats(npc);
    if (bats <= 0 || elapsed >= HIDE_MAX_TICKS) {
        endMiceHide(npc, data);
    }
}

function spawnBloodMice(npc, target, data) {
    if (AbilityAPI.isBusy(npc)) {
        if (AbilityAPI.getActiveId(npc) == "vampire_crimson_bats") {
            data.put(BATS_STARTED_KEY, "1");
        }
        return;
    }
    var started = AbilityAPI.start(npc, "vampire_crimson_bats", target, AbilityAPI.params(
        "telegraphColor", 0xC0FF3030,
        "chargeTicks", 6,
        "radius", 2.5,
        "summonCount", BAT_COUNT,
        "summonRadius", 1.6,
        "maxSummonedNearBoss", BAT_COUNT,
        "cloneTab", BAT_CLONE_TAB,
        "cloneName", BAT_CLONE_NAME,
        "lifeStealPerHit", BAT_HEAL
    ));
    if (started) data.put(BATS_STARTED_KEY, "1");
}

function endMiceHide(npc, data) {
    if (data == null) return;
    data.put(HIDING_KEY, "0");
    data.put(BATS_STARTED_KEY, "0");
    clearInvisibility(npc, data);
    try {
        var ai = npc.getAi();
        var ret = data.has(SAVED_RETALIATE_KEY) ? getInt(data, SAVED_RETALIATE_KEY) : RETALIATE_REVENGE;
        ai.setRetaliateType(ret);
        ai.setWalkingSpeed(getBaseSpeed(data, npc));
    } catch (e) {}
    try {
        var world = npc.getWorld();
        var x = npc.getX();
        var y = npc.getY() + 1.0;
        var z = npc.getZ();
        world.spawnParticle("minecraft:entity_effect", x, y, z, 0.9, 0.1, 0.1, 0, 10);
        world.spawnParticle("minecraft:smoke", x, y, z, 0.4, 0.4, 0.4, 0.02, 12);
        world.playSoundAt(NpcAPI.getIPos(x, y, z), "minecraft:entity.illusioner.mirror_move", 0.9, 1.15);
    } catch (e2) {}
}

function applyInvisibility(npc, data) {
    try {
        var display = npc.getDisplay();
        if (!data.has(SAVED_VISIBLE_KEY)) {
            data.put(SAVED_VISIBLE_KEY, String(display.getVisible()));
        }
        display.setVisible(VISIBLE_HIDDEN);
    } catch (e) {}
    try {
        var mc = npc.getMCEntity();
        if (mc != null) {
            mc.addEffect(new EffectInstance(Effects.INVISIBILITY, 200, 0, false, false));
        }
    } catch (e2) {}
}

function clearInvisibility(npc, data) {
    try {
        var display = npc.getDisplay();
        var vis = data.has(SAVED_VISIBLE_KEY) ? getInt(data, SAVED_VISIBLE_KEY) : VISIBLE_NORMAL;
        display.setVisible(vis);
    } catch (e) {}
    try {
        var mc = npc.getMCEntity();
        if (mc != null) mc.removeEffect(Effects.INVISIBILITY);
    } catch (e2) {}
}

function holdHideSpot(npc, data) {
    try {
        var x = getFloat(data, HIDE_X_KEY);
        var y = getFloat(data, HIDE_Y_KEY);
        var z = getFloat(data, HIDE_Z_KEY);
        npc.setPosition(x, y, z);
        npc.setMotionX(0);
        npc.setMotionY(0);
        npc.setMotionZ(0);
        if (typeof npc.clearNavigation == "function") npc.clearNavigation();
    } catch (e) {}
}

function findCastTarget(npc) {
    var t = npc.getAttackTarget();
    if (t != null && t.isAlive()) return t;
    try {
        var pos = NpcAPI.getIPos(npc.getX(), npc.getY(), npc.getZ());
        var nearby = npc.getWorld().getNearbyEntities(pos, 16, ENTITY_PLAYER);
        var i;
        for (i = 0; i < nearby.length; i++) {
            var p = nearby[i];
            if (p == null || !p.isAlive()) continue;
            if (typeof p.getGamemode == "function" && p.getGamemode() == GAMEMODE_SPECTATOR) continue;
            return p;
        }
    } catch (e) {}
    return null;
}

function countOwnedBats(npc) {
    var n = 0;
    try {
        var ownerId = String(npc.getUUID());
        var pos = NpcAPI.getIPos(npc.getX(), npc.getY(), npc.getZ());
        var list = npc.getWorld().getNearbyEntities(pos, BAT_COUNT_RADIUS, ENTITY_NPC);
        var i;
        for (i = 0; i < list.length; i++) {
            var ent = list[i];
            if (ent == null || !ent.isAlive()) continue;
            if (!ent.hasTag(BAT_TAG)) continue;
            var owner = "";
            try {
                owner = String(ent.getStoreddata().get(BAT_OWNER_KEY));
            } catch (e2) {}
            if (owner != ownerId) continue;
            n++;
        }
    } catch (e) {}
    return n;
}

function updateBloodFrenzy(npc) {
    var data = npc.getStoreddata();
    storeBaseStats(data, npc);

    var wounded = countWoundedPlayers(npc);
    data.put(LAST_WOUNDED_KEY, String(wounded));

    var ratio = wounded / MAX_WOUNDED;
    if (ratio < 0) ratio = 0;
    if (ratio > 1) ratio = 1;

    var baseSpeed = getBaseSpeed(data, npc);
    var nextSpeed = baseSpeed + wounded * SPEED_PER_WOUNDED;
    if (nextSpeed > WALK_SPEED_CAP) nextSpeed = WALK_SPEED_CAP;
    if (nextSpeed < 0) nextSpeed = 0;

    var baseAtk = getBaseAttackSpeed(data, npc);
    var nextAtk = baseAtk - wounded * ATK_REDUCE_PER;
    if (nextAtk < ATK_SPEED_MIN) nextAtk = ATK_SPEED_MIN;

    try {
        var ai = npc.getAi();
        if (!AbilityAPI.isBusy(npc)) {
            ai.setWalkingSpeed(nextSpeed);
        }
        ai.setAttackSpeed(nextAtk);
    } catch (e) {}

    tickFrenzyVfx(npc, wounded, ratio);
}

function countWoundedPlayers(npc) {
    var count = 0;
    try {
        var world = npc.getWorld();
        var pos = NpcAPI.getIPos(npc.getX(), npc.getY(), npc.getZ());
        var nearby = world.getNearbyEntities(pos, Math.ceil(PASSIVE_RADIUS), ENTITY_PLAYER);
        var i;
        for (i = 0; i < nearby.length; i++) {
            if (isWoundedPlayer(nearby[i], npc)) count++;
        }
    } catch (e) {}
    return count;
}

function isWoundedPlayer(ent, npc) {
    if (ent == null || !ent.isAlive()) return false;
    try {
        if (typeof ent.getType == "function" && ent.getType() != ENTITY_PLAYER) return false;
        if (typeof ent.getGamemode == "function" && ent.getGamemode() == GAMEMODE_SPECTATOR) return false;
        if (String(ent.getUUID()) == String(npc.getUUID())) return false;
        var hp = ent.getHealth();
        var max = ent.getMaxHealth();
        if (max <= 0) return false;
        return hp < max - 0.05;
    } catch (e) {
        return false;
    }
}

function tickFrenzyVfx(npc, wounded, ratio) {
    if (wounded <= 0) return;
    try {
        var world = npc.getWorld();
        if (world.getTotalTime() % 8 != 0) return;
        var x = npc.getX();
        var y = npc.getY() + 0.9;
        var z = npc.getZ();
        var n = 3 + wounded * 2;
        world.spawnParticle("minecraft:crimson_spore", x, y, z, 0.4 + ratio * 0.3, 0.35, 0.4 + ratio * 0.3, 0.03, n);
        world.spawnParticle("minecraft:entity_effect", x, y, z, 0.9, 0.08, 0.08, 0, 2 + wounded);
    } catch (e) {}
}

function storeBaseStats(data, npc) {
    try {
        var ai = npc.getAi();
        if (!data.has(BASE_SPEED_KEY)) {
            var speed = ai.getWalkingSpeed();
            if (speed < 0) speed = 5;
            data.put(BASE_SPEED_KEY, String(speed));
        }
        if (!data.has(BASE_ATK_KEY)) {
            var atk = 20;
            try {
                atk = ai.getAttackSpeed();
            } catch (e) {}
            if (atk <= 0) atk = 20;
            data.put(BASE_ATK_KEY, String(atk));
        }
        if (!data.has(SAVED_RETALIATE_KEY)) {
            data.put(SAVED_RETALIATE_KEY, String(ai.getRetaliateType()));
        }
    } catch (e) {}
}

function restoreBaseStats(npc) {
    try {
        var data = npc.getStoreddata();
        var ai = npc.getAi();
        ai.setWalkingSpeed(getBaseSpeed(data, npc));
        ai.setAttackSpeed(getBaseAttackSpeed(data, npc));
    } catch (e) {}
}

function getBaseSpeed(data, npc) {
    if (data.has(BASE_SPEED_KEY)) return getInt(data, BASE_SPEED_KEY);
    try {
        return npc.getAi().getWalkingSpeed();
    } catch (e) {
        return 5;
    }
}

function getBaseAttackSpeed(data, npc) {
    if (data.has(BASE_ATK_KEY)) return getInt(data, BASE_ATK_KEY);
    try {
        return npc.getAi().getAttackSpeed();
    } catch (e) {
        return 20;
    }
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

function distance(a, b) {
    var dx = a.getX() - b.getX();
    var dz = a.getZ() - b.getZ();
    return Math.sqrt(dx * dx + dz * dz);
}

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}

function getFloat(data, key) {
    if (!data.has(key)) return 0.0;
    return parseFloat(String(data.get(key)));
}
