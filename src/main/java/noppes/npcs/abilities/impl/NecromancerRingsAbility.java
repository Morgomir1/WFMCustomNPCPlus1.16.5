package noppes.npcs.abilities.impl;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.registries.ForgeRegistries;
import noppes.npcs.abilities.*;
import noppes.npcs.abilities.event.NecromancerCombatHandler;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NecromancerRingsAbility implements CnpcAbility {
    public static final String ID = "necro_rings";

    private static final int RING_COUNT = 3;
    private static final String WARPFIRE_BLOCK = "wfm:warpfire";
    /** 0.5 с — сколько висит warpfire в блоках кольца. */
    private static final int WARPFIRE_TICKS = 10;
    /**
     * UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE — без neighbor shape update.
     * Иначе соседний FireBlock.updateShape → AbstractFireBlock.getState()
     * подменяет wfm:warpfire на minecraft:fire.
     */
    private static final int PLACE_FLAGS = 2 | 16;

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
        return AbilityDefaults.necroRings();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR,
                AbilityParamKeys.RING1_DISTANCE,
                AbilityParamKeys.RING1_RADIUS,
                AbilityParamKeys.RING2_DISTANCE,
                AbilityParamKeys.RING2_RADIUS,
                AbilityParamKeys.RING3_DISTANCE,
                AbilityParamKeys.RING3_RADIUS);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        if (NecromancerCombatHandler.isStunned(ctx.npc)) {
            return false;
        }
        active.jumpStyle = false;
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 20));
        active.meter = 0.0F;
        active.hitUuids.clear();
        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        if (ctx.target != null) {
            final double dx = ctx.target.getX() - ctx.npc.getX();
            final double dz = ctx.target.getZ() - ctx.npc.getZ();
            active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
            ctx.npc.setRotation(active.yaw);
        }
        spawnCurrentTelegraph(active, ctx);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:block.beacon.power_select", 0.8F, 0.7F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        final int ringIndex = Math.max(0, Math.min(RING_COUNT - 1, (int) active.meter));
        final double[] ring = resolveRing(ctx.params, ringIndex);
        AbilityCombatHelper.holdInPlace(ctx.npc, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
        ctx.npc.setRotation(active.yaw);

        if (active.ticksLeft % 4 == 0) {
            AbilityVfx.spawnDarkCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
            AbilityVfx.spawnDarkSoulRing(
                    ctx.world,
                    ctx.npc.getX(),
                    ctx.npc.getY() + 0.05,
                    ctx.npc.getZ(),
                    ring[0],
                    ring[1]);
        }

        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }

        detonateCurrentRing(active, ctx, ringIndex, ring);
        active.meter = ringIndex + 1;
        if ((int) active.meter >= RING_COUNT) {
            return TickResult.FINISHED;
        }

        active.hitUuids.clear();
        active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 20));
        spawnCurrentTelegraph(active, ctx);
        return TickResult.CONTINUE;
    }

    private void detonateCurrentRing(
            final ActiveAbility active,
            final AbilityContext ctx,
            final int ringIndex,
            final double[] ring) {
        clearTelegraphs(active);
        final double inner = ring[0];
        final double outer = ring[1];
        final double x = ctx.npc.getX();
        final double y = AbilityCombatHelper.findGroundY(ctx.world, x, ctx.npc.getZ(), ctx.npc.getY()) + 0.05;
        final double z = ctx.npc.getZ();

        AbilityCombatHelper.damageInMagicRing(
                active,
                ctx,
                x,
                y + 0.5,
                z,
                inner,
                outer,
                (float) ctx.params.getDouble(AbilityParamKeys.DAMAGE, 10.0));
        spawnTempWarpfire(ctx, x, y, z, inner, outer);
        AbilityVfx.spawnDarkSoulRing(ctx.world, x, y, z, inner, outer);
        AbilityVfx.spawnSoulBurst(ctx.world, x, y + 0.2, z, outer);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(x, y, z),
                "minecraft:entity.wither.break_block",
                0.8F,
                0.75F + ringIndex * 0.08F);
    }

    /**
     * Во всех блоках annulus на уровне пола: если воздух — ставит {@link #WARPFIRE_BLOCK}
     * (без neighbor shape update) и через {@link #WARPFIRE_TICKS} убирает любой
     * {@link AbstractFireBlock} на этих позициях.
     */
    private void spawnTempWarpfire(
            final AbilityContext ctx,
            final double x,
            final double groundY,
            final double z,
            final double inner,
            final double outer) {
        final World mcWorld = resolveMcWorld(ctx);
        if (mcWorld == null || mcWorld.isClientSide) {
            return;
        }
        final Block warpfire = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(WARPFIRE_BLOCK));
        if (warpfire == null || warpfire == Blocks.AIR) {
            return;
        }

        final IWorld world = ctx.world;
        final double innerSq = inner * inner;
        final double outerSq = outer * outer;
        final int minX = MathHelper.floor(x - outer);
        final int maxX = MathHelper.floor(x + outer);
        final int minZ = MathHelper.floor(z - outer);
        final int maxZ = MathHelper.floor(z + outer);
        final List<BlockPos> placed = new ArrayList<>();

        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                final double dx = (bx + 0.5) - x;
                final double dz = (bz + 0.5) - z;
                final double distSq = dx * dx + dz * dz;
                if (distSq < innerSq || distSq > outerSq) {
                    continue;
                }
                final double colY = AbilityCombatHelper.findGroundY(world, bx + 0.5, bz + 0.5, groundY);
                final int by = MathHelper.floor(colY);
                final BlockPos pos = new BlockPos(bx, by, bz);
                if (!mcWorld.isEmptyBlock(pos)) {
                    continue;
                }
                try {
                    final BlockState state = warpfire.defaultBlockState();
                    if (mcWorld.setBlock(pos, state, PLACE_FLAGS)) {
                        placed.add(pos.immutable());
                    }
                } catch (final Exception ignored) {
                }
            }
        }
        scheduleWarpfireCleanup(mcWorld, placed);
    }

    private void scheduleWarpfireCleanup(final World mcWorld, final List<BlockPos> placed) {
        if (mcWorld == null || mcWorld.getServer() == null || placed == null || placed.isEmpty()) {
            return;
        }
        final int when = mcWorld.getServer().getTickCount() + WARPFIRE_TICKS;
        final List<BlockPos> snapshot = new ArrayList<>(placed);
        mcWorld.getServer().tell(new net.minecraft.util.concurrent.TickDelayedTask(when, () -> {
            for (final BlockPos pos : snapshot) {
                if (pos == null) {
                    continue;
                }
                try {
                    final BlockState state = mcWorld.getBlockState(pos);
                    if (!(state.getBlock() instanceof AbstractFireBlock)) {
                        continue;
                    }
                    mcWorld.removeBlock(pos, false);
                } catch (final Exception ignored) {
                }
            }
        }));
    }

    private static World resolveMcWorld(final AbilityContext ctx) {
        try {
            final Object mc = ctx.npc.getMCEntity();
            if (mc instanceof net.minecraft.entity.Entity) {
                return ((net.minecraft.entity.Entity) mc).level;
            }
        } catch (final Exception ignored) {
        }
        return null;
    }

    private void spawnCurrentTelegraph(final ActiveAbility active, final AbilityContext ctx) {
        clearTelegraphs(active);
        final int ringIndex = Math.max(0, Math.min(RING_COUNT - 1, (int) active.meter));
        final double[] ring = resolveRing(ctx.params, ringIndex);
        final int chargeTicks = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 20));
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, TelegraphAPI.DEFAULT_COLOR);
        final String id = TelegraphAPI.ring(
                ctx.npc,
                ctx.npc.getX(),
                ctx.npc.getY(),
                ctx.npc.getZ(),
                ring[1],
                ring[0],
                chargeTicks,
                color);
        if (id != null && !id.isEmpty()) {
            active.telegraphIds.add(id);
        }
    }

    /** @return {inner, outer}; outer = distance + radius */
    private static double[] resolveRing(final AbilityParams params, final int ringIndex) {
        final double distance;
        final double radius;
        if (ringIndex <= 0) {
            distance = params.getDouble(AbilityParamKeys.RING1_DISTANCE, 3.0);
            radius = params.getDouble(AbilityParamKeys.RING1_RADIUS, 2.0);
        } else if (ringIndex == 1) {
            distance = params.getDouble(AbilityParamKeys.RING2_DISTANCE, 6.0);
            radius = params.getDouble(AbilityParamKeys.RING2_RADIUS, 3.0);
        } else {
            distance = params.getDouble(AbilityParamKeys.RING3_DISTANCE, 9.0);
            radius = params.getDouble(AbilityParamKeys.RING3_RADIUS, 4.0);
        }
        final double inner = Math.max(0.0, distance);
        final double outer = inner + Math.max(0.1, radius);
        return new double[] {inner, outer};
    }

    private void clearTelegraphs(final ActiveAbility active) {
        if (active.telegraphIds.isEmpty()) {
            return;
        }
        for (final String id : active.telegraphIds) {
            if (id != null && !id.isEmpty()) {
                TelegraphAPI.remove(id);
            }
        }
        active.telegraphIds.clear();
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        clearTelegraphs(active);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        clearTelegraphs(active);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
