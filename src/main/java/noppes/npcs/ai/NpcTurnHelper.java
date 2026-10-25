package noppes.npcs.ai;

import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import noppes.npcs.api.entity.ICustomNpc;

/**
 * Плавный поворот для крупных CNPC (как WFM SmoothMovementController у Горгона).
 *
 * <pre>
 * NpcTurnHelper.apply(npc, 5.0); // °/тик; у AbstractAnimatedEntity WFM — 5
 * </pre>
 */
public final class NpcTurnHelper {
    private NpcTurnHelper() {
    }

    /**
     * Ставит лимит поворота тела (move) и взгляда (look). Повторить после смены
     * navigation type у CNPC (fly/swim сбрасывает moveControl).
     */
    public static void apply(final ICustomNpc npc, final float degreesPerTick) {
        if (npc == null) {
            return;
        }
        final Entity raw;
        try {
            raw = npc.getMCEntity();
        } catch (final Exception e) {
            return;
        }
        if (!(raw instanceof MobEntity)) {
            return;
        }
        final MobEntity mob = (MobEntity) raw;
        final float speed = Math.max(0.5F, degreesPerTick);
        mob.moveControl = new SmoothMovementController(mob, speed);
        mob.lookControl = new SmoothLookController(mob, speed);
    }
}
