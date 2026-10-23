// Центигор. Третий файл после hs_mob.js.
// Таран на 18 блоков, −30% HP на полосе.
CASTS = [
    { ability: "dash", cd: 160, min: 6, max: 20, shape: "line", len: 18, size: 1.8, hitAt: 40, pct: 0.30,
      params: function () { return AbilityAPI.params("chargeTicks", 30, "distance", 18.0, "damage", 8.0); } }
];
