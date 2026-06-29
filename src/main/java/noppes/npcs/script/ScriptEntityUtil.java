package noppes.npcs.script;

import net.minecraft.util.Hand;
import noppes.npcs.abilities.AbilityCombatHelper;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.IEntityLiving;

public final class ScriptEntityUtil {
    private static final int ANIM_SWING_MAIN = 0;

    private ScriptEntityUtil() {
    }

    public static double distance3D(
            final double x1,
            final double y1,
            final double z1,
            final double x2,
            final double y2,
            final double z2) {
        final double dx = x2 - x1;
        final double dy = y2 - y1;
        final double dz = z2 - z1;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static double horizontalDistance(
            final double x1,
            final double z1,
            final double x2,
            final double z2) {
        return AbilityCombatHelper.flatDistance(x1, z1, x2, z2);
    }

    public static void faceTarget(final IEntity entity, final double tx, final double tz) {
        final double dx = tx - entity.getX();
        final double dz = tz - entity.getZ();
        if (Math.abs(dx) < 0.001 && Math.abs(dz) < 0.001) {
            return;
        }
        final float yaw = (float) (Math.atan2(-dx, dz) * 180.0 / Math.PI);
        entity.setRotation(yaw);
    }

    public static void swingMainHand(final IEntityLiving entity) {
        try {
            entity.playAnimation(ANIM_SWING_MAIN);
            return;
        } catch (final Exception ignored) {
        }

        try {
            final Object mc = entity.getMCEntity();
            if (mc instanceof net.minecraft.entity.LivingEntity) {
                ((net.minecraft.entity.LivingEntity) mc).swing(Hand.MAIN_HAND, true);
            }
        } catch (final Exception ignored) {
        }
    }

    public static boolean isStandingOver(
            final IEntity entity,
            final double cx,
            final double cy,
            final double cz,
            final double xzMax,
            final double yMin,
            final double yMax) {
        final double dx = entity.getX() - cx;
        final double dz = entity.getZ() - cz;
        if (Math.abs(dx) > xzMax || Math.abs(dz) > xzMax) {
            return false;
        }
        final double dy = entity.getY() - cy;
        return dy >= yMin && dy <= yMax;
    }

    public static boolean isStandingOver(
            final ICustomNpc npc,
            final double cx,
            final double cy,
            final double cz,
            final double xzMax,
            final double yMin,
            final double yMax) {
        return isStandingOver((IEntity) npc, cx, cy, cz, xzMax, yMin, yMax);
    }
}
