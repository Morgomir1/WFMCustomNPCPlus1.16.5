// Гор-рубака. Третий файл: hs_core.js → hs_mob.js → этот.
// Рывок 8 блоков, полоса 1,2 с, задетым −15% HP.
CASTS = [
    { ability: "dash", cd: 120, min: 3, max: 10, shape: "line", len: 8, size: 1.5, hitAt: 30, pct: 0.15,
      params: function () { return AbilityAPI.params("chargeTicks", 24, "distance", 8.0, "damage", 6.0); } }
];
