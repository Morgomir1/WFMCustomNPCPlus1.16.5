# lockable_door.js (AnnikenYT)

Скрипт для **блока** (не NPC!) — запираемая дверь, открывающаяся ключом.

```javascript
//Lockable Door Script by AnnikenYT

var key_name = "Anniken's key"
var door_name = "Door"
var locked = true;

// DONT CHANGE!
function init(event) {
 event.block.setBlockModel("minecraft:iron_door");
}

function interact(event) {
 //Lock
 if (!locked) {
   if (event.player.getMainhandItem().getDisplayName() == key_name) {
     locked = true
     event.block.setBlockModel("minecraft:iron_door");
     event.player.message("<" + door_name + "> This door is now locked");
     event.player.playSound("minecraft:block.enchantment_table.use", 1, 1);
     event.setCanceled(true);
   }
 }
 //Unlock
 else {
   if (event.player.getMainhandItem().getDisplayName() == key_name) {
     locked = false
     event.block.setBlockModel("minecraft:wooden_door");
     event.player.message("<" + door_name + "> This door is now unlocked");
     event.player.playSound("minecraft:block.enchantment_table.use", 1, 1);
     event.setCanceled(true);
   }
   //Tell player that door is locked
   if (locked) {
     event.setCanceled(true);
     event.player.message("<" + door_name + "> This door is locked");
     event.player.playSound("minecraft:block.enchantment_table.use", 1, 1);
   }
 }
}
```

## Что демонстрирует

- **Скрипт на блоке**, а не на NPC — использует `event.block`
- `event.block.setBlockModel("mod:block")` — смена модели блока (железная → деревянная дверь)
- `event.player.getMainhandItem().getDisplayName()` — проверка имени предмета в руке
- `event.player.playSound()` — проигрывание звука игроку
- `event.setCanceled(true)` — отмена стандартного открытия/взаимодействия
- Глобальные переменные `var locked` — состояние, общее для всех вызовов
- Проверка ключа по `getDisplayName()`, а не по ID предмета
