// Брюннер, охотник за головами: наёмник, которого наняли собирать варп-камень. Око вызывает его за самым ярким игроком
// поля (eye.js, THIEF_MIN осколков), по одному на поле. Он дуэлянт: меч, выстрел из пистолета, выпад и сеть.
// Добыча погибла или вышла — он подбирает её осколки с земли и уходит к краю поля; догнали и убили — роняет всё
// забранное, не догнали за TH_FLEE_TICKS — осколки ушли с ним. Добыча сдала осколки, ушла в сейф-зону или далеко,
// охота затянулась — уходит ни с чем.
// Файлы скрипта клона: mo_core.js, затем thief.js. Здоровье, удар и ходьбу ставит скрипт; в окне NPC — облик и таблица
// дропа (награда за убитого). Без добычи (поставлен руками, чтобы сохранить в клонер) стоит и ничего не делает.
var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");
var Combat = Java.type("noppes.npcs.abilities.AbilityCombatHelper");

// Удар обычный, через броню; range — дальность удара, delay — тиков между ударами.
var TH = { hp: 900, melee: 9, range: 3, delay: 16, speed: 7, aggro: 64 };
var TH_FACTION = "Враждебные";
// Добыча дальше TH_LOSE_R TH_LOSE_TICKS подряд или в сейф-зоне TH_ZONE_TICKS подряд — потеряна; охота не дольше TH_HUNT_TICKS.
var TH_LOSE_R = 64, TH_LOSE_TICKS = 200, TH_ZONE_TICKS = 160, TH_HUNT_TICKS = 3600;
// Осколки погибшей или вышедшей добычи он подбирает в TH_PICK_R от места, где видел её последний раз, если сам не дальше TH_SEEN_R.
var TH_PICK_R = 8, TH_SEEN_R = 24;
// Уходит TH_FLEE_TICKS прочь от центра поля, TH_FLEE_STEP за 2 тика (таймер мода с числом 1 срабатывает раз в 2 тика) (около 4,4 блока в секунду: бегущий игрок догонит,
// идущий — нет); раз в TH_FLEE_NET бросает сеть в ближайшего догоняющего.
var TH_FLEE_TICKS = 1200, TH_FLEE_STEP = 0.44, TH_FLEE_NET = 160;
// От дальнего боя и взрывов проходит TH_RANGED_TAKEN.
var TH_RANGED_TAKEN = 0.5, TH_RANGED_TYPES = { "wfm.splitting_shot": 1, "wfm.trap_bolt": 1 };
// Приёмы — способности CustomNPCs, первый готовый на этом расстоянии.
var TH_NET = { ability: "net_throw", cd: 300, min: 5, max: 12,
    params: function () { return AbilityAPI.params("chargeTicks", 16, "radius", 2.5, "effectDuration", 40, "effectAmplifier", 2, "landSound", "minecraft:entity.leash_knot.place"); } };
var TH_LUNGE = { ability: "wh_lunge", cd: 160, min: 3, max: 6,
    params: function () { return AbilityAPI.params("distance", 5.5, "chargeTicks", 16, "damage", 10.0); } };
var TH_SHOT = { ability: "pistol_shot", cd: 80, min: 5, max: 16,
    params: function () { return AbilityAPI.params("chargeTicks", 10, "damage", 8.0, "accuracy", 3, "maxRange", 18.0); } };
var TH_CASTS = [TH_NET, TH_LUNGE, TH_SHOT];
var T_WALK = 2;
var TH_GONE_TEXT = {
    empty: "§2Брюннеру больше нечего у тебя взять. Он уходит.",
    zone: "§2Брюннер не стал ждать у портала и ушёл.",
    lost: "§2Брюннер потерял твой след.",
    time: "§2Брюннеру надоело за тобой гоняться. Он уходит."
};

function tGet(npc, k, def) { var d = npc.getStoreddata(); return d.has(k) ? String(d.get(k)) : def; }
function tNum(npc, k) { return Number(tGet(npc, k, "0")); }
function tPut(npc, k, v) { npc.getStoreddata().put(k, String(v)); }
function tNear(npc) { return (Math.round(npc.getX() / 10) * 10) + ", " + (Math.round(npc.getZ() / 10) * 10); }

function tSay(w, text) {
    var ps = moPlayers(w);
    for (var i = 0; i < ps.length; i++) ps[i].message(text);
}

function tFactionId(name) {
    try {
        var list = NpcAPI.getFactions().list();
        var n = list.length;
        if (n == null) n = list.size();
        for (var i = 0; i < n; i++) {
            var f = list[i];
            if (f == null && list.get) f = list.get(i);
            if (f != null && String(f.getName()) == name) return f.getId();
        }
    } catch (err) {
        moLogOnce("thief_factions", "фракции не прочитаны: " + err);
    }
    return -1;
}

function tSetup(npc) {
    var st = npc.getStats(), ai = npc.getAi(), melee = st.getMelee();
    st.setMaxHealth(TH.hp);
    npc.setHealth(TH.hp);
    melee.setStrength(TH.melee);
    melee.setRange(TH.range);
    melee.setDelay(TH.delay);
    st.setAggroRange(TH.aggro);
    ai.setRetaliateType(0);
    ai.setWalkingSpeed(TH.speed);
    ai.setMovingType(0);
    ai.setReturnsHome(false);
    var id = tFactionId(TH_FACTION);
    if (id >= 0) npc.setFaction(id);
    else moLogOnce("thief_faction", "нет фракции " + TH_FACTION + ": Брюннер остался в фракции клона");
}

function init(e) {
    if (!e.npc.isAlive()) return;
    e.npc.getTimers().clear();
    AbilityAPI.cancel(e.npc);
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) {
        AbilityAPI.cancel(npc);
        return;
    }
    var id = tGet(npc, "thief_prey", "");
    if (id == "") return;
    var w = npc.getWorld();
    if (!moRunning(w)) {
        tVanish(npc);
        return;
    }
    moSafeRead(w);
    if (tGet(npc, "thief_set", "0") != "1") {
        tPut(npc, "thief_set", 1);
        tSetup(npc);
    }
    var now = moNow(w);
    if (tGet(npc, "thief_phase", "hunt") == "flee") tFlee(npc, w, now);
    else tHunt(npc, w, id, now);
}

// Охота: держит добычу целью и колдует приёмы; ушла, сдала осколки или затянулось — уходит ни с чем.
function tHunt(npc, w, id, now) {
    var p = null;
    try { p = w.getEntity(id); } catch (err) {}
    if (p == null || !p.isAlive()) {
        tRob(npc, w, now);
        return;
    }
    tPut(npc, "last_x", p.getX());
    tPut(npc, "last_y", p.getY());
    tPut(npc, "last_z", p.getZ());
    if (moShards(p) <= 0) return tGone(npc, p, "empty");
    if (now - tNum(npc, "thief_since") >= TH_HUNT_TICKS) return tGone(npc, p, "time");
    if (tLong(npc, "zone_since", moInSanctuary(p.getX(), p.getZ()), now) >= TH_ZONE_TICKS) return tGone(npc, p, "zone");
    if (tLong(npc, "far_since", moFlat(p, npc.getX(), npc.getZ()) > TH_LOSE_R, now) >= TH_LOSE_TICKS) return tGone(npc, p, "lost");
    if (moInSanctuary(p.getX(), p.getZ())) {
        npc.setAttackTarget(null);
        return;
    }
    var t = npc.getAttackTarget();
    if (t == null || String(t.getUUID()) != id) npc.setAttackTarget(p);
    tCast(npc, p, now);
}

// Сколько тиков подряд держится условие; ложное сбрасывает счёт.
function tLong(npc, key, on, now) {
    var since = tNum(npc, key);
    if (!on) {
        if (since != 0) tPut(npc, key, 0);
        return 0;
    }
    if (since == 0) {
        tPut(npc, key, now);
        return 0;
    }
    return now - since;
}

function tCast(npc, t, now) {
    var dist = moFlat(t, npc.getX(), npc.getZ());
    for (var i = 0; i < TH_CASTS.length; i++) {
        var c = TH_CASTS[i];
        if (now < tNum(npc, "cast_at" + i) || dist < c.min || dist > c.max) continue;
        if (!AbilityAPI.start(npc, c.ability, t, c.params())) continue;
        tPut(npc, "cast_at" + i, now + c.cd);
        return;
    }
}

// Добыча погибла или вышла: её осколки рассыпались там, где он видел её последний раз. Подбирает и уходит с ними.
function tRob(npc, w, now) {
    var x = tNum(npc, "last_x"), y = tNum(npc, "last_y"), z = tNum(npc, "last_z"), n = 0;
    if (moFlat(npc, x, z) <= TH_SEEN_R) {
        var list = w.getNearbyEntities(NpcAPI.getIPos(x, y, z), TH_PICK_R, 6);
        for (var i = 0; i < list.length; i++) {
            var it = list[i].getItem();
            if (!moIsShard(it) || moFlat(list[i], x, z) > TH_PICK_R) continue;
            n += it.getStackSize();
            list[i].despawn();
        }
    }
    if (n <= 0) {
        tVanish(npc);
        return;
    }
    var f = MO.FIELD, dx = npc.getX() - f[0], dz = npc.getZ() - f[2], len = Math.sqrt(dx * dx + dz * dz);
    if (len < 1) {
        var a = Math.random() * Math.PI * 2;
        dx = Math.cos(a);
        dz = Math.sin(a);
        len = 1;
    }
    tPut(npc, "stolen", tNum(npc, "stolen") + n);
    tPut(npc, "thief_phase", "flee");
    tPut(npc, "flee_until", now + TH_FLEE_TICKS);
    tPut(npc, "flee_dx", dx / len);
    tPut(npc, "flee_dz", dz / len);
    npc.setAttackTarget(null);
    AbilityAPI.cancel(npc);
    npc.getTimers().forceStart(T_WALK, 1, true);
    tSay(w, "§c[Око Моррслиб] §fБрюннер подобрал " + n + " осколков и уходит с поля около " + tNear(npc) + ". Догоните его!");
}

// Побег: идёт прочь от центра поля, в бой не вступает, сетью отбивается от догоняющих; дошёл до срока — ушёл с осколками.
function tFlee(npc, w, now) {
    if (now >= tNum(npc, "flee_until")) {
        tSay(w, "§c[Око Моррслиб] §fБрюннер ушёл с поля, а с ним " + tNum(npc, "stolen") + " осколков.");
        tVanish(npc);
        return;
    }
    if (npc.getAttackTarget() != null) npc.setAttackTarget(null);
    if (!npc.getTimers().has(T_WALK)) npc.getTimers().forceStart(T_WALK, 1, true);
    if (now < tNum(npc, "net_at")) return;
    var ps = moPlayers(w), best = null, bd = TH_NET.max;
    for (var i = 0; i < ps.length; i++) {
        var d = moFlat(ps[i], npc.getX(), npc.getZ());
        if (d <= bd && !moInSanctuary(ps[i].getX(), ps[i].getZ())) {
            bd = d;
            best = ps[i];
        }
    }
    if (best != null && AbilityAPI.start(npc, TH_NET.ability, best, TH_NET.params())) tPut(npc, "net_at", now + TH_FLEE_NET);
}

// Шаг побега: сам, как караван, поиск пути мода в лесу на сотни блоков не дойдёт.
function tWalk(npc) {
    if (!npc.isAlive() || tGet(npc, "thief_phase", "") != "flee") {
        npc.getTimers().stop(T_WALK);
        return;
    }
    var dx = tNum(npc, "flee_dx"), dz = tNum(npc, "flee_dz"), x = npc.getX(), y = npc.getY(), z = npc.getZ();
    var nx = x + dx * TH_FLEE_STEP, nz = z + dz * TH_FLEE_STEP, g = moGround(npc.getWorld(), nx, nz, y, 3, 6);
    var ny = g == null ? y : Math.max(y - 0.4, Math.min(y + 0.6, g));
    npc.setRotation(Combat.computeYaw(dx, dz));
    Combat.holdInPlace(npc, nx, ny, nz);
    if (ny < y) Combat.clearFallDistance(npc);
}

function tGone(npc, p, why) {
    if (p != null && TH_GONE_TEXT[why]) p.message(TH_GONE_TEXT[why]);
    tVanish(npc);
}

function tVanish(npc) {
    AbilityAPI.cancel(npc);
    npc.getTimers().clear();
    npc.getWorld().spawnParticle("cloud", npc.getX(), npc.getY() + 1, npc.getZ(), 0.6, 1.0, 0.6, 0.02, 20);
    npc.despawn();
}

function timer(e) {
    if (e.id == T_WALK) tWalk(e.npc);
}

// Убит: всё забранное падает на землю, таблица дропа — награда за него.
function died(e) {
    var npc = e.npc, w = npc.getWorld(), n = tNum(npc, "stolen");
    if (tGet(npc, "thief_prey", "") == "") return;
    tPut(npc, "stolen", 0);
    if (n > 0) moScatter(w, npc.getX(), npc.getY(), npc.getZ(), n);
    tSay(w, "§a[Око Моррслиб] §fБрюннер убит" + (n > 0 ? ", " + n + " осколков на земле." : "."));
}

// Из сейф-зоны его не ранить, как Кигора и стадо; от дальнего боя и взрывов проходит TH_RANGED_TAKEN.
function damaged(e) {
    if (tInZone(e.source)) {
        e.setCanceled(true);
        return;
    }
    if (tRangedOrBlast(e.damageSource)) e.damage = e.damage * TH_RANGED_TAKEN;
}

// Снаряд, как у Кигора: помечен снарядом или ударило не то, что им бросило. Взрыв — по типу урона.
function tRangedOrBlast(ds) {
    var type = String(ds.getType());
    if (ds.isProjectile() || TH_RANGED_TYPES[type] || type.indexOf("explosion") == 0) return true;
    var hit = ds.getImmediateSource(), from = ds.getTrueSource();
    return hit != null && from != null && String(hit.getUUID()) != String(from.getUUID());
}

// В сейф-зону не бьёт и оттуда целей не берёт; на бегу не берёт целей и не бьёт вовсе: мод брал бы догоняющего между тиками.
function tInZone(en) {
    return en != null && en.typeOf(1) && moInSanctuary(en.getX(), en.getZ());
}

function tRunning(npc) {
    return tGet(npc, "thief_phase", "") == "flee";
}

function target(e) {
    if (tRunning(e.npc) || tInZone(e.entity)) e.setCanceled(true);
}

function meleeAttack(e) {
    if (tRunning(e.npc) || tInZone(e.target)) e.setCanceled(true);
}
