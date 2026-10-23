// Сердце жёлоба (HP 150, Respawn = Нет, скорость 0, On Attack = Ничего).
// Без hs_core.js: один этот файл. Потолок 15 за удар, иначе одно сердце сносят в одиночку.
function damaged(e) {
    e.damage = Math.min(e.damage, 15);
}
