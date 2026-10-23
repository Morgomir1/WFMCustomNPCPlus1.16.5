// Гарракс Расколотый Рог (Б4). В контейнере вторым после hs_core.js.
// Круг D3, r 26. Фазы в storeddata: 1 → 2 → 2b → 3.
// setSize меняет и GeckoLib: RenderCustomNpc.scale умножает модель на 0.2 * size.
// Устье — пробка, которую ставит строитель, а не всё кольцо барьера.
// Кольцо (y входа +1…+4, между r+1 и r+3) остаётся. Скрипт открывает только
// южный проём 3×3×3: cx-1…+1, y1+1…+3, cz+27…+29. Плюс столбик у выхода на винт.
HS.BASE_HP = 900;
HS.CAP = 14;
HS.ARENA_R = 26;
HS.ENRAGE_SEC = 600;
ORIGIN = [230, 8, 602];    // мировой блок пола. Сдвинул данж — впиши центр этой арены
GATE = hsBox(-32, 0, 10, 1, 2, 1);
GATE = GATE.concat(hsBox(-1, 73, 27, 3, 3, 3));
GATE_BLOCK = "minecraft:barrier";
SUMMONS = ["Гор (призыв)"];

var HEARTS = [ [0, 0, 22], [-19, 0, -13], [19, 0, -13] ];
var CRYSTALS = [ [-10, 0, 10], [10, 0, 10], [-10, 0, -10], [10, 0, -10] ];
var HEART = "Сердце жёлоба", CRYSTAL = "Варп-кристалл", ADD = "Гор (призыв)";
var HEARTS_TICKS = 900;
var ADD_CAP = 6;
var T_HIT = 1, T_BRAND = 2, T_HEARTS = 3, T_CIRCLES = 4;

function init(e) {
    var npc = e.npc;
    if (!hsInit(npc)) return;
    hsDespawnAll(npc, HEART);
    hsDespawnAll(npc, CRYSTAL);
    if (hsGet(npc, "base_speed", "") == "") hsPut(npc, "base_speed", npc.getAi().getWalkingSpeed());
    npc.getAi().setWalkingSpeed(hsNum(npc, "base_speed"));
    npc.getAi().setRetaliateType(0);
    npc.getDisplay().setSize(10);
    hsPut(npc, "phase", "1");
    hsPut(npc, "rite_done", 0);
    hsPut(npc, "cast_at", 0);
    hsPut(npc, "brand_at", 0);
    hsPut(npc, "circles_at", 0);
    hsPut(npc, "wave_at", 0);
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) { AbilityAPI.cancel(npc); return; }
    var ps = hsFight(npc);
    if (ps == null) return;
    var now = hsNow(npc);
    var hp = npc.getHealth() / npc.getMaxHealth();
    var ph = hsGet(npc, "phase", "1");
    var i;

    if (ph == "1" && hp <= 0.6) {
        AbilityAPI.cancel(npc);
        hsPut(npc, "phase", "2");
        npc.getAi().setRetaliateType(3);
        npc.getAi().setWalkingSpeed(0);
        var stone = hsOrigin(npc);
        npc.setPosition(stone[0] + 0.5, stone[1], stone[2] + 0.5);
        for (i = 0; i < HEARTS.length; i++) hsSpawnAt(npc, HEARTS[i], HEART);
        npc.getTimers().forceStart(T_HEARTS, HEARTS_TICKS, false);
        npc.say("§4КРОВЬ — КАМНЮ! У вас 45 ударов сердца!");
        return;
    }
    if (ph == "2") {
        if (hsCount(npc, HEART) == 0) {
            npc.getTimers().stop(T_HEARTS);
            hsPut(npc, "phase", "2b");
            npc.getAi().setWalkingSpeed(hsNum(npc, "base_speed"));
            npc.getAi().setRetaliateType(0);
            npc.say("§cЖёлобы пусты…");
        } else if (now >= hsNum(npc, "wave_at")) {
            hsPut(npc, "wave_at", now + 200);
            summonAdds(npc);
        }
        return;
    }
    if (ph == "2b" && hp <= 0.3) {
        hsPut(npc, "phase", "3");
        npc.getDisplay().setSize(12);
        for (i = 0; i < CRYSTALS.length; i++) hsSpawnAt(npc, CRYSTALS[i], CRYSTAL);
        npc.say("§2Ночь Тайн пришла!");
        ph = "3";
    }
    var rite = ph == "3" && hsGet(npc, "rite_done", "0") == "0";
    if (rite && hsCount(npc, CRYSTAL) == 0) {
        hsPut(npc, "rite_done", 1);
        rite = false;
        npc.getDisplay().setSize(10);
        npc.setHealth(Math.max(1, npc.getHealth() - npc.getMaxHealth() * 0.15));
        npc.say("§cНЕТ! Камень… молчит…");
    }

    if (now >= hsNum(npc, "brand_at") && !npc.getTimers().has(T_BRAND)) {
        hsPut(npc, "brand_at", now + 400);
        var b = hsRandom(ps);
        hsPut(npc, "brand_uuid", b.getUUID());
        TelegraphAPI.follow(TelegraphAPI.circle(npc, b.getX(), b.getY(), b.getZ(), 3.0, 120, HS.GOLD), b);
        b.addPotionEffect(PotionEffectType_GLOWING, 6, 0, false);
        for (i = 0; i < ps.length; i++) ps[i].message("§6Клеймо на §f" + b.getName() + "§6 — встаньте рядом и разделите удар!");
        npc.getTimers().forceStart(T_BRAND, 120, false);
    }
    if (rite && now >= hsNum(npc, "circles_at") && !npc.getTimers().has(T_CIRCLES)) {
        hsPut(npc, "circles_at", now + 300);
        var h = hsHome(npc), pts = [];
        for (i = 0; i < ps.length; i++) {
            var a = Math.random() * Math.PI * 2, r = 6 + Math.random() * 12;
            var cx = h[0] + Math.cos(a) * r, cz = h[2] + Math.sin(a) * r;
            pts.push(cx.toFixed(1) + ":" + cz.toFixed(1));
            TelegraphAPI.circle(npc, cx, h[1], cz, 2.0, 70, HS.GOLD);
        }
        hsPut(npc, "circles", pts.join(";"));
        npc.say("§2Камень жаждет! Встаньте в круги!");
        npc.getTimers().forceStart(T_CIRCLES, 70, false);
    }

    if (AbilityAPI.isBusy(npc) || npc.getTimers().has(T_HIT) || now < hsNum(npc, "cast_at")) return;
    var t = npc.getAttackTarget();
    if (t == null || !t.isAlive()) return;
    hsPut(npc, "cast_at", now + 100);
    var yaw = hsYawTo(npc, t);
    hsPut(npc, "hit_x", npc.getX());
    hsPut(npc, "hit_z", npc.getZ());
    hsPut(npc, "hit_yaw", yaw);
    var roll = Math.random();
    if (roll < 0.34) {
        hsPut(npc, "hit", "spin");
        hsStart(npc, "weapon_spin", t, AbilityAPI.params("chargeTicks", 20, "activeTicks", 40, "radius", 4.5, "damagePerTick", 2.0));
        npc.getTimers().forceStart(T_HIT, 60, false);
    } else if (roll < 0.67) {
        hsPut(npc, "hit", "dash");
        TelegraphAPI.line(npc, npc.getX(), npc.getY(), npc.getZ(), yaw, 14.0, 3.6, 40, HS.RED);
        hsStart(npc, "dash", t, AbilityAPI.params("chargeTicks", 30, "distance", 14.0, "damage", 6.0, "hitRadius", 1.8));
        npc.getTimers().forceStart(T_HIT, 40, false);
    } else {
        hsPut(npc, "hit", "cone");
        TelegraphAPI.cone(npc, npc.getX(), npc.getY(), npc.getZ(), yaw, 9.0, 30.0, 36, HS.RED);
        npc.getTimers().forceStart(T_HIT, 36, false);
    }
}

function summonAdds(npc) {
    var room = ADD_CAP - hsCount(npc, ADD);
    if (room > 2) room = 2;
    if (room <= 0) return;
    if (room >= 1) hsSpawnAt(npc, [8, 0, 0], ADD);
    if (room >= 2) hsSpawnAt(npc, [-8, 0, 0], ADD);
}

function timer(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    var ps = hsPlayers(npc);
    var i;
    if (e.id == T_HEARTS) {
        if (hsCount(npc, HEART) == 0) return;
        npc.say("§4Камень напился. Вы — следующие.");
        for (i = 0; i < ps.length; i++) hsKill(ps[i]);
        return;
    }
    if (e.id == T_BRAND) {
        var b = aliveById(npc, "brand_uuid");
        if (b == null) return;
        var share = hsPlayersAt(npc, b.getX(), b.getY(), b.getZ(), 3.0);
        for (i = 0; i < share.length; i++) hsStrike(share[i], 0.8 / share.length);
        return;
    }
    if (e.id == T_CIRCLES) {
        var pts = hsGet(npc, "circles", "").split(";"), empty = 0;
        for (i = 0; i < pts.length; i++) {
            var xz = pts[i].split(":");
            if (xz.length < 2) continue;
            if (hsPlayersAt(npc, Number(xz[0]), npc.getY(), Number(xz[1]), 2.0).length == 0) empty++;
        }
        if (empty == 0) return;
        npc.say("§2Камень пьёт из пустых кругов!");
        for (i = 0; i < ps.length; i++) hsStrike(ps[i], 0.6 * empty);
        return;
    }
    if (e.id != T_HIT) return;
    var kind = hsGet(npc, "hit", ""), ox = hsNum(npc, "hit_x"), oz = hsNum(npc, "hit_z"), yaw = hsNum(npc, "hit_yaw");
    for (i = 0; i < ps.length; i++) {
        var p = ps[i];
        if (kind == "spin" && hsFlat(p, npc.getX(), npc.getZ()) <= 4.5) hsStrike(p, 0.25);
        if (kind == "dash" && hsOnLine(p, ox, oz, yaw, 14, 1.8)) hsStrike(p, 0.35);
        if (kind == "cone" && hsFlat(p, ox, oz) <= 9 && Combat.isInFrontCone(ox, oz, yaw, p, 30.0)) hsStrike(p, 0.6);
    }
}

function damaged(e) {
    var npc = e.npc;
    var ph = hsGet(npc, "phase", "1");
    if (ph == "2") { e.setCanceled(true); return; }
    hsCap(e, ph == "3" && hsGet(npc, "rite_done", "0") == "0" ? HS.WEAK : 1);
}

function died(e) {
    var npc = e.npc;
    AbilityAPI.cancel(npc);
    hsZoneClear(npc);
    hsDespawnAll(npc, HEART);
    hsDespawnAll(npc, CRYSTAL);
    hsDespawnAll(npc, ADD);
    hsSetGate(npc, false);
    npc.say("§7Камень трескается. Устье колодца открыто.");
}

function aliveById(npc, key) {
    var id = hsGet(npc, key, "");
    if (id.length < 32) return null;
    try {
        var p = npc.getWorld().getEntity(id);
        if (p != null && p.isAlive()) return p;
    } catch (err) {}
    return null;
}
