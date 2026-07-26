package noppes.npcs.abilities;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.VoxelShape;
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
    /** Dash may step onto / phase through obstacles up to this height. */
    private static final double DASH_MAX_STEP = 1.05;

    private AbilityCombatHelper() {
    }

    public static void stopNavigation(final IMob npc) {
        try {
            npc.clearNavigation();
        } catch (final Exception ignored) {
        }
    }

    public static void zeroHorizontalMotion(final IEntityLiving npc) {
        try {
            npc.setMotionX(0.0);
            npc.setMotionZ(0.0);
        } catch (final Exception ignored) {
        }
    }

    /**
     * Stop pathing and pin XZ (keeps current Y). Call every charge tick.
     */
    public static void holdInPlace(final ICustomNpc npc, final double x, final double y, final double z) {
        stopNavigation(npc);
        zeroHorizontalMotion(npc);
        try {
            npc.setPosition(x, y, z);
        } catch (final Exception ignored) {
        }
    }

    /**
     * Disable chase AI for the cast duration. Pair with {@link #unfreezeAi}.
     */
    public static void freezeAiForCast(final ActiveAbility active, final ICustomNpc npc) {
        if (active == null || npc == null || active.aiFrozen) {
            return;
        }
        try {
            final noppes.npcs.api.entity.data.INPCAi ai = npc.getAi();
            active.savedWalkingSpeed = ai.getWalkingSpeed();
            active.savedRetaliateType = ai.getRetaliateType();
            ai.setWalkingSpeed(0);
            ai.setRetaliateType(3); // OnAttack: Nothing
            active.aiFrozen = true;
        } catch (final Exception ignored) {
        }
        stopNavigation(npc);
        zeroHorizontalMotion(npc);
    }

    public static void unfreezeAi(final ActiveAbility active, final ICustomNpc npc) {
        if (active == null || npc == null || !active.aiFrozen) {
            return;
        }
        try {
            final noppes.npcs.api.entity.data.INPCAi ai = npc.getAi();
            if (active.savedWalkingSpeed >= 0) {
                ai.setWalkingSpeed(active.savedWalkingSpeed);
            }
            if (active.savedRetaliateType >= 0) {
                ai.setRetaliateType(active.savedRetaliateType);
            }
        } catch (final Exception ignored) {
        }
        active.aiFrozen = false;
        active.savedWalkingSpeed = -1;
        active.savedRetaliateType = -1;
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
        if (world instanceof WorldWrapper) {
            final World mcWorld = ((WorldWrapper) world).getMCWorld();
            if (mcWorld != null) {
                return findGroundYMc(mcWorld, x, z, startY);
            }
        }
        final int bx = MathHelper.floor(x);
        final int bz = MathHelper.floor(z);
        for (int by = MathHelper.floor(startY) + 3; by >= MathHelper.floor(startY) - 8; by--) {
            final IBlock block = world.getBlock(bx, by, bz);
            final IBlock above1 = world.getBlock(bx, by + 1, bz);
            final IBlock above2 = world.getBlock(bx, by + 2, bz);
            if (isSolidBlock(block) && !isSolidBlock(above1) && !isSolidBlock(above2)) {
                return by + 1.0;
            }
        }
        return startY;
    }

    /**
     * Solid ground only: {@code material.isSolid()} + non-empty collision shape.
     * Ignores grass/flowers/moss/fog and other walk-through blocks.
     */
    private static double findGroundYMc(
            final World world,
            final double x,
            final double z,
            final double startY) {
        final int bx = MathHelper.floor(x);
        final int bz = MathHelper.floor(z);
        final int from = MathHelper.floor(startY) + 3;
        final int minY = Math.max(0, MathHelper.floor(startY) - 8);
        final BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int by = from; by >= minY; by--) {
            pos.set(bx, by, bz);
            final BlockState state = world.getBlockState(pos);
            if (!isCollisionSolid(state, world, pos)) {
                continue;
            }
            final VoxelShape shape = state.getCollisionShape(world, pos);
            return by + shape.max(Direction.Axis.Y);
        }
        return startY;
    }

    private static boolean isCollisionSolid(
            final BlockState state,
            final World world,
            final BlockPos pos) {
        return state.getMaterial().isSolid() && !state.getCollisionShape(world, pos).isEmpty();
    }

    private static boolean isSolidBlock(final IBlock block) {
        if (block == null) {
            return false;
        }
        final String name = block.getName();
        if (name == null) {
            return false;
        }
        if ("minecraft:air".equals(name)
                || "minecraft:cave_air".equals(name)
                || "minecraft:void_air".equals(name)) {
            return false;
        }
        // Fallback when MC world is unavailable: treat common passables as non-solid.
        return !isLikelyPassableBlockName(name);
    }

    private static boolean isLikelyPassableBlockName(final String name) {
        // Real floors — never treat as passable in the string fallback.
        if (name.contains("grass_block")
                || name.contains("grass_path")
                || name.contains("snow_block")
                || name.contains("mycelium")
                || name.contains("podzol")) {
            return false;
        }
        return name.contains("tall_grass")
                || name.endsWith(":grass")
                || name.contains("fern")
                || name.contains("flower")
                || name.contains("tulip")
                || name.contains("orchid")
                || name.contains("daisy")
                || name.contains("lilac")
                || name.contains("rose")
                || name.contains("peony")
                || name.contains("sunflower")
                || name.contains("seagrass")
                || name.contains("kelp")
                || name.contains("vine")
                || name.contains("moss_carpet")
                || name.contains("hanging_moss")
                || name.contains("fog")
                || name.contains("mist")
                || name.contains("web")
                || name.contains("torch")
                || name.contains("sapling")
                || name.contains("mushroom")
                || name.contains("carpet")
                || name.contains("pressure_plate")
                || name.contains("button")
                || name.contains("rail")
                || name.contains("sign")
                || name.contains("banner")
                || name.equals("minecraft:snow")
                || name.endsWith(":snow")
                || name.contains("fire")
                || name.contains("sugar_cane")
                || name.contains("dead_bush")
                || name.contains("wheat")
                || name.contains("carrot")
                || name.contains("potato")
                || name.contains("beetroot");
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
        final Double cyOpt = resolveDashStandY(ctx, cx, cz, sy);
        if (cyOpt != null) {
            return new double[]{cx, cyOpt, cz};
        }

        double low = 0.0;
        double high = clampedProgress;
        double cy = sy;
        for (int i = 0; i < 8; i++) {
            final double mid = (low + high) * 0.5;
            final double mx = sx + (ex - sx) * mid;
            final double mz = sz + (ez - sz) * mid;
            final Double myOpt = resolveDashStandY(ctx, mx, mz, sy);
            if (myOpt != null) {
                low = mid;
                cx = mx;
                cy = myOpt;
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
        final Double ey = resolveDashStandY(ctx, active.ex, active.ez, active.sy);
        active.ey = ey != null ? ey : findGroundY(ctx.world, active.ex, active.ez, active.sy);
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
            if (resolveDashStandY(ctx, x, z, sy) == null) {
                break;
            }
            safeDistance = traveled;
            traveled += DASH_CLIP_STEP;
        }
        return Math.max(0.0, safeDistance - DASH_WALL_MARGIN);
    }

    /**
     * Y where the NPC can stand during a dash: real ground, 1-block step-up,
     * or phase-through of short / passable obstacles at the start height.
     */
    private static Double resolveDashStandY(
            final AbilityContext ctx,
            final double x,
            final double z,
            final double startY) {
        final double groundY = findGroundY(ctx.world, x, z, startY);
        if (groundY - startY > DASH_MAX_STEP) {
            return null;
        }
        if (canNpcOccupy(ctx, x, groundY, z)) {
            return groundY;
        }

        final double stepY = startY + 1.0;
        if (canNpcOccupy(ctx, x, stepY, z)) {
            return stepY;
        }

        if (canNpcOccupy(ctx, x, startY, z)) {
            return startY;
        }

        // Only a ≤1-high obstacle intersects the hitbox — dash through at startY.
        if (canNpcOccupyWithYOffset(ctx, x, startY, z, 1.0)) {
            return startY;
        }
        if (groundY - startY <= DASH_MAX_STEP && canNpcOccupyWithYOffset(ctx, x, groundY, z, 1.0)) {
            return startY;
        }
        return null;
    }

    private static boolean canNpcOccupy(
            final AbilityContext ctx,
            final double x,
            final double y,
            final double z) {
        return canNpcOccupyWithYOffset(ctx, x, y, z, 0.0);
    }

    private static boolean canNpcOccupyWithYOffset(
            final AbilityContext ctx,
            final double x,
            final double y,
            final double z,
            final double yOffset) {
        try {
            final Entity entity = ctx.npc.getMCEntity();
            if (entity != null && ctx.world instanceof WorldWrapper) {
                final World world = ((WorldWrapper) ctx.world).getMCWorld();
                final AxisAlignedBB moved = entity.getBoundingBox().move(
                        x - entity.getX(),
                        y + yOffset - entity.getY(),
                        z - entity.getZ());
                return world.noCollision(entity, moved);
            }
        } catch (final Exception ignored) {
        }
        return yOffset == 0.0 && canStandAtBlocks(ctx.world, x, y, z);
    }

    private static boolean canStandAtBlocks(
            final noppes.npcs.api.IWorld world,
            final double x,
            final double y,
            final double z) {
        final int minX = MathHelper.floor(x - 0.3);
        final int maxX = MathHelper.floor(x + 0.3);
        final int minZ = MathHelper.floor(z - 0.3);
        final int maxZ = MathHelper.floor(z + 0.3);
        final int footY = MathHelper.floor(y);
        final int floorX = MathHelper.floor(x);
        final int floorZ = MathHelper.floor(z);
        if (!isCollidingBlock(world, floorX, footY - 1, floorZ)) {
            return false;
        }
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                for (int by = footY; by <= footY + 1; by++) {
                    if (isCollidingBlock(world, bx, by, bz)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean isCollidingBlock(
            final noppes.npcs.api.IWorld world,
            final int x,
            final int y,
            final int z) {
        if (world instanceof WorldWrapper) {
            final World mcWorld = ((WorldWrapper) world).getMCWorld();
            if (mcWorld != null) {
                final BlockPos pos = new BlockPos(x, y, z);
                return isCollisionSolid(mcWorld.getBlockState(pos), mcWorld, pos);
            }
        }
        return isSolidBlock(world.getBlock(x, y, z));
    }

    public static boolean isUndead(final IEntity entity) {
        return false;
    }

    public static boolean isInFrontCone(
            final ICustomNpc npc,
            final IEntity entity,
            final double halfAngleDeg) {
        return isInFrontCone(npc.getX(), npc.getZ(), getNpcYaw(npc), entity, halfAngleDeg);
    }

    /**
     * Cone check with fixed apex/yaw — matches a static telegraph cone.
     */
    public static boolean isInFrontCone(
            final double originX,
            final double originZ,
            final float yaw,
            final IEntity entity,
            final double halfAngleDeg) {
        final double ex = entity.getX();
        final double ez = entity.getZ();
        final double rad = (yaw + 90.0) * 0.0174532925;
        final double fwdX = Math.cos(rad);
        final double fwdZ = Math.sin(rad);
        double toX = ex - originX;
        double toZ = ez - originZ;
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

        // Clamp landing distance so a far/teleported target cannot pull the NPC across the map.
        final double maxRange = ctx.params.getDouble(AbilityParamKeys.MAX_RANGE, -1.0);
        if (maxRange > 0.0 && len > maxRange) {
            final double scale = maxRange / len;
            active.ex = sx + dx * scale;
            active.ez = sz + dz * scale;
        } else {
            active.ex = tx;
            active.ez = tz;
        }
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
