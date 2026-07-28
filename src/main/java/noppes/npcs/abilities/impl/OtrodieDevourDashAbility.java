package noppes.npcs.abilities.impl;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.DamageSource;
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
import noppes.npcs.abilities.event.OtrodieCombatHandler;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.IEntityLiving;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.Map;
import java.util.Set;

/**
 * Отродье — «Рывок / пожирание»: red line telegraph → dash grab → freeze+teleport eat 5s.
 * 15 melee-хитов (OtrodieCombatHandler.meter) → spit без хила; timeout → heal + spit.
 */
public final class OtrodieDevourDashAbility implements CnpcAbility {
    public static final String ID = "otrodie_devour_dash";
    /** Must match {@link OtrodieCombatHandler#DEVOUR_PHASE_EAT}. */
    public static final int PHASE_EAT = OtrodieCombatHandler.DEVOUR_PHASE_EAT;

    private static final int DEFAULT_TELEGRAPH_COLOR = 0xC0FF3030;
    private static final int DEFAULT_EAT_TICKS = 100;
    private static final float HEAL_SPIT_DAMAGE = 8.0F;
    private static final double MOUTH_FORWARD = 1.2;
    private static final double MOUTH_EYE_FACTOR = 0.3;

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
        return AbilityDefaults.otrodieDevourDash();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.DISTANCE,
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.HIT_RADIUS,
                AbilityParamKeys.HIT_COUNT,
                AbilityParamKeys.HEAL_ON_FAIL,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR,
                AbilityParamKeys.PARTICLE_COUNT,
                AbilityParamKeys.BLOB_PARTICLES,
                AbilityParamKeys.SPAWN_PUDDLE);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = false;
        active.markers.clear();
        active.telegraphIds.clear();
        active.hitUuids.clear();
        active.meter = 0.0F;

        if (!AbilityCombatHelper.computeDashEndPoints(active, ctx)) {
            return false;
        }

        spawnChargeTelegraph(active, ctx);

        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 24);
        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.ravager.roar", 0.85F, 0.55F);
        return true;
    }

    private void spawnChargeTelegraph(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.params.getInt(AbilityParamKeys.TELEGRAPH, 0) != 0) {
            return;
        }
        final int chargeTicks = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 24));
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, DEFAULT_TELEGRAPH_COLOR);
        final double hitRadius = ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 1.6);
        final double distance = Math.sqrt(
                (active.ex - active.sx) * (active.ex - active.sx)
                        + (active.ez - active.sz) * (active.ez - active.sz));
        if (distance < 0.5) {
            return;
        }
        final String lineId = TelegraphAPI.line(
                ctx.npc,
                active.sx,
                active.sy,
                active.sz,
                active.yaw,
                distance,
                hitRadius * 2.0,
                chargeTicks,
                color);
        if (lineId != null && !lineId.isEmpty()) {
            active.telegraphIds.add(lineId);
        }
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickDash(active, ctx);
        }
        if (active.phase == PHASE_EAT) {
            return tickEat(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        AbilityCombatHelper.zeroHorizontalMotion(ctx.npc);
        ctx.npc.setRotation(active.yaw);

        if (active.ticksLeft % 4 == 0) {
            AbilityVfx.spawnOtrodieVomitCloud(
                    ctx.world,
                    ctx.npc.getX(),
                    ctx.npc.getY() + 0.5,
                    ctx.npc.getZ(),
                    ctx.params.getString(AbilityParamKeys.BLOB_PARTICLES, ""),
                    Math.max(4, ctx.params.getInt(AbilityParamKeys.PARTICLE_COUNT, 12) / 2));
        }

        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }

        AbilityTelegraph.clear(active, ctx);
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 8);
        active.hitUuids.clear();
        AbilityVfx.spawnStartBurst(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), false);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.ravager.attack", 1.0F, 0.7F);
        return TickResult.CONTINUE;
    }

    private TickResult tickDash(final ActiveAbility active, final AbilityContext ctx) {
        final int total = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 8));
        if (active.ticksLeft <= 0) {
            finishMissedDash(active, ctx);
            return TickResult.FINISHED;
        }

        final double progress = 1.0 - (active.ticksLeft - 1) / (double) total;
        final double[] point = AbilityCombatHelper.resolveDashPointAtProgress(
                ctx, active.sx, active.sy, active.sz, active.ex, active.ez, progress);
        final double cx = point[0];
        final double cy = point[1];
        final double cz = point[2];

        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setPosition(cx, cy, cz);
        ctx.npc.setRotation(active.yaw);
        AbilityCombatHelper.zeroHorizontalMotion(ctx.npc);

        final IEntityLiving grabbed = findGrabVictim(active, ctx);
        if (grabbed != null) {
            beginEat(active, ctx, grabbed);
            return TickResult.CONTINUE;
        }

        AbilityVfx.spawnDashTrail(ctx.world, cx, cy, cz);
        active.ticksLeft--;
        return TickResult.CONTINUE;
    }

    private void beginEat(final ActiveAbility active, final AbilityContext ctx, final IEntityLiving victim) {
        AbilityTelegraph.clear(active, ctx);
        active.hitUuids.clear();
        active.hitUuids.add(String.valueOf(victim.getUUID()));
        active.meter = 0.0F;
        active.phase = PHASE_EAT;
        active.ticksLeft = DEFAULT_EAT_TICKS;
        AbilityCombatHelper.stopNavigation(ctx.npc);
        holdVictimAtMouth(active, ctx, victim);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.generic.eat", 1.1F, 0.55F);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.ravager.stunned", 0.8F, 0.7F);
    }

    private TickResult tickEat(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        AbilityCombatHelper.zeroHorizontalMotion(ctx.npc);
        ctx.npc.setRotation(active.yaw);

        final IEntityLiving victim = resolveGrabbedVictim(active, ctx);
        if (victim == null || !victim.isAlive()) {
            active.hitUuids.clear();
            return TickResult.FINISHED;
        }

        holdVictimAtMouth(active, ctx, victim);

        final float hitCount = (float) ctx.params.getInt(AbilityParamKeys.HIT_COUNT, 15);
        if (active.meter >= hitCount) {
            spitVictim(active, ctx, victim, false);
            return TickResult.FINISHED;
        }

        if (active.ticksLeft % 5 == 0) {
            AbilityVfx.spawnOtrodieVomitCloud(
                    ctx.world,
                    victim.getX(),
                    victim.getY() + 0.4,
                    victim.getZ(),
                    ctx.params.getString(AbilityParamKeys.BLOB_PARTICLES, ""),
                    Math.max(3, ctx.params.getInt(AbilityParamKeys.PARTICLE_COUNT, 12) / 3));
        }

        active.ticksLeft--;
        if (active.ticksLeft <= 0) {
            spitVictim(active, ctx, victim, true);
            return TickResult.FINISHED;
        }
        return TickResult.CONTINUE;
    }

    private void holdVictimAtMouth(
            final ActiveAbility active,
            final AbilityContext ctx,
            final IEntityLiving victim) {
        final double rad = (active.yaw + 90.0) * 0.0174532925;
        final double fwdX = Math.cos(rad);
        final double fwdZ = Math.sin(rad);
        final double eye = resolveEyeHeight(ctx.npc);
        final double mx = ctx.npc.getX() + fwdX * MOUTH_FORWARD;
        final double my = ctx.npc.getY() + eye * MOUTH_EYE_FACTOR;
        final double mz = ctx.npc.getZ() + fwdZ * MOUTH_FORWARD;
        try {
            victim.setPosition(mx, my, mz);
            victim.setMotionX(0.0);
            victim.setMotionY(0.0);
            victim.setMotionZ(0.0);
        } catch (final Exception ignored) {
        }
    }

    private void spitVictim(
            final ActiveAbility active,
            final AbilityContext ctx,
            final IEntityLiving victim,
            final boolean healBoss) {
        if (healBoss) {
            healCaster(ctx, ctx.params.getDouble(AbilityParamKeys.HEAL_ON_FAIL, 200.0));
            dealPureDamage(victim, HEAL_SPIT_DAMAGE);
        }

        final double rad = (active.yaw + 90.0) * 0.0174532925;
        final double fwdX = Math.cos(rad);
        final double fwdZ = Math.sin(rad);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 1.6);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.45);
        try {
            victim.setMotionX(fwdX * knockback);
            victim.setMotionY(knockbackY);
            victim.setMotionZ(fwdZ * knockback);
        } catch (final Exception ignored) {
        }

        AbilityVfx.spawnOtrodieVomitCloud(
                ctx.world,
                victim.getX(),
                victim.getY() + 0.5,
                victim.getZ(),
                ctx.params.getString(AbilityParamKeys.BLOB_PARTICLES, ""),
                ctx.params.getInt(AbilityParamKeys.PARTICLE_COUNT, 12));
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(victim.getX(), victim.getY(), victim.getZ()),
                "minecraft:entity.slime.squish",
                1.2F,
                0.4F);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.ravager.roar", 0.9F, 0.8F);

        active.hitUuids.clear();
        active.meter = 0.0F;
    }

    private IEntityLiving findGrabVictim(final ActiveAbility active, final AbilityContext ctx) {
        final double halfWidth = ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 1.6);
        final double midX = (active.sx + active.ex) * 0.5;
        final double midZ = (active.sz + active.ez) * 0.5;
        final double midY = (active.sy + active.ey) * 0.5;
        final double len = AbilityCombatHelper.flatDistance(active.sx, active.sz, active.ex, active.ez);
        final int range = (int) Math.ceil(len * 0.5 + halfWidth + 2.0);
        final IEntity[] list = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(midX, midY, midZ),
                range,
                -1);
        if (list == null) {
            return null;
        }

        IEntityLiving best = null;
        double bestDist = Double.MAX_VALUE;
        for (final IEntity ent : list) {
            if (!(ent instanceof IEntityLiving) || !ent.isAlive()) {
                continue;
            }
            if (!AbilityCombatHelper.isHostileToBoss(ctx.npc, ent)) {
                continue;
            }
            if (!AbilityCombatHelper.isInCorridor(
                    ent.getX(), ent.getZ(),
                    active.sx, active.sz, active.ex, active.ez,
                    halfWidth)) {
                continue;
            }
            final double d = AbilityCombatHelper.flatDistance(
                    ctx.npc.getX(), ctx.npc.getZ(), ent.getX(), ent.getZ());
            if (d < bestDist) {
                bestDist = d;
                best = (IEntityLiving) ent;
            }
        }
        return best;
    }

    private IEntityLiving resolveGrabbedVictim(final ActiveAbility active, final AbilityContext ctx) {
        if (active.hitUuids.isEmpty()) {
            return null;
        }
        final String wanted = active.hitUuids.iterator().next();
        final IEntity[] list = ctx.world.getNearbyEntities(ctx.npc.getPos(), 8, -1);
        if (list == null) {
            return null;
        }
        for (final IEntity ent : list) {
            if (!(ent instanceof IEntityLiving)) {
                continue;
            }
            if (wanted.equals(String.valueOf(ent.getUUID()))) {
                return (IEntityLiving) ent;
            }
        }
        return null;
    }

    private void finishMissedDash(final ActiveAbility active, final AbilityContext ctx) {
        final double[] point = AbilityCombatHelper.resolveDashPointAtProgress(
                ctx, active.sx, active.sy, active.sz, active.ex, active.ez, 1.0);
        ctx.npc.setPosition(point[0], point[1], point[2]);
        AbilityVfx.spawnLandBurst(ctx.world, point[0], point[1], point[2], false);
        AbilityTelegraph.clear(active, ctx);
    }

    private static void healCaster(final AbilityContext ctx, final double amount) {
        if (amount <= 0.0) {
            return;
        }
        try {
            final Object mc = ctx.npc.getMCEntity();
            if (mc instanceof LivingEntity) {
                final LivingEntity living = (LivingEntity) mc;
                final float healed = Math.min(living.getMaxHealth(), living.getHealth() + (float) amount);
                living.setHealth(healed);
            }
        } catch (final Exception ignored) {
        }
    }

    /** Чистый MAGIC-урон (как Ability Zone / warpfire). */
    private static void dealPureDamage(final IEntityLiving victim, final float amount) {
        if (victim == null || !victim.isAlive() || amount <= 0.0F) {
            return;
        }
        try {
            final Object mc = victim.getMCEntity();
            if (mc instanceof LivingEntity) {
                ((LivingEntity) mc).hurt(DamageSource.MAGIC, amount);
                return;
            }
        } catch (final Exception ignored) {
        }
        try {
            victim.damage(amount);
        } catch (final Exception ignored) {
        }
    }

    private static double resolveEyeHeight(final IEntityLiving entity) {
        try {
            return entity.getEyeHeight();
        } catch (final Exception e) {
            return 1.6;
        }
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        AbilityTelegraph.clear(active, ctx);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        maybeSpawnPhase2Puddle(ctx);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == PHASE_EAT) {
            final IEntityLiving victim = resolveGrabbedVictim(active, ctx);
            if (victim != null && victim.isAlive()) {
                spitVictim(active, ctx, victim, false);
            }
        }
        AbilityTelegraph.clear(active, ctx);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        maybeSpawnPhase2Puddle(ctx);
    }

    private static void maybeSpawnPhase2Puddle(final AbilityContext ctx) {
        if (ctx.params.getInt(AbilityParamKeys.SPAWN_PUDDLE, 0) == 0) {
            return;
        }
        // Маленькая кислотная лужа — как hitRadius у spreading filth
        OtrodieSpreadingFilthAbility.spawnSingleUnderNpc(ctx.npc, 2.6);
    }
}
