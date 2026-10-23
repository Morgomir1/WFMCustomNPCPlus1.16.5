// Чемпион Круга (D2). Цепочка: hs_core.js → hs_mob.js → mob_executioner.js → этот файл.
// init чемпиона перекрывает init моба и сам зовёт hsMobInit.
// dialogOption в этой сборке — событие NPC (EventHooks.onNPCDialogOption), не игрока.
// В диалоге «Право Вызова» подписи опций должны содержать «принять» и «отказаться».
var CIRCLE_TAG = "hs_challenge";
var RING_R = 8;

function init(e) {
    var npc = e.npc;
    hsMobInit(npc);
    if (!npc.isAlive()) return;
    npc.addTag(CIRCLE_TAG);
    circleNeutral(npc);
    circleRing(npc, false);
}

function hsMobExtra(npc) {
    if (!circleEmpty(npc)) return;
    circleNeutral(npc);
    circleRing(npc, false);
}

function dialogOption(e) {
    var text = optionText(e.option);
    if (text.indexOf("прин") >= 0) {
        acceptDuel(e.npc);
        return;
    }
    if (text.indexOf("отказ") >= 0) aggroCircle(e.npc);
}

function died(e) {
    AbilityAPI.cancel(e.npc);
    circleRing(e.npc, false);
}

function acceptDuel(npc) {
    if (!setFactionNamed(npc, HS.FACTION_HERD)) return;
    npc.getAi().setRetaliateType(0);
    circleRing(npc, true);
    npc.say("§4Кольцо закрыто. Бейся один.");
}

function circleNeutral(npc) {
    if (!setFactionNamed(npc, HS.FACTION_NEUTRAL)) return;
    npc.getAi().setRetaliateType(3);
}

function circleEmpty(npc) {
    if (npc.getAttackTarget() != null) return false;
    var list = npc.getWorld().getNearbyEntities(npc.getPos(), 32, 1);
    for (var i = 0; i < list.length; i++) {
        var p = list[i];
        if (!p.isAlive() || p.getGamemode() == 1 || p.getGamemode() == 3) continue;
        return false;
    }
    return true;
}

function circleMembers(npc) {
    var list = npc.getWorld().getNearbyEntities(npc.getPos(), 40, 2);
    var out = [];
    for (var i = 0; i < list.length; i++) {
        if (list[i].isAlive() && list[i].hasTag(CIRCLE_TAG)) out.push(list[i]);
    }
    return out;
}

function aggroCircle(npc) {
    var id = hsFactionId(HS.FACTION_HERD);
    if (id < 0) return;
    var list = circleMembers(npc);
    for (var i = 0; i < list.length; i++) {
        list[i].setFaction(id);
        list[i].getAi().setRetaliateType(0);
    }
    npc.say("§4Стадо не прощает отказа!");
}

function setFactionNamed(npc, name) {
    var id = hsFactionId(name);
    if (id < 0) return false;
    npc.setFaction(id);
    return true;
}

function optionText(option) {
    if (option == null) return "";
    try {
        if (typeof option.getName == "function") return String(option.getName()).toLowerCase();
    } catch (err) {}
    return String(option).toLowerCase();
}

function circleRing(npc, closed) {
    var hx = npc.getHomeX();
    var hy = npc.getHomeY();
    var hz = npc.getHomeZ();
    var w = npc.getWorld();
    var blocks = duelRing();
    for (var i = 0; i < blocks.length; i++) {
        var b = blocks[i];
        var pos = NpcAPI.getIPos(hx + b[0], hy + b[1], hz + b[2]);
        if (closed) w.setBlock(pos, "minecraft:barrier");
        else w.removeBlock(pos);
    }
}

function duelRing() {
    var out = [];
    var r2 = RING_R * RING_R;
    var inner = (RING_R - 1) * (RING_R - 1);
    var x, z, d, y;
    for (z = -RING_R; z <= RING_R; z++) {
        for (x = -RING_R; x <= RING_R; x++) {
            d = x * x + z * z;
            if (d < inner || d > r2) continue;
            for (y = 0; y < 3; y++) out.push([x, y, z]);
        }
    }
    return out;
}
