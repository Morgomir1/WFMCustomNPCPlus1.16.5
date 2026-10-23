// Унгор-лучник. Третий файл после hs_mob.js.
// Плевок: лужа r 2,5 на 8 с. effectId — строка, effectDuration — тики (160 = 8 с).
CASTS = [
    { ability: "crimson_blob", cd: 200, min: 5, max: 20, shape: "", pct: 0,
      params: function () {
          return AbilityAPI.params("chargeTicks", 20, "landRadius", 2.5, "zoneTicks", 160, "damage", 2.0,
              "effectId", "minecraft:poison", "effectDuration", 160, "zoneColor", 0x8040C040);
      } }
];
