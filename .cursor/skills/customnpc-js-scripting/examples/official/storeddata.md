# storeddata.js (Daot)

Хранение постоянных данных на NPC и уровне мира. `getStoreddata()` сохраняется в NBT и переживает перезагрузку мира/сервера.

```javascript
//storeddata example by Daot

/**
 * @param {NpcEvent.InitEvent} event
 */
function init(event) {
 // npc data
 event.npc.getStoreddata().put("abc", "stored in npc");
 
 // or world data 
 event.npc.world.getStoreddata().put("abc", "stored in world");
}

/**
 * @param {NpcEvent.InteractEvent} event
 */
function interact(event) {
 // npc data
 event.npc.say(event.npc.getStoreddata().get("abc"));
 
 // or world data 
 event.npc.say(event.npc.world.getStoreddata().get("abc"));
}
```

## Что демонстрирует

- `getStoreddata()` — постоянное хранилище на NPC и на мир
- Разница с `tempdata`: storeddata переживёт перезапуск сервера
- Можно хранить строки, числа, массивы
