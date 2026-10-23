// Гор-щитоносец. Третий файл после hs_mob.js.
// Щит 3 с: спереди в конусе 120° урон не проходит.
CASTS = [
    { ability: "shield_block", cd: 140, min: 0, max: 6, shape: "", pct: 0,
      params: function () { return AbilityAPI.params("activeTicks", 60, "blockAngle", 120.0); } }
];
