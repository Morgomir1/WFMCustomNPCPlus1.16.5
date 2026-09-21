// =====================================================
// Объявление всем игрокам: NPC умер / возродился.
// Формат как у WFM: [Объявление] сообщение
//   скобки — primary (белый), «Объявление» — dark_red, текст — green
//
// Установка:
// - CustomNPC+ → NPC → Advanced → Scripts → Enabled
// - Вставьте этот файл в скрипт NPC
// - После правок: /script reload
//
// Респавн не путается с загрузкой чанка: объявление
// «возродился» уходит только если этот NPC уже умирал.
// =====================================================

var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();

// =========================
// НАСТРОЙКИ
// =========================
var MSG_DIED = "{name} умер";
var MSG_RESPAWNED = "{name} возродился";

// Это и есть формат объявления. WfmBroadcastChat больше не перезаписывает его.
// primary = §f, dark_red = §4, green = §a
var PREFIX = "§f[§4Замок Дракенфельса§f] §a";

// Если задано — ключ смерти уникален даже при одинаковых именах.
// Пустая строка: имя + домашняя точка NPC.
var NPC_ID = "";

var DEAD_KEY_PREFIX = "announce_dead_";

function died(e) {
    var npc = e.npc;
    if (npc == null) return;

    var data = npc.getWorld().getStoreddata();
    var key = deathKey(npc);
    if (String(data.get(key)) == "1") return;

    data.put(key, "1");
    announce(npc, formatMessage(MSG_DIED, npc));
}

function init(e) {
    tryAnnounceRespawn(e.npc);
}

function tick(e) {
    tryAnnounceRespawn(e.npc);
}

function tryAnnounceRespawn(npc) {
    if (npc == null || !npc.isAlive()) return;

    var data = npc.getWorld().getStoreddata();
    var key = deathKey(npc);
    if (String(data.get(key)) != "1") return;

    data.remove(key);
    announce(npc, formatMessage(MSG_RESPAWNED, npc));
}

function announce(npc, text) {
    var message = PREFIX + text;
    try {
        var StringTextComponent = Java.type("net.minecraft.util.text.StringTextComponent");
        var hooks = Java.type("net.minecraftforge.fml.server.ServerLifecycleHooks");
        var Util = Java.type("net.minecraft.util.Util");
        var server = hooks.getCurrentServer();
        if (server != null) {
            var players = server.getPlayerList().getPlayers();
            var msg = new StringTextComponent(message);
            var i;
            for (i = 0; i < players.size(); i++) {
                players.get(i).sendMessage(msg, Util.NIL_UUID);
            }
            return;
        }
    } catch (err) {}

    npc.getWorld().broadcast(message);
}

function formatMessage(template, npc) {
    return String(template).split("{name}").join(npcName(npc));
}

function npcName(npc) {
    try {
        var name = npc.getName();
        if (name != null && String(name).length > 0) return String(name);
    } catch (err) {}
    return "NPC";
}

function deathKey(npc) {
    if (NPC_ID != null && String(NPC_ID).length > 0) {
        return DEAD_KEY_PREFIX + String(NPC_ID);
    }
    return DEAD_KEY_PREFIX + npcName(npc) + "_" + npc.getHomeX() + "_" + npc.getHomeY() + "_" + npc.getHomeZ();
}
