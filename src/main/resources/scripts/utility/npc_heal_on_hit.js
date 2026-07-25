// =====================================================
// Хилл NPC за каждый удар ближнего боя (+10 HP)
// Вставить в GUI скрипта NPC. После правок: /script reload
// =====================================================

var HEAL_AMOUNT = 10;

function meleeAttack(event) {
    var npc = event.npc;
    if (npc == null || !npc.isAlive()) return;

    var maxHp = npc.getMaxHealth();
    var cur = npc.getHealth();
    if (maxHp <= 0 || cur >= maxHp) return;

    var next = cur + HEAL_AMOUNT;
    if (next > maxHp) next = maxHp;
    npc.setHealth(next);
}
