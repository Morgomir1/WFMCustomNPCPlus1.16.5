package noppes.npcs.abilities.impl;

import net.minecraft.entity.Entity;
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
 * Аркан Грула: каждый серверный тик подтягивает цель на шаг. Обрыв — ключ b1_lasso_cut у Грула.
 */
public final class B1PullAbility implements CnpcAbility {
    public static final String ID = "b1_pull";
    private static final String CUT_KEY = "b1_lasso_cut";

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
        return true;
    }

    @Override
    public Map<String, Object> defaultParams() {
        return AbilityDefaults.b1Pull();
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
        if (ctx.target == null || !ctx.target.isAlive()) {
            return false;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 40));
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (isCut(ctx) || ctx.target == null || !ctx.target.isAlive()) {
            return TickResult.FINISHED;
        }
        if (active.ticksLeft <= 0) {
            return TickResult.FINISHED;
        }
        final Entity npc = ctx.npc.getMCEntity();
        final Entity target = ctx.target.getMCEntity();
        if (npc == null || target == null) {
            return TickResult.FINISHED;
        }
        final double dx = npc.getX() - target.getX();
        final double dz = npc.getZ() - target.getZ();
        final double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.5) {
            return TickResult.FINISHED;
        }
        double step = ctx.params.getDouble(AbilityParamKeys.DISTANCE, 0.15);
        if (step < 0.05) {
            step = 0.15;
        }
        if (step > len) {
            step = len;
        }
        target.setPos(target.getX() + dx / len * step, target.getY(), target.getZ() + dz / len * step);
        target.setDeltaMovement(0.0, target.getDeltaMovement().y, 0.0);
        target.hasImpulse = true;
        target.fallDistance = 0.0F;
        active.ticksLeft--;
        return active.ticksLeft > 0 ? TickResult.CONTINUE : TickResult.FINISHED;
    }

    private boolean isCut(final AbilityContext ctx) {
        try {
            final Object value = ctx.npc.getStoreddata().get(CUT_KEY);
            return value != null && "1".equals(String.valueOf(value));
        } catch (final Exception ignored) {
            return false;
        }
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        clearCut(ctx);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        clearCut(ctx);
    }

    private void clearCut(final AbilityContext ctx) {
        try {
            ctx.npc.getStoreddata().put(CUT_KEY, "0");
            ctx.npc.getStoreddata().put("b1_lasso", "");
        } catch (final Exception ignored) {
        }
    }
}
