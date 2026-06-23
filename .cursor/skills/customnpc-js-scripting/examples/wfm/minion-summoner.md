# Призыв миньонов

NPC призывает скелетов/зомби в бою. Демонстрирует: `timer`, `died`, `kill`.

```javascript
function init(event) {
    event.npc.getStats().setMaxHealth(300);
    event.npc.getStats().setHealth(300);
    event.npc.getStats().setResistance(5);
    event.npc.getTimers().start(1, 120, true);
}

function timer(event) {
    if (event.id == 1) {
        var target = event.npc.getAi().getTarget();
        if (target != null && target.isAlive()) {
            var minions = 0;
            var nearby = event.npc.getWorld().getNearbyEntities(
                event.npc.getPos(), 15, 3);
            for (var i = 0; i < nearby.length; i++) {
                if (nearby[i].hasTag("summoned")) minions++;
            }
            if (minions < 4) {
                var skele = event.npc.getWorld().createEntity("minecraft:skeleton");
                var angle = Math.random() * 2 * Math.PI;
                var spawnX = event.npc.getX() + Math.sin(angle) * 3;
                var spawnZ = event.npc.getZ() + Math.cos(angle) * 3;
                skele.setPosition(spawnX, event.npc.getY(), spawnZ);
                skele.addTag("summoned");
                event.npc.getWorld().spawnEntity(skele);
                event.npc.getWorld().spawnParticle("minecraft:enchantment_table",
                    spawnX, event.npc.getY() + 1, spawnZ,
                    0.2, 0.5, 0.2, 0.1, 15);
                event.npc.say("§cВосстаньте, мои слуги!");
            }
        }
    }
}

function died(event) {
    var world = event.npc.getWorld();
    var all = world.getAllEntities(3);
    for (var i = 0; i < all.length; i++) {
        if (all[i].hasTag("summoned")) {
            world.explode(all[i].getX(), all[i].getY(), all[i].getZ(), 2, false, false);
            all[i].kill();
        }
    }
    world.explode(event.npc.getX(), event.npc.getY(), event.npc.getZ(), 5, true, true);
}

function kill(event) {
    var currentHealth = event.npc.getStats().getHealth();
    var maxHealth = event.npc.getStats().getMaxHealth();
    var newHealth = Math.min(currentHealth + 20, maxHealth);
    event.npc.getStats().setHealth(newHealth);
    event.npc.say("§cЕщё один пал!");
}
```
