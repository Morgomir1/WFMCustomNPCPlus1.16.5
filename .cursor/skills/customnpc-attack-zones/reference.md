# Telegraph / Zone — reference

## TelegraphAPI (`noppes.npcs.telegraph.TelegraphAPI`)

| Метод | Аргументы |
|-------|-----------|
| `circle` | npc, x, y, z, radius, durationTicks, color |
| `ring` | npc, x, y, z, outerR, innerR, durationTicks, color |
| `square` | npc, x, y, z, halfSize, durationTicks, color |
| `cone` | npc, x, y, z, yaw, length, halfAngleDeg, durationTicks, color |
| `line` | npc, x, y, z, yaw, length, width, durationTicks, color |
| `follow` / `followNpc` | id, entity/npc |
| `remove` / `removeNear` | id [, npc] |

Константы: `DEFAULT_COLOR = 0x80FF3030`, `DEFAULT_WARNING = 0xC0FF0000`.

Формы: `CIRCLE`, `RING`, `LINE`, `CONE`, `SQUARE`.

Сервер: `TelegraphServer` + tick в `AbilityTickHandler`.  
Клиент: `ClientTelegraphManager` + `TelegraphWorldRenderer` (`RenderWorldLastEvent`).

Пакеты (append only): `PacketTelegraphSpawn`, `PacketTelegraphRemove`.

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
noppes.npcs.telegraph.*          — server + API
noppes.npcs.zone.ZoneAPI
noppes.npcs.entity.EntityAbilityZone
noppes.npcs.abilities.AbilityTelegraph
noppes.npcs.packets.client.PacketTelegraph*
noppes.npcs.client.telegraph.*   — client only
noppes.npcs.client.renderer.RenderAbilityZone
```

Регистрация entity: `CustomEntities` (`ability_zone`).  
Рендер + event bus: `ClientProxy`.
