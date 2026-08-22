---
name: customnpc-attack-zones
description: Добавляет зоны атаки CustomNPC+ (Forge 1.16.5) — Telegraph (warning на charge) через мод WFMTelegraph и Ability Zone (hazard с уроном) через ZoneAPI. Используй при telegraph, зоне атаки, warning circle/cone/line, hazard/trap зоне, telegraphColor, chargeTicks для читаемого dodge-window, или когда босс-абилка должна показывать AoE перед ударом.
---

# Зоны атаки CustomNPC+ (Telegraph / Zone)

> **Связанные skills:** `customnpc-js-scripting` — оркестраторы и tick SM. `customnpc-java-abilities` — `CnpcAbility` / `AbilityAPI`. WFM native-боссы: `wfm-attack-telegraphs`. Полный API: [reference.md](reference.md).

Две системы:

| | Telegraph | Ability Zone |
|--|-----------|--------------|
| Назначение | Warning на время charge | Persistent/trap hazard с уроном |
| Ядро | мод **`wfmtelegraph`** (`com.wfm.telegraph.TelegraphAPI`) | `EntityAbilityZone` в CNPC |
| JS | `noppes.npcs.telegraph.TelegraphAPI` (thin wrapper) | `noppes.npcs.zone.ZoneAPI` |
| Длительность | = `durationTicks` / `chargeTicks` | = lifetime зоны |

**Клиентские пакеты/рендереры не импортировать в серверный код абилок** — только wrapper `TelegraphAPI` / `ZoneAPI`.

Telegraph **не** живёт внутри CNPC: sync/render/tick — в `WFMTelegraph`. CNPC держит wrapper для JS и `AbilityTelegraph`. Runtime: jar `wfmtelegraph` в `mods/` + dependency в `build.gradle` / `mods.toml`.

---

## Когда что использовать

1. **AbilityAPI-босс** (Java + тонкий JS) → авто-телеграф после `onStart`. Достаточно геометрии в params + достаточный `chargeTicks`.
2. **Чистый JS SM** → явный `TelegraphAPI` в `startCharge`, `remove` в `clearState`.
3. **Aura / burn / poison puddle** → `ZoneAPI.hazard*` (урон тикает сам через `dealPureDamage`; убрать дублирующий JS-урон). Для огня: `zone.setFireSeconds(n)`.
4. **Одноразовый trap** → `ZoneAPI.trapCircle`.

Не делать отдельный server-tick DoT поверх `EntityAbilityZone` — зона уже бьёт чистым MAGIC с `ignoreIframes`.

---

## Чеклист: AbilityAPI

```
- [ ] В params есть геометрия: radius / landRadius / coneHalfAngle / distance
- [ ] chargeTicks ≥ ~18–36 (иначе зона мелькает; ~1–1.8 с для читаемого dodge)
- [ ] telegraphColor только если нужен не-дефолт (дефолт красный 0xC0FF3030)
- [ ] Отключить авто: "telegraph", 0
- [ ] Slam/impact: onStart пишет active.ex/ey/ez или telegraphForward
```

Авто-выбор формы (`AbilityTelegraph` после `onStart`):

1. `coneHalfAngle` → cone  
2. `landRadius` → circle в точке ленда  
3. `distance` + `radius` → line (+ circle в impact)  
4. только `distance` → line  
5. `radius` / `auraRadius` → circle  

```javascript
AbilityAPI.start(npc, "drachenfels_poison_feast", target, AbilityAPI.params(
    "telegraphColor", 0xC0FF3030,
    "radius", 5.0,
    "chargeTicks", 36,
    "damage", 14.0
));
```

---

## Чеклист: JS state machine

```
- [ ] Bootstrap TelegraphAPI / ZoneAPI
- [ ] startCharge → spawn telegraph (duration = CHARGE_TICKS); для бегущего NPC — `circleFollow`
- [ ] Y только над solid+collision (траву/цветы игнорировать) — см. секцию ниже
- [ ] clearState / burst → TelegraphAPI.remove(id)
- [ ] Aura: ZoneAPI + moveTo каждый тик; не дублировать damage в JS
```

```javascript
var TelegraphAPI = Java.type("noppes.npcs.telegraph.TelegraphAPI");
var ZoneAPI = Java.type("noppes.npcs.zone.ZoneAPI");

// charge warning (Keeper: один circleFollow на весь charge)
var tid = TelegraphAPI.circleFollow(npc, npc.getX(), npc.getY(), npc.getZ(), RADIUS, CHARGE_TICKS, TelegraphAPI.DEFAULT_COLOR);
data.put("tg_id", String(tid));

// burst / aura
var zone = ZoneAPI.hazardCircle(npc, x, y, z, RADIUS, 60, 2.0, 20);
zone.setEffect("minecraft:poison", 60, 0);
zone.setColor(0xC0FF3030);
```

---

## Y / отрисовка на земле (игнор травы)

При спавне и follow **любой** ground-зоны (`circle` / `cone` / `line` / `ring` / `square`):

- **Нельзя** ставить Y над травой, цветами, кустами, снежным слоем и прочим non-solid.
- Искать вниз первый блок с `material.isSolid()` **и** непустым `getCollisionShape`.
- Y зоны = `solidBlockY + 1.05` (чуть над верхней гранью).
- В lib это делает `TelegraphInstance.findGroundY` (follow каждый тик) и CNPC `TelegraphAPI.resolveGroundY` / `circleFollow`.

| Неверно | Верно |
|---------|--------|
| Y над tall grass / цветами | Y над dirt / stone / grass_block под ними |

```
- [ ] spawn / circleFollow — Y через solid ground (не сырой getY() в траве)
- [ ] follow — lib сам поджимает Y через findGroundY (solid only)
```

---

## Цвета и dodge-window

- Цвет ARGB: `0xAARRGGBB`. Дефолт warning — **красный** `0xC0FF3030`. Не ставить «тематический» зелёный без запроса.
- Telegraph живёт ровно `chargeTicks` / `durationTicks`. Короче ~10 тиков = зона почти нечитаема.
- Ориентиры: big AoE 28–36, cone/line 22–30, dash/step 14–18. Фаза 2 может быть чуть короче.

---

## Расширение Java (редко)

Новая форма / пакет telegraph — править **только** мод `WFMTelegraph`, не CNPC packets.  
Ability Zone / entity — см. [reference.md](reference.md#java-layout).  
Рендер Zone — client-only (`RenderAbilityZone`).

Эталон скрипта с длинным charge: `src/main/resources/scripts/drachenfels/drachenfels_boss.js`.  
JS SM с Zone: `empire_flagellant_pyre.js`, `skaven_plague_censer.js`, `empire_flagellant_martyr.js`.
