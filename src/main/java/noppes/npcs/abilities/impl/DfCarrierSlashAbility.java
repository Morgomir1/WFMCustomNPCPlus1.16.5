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
import noppes.npcs.abilities.DrachenfelsEncounterHelper;
import noppes.npcs.abilities.TickResult;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.Map;
import java.util.Set;

/**
 * Phase 3 carrier window: truncated cone slash like {@link WhFlamingStrikeAbility},
 * with soul/dark VFX. Holds the boss in place so knockback cannot interrupt.
 */
public final class DfCarrierSlashAbility implements CnpcAbility {
    public static final String ID = "df_carrier_slash";

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
        return AbilityDefaults.dfCarrierSlash();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.DISTANCE,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.CONE_HALF_ANGLE,
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return false;
        }

        active.jumpStyle = false;
        active.markers.clear();
        active.telegraphIds.clear();

        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY();
        active.sz = ctx.npc.getZ();

        final double distance = ctx.params.getDouble(AbilityParamKeys.DISTANCE, 4.5);
        final double nearHalfWidth = ctx.params.getDouble(AbilityParamKeys.RADIUS, 1.35);
        final double halfAngle = ctx.params.getDouble(AbilityParamKeys.CONE_HALF_ANGLE, 38.0);

        final double dx = ctx.target.getX() - active.sx;
        final double dz = ctx.target.getZ() - active.sz;
        final double len = Math.sqrt(dx * dx + dz * dz);
        final double nx;
        final double nz;
        if (len < 0.05) {
            nx = 0.0;
            nz = 1.0;
        } else {
            nx = dx / len;
            nz = dz / len;
        }

        active.yaw = AbilityCombatHelper.computeYaw(nx, nz);

        final double tanHalf = Math.tan(Math.toRadians(Math.max(5.0, halfAngle)));
        final double apexBack = Math.max(0.8, nearHalfWidth / Math.max(0.05, tanHalf));
        final double apexX = active.sx - nx * apexBack;
        final double apexZ = active.sz - nz * apexBack;
        final double apexY = active.sy;

        active.ex = active.sx + nx * distance;
        active.ez = active.sz + nz * distance;
        active.ey = AbilityCombatHelper.findGroundY(ctx.world, active.ex, active.ez, active.sy);

        final double minDist = apexBack + 1.0;
        final double maxDist = apexBack + distance;
        active.markers.add(new double[]{apexX, apexY, apexZ, apexBack, minDist, maxDist});

        final int chargeTicks = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 18);
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, TelegraphAPI.DEFAULT_COLOR);
        final String tid = TelegraphAPI.coneTruncated(
                ctx.npc, apexX, apexY, apexZ, active.yaw,
                minDist, maxDist, halfAngle, chargeTicks, color);
        if (tid != null && !tid.isEmpty()) {
            active.telegraphIds.add(tid);
        }

        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = chargeTicks;
        active.hitUuids.clear();
        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.holdInPlace(ctx.npc, active.sx, active.sy, active.sz);
        ctx.npc.setRotation(active.yaw);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.vex.ambient", 0.95F, 0.55F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return doStrike(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.holdInPlace(ctx.npc, active.sx, active.sy, active.sz);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 3 == 0) {
            AbilityVfx.spawnDarkCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY() + 1.0, ctx.npc.getZ());
            AbilityVfx.spawnSoulCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY() + 1.1, ctx.npc.getZ());
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = 1;
        return TickResult.CONTINUE;
    }

    private TickResult doStrike(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.holdInPlace(ctx.npc, active.sx, active.sy, active.sz);
        final double nearHalfWidth = ctx.params.getDouble(AbilityParamKeys.RADIUS, 1.35);
        final double halfAngle = ctx.params.getDouble(AbilityParamKeys.CONE_HALF_ANGLE, 38.0);
        final double distance = ctx.params.getDouble(AbilityParamKeys.DISTANCE, 4.5);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 15.0);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 0.85);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.18);

        double apexX = active.sx;
        double apexY = active.sy;
        double apexZ = active.sz;
        double minDist = 0.0;
        double maxDist = distance;
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

        AbilityVfx.spawnSoulSlashSweep(
                ctx.world,
                active.sx, active.sy, active.sz,
                active.ex, active.ey, active.ez,
                nearHalfWidth);
        AbilityVfx.spawnSoulBurst(ctx.world, active.ex, active.ey + 0.3, active.ez, nearHalfWidth);
        AbilityVfx.spawnSoulWave(ctx.world, active.sx, active.sy + 0.15, active.sz, nearHalfWidth + 0.5);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(active.ex, active.ey, active.ez),
                "minecraft:entity.player.attack.sweep",
                1.05F,
                0.65F);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.wither.shoot", 0.55F, 1.35F);

        AbilityTelegraph.clear(active, ctx);
        return TickResult.FINISHED;
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        AbilityTelegraph.clear(active, ctx);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        DrachenfelsEncounterHelper.onAbilityEnded(ctx.npc, ID);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityTelegraph.clear(active, ctx);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        DrachenfelsEncounterHelper.onAbilityEnded(ctx.npc, ID);
    }
}
