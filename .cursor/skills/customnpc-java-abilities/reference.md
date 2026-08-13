# Справочник Java-абилок CustomNPC+

## Карта файлов

```
noppes.npcs.abilities/
├── CnpcAbility.java          # интерфейс
├── ActiveAbility.java        # runtime-состояние (+ meter)
├── AbilityContext.java       # npc, target, world, params
├── AbilityParams.java        # merge + getDouble/getInt
├── AbilityParamKeys.java     # ключи параметров
├── AbilityDefaults.java      # дефолты по id
├── AbilityRegistry.java      # регистрация
├── AbilityRunner.java        # start/tick/cancel, Map ACTIVE
├── AbilityAPI.java           # точка входа для Nashorn
├── AbilityCombatHelper.java  # бой, геометрия, земля
├── AbilityVfx.java           # партиклы
├── TickResult.java           # CONTINUE | FINISHED
├── event/
│   ├── AbilityTickHandler.java
│   ├── ShieldBlockDamageHandler.java
│   ├── OtrodieCombatHandler.java   # front DR / vomit meter / devour
│   └── VampireCrimsonBatHealHandler.java # bat melee → heal owner
└── impl/
    ├── DashAbility.java           # id: dash
    ├── JumpSlamAbility.java       # id: jump_slam
    ├── PistolShotAbility.java     # id: pistol_shot
    ├── NetThrowAbility.java       # id: net_throw
    ├── StakeThrustAbility.java    # id: stake_thrust
    ├── HolyWaterSplashAbility.java # id: holy_water_splash
    ├── BurningBrandAbility.java   # id: burning_brand
    ├── RetreatDashAbility.java    # id: retreat_dash
    ├── DrachenfelsPoisonFeastAbility.java  # id: drachenfels_poison_feast
    ├── DrachenfelsDarkCleaveAbility.java   # id: drachenfels_dark_cleave
    ├── DrachenfelsSoulRendAbility.java     # id: drachenfels_soul_rend
    ├── DrachenfelsSpiritBarrageAbility.java # id: drachenfels_spirit_barrage
    ├── DrachenfelsSoulSeekerAbility.java    # id: drachenfels_soul_seeker
    ├── DrachenfelsRaiseThrallsAbility.java # id: drachenfels_raise_thralls
    ├── DrachenfelsShadowStepAbility.java   # id: drachenfels_shadow_step
    ├── WhFlamingStrikeAbility.java         # id: wh_flaming_strike
    ├── WhLungeAbility.java                 # id: wh_lunge
    ├── WhFlamingCrossbowAbility.java       # id: wh_flaming_crossbow
    ├── WhFireBombAbility.java              # id: wh_fire_bomb
    ├── OtrodieHellVomitAbility.java        # id: otrodie_hell_vomit
    ├── OtrodieFecalWaveAbility.java        # id: otrodie_fecal_wave
    ├── OtrodieDevourDashAbility.java       # id: otrodie_devour_dash
    ├── OtrodieSpreadingFilthAbility.java   # id: otrodie_spreading_filth
    ├── GhostOrbitSlamAbility.java          # id: ghost_orbit_slam
    ├── GhostSoulBoltAbility.java           # id: ghost_soul_bolt
    ├── VampireWhirlSlashAbility.java       # id: vampire_whirl_slash
    ├── VampireCrimsonBatsAbility.java      # id: vampire_crimson_bats
    └── VampireBloodRingAbility.java        # id: vampire_blood_ring
```

## CnpcAbility — контракт

| Метод | Описание |
|-------|----------|
| `getId()` | Строковый id для `AbilityAPI.start(npc, id, ...)` |
| `requiresTarget()` | `true` — target обязателен при старте |
| `cancelsOnTargetLost()` | `true` (дефолт) — отмена при смерти цели; `false` — доиграть фазу |
| `defaultParams()` | `Map<String, Object>` дефолтов |
| `knownParamKeys()` | Белый список ключей из JS overrides |
| `onStart(active, ctx)` | Инициализация; `false` = каст отклонён |
| `tick(active, ctx)` | Один серверный тик |
| `onEnd` / `onCancel` | Финиш / принудительная остановка |

## AbilityAPI (Nashorn)

```javascript
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");
```

| Метод | Описание |
|-------|----------|
| `start(npc, id, target)` | Дефолтные params |
| `start(npc, id, target, overrides)` | Map из `params(...)` |
| `params(key, value, ...)` | Пары key/value; чётное число аргументов |
| `isBusy(npc)` | Активна ли абилка |
| `getActiveId(npc)` | id или `""` |
| `cancel(npc)` | Сброс |

## Зарегистрированные абилки

### `dash` — DashAbility

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `distance` | double | 16.0 | Дистанция рывка (блоков), полный пробег |
| `chargeTicks` | int | 10 | Зарядка |
| `activeTicks` | int | 7 | Активная фаза |
| `damage` | double | 10.0 | Урон по пути |
| `knockback` | double | 1.8 | Отбрасывание |
| `knockbackY` | double | 0.35 | Подброс |
| `hitRadius` | double | 1.6 | Радиус хита |

`cancelsOnTargetLost = false`.

### `jump_slam` — JumpSlamAbility

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` | int | 12 | Зарядка |
| `activeTicks` | int | 9 | Полёт по дуге |
| `damage` | double | 14.0 | Урон при приземлении |
| `knockback` | double | 2.2 | Радиальное |
| `knockbackY` | double | 0.55 | Подброс |
| `landRadius` | double | 2.8 | AoE при приземлении |
| `arcHeight` | double | 6.0 | Высота дуги |
| `maxRange` | double | 16.0 | Макс. дальность прыжка (блоки) |

Конечная точка — позиция цели, обрезанная до `maxRange` (`computeEndPoints(..., false)`).

### `pistol_shot` — PistolShotAbility

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` | int | 8 | Прицеливание |
| `damage` | double | 9.0 | Урон пули |
| `accuracy` | int | 4 | Разброс (→ inaccuracy × 0.15) |
| `maxRange` | double | 24.0 | Макс. дистанция до цели |
| `projectileItem` | string | `wfm:empire_pistol` | Id **пистолета** (не пули) |

При загруженном **WFM**: на зарядке пистолет в руке (оффхенд для pistol, иначе майн); выстрел `BulletEntity` + звук `item.gunpowder_gun_launch`. Без WFM — fallback CNPC-снаряд.

### `net_throw` — NetThrowAbility

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` | int | 10 | Warning круга (0.5 с) |
| `radius` | double | 3.5 | Радиус зоны опутывания (+ авто-telegraph) |
| `effectDuration` | int | 60 | Длительность `HUNTER_NET` / fallback Slowness |
| `effectAmplifier` | int | 3 | Fallback Slowness IV (без WFM) |

В `onStart` фиксирует точку у цели (`active.ex/ey/ez`). После charge опутывает **всех** врагов в круге через `CustomNpcNetHelper.ensnareAroundPoint` (`HUNTER_NET` + визуал сети). Без WFM — Slowness по площади. `cancelsOnTargetLost = false`.

### `stake_thrust` — StakeThrustAbility

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `distance` | double | 3.5 | Длина выпада |
| `chargeTicks` | int | 6 | Зарядка |
| `activeTicks` | int | 4 | Фаза укола |
| `damage` | double | 16.0 | Урон |
| `hitRadius` | double | 1.1 | Радиус хита |
| `undeadBonusMultiplier` | double | 1.5 | Множитель vs undead |

`cancelsOnTargetLost = false`.

### `holy_water_splash` — HolyWaterSplashAbility

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` | int | 14 | Зарядка |
| `damage` | double | 8.0 | Урон в конусе |
| `radius` | double | 4.0 | Дальность конуса |
| `coneHalfAngle` | double | 30.0 | Половина угла конуса (°) |
| `effectDuration` | int | 100 | Длительность Weakness/Slowness |
| `effectAmplifier` | int | 0 | Уровень эффектов |
| `undeadBonusMultiplier` | double | 2.0 | Множитель vs undead |

### `burning_brand` — BurningBrandAbility

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` | int | 8 | Поджиг факела |
| `activeTicks` | int | 12 | Длительность ауры |
| `damagePerTick` | double | 3.0 | Урон за тик |
| `auraRadius` | double | 3.5 | Радиус огня |

`cancelsOnTargetLost = false`.

### `retreat_dash` — RetreatDashAbility

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `distance` | double | 6.0 | Отступление от цели |
| `chargeTicks` | int | 5 | Зарядка |
| `activeTicks` | int | 5 | Рывок назад |

Без урона. `cancelsOnTargetLost = false`.

## AbilityCombatHelper

```java
stopNavigation(IMob npc)
computeYaw(dx, dz) → float
flatDistance(x1, z1, x2, z2) → double
findGroundY(world, x, z, startY) → double
computeDashEndPoints(active, ctx) → boolean
computeRetreatEndPoints(active, ctx) → boolean
computeEndPoints(active, ctx, dashStyle)
```

Jump-стиль (`dashStyle=false`): точка приземления = позиция цели, обрезанная по `maxRange` (> 0).
Dash-стиль — см. `computeDashEndPoints` (`distance`).

```
distanceToTarget(ctx) → double
isUndead(entity) → boolean
isInFrontCone(npc, entity, halfAngleDeg) → boolean
damageNearby(...)
damageWithUndeadBonus(...)
damageInConeWithUndeadBonus(...)
applyPotionNearby(active, ctx, x, y, z, radius, effectType, duration, amplifier)
applyPotionInCone(ctx, x, y, z, radius, halfAngleDeg, effectType, duration, amplifier)
applyEffect(entity, effect, duration, amplifier)
isHostileToBoss(npc, entity) → boolean
```

`effectType`: строка `slowness` / `weakness` → `AbilityEffectType`.

## AbilityVfx

```java
spawnChargeParticles(world, x, y, z, jumpStyle)
spawnStartBurst(world, x, y, z, jumpStyle)
spawnDashTrail(world, x, y, z)
spawnJumpTrail(world, x, y, z)
spawnLandBurst(world, x, y, z, jumpStyle)
spawnHitParticle(world, ent)
spawnMuzzleFlash(world, x, y, z)
spawnHolySplash(world, x, y, z)
spawnFireRing(world, x, y, z, radius)
spawnNetTrail(world, x, y, z)
// Отродье (nurgle_miasma + smoke/ash/witch)
spawnOtrodieVomitStream(world, sx, sy, sz, ex, ey, ez, arcHeight, particlesCsv, countPerType)
// Навес как crimson_blob: lerp + arcHeight*4*t*(1-t); облака вдоль дуги + mouth burst по касательной

spawnOtrodieVomitCloud(world, x, y, z, particlesCsv, countPerType)
spawnOtrodieFecalBurst(world, apexX, apexY, apexZ, yaw, halfAngleDeg, minDist, maxDist, particlesCsv, countPerType)
spawnOtrodiePuddleSplash(world, x, y, z, radius, particlesCsv, countPerType)
```

`ActiveAbility.meter` — общий float (урон в спину hell vomit, melee-хиты devour eat).

## AbilityRunner — условия отказа start()

- `npc == null`, пустой `abilityId`
- NPC уже в `ACTIVE` (`isBusy`)
- Неизвестный id
- `requiresTarget()` и target мёртв/null
- `onStart` вернул `false`

Таймаут: 600 тиков → `onCancel` + лог.

## Шаблон нового param key

```java
// AbilityParamKeys.java
public static final String PULSE_RADIUS = "pulseRadius";

// AbilityDefaults.java — в методе абилки
map.put(AbilityParamKeys.PULSE_RADIUS, 4.0);

// impl/MyAbility.java — knownParamKeys()
AbilityParamKeys.PULSE_RADIUS

// JS
AbilityAPI.params("pulseRadius", 5.0)
```

Ключи в JS — **camelCase**, совпадают с константами в `AbilityParamKeys`.

## Прогресс движения (active phase)

Стандартная формула (как в DashAbility):

```java
final int total = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 7);
final double progress = 1.0 - (active.ticksLeft - 1) / (double) total;
final double cx = active.sx + (active.ex - active.sx) * progress;
final double cz = active.sz + (active.ez - active.sz) * progress;
```

Дуга (jump_slam): `cy = baseY + Math.sin(t * Math.PI) * arcHeight`.

### `drachenfels_*` — парный босс Constant Drachenfels

| id | Роль | Суть |
|----|------|------|
| `drachenfels_poison_feast` | body | AoE poison + damage |
| `drachenfels_dark_cleave` | body | короткий dash + cone |
| `drachenfels_soul_rend` | spirit | cone wither |
| `drachenfels_spirit_barrage` | spirit | импульсы к цели |
| `drachenfels_soul_seeker` | оба | дальние soul-импульсы (punish kite) |
| `drachenfels_raise_thralls` | оба | spawnClone thralls |
| `drachenfels_shadow_step` | оба | soul dash |

Дефолты — `AbilityDefaults.drachenfels*()`. Эффекты `poison` / `wither` — в `AbilityEffectType`.

### `shield_block` — ShieldBlockAbility

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `activeTicks` | int | 20 | Сколько держать щит |
| `blockAngle` | double | 90.0 | Полный угол фронтального блока |
| `telegraph` | int | 0 | Telegraph выключен |

`requiresTarget = false`. Поднимает щит из left/right hand (`ShieldItem` / WFM). Фронтальный урон гасит `ShieldBlockDamageHandler`. JS: `scripts/utility/npc_shield_block.js`.

### `crimson_blob` — CrimsonBlobAbility

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` / `activeTicks` | int | 20 / 14 | Зарядка + полёт VFX-сгустка |
| `arcHeight` | double | 5.0 | Высота навеса |
| `landRadius` | double | 2.0 | Радиус зоны / telegraph |
| `damage` | double | 3.0 | MAGIC DPS зоны (за хит) |
| `damageInterval` | int | 20 | Тики между хитами зоны |
| `zoneTicks` | int | 160 | Lifetime зоны |
| `effectId` | string | `minecraft:blindness` | Дебаффы через `;` |
| `effectDuration` / `effectAmplifier` | int | 40 / 0 | На каждом хите зоны |
| `blobParticles` / `landParticles` | string | csv | Партиклы полёта / ленда |
| `particleCount` | int | 12 | Плотность |
| `zoneColor` | int | `0xC0801010` | Цвет Ability Zone |
| `maxRange` | double | 20.0 | Дальность каста |

Навес партиклов → `ZoneAPI.hazardCircle`. JS: `scripts/utility/crimson_blob.js`.

### `otrodie_*` — босс Отродье

| id | Роль | Суть |
|----|------|------|
| `otrodie_hell_vomit` | cast | charge → continuous stream + moving red hazard; break по `meter`/`breakDamage` → force fecal_wave |
| `otrodie_fecal_wave` | forced | rear truncated cone (yaw+180), poison+slowness |
| `otrodie_devour_dash` | cast | line TG → dash grab → eat 5s; `hitCount` melee spit / `healOnFail` timeout |
| `otrodie_spreading_filth` | reactive | зелёные лужи; основной путь `OtrodieSpreadingFilthAbility.trigger` (без AbilityRunner) |

Дефолты — `AbilityDefaults.otrodie*()`. Ключи: `breakDamage`, `hitCount`, `healOnFail`, `zoneColor` (`0xC0FF3030` red / `0xC040A030` green). Пассивка DR — `OtrodieCombatHandler`. Полные params: `abilities-reference.md`. JS: `scripts/otrodie/otrodie_boss.js`.

### `wh_flaming_strike` — WhFlamingStrikeAbility

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` | int | 20 | 1 с warning (усечённый конус) |
| `distance` | double | 4.5 | Длина сектора впереди босса |
| `radius` | double | 1.35 | Полуширина у ног (задаёт apexBack) |
| `coneHalfAngle` | double | 38 | Половина угла конуса |
| `damage` | double | 14.0 | Урон удара |
| `fireSeconds` | int | 4 | Поджог цели |

Зона: `coneTruncated` с `minDist = apexBack + 1.0` (босс вне зоны). `cancelsOnTargetLost = false`.

### `wh_lunge` — WhLungeAbility

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` / `activeTicks` | int | 20 / 6 | Зарядка + выпад |
| `distance` | double | 5.5 | Длина рывка |
| `hitRadius` | double | 1.5 | Радиус хита |
| `damage` | double | 16.0 | Урон |
| `knockback` / `knockbackY` | double | 0 / 0 | Без откидывания |
| `arcHeight` | double | 1.8 | Высота прыжка |
| `effectDuration` | int | 20 | Длительность `wfm:stun` |

`cancelsOnTargetLost = false`. После каста JS обычно форсит `wh_flaming_strike`.

### `wh_flaming_crossbow` — WhFlamingCrossbowAbility (пистолет)

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` | int | 10 | Прицел: пистолет в левой руке, рука поднята |
| `distance` / `radius` | double | 18 / 0.7 | Узкий line telegraph |
| `damage` | double | 10.0 | Урон WFM-пули |
| `rangedItem` | string | `wfm:empire_pistol` | Пистолет в offhand |
| `meleeItem` | string | `wfm:empire_witch_hunter_rapier` | Restore правой руки |
| `accuracy` | int | 3 | Разброс пули |
| `fireSeconds` | int | 0 | Опциональный hitscan+поджог по коридору |

Звук выстрела: `WFMGunpowderGunItem.getLaunchSound()` через `CustomNpcGunHelper`.

### `wh_fire_bomb` — WhFireBombAbility

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` / `activeTicks` / `scatterTicks` | int | 20 / 14 / 20 | Charge → полёт → разлёт |
| `landRadius` | double | 3.5 | Первичный AoE / telegraph |
| `damage` | double | 12.0 | Урон первичного взрыва |
| `shots` / `spreadRadius` | int/double | 5 / 5.0 | Малые бомбы |
| `hitRadius` | double | 1.8 | Радиус луж |
| `zoneTicks` / `damagePerTick` | int/double | 70 / 2.5 | Hazard-лужи |
| `fireSeconds` | int | 3 | Поджог |

Визуал полёта: `GunMineEntity` через `WfmIntegration.spawnVisualMine` (без детонации).

### `ghost_orbit_slam` — GhostOrbitSlamAbility

Камикадзе-призрак: подлёт сквозь блоки → орбита вокруг цели → врезание с knockback → `npc.kill()`.

Движение — сырой `setPosition` + `noPhysics` (без clip стен из dash). `cancelsOnTargetLost = false`.

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `approachSpeed` | double | 0.45 | Блоки/тик на подлёте |
| `radius` | double | 2.5 | Радиус орбиты |
| `orbitTicks` | int | 60 | Длительность кружения (~3 с) |
| `orbitSpeed` | double | 8.0 | Градусы/тик |
| `hoverOffset` | double | 1.0 | Высота над целью на орбите |
| `slamTicks` | int | 6 | Длительность врезания |
| `damage` | double | 14.0 | Урон удара |
| `knockback` / `knockbackY` | double | 2.4 / 0.55 | Отбрасывание |
| `hitRadius` | double | 1.8 | Радиус хита |

### `ghost_soul_bolt` — GhostSoulBoltAbility

Навесной soul-сгусток (дуга как `crimson_blob`): charge → полёт VFX → knockback при касании / приземлении. Без hazard-зоны. Авто-telegraph по `landRadius`. JS: `scripts/ghost/ghost_soul_bolt.js`.

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` / `activeTicks` | int | 24 / 14 | Charge + полёт |
| `arcHeight` | double | 5.0 | Высота навеса |
| `landRadius` | double | 2.2 | AoE / telegraph |
| `hitRadius` | double | 1.4 | Касание по пути |
| `damage` | double | 0.0 | Урон (0 = только knockback) |
| `knockback` / `knockbackY` | double | 2.2 / 0.5 | Отбрасывание |
| `blobParticles` / `landParticles` | string | soul… | Партиклы через `,` |
| `maxRange` | double | 24.0 | Дальность каста |

`cancelsOnTargetLost = false`.

### `vampire_whirl_slash` — VampireWhirlSlashAbility

Круговой удар вокруг кастера. Charge 8 тиков (круг telegraph) + yaw-spin BipedModel (~Жатва), затем AoE и `lifeStealPerHit` за каждую поражённую цель. `cancelsOnTargetLost = false`.

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` | int | 8 | Зарядка (0.4 с) |
| `damage` | double | 18.0 | Урон круга |
| `radius` | double | 4.5 | Радиус AoE / telegraph |
| `knockback` / `knockbackY` | double | 1.2 / 0.3 | Радиальное отбрасывание |
| `lifeStealPerHit` | double | 100.0 | Хилл кастера за каждую цель |

### `vampire_crimson_bats` — VampireCrimsonBatsAbility

Призыв 2 клонов (`Vampire Crimson Bat`). После спавна: 50 HP, size 3, walk speed 9, тег `vampire_crimson_bat`, owner UUID. Тычки мышей хилят босса через `VampireCrimsonBatHealHandler`. `cancelsOnTargetLost = false`.

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` | int | 8 | Зарядка |
| `radius` | double | 3.0 | Telegraph-круг у босса |
| `summonCount` | int | 2 | Число мышей |
| `summonRadius` | double | 2.5 | Радиус спавна |
| `maxSummonedNearBoss` | int | 2 | Потолок живых рядом |
| `cloneTab` / `cloneName` | int / string | 1 / `Vampire Crimson Bat` | Клон |
| `lifeStealPerHit` | double | 15.0 | Хилл босса за тычку мыши |

### `vampire_blood_ring` — VampireBloodRingAbility

Charge 8 тиков (ring telegraph), затем hazard-кольцо на 10 с следует за боссом. Урон тикает в annulus (внутри кольца безопасно). Дым + души. Босс после спавна свободен. `cancelsOnTargetLost = false`.

| Ключ | Тип | Дефолт | Описание |
|------|-----|--------|----------|
| `chargeTicks` | int | 8 | Зарядка (0.4 с) |
| `radius` | double | 7.0 | Внешний радиус |
| `innerRadius` | double | 4.5 | Внутренний радиус |
| `damage` | double | 3.0 | Урон тика зоны |
| `zoneTicks` | int | 200 | Lifetime (10 с) |
| `damageInterval` | int | 10 | Интервал урона |
| `zoneColor` | int | `0xC0180810` | Тёмный ARGB |

## Примеры скриптов

| Файл | Назначение |
|------|------------|
| `scripts/boss_dash_jump/boss_periodic_abilities.js` | Чередование dash/jump, фикс. params |
| `scripts/boss_dash_jump/boss_dash_jump.js` | То же + бонус урона при низком HP |
| `scripts/witch_hunter/witch_hunter_boss.js` | Охотник: strike/net/lunge/crossbow/fire_bomb + цепочки |
| `scripts/drachenfels/drachenfels_boss.js` | Тело+дух, Immortal Bond revive, фазы 1/2/bond |
| `scripts/utility/npc_shield_block.js` | Блок щитом: damaged → `shield_block` |
| `scripts/utility/crimson_blob.js` | Навес сгустка → слепая лужа |
| `scripts/otrodie/otrodie_boss.js` | Отродье: CD/forced chain + SpreadingFilth.trigger |
| `scripts/ghost/ghost_orbit_slam.js` | Призрак: агро → `ghost_orbit_slam` → смерть |
| `scripts/ghost/ghost_soul_bolt.js` | Летающий призрак: soul-болт раз в 10 с |
| `scripts/vampire/vampire_crimson_lord.js` | Кровавый лорд: whirl / bats / ring, one-way ярость |

После правки скрипта на NPC: `/script reload`.
