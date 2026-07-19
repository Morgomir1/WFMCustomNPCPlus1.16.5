package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.abilities.integration.WfmIntegration;
import noppes.npcs.api.NpcAPI;

import java.util.Map;
import java.util.Set;

/**
 * Бросок сети в выбранную круглую область: telegraph → charge → опутывание всех в круге.
 */
public final class NetThrowAbility implements CnpcAbility {
    public static final String ID = "net_throw";

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
        return AbilityDefaults.netThrow();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.EFFECT_DURATION,
                AbilityParamKeys.EFFECT_AMPLIFIER);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return false;
        }

        active.jumpStyle = false;
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 10);
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);

        final double tx = ctx.target.getX();
        final double tz = ctx.target.getZ();
        final double ty = AbilityCombatHelper.findGroundY(ctx.world, tx, tz, ctx.target.getY());
        active.ex = tx;
        active.ey = ty;
        active.ez = tz;

        final double dx = tx - ctx.npc.getX();
        final double dz = tz - ctx.npc.getZ();
        active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
        ctx.npc.setRotation(active.yaw);

        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.fishing_bobber.throw", 0.9F, 0.8F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return throwNet(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 4 == 0) {
            AbilityVfx.spawnNetTrail(
                    ctx.world,
                    ctx.npc.getX(),
                    ctx.npc.getY() + 1.0,
                    ctx.npc.getZ());
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = 1;
        return TickResult.CONTINUE;
    }

    private TickResult throwNet(final ActiveAbility active, final AbilityContext ctx) {
        final double x = active.ex;
        final double y = active.ey;
        final double z = active.ez;
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 3.5);
        final int duration = ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 60);
        final int amplifier = ctx.params.getInt(AbilityParamKeys.EFFECT_AMPLIFIER, 3);

        AbilityVfx.spawnNetTrail(ctx.world, x, y + 0.5, z);
        AbilityVfx.spawnNetTrail(ctx.world, ctx.npc.getX(), ctx.npc.getY() + 1.0, ctx.npc.getZ());
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(x, y, z),
                "minecraft:entity.fishing_bobber.splash",
                1.0F,
                1.0F);

        final int ensnared = WfmIntegration.ensnareAroundPoint(ctx.npc, x, y, z, radius, duration);
        if (ensnared < 0) {
            // Без WFM — сильный Slow по площади
            active.hitUuids.clear();
            AbilityCombatHelper.applyPotionNearby(
                    active, ctx, x, y + 0.5, z,
                    radius, AbilityEffectType.SLOWNESS, duration, amplifier);
        }

        return TickResult.FINISHED;
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
