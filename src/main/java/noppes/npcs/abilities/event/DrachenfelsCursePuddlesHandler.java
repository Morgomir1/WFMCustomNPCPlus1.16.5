package noppes.npcs.abilities.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import noppes.npcs.abilities.AbilityCombatHelper;
import noppes.npcs.abilities.AbilityVfx;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.entity.EntityAbilityZone;
import noppes.npcs.zone.ZoneAPI;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drachenfels body curse: cursed players must enter a free cleanse puddle within
 * the timer or the owner heals {@code healAmount} per failed cleanse.
 */
@Mod.EventBusSubscriber(modid = "customnpcs", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DrachenfelsCursePuddlesHandler {
    private static final Map<UUID, CurseEncounter> BY_OWNER = new ConcurrentHashMap<>();

    private DrachenfelsCursePuddlesHandler() {
    }

    public static boolean isPlayerCursed(final UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }
        for (final CurseEncounter encounter : BY_OWNER.values()) {
            for (final CursedPlayer cursed : encounter.cursed) {
                if (playerUuid.equals(cursed.playerUuid)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Start a curse encounter: mark players and spawn visual cleanse puddles.
     *
     * @return number of players cursed
     */
    public static int start(
            final ICustomNpc owner,
            final List<UUID> victimUuids,
            final List<double[]> puddlePoints,
            final double puddleRadius,
            final int curseTicks,
            final float healAmount,
            final int zoneColor) {
        if (owner == null || !owner.isAlive() || victimUuids == null || victimUuids.isEmpty()) {
            return 0;
        }
        final UUID ownerId;
        try {
            ownerId = UUID.fromString(String.valueOf(owner.getUUID()));
        } catch (final Exception e) {
            return 0;
        }

        clearOwner(ownerId);

        final CurseEncounter encounter = new CurseEncounter();
        encounter.ownerUuid = ownerId;
        encounter.healAmount = Math.max(0.0F, healAmount);
        encounter.puddleRadius = Math.max(0.8, puddleRadius);

        final int lifetime = Math.max(20, curseTicks);
        final IWorld world = owner.getWorld();

        if (puddlePoints != null) {
            for (final double[] point : puddlePoints) {
                if (point == null || point.length < 3) {
                    continue;
                }
                final Puddle puddle = new Puddle();
                puddle.x = point[0];
                puddle.y = point[1];
                puddle.z = point[2];
                puddle.used = false;
                final EntityAbilityZone zone = ZoneAPI.hazardCircle(
                        owner,
                        puddle.x,
                        puddle.y,
                        puddle.z,
                        encounter.puddleRadius,
                        lifetime + 10,
                        0.0,
                        999999);
                if (zone != null) {
                    zone.setColor(zoneColor);
                    zone.setZoneHeight(2.6f);
                    zone.setVisible(true);
                    zone.setGroundFill(true);
                    zone.setBorder(true);
                    puddle.zoneUuid = zone.getUUID();
                }
                if (world != null) {
                    AbilityVfx.spawnSoulBurst(world, puddle.x, puddle.y + 0.15, puddle.z, encounter.puddleRadius * 0.7);
                }
                encounter.puddles.add(puddle);
            }
        }

        int cursed = 0;
        for (final UUID victimId : victimUuids) {
            if (victimId == null) {
                continue;
            }
            final Entity mc = findEntity(victimId);
            if (!(mc instanceof PlayerEntity) || !mc.isAlive()) {
                continue;
            }
            final CursedPlayer state = new CursedPlayer();
            state.playerUuid = victimId;
            state.ticksLeft = lifetime;
            encounter.cursed.add(state);
            ((LivingEntity) mc).addEffect(
                    new EffectInstance(Effects.GLOWING, lifetime, 0, false, true, true));
            try {
                final IEntity wrapped = NpcAPI.Instance().getIEntity(mc);
                if (wrapped != null && world != null) {
                    AbilityVfx.spawnHitParticle(world, wrapped);
                }
            } catch (final Exception ignored) {
            }
            cursed++;
        }

        if (cursed <= 0) {
            clearPuddles(encounter);
            return 0;
        }

        BY_OWNER.put(ownerId, encounter);
        return cursed;
    }

    public static void clearOwner(final UUID ownerUuid) {
        if (ownerUuid == null) {
            return;
        }
        final CurseEncounter previous = BY_OWNER.remove(ownerUuid);
        if (previous != null) {
            clearPuddles(previous);
        }
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BY_OWNER.isEmpty()) {
            return;
        }
        final Iterator<Map.Entry<UUID, CurseEncounter>> it = BY_OWNER.entrySet().iterator();
        while (it.hasNext()) {
            final CurseEncounter encounter = it.next().getValue();
            if (!tickOne(encounter)) {
                clearPuddles(encounter);
                it.remove();
            }
        }
    }

    private static boolean tickOne(final CurseEncounter encounter) {
        final Entity ownerMc = findEntity(encounter.ownerUuid);
        if (ownerMc == null || !ownerMc.isAlive()) {
            return false;
        }
        final IEntity ownerWrapped = NpcAPI.Instance().getIEntity(ownerMc);
        if (!(ownerWrapped instanceof ICustomNpc)) {
            return false;
        }
        final ICustomNpc owner = (ICustomNpc) ownerWrapped;
        final IWorld world = owner.getWorld();

        // Cleanse: cursed player enters a free puddle
        final Iterator<CursedPlayer> curseIt = encounter.cursed.iterator();
        while (curseIt.hasNext()) {
            final CursedPlayer cursed = curseIt.next();
            final Entity victimMc = findEntity(cursed.playerUuid);
            if (!(victimMc instanceof PlayerEntity) || !victimMc.isAlive()) {
                curseIt.remove();
                continue;
            }

            final Puddle entered = findFreePuddleContaining(encounter, victimMc.getX(), victimMc.getZ());
            if (entered != null) {
                entered.used = true;
                removeZone(entered);
                clearGlow((LivingEntity) victimMc);
                if (world != null) {
                    AbilityVfx.spawnSoulBurst(world, entered.x, entered.y + 0.2, entered.z, encounter.puddleRadius);
                    try {
                        world.playSoundAt(
                                NpcAPI.Instance().getIPos(entered.x, entered.y, entered.z),
                                "minecraft:block.beacon.power_select",
                                0.9F,
                                1.35F);
                    } catch (final Exception ignored) {
                    }
                }
                curseIt.remove();
                continue;
            }

            cursed.ticksLeft--;
            if (cursed.ticksLeft % 8 == 0 && world != null) {
                AbilityVfx.spawnDarkCharge(world, victimMc.getX(), victimMc.getY() + 1.0, victimMc.getZ());
            }
            if (cursed.ticksLeft > 0) {
                continue;
            }

            // Timer expired → heal boss
            AbilityCombatHelper.healLiving((LivingEntity) ownerMc, encounter.healAmount);
            clearGlow((LivingEntity) victimMc);
            if (world != null) {
                AbilityVfx.spawnSoulWave(world, owner.getX(), owner.getY() + 0.3, owner.getZ(), 2.5);
                try {
                    world.playSoundAt(
                            owner.getPos(),
                            "minecraft:entity.wither.ambient",
                            0.75F,
                            0.55F);
                } catch (final Exception ignored) {
                }
            }
            curseIt.remove();
        }

        return !encounter.cursed.isEmpty();
    }

    private static Puddle findFreePuddleContaining(
            final CurseEncounter encounter,
            final double x,
            final double z) {
        final double r = encounter.puddleRadius;
        for (final Puddle puddle : encounter.puddles) {
            if (puddle.used) {
                continue;
            }
            if (AbilityCombatHelper.flatDistance(x, z, puddle.x, puddle.z) <= r) {
                return puddle;
            }
        }
        return null;
    }

    private static void clearGlow(final LivingEntity living) {
        if (living == null) {
            return;
        }
        try {
            living.removeEffect(Effects.GLOWING);
        } catch (final Exception ignored) {
        }
    }

    private static void clearPuddles(final CurseEncounter encounter) {
        if (encounter == null) {
            return;
        }
        for (final Puddle puddle : encounter.puddles) {
            removeZone(puddle);
        }
        encounter.puddles.clear();
    }

    private static void removeZone(final Puddle puddle) {
        if (puddle == null || puddle.zoneUuid == null) {
            return;
        }
        final Entity zone = findEntity(puddle.zoneUuid);
        if (zone instanceof EntityAbilityZone && !zone.removed) {
            zone.remove();
        }
        puddle.zoneUuid = null;
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

    private static final class CurseEncounter {
        private UUID ownerUuid;
        private float healAmount;
        private double puddleRadius;
        private final List<CursedPlayer> cursed = new ArrayList<>();
        private final List<Puddle> puddles = new ArrayList<>();
    }

    private static final class CursedPlayer {
        private UUID playerUuid;
        private int ticksLeft;
    }

    private static final class Puddle {
        private double x;
        private double y;
        private double z;
        private UUID zoneUuid;
        private boolean used;
    }
}
