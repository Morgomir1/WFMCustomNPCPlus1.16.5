/**
 * Drachenfels Thrall — «Тёмная десятина».
 *
 * Простая support-абилка: thrall находит раненого босса (тег drachenfels),
 * коротко кастует и лечит его, отдавая часть своей жизни.
 * Игрокам выгодно убивать thrall'ов, иначе боссы живут дольше.
 *
 * Clone Bank: Drachenfels Thrall — повесь этот скрипт на клон.
 * Тег drachenfels_thrall ставится в init (и Java raise_thralls тоже).
 */
var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();

var THRALL_TAG = "drachenfels_thrall";
var BOSS_TAG = "drachenfels";

var HEAL_RANGE = 14.0;
var COOLDOWN_TICKS = 80;       // ~4 сек (tick ≈ 10 игровых тиков)
var CHARGE_TICKS = 2;          // 2 скриптовых тика зарядки
var HEAL_AMOUNT = 18.0;        // сколько HP отдаёт боссу
var SELF_COST = 8.0;           // сколько HP теряет thrall
var MIN_BOSS_MISSING = 5.0;    // не хилить почти полного босса

var CD_KEY = "dft_cd";
var CHARGING_KEY = "dft_charging";
var CHARGE_LEFT_KEY = "dft_charge_left";
var TARGET_UUID_KEY = "dft_boss_uuid";

function init(e) {
    var npc = e.npc;
    if (!npc.hasTag(THRALL_TAG)) {
        npc.addTag(THRALL_TAG);
    }
    clearState(npc.getStoreddata());
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) {
        clearState(npc.getStoreddata());
        return;
    }

    var world = npc.getWorld();
    var data = npc.getStoreddata();
    var now = world.getTotalTime();

    if (String(data.get(CHARGING_KEY)) == "1") {
        doChargingTick(npc, world, data);
        return;
    }

    if (now < getInt(data, CD_KEY)) return;

    var boss = findWoundedBoss(npc, world);
    if (boss == null) return;

    startCharge(npc, data, boss);
}

function startCharge(npc, data, boss) {
    data.put(CHARGING_KEY, "1");
    data.put(CHARGE_LEFT_KEY, String(CHARGE_TICKS));
    data.put(TARGET_UUID_KEY, String(boss.getUUID()));

    try {
        npc.getAi().setWalkingSpeed(0);
    } catch (e) {}

    var world = npc.getWorld();
    try {
        world.playSoundAt(
            NpcAPI.getIPos(npc.getX(), npc.getY(), npc.getZ()),
            "minecraft:entity.evoker.prepare_summon",
            0.55,
            1.4
        );
    } catch (e2) {}
}

function doChargingTick(npc, world, data) {
    var left = getInt(data, CHARGE_LEFT_KEY);
    var boss = findNpcByUuid(world, String(data.get(TARGET_UUID_KEY)));

    if (boss == null || !boss.isAlive() || flatDistance(npc, boss) > HEAL_RANGE + 2) {
        clearState(data);
        return;
    }

    spawnLinkVfx(world, npc, boss);

    left--;
    data.put(CHARGE_LEFT_KEY, String(left));
    if (left > 0) return;

    finishTithe(npc, world, data, boss);
}

function finishTithe(npc, world, data, boss) {
    var now = world.getTotalTime();
    data.put(CHARGING_KEY, "0");
    data.put(CD_KEY, String(now + COOLDOWN_TICKS));
    data.put(TARGET_UUID_KEY, "");

    var maxHp = boss.getMaxHealth();
    var cur = boss.getHealth();
    if (maxHp <= 0 || cur >= maxHp) {
        clearState(data);
        return;
    }

    var heal = HEAL_AMOUNT;
    if (cur + heal > maxHp) heal = maxHp - cur;
    if (heal < 1) {
        clearState(data);
        return;
    }

    try {
        boss.setHealth(cur + heal);
    } catch (e) {}

    // Цена десятины
    try {
        var selfHp = npc.getHealth() - SELF_COST;
        if (selfHp <= 0) {
            npc.setHealth(0);
        } else {
            npc.setHealth(selfHp);
        }
    } catch (e2) {}

    spawnHealBurst(world, npc, boss);
    try {
        world.playSoundAt(
            NpcAPI.getIPos(boss.getX(), boss.getY(), boss.getZ()),
            "minecraft:block.respawn_anchor.charge",
            0.7,
            1.35
        );
    } catch (e3) {}

    clearChargeOnly(data);
}

function findWoundedBoss(npc, world) {
    var pos = NpcAPI.getIPos(npc.getX(), npc.getY(), npc.getZ());
    var list = world.getNearbyEntities(pos, Math.ceil(HEAL_RANGE), 2);
    var best = null;
    var bestMissing = MIN_BOSS_MISSING;

    for (var i = 0; i < list.length; i++) {
        var ent = list[i];
        if (ent == null || !ent.isAlive()) continue;
        if (typeof ent.hasTag != "function" || !ent.hasTag(BOSS_TAG)) continue;
        // Не хилить упавшего в Immortal Bond (1 HP / df_downed)
        try {
            if (String(ent.getStoreddata().get("df_downed")) == "1") continue;
        } catch (e) {}

        var maxHp = ent.getMaxHealth();
        if (maxHp <= 0) continue;
        var missing = maxHp - ent.getHealth();
        if (missing < bestMissing) continue;
        if (flatDistance(npc, ent) > HEAL_RANGE) continue;

        bestMissing = missing;
        best = ent;
    }
    return best;
}

function spawnLinkVfx(world, from, to) {
    try {
        var steps = 6;
        for (var i = 0; i <= steps; i++) {
            var t = i / steps;
            var x = from.getX() + (to.getX() - from.getX()) * t;
            var y = from.getY() + 1.0 + (to.getY() + 1.0 - from.getY() - 1.0) * t;
            var z = from.getZ() + (to.getZ() - from.getZ()) * t;
            world.spawnParticle("minecraft:soul", x, y, z, 0, 0.02, 0, 0.01, 1);
            if (i % 2 == 0) {
                world.spawnParticle("minecraft:soul_fire_flame", x, y, z, 0, 0.02, 0, 0, 1);
            }
        }
    } catch (e) {}
}

function spawnHealBurst(world, thrall, boss) {
    try {
        world.spawnParticle(
            "minecraft:soul_fire_flame",
            boss.getX(), boss.getY() + 1.2, boss.getZ(),
            0.25, 0.4, 0.25, 0.03, 14
        );
        world.spawnParticle(
            "minecraft:soul",
            boss.getX(), boss.getY() + 0.8, boss.getZ(),
            0.2, 0.3, 0.2, 0.02, 10
        );
        world.spawnParticle(
            "minecraft:soul",
            thrall.getX(), thrall.getY() + 0.9, thrall.getZ(),
            0.15, 0.2, 0.15, 0.02, 6
        );
        try {
            world.spawnParticle(
                "wfm:fog",
                boss.getX(), boss.getY() + 0.4, boss.getZ(),
                0.3, 0.08, 0.3, 0, 4
            );
        } catch (fogErr) {}
    } catch (e) {}
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

function clearState(data) {
    data.put(CHARGING_KEY, "0");
    data.put(CHARGE_LEFT_KEY, "0");
    data.put(TARGET_UUID_KEY, "");
}

function clearChargeOnly(data) {
    data.put(CHARGING_KEY, "0");
    data.put(CHARGE_LEFT_KEY, "0");
    data.put(TARGET_UUID_KEY, "");
}

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}

function died(e) {
    clearState(e.npc.getStoreddata());
}
