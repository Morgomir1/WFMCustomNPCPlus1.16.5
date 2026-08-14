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
| `ghost_soul_bolt` | `GhostSoulBoltAbility` | Навес soul-болта (как crimson_blob) → knockback |
| `necro_volley` | `NecromancerVolleyAbility` | 3 некро-залпа в случайные точки → каждая точка спавнит сферу-клон |
| `necro_rings` | `NecromancerRingsAbility` | 3 последовательных ring telegraph вокруг босса → 10 MAGIC по annulus |
| `otrodie_hell_vomit` | `OtrodieHellVomitAbility` | Струя + движущаяся red hazard; break по урону в спину → force fecal_wave |
| `otrodie_fecal_wave` | `OtrodieFecalWaveAbility` | Усечённый конус назад (после vomit): урон + poison/slowness |
| `otrodie_devour_dash` | `OtrodieDevourDashAbility` | Line TG → dash grab → eat 5s; 15 melee spit / timeout heal 200 |
| `otrodie_spreading_filth` | `OtrodieSpreadingFilthAbility` | Зелёные лужи (крупная+малые); JS `trigger` по −200 HP / CD 20с |
| `vampire_whirl_slash` | `VampireWhirlSlashAbility` | Круговой удар + 100 HP за цель |
| `vampire_crimson_bats` | `VampireCrimsonBatsAbility` | 2 мыши; тычки хилят босса на 15 |
| `vampire_blood_ring` | `VampireBloodRingAbility` | Кольцо-аура 10с, следует за боссом |
| `vampire_blood_dash` | `VampireBloodDashAbility` | Homing-рывок без уворота + лужи крови |
| `vampire_blood_slash` | `VampireBloodSlashAbility` | Конус мечом (как охотник), кровь |

Оркестратор парного босса: `src/main/resources/scripts/drachenfels/drachenfels_boss.js`.
Оркестратор сгустка: `src/main/resources/scripts/utility/crimson_blob.js`.
Оркестратор Отродья: `src/main/resources/scripts/otrodie/otrodie_boss.js`.
Оркестратор Кровавого лорда: `src/main/resources/scripts/vampire/vampire_crimson_lord.js`.
Оркестратор кровавого рывка: `src/main/resources/scripts/vampire/vampire_blood_dash.js`.
Оркестратор некроманта: `src/main/resources/scripts/necromancer/necromancer_boss.js`.

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
| `arcHeight` | double | 5.0 | Высота навеса струи (как `crimson_blob`) |
| `particleCount` / `blobParticles` | int / string | 12 / nurgle… | Струя VFX по дуге |
| `maxRange` | double | 28.0 | Макс. дальность каста |

Струя семплируется по параболе `baseY + arcHeight * 4 * t * (1-t)` от рта к hazard-зоне. onEnd пишет `storeddata ot_forced_ability=otrodie_fecal_wave`. Прерывание — `OtrodieCombatHandler` + `ActiveAbility.meter`.

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

### `otrodie_devour_dash`

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `distance` | double | 14.0 | Длина рывка / line telegraph |
| `chargeTicks` / `activeTicks` | int | 24 / 8 | Charge + dash |
| `hitRadius` | double | 1.6 | Half-width коридора; TG width = `hitRadius*2` |
| `hitCount` | int | 15 | Melee-хиты для spit без хила |
| `healOnFail` | double | 200.0 | Хилл босса при timeout eat |
| `knockback` / `knockbackY` | double | 1.6 / 0.45 | Spit вперёд |
| `telegraphColor` | int | `0xC0FF3030` | Красный ARGB |

Фаза EAT (100 тиков): freeze+телепорт жертвы к пасти. Invuln + melee meter — `OtrodieCombatHandler`.

### `otrodie_spreading_filth`

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `radius` | double | 4.0 | Крупная лужа под боссом |
| `hitRadius` | double | 1.6 | Радиус малых луж |
| `spreadRadius` | double | 4.5 | Кольцо малых луж (clamp 3–6) |
| `summonCount` | int | 5 | Число малых луж |
| `zoneTicks` | int | 200 | Lifetime (10 с) |
| `damage` / `damageInterval` | double / int | 3.0 / 10 | Урон hazard |
| `zoneColor` | int | `0xC040A030` | Зелёный ARGB |
| `effectId` | string | poison;slowness | Дебаффы зоны |
| `particleCount` / `landParticles` | int / string | 12 / nurgle… | Puddle splash VFX |

Основной путь: `OtrodieSpreadingFilthAbility.trigger(npc, params)` из JS (`otrodie_boss.js` damaged: −200 HP, CD 400 тиков). Не через AbilityRunner — не отменяет текущий каст. `AbilityAPI.start` делегирует в spawn и сразу FINISHED.

**Кулдауны босса** — не в Java; хранятся в JS `storeddata`.

### `ghost_orbit_slam`

Подлёт сквозь блоки → орбита → врезание + knockback → смерть NPC.

| Ключ | Дефолт | Описание |
|------|--------|----------|
| `approachSpeed` | 0.45 | Блоки/тик |
| `radius` | 2.5 | Радиус орбиты |
| `orbitTicks` | 60 | Кружение (~3 с) |
| `orbitSpeed` | 8.0 | Градусы/тик |
| `hoverOffset` | 1.0 | Высота над целью |
| `slamTicks` | 6 | Врезание |
| `damage` / `knockback` / `knockbackY` / `hitRadius` | 14 / 2.4 / 0.55 / 1.8 | Удар |

Скрипт: `scripts/ghost/ghost_orbit_slam.js`. `cancelsOnTargetLost = false`.

### `ghost_soul_bolt`

Навесной soul-сгусток (дуга как `crimson_blob`) → knockback при касании по пути и в точке падения. Без hazard-зоны.

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` / `activeTicks` | int | 24 / 14 | Charge + полёт |
| `arcHeight` | double | 5.0 | Высота навеса |
| `landRadius` | double | 2.2 | AoE / telegraph на земле |
| `hitRadius` | double | 1.4 | Касание по траектории |
| `damage` | double | 0.0 | Урон (0 = только откидывание) |
| `knockback` / `knockbackY` | double | 2.2 / 0.5 | Отбрасывание |
| `blobParticles` / `landParticles` | string | soul… | Партиклы через `,` |
| `maxRange` | double | 24.0 | Дальность каста |

Скрипт: `scripts/ghost/ghost_soul_bolt.js` (CD 10 с). `cancelsOnTargetLost = false`.

### `necro_volley`

Три последовательных навесных некро-сгустка в случайные точки вокруг босса. Каждое приземление спавнит сферу-клон (скелеты — сразу после появления сферы, затем волнами); пока жива хотя бы одна сфера этого босса, JS не даёт повторно кастовать залп.

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` / `activeTicks` | int | 20 / 18 | Charge + окно трёх залпов |
| `shots` | int | 3 | Число точек |
| `shotInterval` / `firstShotTick` | int | 5 / 1 | Ритм выстрелов в active-фазе |
| `spreadRadius` | double | 16.0 | Радиус случайных точек вокруг босса |
| `landRadius` | double | 1.6 | Размер telegraph в точке приземления |
| `maxRange` | double | 28.0 | Проверка дистанции до цели на старте |
| `blobParticles` / `landParticles` | string | soul,witch… | CSV партиклов |
| `particleCount` | int | 10 | Плотность VFX |

Скрипт: `scripts/necromancer/necromancer_boss.js`. Клоны сфер/скелетов задаются в storeddata босса (`necro_clone_tab`, `necro_sphere_clone`, `necro_skeleton_clone`).

### `necro_rings`

Три последовательных кольца вокруг босса: через 1 секунду телеграфа каждое кольцо бьёт на 10 `DamageSource.MAGIC`. Босс во время каста заморожен.

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` | int | 20 | Время телеграфа на каждое кольцо |
| `damage` | double | 10.0 | MAGIC-урон кольца |
| `telegraph` | int | 0 | Авто-telegraph отключен, кольца спавнятся вручную |
| `telegraphColor` | int | `0xC0FF3030` | Цвет telegraph |

Кольца фиксированные: `3–5`, `6–8`, `9–11`. Скрипт: `scripts/necromancer/necromancer_boss.js`.

### `vampire_whirl_slash`

Круговой удар вокруг босса. Charge 8 тиков (круг) + yaw-spin, затем AoE и хилл 100 HP за цель.

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` | int | 8 | Зарядка (0.4 с; фаза 2 → 4) |
| `damage` | double | 18.0 | Урон круга |
| `radius` | double | 4.5 | Радиус AoE / telegraph |
| `knockback` / `knockbackY` | double | 1.2 / 0.3 | Радиальное |
| `lifeStealPerHit` | double | 100.0 | Хилл за каждую цель |

### `vampire_crimson_bats`

Две мыши-клона. Тычка мыши хилит босса на 15 (`VampireCrimsonBatHealHandler`).

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` | int | 8 | Зарядка |
| `radius` | double | 3.0 | Telegraph у босса |
| `summonCount` / `summonRadius` | int / double | 2 / 2.5 | Спавн |
| `maxSummonedNearBoss` | int | 2 | Потолок живых |
| `cloneTab` / `cloneName` | int / string | 1 / `Vampire Crimson Bat` | Клон |
| `lifeStealPerHit` | double | 15.0 | Хилл босса за тычку мыши |

### `vampire_blood_ring`

Charge → hazard-кольцо 10 с следует за боссом. Урон в annulus, дым + души. Босс не бежит по кольцу.

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` | int | 8 | Зарядка |
| `radius` / `innerRadius` | double | 7.0 / 4.5 | Внешний / внутренний |
| `damage` / `damageInterval` | double / int | 3.0 / 10 | Тик урона зоны |
| `zoneTicks` | int | 200 | Lifetime (10 с) |
| `zoneColor` | int | `0xC0180810` | Тёмный ARGB |

Оркестратор: `scripts/vampire/vampire_crimson_lord.js` (пассивка +25 melee, one-way ярость на 50%).

### `vampire_blood_dash`

Homing-рывок: метка на игроке → полёт в **текущую** позицию → snap, урон гарантирован. Игрок 8 с оставляет красные лужи; вампир подбирает лужу (хилл + звук + партиклы), зона сразу исчезает.

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` / `activeTicks` | int | 18 / 6 | Зарядка + рывок |
| `damage` | double | 14.0 | Урон попадания |
| `hitRadius` | double | 2.2 | Касание / метка |
| `radius` | double | 1.8 | Радиус лужи |
| `zoneTicks` | int | 50 | Lifetime лужи |
| `trailTicks` | int | 160 | Длительность следа (8 с) |
| `puddleInterval` | int | 8 | Интервал спавна луж |
| `healPerTick` | double | 15.0 | Хилл вампира в луже |
| `zoneColor` | int | `0xC0B01018` | Цвет лужи |

Оркестратор: `scripts/vampire/vampire_blood_dash.js` (slash вблизи / dash 3–16). Пассивки: +25 HP с автоатаки; раненые игроки ускоряют бег/удары; 50% и 10% HP (по разу за бой) — невидимость + 5 мышей (хилл 10 за удар).

### `vampire_blood_slash`

Удар мечом усечённым конусом, как `wh_flaming_strike`. Красный telegraph, кровь, без огня.

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` | int | 20 | Warning 1 с |
| `distance` | double | 4.5 | Длина сектора |
| `radius` | double | 1.35 | Полуширина у ног |
| `coneHalfAngle` | double | 38 | Половина угла |
| `damage` | double | 14.0 | Урон |

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
| 5 | `innerRadius` > 0 и `radius` > inner | ring |
| 6 | `radius` / `auraRadius` | circle (или `telegraphForward` / impact point) |

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
