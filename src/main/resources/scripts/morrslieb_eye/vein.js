// vein.js — варп-жила, событие поля «Око Моррслиб». Файлы NPC по порядку: morrslieb_eye/mo_core.js, morrslieb_eye/vein.js.
// Клон ставит Око (eye.js) и пишет в storeddata «vein» и срок «vein_until». Прочность и неподвижность скрипт ставит сам
// на первом тике. Без метки — заготовка для клонера: стоит и ничего не делает.
// Жилу ломают. Каждый принятый удар — волна порчи на всех в VEIN_R: заряд порчи +1 и доля здоровья, тем больше, чем
// выше заряд; изредка из жилы выпадает осколок. На заряде VEIN_WEAK слабость, на VEIN_GLOW свечение для Кигора
// и охотников; отошёл дальше VEIN_R на VEIN_FADE — заряд снят. Тем, кто рядом, под ноги ложатся круги, через
// VEIN_SPIKE_HIT из них бьёт шип. На четверти прочности открывается ядро: дальний бой его больше не берёт, только вблизи.
// Разбили — ядро рассыпает остаток осколков. Не разбили к сроку — взрыв: порча по округе, половина остатка на землю,
// Око зовёт Кигоров. Сколько ни бей, с жилы выходит не больше VEIN_SHARDS осколков. Гул, созывающий стадо, — у Ока.
var TelegraphAPI = Java.type("noppes.npcs.telegraph.TelegraphAPI");
var Combat = Java.type("noppes.npcs.abilities.AbilityCombatHelper");

var VEIN_HP = 400, VEIN_R = 8;
// Волна порчи: доля здоровья VEIN_PULSE и ещё VEIN_PULSE_PER за каждый заряд; не чаще раза в VEIN_PULSE_GAP тиков.
var VEIN_PULSE = 0.02, VEIN_PULSE_PER = 0.01, VEIN_PULSE_GAP = 10;
var VEIN_WEAK = 3, VEIN_GLOW = 5, VEIN_MAX = 8, VEIN_FADE = 100;
// Осколки: за удар с шансом VEIN_CRUMB по одному, не больше VEIN_CRUMBS; всего с жилы VEIN_SHARDS, остаток — в ядре.
var VEIN_CRUMB = 0.2, VEIN_CRUMBS = 8, VEIN_SHARDS = 20;
// Шипы: до VEIN_SPIKES кругов радиусом VEIN_SPIKE_R раз в VEIN_SPIKE_EVERY, ниже половины прочности раз в VEIN_SPIKE_FAST.
var VEIN_SPIKES = 3, VEIN_SPIKE_R = 2, VEIN_SPIKE_EVERY = 160, VEIN_SPIKE_FAST = 100, VEIN_SPIKE_HIT = 30, VEIN_SPIKE = 0.15;
// Ядро — с этой доли прочности. Взрыв: доля здоровья VEIN_BURST всем в VEIN_BURST_R.
var VEIN_CORE = 0.25, VEIN_BURST = 0.3, VEIN_BURST_R = 16;
// От дальнего боя и взрывов до ядра проходит VEIN_RANGED_TAKEN, как у стада.
var VEIN_RANGED_TAKEN = 0.25, VEIN_RANGED_TYPES = { "wfm.splitting_shot": 1, "wfm.trap_bolt": 1 };
var VEIN_GREEN = 0xC040FF60 | 0, T_SPIKE = 1;

function vGet(npc, k, def) { var d = npc.getStoreddata(); return d.has(k) ? String(d.get(k)) : def; }
function vNum(npc, k) { return Number(vGet(npc, k, "0")); }
function vPut(npc, k, v) { npc.getStoreddata().put(k, String(v)); }
function vIs(npc) { return vGet(npc, "vein", "") == "1"; }

function vTemp(npc, k) {
    var t = npc.getTempdata();
    if (!t.has(k)) return {};
    try { return JSON.parse(String(t.get(k))); } catch (err) { return {}; }
}

function vTempPut(npc, k, v) { npc.getTempdata().put(k, JSON.stringify(v)); }

// Живые игроки в r по горизонтали, не в креативе и не наблюдатели.
function vPlayers(npc, r) {
    var list = npc.getWorld().getNearbyEntities(NpcAPI.getIPos(npc.getX(), npc.getY(), npc.getZ()), Math.ceil(r) + 1, 1), out = [];
    for (var i = 0; i < list.length; i++) {
        var p = list[i];
        if (!p.isAlive() || p.getGamemode() == 1 || p.getGamemode() == 3) continue;
        if (moFlat(p, npc.getX(), npc.getZ()) <= r) out.push(p);
    }
    return out;
}

// Доля здоровья мимо брони, как удары стада; последнее здоровье уходит чистым уроном, чтобы смерть была смертью.
function vStrike(p, pct) {
    var dmg = p.getMaxHealth() * pct;
    if (p.getHealth() - dmg < 1) {
        p.clearPotionEffects();
        p.setHealth(1);
        Combat.dealPureDamage(p, 100, true);
        return;
    }
    p.setHealth(p.getHealth() - dmg);
}

function vSay(npc, r, text) {
    var ps = vPlayers(npc, r);
    for (var i = 0; i < ps.length; i++) ps[i].message(text);
}

function vSetup(npc) {
    var st = npc.getStats(), ai = npc.getAi();
    st.setMaxHealth(VEIN_HP);
    npc.setHealth(VEIN_HP);
    st.getMelee().setStrength(0);
    st.setAggroRange(1);
    ai.setRetaliateType(3);
    ai.setMovingType(0);
    ai.setReturnsHome(false);
    vPut(npc, "vein_x", npc.getX());
    vPut(npc, "vein_y", npc.getY());
    vPut(npc, "vein_z", npc.getZ());
}

function init(e) {
    if (!e.npc.isAlive()) return;
    e.npc.getTimers().clear();
}

// Раз в 10 тиков: на месте, заряды порчи, шипы, срок.
function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive() || !vIs(npc)) return;
    var w = npc.getWorld(), td = w.getTempdata();
    // Ивент кончился или Око уже закрыло событие — жила уходит вместе с ним.
    if (!moRunning(w) || !td.has("mo_ev") || String(td.get("mo_ev")).indexOf("vein:") != 0) {
        npc.despawn();
        return;
    }
    moSafeRead(w);
    if (vGet(npc, "vein_set", "0") != "1") {
        vPut(npc, "vein_set", 1);
        vSetup(npc);
    }
    Combat.holdInPlace(npc, vNum(npc, "vein_x"), vNum(npc, "vein_y"), vNum(npc, "vein_z"));
    var now = moNow(w);
    if (now >= vNum(npc, "vein_until")) return vBurst(npc, w);
    vCharges(npc, w, now);
    if (now >= vNum(npc, "spike_at")) vSpikes(npc, now);
    if (vCore(npc)) w.spawnParticle("end_rod", npc.getX(), npc.getY() + 1.5, npc.getZ(), 0.4, 0.8, 0.4, 0.02, 6);
}

// Заряды: у кого он есть и кто рядом — слабость и свечение; кто отошёл на VEIN_FADE — без заряда.
function vCharges(npc, w, now) {
    var ch = vTemp(npc, "vein_charge"), near = vPlayers(npc, VEIN_R), seen = {};
    for (var i = 0; i < near.length; i++) {
        var p = near[i], id = String(p.getUUID()), c = ch[id];
        seen[id] = true;
        if (c == null) continue;
        c.away = 0;
        if (c.n >= VEIN_WEAK) p.addPotionEffect(18, 2, 0, false);
        if (c.n >= VEIN_GLOW) moMark(w, p, 60);
    }
    for (var k in ch) {
        if (seen[k]) continue;
        ch[k].away += 10;
        if (ch[k].away >= VEIN_FADE) delete ch[k];
    }
    vTempPut(npc, "vein_charge", ch);
}

// Волна от удара: всем в VEIN_R заряд +1 и доля здоровья по их заряду.
function vPulse(npc, w, now) {
    if (now < vNum(npc, "pulse_at")) return;
    vPut(npc, "pulse_at", now + VEIN_PULSE_GAP);
    var ch = vTemp(npc, "vein_charge"), near = vPlayers(npc, VEIN_R);
    for (var i = 0; i < near.length; i++) {
        var p = near[i], id = String(p.getUUID()), c = ch[id] || { n: 0, away: 0 };
        if (moInSanctuary(p.getX(), p.getZ())) continue;
        c.n = Math.min(VEIN_MAX, c.n + 1);
        c.away = 0;
        ch[id] = c;
        vStrike(p, VEIN_PULSE + VEIN_PULSE_PER * c.n);
        if (c.n == VEIN_WEAK) p.message("§5Порча жилы въедается в тебя. Отойди, чтобы она схлынула.");
        if (c.n == VEIN_GLOW) p.message("§5Ты светишься порчей – тебя видят Кигор и охотники.");
    }
    vTempPut(npc, "vein_charge", ch);
    w.spawnParticle("crit", npc.getX(), npc.getY() + 1, npc.getZ(), VEIN_R / 2, 0.5, VEIN_R / 2, 0.1, 24);
    w.playSoundAt(npc.getPos(), "minecraft:block.respawn_anchor.charge", 1.0, 0.6);
}

// Шипы: круг под ногами у тех, кто рядом; удар — через VEIN_SPIKE_HIT, по тем, кто так и стоит в круге.
function vSpikes(npc, now) {
    var fast = npc.getHealth() <= npc.getMaxHealth() * 0.5;
    vPut(npc, "spike_at", now + (fast ? VEIN_SPIKE_FAST : VEIN_SPIKE_EVERY));
    var near = vPlayers(npc, VEIN_R + 4), spots = [];
    for (var i = 0; i < near.length && spots.length < VEIN_SPIKES; i++) {
        var p = near[i];
        if (moInSanctuary(p.getX(), p.getZ())) continue;
        spots.push([p.getX(), p.getY(), p.getZ()]);
        TelegraphAPI.circle(npc, p.getX(), p.getY(), p.getZ(), VEIN_SPIKE_R, VEIN_SPIKE_HIT, VEIN_GREEN);
    }
    if (spots.length == 0) return;
    vTempPut(npc, "vein_spikes", spots);
    npc.getTimers().forceStart(T_SPIKE, VEIN_SPIKE_HIT - 1, false);
}

function timer(e) {
    var npc = e.npc;
    if (e.id != T_SPIKE || !npc.isAlive() || !vIs(npc)) return;
    var spots = vTemp(npc, "vein_spikes"), w = npc.getWorld();
    for (var i = 0; i < spots.length; i++) {
        var s = spots[i], list = w.getNearbyEntities(NpcAPI.getIPos(s[0], s[1], s[2]), VEIN_SPIKE_R + 1, 1);
        w.spawnParticle("explosion", s[0], s[1] + 0.5, s[2], 0.5, 0.5, 0.5, 0, 2);
        for (var k = 0; k < list.length; k++) {
            var p = list[k];
            if (!p.isAlive() || p.getGamemode() == 1 || p.getGamemode() == 3 || moFlat(p, s[0], s[2]) > VEIN_SPIKE_R) continue;
            vStrike(p, VEIN_SPIKE);
            p.addPotionEffect(2, 2, 1, false);
        }
    }
    npc.getTempdata().remove("vein_spikes");
}

function vCore(npc) { return npc.getHealth() <= npc.getMaxHealth() * VEIN_CORE; }

function vLeft(npc) { return Math.max(0, VEIN_SHARDS - vNum(npc, "crumbs")); }

// Не разбили к сроку: порча по округе, половина остатка на землю; Кигоров зовёт Око по «burst».
function vBurst(npc, w) {
    var ps = vPlayers(npc, VEIN_BURST_R);
    for (var i = 0; i < ps.length; i++) if (!moInSanctuary(ps[i].getX(), ps[i].getZ())) vStrike(ps[i], VEIN_BURST);
    var half = Math.floor(vLeft(npc) / 2);
    if (half > 0) moScatter(w, npc.getX(), npc.getY() + 1, npc.getZ(), half);
    w.spawnParticle("explosion", npc.getX(), npc.getY() + 1, npc.getZ(), 2, 1, 2, 0, 12);
    w.playSoundAt(npc.getPos(), "minecraft:entity.generic.explode", 2.0, 0.6);
    w.getTempdata().put("mo_ev_end", "burst");
    npc.despawn();
}

// Удар по жиле: из сейф-зоны не ранить; дальний бой и взрывы до ядра — на четверть, по ядру — никак. Принятый удар — волна
// порчи и, может быть, осколок.
function damaged(e) {
    var npc = e.npc;
    if (!vIs(npc)) return;
    var src = e.source;
    if (src != null && src.typeOf(1) && moInSanctuary(src.getX(), src.getZ())) {
        e.setCanceled(true);
        return;
    }
    if (vRangedOrBlast(e.damageSource)) {
        if (vCore(npc)) {
            e.setCanceled(true);
            return;
        }
        e.damage = e.damage * VEIN_RANGED_TAKEN;
    }
    var w = npc.getWorld(), now = moNow(w), wasCore = vCore(npc);
    vPulse(npc, w, now);
    if (vNum(npc, "crumbs") < VEIN_CRUMBS && Math.random() < VEIN_CRUMB) {
        vPut(npc, "crumbs", vNum(npc, "crumbs") + 1);
        moScatter(w, npc.getX(), npc.getY() + 1, npc.getZ(), 1);
        w.playSoundAt(npc.getPos(), "minecraft:block.glass.break", 1.0, 1.2);
    }
    if (!wasCore && npc.getHealth() - e.damage <= npc.getMaxHealth() * VEIN_CORE) {
        vSay(npc, VEIN_BURST_R * 2, "§5Ядро варп-жилы открыто: издалека его больше не взять, только вблизи.");
        w.playSoundAt(npc.getPos(), "minecraft:block.beacon.power_select", 1.0, 0.5);
    }
}

// Разбита: ядро высыпает всё, что осталось.
function died(e) {
    var npc = e.npc;
    if (!vIs(npc)) return;
    var w = npc.getWorld(), left = vLeft(npc);
    vPut(npc, "crumbs", VEIN_SHARDS);
    if (left > 0) moScatter(w, npc.getX(), npc.getY() + 1, npc.getZ(), left);
    w.getTempdata().put("mo_ev_end", "broken");
}

// Жила не дерётся: ни целей, ни ударов.
function target(e) { e.setCanceled(true); }
function meleeAttack(e) { e.setCanceled(true); }

// Снаряд, как у стада: помечен снарядом или ударило не то, что им бросило. Взрыв — по типу урона.
function vRangedOrBlast(ds) {
    if (ds == null) return false;
    var type = String(ds.getType());
    if (ds.isProjectile() || VEIN_RANGED_TYPES[type] || type.indexOf("explosion") == 0) return true;
    var hit = ds.getImmediateSource(), from = ds.getTrueSource();
    return hit != null && from != null && String(hit.getUUID()) != String(from.getUUID());
}
