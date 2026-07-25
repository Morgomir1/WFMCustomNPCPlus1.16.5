# Telegraph / Zone — reference

## Telegraph (мод WFMTelegraph)

Ядро: `com.wfm.telegraph.*` в отдельном моде `wfmtelegraph`.  
CNPC wrapper для JS/AbilityAPI: `noppes.npcs.telegraph.TelegraphAPI` → делегирует в lib (`World`/`Entity`).

| Метод (wrapper, первый аргумент npc) | Аргументы |
|-------|-----------|
| `circle` | npc, x, y, z, radius, durationTicks, color |
| `circleFollow` | npc, x, y, z, radius, durationTicks, color — follow с первого пакета (Keeper) |
| `ring` | npc, x, y, z, outerR, innerR, durationTicks, color |
| `square` | npc, x, y, z, halfSize, durationTicks, color |
| `cone` | npc, x, y, z, yaw, length, halfAngleDeg, durationTicks, color |
| `line` | npc, x, y, z, yaw, length, width, durationTicks, color |
| `follow` / `followNpc` | id, entity/npc |
| `remove` / `removeNear` | id [, npc] |

Константы (из lib): `DEFAULT_COLOR = 0x80FF3030`, `DEFAULT_WARNING = 0xC0FF0000`.

Формы: `CIRCLE`, `RING`, `LINE`, `CONE`, `SQUARE`.

### Ground Y (обязательно для зон на земле)

При отрисовке/спавне игнорить растения и «пустые» коллизии — только цельные блоки:

- критерий: `state.getMaterial().isSolid() && !state.getCollisionShape(world, pos).isEmpty()`
- примеры **игнора**: tall grass, flowers, ferns, snow layer, saplings
- примеры **опоры**: dirt, grass_block, stone, planks, slabs (у slab — верхняя грань коллизии)

Lib: `TelegraphInstance.findGroundY` (каждый тик follow).  
CNPC wrapper: `TelegraphAPI.resolveGroundY` / `circleFollow` уже резолвит solid Y.

Sync/render/tick — внутри `WFMTelegraph` (свой `SimpleChannel`, `TelegraphWorldRenderer`).  
**Не** регистрировать telegraph-пакеты в `Packets.register()` CNPC.

Dependency CNPC:

```gradle
implementation fg.deobf('lib:wfmtelegraph:1.0.0-1.16.5')
```

Jar должен быть **Java 8** (class 52). Исходники lib: `c:\Waha\Waha1.16.5\WFMTelegraph\`.

Native WFM-боссы зовут `com.wfm.telegraph.TelegraphAPI` напрямую — skill `wfm-attack-telegraphs`.

## ZoneAPI (`noppes.npcs.zone.ZoneAPI`)

| Метод | Назначение |
|-------|------------|
| `hazardCircle` | npc, x,y,z, radius, duration, damage, damageInterval |
| `hazardRing` | + outer/inner |
| `hazardSquare` | halfSize |
| `trapCircle` | один trigger при входе |
| `remove` | entity |

На entity после spawn:

- `setEffect("minecraft:poison", duration, amplifier)`
- `setColor(0xAARRGGBB)`, `setKnockback(f)`, `setDamage(f)`
- `setLifetimeTicks(n)`, `setDamageInterval(n)`
- `moveTo(x, y, z, yaw, pitch)` — follow aura
- `setVisible` / groundFill / border через data

Фильтр целей: `AbilityCombatHelper.isHostileToBoss` (не бьёт союзников/себя).

Entity: `customnpcs:ability_zone` → `EntityAbilityZone` + `RenderAbilityZone`.

## Ability params (авто-телеграф)

| Ключ | Роль |
|------|------|
| `chargeTicks` | Длительность telegraph |
| `telegraph` | `0` = выключить авто |
| `telegraphColor` | ARGB |
| `telegraphForward` | смещение circle вперёд по yaw |
| `radius` / `auraRadius` / `landRadius` | circle |
| `coneHalfAngle` + radius | cone |
| `distance` (+ optional radius как width) | line |

Хелпер: `AbilityTelegraph.spawnFromCharge` / `clear` из `AbilityRunner`.  
Поле: `ActiveAbility.telegraphId`.  
Always-allowed keys в `AbilityParams`: `telegraph`, `telegraphColor`, `telegraphForward`.

## Java layout

```
# WFMTelegraph (отдельный мод)
com.wfm.telegraph.*
com.wfm.telegraph.network.*
com.wfm.telegraph.client.*

# CNPC
noppes.npcs.telegraph.TelegraphAPI   — thin wrapper → lib
noppes.npcs.abilities.AbilityTelegraph
noppes.npcs.zone.ZoneAPI
noppes.npcs.entity.EntityAbilityZone
noppes.npcs.client.renderer.RenderAbilityZone
```

Регистрация entity zone: `CustomEntities` (`ability_zone`).  
Рендер zone: `ClientProxy` → `RenderAbilityZone` (telegraph renderer регистрирует сам `wfmtelegraph`).
