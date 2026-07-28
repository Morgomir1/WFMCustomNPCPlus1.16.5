package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.AbilityCombatHelper;
import noppes.npcs.abilities.AbilityContext;
import noppes.npcs.abilities.AbilityDefaults;
import noppes.npcs.abilities.AbilityParamKeys;
import noppes.npcs.abilities.AbilityParams;
import noppes.npcs.abilities.AbilityVfx;
import noppes.npcs.abilities.ActiveAbility;
import noppes.npcs.abilities.CnpcAbility;
import noppes.npcs.abilities.TickResult;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.entity.EntityAbilityZone;
import noppes.npcs.zone.ZoneAPI;

import java.util.Map;
import java.util.Set;

/**
 * Отродье — «Разлетающийся кал»: реактивные зелёные лужи (poison + slowness).
 * Основной путь — {@link #trigger} без AbilityRunner, чтобы не сбивать текущий каст.
 */
public final class OtrodieSpreadingFilthAbility implements CnpcAbility {
    public static final String ID = "otrodie_spreading_filth";

    private static final int DEFAULT_ZONE_COLOR = 0xC0B8FF00;
    private static final String DEFAULT_EFFECT = "minecraft:poison;minecraft:slowness";
    private static final String DEFAULT_PARTICLES =
            "wfm:nurgle_miasma,minecraft:smoke,minecraft:large_smoke,minecraft:ash,minecraft:witch";

    private static final Set<String> KNOWN_KEYS = AbilityParams.keys(
            AbilityParamKeys.RADIUS,
            AbilityParamKeys.HIT_RADIUS,
            AbilityParamKeys.SPREAD_RADIUS,
            AbilityParamKeys.SUMMON_COUNT,
            AbilityParamKeys.ZONE_TICKS,
            AbilityParamKeys.DAMAGE,
            AbilityParamKeys.DAMAGE_INTERVAL,
            AbilityParamKeys.ZONE_COLOR,
            AbilityParamKeys.EFFECT_ID,
            AbilityParamKeys.EFFECT_DURATION,
            AbilityParamKeys.EFFECT_AMPLIFIER,
            AbilityParamKeys.PARTICLE_COUNT,
            AbilityParamKeys.BLOB_PARTICLES,
            AbilityParamKeys.LAND_PARTICLES,
            AbilityParamKeys.TELEGRAPH);

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean requiresTarget() {
        return false;
    }

    @Override
    public boolean cancelsOnTargetLost() {
        return false;
    }

    @Override
    public Map<String, Object> defaultParams() {
        return AbilityDefaults.otrodieSpreadingFilth();
    }

    @Override
    public Set<String> knownParamKeys() {
        return KNOWN_KEYS;
    }

    /**
     * Спавн луж без AbilityRunner (из JS по порогу HP).
     */
    public static void trigger(final ICustomNpc npc) {
        trigger(npc, null);
    }

    /**
     * @param overrides опциональные overrides поверх {@link AbilityDefaults#otrodieSpreadingFilth()}
     */
    public static void trigger(final ICustomNpc npc, final Map<String, Object> overrides) {
        if (npc == null || !npc.isAlive()) {
            return;
        }
        final AbilityParams params = AbilityParams.merge(
                AbilityDefaults.otrodieSpreadingFilth(),
                overrides,
                KNOWN_KEYS);
        spawnPuddles(npc, npc.getWorld(), params);
    }

    /**
     * Одна маленькая лужа под NPC (фаза 2 после devour dash).
     */
    public static void spawnSingleUnderNpc(final ICustomNpc npc, final double radius) {
        if (npc == null || !npc.isAlive()) {
            return;
        }
        final AbilityParams params = AbilityParams.merge(
                AbilityDefaults.otrodieSpreadingFilth(),
                null,
                KNOWN_KEYS);
        final IWorld world = npc.getWorld();
        if (world == null) {
            return;
        }
        final double r = Math.max(0.8, radius);
        final int zoneTicks = Math.max(20, params.getInt(AbilityParamKeys.ZONE_TICKS, 200));
        final double damage = Math.max(0.0, params.getDouble(AbilityParamKeys.DAMAGE, 3.0));
        final int damageInterval = Math.max(1, params.getInt(AbilityParamKeys.DAMAGE_INTERVAL, 10));
        final int zoneColor = params.getInt(AbilityParamKeys.ZONE_COLOR, DEFAULT_ZONE_COLOR);
        final String effectId = params.getString(AbilityParamKeys.EFFECT_ID, DEFAULT_EFFECT);
        final int effectDuration = Math.max(1, params.getInt(AbilityParamKeys.EFFECT_DURATION, 60));
        final int effectAmplifier = Math.max(0, params.getInt(AbilityParamKeys.EFFECT_AMPLIFIER, 0));
        final String particles = params.getString(AbilityParamKeys.BLOB_PARTICLES, DEFAULT_PARTICLES);
        final int particleCount = Math.max(4, params.getInt(AbilityParamKeys.PARTICLE_COUNT, 12));

        final double bx = npc.getX();
        final double bz = npc.getZ();
        final double by = AbilityCombatHelper.findGroundY(world, bx, bz, npc.getY());
        spawnHazardPuddle(
                npc, world, bx, by, bz, r, zoneTicks, damage, damageInterval,
                zoneColor, effectId, effectDuration, effectAmplifier, particles, particleCount);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        // Ручной AbilityAPI.start: один тик и FINISHED, зоны уже живут сами.
        spawnPuddles(ctx.npc, ctx.world, ctx.params);
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = 0;
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        return TickResult.FINISHED;
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
    }

    private static void spawnPuddles(
            final ICustomNpc npc,
            final IWorld world,
            final AbilityParams params) {
        if (world == null) {
            return;
        }

        final double largeRadius = Math.max(1.0, params.getDouble(AbilityParamKeys.RADIUS, 4.0));
        final double smallRadius = Math.max(0.8, params.getDouble(AbilityParamKeys.HIT_RADIUS, 1.6));
        final double spreadRadius = clamp(params.getDouble(AbilityParamKeys.SPREAD_RADIUS, 4.5), 3.0, 6.0);
        final int summonCount = Math.max(1, Math.min(8, params.getInt(AbilityParamKeys.SUMMON_COUNT, 5)));
        final int zoneTicks = Math.max(20, params.getInt(AbilityParamKeys.ZONE_TICKS, 200));
        final double damage = Math.max(0.0, params.getDouble(AbilityParamKeys.DAMAGE, 3.0));
        final int damageInterval = Math.max(1, params.getInt(AbilityParamKeys.DAMAGE_INTERVAL, 10));
        final int zoneColor = params.getInt(AbilityParamKeys.ZONE_COLOR, DEFAULT_ZONE_COLOR);
        final String effectId = params.getString(AbilityParamKeys.EFFECT_ID, DEFAULT_EFFECT);
        final int effectDuration = Math.max(1, params.getInt(AbilityParamKeys.EFFECT_DURATION, 60));
        final int effectAmplifier = Math.max(0, params.getInt(AbilityParamKeys.EFFECT_AMPLIFIER, 0));
        final String landParticles = params.getString(AbilityParamKeys.LAND_PARTICLES, "");
        final String blobParticles = params.getString(AbilityParamKeys.BLOB_PARTICLES, DEFAULT_PARTICLES);
        final String particles = (landParticles == null || landParticles.isEmpty())
                ? blobParticles : landParticles;
        final int particleCount = Math.max(4, params.getInt(AbilityParamKeys.PARTICLE_COUNT, 12));

        final double bx = npc.getX();
        final double bz = npc.getZ();
        final double by = AbilityCombatHelper.findGroundY(world, bx, bz, npc.getY());

        spawnHazardPuddle(
                npc, world, bx, by, bz, largeRadius, zoneTicks, damage, damageInterval,
                zoneColor, effectId, effectDuration, effectAmplifier, particles, particleCount);

        final double angleOffset = AbilityCombatHelper.random().nextDouble() * Math.PI * 2.0;
        for (int i = 0; i < summonCount; i++) {
            final double angle = angleOffset + (Math.PI * 2.0 * i) / summonCount
                    + (AbilityCombatHelper.random().nextDouble() - 0.5) * 0.35;
            final double dist = spreadRadius * (0.55 + AbilityCombatHelper.random().nextDouble() * 0.45);
            final double x = bx + Math.cos(angle) * dist;
            final double z = bz + Math.sin(angle) * dist;
            final double y = AbilityCombatHelper.findGroundY(world, x, z, by);
            spawnHazardPuddle(
                    npc, world, x, y, z, smallRadius, zoneTicks, damage, damageInterval,
                    zoneColor, effectId, effectDuration, effectAmplifier, particles,
                    Math.max(6, particleCount * 2 / 3));
        }

        try {
            world.playSoundAt(
                    NpcAPI.Instance().getIPos(bx, by + 0.2, bz),
                    "minecraft:entity.slime.squish",
                    1.1F,
                    0.55F);
            world.playSoundAt(
                    NpcAPI.Instance().getIPos(bx, by + 0.2, bz),
                    "minecraft:block.honey_block.break",
                    0.85F,
                    0.7F);
        } catch (final Exception ignored) {
        }
    }

    private static void spawnHazardPuddle(
            final ICustomNpc npc,
            final IWorld world,
            final double x,
            final double y,
            final double z,
            final double radius,
            final int zoneTicks,
            final double damage,
            final int damageInterval,
            final int zoneColor,
            final String effectId,
            final int effectDuration,
            final int effectAmplifier,
            final String particles,
            final int particleCount) {
        final double zoneY = y + 0.05;
        AbilityVfx.spawnOtrodiePuddleSplash(world, x, zoneY + 0.2, z, radius, particles, particleCount);

        final EntityAbilityZone zone = ZoneAPI.hazardCircle(
                npc, x, zoneY, z, radius, zoneTicks, damage, damageInterval);
        if (zone == null) {
            return;
        }
        zone.setColor(zoneColor);
        zone.setZoneHeight(2.8f);
        zone.setEffect(effectId, effectDuration, effectAmplifier);
        zone.setVisible(true);
        zone.setGroundFill(true);
        zone.setBorder(true);
    }

    private static double clamp(final double value, final double min, final double max) {
        return Math.max(min, Math.min(max, value));
    }
}
