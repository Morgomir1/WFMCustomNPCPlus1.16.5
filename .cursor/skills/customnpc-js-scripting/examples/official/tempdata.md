# tempdata.js (Daot)

Хранение временных данных на NPC и уровне мира. `getTempdata()` живёт, пока мир загружен, и пропадает после перезапуска.

```javascript
//tempdata example by Daot

/**
 * @param {NpcEvent.InitEvent} event
 */
function init(event) {
 // npc data
 event.npc.getTempdata().put("abc", [1, 2, 3, 4]);
 
 // or world data 
 event.npc.world.getTempdata().put("abc", [1, 2, 3, 4]);
}

/**
 * @param {NpcEvent.InteractEvent} event
 */
function interact(event) {
 // npc data
 event.npc.say(event.npc.getTempdata().get("abc"));
 
 // or world data 
 event.npc.say(event.npc.world.getTempdata().get("abc"));
}
```

## Что демонстрирует

- `getTempdata()` — временное хранилище на NPC и на мир
- `put(key, value)` / `get(key)` — запись и чтение
- Можно хранить массивы: `[1, 2, 3, 4]`
