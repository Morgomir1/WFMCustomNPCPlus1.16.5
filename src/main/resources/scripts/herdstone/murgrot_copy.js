// Мургрот-копия (HP 1, Respawn = Нет). В контейнере вторым после hs_core.js.
// Удар по копии бьёт по ударившему. Имя над головой и модель — как у оригинала,
// иначе иллюзию выдаёт табличка. Искать копию после этого можно только по метке.
var BOSS_TAG = "hs_murgrot";
var COPY_TAG = "hs_murgrot_copy";
var BOSS_NAME = "Мургрот Туманный Глас";

function init(e) {
    var npc = e.npc;
    npc.addTag(COPY_TAG);
    var boss = findOriginal(npc);
    if (boss == null) return;
    copyLook(npc, boss);
}

function damaged(e) {
    var src = e.source;
    if (src != null && src.typeOf(1)) {
        hsStrike(src, 0.3);
        src.message("§5Это была иллюзия.");
    }
}

function findOriginal(npc) {
    var list = npc.getWorld().getNearbyEntities(npc.getPos(), 24, 2);
    var byName = null;
    for (var i = 0; i < list.length; i++) {
        var n = list[i];
        if (n == npc || !n.isAlive() || n.hasTag(COPY_TAG)) continue;
        if (n.hasTag(BOSS_TAG)) return n;
        if (byName == null && String(n.getName()) == BOSS_NAME) byName = n;
    }
    return byName;
}

function copyLook(npc, boss) {
    var from = boss.getDisplay();
    var to = npc.getDisplay();
    to.setName(from.getName());
    to.setTitle(from.getTitle());
    to.setNameFormat(from.getNameFormat());
    to.setTitleFormat(from.getTitleFormat());
    to.setShowName(from.getShowName());
    to.setSize(from.getSize());
    to.setTint(from.getTint());
    if (from.getSkinTexture() != null) to.setSkinTexture(from.getSkinTexture());
    if (from.getSkinUrl() != null) to.setSkinUrl(from.getSkinUrl());
    if (from.getSkinPlayer() != null) to.setSkinPlayer(from.getSkinPlayer());
    if (from.getCapeTexture() != null) to.setCapeTexture(from.getCapeTexture());
    if (from.getOverlayTexture() != null) to.setOverlayTexture(from.getOverlayTexture());
    try {
        var model = from.getModel();
        if (model != null && String(model).length > 0) to.setModel(String(model));
    } catch (err) {
        log("murgrot copy entity model: " + err);
    }
    copyGecko(npc, boss);
}

// GeckoLib лежит в display.customModelData, не в setModel (тот выбирает тип сущности).
function copyGecko(npc, boss) {
    try {
        var src = boss.getMCEntity().display.customModelData;
        var dst = npc.getMCEntity().display.customModelData;
        dst.setModel(src.getModel());
        dst.setAnimFile(src.getAnimFile());
        dst.setIdleAnim(src.getIdleAnim());
        dst.setWalkAnim(src.getWalkAnim());
        dst.setAttackAnim(src.getAttackAnim());
        dst.setHurtAnim(src.getHurtAnim());
        npc.getMCEntity().updateClient = true;
    } catch (err) {
        log("murgrot copy model: " + err);
    }
}
