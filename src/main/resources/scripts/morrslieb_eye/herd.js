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

// aggro — на сколько блоков замечает, wander — сколько бродит от точки, 0 — не бродит, hunter — охотник на свет,
// fly — летает, slow — на сколько секунд замедляет ударом.
var HERD = {
    ungor: { hp: 75, melee: 5, aggro: 10, speed: 5, wander: 8 },
    tuskgor: { hp: 150, melee: 7, aggro: 8, speed: 5, wander: 10, cast: TUSK_CHARGE },
    gor: { hp: 200, melee: 8, aggro: 12, speed: 5, wander: 3, cast: GOR_SPIN },
    sniffer: { hp: 50, melee: 4, aggro: 64, speed: 6, wander: 0, hunter: 1, name: "Унгор-нюхач" },
    centigor: { hp: 140, melee: 7, aggro: 64, speed: 8, wander: 0, cast: CENT_CHARGE, hunter: 1, slow: 2 },
    harpy: { hp: 65, melee: 5, aggro: 10, speed: 7, wander: 12, fly: 1 },
    shaman: { hp: 165, melee: 4, aggro: 14, speed: 4, wander: 3, cast: SHAMAN_CURSE }
};
var HUNT_LOSE = 48, HUNT_LOSE_TICKS = 200;
// Поводок: обитатель дальше HERD_LEASH от дома бросает цель и идёт домой, иначе игрок уводит стоянку через всё поле.
var HERD_LEASH = 32;
// Шаман: раз в SHAMAN_EVERY тиков лечит стадо в SHAMAN_R на SHAMAN_HEAL здоровья, пока дерётся.
var SHAMAN_EVERY = 120, SHAMAN_R = 10, SHAMAN_HEAL = 0.12;
// Имя из Global → Factions, буква в букву.
var HERD_FACTION = "Враждебные", HERD_RED = 0xC0FF3030 | 0;
var T_HIT = 1;

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

function hSetup(npc, kind) {
    var st = npc.getStats(), ai = npc.getAi();
    // Сама она ИИ не пересобирает: это делает setMovingType ниже.
    ai.setNavigationType(kind.fly == 1 ? 1 : 0);
    st.setMaxHealth(kind.hp);
    npc.setHealth(kind.hp);
    st.getMelee().setStrength(kind.melee);
    st.setAggroRange(kind.aggro);
    ai.setRetaliateType(0);
    ai.setWalkingSpeed(kind.speed);
    ai.setMovingType(kind.wander > 0 ? 1 : 0);
    if (kind.wander > 0) ai.setWanderingRange(kind.wander);
    ai.setReturnsHome(kind.wander > 0);
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
        && moFlat(p, npc.getX(), npc.getZ()) <= HUNT_LOSE && moShards(p) > 0;
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

// Приём вида: откат, расстояние, показ фигуры. Через hitAt таймер бьёт долей здоровья всех, кто в ней.
function hCast(npc, c, t, now) {
    if (c == null || !t.isAlive() || now < hNum(npc, "cast_at")) return;
    var dist = moFlat(t, npc.getX(), npc.getZ());
    if (dist < c.min || dist > c.max) return;
    var yaw = Combat.computeYaw(t.getX() - npc.getX(), t.getZ() - npc.getZ());
    if (!AbilityAPI.start(npc, c.ability, t, c.params())) return;
    hPut(npc, "cast_at", now + c.cd);
    hPut(npc, "hit_yaw", yaw);
    hPut(npc, "hit_x", c.shape == "target" ? t.getX() : npc.getX());
    hPut(npc, "hit_z", c.shape == "target" ? t.getZ() : npc.getZ());
    if (c.shape == "line") TelegraphAPI.line(npc, npc.getX(), npc.getY(), npc.getZ(), yaw, c.len, c.size * 2, c.hitAt, HERD_RED);
    if (c.shape == "target") TelegraphAPI.circle(npc, t.getX(), t.getY(), t.getZ(), c.size, c.hitAt, HERD_RED);
    npc.getTimers().forceStart(T_HIT, c.hitAt, false);
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
    var now = moNow(w);
    if (kind.hunter == 1) hHunt(npc, w, now);
    if (id == "shaman") hShaman(npc, w, now);
    var t = npc.getAttackTarget();
    if (t == null) return;
    var astray = kind.hunter != 1 && moFlat(npc, npc.getHomeX(), npc.getHomeZ()) > HERD_LEASH;
    if (astray || (t.typeOf(1) && moInSanctuary(t.getX(), t.getZ()))) npc.setAttackTarget(null);
    else hCast(npc, kind.cast, t, now);
}

function timer(e) {
    if (e.id != T_HIT) return;
    var npc = e.npc;
    if (!npc.isAlive()) return;
    var kind = HERD[hGet(npc, "herd", "")];
    var c = kind == null ? null : kind.cast;
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
    if (hInZone(e.source)) e.setCanceled(true);
}
