package noppes.npcs.abilities;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.block.IBlock;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntityLiving;
import noppes.npcs.api.entity.IMob;
import noppes.npcs.api.wrapper.WorldWrapper;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.Random;

public final class AbilityCombatHelper {
    private static final Random RANDOM = new Random();
    private static final double DASH_CLIP_STEP = 0.2;
    private static final double DASH_WALL_MARGIN = 0.35;
    private static final double MIN_DASH_DISTANCE = 0.5;

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
        return applyClippedDashEnd(active, ctx, dirX, dirZ, distance);
    }

    public static boolean computeRetreatEndPoints(final ActiveAbility active, final AbilityContext ctx) {
        final double sx = ctx.npc.getX();
        final double sy = ctx.npc.getY();
        final double sz = ctx.npc.getZ();
        final double tx = ctx.target.getX();
        final double tz = ctx.target.getZ();

        final double dx = sx - tx;
        final double dz = sz - tz;
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

        double distance = ctx.params.getDouble(AbilityParamKeys.DISTANCE, 6.0);
        if (distance < 0.05) {
            distance = 6.0;
        }
        return applyClippedDashEnd(active, ctx, dirX, dirZ, distance);
    }

    public static double[] resolveDashPointAtProgress(
            final AbilityContext ctx,
            final double sx,
            final double sy,
            final double sz,
            final double ex,
            final double ez,
            final double progress) {
        final double clampedProgress = Math.max(0.0, Math.min(1.0, progress));
        double cx = sx + (ex - sx) * clampedProgress;
        double cz = sz + (ez - sz) * clampedProgress;
        double cy = findGroundY(ctx.world, cx, cz, sy);
        if (canNpcOccupy(ctx, cx, cy, cz)) {
            return new double[]{cx, cy, cz};
        }

        double low = 0.0;
        double high = clampedProgress;
        for (int i = 0; i < 8; i++) {
            final double mid = (low + high) * 0.5;
            final double mx = sx + (ex - sx) * mid;
            final double mz = sz + (ez - sz) * mid;
            final double my = findGroundY(ctx.world, mx, mz, sy);
            if (canNpcOccupy(ctx, mx, my, mz)) {
                low = mid;
                cx = mx;
                cy = my;
                cz = mz;
            } else {
                high = mid;
            }
        }
        return new double[]{cx, cy, cz};
    }

    private static boolean applyClippedDashEnd(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double dirX,
            final double dirZ,
            final double distance) {
        final double clipped = clipDashDistance(ctx, active.sx, active.sy, active.sz, dirX, dirZ, distance);
        if (clipped < MIN_DASH_DISTANCE) {
            return false;
        }
        active.ex = active.sx + dirX * clipped;
        active.ez = active.sz + dirZ * clipped;
        active.ey = findGroundY(ctx.world, active.ex, active.ez, active.sy);
        return true;
    }

    private static double clipDashDistance(
            final AbilityContext ctx,
            final double sx,
            final double sy,
            final double sz,
            final double dirX,
            final double dirZ,
            final double maxDistance) {
        double safeDistance = 0.0;
        double traveled = DASH_CLIP_STEP;
        while (traveled <= maxDistance) {
            final double x = sx + dirX * traveled;
            final double z = sz + dirZ * traveled;
            final double y = findGroundY(ctx.world, x, z, sy);
            if (!canNpcOccupy(ctx, x, y, z)) {
                break;
            }
            safeDistance = traveled;
            traveled += DASH_CLIP_STEP;
        }
        return Math.max(0.0, safeDistance - DASH_WALL_MARGIN);
    }

    private static boolean canNpcOccupy(
            final AbilityContext ctx,
            final double x,
            final double y,
            final double z) {
        try {
            final Entity entity = ctx.npc.getMCEntity();
            if (entity != null && ctx.world instanceof WorldWrapper) {
                final World world = ((WorldWrapper) ctx.world).getMCWorld();
                final AxisAlignedBB moved = entity.getBoundingBox().move(
                        x - entity.getX(),
                        y - entity.getY(),
                        z - entity.getZ());
                return world.noCollision(entity, moved);
            }
        } catch (final Exception ignored) {
        }
        return canStandAtBlocks(ctx.world, x, y, z);
    }

    private static boolean canStandAtBlocks(
            final noppes.npcs.api.IWorld world,
            final double x,
            final double y,
            final double z) {
        final int minX = (int) Math.floor(x - 0.3);
        final int maxX = (int) Math.floor(x + 0.3);
        final int minZ = (int) Math.floor(z - 0.3);
        final int maxZ = (int) Math.floor(z + 0.3);
        final int footY = (int) Math.floor(y);
        final IBlock floor = world.getBlock((int) Math.floor(x), footY - 1, (int) Math.floor(z));
        if (!isSolidBlock(floor)) {
            return false;
        }
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                for (int by = footY; by <= footY + 1; by++) {
                    if (isSolidBlock(world.getBlock(bx, by, bz))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static boolean isUndead(final IEntity entity) {
        return false;
    }

    public static boolean isInFrontCone(
            final ICustomNpc npc,
            final IEntity entity,
            final double halfAngleDeg) {
        final double nx = npc.getX();
        final double nz = npc.getZ();
        final double ex = entity.getX();
        final double ez = entity.getZ();
        final float yaw = getNpcYaw(npc);
        final double rad = (yaw + 90.0) * 0.0174532925;
        final double fwdX = Math.cos(rad);
        final double fwdZ = Math.sin(rad);
        double toX = ex - nx;
        double toZ = ez - nz;
        final double len = Math.sqrt(toX * toX + toZ * toZ);
        if (len < 0.05) {
            return true;
        }
        toX /= len;
        toZ /= len;
        final double dot = fwdX * toX + fwdZ * toZ;
        final double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
        return angle <= halfAngleDeg;
    }

    public static void applyPotionNearby(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double x,
            final double y,
            final double z,
            final double radius,
            final AbilityEffectType effectType,
            final int duration,
            final int amplifier) {
        final Effect effect = effectType.toMcEffect();
        final int range = (int) Math.ceil(radius + 0.5);
        final IEntity[] list = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(x, y, z),
                range,
                -1);

        for (final IEntity ent : list) {
            if (!isHostileToBoss(ctx.npc, ent)) {
                continue;
            }
            if (flatDistance(ent.getX(), ent.getZ(), x, z) > radius) {
                continue;
            }
            applyEffect(ent, effect, duration, amplifier);
        }
    }

    public static void applyEffect(
            final IEntity entity,
            final Effect effect,
            final int duration,
            final int amplifier) {
        try {
            final Entity mc = entity.getMCEntity();
            if (mc instanceof LivingEntity) {
                ((LivingEntity) mc).addEffect(new EffectInstance(effect, duration, amplifier));
            }
        } catch (final Exception ignored) {
        }
    }

    public static void damageWithUndeadBonus(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double x,
            final double y,
            final double z,
            final double radius,
            final double damage,
            final double undeadBonus,
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
            final float finalDamage = isUndead(ent)
                    ? (float) (damage * undeadBonus)
                    : (float) damage;
            ent.damage(finalDamage);
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

    public static void damageInConeWithUndeadBonus(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double x,
            final double y,
            final double z,
            final double radius,
            final double halfAngleDeg,
            final double damage,
            final double undeadBonus,
            final double knockback,
            final double lift) {
        final int range = (int) Math.ceil(radius + 0.5);
        final IEntity[] list = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(x, y, z),
                range,
                -1);

        for (final IEntity ent : list) {
            if (!isHostileToBoss(ctx.npc, ent)) {
                continue;
            }
            if (!isInFrontCone(ctx.npc, ent, halfAngleDeg)) {
                continue;
            }
            final String id = String.valueOf(ent.getUUID());
            if (active.hitUuids.contains(id)) {
                continue;
            }
            if (flatDistance(ent.getX(), ent.getZ(), x, z) > radius) {
                continue;
            }
            final float finalDamage = isUndead(ent)
                    ? (float) (damage * undeadBonus)
                    : (float) damage;
            ent.damage(finalDamage);
            if (ent instanceof IEntityLiving) {
                applyRadialKnockback((IEntityLiving) ent, x, z, knockback, lift);
            }
            AbilityVfx.spawnHitParticle(ctx.world, ent);
            active.hitUuids.add(id);
        }
    }

    public static void applyPotionInCone(
            final AbilityContext ctx,
            final double x,
            final double y,
            final double z,
            final double radius,
            final double halfAngleDeg,
            final AbilityEffectType effectType,
            final int duration,
            final int amplifier) {
        final int range = (int) Math.ceil(radius + 0.5);
        final IEntity[] list = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(x, y, z),
                range,
                -1);

        for (final IEntity ent : list) {
            if (!isHostileToBoss(ctx.npc, ent)) {
                continue;
            }
            if (!isInFrontCone(ctx.npc, ent, halfAngleDeg)) {
                continue;
            }
            if (flatDistance(ent.getX(), ent.getZ(), x, z) > radius) {
                continue;
            }
            applyEffect(ent, effectType.toMcEffect(), duration, amplifier);
        }
    }

    public static double distanceToTarget(final AbilityContext ctx) {
        if (ctx.target == null) {
            return Double.MAX_VALUE;
        }
        return flatDistance(
                ctx.npc.getX(),
                ctx.npc.getZ(),
                ctx.target.getX(),
                ctx.target.getZ());
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
