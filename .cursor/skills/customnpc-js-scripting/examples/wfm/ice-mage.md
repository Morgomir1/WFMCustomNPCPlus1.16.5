# Ледяной маг с конусом холода

NPC стреляет ледяными снарядами, замораживает и накладывает эффекты. Демонстрирует: `rangedLaunched`, `meleeAttack`, `damaged`.

```javascript
function init(event) {
    event.npc.getStats().setMaxHealth(150);
    event.npc.setName("§bЛедяной Маг");
    event.npc.getTimers().start(1, 200, true);
}

function rangedLaunched(event) {
    event.setCanceled(true);
    var world = event.npc.getWorld();
    var iceItem = world.createItem("minecraft:snowball", 1);
    var target = event.target;
    for (var i = 0; i < 3; i++) {
        event.npc.shootItem(target, iceItem, 5);
    }
    target.setMotionY(0.3);
    target.setBurning(0);
    world.spawnParticle("minecraft:snowballpoof",
        target.getX(), target.getY() + 1, target.getZ(),
        0.2, 0.2, 0.2, 0.1, 10);
}

function meleeAttack(event) {
    event.target.setMotionX(event.target.getMotionX() * 0.3);
    event.target.setMotionZ(event.target.getMotionZ() * 0.3);
    event.target.damage(5);
    event.npc.getWorld().spawnParticle("minecraft:item_snowball",
        event.target.getX(), event.target.getY() + 1, event.target.getZ(),
        0.1, 0.1, 0.1, 0, 5);
}

function damaged(event) {
    if (event.source != null) {
        event.source.setMotionX(0);
        event.source.setMotionZ(0);
    }
}

function timer(event) {
    if (event.id == 1) {
        var nearby = event.npc.getWorld().getNearbyEntities(
            event.npc.getPos(), 6, 5);
        for (var i = 0; i < nearby.length; i++) {
            var ent = nearby[i];
            if (ent.getUUID() != event.npc.getUUID()) {
                ent.setMotionX(ent.getMotionX() * 0.5);
                ent.setMotionZ(ent.getMotionZ() * 0.5);
            }
        }
        event.npc.getWorld().spawnParticle("minecraft:snowballpoof",
            event.npc.getX(), event.npc.getY() + 1, event.npc.getZ(),
            2, 1, 2, 0, 30);
    }
}

function interact(event) {
    event.player.message("§bТы чувствуешь дыхание зимы...");
    event.setCanceled(true);
}
```
