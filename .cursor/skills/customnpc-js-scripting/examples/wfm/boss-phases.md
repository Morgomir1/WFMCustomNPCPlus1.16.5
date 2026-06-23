# Босс с фазами и хранилищем данных

Босс меняет поведение в зависимости от оставшегося здоровья. Демонстрирует: `storeddata`, `timer`, `damaged`.

```javascript
function init(event) {
    event.npc.getStats().setMaxHealth(500);
    event.npc.getStats().setHealth(500);
    event.npc.setName("§4§lПовелитель Бездны");
    event.npc.getStats().setResistance(10);
    
    event.npc.getStoreddata().put("phase", 1);
    event.npc.getTimers().start(1, 100, true);
    event.npc.getTimers().start(2, 40, true);
}

function tick(event) {
    var phase = event.npc.getStoreddata().get("phase");
    var particle = "minecraft:portal";
    if (phase == 3) particle = "minecraft:soul_fire_flame";
    else if (phase == 2) particle = "minecraft:dragon_breath";
    
    if (Math.random() < 0.2) {
        event.npc.getWorld().spawnParticle(particle,
            event.npc.getX() + (Math.random() - 0.5) * 3,
            event.npc.getY() + Math.random() * 3,
            event.npc.getZ() + (Math.random() - 0.5) * 3,
            0, 0, 0, 0, 2);
    }
}

function timer(event) {
    var target = event.npc.getAi().getTarget();
    if (target == null || !target.isAlive()) return;
    var phase = event.npc.getStoreddata().get("phase");
    
    if (event.id == 1) {
        var health = event.npc.getStats().getHealth();
        var maxHealth = event.npc.getStats().getMaxHealth();
        var ratio = health / maxHealth;
        
        var newPhase = 1;
        if (ratio < 0.3) newPhase = 3;
        else if (ratio < 0.6) newPhase = 2;
        
        var oldPhase = event.npc.getStoreddata().get("phase");
        if (newPhase != oldPhase) {
            event.npc.getStoreddata().put("phase", newPhase);
            event.npc.say("§4Ты лишь приближаешь свою гибель! Фаза " + newPhase);
            event.npc.getWorld().spawnParticle("minecraft:explosion_emitter",
                event.npc.getX(), event.npc.getY() + 2, event.npc.getZ(),
                0, 0, 0, 0, 1);
            event.npc.getStats().setResistance(10 + newPhase * 5);
        }
    }
    
    if (event.id == 2) {
        if (phase >= 1) {
            var fireball = event.npc.getWorld().createItem("minecraft:fire_charge", 1);
            event.npc.shootItem(target, fireball, 10);
        }
        if (phase >= 2) {
            if (Math.random() < 0.3) {
                var mob = event.npc.getWorld().createEntity("minecraft:zombie");
                mob.setPosition(
                    target.getX() + (Math.random() - 0.5) * 5,
                    target.getY(),
                    target.getZ() + (Math.random() - 0.5) * 5);
                event.npc.getWorld().spawnEntity(mob);
                event.npc.say("§cНа помощь!");
            }
        }
        if (phase >= 3) {
            event.npc.getWorld().thunderStrike(target.getX(), target.getY(), target.getZ());
        }
    }
}

function damaged(event) {
    var phase = event.npc.getStoreddata().get("phase");
    if (phase >= 3 && event.source != null) {
        var reflectDamage = event.damage * 0.25;
        event.source.damage(reflectDamage);
    }
    if (phase >= 2 && Math.random() < 0.2) {
        event.setCanceled(true);
    }
}

function kill(event) {
    event.npc.say("§4Ничтожество!");
    event.npc.getStats().setHealth(
        event.npc.getStats().getHealth() + 30);
}

function died(event) {
    var world = event.npc.getWorld();
    world.broadcast("§4§lПовелитель Бездны повержен!");
    world.explode(event.npc.getX(), event.npc.getY(), event.npc.getZ(), 6, false, false);
    var drop = world.createItem("minecraft:nether_star", 1);
    drop.setDisplayName("§5Сердце Бездны");
    event.npc.getWorld().getPlayer("@a").giveItem(drop);
}
```
