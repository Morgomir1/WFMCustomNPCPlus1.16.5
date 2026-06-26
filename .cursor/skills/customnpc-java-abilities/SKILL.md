---
name: customnpc-java-abilities
description: Добавляет серверные боевые абилки CustomNPC+ (Forge 1.16.5) через Java — CnpcAbility, AbilityRegistry, charge/active фазы, AbilityCombatHelper, AbilityAPI для JS. Используй при создании dash/slam/AoE-способностей NPC в noppes.npcs.abilities, регистрации нового ability id, или когда механика слишком тяжёлая для Nashorn.
---

# Java-абилки CustomNPC+

> **Связанные skills:** `customnpc-js-scripting` — тонкий JS-оркестратор (`AbilityAPI.start`). `nashorn-customnpc-scripting` — Nashorn bootstrap.

Код абилок — **только в `WFMCustomNPCPlus1.16.5`**, пакет `noppes.npcs.abilities`.

## Когда Java, а когда JS

| Java (`CnpcAbility`) | JS (tick state machine) |
|----------------------|-------------------------|
| Движение NPC, урон, knockback, VFX по тикам | Простой VFX-пролёт без телепорта |
| Несколько фаз, hit-list, геометрия | Разовый эффект, редкая логика |
| Переиспользование между боссами | Уникальное поведение одного NPC |

**Паттерн WFM:** механика в Java, JS решает **когда** кастовать и передаёт **params**.

## Быстрый чеклист новой абилки

```
- [ ] impl/MyAbility.java implements CnpcAbility
- [ ] Константа ID + getId()
- [ ] AbilityParamKeys — новые ключи (если нужны)
- [ ] AbilityDefaults.myAbility() — дефолты
- [ ] AbilityRegistry.register(new MyAbility())
- [ ] JS-оркестратор: AbilityAPI.start(npc, id, target, params)
- [ ] reference.md — параметры и id (если публичная абилка)
```

## Архитектура

```
JS timer/tick → AbilityAPI.start
    → AbilityRunner (Map UUID → ActiveAbility)
    → AbilityTickHandler (ServerTickEvent END) → tick() каждый серверный тик
    → CnpcAbility.onStart → tick (CONTINUE/FINISHED) → onEnd / onCancel
```

- Один активный ability на NPC (`isBusy` блокирует повторный старт).
- Только **сервер** — клиентские пакеты в ability-коде не использовать.
- `onStart` возвращает `false` → каст не начинается (лог в `AbilityRunner`).

## Каркас класса

Эталоны: `impl/DashAbility.java`, `impl/JumpSlamAbility.java`.

```java
public final class MyAbility implements CnpcAbility {
    public static final String ID = "my_ability";

    @Override public String getId() { return ID; }

    @Override public boolean requiresTarget() { return true; }

    // false — абилка доигрывает, даже если цель умерла (рывок, slam)
    @Override public boolean cancelsOnTargetLost() { return false; }

    @Override public Map<String, Object> defaultParams() {
        return AbilityDefaults.myAbility();
    }

    @Override public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.DAMAGE);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 10);
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickActive(active, ctx);
        }
        return TickResult.FINISHED;
    }

    @Override public void onEnd(final ActiveAbility active, final AbilityContext ctx) {}
    @Override public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
```

### Фазы charge → active

1. **CHARGE** — NPC стоит, VFX/звук, `stopNavigation` каждый тик.
2. `ticksLeft` до 0 → `PHASE_ACTIVE`, сброс `hitUuids`, burst VFX.
3. **ACTIVE** — движение/урон; `ticksLeft--`; при 0 → `TickResult.FINISHED`.
4. `onCancel` — очистка навигации; вызывается при смерти NPC, таймауте (600 тиков), отмене.

### Состояние на `ActiveAbility`

| Поле | Назначение |
|------|------------|
| `sx,sy,sz` / `ex,ey,ez` | Старт и конец траектории |
| `yaw` | Направление |
| `hitUuids` | Одно попадание на цель за фазу |
| `phase`, `ticksLeft` | Фазовая машина |

## Регистрация

```java
// AbilityRegistry.java static {}
register(new MyAbility());

// AbilityDefaults.java
public static Map<String, Object> myAbility() { ... }

// AbilityParamKeys.java — строковые ключи для JSON/JS
public static final String MY_PARAM = "myParam";
```

## Хелперы (не дублировать)

| Класс | Использование |
|-------|---------------|
| `AbilityCombatHelper` | `stopNavigation`, `computeDashEndPoints`, `computeEndPoints` (jump к цели), `findGroundY`, `damageNearby`, `isHostileToBoss` |
| `AbilityVfx` | `spawnChargeParticles`, `spawnStartBurst`, `spawnDashTrail`, `spawnLandBurst`, `spawnHitParticle` |
| `AbilityParams` | `merge(defaults, overrides, knownKeys)` — неизвестные ключи логируются и игнорируются |

**Рывок (`distance`):** полная дистанция в направлении цели, **не** обрезать до дистанции до цели. Y на каждом тике — `findGroundY(world, cx, cz, sy)`.

**Урон:** `damageNearby(active, ctx, x, y, z, radius, damage, dirX, dirZ, knockback, knockbackY, useFixedDir)` — `useFixedDir=true` для dash, `false` для радиального AoE.

## JS-оркестратор (минимум)

Пример: `src/main/resources/scripts/boss_dash_jump/boss_periodic_abilities.js`.

```javascript
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");

function timer(event) {
    var npc = event.npc;
    if (!npc.isAlive()) { AbilityAPI.cancel(npc); return; }
    if (AbilityAPI.isBusy(npc)) return;

    var target = npc.getAttackTarget();
    if (target == null || !target.isAlive()) return;

    AbilityAPI.start(npc, "my_ability", target, AbilityAPI.params(
        "damage", 12.0,
        "chargeTicks", 10,
        "activeTicks", 8
    ));
}
```

Обязательно: `targetLost` / `died` → `AbilityAPI.cancel(npc)`.

Кулдауны и выбор скилла — в JS `storeddata`, **не** в Java.

## Антипаттерны

- Логика каста (шанс, дистанция, LOS) в Java — держать в JS.
- Два ability одновременно на одном NPC.
- Клиентский код в `tick`/`onStart`.
- Магические строки параметров — только через `AbilityParamKeys`.
- Дублировать урон/VFX вместо `AbilityCombatHelper` / `AbilityVfx`.

## Дополнительно

- Полный API, параметры `dash`/`jump_slam`, карта файлов: [reference.md](reference.md)
- JS-детали и `abilities-reference.md`: skill `customnpc-js-scripting`
