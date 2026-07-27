# Java-абилки CustomNPC+ (`AbilityAPI`)

Серверные боевые способности NPC в пакете `noppes.npcs.abilities`. Механика (фазы charge/active, движение, урон, VFX) — в Java; JS-скрипт только решает **когда** кастовать и передаёт **пер-кастовые** параметры.

Пример тонкого оркестратора: `src/main/resources/scripts/boss_dash_jump/boss_dash_jump.js`.

---

## Bootstrap в скрипте

```javascript
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");
```

---

## API

| Метод | Описание |
|-------|----------|
| `start(npc, id, target)` | Старт с дефолтами Java |
| `start(npc, id, target, overrides)` | Старт с переопределениями (`Map` из `params`) |
| `params(key, value, ...)` | Пары key/value → `HashMap`; нечётное число аргументов → исключение |
| `isBusy(npc)` | `true`, пока абилка активна |
| `getActiveId(npc)` | id активной абилки или `null` |
| `cancel(npc)` | Принудительный сброс |

- `npc` — `ICustomNpc`
- `target` — `IEntityLiving` (для `dash` и `jump_slam` обязателен)
- Неизвестный `id` → лог + `false` от `start`
- Тик runner: каждый **серверный** тик (`AbilityTickHandler`, `ServerTickEvent` END)

---

## Зарегистрированные абилки

| id | Класс | Описание |
|----|-------|----------|
| `dash` | `DashAbility` | Рывок к цели, урон по пути |
| `jump_slam` | `JumpSlamAbility` | Прыжок по дуге, AoE при приземлении |
| `drachenfels_poison_feast` | `DrachenfelsPoisonFeastAbility` | AoE яд + урон (тело Дракенфельса) |
| `drachenfels_dark_cleave` | `DrachenfelsDarkCleaveAbility` | Короткий рывок-замах конусом |
| `drachenfels_soul_rend` | `DrachenfelsSoulRendAbility` | Конус soul/wither (дух) |
| `drachenfels_spirit_barrage` | `DrachenfelsSpiritBarrageAbility` | Серия импульсов к цели |
| `drachenfels_soul_seeker` | `DrachenfelsSoulSeekerAbility` | Дальние soul-импульсы по линии (punish kite) |
| `drachenfels_soul_orbs` | `DrachenfelsSoulOrbsAbility` | Несколько soul-шаров: круглые telegraph → AoE при приземлении |
| `drachenfels_raise_thralls` | `DrachenfelsRaiseThrallsAbility` | Призыв thrall-клонов + aura |
| `drachenfels_shadow_step` | `DrachenfelsShadowStepAbility` | Теневой dash к цели |
| `crimson_blob` | `CrimsonBlobAbility` | Навес сгустка → hazard-зона слепоты + MAGIC DPS |
| `otrodie_hell_vomit` | `OtrodieHellVomitAbility` | Струя + движущаяся red hazard; break по урону в спину → force fecal_wave |
| `otrodie_fecal_wave` | `OtrodieFecalWaveAbility` | Усечённый конус назад (после vomit): урон + poison/slowness |

Оркестратор парного босса: `src/main/resources/scripts/drachenfels/drachenfels_boss.js`.
Оркестратор сгустка: `src/main/resources/scripts/utility/crimson_blob.js`.

---

## Параметры (`AbilityAPI.params`)

Дефолты — в `AbilityDefaults`. Неизвестные ключи логируются и игнорируются.

### `dash`

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `distance` | double | 16.0 | Макс. дистанция рывка |
| `chargeTicks` | int | 10 | Тики зарядки |
| `activeTicks` | int | 7 | Тики активной фазы |
| `damage` | double | 10.0 | Урон при контакте |
| `knockback` | double | 1.8 | Сила отбрасывания |
| `knockbackY` | double | 0.35 | Вертикальный импульс |
| `hitRadius` | double | 1.6 | Радиус попадания по пути |

### `jump_slam`

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` | int | 12 | Тики зарядки |
| `activeTicks` | int | 9 | Тики полёта по дуге |
| `damage` | double | 14.0 | Урон при приземлении |
| `knockback` | double | 2.2 | Радиальное отбрасывание |
| `knockbackY` | double | 0.55 | Вертикальный импульс |
| `landRadius` | double | 2.8 | Радиус AoE при приземлении |
| `arcHeight` | double | 6.0 | Высота дуги |
| `maxRange` | double | 16.0 | Макс. дальность прыжка |

### `drachenfels_poison_feast`

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` | int | 14 | Зарядка |
| `damage` | double | 14.0 | Урон AoE |
| `radius` | double | 5.0 | Радиус |
| `knockback` / `knockbackY` | double | 0.9 / 0.25 | Отбрасывание |
| `effectType` | string | `poison` | Эффект (`AbilityEffectType`) |
| `effectDuration` | int | 80 | Длительность эффекта (тики MC) |
| `effectAmplifier` | int | 1 | Уровень эффекта |

### `drachenfels_dark_cleave`

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `distance` | double | 5.5 | Длина рывка |
| `chargeTicks` / `activeTicks` | int | 8 / 5 | Фазы |
| `damage` | double | 13.0 | Урон |
| `radius` | double | 2.4 | Радиус конуса |
| `coneHalfAngle` | double | 65.0 | Полуугол конуса |
| `knockback` / `knockbackY` | double | 1.4 / 0.3 | Отбрасывание |

### `drachenfels_soul_rend`

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` | int | 12 | Зарядка |
| `damage` | double | 12.0 | Урон конуса |
| `radius` | double | 6.0 | Дальность конуса |
| `coneHalfAngle` | double | 40.0 | Полуугол |
| `effectType` | string | `wither` | Эффект |
| `effectDuration` / `effectAmplifier` | int | 60 / 0 | Параметры эффекта |
| `knockback` / `knockbackY` | double | 0.7 / 0.2 | Отбрасывание |

### `drachenfels_spirit_barrage`

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` / `activeTicks` | int | 10 / 16 | Фазы |
| `shots` | int | 4 | Число импульсов |
| `distance` | double | 14.0 | Макс. длина линии импульсов |
| `damage` | double | 7.0 | Урон импульса |
| `hitRadius` | double | 1.8 | Радиус попадания |
| `knockback` / `knockbackY` | double | 0.5 / 0.15 | Отбрасывание |

### `drachenfels_soul_seeker`

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` / `activeTicks` | int | 12 / 14 | Фазы |
| `shots` | int | 2 | Число импульсов (1–3) |
| `maxRange` / `distance` | double | 40.0 | Дальность луча к цели |
| `damage` | double | 10.0 | Урон импульса (по пути ~55%) |
| `hitRadius` | double | 2.2 | Радиус попадания |
| `knockback` / `knockbackY` | double | 0.65 / 0.18 | Отбрасывание |

### `drachenfels_raise_thralls`

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` / `activeTicks` | int | 12 / 18 | Фазы |
| `summonCount` | int | 2 | Сколько клонов |
| `summonRadius` | double | 3.5 | Радиус спавна |
| `maxSummonedNearBoss` | int | 4 | Лимит thrall рядом |
| `cloneTab` / `cloneName` | int / string | 1 / `Drachenfels Thrall` | Clone Bank |
| `radius` | double | 4.0 | Радиус aura slow |
| `effectType` | string | `slowness` | Эффект aura |

### `drachenfels_shadow_step`

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `distance` | double | 10.0 | Дистанция dash |
| `chargeTicks` / `activeTicks` | int | 6 / 5 | Фазы |
| `damage` | double | 8.0 | Урон по пути |
| `hitRadius` | double | 1.5 | Радиус хита |
| `knockback` / `knockbackY` | double | 1.1 / 0.25 | Отбрасывание |

### `crimson_blob`

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` / `activeTicks` | int | 20 / 14 | Зарядка + полёт навесом |
| `arcHeight` | double | 5.0 | Высота дуги |
| `landRadius` | double | 2.0 | Радиус hazard-зоны / telegraph |
| `damage` | double | 3.0 | MAGIC-урон зоны за хит |
| `damageInterval` | int | 20 | Интервал урона зоны (тики) |
| `zoneTicks` | int | 160 | Lifetime зоны (8 с) |
| `effectId` | string | `minecraft:blindness` | Дебаффы через `;` |
| `effectDuration` / `effectAmplifier` | int | 40 / 0 | Длительность/уровень при каждом хите |
| `blobParticles` / `landParticles` | string | flame,smoke,… | Партиклы через `,` |
| `particleCount` | int | 12 | Плотность партиклов |
| `zoneColor` | int | `0xC0801010` | ARGB цвет зоны |
| `maxRange` | double | 20.0 | Макс. дальность каста |

Приземление: `ZoneAPI.hazardCircle` + эффекты из `effectId`. Тюнинг — в JS (`scripts/utility/crimson_blob.js`).

### `otrodie_hell_vomit`

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` / `activeTicks` | int | 28 / 280 | Charge + continuous vomit |
| `radius` | double | 2.5 | Радиус moving hazard / telegraph |
| `damage` / `damageInterval` | double / int | 3.0 / 10 | Урон hazard-зоны |
| `zoneTicks` | int | 300 | Lifetime зоны |
| `zoneColor` / `telegraphColor` | int | `0xC0FF3030` | Красный ARGB |
| `effectId` | string | poison;slowness | Дебаффы зоны |
| `breakDamage` | double | 100.0 | Урон в спину для прерывания |
| `particleCount` / `blobParticles` | int / string | 12 / nurgle… | Струя VFX |
| `maxRange` | double | 28.0 | Макс. дальность каста |

onEnd пишет `storeddata ot_forced_ability=otrodie_fecal_wave`. Прерывание — `OtrodieCombatHandler` + `ActiveAbility.meter`.

### `otrodie_fecal_wave`

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` | int | 24 | Charge + red truncated cone telegraph |
| `hitRadius` | double | 2.0 | minDist усечённого конуса |
| `distance` | double | 12.0 | maxDist конуса |
| `coneHalfAngle` | double | 55.0 | Полуугол сектора |
| `damage` | double | 14.0 | Burst-урон |
| `knockback` / `knockbackY` | double | 0.85 / 0.25 | Отбрасывание назад по конусу |
| `effectId` | string | poison;slowness | Дебаффы через `;` |
| `effectDuration` / `effectAmplifier` | int | 60 / 0 | Длительность/уровень |
| `telegraphColor` | int | `0xC0FF3030` | Красный ARGB |
| `particleCount` / `blobParticles` | int / string | 12 / nurgle… | Burst VFX |

Yaw конуса = взгляд босса + 180 (атака в спину). Старт обычно forced после `otrodie_hell_vomit`.

**Кулдауны босса** — не в Java; хранятся в JS `storeddata`.

---

## Примеры

```javascript
// дефолты
AbilityAPI.start(npc, "dash", target);

// пер-кастовый урон
var hpRatio = npc.getHealth() / npc.getMaxHealth();
var damage = hpRatio < 0.3 ? 14.0 : 10.0;
AbilityAPI.start(npc, "dash", target, AbilityAPI.params("damage", damage));

// усиленный прыжок
AbilityAPI.start(npc, "jump_slam", target, AbilityAPI.params(
    "damage", 18.0,
    "arcHeight", 7.0,
    "landRadius", 3.0,
    "maxRange", 16.0
));

// в timer/tick оркестратора
if (AbilityAPI.isBusy(npc)) return;
```

При смерти NPC или потере цели: `AbilityAPI.cancel(npc)`.

---

## Архитектура (кратко)

```
JS → AbilityAPI.start → AbilityRunner (Map UUID → ActiveAbility)
     ↑ каждый серверный тик
AbilityTickHandler → AbilityRunner.tickAll → DashAbility / JumpSlamAbility
```

Новые абилки: реализовать `CnpcAbility`, зарегистрировать в `AbilityRegistry`, добавить дефолты и ключи в `AbilityParamKeys` / `abilities-reference.md`.

---

## Telegraph + Ability Zone

Визуальные warning-зоны (telegraph) и наносящие урон hazard-зоны для боссов.

### Авто-телеграф (AbilityAPI)

После успешного `onStart` `AbilityRunner` сам спавнит telegraph по параметрам:

| Приоритет | Условие | Форма |
|-----------|---------|--------|
| 1 | `coneHalfAngle` > 0 | cone (`radius` / `hitRadius` = длина) |
| 2 | `landRadius` > 0 | circle в точке ленда (`active.ex/ey/ez`) |
| 3 | `distance` + `radius` | line-коридор (+ circle в impact, если есть) |
| 4 | только `distance` | line (`hitRadius` = ширина) |
| 5 | `radius` / `auraRadius` | circle (или `telegraphForward` / impact point) |

Отключить: `"telegraph", 0`. Цвет: `"telegraphColor", 0xC0FF3030`.

### JS API — Telegraph

```javascript
var TelegraphAPI = Java.type("noppes.npcs.telegraph.TelegraphAPI");

var id = TelegraphAPI.circle(npc, x, y, z, radius, durationTicks, 0x80FF3030);
TelegraphAPI.cone(npc, x, y, z, yaw, length, halfAngleDeg, durationTicks, color);
TelegraphAPI.line(npc, x, y, z, yaw, length, width, durationTicks, color);
TelegraphAPI.ring(npc, x, y, z, outerR, innerR, durationTicks, color);
TelegraphAPI.follow(id, entity);      // или followNpc(id, npc)
TelegraphAPI.remove(id);
```

### JS API — Zone (hazard entity)

```javascript
var ZoneAPI = Java.type("noppes.npcs.zone.ZoneAPI");

var zone = ZoneAPI.hazardCircle(npc, x, y, z, radius, durationTicks, damage, damageInterval);
ZoneAPI.hazardRing(npc, x, y, z, outerR, innerR, duration, damage, interval);
ZoneAPI.trapCircle(npc, x, y, z, radius, duration, damage);

zone.setEffect("minecraft:poison", 60, 0);
zone.setColor(0x80FF0000);
zone.setKnockback(0.5);
zone.setDamage(3.0);
zone.moveTo(x, y, z, 0, 0);   // следовать за NPC
ZoneAPI.remove(zone);
```

- Урон только врагам кастера (`AbilityCombatHelper.isHostileToBoss`).
- `HAZARD` — тик урона; `TRAP` — один trigger при входе.
- Клиент рисует заливку/бордер; пакеты telegraph не тянуть в серверный код абилок — только `TelegraphAPI` / `ZoneAPI`.
