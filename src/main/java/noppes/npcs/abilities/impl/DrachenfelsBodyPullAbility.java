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
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.Map;
import java.util.Set;

/**
 * Body pull combo: windup → pull players in → circle telegraph under caster →
 * delayed 15 pure AoE. Follow-up body cast is orchestrated in JS via {@code df_forced_ability}.
 */
public final class DrachenfelsBodyPullAbility implements CnpcAbility {
    public static final String ID = "drachenfels_body_pull";

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
        return AbilityDefaults.drachenfelsBodyPull();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.MAX_RANGE,
                AbilityParamKeys.HIT_RADIUS,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = false;
        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY();
        active.sz = ctx.npc.getZ();
        active.ex = active.sx;
        // Feet ground — flying body + low ceiling must not latch telegraph onto roof.
        active.ey = AbilityCombatHelper.findFeetGroundY(ctx.world, active.sx, active.sz, active.sy);
        active.ez = active.sz;
        active.hitUuids.clear();
        active.telegraphIds.clear();
        active.telegraphId = null;

        if (ctx.target != null && ctx.target.isAlive()) {
            final double dx = ctx.target.getX() - ctx.npc.getX();
            final double dz = ctx.target.getZ() - ctx.npc.getZ();
            active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
            ctx.npc.setRotation(active.yaw);
        }

        final int chargeTicks = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 16));
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = chargeTicks;
        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);

        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.evoker.prepare_attack", 1.0F, 0.5F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickWarn(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 3 == 0) {
            AbilityVfx.spawnDarkCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
            AbilityVfx.spawnSoulFogCloud(ctx.world, ctx.npc.getX(), ctx.npc.getY() + 0.3, ctx.npc.getZ(), 1.1F);
        }

        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }

        beginPullAndWarn(active, ctx);
        return TickResult.CONTINUE;
    }

    private void beginPullAndWarn(final ActiveAbility active, final AbilityContext ctx) {
        final double x = ctx.npc.getX();
        final double y = ctx.npc.getY();
        final double z = ctx.npc.getZ();
        final double pullRange = ctx.params.getDouble(AbilityParamKeys.MAX_RANGE, 12.0);
        final double standOff = ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 2.0);
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 5.0);
        // Readable dodge window after pull (~1.8s).
        final int warnTicks = Math.max(36, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 36));
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, TelegraphAPI.DEFAULT_COLOR);

        active.ex = x;
        active.ey = AbilityCombatHelper.findFeetGroundY(ctx.world, x, z, y);
        active.ez = z;

        AbilityCombatHelper.pullPlayersToward(ctx, x, y, z, pullRange, standOff);

        AbilityVfx.spawnSoulWave(ctx.world, x, active.ey + 0.15, z, Math.min(pullRange, 8.0));
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(x, y, z),
                "minecraft:entity.elder_guardian.curse",
                0.95F,
                0.7F);

        // Зона удара только после стяжки: Y уже под ногами, без resolveGroundY (+2 → потолок).
        AbilityTelegraph.clear(active, ctx);
        final String tid = TelegraphAPI.circleAt(
                ctx.npc, active.ex, active.ey + 0.05, active.ez, radius, warnTicks, color);
        if (tid != null && !tid.isEmpty()) {
            active.telegraphId = tid;
            active.telegraphIds.add(tid);
        }

        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = warnTicks;
        active.hitUuids.clear();
    }

    private TickResult tickWarn(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 4 == 0) {
            AbilityVfx.spawnDarkCharge(ctx.world, active.ex, active.ey + 0.35, active.ez);
        }

        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }

        doBlast(active, ctx);
        return TickResult.FINISHED;
    }

    private void doBlast(final ActiveAbility active, final AbilityContext ctx) {
        final double x = active.ex;
        final double y = active.ey;
        final double z = active.ez;
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 5.0);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 15.0);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 0.7);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.25);

        AbilityTelegraph.clear(active, ctx);
        AbilityVfx.spawnSoulBurst(ctx.world, x, y + 0.2, z, radius);
        AbilityVfx.spawnDarkCharge(ctx.world, x, y + 0.45, z);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(x, y, z),
                "minecraft:entity.wither.break_block",
                1.0F,
                0.55F);

        active.hitUuids.clear();
        AbilityCombatHelper.damageNearbyPure(
                active, ctx, x, y + 0.5, z,
                radius, damage, 0, 0, knockback, knockbackY, false);
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        AbilityTelegraph.clear(active, ctx);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityTelegraph.clear(active, ctx);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
