// Тотем Мургрота. В контейнере вторым после hs_core.js. Скорость 0, On Attack = Ничего, Respawn = нет.
// Тотем не ломается: каждый удар игрока звучит его нотой. Ноту, высоту и частицы пишет Мургрот
// при призыве (hs_note, hs_pitch, hs_particle). Пока Мургрот слушает (song = listen), удар —
// следующая нота песни: верная двигает seq_pos, последняя пишет song = done. Неверная сразу
// ломает песнь (song = wrong) и взрывается: все в WRONG_R вокруг этого тотема теряют WRONG_STRIKE,
// вздрагивают и отлетают прочь.
// Итог разбирает tick Мургрота.
var BOSS_TAG = "hs_murgrot";
var COPY_TAG = "hs_murgrot_copy";
var NOTE_SOUND = "minecraft:block.note_block.bell";
var SAME_SWING = 15;   // тиков: второй удар по той же ноте — тот же взмах
var WRONG_R = 8, WRONG_STRIKE = 0.5, WRONG_PUSH = 1;   // толчок: knockback(1) — блока на три-четыре

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
        soundAround(totem, NOTE_SOUND, 1.0, Number(String(d.get("hs_pitch"))));
        return;
    }
    var now = Number(w.getTotalTime());
    if (note == Number(text(b, "last_note")) && now - Number(text(b, "last_at")) < SAME_SWING) return;
    var seq = text(b, "seq").split(",");
    var pos = Number(text(b, "seq_pos"));
    if (note != Number(seq[pos])) {
        b.put("song", "wrong");
        wrongNote(totem);
        return;
    }
    soundAround(totem, NOTE_SOUND, 1.0, Number(String(d.get("hs_pitch"))));
    pos++;
    b.put("seq_pos", String(pos));
    b.put("last_note", String(note));
    b.put("last_at", String(now));
    w.spawnParticle(String(d.get("hs_particle")), totem.getX(), totem.getY() + 1.5, totem.getZ(), 0.4, 0.6, 0.4, 0.02, 20);
    if (pos >= seq.length) b.put("song", "done");
}

// Песнь уже не listen, так что второй удар того же взмаха сюда не попадёт.
function wrongNote(totem) {
    var w = totem.getWorld();
    soundAround(totem, "minecraft:block.note_block.didgeridoo", 1.0, 0.5);
    soundAround(totem, "minecraft:entity.generic.explode", 1.0, 0.8);
    TelegraphAPI.circle(totem, totem.getX(), totem.getY(), totem.getZ(), WRONG_R, 10, 0xC0FF0000 | 0);
    w.spawnParticle("explosion_emitter", totem.getX(), totem.getY() + 1, totem.getZ(), 0, 0, 0, 0, 1);
    w.spawnParticle("explosion", totem.getX(), totem.getY() + 1, totem.getZ(), WRONG_R * 0.6, 0.8, WRONG_R * 0.6, 0, 24);
    var ps = hsPlayersAt(totem, totem.getX(), totem.getY(), totem.getZ(), WRONG_R);
    for (var i = 0; i < ps.length; i++) {
        var p = ps[i];
        hsStrike(p, WRONG_STRIKE);
        p.message("§cФальшивая нота! Тотем взрывается.");
        if (!p.isAlive()) continue;
        // hsStrike снимает здоровье напрямую, а вздрагивание экрана клиент показывает только от урона
        // через hurt: крошечный магический урон ради него, затем толчок прочь от тотема.
        Combat.dealPureDamage(p, 0.01, true);
        p.knockback(WRONG_PUSH, Combat.computeYaw(p.getX() - totem.getX(), p.getZ() - totem.getZ()));
    }
}

// playSoundAt CustomNPCs слышат только в 16 блоках от точки; ноту тотема должна слышать вся арена.
function soundAround(totem, sound, volume, pitch) {
    var list = totem.getWorld().getNearbyEntities(totem.getPos(), 48, 1);
    for (var i = 0; i < list.length; i++) list[i].playSound(sound, volume, pitch);
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
