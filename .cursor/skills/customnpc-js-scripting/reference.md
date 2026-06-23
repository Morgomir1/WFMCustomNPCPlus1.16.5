# API Reference — CustomNPC+ JS Scripting (1.16.5)

Подробный справочник API для всех объектов, доступных в JS-скриптах CustomNPC+ (Nashorn ECMAScript) для Minecraft 1.16.5.

Официальная документация: http://www.kodevelopment.nl/customnpcs/api/1.16.5/

---

## event.npc (`ICustomNpc`) — сам NPC

Наследует: `IEntity` → `IEntityLiving` → `IMob` → `ICustomNpc`

```javascript
npc.getDisplay()        // INPCDisplay (имя, титул)
npc.getStats()          // INPCStats (жизни, броня, урон)
npc.getAi()             // INPCAi (AI, цель, пути)
npc.getInventory()      // INPCInventory (инвентарь NPC)
npc.getFaction()        // IFaction (фракция)
npc.setFaction(id)      // установить фракцию по id
npc.getAdvanced()       // INPCAdvanced (доп. настройки)
npc.getRole()           // INPCRole (роль банкира/торговца...)
npc.getJob()            // INPCJob (работа барда/курьера...)
npc.getTimers()         // ITimers — таймеры (start, stop, has)
npc.getOwner()          // IEntityLiving — владелец (фолловер/компаньон)
npc.getHomeX/Y/Z()      // координаты дома
npc.setHome(x, y, z)    // установить домашнюю точку
npc.getDialog(slot)     // IDialog (слот 0-11)
npc.setDialog(slot, dialog) // установить диалог
npc.updateClient()      // принудительно обновить клиент
npc.say(message)        // сказать всем вокруг
npc.sayTo(player, msg)  // сказать игроку
npc.shootItem(target, item, accuracy) // выстрелить предметом в сущность
npc.shootItem(x, y, z, item, accuracy) // выстрелить в точку
npc.giveItem(player, item)  // дать предмет игроку (если инвентарь полон — на землю)
npc.executeCommand(cmd)  // выполнить команду (возвращает вывод)
npc.reset()             // полный сброс NPC (вызовет init заново)
npc.trigger(id, ...args) // вызвать trigger-событие
```

---

## event.player (`IPlayer`) — игрок

Наследует: `IEntity` → `IEntityLiving` → `IPlayer`

```javascript
player.getName()                // имя игрока
player.getDisplayName()         // отображаемое имя
player.message(text)            // отправить сообщение
player.kick(message)            // кикнуть с сервера
player.getInventory()           // IContainer инвентаря (36 слотов)
player.getOpenContainer()       // IContainer открытого в данный момент контейнера
player.giveItem(item/string, n) // дать предмет (возвращает boolean)
player.removeItem(item/string, amount) // забрать предмет (возвращает boolean)
player.removeAllItems(item)     // забрать все предметы этого типа
player.inventoryItemCount(item/id) // сколько предметов [deprecated — используйте getInventory().count()]
player.getInventoryHeldItem()   // IItemStack в руке (в контейнере)
player.updatePlayerInventory()  // синхронизировать инвентарь с клиентом
player.getExpLevel()            // уровень опыта
player.setExpLevel(level)       // установить уровень
player.getHunger() / setHunger(n) // голод
player.getGamemode() / setGamemode(type) // режим игры (0-3)
player.hasPermission(perm)      // проверка пермишена
player.startQuest(id)           // начать квест
player.finishQuest(id)          // завершить квест
player.stopQuest(id)            // остановить активный квест
player.removeQuest(id)          // удалить квест из активных и выполненных
player.hasActiveQuest(id)       // есть ли активный квест
player.hasFinishedQuest(id)     // выполнен ли квест
player.canQuestBeAccepted(id)   // можно ли принять квест
player.getActiveQuests()        // IQuest[] активные квесты
player.getFinishedQuests()      // IQuest[] выполненные квесты
player.addFactionPoints(factionId, pts) // очки фракции
player.getFactionPoints(factionId)
player.factionStatus(factionId) // -1=враг, 0=нейтрал, 1=дружелюбен
player.addDialog(id)            // добавить диалог в прочитанные
player.removeDialog(id)         // удалить из прочитанных
player.hasReadDialog(id)        // проверка прочитан ли диалог
player.showDialog(id, name)     // показать диалог игроку
player.setPosition(x, y, z)     // телепортация
player.setSpawnpoint(x, y, z)   // установить точку возрождения
player.resetSpawnpoint()        // сбросить точку возрождения
player.getSpawnPoint()          // IBlock точки возрождения
player.showCustomGui(gui)       // показать кастомный GUI
player.getCustomGui()           // ICustomGui текущего открытого GUI
player.closeGui()               // закрыть GUI
player.playSound(sound, vol, pitch) // проиграть звук игроку
player.playMusic(sound, background, loops) // проиграть музыку
player.sendNotification(title, msg, type) // отправить уведомление
player.sendMail(mail)           // отправить письмо
player.clearData()              // ОЧИСТИТЬ ВСЕ ДАННЫЕ CustomNPCs игрока
player.getStoreddata()          // IData — сохраняемые данные (между перезагрузками)
player.getTempdata()            // IData — временные данные (до перезагрузки)
player.getTimers()              // ITimers — таймеры игрока
player.hasAdvancement(id)       // есть ли достижение
player.getMCEntity()            // Minecraft ServerPlayerEntity (эксперты)
player.trigger(id, ...args)     // trigger для игрока
```

---

## event.entity / event.source (`IEntity`) — любая сущность

Наследуется: `IAnimal`, `IArrow`, `ICustomNpc`, `IEntityItem`, `IEntityLiving`, `IMob`, `IMonster`, `IPlayer`, `IProjectile`, `IThrowable`, `IVillager`

```javascript
// Позиция и перемещение
entity.getX() / getY() / getZ() / getBlockX() / getBlockY() / getBlockZ()
entity.setX(x) / setY(y) / setZ(z)
entity.setPosition(x, y, z)
entity.getPos()               // IPos
entity.setPos(pos)
entity.getRotation() / setRotation(rot)  // 0-360
entity.getPitch() / setPitch(pitch)
entity.getHeight() / getWidth() / getEyeHeight()

// Мир
entity.getWorld()             // IWorld

// Движение
entity.getMotionX() / getMotionY() / getMotionZ()
entity.setMotionX(x) / setMotionY(y) / setMotionZ(z)
entity.knockback(power, direction) // direction 0-360

// Состояние
entity.isAlive()
entity.getAge()               // возраст в тиках
entity.getName() / setName(name)
entity.getEntityName()        // оригинальное имя (registered name)
entity.hasCustomName()        // есть ли кастомное имя
entity.getType()              // 0=entity, 1=player, 2=npc, 3=monster...
entity.getTypeName()          // registry name типа
entity.typeOf(type)           // проверка типа
entity.getUUID()
entity.generateNewUUID()

// Здоровье и урон
entity.damage(amount)         // нанести урон
entity.damage(amount, source) // нанести урон от источника
entity.kill()                 // убить (не деспавнит)
entity.despawn()              // деспавн (удалить навсегда)
entity.spawn()                // заспавнить (для NPC — установить home)
entity.isBurning() / setBurning(seconds) / extinguish()
entity.inWater() / inFire() / inLava()

// Данные
entity.getTempdata()          // IData — временные данные
entity.getStoreddata()        // IData — сохраняемые данные (в NBT)
entity.getNbt()               // INbt — NBT данные сущности
entity.getEntityNbt()         // INbt — полный NBT сущности (не каждый тик!)
entity.setEntityNbt(nbt)

// Теги (scoreboard)
entity.getTags() / addTag(tag) / hasTag(tag) / removeTag(tag)

// Всадники
entity.getMount() / setMount(entity)
entity.getRiders() / getAllRiders()
entity.addRider(entity) / clearRiders()

// Рейтрейс
entity.rayTraceBlock(distance, stopOnLiquid, ignoreNoBBox) // IRayTrace
entity.rayTraceEntities(distance, stopOnLiquid, ignoreNoBBox) // IEntity[]

// Предметы и эффекты
entity.dropItem(item)         // выбросить предмет (IEntityItem)
entity.playAnimation(type)    // 0=swing main, 1=hurt, 2=wakeup player, 3=swing offhand, 4=crit, 5=spell crit
entity.isSneaking() / isSprinting()

// Клонирование
entity.storeAsClone(tab, name)
```

---

## event.world (`IWorld`) — мир

```javascript
// Блоки
world.getBlock(x, y, z)        // IBlock (IPos вариант: getBlock(pos))
world.setBlock(pos, "mod:block") // установить блок
world.removeBlock(x, y, z)
world.getLightValue(x, y, z)   // 0-1
world.getBiomeName(x, z)       // название биома
world.getRedstonePower(x, y, z) // 0-15

// Время и погода
world.getTime() / setTime(time)    // время суток (ticks)
world.getTotalTime()               // общее время мира
world.isDay() / isRaining()
world.setRaining(bool)

// Сущности
world.getNearbyEntities(pos, range, type) // IEntity[]
world.getAllEntities(type)       // IEntity[] всех загруженных сущностей типа
world.getClosestEntity(pos, range, type)
world.getEntity(uuid)            // IEntity по UUID
world.getAllPlayers()            // IPlayer[]
world.getPlayer(name)            // IPlayer по имени

// Создание
world.createItem("mod:item", n)  // IItemStack
world.createItemFromNbt(nbt)     // IItemStack из NBT
world.createEntity("mod:entity") // IEntity (без спавна)
world.createEntityFromNBT(nbt)   // IEntity из NBT

// Спавн
world.spawnEntity(entity)        // заспавнить
world.spawnClone(x, y, z, tab, name) // спавн клона [deprecated — используйте API.clones]

// Эффекты
world.explode(x, y, z, range, fire, grief) // взрыв
world.thunderStrike(x, y, z)     // молния
world.spawnParticle(particle, x, y, z, dx, dy, dz, speed, count)

// Коммуникация
world.broadcast(message)         // сообщение всем игрокам
world.playSoundAt(pos, sound, vol, pitch) // звук (16 блоков)

// Данные и мета
world.getScoreboard()            // IScoreboard
world.getDimension()             // IDimension
world.getTempdata() / getStoreddata() // данные мира (cross-dimension)
world.getSpawnPoint() / setSpawnPoint(block)
world.getName()

// Forge
world.getMCWorld()               // ServerWorld (эксперты)
world.getMCBlockPos(x, y, z)     // BlockPos (эксперты)
world.trigger(id, ...args)       // trigger на уровне мира
```

---

## IItemStack — предмет

```javascript
item.getStackSize() / setStackSize(n) // размер стека
item.getName() / setDisplayName(name)  // имя / отображаемое имя
item.getCount()                       // количество
item.isEnchanted()
item.getNbt() / hasNbt()              // INbt
item.getMCItemStack()                 // Minecraft ItemStack
item.getItemStrength() / getFoodLevel() / getDamage()
```

---

## IEntityLiving — живая сущность (наследует IEntity)

Доступно для NPC, игроков, монстров, животных:

```javascript
living.getHealth() / setHealth(hp)
living.getMaxHealth() / setMaxHealth(hp)
living.getMainhandItem() / setMainhandItem(item)
living.getOffhandItem() / setOffhandItem(item)
living.getArmor(slot) / setArmor(slot, item)  // slot 0-3 (feet-head)
living.getAttackTarget() / setAttackTarget(entity)
living.isAttacking()
living.isChild()
living.canSeeEntity(entity)
living.addPotionEffect(typeId, durationSeconds, amplifier, hideParticles)
living.getPotionEffect(typeId)
living.clearPotionEffects()
living.getMoveForward() / setMoveForward(value)
living.getMoveStrafing() / setMoveStrafing(value)
living.getMoveVertical() / setMoveVertical(value)
living.swingMainhand() / swingOffhand()
living.addMark(tag) / removeMark(tag) / getMarks()
living.getLastAttacked() / getLastAttackedTime()
```

**`addPotionEffect(typeId, duration, amplifier, hideParticles)`** — второй аргумент `duration` задаётся в **секундах**, не в тиках. API сам умножает на 20. Пример: `player.addPotionEffect(PotionEffectType_POISON, 5, 0, false)` = отравление I на 5 секунд. Если передать `100`, эффект будет ~100 секунд.

---

## IMob — моб (наследует IEntityLiving)

```javascript
mob.getMCEntity()              // Minecraft Entity (эксперты)
mob.navigateTo(x, y, z, speed) // навигация к точке
mob.clearNavigation()          // очистить путь
mob.isNavigating()
mob.getNavigationPath()        // IPos[] текущий путь
mob.jump()
```

---

## IData — хранилище ключ-значение

```javascript
data.put(key, value)    // сохранить (value: number или string)
data.get(key)           // прочитать
data.has(key)           // проверить
data.remove(key)        // удалить
data.clear()            // очистить
data.getKeys()          // все ключи
```

**Разница:**
- `getTempdata()` — временное, живёт пока мир загружен
- `getStoreddata()` — сохраняется в NBT (постоянно)

---

## Глобальные константы

Доступны без префикса:

### ParticleType

- `ParticleType_FIRE`
- `ParticleType_SMOKE`
- `ParticleType_HEART`
- `ParticleType_EXPLOSION_NORMAL`
- `ParticleType_CRIT`
- `ParticleType_ENCHANTMENT_TABLE`
- `ParticleType_CLOUD`
- `ParticleType_PORTAL`
- `ParticleType_SPELL_WITCH`
- `ParticleType_SNOWBALL`
- `ParticleType_SLIME`
- `ParticleType_LAVA`
- `ParticleType_DRIP_LAVA`
- `ParticleType_DRIP_WATER`
- `ParticleType_NOTE`
- `ParticleType_ITEM_CRACK`
- `ParticleType_TOTEM`
- `ParticleType_END_ROD`
- `ParticleType_FIREWORKS_SPARK`
- `ParticleType_DRAGON_BREATH`
- `ParticleType_NAUTILUS`
- `ParticleType_DOLPHIN`
- `ParticleType_BUBBLE_COLUMN_UP`
- `ParticleType_BUBBLE_POP`
- `ParticleType_CURRENT_DOWN`
- `ParticleType_SOUL_FIRE_FLAME`
- `ParticleType_WARPED_SPORE`
- `ParticleType_CRIMSON_SPORE`
- `ParticleType_DRIPPING_OBSIDIAN_TEAR`
- `ParticleType_FALLING_OBSIDIAN_TEAR`
- `ParticleType_REVERSE_PORTAL`
- `ParticleType_INSTANT_EFFECT`
- `ParticleType_SNEEZE`
- `ParticleType_LANDING_OBSIDIAN_TEAR`
- `ParticleType_CAMPFIRE_COSY_SMOKE`
- `ParticleType_FALLING_SPORE_BLOSSOM`
- `ParticleType_SPORE_BLOSSOM_AIR`
- `ParticleType_SONIC_BOOM`

### PotionEffectType

- `PotionEffectType_SPEED`
- `PotionEffectType_SLOWNESS`
- `PotionEffectType_HASTE`
- `PotionEffectType_MINING_FATIGUE`
- `PotionEffectType_STRENGTH`
- `PotionEffectType_INSTANT_HEALTH`
- `PotionEffectType_INSTANT_DAMAGE`
- `PotionEffectType_JUMP_BOOST`
- `PotionEffectType_NAUSEA`
- `PotionEffectType_REGENERATION`
- `PotionEffectType_RESISTANCE`
- `PotionEffectType_FIRE_RESISTANCE`
- `PotionEffectType_WATER_BREATHING`
- `PotionEffectType_INVISIBILITY`
- `PotionEffectType_BLINDNESS`
- `PotionEffectType_NIGHT_VISION`
- `PotionEffectType_HUNGER`
- `PotionEffectType_WEAKNESS`
- `PotionEffectType_POISON`
- `PotionEffectType_WITHER`
- `PotionEffectType_HEALTH_BOOST`
- `PotionEffectType_ABSORPTION`
- `PotionEffectType_SATURATION`
- `PotionEffectType_GLOWING`
- `PotionEffectType_LEVITATION`
- `PotionEffectType_LUCK`
- `PotionEffectType_UNLUCK`
- `PotionEffectType_SLOW_FALLING`
- `PotionEffectType_CONDUIT_POWER`
- `PotionEffectType_DOLPHINS_GRACE`
- `PotionEffectType_BAD_OMEN`
- `PotionEffectType_HERO_OF_THE_VILLAGE`

**Список ParticleType и PotionEffectType:** http://www.kodevelopment.nl/customnpcs/api/1.16.5/noppes/npcs/api/constants/package-summary.html

### Вспомогательные функции

- `dump(object)` — вывести все поля/методы объекта в консоль (отладка)
- `log(text)` — запись в консоль скрипта и серверный лог