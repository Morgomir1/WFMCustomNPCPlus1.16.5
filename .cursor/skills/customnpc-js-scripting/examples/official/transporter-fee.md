# transporters_fee.js (Xelerax)

NPC-транспортировщик с платой за перемещение — использует роль `transporter` и диалоговую систему.

```javascript
//Transporter's Fee by Xelerax

//// Get the location of that transporter's teleport location ////

/**
 * @param {NpcEvent.InitEvent} event
 */
function init(event) {
 var locname1 = event.npc.role.location.getName();
 event.npc.world.getStoreddata().put("locname1", locname1)
}

//// Function using the transporter role ////

/**
 * @param {DialogEvent.CloseEvent} event
 */
function dialogClose(event) {
 var dest = event.dialog.getId();
 if (dest === "DIALOG ID") {
   if (event.player.removeItem("minecraft:cobblestone", 0, 10)) {
     var User = event.player.getMCEntity();
     var loc1 = event.npc.world.getStoreddata().get("locname1");
     event.npc.role.transport(User, locname1);
   }
   else {
     event.npc.say("No can do chief, I'm not running a charity. " +
                    "I've got to \"pay the rent\" aswell.");
   }
 }
}
```

## Что демонстрирует

- `event.npc.role` — доступ к роли NPC (здесь `transporter`)
- `event.npc.role.location.getName()` — получение имени локации транспортировщика
- `dialogClose(event)` — событие закрытия диалога
- `event.dialog.getId()` — ID диалога, который закрылся
- `event.player.removeItem(item, variant, count)` — снятие предметов с игрока (плата)
- `event.player.getMCEntity()` — получение `PlayerEntity` Minecraft для передачи в `role.transport()`
- `event.npc.role.transport(playerEntity, locationName)` — телепортация через роль
- `event.npc.world.getStoreddata()` — хранение имени локации на уровне мира
