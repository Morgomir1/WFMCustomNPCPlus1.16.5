package noppes.npcs.ai;

import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.controller.LookController;

/**
 * LookControl с потолком °/тик — режет мгновенные развороты головы/взгляда
 * (CNPC AttackTarget передаёт 30°/тик).
 */
public class SmoothLookController extends LookController {
    private final float maxYawPerTick;

    public SmoothLookController(final MobEntity mob, final float maxYawPerTick) {
        super(mob);
        this.maxYawPerTick = Math.max(0.5F, maxYawPerTick);
    }

    public float getMaxYawPerTick() {
        return this.maxYawPerTick;
    }

    @Override
    public void setLookAt(
            final double x,
            final double y,
            final double z,
            final float deltaYaw,
            final float deltaPitch) {
        super.setLookAt(
                x,
                y,
                z,
                Math.min(deltaYaw, this.maxYawPerTick),
                Math.min(deltaPitch, this.maxYawPerTick));
    }
}
