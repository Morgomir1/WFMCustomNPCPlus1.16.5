// Унгор-рейдер. Третий файл после hs_mob.js.
// Сеть по цели на 6–18 блоков. Мили-удар вешает Замедление II.
CASTS = [
    { ability: "net_throw", cd: 240, min: 6, max: 18, shape: "", pct: 0,
      params: function () {
          return AbilityAPI.params(
              "chargeTicks", 16,
              "activeTicks", 12,
              "radius", 2.2,
              "effectDuration", 100
          );
      } }
];
SLOW_ON_HIT = 3;

function meleeAttack(e) {
    if (e.target == null || !e.target.typeOf(1)) return;
    e.target.addPotionEffect(PotionEffectType_SLOWNESS, 3, 2, false);
}
