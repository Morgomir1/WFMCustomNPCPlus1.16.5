# Огненный аурный барьер

NPC получает периодический урон от огня, а при ударе поджигает атакующего. Демонстрирует: `damaged`, `particle`, `timer`.

```javascript
function init(event) {
    event.npc.getStats().setMaxHealth(200);
    event.npc.getStats().setHealth(200);
    event.npc.setName("§cОгненный Страж");
    event.npc.getTimers().start(1, 100, true);
}

function tick(event) {
    var world = event.npc.getWorld();
    var npc = event.npc;
    if (Math.random() < 0.3) {
        world.spawnParticle("minecraft:flame",
            npc.getX() + (Math.random() - 0.5) * 2,
            npc.getY() + Math.random() * 2,
            npc.getZ() + (Math.random() - 0.5) * 2,
            0, 0.1, 0, 0, 1);
    }
}

function timer(event) {
    if (event.id == 1) {
        var nearby = event.npc.getWorld().getNearbyEntities(
            event.npc.getPos(), 5, 5);
        for (var i = 0; i < nearby.length; i++) {
            var ent = nearby[i];
            if (ent.getType() == 1 || ent.getType() == 3) {
                if (ent.getUUID() != event.npc.getUUID()) {
                    ent.setBurning(40);
                }
            }
        }
    }
}

function damaged(event) {
    if (event.source != null) {
        event.source.setBurning(80);
        event.source.setMotionY(0.5);
        event.source.damage(3);
        event.npc.getWorld().spawnParticle("minecraft:lava",
            event.npc.getX(), event.npc.getY() + 1, event.npc.getZ(),
            0.5, 0.5, 0.5, 0, 10);
    }
}

function died(event) {
    event.npc.getWorld().explode(
        event.npc.getX(), event.npc.getY(), event.npc.getZ(),
        4, true, true);
    event.npc.say("Я ещё вернусь...");
}
```
