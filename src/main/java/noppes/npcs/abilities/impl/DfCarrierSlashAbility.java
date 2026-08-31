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
 * Truncated cone slash (phase 3 carrier / phase 1 guards).
 * Guards pass {@code preDash=1}: approach dash toward the player, then cone telegraph + strike.
 * Holds the caster in place during slash charge so knockback cannot interrupt.
 */
public final class DfCarrierSlashAbility implements CnpcAbility {
    public static final String ID = "df_carrier_slash";

    /** Dash toward target before slash telegraph. */
    private static final int PHASE_DASH = 3;

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
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y,
                AbilityParamKeys.MAX_RANGE,
                AbilityParamKeys.LAND_RADIUS,
                AbilityParamKeys.PRE_DASH,
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
        active.hitUuids.clear();
        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);

        if (ctx.params.getInt(AbilityParamKeys.PRE_DASH, 0) != 0
                && beginApproachDash(active, ctx)) {
            return true;
        }
        return beginSlashCharge(active, ctx);
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == PHASE_DASH) {
            return tickDash(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return doStrike(active, ctx);
        }
        return TickResult.FINISHED;
    }

    /**
     * @return true if a dash phase was started; false if already in slash range (caller starts slash).
     */
    private boolean beginApproachDash(final ActiveAbility active, final AbilityContext ctx) {
        final double sx = ctx.npc.getX();
        final double sy = ctx.npc.getY();
        final double sz = ctx.npc.getZ();
        final double tx = ctx.target.getX();
        final double tz = ctx.target.getZ();
        final double dx = tx - sx;
        final double dz = tz - sz;
        final double len = Math.sqrt(dx * dx + dz * dz);
        final double standoff = Math.max(0.8, ctx.params.getDouble(AbilityParamKeys.LAND_RADIUS, 2.0));
        final double maxRange = Math.max(1.0, ctx.params.getDouble(AbilityParamKeys.MAX_RANGE, 10.0));

        if (len <= standoff + 0.4) {
            return false;
        }

        final double dirX;
        final double dirZ;
        if (len < 0.05) {
            dirX = 0.0;
            dirZ = 1.0;
        } else {
            dirX = dx / len;
            dirZ = dz / len;
        }

        final double travel = Math.min(maxRange, len - standoff);
        if (travel < 0.5) {
            return false;
        }

        active.sx = sx;
        active.sy = sy;
        active.sz = sz;
        active.ex = sx + dirX * travel;
        active.ez = sz + dirZ * travel;
        active.ey = AbilityCombatHelper.findGroundY(ctx.world, active.ex, active.ez, sy);
        active.yaw = AbilityCombatHelper.computeYaw(dirX, dirZ);

        final int dashTicks = Math.max(3, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 8));
        active.markers.add(new double[]{dashTicks});

        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, TelegraphAPI.DEFAULT_COLOR);
        final String lineId = TelegraphAPI.line(
                ctx.npc, active.sx, active.sy, active.sz, active.yaw,
                travel, 1.1, dashTicks, color);
        if (lineId != null && !lineId.isEmpty()) {
            active.telegraphIds.add(lineId);
        }

        active.phase = PHASE_DASH;
        active.ticksLeft = dashTicks;
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.ravager.roar", 0.55F, 1.35F);
        return true;
    }

    private TickResult tickDash(final ActiveAbility active, final AbilityContext ctx) {
        final int total = !active.markers.isEmpty()
                ? Math.max(1, (int) active.markers.get(0)[0])
                : Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 8));
        final double progress = 1.0 - (active.ticksLeft - 1) / (double) total;
        final double[] point = AbilityCombatHelper.resolveDashPointAtProgress(
                ctx, active.sx, active.sy, active.sz, active.ex, active.ez, progress);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setPosition(point[0], point[1], point[2]);
        ctx.npc.setRotation(active.yaw);
        AbilityVfx.spawnShadowTrail(ctx.world, point[0], point[1], point[2]);

        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }

        final double[] end = AbilityCombatHelper.resolveDashPointAtProgress(
                ctx, active.sx, active.sy, active.sz, active.ex, active.ez, 1.0);
        ctx.npc.setPosition(end[0], end[1], end[2]);
        AbilityTelegraph.clear(active, ctx);
        AbilityVfx.spawnLandBurst(ctx.world, end[0], end[1], end[2], false);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(end[0], end[1], end[2]),
                "minecraft:entity.player.attack.strong",
                0.75F,
                0.95F);

        if (!beginSlashCharge(active, ctx)) {
            return TickResult.FINISHED;
        }
        return TickResult.CONTINUE;
    }

    private boolean beginSlashCharge(final ActiveAbility active, final AbilityContext ctx) {
        active.markers.clear();
        active.telegraphIds.clear();

        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY();
        active.sz = ctx.npc.getZ();

        final double distance = ctx.params.getDouble(AbilityParamKeys.DISTANCE, 4.5);
        final double nearHalfWidth = ctx.params.getDouble(AbilityParamKeys.RADIUS, 1.35);
        final double halfAngle = ctx.params.getDouble(AbilityParamKeys.CONE_HALF_ANGLE, 38.0);

        final double aimX;
        final double aimZ;
        if (ctx.target != null && ctx.target.isAlive()) {
            aimX = ctx.target.getX();
            aimZ = ctx.target.getZ();
        } else {
            final double rad = (active.yaw + 90.0) * 0.0174532925;
            aimX = active.sx + Math.cos(rad);
            aimZ = active.sz + Math.sin(rad);
        }

        final double dx = aimX - active.sx;
        final double dz = aimZ - active.sz;
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
        AbilityCombatHelper.holdInPlace(ctx.npc, active.sx, active.sy, active.sz);
        ctx.npc.setRotation(active.yaw);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.vex.ambient", 0.95F, 0.55F);
        return true;
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
