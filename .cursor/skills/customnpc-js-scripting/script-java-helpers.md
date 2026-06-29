# Java helpers для CNPC-скриптов (WFMCustomNPCPlus)

Когда выносить логику из Nashorn в Java и как вызывать из JS.

## Три уровня

| Уровень | Класс | Когда |
|---------|-------|-------|
| Утилиты | `noppes.npcs.script.ScriptDataUtil` | `getInt`/`getFloat`/флаги/кулдауны в `IData` |
| Утилиты | `noppes.npcs.script.ScriptEntityUtil` | distance, `faceTarget`, `swingMainHand`, `isStandingOver` |
| Домен | `noppes.npcs.script.vampire.*` | Механики vampire (трупы, пожирание) |
| Абилки | `noppes.npcs.abilities.AbilityAPI` | Движение, урон, фазы — tick каждый серверный тик |
| VFX | `noppes.npcs.abilities.AbilityVfx` | Партиклы крови, взрывы, дым |

**Правило:** повторяется в 3+ скриптах → утилита Java. Целая tick SM → `*Helper` или `CnpcAbility`.

## Bootstrap в JS

```javascript
var ScriptData = Java.type("noppes.npcs.script.ScriptDataUtil");
var CryptFeast = Java.type("noppes.npcs.script.vampire.CryptFeastHelper");
var CryptGhoulDeath = Java.type("noppes.npcs.script.vampire.CryptGhoulDeathHelper");
```

## ScriptDataUtil

```javascript
var data = npc.getStoreddata();
var stacks = ScriptData.getInt(data, "my_stacks");
ScriptData.setFlag(data, "charging", true);
if (ScriptData.isCooldownReady(data, "my_cd", world.getTotalTime())) { ... }
ScriptData.setCooldown(data, "my_cd", world.getTotalTime(), 80);
```

## Vampire helpers

### CryptGhoulDeathHelper

При смерти вурдалака — тег `crypt_ghoul` + запись трупа в `world.tempdata`.

```javascript
function died(e) {
    CryptGhoulDeath.onDeath(e.npc);
}
```

### CryptFeastHelper

Пожирание трупа ужасом: стоя над трупом, стаки баффов, decay 60 сек без боя.

Эталон: [`scripts/vampire/crypt_horror_corpse_feast.js`](../../../src/main/resources/scripts/vampire/crypt_horror_corpse_feast.js)

```javascript
function init(e) { CryptFeast.init(e.npc); }
function tick(e) { CryptFeast.tick(e.npc); }
function meleeAttack(e) { CryptFeast.onCombat(e.npc); }
```

Константы баланса — в `CryptFeastHelper.java` (не в JS).

## CryptCorpseRegistry (внутренний)

Общий реестр между ghoul и horror. Ключи: `crypt_ghoul_corpses`, `crypt_ghoul_eaten`. Не вызывать из JS напрямую — через helpers.

## После изменения Java

1. Пересобрать **WFMCustomNPCPlus**
2. `/script reload` или перезагрузка скрипта в GUI NPC
3. Эталонные `.js` из `src/main/resources/scripts/` — скопировать в NPC при необходимости

## См. также

- [`customnpc-java-abilities`](../customnpc-java-abilities/SKILL.md) — `CnpcAbility`, `AbilityAPI`
- [`architecture.md`](architecture.md) — tick state machine в JS (если helper не нужен)
