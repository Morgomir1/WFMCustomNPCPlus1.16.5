---
name: nashorn-customnpc-scripting
description: Описывает ограничения Nashorn (JSR-223, ECMAScript 5.1), Java↔JS interop и стиль кода для JS-скриптов WFMCustomNPCPlus1.16.5. Используй при написании/отладке CustomNPC+ JavaScript, ошибках синтаксиса Nashorn, Java.type/getMCEntity, несовместимости ES6+ или когда нужен низкоуровневый доступ к JVM из скрипта. Дополняет skill customnpc-js-scripting (события и API CustomNPC+).
---

# Nashorn в CustomNPC+ (WFM 1.16.5)

## Разделение ответственности со skill `customnpc-js-scripting`

| Skill | Что покрывает |
|---|---|
| **customnpc-js-scripting** | События NPC/player/item/block, API (`IEntity`, `IWorld`, таймеры, снаряды, WFM-интеграция) |
| **nashorn-customnpc-scripting** (этот) | Движок Nashorn, ECMAScript 5.1, Java↔JS, стиль кода, отладка синтаксиса и interop |

Перед написанием скрипта читай **оба** skill: API — в `customnpc-js-scripting`, язык и JVM — здесь.

---

## Стек выполнения

```
Nashorn (Project Nashorn / nashorn.jar)
  → JSR-223 ScriptEngine (javax.script)
  → CustomNPC+ ScriptContainer
  → invokeFunction("tick"|"init"|..., event)
```

- Скрипты выполняются **только на сервере**.
- Код из GUI NPC сохраняется в NBT; референсы — в `WFMCustomNPCPlus1.16.5/src/main/resources/scripts/`.
- CustomNPC+ компилирует скрипт один раз (`engine.eval`), затем вызывает функции по имени события через `Invocable.invokeFunction`.
- Имя функции = имя события: `function tick(event)`, `function damaged(event)` и т.д.

### Зависимость: Project Nashorn

С Java 15 Nashorn удалён из JDK. Для MC 1.16.5 нужен мод [Project Nashorn](https://www.curseforge.com/minecraft/mc-mods/project-nashorn) или `nashorn.jar` в `mods/` (**только сервер**, если клиент не редактирует скрипты).

Официальная настройка: [CustomNPCs Scripting](http://www.kodevelopment.nl/minecraft/customnpcs/scripting/), примеры: [cnpcs-scripting-examples](https://github.com/Noppes/cnpcs-scripting-examples).

---

## Языковой стандарт: ECMAScript 5.1

Nashorn заявляет совместимость с [ECMA-262 Edition 5.1](https://es5.github.io/). Это **не** современный браузерный JS.

### Пиши так (безопасно для CustomNPC+)

```javascript
var COOLDOWN_SEC = 5;

function init(event) {
    var npc = event.npc;
    npc.getTimers().start(1, 20, true);
}

function timer(event) {
    if (event.id != 1) return;
    for (var i = 0; i < players.length; i++) {
        var p = players[i];
        if (p != null && p.isAlive()) {
            doSomething(p);
        }
    }
}

function doSomething(player) {
    try {
        player.message("ok");
    } catch (e) {
        log("error: " + e);
    }
}
```

### Избегай (может не работать без флагов ES6)

| Конструкция | Альтернатива |
|---|---|
| `=>` arrow functions | `function(x) { return x; }` |
| `let` / `const` | `var` (предпочтительно для переносимости) |
| `class Foo {}` | объект + функции или `Java.extend` |
| `async`/`await`, Promises | синхронный код + таймеры CustomNPC+ |
| `import`/`export` ES modules | один файл, функции верхнего уровня |
| `.forEach(function(){})` в цепочках | классический `for (var i = 0; ...)` |
| template literals `` `a${b}` `` | конкатенация `"a" + b` |
| деструктуризация `{x, y} = obj` | `var x = obj.x; var y = obj.y;` |

> **Правило WFM:** все скрипты в репозитории (`push_interact/`, `grey_seer/`, `rat_ogre/`, `witch_hunter/`) написаны в **ES5.1-стиле** — следуй им.

Частичный ES6 (`--language=es6`) теоретически доступен через `nashorn.args`, но CustomNPC+ **не гарантирует** его включение. Не полагайся на ES6 в продакшен-скриптах.

---

## Модель выполнения CustomNPC+

### Функции-события

CustomNPC+ ищет **глобальную функцию** с именем события. Если функции нет — событие игнорируется (не ошибка).

```javascript
function init(event) { /* один раз при спавне */ }
function tick(event) { /* каждые 10 тиков */ }
function timer(event) { /* по npc.getTimers().start(id, interval, repeat) */ }
function trigger(event) { /* /script trigger или npc.trigger() */ }
```

### Глобальное состояние скрипта

Переменные верхнего уровня (`var SPELLS = {...}`) живут в scope одного `ScriptEngine` на NPC. Для персистентности используй `getStoreddata()` / `getTempdata()` — см. `customnpc-js-scripting`.

### Несколько вкладок скрипта

Каждая вкладка в редакторе NPC = **отдельный ScriptEngine** (см. комментарий в `rat_ogre.js` про `rat_ogre_pursuit.js`). Общие утилиты дублируй или выноси константы в storeddata мира/NPC.

### Глобальные хелперы CustomNPC+

Доступны без импорта:

```javascript
log("сообщение");   // консоль сервера + лог скрипта
dump(event.npc);    // поля и методы объекта (отладка)
```

Константы API (`PotionEffectType_POISON`, `ParticleType_FIRE`, …) — глобальные, см. `customnpc-js-scripting/reference.md`.

---

## Java ↔ JavaScript interop

### Предпочитай CustomNPC API

```javascript
// ✅ Основной путь
var world = npc.getWorld();
var near = world.getNearbyEntities(npc.getPos(), 8, 1); // type 1 = игрок
player.addPotionEffect(PotionEffectType_POISON, 5, 0, false);
```

### `getMCEntity()` — низкоуровневый доступ

Когда API CustomNPC+ не хватает (навигация, GeckoLib, поля `EntityProjectile`):

```javascript
try {
    var mc = npc.getMCEntity();
    mc.getNavigation().stop();
} catch (e) {
    log("mc access failed: " + e);
}
```

Паттерн «сырой Entity vs IEntity wrapper» — в `customnpc-js-scripting` (раздел projectileImpact). Кратко:

```javascript
function wrapEntity(rawOrWrapper, api) {
    if (rawOrWrapper == null) return null;
    if (typeof rawOrWrapper.getMCEntity == "function") return rawOrWrapper;
    return api.getIEntity(rawOrWrapper);
}
```

### `Java.type` — прямой доступ к Java-классам

```javascript
var NpcAPI = Java.type("noppes.npcs.api.NpcAPI");
var api = NpcAPI.Instance();
```

Используй **только когда** нет эквивалента в `event.API` / `event.npc` / `event.world`. Примеры из репозитория: `rat_ogre.js` (NpcAPI.Instance).

Полный справочник interop: [nashorn-reference.md](nashorn-reference.md).

---

## Типичные ошибки Nashorn в CustomNPC+

### 1. Синтаксис ES6+

```
ScriptException: <eval>:10:0 Expected an operand but found =>
```

**Решение:** заменить arrow function на `function() {}`.

### 2. Сравнение Java-объектов

`==` между Java-обёртками может вести себя неожиданно. Для UUID/строк:

```javascript
function isSameEntity(a, b) {
    if (a == null || b == null) return false;
    return String(a.getUUID()) == String(b.getUUID());
}
```

### 3. `storeddata` возвращает string

Всегда приводи к числу:

```javascript
var cdUntil = Number(player.getStoreddata().get(KEY_CD));
if (!isNaN(cdUntil) && now < cdUntil) return;
player.getStoreddata().put(KEY_CD, now + COOLDOWN_SEC * 20);
```

### 4. `addPotionEffect` — секунды, не тики

`player.addPotionEffect(PotionEffectType_POISON, 5, 0, false)` = **5 секунд**, не 100 тиков.

### 5. `event.target` в projectileImpact

Может быть **сырым** `net.minecraft.entity.Entity`, не `IEntity`. Оборачивай через `event.API.getIEntity()`.

### 6. `getOwner()` у снарядов NPC

Часто `null` для NPC-стрелка. Используй `mc.thrower` / `mc.npc` — см. `customnpc-js-scripting`.

### 7. Ошибка отключает скрипт

Любое необработанное исключение в событии может **отключить** скрипт до `/script reload`. Оборачивай рискованный код в `try/catch` + `log(e)`.

---

## Стиль и структура скрипта WFM

Шаблон из репозитория:

```javascript
// Заголовок: имя, способности, команды отладки
var CONST_A = 10;
var KEY_STATE = "my_state";

function init(event) {
    var npc = event.npc;
    npc.getStoreddata().put(KEY_STATE, 0);
    npc.getTimers().start(1, 20, true);
}

function timer(event) { /* ... */ }
function damaged(event) { /* ... */ }
function trigger(event) {
    if (event.id == 1) { /* /script trigger 1 */ }
}

// --- утилиты внизу файла ---
function helper(x) { /* ... */ }
```

Практики:
- Константы — `var` вверху файла.
- Логика кастов — отдельные `function castX(ctx)`.
- Отладка — `log("[boss_id] ...")` с префиксом; флаг `var DEBUG = false`.
- Таймеры CustomNPC+ вместо тяжёлого `tick()`.
- `//` комментарии (не полагайся на `#` — это расширение Nashorn `-scripting`).

---

## Отладка

```javascript
log("phase=" + npc.getStoreddata().get("phase"));
dump(event.player);

try {
    riskyCall();
} catch (e) {
    log("fail: " + e);
    if (e.stack) log(e.stack);  // Nashorn добавляет e.stack, e.lineNumber
}
```

1. Смотри консоль **dedicated server** (не клиент).
2. После правки в GUI: `/script reload`.
3. Тестируй триггеры: `/script trigger cast dash` (если скрипт обрабатывает `trigger`).

---

## Референсные скрипты (WFMCustomNPCPlus1.16.5)

| Путь | Что демонстрирует |
|---|---|
| `scripts/push_interact/player_push_to_coords.js` | interact, timer, storeddata, motion |
| `scripts/grey_seer/rat_wave.js` | SPELLS registry, projectileImpact, getMCEntity |
| `scripts/rat_ogre/rat_ogre.js` | state machine, Java.type(NpcAPI), таймеры |
| `scripts/witch_hunter/witch_hunter_main.js` | несколько оружий, MC navigation |

---

## Внешние источники

| Ресурс | URL |
|---|---|
| ECMAScript 5.1 (спецификация) | https://es5.github.io/ |
| Nashorn extensions (Java interop) | https://wiki.openjdk.org/spaces/Nashorn/pages/17957105/Nashorn+extensions |
| JSR-223 Scripting API | https://docs.oracle.com/en/java/javase/11/scripting/java-scripting-api.html |
| CustomNPCs 1.16.5 API | http://www.kodevelopment.nl/customnpcs/api/1.16.5/ |
| Project Nashorn (CurseForge) | https://www.curseforge.com/minecraft/mc-mods/project-nashorn |

Подробности Java.type, SAM, конвертации типов: [nashorn-reference.md](nashorn-reference.md).
