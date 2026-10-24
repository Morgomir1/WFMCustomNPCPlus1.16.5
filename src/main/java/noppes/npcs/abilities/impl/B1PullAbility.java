package noppes.npcs.abilities.impl;

import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
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
 * Аркан Грула: босс стоит на месте, поводок к цели, каждый тик подтягивает игрока.
 * Всегда дотягивает до 1.5 блока за activeTicks (шаг = остаток / тики).
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
        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY();
        active.sz = ctx.npc.getZ();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        freeze(ctx);
        attachLeash(ctx);
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
        // Босс не двигается — фиксация позиции + стоп AI (иначе leash/навигация тянут его к игроку).
        npc.setPos(active.sx, active.sy, active.sz);
        npc.setDeltaMovement(0.0, 0.0, 0.0);
        npc.hasImpulse = true;
        AbilityCombatHelper.stopNavigation(ctx.npc);
        freeze(ctx);
        keepLeash(ctx);

        final double dx = active.sx - target.getX();
        final double dz = active.sz - target.getZ();
        final double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.5) {
            return TickResult.FINISHED;
        }
        double step = ctx.params.getDouble(AbilityParamKeys.DISTANCE, 0.55);
        // Гарантия: успеть подтянуть за оставшиеся тики.
        final double need = (len - 1.45) / Math.max(1, active.ticksLeft);
        if (need > step) {
            step = need;
        }
        // Vanilla leash рвётся >10 блоков — сразу втягиваем в рабочий радиус поводка.
        if (len > 10.0) {
            step = Math.max(step, len - 9.5);
        }
        if (step < 0.05) {
            step = 0.15;
        }
        if (step > len) {
            step = len;
        }
        target.setPos(
                target.getX() + dx / len * step,
                target.getY(),
                target.getZ() + dz / len * step);
        target.setDeltaMovement(0.0, target.getDeltaMovement().y, 0.0);
        target.hasImpulse = true;
        target.fallDistance = 0.0F;
        active.ticksLeft--;
        return active.ticksLeft > 0 ? TickResult.CONTINUE : TickResult.FINISHED;
    }

    private void attachLeash(final AbilityContext ctx) {
        try {
            final Entity npc = ctx.npc.getMCEntity();
            final Entity target = ctx.target.getMCEntity();
            if (!(npc instanceof MobEntity) || !(target instanceof PlayerEntity)) {
                return;
            }
            ((MobEntity) npc).setLeashedTo(target, true);
        } catch (final Exception ignored) {
        }
    }

    private void keepLeash(final AbilityContext ctx) {
        try {
            final Entity npc = ctx.npc.getMCEntity();
            final Entity target = ctx.target.getMCEntity();
            if (!(npc instanceof MobEntity) || !(target instanceof PlayerEntity)) {
                return;
            }
            final MobEntity mob = (MobEntity) npc;
            if (mob.getLeashHolder() != target) {
                mob.setLeashedTo(target, true);
            }
        } catch (final Exception ignored) {
        }
    }

    private void dropLeash(final AbilityContext ctx) {
        try {
            final Entity npc = ctx.npc.getMCEntity();
            if (npc instanceof MobEntity) {
                ((MobEntity) npc).dropLeash(true, false);
            }
        } catch (final Exception ignored) {
        }
    }

    private void freeze(final AbilityContext ctx) {
        try {
            ctx.npc.getAi().setWalkingSpeed(0);
            ctx.npc.getAi().setRetaliateType(3);
        } catch (final Exception ignored) {
        }
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
        dropLeash(ctx);
        clearCut(ctx);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        dropLeash(ctx);
        clearCut(ctx);
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }

    private void clearCut(final AbilityContext ctx) {
        try {
            ctx.npc.getStoreddata().put(CUT_KEY, "0");
            ctx.npc.getStoreddata().put("b1_lasso", "");
        } catch (final Exception ignored) {
        }
    }
}
