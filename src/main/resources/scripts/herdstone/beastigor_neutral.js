// Бестигор Круга Вызова. Второй файл после hs_core.js.
// Пока игрок не ударит бестигора и чемпион не откажется от дуэли — фракция HS.FACTION_NEUTRAL.
// Display name любой: группа ищется по метке hs_challenge. Её же ставит чемпион.
var CIRCLE_TAG = "hs_challenge";

function init(e) {
    var npc = e.npc;
    if (!npc.isAlive()) return;
    npc.addTag(CIRCLE_TAG);
    circleNeutral(npc);
}

function tick(e) {
    var npc = e.npc;
    if (!npc.isAlive() || !circleEmpty(npc)) return;
    circleNeutral(npc);
}

function damaged(e) {
    var src = e.source;
    if (src == null || !src.typeOf(1)) return;
    aggroCircle(e.npc);
}

function circleNeutral(npc) {
    var id = hsFactionId(HS.FACTION_NEUTRAL);
    if (id < 0) return;
    npc.setFaction(id);
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

function aggroCircle(npc) {
    var id = hsFactionId(HS.FACTION_HERD);
    if (id < 0) return;
    var list = npc.getWorld().getNearbyEntities(npc.getPos(), 40, 2);
    for (var i = 0; i < list.length; i++) {
        var n = list[i];
        if (!n.isAlive() || !n.hasTag(CIRCLE_TAG)) continue;
        n.setFaction(id);
        n.getAi().setRetaliateType(0);
    }
}
