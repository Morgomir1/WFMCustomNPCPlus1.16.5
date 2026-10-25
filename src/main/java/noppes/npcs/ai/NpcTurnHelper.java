package noppes.npcs.ai;

import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.controller.LookController;
import net.minecraft.entity.ai.controller.MovementController;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import noppes.npcs.api.entity.ICustomNpc;

import java.lang.reflect.Field;

/**
 * Плавный поворот для крупных CNPC (как WFM SmoothMovementController у Горгона).
 *
 * <p>CNPC {@code updateTasks()} после {@code setRetaliateType}/{@code updateAI} снова ставит
 * ванильный {@link MovementController} — поэтому скорость хранится в PersistentData и
 * контроллеры переустанавливаются mixin'ом / {@link #ensureInstalled}.
 *
 * <pre>
 * NpcTurnHelper.apply(npc, 5.0); // °/тик; у AbstractAnimatedEntity WFM — 5
 * </pre>
 */
public final class NpcTurnHelper {
    public static final String DATA_KEY = "cnpcSmoothTurnDeg";

    /** SRG: MobEntity.moveControl */
    private static final Field MOVE_CONTROL =
            ObfuscationReflectionHelper.findField(MobEntity.class, "field_70765_h");
    /** SRG: MobEntity.lookControl */
    private static final Field LOOK_CONTROL =
            ObfuscationReflectionHelper.findField(MobEntity.class, "field_70749_g");

    private NpcTurnHelper() {
    }

    /**
     * Включает плавный поворот и запоминает °/тик (переживает rebuild AI).
     */
    public static void apply(final ICustomNpc npc, final float degreesPerTick) {
        final MobEntity mob = asMob(npc);
        if (mob == null) {
            return;
        }
        final float speed = Math.max(0.5F, degreesPerTick);
        mob.getPersistentData().putFloat(DATA_KEY, speed);
        install(mob, speed);
    }

    public static void clear(final ICustomNpc npc) {
        final MobEntity mob = asMob(npc);
        if (mob == null) {
            return;
        }
        mob.getPersistentData().remove(DATA_KEY);
    }

    /**
     * Если на сущности висит {@link #DATA_KEY} — вернуть smooth-контроллеры
     * (после {@code EntityNPCInterface.updateTasks}).
     */
    public static void ensureInstalled(final MobEntity mob) {
        if (mob == null || mob.level == null || mob.level.isClientSide) {
            return;
        }
        final CompoundNBT data = mob.getPersistentData();
        if (!data.contains(DATA_KEY)) {
            return;
        }
        install(mob, Math.max(0.5F, data.getFloat(DATA_KEY)));
    }

    private static void install(final MobEntity mob, final float speed) {
        final MovementController currentMove = mob.getMoveControl();
        final boolean groundVanilla = currentMove != null
                && currentMove.getClass() == MovementController.class;
        final boolean alreadySmooth = currentMove instanceof SmoothMovementController;
        if (groundVanilla
                || (alreadySmooth && ((SmoothMovementController) currentMove).getRotationSpeed() != speed)) {
            setMoveControl(mob, new SmoothMovementController(mob, speed));
        }

        final LookController currentLook = mob.getLookControl();
        final boolean lookOk = currentLook instanceof SmoothLookController
                && ((SmoothLookController) currentLook).getMaxYawPerTick() == speed;
        if (!lookOk) {
            setLookControl(mob, new SmoothLookController(mob, speed));
        }
    }

    private static MobEntity asMob(final ICustomNpc npc) {
        if (npc == null) {
            return null;
        }
        try {
            final Entity raw = npc.getMCEntity();
            return raw instanceof MobEntity ? (MobEntity) raw : null;
        } catch (final Exception e) {
            return null;
        }
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
