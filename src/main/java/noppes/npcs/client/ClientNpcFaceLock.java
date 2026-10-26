package noppes.npcs.client;

import net.minecraft.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Жёсткий лок взгляда NPC на клиенте (см. {@code NpcTurnHelper.lockFace}).
 *
 * <p>yBodyRot не синхронизируется ванилой: клиентский BodyController догоняет голову
 * с задержкой 10–20 тиков, поэтому GeckoLib-модель смотрела мимо телеграфа.
 * Сервер шлёт yaw пакетом, рендер ставит его в body/head каждый кадр.
 */
public final class ClientNpcFaceLock {
    /** Без refresh от сервера лок снимается сам (смерть, выход из трекинга). */
    private static final long EXPIRE_TICKS = 40L;

    private static final Map<Integer, Lock> LOCKS = new HashMap<>();

    private ClientNpcFaceLock() {
    }

    public static void set(final int entityId, final boolean locked, final float yaw, final long gameTime) {
        if (locked) {
            LOCKS.put(entityId, new Lock(yaw, gameTime + EXPIRE_TICKS));
        } else {
            LOCKS.remove(entityId);
        }
    }

    /** Применяет лок к сущности; false — лока нет. */
    public static boolean apply(final LivingEntity entity) {
        if (LOCKS.isEmpty()) {
            return false;
        }
        final Lock lock = LOCKS.get(entity.getId());
        if (lock == null) {
            return false;
        }
        if (entity.level == null || entity.level.getGameTime() > lock.until) {
            LOCKS.remove(entity.getId());
            return false;
        }
        entity.yBodyRot = lock.yaw;
        entity.yBodyRotO = lock.yaw;
        entity.yHeadRot = lock.yaw;
        entity.yHeadRotO = lock.yaw;
        return true;
    }

    public static void clear() {
        LOCKS.clear();
    }

    private static final class Lock {
        private final float yaw;
        private final long until;

        private Lock(final float yaw, final long until) {
            this.yaw = yaw;
            this.until = until;
        }
    }
}
