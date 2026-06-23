# Телепортация и иллюзии

NPC телепортируется за спину цели и создаёт иллюзии. Демонстрирует: `interact`, `target`, `timer`.

```javascript
function init(event) {
    event.npc.getStats().setMaxHealth(80);
    event.npc.getStats().setHealth(80);
    event.npc.getAi().setAttackSpeed(10);
    event.npc.getTimers().start(1, 80, true);
    event.npc.getStoreddata().put("phase", 0);
}

function tick(event) {
    if (Math.random() < 0.2) {
        event.npc.getWorld().spawnParticle("minecraft:portal",
            event.npc.getX() + (Math.random() - 0.5) * 3,
            event.npc.getY() + Math.random() * 2,
            event.npc.getZ() + (Math.random() - 0.5) * 3,
            0, 0, 0, 0.05, 3);
    }
}

function timer(event) {
    if (event.id == 1) {
        var target = event.npc.getAi().getTarget();
        if (target != null && target.isAlive()) {
            var angle = target.getRotation() * Math.PI / 180;
            var behindX = target.getX() - Math.sin(angle) * 3;
            var behindZ = target.getZ() + Math.cos(angle) * 3;
            event.npc.setPosition(behindX, target.getY(), behindZ);
            event.npc.getWorld().spawnParticle("minecraft:portal",
                behindX, target.getY() + 1, behindZ,
                0.3, 0.3, 0.3, 0.05, 20);
            event.npc.getAi().setTarget(target);
        }
    }
}

function damaged(event) {
    if (Math.random() < 0.3) {
        var x = event.npc.getX() + (Math.random() - 0.5) * 10;
        var z = event.npc.getZ() + (Math.random() - 0.5) * 10;
        event.npc.setPosition(x, event.npc.getY(), z);
        event.setCanceled(true);
        event.npc.say("§bХа-ха, не попал!");
    }
}

function interact(event) {
    event.player.message("§5Я — тень среди теней...");
    var x = event.npc.getX() + (Math.random() - 0.5) * 15;
    var z = event.npc.getZ() + (Math.random() - 0.5) * 15;
    event.player.setPosition(x, event.player.getY(), z);
    event.setCanceled(true);
}
```
