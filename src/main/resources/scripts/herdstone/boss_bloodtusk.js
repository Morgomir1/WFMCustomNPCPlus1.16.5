// Кровохряк (Б1). В контейнере вторым файлом после hs_core.js.
// ORIGIN — мировой блок пола в центре A4. Колья и завал — оффсеты от него.
// Арена 44×38. Колья на √(16²+13²) ≈ 20.6 от центра, поэтому круг ARENA_R = 20
// давал поводок у кола. Прямоугольник покрывает зал и не трогает игрока у кола.
// Завал A4_down общий с Грулом. init (respawn и reset) ставит его обратно:
// кто из двоих возродился первым, тот и закрывает спуск.
HS.BASE_HP = 300;
HS.ARENA = { shape: "rect", hw: 22, hd: 19 };
HS.ENRAGE_SEC = 360;
HS.DISPEL_SEC = 99999;   // рёв — способность Грула, не ядра
ORIGIN = [280, 80, 37];   // мировой блок пола. Сдвинул данж — впиши центр этой арены
GATE = hsBox(12, 0, -11, 3, 2, 3);   // завал шахты A4_down (292, 26) от центра 280,37
SUMMONS = ["Унгор-рейдер (призыв)"];

var PARTNER = "Грул Кабанья Рука";
var STAKES = [ [-16, 1, -13], [16, 1, -13], [-16, 1, 13], [16, 1, 13] ];
var STAKE_BLOCK = "minecraft:dark_oak_log";
var RAM_LEN = 14;
var RAM_EVERY = 180;     // 9 с, после смерти Грула — 6 с
var STUN_TICKS = 120;    // 6 с: только тогда урон не режется
var T_RAM = 1, T_STUN = 2;

function init(e) {
    var npc = e.npc;
    if (!hsInit(npc)) return;
    var w = npc.getWorld();
    for (var i = 0; i < STAKES.length; i++) {
        var p = hsXYZ(npc, STAKES[i]);
        w.setBlock(NpcAPI.getIPos(p[0], p[1], p[2]), STAKE_BLOCK);
        w.setBlock(NpcAPI.getIPos(p[0], p[1] + 1, p[2]), STAKE_BLOCK);
    }
    if (hsGet(npc, "base_speed", "") == "") hsPut(npc, "base_speed", npc.getAi().getWalkingSpeed());
    npc.getAi().setWalkingSpeed(hsNum(npc, "base_speed"));
    npc.getAi().setRetaliateType(0);
    npc.clearPotionEffects();
    hsPut(npc, "stun", 0);
    hsPut(npc, "ram_at", 0);
    hsPut(npc, "mark_until", 0);
    hsPut(npc, "widowed", 0);
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) { AbilityAPI.cancel(npc); return; }
    var ps = hsFight(npc);
    if (ps == null) return;
    var now = hsNow(npc);
    if (hsGet(npc, "widowed", "0") == "0" && hsCount(npc, PARTNER) == 0) {
        hsPut(npc, "widowed", 1);
        npc.addPotionEffect(PotionEffectType_STRENGTH, 99999, 0, true);
        npc.say("§4Кровохряк визжит над телом хозяина!");
    }
    if (hsGet(npc, "stun", "0") == "1" || AbilityAPI.isBusy(npc) || npc.getTimers().has(T_RAM)) return;
    if (now < hsNum(npc, "ram_at")) return;
    var t = markedTarget(npc, now) || npc.getAttackTarget();
    if (t == null || !t.isAlive()) return;
    var yaw = hsYawTo(npc, t);
    hsPut(npc, "ram_x", npc.getX());
    hsPut(npc, "ram_z", npc.getZ());
    hsPut(npc, "ram_yaw", yaw);
    TelegraphAPI.line(npc, npc.getX(), npc.getY(), npc.getZ(), yaw, RAM_LEN, 3.0, 40, HS.RED);
    hsStart(npc, "dash", t, AbilityAPI.params("chargeTicks", 30, "activeTicks", 8,
        "distance", RAM_LEN * 1.0, "damage", 4.0, "hitRadius", 1.8));
    npc.getTimers().forceStart(T_RAM, 40, false);
    hsPut(npc, "ram_at", now + (hsGet(npc, "widowed", "0") == "1" ? 120 : RAM_EVERY));
}

// Грул пишет UUID отмеченного: следующий таран идёт в него. После смерти Грула метки нет.
function markedTarget(npc, now) {
    if (now > hsNum(npc, "mark_until")) return null;
    var id = hsGet(npc, "mark_uuid", "");
    if (id.length < 32) return null;
    try {
        var p = npc.getWorld().getEntity(id);
        return (p != null && p.isAlive()) ? p : null;
    } catch (err) {
        return null;
    }
}

function timer(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    if (e.id == T_STUN) {
        hsPut(npc, "stun", 0);
        npc.getAi().setWalkingSpeed(hsNum(npc, "base_speed"));
        npc.getAi().setRetaliateType(0);
        return;
    }
    if (e.id != T_RAM) return;
    var sx = hsNum(npc, "ram_x"), sz = hsNum(npc, "ram_z"), yaw = hsNum(npc, "ram_yaw");
    var ps = hsPlayersAt(npc, sx, npc.getY(), sz, RAM_LEN + 1);
    for (var i = 0; i < ps.length; i++) {
        if (hsOnLine(ps[i], sx, sz, yaw, RAM_LEN, 1.8)) hsStrike(ps[i], 0.30);
    }
    var w = npc.getWorld();
    for (var j = 0; j < STAKES.length; j++) {
        var s = hsXYZ(npc, STAKES[j]);
        if (!isStake(w.getBlock(s[0], s[1], s[2]))) continue;
        if (hsFlat(npc, s[0] + 0.5, s[2] + 0.5) > 2.5) continue;
        w.removeBlock(NpcAPI.getIPos(s[0], s[1], s[2]));
        w.removeBlock(NpcAPI.getIPos(s[0], s[1] + 1, s[2]));
        hsPut(npc, "stun", 1);
        npc.getAi().setWalkingSpeed(0);
        npc.getAi().setRetaliateType(3);
        npc.getTimers().forceStart(T_STUN, STUN_TICKS, false);
        w.playSoundAt(npc.getPos(), "minecraft:block.wood.break", 3.0, 0.5);
        npc.say("§eКровохряк насадился на кол и оглушён!");
        return;
    }
}

function damaged(e) {
    hsCap(e, hsGet(e.npc, "stun", "0") == "1" ? 1 : HS.WEAK);
}

function died(e) {
    var npc = e.npc;
    AbilityAPI.cancel(npc);
    hsZoneClear(npc);
    if (hsCount(npc, PARTNER) > 0) return;
    hsSetGate(npc, false);
    npc.say("§7Путь вниз открыт.");
}

function isStake(block) {
    if (block == null) return false;
    var name = String(block.getName());
    return name == STAKE_BLOCK || name == "dark_oak_log";
}
