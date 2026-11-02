// Брюннер ивента «Око Моррслиб»: охотник за осколками. Заказ даёт Око (eye.js): самый яркий игрок поля, его UUID —
// в данных клона (thief_prey). Бой перенесён из охотника за головами (npc/special/brunner/brunner.js v1.5) и живёт
// здесь сам по себе: стойки мечника и стрелка, арбалет, пистоль, ножи, сеть, дым, «Палач», метки ударов, погоня
// «верхом». Без дверей, посредника, контракта, кошеля и удержания чанка: на поле они не нужны.
// Добыча погибла или вышла — он подбирает её осколки с земли и минуту уходит прочь от центра поля; догнали и убили —
// роняет всё забранное, не догнали — осколки ушли с ним. Добыча сдала осколки, простояла в сейф-зоне TH_ZONE_TICKS
// или охота шла TH_HUNT_TICKS — уходит ни с чем; пока добыча в зоне, он стоит и не бьёт.
// Файлы скрипта клона по порядку: mo_core.js, thief.js. Без добычи (поставлен руками, чтобы сохранить в клонер) стоит.
var BR_Helper = Java.type("noppes.npcs.abilities.AbilityCombatHelper");
var TH_Ability = Java.type("noppes.npcs.abilities.AbilityAPI");
var BR_Class = Java.type("java.lang.Class");
var BR_Thread = Java.type("java.lang.Thread");
var BR_MethodHandles = Java.type("java.lang.invoke.MethodHandles");
var BR_MethodType = Java.type("java.lang.invoke.MethodType");

// --- Ивент ---

// Здоровье и сила удара из меню заменяются этими: удары Брюннера — множители силы.
var TH_HP = 900, TH_STRENGTH = 7, TH_FACTION = "Враждебные";
// Осколки погибшей или вышедшей добычи он подбирает в TH_PICK_R от места, где видел её последний раз, если сам не дальше
// TH_SEEN_R; ищет их TH_ROB_TICKS — предметы падают в том же тике, что и смерть, но позже события.
var TH_PICK_R = 8, TH_SEEN_R = 24, TH_ROB_TICKS = 40;
var TH_ZONE_TICKS = 160, TH_HUNT_TICKS = 3600;
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

// --- Бой ---

// Броню и меч надевает при первом тике, если ячейка в меню пуста; "" — не трогать.
var EQUIP = true;
var GEAR = { armor: ["wfm:brunner_the_bounty_hunter_helmet", "wfm:brunner_the_bounty_hunter_chestplate",
        "wfm:brunner_the_bounty_hunter_leggings", "wfm:brunner_the_bounty_hunter_boots"],
    sword: "wfm:brunner_sword", crossbow: "minecraft:crossbow", knife: "wfm:iron_dagger", headsman: "wfm:iron_dagger", pistol: "" };
var START_GRACE_TICKS = 40; // После первых слов не стреляет столько тиков.
// Передвижение.
var FIGHT_SPEED = 1.35;
var CHASE_SPEED = 1.5; // В стойке мечника к далёкой цели бежит быстрее.
var SHOOT_MIN = 5; // Стойка стрелка: ближе — отходит.
var SHOOT_MAX = 11; // Дальше — подходит.
var DUEL_RANGE = 2.5; // Стойка мечника: на каком расстоянии кружит.
var CATCHUP_DISTANCE = 48; // Дальше — догоняет «верхом»: появляется за спиной цели вне взгляда, под стук копыт.
var STUCK_TICKS = 80; // 4 секунды без сближения — тоже.
var TRAP_TICKS = 60; // 3 секунды на месте, а до цели не добраться (яма, уступ, стена) — выбраться на край.
var RIDE_COOLDOWN_TICKS = 160;
var RIDE_MIN = 14;
var RIDE_MAX = 20;
var RIDE_AHEAD_MIN = 12; // Бегущую цель обгоняет и встречает впереди на её пути.
var RIDE_AHEAD_MAX = 18;
var OTHER_PLAYER_CLEARANCE = 4;
// Стойки: мечник идёт вплотную и давит, стрелок держит дистанцию. Начинает с START_STANCE. Мечник уходит в стрельбу
// только в дыму (шашка) или когда мечом цель не достать (она выше или ниже HIGH_TICKS подряд); стрелок возвращается
// к мечу по времени, а раньше — когда цель сама подошла (RUSH_RANGE на RUSH_TICKS), попалась в сеть или ранена до добивания.
var START_STANCE = "melee";
var RANGED_TICKS = 100;
var MELEE_TICKS = 300;
var RUSH_RANGE = 3.5;
var RUSH_TICKS = 15;
var HIGH_TICKS = 20;
// Атаки. on — включена; weight — вес при выборе; damage — множитель силы удара; время в тиках (1/20 с); min и max —
// дистанция. Замах: стоит, на земле метка, звучит сигнал; после атаки — передышка ATTACK_GAP_TICKS.
// Такт цикла в тиках. Таким он был во всех боях охотника за головами; ритм и снаряды подобраны под него.
var LOOP_TICKS = 2;
var ATTACK_GAP_TICKS = 12;
var MELEE_GAP_TICKS = 10; // После меча, выпада и «Палача» — короче, чем после выстрела.
var MIN_WINDUP_TICKS = 8;
var AIM_LOCK_TICKS = 6; // Последние 0,3 с замаха прицел не следит за целью; щелчок — пора уходить с метки.
var AIM_LEAD_MAX = 4;
var SHOT_PAST = 5; // Снаряд летит до точки прицела и ещё столько блоков, но не дальше своей дальности (max).
// Арбалет: очередь болтов, каждый со своей меткой. Щит и укрытие спасают. speed у снарядов — блоков за тик.
var CROSSBOW = { on: true, windup: 14, cooldown: 50, weight: 3, damage: 1.0, bolts: 3, gap: 8, min: 5, max: 22, speed: 1.0, radius: 0.8 };
// Пистоль: долгий прицел, выстрел мгновенный, pierce единиц урона проходит сквозь броню и щит. lock — фиксация прицела.
// Спасает шаг вбок после щелчка или стена. Во второй фазе стреляет и в упор (до pointBlank блоков), с коротким замахом.
var PISTOL = { on: true, windup: 24, cooldown: 240, weight: 2, damage: 1.8, pierce: 3, min: 4, max: 26, width: 1.2, lock: 8, phase2Min: 0, phase2Windup: 16, pointBlank: 3.5 };
// Метательные ножи: веер из count ножей через spread градусов (зона — конус); попавший нож замедляет на slow секунд.
var KNIVES = { on: true, windup: 10, cooldown: 80, weight: 2, damage: 0.8, count: 5, spread: 10, min: 3, max: 14, speed: 0.7, radius: 0.7, slow: 3, slowLevel: 2 };
// Сеть: урона нет, щит не спасает; держит на месте root секунд — и он идёт вплотную.
var NET = { on: true, windup: 14, cooldown: 200, weight: 1, min: 4, max: 14, speed: 0.55, radius: 1.0, root: 4, rootLevel: 5 };
// Меч: серия ударов под одной меткой; gap — между ударами серии, cooldown — пауза до следующей серии.
var SWORD = { on: true, windup: 10, cooldown: 20, weight: 6, hits: 3, gap: 10, reach: 3.8, width: 2.6, damage: 1.2 };
// Выпад: в стойке мечника рывком догоняет цель по полосе и бьёт; stop — где остановится перед ней. Спасает шаг вбок.
var LUNGE = { on: true, windup: 10, cooldown: 60, weight: 6, min: 3.5, max: 9, width: 2.0, damage: 1.3, stop: 1.8 };
// «Палач»: добивание, когда у цели не больше health от здоровья.
var HEADSMAN = { on: true, health: 0.35, windup: 14, cooldown: 80, weight: 9, reach: 3.2, width: 2.2, damage: 3.0 };
// Дымовая шашка: стойка мечника кончилась — дым и отход на min–max блоков.
var SMOKE = { on: true, windup: 8, cooldown: 200, radius: 3, min: 8, max: 11 };
// Вторая фаза — с этой доли здоровья: перезарядки короче (множитель), пистоль в упор.
var PHASE2_HEALTH = 0.5;
var PHASE2_COOLDOWN = 0.6;
var RETALIATE = true; // Отвечает тем, кто его ударил.
var RETALIATE_TICKS = 200;
var RETALIATE_RANGE = 24;
// Реплики в местный чат. {name} — ник добычи.
var SAY = true;
var SAY_COOLDOWN_TICKS = 200;
var SAY_LINES = {
    start: ["{name}. Осколки при тебе – значит, и ты мой. Ничего личного.", "{name}. Варп-камень стоит дороже тебя. Ничего личного."],
    pistol: ["Стой смирно.", "Порох дороже тебя. Не дёргайся."],
    net: ["Живым ты никому не нужен. Но так удобнее."],
    melee: ["Ближе. Так быстрее."],
    headsman: ["Не дёргайся. \"Палач\" не любит неровных срезов."],
    smoke: ["Подыши."],
    phase2: ["Цена только что выросла.", "Хватит честной работы."],
    retaliate: ["За тебя не платят. Не лезь."],
    rob: ["Варп-камень мой."],
    time: ["Хватит. Живи пока."],
    death: ["Осколки… забирайте."]
};

var BR_KEY = "thief_fight"; // tempdata: состояние боя.
var BR_STRIKE_KEY = "thief_strike"; // tempdata: кого бьёт скрипт прямо сейчас.
var BR_AGGRO_KEY = "thief_aggro";
var BR_TICK_TIMER = 90511; // Цикл боя: таймер с числом 0 — каждый тик NPC (с числом N срабатывает раз в N+1).
var BR_SHOT_COLOR = 0xC0E0E0E0 | 0;
var BR_PISTOL_COLOR = 0xC0FF8C1A | 0;
var BR_NET_COLOR = 0xC08B6B3A | 0;
var BR_BLADE_COLOR = 0xC0FF3030 | 0;
var BR_handles = {};
var BR_telegraph = null;
var BR_loopAt = null; // Тик мира последнего прохода цикла.
var BR_every = {}; // Когда в последний раз сработала каждая периодическая проверка.
var BR_DEFS = { crossbow: CROSSBOW, pistol: PISTOL, knives: KNIVES, net: NET, sword: SWORD, lunge: LUNGE, headsman: HEADSMAN, smoke: SMOKE };

// --- События CustomNPCs ---

function init(e) {
    var npc = e.npc;
    npc.getTempdata().put(BR_AGGRO_KEY, "{}");
    brSave(npc, brNewState(npc));
    npc.getTimers().forceStart(BR_TICK_TIMER, 0, true);
}

// Раз в 10 тиков: ход ивента, добыча, её осколки и сейф-зона. Бой идёт в timer: CustomNPCs обновляет таймеры каждый тик NPC.
function tick(e) {
    var npc = e.npc;
    if (!npc.getTimers().has(BR_TICK_TIMER)) npc.getTimers().forceStart(BR_TICK_TIMER, 0, true);
    if (!tEvent(npc) || !npc.isAlive()) return;
    var w = npc.getWorld();
    if (!moRunning(w)) return tVanish(npc);
    moSafeRead(w);
    var now = moNow(w);
    if (tGet(npc, "thief_set", "0") != "1") {
        tPut(npc, "thief_set", 1);
        tSetup(npc, now);
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
    if (now - tNum(npc, "hunt_at") >= TH_HUNT_TICKS) return tGone(npc, p, "time");
}

// Цикл боя — раз в LOOP_TICKS тиков мира, только на охоте и пока добыча не в зоне; шаг побега — свой таймер.
function timer(e) {
    var npc = e.npc;
    if (e.id == TH_WALK) return tWalk(npc);
    if (e.id != BR_TICK_TIMER || !tEvent(npc) || tGet(npc, "thief_set", "0") != "1") return;
    if (tGet(npc, "thief_phase", "hunt") != "hunt") return;
    if (tGet(npc, "prey_zone", "0") == "1") return brPause(npc);
    var now = brNow(npc);
    if (BR_loopAt != null && now >= BR_loopAt && now - BR_loopAt < Number(LOOP_TICKS)) return;
    BR_loopAt = now;
    brMain(npc);
}

function target(e) {
    e.setCanceled(true); // Бой меню не нужен: Брюннер сам выбирает, куда идти и чем бить.
}

// Из сейф-зоны его не ранить, как Кигора и стадо; от дальнего боя и взрывов проходит TH_RANGED_TAKEN.
// Другой игрок, ударивший его, на время становится противником.
function damaged(e) {
    var npc = e.npc, src = e.source;
    if (!tEvent(npc)) return;
    if (tInZone(src)) {
        e.setCanceled(true);
        return;
    }
    if (tRangedOrBlast(e.damageSource)) e.damage = e.damage * TH_RANGED_TAKEN;
    if (!RETALIATE || src == null || src.getType() != 1) return;
    var id = String(src.getUUID());
    if (brLoad(npc).stopped || id == tGet(npc, "thief_prey", "")) return;
    var list = brLoadAggro(npc);
    list[id] = brNow(npc) + RETALIATE_TICKS;
    brSaveAggro(npc, list);
}

// Удар NPC проходит только от скрипта: у него был замах. Атаки ИИ из меню — без предупреждения. В сейф-зону не бьёт.
function meleeAttack(e) {
    var npc = e.npc, data = npc.getTempdata();
    if (tInZone(e.target) || !data.has(BR_STRIKE_KEY) || String(data.get(BR_STRIKE_KEY)) != String(e.target.getUUID())) {
        e.setCanceled(true);
        return;
    }
    var k = brLoad(npc).atk;
    if (k != null) e.damage = Number(e.damage) * Number(BR_DEFS[k.kind].damage || 1);
}

// Убит: всё забранное падает на землю, таблица дропа клона — награда за него.
function died(e) {
    var npc = e.npc, state = brLoad(npc);
    brEndAttack(npc, state);
    state.stopped = true;
    brSave(npc, state);
    if (!tEvent(npc)) return;
    var w = npc.getWorld(), n = tNum(npc, "stolen");
    tPut(npc, "stolen", 0);
    if (n > 0) {
        brSay(npc, state, "death", true, {});
        moScatter(w, npc.getX(), npc.getY(), npc.getZ(), n);
    }
    tSay(w, "§a[Око Моррслиб] §fБрюннер убит" + (n > 0 ? ", " + n + " осколков на земле." : "."));
}

// --- Ход ивента ---

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

// Первый тик клона, вызванного Оком: срок охоты считается с этого тика; здоровье, сила и фракция — ивента;
// регенерации и возрождения нет; ИИ меню ничего не решает.
function tSetup(npc, now) {
    tPut(npc, "hunt_at", now);
    var ai = npc.getAi();
    ai.setReturnsHome(false);
    ai.setMovingType(0);
    ai.setNavigationType(0);
    ai.setRetaliateType(0);
    ai.setDoorInteract(1);
    ai.setCanSwim(true);
    ai.setLeapAtTarget(false);
    try {
        var st = npc.getStats();
        st.setMaxHealth(TH_HP);
        npc.setHealth(TH_HP);
        st.getMelee().setStrength(TH_STRENGTH);
        st.setHealthRegen(0);
        st.setCombatRegen(0);
        st.setRespawnType(3);
    } catch (err) {
        moLogOnce("thief_stats", "здоровье и сила Брюннера не выставлены: " + err);
    }
    var id = tFactionId(TH_FACTION);
    if (id >= 0) npc.setFaction(id);
    else moLogOnce("thief_faction", "нет фракции " + TH_FACTION + ": Брюннер остался в фракции клона");
    if (EQUIP) brEquip(npc);
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

// Добыча погибла или вышла: её осколки рассыпались там, где он видел её последний раз. Дальше TH_SEEN_R — не его.
function tStartRob(npc, w, now) {
    if (moFlat(npc, tNum(npc, "last_x"), tNum(npc, "last_z")) > TH_SEEN_R) return tVanish(npc);
    tStopFight(npc);
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
    brSay(npc, brLoad(npc), "rob", true, {});
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
    npc.setRotation(BR_Helper.computeYaw(dx, dz));
    BR_Helper.holdInPlace(npc, nx, ny, nz);
    if (ny < y) BR_Helper.clearFallDistance(npc);
}

// Уходит ни с чем: строка добыче и прощальная реплика, если она есть.
function tGone(npc, p, why) {
    if (p != null && TH_GONE_TEXT[why]) p.message(TH_GONE_TEXT[why]);
    brSay(npc, brLoad(npc), why, true, {});
    tVanish(npc);
}

function tStopFight(npc) {
    try {
        var state = brLoad(npc);
        brEndAttack(npc, state);
        state.stopped = true;
        brSave(npc, state);
    } catch (err) {}
    npc.setAttackTarget(null);
    npc.clearNavigation();
}

function tVanish(npc) {
    tStopFight(npc);
    TH_Ability.cancel(npc);
    npc.getTimers().stop(TH_WALK);
    npc.getWorld().spawnParticle("cloud", npc.getX(), npc.getY() + 1, npc.getZ(), 0.6, 1.0, 0.6, 0.02, 20);
    npc.despawn();
}

// Снаряд, как у Кигора: помечен снарядом или ударило не то, что им бросило. Взрыв — по типу урона.
function tRangedOrBlast(ds) {
    if (ds == null) return false;
    var type = String(ds.getType());
    if (ds.isProjectile() || TH_RANGED_TYPES[type] || type.indexOf("explosion") == 0) return true;
    var hit = ds.getImmediateSource(), from = ds.getTrueSource();
    return hit != null && from != null && String(hit.getUUID()) != String(from.getUUID());
}

// --- Цикл боя ---

// Периодическая проверка: true не чаще раза в ticks тиков мира. Считает прошедшее время, а не номер тика —
// цикл видит не каждый тик, и «now%N==0» на части тиков не срабатывает никогда.
function brEvery(key, ticks, now) {
    var last = BR_every[key];
    if (last != null && now >= last && now - last < ticks) return false;
    BR_every[key] = now;
    return true;
}

function brMain(npc) {
    var state = brLoad(npc), now = brNow(npc);
    if (state.stopped || !npc.isAlive() || now < (state.retryAt || 0)) return;
    try {
        if (state.hoofs > 0 && brEvery("hoofs", 4, now)) {
            state.hoofs--;
            brSound(npc, npc.getX(), npc.getY(), npc.getZ(), "minecraft:entity.horse.gallop", 1.0, 1.0);
        }
        var prey = tPrey(npc);
        if (prey != null && (!prey.isAlive() || prey.getGamemode() == 3)) prey = null;
        var foe = brAggressor(npc, now) || prey;
        if (foe == null) {
            brEndAttack(npc, state);
            npc.setAttackTarget(null);
            npc.clearNavigation();
            state.foe = "";
            brProgress(state, now);
            brSave(npc, state);
            return;
        }
        var foeId = String(foe.getUUID()), hunting = prey != null && foeId == String(prey.getUUID());
        if (foeId != state.foe) {
            brEndAttack(npc, state);
            npc.clearNavigation();
            state.foe = foeId;
            state.tx = null;
            brProgress(state, now);
            if (!hunting) brSay(npc, state, "retaliate", true);
        }
        if (hunting && !state.started) brStartHunt(npc, foe, state, now);
        brPhase(npc, state, now);
        if (now < (state.holdUntil || 0)) {
            brPause(npc);
            state.lastTick = now;
            brSave(npc, state);
            return;
        }
        brTrack(foe, state, now);
        var elapsed = Math.max(0, Math.min(20, now - state.lastTick));
        state.lastTick = now;
        var distance = Math.sqrt(brDistSq(npc, foe));
        if (state.mode == "attack") {
            state.trapAt = now;
            brAttackTick(npc, foe, state, now);
            brSave(npc, state);
            return;
        }
        var near = state.stance == "melee" ? Number(DUEL_RANGE) + 2 : Number(SHOOT_MAX);
        if (state.bestRange == null || distance <= state.bestRange - 1 || (distance <= near && npc.canSeeEntity(foe))) {
            state.bestRange = distance;
            state.stuck = 0;
        } else state.stuck += elapsed;
        // Яма или уступ: стоит на месте, а до цели не добраться — на край.
        if (brTrapped(npc, foe, state, now, distance)) {
            brEscape(npc, foe, state, now);
            brSave(npc, state);
            return;
        }
        if (hunting && (distance > CATCHUP_DISTANCE || state.stuck >= STUCK_TICKS) && now - (state.lastRide || -1000000) >= RIDE_COOLDOWN_TICKS) {
            state.lastRide = now;
            if (brRide(npc, foe, state, now)) {
                brProgress(state, now);
                brSave(npc, state);
                return;
            }
        }
        brFightTick(npc, foe, state, now, distance, elapsed);
        brSave(npc, state);
    } catch (error) {
        brEndAttack(npc, state);
        brFail(npc, state, error);
    }
}

function brNewState(npc) {
    return { foe: "", stopped: false, started: false, mode: "fight", atk: null, cd: {}, attackReadyAt: 0, phase2: false,
        stance: null, stanceUntil: 0, rush: 0, lastRide: -1000000, stuck: 0, bestRange: null, lastTick: brNow(npc),
        vx: 0, vz: 0, hoofs: 0 };
}

function brStartHunt(npc, foe, state, now) {
    state.started = true;
    brSay(npc, state, "start", true, { name: String(foe.getName()) });
    state.attackReadyAt = now + Number(START_GRACE_TICKS); // Сказал — и дал две секунды понять, что происходит.
}

function brProgress(state, now) {
    state.lastTick = now;
    state.stuck = 0;
    state.bestRange = null;
}

function brFail(npc, state, error) {
    state.retryAt = brNow(npc) + 100;
    brSave(npc, state);
    try {
        npc.setAttackTarget(null);
        npc.clearNavigation();
    } catch (ignored) {}
    moLogOnce("thief_fail:" + error, "сбой боя Брюннера, повтор через 5 секунд: " + error);
}

function brPause(npc) {
    npc.setAttackTarget(null);
    npc.clearNavigation();
    npc.setMotionX(0);
    npc.setMotionZ(0);
}

function brFace(npc, foe) { npc.setRotation(Number(BR_Helper.computeYaw(foe.getX() - npc.getX(), foe.getZ() - npc.getZ()))); }

// Скорость цели сглаживается: по ней выстрел берёт упреждение, а «верхом» он обгоняет бегущую.
function brTrack(foe, state, now) {
    var x = foe.getX(), z = foe.getZ();
    if (state.tx == null) {
        state.vx = 0;
        state.vz = 0;
    } else if (now > (state.tAt || 0)) {
        var dt = now - state.tAt;
        state.vx = (state.vx || 0) * 0.7 + (x - state.tx) / dt * 0.3;
        state.vz = (state.vz || 0) * 0.7 + (z - state.tz) / dt * 0.3;
    }
    state.tx = x;
    state.tz = z;
    state.tAt = now;
}

function brPhase(npc, state, now) {
    if (state.phase2 || !state.started || !(PHASE2_HEALTH > 0) || npc.getHealth() > npc.getMaxHealth() * PHASE2_HEALTH) return;
    state.phase2 = true;
    brSound(npc, npc.getX(), npc.getY(), npc.getZ(), "minecraft:item.crossbow.loading_end", 1.0, 0.6);
    brSay(npc, state, "phase2", true);
}

// --- Бой: стойка, выбор атаки, движение ---

function brFightTick(npc, foe, state, now, distance, elapsed) {
    npc.setAttackTarget(null);
    brFace(npc, foe);
    brStanceTick(npc, foe, state, now, distance, elapsed);
    var attack = brPickAttack(npc, foe, state, now, distance);
    if (attack != null) {
        brStartAttack(npc, foe, state, now, attack);
        return;
    }
    if (state.stance == "melee") brDuelStep(npc, foe, state, now, distance);
    else brKeepRange(npc, foe, state, now, distance);
}

function brLow(foe) {
    try { return HEADSMAN.on && foe.getMaxHealth() > 0 && Number(foe.getHealth()) / Number(foe.getMaxHealth()) <= Number(HEADSMAN.health); }
    catch (error) { return false; }
}

function brStanceTick(npc, foe, state, now, distance, elapsed) {
    if (state.stance == null) {
        brSetStance(npc, state, now, String(START_STANCE) == "ranged" ? "ranged" : "melee");
        return;
    }
    var rooted = now < (state.rootedUntil || 0), low = brLow(foe);
    if (state.stance == "ranged") {
        state.rush = distance <= RUSH_RANGE ? (state.rush || 0) + elapsed : 0;
        if (rooted || low || state.rush >= RUSH_TICKS || now >= state.stanceUntil) {
            brSetStance(npc, state, now, "melee");
            brSay(npc, state, low ? "headsman" : "melee", false);
        }
        return;
    }
    // Мечник не отходит сам: стойка кончилась — уйдёт в дыму, когда шашка готова (решит выбор атаки), а до тех пор давит.
    // Мечом не достать (цель выше или ниже) — стреляет.
    state.high = Math.abs(foe.getY() - npc.getY()) >= 1.8 && distance <= Number(LUNGE.max) ? (state.high || 0) + elapsed : 0;
    if (state.high >= HIGH_TICKS && !rooted) brSetStance(npc, state, now, "ranged");
}

function brSetStance(npc, state, now, stance) {
    state.stance = stance;
    state.rush = 0;
    state.high = 0;
    state.stanceUntil = now + (stance == "melee" ? Number(MELEE_TICKS) : Number(RANGED_TICKS)) + Math.floor(Math.random() * 40);
    brHold(npc, stance == "melee" ? "sword" : "crossbow");
}

// Что в правой руке: меч, арбалет, нож, «Палач». Пустой id — не менять.
function brHold(npc, what) {
    var id = String(GEAR[what] || "");
    if (id == "") return;
    try {
        var inv = npc.getInventory(), now = inv.getRightHand();
        if (now != null && !now.isEmpty() && String(now.getName()) == id) return;
        inv.setRightHand(npc.getWorld().createItem(id, 1));
        npc.updateClient();
    } catch (error) {
        brOnce("hold:" + what, "предмет " + id + " Брюннеру в руку не взять: " + error);
    }
}

function brKeepRange(npc, foe, state, now, distance) {
    if (!brEvery("move", 10, now)) return;
    if (brEvery("orbit", 100, now)) state.orbitDir = -(state.orbitDir || 1);
    var dx = npc.getX() - foe.getX(), dz = npc.getZ() - foe.getZ(), d = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
    var ux = dx / d, uz = dz / d, r = distance;
    if (distance < SHOOT_MIN) r = Number(SHOOT_MIN) + 2;
    else if (distance > SHOOT_MAX) r = Number(SHOOT_MAX) - 2;
    else {
        var a = Math.atan2(uz, ux) + (state.orbitDir || 1) * 0.35;
        ux = Math.cos(a);
        uz = Math.sin(a);
    }
    npc.navigateTo(foe.getX() + ux * r, foe.getY(), foe.getZ() + uz * r, FIGHT_SPEED);
}

function brDuelStep(npc, foe, state, now, distance) {
    if (!brEvery("move", 10, now)) return;
    var r = Number(DUEL_RANGE);
    if (distance > r + 0.7) {
        npc.navigateTo(foe.getX(), foe.getY(), foe.getZ(), distance > 6 ? CHASE_SPEED : FIGHT_SPEED);
        return;
    }
    if (brEvery("orbit", 100, now)) state.orbitDir = -(state.orbitDir || 1);
    var dx = npc.getX() - foe.getX(), dz = npc.getZ() - foe.getZ(), a = Math.atan2(dz, dx) + (state.orbitDir || 1) * 0.5;
    npc.navigateTo(foe.getX() + Math.cos(a) * r, foe.getY(), foe.getZ() + Math.sin(a) * r, FIGHT_SPEED);
}

function brPickAttack(npc, foe, state, now, h) {
    if (now < (state.attackReadyAt || 0)) return null;
    var cd = state.cd || {}, melee = state.stance == "melee", low = brLow(foe), rooted = now < (state.rootedUntil || 0);
    function ready(kind) { return BR_DEFS[kind].on && now >= (cd[kind] || 0); }
    // Стойка мечника кончилась — дым и отход, если шашка готова.
    if (melee && now >= state.stanceUntil && !low && !rooted && ready("smoke")) return "smoke";
    if (!npc.canSeeEntity(foe)) return null;
    var dy = foe.getY() - npc.getY(), list = [], total = 0;
    function add(kind, ok, w) {
        if (!ok || !ready(kind) || !(w > 0)) return;
        list.push({ kind: kind, w: w });
        total += w;
    }
    var pistolMin = state.phase2 ? Number(PISTOL.phase2Min) : Number(PISTOL.min);
    if (melee) {
        // Мечник не стоит и не стреляет по цели, до которой достаёт выпадом: бежит и бьёт. Стреляет только вдогонку
        // далёкой цели, а пистоль во второй фазе — ещё и в упор.
        var flat = Math.abs(dy) < 1.8, far = h > Number(LUNGE.max);
        add("headsman", low && flat && h <= HEADSMAN.reach - 0.5, Number(HEADSMAN.weight));
        add("sword", flat && h <= SWORD.reach - 0.5, Number(SWORD.weight));
        add("lunge", Math.abs(dy) < 2.5 && h >= LUNGE.min && h <= LUNGE.max, Number(LUNGE.weight));
        add("pistol", (far || (state.phase2 && h <= Number(PISTOL.pointBlank))) && h >= pistolMin && h <= PISTOL.max, Number(PISTOL.weight));
        add("knives", far && h <= KNIVES.max, Number(KNIVES.weight));
        add("net", far && !rooted && h <= NET.max, Number(NET.weight));
    } else {
        add("crossbow", h >= CROSSBOW.min && h <= CROSSBOW.max, Number(CROSSBOW.weight));
        add("pistol", h >= pistolMin && h <= PISTOL.max, Number(PISTOL.weight));
        add("knives", h >= KNIVES.min && h <= KNIVES.max, Number(KNIVES.weight));
        add("net", !rooted && h >= NET.min && h <= NET.max, Number(NET.weight));
    }
    if (total <= 0) return null;
    var r = Math.random() * total;
    for (var i = 0; i < list.length; i++) {
        r -= list[i].w;
        if (r < 0) return list[i].kind;
    }
    return list[list.length - 1].kind;
}

function brWindup(kind, state) {
    var a = BR_DEFS[kind], w = kind == "pistol" && state.phase2 ? Number(a.phase2Windup) : Number(a.windup);
    return Math.max(Number(MIN_WINDUP_TICKS), Math.round(w));
}

function brLock(kind) { return kind == "pistol" ? Number(PISTOL.lock) : Number(AIM_LOCK_TICKS); }

function brStartAttack(npc, foe, state, now, kind) {
    var ticks = brWindup(kind, state);
    state.mode = "attack";
    state.rush = 0;
    var k = state.atk = { kind: kind, phase: "windup", start: now, until: now + ticks, tg: "", yaw: 0, locked: false,
        sx: npc.getX(), sy: npc.getY(), sz: npc.getZ(), tx: foe.getX(), ty: foe.getY(), tz: foe.getZ(), len: 0, shots: [], fired: 0, hits: 0 };
    brPause(npc);
    if (kind == "smoke") {
        k.tg = brCircle(npc, { x: k.sx, y: k.sy, z: k.sz }, SMOKE.radius, ticks, BR_SHOT_COLOR);
        brSound(npc, k.sx, k.sy, k.sz, "minecraft:entity.tnt.primed", 1.0, 1.6);
        brSay(npc, state, "smoke", false);
        return;
    }
    brHold(npc, (kind == "sword" || kind == "lunge") ? "sword" : kind == "headsman" ? "headsman" : kind == "knives" ? "knife" : kind == "pistol" ? "pistol" : kind == "net" ? "" : "crossbow");
    // Одна метка на всю атаку (на всю очередь болтов и всю серию ударов); за прицелом её поворачивает brTurnAim.
    var a = BR_DEFS[kind], repeats = kind == "crossbow" ? Number(a.bolts) : Number(a.hits || 1);
    var total = ticks + Math.max(0, Math.round(repeats) - 1) * Math.max(Number(MIN_WINDUP_TICKS), Number(a.gap || MIN_WINDUP_TICKS));
    brAim(npc, foe, state, k);
    npc.setRotation(k.yaw);
    brDrawAim(npc, k, total);
    var sound = kind == "crossbow" ? "minecraft:item.crossbow.loading_middle" : kind == "pistol" ? "minecraft:block.tripwire.click_on"
        : kind == "knives" ? "minecraft:item.trident.throw" : kind == "net" ? "minecraft:entity.fishing_bobber.throw"
        : kind == "headsman" ? "minecraft:entity.player.attack.knockback" : kind == "lunge" ? "minecraft:entity.horse.jump" : "minecraft:entity.player.attack.sweep";
    brSound(npc, k.sx, k.sy, k.sz, sound, 1.0, kind == "pistol" ? 0.6 : 0.8);
    if (kind == "pistol" || kind == "net" || kind == "headsman") brSay(npc, state, kind, false);
}

function brAttackTick(npc, foe, state, now) {
    var k = state.atk;
    if (k == null) {
        state.mode = "fight";
        return;
    }
    brPause(npc);
    if (k.shots.length > 0) brShotsTick(npc, foe, state, now);
    if (state.atk == null) return;
    if (k.phase == "windup") {
        var left = k.until - now;
        if (k.kind == "smoke") {
            if (left % 3 == 0) brRing(npc, k.sx, k.sy, k.sz, SMOKE.radius, "minecraft:smoke");
        } else {
            brAimTick(npc, foe, state, k, left);
            if (left % 3 == 0 && !k.tg) brAimParticles(npc, k); // Частицы — только если мода меток нет.
        }
        if (now < k.until) return;
        if (k.kind == "sword" || k.kind == "headsman") brBladeHit(npc, foe, state, now);
        else if (k.kind == "pistol") brPistolFire(npc, foe, state, now);
        else if (k.kind == "lunge") brLungeHit(npc, foe, state, now);
        else if (k.kind == "smoke") brSmokeHit(npc, foe, state, now);
        else brLaunch(npc, foe, state, now);
        return;
    }
    // Снаряды в полёте: новых не будет — ждём, пока долетят.
    npc.setRotation(k.yaw);
    if (k.shots.length == 0 || now >= k.until) brAttackEnd(npc, state, now);
}

// Итог атаки: перезарядка этой атаки (во второй фазе короче) и общая передышка.
function brAttackEnd(npc, state, now) {
    var k = state.atk;
    if (k == null) return;
    brRemoveTelegraph(npc, k.tg);
    if (state.cd == null) state.cd = {};
    state.cd[k.kind] = now + Math.round(Number(BR_DEFS[k.kind].cooldown) * (state.phase2 ? Number(PHASE2_COOLDOWN) : 1));
    var blade = k.kind == "sword" || k.kind == "lunge" || k.kind == "headsman";
    state.attackReadyAt = now + (k.caught ? 0 : blade ? Number(MELEE_GAP_TICKS) : Number(ATTACK_GAP_TICKS));
    state.atk = null;
    if (state.mode == "attack") state.mode = "fight";
    brHold(npc, state.stance == "melee" ? "sword" : "crossbow");
}

// Прервать атаку без перезарядки: смена противника, конец охоты.
function brEndAttack(npc, state) {
    if (state.atk != null) brRemoveTelegraph(npc, state.atk.tg);
    state.atk = null;
    if (state.mode == "attack") state.mode = "fight";
}

// Прицел следит за целью и фиксируется со щелчком перед ударом. Снаряд берёт упреждение по бегу цели.
function brAim(npc, foe, state, k) {
    var a = BR_DEFS[k.kind], px = foe.getX(), pz = foe.getZ(), dx0 = px - npc.getX(), dz0 = pz - npc.getZ(), d0 = Math.sqrt(dx0 * dx0 + dz0 * dz0);
    if (a.speed > 0) {
        var lead = brLock(k.kind) + d0 / Number(a.speed);
        var ox = (state.vx || 0) * lead, oz = (state.vz || 0) * lead, o = Math.sqrt(ox * ox + oz * oz), cap = Number(AIM_LEAD_MAX);
        if (o > cap) {
            ox *= cap / o;
            oz *= cap / o;
        }
        px += ox;
        pz += oz;
    }
    k.tx = px;
    k.ty = foe.getY();
    k.tz = pz;
    k.yaw = Number(BR_Helper.computeYaw(px - npc.getX(), pz - npc.getZ()));
    k.len = a.reach > 0 ? Number(a.reach) : Number(a.max) + (k.kind == "lunge" ? 1 : 2); // Вся дальность выстрела: метка одна и не удлиняется.
}

function brAimTick(npc, foe, state, k, left) {
    if (k.locked) {
        npc.setRotation(k.yaw);
        return;
    }
    brAim(npc, foe, state, k);
    npc.setRotation(k.yaw);
    brTurnAim(npc, k);
    // До щелчка метка идёт за целью вместе с прицелом; после щелчка ни прицел, ни метка не двигаются.
    if (left <= brLock(k.kind)) {
        k.locked = true;
        brSound(npc, npc.getX(), npc.getY(), npc.getZ(), "minecraft:block.note_block.hat", 1.0, 2.0); // Щелчок фиксации.
    }
}

function brAimWidth(k) {
    var a = BR_DEFS[k.kind];
    return a.width > 0 ? Number(a.width) : Number(a.radius) * 2;
}

// Зона атаки: полоса, у веера ножей — конус. Рисуется один раз на всю атаку, дальше её ведёт brTurnAim.
function brDrawAim(npc, k, ticks) {
    brRemoveTelegraph(npc, k.tg);
    k.markUntil = brNow(npc) + ticks;
    k.drawnYaw = k.yaw;
    k.drawnX = k.sx;
    k.drawnZ = k.sz;
    var color = k.kind == "pistol" ? BR_PISTOL_COLOR : k.kind == "net" ? BR_NET_COLOR : (k.kind == "sword" || k.kind == "headsman" || k.kind == "lunge") ? BR_BLADE_COLOR : BR_SHOT_COLOR;
    if (k.kind == "knives") k.tg = brCone(npc, k.sx, k.sy, k.sz, k.yaw, k.len, brFanHalfAngle(), ticks, color);
    else k.tg = brBand(npc, k.sx, k.sy, k.sz, k.yaw, k.len, brAimWidth(k), ticks, color);
}

// Повернуть метку за прицелом, не пересоздавая её: та же метка (тот же номер) отправляется клиентам заново с новым
// направлением и началом. Возраст метки едет в пакете, поэтому её пульсация не начинается сначала — мерцания нет.
// Привязка метки к NPC (follow) не годится: она берёт поворот из yRot, а setRotation CustomNPCs меняет только корпус.
function brTurnAim(npc, k) {
    if (!k.tg) return;
    var x = npc.getX(), z = npc.getZ();
    if (Math.abs(Number(k.yaw) - Number(k.drawnYaw)) < 0.5 && Math.abs(x - k.drawnX) < 0.05 && Math.abs(z - k.drawnZ) < 0.05) return;
    try {
        var mark = brNative("com.wfm.telegraph.TelegraphServer", "get", "(Ljava/lang/String;)Lcom/wfm/telegraph/TelegraphInstance;", null, [String(k.tg)]);
        if (mark == null) return; // Уже истекла.
        mark.yaw = k.yaw;
        mark.x = x;
        mark.z = z;
        brNative("com.wfm.telegraph.TelegraphServer", "spawn", "(Lnet/minecraft/world/World;Lcom/wfm/telegraph/TelegraphInstance;)Ljava/lang/String;",
            null, [npc.getWorld().getMCWorld(), mark]);
        k.drawnYaw = k.yaw;
        k.drawnX = x;
        k.drawnZ = z;
    } catch (error) {
        // Мод меток устроен иначе, чем ожидалось: метка всё равно должна показывать правду — перерисовать на остаток атаки.
        brOnce("turn", "метку Брюннера не повернуть на месте, перерисовываю её при каждом повороте: " + error);
        k.sx = x;
        k.sz = z;
        brDrawAim(npc, k, Math.max(1, Number(k.markUntil) - brNow(npc)));
    }
}

// Половина угла веера: крайние ножи плюс их ширина на средней дистанции.
function brFanHalfAngle() {
    var n = Math.max(1, Math.round(Number(KNIVES.count)));
    return (n - 1) / 2 * Number(KNIVES.spread) + Math.atan(Number(KNIVES.radius) / Math.max(1, Number(KNIVES.max) / 2)) * 180 / Math.PI;
}

function brAimParticles(npc, k) {
    var rad = (k.yaw + 90) * Math.PI / 180;
    for (var s = 1; s <= k.len; s += 1) brParticles(npc, "minecraft:crit", k.sx + Math.cos(rad) * s, k.sy + 0.15, k.sz + Math.sin(rad) * s, 0, 0, 0, 0, 1);
}

function brInBand(npc, foe, yaw, len, width, tall) {
    var rad = (Number(yaw) + 90) * Math.PI / 180, ux = Math.cos(rad), uz = Math.sin(rad);
    var dx = foe.getX() - npc.getX(), dz = foe.getZ() - npc.getZ();
    var along = dx * ux + dz * uz, side = Math.abs(dz * ux - dx * uz);
    return along >= -0.5 && along <= len && side <= width / 2 && (tall || Math.abs(foe.getY() - npc.getY()) < 2);
}

// Меч и «Палач»: полоса перед ним; в серии каждый следующий удар — с новой меткой, своим замахом и щелчком.
function brBladeHit(npc, foe, state, now) {
    var k = state.atk, m = BR_DEFS[k.kind], rad = (k.yaw + 90) * Math.PI / 180;
    var inside = brInBand(npc, foe, k.yaw, m.reach, m.width, false);
    brParticles(npc, "minecraft:sweep_attack", npc.getX() + Math.cos(rad) * 1.5, npc.getY() + 1, npc.getZ() + Math.sin(rad) * 1.5, 0.3, 0.1, 0.3, 0, 2);
    npc.setMotionX(Math.cos(rad) * 0.3);
    npc.setMotionZ(Math.sin(rad) * 0.3);
    try { npc.swingMainhand(); } catch (ignored) {}
    var hit = inside && npc.canSeeEntity(foe) && brStrike(npc, foe, state);
    brSound(npc, npc.getX(), npc.getY(), npc.getZ(), hit ? "minecraft:entity.player.attack.strong" : "minecraft:entity.player.attack.sweep", 1.0, k.kind == "headsman" ? 0.7 : 1.0);
    k.hits = (k.hits || 0) + 1;
    if (k.hits < Number(m.hits || 1) && foe.isAlive()) {
        var gap = Math.max(Number(MIN_WINDUP_TICKS), Number(m.gap || MIN_WINDUP_TICKS));
        k.sx = npc.getX();
        k.sy = npc.getY();
        k.sz = npc.getZ();
        k.until = now + gap;
        k.locked = false;
        brAim(npc, foe, state, k);
        npc.setRotation(k.yaw);
        brTurnAim(npc, k); // Метка та же: скрипт переносит её к нему.
        return;
    }
    brAttackEnd(npc, state, now);
}

// Выпад: рывок по зафиксированной полосе к цели и удар мечом. Обрыв, стена или опасный блок останавливают рывок.
function brLungeHit(npc, foe, state, now) {
    var k = state.atk, rad = (k.yaw + 90) * Math.PI / 180, ux = Math.cos(rad), uz = Math.sin(rad), world = npc.getWorld();
    var x0 = npc.getX(), z0 = npc.getZ(), y = npc.getY(), spot = null;
    var inside = brInBand(npc, foe, k.yaw, k.len, LUNGE.width, false);
    var along = (foe.getX() - x0) * ux + (foe.getZ() - z0) * uz, want = Math.max(0, Math.min(Number(LUNGE.max), along) - Number(LUNGE.stop));
    for (var s = 0.5; s <= want; s += 0.5) {
        var x = x0 + ux * s, z = z0 + uz * s, gy = Number(BR_Helper.findGroundY(world, x, z, y + 1));
        if (Math.abs(gy - y) > 1.2 || brBlocked(world, x, gy + 0.5, z) || brBlocked(world, x, gy + 1.5, z) || !brClearLanding(world, x, gy, z)) break;
        y = gy;
        spot = { x: x, y: gy, z: z };
        if (s % 1 == 0) brParticles(npc, "minecraft:cloud", x, gy + 0.6, z, 0.1, 0.2, 0.1, 0.01, 2);
    }
    if (spot != null) brTeleport(npc, spot);
    try { npc.swingMainhand(); } catch (ignored) {}
    var hit = inside && npc.canSeeEntity(foe) && brStrike(npc, foe, state);
    brParticles(npc, "minecraft:sweep_attack", npc.getX() + ux * 1.2, npc.getY() + 1, npc.getZ() + uz * 1.2, 0.3, 0.1, 0.3, 0, 2);
    brSound(npc, npc.getX(), npc.getY(), npc.getZ(), hit ? "minecraft:entity.player.attack.strong" : "minecraft:entity.player.attack.sweep", 1.0, 0.9);
    brAttackEnd(npc, state, now);
}

// Пистоль: выстрел мгновенный, по зафиксированной полосе; стена между ними спасает. В сейф-зону пуля не идёт.
function brPistolFire(npc, foe, state, now) {
    var k = state.atk, rad = (k.yaw + 90) * Math.PI / 180, ux = Math.cos(rad), uz = Math.sin(rad), y = npc.getY() + 1.4;
    brRemoveTelegraph(npc, k.tg);
    k.tg = "";
    try { npc.swingMainhand(); } catch (ignored) {}
    brSound(npc, npc.getX(), y, npc.getZ(), "minecraft:entity.generic.explode", 1.0, 1.8);
    brSound(npc, npc.getX(), y, npc.getZ(), "minecraft:entity.firework_rocket.blast", 1.0, 0.7);
    brParticles(npc, "minecraft:large_smoke", npc.getX() + ux, y, npc.getZ() + uz, 0.2, 0.2, 0.2, 0.02, 10);
    for (var s = 2; s <= k.len; s += 2) brParticles(npc, "minecraft:smoke", npc.getX() + ux * s, y, npc.getZ() + uz * s, 0.02, 0.02, 0.02, 0, 1);
    if (brInBand(npc, foe, k.yaw, k.len, PISTOL.width, true) && npc.canSeeEntity(foe) && !tInZone(foe)) {
        brStrike(npc, foe, state);
        brPierce(foe, Number(PISTOL.pierce));
        brParticles(npc, "minecraft:crit", foe.getX(), foe.getY() + 1, foe.getZ(), 0.3, 0.4, 0.3, 0.2, 12);
    }
    brAttackEnd(npc, state, now);
}

// Арбалет, ножи, сеть: снаряд летит speed блоков за тик по прямой; болты — очередью, ножи — веером.
function brLaunch(npc, foe, state, now) {
    var k = state.atk, a = BR_DEFS[k.kind];
    try { npc.swingMainhand(); } catch (ignored) {}
    if (k.kind == "knives") {
        var n = Math.max(1, Math.round(Number(a.count)));
        for (var i = 0; i < n; i++) brShoot(npc, k, (i - (n - 1) / 2) * Number(a.spread));
        k.fired = n;
        brSound(npc, k.sx, k.sy, k.sz, "minecraft:entity.arrow.shoot", 1.0, 1.5);
    } else {
        brShoot(npc, k, 0);
        k.fired++;
        brSound(npc, k.sx, k.sy, k.sz, k.kind == "net" ? "minecraft:entity.fishing_bobber.throw" : "minecraft:item.crossbow.shoot", 1.0, k.kind == "net" ? 0.6 : 1.0);
    }
    var total = k.kind == "crossbow" ? Math.max(1, Math.round(Number(a.bolts))) : k.fired;
    if (k.fired < total && foe.isAlive()) {
        // Следующий болт: та же метка, он снова ведёт цель и снова щёлкает.
        var gap = Math.max(Number(MIN_WINDUP_TICKS), Number(a.gap || MIN_WINDUP_TICKS));
        k.until = now + gap;
        k.locked = false;
        brAim(npc, foe, state, k);
        npc.setRotation(k.yaw);
        brTurnAim(npc, k);
        return;
    }
    brRemoveTelegraph(npc, k.tg);
    k.tg = "";
    k.phase = "active";
    k.until = now + Math.ceil((Number(a.max) + 1) / Number(a.speed)) + Number(LOOP_TICKS);
}

function brShoot(npc, k, offsetDeg) {
    var sx = npc.getX(), sy = npc.getY() + 1.3, sz = npc.getZ();
    var dx = k.tx - sx, dy = k.ty + 1 - sy, dz = k.tz - sz, flat = Math.sqrt(dx * dx + dz * dz);
    if (flat < 0.3) {
        var rad = (k.yaw + 90) * Math.PI / 180;
        dx = Math.cos(rad);
        dz = Math.sin(rad);
        dy = 0;
    }
    var o = Number(offsetDeg) * Math.PI / 180, cos = Math.cos(o), sin = Math.sin(o), rx = dx * cos - dz * sin, rz = dx * sin + dz * cos;
    var len = Math.sqrt(rx * rx + dy * dy + rz * rz);
    var reach = Math.min(Number(BR_DEFS[k.kind].max) + 1, len + Number(SHOT_PAST));
    k.shots.push({ x: sx, y: sy, z: sz, dx: rx / len, dy: dy / len, dz: rz / len, gone: 0, reach: reach });
}

function brShotsTick(npc, foe, state, now) {
    var k = state.atk, a = BR_DEFS[k.kind], world = npc.getWorld(), step = Number(a.speed) * Number(LOOP_TICKS) / 2, keep = []; // Два шага за цикл.
    var trail = k.kind == "net" ? "minecraft:item_slime" : "minecraft:crit";
    for (var s = 0; s < k.shots.length; s++) {
        var b = k.shots[s], done = false;
        for (var i = 0; i < 2 && !done; i++) {
            b.x += b.dx * step;
            b.y += b.dy * step;
            b.z += b.dz * step;
            b.gone += step;
            brParticles(npc, trail, b.x, b.y, b.z, 0.03, 0.03, 0.03, 0, k.kind == "net" ? 3 : 1);
            var fx = foe.getX() - b.x, fz = foe.getZ() - b.z;
            if (fx * fx + fz * fz <= a.radius * a.radius && b.y >= foe.getY() - 0.3 && b.y <= foe.getY() + 2.1) {
                brShotHit(npc, foe, state, now, b);
                done = true;
            } else if (b.gone >= (b.reach || Number(a.max) + 1) || !world.isChunkLoaded(Math.floor(b.x), Math.floor(b.z)) || brBlocked(world, b.x, b.y, b.z)) { // Незагруженный чанк — стена.
                brParticles(npc, "minecraft:smoke", b.x, b.y, b.z, 0.1, 0.1, 0.1, 0.01, 4);
                brSound(npc, b.x, b.y, b.z, "minecraft:entity.arrow.hit", 0.6, 1.2);
                done = true;
            }
        }
        if (!done) keep.push(b);
    }
    k.shots = keep;
}

function brShotHit(npc, foe, state, now, b) {
    var k = state.atk;
    if (tInZone(foe)) return; // Сейф-зона: ни урона, ни сети.
    if (k.kind == "net") {
        // Сеть: без урона, щит не спасает; держит на месте, а он идёт вплотную.
        try { foe.addPotionEffect(2, Number(NET.root), Number(NET.rootLevel), false); }
        catch (error) { brOnce("root", "сеть Брюннера не держит: " + error); }
        state.rootedUntil = now + Math.round(Number(NET.root) * 20);
        k.caught = true; // Поймал — идёт вплотную без передышки.
        brParticles(npc, "minecraft:item_slime", foe.getX(), foe.getY() + 1, foe.getZ(), 0.5, 0.7, 0.5, 0.02, 30);
        brSound(npc, b.x, b.y, b.z, "minecraft:block.wool.place", 1.0, 0.7);
        return;
    }
    var hit = brStrike(npc, foe, state); // Попадание в щит ударом не считается.
    brSound(npc, b.x, b.y, b.z, "minecraft:entity.arrow.hit_player", 1.0, 1.0);
    if (hit && k.kind == "knives" && KNIVES.slow > 0) {
        try { foe.addPotionEffect(2, Number(KNIVES.slow), Number(KNIVES.slowLevel), false); }
        catch (error) { brOnce("slow", "замедление от ножа Брюннера недоступно: " + error); }
    }
}

function brPierce(foe, amount) {
    if (!(amount > 0)) return false;
    try { return !!BR_Helper.dealPureDamage(foe, amount, true); }
    catch (error) { brOnce("pierce", "урон пистоля Брюннера сквозь броню недоступен: " + error); return false; }
}

function brBlocked(world, x, y, z) {
    var block = world.getBlock(Math.floor(x), Math.floor(y), Math.floor(z));
    if (block.isAir()) return false;
    return !/(?:leaves|grass|fern|flower|vine|sapling|bush|torch|carpet|snow|water|rail|button|sign|banner)/.test(String(block.getName()));
}

// Дымовая шашка: облако на месте, сам — в сторону от цели на дистанцию стрелка.
function brSmokeHit(npc, foe, state, now) {
    var k = state.atk;
    for (var i = 0; i < 3; i++) brParticles(npc, "minecraft:campfire_cosy_smoke", k.sx, k.sy + 0.5 + i * 0.6, k.sz, 1.2, 0.3, 1.2, 0.01, 20);
    brParticles(npc, "minecraft:large_smoke", k.sx, k.sy + 1, k.sz, 1.5, 0.8, 1.5, 0.02, 40);
    brSound(npc, k.sx, k.sy, k.sz, "minecraft:block.fire.extinguish", 1.0, 0.7);
    var dx = npc.getX() - foe.getX(), dz = npc.getZ() - foe.getZ();
    var spot = brFindLanding(npc, foe, Math.atan2(dx, -dz), 0.6, 32, Number(SMOKE.min), Number(SMOKE.max));
    if (spot != null) brTeleport(npc, spot);
    brAttackEnd(npc, state, now);
    brSetStance(npc, state, now, "ranged");
    state.holdUntil = now + 5;
}

// --- «Верхом», ямы ---

// «Верхом»: догоняет вне взгляда цели — бегущую встречает впереди на её пути, стоящую — из-за спины.
function brRide(npc, foe, state, now) {
    var spot = null;
    if (Math.sqrt((state.vx || 0) * (state.vx || 0) + (state.vz || 0) * (state.vz || 0)) > 0.12)
        spot = brFindLanding(npc, foe, Math.atan2(state.vx, -state.vz), 0.15, 40, Number(RIDE_AHEAD_MIN), Number(RIDE_AHEAD_MAX));
    if (spot == null) spot = brFindLanding(npc, foe, Number(foe.getRotation()) * Math.PI / 180, 0.8, 40, Number(RIDE_MIN), Number(RIDE_MAX));
    if (spot == null) return false;
    brTeleport(npc, spot);
    state.hoofs = 4;
    brFace(npc, foe);
    return true;
}

function brTeleport(npc, spot) {
    brPause(npc);
    npc.setPosition(spot.x, spot.y, spot.z);
    npc.setMotionX(0);
    npc.setMotionY(0);
    npc.setMotionZ(0);
    BR_Helper.clearFallDistance(npc);
}

// Точка по сектору вокруг цели: angle по взгляду цели (x=+sin, z=−cos — за спиной), затем по кругу.
function brFindLanding(npc, foe, angle, spread, focused, min, max) {
    var world = npc.getWorld();
    for (var i = 0; i < 48; i++) {
        var a = i < focused ? angle + (Math.random() - 0.5) * 2 * spread : Math.random() * Math.PI * 2;
        var range = min + Math.random() * (max - min);
        var x = Math.floor(foe.getX() + Math.sin(a) * range) + 0.5;
        var z = Math.floor(foe.getZ() - Math.cos(a) * range) + 0.5;
        if (!world.isChunkLoaded(Math.floor(x), Math.floor(z))) continue;
        var y = Number(BR_Helper.findGroundY(world, x, z, foe.getY()));
        if (y < 1 || y > 253 || !brClearLanding(world, x, y, z)) continue;
        var dx = x - foe.getX(), dz = z - foe.getZ(), h = dx * dx + dz * dz;
        if (h < min * min || h > max * max || !brNoPlayerNear(world, x, y, z, OTHER_PLAYER_CLEARANCE)) continue;
        return { x: x, y: y, z: z };
    }
    return null;
}

// По списку игроков сервера: запрос сущностей мира у границы чанка может ждать соседний чанк, который ещё грузится.
function brNoPlayerNear(world, x, y, z, clearance) {
    var players = world.getAllPlayers(), dim = String(world.getDimension().getId());
    for (var i = 0; i < players.length; i++) {
        var p = players[i];
        if (!p.isAlive() || brDim(p) != dim || Math.abs(p.getY() - y) > clearance + 2) continue;
        var dx = x - p.getX(), dz = z - p.getZ();
        if (dx * dx + dz * dz < clearance * clearance) return false;
    }
    return true;
}

// Можно стоять: тело помещается и под ногами твёрдое (canStandAt) — снег, трава и цветы не мешают; опасное — нельзя.
function brClearLanding(world, x, y, z) {
    if (!BR_Helper.canStandAt(world, x, y, z)) return false;
    var bx = Math.floor(x), by = Math.floor(y), bz = Math.floor(z), hazard = /(?:lava|water|fire|magma|cactus|campfire|sweet_berry|portal)/;
    return !hazard.test(String(world.getBlock(bx, by, bz).getName())) && !hazard.test(String(world.getBlock(bx, by + 1, bz).getName()))
        && !hazard.test(String(world.getBlock(bx, Math.floor(y - 0.05), bz).getName()));
}

// Ямы: на одном месте TRAP_TICKS, а цель недосягаема — выше, за стеной или дальше нужного.
function brTrapped(npc, foe, state, now, distance) {
    var x = npc.getX(), y = npc.getY(), z = npc.getZ();
    if (state.trapX == null || brDist3(x, y, z, state.trapX, state.trapY, state.trapZ) > 2.25) {
        state.trapX = x;
        state.trapY = y;
        state.trapZ = z;
        state.trapAt = now;
        return false;
    }
    var want = state.stance == "melee" ? Number(DUEL_RANGE) + 2 : Number(SHOOT_MAX) + 2;
    var stuck = foe.getY() - y >= 2 || !npc.canSeeEntity(foe) || distance > want;
    if (!stuck) { // Стоит на дистанции боя и видит цель — так и задумано.
        state.trapAt = now;
        return false;
    }
    return now - (state.trapAt || now) >= Number(TRAP_TICKS);
}

// Выбраться: на ближайший край выше себя (2–6 блоков); края нет — догонять «верхом».
function brEscape(npc, foe, state, now) {
    state.trapX = null;
    var exit = brFindExit(npc);
    if (exit != null) {
        brTeleport(npc, exit);
        state.holdUntil = now + 5;
        brProgress(state, now);
        return;
    }
    if (now - (state.lastRide || -1000000) >= 40) {
        state.lastRide = now;
        brRide(npc, foe, state, now);
    }
}

function brFindExit(npc) {
    var world = npc.getWorld(), x0 = npc.getX(), y0 = npc.getY(), z0 = npc.getZ();
    for (var r = 2; r <= 6; r++) {
        for (var k = 0; k < 8; k++) {
            var a = k * Math.PI / 4 + (r % 2) * Math.PI / 8;
            var x = Math.floor(x0 + Math.cos(a) * r) + 0.5, z = Math.floor(z0 + Math.sin(a) * r) + 0.5;
            if (!world.isChunkLoaded(Math.floor(x), Math.floor(z))) continue;
            var y = Number(BR_Helper.findGroundY(world, x, z, y0 + 7)); // Самый высокий пол до y+10.
            if (y < y0 + 0.9 || y > y0 + 10) continue;
            if (!brClearLanding(world, x, y, z) || !brNoPlayerNear(world, x, y, z, 2)) continue;
            return { x: x, y: y, z: z };
        }
    }
    return null;
}

function brDist3(x, y, z, a, b, c) { return (x - a) * (x - a) + (y - b) * (y - b) + (z - c) * (z - c); }

// --- Противники ---

function brLoadAggro(npc) {
    var data = npc.getTempdata();
    if (!data.has(BR_AGGRO_KEY)) return {};
    try { return JSON.parse(String(data.get(BR_AGGRO_KEY))); } catch (error) { return {}; }
}

function brSaveAggro(npc, list) { npc.getTempdata().put(BR_AGGRO_KEY, JSON.stringify(list)); }

// Ближайший из ударивших его за RETALIATE_TICKS: живой игрок не в креативе, не наблюдатель и не в сейф-зоне.
function brAggressor(npc, now) {
    if (!RETALIATE) return null;
    var list = brLoadAggro(npc), world = npc.getWorld(), best = null, bestD = Infinity, changed = false;
    for (var id in list) {
        if (list[id] < now) {
            delete list[id];
            changed = true;
            continue;
        }
        var e = world.getEntity(id);
        if (e == null || e.getType() != 1 || !e.isAlive() || e.getGamemode() == 1 || e.getGamemode() == 3 || tInZone(e)) continue;
        var d = brDistSq(npc, e);
        if (d > RETALIATE_RANGE * RETALIATE_RANGE || d >= bestD) continue;
        best = e;
        bestD = d;
    }
    if (changed) brSaveAggro(npc, list);
    return best;
}

// --- Слова, частицы, звуки и метки wfmtelegraph. Без мода меток остаются частицы и звуки ---

function brPhrase(kind, vars) {
    var lines = SAY_LINES[kind];
    if (!lines || lines.length == 0) return "";
    var text = String(lines[Math.floor(Math.random() * lines.length)]);
    if (vars) for (var key in vars) text = text.split("{" + key + "}").join(String(vars[key]));
    return text;
}

function brSay(npc, state, kind, force, vars) {
    if (!SAY) return;
    var now = brNow(npc);
    if (!force && now < (state.sayAt || 0)) return;
    var text = brPhrase(kind, vars);
    if (text == "") return;
    state.sayAt = now + Number(SAY_COOLDOWN_TICKS);
    try { npc.say(text); } catch (error) { brOnce("say", "реплика Брюннера не отправлена: " + error); }
}

function brParticles(npc, name, x, y, z, dx, dy, dz, speed, count) {
    try { npc.getWorld().spawnParticle(name, x, y, z, dx, dy, dz, speed, count); }
    catch (error) { brOnce("particle:" + name, "частицы " + name + " недоступны: " + error); }
}

function brRing(npc, x, y, z, radius, name) {
    for (var i = 0; i < 12; i++) {
        var a = i * Math.PI / 6;
        brParticles(npc, name, x + Math.cos(a) * radius, y + 0.15, z + Math.sin(a) * radius, 0, 0, 0, 0, 1);
    }
}

function brSound(npc, x, y, z, name, volume, pitch) {
    try { npc.getWorld().playSoundAt(NpcAPI.getIPos(x, y, z), name, volume, pitch); }
    catch (error) { brOnce("sound:" + name, "звук " + name + " недоступен: " + error); }
}

function brTelegraphApi() {
    if (BR_telegraph == null) {
        try { BR_telegraph = Java.type("noppes.npcs.telegraph.TelegraphAPI"); }
        catch (error) {
            BR_telegraph = false;
            brOnce("telegraph", "метки Брюннера недоступны, остаются частицы: " + error);
        }
    }
    return BR_telegraph;
}

function brCircle(npc, p, radius, ticks, color) {
    var api = brTelegraphApi();
    if (!api) return "";
    try { return String(api.circleAt(npc, p.x, p.y, p.z, radius, ticks, color)); }
    catch (error) { brOnce("mark", "круг Брюннера не показан, остаются частицы: " + error); return ""; }
}

function brBand(npc, x, y, z, yaw, length, width, ticks, color) {
    var api = brTelegraphApi();
    if (!api) return "";
    try { return String(api.line(npc, x, y, z, yaw, length, width, ticks, color)); }
    catch (error) { brOnce("line", "полоса Брюннера не показана, остаются частицы: " + error); return ""; }
}

function brCone(npc, x, y, z, yaw, length, halfAngle, ticks, color) {
    var api = brTelegraphApi();
    if (!api) return "";
    try { return String(api.cone(npc, x, y, z, yaw, length, halfAngle, ticks, color)); }
    catch (error) { brOnce("cone", "конус Брюннера не показан, остаются частицы: " + error); return ""; }
}

function brRemoveTelegraph(npc, id) {
    if (!id || !BR_telegraph) return;
    try { BR_telegraph.removeNear(npc, id); } catch (ignored) {}
}

// --- Снаряжение, удар, состояние ---

// Броня и меч Брюннера из WFM — в пустые ячейки. У NPC шлем в ячейке 0.
function brEquip(npc) {
    try {
        var inv = npc.getInventory(), world = npc.getWorld(), changed = false;
        for (var i = 0; i < 4; i++) {
            var id = String((GEAR.armor || [])[i] || ""), now = inv.getArmor(i);
            if (id == "" || (now != null && !now.isEmpty())) continue;
            inv.setArmor(i, world.createItem(id, 1));
            changed = true;
        }
        var hand = inv.getRightHand();
        if (String(GEAR.sword || "") != "" && (hand == null || hand.isEmpty())) {
            inv.setRightHand(world.createItem(String(GEAR.sword), 1));
            changed = true;
        }
        if (changed) npc.updateClient();
    } catch (error) {
        brOnce("equip", "снаряжение Брюннера не надеть, выставь в меню: " + error);
    }
}

// Удар NPC с уроном из меню: meleeAttack пропускает только его и применяет множитель атаки.
function brStrike(npc, foe, state) {
    brSave(npc, state); // meleeAttack читает атаку из tempdata.
    var data = npc.getTempdata();
    data.put(BR_STRIKE_KEY, String(foe.getUUID()));
    try {
        return !!brNative("net.minecraft.entity.MobEntity", "func_70652_k", "(Lnet/minecraft/entity/Entity;)Z", npc.getMCEntity(), [foe.getMCEntity()]);
    } catch (error) {
        brOnce("strike", "удар Брюннера недоступен: " + error);
        return false;
    } finally {
        data.put(BR_STRIKE_KEY, "");
    }
}

function brLoad(npc) {
    var data = npc.getTempdata();
    return data.has(BR_KEY) ? JSON.parse(String(data.get(BR_KEY))) : brNewState(npc);
}

function brSave(npc, state) { npc.getTempdata().put(BR_KEY, JSON.stringify(state)); }
function brNow(npc) { return Number(npc.getWorld().getTotalTime()); }
function brDim(entity) { return String(entity.getWorld().getDimension().getId()); }

function brDistSq(a, b) {
    var dx = a.getX() - b.getX(), dy = a.getY() - b.getY(), dz = a.getZ() - b.getZ();
    return dx * dx + dy * dy + dz * dz;
}

function brOnce(key, message) { moLogOnce("thief_" + key, message); }

// Точечные вызовы через MethodHandles: Nashorn не перечисляет классы Minecraft и WFM целиком
// (на выделенном сервере там есть клиентские методы).
function brNative(owner, name, descriptor, receiver, args) {
    var key = (receiver == null ? "S:" : "V:") + owner + "." + name + descriptor;
    var handle = BR_handles[key];
    if (handle == null) {
        var loader = BR_Thread.currentThread().getContextClassLoader();
        var type = BR_Class.forName(owner, false, loader);
        var signature = BR_MethodType.fromMethodDescriptorString(descriptor, loader);
        var lookup = BR_MethodHandles.publicLookup();
        handle = receiver == null ? lookup.findStatic(type, name, signature) : lookup.findVirtual(type, name, signature);
        BR_handles[key] = handle;
    }
    return handle.invokeWithArguments(Java.to(receiver == null ? args : [receiver].concat(args), "java.lang.Object[]"));
}
