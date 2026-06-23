# Хиллер-саппорт

NPC лечит союзников и даёт баффы. Демонстрирует: `timer`, `interact`.

```javascript
function init(event) {
    event.npc.getStats().setMaxHealth(100);
    event.npc.setName("§aЦелитель");
    event.npc.getTimers().start(1, 60, true);
}

function timer(event) {
    if (event.id == 1) {
        var nearby = event.npc.getWorld().getNearbyEntities(
            event.npc.getPos(), 8, 2);
        nearby = nearby.concat(
            event.npc.getWorld().getNearbyEntities(
                event.npc.getPos(), 8, 1));
        for (var i = 0; i < nearby.length; i++) {
            var ent = nearby[i];
            if (ent.isAlive() && ent.hasTag("friendly")) {
                var currentHp = ent.getHealth();
                var maxHp = ent.getMaxHealth();
                if (currentHp < maxHp) {
                    var healed = Math.min(currentHp + 8, maxHp);
                    ent.setHealth(healed);
                    ent.getWorld().spawnParticle("minecraft:heart",
                        ent.getX(), ent.getY() + 1.5, ent.getZ(),
                        0.2, 0.2, 0.2, 0, 3);
                }
            }
        }
    }
}

function interact(event) {
    var player = event.player;
    var currentHp = player.getHealth();
    var maxHp = player.getMaxHealth();
    if (currentHp < maxHp) {
        player.setHealth(Math.min(currentHp + 20, maxHp));
        player.message("§aЦелитель восстановил твоё здоровье!");
        player.getWorld().spawnParticle("minecraft:heart",
            player.getX(), player.getY() + 1.5, player.getZ(),
            0.3, 0.3, 0.3, 0, 10);
    } else {
        player.message("§eУ тебя полное здоровье.");
    }
    event.setCanceled(true);
}
```
