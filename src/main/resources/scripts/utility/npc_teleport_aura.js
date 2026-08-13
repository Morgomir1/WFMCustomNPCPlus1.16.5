// =====================================================
// Аура телепорта вокруг NPC.
// Игрок, который ВОШЁЛ в радиус, переносится на DEST_*.
// Визуал: ОДНА серая Ability Zone, едет за NPC (Java helper).
//
// Нужна пересборка мода. Затем вставить скрипт целиком и /script reload.
// =====================================================

function tick(e) {
    var NpcAPI = Java.type("noppes.npcs.api.NpcAPI").Instance();
    var AuraZone = Java.type("noppes.npcs.script.TeleportAuraZoneHelper");

    // ----- НАСТРОЙКИ -----
    var AURA_RADIUS = 5.0;
    var AURA_HORIZONTAL = false;
    var DEST_X = 0.5;
    var DEST_Y = 64.0;
    var DEST_Z = 0.5;
    var TELEPORT_MESSAGE = "\u00a75Вас уносит прочь...";
    var TELEPORT_SOUND = "minecraft:entity.enderman.teleport";
    var TELEPORT_COOLDOWN_TICKS = 40;
    var ENTITY_PLAYER = 1;
    var GAMEMODE_SPECTATOR = 3;
    var INSIDE_KEY = "tp_aura_inside";
    var CD_KEY = "tp_aura_cd";
    var ZONE_COLOR = (0xB0 << 24) | (0x88 << 16) | (0x88 << 8) | 0x88;
    // ---------------------

    var npc = e.npc;
    if (npc == null) return;

    var data = npc.getStoreddata();
    if (!npc.isAlive()) {
        try { AuraZone.clear(npc); } catch (eClear) {}
        data.put(INSIDE_KEY, "");
        data.put(CD_KEY, "");
        return;
    }

    try { AuraZone.tick(npc, AURA_RADIUS, ZONE_COLOR); } catch (eZone) {
        try { log("tp aura zone: " + eZone); } catch (eLog) {}
    }

    var world = npc.getWorld();
    var now = world.getTotalTime();
    var nx = npc.getX();
    var ny = npc.getY();
    var nz = npc.getZ();
    var searchRange = Math.ceil(AURA_RADIUS + 1);
    var pos = NpcAPI.getIPos(nx, ny, nz);
    var nearby = world.getNearbyEntities(pos, searchRange, ENTITY_PLAYER);

    var prevInside = parseUuidList(String(data.get(INSIDE_KEY) || ""));
    var nowInside = [];
    var i;

    for (i = 0; i < nearby.length; i++) {
        var player = nearby[i];
        if (!isTeleportPlayer(player)) continue;
        if (!inAura(player)) continue;

        var uuid = String(player.getUUID());
        nowInside.push(uuid);

        if (containsUuid(prevInside, uuid)) continue;
        if (now < getPlayerCd(uuid)) continue;

        teleportPlayer(player);
        putPlayerCd(uuid, now + TELEPORT_COOLDOWN_TICKS);
    }

    data.put(INSIDE_KEY, nowInside.join(";"));

    function teleportPlayer(player) {
        var fromPos = NpcAPI.getIPos(player.getX(), player.getY(), player.getZ());
        var destPos = NpcAPI.getIPos(DEST_X, DEST_Y, DEST_Z);

        try { world.playSoundAt(fromPos, TELEPORT_SOUND, 1.0, 1.0); } catch (eSnd1) {}
        player.setPosition(DEST_X, DEST_Y, DEST_Z);
        try { world.playSoundAt(destPos, TELEPORT_SOUND, 1.0, 1.0); } catch (eSnd2) {}

        if (TELEPORT_MESSAGE != null && String(TELEPORT_MESSAGE).length > 0) {
            try { player.message(TELEPORT_MESSAGE); } catch (eMsg) {}
        }
    }

    function inAura(player) {
        var dx = player.getX() - nx;
        var dz = player.getZ() - nz;
        if (AURA_HORIZONTAL) {
            return (dx * dx + dz * dz) <= (AURA_RADIUS * AURA_RADIUS);
        }
        var dy = player.getY() - ny;
        return (dx * dx + dy * dy + dz * dz) <= (AURA_RADIUS * AURA_RADIUS);
    }

    function isTeleportPlayer(entity) {
        if (entity == null) return false;
        if (!entity.isAlive()) return false;
        if (typeof entity.getType == "function" && entity.getType() != ENTITY_PLAYER) return false;
        try {
            if (typeof entity.getGamemode == "function" && entity.getGamemode() == GAMEMODE_SPECTATOR) {
                return false;
            }
        } catch (eGm) {}
        return true;
    }

    function parseUuidList(raw) {
        var out = [];
        if (raw == null || raw.length == 0) return out;
        var parts = String(raw).split(";");
        var p;
        for (p = 0; p < parts.length; p++) {
            if (parts[p].length > 0) out.push(parts[p]);
        }
        return out;
    }

    function containsUuid(list, uuid) {
        var c;
        for (c = 0; c < list.length; c++) {
            if (list[c] == uuid) return true;
        }
        return false;
    }

    function getPlayerCd(uuid) {
        var map = parseCdMap(String(data.get(CD_KEY) || ""));
        if (map[uuid] == null) return 0;
        return map[uuid];
    }

    function putPlayerCd(uuid, until) {
        var map = parseCdMap(String(data.get(CD_KEY) || ""));
        map[uuid] = until;
        data.put(CD_KEY, stringifyCdMap(map, now));
    }

    function parseCdMap(raw) {
        var map = {};
        if (raw == null || raw.length == 0) return map;
        var parts = String(raw).split(";");
        var p;
        for (p = 0; p < parts.length; p++) {
            var row = parts[p];
            var colon = row.lastIndexOf(":");
            if (colon <= 0) continue;
            var id = row.substring(0, colon);
            var until = parseInt(row.substring(colon + 1));
            if (id.length > 0 && !isNaN(until)) map[id] = until;
        }
        return map;
    }

    function stringifyCdMap(map, timeNow) {
        var parts = [];
        var id;
        for (id in map) {
            if (!map.hasOwnProperty(id)) continue;
            if (map[id] <= timeNow) continue;
            parts.push(id + ":" + map[id]);
        }
        return parts.join(";");
    }
}

function died(e) {
    try {
        Java.type("noppes.npcs.script.TeleportAuraZoneHelper").clear(e.npc);
    } catch (err) {}
    try {
        e.npc.getStoreddata().put("tp_aura_inside", "");
    } catch (err2) {}
}
