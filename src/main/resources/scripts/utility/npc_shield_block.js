// =====================================================
// NPC — блок щитом (тонкий JS)
// Механика: Java AbilityAPI id "shield_block"
// JS: шанс / кулдаун / старт абилки при уроне
//
// Нужен щит в левой (или правой) руке NPC.
// =====================================================

var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");

// -------------------------
// НАСТРОЙКИ
// -------------------------
var ABILITY_ID = "shield_block";
var BLOCK_CHANCE = 50;           // %
var BLOCK_TICKS = 20;            // длительность блока
var BLOCK_COOLDOWN_TICKS = 40;
var BLOCK_ANGLE = 90;            // полный угол спереди

var CD_KEY = "shield_block_cd";

function damaged(e) {
    var npc = e.npc;
    if (npc == null || !npc.isAlive()) return;

    // Уже в блоке — урон гасит Java ShieldBlockDamageHandler;
    // на всякий случай дублируем cancel в CNPC-событии
    if (String(AbilityAPI.getActiveId(npc)) == ABILITY_ID) {
        try { e.setCanceled(true); } catch (err) {}
        try { e.damage = 0; } catch (err2) {}
        return;
    }

    var data = npc.getStoreddata();
    var now = npc.getWorld().getTotalTime();
    if (now < getInt(data, CD_KEY)) return;
    if (AbilityAPI.isBusy(npc)) return;
    if (!isFrontalHit(npc, e.source)) return;
    if (Math.random() * 100 >= BLOCK_CHANCE) return;

    var started = AbilityAPI.start(
        npc,
        ABILITY_ID,
        null,
        AbilityAPI.params(
            "activeTicks", BLOCK_TICKS,
            "blockAngle", BLOCK_ANGLE,
            "telegraph", 0
        )
    );
    if (!started) return;

    try { e.setCanceled(true); } catch (err3) {}
    try { e.damage = 0; } catch (err4) {}
    data.put(CD_KEY, String(now + BLOCK_TICKS + BLOCK_COOLDOWN_TICKS));
}

function died(e) {
    AbilityAPI.cancel(e.npc);
}

function isFrontalHit(npc, source) {
    if (source == null) return true;
    var dx = source.getX() - npc.getX();
    var dz = source.getZ() - npc.getZ();
    if (dx * dx + dz * dz < 0.0001) return true;
    var angle = Math.atan2(dx, dz) * 180.0 / Math.PI;
    var diff = Math.abs(angle - npc.getRotation()) % 360;
    if (diff > 180) diff = 360 - diff;
    return diff <= (BLOCK_ANGLE * 0.5);
}

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)), 10) || 0;
}
