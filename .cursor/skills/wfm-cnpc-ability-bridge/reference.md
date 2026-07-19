# Справочник: мост WFM ↔ CNPC abilities

## Карта файлов

```
WFM1.16.5/
└── src/main/java/wfm/common/integration/customnpc/
    ├── CustomNpcNetHelper.java    # DwarfRangerNetEntity
    └── CustomNpcGunHelper.java    # BulletEntity + equip pistol

WFMCustomNPCPlus1.16.5/
└── src/main/java/noppes/npcs/abilities/
    ├── integration/WfmIntegration.java
    └── impl/
        ├── NetThrowAbility.java
        └── PistolShotAbility.java
```

## WfmIntegration API

| Метод | WFM-хелпер | Назначение |
|-------|------------|------------|
| `isWfmNetAvailable()` | `CustomNpcNetHelper` | WFM в classpath |
| `throwDwarfRangerNet(npc, target, inaccuracy)` | `throwDwarfRangerNet` | Бросок сети |
| `isWfmGunAvailable()` | `CustomNpcGunHelper` | WFM в classpath |
| `equipPistolForShot(npc, gunItemId)` | `equipPistolForShot` | Пистолет в руке на charge |
| `restorePistolEquipment(npc)` | `restoreEquipment` | Вернуть руки |
| `performPistolShot(npc, target, gunItemId, inaccuracy, damage)` | `performPistolShot` | BulletEntity + GUN_LAUNCH |

Все методы — **server-only**; на клиенте возвращают `false` / no-op.

## CustomNpcNetHelper

```java
public static boolean throwDwarfRangerNet(
        LivingEntity thrower,
        LivingEntity target,
        float inaccuracy)

public static int ensnareAroundPoint(
        LivingEntity source,
        double x, double y, double z,
        double radius,
        int durationTicks)
```

- `throwDwarfRangerNet` — летящая сеть в одну цель.
- `ensnareAroundPoint` — мгновенно опутывает всех не-союзников в круге (`HUNTER_NET` + locked `DwarfRangerNetEntity`).
- Звук: `LOTRSoundEvents.ENSNARE_MISSIKE` / `ENSNARE_TARGET`.

## CustomNpcGunHelper

```java
public static boolean equipPistolForShot(LivingEntity entity, String gunItemId)
public static void restoreEquipment(LivingEntity entity)
public static boolean performPistolShot(
        LivingEntity shooter,
        LivingEntity target,
        String gunItemId,
        float inaccuracy,
        float damageOverride)
```

**Экипировка:**
- Пистолет уже в руке → не менять.
- `WFMGunpowderGunItem.isPistol()` → оффхенд.
- Иначе → майнхенд.
- Временная выдача сохраняется в `SAVED_EQUIPMENT` по UUID.

**Выстрел:**
- Берёт пистолет из рук shooter (или `wfm:empire_pistol` default).
- `BulletItem.createArrow` → `BulletEntity`.
- `damageOverride > 0` → и vs players, и vs mobs.
- Звук: `gun.getLaunchSound()` → `item.gunpowder_gun_launch`.
- Частицы: `ParticleTypes.POOF`.

**gunItemId:** `"wfm:empire_pistol"` или `"empire_pistol"`.

## Шаблон нового хелпера

### 1. WFM

```java
package wfm.common.integration.customnpc;

public final class CustomNpcFooHelper {
    public static boolean doFoo(LivingEntity a, LivingEntity b, float x) {
        if (a == null || b == null || a.level.isClientSide) return false;
        // ...
        return true;
    }
}
```

Искать в WFM готовую логику:
- `NPCEntity.npcArrowAttack` / `WFMGunpowderGunItem.performMobShootAtTarget`
- `DwarfRangerNetItem.releaseUsing` / entity constructors
- `LOTRSoundEvents`, `LOTREntities`

### 2. WfmIntegration

```java
private static final String FOO_HELPER = "wfm.common.integration.customnpc.CustomNpcFooHelper";
// ensureFooInitialized(), isWfmFooAvailable(), doFoo(...)
```

Сигнатура `getMethod` **точно** совпадает с public static в хелпере.

### 3. Ability

- `onStart` — equip/aim prep
- `tick` active — `WfmIntegration.doFoo(...)`; при `false` → fallback
- `onEnd` / `onCancel` — cleanup

### 4. AbilityDefaults

Параметры абилки не дублируют WFM JSON — только то, что JS переопределяет (`damage`, `accuracy`, id предмета).

## Fallback без WFM

| Абилка | Fallback |
|--------|----------|
| `net_throw` | `shootItem(lead)` + `applyPotionNearby` Slowness |
| `pistol_shot` | `shootItem(iron_nugget)` + `EntityProjectile.damage` |

Fallback должен оставаться играбельным, но визуально проще.

## Сборка и тест

1. Собрать **WFM1.16.5** и **WFMCustomNPCPlus1.16.5**.
2. Оба мода в `mods/` — иначе только fallback.
3. NPC: скрипт с `AbilityAPI.start`; `/script reload`.
4. Проверить: сущность WFM в мире, звук WFM, эффект WFM (не vanilla).

## Расширение списка хелперов

При добавлении третьего хелпера можно вынести общее в `WfmIntegration`:

```java
private static Method resolve(String className, String method, Class<?>... params) { ... }
```

Пока 2 хелпера — отдельные `ensure*Initialized` читаемее.
