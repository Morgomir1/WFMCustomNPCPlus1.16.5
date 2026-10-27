// Великая Утроба, Горгон (Б3). В контейнере вторым после hs_core.js.
// Круг C5, r 21. Зона сжимается ступенями + спазмы; таран в стенку даёт
// стан и временное расширение. Большие AoE с анимациями модели gorgon.
HS.BASE_HP = 520;
HS.CAP = 14;
HS.ARENA_R = 21;
HS.ENRAGE_SEC = 99999; // soft-enrage из hs_core отключён (см. hsEnrage ниже)
ORIGIN = [-373089, 53, -13091];   // мировой блок пола. Сдвинул данж — впиши центр этой арены
GATE = hsBox(-2, 0, -12, 3, 2, 3);   // завал Глотки C5_down (413, 465)

/** Soft-enrage «Стадо не ждёт» / kill random — выключен. */
function hsEnrage(npc, ps, now) {}

// --- Зона / сжатие ---
var SAFE_R = 10.6;         // safeR на фазе 3
var SAFE_P1 = 17.0;        // фаза 1 (hp > 66%)
var SAFE_P2 = 13.5;        // фаза 2 (hp > 33%)
var RING_MIN_W = 0.75;
var EXPAND_BONUS = 2.5;    // после тарана в стену
var EXPAND_TICKS = 300;
var SPASM_CD = 400;        // период спазмов с фазы 2
var SPASM_WARN = 40;
var SPASM_ACTIVE = 70;
var SPASM_EXTRA = 4.5;     // доп. сжатие во время спазма
var WALL_EDGE = 2.0;       // дистанция до границы safeR = «стена»
var EDGE_DPS = 0.13;       // доля max HP игрока в секунду (было 0.10 за удар раз в секунду)
var EDGE_PERIOD = 5;       // тиков между проверками кольца (было окно в 20 — заход промахивался)
var EDGE_HEAL = 0.05;      // доля max HP босса в секунду за каждого игрока в кольце

// --- Удар пасти (усечённый конус) ---
var SLASH_DIST = 12.0;
var SLASH_NEAR_HW = 3.5;
var SLASH_HALF = 50.0;
var SLASH_CHARGE = 30;     // windup до impact (1.5s)
var SLASH_RECOVER = 10;    // откат после удара (0.5s) → клип ~2.0s
var SLASH_PCT = 0.70;
var SLASH_KB = 1.8;
var SLASH_KB_Y = 0.45;
var SLASH_CD = 200;
var DOUBLE_GAP = 8;        // пауза между ударами двойной пасти (0.4s)

// --- Топот брюхом ---
var STOMP_R = 7.0;
var STOMP_CHARGE = 25;     // windup до impact (1.25s)
var STOMP_RECOVER = 10;    // откат (0.5s) → клип ~1.75s
var STOMP_PCT = 0.45;
var STOMP_KB = 2.2;
var STOMP_KB_Y = 0.55;
var STOMP_CD = 280;

// --- Вдох утробы → топот ---
var INHALE_WIND = 40;      // idle2 2.0s
var INHALE_PULL = 0.35;
var INHALE_R = 16.0;
var INHALE_CD = 360;

// --- Таран ---
var RAM_CHARGE = 28;
var RAM_ACTIVE = 8;
var RAM_LEN_MIN = 12;
var RAM_LEN_MAX = 18;
var RAM_OVER = 4;
var RAM_LINE_W = 4.0;
var RAM_HIT_R = 2.0;
var RAM_DMG = 8.0;         // flat AbilityAPI (масштабируется hsStart)
var RAM_KB = 1.4;
var RAM_KB_Y = 0.35;
var RAM_CD = 260;
var RAM_PAD = 12;
var STUN_TICKS = 60;
var STUN_CAP_MUL = 2.0;

// --- Концентрические кольца (от центра арены) ---
var RINGS_CHARGE = 40;     // windup до волны 1 (2.0s)
var RINGS_GAP = 18;
var RINGS_RECOVER = 8;     // откат после волны 2 (0.4s)
var RINGS_PCT = 0.35;
var RINGS_CD = 400;

// --- Рёв-спицы ---
var SPOKES_N = 6;
var SPOKES_CHARGE = 36;    // windup до удара 1 (1.8s)
var SPOKES_W = 2.2;
var SPOKES_RECOVER = 8;    // откат после 2-го захода (0.4s)
var SPOKES_PCT = 0.40;
var SPOKES_CD = 380;
var SPOKES_GAP = 16;

// --- Лужи желудочного сока ---
var PUDDLE_R = 3.5;
var PUDDLE_TICKS = 400;
var PUDDLE_DMG = 2.5;
var PUDDLE_PERIOD = 20;

// Таймеры
var T_SLASH = 1, T_SLASH_HOLD = 3;
var T_STOMP = 4, T_INHALE = 5, T_INHALE_TICK = 6;
var T_RINGS = 7, T_RINGS2 = 8, T_SPOKES = 9, T_SPOKES2 = 10;
var T_RAM = 11, T_DOUBLE = 12, T_RECOVER = 13, T_RAM_GO = 14, T_EDGE = 15;

// Клипы beastmen_gorgon.animation.json (WAH-4440). Пока новых клипов нет в модели —
// держи false: играются старые attack/attack2/attack3/idle2/idle3.
var GORGON_NEW_ANIMS = true;
var ANIM_SLASH = GORGON_NEW_ANIMS ? "attack_slash" : "attack3";     // 2.00s: impact 1.50
var ANIM_STOMP = GORGON_NEW_ANIMS ? "attack_stomp" : "attack2";     // 1.75s: impact 1.25
var ANIM_INHALE = GORGON_NEW_ANIMS ? "attack_inhale" : "idle2";     // 2.00s, дальше stomp
var ANIM_RAM = GORGON_NEW_ANIMS ? "attack_ram" : "attack";          // 2.40s: dash 1.40–1.80
var ANIM_STUN = GORGON_NEW_ANIMS ? "stun_wall" : "idle2";           // 3.00s
var ANIM_ROAR = GORGON_NEW_ANIMS ? "attack_roar" : "idle3";         // 3.30s: волны 2.00 и 2.90
var ANIM_SPOKES = GORGON_NEW_ANIMS ? "attack_spokes" : "idle3";     // 3.00s: удары 1.80 и 2.60

// WFM sounds.json (ключи проверены на наличие .ogg)
var SND_STUN = "wfm:enchantment.pommel_strike_stun";
var SND_TREMOR = "wfm:enchantment.earth_tremor";
var SND_ROAR = "wfm:entity.gorgon.attack";               // рык на старте каста
var SND_GROWL = "wfm:entity.gorgon.ambient";
var SND_SWING = "wfm:entity.terracota_sentinel.swing";   // взмах пасти
var SND_BITE = "wfm:enchantment.snake_bite";             // щелчок челюстей
var SND_BREATH = "wfm:entity.hydra.fire_breath";         // вдох утробы (низкий pitch)
var SND_RAM_PREP = "wfm:entity.goat.prepare_ram";
var SND_RAM_GO = "wfm:entity.artillery.battering_ram_release";
var SND_RAM_HIT = "wfm:entity.goat.ram_impact";
var SND_BOOM = "wfm:entity.skull_cannon.impact";         // волны колец / спицы / удар в стену
var SND_SPOKES = "wfm:entity.skull_cannon.cannon_ball_roar";
var SND_BIG_ROAR = "minecraft:entity.ravager.roar";

var TURN_DEG = 5;
var NpcTurnHelper = Java.type("noppes.npcs.ai.NpcTurnHelper");

function gorgonSound(npc, sound, vol, pitch) {
    try {
        npc.getWorld().playSoundAt(npc.getPos(), sound, vol, pitch);
    } catch (err) {
        log("gorgon sound: " + err);
    }
}

function init(e) {
    var npc = e.npc;
    if (!hsInit(npc)) return;
    if (hsGet(npc, "base_speed", "") == "") hsPut(npc, "base_speed", npc.getAi().getWalkingSpeed());
    npc.getAi().setRetaliateType(0);
    npc.getAi().setWalkingSpeed(hsNum(npc, "base_speed"));
    try { NpcTurnHelper.apply(npc, TURN_DEG); } catch (err) { log("gorgon turn: " + err); }
    hsPut(npc, "phase", 1);
    hsPut(npc, "frenzy", 0);
    hsPut(npc, "slash_at", hsNow(npc) + 120);
    hsPut(npc, "ram_at", hsNow(npc) + 160);
    hsPut(npc, "stomp_at", hsNow(npc) + 280);
    hsPut(npc, "inhale_at", hsNow(npc) + 340);
    hsPut(npc, "rings_at", hsNow(npc) + 420);
    hsPut(npc, "spokes_at", hsNow(npc) + 460);
    hsPut(npc, "spasm_at", hsNow(npc) + SPASM_CD);
    hsPut(npc, "spasm_state", 0);   // 0 idle, 1 warn, 2 active
    hsPut(npc, "spasm_until", 0);
    hsPut(npc, "expand_until", 0);
    hsPut(npc, "expand_bonus", 0);
    hsPut(npc, "stun_until", 0);
    hsPut(npc, "stun_lock", 0);
    hsPut(npc, "post_spasm", 0);
    hsPut(npc, "ram_pending", 0);
    hsPut(npc, "ram_chain", 0);
    hsPut(npc, "double_next", 0);
    npc.getTimers().forceStart(T_EDGE, EDGE_PERIOD, true);
}

function cd(npc, ticks) {
    return hsGet(npc, "frenzy", "0") == "1" ? Math.round(ticks * 0.6) : ticks;
}

function gorgonWalkSpeed(npc) {
    var spd = hsNum(npc, "base_speed");
    if (hsGet(npc, "frenzy", "0") == "1") spd = Math.min(10, spd + 2);
    return spd;
}

function gorgonRestoreAi(npc) {
    npc.getAi().setWalkingSpeed(gorgonWalkSpeed(npc));
    npc.getAi().setRetaliateType(0);
    try { NpcTurnHelper.apply(npc, TURN_DEG); } catch (err) { log("gorgon turn: " + err); }
    try { NpcTurnHelper.unlockFace(npc); } catch (err) { log("gorgon unlock: " + err); }
}

function gorgonFreezeAi(npc) {
    npc.getAi().setWalkingSpeed(0);
    npc.getAi().setRetaliateType(3);
}

function gorgonAnim(npc, name) {
    if (name == null || name == "") return;
    try {
        npc.syncAnimationsForAll(NpcAPI.createAnimBuilder().playOnce(name));
    } catch (err) {
        log("gorgon anim: " + err);
    }
}

function gorgonBusy(npc) {
    if (AbilityAPI.isBusy(npc)) return true;
    var t = npc.getTimers();
    return t.has(T_SLASH) || t.has(T_STOMP) || t.has(T_INHALE)
        || t.has(T_RINGS) || t.has(T_RINGS2) || t.has(T_SPOKES) || t.has(T_SPOKES2)
        || t.has(T_RAM) || t.has(T_DOUBLE) || t.has(T_RECOVER);
}

// Держим позу после impact, пока доигрывается recovery-часть клипа.
function gorgonStartRecover(npc, ticks) {
    if (ticks == null || ticks <= 0) {
        gorgonRestoreAi(npc);
        hsPut(npc, "hold_mode", "");
        return;
    }
    hsPut(npc, "hold_mode", "cast");
    npc.getTimers().forceStart(T_RECOVER, ticks, false);
    npc.getTimers().forceStart(T_SLASH_HOLD, 1, true);
}

function gorgonStunned(npc) {
    return hsNow(npc) < hsNum(npc, "stun_until");
}

function gorgonPhase(hp) {
    if (hp > 0.66) return 1;
    if (hp > 0.33) return 2;
    return 3;
}

function gorgonBaseSafeR(phase) {
    if (phase <= 1) return SAFE_P1;
    if (phase == 2) return SAFE_P2;
    return SAFE_R;
}

function gorgonSafeR(npc, phase) {
    var now = hsNow(npc);
    var r = gorgonBaseSafeR(phase);
    if (now < hsNum(npc, "expand_until")) r = r + hsNum(npc, "expand_bonus");
    if (hsGet(npc, "spasm_state", "0") == "2") r = r - SPASM_EXTRA;
    if (r > HS.ARENA_R - RING_MIN_W) r = HS.ARENA_R - RING_MIN_W;
    if (r < SAFE_R - 1) r = SAFE_R - 1;
    return r;
}

// Жёсткий лок взгляда: GeckoLib крутит модель по клиентскому yBodyRot, который
// ванила не синкает — lockFace пишет rot на сервере и шлёт yaw клиентам пакетом.
// Снимается в gorgonRestoreAi.
function gorgonFaceYaw(npc, yaw) {
    if (yaw == null || isNaN(yaw)) return;
    try {
        NpcTurnHelper.lockFace(npc, yaw);
    } catch (err) {
        try { npc.setRotation(yaw); } catch (err2) {}
    }
}

function gorgonLockFace(npc, yaw) {
    hsPut(npc, "face_yaw", yaw);
    gorgonFaceYaw(npc, yaw);
}

function gorgonFaceTarget(npc, target) {
    var yaw = npc.getRotation();
    if (target != null && target.isAlive()) yaw = hsYawTo(npc, target);
    gorgonLockFace(npc, yaw);
    return yaw;
}

function gorgonHoldAt(npc, x, y, z, yaw) {
    Combat.stopNavigation(npc);
    Combat.holdInPlace(npc, x, y, z);
    Combat.zeroHorizontalMotion(npc);
    if (yaw == null || isNaN(yaw)) yaw = hsNum(npc, "face_yaw");
    gorgonFaceYaw(npc, yaw);
}

function gorgonHoldSlash(npc) {
    gorgonHoldAt(npc,
        hsNum(npc, "slash_x"), hsNum(npc, "slash_y"), hsNum(npc, "slash_z"),
        hsNum(npc, "slash_yaw"));
}

function gorgonSlashGeom(sx, sz, yaw) {
    var rad = (yaw + 90) * Math.PI / 180;
    var nx = Math.cos(rad), nz = Math.sin(rad);
    var tanHalf = Math.tan(SLASH_HALF * Math.PI / 180);
    var apexBack = Math.max(0.8, SLASH_NEAR_HW / Math.max(0.05, tanHalf));
    return {
        nx: nx, nz: nz,
        apexX: sx - nx * apexBack,
        apexZ: sz - nz * apexBack,
        apexBack: apexBack,
        minDist: apexBack + 1.0,
        maxDist: apexBack + SLASH_DIST
    };
}

function gorgonStartSlash(npc, yaw, followDouble) {
    var sx = npc.getX(), sy = npc.getY(), sz = npc.getZ();
    var g = gorgonSlashGeom(sx, sz, yaw);
    hsPut(npc, "slash_x", sx);
    hsPut(npc, "slash_y", sy);
    hsPut(npc, "slash_z", sz);
    hsPut(npc, "slash_yaw", yaw);
    hsPut(npc, "face_yaw", yaw);
    hsPut(npc, "slash_ax", g.apexX);
    hsPut(npc, "slash_az", g.apexZ);
    hsPut(npc, "slash_min", g.minDist);
    hsPut(npc, "slash_max", g.maxDist);
    hsPut(npc, "double_next", followDouble ? 1 : 0);
    // Двойная пасть: recovery только после второго удара.
    hsPut(npc, "recover_ticks", followDouble ? 0 : SLASH_RECOVER);

    gorgonFreezeAi(npc);
    gorgonHoldSlash(npc);
    gorgonAnim(npc, ANIM_SLASH);
    gorgonSound(npc, SND_ROAR, 2.0, 0.9);
    TelegraphAPI.coneTruncated(npc, g.apexX, sy, g.apexZ, yaw, g.minDist, g.maxDist, SLASH_HALF, SLASH_CHARGE, HS.RED);
    npc.getTimers().forceStart(T_SLASH, SLASH_CHARGE, false);
    npc.getTimers().forceStart(T_SLASH_HOLD, 1, true);
}

function gorgonSlashHit(npc, ps) {
    var ax = hsNum(npc, "slash_ax"), az = hsNum(npc, "slash_az");
    var bx = hsNum(npc, "slash_x"), bz = hsNum(npc, "slash_z");
    var yaw = hsNum(npc, "slash_yaw");
    var minD = hsNum(npc, "slash_min"), maxD = hsNum(npc, "slash_max");
    var rad = (yaw + 90) * Math.PI / 180;
    var fx = Math.cos(rad), fz = Math.sin(rad);
    gorgonSound(npc, SND_SWING, 2.0, 0.6);
    gorgonSound(npc, SND_BITE, 2.0, 0.6);
    for (var i = 0; i < ps.length; i++) {
        var p = ps[i];
        var dist = hsFlat(p, ax, az);
        if (dist < minD || dist > maxD) continue;
        if (!Combat.isInFrontCone(ax, az, yaw, p, SLASH_HALF)) continue;
        hsStrike(p, SLASH_PCT);
        try {
            var dx = p.getX() - bx, dz = p.getZ() - bz;
            var len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.05) { dx = fx; dz = fz; len = 1; }
            p.setMotionX((dx / len) * SLASH_KB);
            p.setMotionY(SLASH_KB_Y);
            p.setMotionZ((dz / len) * SLASH_KB);
        } catch (err) {
            log("gorgon knock: " + err);
        }
    }
}

function gorgonKnockFrom(npc, p, ox, oz, kb, kbY) {
    try {
        var dx = p.getX() - ox, dz = p.getZ() - oz;
        var len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.05) {
            var h = hsHome(npc);
            dx = p.getX() - h[0];
            dz = p.getZ() - h[2];
            len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.05) { dx = 1; dz = 0; len = 1; }
        }
        p.setMotionX((dx / len) * kb);
        p.setMotionY(kbY);
        p.setMotionZ((dz / len) * kb);
    } catch (err) {
        log("gorgon knockFrom: " + err);
    }
}

function gorgonZoneGet(npc) {
    var td = npc.getTempdata();
    if (td == null || !td.has("hs_zone")) return null;
    var z = td.get("hs_zone");
    try {
        if (z == null || z.removed) {
            td.remove("hs_zone");
            return null;
        }
    } catch (err) {
        td.remove("hs_zone");
        return null;
    }
    return z;
}

function gorgonUpdateRing(npc, h, safeR) {
    var outer = HS.ARENA_R;
    var inner = safeR;
    if (inner > outer - RING_MIN_W) inner = outer - RING_MIN_W;
    var zone = gorgonZoneGet(npc);
    if (zone == null) {
        zone = ZoneAPI.hazardRing(npc, h[0], h[1], h[2], outer, inner, 200, 0, 20);
        if (zone == null) return;
        zone.setColor(HS.RED);
        hsZonePut(npc, zone);
        return;
    }
    zone.setRadius(outer);
    zone.setInnerRadius(inner);
    zone.setLifetimeTicks(200);
}

function gorgonPuddle(npc, x, y, z) {
    var dmg = PUDDLE_DMG * hsScale(npc.getWorld());
    var zone = ZoneAPI.hazardCircle(npc, x, y, z, PUDDLE_R, PUDDLE_TICKS, dmg, PUDDLE_PERIOD);
    if (zone == null) return;
    try { zone.setColor(HS.RED); } catch (err) {}
}

function gorgonNearWall(npc, safeR) {
    var h = hsHome(npc);
    return hsFlat(npc, h[0], h[2]) >= safeR - WALL_EDGE;
}

// Кольцо за safeR. Доля за один пульс = величина в секунду * период / 20.
function gorgonEdgePulse(npc) {
    if (hsNum(npc, "fight_at") == 0) return;
    var phase = gorgonPhase(npc.getHealth() / npc.getMaxHealth());
    var safeR = gorgonSafeR(npc, phase);
    if (safeR >= HS.ARENA_R - 0.25) return;
    var ps = hsPlayers(npc);
    var h = hsHome(npc);
    var hit = 0;
    var share = EDGE_PERIOD / 20.0;
    var i;
    for (i = 0; i < ps.length; i++) {
        if (hsFlat(ps[i], h[0], h[2]) <= safeR) continue;
        hsStrike(ps[i], EDGE_DPS * share);
        hit++;
    }
    if (hit <= 0) return;
    var heal = npc.getMaxHealth() * EDGE_HEAL * share * hit;
    npc.setHealth(Math.min(npc.getMaxHealth(), npc.getHealth() + heal));
}

// --- Спазмы ---
function gorgonSpasmTick(npc, h, phase, safeR, now) {
    if (phase < 2) {
        hsPut(npc, "spasm_state", 0);
        return safeR;
    }
    var state = hsNum(npc, "spasm_state");
    if (state == 0 && now >= hsNum(npc, "spasm_at")) {
        hsPut(npc, "spasm_state", 1);
        hsPut(npc, "spasm_until", now + SPASM_WARN);
        var future = safeR - SPASM_EXTRA;
        if (future < SAFE_R - 1) future = SAFE_R - 1;
        TelegraphAPI.ring(npc, h[0], h[1], h[2], safeR, future, SPASM_WARN, HS.RED);
        npc.say("§4Утроба содрогается!");
        gorgonSound(npc, SND_GROWL, 3.0, 0.5);
        return safeR;
    }
    if (state == 1 && now >= hsNum(npc, "spasm_until")) {
        hsPut(npc, "spasm_state", 2);
        hsPut(npc, "spasm_until", now + SPASM_ACTIVE);
        npc.say("§4Яма сжимается!");
        return gorgonSafeR(npc, phase);
    }
    if (state == 2 && now >= hsNum(npc, "spasm_until")) {
        hsPut(npc, "spasm_state", 0);
        hsPut(npc, "spasm_at", now + cd(npc, SPASM_CD));
        hsPut(npc, "post_spasm", 1);
        hsPut(npc, "stomp_at", now + 10);
        return gorgonSafeR(npc, phase);
    }
    return gorgonSafeR(npc, phase);
}

// --- Топот ---
function gorgonStartStomp(npc) {
    var x = npc.getX(), y = npc.getY(), z = npc.getZ();
    hsPut(npc, "stomp_x", x);
    hsPut(npc, "stomp_y", y);
    hsPut(npc, "stomp_z", z);
    gorgonFreezeAi(npc);
    gorgonFaceTarget(npc, npc.getAttackTarget());
    gorgonHoldAt(npc, x, y, z, hsNum(npc, "face_yaw"));
    gorgonAnim(npc, ANIM_STOMP);
    gorgonSound(npc, SND_GROWL, 2.0, 0.7);
    TelegraphAPI.circle(npc, x, y, z, STOMP_R, STOMP_CHARGE, HS.RED);
    npc.getTimers().forceStart(T_STOMP, STOMP_CHARGE, false);
    npc.getTimers().forceStart(T_SLASH_HOLD, 1, true);
    hsPut(npc, "hold_mode", "stomp");
}

function gorgonStompHit(npc, ps) {
    var ox = hsNum(npc, "stomp_x"), oz = hsNum(npc, "stomp_z");
    gorgonSound(npc, SND_TREMOR, 2.0, 0.9);
    gorgonSound(npc, SND_BOOM, 1.5, 0.6);
    for (var i = 0; i < ps.length; i++) {
        if (hsFlat(ps[i], ox, oz) > STOMP_R) continue;
        hsStrike(ps[i], STOMP_PCT);
        gorgonKnockFrom(npc, ps[i], ox, oz, STOMP_KB, STOMP_KB_Y);
    }
    gorgonPuddle(npc, ox, hsNum(npc, "stomp_y"), oz);
}

// --- Вдох ---
function gorgonStartInhale(npc) {
    var x = npc.getX(), y = npc.getY(), z = npc.getZ();
    hsPut(npc, "inhale_x", x);
    hsPut(npc, "inhale_y", y);
    hsPut(npc, "inhale_z", z);
    gorgonFreezeAi(npc);
    gorgonFaceTarget(npc, npc.getAttackTarget());
    gorgonHoldAt(npc, x, y, z, hsNum(npc, "face_yaw"));
    gorgonAnim(npc, ANIM_INHALE);
    gorgonSound(npc, SND_BREATH, 2.0, 0.5);
    TelegraphAPI.circle(npc, x, y, z, STOMP_R, INHALE_WIND + STOMP_CHARGE, HS.RED);
    npc.getTimers().forceStart(T_INHALE, INHALE_WIND, false);
    npc.getTimers().forceStart(T_INHALE_TICK, 1, true);
    hsPut(npc, "hold_mode", "inhale");
}

function gorgonInhalePull(npc, ps) {
    var ox = hsNum(npc, "inhale_x"), oz = hsNum(npc, "inhale_z");
    for (var i = 0; i < ps.length; i++) {
        var p = ps[i];
        var dist = hsFlat(p, ox, oz);
        if (dist > INHALE_R || dist < 1.2) continue;
        try {
            var dx = ox - p.getX(), dz = oz - p.getZ();
            var len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.05) continue;
            p.setMotionX((dx / len) * INHALE_PULL);
            p.setMotionZ((dz / len) * INHALE_PULL);
        } catch (err) {}
    }
}

// --- Кольца ---
function gorgonStartRings(npc) {
    var h = hsHome(npc);
    hsPut(npc, "rings_x", h[0]);
    hsPut(npc, "rings_y", h[1]);
    hsPut(npc, "rings_z", h[2]);
    gorgonFreezeAi(npc);
    gorgonFaceTarget(npc, npc.getAttackTarget());
    gorgonHoldAt(npc, npc.getX(), npc.getY(), npc.getZ(), hsNum(npc, "face_yaw"));
    gorgonAnim(npc, ANIM_ROAR);
    gorgonSound(npc, SND_BIG_ROAR, 3.0, 0.7);
    // Волна 1: 0–5 и 10–15
    TelegraphAPI.circle(npc, h[0], h[1], h[2], 5.0, RINGS_CHARGE, HS.RED);
    TelegraphAPI.ring(npc, h[0], h[1], h[2], 15.0, 10.0, RINGS_CHARGE, HS.RED);
    npc.getTimers().forceStart(T_RINGS, RINGS_CHARGE, false);
    hsPut(npc, "hold_mode", "cast");
    npc.getTimers().forceStart(T_SLASH_HOLD, 1, true);
}

function gorgonRingBandHit(npc, ps, inner, outer) {
    var ox = hsNum(npc, "rings_x"), oz = hsNum(npc, "rings_z");
    gorgonSound(npc, SND_BOOM, 2.0, 0.5 + inner / 30);
    for (var i = 0; i < ps.length; i++) {
        var d = hsFlat(ps[i], ox, oz);
        if (d < inner || d > outer) continue;
        hsStrike(ps[i], RINGS_PCT);
    }
}

// --- Спицы ---
function gorgonStartSpokes(npc, offsetDeg) {
    var x = npc.getX(), y = npc.getY(), z = npc.getZ();
    var h = hsHome(npc);
    var len = HS.ARENA_R + 2;
    hsPut(npc, "spokes_x", x);
    hsPut(npc, "spokes_y", y);
    hsPut(npc, "spokes_z", z);
    hsPut(npc, "spokes_off", offsetDeg);
    gorgonFreezeAi(npc);
    gorgonFaceTarget(npc, npc.getAttackTarget());
    gorgonHoldAt(npc, x, y, z, hsNum(npc, "face_yaw"));
    gorgonAnim(npc, ANIM_SPOKES);
    gorgonSound(npc, SND_ROAR, 2.5, 0.7);
    var step = 360.0 / SPOKES_N;
    for (var i = 0; i < SPOKES_N; i++) {
        var yaw = offsetDeg + i * step;
        TelegraphAPI.line(npc, x, y, z, yaw, len, SPOKES_W * 2, SPOKES_CHARGE, HS.RED);
    }
    npc.getTimers().forceStart(T_SPOKES, SPOKES_CHARGE, false);
    hsPut(npc, "hold_mode", "cast");
    npc.getTimers().forceStart(T_SLASH_HOLD, 1, true);
}

function gorgonSpokesHit(npc, ps) {
    var sx = hsNum(npc, "spokes_x"), sz = hsNum(npc, "spokes_z");
    var off = hsNum(npc, "spokes_off");
    var len = HS.ARENA_R + 2;
    var step = 360.0 / SPOKES_N;
    gorgonSound(npc, SND_SPOKES, 2.5, 0.8);
    gorgonSound(npc, SND_BOOM, 2.0, 0.7);
    for (var i = 0; i < SPOKES_N; i++) {
        var yaw = off + i * step;
        for (var j = 0; j < ps.length; j++) {
            if (hsOnLine(ps[j], sx, sz, yaw, len, SPOKES_W)) hsStrike(ps[j], SPOKES_PCT);
        }
    }
}

// --- Таран ---
function gorgonRamLen(npc, target) {
    var dist = hsFlat(npc, target.getX(), target.getZ());
    var len = dist + RAM_OVER;
    if (len < RAM_LEN_MIN) len = RAM_LEN_MIN;
    if (len > RAM_LEN_MAX) len = RAM_LEN_MAX;
    return len;
}

function gorgonStartRam(npc, target) {
    if (target == null || !target.isAlive()) return false;
    var yaw = hsYawTo(npc, target);
    var len = gorgonRamLen(npc, target);
    hsPut(npc, "ram_x", npc.getX());
    hsPut(npc, "ram_z", npc.getZ());
    hsPut(npc, "ram_yaw", yaw);
    hsPut(npc, "face_yaw", yaw);
    hsPut(npc, "ram_len", len);
    hsPut(npc, "ram_pending", 1);
    gorgonFreezeAi(npc);
    Combat.stopNavigation(npc);
    gorgonFaceYaw(npc, yaw);
    gorgonAnim(npc, ANIM_RAM);
    gorgonSound(npc, SND_RAM_PREP, 2.0, 0.6);
    TelegraphAPI.line(npc, npc.getX(), npc.getY(), npc.getZ(), yaw, len, RAM_LINE_W, RAM_CHARGE, HS.RED);
    var started = hsStart(npc, "dash", target, AbilityAPI.params(
        "chargeTicks", RAM_CHARGE,
        "activeTicks", RAM_ACTIVE,
        "distance", len * 1.0,
        "damage", RAM_DMG,
        "hitRadius", RAM_HIT_R,
        "knockback", RAM_KB,
        "knockbackY", RAM_KB_Y
    ));
    if (!started) {
        hsPut(npc, "ram_pending", 0);
        gorgonRestoreAi(npc);
        return false;
    }
    npc.getTimers().forceStart(T_RAM, RAM_CHARGE + RAM_ACTIVE + RAM_PAD, false);
    npc.getTimers().forceStart(T_RAM_GO, RAM_CHARGE, false);
    return true;
}

function gorgonResolveRam(npc, phase) {
    if (hsGet(npc, "ram_pending", "0") != "1") return;
    hsPut(npc, "ram_pending", 0);
    var safeR = gorgonSafeR(npc, phase);
    if (gorgonNearWall(npc, safeR)) {
        gorgonSound(npc, SND_BOOM, 2.5, 0.6);
        gorgonWallCrash(npc);
        hsPut(npc, "ram_chain", 0);
        return;
    }
    // Фаза 3 / frenzy: второй таран подряд
    if (hsNum(npc, "ram_chain") > 0) {
        hsPut(npc, "ram_chain", hsNum(npc, "ram_chain") - 1);
        var ps = hsPlayers(npc);
        var t = npc.getAttackTarget();
        if ((t == null || !t.isAlive()) && ps.length > 0) t = ps[0];
        if (t != null && t.isAlive()) {
            gorgonRestoreAi(npc);
            gorgonStartRam(npc, t);
            return;
        }
    }
    gorgonRestoreAi(npc);
}

function gorgonWallCrash(npc) {
    var now = hsNow(npc);
    hsPut(npc, "stun_until", now + STUN_TICKS);
    hsPut(npc, "stun_lock", 1);
    hsPut(npc, "expand_bonus", EXPAND_BONUS);
    hsPut(npc, "expand_until", now + EXPAND_TICKS);
    gorgonFreezeAi(npc);
    gorgonLockFace(npc, npc.getRotation());
    gorgonHoldAt(npc, npc.getX(), npc.getY(), npc.getZ(), hsNum(npc, "face_yaw"));
    gorgonAnim(npc, ANIM_STUN);
    gorgonSound(npc, SND_STUN, 2.5, 1.0);
    npc.say("§6Утроба отрыгнула! Горгон оглушён!");
}

function gorgonHoldMode(npc) {
    var mode = hsGet(npc, "hold_mode", "");
    var face = hsNum(npc, "face_yaw");
    if (mode == "stomp") {
        gorgonHoldAt(npc, hsNum(npc, "stomp_x"), hsNum(npc, "stomp_y"), hsNum(npc, "stomp_z"), face);
        return;
    }
    if (mode == "inhale") {
        gorgonHoldAt(npc, hsNum(npc, "inhale_x"), hsNum(npc, "inhale_y"), hsNum(npc, "inhale_z"), face);
        return;
    }
    if (mode == "cast") {
        Combat.stopNavigation(npc);
        Combat.zeroHorizontalMotion(npc);
        gorgonFaceYaw(npc, face);
        return;
    }
    if (npc.getTimers().has(T_SLASH)) gorgonHoldSlash(npc);
}

function gorgonTryCast(npc, ps, t, now, phase) {
    if (gorgonStunned(npc) || gorgonBusy(npc)) return;

    // После спазма — топот в центр
    if (hsGet(npc, "post_spasm", "0") == "1" && phase >= 2 && now >= hsNum(npc, "stomp_at")) {
        hsPut(npc, "post_spasm", 0);
        hsPut(npc, "stomp_at", now + cd(npc, STOMP_CD));
        gorgonStartStomp(npc);
        return;
    }

    if (now >= hsNum(npc, "ram_at")) {
        hsPut(npc, "ram_at", now + cd(npc, RAM_CD));
        hsPut(npc, "ram_chain", (phase >= 3 || hsGet(npc, "frenzy", "0") == "1") ? 1 : 0);
        if (gorgonStartRam(npc, t)) return;
    }

    if (phase >= 2 && now >= hsNum(npc, "inhale_at")) {
        hsPut(npc, "inhale_at", now + cd(npc, INHALE_CD));
        gorgonStartInhale(npc);
        return;
    }

    if (phase >= 3 && now >= hsNum(npc, "rings_at")) {
        hsPut(npc, "rings_at", now + cd(npc, RINGS_CD));
        gorgonStartRings(npc);
        return;
    }

    if (phase >= 3 && now >= hsNum(npc, "spokes_at")) {
        hsPut(npc, "spokes_at", now + cd(npc, SPOKES_CD));
        gorgonStartSpokes(npc, 0);
        return;
    }

    if (phase >= 2 && now >= hsNum(npc, "stomp_at")) {
        hsPut(npc, "stomp_at", now + cd(npc, STOMP_CD));
        gorgonStartStomp(npc);
        return;
    }

    if (now >= hsNum(npc, "slash_at")) {
        hsPut(npc, "slash_at", now + cd(npc, SLASH_CD));
        var yaw = hsYawTo(npc, t);
        var dbl = phase >= 3 && Math.random() < 0.55;
        gorgonStartSlash(npc, yaw, dbl);
    }
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) { AbilityAPI.cancel(npc); return; }
    var ps = hsFight(npc);
    if (ps == null) return;
    var now = hsNow(npc);
    var hp = npc.getHealth() / npc.getMaxHealth();
    var h = hsHome(npc);
    var phase = gorgonPhase(hp);

    if (phase != hsNum(npc, "phase")) {
        hsPut(npc, "phase", phase);
        if (phase == 2) {
            npc.say("§4Перистальтика! Утроба пульсирует!");
            hsPut(npc, "spasm_at", now + 80);
        }
        if (phase == 3) npc.say("§4Переваривание... бежать некуда!");
    }

    var safeR = gorgonSafeR(npc, phase);
    safeR = gorgonSpasmTick(npc, h, phase, safeR, now);
    gorgonUpdateRing(npc, h, safeR);
    if (!npc.getTimers().has(T_EDGE)) npc.getTimers().forceStart(T_EDGE, EDGE_PERIOD, true);

    if (hsGet(npc, "frenzy", "0") == "0" && hp <= 0.25) {
        hsPut(npc, "frenzy", 1);
        if (!gorgonBusy(npc) && !gorgonStunned(npc)) {
            npc.getAi().setWalkingSpeed(gorgonWalkSpeed(npc));
        }
        npc.say("§4Горгон в бешенстве!");
    }

    // Стан после стены
    if (gorgonStunned(npc)) {
        gorgonFreezeAi(npc);
        gorgonHoldAt(npc, npc.getX(), npc.getY(), npc.getZ(), hsNum(npc, "face_yaw"));
        return;
    }
    if (hsGet(npc, "stun_lock", "0") == "1") {
        hsPut(npc, "stun_lock", 0);
        gorgonRestoreAi(npc);
    }

    // Hold во время кастов
    if (npc.getTimers().has(T_SLASH) || npc.getTimers().has(T_STOMP)
            || npc.getTimers().has(T_INHALE) || npc.getTimers().has(T_RINGS)
            || npc.getTimers().has(T_RINGS2) || npc.getTimers().has(T_SPOKES)
            || npc.getTimers().has(T_SPOKES2) || npc.getTimers().has(T_DOUBLE)
            || npc.getTimers().has(T_RECOVER)) {
        gorgonHoldMode(npc);
        return;
    }

    // Таран ещё в AbilityAPI — ждём resolve по таймеру
    if (hsGet(npc, "ram_pending", "0") == "1") {
        gorgonFaceYaw(npc, hsNum(npc, "ram_yaw"));
        if (!AbilityAPI.isBusy(npc) && !npc.getTimers().has(T_RAM)) {
            gorgonResolveRam(npc, phase);
        }
        return;
    }

    if (AbilityAPI.isBusy(npc)) return;

    var t = npc.getAttackTarget();
    if (t == null || !t.isAlive()) return;
    gorgonTryCast(npc, ps, t, now, phase);
}

function timer(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    var ps = hsPlayers(npc);
    var hp = npc.getHealth() / npc.getMaxHealth();
    var phase = gorgonPhase(hp);
    var i;

    if (e.id == T_SLASH_HOLD) {
        if (!npc.getTimers().has(T_SLASH) && !npc.getTimers().has(T_STOMP)
                && !npc.getTimers().has(T_RINGS) && !npc.getTimers().has(T_RINGS2)
                && !npc.getTimers().has(T_SPOKES) && !npc.getTimers().has(T_SPOKES2)
                && !npc.getTimers().has(T_DOUBLE) && !npc.getTimers().has(T_INHALE)
                && !npc.getTimers().has(T_RECOVER)) {
            npc.getTimers().stop(T_SLASH_HOLD);
            if (!gorgonStunned(npc)) gorgonRestoreAi(npc);
            hsPut(npc, "hold_mode", "");
            return;
        }
        gorgonHoldMode(npc);
        return;
    }

    if (e.id == T_SLASH) {
        gorgonHoldSlash(npc);
        gorgonSlashHit(npc, ps);
        if (hsGet(npc, "double_next", "0") == "1") {
            hsPut(npc, "double_next", 0);
            hsPut(npc, "hold_mode", "cast");
            npc.getTimers().forceStart(T_DOUBLE, DOUBLE_GAP, false);
            npc.getTimers().forceStart(T_SLASH_HOLD, 1, true);
            return;
        }
        gorgonStartRecover(npc, hsNum(npc, "recover_ticks"));
        return;
    }

    if (e.id == T_DOUBLE) {
        var yaw2 = hsNum(npc, "slash_yaw") + 180;
        if (yaw2 >= 360) yaw2 = yaw2 - 360;
        gorgonStartSlash(npc, yaw2, false);
        return;
    }

    if (e.id == T_STOMP) {
        gorgonStompHit(npc, ps);
        gorgonStartRecover(npc, STOMP_RECOVER);
        return;
    }

    if (e.id == T_RECOVER) {
        npc.getTimers().stop(T_SLASH_HOLD);
        if (!gorgonStunned(npc)) gorgonRestoreAi(npc);
        hsPut(npc, "hold_mode", "");
        return;
    }

    if (e.id == T_INHALE_TICK) {
        if (!npc.getTimers().has(T_INHALE)) {
            npc.getTimers().stop(T_INHALE_TICK);
            return;
        }
        gorgonHoldMode(npc);
        gorgonInhalePull(npc, ps);
        return;
    }

    if (e.id == T_INHALE) {
        npc.getTimers().stop(T_INHALE_TICK);
        // Сразу топот после вдоха
        hsPut(npc, "stomp_x", hsNum(npc, "inhale_x"));
        hsPut(npc, "stomp_y", hsNum(npc, "inhale_y"));
        hsPut(npc, "stomp_z", hsNum(npc, "inhale_z"));
        gorgonAnim(npc, ANIM_STOMP);
        TelegraphAPI.circle(npc, hsNum(npc, "inhale_x"), hsNum(npc, "inhale_y"), hsNum(npc, "inhale_z"),
            STOMP_R, STOMP_CHARGE, HS.RED);
        hsPut(npc, "hold_mode", "stomp");
        npc.getTimers().forceStart(T_STOMP, STOMP_CHARGE, false);
        npc.getTimers().forceStart(T_SLASH_HOLD, 1, true);
        return;
    }

    if (e.id == T_RINGS) {
        gorgonRingBandHit(npc, ps, 0, 5.0);
        gorgonRingBandHit(npc, ps, 10.0, 15.0);
        // Волна 2: 5–10 и 15–outer
        var h = hsHome(npc);
        var outer = Math.min(HS.ARENA_R, gorgonSafeR(npc, phase) + 1);
        TelegraphAPI.ring(npc, h[0], h[1], h[2], 10.0, 5.0, RINGS_GAP, HS.RED);
        if (outer > 15.5) TelegraphAPI.ring(npc, h[0], h[1], h[2], outer, 15.0, RINGS_GAP, HS.RED);
        npc.getTimers().forceStart(T_RINGS2, RINGS_GAP, false);
        return;
    }

    if (e.id == T_RINGS2) {
        gorgonRingBandHit(npc, ps, 5.0, 10.0);
        gorgonRingBandHit(npc, ps, 15.0, HS.ARENA_R + 1);
        gorgonStartRecover(npc, RINGS_RECOVER);
        return;
    }

    if (e.id == T_SPOKES) {
        gorgonSpokesHit(npc, ps);
        npc.getTimers().forceStart(T_SPOKES2, SPOKES_GAP, false);
        // Второй заход со сдвигом на полшага — телеграф сразу
        var x = hsNum(npc, "spokes_x"), y = hsNum(npc, "spokes_y"), z = hsNum(npc, "spokes_z");
        var off = 180.0 / SPOKES_N;
        hsPut(npc, "spokes_off", off);
        var step = 360.0 / SPOKES_N;
        var len = HS.ARENA_R + 2;
        for (i = 0; i < SPOKES_N; i++) {
            TelegraphAPI.line(npc, x, y, z, off + i * step, len, SPOKES_W * 2, SPOKES_GAP, HS.RED);
        }
        return;
    }

    if (e.id == T_SPOKES2) {
        gorgonSpokesHit(npc, ps);
        gorgonStartRecover(npc, SPOKES_RECOVER);
        return;
    }

    if (e.id == T_RAM) {
        gorgonResolveRam(npc, phase);
        return;
    }

    if (e.id == T_RAM_GO) {
        gorgonSound(npc, SND_RAM_GO, 2.0, 0.6);
        gorgonSound(npc, SND_RAM_HIT, 2.0, 0.6);
        return;
    }

    if (e.id == T_EDGE) {
        gorgonEdgePulse(npc);
        return;
    }
}

function meleeAttack(e) {
    e.setCanceled(true);
}

function damaged(e) {
    var mul = 1;
    if (gorgonStunned(e.npc)) mul = STUN_CAP_MUL;
    hsCap(e, mul);
}

function died(e) {
    var npc = e.npc;
    AbilityAPI.cancel(npc);
    try {
        var ids = [T_SLASH, T_SLASH_HOLD, T_STOMP, T_INHALE, T_INHALE_TICK,
            T_RINGS, T_RINGS2, T_SPOKES, T_SPOKES2, T_RAM, T_DOUBLE, T_RECOVER, T_RAM_GO, T_EDGE];
        for (var i = 0; i < ids.length; i++) npc.getTimers().stop(ids[i]);
    } catch (err) {}
    try { NpcTurnHelper.unlockFace(npc); } catch (err) {}
    hsZoneClear(npc);
    hsSetGate(npc, false);
    npc.say("§7Утроба затихла. Глотка открыта.");
}
