// Брюннер ивента «Око Моррслиб»: связка поверх brunner.js, охотника за головами. Бой целиком его: стойки, арбалет,
// пистоль, ножи, сеть, дым, «Палач», метки ударов. Здесь — то, чего у него нет: заказ даёт Око (eye.js, за самым ярким
// игроком поля), голова не нужна. Добыча погибла или вышла — он подбирает её осколки с земли и минуту уходит прочь
// от центра поля; догнали и убили — роняет всё забранное, не догнали — осколки ушли с ним. Добыча сдала осколки
// или простояла в сейф-зоне TH_ZONE_TICKS — уходит ни с чем; пока она в зоне, он стоит и не бьёт.
// Файлы скрипта клона по порядку: mo_core.js, brunner.js (v1.5), thief.js. Связка подменяет функции brunner.js
// присваиванием, поэтому идёт последней. Без добычи (поставлен руками, чтобы сохранить в клонер) — обычная заготовка.
var TH_Ability = Java.type("noppes.npcs.abilities.AbilityAPI");
var TH_Combat = Java.type("noppes.npcs.abilities.AbilityCombatHelper");

// Его настройки в ивенте: без посредника, кошеля и контракта, без объявлений на весь сервер и личных отчётов;
// охота не дольше HUNT_MINUTES реальных минут.
DEBUG_PLAYER = "";
HUNT_MINUTES = 3;
GLOBAL_MESSAGES = false;
PURSE_SHARE = 0;
DROP_CONTRACT = false;
SAY_LINES.start = ["{name}. Осколки при тебе – значит, и ты мой. Ничего личного.", "{name}. Варп-камень стоит дороже тебя. Ничего личного."];
SAY_LINES.startFree = SAY_LINES.start;
SAY_LINES.kill = ["Варп-камень мой."];
SAY_LINES.giveup = ["Хватит. Живи пока."];
SAY_LINES.death = ["Осколки… забирайте."];

// Здоровье и сила удара из меню заменяются этими: удары Брюннера — множители силы.
var TH_HP = 900, TH_STRENGTH = 7, TH_FACTION = "Враждебные";
// Осколки погибшей или вышедшей добычи он подбирает в TH_PICK_R от места, где видел её последний раз, если сам не дальше
// TH_SEEN_R; ищет их TH_ROB_TICKS — предметы падают в том же тике, что и смерть, но позже события.
var TH_PICK_R = 8, TH_SEEN_R = 24, TH_ROB_TICKS = 40;
var TH_ZONE_TICKS = 160;
// Побег: TH_FLEE_TICKS прочь от центра поля, TH_FLEE_STEP за 2 тика (около 4,4 блока в секунду: бегущий игрок догонит,
// идущий — нет; таймер мода с числом 1 срабатывает раз в 2 тика); раз в TH_FLEE_NET сеть в ближайшего догоняющего.
var TH_FLEE_TICKS = 1200, TH_FLEE_STEP = 0.44, TH_FLEE_NET = 160;
var TH_NET = { min: 0, max: 12,
    params: function () { return TH_Ability.params("chargeTicks", 16, "radius", 2.5, "effectDuration", 40, "effectAmplifier", 2, "landSound", "minecraft:entity.leash_knot.place"); } };
// От дальнего боя и взрывов проходит TH_RANGED_TAKEN.
var TH_RANGED_TAKEN = 0.5, TH_RANGED_TYPES = { "wfm.splitting_shot": 1, "wfm.trap_bolt": 1 };
var TH_WALK = 2;
var TH_GONE_TEXT = {
    empty: "§2Брюннеру больше нечего у тебя взять. Он уходит.",
    zone: "§2Брюннер не стал ждать у портала и ушёл.",
    time: "§2Брюннеру надоело за тобой гоняться. Он уходит."
};

// Обработчики brunner.js, которые связка оборачивает.
var TH_BR = { tick: tick, timer: timer, damaged: damaged, died: died, meleeAttack: meleeAttack };

function tGet(npc, k, def) { var d = npc.getStoreddata(); return d.has(k) && d.get(k) != null ? String(d.get(k)) : def; }
function tNum(npc, k) { return Number(tGet(npc, k, "0")); }
function tPut(npc, k, v) { npc.getStoreddata().put(k, String(v)); }
function tNear(npc) { return (Math.round(npc.getX() / 10) * 10) + ", " + (Math.round(npc.getZ() / 10) * 10); }
function tEvent(npc) { return tGet(npc, "thief_prey", "") != ""; }

function tSay(w, text) {
    var ps = moPlayers(w);
    for (var i = 0; i < ps.length; i++) ps[i].message(text);
}

function tInZone(en) {
    return en != null && en.getType() == 1 && moInSanctuary(en.getX(), en.getZ());
}

// Чанк держать незачем: он всегда рядом с добычей, а значит, с игроком. Заготовке и обычному Брюннеру — как было.
var TH_HOLD_CHUNK = brHoldChunk;
brHoldChunk = function (npc) {
    if (!tEvent(npc)) TH_HOLD_CHUNK(npc);
};
// Итогов посреднику нет, доски посредника нет: заказ не снимается, а исход решает эта связка.
brPost = function () {};
brRecalled = function () { return false; };
brVictory = function (npc, state, victim, now) { npc.getStoreddata().put(BR_DONE_KEY, brHuntId(npc)); };
// Время вышло: строка добыче, дальше он уезжает сам, как после любой охоты.
brLeave = function (npc, state, now, result) {
    npc.getStoreddata().put(BR_DONE_KEY, brHuntId(npc));
    brSay(npc, state, result, true, { name: state.name });
    if (result == "giveup" && tEvent(npc)) {
        var p = tPrey(npc);
        if (p != null) p.message(TH_GONE_TEXT.time);
    }
};

function tPrey(npc) {
    try { return npc.getWorld().getEntity(tGet(npc, "thief_prey", "")); } catch (err) { return null; }
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

// Первый тик клона, вызванного Оком: срок охоты считается с этой минуты, здоровье и сила — ивента.
function tSetup(npc) {
    tPut(npc, BR_SUMMONED_KEY, BR_System.currentTimeMillis());
    var st = npc.getStats();
    st.setMaxHealth(TH_HP);
    npc.setHealth(TH_HP);
    st.getMelee().setStrength(TH_STRENGTH);
    var id = tFactionId(TH_FACTION);
    if (id >= 0) npc.setFaction(id);
    else moLogOnce("thief_faction", "нет фракции " + TH_FACTION + ": Брюннер остался в фракции клона");
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

// Раз в 10 тиков: ход ивента, добыча, её осколки и сейф-зона. Цикл боя brunner.js идёт в timer.
tick = function (e) {
    var npc = e.npc;
    if (!tEvent(npc) || !npc.isAlive()) {
        TH_BR.tick(e);
        return;
    }
    var w = npc.getWorld();
    if (!moRunning(w)) {
        tVanish(npc);
        return;
    }
    moSafeRead(w);
    var now = moNow(w);
    if (tGet(npc, "thief_set", "0") != "1") {
        tPut(npc, "thief_set", 1);
        tSetup(npc);
    }
    var phase = tGet(npc, "thief_phase", "hunt");
    if (phase == "flee") return tFlee(npc, w, now);
    if (phase == "rob") return tRob(npc, w, now);
    var p = tPrey(npc);
    if (p == null || !p.isAlive()) return tStartRob(npc, w, now);
    tPut(npc, "last_x", p.getX());
    tPut(npc, "last_y", p.getY());
    tPut(npc, "last_z", p.getZ());
    if (moShards(p) <= 0) return tGone(npc, p, "empty");
    var zone = moInSanctuary(p.getX(), p.getZ());
    tPut(npc, "prey_zone", zone ? 1 : 0);
    if (tLong(npc, "zone_since", zone, now) >= TH_ZONE_TICKS) return tGone(npc, p, "zone");
    TH_BR.tick(e);
};

// Цикл боя — только на охоте и пока добыча не в зоне; шаг побега — свой таймер.
timer = function (e) {
    var npc = e.npc;
    if (e.id == TH_WALK) return tWalk(npc);
    if (!tEvent(npc)) return TH_BR.timer(e);
    if (tGet(npc, "thief_phase", "hunt") != "hunt") return;
    if (tGet(npc, "prey_zone", "0") == "1") {
        brPause(npc);
        return;
    }
    TH_BR.timer(e);
};

// Добыча погибла или вышла: её осколки рассыпались там, где он видел её последний раз. Дальше TH_SEEN_R — не его.
function tStartRob(npc, w, now) {
    if (moFlat(npc, tNum(npc, "last_x"), tNum(npc, "last_z")) > TH_SEEN_R) return tVanish(npc);
    npc.getStoreddata().put(BR_DONE_KEY, brHuntId(npc));
    var state = brLoad(npc);
    brEndAttack(npc, state);
    state.stopped = true;
    brSave(npc, state);
    npc.setAttackTarget(null);
    npc.clearNavigation();
    brReleaseChunk();
    tPut(npc, "thief_phase", "rob");
    tPut(npc, "rob_until", now + TH_ROB_TICKS);
    tRob(npc, w, now);
}

function tRob(npc, w, now) {
    var x = tNum(npc, "last_x"), y = tNum(npc, "last_y"), z = tNum(npc, "last_z"), n = 0;
    var list = w.getNearbyEntities(NpcAPI.getIPos(x, y, z), TH_PICK_R, 6);
    for (var i = 0; i < list.length; i++) {
        var it = list[i].getItem();
        if (!moIsShard(it) || moFlat(list[i], x, z) > TH_PICK_R) continue;
        n += it.getStackSize();
        list[i].despawn();
    }
    if (n <= 0) {
        if (now >= tNum(npc, "rob_until")) tVanish(npc);
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
    npc.getTimers().forceStart(TH_WALK, 1, true);
    brSay(npc, brLoad(npc), "kill", true, {});
    tSay(w, "§c[Око Моррслиб] §fБрюннер подобрал " + n + " осколков и уходит с поля около " + tNear(npc) + ". Догоните его!");
}

// Побег: прочь от центра поля, в бой не вступает, сетью отбивается от догоняющих; дошёл до срока — ушёл с осколками.
function tFlee(npc, w, now) {
    if (now >= tNum(npc, "flee_until")) {
        tSay(w, "§c[Око Моррслиб] §fБрюннер ушёл с поля, а с ним " + tNum(npc, "stolen") + " осколков.");
        return tVanish(npc);
    }
    if (!npc.getTimers().has(TH_WALK)) npc.getTimers().forceStart(TH_WALK, 1, true);
    if (now < tNum(npc, "net_at")) return;
    var ps = moPlayers(w), best = null, bd = TH_NET.max;
    for (var i = 0; i < ps.length; i++) {
        var d = moFlat(ps[i], npc.getX(), npc.getZ());
        if (d <= bd && !moInSanctuary(ps[i].getX(), ps[i].getZ())) {
            bd = d;
            best = ps[i];
        }
    }
    if (best != null && TH_Ability.start(npc, "net_throw", best, TH_NET.params())) tPut(npc, "net_at", now + TH_FLEE_NET);
}

// Шаг побега: сам, как караван, поиск пути мода в лесу на сотни блоков не дойдёт.
function tWalk(npc) {
    if (!npc.isAlive() || tGet(npc, "thief_phase", "") != "flee") {
        npc.getTimers().stop(TH_WALK);
        return;
    }
    var dx = tNum(npc, "flee_dx"), dz = tNum(npc, "flee_dz"), x = npc.getX(), y = npc.getY(), z = npc.getZ();
    var nx = x + dx * TH_FLEE_STEP, nz = z + dz * TH_FLEE_STEP, g = moGround(npc.getWorld(), nx, nz, y, 3, 6);
    var ny = g == null ? y : Math.max(y - 0.4, Math.min(y + 0.6, g));
    npc.setRotation(TH_Combat.computeYaw(dx, dz));
    TH_Combat.holdInPlace(npc, nx, ny, nz);
    if (ny < y) TH_Combat.clearFallDistance(npc);
}

function tGone(npc, p, why) {
    if (p != null && TH_GONE_TEXT[why]) p.message(TH_GONE_TEXT[why]);
    tVanish(npc);
}

function tVanish(npc) {
    npc.getStoreddata().put(BR_DONE_KEY, brHuntId(npc));
    try {
        var state = brLoad(npc);
        brEndAttack(npc, state);
        state.stopped = true;
        brSave(npc, state);
    } catch (err) {}
    brReleaseChunk();
    TH_Ability.cancel(npc);
    npc.getTimers().stop(TH_WALK);
    npc.getWorld().spawnParticle("cloud", npc.getX(), npc.getY() + 1, npc.getZ(), 0.6, 1.0, 0.6, 0.02, 20);
    npc.despawn();
}

// Убит: всё забранное падает на землю, таблица дропа клона — награда за него.
died = function (e) {
    TH_BR.died(e);
    var npc = e.npc, w = npc.getWorld(), n = tNum(npc, "stolen");
    if (!tEvent(npc)) return;
    tPut(npc, "stolen", 0);
    if (n > 0) moScatter(w, npc.getX(), npc.getY(), npc.getZ(), n);
    tSay(w, "§a[Око Моррслиб] §fБрюннер убит" + (n > 0 ? ", " + n + " осколков на земле." : "."));
};

// Из сейф-зоны его не ранить, как Кигора и стадо; от дальнего боя и взрывов проходит TH_RANGED_TAKEN.
damaged = function (e) {
    if (tEvent(e.npc)) {
        if (tInZone(e.source)) {
            e.setCanceled(true);
            return;
        }
        if (tRangedOrBlast(e.damageSource)) e.damage = e.damage * TH_RANGED_TAKEN;
    }
    TH_BR.damaged(e);
};

// В сейф-зону не бьёт.
meleeAttack = function (e) {
    if (tEvent(e.npc) && tInZone(e.target)) {
        e.setCanceled(true);
        return;
    }
    TH_BR.meleeAttack(e);
};

// Снаряд, как у Кигора: помечен снарядом или ударило не то, что им бросило. Взрыв — по типу урона.
function tRangedOrBlast(ds) {
    var type = String(ds.getType());
    if (ds.isProjectile() || TH_RANGED_TYPES[type] || type.indexOf("explosion") == 0) return true;
    var hit = ds.getImmediateSource(), from = ds.getTrueSource();
    return hit != null && from != null && String(hit.getUUID()) != String(from.getUUID());
}
