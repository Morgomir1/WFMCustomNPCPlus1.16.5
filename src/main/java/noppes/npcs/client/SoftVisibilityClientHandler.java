package noppes.npcs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.NpcSoftVisibility;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Soft-hide client housekeeping: clear stale hide flags on logout, and scrub
 * crosshair / hitResult when they still point at a soft-hidden NPC.
 * <p>
 * Note: RPG-HUD Entity Inspect does not read these fields — it raycasts entity
 * AABBs itself; that path is covered by {@code EntitySoftHideBoundingBoxMixin}.
 */
@Mod.EventBusSubscriber(modid = "customnpcs", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SoftVisibilityClientHandler {
    private SoftVisibilityClientHandler() {
    }

    @SubscribeEvent
    public static void onLoggedOut(final ClientPlayerNetworkEvent.LoggedOutEvent event) {
        ClientNpcVisibility.clear();
        ClientNpcSpawnData.clear();
    }

    @SubscribeEvent
    public static void onEntityJoin(final EntityJoinWorldEvent event) {
        if (!event.getWorld().isClientSide()) {
            return;
        }
        ClientNpcSpawnData.applyIfPending(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        ClientNpcSpawnData.tryApplyPending();

        final RayTraceResult hit = mc.hitResult;
        if (hit instanceof EntityRayTraceResult) {
            final Entity target = ((EntityRayTraceResult) hit).getEntity();
            if (target instanceof EntityNPCInterface
                    && NpcSoftVisibility.isInvisibleTo((EntityNPCInterface) target, mc.player)) {
                mc.hitResult = BlockRayTraceResult.miss(
                        hit.getLocation(),
                        mc.player.getDirection(),
                        new BlockPos(hit.getLocation()));
            }
        }

        if (mc.crosshairPickEntity instanceof EntityNPCInterface
                && NpcSoftVisibility.isInvisibleTo((EntityNPCInterface) mc.crosshairPickEntity, mc.player)) {
            mc.crosshairPickEntity = null;
        }
    }
}
