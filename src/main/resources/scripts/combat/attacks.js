// combat/attacks.js — общий язык атак для мобов и боссов CustomNPCs: сигналы, прерывание, приёмы и цепочки.
// Ставится в список скриптов NPC перед файлом моба. Сам ничего не делает: файл моба описывает его цепочки и зовёт
// atTick (вместо своих приёмов), atTimer, atDamaged, atInit; обычный удар моба с цепочками отменяет сам файл моба.
// От ивента не зависит: ни MO, ни поля. Может определить у себя atSpare(p) — кого удары не задевают (сейф-зона).
//
// Палитра одна на всех, меняется только здесь. Цвет говорит, что за атака, форма — где она бьёт:
//   зелёный  — прерываемая: кольцо вокруг моба на весь замах, зона удара тоже зелёная;
//   золотой  — окно прерывания: второе кольцо, вспышка и звук тому, на кого замах (звук — только у ранга 2);
//   красный  — непрерываемая: конус или полоса по направлению удара;
//   фиолетовый — по области: зона на земле; свойство, сочетается и с зелёным кольцом.
// Кольцо, конус, полоса и залитый круг различимы и без цвета: красный и зелёный при красно-зелёной слепоте почти одно.
// Рисует их мод wfmtelegraph (TelegraphAPI), он уже стоит на сервере; частица dust с цветом из скрипта не вызывается.
//
// Ранг моба (rank в описании): 0 — рядовой, сигнал только у крупных приёмов (big: 1), без звука; 1 — элитный, сигнал
// у всех приёмов; 2 — единичный и босс, ещё и звук окна. Так в толпе нет супа из частиц, а каждый слышит свои окна.
//
// Прерывание: удар рукой в золотом окне отменяет приём и цепочку, моб стоит в стане AT_STUN. Засчитывается, только если
// этот игрок перед тем не бил того же моба AT_CALM тиков: зарядку удара скрипт в момент попадания уже не видит, а так
// закликивание окна не берёт. Прерываемы приёмы с sig "open", и это не редкость (замысел Антона 4 ноября): почти все удары
// вблизи прерываются, но окно маленькое, AT_WINDOW в конце замаха; его ловят по длине зелёного кольца, а не по вспышке.
// Красные — те, от которых только уходят. Сбитый моб AT_GUARD тиков после стана собран: его приёмы идут красными, без окна,
// иначе его держали бы в стане вечно.
//
// Цепочки: у моба chains — список { min, max, steps, fork, weight, cd }. Следующая выбирается случайно из тех, что подходят
// по расстоянию до цели (min..max, по горизонтали), та же дважды подряд — только если другой нет; cd — откат цепочки в тиках
// (сеть, рывок: иначе их бросали бы каждую цепочку). Между цепочками
// передышка rest ([от, до] тиков, по умолчанию AT_REST) — окно игрока. fork — развилка: после шага after с шансом chance
// цепочка идёт по своим шагам steps; у развилки свой сигнал, обычно прерываемый приём с золотым окном.
// Шаг — имя приёма из AT_MOVES или { move: имя, ...поля поверх }, params сливаются.
//
// Приём: sig — "open" или "hard"; area: 1 — по области; windup — замах, тиков до удара; recover — пауза после;
// big: 1 — сигнал и у рядового. Способность мода: ability, params (chargeTicks ставит windup), active — сколько она идёт
// после замаха (по умолчанию params.activeTicks), line: 1 — своя полоса до цели (у способности её нет); window — своё окно
// вместо AT_WINDOW.
// Свой удар скрипта: shape "arc" (дуга перед собой: reach, half), "circle" (круг вокруг: reach) или "line" (полоса от точки
// замаха по его направлению: reach — длина, width — ширина), hit — тик удара (по умолчанию windup), dmg — обычный удар моба
// (броня, щит и отбрасывание, как от удара рукой: AbilityCombatHelper.mobStrike; на старом JAR — урон без источника, его
// щит не держит), pct — доля здоровья мимо брони, push — отбрасывание сверху, slow — секунд замедления;
// у приёма способности свой удар рисует свою зону, а зону способности выключает: знак показывает то, что бьёт;
// lunge — шаг вперёд по направлению замаха в момент удара, до стольких блоков, но не ближе AT_LUNGE_GAP к цели, не в стену
// и не с обрыва: иначе от замаха уходят шагом назад и моба просто не подпускают. Конус замаха показывает reach + lunge.
var AtAbility = Java.type("noppes.npcs.abilities.AbilityAPI");
var AtTelegraph = Java.type("noppes.npcs.telegraph.TelegraphAPI");
var AtCombat = Java.type("noppes.npcs.abilities.AbilityCombatHelper");
var AtNpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();

// Палитра, ARGB.
var AT_GREEN = 0xC040E060 | 0, AT_GOLD = 0xE0FFC830 | 0, AT_RED = 0xC0FF3030 | 0, AT_PURPLE = 0xB0B050FF | 0;
// Кольцо прерываемой атаки и кольцо окна вокруг ног моба: внешний и внутренний радиус.
var AT_RING = [1.6, 1.2], AT_GOLD_RING = [2.2, 1.7];
var AT_WINDOW = 8, AT_CALM = 10, AT_STUN = 40, AT_GUARD = 60, AT_STUN_ID = "wfm:stun";
var AT_REST = [16, 30], AT_LUNGE_GAP = 1.2;
var AT_WINDOW_SOUND = "minecraft:block.note_block.bell", AT_BREAK_SOUND = "minecraft:block.anvil.land";
var AT_T_WINDOW = 31, AT_T_STRIKE = 32, AT_T_NEXT = 33;
var AT_LOGGED = {};

// Общий набор приёмов: числа по умолчанию, моб правит их в своих шагах.
var AT_MOVES = {
    // Рубящий: дуга перед собой с шагом вперёд. Прерывается: зелёное кольцо и зелёный конус.
    slash: { sig: "open", windup: 14, recover: 6, shape: "arc", reach: 4.5, half: 60, lunge: 2, dmg: 6, push: 1 },
    // Тычок: короткий выпад вперёд. Прерывается: кольцо и полоса способности зелёные.
    thrust: { sig: "open", ability: "stake_thrust", windup: 10, recover: 8,
        params: { distance: 3.5, activeTicks: 4, damage: 5.0, knockback: 0.6, hitRadius: 1.1 } },
    // Толчок: тот же выпад, короче, слабее и с сильным отбрасыванием. Прерывается.
    shove: { sig: "open", ability: "stake_thrust", windup: 12, recover: 10,
        params: { distance: 2.5, activeTicks: 4, damage: 3.0, knockback: 1.6, hitRadius: 1.3 } },
    // Рывок: по прямой к цели. Красная полоса способности.
    charge: { sig: "hard", ability: "dash", windup: 24, recover: 12,
        params: { distance: 10.0, activeTicks: 8, damage: 5.0, knockback: 1.0 } },
    // Прыжок на точку цели, удар по кругу при приземлении. Зелёное кольцо — прерывается; фиолетовый круг приземления.
    leap: { sig: "open", area: 1, ability: "jump_slam", windup: 24, recover: 12,
        params: { activeTicks: 9, damage: 6.0, knockback: 1.2, landRadius: 3.0, arcHeight: 3.0, maxRange: 10.0 } },
    // Удар по земле: круг вокруг себя. Фиолетовый круг.
    slam: { sig: "hard", area: 1, windup: 20, recover: 12, shape: "circle", reach: 3.5, dmg: 6, push: 2 },
    // Вращение с оружием: круг вокруг себя, бьёт, пока крутится. Фиолетовый круг способности.
    spin: { sig: "hard", area: 1, ability: "weapon_spin", windup: 16, recover: 14,
        params: { activeTicks: 24, radius: 3.5, damagePerTick: 2.0 } },
    // Сеть: летит на точку цели и замедляет всех в круге. Фиолетовый круг на месте падения.
    net: { sig: "hard", area: 1, ability: "net_throw", windup: 20, active: 12, recover: 10,
        params: { radius: 2.5, effectDuration: 30, effectAmplifier: 2, landSound: "minecraft:entity.leash_knot.place" } },
    // Бросок топора: летит до цели и возвращается. Своя красная полоса до цели.
    throw: { sig: "hard", ability: "axe_throw", windup: 16, active: 26, recover: 6, line: 1,
        params: { maxRange: 16.0, hitRadius: 1.4, outboundTicks: 14, holdTicks: 2, returnTicks: 10, arcHeight: 0.4,
            damagePercent: 0.1, effectDuration: 30, effectAmplifier: 0 } }
};

function atGet(npc, k, def) { var d = npc.getTempdata(); return d.has(k) ? String(d.get(k)) : def; }
function atNum(npc, k) { return Number(atGet(npc, k, "0")); }
function atPut(npc, k, v) { npc.getTempdata().put(k, String(v)); }
function atLogOnce(key, text) {
    if (AT_LOGGED[key]) return;
    AT_LOGGED[key] = true;
    log("attacks: " + text);
}
function atNow(npc) { return Number(npc.getWorld().getTotalTime()); }
function atFlat(e, x, z) { var dx = e.getX() - x, dz = e.getZ() - z; return Math.sqrt(dx * dx + dz * dz); }

// Таймер CustomNPCs с числом N срабатывает на N+1-е обновление; здесь ticks — сколько тиков на самом деле.
function atArm(npc, id, ticks) { npc.getTimers().forceStart(id, Math.max(0, Math.round(ticks) - 1), false); }

// Приём шага: поля из набора, поверх — поля шага; params сливаются.
function atMove(step) {
    var own = typeof step == "string" ? null : step, name = own == null ? step : own.move, base = AT_MOVES[name];
    if (base == null) return null;
    var m = {}, p = {}, k;
    for (k in base) m[k] = base[k];
    if (base.params != null) for (k in base.params) p[k] = base.params[k];
    if (own != null) {
        for (k in own) if (k != "params") m[k] = own[k];
        if (own.params != null) for (k in own.params) p[k] = own.params[k];
    }
    m.params = p;
    m.name = name;
    if (m.active == null) m.active = p.activeTicks > 0 ? p.activeTicks : 0;
    return m;
}

function atSteps(chain, forked) {
    if (!forked || chain.fork == null) return chain.steps;
    return chain.steps.slice(0, chain.fork.after).concat(chain.fork.steps);
}

// Цепочка по расстоянию и не на откате; прошлая — только если другой нет.
function atPick(npc, mob, dist, last, now) {
    var fit = [], i;
    for (i = 0; i < mob.chains.length; i++) {
        var c = mob.chains[i];
        if (dist >= c.min && dist <= c.max && now >= atNum(npc, "at_cd" + i)) fit.push(i);
    }
    if (fit.length > 1 && fit.indexOf(last) >= 0) fit.splice(fit.indexOf(last), 1);
    if (fit.length == 0) return -1;
    var total = 0;
    for (i = 0; i < fit.length; i++) total += mob.chains[fit[i]].weight > 0 ? mob.chains[fit[i]].weight : 1;
    var r = Math.random() * total;
    for (i = 0; i < fit.length; i++) {
        r -= mob.chains[fit[i]].weight > 0 ? mob.chains[fit[i]].weight : 1;
        if (r < 0) return fit[i];
    }
    return fit[fit.length - 1];
}

function atRestTicks(mob) {
    var r = mob.rest != null ? mob.rest : AT_REST;
    return r[0] + Math.floor(Math.random() * (r[1] - r[0] + 1));
}

function atShows(mob, m) { return mob.rank >= 1 || m.big == 1; }
function atWinTicks(m) { return Math.min(m.window > 0 ? m.window : AT_WINDOW, m.windup); }
function atZoneColor(m) { return m.area == 1 ? AT_PURPLE : m.sig == "open" ? AT_GREEN : AT_RED; }

function atBusy(npc) { return atGet(npc, "at_chain", "-1") != "-1"; }

// Вызывать из tick моба с его целью: начинает цепочку, если моб свободен. Пока цепочка идёт, стоит стан или передышка, —
// ничего. Возвращает true, если моб занят языком атак.
function atTick(npc, mob, t, now) {
    if (now < atNum(npc, "at_stun") || atBusy(npc)) return true;
    if (now < atNum(npc, "at_rest") || AtAbility.isBusy(npc) || t == null || !t.isAlive()) return false;
    var c = atPick(npc, mob, atFlat(t, npc.getX(), npc.getZ()), Number(atGet(npc, "at_last", "-1")), now);
    if (c < 0) return false;
    var chain = mob.chains[c], forked = chain.fork != null && Math.random() < chain.fork.chance;
    if (chain.cd > 0) atPut(npc, "at_cd" + c, now + chain.cd);
    atPut(npc, "at_chain", c);
    atPut(npc, "at_fork", forked ? 1 : 0);
    atPut(npc, "at_target", t.getUUID());
    atStep(npc, mob, 0);
    return true;
}

function atCurrent(npc, mob) {
    var chain = mob.chains[Number(atGet(npc, "at_chain", "-1"))];
    if (chain == null) return null;
    var steps = atSteps(chain, atGet(npc, "at_fork", "0") == "1"), step = steps[atNum(npc, "at_step")];
    return step == null ? null : atMove(step);
}

function atStep(npc, mob, i) {
    var chain = mob.chains[Number(atGet(npc, "at_chain", "-1"))];
    if (chain == null) return;
    atUnfreeze(npc);
    atClearTg(npc);
    var steps = atSteps(chain, atGet(npc, "at_fork", "0") == "1");
    if (i >= steps.length) {
        atEnd(npc, atRestTicks(mob));
        return;
    }
    var t = npc.getWorld().getEntity(atGet(npc, "at_target", "")), cur = npc.getAttackTarget();
    if (t == null || !t.isAlive() || cur == null || String(cur.getUUID()) != String(t.getUUID())) {
        atEnd(npc, AT_REST[0]);
        return;
    }
    var m = atMove(steps[i]);
    if (m == null) {
        atLogOnce("move", "нет приёма " + JSON.stringify(steps[i]));
        atEnd(npc, AT_REST[0]);
        return;
    }
    var now = atNow(npc), x = npc.getX(), y = npc.getY(), z = npc.getZ(), yaw = AtCombat.computeYaw(t.getX() - x, t.getZ() - z);
    var show = atShows(mob, m);
    // Собранный после сбитого приёма бьёт без окна, и знак у него честно красный.
    if (m.sig == "open" && now < atNum(npc, "at_guard")) m.sig = "hard";
    atPut(npc, "at_step", i);
    atPut(npc, "at_yaw", yaw);
    atPut(npc, "at_x", x);
    atPut(npc, "at_z", z);
    npc.setRotation(yaw);
    if (m.ability != null) {
        if (!AtAbility.start(npc, m.ability, t, atParams(m, show))) {
            atEnd(npc, AT_REST[0]);
            return;
        }
    } else {
        atFreeze(npc, x, y, z);
    }
    if (show) atSignal(npc, m, t, x, y, z, yaw);
    if (m.sig == "open") {
        var win = atWinTicks(m);
        atPut(npc, "at_win_from", now + m.windup - win);
        atPut(npc, "at_win_to", now + m.windup);
        atArm(npc, AT_T_WINDOW, m.windup - win);
    } else {
        atPut(npc, "at_win_from", 0);
        atPut(npc, "at_win_to", 0);
    }
    if (m.shape != null) atArm(npc, AT_T_STRIKE, m.hit > 0 ? m.hit : m.windup);
    atArm(npc, AT_T_NEXT, Math.max(m.windup + m.active + (m.recover > 0 ? m.recover : 0), m.hit > 0 ? m.hit + 1 : 0));
}

// Числа способности: замах — windup; свою фигуру она рисует цветом языка, а без сигнала не рисует ничего.
function atParams(m, show) {
    var p = AtAbility.params(), k;
    for (k in m.params) p.put(k, m.params[k]);
    p.put("chargeTicks", m.windup);
    p.put("telegraph", show && m.shape == null ? 1 : 0);
    p.put("telegraphColor", atZoneColor(m));
    return p;
}

function atSignal(npc, m, t, x, y, z, yaw) {
    var color = atZoneColor(m);
    if (m.sig == "open") atTg(npc, AtTelegraph.ring(npc, x, y, z, AT_RING[0], AT_RING[1], m.windup, AT_GREEN));
    if (m.line == 1) {
        var len = Math.min(atFlat(t, x, z) + 1, m.params.maxRange > 0 ? m.params.maxRange : 16);
        atTg(npc, AtTelegraph.line(npc, x, y, z, yaw, len, (m.params.hitRadius > 0 ? m.params.hitRadius : 1) * 2, m.windup, color));
    }
    if (m.shape == null) return;
    var until = m.hit > 0 ? m.hit : m.windup;
    if (m.shape == "circle") {
        // Круг вокруг себя идёт за мобом: бьёт там, где он стоит в момент удара.
        var id = AtTelegraph.circle(npc, x, y, z, m.reach, until, color);
        AtTelegraph.follow(id, npc);
        atTg(npc, id);
    } else if (m.shape == "line") {
        atTg(npc, AtTelegraph.line(npc, x, y, z, yaw, m.reach, m.width, until, color));
    } else {
        atTg(npc, AtTelegraph.cone(npc, x, y, z, yaw, m.reach + (m.lunge > 0 ? m.lunge : 0), m.half, until, color));
    }
}

// Окно открылось: золотое кольцо и вспышка всем рядом, звук — только тому, на кого замах, и только у ранга 2.
function atWindow(npc, mob, m) {
    // Окно открыто с того тика, как его видно, даже если таймер сработал на тик раньше расчёта.
    atPut(npc, "at_win_from", Math.min(atNum(npc, "at_win_from"), atNow(npc)));
    if (!atShows(mob, m)) return;
    var x = npc.getX(), y = npc.getY(), z = npc.getZ(), w = npc.getWorld(), ticks = atWinTicks(m);
    atTg(npc, AtTelegraph.ring(npc, x, y, z, AT_GOLD_RING[0], AT_GOLD_RING[1], ticks, AT_GOLD));
    w.spawnParticle("flash", x, y + 1.2, z, 0, 0, 0, 0, 1);
    if (mob.rank < 2) return;
    var t = w.getEntity(atGet(npc, "at_target", ""));
    if (t != null && t.typeOf(1)) t.playSound(AT_WINDOW_SOUND, 1.0, 1.6);
}

// Свой удар скрипта: дуга — от точки и по направлению замаха, круг — вокруг того места, где моб стоит в момент удара.
function atStrike(npc, m) {
    var arc = m.shape == "arc", line = m.shape == "line", w = npc.getWorld();
    if (m.lunge > 0) atLunge(npc, m);
    var fixed = arc || line, ox = fixed ? atNum(npc, "at_x") : npc.getX(), oz = fixed ? atNum(npc, "at_z") : npc.getZ();
    var yaw = atNum(npc, "at_yaw");
    if (m.ability == null) {
        npc.swingMainhand();
        w.playSoundAt(npc.getPos(), arc ? "minecraft:entity.player.attack.sweep" : "minecraft:entity.generic.explode", 1.0, arc ? 0.8 : 1.2);
        if (!arc) w.spawnParticle("explosion", ox, npc.getY() + 0.3, oz, m.reach / 2, 0.2, m.reach / 2, 0, 4);
    }
    var ps = atPlayersAt(npc, ox, npc.getY(), oz, m.reach + (line ? 1 : 0));
    for (var i = 0; i < ps.length; i++) {
        var p = ps[i];
        if (arc && !AtCombat.isInFrontCone(ox, oz, yaw, p, m.half)) continue;
        if (line && !atOnLine(p, ox, oz, yaw, m.reach, m.width / 2)) continue;
        if (m.dmg > 0) atHit(npc, p, m.dmg);
        if (m.pct > 0) atStrikePct(p, m.pct);
        if (!p.isAlive()) continue;
        if (m.push > 0) p.knockback(Math.round(m.push), AtCombat.computeYaw(p.getX() - ox, p.getZ() - oz));
        if (m.slow > 0) p.addPotionEffect(2, m.slow, 0, false);
    }
}

// Удар моба, как его удар рукой: броня, щит и отбрасывание. На старом JAR CustomNPCs помощника нет — урон без источника.
function atHit(npc, p, dmg) {
    var strike = null;
    try { strike = AtCombat.mobStrike; } catch (err) {}
    if (strike != null) AtCombat.mobStrike(npc, p, dmg);
    else {
        atLogOnce("mob_strike", "нет AbilityCombatHelper.mobStrike: старый JAR, удар без источника, щит его не держит");
        p.damage(dmg);
    }
}

// На полосе: от точки замаха вперёд не дальше len, вбок не дальше half; чуть позади точки — тоже.
function atOnLine(p, sx, sz, yaw, len, half) {
    var rad = (yaw + 90) * Math.PI / 180, fx = Math.cos(rad), fz = Math.sin(rad), dx = p.getX() - sx, dz = p.getZ() - sz;
    var along = dx * fx + dz * fz;
    return along >= -1 && along <= len && Math.abs(dx * fz - dz * fx) <= half;
}

// Шаг вперёд по направлению замаха: к цели, но не вплотную; новая точка — начало дуги.
function atLunge(npc, m) {
    var w = npc.getWorld(), t = w.getEntity(atGet(npc, "at_target", ""));
    if (t == null) return;
    var x = npc.getX(), y = npc.getY(), z = npc.getZ(), rad = (atNum(npc, "at_yaw") + 90) * Math.PI / 180;
    var fx = Math.cos(rad), fz = Math.sin(rad), d = Math.min(m.lunge, (t.getX() - x) * fx + (t.getZ() - z) * fz - AT_LUNGE_GAP);
    if (!(d > 0.2)) return;
    var nx = x + fx * d, nz = z + fz * d, ny = AtCombat.findGroundY(w, nx, nz, y);
    if (Math.abs(ny - y) > 1.1 || !AtCombat.canStandAt(w, nx, ny, nz)) return;
    AtCombat.holdInPlace(npc, nx, ny, nz);
    atPut(npc, "at_x", nx);
    atPut(npc, "at_z", nz);
}

function atPlayersAt(npc, x, y, z, r) {
    var list = npc.getWorld().getNearbyEntities(AtNpcAPI.getIPos(x, y, z), Math.ceil(r) + 1, 1), out = [];
    for (var i = 0; i < list.length; i++) {
        var p = list[i];
        if (!p.isAlive() || p.getGamemode() == 1 || p.getGamemode() == 3) continue;
        if (typeof atSpare == "function" && atSpare(p)) continue;
        if (atFlat(p, x, z) <= r) out.push(p);
    }
    return out;
}

// Доля здоровья мимо брони; последнее здоровье — чистым уроном, чтобы смерть была смертью, а не setHealth(0).
function atStrikePct(p, pct) {
    var dmg = p.getMaxHealth() * pct;
    if (p.getHealth() - dmg < 1) {
        p.clearPotionEffects();
        p.setHealth(1);
        AtCombat.dealPureDamage(p, 100, true);
        return;
    }
    p.setHealth(p.getHealth() - dmg);
    p.getWorld().playSoundAt(p.getPos(), "minecraft:entity.player.hurt", 1.0, 0.7);
}

// Свой удар скрипта стоит на месте, пока замахивается: иначе мод ведёт его к цели и фигура уезжает от него.
function atFreeze(npc, x, y, z) {
    if (atGet(npc, "at_speed", "") == "") atPut(npc, "at_speed", npc.getAi().getWalkingSpeed());
    npc.getAi().setWalkingSpeed(0);
    AtCombat.holdInPlace(npc, x, y, z);
}

function atUnfreeze(npc) {
    var s = atGet(npc, "at_speed", "");
    if (s == "") return;
    npc.getAi().setWalkingSpeed(Number(s));
    npc.getTempdata().remove("at_speed");
}

function atTg(npc, id) {
    if (id == null || String(id) == "") return;
    var all = atGet(npc, "at_tg", "");
    atPut(npc, "at_tg", all == "" ? String(id) : all + ";" + id);
}

function atClearTg(npc) {
    var all = atGet(npc, "at_tg", "");
    if (all == "") return;
    var ids = all.split(";");
    for (var i = 0; i < ids.length; i++) AtTelegraph.removeNear(npc, ids[i]);
    npc.getTempdata().remove("at_tg");
}

function atEnd(npc, rest) {
    var tm = npc.getTimers();
    tm.stop(AT_T_WINDOW);
    tm.stop(AT_T_STRIKE);
    tm.stop(AT_T_NEXT);
    atClearTg(npc);
    atUnfreeze(npc);
    if (atBusy(npc)) atPut(npc, "at_last", atGet(npc, "at_chain", "-1"));
    atPut(npc, "at_chain", -1);
    atPut(npc, "at_win_from", 0);
    atPut(npc, "at_win_to", 0);
    atPut(npc, "at_rest", atNow(npc) + rest);
}

// Сбит: приём и цепочка отменены, моб в стане; новая цепочка — не раньше, чем стан пройдёт.
function atInterrupt(npc, p) {
    var now = atNow(npc), w = npc.getWorld();
    AtAbility.cancel(npc);
    atEnd(npc, AT_STUN);
    atPut(npc, "at_stun", now + AT_STUN);
    atPut(npc, "at_guard", now + AT_STUN + AT_GUARD);
    AtCombat.applyNamedEffect(npc, AT_STUN_ID, AT_STUN, 0);
    w.playSoundAt(npc.getPos(), AT_BREAK_SOUND, 0.7, 1.6);
    w.spawnParticle("crit", npc.getX(), npc.getY() + 1.2, npc.getZ(), 0.5, 0.5, 0.5, 0.2, 20);
}

// Вызывать из damaged моба для удара рукой, после всех его отмен. Возвращает true, если удар сбил приём.
function atDamaged(e, mob) {
    var npc = e.npc, p = e.source;
    if (p == null || !p.typeOf(1)) return false;
    var now = atNow(npc), key = "at_hit_" + p.getUUID(), last = Number(atGet(npc, key, "-1000000"));
    atPut(npc, key, now);
    if (!atBusy(npc) || now < atNum(npc, "at_win_from") || now >= atNum(npc, "at_win_to")) return false;
    if (now - last < AT_CALM) return false;
    atInterrupt(npc, p);
    return true;
}

// Вызывать из timer моба первым: свои таймеры забирает и возвращает true.
function atTimer(e, mob) {
    var id = e.id;
    if (id != AT_T_WINDOW && id != AT_T_STRIKE && id != AT_T_NEXT) return false;
    var npc = e.npc;
    if (!npc.isAlive() || mob == null || mob.chains == null) return true;
    if (id == AT_T_NEXT) {
        atStep(npc, mob, atNum(npc, "at_step") + 1);
        return true;
    }
    var m = atCurrent(npc, mob);
    if (m == null) return true;
    if (id == AT_T_WINDOW) atWindow(npc, mob, m);
    else atStrike(npc, m);
    return true;
}

// Вызывать из init моба: перезагруженный скрипт не помнит замаха — шаг вернуть, скорость отдать.
function atInit(npc) {
    atUnfreeze(npc);
    var d = npc.getTempdata(), keys = ["at_chain", "at_fork", "at_step", "at_target", "at_win_from", "at_win_to", "at_rest", "at_stun", "at_guard", "at_tg"];
    for (var i = 0; i < keys.length; i++) d.remove(keys[i]);
}
