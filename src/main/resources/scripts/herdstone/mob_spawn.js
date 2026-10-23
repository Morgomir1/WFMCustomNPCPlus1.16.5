// Отродье Хаоса. Третий файл после hs_mob.js.
// Хватка: effectType строка "slowness", усилитель 3 = Замедление IV. Через 2 с −20% HP.
// Вращение r 4. Круг рисует weapon_spin сама.
CASTS = [
    { ability: "grasping_dead", cd: 160, min: 2, max: 14, shape: "target", size: 2, hitAt: 40, pct: 0.20,
      params: function () { return AbilityAPI.params("chargeTicks", 20, "radius", 2.0, "effectType", "slowness", "effectAmplifier", 3); } },
    { ability: "weapon_spin", cd: 200, min: 0, max: 5, shape: "self", size: 4, hitAt: 56, pct: 0.25,
      params: function () { return AbilityAPI.params("chargeTicks", 20, "activeTicks", 36, "radius", 4.0, "damagePerTick", 2.5); } }
];
