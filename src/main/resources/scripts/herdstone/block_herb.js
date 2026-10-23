// Святая трава C3. Отдельный Scripted Block, без hs_core.js.
// ПКМ даёт 15 с чистоты: туман не вешает тошноту и иссушение, пока не истечёт hs_clean_until.
function interact(e) {
    var now = e.block.getWorld().getTotalTime();
    e.player.getStoreddata().put("hs_clean_until", String(now + 300));
    e.player.message("§aТрава держит туман 15 секунд.");
}
