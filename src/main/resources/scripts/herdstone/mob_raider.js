// Унгор-рейдер. Третий файл после hs_mob.js.
// Ближе FLEE_DIST отступает, иначе стоит и кидает сеть (NET_MIN–NET_MAX).
// Пока идёт каст сети или рывок клича — AI не трогаем.

// --- НАСТРОЙКИ СПОСОБНОСТЕЙ ---
var RETALIATE_RETREAT = 2;
var RETALIATE_NONE = 3;
var RAIDER_SPEED = 6;
var FLEE_DIST = 9;
// Сеть (net_throw)
var NET_CD = 130;
var NET_MIN = 6;
var NET_MAX = 24;
var NET_CHARGE = 8;
var NET_ACTIVE = 8;
var NET_R = 2.2;
var NET_EFFECT = 100;
var NET_COLOR = 0xC0D8C090 | 0;
// Мили
var SLOW_ON_HIT = 3;
var MELEE_SLOW_SEC = 3;
var MELEE_SLOW_AMP = 2;

CASTS = [
    { ability: "net_throw", cd: NET_CD, min: NET_MIN, max: NET_MAX, shape: "", pct: 0,
      params: function () {
          return AbilityAPI.params(
              "chargeTicks", NET_CHARGE,
              "activeTicks", NET_ACTIVE,
              "radius", NET_R,
              "effectDuration", NET_EFFECT,
              "telegraphColor", NET_COLOR
          );
      } }
];

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
    e.target.addPotionEffect(PotionEffectType_SLOWNESS, MELEE_SLOW_SEC, MELEE_SLOW_AMP, false);
}
