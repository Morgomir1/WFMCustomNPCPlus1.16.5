# Hello World

Простейший скрипт — NPC говорит приветствие при инициализации.

```javascript
/**
 * @param {NpcEvent.InitEvent} event
 */
function init(event){
 event.npc.say("Hello World!");
}
```
