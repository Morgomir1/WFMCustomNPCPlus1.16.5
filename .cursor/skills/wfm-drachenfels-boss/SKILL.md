---
name: wfm-drachenfels-boss
description: >-
  Фазы, способности и encounter-логика соло-босса Constant Drachenfels (CustomNPC+ Forge 1.16.5).
  Используй при правках DrachenfelsEncounterHelper, Df*Ability, drachenfels_constant.js,
  телеграфах/зонах Драхенфельса, фазах, сосудах, осколках или балансе его спеллов.
---

# Constant Drachenfels (соло)

> **Связанные skills:** `customnpc-java-abilities` — `CnpcAbility` / `AbilityAPI`. `customnpc-attack-zones` — Telegraph / Zone. `customnpc-js-scripting` / `nashorn-customnpc-scripting` — тонкий JS. `wfm-cnpc-ability-bridge` — только если нужен WFM API.

Соло-энкаунтер: **один** босс (`df_constant`). Механика в Java; JS только клоны + числовые константы.

| Слой | Где |
|------|-----|
| Оркестратор | `DrachenfelsEncounterHelper` — фазы, CD, адды, сосуды |
| Касты | `AbilityAPI.start` → `impl/Df*Ability` |
| Конфиг | `DrachenfelsConfig` ← `Encounter.configure(npc, AbilityAPI.params(...))` |
| JS | `scripts/drachenfels/drachenfels_constant.js` |
| Absorb / caps | `DrachenfelsCombatHandler` |

## Глобальные правила

- Арена: круг **R≈12**, центр = spawn (`df_home_*`).
- **Один** AbilityAPI-каст босса за раз (`isBusy`).
- Аггро / engage только на **Survival/Adventure**; creative и spectator игнорируются (цель сбрасывается).
- Без таких игроков в радиусе engage — **не кастует** (активный каст cancel). Engage = `max(arenaRadius+4, engageRadius config, Stats aggro)`; JS default `engageRadius=60`.
- После смерти `cleanup` сбрасывает `df_inited` → респавн = фаза 1 и полное max HP (не cap прошлой фазы).
- Урон спеллов — **pure MAGIC** (`dealPureDamage`).
- Пороги фаз — **% maxHealth** (не абсолютные HP): phase2 **66%**, phase3 **33%**.
- Переход фазы: cancel каста → invuln (~60t) → телепорт в центр → чистка аддов/зон.
- Числа из JS → `df_c_<key>` в storeddata. Nashorn: `Encounter.configure(npc, AbilityAPI.params(...))`, не varargs после `npc`.
- Warning на charge через `TelegraphAPI`; hazard DoT — `ZoneAPI`. Авто-телеграф отключён (`telegraph: 0`) — абилки спавнят зоны сами.
- Цвета зон: **атака/warning = красный** `0xC0FF3030`; **лужи Seal = тёмно-зелёный** `0xC0143C14`; **белый только Feast safe seats**.

## Фазы

```
HP 100% ──► Phase 1 (kite + seal/gaze/bell/court)
     66% ──► Transition ──► Phase 2 (цикл яд/места/бал + false host)
     33% ──► Transition ──► Phase 3 spirit (сосуды/осколки + step/whisper/steal + carrier)
```

### Phase 1 — тело / kite

AI: scripted kite (walkingSpeed 0) к плотным seal-лужам **на kite-дистанции** (~6); без луж — kite. `gazeRange` **&lt; kite** (5 &lt; 6), иначе Gaze не кастуется и остаётся только Seal раз в 10с. Приоритет: **Gaze → Repulse → Court → Seal**. CD с **начала** каста. Encounter.tick с LivingUpdate + JS (debounce 1 тик).

| Id / механика | Что делает | Telegraph |
|---------------|------------|-----------|
| `df_black_seal` | 3 круга → burst + hazard puddles (тёмно-зелёные) | 3× circle (красные) |
| `df_mask_gaze` | если цель далеко ≥`gazeFarTicks` — charge line + **летящий soul-снаряд** по лучу | line |
| `df_repulse` | charge 1.5с → knockback игроков в R=3; CD 10с; если цель ≤`repulseTrigger` | circle |
| **Bell** (helper) | HP marks ~88%/76% → absorb ~13.3% maxHP на босса **и** Court (cultist/guard) + spawn Monk; absorb = **кольцо партиклов**; смерть монаха снимает щит со всех | — |
| **Court** (helper) | spawn Cultist (`df_mask_gaze`) или Guard (`df_carrier_slash` + pre-dash, CD 5с) | line → coneTruncated |

На каждый AbilityAPI-каст (и Bell/Court) босс говорит уникальную фразу.

Absorb снимается уроном по щиту; Monk — отдельный адд. Смерть монаха сбрасывает absorb у босса и у всех Court.

### Phase 2 — пир (kite)

AI: тот же scripted kite, что в фазе 1 (`kiteDistance` / `phase1Speed`); во время каста — freeze на месте. Цикл ~**360t** (слоты):

| Slot / elapsed | Ability | Telegraph |
|----------------|---------|-----------|
| 0 / start | `df_imperial_poison` — 3 волны expanding poison ring, пауза 2с | circle арены → Zone ring ×3 |
| 1 / ~120t | `df_feast_seats` — blast арены, safe = 6 белых seats (random near boss) | red arena + white seats |
| 2 / ~220t | `df_leper_ball` — 3 залпа × 4 духа **от** босса наружу (R≈24), виляют; красная зона урона под каждым | circle у босса перед каждым залпом |

**False Host** на HP marks ~56%/46%/36% (макс 3): `df_false_host` — charge (копии + landing) → teleport + 3 false copies. Копии оставляют тёмно-зелёные лужи (живут до смерти копии; не стакаются на одной точке). Сдвигает cycle (`falseShift`).

Цикл рестартует после `cycleLength`.

### Phase 3 — дух / имя

Spirit AI (без ходьбы). Сразу 3 **Vessel** на кольце (random base angle + jitter, radius в `[vesselRingMin, vesselRing]`). Касты (CD-очередь): Steal (ближний) → Whisper → Step.

| Id | Что делает | Telegraph |
|----|------------|-----------|
| `df_nameless_step` | dash к текущей позиции игрока (retarget в конце charge + overshoot) | line + circle |
| `df_nameless_whisper` | 3 кольца, hit только в bands + blind | 3× ring |
| `df_name_steal` | charge на цели → damage + weak + blind | circle на цели |
| `df_carrier_slash` | truncated cone (как wh_flaming_strike), soul VFX; hold-in-place | coneTruncated |

**Сосуды / осколки / Носитель:**

1. Vessel умирает → через delay spawn **Shard** (бежит к боссу).
2. Shard касается босса → heal ~2.67% maxHP, **cap = phase3 ratio (33%)**.
3. Все vessel мертвы → **Carrier window** (~240t): босс снова плоть, cone-атаки; убить до конца окна.
4. Окно провалено → снова spirit + новый набор vessel (меньше HP).

## Адды (клоны)

Имена задаются в JS → `configureClones`. Теги: `df_monk`, `df_court`/`df_cultist`/`df_guard`, `df_leper`, `df_false`, `df_vessel`, `df_shard`.

| Клон | Роль |
|------|------|
| Monk | Bell add |
| Cultist | `df_mask_gaze` (параметры босса) |
| Guard | `df_carrier_slash` + pre-dash к игроку (`guardDash*`); slash-параметры с босса; CD `guardInterval` = 5с |
| Leper Phantom | 3 залпа от босса наружу, виляющий полёт |
| False Host | 1 HP decoy |
| Vessel / Shard | phase 3 mechanics |

## При правках

```
- [ ] Новая механика каста → Df*Ability + AbilityRegistry + AbilityDefaults
- [ ] Когда кастовать / пороги → DrachenfelsEncounterHelper
- [ ] Числа → ключ в DrachenfelsConfig + переменная в drachenfels_constant.js
- [ ] Warning zone → TelegraphAPI в onStart; telegraph: 0; chargeTicks ≥ ~14–36
- [ ] DoT puddle → ZoneAPI, не дублировать урон в tick
- [ ] Только серверный код; клиентские пакеты не импортировать
- [ ] После Java-правок — rebuild jar и заменить mods на сервере
```

## Файлы

```
abilities/DrachenfelsEncounterHelper.java
abilities/DrachenfelsConfig.java
abilities/event/DrachenfelsCombatHandler.java
abilities/impl/DfBlackSealAbility.java
abilities/impl/DfMaskGazeAbility.java
abilities/impl/DfRepulseAbility.java
abilities/impl/DfImperialPoisonAbility.java
abilities/impl/DfFeastSeatsAbility.java
abilities/impl/DfLeperBallAbility.java
abilities/impl/DfFalseHostAbility.java
abilities/impl/DfNamelessStepAbility.java
abilities/impl/DfNamelessWhisperAbility.java
abilities/impl/DfNameStealAbility.java
abilities/impl/DfCarrierSlashAbility.java
resources/scripts/drachenfels/drachenfels_constant.js
```

Детали ключей конфига и дефолтов: [reference.md](reference.md).
