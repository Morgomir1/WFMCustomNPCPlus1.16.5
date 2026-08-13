package noppes.npcs.abilities;

import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntityLiving;
import noppes.npcs.telegraph.TelegraphAPI;

/**
 * Auto-spawns a charge telegraph from common ability params.
 */
public final class AbilityTelegraph {
    private AbilityTelegraph() {
    }

    public static String spawnFromCharge(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx == null || ctx.npc == null || ctx.params == null) {
            return "";
        }
        if (ctx.params.getInt(AbilityParamKeys.TELEGRAPH, 1) == 0) {
            return "";
        }
        final int chargeTicks = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 0));
        if (chargeTicks <= 0) {
            return "";
        }

        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, TelegraphAPI.DEFAULT_COLOR);
        final ICustomNpc npc = ctx.npc;
        final double nx = npc.getX();
        final double ny = npc.getY();
        final double nz = npc.getZ();
        final float yaw = resolveYaw(active, ctx);

        final double coneHalf = ctx.params.getDouble(AbilityParamKeys.CONE_HALF_ANGLE, 0);
        if (coneHalf > 0.5) {
            final double radius = firstPositive(
                    ctx.params.getDouble(AbilityParamKeys.RADIUS, 0),
                    ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 0),
                    4.0);
            return TelegraphAPI.cone(npc, nx, ny, nz, yaw, radius, coneHalf, chargeTicks, color);
        }

        final double landRadius = ctx.params.getDouble(AbilityParamKeys.LAND_RADIUS, 0);
        if (landRadius > 0.05) {
            final double tx = active.ex != 0 || active.ez != 0 ? active.ex : (ctx.target != null ? ctx.target.getX() : nx);
            final double tz = active.ex != 0 || active.ez != 0 ? active.ez : (ctx.target != null ? ctx.target.getZ() : nz);
            final double ty = active.ey != 0 ? active.ey : (ctx.target != null ? ctx.target.getY() : ny);
            // Prefer landing point if already set in onStart
            if (active.ex != 0 || active.ez != 0 || active.ey != 0) {
                return TelegraphAPI.circle(npc, active.ex, active.ey, active.ez, landRadius, chargeTicks, color);
            }
            return TelegraphAPI.circle(npc, tx, ty, tz, landRadius, chargeTicks, color);
        }

        final double distance = ctx.params.getDouble(AbilityParamKeys.DISTANCE, 0);
        final double radius = firstPositive(
                ctx.params.getDouble(AbilityParamKeys.RADIUS, 0),
                ctx.params.getDouble(AbilityParamKeys.AURA_RADIUS, 0),
                0);
        // Strip / artillery: both length and width → line corridor (+ impact circle if locked)
        if (distance > 0.5 && radius > 0.05) {
            final String lineId = TelegraphAPI.line(
                    npc, nx, ny, nz, yaw, distance, radius, chargeTicks, color);
            if (active.ex != 0 || active.ez != 0 || active.ey != 0) {
                TelegraphAPI.circle(npc, active.ex, active.ey, active.ez, radius, chargeTicks, color);
            }
            return lineId;
        }
        // Dash: distance without AoE radius → line using hitRadius as width
        if (distance > 0.5) {
            final double width = firstPositive(
                    ctx.params.getDouble(AbilityParamKeys.HIT_RADIUS, 0),
                    1.2);
            return TelegraphAPI.line(npc, nx, ny, nz, yaw, distance, width, chargeTicks, color);
        }

        if (radius > 0.05) {
            final double innerRadius = ctx.params.getDouble(AbilityParamKeys.INNER_RADIUS, 0);
            if (innerRadius > 0.05 && radius > innerRadius) {
                return TelegraphAPI.ring(npc, nx, ny, nz, radius, innerRadius, chargeTicks, color);
            }
            // Prefer impact point fixed in onStart (e.g. slam forward)
            if (active.ex != 0 || active.ez != 0 || active.ey != 0) {
                return TelegraphAPI.circle(npc, active.ex, active.ey, active.ez, radius, chargeTicks, color);
            }
            final double forward = ctx.params.getDouble(AbilityParamKeys.TELEGRAPH_FORWARD, 0);
            double cx = nx;
            double cz = nz;
            double cy = ny;
            if (Math.abs(forward) > 0.01) {
                final double rad = (yaw + 90.0) * 0.0174532925;
                cx += Math.cos(rad) * forward;
                cz += Math.sin(rad) * forward;
            }
            return TelegraphAPI.circle(npc, cx, cy, cz, radius, chargeTicks, color);
        }

        return "";
    }

    public static void clear(final ActiveAbility active, final AbilityContext ctx) {
        if (active == null) {
            return;
        }
        if (active.telegraphId != null && !active.telegraphId.isEmpty()) {
            if (ctx != null && ctx.npc != null) {
                TelegraphAPI.removeNear(ctx.npc, active.telegraphId);
            } else {
                TelegraphAPI.remove(active.telegraphId);
            }
            active.telegraphId = null;
        }
        if (!active.telegraphIds.isEmpty()) {
            for (final String id : active.telegraphIds) {
                if (id == null || id.isEmpty()) {
                    continue;
                }
                if (ctx != null && ctx.npc != null) {
                    TelegraphAPI.removeNear(ctx.npc, id);
                } else {
                    TelegraphAPI.remove(id);
                }
            }
            active.telegraphIds.clear();
        }
    }

    private static float resolveYaw(final ActiveAbility active, final AbilityContext ctx) {
        // Prefer cast yaw from onStart. Do not use `active.yaw != 0` — 0 is valid (south).
        if (active != null) {
            return active.yaw;
        }
        final IEntityLiving target = ctx.target;
        if (target != null) {
            final double dx = target.getX() - ctx.npc.getX();
            final double dz = target.getZ() - ctx.npc.getZ();
            return AbilityCombatHelper.computeYaw(dx, dz);
        }
        try {
            return ctx.npc.getMCEntity().yRot;
        } catch (final Exception e) {
            return 0;
        }
    }

    private static double firstPositive(final double... values) {
        for (final double v : values) {
            if (v > 0.05) {
                return v;
            }
        }
        return 0;
    }
}
