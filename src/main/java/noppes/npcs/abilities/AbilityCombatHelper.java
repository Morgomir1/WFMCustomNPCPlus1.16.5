package noppes.npcs.abilities;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.block.IBlock;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntityLiving;
import noppes.npcs.api.entity.IMob;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.Random;

public final class AbilityCombatHelper {
    private static final Random RANDOM = new Random();

    private AbilityCombatHelper() {
    }

    public static void stopNavigation(final IMob npc) {
        try {
            npc.clearNavigation();
        } catch (final Exception ignored) {
        }
    }

    public static float computeYaw(final double dx, final double dz) {
        return (float) (Math.atan2(dz, dx) * 57.2957795 - 90.0);
    }

    public static double flatDistance(final double x1, final double z1, final double x2, final double z2) {
        final double dx = x1 - x2;
        final double dz = z1 - z2;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static double findGroundY(
            final noppes.npcs.api.IWorld world,
            final double x,
            final double z,
            final double startY) {
        final int bx = (int) Math.floor(x);
        final int bz = (int) Math.floor(z);
        for (int by = (int) Math.floor(startY) + 3; by >= (int) Math.floor(startY) - 8; by--) {
            final IBlock block = world.getBlock(bx, by, bz);
            final IBlock above1 = world.getBlock(bx, by + 1, bz);
            final IBlock above2 = world.getBlock(bx, by + 2, bz);
            if (isSolidBlock(block) && !isSolidBlock(above1) && !isSolidBlock(above2)) {
                return by + 1.0;
            }
        }
        return startY;
    }

    private static boolean isSolidBlock(final IBlock block) {
        if (block == null) {
            return false;
        }
        final String name = block.getName();
        return !"minecraft:air".equals(name)
                && !"minecraft:cave_air".equals(name)
                && !"minecraft:void_air".equals(name);
    }

    public static boolean computeDashEndPoints(final ActiveAbility active, final AbilityContext ctx) {
        final double sx = ctx.npc.getX();
        final double sy = ctx.npc.getY();
        final double sz = ctx.npc.getZ();
        final double tx = ctx.target.getX();
        final double tz = ctx.target.getZ();

        final double dx = tx - sx;
        final double dz = tz - sz;
        final double len = Math.sqrt(dx * dx + dz * dz);

        final double dirX;
        final double dirZ;
        if (len < 0.05) {
            final float yaw = getNpcYaw(ctx.npc);
            final double rad = (yaw + 90.0) * 0.0174532925;
            dirX = Math.cos(rad);
            dirZ = Math.sin(rad);
            active.yaw = yaw;
        } else {
            dirX = dx / len;
            dirZ = dz / len;
            active.yaw = computeYaw(dx, dz);
        }

        active.sx = sx;
        active.sy = sy;
        active.sz = sz;

        double distance = ctx.params.getDouble(AbilityParamKeys.DISTANCE, 16.0);
        if (distance < 0.05) {
            distance = 16.0;
        }
        active.ex = sx + dirX * distance;
        active.ez = sz + dirZ * distance;
        active.ey = findGroundY(ctx.world, active.ex, active.ez, sy);
        return true;
    }

    public static boolean computeEndPoints(
            final ActiveAbility active,
            final AbilityContext ctx,
            final boolean dashStyle) {
        if (dashStyle) {
            return computeDashEndPoints(active, ctx);
        }

        final double sx = ctx.npc.getX();
        final double sy = ctx.npc.getY();
        final double sz = ctx.npc.getZ();
        final double tx = ctx.target.getX();
        final double ty = ctx.target.getY();
        final double tz = ctx.target.getZ();

        final double dx = tx - sx;
        final double dz = tz - sz;
        final double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.05) {
            return false;
        }

        active.yaw = computeYaw(dx, dz);
        active.sx = sx;
        active.sy = sy;
        active.sz = sz;
        active.ex = tx;
        active.ez = tz;
        active.ey = findGroundY(ctx.world, active.ex, active.ez, ty);
        return true;
    }

    private static float getNpcYaw(final ICustomNpc npc) {
        try {
            if (npc instanceof IEntityLiving) {
                final Entity entity = ((IEntityLiving) npc).getMCEntity();
                return entity.yRot;
            }
        } catch (final Exception ignored) {
        }
        return 0.0f;
    }

    public static void damageNearby(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double x,
            final double y,
            final double z,
            final double radius,
            final double damage,
            final double dirX,
            final double dirZ,
            final double knockback,
            final double lift,
            final boolean useFixedDir) {
        final int range = (int) Math.ceil(radius + 0.5);
        final IEntity[] list = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(x, y, z),
                range,
                -1);

        for (final IEntity ent : list) {
            if (!isHostileToBoss(ctx.npc, ent)) {
                continue;
            }
            final String id = String.valueOf(ent.getUUID());
            if (active.hitUuids.contains(id)) {
                continue;
            }
            if (flatDistance(ent.getX(), ent.getZ(), x, z) > radius) {
                continue;
            }
            ent.damage((float) damage);
            if (ent instanceof IEntityLiving) {
                final IEntityLiving living = (IEntityLiving) ent;
                if (useFixedDir) {
                    living.setMotionX(dirX * knockback);
                    living.setMotionY(lift);
                    living.setMotionZ(dirZ * knockback);
                } else {
                    applyRadialKnockback(living, x, z, knockback, lift);
                }
            }
            AbilityVfx.spawnHitParticle(ctx.world, ent);
            active.hitUuids.add(id);
        }
    }

    private static void applyRadialKnockback(
            final IEntityLiving entity,
            final double fromX,
            final double fromZ,
            final double strength,
            final double lift) {
        double dx = entity.getX() - fromX;
        double dz = entity.getZ() - fromZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.05) {
            dx = 1.0;
            dz = 0.0;
            len = 1.0;
        }
        entity.setMotionX((dx / len) * strength);
        entity.setMotionY(lift);
        entity.setMotionZ((dz / len) * strength);
    }

    public static boolean isHostileToBoss(final IEntityLiving npc, final IEntity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        if (String.valueOf(entity.getUUID()).equals(String.valueOf(npc.getUUID()))) {
            return false;
        }
        try {
            final Entity mcNpc = npc.getMCEntity();
            final Entity mcEnt = entity.getMCEntity();
            if (mcNpc instanceof LivingEntity && mcEnt != null) {
                if (((LivingEntity) mcNpc).isAlliedTo(mcEnt)) {
                    return false;
                }
            }
            if (mcNpc instanceof EntityNPCInterface && mcEnt instanceof LivingEntity) {
                if (((EntityNPCInterface) mcNpc).isAlliedTo((LivingEntity) mcEnt)) {
                    return false;
                }
            }
        } catch (final Exception ignored) {
        }
        return true;
    }

    public static Random random() {
        return RANDOM;
    }
}
