# Herdstone: план реализации скриптов

Источник требований — [herdstone_dungeon.html](herdstone_dungeon.html) (раздел «Как собрать это в CustomNPC+»).
Как ставить это в мир — [herdstone_deploy.md](herdstone_deploy.md).
В HTML уже есть черновики почти всех скриптов. API мода сверено: `AbilityAPI`, `TelegraphAPI`,
`ZoneAPI`, `AbilityCombatHelper`, все 8 абилок и глобальные `PotionEffectType_*` существуют
с подходящими сигнатурами. Работа — вынести код в файлы, исправить баги и дописать недостающее.

Порядок: **§1 → §2 → §3 → §5 (Б1 → Б4) → §4 → §6 → §7 → §8**.
§1 и §2 блокируют всё остальное; §4 и §6 можно делать параллельно с боссами.

---

## 1. Проверки в строителе (до кода)

- [ ] Y пола и маркеров. Маркер ставится на `y0 + 1` (`AsyncDungeonBuilder.java:281`).
      Убедиться, что `hsSpawnAt` с `dy = 0` не спавнит тотемы, сердца и кристаллы внутри пола,
      а `STAKES` с `dy = 1` попадают в нижний блок кола.
- [ ] Ширина спусков против завалов `hsBox(…, 3, 2, 3)`:
      `A4_down` (292,26), `B5_down` (523,230), `C5_down` (413,465), `exit_up` (198,612).
      Оффсеты от центров арен сходятся: +12/−11, +8/+8, −2/−12, −32/+10.
- [x] GATE устья колодца Б4. Строитель ставит кольцо барьера навсегда (y входа +1…+4)
      и отдельно южную пробку 3×3×3: `cx-1…+1`, `y1+1…+3`, `cz+27…+29`.
      Скрипт открывает только пробку (`hsBox(-1, 73, 27, 3, 3, 3)`) и столбик выхода.
      Кольцо не трогает.
- [x] В HTML команда постройки — `/cnpcdungeon herdstone`.

## 2. Раскладка файлов и доставка в игру

- Исходники: `src/main/resources/scripts/herdstone/` (по образцу `vampire/`, `drachenfels/`).
- Мод эти файлы не грузит. Скрипты читаются только из `<world>/customnpcs/scripts/ecmascript/**`.
  Нужен `tools/herdstone/sync_scripts.ps1` — копирует папку в
  `run/saves/New World123/customnpcs/scripts/ecmascript/herdstone/`.
- Несколько файлов на NPC поддерживается: `ScriptContainer.getFullCode()` склеивает их по порядку.
  Ядро всегда первым, файлы ниже переопределяют поля `HS` и глобальные функции.
- После готовности заменить код в HTML ссылками на файлы — один источник правды.

Целевой список файлов:

```
herdstone/
  hs_core.js
  hs_mob.js
  mob_gor.js  mob_shield.js  mob_archer.js  mob_raider.js
  mob_executioner.js  mob_centigor.js  mob_spawn.js  mob_minotaur.js
  shaman.js  herd_totem.js  drummer.js
  champion.js  beastigor_neutral.js
  boss_bloodtusk.js  boss_grul.js
  boss_murgrot.js  murgrot_copy.js
  boss_gorgon.js
  boss_garrax.js  heart.js  crystal.js
  boss_jabberslythe.js
  block_fog.js  block_herb.js  block_dmg_scale.js
  camp_spawnpoint.js
```

## 3. Ядро `hs_core.js`

- [x] **Свой центр у каждого босса.** `ORIGIN = [x, y, z]` в файле босса — мировой блок пола
      арены. Скрипт не читает точку входа и не прибавляет её. Числа записаны для каркаса от
      `0, 80, 0` (этажи 80 / 56 / 32 / 8). Койки лагерей — свои мировые тройки в `camp_spawnpoint.js`.
- [x] **Прямоугольный поводок.** Баг: у A4 (44×38) `ARENA_R = 20`, а колья на расстоянии
      √(16² + 13²) ≈ 20,6 — игрок у кола получает поводок −25% и телепорт. То же у C4
      (72×40 при `ARENA_R = 22`). Ввести `HS.ARENA = {shape: "rect", hw, hd}` в
      `hsLeash` / `hsPlayers`.
- [x] **Сброс убирает аддов** (правило 6). Список `SUMMONS` и `hsDespawnAll` в `hsInit`:
      рейдеры Грула, «Гор (призыв)» Гарракса и Барабанщика, тотемы Мургрота.
      Ядро зовёт `hsDespawnSummons`; имена прописывает файл босса.
- [x] **Зоны.** `ZoneAPI.hazardRing` возвращает `EntityAbilityZone`, не строку.
      Хранить в tempdata, снимать `ZoneAPI.remove` в `init` и `died` (`hsZoneClear`).
      `hsInit` уже снимает зону. В `died` босса вызвать `hsZoneClear(npc)` самому.

## 4. Мобы переходов

- [x] `hs_mob.js` — только логика. Пресеты — `mob_gor.js`, `mob_shield.js`, `mob_archer.js`,
      `mob_raider.js`, `mob_executioner.js`, `mob_centigor.js`, `mob_spawn.js`, `mob_minotaur.js`.
      Цепочка: `hs_core` → `hs_mob` → `mob_<тип>`.
- [x] `effectType` / `effectId` — строки (`"slowness"`, `"minecraft:poison"`).
      Длительность эффекта зоны — тики (`160` = 8 с).
- [x] `weapon_spin` сам рисует красный круг: `telegraph` по умолчанию 1, `circleFollow` на `radius`.
      Второй `TelegraphAPI.circle` для `shape: "self"` не добавлялся.
- [x] `mob_minotaur.js`: «Кровавое безумие» и групповой агр в `damaged` (радиус 24).

## 5. Боссы

| Файл | Черновик | Доделать |
|---|---|---|
| `boss_bloodtusk.js`, `boss_grul.js` | вынесено | [x] Прямоугольник 22×19 вместо `ARENA_R = 20`; рейдеры в `SUMMONS`; `init` обоих закрывает один и тот же `GATE`, поэтому спуск ставит тот, кто возродился первым. Рёв (рассеивание + 2 рейдера) только у Грула, раз в 45 с с начала боя |
| `boss_murgrot.js` | вынесено | [x] `init` снимает тотемы (`SUMMONS`) и копии по метке |
| `murgrot_copy.js` | вынесено | [x] Имя, размер и GeckoLib-модель копируются с оригинала; метка `hs_murgrot_copy` |
| `boss_gorgon.js` | вынесено | [x] Зона с уроном 0 хранится и снимается; −10% раз в секунду остаётся один раз; конус 9 |
| `boss_garrax.js` | вынесено | [x] Пробка устья как у строителя; `setSize` жив и на GeckoLib (`RenderCustomNpc.scale`); горы в `SUMMONS` |
| `heart.js`, `crystal.js` | вынесено | [x] Без изменений механики |
| `boss_jabberslythe.js` | вынесено | [x] Прямоугольник C4 36×20, менгиры на метках строителя |

## 6. Элиты и NPC с диалогом

| Файл | Доделать |
|---|---|
| `shaman.js`, `herd_totem.js` | [x] Вынесены как в черновике. Тотем без ядра |
| `drummer.js` | [x] «Гор (призыв)» в `SUMMONS`, `init` их снимает |
| `champion.js` + `beastigor_neutral.js` | [x] `dialogOption` — событие NPC (`onNPCDialogOption`). «принять» — кольцо барьера r 8 и `HS.FACTION_HERD`; «отказаться» или удар по бестигору — вся группа с меткой `hs_challenge`. Пустая арена 32 блока возвращает `HS.FACTION_NEUTRAL` |
| `camp_spawnpoint.js` | [x] `interact` Отто и Гурхака ставит койку по мировым `BED_ORDER` / `BED_DESTR` |

## 7. Scripted Blocks

- [x] `block_fog.js` — туман C3. Креатив и наблюдатель пропускаются.
- [x] `block_herb.js` — святая трава, `interact` пишет `hs_clean_until` на 15 с.
- [x] `block_dmg_scale.js` — шкала `hs_dmg`, цикл 0.75 → 2, только креатив.

## 8. Тестирование в dev-мире

Построить `/cnpcdungeon herdstone force`, `hs_dmg = 1`. Каждого босса пройти соло и вдвоём:

- [ ] масштаб HP при пуле: `BASE × (0,7 + 0,3·N) × HS.DMG`;
- [ ] потолок урона и окно уязвимости;
- [ ] поводок у стен, у кольев (Б1) и у менгиров (C4);
- [ ] сброс через 15 с без игроков: HP, фазы и аддов убирает, ворота закрывает;
- [ ] ярость (временно уменьшить `ENRAGE_SEC`);
- [ ] `died` открывает проход, респаун (временно `Respawn 60`) закрывает снова.

Отдельно:

- [ ] D2: принять дуэль / отказаться / ударить бестигора;
- [ ] D1: групповой агр минотавров и «Кровавое безумие»;
- [ ] C3: туман и святая трава;
- [ ] полный проход за Порядок и за Разрушение (квесты Area Kill засчитываются).
