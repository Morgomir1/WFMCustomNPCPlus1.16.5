# Архитектура способностей NPC (tick state machine)

Эталон для новых скриптов WFM CustomNPC+. Паттерн community-скриптов: зарядка → активная фаза → финальный удар, вся логика в `tick`, bootstrap через `Java.type`.

---

## Bootstrap (обязательно в начале файла)

```javascript
var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();
var EntitiesType = Java.type("noppes.npcs.api.constants.EntitiesType");
// Опционально — зоны атаки:
// var TelegraphAPI = Java.type("noppes.npcs.telegraph.TelegraphAPI");
// var ZoneAPI = Java.type("noppes.npcs.zone.ZoneAPI");
```

Если `EntitiesType` не резолвится в вашей сборке — подставьте числовые id:

```javascript
var ENTITY_ANY = -1;
var ENTITY_PLAYER = 1;
var ENTITY_LIVING = 5;
```

`NpcAPI` доступен **везде** в файле (утилиты, VFX, урон) без `event.API`.

---

## Структура файла

```
1. Bootstrap (NpcAPI, EntitiesType)
2. НАСТРОЙКИ — урон, радиусы, кулдауны, пороги HP
3. Ключи storeddata — ACTIVE_KEY, CHARGING_KEY, CD_KEY, ...
4. function tick(e)           — диспетчер
5. Фазы — startCharge, doChargingTick, doActiveTick, clearState
6. VFX / урон — telegraph в startCharge, ZoneAPI для aura/burst, doFinisher
7. Утилиты — getInt, getFloat, distance, findPlayerByUUID
```

**Telegraph** — warning на время charge (`TelegraphAPI.circle/cone/line`).  
**Zone** — hazard-entity с тик-уроном (`ZoneAPI.hazardCircle`); см. [abilities-reference.md](abilities-reference.md) § Telegraph + Ability Zone.

---

## Диспетчер `tick`

```javascript
function tick(e) {
    var npc = e.npc;
    var world = npc.getWorld();
    var data = npc.getStoreddata();
    var now = world.getTotalTime();

    if (!npc.isAlive()) {
        clearState(data);
        return;
    }

    if (String(data.get(CHARGING_KEY)) == "1") {
        doChargingTick(npc, world, data);
        return;
    }

    if (String(data.get(ACTIVE_KEY)) == "1") {
        doActiveTick(npc, world, data);
        return;
    }

    if (now < getInt(data, CD_KEY)) {
        return;
    }

    if (!canStartCast(npc, world, data)) {
        return;
    }

    var target = getCastTarget(npc, world);
    if (target == null) {
        return;
    }

    startCharge(npc, world, data, target, now);
}
```

`tick` вызывается раз в **10 игровых тиков** (~0.5 с). Один вызов = один шаг зарядки или один шаг пролёта.

---

## storeddata: только строки

| Паттерн | Пример |
|---------|--------|
| Флаг фазы | `data.put(ACTIVE_KEY, "1")` / `"0"` |
| Кулдаун (абсолютное время) | `data.put(CD_KEY, String(now + COOLDOWN_TICKS))` |
| Координаты эффекта | `data.put(X_KEY, String(x))` |
| Направление | `data.put(DX_KEY, String(dx))` |
| Счётчик шагов | `data.put(STEP_KEY, String(step + 1))` |
| Hit-list (без повторного урона) | `data.put(HIT_KEY, hitRaw + uuid + ";")` |
| UUID цели | `data.put(TARGET_KEY, String(target.getUUID()))` |

Хелперы:

```javascript
function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}

function getFloat(data, key) {
    if (!data.has(key)) return 0.0;
    return parseFloat(String(data.get(key)));
}
```

---

## Кулдаун через `getTotalTime`

```javascript
var now = world.getTotalTime();
data.put(CD_KEY, String(now + COOLDOWN_TICKS));

// в tick:
if (now < getInt(data, CD_KEY)) return;
```

Не декрементируй КД вручную каждый тик — храни **метку времени окончания**.

---

## Условия каста (типичные)

```javascript
function canStartCast(npc, world, data) {
    if (npc.getHealth() > npc.getMaxHealth() * HEALTH_THRESHOLD) return false;

    var target = getCastTarget(npc, world);
    if (target == null || !target.isAlive()) return false;
    if (!npc.canSeeEntity(target)) return false;
    if (distance(npc, target) > SEARCH_RADIUS) return false;

    return true;
}
```

Проверка «цель — игрок»:

```javascript
function getCastTarget(npc, world) {
    if (!npc.isAttacking()) return null;
    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) return null;

    var players = world.getAllPlayers();
    var targetUUID = String(target.getUUID());
    for (var i = 0; i < players.length; i++) {
        if (String(players[i].getUUID()) == targetUUID) return players[i];
    }
    return null;
}
```

---

## Зарядка и старт активной фазы

```javascript
function startCharge(npc, world, data, target, now) {
    data.put(CHARGING_KEY, "1");
    data.put(ACTIVE_KEY, "0");
    data.put(STEP_KEY, "0");
    data.put(HIT_KEY, "");
    data.put(TARGET_KEY, String(target.getUUID()));
    data.put(CD_KEY, String(now + COOLDOWN_TICKS));
    data.put(CHARGE_LEFT_KEY, String(CHARGE_TICKS));

    npc.say("...");
    world.playSoundAt(npc.getPos(), "minecraft:block.beacon.power_select", 0.9, 0.9);
}

function launchEffect(npc, world, data) {
    var sx = npc.getX();
    var sy = npc.getY() + START_Y_OFFSET;
    var sz = npc.getZ();
    // направление по XZ, dy = 0 для горизонтального пролёта
    // ...
    data.put(CHARGING_KEY, "0");
    data.put(ACTIVE_KEY, "1");
    data.put(X_KEY, String(sx));
    data.put(Y_KEY, String(sy));
    data.put(Z_KEY, String(sz));
    data.put(DX_KEY, String(dx));
    data.put(DY_KEY, String(dy));
    data.put(DZ_KEY, String(dz));
}
```

---

## Активная фаза: шаги и финиш

```javascript
function doActiveTick(npc, world, data) {
    var step = getInt(data, STEP_KEY);
    var maxSteps = Math.floor(RANGE / SPEED);

    if (step >= maxSteps) {
        doFinisher(npc, world, data);
        clearState(data);
        return;
    }

    var dx = getFloat(data, DX_KEY);
    var dy = getFloat(data, DY_KEY);
    var dz = getFloat(data, DZ_KEY);
    var x = getFloat(data, X_KEY) + dx * SPEED;
    var y = getFloat(data, Y_KEY) + dy * SPEED;
    var z = getFloat(data, Z_KEY) + dz * SPEED;

    data.put(X_KEY, String(x));
    data.put(Y_KEY, String(y));
    data.put(Z_KEY, String(z));
    data.put(STEP_KEY, String(step + 1));

    drawEffect(world, x, y, z, dx, dy, dz, step);
    damageAlongPath(npc, world, data, x, y, z);
}
```

### VFX-пролёт (NPC стоит на месте)

Партиклы рисуются в `(x, y, z)` из storeddata. NPC **не обязан** двигаться.

### Реальный рывок босса

Добавь в `doActiveTick`: `npc.setPosition(x, y, z)` и `npc.setRotation(yaw)`.

---

## Урон по пути

```javascript
function damageAlongPath(npc, world, data, x, y, z) {
    var pos = NpcAPI.getIPos(x, y, z);
    var list = world.getNearbyEntities(pos, HIT_RADIUS, EntitiesType.ANY);
    var hitRaw = String(data.get(HIT_KEY));
    var hitList = "," + hitRaw.replace(/;/g, ",") + ",";

    for (var i = 0; i < list.length; i++) {
        var ent = list[i];
        if (!ent.isAlive()) continue;
        if (String(ent.getUUID()) == String(npc.getUUID())) continue;

        var id = String(ent.getUUID());
        if (hitList.indexOf("," + id + ",") !== -1) continue;

        ent.damage(RAM_DAMAGE);
        hitRaw = hitRaw + id + ";";
        hitList = "," + hitRaw.replace(/;/g, ",") + ",";
    }

    data.put(HIT_KEY, hitRaw);
}
```

Финальный AoE — тот же `getNearbyEntities` + `NpcAPI.getIPos` с большим радиусом.

---

## Партиклы и звук

Строковые id партиклов (как в community-скриптах):

```javascript
world.spawnParticle("soul_fire_flame", x, y, z, 0.02, 0.02, 0.02, 0.0, 1);
world.spawnParticle("end_rod", x, y, z, 0.0, 0.0, 0.0, 0.0, 1);
world.playSoundAt(NpcAPI.getIPos(x, y, z), "minecraft:entity.generic.explode", 1.0, 0.8);
```

Глобальные `ParticleType_*` допустимы, но для сложного VFX чаще удобнее строки.

---

## `clearState`

```javascript
function clearState(data) {
    data.put(ACTIVE_KEY, "0");
    data.put(CHARGING_KEY, "0");
    data.put(STEP_KEY, "0");
    data.put(HIT_KEY, "");
    data.put(TARGET_KEY, "");
    data.put(CHARGE_LEFT_KEY, "0");
}
```

При потере цели во время зарядки/пролёта — `clearState(data)` и `return`.

---

## Когда НЕ этот паттерн

| Задача | Событие |
|--------|---------|
| Снаряды `shootItem` + логика попадания | `projectileImpact`, `projectileTick` |
| Толчок игрока по ПКМ, motion каждый тик | `interact` + `init` + `timer(1)` |
| Реакция только на урон без polling | `damaged` (опционально дублирует tick) |

`init` + `timer` — **исключение**, не стандарт для боевых способностей.

---

## Java-абилки + тонкий JS (рекомендуется для боссов)

Для сложных боевых паттернов (рывок, прыжок-slam, несколько фаз с уроном/VFX) механика вынесена в **Java** (`noppes.npcs.abilities`). JS остаётся **оркестратором**: агро, LOS, шанс каста, кулдауны в `storeddata`, выбор `dash` / `jump_slam`, пер-кастовые параметры.

```javascript
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");

function timer(event) {
    var npc = event.npc;
    if (AbilityAPI.isBusy(npc)) return;
    // ... проверки цели, кулдауны, дистанция ...
    AbilityAPI.start(npc, "dash", target, AbilityAPI.params("damage", 12.0));
}
```

| Слой | Ответственность |
|------|-----------------|
| **JS** | Когда кастовать, кулдауны (`getTotalTime` + storeddata), `pickAbility`, `params` по HP/фазе |
| **Java** | charge/active тики, `setPosition`, урон, knockback, hit-list, партиклы, звуки |

Полный справочник API и ключей параметров: [abilities-reference.md](abilities-reference.md).

Эталон тонкого скрипта: `scripts/boss_dash_jump/boss_dash_jump.js`.

При смерти / потере цели: `AbilityAPI.cancel(npc)`.

Классический tick state machine (ниже) по-прежнему подходит для **простых** способностей без Java-реализации (VFX-пролёт, одиночный заряд).

---

## Чеклист нового скрипта

- [ ] Bootstrap `NpcAPI` + `EntitiesType` вверху
- [ ] Блок НАСТРОЙКИ и KEY-константы
- [ ] `tick` — единственный диспетчер фаз
- [ ] storeddata — строки, `getInt`/`getFloat`
- [ ] Кулдаун через `getTotalTime`
- [ ] Hit-list для пролёта
- [ ] `clearState` при смерти NPC и срыве каста
- [ ] `NpcAPI.getIPos` для `getNearbyEntities` / `playSoundAt`
