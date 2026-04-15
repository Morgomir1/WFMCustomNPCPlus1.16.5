package noppes.npcs.entity.data;

import net.minecraft.entity.MobEntity;
import net.minecraft.util.math.BlockPos;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Сущности из шаблона структуры получают корректные координаты из ванили, но CustomNPCs
 * хранит в NBT абсолютные координаты ИИ (старт, маршрут). Пересчитываем их относительно
 * фактической позиции после {@link net.minecraft.world.gen.feature.template.Template}.
 */
public final class NpcStructureTemplateSpawnFix {
    /** Если старт уже совпал с новой позицией, но путь всё ещё в старом мире — сдвиг по первой точке. */
    private static final int PATH_ANCHOR_DIST_SQ = 32 * 32;

    private NpcStructureTemplateSpawnFix() {
    }

    private static BlockPos pathPoint(final int[] p) {
        if (p == null || p.length < 3) {
            return null;
        }
        return new BlockPos(p[0], p[1], p[2]);
    }

    public static void apply(final EntityNPCInterface npc) {
        if (npc == null || !npc.isAlive() || npc.ais == null) {
            return;
        }
        if (npc instanceof MobEntity) {
            ((MobEntity) npc).getNavigation().stop();
        }
        final BlockPos at = npc.blockPosition();
        final BlockPos oldStart = npc.ais.startPos();
        int dx = at.getX() - oldStart.getX();
        int dy = at.getY() - oldStart.getY();
        int dz = at.getZ() - oldStart.getZ();
        final int pathLen = npc.ais.getMovingPathSize();
        if (dx == 0 && dy == 0 && dz == 0 && pathLen > 0) {
            final int[] p0 = npc.ais.getMovingPathPos(0);
            final BlockPos p0b = pathPoint(p0);
            if (p0b != null && at.distSqr(p0b) > PATH_ANCHOR_DIST_SQ) {
                dx = at.getX() - p0b.getX();
                dy = at.getY() - p0b.getY();
                dz = at.getZ() - p0b.getZ();
            }
        }
        if (dx == 0 && dy == 0 && dz == 0) {
            return;
        }
        npc.ais.setStartPos(at);
        for (int i = 0; i < pathLen; i++) {
            final int[] p = npc.ais.getMovingPathPos(i);
            if (p != null && p.length >= 3) {
                npc.ais.setMovingPathPos(i, new int[]{p[0] + dx, p[1] + dy, p[2] + dz});
            }
        }
        if (npc instanceof MobEntity) {
            final MobEntity mob = (MobEntity) npc;
            if (mob.hasRestriction()) {
                final BlockPos rc = mob.getRestrictCenter();
                mob.restrictTo(rc.offset(dx, dy, dz), (int) mob.getRestrictRadius());
            }
        }
    }
}
