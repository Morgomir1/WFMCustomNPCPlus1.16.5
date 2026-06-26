# Tick state machine — эталон способности

Краткий референс. Полный разбор: [architecture.md](../../architecture.md).

## Bootstrap

```javascript
var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();
var EntitiesType = Java.type("noppes.npcs.api.constants.EntitiesType");
```

## Минимальный каркас

```javascript
var COOLDOWN_TICKS = 400;
var CHARGE_TICKS = 24;
var ACTIVE_KEY = "ability_active";
var CHARGING_KEY = "ability_charging";
var CD_KEY = "ability_cd";
var STEP_KEY = "ability_step";
var X_KEY = "ability_x";
var Y_KEY = "ability_y";
var Z_KEY = "ability_z";

function tick(e) {
    var npc = e.npc;
    var world = npc.getWorld();
    var data = npc.getStoreddata();
    var now = world.getTotalTime();

    if (!npc.isAlive()) { clearAbility(data); return; }
    if (String(data.get(CHARGING_KEY)) == "1") { tickCharge(npc, world, data); return; }
    if (String(data.get(ACTIVE_KEY)) == "1") { tickActive(npc, world, data); return; }
    if (now < getInt(data, CD_KEY)) return;
    // ... проверки цели ...
    startCharge(npc, world, data, target, now);
}

function clearAbility(data) {
    data.put(ACTIVE_KEY, "0");
    data.put(CHARGING_KEY, "0");
    data.put(STEP_KEY, "0");
}

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}
```

## Урон в точке эффекта

```javascript
var pos = NpcAPI.getIPos(x, y, z);
var hits = world.getNearbyEntities(pos, 3.0, EntitiesType.ANY);
```
