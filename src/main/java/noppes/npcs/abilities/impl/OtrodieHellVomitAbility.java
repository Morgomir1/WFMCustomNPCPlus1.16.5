package noppes.npcs.abilities.impl;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import noppes.npcs.abilities.AbilityCombatHelper;
import noppes.npcs.abilities.AbilityContext;
import noppes.npcs.abilities.AbilityDefaults;
import noppes.npcs.abilities.AbilityParamKeys;
import noppes.npcs.abilities.AbilityParams;
import noppes.npcs.abilities.AbilityTelegraph;
import noppes.npcs.abilities.AbilityVfx;
import noppes.npcs.abilities.ActiveAbility;
import noppes.npcs.abilities.CnpcAbility;
import noppes.npcs.abilities.TickResult;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.entity.EntityAbilityZone;
import noppes.npcs.script.ScriptDataUtil;
import noppes.npcs.zone.ZoneAPI;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Отродье — «Адская блевотина»: charge → continuous nurgle stream + moving red hazard zone.
 * Прерывание по урону в спину ({@link noppes.npcs.abilities.event.OtrodieCombatHandler} → meter).
 * onEnd форсит {@code otrodie_fecal_wave} через storeddata.
 */
public final class OtrodieHellVomitAbility implements CnpcAbility {
    public static final String ID = "otrodie_hell_vomit";
    public static final String FORCED_ABILITY_KEY = "ot_forced_ability";
    public static final String FECAL_WAVE_ID = "otrodie_fecal_wave";

    private static final int DEFAULT_ZONE_COLOR = 0xC0FF3030;
    private static final String DEFAULT_EFFECT =
            "minecraft:poison;minecraft:slowness";
    private static final double ZONE_SPAWN_OFFSET = 6.0;
    private static final double ZONE_APPROACH_SPEED = 0.225; // 0.15 * 1.5
    private static final double MOUTH_FORWARD = 0.55;
    private static final double MOUTH_Y = 1.35;

    private static final ConcurrentHashMap<UUID, UUID> ZONE_BY_NPC = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean requiresTarget() {
        return true;
    }

    @Override
    public boolean cancelsOnTargetLost() {
        return false;
    }

    @Override
    public Map<String, Object> defaultParams() {
        return AbilityDefaults.otrodieHellVomit();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.DAMAGE_INTERVAL,
                AbilityParamKeys.ZONE_TICKS,
                AbilityParamKeys.ZONE_COLOR,
                AbilityParamKeys.EFFECT_ID,
                AbilityParamKeys.EFFECT_DURATION,
                AbilityParamKeys.EFFECT_AMPLIFIER,
                AbilityParamKeys.BREAK_DAMAGE,
                AbilityParamKeys.PARTICLE_COUNT,
                AbilityParamKeys.BLOB_PARTICLES,
                AbilityParamKeys.ARC_HEIGHT,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR,
                AbilityParamKeys.MAX_RANGE);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return false;
        }
        final double maxRange = ctx.params.getDouble(AbilityParamKeys.MAX_RANGE, 28.0);
        if (AbilityCombatHelper.distanceToTarget(ctx) > maxRange) {
            return false;
        }

        active.jumpStyle = false;
        active.hitUuids.clear();
        active.meter = 0.0F;
        clearZoneRef(active.npcUuid);

        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY();
        active.sz = ctx.npc.getZ();

        faceTarget(active, ctx);
        computeZoneStart(active, ctx);

        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 28);

        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.holdInPlace(ctx.npc, active.sx, active.sy, active.sz);
        ctx.npc.setRotation(active.yaw);

        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.ravager.roar", 0.85F, 0.55F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (shouldBreak(active, ctx)) {
            return TickResult.FINISHED;
        }
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickActive(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.holdInPlace(ctx.npc, active.sx, active.sy, active.sz);
        faceTarget(active, ctx);
        ctx.npc.setRotation(active.yaw);

        if (active.ticksLeft % 4 == 0) {
            AbilityVfx.spawnOtrodieVomitCloud(
                    ctx.world,
                    active.ex,
                    active.ey + 0.35,
                    active.ez,
                    ctx.params.getString(AbilityParamKeys.BLOB_PARTICLES, ""),
                    Math.max(4, ctx.params.getInt(AbilityParamKeys.PARTICLE_COUNT, 12) / 2));
        }

        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }

        if (shouldBreak(active, ctx)) {
            return TickResult.FINISHED;
        }

        spawnHazardZone(active, ctx);
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 280));
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.llama.spit", 1.1F, 0.45F);
        return TickResult.CONTINUE;
    }

    private TickResult tickActive(final ActiveAbility active, final AbilityContext ctx) {
        if (active.ticksLeft <= 0) {
            return TickResult.FINISHED;
        }

        AbilityCombatHelper.holdInPlace(ctx.npc, active.sx, active.sy, active.sz);
        faceTarget(active, ctx);
        ctx.npc.setRotation(active.yaw);

        spawnStream(active, ctx);
        approachZoneTowardTarget(active, ctx);

        if (active.ticksLeft % 20 == 0) {
            ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.slime.squish", 0.7F, 0.4F);
        }

        active.ticksLeft--;
        if (active.ticksLeft <= 0 || shouldBreak(active, ctx)) {
            return TickResult.FINISHED;
        }
        return TickResult.CONTINUE;
    }

    private static boolean shouldBreak(final ActiveAbility active, final AbilityContext ctx) {
        final float breakDamage = (float) ctx.params.getDouble(AbilityParamKeys.BREAK_DAMAGE, 100.0);
        return active.meter >= breakDamage;
    }

    private static void faceTarget(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return;
        }
        final double dx = ctx.target.getX() - ctx.npc.getX();
        final double dz = ctx.target.getZ() - ctx.npc.getZ();
        active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
    }

    /** Zone starts behind the target along boss→target vector. */
    private static void computeZoneStart(final ActiveAbility active, final AbilityContext ctx) {
        final double nx = ctx.npc.getX();
        final double nz = ctx.npc.getZ();
        final double tx = ctx.target.getX();
        final double tz = ctx.target.getZ();
        double dx = tx - nx;
        double dz = tz - nz;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.05) {
            final double rad = (active.yaw + 90.0) * 0.0174532925;
            dx = Math.cos(rad);
            dz = Math.sin(rad);
            len = 1.0;
        }
        dx /= len;
        dz /= len;

        final double zx = tx + dx * ZONE_SPAWN_OFFSET;
        final double zz = tz + dz * ZONE_SPAWN_OFFSET;
        final double zy = AbilityCombatHelper.findGroundY(ctx.world, zx, zz, ctx.target.getY());
        active.ex = zx;
        active.ey = zy;
        active.ez = zz;
    }

    private void spawnHazardZone(final ActiveAbility active, final AbilityContext ctx) {
        clearZone(active, ctx);

        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 3.5);
        final int zoneTicks = ctx.params.getInt(AbilityParamKeys.ZONE_TICKS,
                ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 280) + 20);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 3.0);
        final int damageInterval = ctx.params.getInt(AbilityParamKeys.DAMAGE_INTERVAL, 10);
        final int zoneColor = ctx.params.getInt(AbilityParamKeys.ZONE_COLOR, DEFAULT_ZONE_COLOR);
        final String effectId = ctx.params.getString(AbilityParamKeys.EFFECT_ID, DEFAULT_EFFECT);
        final int effectDuration = ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 60);
        final int effectAmplifier = ctx.params.getInt(AbilityParamKeys.EFFECT_AMPLIFIER, 0);

        final double zoneY = active.ey + 0.05;
        final EntityAbilityZone zone = ZoneAPI.hazardCircle(
                ctx.npc, active.ex, zoneY, active.ez, radius, zoneTicks, damage, damageInterval);
        if (zone == null) {
            return;
        }
        zone.setColor(zoneColor);
        zone.setZoneHeight(3.0f);
        zone.setEffect(effectId, effectDuration, effectAmplifier);
        zone.setVisible(true);
        zone.setGroundFill(true);
        zone.setBorder(true);
        ZONE_BY_NPC.put(active.npcUuid, zone.getUUID());
    }

    private void approachZoneTowardTarget(final ActiveAbility active, final AbilityContext ctx) {
        final EntityAbilityZone zone = resolveZone(active, ctx);
        if (zone == null || ctx.target == null || !ctx.target.isAlive()) {
            return;
        }

        final double tx = ctx.target.getX();
        final double tz = ctx.target.getZ();
        final double zx = zone.getX();
        final double zz = zone.getZ();
        final double dx = tx - zx;
        final double dz = tz - zz;
        final double dist = Math.sqrt(dx * dx + dz * dz);
        double nx = zx;
        double nz = zz;
        if (dist > 0.05) {
            final double step = Math.min(ZONE_APPROACH_SPEED, dist);
            nx = zx + (dx / dist) * step;
            nz = zz + (dz / dist) * step;
        }
        final double ny = AbilityCombatHelper.findGroundY(ctx.world, nx, nz, ctx.target.getY()) + 0.05;
        zone.moveTo(nx, ny, nz, 0, 0);
        active.ex = nx;
        active.ey = ny - 0.05;
        active.ez = nz;
    }

    private void spawnStream(final ActiveAbility active, final AbilityContext ctx) {
        final double rad = (active.yaw + 90.0) * 0.0174532925;
        final double fwdX = Math.cos(rad);
        final double fwdZ = Math.sin(rad);
        final double ox = ctx.npc.getX() + fwdX * MOUTH_FORWARD;
        final double oy = ctx.npc.getY() + MOUTH_Y;
        final double oz = ctx.npc.getZ() + fwdZ * MOUTH_FORWARD;

        // Impact = moving hazard zone (or target feet) — lob like crimson_blob.
        double ex = active.ex;
        double ey = active.ey + 0.35;
        double ez = active.ez;
        final EntityAbilityZone zone = resolveZone(active, ctx);
        if (zone != null) {
            ex = zone.getX();
            ey = zone.getY() + 0.35;
            ez = zone.getZ();
        } else if (ctx.target != null && ctx.target.isAlive()) {
            ex = ctx.target.getX();
            ey = ctx.target.getY() + 0.2;
            ez = ctx.target.getZ();
        }

        final String particles = ctx.params.getString(AbilityParamKeys.BLOB_PARTICLES, "");
        final int count = ctx.params.getInt(AbilityParamKeys.PARTICLE_COUNT, 12);
        final double arcHeight = ctx.params.getDouble(AbilityParamKeys.ARC_HEIGHT, 5.0);

        AbilityVfx.spawnOtrodieVomitStream(
                ctx.world, ox, oy, oz, ex, ey, ez, arcHeight, particles, count);

        if (zone != null) {
            AbilityVfx.spawnOtrodieVomitCloud(
                    ctx.world, zone.getX(), zone.getY() + 0.4, zone.getZ(), particles, count);
        }
    }

    private static EntityAbilityZone resolveZone(final ActiveAbility active, final AbilityContext ctx) {
        final UUID zoneId = ZONE_BY_NPC.get(active.npcUuid);
        if (zoneId == null) {
            return null;
        }
        final World world = mcWorld(ctx);
        if (!(world instanceof ServerWorld)) {
            return null;
        }
        final Entity entity = ((ServerWorld) world).getEntity(zoneId);
        if (entity instanceof EntityAbilityZone && !entity.removed) {
            return (EntityAbilityZone) entity;
        }
        ZONE_BY_NPC.remove(active.npcUuid);
        return null;
    }

    private static void clearZone(final ActiveAbility active, final AbilityContext ctx) {
        final EntityAbilityZone zone = resolveZone(active, ctx);
        if (zone != null) {
            ZoneAPI.remove(zone);
        }
        clearZoneRef(active.npcUuid);
    }

    private static void clearZoneRef(final UUID npcUuid) {
        if (npcUuid != null) {
            ZONE_BY_NPC.remove(npcUuid);
        }
    }

    private static World mcWorld(final AbilityContext ctx) {
        try {
            final Object mc = ctx.npc.getMCEntity();
            if (mc instanceof Entity) {
                return ((Entity) mc).level;
            }
        } catch (final Exception ignored) {
        }
        return null;
    }

    private static void forceFecalWave(final AbilityContext ctx) {
        try {
            ScriptDataUtil.putString(ctx.npc.getStoreddata(), FORCED_ABILITY_KEY, FECAL_WAVE_ID);
        } catch (final Exception ignored) {
        }
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        clearZone(active, ctx);
        AbilityTelegraph.clear(active, ctx);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        forceFecalWave(ctx);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ()),
                "minecraft:entity.ravager.stunned",
                0.8F,
                0.7F);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        clearZone(active, ctx);
        AbilityTelegraph.clear(active, ctx);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
