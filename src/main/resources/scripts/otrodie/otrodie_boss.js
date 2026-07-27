/**
 * Босс: Отродье.
 * Механика — Java AbilityAPI. JS: кулдауны, выбор скилла, реактивные лужи.
 *
 * Реактив: otrodie_spreading_filth через OtrodieSpreadingFilthAbility.trigger
 * (не AbilityAPI.start — не блокирует текущий каст).
 */
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");
var OtrodieSpreadingFilthAbility = Java.type("noppes.npcs.abilities.impl.OtrodieSpreadingFilthAbility");

var OTRODIE_BOSS_FLAG = "otrodie_boss";
var HP_MARK_KEY = "ot_hp_mark";
var PUDDLE_CD_KEY = "ot_puddle_cd";
var FORCED_ABILITY_KEY = "ot_forced_ability";

var HP_THRESHOLD = 200;
var PUDDLE_CD_TICKS = 400; // 20 с

function init(event) {
    var npc = event.npc;
    var data = npc.getStoreddata();
    data.put(OTRODIE_BOSS_FLAG, "1");
    data.put(FORCED_ABILITY_KEY, "");
    data.put(PUDDLE_CD_KEY, "0");
    data.put(HP_MARK_KEY, String(npc.getHealth()));
}

function damaged(event) {
    var npc = event.npc;
    if (npc == null || !npc.isAlive()) return;

    var data = npc.getStoreddata();
    var world = npc.getWorld();
    var now = world.getTotalTime();

    var currentHp = npc.getHealth();
    try {
        var dmg = parseFloat(String(event.damage));
        if (dmg > 0) {
            // damaged может сработать до применения урона
            var after = currentHp - dmg;
            if (after < currentHp) currentHp = after;
        }
    } catch (e) {}
    if (currentHp < 0) currentHp = 0;

    var mark = getFloat(data, HP_MARK_KEY);
    if (mark <= 0) {
        data.put(HP_MARK_KEY, String(currentHp));
        return;
    }

    if (mark - currentHp < HP_THRESHOLD) return;
    if (now < getInt(data, PUDDLE_CD_KEY)) return;

    OtrodieSpreadingFilthAbility.trigger(npc, AbilityAPI.params());
    data.put(PUDDLE_CD_KEY, String(now + PUDDLE_CD_TICKS));
    data.put(HP_MARK_KEY, String(currentHp));
}

function targetLost(event) {
    AbilityAPI.cancel(event.npc);
}

function died(event) {
    AbilityAPI.cancel(event.npc);
}

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}

function getFloat(data, key) {
    if (!data.has(key)) return 0;
    return parseFloat(String(data.get(key)));
}
