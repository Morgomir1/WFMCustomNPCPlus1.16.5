---
name: customnpc-attack-zones
description: Добавляет зоны атаки CustomNPC+ (Forge 1.16.5) — Telegraph (warning на charge) и Ability Zone (hazard с уроном) через TelegraphAPI/ZoneAPI и авто-телеграф AbilityAPI. Используй при telegraph, зоне атаки, warning circle/cone/line, hazard/trap зоне, telegraphColor, chargeTicks для читаемого dodge-window, или когда босс-абилка должна показывать AoE перед ударом.
---

# Зоны атаки CustomNPC+ (Telegraph / Zone)

> **Связанные skills:** `customnpc-js-scripting` — оркестраторы и tick SM. `customnpc-java-abilities` — `CnpcAbility` / `AbilityAPI`. Полный API: [reference.md](reference.md).

Две системы:

| | Telegraph | Ability Zone |
|--|-----------|--------------|
| Назначение | Warning на время charge | Persistent/trap hazard с уроном |
| Sync | `PacketTelegraphSpawn/Remove` | `EntityAbilityZone` |
| JS | `noppes.npcs.telegraph.TelegraphAPI` | `noppes.npcs.zone.ZoneAPI` |
| Длительность | = `durationTicks` / `chargeTicks` | = lifetime зоны |

**Клиентские пакеты/рендереры не импортировать в серверный код абилок** — только `TelegraphAPI` / `ZoneAPI`.

---

## Когда что использовать

1. **AbilityAPI-босс** (Java + тонкий JS) → авто-телеграф после `onStart`. Достаточно геометрии в params + достаточный `chargeTicks`.
2. **Чистый JS SM** → явный `TelegraphAPI` в `startCharge`, `remove` в `clearState`.
3. **Aura / burn / poison puddle** → `ZoneAPI.hazard*` (урон тикает сам; убрать дублирующий JS-урон).
4. **Одноразовый trap** → `ZoneAPI.trapCircle`.

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
- [ ] startCharge → spawn telegraph (duration = CHARGE_TICKS)
- [ ] followNpc, если зона у ног NPC
- [ ] clearState / burst → TelegraphAPI.remove(id)
- [ ] Aura: ZoneAPI + moveTo каждый тик; не дублировать damage в JS
```

```javascript
var TelegraphAPI = Java.type("noppes.npcs.telegraph.TelegraphAPI");
var ZoneAPI = Java.type("noppes.npcs.zone.ZoneAPI");

// charge warning
var tid = TelegraphAPI.circle(npc, npc.getX(), npc.getY(), npc.getZ(), RADIUS, CHARGE_TICKS, 0xC0FF3030);
TelegraphAPI.followNpc(tid, npc);
data.put("tg_id", String(tid));

// burst / aura
var zone = ZoneAPI.hazardCircle(npc, x, y, z, RADIUS, 60, 2.0, 20);
zone.setEffect("minecraft:poison", 60, 0);
zone.setColor(0xC0FF3030);
```

---

## Цвета и dodge-window

- Цвет ARGB: `0xAARRGGBB`. Дефолт warning — **красный** `0xC0FF3030`. Не ставить «тематический» зелёный без запроса.
- Telegraph живёт ровно `chargeTicks` / `durationTicks`. Короче ~10 тиков = зона почти нечитаема.
- Ориентиры: big AoE 28–36, cone/line 22–30, dash/step 14–18. Фаза 2 может быть чуть короче.

---

## Расширение Java (редко)

Новая форма / пакет — см. [reference.md](reference.md#java-layout). Пакеты только **append** в конец `Packets.register()`. Рендер — client-only (`TelegraphWorldRenderer`, `RenderAbilityZone`).

Эталон скрипта с длинным charge: `src/main/resources/scripts/drachenfels/drachenfels_boss.js`.  
JS SM с Zone: `empire_flagellant_pyre.js`, `skaven_plague_censer.js`, `empire_flagellant_martyr.js`.
