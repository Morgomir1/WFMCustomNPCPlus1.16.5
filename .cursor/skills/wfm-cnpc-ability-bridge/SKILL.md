---
name: wfm-cnpc-ability-bridge
description: Пробрасывает WFM-механики (сети, пули, звуки, сущности) в Java-абилки CustomNPC+ через reflection-мост без compile-зависимости CNPC→WFM. Используй когда абилка CNPC должна вызывать WFM API (DwarfRangerNetEntity, BulletEntity, WFMGunpowderGunItem), добавлять CustomNpc*Helper в WFM1.16.5, или расширять WfmIntegration.
---

# Мост WFM ↔ CustomNPC+ для Java-абилок

> **Связанные skills:** `customnpc-java-abilities` — `CnpcAbility`, регистрация. `customnpc-js-scripting` — JS-оркестратор.

## Зачем мост

CNPC-абилки живут в **WFMCustomNPCPlus1.16.5** (`noppes.npcs.abilities`). WFM-сущности и предметы — в **WFM1.16.5**. Прямой import WFM в CNPC ломает сборку без жёсткой зависимости.

**Паттерн:** публичный static-хелпер в WFM → вызов через reflection из `WfmIntegration` → `*Ability` вызывает `WfmIntegration` + fallback без WFM.

```
CnpcAbility (CNPC)
    → WfmIntegration.* (reflection, lazy init)
        → wfm.common.integration.customnpc.CustomNpc*Helper (WFM)
            → DwarfRangerNetEntity / BulletEntity / ...
```

## Когда нужен мост

| Нужно | Без моста | С мостом |
|-------|-----------|----------|
| WFM-снаряд с hit-логикой | `shootItem(minecraft:lead)` | `CustomNpcNetHelper` |
| Пуля + звук `gun_launch` | `shootItem(пистолет)` | `CustomNpcGunHelper` |
| Эффект `HUNTER_NET`, GeckoLib VFX | `applyPotionNearby` | сущность WFM |

**Не нужен мост:** dash, AoE, potion через `AbilityCombatHelper`, чистый CNPC `shootItem` для декора.

## Чеклист нового моста

```
WFM1.16.5:
- [ ] wfm/common/integration/customnpc/CustomNpcXxxHelper.java
- [ ] public static методы; только LivingEntity/World/примитивы
- [ ] isClientSide guard; server-only логика

WFMCustomNPCPlus1.16.5:
- [ ] WfmIntegration — Class.forName + getMethod + invoke
- [ ] isWfmXxxAvailable() для проверки
- [ ] *Ability — вызов моста + fallback в onEnd/onCancel

Документация:
- [ ] reference.md этого skill — сигнатуры
- [ ] customnpc-java-abilities/reference.md — параметры абилки
```

## WFM: хелпер

Пакет: `wfm.common.integration.customnpc`.

```java
public final class CustomNpcXxxHelper {
    private CustomNpcXxxHelper() {}

    /** @return true если действие выполнено */
    public static boolean doThing(
            final LivingEntity shooter,
            final LivingEntity target,
            final float param) {
        if (shooter == null || target == null || shooter.level.isClientSide) {
            return false;
        }
        // переиспользовать существующую WFM-логику (NPCEntity, Item, Entity)
        return true;
    }
}
```

Правила:
- **Не** тянуть `noppes.npcs.*` в WFM.
- Переиспользовать публичные методы WFM (`performMobShootAtTarget`, конструкторы entity).
- Временная экипировка — `ConcurrentHashMap<UUID, SavedState>` + restore в парном методе.
- Звуки — `LOTRSoundEvents`, не vanilla-substitute.

## CNPC: WfmIntegration

Файл: `noppes.npcs.abilities.integration.WfmIntegration`.

```java
private static final String HELPER_CLASS =
        "wfm.common.integration.customnpc.CustomNpcXxxHelper";
private static Boolean available;
private static Method doThingMethod;

public static boolean doThing(ICustomNpc npc, IEntityLiving target, float p) {
    ensureXxxInitialized();
    if (!Boolean.TRUE.equals(available)) return false;
    LivingEntity shooter = toLivingEntity(npc);
    LivingEntity tgt = toLivingEntity(target);
    if (shooter == null || tgt == null) return false;
    try {
        Object r = doThingMethod.invoke(null, shooter, tgt, p);
        return r instanceof Boolean && (Boolean) r;
    } catch (Exception ignored) {
        return false;
    }
}

private static void ensureXxxInitialized() {
    if (available != null) return;
    try {
        Class<?> c = Class.forName(HELPER_CLASS);
        doThingMethod = c.getMethod("doThing",
                LivingEntity.class, LivingEntity.class, float.class);
        available = true;
    } catch (Exception e) {
        available = false;
    }
}
```

`toLivingEntity` — через `entity.getMCEntity()` → `LivingEntity`.

Отдельный `ensure*Initialized` на каждый хелпер (net/gun могут грузиться независимо).

## CNPC: абилка

```java
@Override
public boolean onStart(ActiveAbility active, AbilityContext ctx) {
    WfmIntegration.equipForShot(ctx.npc, itemId); // если нужно
    return true;
}

private TickResult tickActive(...) {
    if (WfmIntegration.performShot(ctx.npc, ctx.target, ...)) {
        return TickResult.FINISHED;
    }
    return fallbackWithoutWfm(ctx);
}

@Override
public void onEnd(...) { WfmIntegration.restoreEquipment(ctx.npc); }
@Override
public void onCancel(...) { WfmIntegration.restoreEquipment(ctx.npc); }
```

- **Fallback** обязателен: CNPC без WFM в dev или тестовый стенд.
- Параметр `projectileItem` в defaults — id **оружия/предмета WFM**, не снаряда (пуля создаётся внутри хелпера).

## Эталоны в репозитории

| Мост | WFM | CNPC ability |
|------|-----|--------------|
| Сеть | `CustomNpcNetHelper` | `NetThrowAbility` |
| Пистолет | `CustomNpcGunHelper` | `PistolShotAbility` |
| Оркестратор | — | `scripts/witch_hunter/witch_hunter_boss.js` |

## Антипаттерны

- Import `wfm.*` в `WFMCustomNPCPlus1.16.5` — только reflection.
- Менять `WFMCustomNPCPlus` без запроса; мост WFM→CNPC — хелперы в **WFM1.16.5**, вызовы в CNPC.
- Дублировать WFM hit-логику в `AbilityCombatHelper` — лучше entity WFM.
- `shootItem(wfm:empire_pistol)` как снаряд — летит модель пистолета, не пуля.
- Забыть `restoreEquipment` в `onCancel` (смерть NPC, `AbilityAPI.cancel`).

## Дополнительно

Сигнатуры, карта файлов, шаблон нового хелпера: [reference.md](reference.md)
