// Бестигор-палач. Третий файл после hs_mob.js.
// Конус с добиванием ниже 35% HP и вращение r 4 (−25% в конце).
// Круг вращения рисует weapon_spin сама (telegraph = 1).
CASTS = [
    { ability: "barrow_sentinel", cd: 100, min: 0, max: 4, shape: "", pct: 0,
      params: function () { return AbilityAPI.params("chargeTicks", 20, "radius", 3.5, "damage", 9.0, "executeHpThreshold", 0.35, "executeBonusDamage", 14.0); } },
    { ability: "weapon_spin", cd: 240, min: 0, max: 5, shape: "self", size: 4, hitAt: 56, pct: 0.25,
      params: function () { return AbilityAPI.params("chargeTicks", 20, "activeTicks", 36, "radius", 4.0, "damagePerTick", 2.0); } }
];
