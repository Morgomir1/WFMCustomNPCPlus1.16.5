package noppes.npcs.telegraph;

import net.minecraft.entity.Entity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import java.util.UUID;

public final class TelegraphInstance {
    public String id;
    public final TelegraphType type;
    public RegistryKey<World> dimension;
    public double x;
    public double y;
    public double z;
    public float yaw;
    public double prevX;
    public double prevY;
    public double prevZ;
    public float prevYaw;

    public float radius;
    public float innerRadius;
    public float length;
    public float width;
    public float angle;
    public float heightOffset = 0.05f;
    public int color = 0x80FF3030;
    public int warningColor = 0xC0FF0000;
    public int remainingTicks;
    public int totalTicks;
    public int followEntityId = -1;
    public int groundSearchRange = 3;
    public boolean warning;

    public TelegraphInstance(
            final TelegraphType type,
            final World world,
            final double x,
            final double y,
            final double z,
            final float yaw,
            final int durationTicks) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.type = type;
        this.dimension = world == null ? World.OVERWORLD : world.dimension();
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.prevX = x;
        this.prevY = y;
        this.prevZ = z;
        this.prevYaw = yaw;
        this.remainingTicks = Math.max(1, durationTicks);
        this.totalTicks = this.remainingTicks;
    }

    public boolean tick() {
        if (this.remainingTicks <= 0) {
            return false;
        }
        this.prevX = this.x;
        this.prevY = this.y;
        this.prevZ = this.z;
        this.prevYaw = this.yaw;
        this.remainingTicks--;

        final ServerWorld world = resolveWorld();
        if (this.followEntityId >= 0 && world != null) {
            final Entity entity = world.getEntity(this.followEntityId);
            if (entity != null) {
                this.x = entity.getX();
                this.z = entity.getZ();
                this.y = findGroundY(world, entity.getX(), entity.getY(), entity.getZ(), this.groundSearchRange);
                this.yaw = entity.yRot;
            }
        }

        final int warnAt = Math.max(1, this.totalTicks / 4);
        if (!this.warning && this.remainingTicks <= warnAt) {
            this.warning = true;
        }
        return this.remainingTicks > 0;
    }

    public ServerWorld resolveWorld() {
        try {
            final net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null || this.dimension == null) {
                return null;
            }
            return server.getLevel(this.dimension);
        } catch (final Exception e) {
            return null;
        }
    }

    public float getProgress() {
        if (this.totalTicks <= 0) {
            return 1.0f;
        }
        return 1.0f - ((float) this.remainingTicks / (float) this.totalTicks);
    }

    public int getCurrentColor() {
        return this.warning ? this.warningColor : this.color;
    }

    public int getAnimatedColor(final float partialTicks) {
        final int base = getCurrentColor();
        final float time = (this.totalTicks - this.remainingTicks) + partialTicks;
        final float breath = (float) Math.sin(time * 0.12);
        final float pulse = 0.55f + 0.45f * (0.5f + 0.5f * breath);
        final int alpha = (int) (((base >> 24) & 0xFF) * pulse);
        return (alpha << 24) | (base & 0x00FFFFFF);
    }

    public double getInterpolatedX(final float partialTicks) {
        return this.prevX + (this.x - this.prevX) * partialTicks;
    }

    public double getInterpolatedY(final float partialTicks) {
        return this.prevY + (this.y - this.prevY) * partialTicks;
    }

    public double getInterpolatedZ(final float partialTicks) {
        return this.prevZ + (this.z - this.prevZ) * partialTicks;
    }

    public float getInterpolatedYaw(final float partialTicks) {
        float diff = this.yaw - this.prevYaw;
        while (diff > 180.0f) {
            diff -= 360.0f;
        }
        while (diff < -180.0f) {
            diff += 360.0f;
        }
        return this.prevYaw + diff * partialTicks;
    }

    public static double findGroundY(
            final World world,
            final double x,
            final double y,
            final double z,
            final int searchRange) {
        final int ix = (int) Math.floor(x);
        final int iz = (int) Math.floor(z);
        final int startY = (int) Math.floor(y);
        for (int dy = 0; dy <= searchRange; dy++) {
            final int checkY = startY - dy;
            if (checkY < 0) {
                break;
            }
            final BlockPos ground = new BlockPos(ix, checkY, iz);
            final BlockPos above = ground.above();
            if (!world.isEmptyBlock(ground) && world.isEmptyBlock(above)) {
                return checkY + 1.02;
            }
        }
        return y;
    }
}
