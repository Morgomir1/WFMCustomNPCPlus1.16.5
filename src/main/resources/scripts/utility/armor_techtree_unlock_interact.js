// ПКМ по NPC — разблокировать узлы брони в дереве технологий игрока.
//
// Установка:
// - CustomNPC+ → NPC → Advanced → Scripts → Enabled
// - Вставьте этот файл в скрипт NPC
//
// Требования сервера:
// - enable-command-block=true (команды через npc.executeCommand)
// - у игрока должна быть фракция клятвы (pledge), иначе /techtree unlock не сработает

var UNLOCK_NODES = [
    "heavy_armor_1",
    "heavy_armor_2",
    "heavy_armor_resistance",
    "light_armor_1",
    "light_armor_2",
    "light_armor_3"
];

var CANCEL_INTERACT = true;  // true — не открывать диалог/GUI NPC
var SEND_MESSAGE = true;     // сообщение игроку после выдачи

function interact(event) {
    var npc = event.npc;
    var player = event.player;
    if (npc == null || player == null || !player.isAlive()) return;

    var playerName = player.getName();
    if (playerName == null || String(playerName).length == 0) return;

    for (var i = 0; i < UNLOCK_NODES.length; i++) {
        npc.executeCommand("techtree unlock " + UNLOCK_NODES[i] + " " + playerName);
    }

    if (SEND_MESSAGE) {
        player.message("§aУзлы брони разблокированы в дереве технологий.");
    }

    if (CANCEL_INTERACT) {
        event.setCanceled(true);
    }
}
