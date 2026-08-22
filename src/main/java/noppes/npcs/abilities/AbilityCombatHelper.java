package noppes.npcs.abilities;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.World;
import net.minecraftforge.registries.ForgeRegistries;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.block.IBlock;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntityLiving;
import noppes.npcs.api.entity.IMob;
import noppes.npcs.api.entity.data.IData;
import noppes.npcs.api.wrapper.WorldWrapper;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public final class AbilityCombatHelper {
    private static final Random RANDOM = new Random();
    private static final double DASH_CLIP_STEP = 0.2;
    private static final double DASH_WALL_MARGIN = 0.35;
    private static final double MIN_DASH_DISTANCE = 0.5;
    /** Dash may step onto / phase through obstacles up to this height. */
    private static final double DASH_MAX_STEP = 1.05;
    private static final double JUMP_CLIP_STEP = 0.25;
    private static final double JUMP_WALL_MARGIN = 0.35;
    private static final double JUMP_HIT_EPSILON = 0.08;

    /** Encounter key prefix for Drachenfels pair board snapshots. */
    public static final String DRACHENFELS_PAIR_ID_KEY = "df_pair_id";
    private static final int BOARD_BREAK_Y_DOWN = 1;
    private static final int BOARD_BREAK_Y_UP = 3;
    private static final int BOARD_RESTORE_FLAGS = 3;
    private static final Map<String, List<BrokenBoardEntry>> BROKEN_BOARDS = new ConcurrentHashMap<>();

    private AbilityCombatHelper() {
    }

    /** Saved plank for arena restore after full aggro reset. */
    public static final class BrokenBoardEntry {
        public final BlockPos pos;
        public final BlockState state;

        public BrokenBoardEntry(final BlockPos pos, final BlockState state) {
            this.pos = pos.immutable();
            this.state = state;
        }
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
     * Server-side pin for grab/parasite: teleport + zero all motion (no client packets).
     */
    public static void pinLiving(final IEntityLiving living, final double x, final double y, final double z) {
        if (living == null) {
            return;
        }
        try {
            living.setPosition(x, y, z);
            living.setMotionX(0.0);
            living.setMotionY(0.0);
            living.setMotionZ(0.0);
        } catch (final Exception ignored) {
        }
    }

    /**
     * Pull hostile players in {@code pullRadius} toward {@code (x,y,z)}, placing them at
     * {@code standOff} blocks out (preserves relative XZ angle). Server-only, no packets.
     *
     * @return number of players pulled
     */
    public static int pullPlayersToward(
            final AbilityContext ctx,
            final double x,
            final double y,
            final double z,
            final double pullRadius,
            final double standOff) {
        if (ctx == null || ctx.npc == null || ctx.world == null || pullRadius <= 0.05) {
            return 0;
        }
        final double standoff = Math.max(0.6, standOff);
        final int range = (int) Math.ceil(pullRadius + 0.5);
        // EntitiesType.PLAYER = 1
        final IEntity[] list = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(x, y, z),
                range,
                1);

        int pulled = 0;
        for (final IEntity ent : list) {
            if (!(ent instanceof IEntityLiving) || !isHostileToBoss(ctx.npc, ent)) {
                continue;
            }
            try {
                final Object mc = ent.getMCEntity();
                if (!(mc instanceof PlayerEntity) || !((PlayerEntity) mc).isAlive()) {
                    continue;
                }
            } catch (final Exception ignored) {
                continue;
            }
            if (flatDistance(ent.getX(), ent.getZ(), x, z) > pullRadius) {
                continue;
            }

            double dx = ent.getX() - x;
            double dz = ent.getZ() - z;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.08) {
                final double ang = RANDOM.nextDouble() * Math.PI * 2.0;
                dx = Math.cos(ang);
                dz = Math.sin(ang);
                len = 1.0;
            }
            final double nx = x + (dx / len) * standoff;
            final double nz = z + (dz / len) * standoff;
            final double ny = findFeetGroundY(ctx.world, nx, nz, y);
            pinLiving((IEntityLiving) ent, nx, ny, nz);
            AbilityVfx.spawnHitParticle(ctx.world, ent);
            pulled++;
        }
        return pulled;
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
     * Ground under the entity's feet. Unlike {@link #findGroundY}, does not scan
     * above {@code feetY} — a ceiling/roof must not become the zone/telegraph floor.
     */
    public static double findFeetGroundY(
            final noppes.npcs.api.IWorld world,
            final double x,
            final double z,
            final double feetY) {
        if (world instanceof WorldWrapper) {
            final World mcWorld = ((WorldWrapper) world).getMCWorld();
            if (mcWorld != null) {
                return findFeetGroundYMc(mcWorld, x, z, feetY);
            }
        }
        return findGroundY(world, x, z, feetY);
    }

    private static double findFeetGroundYMc(
            final World world,
            final double x,
            final double z,
            final double feetY) {
        final int bx = MathHelper.floor(x);
        final int bz = MathHelper.floor(z);
        final int from = MathHelper.floor(feetY);
        final int minY = Math.max(0, from - 8);
        final BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int by = from; by >= minY; by--) {
            pos.set(bx, by, bz);
            final BlockState state = world.getBlockState(pos);
            if (!isCollisionSolid(state, world, pos)) {
                continue;
            }
            final VoxelShape shape = state.getCollisionShape(world, pos);
            final double top = by + shape.max(Direction.Axis.Y);
            if (top > feetY + 0.05) {
                continue;
            }
            return top;
        }
        return feetY;
    }

    /**
     * Solid ground only: {@code material.isSolid()} + non-empty collision shape.
     * Ignores grass/flowers/moss/fog and other walk-through blocks.
     * Requires two clear body blocks above the floor (no spawn inside walls).
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
            pos.set(bx, by + 1, bz);
            if (isCollisionSolid(world.getBlockState(pos), world, pos)) {
                continue;
            }
            pos.set(bx, by + 2, bz);
            if (isCollisionSolid(world.getBlockState(pos), world, pos)) {
                continue;
            }
            pos.set(bx, by, bz);
            final VoxelShape shape = state.getCollisionShape(world, pos);
            return by + shape.max(Direction.Axis.Y);
        }
        return startY;
    }

    /**
     * True if an NPC-sized body can stand at {@code (x,y,z)}: solid floor under feet
     * and no solid collision in a ~0.6×1.8 hitbox.
     */
    public static boolean canStandAt(
            final noppes.npcs.api.IWorld world,
            final double x,
            final double y,
            final double z) {
        if (world instanceof WorldWrapper) {
            final World mcWorld = ((WorldWrapper) world).getMCWorld();
            if (mcWorld != null) {
                final AxisAlignedBB body = new AxisAlignedBB(
                        x - 0.3, y, z - 0.3,
                        x + 0.3, y + 1.8, z + 0.3);
                if (!mcWorld.noCollision(body)) {
                    return false;
                }
                final BlockPos below = new BlockPos(
                        MathHelper.floor(x),
                        MathHelper.floor(y - 0.05),
                        MathHelper.floor(z));
                return isCollisionSolid(mcWorld.getBlockState(below), mcWorld, below);
            }
        }
        return canStandAtBlocks(world, x, y, z);
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

    /**
     * Усечённый конус: вершина сзади, урон только в кольце дистанций от вершины
     * [{@code minDist}, {@code maxDist}] — у «основания» у кастера уже есть ширина.
     *
     * @return число попаданий
     */
    public static int damageInTruncatedCone(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double apexX,
            final double apexY,
            final double apexZ,
            final float yaw,
            final double halfAngleDeg,
            final double minDist,
            final double maxDist,
            final double damage,
            final double knockback,
            final double lift,
            final int fireSeconds) {
        final double rad = (yaw + 90.0) * 0.0174532925;
        final double fwdX = Math.cos(rad);
        final double fwdZ = Math.sin(rad);
        final double midX = apexX + fwdX * ((minDist + maxDist) * 0.5);
        final double midZ = apexZ + fwdZ * ((minDist + maxDist) * 0.5);
        final int range = (int) Math.ceil(maxDist + 1.5);
        final IEntity[] list = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(midX, apexY, midZ),
                range,
                -1);

        int hits = 0;
        for (final IEntity ent : list) {
            if (!isHostileToBoss(ctx.npc, ent)) {
                continue;
            }
            final String id = String.valueOf(ent.getUUID());
            if (active.hitUuids.contains(id)) {
                continue;
            }
            final double dist = flatDistance(ent.getX(), ent.getZ(), apexX, apexZ);
            if (dist < minDist || dist > maxDist) {
                continue;
            }
            if (!isInFrontCone(apexX, apexZ, yaw, ent, halfAngleDeg)) {
                continue;
            }
            ent.damage((float) damage);
            if (ent instanceof IEntityLiving) {
                final IEntityLiving living = (IEntityLiving) ent;
                living.setMotionX(fwdX * knockback);
                living.setMotionY(lift);
                living.setMotionZ(fwdZ * knockback);
            }
            if (fireSeconds > 0) {
                igniteEntity(ent, fireSeconds);
            }
            AbilityVfx.spawnHitParticle(ctx.world, ent);
            active.hitUuids.add(id);
            hits++;
        }
        return hits;
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
                ((LivingEntity) mc).addEffect(
                        new EffectInstance(effect, duration, amplifier, false, true, true));
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
        return clipJumpLanding(active, ctx);
    }

    /**
     * Point on the jump arc at {@code progress} (0..1), clipped so the NPC never
     * occupies solid blocks. If the intended point is blocked, returns the last
     * free point along the same arc.
     */
    public static double[] resolveJumpPointAtProgress(
            final AbilityContext ctx,
            final double sx,
            final double sy,
            final double sz,
            final double ex,
            final double ey,
            final double ez,
            final double arcHeight,
            final double progress) {
        final double t = Math.max(0.0, Math.min(1.0, progress));
        final double[] intended = jumpPointAt(sx, sy, sz, ex, ey, ez, arcHeight, t);
        if (canNpcOccupy(ctx, intended[0], intended[1], intended[2])) {
            return intended;
        }

        double low = 0.0;
        double high = t;
        double[] best = new double[]{sx, sy, sz};
        for (int i = 0; i < 10; i++) {
            final double mid = (low + high) * 0.5;
            final double[] midPoint = jumpPointAt(sx, sy, sz, ex, ey, ez, arcHeight, mid);
            if (canNpcOccupy(ctx, midPoint[0], midPoint[1], midPoint[2])) {
                low = mid;
                best = midPoint;
            } else {
                high = mid;
            }
        }
        return best;
    }

    public static boolean isJumpPointBlocked(
            final double[] resolved,
            final double[] intended) {
        if (resolved == null || intended == null || resolved.length < 3 || intended.length < 3) {
            return false;
        }
        return Math.abs(resolved[0] - intended[0]) > JUMP_HIT_EPSILON
                || Math.abs(resolved[1] - intended[1]) > JUMP_HIT_EPSILON
                || Math.abs(resolved[2] - intended[2]) > JUMP_HIT_EPSILON;
    }

    private static double[] jumpPointAt(
            final double sx,
            final double sy,
            final double sz,
            final double ex,
            final double ey,
            final double ez,
            final double arcHeight,
            final double t) {
        final double cx = sx + (ex - sx) * t;
        final double cz = sz + (ez - sz) * t;
        final double baseY = sy + (ey - sy) * t;
        final double cy = baseY + Math.sin(t * Math.PI) * arcHeight;
        return new double[]{cx, cy, cz};
    }

    /**
     * Pull landing XZ back toward start until the NPC can stand there (no walls).
     * Does not require a clear ground path — the arc may clear obstacles mid-flight.
     */
    private static boolean clipJumpLanding(final ActiveAbility active, final AbilityContext ctx) {
        final double dx = active.ex - active.sx;
        final double dz = active.ez - active.sz;
        final double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.05) {
            return false;
        }
        final double dirX = dx / len;
        final double dirZ = dz / len;

        double distance = len;
        while (distance >= MIN_DASH_DISTANCE) {
            final double x = active.sx + dirX * distance;
            final double z = active.sz + dirZ * distance;
            final double y = findGroundY(ctx.world, x, z, active.sy);
            if (canNpcOccupy(ctx, x, y, z)) {
                if (distance < len) {
                    distance = Math.max(MIN_DASH_DISTANCE, distance - JUMP_WALL_MARGIN);
                    active.ex = active.sx + dirX * distance;
                    active.ez = active.sz + dirZ * distance;
                    active.ey = findGroundY(ctx.world, active.ex, active.ez, active.sy);
                    if (canNpcOccupy(ctx, active.ex, active.ey, active.ez)) {
                        return true;
                    }
                }
                active.ex = x;
                active.ez = z;
                active.ey = y;
                return true;
            }
            distance -= JUMP_CLIP_STEP;
        }
        return false;
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

    /**
     * Чистый урон ({@link DamageSource#MAGIC}): обходит броню.
     * Не путать с {@link IEntity#damage} — тот бьёт {@link DamageSource#GENERIC}.
     *
     * @param ignoreIframes если true, сбрасывает {@code invulnerableTime} перед ударом
     *                      (нужно для стабильного DoT раз в секунду)
     * @return true если урон применён (или fallback {@code entity.damage} вызван)
     */
    public static boolean dealPureDamage(final IEntity victim, final float amount, final boolean ignoreIframes) {
        if (victim == null || !victim.isAlive() || amount <= 0.0F) {
            return false;
        }
        try {
            final Entity mc = victim.getMCEntity();
            if (mc instanceof LivingEntity) {
                return dealPureDamage((LivingEntity) mc, amount, ignoreIframes);
            }
        } catch (final Exception ignored) {
        }
        try {
            victim.damage(amount);
            return true;
        } catch (final Exception ignored) {
            return false;
        }
    }

    public static boolean dealPureDamage(final IEntity victim, final float amount) {
        return dealPureDamage(victim, amount, false);
    }

    public static boolean dealPureDamage(final LivingEntity living, final float amount, final boolean ignoreIframes) {
        if (living == null || !living.isAlive() || amount <= 0.0F) {
            return false;
        }
        if (ignoreIframes) {
            living.invulnerableTime = 0;
        }
        return living.hurt(DamageSource.MAGIC, amount);
    }

    public static boolean dealPureDamage(final LivingEntity living, final float amount) {
        return dealPureDamage(living, amount, false);
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
        damageNearbyInternal(
                active, ctx, x, y, z, radius, damage, dirX, dirZ, knockback, lift, useFixedDir, false);
    }

    /**
     * Как {@link #damageNearby}, но чистый {@link DamageSource#MAGIC} (без брони).
     */
    public static void damageNearbyPure(
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
        damageNearbyInternal(
                active, ctx, x, y, z, radius, damage, dirX, dirZ, knockback, lift, useFixedDir, true);
    }

    private static void damageNearbyInternal(
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
            final boolean useFixedDir,
            final boolean pure) {
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
            if (pure) {
                dealPureDamage(ent, (float) damage, false);
            } else {
                ent.damage((float) damage);
            }
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

    public static int damageInMagicRing(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double x,
            final double y,
            final double z,
            final double innerRadius,
            final double outerRadius,
            final float damage) {
        final int range = (int) Math.ceil(outerRadius + 1.0);
        final IEntity[] list = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(x, y, z),
                range,
                -1);

        int hits = 0;
        for (final IEntity ent : list) {
            if (!isHostileToBoss(ctx.npc, ent)) {
                continue;
            }
            final String id = String.valueOf(ent.getUUID());
            if (active.hitUuids.contains(id)) {
                continue;
            }
            final double dist = flatDistance(ent.getX(), ent.getZ(), x, z);
            if (dist < innerRadius || dist > outerRadius) {
                continue;
            }
            if (!dealPureDamage(ent, damage, false)) {
                ent.damage(damage);
            }
            AbilityVfx.spawnHitParticle(ctx.world, ent);
            active.hitUuids.add(id);
            hits++;
        }
        return hits;
    }

    /**
     * Урон по прямоугольному коридору от (sx,sz) до (ex,ez) шириной {@code halfWidth * 2}.
     *
     * @return число поражённых целей
     */
    public static int damageInCorridor(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double sx,
            final double sy,
            final double sz,
            final double ex,
            final double ey,
            final double ez,
            final double halfWidth,
            final double damage,
            final double knockback,
            final double lift,
            final int fireSeconds,
            final String effectId,
            final int effectTicks,
            final int effectAmplifier) {
        final double dx = ex - sx;
        final double dz = ez - sz;
        final double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.05) {
            return 0;
        }
        final double nx = dx / len;
        final double nz = dz / len;
        final double midX = (sx + ex) * 0.5;
        final double midZ = (sz + ez) * 0.5;
        final double midY = (sy + ey) * 0.5;
        final int range = (int) Math.ceil(len * 0.5 + halfWidth + 1.5);
        final IEntity[] list = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(midX, midY, midZ),
                range,
                -1);

        int hits = 0;
        for (final IEntity ent : list) {
            if (!isHostileToBoss(ctx.npc, ent)) {
                continue;
            }
            final String id = String.valueOf(ent.getUUID());
            if (active.hitUuids.contains(id)) {
                continue;
            }
            if (!isInCorridor(ent.getX(), ent.getZ(), sx, sz, ex, ez, halfWidth)) {
                continue;
            }
            ent.damage((float) damage);
            if (ent instanceof IEntityLiving) {
                final IEntityLiving living = (IEntityLiving) ent;
                living.setMotionX(nx * knockback);
                living.setMotionY(lift);
                living.setMotionZ(nz * knockback);
            }
            if (fireSeconds > 0) {
                igniteEntity(ent, fireSeconds);
            }
            if (effectId != null && !effectId.isEmpty() && effectTicks > 0) {
                applyNamedEffect(ent, effectId, effectTicks, effectAmplifier);
            }
            AbilityVfx.spawnHitParticle(ctx.world, ent);
            active.hitUuids.add(id);
            hits++;
        }
        return hits;
    }

    public static boolean isInCorridor(
            final double px,
            final double pz,
            final double sx,
            final double sz,
            final double ex,
            final double ez,
            final double halfWidth) {
        final double dx = ex - sx;
        final double dz = ez - sz;
        final double lenSq = dx * dx + dz * dz;
        if (lenSq < 0.0001) {
            return flatDistance(px, pz, sx, sz) <= halfWidth;
        }
        double t = ((px - sx) * dx + (pz - sz) * dz) / lenSq;
        if (t < 0.0) {
            t = 0.0;
        } else if (t > 1.0) {
            t = 1.0;
        }
        final double closestX = sx + t * dx;
        final double closestZ = sz + t * dz;
        return flatDistance(px, pz, closestX, closestZ) <= halfWidth;
    }

    public static void igniteEntity(final IEntity entity, final int seconds) {
        if (entity == null || seconds <= 0) {
            return;
        }
        try {
            final Entity mc = entity.getMCEntity();
            if (mc instanceof LivingEntity) {
                mc.setSecondsOnFire(seconds);
            }
        } catch (final Exception ignored) {
        }
    }

    /**
     * Вешает эффект по registry id ({@code "wfm:stun"}, {@code "minecraft:slowness"}, …).
     */
    public static boolean applyNamedEffect(
            final IEntity entity,
            final String effectId,
            final int durationTicks,
            final int amplifier) {
        if (entity == null || effectId == null || effectId.isEmpty() || durationTicks <= 0) {
            return false;
        }
        try {
            final Effect effect = ForgeRegistries.POTIONS.getValue(new ResourceLocation(effectId));
            if (effect == null) {
                return false;
            }
            applyEffect(entity, effect, durationTicks, amplifier);
            return true;
        } catch (final Exception ignored) {
            return false;
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

    public static void healCaster(final AbilityContext ctx, final double amount) {
        if (ctx == null || ctx.npc == null) {
            return;
        }
        try {
            final Object mc = ctx.npc.getMCEntity();
            if (mc instanceof LivingEntity) {
                healLiving((LivingEntity) mc, amount);
            }
        } catch (final Exception ignored) {
        }
    }

    public static void healLiving(final LivingEntity living, final double amount) {
        if (living == null || amount <= 0.0) {
            return;
        }
        living.setHealth(Math.min(living.getMaxHealth(), living.getHealth() + (float) amount));
    }

    /**
     * One step of Drachenfels HP-equalize ritual: healthier half loses a small share,
     * weaker half gains more (asymmetric transfer). No-op if HP ratios are close.
     *
     * @return {@code true} if any HP was moved
     */
    public static boolean transferDrachenfelsRitualHp(final ICustomNpc a, final ICustomNpc b) {
        return transferDrachenfelsRitualHp(a, b, 5.0F, 0.18F, 0.10F);
    }

    /**
     * @param maxHealPerStep max HP the weaker half gains this call
     * @param drainOfHeal    donor loses {@code heal * drainOfHeal} (e.g. 0.18)
     * @param minRatioGap    minimum |ratioA - ratioB| required to transfer
     */
    public static boolean transferDrachenfelsRitualHp(
            final ICustomNpc a,
            final ICustomNpc b,
            final float maxHealPerStep,
            final float drainOfHeal,
            final float minRatioGap) {
        final LivingEntity livingA = resolveLiving(a);
        final LivingEntity livingB = resolveLiving(b);
        if (livingA == null || livingB == null) {
            return false;
        }
        if (!livingA.isAlive() || !livingB.isAlive()) {
            return false;
        }

        final float maxA = livingA.getMaxHealth();
        final float maxB = livingB.getMaxHealth();
        if (maxA <= 0.01F || maxB <= 0.01F) {
            return false;
        }

        final float hpA = livingA.getHealth();
        final float hpB = livingB.getHealth();
        final float ratioA = hpA / maxA;
        final float ratioB = hpB / maxB;
        final float gap = Math.abs(ratioA - ratioB);
        final float gapFloor = Math.max(0.01F, minRatioGap);
        if (gap < gapFloor) {
            return false;
        }

        final boolean aIsDonor = ratioA >= ratioB;
        final LivingEntity donor = aIsDonor ? livingA : livingB;
        final LivingEntity recipient = aIsDonor ? livingB : livingA;
        final float donorMax = aIsDonor ? maxA : maxB;
        final float recipientMax = aIsDonor ? maxB : maxA;

        final float missing = Math.max(0.0F, recipientMax - recipient.getHealth());
        if (missing <= 0.05F) {
            return false;
        }

        // Close a slice of the ratio gap, but never more than maxHealPerStep / missing.
        final float gapHeal = gap * recipientMax * 0.35F;
        final float heal = Math.min(Math.max(0.05F, maxHealPerStep), Math.min(missing, gapHeal));
        if (heal <= 0.05F) {
            return false;
        }

        final float drainFrac = MathHelper.clamp(drainOfHeal, 0.0F, 1.0F);
        final float drain = Math.min(heal * drainFrac, Math.max(0.0F, donor.getHealth() - 1.0F));

        recipient.setHealth(Math.min(recipientMax, recipient.getHealth() + heal));
        if (drain > 0.05F) {
            donor.setHealth(Math.max(1.0F, donor.getHealth() - drain));
        }
        return true;
    }

    private static LivingEntity resolveLiving(final ICustomNpc npc) {
        if (npc == null) {
            return null;
        }
        try {
            final Object mc = npc.getMCEntity();
            if (mc instanceof LivingEntity) {
                return (LivingEntity) mc;
            }
        } catch (final Exception ignored) {
        }
        return null;
    }

    /**
     * Encounter key for Drachenfels board snapshots: {@code df_pair_id} or NPC UUID.
     */
    public static String resolveDrachenfelsEncounterKey(final ICustomNpc npc) {
        if (npc == null) {
            return "";
        }
        try {
            final IData data = npc.getStoreddata();
            if (data != null && data.has(DRACHENFELS_PAIR_ID_KEY)) {
                final Object raw = data.get(DRACHENFELS_PAIR_ID_KEY);
                if (raw != null) {
                    final String pairId = String.valueOf(raw).trim();
                    if (!pairId.isEmpty() && !"null".equalsIgnoreCase(pairId)) {
                        return "df:" + pairId;
                    }
                }
            }
        } catch (final Exception ignored) {
        }
        try {
            return "npc:" + String.valueOf(npc.getUUID());
        } catch (final Exception ignored) {
            return "";
        }
    }

    /**
     * Breaks only {@link BlockTags#PLANKS} in a cylinder around the blast.
     * Original states are appended to the encounter snapshot for later restore.
     *
     * @return number of plank blocks removed
     */
    public static int breakWoodenPlanksInRadius(
            final ICustomNpc npc,
            final IWorld world,
            final double x,
            final double y,
            final double z,
            final double radius) {
        if (npc == null || world == null || radius <= 0.05) {
            return 0;
        }
        final World mcWorld = resolveMcWorld(world, npc);
        if (mcWorld == null || mcWorld.isClientSide) {
            return 0;
        }
        final String key = resolveDrachenfelsEncounterKey(npc);
        if (key.isEmpty()) {
            return 0;
        }

        final double r = Math.max(0.5, radius);
        final double rSq = r * r;
        final int minX = MathHelper.floor(x - r);
        final int maxX = MathHelper.floor(x + r);
        final int minZ = MathHelper.floor(z - r);
        final int maxZ = MathHelper.floor(z + r);
        final int baseY = MathHelper.floor(y);
        final int minY = baseY - BOARD_BREAK_Y_DOWN;
        final int maxY = baseY + BOARD_BREAK_Y_UP;

        final List<BrokenBoardEntry> snapshot = BROKEN_BOARDS.computeIfAbsent(key, k -> new ArrayList<>());
        int broken = 0;

        synchronized (snapshot) {
            for (int bx = minX; bx <= maxX; bx++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    final double dx = (bx + 0.5) - x;
                    final double dz = (bz + 0.5) - z;
                    if (dx * dx + dz * dz > rSq) {
                        continue;
                    }
                    for (int by = minY; by <= maxY; by++) {
                        final BlockPos pos = new BlockPos(bx, by, bz);
                        final BlockState state = mcWorld.getBlockState(pos);
                        if (!isWoodenPlank(state)) {
                            continue;
                        }
                        if (!containsBoardPos(snapshot, pos)) {
                            snapshot.add(new BrokenBoardEntry(pos, state));
                        }
                        try {
                            mcWorld.levelEvent(2001, pos, Block.getId(state));
                            if (mcWorld.removeBlock(pos, false)) {
                                broken++;
                            }
                        } catch (final Exception ignored) {
                        }
                    }
                }
            }
        }
        return broken;
    }

    /**
     * Restores all snapped wooden planks for this NPC's encounter key.
     *
     * @return number of blocks restored
     */
    public static int restoreBrokenBoards(final ICustomNpc npc) {
        if (npc == null) {
            return 0;
        }
        final String key = resolveDrachenfelsEncounterKey(npc);
        if (key.isEmpty()) {
            return 0;
        }
        final List<BrokenBoardEntry> snapshot = BROKEN_BOARDS.remove(key);
        if (snapshot == null || snapshot.isEmpty()) {
            return 0;
        }
        final World mcWorld = resolveMcWorld(null, npc);
        if (mcWorld == null || mcWorld.isClientSide) {
            return 0;
        }
        int restored = 0;
        synchronized (snapshot) {
            for (final BrokenBoardEntry entry : snapshot) {
                if (entry == null || entry.pos == null || entry.state == null) {
                    continue;
                }
                try {
                    if (mcWorld.setBlock(entry.pos, entry.state, BOARD_RESTORE_FLAGS)) {
                        restored++;
                    }
                } catch (final Exception ignored) {
                }
            }
            snapshot.clear();
        }
        return restored;
    }

    /** Drops encounter board snapshot without placing blocks back. */
    public static void clearBrokenBoards(final ICustomNpc npc) {
        if (npc == null) {
            return;
        }
        final String key = resolveDrachenfelsEncounterKey(npc);
        if (key.isEmpty()) {
            return;
        }
        final List<BrokenBoardEntry> snapshot = BROKEN_BOARDS.remove(key);
        if (snapshot != null) {
            synchronized (snapshot) {
                snapshot.clear();
            }
        }
    }

    public static int getBrokenBoardCount(final ICustomNpc npc) {
        if (npc == null) {
            return 0;
        }
        final String key = resolveDrachenfelsEncounterKey(npc);
        if (key.isEmpty()) {
            return 0;
        }
        final List<BrokenBoardEntry> snapshot = BROKEN_BOARDS.get(key);
        if (snapshot == null) {
            return 0;
        }
        synchronized (snapshot) {
            return snapshot.size();
        }
    }

    private static boolean isWoodenPlank(final BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        try {
            return state.getBlock().is(BlockTags.PLANKS);
        } catch (final Exception ignored) {
            return false;
        }
    }

    private static boolean containsBoardPos(final List<BrokenBoardEntry> snapshot, final BlockPos pos) {
        for (final BrokenBoardEntry entry : snapshot) {
            if (entry != null && entry.pos != null && entry.pos.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    private static World resolveMcWorld(final IWorld world, final ICustomNpc npc) {
        if (world instanceof WorldWrapper) {
            try {
                return ((WorldWrapper) world).getMCWorld();
            } catch (final Exception ignored) {
            }
        }
        try {
            final Object mc = npc.getMCEntity();
            if (mc instanceof Entity) {
                return ((Entity) mc).level;
            }
        } catch (final Exception ignored) {
        }
        return null;
    }
}
