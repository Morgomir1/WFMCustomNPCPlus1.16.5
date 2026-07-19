---
name: customnpc-js-scripting
description: Обучает JS-скриптам CustomNPC+ (Nashorn ES5.1) для способностей NPC — стандарт WFM tick state machine, Java.type(NpcAPI).Instance(), storeddata-строки, getTotalTime-кулдауны, VFX-пролёт. Используй при JS-скриптах CustomNPC+, способностях/спеллах NPC, босс-абилках, AI через CustomNPCs, или когда нужно уникальное поведение моба.
---

# JS-скриптинг CustomNPC+ для WFM 1.16.5

> **Связанные skills:** `nashorn-customnpc-scripting` — Nashorn ES5.1, `Java.type`, bootstrap `NpcAPI`, стиль кода. `customnpc-java-abilities` — создание абилок в Java (`CnpcAbility`, `AbilityRegistry`). [script-java-helpers.md](script-java-helpers.md) — утилиты `ScriptDataUtil`, vampire helpers. Этот skill — события, API и **архитектура способностей**.

## Стандартная архитектура WFM (способности NPC)

**Два пути:**

1. **Java-абилки + тонкий JS** (боссы, сложная механика) — `AbilityAPI.start(npc, id, target, params)`. См. [abilities-reference.md](abilities-reference.md) и секцию в [architecture.md](architecture.md).
2. **Tick state machine в JS** (простые способности, VFX-пролёт) — зарядка → активная фаза → финиш. Полный разбор: [architecture.md](architecture.md).

### Bootstrap (начало каждого скрипта способности)

```javascript
var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();
var EntitiesType = Java.type("noppes.npcs.api.constants.EntitiesType");
```

Если `EntitiesType` недоступен — используй числовые id (`-1` = любая сущность). См. таблицу «Типы сущностей» внизу.

### Каркас файла

1. Bootstrap → 2. **НАСТРОЙКИ** → 3. ключи storeddata → 4. **`function tick(e)`** → 5. фазы (`startCharge`, `doChargingTick`, `doActiveTick`, `clearState`) → 6. VFX/урон (TelegraphAPI / ZoneAPI) → 7. утилиты (`getInt`, `getFloat`, `distance`)

Зоны атаки: `TelegraphAPI` (warning) + `ZoneAPI` (hazard). AbilityAPI сам рисует telegraph по `radius`/`landRadius`/`coneHalfAngle`/`distance`. Подробности — [abilities-reference.md](abilities-reference.md).

### Главный цикл

```javascript
function tick(e) {
    var npc = e.npc;
    var world = npc.getWorld();
    var data = npc.getStoreddata();
    var now = world.getTotalTime();

    if (!npc.isAlive()) { clearState(data); return; }
    if (String(data.get(CHARGING_KEY)) == "1") { doChargingTick(npc, world, data); return; }
    if (String(data.get(ACTIVE_KEY)) == "1") { doActiveTick(npc, world, data); return; }
    if (now < getInt(data, CD_KEY)) return;
    // проверки цели → startCharge(...)
}
```

**Состояние:** все значения в `storeddata` — **строки** (`"0"`/`"1"`, координаты через `String(x)`). **Кулдаун:** `data.put(CD_KEY, String(now + COOLDOWN_TICKS))`. **Урон:** `NpcAPI.getIPos(x,y,z)` + `world.getNearbyEntities(pos, radius, EntitiesType.ANY)` + hit-list UUID в storeddata.

**VFX-пролёт:** координаты эффекта в storeddata; NPC может стоять на месте. **Реальный рывок:** `npc.setPosition(x, y, z)` в `doActiveTick`.

### Когда другие события (не основной путь)

| Событие | Когда |
|---|---|
| `tick` | **Основной** цикл способностей |
| `init` + `timer(1)` | Только плавный motion (толчок по ПКМ) |
| `projectileImpact` | `shootItem` + `enableEvents()` |
| `interact` | Утилиты по ПКМ |

### Переключение режимов AI (OnAttack)

Для фаз «стоять и кастовать», «отступать N секунд», «снова атаковать» — `npc.getAi().setRetaliateType(id)` + `setWalkingSpeed`. Полный паттерн, константы и цикл стрельба→отступление: [ai-retaliate-modes.md](ai-retaliate-modes.md).

---

## Общая архитектура

CustomNPC+ использует **Nashorn** (JSR-223) на **сервере** через `ScriptContainer`. Скрипт — в GUI NPC, сохраняется в NBT.

### Жизненный цикл

1. **`tick(event)`** — **главный цикл** способностей (раз в 10 игровых тиков ≈ 0.5 с)
2. `init(event)` — опционально (таймеры с интервалом 1 тик)
3. `timer(event)` — опционально (не для стандартных боевых абилок)
4. `interact`, `damaged`, `projectileImpact` — по задаче

### Как скрипт выполняется

```java
// Псевдокод выполнения:
engine.eval(fullCode);                    // компиляция JS-кода (однократно)
((Invocable)engine).invokeFunction(type, event);  // вызов функции по имени события
```

Где `type` = `"tick"`, `"init"`, `"interact"` и т.д., `event` — Java-объект события с полями.

---

## Полный список событий NPC

### NPC события

| JS-функция | Описание | Поля event | Cancelable |
|---|---|---|---|
| `init(event)` | При создании NPC | `event.npc` | нет |
| `tick(event)` | Каждые 10 тиков (0.5с) | `event.npc` | нет |
| `interact(event)` | Игрок ПКМ по NPC | `event.npc`, `event.player` | **да** |
| `dialog(event)` | Открытие диалога | `event.npc`, `event.player`, `event.dialog` | **да** |
| `dialogOption(event)` | Выбор варианта диалога | `event.npc`, `event.player`, `event.dialog`, `event.option` | нет |
| `dialogClose(event)` | Закрытие диалога | `event.npc`, `event.player`, `event.dialog` | нет |
| `damaged(event)` | NPC получил урон | `event.npc`, `event.source`, `event.damage`, `event.damageSource` | **да** |
| `died(event)` | NPC умер | `event.npc`, `event.damageSource`, `event.type`, `event.source` | нет |
| `meleeAttack(event)` | NPC атакует вблизи | `event.npc`, `event.target`, `event.damage` | **да** |
| `rangedLaunched(event)` | NPC стреляет | `event.npc`, `event.target`, `event.damage`, `event.projectiles[]` | **да** |
| `target(event)` | NPC выбрал цель | `event.npc`, `event.entity` | **да** |
| `targetLost(event)` | Потерял цель | `event.npc`, `event.entity` | **да** |
| `kill(event)` | NPC убил кого-то | `event.npc`, `event.entity` | нет |
| `collide(event)` | Столкновение с сущностью | `event.npc`, `event.entity` | нет |
| `timer(event)` | Сработал таймер (по `getTimers()`) | `event.npc`, `event.id` | нет |
| `trigger(event)` | Команда `/noppes script trigger` или `npc.trigger()` | `event.id`, `event.arguments[]`, `event.world`, `event.pos` | нет |
| `role(event)` | Роль NPC (см. RoleEvent) | зависит от роли | нет |
| `projectileTick(event)` | Тик кастомного снаряда | `event.projectile`, `event.API` | нет |
| `projectileImpact(event)` | Попадание кастомного снаряда | `event.projectile`, `event.type`, `event.target`, `event.API` | нет |

### Player события (скрипт на игроке)

| JS-функция | Описание | Поля event | Cancelable |
|---|---|---|---|
| `init(event)` | Игрок создан/загружен | `event.player` | нет |
| `tick(event)` | Каждые 10 тиков | `event.player` | нет |
| `interact(event)` | Игрок ПКМ по блоку/воздуху | `event.player`, `event.item` | нет |
| `attack(event)` | Игрок ЛКМ (по блоку/сущности/воздуху) | `event.player`, `event.target` | нет |
| `broken(event)` | Игрок сломал блок | `event.player`, `event.block` | нет |
| `damaged(event)` | Игрок получил урон | `event.player`, `event.source`, `event.damage` | **да** |
| `damagedEntity(event)` | Игрок нанёс урон сущности | `event.player`, `event.entity`, `event.damage` | нет |
| `died(event)` | Игрок умер | `event.player`, `event.damageSource`, `event.type` | нет |
| `kill(event)` | Игрок убил сущность | `event.player`, `event.entity` | нет |
| `rangedLaunched(event)` | Игрок выстрелил из лука | `event.player`, `event.projectiles[]` | нет |
| `toss(event)` | Игрок выбросил предмет | `event.player`, `event.item` | **да** |
| `pickUp(event)` | Игрок подобрал предмет | `event.player`, `event.item` | нет |
| `containerOpen(event)` | Открытие контейнера | `event.player`, `event.container` | нет |
| `containerClosed(event)` | Закрытие контейнера | `event.player`, `event.container` | нет |
| `chat(event)` | Игрок написал сообщение | `event.player`, `event.message` | **да** |
| `login(event)` | Игрок зашёл | `event.player` | нет |
| `logout(event)` | Игрок вышел | `event.player` | нет |
| `factionUpdate(event)` | Очки фракции изменились | `event.player`, `event.faction`, `event.points` | нет |
| `levelUp(event)` | Уровень игрока изменился | `event.player`, `event.level` | нет |
| `keyPressed(event)` | Нажата клавиша | `event.player`, `event.key` | нет |
| `keyReleased(event)` | Отпущена клавиша | `event.player`, `event.key` | нет |
| `playSound(event)` | Игрок проиграл звук | `event.player`, `event.sound`, `event.category` | нет |
| `dialog(event)` | Открытие диалога | `event.player`, `event.npc`, `event.dialog` | **да** |
| `dialogOption(event)` | Выбор варианта диалога | `event.player`, `event.npc`, `event.dialog`, `event.option` | нет |
| `dialogClose(event)` | Закрытие диалога | `event.player`, `event.npc`, `event.dialog` | нет |
| `questStart(event)` | Игрок начал квест | `event.player`, `event.quest` | нет |
| `questCompleted(event)` | Игрок завершил квест | `event.player`, `event.quest` | нет |
| `questTurnIn(event)` | Игрок сдал квест | `event.player`, `event.quest` | нет |
| `timer(event)` | Сработал таймер | `event.player`, `event.id` | нет |
| `trigger(event)` | Вызов триггера `/noppes script trigger` | `event.id`, `event.arguments[]`, `event.world` | нет |

### Item события (скриптовый предмет)

| JS-функция | Описание | Поля event | Cancelable |
|---|---|---|---|
| `init(event)` | Предмет создан/загружен | `event.item` | нет |
| `tick(event)` | Каждые 10 тиков (пока в инвентаре) | `event.item` | нет |
| `interact(event)` | ПКМ по блоку/сущности/воздуху | `event.player`, `event.item`, `event.target` | нет |
| `attack(event)` | ЛКМ по блоку/сущности/воздуху | `event.player`, `event.item`, `event.target` | нет |
| `toss(event)` | Выброшен на землю | `event.player`, `event.item` | **да** |
| `spawn(event)` | Появился в мире (выброшен) | `event.entity` (IEntityItem), `event.item` | нет |
| `pickedUp(event)` | Подобран игроком | `event.player`, `event.item` | **да** |

### Block события (скриптовый блок)

| JS-функция | Описание | Поля event | Cancelable |
|---|---|---|---|
| `init(event)` | Блок создан/загружен | `event.block` | нет |
| `tick(event)` | Каждые 10 тиков | `event.block` | нет |
| `interact(event)` | ПКМ по блоку | `event.block`, `event.player` | нет |
| `redstone(event)` | Сигнал редстоуна | `event.block`, `event.power` | нет |
| `broken(event)` | Блок разрушен | `event.block`, `event.player` | нет |
| `exploded(event)` | Блок взорван | `event.block` | нет |
| `fallenUpon(event)` | Сущность упала на блок | `event.block`, `event.entity`, `event.distance` | нет |
| `rainFilled(event)` | Дождь наполнил | `event.block` | нет |
| `neighborChanged(event)` | Соседний блок изменился | `event.block`, `event.changedBlock` | нет |
| `clicked(event)` | Клик по блоку | `event.block`, `event.player`, `event.side` | нет |
| `harvested(event)` | Блок собран игроком | `event.block`, `event.player` | **да** |
| `collide(event)` | Сущность столкнулась с блоком | `event.block`, `event.entity` | нет |
| `doorToggle(event)` | Дверь открыта/закрыта | `event.block` | нет |
| `timer(event)` | Сработал таймер | `event.block`, `event.id` | нет |
| `trigger(event)` | Триггер | `event.id`, `event.arguments[]`, `event.world`, `event.pos` | нет |

### Custom GUI события

| JS-функция | Описание | Поля event |
|---|---|---|
| `customGuiButton(event)` | Нажата кнопка в кастомном GUI | `event.player`, `event.gui`, `event.buttonId` |
| `customGuiClosed(event)` | Кастомный GUI закрыт | `event.player`, `event.gui` |
| `customGuiSlot(event)` | Клик по слоту | `event.player`, `event.gui`, `event.slot` |
| `customGuiScroll(event)` | Прокрутка списка | `event.player`, `event.gui`, `event.scroll` |

### Projectile события

| JS-функция | Описание | Поля event |
|---|---|---|
| `projectileTick(event)` | Каждый тик полёта снаряда | `event.projectile`, `event.API` |
| `projectileImpact(event)` | Попадание снаряда | `event.projectile`, `event.type`, `event.target`, `event.API` |

### Role события (роли NPC)

| JS-функция | Описание |
|---|---|
| `role(event)` | Общее — вызывается ролью. Внутри проверяйте `event.type`: `trader`, `transporter`, `follower`, `bank`, `mailman` |

> **Cancelable** = если поставить `event.setCanceled(true)`, событие отменяется (например, урон не пройдёт, атака не состоится).

> `projectileTick` / `projectileImpact` вызываются **только если** на снаряде вызван `proj.enableEvents()` **во время выполнения скрипта NPC** (при `shootItem`). См. раздел «Снаряды NPC» ниже.

---

## API-объекты из JS

Официальная документация: http://www.kodevelopment.nl/customnpcs/api/1.16.5/

Для полного списка всех методов каждого API-объекта см. [reference.md](reference.md).

**Краткая навигация по иерархии наследования:**

```
IEntity → IEntityLiving → IMob → ICustomNpc (NPC)
IEntity → IEntityLiving → IPlayer (игрок)
IEntity → IEntityLiving → IMonster (монстр)
IEntity → IAnimal (животное)
IEntity → IProjectile (снаряд)
IEntity → IEntityItem (предмет на земле)
IEntity → IArrow (стрела)
IEntity → IThrowable (бросаемый предмет)
IEntity → IVillager (житель)
```

**Основные точки входа:**

### event.npc (`ICustomNpc`)
```javascript
npc.getDisplay()        // INPCDisplay (имя, титул)
npc.getStats()          // INPCStats (жизни, броня, урон)
npc.getAi()             // INPCAi (AI, цель, пути)
npc.getInventory()      // INPCInventory (инвентарь NPC)
npc.getFaction()        // IFaction (фракция)
npc.getAdvanced()       // INPCAdvanced (доп. настройки)
npc.getRole()           // INPCRole (роль банкира/торговца...)
npc.getJob()            // INPCJob (работа барда/курьера...)
npc.getTimers()         // ITimers — таймеры (start, stop, has)
npc.getOwner()          // IEntityLiving — владелец (фолловер)
npc.shootItem(target, item, accuracy) // выстрелить
npc.executeCommand(cmd) // выполнить команду
npc.trigger(id, ...args)
```

### event.player (`IPlayer`)
```javascript
player.getDisplayName()         // отображаемое имя
player.message(text)            // отправить сообщение
player.getInventory()           // IContainer инвентаря
player.giveItem(item/string, n) // дать предмет
player.removeItem(item/string, n) // забрать предмет
player.getExpLevel() / setExpLevel(level)
player.getHunger() / setHunger(n)
player.getGamemode() / setGamemode(type)
player.startQuest(id) / finishQuest(id)
player.hasActiveQuest(id) / hasFinishedQuest(id)
player.addFactionPoints(factionId, pts)
player.showCustomGui(gui) / closeGui()
player.getStoreddata() / getTempdata()
player.getTimers()
player.trigger(id, ...args)
```

### event.entity / event.source (`IEntity`)
```javascript
entity.getX() / getY() / getZ() / getBlockX() / getBlockY() / getBlockZ()
entity.setPosition(x, y, z)
entity.getWorld()
entity.isAlive() / getAge()
entity.getName() / setName(name) / getEntityName()
entity.getType() / typeOf(type) / getTypeName()
entity.getUUID()
entity.damage(amount)  / kill() / despawn() / spawn()
entity.getTempdata() / getStoreddata() / getNbt()
entity.getTags() / addTag(tag) / hasTag(tag)
entity.getMount() / setMount(entity)
entity.getRiders() / addRider(entity) / clearRiders()
entity.rayTraceBlock(distance) / rayTraceEntities(distance)
entity.getMotionX() / setMotionX(x)
entity.dropItem(item)
entity.playAnimation(type)
```

### event.world (`IWorld`)
```javascript
world.getBlock(x, y, z) / setBlock(pos, "mod:id") / removeBlock(x, y, z)
world.getTime() / setTime(time) / isDay() / isRaining()
world.getAllEntities(type) / getNearbyEntities(pos, range, type)
world.getClosestEntity(pos, range, type)
world.getAllPlayers() / getPlayer(name)
world.createItem("mod:id", n) / createEntity("mod:id")
world.spawnEntity(entity)
world.explode(x, y, z, range, fire, grief)
world.thunderStrike(x, y, z)
world.spawnParticle(particle, x, y, z, dx, dy, dz, speed, count)
world.broadcast(message) / playSoundAt(pos, sound, vol, pitch)
world.getTempdata() / getStoreddata()
world.trigger(id, ...args)
```

### IItemStack — предмет

```javascript
item.getStackSize() / setStackSize(n)
item.getName() / setDisplayName(name)
item.getCount() / isEnchanted()
item.getNbt() / hasNbt()
item.getMCItemStack()
```

### IData — хранилище ключ-значение

```javascript
data.put(key, value)    // сохранить (value: number или string)
data.get(key)           // прочитать
data.has(key)           // проверить
data.remove(key)        // удалить
data.clear()            // очистить
data.getKeys()          // все ключи
```

**Разница**:
- `getTempdata()` — временное, живёт пока мир загружен
- `getStoreddata()` — сохраняется в NBT (постоянно)

### Глобальные константы

Полный список ParticleType (37 шт) и PotionEffectType (30 шт): см. [reference.md](reference.md) и http://www.kodevelopment.nl/customnpcs/api/1.16.5/noppes/npcs/api/constants/package-summary.html

**`addPotionEffect(typeId, duration, amplifier, hideParticles)`** — второй аргумент `duration` задаётся в **секундах**, не в тиках. API сам умножает на 20. Пример: `player.addPotionEffect(PotionEffectType_POISON, 5, 0, false)` = отравление I на 5 секунд.

**Вспомогательные функции:**
- `dump(object)` — вывести все поля/методы объекта в консоль (отладка)
- `log(text)` — запись в консоль скрипта и серверный лог

---

## Снаряды NPC: projectileImpact / projectileTick

Паттерн для спеллов «залп снарядов с кастомной логикой при попадании» (проклятие, стаки, дебаффы). Референс: `WFMCustomNPCPlus1.16.5/.../scripts/grey_seer/rat_wave.js`.

### Цепочка вызовов

1. NPC в `timer` / `trigger` вызывает `npc.shootItem(...)` → получает `IProjectile`.
2. Сразу после выстрела (в том же скрипте): `proj.enableEvents()` + метка в `proj.getTempdata()`.
3. При полёте: `projectileTick(event)` на каждый тик снаряда.
4. При попадании: `projectileImpact(event)` — **только если** снаряд зарегистрирован через `enableEvents()`.

```javascript
function shootCustomProj(npc, world, target) {
    var item = world.createItem("minecraft:emerald", 1);
    var proj = npc.shootItem(target, item, 10);
    if (proj == null) return null;

    proj.enableEvents(); // ОБЯЗАТЕЛЬНО во время скрипта NPC, иначе impact не придёт
    proj.getTempdata().put("my_proj", 1);

    var mc = proj.getMCEntity();
    mc.thrower = npc.getMCEntity(); // см. «Владелец снаряда» ниже
    mc.npc = npc.getMCEntity();
    return proj;
}
```

### event.projectileImpact — поля

| Поле | Значение |
|---|---|
| `event.type` | `0` = попадание в **сущность**, `1` = в **блок** |
| `event.target` | При `type=0`: сырой `net.minecraft.entity.Entity` (часто `ServerPlayerEntity`). При `type=1`: `IBlock` |
| `event.projectile` | `IProjectile` |
| `event.API` | `NpcAPI` для `getIEntity()` |

Большинство снарядов залпа попадают в блоки (`type=1`) — это нормально. Логику стаков/дебаффов вешайте только на `type == 0`.

### Обёртка цели: сырой Entity ≠ IEntity

**Частая ошибка:** `event.target.getType() == 1` на сыром игроке. У Minecraft-entity `getType()` возвращает `EntityType`, не CustomNPC type id.

```javascript
function wrapImpactTarget(event) {
    var target = event.target;
    if (target == null) return null;
    if (typeof target.getMCEntity == "function") return target; // уже IEntity
    return NpcAPI.getIEntity(target); // или event.API.getIEntity(target)
}

function isPlayerEntity(entity) {
    if (entity == null) return false;
    if (typeof entity.typeOf == "function" && entity.typeOf(1)) return true;
    if (typeof entity.getType == "function" && entity.getType() == 1) return true;
    if (typeof entity.getMCEntity == "function") {
        var mc = entity.getMCEntity();
        return mc != null && String(mc.getClass().getName()).indexOf("ServerPlayerEntity") >= 0;
    }
    return String(entity.getClass().getName()).indexOf("ServerPlayerEntity") >= 0;
}
```

### Владелец снаряда от NPC: не использовать getOwner()

`EntityProjectile.getOwner()` при потере ссылки `thrower` ищет владельца через `world.getPlayerByUUID(throwerName)` — **только среди игроков**. Для снарядов NPC `getOwner()` часто возвращает `null`.

Надёжный порядок поиска босса-стрелка:

```javascript
function getProjectileBoss(event) {
    var mc = event.projectile.getMCEntity();
    var world = event.projectile.getWorld();

    if (mc.thrower != null) {
        var boss = NpcAPI.getIEntity(mc.thrower);
        if (boss != null) return boss;
    }
    if (mc.npc != null) {
        var boss2 = NpcAPI.getIEntity(mc.npc);
        if (boss2 != null) return boss2;
    }
    if (mc.throwerName != null && String(mc.throwerName).length > 0) {
        var all = world.getAllEntities(2); // все NPC
        for (var i = 0; i < all.length; i++) {
            if (String(all[i].getUUID()) == String(mc.throwerName)) return all[i];
        }
    }
    return null;
}
```

При выстреле явно закрепляйте владельца:

```javascript
var bossMc = npc.getMCEntity();
mc.thrower = bossMc;
mc.npc = bossMc;
proj.getTempdata().put("boss_uuid", String(npc.getUUID()));
```

### Состояние на игроке: storeddata, не только теги

Для стаков проклятия / счётчиков попаданий предпочтительнее `player.getStoreddata()` — сохраняется в NBT игрока, не путается с командными тегами.

```javascript
player.getStoreddata().put("my_stacks", 5);
var stacks = player.getStoreddata().get("my_stacks");
```

Теги (`addTag`) можно дублировать для отладки (`/data get entity @s Tags`), но не полагайтесь только на них.

### Минимальный projectileImpact

```javascript
function projectileImpact(event) {
    if (event.type != 0) return;

    var proj = event.projectile;
    if (proj == null || proj.getTempdata().get("my_proj") != 1) return;

    var target = wrapImpactTarget(event);
    if (!isPlayerEntity(target)) return;

    target.addPotionEffect(PotionEffectType_POISON, 5, 0, false); // 5 секунд!
    log("hit " + target.getName());
}
```

### Чеклист отладки снарядов

1. `enableEvents()` вызван сразу после `shootItem` (в том же `timer`/`cast`)?
2. В логе есть `projectileImpact` вообще? Если нет — снаряд не зарегистрирован.
3. `type=1` в логе — попадание в блок, не в игрока.
4. `boss not found` — не используйте `getOwner()` для NPC; читайте `mc.thrower` / `mc.npc`.
5. Эффект слишком долгий — `addPotionEffect` принимает **секунды**, не тики (`5`, не `100`).

---

## Примеры

Все примеры хранятся в отдельных файлах:

### Из официального репозитория Noppes/cnpcs-scripting-examples

| # | Файл | Описание | События |
|---|---|---|---|
| 1 | [examples/official/hello-world.md](examples/official/hello-world.md) | Hello World | `init` |
| 2 | [examples/official/tempdata.md](examples/official/tempdata.md) | Временные данные (Daot) | `init`, `interact`, `getTempdata` |
| 3 | [examples/official/storeddata.md](examples/official/storeddata.md) | Постоянные данные (Daot) | `init`, `interact`, `getStoreddata` |
| 4 | [examples/official/retreat-weapon.md](examples/official/retreat-weapon.md) | Убирание оружия (Runon) | `init`, `target`, `targetLost`, NBT |
| 5 | [examples/official/lockable-door.md](examples/official/lockable-door.md) | Запираемая дверь (AnnikenYT) | блок-скрипт, `event.block` |
| 6 | [examples/official/transporter-fee.md](examples/official/transporter-fee.md) | Транспортировщик (Xelerax) | `dialogClose`, `role`, `removeItem` |

### WFM практические примеры

| # | Файл | Описание | События |
|---|---|---|---|
| 1 | [examples/wfm/fire-barrier.md](examples/wfm/fire-barrier.md) | Огненный аурный барьер | `damaged`, `particle`, `timer` |
| 2 | [examples/wfm/teleport-illusion.md](examples/wfm/teleport-illusion.md) | Телепортация и иллюзии | `interact`, `target`, `timer` |
| 3 | [examples/wfm/minion-summoner.md](examples/wfm/minion-summoner.md) | Призыв миньонов | `timer`, `died`, `kill` |
| 4 | [examples/wfm/ice-mage.md](examples/wfm/ice-mage.md) | Ледяной маг с конусом холода | `rangedLaunched`, `meleeAttack`, `damaged` |
| 5 | [examples/wfm/boss-phases.md](examples/wfm/boss-phases.md) | Босс с фазами | `storeddata`, `timer`, `damaged` |
| 6 | [examples/wfm/healer-support.md](examples/wfm/healer-support.md) | Хиллер-саппорт | `timer`, `interact` |
| 7 | [examples/wfm/shield-blocker.md](examples/wfm/shield-blocker.md) | Щитоносец с блокированием | `damaged`, `meleeAttack` |
| 8 | [examples/wfm/tick-ability-state-machine.md](examples/wfm/tick-ability-state-machine.md) | **Эталон способности** (tick SM) | `tick`, `Java.type`, storeddata |

### Референсные скрипты (WFMCustomNPCPlus1.16.5)

| Паттерн | Файл |
|---|---|
| **Эталон способности (tick SM)** | [architecture.md](architecture.md) — зарядка, VFX-пролёт, таран, `NpcAPI.getIPos` |
| Снаряды + `projectileImpact` | `scripts/grey_seer/rat_wave.js` |
| `interact` + `timer(1)` (исключение) | `scripts/push_interact/player_push_in_npc_look_dir.js` |
| OnAttack: мщение / отступление | `scripts/skaven/skaven_eshin_smoke_stab.js` |
| OnAttack + Java-абилка: залп → отступление 8 сек | `scripts/skaven/skaven_engineer_ratling_gun.js` |
| Рывок с `setPosition` | `boss_dash_script.js` |

---

## Триггеры (вызов извне)

Триггеры позволяют вызывать JS-функцию `trigger(event)` из любой точки:

**Из команды:**
```
/script trigger 42 аргумент1 аргумент2
```

**Из Java-кода:**
```javascript
event.npc.trigger(42, "arg1", 123);
```

**В скрипте:**
```javascript
function trigger(event) {
    log("Триггер #" + event.id + " вызван с аргументами: " + event.arguments);
    if (event.id == 42) {
        // особая логика
    }
}
```

---

## Взаимодействие с WFM

### Проверка фракции игрока

```javascript
// WFM фракция игрока (предполагается хранение в storeddata)
function interact(event) {
    var faction = event.player.getFactionPoints(1); // id фракции
    if (faction > 100) {
        event.player.message("§aСоюзник!");
    } else if (faction < -100) {
        event.player.message("§cВраг!");
    }
}
```

### Выполнение WFM-команд

```javascript
function interact(event) {
    event.npc.executeCommand("wfm_command arg1 arg2");
}
```

> **Важно**: Командные блоки должны быть включены на сервере (`enable-command-block=true`).

---

## Отладка скриптов

```javascript
// Вывод в консоль
log("Что-то произошло, значение: " + someValue);

// Дамп объекта (все поля и методы)
dump(event.npc);
dump(event.player);
dump(event.world);
```

Для просмотра ошибок скрипта — консоль сервера. При ошибке скрипт отключается до перезагрузки (`/script reload`).

---

## Лучшие практики

1. **Bootstrap вверху**: `var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance()` + `EntitiesType` — в каждом скрипте способности.
2. **Один диспетчер `tick`**: фазы через флаги в `storeddata` (`CHARGING_KEY`, `ACTIVE_KEY`), не размазывай state machine по `timer`/`damaged`.
3. **storeddata — строки**: флаги `"0"`/`"1"`, числа через `String()`, чтение через `getInt`/`getFloat`.
4. **Кулдаун через `world.getTotalTime()`**: абсолютная метка `now + COOLDOWN_TICKS`, не декремент каждый тик.
5. **Hit-list в storeddata**: UUID через `;` — не бить одну цель дважды за пролёт.
6. **Позиция для AoE**: `NpcAPI.getIPos(x, y, z)` для `getNearbyEntities` и `playSoundAt`.
7. **Проверяй цель**: `canSeeEntity`, дистанция, при необходимости — только игрок (`getAllPlayers` + UUID).
8. **`clearState`**: при смерти NPC и срыве каста (потеря цели, нет линии видимости).
9. **Таймеры — исключение**: только для motion 1 тик/тик (`push_interact`); для периодики — `forceStart` в `init` + страховка в `interact`.
10. **Режимы OnAttack**: `setRetaliateType` (0 мстить / 2 отступать / 3 ничего) — см. [ai-retaliate-modes.md](ai-retaliate-modes.md).
11. **Снаряды** — отдельный паттерн: `shootItem` + `enableEvents()` + `projectileImpact`; там `NpcAPI.getIEntity` для сырого `event.target`.
12. **Партиклы**: строковые id (`"end_rod"`, `"soul_fire_flame"`) для сложного VFX; не спамь сотнями за один вызов `tick`.
13. **`/script reload`** после правок в GUI.

---

## Ссылки

- **API документация 1.16.5:** http://www.kodevelopment.nl/customnpcs/api/1.16.5/
- **Константы (ParticleType, PotionEffectType, ...):** http://www.kodevelopment.nl/customnpcs/api/1.16.5/noppes/npcs/api/constants/package-summary.html
- **Официальные примеры скриптов (GitHub):** https://github.com/Noppes/cnpcs-scripting-examples
- **Страница скриптинга (Kodevelopment):** https://www.kodevelopment.nl/minecraft/customnpcs/scripting/
- **API 1.18.2 (актуальная версия):** http://www.kodevelopment.nl/customnpcs/api/1.18.2/

---

## Типы сущностей

| ID | Тип |
|---|---|
| -1 | Любая сущность (`Entity.class`) |
| 1 | Игрок (`PlayerEntity.class`) |
| 2 | NPC (`EntityNPCInterface.class`) |
| 3 | Монстр (`MonsterEntity.class`) |
| 4 | Животное (`AnimalEntity.class`) |
| 5 | Живое существо (`LivingEntity.class`) |
| 6 | Предмет (`ItemEntity.class`) |
| 7 | Снаряд CustomNPC (`EntityProjectile.class`) |
| 8 | Покемон (Pixelmon) |
| 9 | Житель (`VillagerEntity.class`) |
| 10 | Стрела (`AbstractArrowEntity.class`) |
| 11 | Бросаемый предмет (`ThrowableEntity.class`) |
