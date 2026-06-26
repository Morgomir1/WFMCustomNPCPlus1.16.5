# Переключение режимов CNPC (OnAttack / «Если найдёт врага»)

GUI: **AI → On Attack** (`npc.getAi().setRetaliateType(...)`).

## Константы

```javascript
// CNPC OnAttack: 0=Мстить, 1=Паника, 2=Отступать, 3=Ничего
var RETALIATE_REVENGE = 0;
var RETALIATE_PANIC = 1;
var RETALIATE_RETREAT = 2;
var RETALIATE_NONE = 3;
```

| Значение | GUI (RU) | Поведение |
|----------|----------|-----------|
| `0` | Мстить | преследует и атакует врага |
| `1` | Паника | хаотичное бегство |
| `2` | Отступать | отходит от врага |
| `3` | Ничего | не реагирует на врага (стоит на месте / не бежит в мили) |

Дополнительно: `ai.setWalkingSpeed(n)` — скорость при отступлении выше базовой.

## Минимальный каркас

```javascript
var MODE_KEY = "my_mode";           // "normal" | "shoot" | "retreat"
var RETREAT_END_KEY = "my_retreat_end";
var BASE_SPEED_KEY = "my_base_speed";
var RETREAT_TICKS = 160;            // 8 сек
var RETREAT_SPEED = 6;

function storeBaseSpeed(data, ai) {
    if (!data.has(BASE_SPEED_KEY)) {
        data.put(BASE_SPEED_KEY, String(ai.getWalkingSpeed()));
    }
}

function applyNormalMode(npc, data) {
    data.put(MODE_KEY, "normal");
    data.put(RETREAT_END_KEY, "0");
    var ai = npc.getAi();
    ai.setRetaliateType(RETALIATE_REVENGE);
    ai.setWalkingSpeed(getInt(data, BASE_SPEED_KEY));
}

function applyShootingMode(npc, data, ai) {
    data.put(MODE_KEY, "shoot");
    ai.setRetaliateType(RETALIATE_NONE);
    ai.setWalkingSpeed(getInt(data, BASE_SPEED_KEY));
}

function beginRetreat(npc, data, ai, now) {
    data.put(MODE_KEY, "retreat");
    data.put(RETREAT_END_KEY, String(now + RETREAT_TICKS));
    ai.setRetaliateType(RETALIATE_RETREAT);
    ai.setWalkingSpeed(RETREAT_SPEED);
}

function isRetreating(data, now) {
    return String(data.get(MODE_KEY)) == "retreat" && now < getInt(data, RETREAT_END_KEY);
}
```

## Типовой цикл (стрельба → отступление → снова бой)

1. **Во время каста / абилки** — `RETALIATE_NONE` (не убегает в мили, не преследует).
2. **После окончания каста** — `beginRetreat(npc, data, ai, now)` на `RETREAT_TICKS`.
3. **В `tick`** — пока `isRetreating`, держать `RETALIATE_RETREAT`; по истечении — `applyNormalMode`.
4. **В `timer` / каст** — не начинать новый залп, пока `isRetreating(data, now)`.

С Java-абилкой (`AbilityAPI`):

```javascript
var WAS_SHOOTING_KEY = "was_shooting";

function tick(e) {
    var npc = e.npc;
    var data = npc.getStoreddata();
    var now = npc.getWorld().getTotalTime();
    var ai = npc.getAi();

    if (AbilityAPI.isBusy(npc) && AbilityAPI.getActiveId(npc) == "my_ability") {
        applyShootingMode(npc, data, ai);
        data.put(WAS_SHOOTING_KEY, "1");
        return;
    }
    if (String(data.get(WAS_SHOOTING_KEY)) == "1") {
        data.put(WAS_SHOOTING_KEY, "0");
        beginRetreat(npc, data, ai, now);
        return;
    }
    if (isRetreating(data, now)) {
        ai.setRetaliateType(RETALIATE_RETREAT);
        ai.setWalkingSpeed(RETREAT_SPEED);
        return;
    }
    if (String(data.get(MODE_KEY)) == "retreat") {
        applyNormalMode(npc, data);
    }
}
```

## Референсные скрипты

| Сценарий | Файл |
|----------|------|
| Мщение ↔ отступление по таймеру, урон | `scripts/skaven/skaven_eshin_smoke_stab.js` |
| Залп → 8 сек отступление → снова залп | `scripts/skaven/skaven_engineer_ratling_gun.js` |

## Практики

- Сохраняй **базовую скорость** в `storeddata` при `init`, восстанавливай в `applyNormalMode`.
- Оборачивай `setRetaliateType` / `setWalkingSpeed` в `try/catch` — на кастомных моделях API иногда падает.
- Кулдаун отступления — **абсолютная метка** `world.getTotalTime() + RETREAT_TICKS`, не декремент в тике.
- При `targetLost` / отмене абилки — тоже вызывай `beginRetreat`, если каст прервался во время стрельбы.
