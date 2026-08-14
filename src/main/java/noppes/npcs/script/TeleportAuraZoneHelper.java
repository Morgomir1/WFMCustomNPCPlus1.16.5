package noppes.npcs.script;

import net.minecraft.entity.Entity;
import net.minecraft.world.server.ServerWorld;
import noppes.npcs.abilities.AbilityCombatHelper;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.wrapper.WorldWrapper;
import noppes.npcs.entity.EntityAbilityZone;
import noppes.npcs.zone.ZoneAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One follow-aura per NPC. Call from JS — do not spawn ZoneAPI in Nashorn.
 */
public final class TeleportAuraZoneHelper {
    public static final String TAG = "cnpc_tp_aura";
    public static final int DEFAULT_COLOR = 0xB0888888;
    private static final int LIFETIME_TICKS = 200;
    private static final Map<UUID, UUID> ZONE_BY_NPC = new ConcurrentHashMap<>();

    private TeleportAuraZoneHelper() {
    }

    public static void tick(final ICustomNpc npc, final double radius, final int color) {
        if (npc == null || !npc.isAlive()) {
            clear(npc);
            return;
        }
        final UUID npcId = npcUuid(npc);
        if (npcId == null) {
            return;
        }
        final ServerWorld world = serverWorld(npc);
        if (world == null) {
            return;
        }

        EntityAbilityZone keep = resolveMapped(world, npcId);
        keep = sweepExtras(world, npcId, keep);
        if (keep == null || keep.removed) {
            keep = spawn(npc, world, radius, color);
        }
        if (keep == null) {
            ZONE_BY_NPC.remove(npcId);
            return;
        }
        configure(keep, npc, radius, color);
        ZONE_BY_NPC.put(npcId, keep.getUUID());
    }

    public static void clear(final ICustomNpc npc) {
        final UUID npcId = npcUuid(npc);
        if (npcId == null) {
            return;
        }
        final ServerWorld world = serverWorld(npc);
        if (world != null) {
            sweepExtras(world, npcId, null);
        }
        ZONE_BY_NPC.remove(npcId);
    }

    private static EntityAbilityZone spawn(
            final ICustomNpc npc,
            final ServerWorld world,
            final double radius,
            final int color) {
        final double x = npc.getX();
        final double z = npc.getZ();
        final double y = AbilityCombatHelper.findFeetGroundY(npc.getWorld(), x, z, npc.getY()) + 0.05;
        final EntityAbilityZone zone = ZoneAPI.hazardCircle(
                npc, x, y, z, radius, LIFETIME_TICKS, 0.0, 40);
        if (zone == null) {
            return null;
        }
        configure(zone, npc, radius, color);
        try {
            zone.addTag(TAG);
        } catch (final Exception ignored) {
        }
        return zone;
    }

    private static void configure(
            final EntityAbilityZone zone,
            final ICustomNpc npc,
            final double radius,
            final int color) {
        zone.setDamage(0.0F);
        zone.setHealOwner(0.0F);
        zone.setKnockback(0.0F);
        zone.setColor(color == 0 ? DEFAULT_COLOR : color);
        zone.setFollowOwner(true);
        zone.setVisible(true);
        zone.setGroundFill(true);
        zone.setBorder(true);
        zone.setRadius((float) Math.max(0.5, radius));
        zone.setLifetimeTicks(LIFETIME_TICKS);
        try {
            zone.addTag(TAG);
        } catch (final Exception ignored) {
        }
        final double x = npc.getX();
        final double z = npc.getZ();
        final double y = AbilityCombatHelper.findFeetGroundY(npc.getWorld(), x, z, npc.getY()) + 0.05;
        zone.moveTo(x, y, z, 0, 0);
    }

    private static EntityAbilityZone resolveMapped(final ServerWorld world, final UUID npcId) {
        final UUID zoneId = ZONE_BY_NPC.get(npcId);
        if (zoneId == null) {
            return null;
        }
        final Entity entity = world.getEntity(zoneId);
        if (entity instanceof EntityAbilityZone && !entity.removed) {
            return (EntityAbilityZone) entity;
        }
        ZONE_BY_NPC.remove(npcId);
        return null;
    }

    /**
     * Drops every leftover teleport-aura zone for this NPC (including the old JS trail).
     * {@code keep} is preserved; pass null to delete all of them.
     */
    private static EntityAbilityZone sweepExtras(
            final ServerWorld world,
            final UUID npcId,
            final EntityAbilityZone keep) {
        EntityAbilityZone chosen = keep;
        final List<EntityAbilityZone> extras = new ArrayList<>();
        for (final Entity entity : world.getAllEntities()) {
            if (!(entity instanceof EntityAbilityZone) || entity.removed) {
                continue;
            }
            final EntityAbilityZone zone = (EntityAbilityZone) entity;
            if (!isTeleportAuraZone(zone, npcId)) {
                continue;
            }
            if (chosen == null) {
                chosen = zone;
            } else if (zone != chosen) {
                extras.add(zone);
            }
        }
        for (final EntityAbilityZone extra : extras) {
            ZoneAPI.remove(extra);
        }
        return chosen;
    }

    private static boolean isTeleportAuraZone(final EntityAbilityZone zone, final UUID npcId) {
        try {
            if (zone.getTags().contains(TAG)) {
                final UUID owner = zone.getOwnerUuid();
                return owner == null || owner.equals(npcId);
            }
        } catch (final Exception ignored) {
        }
        final UUID owner = zone.getOwnerUuid();
        return owner != null && owner.equals(npcId) && zone.getDamage() <= 0.001F;
    }

    private static UUID npcUuid(final ICustomNpc npc) {
        if (npc == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(npc.getUUID()));
        } catch (final Exception e) {
            return null;
        }
    }

    private static ServerWorld serverWorld(final ICustomNpc npc) {
        if (npc == null) {
            return null;
        }
        try {
            if (npc.getWorld() instanceof WorldWrapper) {
                return ((WorldWrapper) npc.getWorld()).getMCWorld();
            }
            final Entity mc = npc.getMCEntity();
            if (mc != null && mc.level instanceof ServerWorld) {
                return (ServerWorld) mc.level;
            }
        } catch (final Exception ignored) {
        }
        return null;
    }
}
