package noppes.npcs.abilities.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import noppes.npcs.abilities.AbilityCombatHelper;
import noppes.npcs.abilities.AbilityParamKeys;
import noppes.npcs.abilities.AbilityParams;
import noppes.npcs.abilities.AbilityVfx;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.IEntityLiving;
import noppes.npcs.entity.EntityAbilityZone;
import noppes.npcs.zone.ZoneAPI;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * After {@code vampire_blood_dash} hits a player, spawn blood puddles under them.
 * The caster heals when standing in those zones ({@link EntityAbilityZone#setHealOwner}).
 */
@Mod.EventBusSubscriber(modid = "customnpcs", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VampireBloodTrailHandler {
    private static final int MAX_PUDDLES = 8;
    private static final double SKIP_DIST = 1.2;
    private static final Map<UUID, Trail> TRAILS = new ConcurrentHashMap<>();

    private VampireBloodTrailHandler() {
    }

    public static void start(final ICustomNpc owner, final IEntityLiving victim, final AbilityParams params) {
        if (owner == null || victim == null || !victim.isAlive() || params == null) {
            return;
        }
        try {
            final Object mc = victim.getMCEntity();
            if (!(mc instanceof PlayerEntity)) {
                return;
            }
        } catch (final Exception e) {
            return;
        }
        final UUID ownerId;
        final UUID victimId;
        try {
            ownerId = UUID.fromString(String.valueOf(owner.getUUID()));
            victimId = UUID.fromString(String.valueOf(victim.getUUID()));
        } catch (final Exception e) {
            return;
        }
        final Trail trail = new Trail();
        trail.ownerUuid = ownerId;
        trail.victimUuid = victimId;
        trail.remainingTicks = Math.max(1, params.getInt(AbilityParamKeys.TRAIL_TICKS, 160));
        trail.interval = Math.max(1, params.getInt(AbilityParamKeys.PUDDLE_INTERVAL, 8));
        trail.sinceLast = trail.interval;
        trail.radius = (float) params.getDouble(AbilityParamKeys.RADIUS, 1.8);
        trail.zoneTicks = Math.max(1, params.getInt(AbilityParamKeys.ZONE_TICKS, 50));
        trail.healPerTick = (float) params.getDouble(AbilityParamKeys.HEAL_PER_TICK, 15.0);
        trail.damageInterval = Math.max(1, params.getInt(AbilityParamKeys.DAMAGE_INTERVAL, 10));
        trail.color = params.getInt(AbilityParamKeys.ZONE_COLOR, 0xC0B01018);
        TRAILS.put(victimId, trail);
        spawnPuddle(trail);
        trail.sinceLast = 0;
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TRAILS.isEmpty()) {
            return;
        }
        final Iterator<Map.Entry<UUID, Trail>> it = TRAILS.entrySet().iterator();
        while (it.hasNext()) {
            final Trail trail = it.next().getValue();
            trail.remainingTicks--;
            if (trail.remainingTicks <= 0) {
                it.remove();
                continue;
            }
            trail.sinceLast++;
            if (trail.sinceLast < trail.interval) {
                continue;
            }
            if (!spawnPuddle(trail)) {
                it.remove();
                continue;
            }
            trail.sinceLast = 0;
        }
    }

    private static boolean spawnPuddle(final Trail trail) {
        final Entity ownerMc = findEntity(trail.ownerUuid);
        final Entity victimMc = findEntity(trail.victimUuid);
        if (ownerMc == null || !ownerMc.isAlive()) {
            return false;
        }
        if (!(victimMc instanceof PlayerEntity) || !victimMc.isAlive()) {
            return false;
        }
        final IEntity ownerWrapped = NpcAPI.Instance().getIEntity(ownerMc);
        if (!(ownerWrapped instanceof ICustomNpc)) {
            return false;
        }
        final ICustomNpc npc = (ICustomNpc) ownerWrapped;
        if (!(victimMc.level instanceof ServerWorld)) {
            return false;
        }
        final ServerWorld world = (ServerWorld) victimMc.level;
        final double x = victimMc.getX();
        final double z = victimMc.getZ();
        final double y = AbilityCombatHelper.findGroundY(npc.getWorld(), x, z, victimMc.getY()) + 0.05;
        if (hasNearbyPuddle(world, trail, x, y, z)) {
            return true;
        }
        final EntityAbilityZone zone = ZoneAPI.hazardCircle(
                npc, x, y, z, trail.radius, trail.zoneTicks, 0.0, trail.damageInterval);
        if (zone == null) {
            return true;
        }
        zone.setHealOwner(trail.healPerTick);
        zone.setColor(trail.color);
        zone.setZoneHeight(2.4f);
        zone.setVisible(true);
        zone.setGroundFill(true);
        zone.setBorder(true);
        AbilityVfx.spawnBloodCharge(npc.getWorld(), x, y + 0.15, z);
        return true;
    }

    private static boolean hasNearbyPuddle(
            final ServerWorld world,
            final Trail trail,
            final double x,
            final double y,
            final double z) {
        final double r = Math.max(trail.radius, SKIP_DIST);
        final AxisAlignedBB box = new AxisAlignedBB(
                x - r - 0.5, y - 1.0, z - r - 0.5,
                x + r + 0.5, y + 2.5, z + r + 0.5);
        final List<EntityAbilityZone> zones = world.getEntitiesOfClass(EntityAbilityZone.class, box);
        int count = 0;
        for (final EntityAbilityZone zone : zones) {
            if (zone == null || zone.getHealOwner() <= 0.001f) {
                continue;
            }
            if (trail.ownerUuid != null && !trail.ownerUuid.equals(zone.getOwnerUuid())) {
                continue;
            }
            count++;
            final double dx = zone.getX() - x;
            final double dz = zone.getZ() - z;
            if (dx * dx + dz * dz <= SKIP_DIST * SKIP_DIST) {
                return true;
            }
        }
        return count >= MAX_PUDDLES;
    }

    private static Entity findEntity(final UUID uuid) {
        if (uuid == null) {
            return null;
        }
        final net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        for (final ServerWorld world : server.getAllLevels()) {
            final Entity entity = world.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static final class Trail {
        private UUID ownerUuid;
        private UUID victimUuid;
        private int remainingTicks;
        private int interval;
        private int sinceLast;
        private float radius;
        private int zoneTicks;
        private float healPerTick;
        private int damageInterval;
        private int color;
    }
}
