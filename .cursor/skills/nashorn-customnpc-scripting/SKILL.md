---
name: nashorn-customnpc-scripting
description: Nashorn ES5.1 и Java interop для CustomNPC+ WFM 1.16.5 — стандартный bootstrap Java.type(NpcAPI).Instance(), tick state machine, storeddata-строки, getInt/getFloat. Используй при написании/отладке JS-скриптов NPC, ошибках Nashorn, Java.type/EntitiesType или низкоуровневом JVM-доступе. Дополняет customnpc-js-scripting.
---

# Nashorn в CustomNPC+ (WFM 1.16.5)

## Разделение со skill `customnpc-js-scripting`

| Skill | Что покрывает |
|---|---|
| **customnpc-js-scripting** | События, API, **архитектура способностей** ([architecture.md](../customnpc-js-scripting/architecture.md)) |
| **nashorn-customnpc-scripting** (этот) | ES5.1, `Java.type` bootstrap, стиль файла, storeddata-строки, отладка |

Читай **оба** перед написанием скрипта.

---

## Стандартный bootstrap (начало файла)

```javascript
var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();
var EntitiesType = Java.type("noppes.npcs.api.constants.EntitiesType");
```

- `NpcAPI` — глобальный синглтон; используй в утилитах без `event.API`.
- `EntitiesType.ANY` — для `getNearbyEntities`. Fallback: `-1` (любая сущность).
- В `projectileImpact` допустим `event.API` — эквивалент `NpcAPI`.

---

## Стек выполнения

```
Nashorn → JSR-223 → ScriptContainer → invokeFunction("tick", event)
```

- Только **сервер**. Референсы: `WFMCustomNPCPlus1.16.5/src/main/resources/scripts/`.
- [Project Nashorn](https://www.curseforge.com/minecraft/mc-mods/project-nashorn) в `mods/`.

---

## ECMAScript 5.1

```javascript
var RANGE = 25;
var ACTIVE_KEY = "ability_active";

function tick(e) {
    var npc = e.npc;
    var data = npc.getStoreddata();
    if (String(data.get(ACTIVE_KEY)) == "1") {
        doActiveTick(npc, npc.getWorld(), data);
    }
}
```

| Избегай | Альтернатива |
|---|---|
| `=>`, `let`/`const`, `class` | `var`, `function() {}` |
| template literals | `"a" + b` |
| `async`/`await` | синхронный код + фазы в `tick` |

---

## Структура скрипта способности (WFM)

```javascript
var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();
var EntitiesType = Java.type("noppes.npcs.api.constants.EntitiesType");

// =========================
// НАСТРОЙКИ
// =========================
var COOLDOWN_TICKS = 400;
var RAM_DAMAGE = 14.0;

// ключи storeddata
var ACTIVE_KEY = "ability_active";
var CHARGING_KEY = "ability_charging";
var CD_KEY = "ability_cd";

function tick(e) { /* диспетчер фаз */ }
function startCharge(npc, world, data, target, now) { /* ... */ }
function doChargingTick(npc, world, data) { /* ... */ }
function doActiveTick(npc, world, data) { /* ... */ }
function clearState(data) { /* ... */ }

function getInt(data, key) {
    if (!data.has(key)) return 0;
    return parseInt(String(data.get(key)));
}
function getFloat(data, key) {
    if (!data.has(key)) return 0.0;
    return parseFloat(String(data.get(key)));
}
```

Практики:
- **`tick` — главный цикл** способностей; `init`/`timer` — только для исключений (motion 1 тик).
- **storeddata — строки**; флаги `"0"`/`"1"`; кулдаун `world.getTotalTime()`.
- Константы и ключи — `var` вверху.
- Утилиты — внизу файла.
- `//` комментарии.

Подробности: [architecture.md](../customnpc-js-scripting/architecture.md).

---

## storeddata: строки, не числа напрямую

```javascript
var now = world.getTotalTime();
data.put(CD_KEY, String(now + COOLDOWN_TICKS));
data.put(ACTIVE_KEY, "1");

if (now < getInt(data, CD_KEY)) return;
if (String(data.get(CHARGING_KEY)) == "1") { /* ... */ }
```

Явное `String()` при записи, `getInt`/`getFloat`/`String()` при чтении — избегает сюрпризов Nashorn↔Java.

---

## Java ↔ JS interop

### Основной путь — NpcAPI + world/npc

```javascript
var pos = NpcAPI.getIPos(x, y, z);
var list = world.getNearbyEntities(pos, 3.0, EntitiesType.ANY);
ent.damage(RAM_DAMAGE);
world.spawnParticle("end_rod", x, y, z, 0, 0, 0, 0, 1);
```

### `getMCEntity()` — когда API не хватает

```javascript
var mc = npc.getMCEntity();
mc.getNavigation().stop();
```

### `event.API` vs `NpcAPI`

В хуках с `event` оба работают. В **глобальных функциях** (без `event`) — только `NpcAPI`.

```javascript
function wrapEntity(raw) {
    if (raw == null) return null;
    if (typeof raw.getMCEntity == "function") return raw;
    return NpcAPI.getIEntity(raw);
}
```

Полный interop: [nashorn-reference.md](nashorn-reference.md).

---

## Типичные ошибки

### ES6+ синтаксис
`ScriptException: Expected an operand but found =>` → замени на `function() {}`.

### Сравнение UUID
`String(a.getUUID()) == String(b.getUUID())`

### `addPotionEffect` — секунды
`addPotionEffect(PotionEffectType_POISON, 5, 0, false)` = 5 секунд.

### `projectileImpact`: сырой Entity
`NpcAPI.getIEntity(event.target)` — не `event.target.getType() == 1`.

### Снаряды NPC: не `getOwner()`
Используй `mc.thrower` / `mc.npc` — см. `customnpc-js-scripting`.

### Необработанное исключение
Отключает скрипт до `/script reload`. `try/catch` + `log(e)` в рискованных местах.

### Таймеры (только если используешь)
Идемпотентный `init`: `forceStart(id, 1, true)`; страховка запуска в `interact` если `init` не сработал.

### Режимы OnAttack (`setRetaliateType`)
Стрельба / отступление / «ничего» — [ai-retaliate-modes.md](ai-retaliate-modes.md).

---

## Исключения из tick-архитектуры

| Задача | Паттерн |
|---|---|
| Боевая способность | `tick` + storeddata SM |
| Толчок по ПКМ, motion/тик | `interact` + `init` + `timer(1)` |
| Залп снарядов | `timer`/`tick` каст + `projectileImpact` |

---

## Отладка

```javascript
log("phase=" + data.get(ACTIVE_KEY) + " step=" + getInt(data, STEP_KEY));
try { risky(); } catch (e) { log("fail: " + e); if (e.stack) log(e.stack); }
```

Консоль **dedicated server**. После правок: `/script reload`.

---

## Референсы

| Ресурс | Путь / URL |
|---|---|
| Архитектура способностей | [architecture.md](../customnpc-js-scripting/architecture.md) |
| Снаряды | `scripts/grey_seer/rat_wave.js` |
| interact + timer (исключение) | `scripts/push_interact/player_push_in_npc_look_dir.js` |
| ECMAScript 5.1 | https://es5.github.io/ |
| API 1.16.5 | http://www.kodevelopment.nl/customnpcs/api/1.16.5/ |
