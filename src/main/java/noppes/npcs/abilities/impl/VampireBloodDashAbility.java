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
import noppes.npcs.abilities.event.VampireBloodTrailHandler;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntityLiving;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.Map;
import java.util.Set;

/**
 * Homing dash that cannot be dodged: interpolates to the target's current
 * position every tick and snaps onto them at the end. The hit player then
 * leaves blood puddles that heal the caster.
 */
public final class VampireBloodDashAbility implements CnpcAbility {
    public static final String ID = "vampire_blood_dash";

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
        return AbilityDefaults.vampireBloodDash();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.DISTANCE,
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y,
                AbilityParamKeys.HIT_RADIUS,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.ZONE_TICKS,
                AbilityParamKeys.DAMAGE_INTERVAL,
                AbilityParamKeys.ZONE_COLOR,
                AbilityParamKeys.HEAL_PER_TICK,
                AbilityParamKeys.TRAIL_TICKS,
                AbilityParamKeys.PUDDLE_INTERVAL,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return false;
        }
        active.jumpStyle = false;
        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY();
        active.sz = ctx.npc.getZ();
        lockAim(active, ctx);
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 18);
        active.hitUuids.clear();
        active.telegraphIds.clear();
        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.holdInPlace(ctx.npc, active.sx, active.sy, active.sz);
        ctx.npc.setRotation(active.yaw);
        spawnMarkTelegraph(active, ctx);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.phantom.bite", 0.9F, 0.7F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
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
        lockAim(active, ctx);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 4 == 0) {
            AbilityVfx.spawnBloodCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
            retargetMark(active, ctx);
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 6));
        active.hitUuids.clear();
        AbilityVfx.spawnBloodBurst(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ(), 1.4);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.ravager.roar", 0.75F, 1.35F);
        return TickResult.CONTINUE;
    }

    private TickResult tickActive(final ActiveAbility active, final AbilityContext ctx) {
        lockAim(active, ctx);
        final boolean lastTick = active.ticksLeft <= 1;
        final double tx = active.ex;
        final double ty = active.ey;
        final double tz = active.ez;
        final double nx = ctx.npc.getX();
        final double ny = ctx.npc.getY();
        final double nz = ctx.npc.getZ();

        final double cx;
        final double cy;
        final double cz;
        if (lastTick) {
            cx = tx;
            cy = ty;
            cz = tz;
        } else {
            final double t = 1.0 / Math.max(1, active.ticksLeft);
            cx = nx + (tx - nx) * t;
            cy = ny + (ty - ny) * t;
            cz = nz + (tz - nz) * t;
        }

        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setPosition(cx, cy, cz);
        ctx.npc.setRotation(active.yaw);

        final double rad = (active.yaw + 90.0) * 0.0174532925;
        final double knockDirX = Math.cos(rad);
        final double knockDirZ = Math.sin(rad);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 14.0);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 0.8);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.2);
        final double hitRadius = ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 2.2);

        AbilityCombatHelper.damageNearby(
                active, ctx, cx, cy + 1.0, cz,
                hitRadius, damage, knockDirX, knockDirZ, knockback, knockbackY, true);
        AbilityVfx.spawnBloodCharge(ctx.world, cx, cy, cz);
        AbilityVfx.spawnDashTrail(ctx.world, cx, cy, cz);

        if (lastTick) {
            guaranteeHit(active, ctx, damage, knockDirX, knockDirZ, knockback, knockbackY);
            AbilityVfx.spawnBloodBurst(ctx.world, cx, cy, cz, 2.0);
            ctx.world.playSoundAt(
                    NpcAPI.Instance().getIPos(cx, cy, cz),
                    "minecraft:entity.player.attack.crit",
                    1.0F,
                    0.75F);
            VampireBloodTrailHandler.start(ctx.npc, ctx.target, ctx.params);
            return TickResult.FINISHED;
        }

        active.ticksLeft--;
        return TickResult.CONTINUE;
    }

    private static void lockAim(final ActiveAbility active, final AbilityContext ctx) {
        final IEntityLiving target = ctx.target;
        if (target != null && target.isAlive()) {
            active.ex = target.getX();
            active.ez = target.getZ();
            active.ey = target.getY();
            final double dx = active.ex - ctx.npc.getX();
            final double dz = active.ez - ctx.npc.getZ();
            active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
            return;
        }
        if (active.ex == 0.0 && active.ez == 0.0 && active.ey == 0.0) {
            active.ex = ctx.npc.getX();
            active.ey = ctx.npc.getY();
            active.ez = ctx.npc.getZ();
        }
    }

    private static void spawnMarkTelegraph(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.params.getInt(AbilityParamKeys.TELEGRAPH, 0) == 0 && ctx.target == null) {
            return;
        }
        final int chargeTicks = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 18));
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, TelegraphAPI.DEFAULT_COLOR);
        final double radius = ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 2.2);
        final IEntityLiving target = ctx.target;
        if (target == null) {
            return;
        }
        final String id = TelegraphAPI.circle(
                ctx.npc,
                target.getX(),
                target.getY(),
                target.getZ(),
                radius,
                chargeTicks,
                color);
        if (id == null || id.isEmpty()) {
            return;
        }
        active.telegraphIds.add(id);
        TelegraphAPI.follow(id, target);
    }

    private static void retargetMark(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || active.telegraphIds.isEmpty()) {
            return;
        }
        TelegraphAPI.follow(active.telegraphIds.get(0), ctx.target);
    }

    private static void guaranteeHit(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double damage,
            final double knockDirX,
            final double knockDirZ,
            final double knockback,
            final double knockbackY) {
        final IEntityLiving target = ctx.target;
        if (target == null || !target.isAlive()) {
            return;
        }
        final String id = String.valueOf(target.getUUID());
        if (active.hitUuids.contains(id)) {
            return;
        }
        target.damage((float) damage);
        target.setMotionX(knockDirX * knockback);
        target.setMotionY(knockbackY);
        target.setMotionZ(knockDirZ * knockback);
        AbilityVfx.spawnHitParticle(ctx.world, target);
        active.hitUuids.add(id);
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
