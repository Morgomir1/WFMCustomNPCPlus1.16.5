package noppes.npcs.ai;

import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.controller.LookController;
import net.minecraft.entity.ai.controller.MovementController;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import noppes.npcs.api.entity.ICustomNpc;

import java.lang.reflect.Field;

/**
 * Плавный поворот для крупных CNPC (как WFM SmoothMovementController у Горгона).
 *
 * <pre>
 * NpcTurnHelper.apply(npc, 5.0); // °/тик; у AbstractAnimatedEntity WFM — 5
 * </pre>
 */
public final class NpcTurnHelper {
    /** SRG: MobEntity.moveControl */
    private static final Field MOVE_CONTROL =
            ObfuscationReflectionHelper.findField(MobEntity.class, "field_70765_h");
    /** SRG: MobEntity.lookControl */
    private static final Field LOOK_CONTROL =
            ObfuscationReflectionHelper.findField(MobEntity.class, "field_70749_g");

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
        setMoveControl(mob, new SmoothMovementController(mob, speed));
        setLookControl(mob, new SmoothLookController(mob, speed));
    }

    private static void setMoveControl(final MobEntity mob, final MovementController control) {
        try {
            MOVE_CONTROL.set(mob, control);
        } catch (final IllegalAccessException e) {
            throw new IllegalStateException("Failed to set MobEntity.moveControl", e);
        }
    }

    private static void setLookControl(final MobEntity mob, final LookController control) {
        try {
            LOOK_CONTROL.set(mob, control);
        } catch (final IllegalAccessException e) {
            throw new IllegalStateException("Failed to set MobEntity.lookControl", e);
        }
    }
}
