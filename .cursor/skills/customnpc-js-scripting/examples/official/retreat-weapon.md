# retreat_weapon.js (Runon)

NPC убирает оружие в ножны, когда нет цели, и достаёт при появлении врага.

```javascript
//Retreat weapons by Runon

//Pulls out weapon when aggressive. Hides weapons if target is lost.

//If you save an npc with a weapon, and the weapon disappears after saving,
//that means the script work, as the NPC retreated its weapon

//Init is called when script initializes
//This hapens after saving scripts, or after saving npc in npc wand,
//the last one is very important
/**
 * @param {NpcEvent.InitEvent} event
 */
function init(e) {
 var data = e.npc.storeddata;
 var item = e.npc.mainhandItem;

 if (!item.isEmpty()) {
   data.put('weapon', item.getItemNbt().toJsonString());
 }

 if (!e.npc.getAttackTarget()) {
   var air = e.npc.world.createItem('minecraft:air', 0, 1);
   e.npc.setMainhandItem(air);
 }
}

//when the npc gets a new attack target
/**
 * @param {NpcEvent.TargetEvent} event
 */
function target(e) {
 var data = e.npc.storeddata;
 if(data.has('weapon')) {
   var item = e.npc.world.createItemFromNbt(e.API.stringToNbt(data.get('weapon')));
   e.npc.setMainhandItem(item);
 }
}

//When the npc loses its attack target
/**
 * @param {NpcEvent.TargetLostEvent} event
 */
function targetLost(e) {
 var air = e.npc.world.createItem('minecraft:air', 0, 1);
 e.npc.setMainhandItem(air);
}
```

## Что демонстрирует

- Сохранение предмета из `mainhandItem` в `storeddata` через NBT (`getItemNbt().toJsonString()`)
- Восстановление предмета из NBT-строки: `world.createItemFromNbt(API.stringToNbt(...))`
- `target(event)` — NPC взял цель в AI
- `targetLost(event)` — NPC потерял цель
- `mainhandItem` / `setMainhandItem(item)` — управление предметом в руке
- Важный нюанс: `init()` срабатывает **после сохранения скрипта или после сохранения NPC жезлом** — именно в этот момент нужно захватить предмет
