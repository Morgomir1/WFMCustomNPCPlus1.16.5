// herd.js — зверолюды поля ивента «Око Моррслиб». Файлы NPC по порядку: morrslieb_eye/mo_core.js, morrslieb_eye/herd.js.
// Клон ставит Око (eye.js) и пишет вид в storeddata «herd». Числа вида, фракцию и ходьбу скрипт выставляет сам на первом тике:
// в окне NPC нужны только модель, скин и таблица дропа.
// Без вида — заготовка для клонера: её ставят руками, чтобы настроить и сохранить. Стоит и ничего не делает.
// Ивент выключили — стадо уходит с поля. В сейф-зоне и у алтаря оно никого не трогает и оттуда его не ранить: иначе удар
// срывал бы 30 секунд выхода, а зона стала бы укрытием, как у Кигора.
// Охотники, нюхач и центигор, идут на свет своей добычи: кто погас, вошёл в зону или ушёл дальше HUNT_LOSE, того они теряют
// и через HUNT_LOSE_TICKS уходят. Гарпия летает, это настройка навигации NPC. Шаман лечит стадо рядом, а кого заметил, того
// подсвечивает: такого видит и Кигор.
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");
var TelegraphAPI = Java.type("noppes.npcs.telegraph.TelegraphAPI");
var Combat = Java.type("noppes.npcs.abilities.AbilityCombatHelper");

// Рывок бивнегора и центигора: полоса, задетым доля здоровья. Вращение гора: круг вокруг него. Проклятие шамана: круг на цели.
var TUSK_CHARGE = { ability: "dash", cd: 160, min: 4, max: 12, shape: "line", len: 10, size: 1.5, hitAt: 34, pct: 0.12,
    params: function () { return AbilityAPI.params("chargeTicks", 28, "distance", 10.0, "damage", 5.0); } };
var GOR_SPIN = { ability: "weapon_spin", cd: 140, min: 0, max: 4, shape: "self", size: 3.5, hitAt: 40, pct: 0.15,
    params: function () { return AbilityAPI.params("chargeTicks", 16, "activeTicks", 24, "radius", 3.5, "damagePerTick", 2.0); } };

var CENT_CHARGE = { ability: "dash", cd: 200, min: 5, max: 14, shape: "line", len: 12, size: 1.5, hitAt: 34, pct: 0.15,
    params: function () { return AbilityAPI.params("chargeTicks", 28, "distance", 12.0, "damage", 6.0); } };
var SHAMAN_CURSE = { ability: "grasping_dead", cd: 200, min: 2, max: 16, shape: "target", size: 3, hitAt: 40, pct: 0.18,
    params: function () { return AbilityAPI.params("chargeTicks", 20, "radius", 3.0, "effectType", "slowness", "effectAmplifier", 1); } };

// Приёмы без своей фигуры: всё делает способность, shape "none". Выпад унгора, сеть нюхача, топот бивнегора, бросок топора, пике гарпии.
var UNGOR_THRUST = { ability: "stake_thrust", cd: 120, min: 1, max: 4, shape: "none",
    params: function () { return AbilityAPI.params("distance", 3.5, "chargeTicks", 8, "activeTicks", 4, "damage", 3.0, "knockback", 0.6, "hitRadius", 1.1); } };
var SNIFFER_NET = { ability: "net_throw", cd: 400, min: 5, max: 14, shape: "none",
    params: function () { return AbilityAPI.params("chargeTicks", 20, "radius", 2.5, "effectDuration", 30, "effectAmplifier", 2); } };
var TUSK_STOMP = { ability: "jump_slam", cd: 240, min: 3, max: 9, shape: "none",
    params: function () { return AbilityAPI.params("chargeTicks", 14, "activeTicks", 9, "damage", 4.0, "knockback", 1.2, "landRadius", 3.0, "arcHeight", 3.0, "maxRange", 10.0); } };
var GOR_THROW = { ability: "axe_throw", cd: 180, min: 6, max: 16, shape: "none",
    params: function () { return AbilityAPI.params("chargeTicks", 12, "maxRange", 16.0, "hitRadius", 1.4, "outboundTicks", 14, "holdTicks", 2, "returnTicks", 10, "arcHeight", 0.4, "damagePercent", 0.1, "effectDuration", 30, "effectAmplifier", 0); } };
var CENT_THROW = { ability: "axe_throw", cd: 220, min: 6, max: 16, shape: "none",
    params: function () { return AbilityAPI.params("chargeTicks", 12, "maxRange", 16.0, "hitRadius", 1.4, "outboundTicks", 14, "holdTicks", 2, "returnTicks", 10, "arcHeight", 0.4, "damagePercent", 0.08, "effectDuration", 30, "effectAmplifier", 0); } };
var HARPY_DIVE = { ability: "vampire_pounce", cd: 100, min: 4, max: 14, shape: "none",
    params: function () { return AbilityAPI.params("chargeTicks", 12, "activeTicks", 9, "damage", 6.0, "knockback", 0.8, "landRadius", 2.8, "arcHeight", 4.0, "lifeStealPerHit", 0.0); } };

// Бра-шаман держит дистанцию и колдует: подошедшего вплотную отталкивает и сам отскакивает, проклинает, бросает лужу порчи,
// между ними — сгусток душ раз в 3 секунды. Сгусток бьёт обычным уроном через броню, лужа — магией, пока в ней стоят.
var SHAMAN_REPULSE = { ability: "df_repulse", cd: 200, min: 0, max: 3, shape: "none",
    params: function () { return AbilityAPI.params("chargeTicks", 16, "radius", 3.5, "knockback", 1.6, "knockbackY", 0.4); } };
var SHAMAN_RETREAT = { ability: "retreat_dash", cd: 160, min: 0, max: 4, shape: "none",
    params: function () { return AbilityAPI.params("distance", 7.0, "chargeTicks", 5, "activeTicks", 6); } };
var SHAMAN_POOL = { ability: "crimson_blob", cd: 240, min: 5, max: 18, shape: "none",
    params: function () { return AbilityAPI.params("chargeTicks", 20, "maxRange", 18.0, "landRadius", 2.5, "damage", 2.0, "damageInterval", 20, "zoneTicks", 120,
        "effectId", "minecraft:poison", "effectDuration", 60, "effectAmplifier", 0, "zoneColor", 0xC0408020 | 0); } };
var SHAMAN_BOLT = { ability: "ghost_soul_bolt", cd: 60, min: 4, max: 20, shape: "none",
    params: function () { return AbilityAPI.params("chargeTicks", 16, "maxRange", 20.0, "damage", 5.0, "knockback", 0.8, "knockbackY", 0.3, "hitRadius", 1.4, "landRadius", 2.0); } };

// aggro — на сколько блоков замечает, wander — сколько бродит от точки, 0 — не бродит, hunter — охотник на свет,
// range — дальность удара: CustomNPCs меряет её от ног до ног, по умолчанию 2, и игрок с мечом перебивал стадо издалека;
// delay — тиков между ударами, по умолчанию 20. fly — летает, slow — на сколько секунд замедляет ударом, casts — приёмы по порядку, у каждого свой откат, loot — сколько осколков роняет.
var HERD = {
    ungor: { hp: 75, melee: 5, range: 3, aggro: 16, speed: 6, wander: 8, casts: [UNGOR_THRUST] },
    tuskgor: { hp: 150, melee: 7, range: 4, aggro: 14, speed: 6, wander: 10, casts: [TUSK_CHARGE, TUSK_STOMP] },
    gor: { hp: 200, melee: 8, range: 3, aggro: 18, speed: 5, wander: 3, casts: [GOR_SPIN, GOR_THROW] },
    sniffer: { hp: 50, melee: 4, range: 3, aggro: 64, speed: 7, wander: 0, casts: [SNIFFER_NET], hunter: 1, name: "Унгор-нюхач" },
    centigor: { hp: 140, melee: 7, range: 4, aggro: 64, speed: 8, wander: 0, casts: [CENT_CHARGE, CENT_THROW], hunter: 1, slow: 2 },
    harpy: { hp: 110, melee: 6, range: 4, delay: 14, aggro: 16, speed: 8, wander: 12, casts: [HARPY_DIVE], fly: 1 },
    shaman: { hp: 165, melee: 4, range: 3, aggro: 20, speed: 4, wander: 3, casts: [SHAMAN_REPULSE, SHAMAN_RETREAT, SHAMAN_CURSE, SHAMAN_POOL, SHAMAN_BOLT] },
    pack: { hp: 300, melee: 5, aggro: 1, speed: 5, wander: 0, loot: 10, name: "Вьючный бивнегор" }
};
var HUNT_LOSE = 48, HUNT_LOSE_TICKS = 200, HUNT_AGGRO = 64;
// Поводок: обитатель дальше HERD_LEASH от дома бросает цель и идёт домой, иначе игрок уводит стоянку через всё поле.
var HERD_LEASH = 32;
// Шаман: раз в SHAMAN_EVERY тиков лечит стадо в SHAMAN_R на SHAMAN_HEAL здоровья, пока дерётся.
var SHAMAN_EVERY = 120, SHAMAN_R = 10, SHAMAN_HEAL = 0.12;
// Имя из Global → Factions, буква в букву.
var HERD_FACTION = "Враждебные", HERD_RED = 0xC0FF3030 | 0;
var T_HIT = 1, T_WALK = 2, WALK_STEP = 0.3;
// От дальнего боя и взрывов стадо получает RANGED_TAKEN урона. Резист в окне NPC тут не годится: CustomNPCs считает дальним
// только стрелы и брошенное, а пули и копья мода снарядами не помечены. Добавочный урон чар стрелкового оружия идёт
// от самого игрока, его узнаём по типу.
var RANGED_TAKEN = 0.25, RANGED_TYPES = { "wfm.splitting_shot": 1, "wfm.trap_bolt": 1 };

function hGet(npc, k, def) { var d = npc.getStoreddata(); return d.has(k) ? String(d.get(k)) : def; }
function hNum(npc, k) { return Number(hGet(npc, k, "0")); }
function hPut(npc, k, v) { npc.getStoreddata().put(k, String(v)); }

function hFactionId(name) {
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
        moLogOnce("herd_factions", "фракции не прочитаны: " + err);
    }
    return -1;
}

// Случайная встреча: обычный вид, но идёт на своего игрока, как охотник, и свет ему не нужен. Ключ ставит Око.
function hPatrol(npc) { return hGet(npc, "herd_camp", "").indexOf("pt_") == 0; }

function hSetup(npc, kind) {
    var st = npc.getStats(), ai = npc.getAi(), patrol = hPatrol(npc), wander = patrol || hGet(npc, "herd_to", "") != "" ? 0 : kind.wander;
    // Сама она ИИ не пересобирает: это делает setMovingType ниже.
    ai.setNavigationType(kind.fly == 1 ? 1 : 0);
    st.setMaxHealth(kind.hp);
    npc.setHealth(kind.hp);
    st.getMelee().setStrength(kind.melee);
    if (kind.range > 0) st.getMelee().setRange(kind.range);
    if (kind.delay > 0) st.getMelee().setDelay(kind.delay);
    st.setAggroRange(patrol ? HUNT_AGGRO : kind.aggro);
    ai.setRetaliateType(0);
    ai.setWalkingSpeed(kind.speed);
    ai.setMovingType(wander > 0 ? 1 : 0);
    if (wander > 0) ai.setWanderingRange(wander);
    ai.setReturnsHome(wander > 0);
    var id = hFactionId(HERD_FACTION);
    if (id >= 0) npc.setFaction(id);
    else moLogOnce("herd_faction", "нет фракции " + HERD_FACTION + ": стадо осталось в фракции клона");
    if (kind.name != null) npc.getDisplay().setName(kind.name);
}

// Цель охотника — его добыча; держит, пока она светится, рядом и не в зоне.
function hHunt(npc, w, now) {
    var id = hGet(npc, "herd_prey", ""), p = null;
    try { p = w.getEntity(id); } catch (err) {}
    var ok = p != null && p.isAlive() && !moInSanctuary(p.getX(), p.getZ())
        && moFlat(p, npc.getX(), npc.getZ()) <= HUNT_LOSE && (hPatrol(npc) || moShards(p) > 0);
    if (ok) {
        hPut(npc, "herd_lost", 0);
        var t = npc.getAttackTarget();
        if (t == null || String(t.getUUID()) != id) npc.setAttackTarget(p);
        return;
    }
    var lost = hNum(npc, "herd_lost");
    if (lost == 0) {
        hPut(npc, "herd_lost", now);
        npc.setAttackTarget(null);
    } else if (now - lost >= HUNT_LOSE_TICKS) {
        npc.despawn();
    }
}

// Кого шаман заметил, тот светится для Кигора; пока идёт бой, шаман лечит стадо вокруг себя.
function hShaman(npc, w, now) {
    var t = npc.getAttackTarget();
    if (t == null || !t.isAlive()) return;
    if (t.typeOf(1)) moMark(w, t, MO.MARK_TICKS);
    if (now < hNum(npc, "heal_at")) return;
    hPut(npc, "heal_at", now + SHAMAN_EVERY);
    var list = w.getNearbyEntities(npc.getPos(), SHAMAN_R, 2);
    for (var i = 0; i < list.length; i++) {
        var a = list[i];
        if (a.isAlive() && a.getStoreddata().has("herd")) a.setHealth(Math.min(a.getMaxHealth(), a.getHealth() + a.getMaxHealth() * SHAMAN_HEAL));
    }
    w.spawnParticle("happy_villager", npc.getX(), npc.getY() + 1, npc.getZ(), 3, 1, 3, 0, 20);
}

// Приём вида: первый готовый по откату и расстоянию. Есть фигура — показать её; через hitAt таймер бьёт долей здоровья всех, кто в ней.
function hCast(npc, casts, t, now) {
    if (casts == null || !t.isAlive()) return;
    var dist = moFlat(t, npc.getX(), npc.getZ());
    for (var i = 0; i < casts.length; i++) {
        var c = casts[i];
        if (now < hNum(npc, "cast_at" + i) || dist < c.min || dist > c.max) continue;
        var yaw = Combat.computeYaw(t.getX() - npc.getX(), t.getZ() - npc.getZ());
        if (!AbilityAPI.start(npc, c.ability, t, c.params())) continue;
        hPut(npc, "cast_at" + i, now + c.cd);
        if (!(c.pct > 0)) return;
        hPut(npc, "hit_i", i);
        hPut(npc, "hit_yaw", yaw);
        hPut(npc, "hit_x", c.shape == "target" ? t.getX() : npc.getX());
        hPut(npc, "hit_z", c.shape == "target" ? t.getZ() : npc.getZ());
        if (c.shape == "line") TelegraphAPI.line(npc, npc.getX(), npc.getY(), npc.getZ(), yaw, c.len, c.size * 2, c.hitAt, HERD_RED);
        if (c.shape == "target") TelegraphAPI.circle(npc, t.getX(), t.getY(), t.getZ(), c.size, c.hitAt, HERD_RED);
        npc.getTimers().forceStart(T_HIT, c.hitAt, false);
        return;
    }
}

// Шаг по маршруту события: сам, как Кигор, поиск пути мода в лесу не дойдёт. Охрана на бой останавливается, вьючный идёт всегда.
function hWalk(npc) {
    var kind = HERD[hGet(npc, "herd", "")], to = hGet(npc, "herd_to", "").split(",");
    if (kind == null || to.length < 2 || !npc.isAlive() || (kind.loot == null && npc.getAttackTarget() != null)) return;
    var x = npc.getX(), y = npc.getY(), z = npc.getZ(), dx = Number(to[0]) - x, dz = Number(to[1]) - z, d = Math.sqrt(dx * dx + dz * dz);
    if (d < 3) {
        if (kind.loot > 0) npc.getWorld().getTempdata().put("mo_ev_end", "left");
        npc.despawn();
        return;
    }
    var nx = x + dx / d * WALK_STEP, nz = z + dz / d * WALK_STEP, g = moGround(npc.getWorld(), nx, nz, y, 3, 6);
    var ny = g == null ? y : Math.max(y - 0.4, Math.min(y + 0.6, g));
    npc.setRotation(Combat.computeYaw(dx, dz));
    Combat.holdInPlace(npc, nx, ny, nz);
    if (ny < y) Combat.clearFallDistance(npc);
}

function hPlayersAt(npc, x, y, z, r) {
    var list = npc.getWorld().getNearbyEntities(NpcAPI.getIPos(x, y, z), Math.ceil(r) + 1, 1);
    var out = [];
    for (var i = 0; i < list.length; i++) {
        var p = list[i];
        if (!p.isAlive() || p.getGamemode() == 1 || p.getGamemode() == 3) continue;
        if (moFlat(p, x, z) <= r) out.push(p);
    }
    return out;
}

function hOnLine(p, sx, sz, yaw, len, half) {
    var rad = (yaw + 90) * Math.PI / 180;
    var fx = Math.cos(rad), fz = Math.sin(rad);
    var dx = p.getX() - sx, dz = p.getZ() - sz;
    var along = dx * fx + dz * fz;
    if (along < -1 || along > len) return false;
    return Math.abs(dx * fz - dz * fx) <= half;
}

function hStrike(p, pct) {
    var dmg = p.getMaxHealth() * pct;
    if (p.getHealth() - dmg < 1) {
        p.clearPotionEffects();
        p.setHealth(1);
        Combat.dealPureDamage(p, 100, true);
        return;
    }
    p.setHealth(p.getHealth() - dmg);
    p.getWorld().playSoundAt(p.getPos(), "minecraft:entity.player.hurt", 1.0, 0.7);
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
    var id = hGet(npc, "herd", "");
    var kind = HERD[id];
    if (kind == null) return;
    var w = npc.getWorld();
    if (!moRunning(w)) {
        npc.despawn();
        return;
    }
    moSafeRead(w);
    if (hGet(npc, "herd_set", "0") != "1") {
        hPut(npc, "herd_set", 1);
        hSetup(npc, kind);
    }
    var now = moNow(w), until = hNum(npc, "herd_until"), to = hGet(npc, "herd_to", "");
    if (until > 0 && now >= until) {
        npc.despawn();
        return;
    }
    if (to != "" && !npc.getTimers().has(T_WALK)) npc.getTimers().forceStart(T_WALK, 2, true);
    // Вьючный в бой не вступает: идёт, пока его бьют.
    if (kind.loot > 0) {
        if (npc.getAttackTarget() != null) npc.setAttackTarget(null);
        return;
    }
    var hunts = kind.hunter == 1 || hPatrol(npc);
    if (hunts) hHunt(npc, w, now);
    if (id == "shaman") hShaman(npc, w, now);
    var t = npc.getAttackTarget();
    if (t == null) return;
    var astray = !hunts && to == "" && moFlat(npc, npc.getHomeX(), npc.getHomeZ()) > HERD_LEASH;
    if (astray || (t.typeOf(1) && moInSanctuary(t.getX(), t.getZ()))) npc.setAttackTarget(null);
    else hCast(npc, kind.casts, t, now);
}

function timer(e) {
    if (e.id == T_WALK) hWalk(e.npc);
    if (e.id != T_HIT) return;
    var npc = e.npc;
    if (!npc.isAlive()) return;
    var kind = HERD[hGet(npc, "herd", "")];
    var c = kind == null || kind.casts == null ? null : kind.casts[hNum(npc, "hit_i")];
    if (c == null) return;
    var x = hNum(npc, "hit_x"), z = hNum(npc, "hit_z"), yaw = hNum(npc, "hit_yaw");
    if (c.shape == "self") {
        x = npc.getX();
        z = npc.getZ();
    }
    var ps = hPlayersAt(npc, x, npc.getY(), z, c.shape == "line" ? c.len + 1 : c.size);
    for (var i = 0; i < ps.length; i++) {
        if (c.shape != "line" || hOnLine(ps[i], x, z, yaw, c.len, c.size)) hStrike(ps[i], c.pct);
    }
}

// Вьючный роняет добычу, убитые шаманы ритуала считаются: чанк с ними может быть далеко от Ока.
function died(e) {
    var npc = e.npc, id = hGet(npc, "herd", ""), kind = HERD[id], td = npc.getWorld().getTempdata();
    if (kind == null) return;
    if (kind.loot > 0) {
        moScatter(npc.getWorld(), npc.getX(), npc.getY(), npc.getZ(), kind.loot);
        td.put("mo_ev_end", "killed");
    }
    if (id == "shaman" && hGet(npc, "herd_camp", "") == "ev_ritual") td.put("mo_ev_kills", String(Number(td.has("mo_ev_kills") ? td.get("mo_ev_kills") : 0) + 1));
}

function hInZone(e) {
    return e != null && e.typeOf(1) && moInSanctuary(e.getX(), e.getZ());
}

function target(e) {
    if (hInZone(e.entity)) e.setCanceled(true);
}

function meleeAttack(e) {
    if (hInZone(e.target)) {
        e.setCanceled(true);
        return;
    }
    var kind = HERD[hGet(e.npc, "herd", "")];
    if (kind != null && kind.slow > 0 && e.target != null && e.target.typeOf(1)) e.target.addPotionEffect(2, kind.slow, 0, false);
}

function damaged(e) {
    if (hInZone(e.source)) {
        e.setCanceled(true);
        return;
    }
    if (hRangedOrBlast(e.damageSource)) e.damage = e.damage * RANGED_TAKEN;
}

// Снаряд, как у Кигора: помечен снарядом или ударило не то, что им бросило. Взрыв — по типу урона.
function hRangedOrBlast(ds) {
    var type = String(ds.getType());
    if (ds.isProjectile() || RANGED_TYPES[type] || type.indexOf("explosion") == 0) return true;
    var hit = ds.getImmediateSource(), from = ds.getTrueSource();
    return hit != null && from != null && String(hit.getUUID()) != String(from.getUUID());
}
