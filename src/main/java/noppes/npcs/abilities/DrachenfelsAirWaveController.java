package noppes.npcs.abilities;

import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.entity.EntityAbilityZone;
import noppes.npcs.zone.ZoneAPI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side ambient rings used during Drachenfels' flight.
 *
 * <p>These are deliberately not AbilityAPI casts: active rings keep expanding
 * while the boss continuously uses its normal cast slot for crimson blobs.</p>
 */
public final class DrachenfelsAirWaveController {
    private static final ConcurrentHashMap<String, List<Wave>> WAVES_BY_BOSS =
            new ConcurrentHashMap<>();

    private DrachenfelsAirWaveController() {
    }

    public static void spawnPoisonWave(
            final ICustomNpc boss, final Map<String, Object> params) {
        spawn(boss, params, true);
    }

    public static void spawnWhisperWave(
            final ICustomNpc boss, final Map<String, Object> params) {
        spawn(boss, params, false);
    }

    private static void spawn(
            final ICustomNpc boss,
            final Map<String, Object> overrides,
            final boolean poison) {
        if (boss == null || !boss.isAlive()) {
            return;
        }
        final AbilityParams params = AbilityParams.merge(null, overrides, null);
        final double x = boss.getX();
        final double z = boss.getZ();
        final double y = AbilityCombatHelper.findGroundY(
                boss.getWorld(), x, z, boss.getY()) + 0.05;
        final Wave wave = new Wave(poison, x, y, z, params);
        wave.zone = ZoneAPI.hazardRing(boss, x, y, z, 2.0, 0.05, 40, 0, 999);
        if (wave.zone != null) {
            wave.zone.setColor(params.getInt(AbilityParamKeys.ZONE_COLOR, 0xC0FF3030));
            wave.zone.setZoneHeight((float) Math.max(
                    0.25, params.getDouble(AbilityParamKeys.ARC_HEIGHT, 1.0)));
            wave.zone.setVisible(true);
            wave.zone.setGroundFill(true);
            wave.zone.setBorder(true);
        }
        WAVES_BY_BOSS.computeIfAbsent(boss.getUUID(), ignored -> new ArrayList<>()).add(wave);
        boss.getWorld().playSoundAt(
                boss.getPos(),
                poison ? "minecraft:entity.witch.celebrate" : "minecraft:entity.wither.shoot",
                poison ? 0.85F : 0.65F,
                poison ? 0.7F : 0.85F);
    }

    public static void tick(final ICustomNpc boss) {
        if (boss == null) {
            return;
        }
        final List<Wave> waves = WAVES_BY_BOSS.get(boss.getUUID());
        if (waves == null) {
            return;
        }
        final Iterator<Wave> iterator = waves.iterator();
        while (iterator.hasNext()) {
            final Wave wave = iterator.next();
            tickWave(boss, wave);
            if (wave.finished) {
                clearZone(wave);
                iterator.remove();
            }
        }
        if (waves.isEmpty()) {
            WAVES_BY_BOSS.remove(boss.getUUID(), waves);
        }
    }

    private static void tickWave(final ICustomNpc boss, final Wave wave) {
        wave.elapsedTicks++;
        final int total = Math.max(1, wave.params.getInt(
                AbilityParamKeys.ACTIVE_TICKS, wave.poison ? 120 : 60));
        final double radius = wave.params.getDouble(AbilityParamKeys.RADIUS, 12.0);
        final double thickness = wave.params.getDouble(
                AbilityParamKeys.INNER_RADIUS, wave.poison ? 2.0 : 1.333);
        final double progress = Math.min(1.0, wave.elapsedTicks / (double) total);
        final double inner = progress * radius;
        final double outer = Math.min(radius + thickness, inner + thickness);

        if (wave.zone != null && wave.zone.isAlive()) {
            wave.zone.setRadius((float) outer);
            wave.zone.setInnerRadius((float) Math.max(0.05, inner));
            wave.zone.moveTo(wave.x, wave.y, wave.z, 0.0F, 0.0F);
        }
        tickHits(boss, wave, inner, outer);
        AbilityVfx.spawnDarkSoulRing(
                boss.getWorld(), wave.x, wave.y, wave.z, inner, outer);
        wave.finished = wave.elapsedTicks >= total;
    }

    private static void tickHits(
            final ICustomNpc boss,
            final Wave wave,
            final double inner,
            final double outer) {
        final double hitHeight = Math.max(
                0.25, wave.params.getDouble(AbilityParamKeys.ARC_HEIGHT, 1.0));
        final int range = (int) Math.ceil(outer + 1.0);
        final IEntity[] entities = boss.getWorld().getNearbyEntities(
                NpcAPI.Instance().getIPos(wave.x, wave.y, wave.z), range, -1);
        for (final IEntity entity : entities) {
            if (!AbilityCombatHelper.isHostileToBoss(boss, entity)) {
                continue;
            }
            final String uuid = String.valueOf(entity.getUUID());
            if (wave.hitUuids.contains(uuid)) {
                continue;
            }
            final double distance = AbilityCombatHelper.flatDistance(
                    entity.getX(), entity.getZ(), wave.x, wave.z);
            if (distance < inner || distance > outer || entity.getY() - wave.y > hitHeight) {
                continue;
            }
            if (wave.poison) {
                hitWithPoison(wave, entity);
            } else {
                hitWithWhisper(wave, entity);
            }
            AbilityVfx.spawnHitParticle(boss.getWorld(), entity);
            wave.hitUuids.add(uuid);
        }
    }

    private static void hitWithPoison(final Wave wave, final IEntity entity) {
        final float damage = (float) wave.params.getDouble(AbilityParamKeys.DAMAGE, 8.0);
        if (!AbilityCombatHelper.dealPureDamage(entity, damage, false)) {
            entity.damage(damage);
        }
        AbilityCombatHelper.applyEffect(
                entity,
                AbilityEffectType.POISON.toMcEffect(),
                wave.params.getInt(AbilityParamKeys.EFFECT_DURATION, 80),
                wave.params.getInt(AbilityParamKeys.EFFECT_AMPLIFIER, 3));
        AbilityCombatHelper.applyEffect(
                entity,
                AbilityEffectType.SLOWNESS.toMcEffect(),
                wave.params.getInt(AbilityParamKeys.TRAIL_TICKS, 40),
                0);
    }

    private static void hitWithWhisper(final Wave wave, final IEntity entity) {
        AbilityCombatHelper.applyEffect(
                entity,
                AbilityEffectType.BLINDNESS.toMcEffect(),
                wave.params.getInt(AbilityParamKeys.EFFECT_DURATION, 40),
                0);
        AbilityCombatHelper.applyEffect(
                entity,
                AbilityEffectType.WITHER.toMcEffect(),
                wave.params.getInt(AbilityParamKeys.TRAIL_TICKS, 60),
                wave.params.getInt(AbilityParamKeys.EFFECT_AMPLIFIER, 0));
    }

    public static void clear(final ICustomNpc boss) {
        if (boss == null) {
            return;
        }
        final List<Wave> waves = WAVES_BY_BOSS.remove(boss.getUUID());
        if (waves == null) {
            return;
        }
        for (final Wave wave : waves) {
            clearZone(wave);
        }
    }

    private static void clearZone(final Wave wave) {
        if (wave.zone != null) {
            ZoneAPI.remove(wave.zone);
            wave.zone = null;
        }
    }

    private static final class Wave {
        private final boolean poison;
        private final double x;
        private final double y;
        private final double z;
        private final AbilityParams params;
        private final Set<String> hitUuids = new HashSet<>();
        private EntityAbilityZone zone;
        private int elapsedTicks;
        private boolean finished;

        private Wave(
                final boolean poison,
                final double x,
                final double y,
                final double z,
                final AbilityParams params) {
            this.poison = poison;
            this.x = x;
            this.y = y;
            this.z = z;
            this.params = params;
        }
    }
}
