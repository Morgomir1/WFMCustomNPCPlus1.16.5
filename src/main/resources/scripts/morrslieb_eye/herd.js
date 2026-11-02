// herd.js — зверолюды поля ивента «Око Моррслиб». Файлы NPC по порядку: morrslieb_eye/mo_core.js, herdstone/hs_core.js,
// herdstone/hs_mob.js, morrslieb_eye/herd.js. Файлы Herdstone не меняются: приёмы ведёт hs_mob по таблице CASTS этого вида.
// Клон ставит Око (eye.js) и пишет вид в storeddata «herd». Числа вида, фракцию и ходьбу скрипт выставляет сам на первом тике:
// в окне NPC нужны только модель, скин и таблица дропа.
// Без вида — заготовка для клонера: её ставят руками, чтобы настроить и сохранить. Стоит и ничего не делает.
// Ивент выключили — стадо уходит с поля. В сейф-зоне и у алтаря оно никого не трогает и оттуда его не ранить: иначе удар
// срывал бы 30 секунд выхода, а зона стала бы укрытием, как у Кигора.
// Нюхач идёт на свет своей добычи: кто погас, вошёл в зону или ушёл дальше SNIFF_LOSE, того он теряет и через
// SNIFF_LOSE_TICKS уходит.

// Рывок бивнегора: полоса, задетым доля здоровья. Вращение гора: круг вокруг него.
var TUSK_CHARGE = { ability: "dash", cd: 160, min: 4, max: 12, shape: "line", len: 10, size: 1.5, hitAt: 34, pct: 0.12,
    params: function () { return AbilityAPI.params("chargeTicks", 28, "distance", 10.0, "damage", 4.0); } };
var GOR_SPIN = { ability: "weapon_spin", cd: 140, min: 0, max: 4, shape: "self", size: 3.5, hitAt: 40, pct: 0.15,
    params: function () { return AbilityAPI.params("chargeTicks", 16, "activeTicks", 24, "radius", 3.5, "damagePerTick", 1.5); } };

// aggro — на сколько блоков замечает, wander — сколько бродит от точки, 0 — не бродит.
var HERD = {
    ungor: { hp: 60, melee: 4, aggro: 10, speed: 5, wander: 8, casts: [] },
    tuskgor: { hp: 120, melee: 6, aggro: 8, speed: 5, wander: 10, casts: [TUSK_CHARGE] },
    gor: { hp: 160, melee: 7, aggro: 12, speed: 5, wander: 3, casts: [GOR_SPIN] },
    sniffer: { hp: 40, melee: 3, aggro: 64, speed: 6, wander: 0, casts: [], name: "Унгор-нюхач" }
};
var SNIFF_LOSE = 48, SNIFF_LOSE_TICKS = 200;

function hSetup(npc, kind) {
    var st = npc.getStats(), ai = npc.getAi();
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

// Цель нюхача — его добыча; держит, пока она светится, рядом и не в зоне.
function hSniff(npc, w, now) {
    var id = hsGet(npc, "herd_prey", ""), p = null;
    try { p = w.getEntity(id); } catch (err) {}
    var ok = p != null && p.isAlive() && !moInSanctuary(p.getX(), p.getZ())
        && hsFlat(p, npc.getX(), npc.getZ()) <= SNIFF_LOSE && moShards(p) > 0;
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
    } else if (now - lost >= SNIFF_LOSE_TICKS) {
        npc.despawn();
    }
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
    if (id == "sniffer") hSniff(npc, w, hsNow(npc));
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
    if (hInZone(e.target)) e.setCanceled(true);
}

function damaged(e) {
    if (hInZone(e.source)) e.setCanceled(true);
}
