package noppes.npcs.abilities.impl;

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
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.Map;
import java.util.Set;

/**
 * Отродье — «Каловые массы»: усечённый конус назад (после hell vomit), red telegraph,
 * burst урон + poison/slowness.
 */
public final class OtrodieFecalWaveAbility implements CnpcAbility {
    public static final String ID = "otrodie_fecal_wave";

    private static final int DEFAULT_TELEGRAPH_COLOR = 0xC0FF3030;
    private static final String DEFAULT_EFFECT = "minecraft:poison;minecraft:slowness";
    private static final double DEFAULT_MIN_DIST = 2.0;
    private static final double DEFAULT_MAX_DIST = 12.0;
    private static final double DEFAULT_HALF_ANGLE = 55.0;

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
        return AbilityDefaults.otrodieFecalWave();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.DISTANCE,
                AbilityParamKeys.HIT_RADIUS,
                AbilityParamKeys.CONE_HALF_ANGLE,
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y,
                AbilityParamKeys.EFFECT_ID,
                AbilityParamKeys.EFFECT_DURATION,
                AbilityParamKeys.EFFECT_AMPLIFIER,
                AbilityParamKeys.PARTICLE_COUNT,
                AbilityParamKeys.BLOB_PARTICLES,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = false;
        active.markers.clear();
        active.telegraphIds.clear();
        active.hitUuids.clear();

        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY();
        active.sz = ctx.npc.getZ();

        // Конус назад от текущего взгляда; босс продолжает смотреть вперёд.
        final float faceYaw = ctx.npc.getRotation();
        active.yaw = faceYaw + 180.0f;

        final double minDist = Math.max(0.5, ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, DEFAULT_MIN_DIST));
        final double maxDist = Math.max(minDist + 0.5,
                ctx.params.getDouble(AbilityParamKeys.DISTANCE, DEFAULT_MAX_DIST));
        final double halfAngle = ctx.params.getDouble(AbilityParamKeys.CONE_HALF_ANGLE, DEFAULT_HALF_ANGLE);

        active.ex = active.sx;
        active.ey = active.sy;
        active.ez = active.sz;
        // markers[0]: apex + min/max (apex = позиция босса)
        active.markers.add(new double[]{active.sx, active.sy, active.sz, faceYaw, minDist, maxDist});

        final int chargeTicks = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 24);
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, DEFAULT_TELEGRAPH_COLOR);
        final String tid = TelegraphAPI.coneTruncated(
                ctx.npc, active.sx, active.sy, active.sz, active.yaw,
                minDist, maxDist, halfAngle, chargeTicks, color);
        if (tid != null && !tid.isEmpty()) {
            active.telegraphIds.add(tid);
        }

        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = chargeTicks;
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(faceYaw);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.ravager.roar", 0.75F, 0.45F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return doBurst(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        final float faceYaw = faceYawFromMarkers(active);
        ctx.npc.setRotation(faceYaw);

        if (active.ticksLeft % 4 == 0) {
            AbilityVfx.spawnOtrodieVomitCloud(
                    ctx.world,
                    active.sx,
                    active.sy + 0.4,
                    active.sz,
                    ctx.params.getString(AbilityParamKeys.BLOB_PARTICLES, ""),
                    Math.max(4, ctx.params.getInt(AbilityParamKeys.PARTICLE_COUNT, 12) / 2));
        }

        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = 1;
        return TickResult.CONTINUE;
    }

    private TickResult doBurst(final ActiveAbility active, final AbilityContext ctx) {
        final double halfAngle = ctx.params.getDouble(AbilityParamKeys.CONE_HALF_ANGLE, DEFAULT_HALF_ANGLE);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 14.0);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 0.85);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.25);
        final String effectId = ctx.params.getString(AbilityParamKeys.EFFECT_ID, DEFAULT_EFFECT);
        final int effectDuration = ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 60);
        final int effectAmplifier = ctx.params.getInt(AbilityParamKeys.EFFECT_AMPLIFIER, 0);
        final int particleCount = ctx.params.getInt(AbilityParamKeys.PARTICLE_COUNT, 12);
        final String particles = ctx.params.getString(AbilityParamKeys.BLOB_PARTICLES, "");

        double apexX = active.sx;
        double apexY = active.sy;
        double apexZ = active.sz;
        double minDist = DEFAULT_MIN_DIST;
        double maxDist = DEFAULT_MAX_DIST;
        if (!active.markers.isEmpty()) {
            final double[] m = active.markers.get(0);
            apexX = m[0];
            apexY = m[1];
            apexZ = m[2];
            if (m.length > 5) {
                minDist = m[4];
                maxDist = m[5];
            }
        }

        AbilityCombatHelper.damageInTruncatedCone(
                active, ctx,
                apexX, apexY, apexZ,
                active.yaw, halfAngle,
                minDist, maxDist,
                damage, knockback, knockbackY, 0);

        applyEffectsInCone(active, ctx, apexX, apexY, apexZ, halfAngle, minDist, maxDist,
                effectId, effectDuration, effectAmplifier);

        AbilityVfx.spawnOtrodieFecalBurst(
                ctx.world,
                apexX, apexY, apexZ,
                active.yaw, halfAngle,
                minDist, maxDist,
                particles, particleCount);

        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(apexX, apexY, apexZ),
                "minecraft:entity.slime.squish",
                1.15F,
                0.35F);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.ravager.attack", 0.9F, 0.55F);

        AbilityTelegraph.clear(active, ctx);
        return TickResult.FINISHED;
    }

    private static void applyEffectsInCone(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double apexX,
            final double apexY,
            final double apexZ,
            final double halfAngle,
            final double minDist,
            final double maxDist,
            final String effectIdCsv,
            final int duration,
            final int amplifier) {
        if (effectIdCsv == null || effectIdCsv.isEmpty() || duration <= 0) {
            return;
        }
        final double rad = (active.yaw + 90.0) * 0.0174532925;
        final double fwdX = Math.cos(rad);
        final double fwdZ = Math.sin(rad);
        final double midX = apexX + fwdX * ((minDist + maxDist) * 0.5);
        final double midZ = apexZ + fwdZ * ((minDist + maxDist) * 0.5);
        final int range = (int) Math.ceil(maxDist + 1.5);
        final IEntity[] list = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(midX, apexY, midZ),
                range,
                -1);

        for (final IEntity ent : list) {
            final String id = String.valueOf(ent.getUUID());
            if (!active.hitUuids.contains(id)) {
                continue;
            }
            applyNamedEffects(ent, effectIdCsv, duration, amplifier);
        }
    }

    private static void applyNamedEffects(
            final IEntity entity,
            final String effectIdCsv,
            final int duration,
            final int amplifier) {
        final String[] ids = effectIdCsv.split("[;|]");
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i].trim();
            if (id.isEmpty()) {
                continue;
            }
            AbilityCombatHelper.applyNamedEffect(entity, id, duration, amplifier);
        }
    }

    private static float faceYawFromMarkers(final ActiveAbility active) {
        if (!active.markers.isEmpty()) {
            final double[] m = active.markers.get(0);
            if (m.length > 3) {
                return (float) m[3];
            }
        }
        return active.yaw - 180.0f;
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        AbilityTelegraph.clear(active, ctx);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityTelegraph.clear(active, ctx);
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
