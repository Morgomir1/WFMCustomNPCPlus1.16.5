# Щитоносец с блокированием

NPC блокирует щитом урон спереди и контратакует. Демонстрирует: `damaged`, `meleeAttack`.

```javascript
function init(event) {
    event.npc.getStats().setMaxHealth(250);
    event.npc.setName("§6Щитоносец");
    event.npc.getStats().setResistance(15);
    event.npc.getAi().setAttackSpeed(25);
    event.npc.getStoreddata().put("blockCooldown", 0);
}

function damaged(event) {
    var cooldown = event.npc.getStoreddata().get("blockCooldown");
    if (cooldown > 0) {
        event.npc.getStoreddata().put("blockCooldown", cooldown - 1);
    }
    
    if (event.source != null && cooldown <= 0) {
        var dx = event.source.getX() - event.npc.getX();
        var dz = event.source.getZ() - event.npc.getZ();
        var angle = Math.atan2(dx, dz) * 180 / Math.PI;
        var npcRot = event.npc.getRotation();
        var diff = Math.abs(angle - npcRot);
        diff = Math.min(diff, 360 - diff);
        
        if (diff < 60) {
            event.setCanceled(true);
            var reduced = event.damage * 0.3;
            if (event.source.getType() == 1) {
                event.source.damage(reduced);
            }
            event.npc.getWorld().spawnParticle("minecraft:crit",
                event.npc.getX(), event.npc.getY() + 1.5, event.npc.getZ(),
                0.1, 0.1, 0.1, 0, 5);
            event.npc.getStoreddata().put("blockCooldown", 20);
        }
    }
}

function meleeAttack(event) {
    event.target.setMotionY(0.4);
    event.damage = event.damage * 1.5;
    event.npc.getWorld().spawnParticle("minecraft:crit",
        event.target.getX(), event.target.getY() + 1, event.target.getZ(),
        0.2, 0.2, 0.2, 0, 8);
}
```
