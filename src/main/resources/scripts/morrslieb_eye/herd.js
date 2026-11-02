// herd.js — зверолюды поля ивента «Око Моррслиб». Файлы NPC по порядку: morrslieb_eye/mo_core.js, herdstone/hs_core.js,
// herdstone/hs_mob.js, morrslieb_eye/herd.js. Файлы Herdstone не меняются: приёмы ведёт hs_mob по таблице CASTS этого вида.
// Клон ставит Око (eye.js) и пишет вид в storeddata «herd». Числа вида, фракцию и ходьбу скрипт выставляет сам на первом тике:
// в окне NPC нужны только модель, скин и таблица дропа.
// Без вида — заготовка для клонера: её ставят руками, чтобы настроить и сохранить. Стоит и ничего не делает.
// Ивент выключили — стадо уходит с поля. В сейф-зоне и у алтаря оно никого не трогает и оттуда его не ранить: иначе удар
// срывал бы 30 секунд выхода, а зона стала бы укрытием, как у Кигора.
// Охотники, нюхач и центигор, идут на свет своей добычи: кто погас, вошёл в зону или ушёл дальше HUNT_LOSE, того они теряют
// и через HUNT_LOSE_TICKS уходят. Гарпия летает, это настройка навигации NPC. Шаман лечит стадо рядом, а кого заметил, того
// подсвечивает: такого видит и Кигор.

// Рывок бивнегора и центигора: полоса, задетым доля здоровья. Вращение гора: круг вокруг него. Проклятие шамана: круг на цели.
var TUSK_CHARGE = { ability: "dash", cd: 160, min: 4, max: 12, shape: "line", len: 10, size: 1.5, hitAt: 34, pct: 0.12,
    params: function () { return AbilityAPI.params("chargeTicks", 28, "distance", 10.0, "damage", 4.0); } };
var GOR_SPIN = { ability: "weapon_spin", cd: 140, min: 0, max: 4, shape: "self", size: 3.5, hitAt: 40, pct: 0.15,
    params: function () { return AbilityAPI.params("chargeTicks", 16, "activeTicks", 24, "radius", 3.5, "damagePerTick", 1.5); } };

var CENT_CHARGE = { ability: "dash", cd: 200, min: 5, max: 14, shape: "line", len: 12, size: 1.5, hitAt: 34, pct: 0.15,
    params: function () { return AbilityAPI.params("chargeTicks", 28, "distance", 12.0, "damage", 5.0); } };
var SHAMAN_CURSE = { ability: "grasping_dead", cd: 200, min: 2, max: 16, shape: "target", size: 3, hitAt: 40, pct: 0.18,
    params: function () { return AbilityAPI.params("chargeTicks", 20, "radius", 3.0, "effectType", "slowness", "effectAmplifier", 1); } };

// aggro — на сколько блоков замечает, wander — сколько бродит от точки, 0 — не бродит, hunter — охотник на свет,
// fly — летает, slow — на сколько секунд замедляет ударом.
var HERD = {
    ungor: { hp: 60, melee: 4, aggro: 10, speed: 5, wander: 8, casts: [] },
    tuskgor: { hp: 120, melee: 6, aggro: 8, speed: 5, wander: 10, casts: [TUSK_CHARGE] },
    gor: { hp: 160, melee: 7, aggro: 12, speed: 5, wander: 3, casts: [GOR_SPIN] },
    sniffer: { hp: 40, melee: 3, aggro: 64, speed: 6, wander: 0, casts: [], hunter: 1, name: "Унгор-нюхач" },
    centigor: { hp: 110, melee: 6, aggro: 64, speed: 8, wander: 0, casts: [CENT_CHARGE], hunter: 1, slow: 2 },
    harpy: { hp: 50, melee: 4, aggro: 10, speed: 7, wander: 12, casts: [], fly: 1 },
    shaman: { hp: 130, melee: 3, aggro: 14, speed: 4, wander: 3, casts: [SHAMAN_CURSE] }
};
var HUNT_LOSE = 48, HUNT_LOSE_TICKS = 200;
// Шаман: раз в SHAMAN_EVERY тиков лечит стадо в SHAMAN_R на SHAMAN_HEAL здоровья, пока дерётся.
var SHAMAN_EVERY = 120, SHAMAN_R = 10, SHAMAN_HEAL = 0.12;

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
    var id = hsFactionId(HS.FACTION_HERD);
    if (id >= 0) npc.setFaction(id);
    if (kind.name != null) npc.getDisplay().setName(kind.name);
}

// Цель охотника — его добыча; держит, пока она светится, рядом и не в зоне.
function hHunt(npc, w, now) {
    var id = hsGet(npc, "herd_prey", ""), p = null;
    try { p = w.getEntity(id); } catch (err) {}
    var ok = p != null && p.isAlive() && !moInSanctuary(p.getX(), p.getZ())
        && hsFlat(p, npc.getX(), npc.getZ()) <= HUNT_LOSE && moShards(p) > 0;
    if (ok) {
        hsPut(npc, "herd_lost", 0);
        var t = npc.getAttackTarget();
        if (t == null || String(t.getUUID()) != id) npc.setAttackTarget(p);
        return;
    }
    var lost = hsNum(npc, "herd_lost");
    if (lost == 0) {
        hsPut(npc, "herd_lost", now);
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
    if (now < hsNum(npc, "heal_at")) return;
    hsPut(npc, "heal_at", now + SHAMAN_EVERY);
    var list = w.getNearbyEntities(npc.getPos(), SHAMAN_R, 2);
    for (var i = 0; i < list.length; i++) {
        var a = list[i];
        if (a.isAlive() && a.getStoreddata().has("herd")) a.setHealth(Math.min(a.getMaxHealth(), a.getHealth() + a.getMaxHealth() * SHAMAN_HEAL));
    }
    w.spawnParticle("happy_villager", npc.getX(), npc.getY() + 1, npc.getZ(), 3, 1, 3, 0, 20);
}

// Приходит из тика hs_mob, раньше приёмов.
function hsMobExtra(npc) {
    var id = hsGet(npc, "herd", "");
    var kind = HERD[id];
    if (kind == null) return;
    var w = npc.getWorld();
    if (!moRunning(w)) {
        npc.despawn();
        return;
    }
    moSafeRead(w);
    CASTS = kind.casts;
    if (hsGet(npc, "herd_set", "0") != "1") {
        hsPut(npc, "herd_set", 1);
        hSetup(npc, kind);
    }
    if (kind.hunter == 1) hHunt(npc, w, hsNow(npc));
    if (id == "shaman") hShaman(npc, w, hsNow(npc));
    var t = npc.getAttackTarget();
    if (t != null && t.typeOf(1) && moInSanctuary(t.getX(), t.getZ())) npc.setAttackTarget(null);
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
    var kind = HERD[hsGet(e.npc, "herd", "")];
    if (kind != null && kind.slow > 0 && e.target != null && e.target.typeOf(1)) e.target.addPotionEffect(2, kind.slow, 0, false);
}

function damaged(e) {
    if (hInZone(e.source)) e.setCanceled(true);
}
