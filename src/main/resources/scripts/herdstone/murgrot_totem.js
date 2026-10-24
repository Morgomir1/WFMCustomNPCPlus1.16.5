// Тотем Мургрота. В контейнере вторым после hs_core.js. Скорость 0, On Attack = Ничего, Respawn = нет.
// Тотем не ломается: каждый удар игрока звучит его нотой. Ноту, высоту и частицы пишет Мургрот
// при призыве (hs_note, hs_pitch, hs_particle). Пока Мургрот слушает (song = listen), удар —
// следующая нота песни: верная двигает seq_pos, последняя пишет song = done (итог разбирает tick
// Мургрота). Неверная бьёт всех в WRONG_R вокруг этого тотема, песнь идёт дальше с того же места.
var BOSS_TAG = "hs_murgrot";
var COPY_TAG = "hs_murgrot_copy";
var NOTE_SOUND = "minecraft:block.note_block.bell";
var SAME_SWING = 15;   // тиков: второй удар по той же ноте — тот же взмах
var WRONG_R = 4, WRONG_STRIKE = 0.3;

function damaged(e) {
    e.setCanceled(true);
    var src = e.source;
    if (src == null || !src.typeOf(1)) return;
    var totem = e.npc;
    var d = totem.getStoreddata();
    if (!d.has("hs_note")) return;
    var note = Number(String(d.get("hs_note")));
    var w = totem.getWorld();
    var boss = findBoss(totem);
    var b = boss == null ? null : boss.getStoreddata();
    if (b == null || text(b, "song") != "listen") {
        w.playSoundAt(totem.getPos(), NOTE_SOUND, 3.0, Number(String(d.get("hs_pitch"))));
        return;
    }
    var now = Number(w.getTotalTime());
    if (note == Number(text(b, "last_note")) && now - Number(text(b, "last_at")) < SAME_SWING) return;
    var seq = text(b, "seq").split(",");
    var pos = Number(text(b, "seq_pos"));
    if (note != Number(seq[pos])) {
        wrongNote(totem, now);
        return;
    }
    w.playSoundAt(totem.getPos(), NOTE_SOUND, 3.0, Number(String(d.get("hs_pitch"))));
    pos++;
    b.put("seq_pos", String(pos));
    b.put("last_note", String(note));
    b.put("last_at", String(now));
    w.spawnParticle(String(d.get("hs_particle")), totem.getX(), totem.getY() + 1.5, totem.getZ(), 0.4, 0.6, 0.4, 0.02, 20);
    if (pos >= seq.length) b.put("song", "done");
}

function wrongNote(totem, now) {
    var d = totem.getStoreddata();
    if (now - Number(text(d, "wrong_at")) < SAME_SWING) return;
    d.put("wrong_at", String(now));
    var w = totem.getWorld();
    w.playSoundAt(totem.getPos(), "minecraft:block.note_block.didgeridoo", 3.0, 0.5);
    w.playSoundAt(totem.getPos(), "minecraft:entity.generic.explode", 2.0, 1.2);
    w.spawnParticle("explosion", totem.getX(), totem.getY() + 1, totem.getZ(), 1.5, 0.5, 1.5, 0, 6);
    var ps = hsPlayersAt(totem, totem.getX(), totem.getY(), totem.getZ(), WRONG_R);
    for (var i = 0; i < ps.length; i++) {
        hsStrike(ps[i], WRONG_STRIKE);
        ps[i].message("§cФальшивая нота! Тотем бьёт всех рядом.");
    }
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
