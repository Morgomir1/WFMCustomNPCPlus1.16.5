/**
 * Босс: Constant Drachenfels — парный (тело + дух).
 *
 * Immortal Bond: при летальном уроне тот же entity «падает» на месте,
 * партнёр поднимает ЕГО ЖЕ (UUID/home не меняются). spawnClone для боссов нет.
 * Настоящая смерть обоих → обычный CNPC-respawn на домашней точке.
 *
 * Роль: storeddata df_role = "body" | "spirit" (или по имени клона).
 * Тег: drachenfels (вешается в init). Thralls: тег drachenfels_thrall.
 *
 * Движение: не преследует в мили (OnAttack=Ничего). Дистанция — kite/retreat
 * и shadow_step. Дух: Navigation=Flying + hover.
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");
var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();

var TIMER_ID = 841;
var PHASE_CHECK_ID = 842;

var CAST_INTERVAL_PHASE1 = 80;
var CAST_INTERVAL_PHASE2 = 58;
var CAST_INTERVAL_BOND = 48;

var REVIVE_WINDOW_TICKS = 240;
var REVIVE_HP_RATIO = 0.45;
var LINK_RADIUS = 48;
var THRALL_RANGE = 48;
var PAIR_TAG = "drachenfels";
var THRALL_TAG = "drachenfels_thrall";

// CNPC OnAttack: 0=Мстить, 1=Паника, 2=Отступать, 3=Ничего
var RETALIATE_REVENGE = 0;
var RETALIATE_RETREAT = 2;
var RETALIATE_NONE = 3;
// Navigation: 0=Ground, 1=Flying, 2=Swimming
var NAV_GROUND = 0;
var NAV_FLYING = 1;
// Moving: 0=Standing, 1=Wandering, 2=MovingPath
var MOVING_STANDING = 0;

var VISIBLE_HIDDEN = 1;
var VISIBLE_NORMAL = 0;

// Дистанции «скиллового» позиционирования
var BODY_MIN_RANGE = 3.8;
var BODY_MAX_RANGE = 9.5;
var SPIRIT_MIN_RANGE = 6.5;
var SPIRIT_MAX_RANGE = 15.0;
var KITE_TICKS = 45;
var KITE_SPEED_BODY = 5;
var KITE_SPEED_SPIRIT = 6;
var CASTER_SPEED = 1;
var AGRO_RANGE = 56;        // радиус захвата цели от NPC
var LEASH_RANGE = 72;       // арена: игрок/цель в радиусе от home
var HOME_ARRIVE_DIST = 1.8; // уже «дома»
// RETALIATE_NONE часто сбрасывает цель → targetLost; домой только после паузы без игрока
var HOME_RETURN_DELAY_TICKS = 50;

var CLONE_TAB = 1;
var CLONE_THRALL = "Drachenfels Thrall";

var ROLE_KEY = "df_role";
var PARTNER_UUID_KEY = "df_partner_uuid";
var PAIR_ID_KEY = "df_pair_id";
var PARTNER_DEAD_KEY = "df_partner_dead";
var REVIVE_UNTIL_KEY = "df_revive_until";
var DEAD_UUID_KEY = "df_dead_uuid";
var DEAD_X_KEY = "df_dead_x";
var DEAD_Y_KEY = "df_dead_y";
var DEAD_Z_KEY = "df_dead_z";
var DEAD_ROLE_KEY = "df_dead_role";
var DOWNED_KEY = "df_downed";
var DOWNED_X_KEY = "df_downed_x";
var DOWNED_Y_KEY = "df_downed_y";
var DOWNED_Z_KEY = "df_downed_z";
var HOME_X_KEY = "df_home_x";
var HOME_Y_KEY = "df_home_y";
var HOME_Z_KEY = "df_home_z";
var HOVER_Y_KEY = "df_hover_y";
var HOVER_AMP = 0.22;
var HOVER_MAX_DRIFT = 1.8;
var SAVED_RETALIATE_KEY = "df_saved_retaliate";
var SAVED_SPEED_KEY = "df_saved_speed";
var SAVED_VISIBLE_KEY = "df_saved_visible";
var PHASE_KEY = "df_phase";
var NEXT_CAST_KEY = "df_next_cast";
var LAST_ABILITY_KEY = "df_last_ability";
var FORCED_ABILITY_KEY = "df_forced_ability";
var CD_PREFIX = "df_cd_";
var LINKED_KEY = "df_linked";
var KITE_UNTIL_KEY = "df_kite_until";
var STANCE_READY_KEY = "df_stance_ready";
var LOST_AGGRO_SINCE_KEY = "df_lost_aggro_since";

var POISON_FEAST_ID = "drachenfels_poison_feast";
var DARK_CLEAVE_ID = "drachenfels_dark_cleave";
var SOUL_REND_ID = "drachenfels_soul_rend";
var SPIRIT_BARRAGE_ID = "drachenfels_spirit_barrage";
var SOUL_SEEKER_ID = "drachenfels_soul_seeker";
var SOUL_ORBS_ID = "drachenfels_soul_orbs";
var RAISE_THRALLS_ID = "drachenfels_raise_thralls";
var SHADOW_STEP_ID = "drachenfels_shadow_step";
// Дальше этой дистанции — punish soul_seeker (line) / soul_orbs (circles)
var PUNISH_RANGE = 14.0;

var QUOTES_BODY_1 = [
    "Пир отравлен — так же, как и ваша надежда.",
    "Плоть вечна, пока живёт воля.",
    "Кланяйтесь Тьме, которую не сломить."
];
var QUOTES_BODY_2 = [
    "Вы лишь гости на моём пиру смерти!",
    "Каждый удар питает моё бессмертие!",
    "Империя падёт — я останусь."
];
var QUOTES_SPIRIT_1 = [
    "Я — имя, стёртое смертью.",
    "Дух не держит клинок — он держит судьбу.",
    "Ваши души уже в моём замке."
];
var QUOTES_SPIRIT_2 = [
    "Раздерите их души!",
    "Тело падёт — дух восстанет!",
    "Я был до ваших богов."
];
var QUOTES_BOND = [
    "Связь нерасторжима!",
    "Пока один дышит — второй вернётся!",
    "Смерть — лишь пауза."
];
var QUOTES_REVIVE = [
    "Встань. Мы ещё не закончили.",
    "Смерть отложена — по моей милости.",
    "Плоть и дух снова едины."
];

function init(event) {
    var npc = event.npc;
    var data = npc.getStoreddata();
    ensureRole(npc, data);
    if (!npc.hasTag(PAIR_TAG)) {
        npc.addTag(PAIR_TAG);
    }
    // Домашняя точка — куда вернёт CNPC-respawn после настоящей смерти обоих
    if (!data.has(HOME_X_KEY) || String(data.get(HOME_X_KEY)).length == 0) {
        data.put(HOME_X_KEY, String(npc.getX()));
        data.put(HOME_Y_KEY, String(npc.getY()));
        data.put(HOME_Z_KEY, String(npc.getZ()));
    }
    if (getRole(data) == "spirit") {
        if (!data.has(HOVER_Y_KEY) || String(data.get(HOVER_Y_KEY)).length == 0) {
            data.put(HOVER_Y_KEY, String(getFloat(data, HOME_Y_KEY) + 1.2));
        }
    }
    if (String(data.get(DOWNED_KEY)) != "1") {
        data.put(PHASE_KEY, "1");
        data.put(PARTNER_DEAD_KEY, "0");
        data.put(REVIVE_UNTIL_KEY, "0");
        data.put(DEAD_UUID_KEY, "");
        data.put(DOWNED_X_KEY, "");
        data.put(DOWNED_Y_KEY, "");
        data.put(DOWNED_Z_KEY, "");
    }
    data.put(LAST_ABILITY_KEY, "");
    data.put(FORCED_ABILITY_KEY, "");
    data.put(KITE_UNTIL_KEY, "0");
    data.put(LOST_AGGRO_SINCE_KEY, "0");
    tryLinkPartner(npc);
    applyCasterStance(npc);
    data.put(STANCE_READY_KEY, "1");
    startTimers(npc);
}

function timer(event) {
    if (event.id == PHASE_CHECK_ID) {
        var npc = event.npc;
        if (!npc.isAlive()) return;

        var data = npc.getStoreddata();
        if (String(data.get(DOWNED_KEY)) == "1") {
            tickDownedWatchdog(npc);
            return;
        }

        tryLinkPartner(npc);
        ensureCombatTarget(npc);
        updateBondAndPhase(npc);
        tickBondVfx(npc);
        tryCompleteRevive(npc);
        cleanupThrallsIfNoAggro(npc);
        tryReturnHomeIfIdle(npc);
        if (hasCombatTarget(npc)) {
            manageSpacing(npc);
        }
        return;
    }
    if (event.id != TIMER_ID) return;

    var npc = event.npc;
    if (!npc.isAlive()) {
        AbilityAPI.cancel(npc);
        return;
    }

    var data = npc.getStoreddata();
    if (String(data.get(DOWNED_KEY)) == "1") {
        // Каждый тик: иначе гравитация/полёт колбасит между pin'ами раз в 20 тиков
        AbilityAPI.cancel(npc);
        freezeDownedNpc(npc);
        return;
    }

    tickSpiritHover(npc);

    if (AbilityAPI.isBusy(npc)) {
        // Во время абилки не бежать в мили
        try { npc.getAi().setRetaliateType(RETALIATE_NONE); } catch (eBusy) {}
        return;
    }

    var world = npc.getWorld();
    var now = world.getTotalTime();
    ensureCombatTarget(npc);
    manageSpacing(npc);

    if (now < getInt(data, NEXT_CAST_KEY)) return;

    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) return;

    var phase = String(data.get(PHASE_KEY));
    if (phase.length == 0) phase = "1";
    var forced = String(data.get(FORCED_ABILITY_KEY));
    var abilityId = null;

    if (forced.length > 0 && isCooldownReady(data, now, forced)) {
        abilityId = forced;
    } else {
        abilityId = pickAbility(npc, target, phase, data, now);
    }
    if (abilityId == null) return;

    // Перед кастом — стойка кастера (не chase)
    applyCasterStance(npc);

    var started = AbilityAPI.start(npc, abilityId, target, buildParams(abilityId, phase, data));
    if (!started) return;

    data.put(NEXT_CAST_KEY, String(now + getCastInterval(phase)));
    data.put(LAST_ABILITY_KEY, abilityId);
    data.put(FORCED_ABILITY_KEY, "");
    data.put(CD_PREFIX + abilityId, String(now + getCooldownTicks(abilityId, phase)));

    if (Math.random() < (phase == "bond" ? 0.5 : (phase == "2" ? 0.4 : 0.28))) {
        sayQuote(npc, phase);
    }
}

/**
 * Летальный удар → падение ТОГО ЖЕ NPC (если партнёр жив и не упал).
 * Второй летальный при упавшем партнёре → настоящая смерть обоих (CNPC-respawn home).
 */
function damaged(event) {
    var npc = event.npc;
    var data = npc.getStoreddata();

    if (String(data.get(DOWNED_KEY)) == "1") {
        cancelLethal(event);
        pinDownedPosition(npc);
        try { npc.setHealth(1); } catch (e) {}
        return;
    }

    var damage = 0;
    try { damage = parseFloat(String(event.damage)); } catch (e2) { damage = 0; }
    if (!(damage > 0)) return;

    var hp = npc.getHealth();
    if (hp - damage > 0.5) return;

    tryLinkPartner(npc);
    var partner = findPartner(npc);
    // Партнёр ещё не слинкован — ищем рядом ещё раз перед настоящей смертью
    if (partner == null || !partner.isAlive() || isDowned(partner)) {
        tryLinkPartner(npc);
        partner = findPartner(npc);
    }

    if (partner != null && partner.isAlive() && isDowned(partner)) {
        // Второй босс умирает по-настоящему — добиваем упавшего (оба → CNPC home respawn)
        trulyKillDowned(partner);
        clearBondFlags(data);
        return; // не cancel: текущий тоже умирает по-настоящему
    }

    if (partner == null || !partner.isAlive()) {
        // Нет живого партнёра — обычная смерть (CNPC вернёт на home)
        clearBondFlags(data);
        return;
    }

    cancelLethal(event);
    enterDownedState(npc, partner);
}

function cancelLethal(event) {
    try { event.setCanceled(true); } catch (e) {}
    try {
        if (typeof event.setDamage == "function") event.setDamage(0);
    } catch (e2) {}
}

function targetLost(event) {
    var npc = event.npc;
    AbilityAPI.cancel(npc);
    // RETALIATE_NONE часто шлёт targetLost при живом игроке рядом —
    // не телепортируемся и не режем thralls сразу. Перехват цели / debounce home.
    if (isInBondPhase(npc) || isDowned(npc)) return;
    var retarget = ensureCombatTarget(npc);
    if (retarget != null) {
        clearLostAggroTimer(npc);
        return;
    }
    markLostAggro(npc);
}

function died(event) {
    var npc = event.npc;
    AbilityAPI.cancel(npc);
    despawnThrallsNear(npc);

    var data = npc.getStoreddata();
    var world = npc.getWorld();

    // Настоящая смерть выжившего во время bond → добить упавшего того же UUID
    if (String(data.get(PARTNER_DEAD_KEY)) == "1") {
        var downed = findNpcByUuid(world, String(data.get(DEAD_UUID_KEY)));
        if (downed != null && isDowned(downed)) {
            trulyKillDowned(downed);
        }
    }

    var partner = findPartner(npc);
    if (partner != null && isDowned(partner)) {
        trulyKillDowned(partner);
    }

    clearBondFlags(data);
}

function clearBondFlags(data) {
    if (data == null) return;
    data.put(DOWNED_KEY, "0");
    data.put(PARTNER_DEAD_KEY, "0");
    data.put(REVIVE_UNTIL_KEY, "0");
    data.put(DEAD_UUID_KEY, "");
    data.put(KITE_UNTIL_KEY, "0");
    data.put(DOWNED_X_KEY, "");
    data.put(DOWNED_Y_KEY, "");
    data.put(DOWNED_Z_KEY, "");
    data.put(FORCED_ABILITY_KEY, "");
    data.put(LOST_AGGRO_SINCE_KEY, "0");
}

function ensureRole(npc, data) {
    var role = String(data.get(ROLE_KEY));
    if (role == "body" || role == "spirit") return;
    var name = "";
    try {
        name = String(npc.getName()).toLowerCase();
    } catch (e) {
        name = "";
    }
    if (name.indexOf("spirit") >= 0 || name.indexOf("дух") >= 0 || name.indexOf("nameless") >= 0) {
        data.put(ROLE_KEY, "spirit");
    } else {
        data.put(ROLE_KEY, "body");
    }
}

function getRole(data) {
    var role = String(data.get(ROLE_KEY));
    return role == "spirit" ? "spirit" : "body";
}

function isDowned(npc) {
    if (npc == null) return false;
    return String(npc.getStoreddata().get(DOWNED_KEY)) == "1";
}

function findPartner(npc) {
    var uuid = String(npc.getStoreddata().get(PARTNER_UUID_KEY));
    if (uuid.length == 0) return null;
    return findNpcByUuid(npc.getWorld(), uuid);
}

function tryLinkPartner(npc) {
    var data = npc.getStoreddata();
    if (isDowned(npc)) return;

    var partnerUuid = String(data.get(PARTNER_UUID_KEY));
    if (partnerUuid.length > 0) {
        var existing = findNpcByUuid(npc.getWorld(), partnerUuid);
        if (existing != null && existing.isAlive()) {
            data.put(LINKED_KEY, "1");
            ensurePairId(npc, existing);
            return;
        }
    }

    var world = npc.getWorld();
    var pos = NpcAPI.getIPos(npc.getX(), npc.getY(), npc.getZ());
    var nearby = world.getNearbyEntities(pos, LINK_RADIUS, 2);
    var best = null;
    var bestDist = LINK_RADIUS + 1;
    var myUuid = String(npc.getUUID());
    var myRole = getRole(data);

    for (var i = 0; i < nearby.length; i++) {
        var other = nearby[i];
        if (other == null || !other.isAlive()) continue;
        if (String(other.getUUID()) == myUuid) continue;
        if (!other.hasTag(PAIR_TAG)) continue;
        if (isDowned(other)) continue;
        var od = other.getStoreddata();
        ensureRole(other, od);
        var otherRole = getRole(od);
        if (otherRole == myRole) continue;
        var d = flatDistance(npc, other);
        if (d < bestDist) {
            bestDist = d;
            best = other;
        }
    }

    if (best == null) {
        data.put(LINKED_KEY, "0");
        return;
    }

    linkPair(npc, best);
}

function linkPair(a, b) {
    var da = a.getStoreddata();
    var db = b.getStoreddata();
    da.put(PARTNER_UUID_KEY, String(b.getUUID()));
    db.put(PARTNER_UUID_KEY, String(a.getUUID()));
    da.put(LINKED_KEY, "1");
    db.put(LINKED_KEY, "1");
    ensurePairId(a, b);
}

function ensurePairId(a, b) {
    var da = a.getStoreddata();
    var db = b.getStoreddata();
    var existing = String(da.get(PAIR_ID_KEY));
    if (existing.length > 0) {
        db.put(PAIR_ID_KEY, existing);
        return;
    }
    existing = String(db.get(PAIR_ID_KEY));
    if (existing.length > 0) {
        da.put(PAIR_ID_KEY, existing);
        return;
    }
    var ua = String(a.getUUID());
    var ub = String(b.getUUID());
    var pairId = ua < ub ? ua + "_" + ub : ub + "_" + ua;
    da.put(PAIR_ID_KEY, pairId);
    db.put(PAIR_ID_KEY, pairId);
}

function enterDownedState(npc, partner) {
    AbilityAPI.cancel(npc);
    var data = npc.getStoreddata();
    var x = npc.getX();
    var y = npc.getY();
    var z = npc.getZ();

    data.put(DOWNED_KEY, "1");
    data.put(PARTNER_DEAD_KEY, "0");
    data.put(REVIVE_UNTIL_KEY, "0");
    data.put(KITE_UNTIL_KEY, "0");
    data.put(DOWNED_X_KEY, String(x));
    data.put(DOWNED_Y_KEY, String(y));
    data.put(DOWNED_Z_KEY, String(z));

    try { npc.setHealth(1); } catch (e) {}
    try { npc.setAttackTarget(null); } catch (e2) {}
    try { npc.setPosition(x, y, z); } catch (ePos) {}

    try {
        var ai = npc.getAi();
        data.put(SAVED_RETALIATE_KEY, String(RETALIATE_NONE));
        data.put(SAVED_SPEED_KEY, String(CASTER_SPEED));
        ai.setRetaliateType(RETALIATE_NONE);
        ai.setWalkingSpeed(0);
        ai.setMovingType(MOVING_STANDING);
        // Оставляем Flying: на Ground гравитация роняет, а pin дёргает вверх-вниз
        if (typeof ai.setNavigationType == "function") {
            ai.setNavigationType(getRole(data) == "spirit" ? NAV_FLYING : NAV_GROUND);
        }
    } catch (e3) {}

    freezeDownedNpc(npc);

    // Не скрываем полностью — тот же моб остаётся на точке падения (виден как «труп»)
    try {
        var display = npc.getDisplay();
        if (!data.has(SAVED_VISIBLE_KEY) || String(data.get(SAVED_VISIBLE_KEY)).length == 0) {
            data.put(SAVED_VISIBLE_KEY, String(display.getVisible()));
        }
        display.setVisible(VISIBLE_NORMAL);
    } catch (e4) {}

    armPartnerBond(partner, npc);

    var world = npc.getWorld();
    partner.say("§5§l" + QUOTES_BOND[Math.floor(Math.random() * QUOTES_BOND.length)]);

    try {
        world.spawnParticle("minecraft:soul", x, y + 1.0, z, 0.3, 0.5, 0.3, 0.04, 20);
        world.spawnParticle("minecraft:soul_fire_flame", x, y + 0.6, z, 0.25, 0.4, 0.25, 0.03, 16);
        try {
            world.spawnParticle("wfm:fog", x, y + 0.3, z, 0.4, 0.1, 0.4, 0, 6);
        } catch (fogErr) {}
        world.playSoundAt(NpcAPI.getIPos(x, y, z), "minecraft:entity.wither.hurt", 0.8, 0.6);
    } catch (e5) {}
}

function armPartnerBond(partner, downedNpc) {
    if (partner == null || downedNpc == null) return;
    var world = partner.getWorld();
    var now = world.getTotalTime();
    var dd = downedNpc.getStoreddata();
    var role = getRole(dd);
    var pd = partner.getStoreddata();
    pd.put(PARTNER_DEAD_KEY, "1");
    pd.put(REVIVE_UNTIL_KEY, String(now + REVIVE_WINDOW_TICKS));
    pd.put(DEAD_UUID_KEY, String(downedNpc.getUUID()));
    pd.put(DEAD_X_KEY, String(dd.get(DOWNED_X_KEY)));
    pd.put(DEAD_Y_KEY, String(dd.get(DOWNED_Y_KEY)));
    pd.put(DEAD_Z_KEY, String(dd.get(DOWNED_Z_KEY)));
    pd.put(DEAD_ROLE_KEY, role);
    pd.put(PHASE_KEY, "bond");
    pd.put(FORCED_ABILITY_KEY, getBondPressureAbility(getRole(pd)));
    pd.put(NEXT_CAST_KEY, String(now + 10));
    ensurePairId(downedNpc, partner);
}

function pinDownedPosition(npc) {
    if (npc == null) return;
    var data = npc.getStoreddata();
    if (!data.has(DOWNED_X_KEY) || String(data.get(DOWNED_X_KEY)).length == 0) return;
    var x = getFloat(data, DOWNED_X_KEY);
    var y = getFloat(data, DOWNED_Y_KEY);
    var z = getFloat(data, DOWNED_Z_KEY);
    try {
        npc.setPosition(x, y, z);
        npc.setMotionX(0);
        npc.setMotionY(0);
        npc.setMotionZ(0);
    } catch (e) {}
}

/** Полная заморозка упавшего (позиция + AI + без hover). */
function freezeDownedNpc(npc) {
    if (npc == null || !isDowned(npc)) return;
    pinDownedPosition(npc);
    try { npc.setHealth(1); } catch (e) {}
    try { npc.setAttackTarget(null); } catch (e2) {}
    try {
        var ai = npc.getAi();
        ai.setRetaliateType(RETALIATE_NONE);
        ai.setWalkingSpeed(0);
        ai.setMovingType(MOVING_STANDING);
        // Spirit: flying без вертикальной скорости (motion обнулён в pin)
        if (typeof ai.setNavigationType == "function") {
            var role = getRole(npc.getStoreddata());
            ai.setNavigationType(role == "spirit" ? NAV_FLYING : NAV_GROUND);
        }
    } catch (e3) {}
}

function exitDownedState(npc, hpRatio) {
    var data = npc.getStoreddata();
    var x = getFloat(data, DOWNED_X_KEY);
    var y = getFloat(data, DOWNED_Y_KEY);
    var z = getFloat(data, DOWNED_Z_KEY);

    data.put(DOWNED_KEY, "0");
    data.put(KITE_UNTIL_KEY, "0");

    // Тот же entity, та же точка падения
    if (String(data.get(DOWNED_X_KEY)).length > 0) {
        try { npc.setPosition(x, y, z); } catch (ePos) {}
    }

    try {
        var maxHp = npc.getMaxHealth();
        if (maxHp > 0) {
            npc.setHealth(maxHp * hpRatio);
        }
    } catch (e) {}

    try {
        var display = npc.getDisplay();
        var vis = data.has(SAVED_VISIBLE_KEY) ? getInt(data, SAVED_VISIBLE_KEY) : VISIBLE_NORMAL;
        display.setVisible(vis);
    } catch (e3) {}

    data.put(DOWNED_X_KEY, "");
    data.put(DOWNED_Y_KEY, "");
    data.put(DOWNED_Z_KEY, "");

    applyCasterStance(npc);
}

function trulyKillDowned(npc) {
    if (npc == null) return;
    var data = npc.getStoreddata();
    clearBondFlags(data);

    try {
        var display = npc.getDisplay();
        var vis = data.has(SAVED_VISIBLE_KEY) ? getInt(data, SAVED_VISIBLE_KEY) : VISIBLE_NORMAL;
        display.setVisible(vis);
    } catch (e) {}

    AbilityAPI.cancel(npc);
    // Обычная смерть → CNPC Respawn Time вернёт ЭТОТ же слот/home, не clone
    try { npc.setHealth(0); } catch (e2) {}
    try { npc.kill(); } catch (e3) {}
}

function tickDownedWatchdog(npc) {
    AbilityAPI.cancel(npc);
    freezeDownedNpc(npc);

    var myUuid = String(npc.getUUID());
    var partner = findPartner(npc);

    // Партнёр жив и держит bond на НАШ UUID → остаёмся упавшими на месте
    if (partner != null && partner.isAlive() && !isDowned(partner)) {
        var pd = partner.getStoreddata();
        if (String(pd.get(PARTNER_DEAD_KEY)) == "1" && String(pd.get(DEAD_UUID_KEY)) == myUuid) {
            return;
        }
        // Bond слетел — восстановить на того же downed entity
        armPartnerBond(partner, npc);
        return;
    }

    // Партнёра больше нет (настоящая смерть) → умираем сами (CNPC home respawn)
    if (partner == null || !partner.isAlive()) {
        trulyKillDowned(npc);
    }
}

function getBondPressureAbility(role) {
    return role == "spirit" ? SOUL_REND_ID : POISON_FEAST_ID;
}

/**
 * При OnAttack=Ничего CNPC сам может не брать цель.
 * Агр только на игроков в AGRO_RANGE от NPC и в LEASH_RANGE от home.
 * Каждый тик перехватывает цель — бой не рвётся из‑за RETALIATE_NONE.
 */
function ensureCombatTarget(npc) {
    if (npc == null || isDowned(npc)) return null;
    var cur = null;
    try { cur = npc.getAttackTarget(); } catch (e) { cur = null; }
    if (cur != null && cur.isAlive()) {
        if (distEntityToHome(npc, cur) > LEASH_RANGE) {
            // Цель ушла с арены — сброс, ищем другую; домой не телепортируем здесь
            try { npc.setAttackTarget(null); } catch (eDrop) {}
        } else {
            clearLostAggroTimer(npc);
            return cur;
        }
    }

    var best = findValidPlayer(npc, AGRO_RANGE, LEASH_RANGE);
    if (best != null) {
        try { npc.setAttackTarget(best); } catch (e3) {}
        clearLostAggroTimer(npc);
        return best;
    }
    markLostAggro(npc);
    return null;
}

/** Живой survival/adventure игрок: dist к NPC <= maxNpcDist, к home <= maxHomeDist. */
function findValidPlayer(npc, maxNpcDist, maxHomeDist) {
    if (npc == null) return null;
    var world = npc.getWorld();
    var best = null;
    var bestDist = maxNpcDist + 1;
    try {
        var players = world.getAllPlayers();
        for (var i = 0; i < players.length; i++) {
            var p = players[i];
            if (!isValidCombatPlayer(p)) continue;
            if (distEntityToHome(npc, p) > maxHomeDist) continue;
            var d = flatDistance(npc, p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
    } catch (e) {}
    return best;
}

function isValidCombatPlayer(p) {
    if (p == null || !p.isAlive()) return false;
    try {
        if (typeof p.getGamemode == "function") {
            var gm = p.getGamemode();
            if (gm == 1 || gm == 3) return false;
        }
    } catch (eGm) {}
    return true;
}

/** Любой валидный игрок в радиусе арены от home (дистанция до NPC не важна). */
function findPlayerInLeash(npc) {
    if (npc == null) return null;
    var best = null;
    var bestDist = LEASH_RANGE + 1;
    try {
        var players = npc.getWorld().getAllPlayers();
        for (var i = 0; i < players.length; i++) {
            var p = players[i];
            if (!isValidCombatPlayer(p)) continue;
            var dh = distEntityToHome(npc, p);
            if (dh > LEASH_RANGE) continue;
            if (dh < bestDist) {
                bestDist = dh;
                best = p;
            }
        }
    } catch (e) {}
    return best;
}

function findPlayerInAgro(npc) {
    return findValidPlayer(npc, AGRO_RANGE, LEASH_RANGE);
}

function isInActiveCombat(npc) {
    if (npc == null) return false;
    if (hasCombatTarget(npc)) return true;
    return findPlayerInAgro(npc) != null;
}

function clearLostAggroTimer(npc) {
    if (npc == null) return;
    try { npc.getStoreddata().put(LOST_AGGRO_SINCE_KEY, "0"); } catch (e) {}
}

function markLostAggro(npc) {
    if (npc == null) return;
    var data = npc.getStoreddata();
    if (getInt(data, LOST_AGGRO_SINCE_KEY) > 0) return;
    try {
        data.put(LOST_AGGRO_SINCE_KEY, String(npc.getWorld().getTotalTime()));
    } catch (e) {}
}

function hasLostAggroLongEnough(npc) {
    if (npc == null) return false;
    var since = getInt(npc.getStoreddata(), LOST_AGGRO_SINCE_KEY);
    if (since <= 0) return false;
    return npc.getWorld().getTotalTime() - since >= HOME_RETURN_DELAY_TICKS;
}

function isInBondPhase(npc) {
    if (npc == null) return false;
    var data = npc.getStoreddata();
    return String(data.get(PARTNER_DEAD_KEY)) == "1" || isDowned(npc);
}

function hasHome(data) {
    return data != null && data.has(HOME_X_KEY) && String(data.get(HOME_X_KEY)).length > 0;
}

function distToHomeFlat(npc) {
    var data = npc.getStoreddata();
    if (!hasHome(data)) return 0;
    var dx = npc.getX() - getFloat(data, HOME_X_KEY);
    var dz = npc.getZ() - getFloat(data, HOME_Z_KEY);
    return Math.sqrt(dx * dx + dz * dz);
}

function distEntityToHome(npc, ent) {
    var data = npc.getStoreddata();
    if (!hasHome(data) || ent == null) return 9999;
    var dx = ent.getX() - getFloat(data, HOME_X_KEY);
    var dz = ent.getZ() - getFloat(data, HOME_Z_KEY);
    return Math.sqrt(dx * dx + dz * dz);
}

/** Телепорт на исходную позицию (home из init). Не вызывать во время боя. */
function returnToHome(npc) {
    if (npc == null || !npc.isAlive() || isDowned(npc)) return;
    if (isInBondPhase(npc)) return;
    // Жёсткий стоп: цель или игрок в агро — остаёмся драться
    if (isInActiveCombat(npc)) return;
    if (findPlayerInLeash(npc) != null) return;

    var data = npc.getStoreddata();
    if (!hasHome(data)) return;

    var x = getFloat(data, HOME_X_KEY);
    var y = getFloat(data, HOME_Y_KEY);
    var z = getFloat(data, HOME_Z_KEY);
    var role = getRole(data);

    if (role == "spirit") {
        var hoverY = y + 1.2;
        data.put(HOVER_Y_KEY, String(hoverY));
        y = hoverY;
    }

    AbilityAPI.cancel(npc);
    data.put(KITE_UNTIL_KEY, "0");
    data.put(FORCED_ABILITY_KEY, "");
    data.put(NEXT_CAST_KEY, "0");
    data.put(LOST_AGGRO_SINCE_KEY, "0");

    try { npc.setAttackTarget(null); } catch (e) {}
    try {
        npc.setPosition(x, y, z);
        npc.setMotionX(0);
        npc.setMotionY(0);
        npc.setMotionZ(0);
    } catch (e2) {}

    applyCasterStance(npc);

    try {
        var world = npc.getWorld();
        world.spawnParticle("minecraft:soul_fire_flame", x, y + 0.5, z, 0.2, 0.3, 0.2, 0.02, 8);
        try {
            world.spawnParticle("wfm:fog", x, y + 0.2, z, 0.25, 0.05, 0.25, 0, 3);
        } catch (fogErr) {}
    } catch (e3) {}
}

/**
 * Домой только если бой реально закончен: нет цели, нет игрока в leash,
 * не busy/bond/downed, и пауза без агра >= HOME_RETURN_DELAY_TICKS.
 */
function tryReturnHomeIfIdle(npc) {
    if (npc == null || !npc.isAlive() || isDowned(npc) || isInBondPhase(npc)) return;
    if (AbilityAPI.isBusy(npc)) return;
    if (hasCombatTarget(npc)) {
        clearLostAggroTimer(npc);
        return;
    }
    // Игрок ещё на арене — не уходим; перехватим цель если в агро
    if (findPlayerInLeash(npc) != null) {
        ensureCombatTarget(npc);
        return;
    }
    markLostAggro(npc);
    if (!hasLostAggroLongEnough(npc)) return;

    if (distToHomeFlat(npc) <= HOME_ARRIVE_DIST) {
        clearLostAggroTimer(npc);
        if (getRole(npc.getStoreddata()) == "spirit") {
            tickSpiritHover(npc);
        }
        return;
    }
    returnToHome(npc);
}

/** Кастерская стойка: не преследует, стоит на месте; дух — летает. */
function applyCasterStance(npc) {
    if (npc == null || isDowned(npc)) return;
    var data = npc.getStoreddata();
    var role = getRole(data);
    try {
        var ai = npc.getAi();
        ai.setRetaliateType(RETALIATE_NONE);
        ai.setMovingType(MOVING_STANDING);
        ai.setWalkingSpeed(CASTER_SPEED);
        if (typeof ai.setNavigationType == "function") {
            ai.setNavigationType(role == "spirit" ? NAV_FLYING : NAV_GROUND);
        }
        if (typeof ai.setLeapAtTarget == "function") {
            ai.setLeapAtTarget(false);
        }
    } catch (e) {}
}

function applyKiteStance(npc) {
    if (npc == null || isDowned(npc)) return;
    var data = npc.getStoreddata();
    var role = getRole(data);
    try {
        var ai = npc.getAi();
        ai.setRetaliateType(RETALIATE_RETREAT);
        ai.setMovingType(MOVING_STANDING);
        ai.setWalkingSpeed(role == "spirit" ? KITE_SPEED_SPIRIT : KITE_SPEED_BODY);
        if (typeof ai.setNavigationType == "function") {
            ai.setNavigationType(role == "spirit" ? NAV_FLYING : NAV_GROUND);
        }
    } catch (e) {}
}

function isKiting(data, now) {
    return now < getInt(data, KITE_UNTIL_KEY);
}

/**
 * Держит дистанцию: слишком близко → краткий kite;
 * слишком далеко → forced soul_seeker (punish), иначе shadow_step.
 */
function manageSpacing(npc) {
    if (npc == null || !npc.isAlive() || isDowned(npc)) return;
    if (AbilityAPI.isBusy(npc)) {
        applyCasterStance(npc);
        return;
    }

    var data = npc.getStoreddata();
    var now = npc.getWorld().getTotalTime();
    var target = null;
    try { target = npc.getAttackTarget(); } catch (e) { target = null; }

    if (target == null || !target.isAlive()) {
        applyCasterStance(npc);
        data.put(KITE_UNTIL_KEY, "0");
        return;
    }

    var role = getRole(data);
    var dist = flatDistance(npc, target);
    var minR = role == "spirit" ? SPIRIT_MIN_RANGE : BODY_MIN_RANGE;
    var maxR = role == "spirit" ? SPIRIT_MAX_RANGE : BODY_MAX_RANGE;

    if (dist < minR) {
        data.put(KITE_UNTIL_KEY, String(now + KITE_TICKS));
        applyKiteStance(npc);
        return;
    }

    if (isKiting(data, now)) {
        applyKiteStance(npc);
        return;
    }

    var forced = String(data.get(FORCED_ABILITY_KEY));
    if (forced.length == 0 && dist > maxR) {
        if (isCooldownReady(data, now, SOUL_SEEKER_ID)) {
            data.put(FORCED_ABILITY_KEY, SOUL_SEEKER_ID);
        } else if (isCooldownReady(data, now, SHADOW_STEP_ID)) {
            data.put(FORCED_ABILITY_KEY, SHADOW_STEP_ID);
        }
    }

    applyCasterStance(npc);
}

/**
 * Парение духа вокруг фиксированной высоты (df_hover_y).
 * Без постоянного +Y — иначе улетает в небо.
 */
function tickSpiritHover(npc) {
    var data = npc.getStoreddata();
    if (getRole(data) != "spirit") return;
    if (isDowned(npc)) return;

    try {
        var ai = npc.getAi();
        if (typeof ai.setNavigationType == "function") {
            ai.setNavigationType(NAV_FLYING);
        }
    } catch (e) {}

    // Во время абилки (shadow_step и т.п.) высоту не трогаем
    if (AbilityAPI.isBusy(npc)) return;

    try {
        if (!data.has(HOVER_Y_KEY) || String(data.get(HOVER_Y_KEY)).length == 0) {
            var base = data.has(HOME_Y_KEY) ? getFloat(data, HOME_Y_KEY) + 1.2 : npc.getY();
            data.put(HOVER_Y_KEY, String(base));
        }
        var hoverY = getFloat(data, HOVER_Y_KEY);
        var t = npc.getWorld().getTotalTime();
        var targetY = hoverY + Math.sin(t * 0.07) * HOVER_AMP;
        var x = npc.getX();
        var y = npc.getY();
        var z = npc.getZ();
        var dy = targetY - y;

        // Жёсткий потолок / пол — если унесло
        if (y > hoverY + HOVER_MAX_DRIFT) {
            npc.setPosition(x, hoverY + HOVER_AMP, z);
            npc.setMotionY(-0.1);
            return;
        }
        if (y < hoverY - HOVER_MAX_DRIFT) {
            npc.setPosition(x, hoverY - HOVER_AMP, z);
            npc.setMotionY(0.05);
            return;
        }

        // Мягкая коррекция к целевой высоте (и вверх, и вниз)
        var corr = dy * 0.15;
        if (corr > 0.06) corr = 0.06;
        if (corr < -0.06) corr = -0.06;
        npc.setMotionY(corr);
    } catch (e3) {}
}

function updateBondAndPhase(npc) {
    var data = npc.getStoreddata();
    if (isDowned(npc)) return;

    var partnerDead = String(data.get(PARTNER_DEAD_KEY)) == "1";
    if (partnerDead) {
        var until = getInt(data, REVIVE_UNTIL_KEY);
        var now = npc.getWorld().getTotalTime();
        if (until > 0 && now < until) {
            if (String(data.get(PHASE_KEY)) != "bond") {
                data.put(PHASE_KEY, "bond");
            }
            return;
        }
    }

    var maxHealth = npc.getMaxHealth();
    if (maxHealth <= 0) return;
    var ratio = npc.getHealth() / maxHealth;
    var newPhase = ratio <= 0.5 ? "2" : "1";
    var oldPhase = String(data.get(PHASE_KEY));
    if (oldPhase == "bond" && partnerDead) return;
    if (newPhase == oldPhase) return;

    data.put(PHASE_KEY, newPhase);
    if (newPhase == "2") {
        var role = getRole(data);
        data.put(FORCED_ABILITY_KEY, role == "spirit" ? SPIRIT_BARRAGE_ID : POISON_FEAST_ID);
        npc.say(role == "spirit"
            ? "§5§lБезымянный гнев пробудился!"
            : "§4§lВеликий Чародей раскрывает силу!");
    }
}

function tickBondVfx(npc) {
    var data = npc.getStoreddata();
    if (String(data.get(PARTNER_DEAD_KEY)) != "1") return;
    var now = npc.getWorld().getTotalTime();
    if (now % 10 != 0) return;

    var downed = findNpcByUuid(npc.getWorld(), String(data.get(DEAD_UUID_KEY)));
    var dx = downed != null ? downed.getX() : getFloat(data, DEAD_X_KEY);
    var dy = downed != null ? downed.getY() : getFloat(data, DEAD_Y_KEY);
    var dz = downed != null ? downed.getZ() : getFloat(data, DEAD_Z_KEY);
    var world = npc.getWorld();
    try {
        var steps = 8;
        for (var i = 0; i <= steps; i++) {
            var t = i / steps;
            var x = npc.getX() + (dx - npc.getX()) * t;
            var y = npc.getY() + 1.0 + (dy + 0.5 - npc.getY() - 1.0) * t;
            var z = npc.getZ() + (dz - npc.getZ()) * t;
            world.spawnParticle("minecraft:soul_fire_flame", x, y, z, 0, 0.02, 0, 0, 1);
            if (i % 2 == 0) {
                world.spawnParticle("minecraft:soul", x, y + 0.1, z, 0, 0.02, 0, 0.01, 1);
            }
            if (i == 0 || i == steps || i == Math.floor(steps / 2)) {
                try {
                    world.spawnParticle("wfm:fog", x, y, z, 0.004, 0.002, 0.004, 0, 1);
                } catch (fogErr) {}
            }
        }
        world.spawnParticle("minecraft:soul", dx, dy + 0.8, dz, 0.1, 0.06, 0.1, 0.02, 5);
        world.spawnParticle("minecraft:soul_fire_flame", dx, dy + 0.5, dz, 0.15, 0.08, 0.15, 0.02, 4);
        try {
            world.spawnParticle("wfm:fog", dx, dy + 0.35, dz, 0.2, 0.05, 0.2, 0, 3);
            world.spawnParticle("wfm:fog_wall", dx, dy + 0.15, dz, 0.1, 0.02, 0.1, 0, 1);
        } catch (fogErr2) {}
    } catch (e) {}
}

function tryCompleteRevive(npc) {
    var data = npc.getStoreddata();
    if (String(data.get(PARTNER_DEAD_KEY)) != "1") return;
    if (!npc.isAlive() || isDowned(npc)) return;

    var world = npc.getWorld();
    var now = world.getTotalTime();
    var until = getInt(data, REVIVE_UNTIL_KEY);
    if (until <= 0 || now < until) return;

    // Только существующий entity по UUID — никаких spawnClone
    var deadUuid = String(data.get(DEAD_UUID_KEY));
    var downed = findNpcByUuid(world, deadUuid);
    if (downed == null || !downed.isAlive() || !isDowned(downed)) {
        data.put(PARTNER_DEAD_KEY, "0");
        data.put(REVIVE_UNTIL_KEY, "0");
        data.put(DEAD_UUID_KEY, "");
        return;
    }

    if (String(downed.getUUID()) != deadUuid) {
        log("drachenfels revive aborted: uuid mismatch");
        return;
    }

    exitDownedState(downed, REVIVE_HP_RATIO);
    linkPair(npc, downed);

    var sd = downed.getStoreddata();
    var hpPhaseDowned = downed.getMaxHealth() > 0 && (downed.getHealth() / downed.getMaxHealth()) <= 0.5 ? "2" : "1";
    sd.put(PHASE_KEY, hpPhaseDowned);
    sd.put(PARTNER_DEAD_KEY, "0");
    sd.put(REVIVE_UNTIL_KEY, "0");
    sd.put(DEAD_UUID_KEY, "");
    sd.put(LAST_ABILITY_KEY, "");
    sd.put(FORCED_ABILITY_KEY, "");

    data.put(PARTNER_DEAD_KEY, "0");
    data.put(REVIVE_UNTIL_KEY, "0");
    data.put(DEAD_UUID_KEY, "");
    var hpPhase = npc.getMaxHealth() > 0 && (npc.getHealth() / npc.getMaxHealth()) <= 0.5 ? "2" : "1";
    data.put(PHASE_KEY, hpPhase);

    // Передать цель живому, если есть
    try {
        var target = npc.getAttackTarget();
        if (target != null && target.isAlive()) {
            downed.setAttackTarget(target);
        }
    } catch (e) {}

    npc.say("§5§l" + QUOTES_REVIVE[Math.floor(Math.random() * QUOTES_REVIVE.length)]);
    var x = downed.getX();
    var y = downed.getY();
    var z = downed.getZ();
    try {
        world.spawnParticle("minecraft:soul_fire_flame", x, y + 1.0, z, 0.3, 0.6, 0.3, 0.05, 24);
        world.spawnParticle("minecraft:soul", x, y + 0.8, z, 0.35, 0.4, 0.35, 0.04, 16);
        try {
            world.spawnParticle("wfm:fog", x, y + 0.4, z, 0.5, 0.15, 0.5, 0, 8);
            world.spawnParticle("wfm:fog_wall", x, y + 0.2, z, 0.3, 0.08, 0.3, 0, 3);
        } catch (fogErr) {}
        world.playSoundAt(NpcAPI.getIPos(x, y, z), "minecraft:entity.wither.spawn", 0.7, 1.3);
    } catch (e4) {}
}

function cleanupThrallsIfNoAggro(npc) {
    if (hasCombatTarget(npc)) return;
    // Игрок на арене / в агро — ложный сброс цели, thralls не трогаем
    if (findPlayerInLeash(npc) != null || isInActiveCombat(npc)) return;
    var partner = findPartner(npc);
    if (partner != null && partner.isAlive() && !isDowned(partner)) {
        if (hasCombatTarget(partner) || findPlayerInLeash(partner) != null) {
            return;
        }
    }
    // Реальная потеря агра (после debounce) — despawn thralls
    if (!hasLostAggroLongEnough(npc)) {
        markLostAggro(npc);
        return;
    }
    despawnThrallsNear(npc);
    if (partner != null) {
        despawnThrallsNear(partner);
    }
    if (!isInBondPhase(npc)) {
        tryReturnHomeIfIdle(npc);
    }
    if (partner != null && partner.isAlive() && !isDowned(partner) && !isInBondPhase(partner)) {
        tryReturnHomeIfIdle(partner);
    }
}

function hasCombatTarget(npc) {
    try {
        var t = npc.getAttackTarget();
        return t != null && t.isAlive();
    } catch (e) {
        return false;
    }
}

function despawnThrallsNear(npc) {
    if (npc == null) return 0;
    var world = npc.getWorld();
    var pos = NpcAPI.getIPos(npc.getX(), npc.getY(), npc.getZ());
    var list = world.getNearbyEntities(pos, THRALL_RANGE, 2);
    var count = 0;
    for (var i = 0; i < list.length; i++) {
        var ent = list[i];
        if (ent == null || !ent.isAlive()) continue;
        if (!ent.hasTag(THRALL_TAG)) continue;
        try {
            ent.despawn();
            count++;
        } catch (e) {
            try { ent.kill(); count++; } catch (e2) {}
        }
    }
    return count;
}

function pickAbility(npc, target, phase, data, now) {
    var role = getRole(data);
    var dist = flatDistance(npc, target);
    var last = String(data.get(LAST_ABILITY_KEY));
    var minR = role == "spirit" ? SPIRIT_MIN_RANGE : BODY_MIN_RANGE;
    var maxR = role == "spirit" ? SPIRIT_MAX_RANGE : BODY_MAX_RANGE;
    var far = dist > maxR || dist > PUNISH_RANGE;

    // Слишком далеко — line-seeker или круглые soul_orbs (punish), иначе shadow_step
    if (far && last != SOUL_ORBS_ID && last != SOUL_SEEKER_ID) {
        if (isCooldownReady(data, now, SOUL_ORBS_ID) && Math.random() < 0.55) return SOUL_ORBS_ID;
        if (isCooldownReady(data, now, SOUL_SEEKER_ID)) return SOUL_SEEKER_ID;
        if (isCooldownReady(data, now, SOUL_ORBS_ID)) return SOUL_ORBS_ID;
    }
    if (dist > maxR && isCooldownReady(data, now, SHADOW_STEP_ID) && last != SHADOW_STEP_ID) {
        return SHADOW_STEP_ID;
    }

    if (phase == "bond") {
        if (role == "body") {
            if (far && isCooldownReady(data, now, SOUL_ORBS_ID)) return SOUL_ORBS_ID;
            if (far && isCooldownReady(data, now, SOUL_SEEKER_ID)) return SOUL_SEEKER_ID;
            if (dist < 5.0 && isCooldownReady(data, now, DARK_CLEAVE_ID)) return DARK_CLEAVE_ID;
            if (isCooldownReady(data, now, POISON_FEAST_ID)) return POISON_FEAST_ID;
            if (dist > 7.0 && isCooldownReady(data, now, SOUL_ORBS_ID)) return SOUL_ORBS_ID;
            if (isCooldownReady(data, now, SOUL_SEEKER_ID)) return SOUL_SEEKER_ID;
            if (isCooldownReady(data, now, SHADOW_STEP_ID)) return SHADOW_STEP_ID;
            if (isCooldownReady(data, now, RAISE_THRALLS_ID)) return RAISE_THRALLS_ID;
        } else {
            if (far && isCooldownReady(data, now, SOUL_ORBS_ID)) return SOUL_ORBS_ID;
            if (far && isCooldownReady(data, now, SOUL_SEEKER_ID)) return SOUL_SEEKER_ID;
            if (isCooldownReady(data, now, SOUL_REND_ID)) return SOUL_REND_ID;
            if (dist > 5 && isCooldownReady(data, now, SOUL_ORBS_ID)) return SOUL_ORBS_ID;
            if (dist > 5 && isCooldownReady(data, now, SPIRIT_BARRAGE_ID)) return SPIRIT_BARRAGE_ID;
            if (isCooldownReady(data, now, SOUL_SEEKER_ID)) return SOUL_SEEKER_ID;
            if (isCooldownReady(data, now, RAISE_THRALLS_ID)) return RAISE_THRALLS_ID;
            if (isCooldownReady(data, now, SHADOW_STEP_ID)) return SHADOW_STEP_ID;
        }
        return null;
    }

    if (role == "body") {
        if (far && isCooldownReady(data, now, SOUL_ORBS_ID)) return SOUL_ORBS_ID;
        if (far && isCooldownReady(data, now, SOUL_SEEKER_ID)) return SOUL_SEEKER_ID;
        if (phase == "2" && dist >= minR && dist < 6.5 && isCooldownReady(data, now, POISON_FEAST_ID) && Math.random() < 0.35) {
            return POISON_FEAST_ID;
        }
        if (dist >= minR && dist < 5.5 && isCooldownReady(data, now, DARK_CLEAVE_ID)) {
            if (last != DARK_CLEAVE_ID || Math.random() < 0.55) return DARK_CLEAVE_ID;
        }
        if (dist > 7.0 && isCooldownReady(data, now, SHADOW_STEP_ID)) return SHADOW_STEP_ID;
        if (phase == "2" && isCooldownReady(data, now, RAISE_THRALLS_ID) && Math.random() < 0.3) {
            return RAISE_THRALLS_ID;
        }
        if (dist < 7.0 && isCooldownReady(data, now, POISON_FEAST_ID)) return POISON_FEAST_ID;
        if (isCooldownReady(data, now, DARK_CLEAVE_ID)) return DARK_CLEAVE_ID;
        if (dist > 8.0 && isCooldownReady(data, now, SOUL_ORBS_ID)) return SOUL_ORBS_ID;
        if (isCooldownReady(data, now, SOUL_SEEKER_ID) && dist > 8.0) return SOUL_SEEKER_ID;
        if (isCooldownReady(data, now, SHADOW_STEP_ID)) return SHADOW_STEP_ID;
        if (phase == "2" && isCooldownReady(data, now, RAISE_THRALLS_ID)) return RAISE_THRALLS_ID;
        return null;
    }

    // spirit — держит дистанцию; mid-range — круглые orbs, line barrage/seeker остаются
    if (far && isCooldownReady(data, now, SOUL_ORBS_ID)) return SOUL_ORBS_ID;
    if (far && isCooldownReady(data, now, SOUL_SEEKER_ID)) return SOUL_SEEKER_ID;
    if (dist < minR && isCooldownReady(data, now, SHADOW_STEP_ID) && Math.random() < 0.35) {
        return null;
    }
    if (phase == "2" && isCooldownReady(data, now, SOUL_ORBS_ID) && Math.random() < 0.4) {
        return SOUL_ORBS_ID;
    }
    if (phase == "2" && isCooldownReady(data, now, SPIRIT_BARRAGE_ID) && Math.random() < 0.32) {
        return SPIRIT_BARRAGE_ID;
    }
    if (dist >= minR && dist < 10.0 && isCooldownReady(data, now, SOUL_REND_ID)) return SOUL_REND_ID;
    if (dist > 6.0 && isCooldownReady(data, now, SOUL_ORBS_ID) && last != SOUL_ORBS_ID) return SOUL_ORBS_ID;
    if (dist > 6.0 && isCooldownReady(data, now, SPIRIT_BARRAGE_ID)) return SPIRIT_BARRAGE_ID;
    if (dist > 10.0 && isCooldownReady(data, now, SOUL_SEEKER_ID)) return SOUL_SEEKER_ID;
    if (phase == "2" && isCooldownReady(data, now, RAISE_THRALLS_ID) && Math.random() < 0.28) {
        return RAISE_THRALLS_ID;
    }
    if (isCooldownReady(data, now, SOUL_REND_ID)) return SOUL_REND_ID;
    if (isCooldownReady(data, now, SOUL_ORBS_ID)) return SOUL_ORBS_ID;
    if (isCooldownReady(data, now, SPIRIT_BARRAGE_ID)) return SPIRIT_BARRAGE_ID;
    if (isCooldownReady(data, now, SOUL_SEEKER_ID)) return SOUL_SEEKER_ID;
    if (dist > maxR && isCooldownReady(data, now, SHADOW_STEP_ID)) return SHADOW_STEP_ID;
    if (phase == "2" && isCooldownReady(data, now, RAISE_THRALLS_ID)) return RAISE_THRALLS_ID;
    return null;
}

function buildParams(abilityId, phase, data) {
    var bond = phase == "bond";
    var p2 = phase == "2" || bond;

    // chargeTicks ≈ telegraph duration: big AoE ~1.5–1.8s, dash ~0.7–0.9s
    if (abilityId == POISON_FEAST_ID) {
        return AbilityAPI.params(
            "telegraphColor", 0xC0FF3030,
            "damage", p2 ? 17.0 : 14.0,
            "radius", p2 ? 5.8 : 5.0,
            "knockback", 0.95,
            "knockbackY", 0.28,
            "effectType", "poison",
            "effectDuration", p2 ? 100 : 80,
            "effectAmplifier", 1,
            "chargeTicks", bond ? 28 : (p2 ? 32 : 36)
        );
    }
    if (abilityId == DARK_CLEAVE_ID) {
        return AbilityAPI.params(
            "telegraphColor", 0xC0FF3030,
            "damage", p2 ? 16.0 : 13.0,
            "distance", p2 ? 6.2 : 5.5,
            "chargeTicks", bond ? 18 : (p2 ? 22 : 26),
            "activeTicks", 5,
            "radius", p2 ? 2.8 : 2.4,
            "coneHalfAngle", 65.0,
            "knockback", 1.5,
            "knockbackY", 0.32
        );
    }
    if (abilityId == SOUL_REND_ID) {
        return AbilityAPI.params(
            "telegraphColor", 0xC0FF3030,
            "damage", p2 ? 15.0 : 12.0,
            "radius", p2 ? 7.0 : 6.0,
            "coneHalfAngle", 42.0,
            "knockback", 0.75,
            "knockbackY", 0.22,
            "effectType", "wither",
            "effectDuration", p2 ? 80 : 60,
            "effectAmplifier", 0,
            "chargeTicks", bond ? 26 : (p2 ? 30 : 34)
        );
    }
    if (abilityId == SPIRIT_BARRAGE_ID) {
        return AbilityAPI.params(
            "telegraphColor", 0xC0FF3030,
            "damage", p2 ? 9.0 : 7.0,
            "shots", p2 ? 5 : 4,
            "distance", 15.0,
            "hitRadius", 1.9,
            "chargeTicks", p2 ? 24 : 28,
            "activeTicks", p2 ? 18 : 16,
            "knockback", 0.55,
            "knockbackY", 0.15
        );
    }
    if (abilityId == SOUL_SEEKER_ID) {
        var role = getRole(data);
        var spirit = role == "spirit";
        return AbilityAPI.params(
            "telegraphColor", 0xC0FF3030,
            "damage", p2 ? (spirit ? 12.0 : 11.0) : (spirit ? 10.0 : 9.0),
            "shots", p2 ? (spirit ? 3 : 2) : (spirit ? 2 : 1),
            "maxRange", p2 ? 44.0 : 40.0,
            "distance", p2 ? 44.0 : 40.0,
            "hitRadius", p2 ? 2.4 : 2.2,
            "chargeTicks", bond ? 22 : (p2 ? 26 : 30),
            "activeTicks", p2 ? 16 : 14,
            "knockback", 0.7,
            "knockbackY", 0.2
        );
    }
    if (abilityId == SOUL_ORBS_ID) {
        return AbilityAPI.params(
            "telegraph", 0,
            "telegraphColor", 0xC0FF3030,
            "damage", p2 ? 11.0 : 9.0,
            "shots", bond ? 4 : (p2 ? 4 : 3),
            "landRadius", p2 ? 2.8 : 2.5,
            "spreadRadius", p2 ? 5.2 : 4.5,
            "maxRange", p2 ? 32.0 : 28.0,
            "chargeTicks", bond ? 28 : (p2 ? 30 : 34),
            "activeTicks", p2 ? 20 : 18,
            "knockback", 0.75,
            "knockbackY", 0.24
        );
    }
    if (abilityId == RAISE_THRALLS_ID) {
        return AbilityAPI.params(
            "telegraphColor", 0xC0FF3030,
            "chargeTicks", bond ? 28 : 32,
            "activeTicks", 16,
            "summonCount", bond ? 3 : 2,
            "summonRadius", 3.5,
            "maxSummonedNearBoss", 4,
            "cloneTab", CLONE_TAB,
            "cloneName", CLONE_THRALL,
            "radius", 4.0,
            "effectType", "slowness",
            "effectDuration", 40,
            "effectAmplifier", 0
        );
    }
    if (abilityId == SHADOW_STEP_ID) {
        return AbilityAPI.params(
            "telegraphColor", 0xC0FF3030,
            "distance", p2 ? 12.0 : 10.0,
            "chargeTicks", bond ? 14 : 18,
            "activeTicks", 5,
            "damage", p2 ? 10.0 : 8.0,
            "knockback", 1.15,
            "knockbackY", 0.28,
            "hitRadius", 1.6
        );
    }
    return null;
}

function startTimers(npc) {
    var timers = npc.getTimers();
    if (timers == null) return;
    if (typeof timers.forceStart == "function") {
        timers.forceStart(TIMER_ID, 1, true);
        timers.forceStart(PHASE_CHECK_ID, 20, true);
    } else {
        timers.start(TIMER_ID, 1, true);
        timers.start(PHASE_CHECK_ID, 20, true);
    }
}

function getCastInterval(phase) {
    if (phase == "bond") return CAST_INTERVAL_BOND;
    if (phase == "2") return CAST_INTERVAL_PHASE2;
    return CAST_INTERVAL_PHASE1;
}

function getCooldownTicks(abilityId, phase) {
    var bond = phase == "bond";
    var p2 = phase == "2" || bond;
    if (abilityId == POISON_FEAST_ID) return bond ? 90 : (p2 ? 120 : 150);
    if (abilityId == DARK_CLEAVE_ID) return bond ? 48 : (p2 ? 58 : 72);
    if (abilityId == SOUL_REND_ID) return bond ? 70 : (p2 ? 85 : 105);
    if (abilityId == SPIRIT_BARRAGE_ID) return p2 ? 100 : 130;
    // Punish: заметный CD, не спам
    if (abilityId == SOUL_SEEKER_ID) return bond ? 90 : (p2 ? 120 : 150);
    if (abilityId == SOUL_ORBS_ID) return bond ? 85 : (p2 ? 110 : 140);
    if (abilityId == RAISE_THRALLS_ID) return bond ? 140 : 180;
    if (abilityId == SHADOW_STEP_ID) return bond ? 55 : (p2 ? 65 : 80);
    return 80;
}

function isCooldownReady(data, now, abilityId) {
    var key = CD_PREFIX + abilityId;
    if (!data.has(key)) return true;
    return now >= getInt(data, key);
}

function sayQuote(npc, phase) {
    var data = npc.getStoreddata();
    var role = getRole(data);
    var quotes;
    if (phase == "bond") {
        quotes = QUOTES_BOND;
    } else if (role == "spirit") {
        quotes = phase == "2" ? QUOTES_SPIRIT_2 : QUOTES_SPIRIT_1;
    } else {
        quotes = phase == "2" ? QUOTES_BODY_2 : QUOTES_BODY_1;
    }
    var idx = Math.floor(Math.random() * quotes.length);
    npc.say("§7" + quotes[idx]);
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

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}

function getFloat(data, key) {
    if (!data.has(key)) return 0;
    return parseFloat(String(data.get(key)));
}
