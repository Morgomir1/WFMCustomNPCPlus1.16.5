/**
 * CustomNPCs 1.16.5 — скрипт NPC (вкладка Scripts).
 *
 * При смерти этого NPC мгновенно телепортирует всех игроков
 * в сферическом радиусе RADIUS на координаты ниже.
 *
 * Как поставить:
 * 1. Откройте NPC → Advanced → Scripts
 * 2. Включите скрипты, язык JavaScript
 * 3. Вставьте этот файл целиком (или загрузите его)
 */

// Куда телепортировать игроков (мир тот же, что у NPC)
var DEST_X = 0.5;
var DEST_Y = 64.0;
var DEST_Z = 0.5;

var RADIUS = 10;
var PLAYER_TYPE = 1;

function died(event) {
    var npc = event.npc;
    var world = npc.getWorld();
    var nx = npc.getX();
    var ny = npc.getY();
    var nz = npc.getZ();
    var radiusSq = RADIUS * RADIUS;

    var nearby = world.getNearbyEntities(
        npc.getBlockX(),
        npc.getBlockY(),
        npc.getBlockZ(),
        RADIUS,
        PLAYER_TYPE
    );

    var i;
    var player;
    var dx;
    var dy;
    var dz;

    for (i = 0; i < nearby.length; i++) {
        player = nearby[i];
        dx = player.getX() - nx;
        dy = player.getY() - ny;
        dz = player.getZ() - nz;
        if (dx * dx + dy * dy + dz * dz <= radiusSq) {
            player.setPosition(DEST_X, DEST_Y, DEST_Z);
        }
    }
}
