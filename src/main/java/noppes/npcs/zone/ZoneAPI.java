package noppes.npcs.zone;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import noppes.npcs.CustomEntities;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.wrapper.WorldWrapper;
import noppes.npcs.entity.EntityAbilityZone;
import noppes.npcs.entity.EntityAbilityZone.ZoneShape;
import noppes.npcs.telegraph.TelegraphAPI;

/**
 * JS/Java API for damaging ability zones.
 *
 * <pre>
 * var ZoneAPI = Java.type("noppes.npcs.zone.ZoneAPI");
 * var zone = ZoneAPI.hazardCircle(npc, x, y, z, radius, durationTicks, damage, damageInterval);
 * zone.setEffect("minecraft:poison", 60, 0);
 * zone.setColor(0x80FF0000);
 * zone.setFireSeconds(3); // optional ignite with each damage tick
 * </pre>
 */
public final class ZoneAPI {
    private ZoneAPI() {
    }

    public static EntityAbilityZone hazardCircle(
            final ICustomNpc npc,
            final double x,
            final double y,
            final double z,
            final double radius,
            final int durationTicks,
            final double damage,
            final int damageInterval) {
        return spawnHazard(npc, x, y, z, ZoneShape.CIRCLE, radius, 0, durationTicks, damage, damageInterval);
    }

    public static EntityAbilityZone hazardRing(
            final ICustomNpc npc,
            final double x,
            final double y,
            final double z,
            final double outerRadius,
            final double innerRadius,
            final int durationTicks,
            final double damage,
            final int damageInterval) {
        return spawnHazard(npc, x, y, z, ZoneShape.RING, outerRadius, innerRadius, durationTicks, damage, damageInterval);
    }

    public static EntityAbilityZone hazardSquare(
            final ICustomNpc npc,
            final double x,
            final double y,
            final double z,
            final double halfSize,
            final int durationTicks,
            final double damage,
            final int damageInterval) {
        return spawnHazard(npc, x, y, z, ZoneShape.SQUARE, halfSize, 0, durationTicks, damage, damageInterval);
    }

    public static EntityAbilityZone trapCircle(
            final ICustomNpc npc,
            final double x,
            final double y,
            final double z,
            final double radius,
            final int durationTicks,
            final double damage) {
        final EntityAbilityZone zone = create(npc, x, y, z);
        if (zone == null) {
            return null;
        }
        zone.configureTrap(npc, ZoneShape.CIRCLE, (float) radius, durationTicks, (float) damage);
        zone.setColor(TelegraphAPI.DEFAULT_COLOR);
        return zone;
    }

    public static void remove(final EntityAbilityZone zone) {
        if (zone != null && !zone.removed) {
            zone.remove();
        }
    }

    private static EntityAbilityZone spawnHazard(
            final ICustomNpc npc,
            final double x,
            final double y,
            final double z,
            final ZoneShape shape,
            final double radius,
            final double innerRadius,
            final int durationTicks,
            final double damage,
            final int damageInterval) {
        final EntityAbilityZone zone = create(npc, x, y, z);
        if (zone == null) {
            return null;
        }
        zone.configureHazard(
                npc,
                shape,
                (float) radius,
                (float) innerRadius,
                durationTicks,
                (float) damage,
                damageInterval);
        zone.setColor(TelegraphAPI.DEFAULT_COLOR);
        return zone;
    }

    private static EntityAbilityZone create(
            final ICustomNpc npc,
            final double x,
            final double y,
            final double z) {
        final World world = worldOf(npc);
        if (world == null || world.isClientSide || CustomEntities.entityAbilityZone == null) {
            return null;
        }
        final EntityAbilityZone zone = CustomEntities.entityAbilityZone.create(world);
        if (zone == null) {
            return null;
        }
        zone.moveTo(x, y, z, 0, 0);
        world.addFreshEntity(zone);
        return zone;
    }

    private static World worldOf(final ICustomNpc npc) {
        if (npc == null) {
            return null;
        }
        try {
            if (npc.getWorld() instanceof WorldWrapper) {
                final ServerWorld sw = ((WorldWrapper) npc.getWorld()).getMCWorld();
                return sw;
            }
            final Entity mc = npc.getMCEntity();
            return mc == null ? null : mc.level;
        } catch (final Exception e) {
            return null;
        }
    }
}
