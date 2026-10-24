package noppes.npcs.abilities.impl;

import net.minecraft.entity.Entity;
import noppes.npcs.abilities.AbilityCombatHelper;
import noppes.npcs.abilities.AbilityContext;
import noppes.npcs.abilities.AbilityDefaults;
import noppes.npcs.abilities.AbilityParamKeys;
import noppes.npcs.abilities.AbilityParams;
import noppes.npcs.abilities.ActiveAbility;
import noppes.npcs.abilities.CnpcAbility;
import noppes.npcs.abilities.TickResult;

import java.util.Map;
import java.util.Set;

/**
 * Короткий проезд по уже заданному yaw. Рикошет Кровохряка: скрипт выбирает направление,
 * движение идёт каждый серверный тик.
 */
public final class B1SlideAbility implements CnpcAbility {
    public static final String ID = "b1_slide";

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
        return AbilityDefaults.b1Slide();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.DISTANCE,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.TELEGRAPH);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = false;
        active.yaw = ctx.npc.getRotation();
        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY();
        active.sz = ctx.npc.getZ();
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 8));
        AbilityCombatHelper.stopNavigation(ctx.npc);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        final int total = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 8));
        final double distance = ctx.params.getDouble(AbilityParamKeys.DISTANCE, 10.0);
        if (active.ticksLeft <= 0) {
            move(active, ctx, distance, 1.0);
            return TickResult.FINISHED;
        }
        final double progress = 1.0 - (active.ticksLeft - 1) / (double) total;
        move(active, ctx, distance, progress);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        active.ticksLeft--;
        if (active.ticksLeft <= 0) {
            move(active, ctx, distance, 1.0);
            return TickResult.FINISHED;
        }
        return TickResult.CONTINUE;
    }

    private void move(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double distance,
            final double progress) {
        final double rad = Math.toRadians(active.yaw + 90.0F);
        final double x = active.sx + Math.cos(rad) * distance * progress;
        final double z = active.sz + Math.sin(rad) * distance * progress;
        final double y = AbilityCombatHelper.findGroundY(ctx.world, x, z, active.sy);
        ctx.npc.setPosition(x, y, z);
        ctx.npc.setRotation(active.yaw);
        final Entity mc = ctx.npc.getMCEntity();
        if (mc != null) {
            mc.fallDistance = 0.0F;
            mc.hasImpulse = true;
        }
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
