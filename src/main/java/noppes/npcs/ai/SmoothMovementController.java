package noppes.npcs.ai;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.controller.MovementController;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.VoxelShape;

/**
 * Ванильный MOVE_TO с лимитом °/тик. Без полной остановки на довороте —
 * иначе крупные NPC крутятся на месте. Порт с WFM SmoothMovementController.
 */
public class SmoothMovementController extends MovementController {
    private final float rotationSpeed;

    public SmoothMovementController(final MobEntity mob) {
        this(mob, 15.0F);
    }

    public SmoothMovementController(final MobEntity mob, final float rotationSpeed) {
        super(mob);
        this.rotationSpeed = Math.max(0.5F, rotationSpeed);
    }

    public float getRotationSpeed() {
        return this.rotationSpeed;
    }

    @Override
    public void tick() {
        if (this.operation == Action.STRAFE) {
            super.tick();
            return;
        }

        if (this.operation == Action.MOVE_TO) {
            this.operation = Action.WAIT;

            final double dx = this.wantedX - this.mob.getX();
            final double dy = this.wantedY - this.mob.getY();
            final double dz = this.wantedZ - this.mob.getZ();
            final double distanceSqr = dx * dx + dy * dy + dz * dz;

            final double arriveDist = Math.max(0.05D, this.mob.getBbWidth() * 0.2D);
            if (distanceSqr < arriveDist * arriveDist) {
                this.mob.setZza(0.0F);
                return;
            }

            final double horizontalSqr = dx * dx + dz * dz;
            float angleDiff = 0.0F;
            if (horizontalSqr > 1.0E-4D) {
                final float desiredYRot = (float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
                angleDiff = MathHelper.wrapDegrees(desiredYRot - this.mob.yRot);
                this.mob.yRot = this.rotlerp(this.mob.yRot, desiredYRot, this.rotationSpeed);
            }

            final float speed = (float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
            final float turnFactor = 1.0F - MathHelper.clamp((Math.abs(angleDiff) - 45.0F) / 135.0F, 0.0F, 0.5F);
            this.mob.setSpeed(speed * turnFactor);

            final BlockPos pos = this.mob.blockPosition();
            final BlockState state = this.mob.level.getBlockState(pos);
            final Block block = state.getBlock();
            final VoxelShape shape = state.getCollisionShape(this.mob.level, pos);
            if (dy > (double) this.mob.maxUpStep && horizontalSqr < (double) Math.max(1.0F, this.mob.getBbWidth())
                    || !shape.isEmpty() && this.mob.getY() < shape.max(Direction.Axis.Y) + (double) pos.getY()
                    && !block.is(BlockTags.DOORS) && !block.is(BlockTags.FENCES)) {
                this.mob.getJumpControl().jump();
                this.operation = Action.JUMPING;
            }
        } else if (this.operation == Action.JUMPING) {
            this.mob.setSpeed((float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
            if (this.mob.isOnGround()) {
                this.operation = Action.WAIT;
            }
        } else {
            this.mob.setZza(0.0F);
        }
    }

    @Override
    protected float rotlerp(final float current, final float target, final float maxStep) {
        float diff = MathHelper.wrapDegrees(target - current);
        if (diff > maxStep) {
            diff = maxStep;
        }
        if (diff < -maxStep) {
            diff = -maxStep;
        }
        return MathHelper.wrapDegrees(current + diff);
    }
}
