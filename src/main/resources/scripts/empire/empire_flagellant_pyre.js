// =====================================================
// Имперский флагеллянт — «Живое кадило»
// Погнался за целью -> поджигает себя -> периодический огненный
// урон вокруг; чем меньше HP, тем больнее «пердеж».
// Telegraph: circleFollow как у martyr (зона вокруг бегущего NPC).
// Урон: JS-пульс (doFirePulse) — Ability Zone здесь не используем.
// =====================================================

var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();
var EntitiesType = Java.type("noppes.npcs.api.constants.EntitiesType");
var TelegraphAPI = Java.type("noppes.npcs.telegraph.TelegraphAPI");

// CNPC OnAttack: 0=Мстить, 1=Паника, 2=Отступать, 3=Ничего
var RETALIATE_REVENGE = 0;

// -------------------------
// НАСТРОЙКИ
// -------------------------
var BURN_RADIUS = 3.5;
var PULSE_INTERVAL_TICKS = 20;    // урон раз в 1 сек
var BASE_PULSE_DAMAGE = 2.0;      // при полном HP
var MAX_PULSE_DAMAGE = 9.0;       // при почти нулевом HP
var SELF_FIRE_TICKS = 80;         // длительность огня на себе (обновляется)
var FART_PARTICLE = "flame";      // обычный огонь, не soul_fire_flame
var FART_SOUND = "minecraft:entity.blaze.burn";
var FART_SOUND_VOL = 0.45;
var FART_SOUND_PITCH = 0.75;
// Как у martyr/Keeper: DEFAULT_COLOR (signed int, без Nashorn-hex проблем)
var TELEGRAPH_COLOR = TelegraphAPI.DEFAULT_COLOR;
// Долгий follow; перед истечением переспавниваем без щели
var TELEGRAPH_DURATION = 200;     // 10 сек
var TELEGRAPH_REFRESH_SLACK = 10; // переспавн за N тиков до конца

// -------------------------
// storeddata keys
// -------------------------
var ACTIVE_KEY = "eff_burn_active";
var NEXT_PULSE_KEY = "eff_next_pulse";
var TELEGRAPH_KEY = "eff_telegraph";
var TELEGRAPH_END_KEY = "eff_telegraph_end";

function init(e) {
    clearState(e.npc, e.npc.getStoreddata());
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) {
        clearState(npc, npc.getStoreddata());
        return;
    }

    var world = npc.getWorld();
    var data = npc.getStoreddata();

    if (String(data.get(ACTIVE_KEY)) != "1") {
        tryActivate(npc, world, data);
        return;
    }

    doBurningTick(npc, world, data);
}

function target(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    tryActivate(npc, npc.getWorld(), npc.getStoreddata());
}

function targetLost(e) {
    // Горит до смерти — покаяние не отменяется.
}

function died(e) {
    var npc = e.npc;
    try {
        npc.extinguish();
    } catch (err) {}
    clearState(npc, npc.getStoreddata());
}

function tryActivate(npc, world, data) {
    if (String(data.get(ACTIVE_KEY)) == "1") return;

    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) return;
    if (typeof npc.canSeeEntity == "function" && !npc.canSeeEntity(target)) return;

    startBurning(npc, world, data);
}

function startBurning(npc, world, data) {
    var now = world.getTotalTime();

    data.put(ACTIVE_KEY, "1");
    data.put(NEXT_PULSE_KEY, String(now + PULSE_INTERVAL_TICKS));

    try {
        npc.getAi().setRetaliateType(RETALIATE_REVENGE);
    } catch (err) {}

    refreshSelfFire(npc);

    try {
        world.playSoundAt(npc.getPos(), "minecraft:item.flintandsteel.use", 0.8, 1.1);
    } catch (err2) {}

    spawnFireFart(world, npc, 14);
    spawnBurnTelegraph(npc, world, data);
}

function spawnBurnTelegraph(npc, world, data) {
    clearTelegraph(npc, data);
    try {
        // circleFollow: ground Y + followEntityId до broadcast (как martyr / Keeper)
        var tid = TelegraphAPI.circleFollow(
            npc,
            npc.getX(),
            npc.getY(),
            npc.getZ(),
            BURN_RADIUS,
            TELEGRAPH_DURATION,
            TELEGRAPH_COLOR
        );
        if (tid != null && String(tid) != "") {
            data.put(TELEGRAPH_KEY, String(tid));
            data.put(TELEGRAPH_END_KEY, String(world.getTotalTime() + TELEGRAPH_DURATION));
        }
    } catch (err) {
        try { log("eff telegraph fail: " + err); } catch (e2) {}
    }
}

function doBurningTick(npc, world, data) {
    refreshSelfFire(npc);
    spawnAmbientFlame(world, npc);

    var now = world.getTotalTime();
    var tgEnd = getInt(data, TELEGRAPH_END_KEY);
    if (!data.has(TELEGRAPH_KEY) || tgEnd <= 0 || now >= tgEnd - TELEGRAPH_REFRESH_SLACK) {
        spawnBurnTelegraph(npc, world, data);
    }

    if (now < getInt(data, NEXT_PULSE_KEY)) return;

    var damage = calcPulseDamage(npc);
    doFirePulse(npc, world, damage);
    data.put(NEXT_PULSE_KEY, String(now + PULSE_INTERVAL_TICKS));
}

function calcPulseDamage(npc) {
    var maxHp = npc.getMaxHealth();
    if (maxHp <= 0) return BASE_PULSE_DAMAGE;

    var hp = npc.getHealth();
    if (hp < 0) hp = 0;

    var missingRatio = 1.0 - (hp / maxHp);
    if (missingRatio < 0) missingRatio = 0;
    if (missingRatio > 1) missingRatio = 1;

    return BASE_PULSE_DAMAGE + (MAX_PULSE_DAMAGE - BASE_PULSE_DAMAGE) * missingRatio;
}

function doFirePulse(npc, world, damage) {
    var pos = NpcAPI.getIPos(npc.getX(), npc.getY() + 0.5, npc.getZ());
    var list = world.getNearbyEntities(pos, BURN_RADIUS, EntitiesType.ANY);
    var mcNpc = null;

    try {
        mcNpc = npc.getMCEntity();
    } catch (err) {}

    var hitCount = 0;
    for (var i = 0; i < list.length; i++) {
        var ent = list[i];
        if (!isValidBurnTarget(npc, ent, mcNpc)) continue;

        try {
            ent.damage(damage);
            ent.setBurning(40);
            hitCount++;
        } catch (err2) {}
    }

    var fartCount = 10 + Math.floor(damage * 2);
    spawnFireFart(world, npc, fartCount);

    if (hitCount > 0 || damage >= MAX_PULSE_DAMAGE * 0.6) {
        try {
            world.playSoundAt(npc.getPos(), FART_SOUND, FART_SOUND_VOL, FART_SOUND_PITCH + damage * 0.03);
        } catch (err3) {}
    }
}

function refreshSelfFire(npc) {
    try {
        npc.setBurning(SELF_FIRE_TICKS);
    } catch (err) {
        try {
            npc.getMCEntity().setRemainingFireTicks(SELF_FIRE_TICKS);
        } catch (err2) {}
    }
}

function spawnFireFart(world, npc, count) {
    var x = npc.getX();
    var y = npc.getY() + 0.75;
    var z = npc.getZ();

    try {
        world.spawnParticle(FART_PARTICLE, x, y, z, 0.45, 0.22, 0.45, 0.05, count);
        world.spawnParticle(FART_PARTICLE, x, y + 0.15, z, 0.35, 0.12, 0.35, 0.04, Math.max(4, Math.floor(count * 0.55)));
        world.spawnParticle(FART_PARTICLE, x, y - 0.05, z, 0.3, 0.08, 0.3, 0.03, Math.max(3, Math.floor(count * 0.35)));
        world.spawnParticle("lava", x, y + 0.1, z, 0.12, 0.05, 0.12, 0, Math.max(2, Math.floor(count * 0.15)));
    } catch (err) {}
}

function spawnAmbientFlame(world, npc) {
    if (Math.random() > 0.55) return;

    var x = npc.getX();
    var y = npc.getY();
    var z = npc.getZ();
    var h = npc.getHeight();

    try {
        for (var i = 0; i < 2; i++) {
            var ox = (Math.random() - 0.5) * 0.7;
            var oy = Math.random() * h;
            var oz = (Math.random() - 0.5) * 0.7;
            world.spawnParticle(FART_PARTICLE, x + ox, y + oy, z + oz, 0, 0.04, 0, 0.02, 1);
        }
    } catch (err) {}
}

function isValidBurnTarget(npc, ent, mcNpc) {
    if (ent == null || !ent.isAlive()) return false;
    if (String(ent.getUUID()) == String(npc.getUUID())) return false;
    if (flatDistance(npc, ent) > BURN_RADIUS) return false;

    if (mcNpc != null && typeof mcNpc.isAlliedTo == "function") {
        try {
            if (mcNpc.isAlliedTo(ent.getMCEntity())) return false;
        } catch (err) {}
    }

    var target = npc.getAttackTarget();
    if (target != null && String(target.getUUID()) == String(ent.getUUID())) return true;
    if (isPlayerEntity(ent)) return true;

    if (typeof ent.typeOf == "function" && ent.typeOf(3)) return true;
    if (typeof ent.getType == "function" && ent.getType() == 3) return true;

    return false;
}

function clearTelegraph(npc, data) {
    if (!data.has(TELEGRAPH_KEY)) {
        data.remove(TELEGRAPH_END_KEY);
        return;
    }
    var tid = String(data.get(TELEGRAPH_KEY));
    try {
        if (npc != null) {
            TelegraphAPI.removeNear(npc, tid);
        } else {
            TelegraphAPI.remove(tid);
        }
    } catch (te) {
        try {
            TelegraphAPI.remove(tid);
        } catch (te2) {}
    }
    data.remove(TELEGRAPH_KEY);
    data.remove(TELEGRAPH_END_KEY);
}

function clearState(npc, data) {
    clearTelegraph(npc, data);
    data.put(ACTIVE_KEY, "0");
    data.remove(NEXT_PULSE_KEY);
}

function isPlayerEntity(entity) {
    if (entity == null) return false;
    if (typeof entity.typeOf == "function" && entity.typeOf(1)) return true;
    if (typeof entity.getType == "function" && entity.getType() == 1) return true;
    if (typeof entity.getMCEntity == "function") {
        var mc = entity.getMCEntity();
        if (mc != null && String(mc.getClass().getName()).indexOf("ServerPlayerEntity") >= 0) return true;
    }
    return String(entity.getClass().getName()).indexOf("ServerPlayerEntity") >= 0;
}

function flatDistance(a, b) {
    var dx = a.getX() - b.getX();
    var dy = a.getY() - b.getY();
    var dz = a.getZ() - b.getZ();
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
}

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}
