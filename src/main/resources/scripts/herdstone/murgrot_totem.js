// Тотем Мургрота. Один этот файл, без hs_core.js. Скорость 0, On Attack = Ничего, Respawn = нет.
// Тотем не ломается: каждый удар игрока звучит его нотой. Ноту, высоту и частицы пишет Мургрот
// при призыве (hs_note, hs_pitch, hs_particle). Пока Мургрот слушает (song = listen), удар —
// следующая нота песни: верная двигает seq_pos, неверная пишет song = fail, последняя — song = done.
// Итог разбирает tick Мургрота.
var BOSS_TAG = "hs_murgrot";
var COPY_TAG = "hs_murgrot_copy";
var NOTE_SOUND = "minecraft:block.note_block.bell";
var SAME_SWING = 15;   // тиков: второй удар по той же ноте — тот же взмах, не ошибка

function damaged(e) {
    e.setCanceled(true);
    var src = e.source;
    if (src == null || !src.typeOf(1)) return;
    var totem = e.npc;
    var d = totem.getStoreddata();
    if (!d.has("hs_note")) return;
    var note = Number(String(d.get("hs_note")));
    var w = totem.getWorld();
    w.playSoundAt(totem.getPos(), NOTE_SOUND, 3.0, Number(String(d.get("hs_pitch"))));
    var boss = findBoss(totem);
    if (boss == null) return;
    var b = boss.getStoreddata();
    if (text(b, "song") != "listen") return;
    var now = Number(w.getTotalTime());
    if (note == Number(text(b, "last_note")) && now - Number(text(b, "last_at")) < SAME_SWING) return;
    var seq = text(b, "seq").split(",");
    var pos = Number(text(b, "seq_pos"));
    if (note != Number(seq[pos])) {
        b.put("song", "fail");
        return;
    }
    pos++;
    b.put("seq_pos", String(pos));
    b.put("last_note", String(note));
    b.put("last_at", String(now));
    w.spawnParticle(String(d.get("hs_particle")), totem.getX(), totem.getY() + 1.5, totem.getZ(), 0.4, 0.6, 0.4, 0.02, 20);
    if (pos >= seq.length) b.put("song", "done");
}

// Копия и тотем, сохранённые в клонер с самого Мургрота, несут его метку в NBT — их пропускаем.
function findBoss(totem) {
    var list = totem.getWorld().getNearbyEntities(totem.getPos(), 48, 2);
    for (var i = 0; i < list.length; i++) {
        var n = list[i];
        if (!n.isAlive() || !n.hasTag(BOSS_TAG) || n.hasTag(COPY_TAG)) continue;
        if (n.getStoreddata().has("hs_note")) continue;
        return n;
    }
    return null;
}

function text(data, key) {
    return data.has(key) ? String(data.get(key)) : "";
}
