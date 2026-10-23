// Грул Кабанья Рука (Б1). В контейнере вторым файлом после hs_core.js.
// Тот же центр, прямоугольник и завал, что у Кровохряка: init любого из двоих
// закрывает спуск, поэтому ворота ставит тот, кто возродился первым.
// Рёв Стада — здесь, не в ядре: рассеивание и два рейдера раз в 45 с.
// Рейдеры в SUMMONS: hsInit убирает их при возрождении и при сбросе пустой арены.
HS.BASE_HP = 220;
HS.ARENA = { shape: "rect", hw: 22, hd: 19 };
HS.ENRAGE_SEC = 360;
HS.DISPEL_SEC = 99999;
ORIGIN = [280, 80, 37];   // тот же мировой блок пола, что у Кровохряка
GATE = hsBox(12, 0, -11, 3, 2, 3);
SUMMONS = ["Унгор-рейдер (призыв)"];

var BOAR = "Кровохряк";
var RAIDER = "Унгор-рейдер (призыв)";
var MARK_EVERY = 300;    // 15 с, только пока кабан жив
var CALL_EVERY = 900;    // 45 с
var RAIDER_CAP = 4;

function init(e) {
    var npc = e.npc;
    if (!hsInit(npc)) return;
    if (hsGet(npc, "base_speed", "") == "") hsPut(npc, "base_speed", npc.getAi().getWalkingSpeed());
    npc.getAi().setWalkingSpeed(hsNum(npc, "base_speed"));
    npc.clearPotionEffects();
    hsPut(npc, "mark_at", 0);
    hsPut(npc, "call_at", 0);
    hsPut(npc, "widowed", 0);
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    var ps = hsFight(npc);
    if (ps == null) return;
    var now = hsNow(npc);
    var boar = hsFind(npc, BOAR);
    if (boar.length == 0 && hsGet(npc, "widowed", "0") == "0") {
        hsPut(npc, "widowed", 1);
        npc.addPotionEffect(PotionEffectType_STRENGTH, 99999, 1, true);
        var base = hsNum(npc, "base_speed");
        npc.getAi().setWalkingSpeed(Math.min(10, base + 2));
        npc.say("§4Ты убил моего кабана! Я сам тебя растопчу!");
    }
    if (boar.length > 0 && now >= hsNum(npc, "mark_at")) {
        hsPut(npc, "mark_at", now + MARK_EVERY);
        var p = hsRandom(ps);
        p.addPotionEffect(PotionEffectType_GLOWING, 6, 0, false);
        boar[0].getStoreddata().put("mark_uuid", String(p.getUUID()));
        boar[0].getStoreddata().put("mark_until", String(now + 120));
        for (var i = 0; i < ps.length; i++) ps[i].message("§6Грул метит §f" + p.getName() + "§6 — кабан пойдёт на него!");
        p.message("§eВстань перед целым колом!");
    }
    if (hsNum(npc, "call_at") == 0) hsPut(npc, "call_at", hsNum(npc, "fight_at") + CALL_EVERY);
    if (now >= hsNum(npc, "call_at")) {
        hsPut(npc, "call_at", now + CALL_EVERY);
        hsDispel(npc, ps);
        summonRaiders(npc);
    }
}

function summonRaiders(npc) {
    var room = RAIDER_CAP - hsCount(npc, RAIDER);
    if (room > 2) room = 2;
    if (room <= 0) return;
    var h = hsHome(npc);
    var w = npc.getWorld();
    if (room >= 1) w.spawnClone(h[0] + 6, h[1], h[2], HS.TAB, RAIDER);
    if (room >= 2) w.spawnClone(h[0] - 6, h[1], h[2], HS.TAB, RAIDER);
}

function meleeAttack(e) {
    if (e.target != null && e.target.typeOf(1)) e.target.addPotionEffect(PotionEffectType_SLOWNESS, 3, 1, false);
}

function damaged(e) {
    hsCap(e, 1);
}

function died(e) {
    var npc = e.npc;
    AbilityAPI.cancel(npc);
    hsZoneClear(npc);
    if (hsCount(npc, BOAR) > 0) return;
    hsSetGate(npc, false);
    npc.say("§7Путь вниз открыт.");
}
