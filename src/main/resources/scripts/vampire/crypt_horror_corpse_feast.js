// =====================================================
// Ужас из склепа — пожирание трупа вурдалака
// Логика и баланс — в Java: CryptFeastHelper
// После обновления мода: /script reload + вставка в GUI NPC
// =====================================================

var CryptFeast = Java.type("noppes.npcs.script.vampire.CryptFeastHelper");

function init(e) {
    CryptFeast.init(e.npc);
}

function tick(e) {
    CryptFeast.tick(e.npc);
}

function died(e) {
    CryptFeast.onDeath(e.npc);
}

function meleeAttack(e) {
    CryptFeast.onCombat(e.npc);
}

function damaged(e) {
    CryptFeast.onCombat(e.npc);
}

function target(e) {
    if (e.entity != null && e.entity.isAlive()) {
        CryptFeast.onCombat(e.npc);
    }
}

function rangedLaunched(e) {
    CryptFeast.onCombat(e.npc);
}
