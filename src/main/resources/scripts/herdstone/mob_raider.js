// Унгор-рейдер. Третий файл после hs_mob.js.
// Ближе 9 блоков отступает, иначе стоит и кидает сеть (6–18).
// Пока идёт каст сети или рывок клича — AI не трогаем.
var RETALIATE_RETREAT = 2;
var RETALIATE_NONE = 3;
var FLEE_DIST = 9;
var NET_COLOR = 0xC0D8C090 | 0;

CASTS = [
    { ability: "net_throw", cd: 160, min: 6, max: 18, shape: "", pct: 0,
      params: function () {
          return AbilityAPI.params(
              "chargeTicks", 8,
              "activeTicks", 8,
              "radius", 2.2,
              "effectDuration", 100,
              "telegraphColor", NET_COLOR
          );
      } }
];
SLOW_ON_HIT = 3;
var RAIDER_SPEED = 6;

function init(e) {
    hsMobInit(e.npc);
    e.npc.getAi().setWalkingSpeed(RAIDER_SPEED);
}

function hsMobExtra(npc) {
    if (AbilityAPI.isBusy(npc)) return;
    var t = npc.getAttackTarget();
    var want = RETALIATE_RETREAT;
    if (t != null && t.isAlive() && hsFlat(t, npc.getX(), npc.getZ()) >= FLEE_DIST) want = RETALIATE_NONE;
    try {
        var ai = npc.getAi();
        if (ai.getRetaliateType() == want) return;
        ai.setRetaliateType(want);
        if (want == RETALIATE_NONE) npc.getMCEntity().getNavigation().stop();
    } catch (err) {}
}

function meleeAttack(e) {
    if (e.target == null || !e.target.typeOf(1)) return;
    e.target.addPotionEffect(PotionEffectType_SLOWNESS, 3, 2, false);
}
