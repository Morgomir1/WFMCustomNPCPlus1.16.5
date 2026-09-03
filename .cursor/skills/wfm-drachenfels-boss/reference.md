# Drachenfels — reference

## Ability ids

| Id | Класс | Фаза |
|----|-------|------|
| `df_black_seal` | `DfBlackSealAbility` | 1 |
| `df_mask_gaze` | `DfMaskGazeAbility` | 1 |
| `df_repulse` | `DfRepulseAbility` | 1 |
| `df_imperial_poison` | `DfImperialPoisonAbility` | 2 |
| `df_feast_seats` | `DfFeastSeatsAbility` | 2 |
| `df_leper_ball` | `DfLeperBallAbility` | 2 |
| `df_false_host` | `DfFalseHostAbility` | 2 |
| `df_nameless_step` | `DfNamelessStepAbility` | 3 |
| `df_nameless_whisper` | `DfNamelessWhisperAbility` | 3 |
| `df_name_steal` | `DfNameStealAbility` | 3 |
| `df_carrier_slash` | `DfCarrierSlashAbility` | 1 Court Guard |

Helper-only (не AbilityAPI): Bell, Court spawn, **Desperation Air**.

Desperation Air reuse: `df_imperial_poison` / `df_nameless_whisper` (rings) + `crimson_blob` (blobs → seal puddles). Tag `df_desp_shard`.

## Config keys (`df_c_*`)

Глобальные: `arenaRadius`, `engageRadius` (каст/цель; default 60; также берётся max со Stats aggro), `phase2Ratio`, `phase3Ratio`, `invulnTicks`, `kiteDistance`, `phase1Speed`, `telegraphColor`, `sealFirstDelay`, `gazeFirstDelay`, `courtFirstDelay`, `repulseFirstDelay`.

Seal: `sealCd`, `sealChargeTicks`, `sealActiveTicks`, `sealDamage`, `sealRadius`, `sealZoneTicks` (≈480 — puddles from 3 seals overlap), `sealZoneDamage`, `sealZoneInterval`, `sealZoneColor` (dark green puddles), `sealPoisonDuration` (100 = 5s), `sealPoisonAmp` (1 = Poison II), `sealMinBossDist`, `sealMinCircleDist`.

Gaze: `gazeCd`, `gazeRange` (5 — must be &lt; kite 6), `gazeFarTicks` (8), `gazeChargeTicks`, `gazeActiveTicks`, `gazeDistance`, `gazeWidth`, `gazeDamage` (telegraph = charge+flight; damage via flying soul projectile).

Attacking telegraphs/hazard waves default **red** `0xC0FF3030`. Seal **puddles** are dark green `0xC0143C14` (`sealZoneColor`). White `0xC0FFFFFF` only for Feast safe seats (`feastColor`); arena blast stays red. Bell absorb shows rotating enchant/end_rod particle ring while active (boss + Court cultist/guard). Monk death clears absorb on boss and all Court.

Repulse: `repulseCd` (200), `repulseChargeTicks` (30), `repulseActiveTicks`, `repulseRadius` (3), `repulseKnockback`, `repulseKnockbackY`, `repulseTrigger` (каст если цель ближе).

Phase 1 CD notes: AbilityAPI CDs arm **on cast start**. `Encounter.init` is one-shot (`df_inited`) so JS reload does not rewind clocks. Court CD only after successful spawn.

Bell / court: `bellRatios` (`"0.88,0.76"`), `bellCd`, `absorbRatio`, `courtCd`, `cultistInterval`, `guardInterval` (5с = 100t). Параметры кастов аддов берутся из `gaze*` / `carrierArc*` босса. Guard slash: `preDash` + `guardDashTicks` / `guardDashRange` / `guardDashStandoff` (дэш к игроку, затем cone).

**Add HP (all spawnable adds):** `monkHp`, `cultistHp`, `guardHp`, `leperHp`, `falseCloneHp`, `shardHp` / `despShardHp`.

Phase 2 cycle: `cycleLength`, `cycleFeastAt`, `cycleLeperAt`.

Imperial: `imperialChargeTicks`, `imperialActiveTicks` (expand duration; higher = slower), `imperialArenaRadius`, `imperialThickness` (ring band width), `imperialHitHeight` (jumpable: feet above this clear the ring), `imperialDamage`, `imperialPoisonDuration`, `imperialPoisonAmp`, `imperialSlowDuration`, `imperialWaveCount` (3), `imperialWaveInterval` (40 = 2s between waves, like whisper).

Feast: `feastChargeTicks`, `feastActiveTicks`, `feastSeatRadius`, `feastSeatRing` (max scatter from boss, default 9.5), `feastSeatMinBossDist` (default 2.5), `feastArenaRadius`, `feastDamage`, `feastPoisonDuration`, `feastPoisonAmp`, `feastColor`. Seats spawn at random positions near the boss (not a fixed ring).

Leper: `leperChargeTicks`, `leperActiveTicks`, `leperDamage`, `leperHp`, `leperStartRadius` (spawn near boss), `leperSpawnRadius` (outward end, default 24), `leperDuration` (flight ticks), `leperHitRadius` (red following hazard), `leperSlowDuration`, `leperSlowAmp`, `leperVolleys` (3), `leperVolleyInterval`, `leperWiggleAmp`, `leperWiggleFreq`, `leperHover` (locked Y above spawn floor). Three staggered-angle salvos fly **away** from the boss with a sine weave; each spirit has a red ZoneAPI circle that moves with it and deals damage.

False: `falseRatios`, `falseMax`, `falseShift`, `falseChargeTicks`, `falseActiveTicks`, `falseCloneHp`, `falseCopyDist`, `falseTeleportRing`, `falseTelegraphRadius`, `falseRunStep`, `falsePuddleRadius`/`Damage`/`DamageInterval`/`Interval` (лужи живут до смерти иллюзии; не стакаются если в позиции уже есть зона; `falsePuddleTicks` не используется). While copies live, real boss uses Display **Visible=No** + soft-visibility packets (not vanilla `Entity.setInvisible` — CNPC ignores that flag for rendering).

Phase 3 casts: `stepCd`/`ChargeTicks`/`ActiveTicks`/`Damage`/`Width`/`LandRadius`/`Overshoot`/`MinPlayerDist`, `whisperCd`/`ChargeTicks`/`ActiveTicks`/`BlindDuration`/`Thickness`, `stealCd`/`Range`/`Damage`/`ChargeTicks`/`TelegraphRadius`/`WeakDuration`/`BlindDuration`. Court Guard slash AbilityAPI `df_carrier_slash`: `carrierArcDamage`/`Distance`/`NearWidth`/`HalfAngle`/`CastTicks`/`Interval`/`Knockback`/`KnockbackY`.

Desperation Air: `desperationRatios` (`"0.25,0.15,0.05"`), `despAirHeight` (5), `despRingInterval` (80 = 4s gap after ring ends), `despBlobCd` (30), `despStunTicks` (200), `despShardMinDist` (10), `despShardSpeed` (0.03), `despShardHp`, `shardTouchDist`, `despBlobRadius` (3), `despBlobChargeTicks`/`FlightTicks`/`ArcHeight`/`ZoneTicks`/`Damage`/`DamageInterval`. Blob puddles use `sealZoneColor` + seal poison. Fail → full heal + phase 1 (marks reset). Success → stun then spirit casts resume.

## JS bootstrap

```javascript
var AbilityAPI = Java.type("noppes.npcs.abilities.AbilityAPI");
var Encounter = Java.type("noppes.npcs.abilities.DrachenfelsEncounterHelper");

function init(event) {
    var npc = event.npc;
    Encounter.configureClones(npc, CLONE_TAB, CLONE_MONK, /* ... */);
    Encounter.configure(npc, AbilityAPI.params("arenaRadius", 12.0, "sealDamage", 12.0 /* ... */));
    Encounter.init(npc);
    // timer → Encounter.tick(npc)
}
```

## Quotes (say)

| Когда | Текст |
|-------|-------|
| Init | `Этот замок помнит вас дольше, чем вы — себя.` |
| Phase 2 | `Садитесь. Пир уже накрыт.` |
| Phase 3 | `Тело — лишь маска. Имя остаётся.` |
| (death path) | `Замок не умрёт с этим телом.` |
| `df_black_seal` | `Печать ложится. Земля запомнит.` |
| `df_mask_gaze` | `Смотрите в маску — и потеряете лицо.` |
| `df_repulse` | `Прочь с порога замка.` |
| Bell | `Колокол мёртвых бьёт по вам.` |
| Court | `Свита склоняется. Вы — нет.` |
| `df_imperial_poison` | `Пейте. Яд — вино этого пира.` |
| `df_feast_seats` | `Садитесь. Места уже заняты смертью.` |
| `df_leper_ball` | `Прокажённые танцуют для вас.` |
| `df_false_host` | `Кто из нас хозяин? Угадайте.` |
| `df_nameless_step` | `Шаг без имени.` |
| `df_nameless_whisper` | `Шёпот, который стирает вас.` |
| `df_name_steal` | `Ваше имя теперь моё.` |
| Desperation start | `Взлетаю. Разбейте осколки — или замок начнётся снова.` |
| Desperation fail | `Осколок коснулся. Пир начинается сначала.` |
| Desperation success | `Осколки разбиты. Падаю…` |
