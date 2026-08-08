package noppes.npcs.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileHelper;
import noppes.npcs.NpcSoftVisibility;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Predicate;

/**
 * Wrap entity-raytrace predicates so soft-hidden NPCs are never hit — covers HUD/HP mods
 * that call {@link ProjectileHelper} with a custom filter and skip {@code isPickable}.
 */
@Mixin(ProjectileHelper.class)
public abstract class ProjectileHelperSoftHideMixin {

    @ModifyVariable(
            method = "getEntityHitResult(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/vector/Vector3d;Lnet/minecraft/util/math/vector/Vector3d;Lnet/minecraft/util/math/AxisAlignedBB;Ljava/util/function/Predicate;D)Lnet/minecraft/util/math/EntityRayTraceResult;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private static Predicate<Entity> cnpc$filterClientPick(final Predicate<Entity> filter) {
        return cnpc$andSoftHide(filter);
    }

    @ModifyVariable(
            method = "getEntityHitResult(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/vector/Vector3d;Lnet/minecraft/util/math/vector/Vector3d;Lnet/minecraft/util/math/AxisAlignedBB;Ljava/util/function/Predicate;)Lnet/minecraft/util/math/EntityRayTraceResult;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private static Predicate<Entity> cnpc$filterWorldPick(final Predicate<Entity> filter) {
        return cnpc$andSoftHide(filter);
    }

    private static Predicate<Entity> cnpc$andSoftHide(final Predicate<Entity> filter) {
        final PlayerEntity player = Minecraft.getInstance().player;
        if (player == null) {
            return filter;
        }
        final Predicate<Entity> softHide = entity -> {
            if (!(entity instanceof EntityNPCInterface)) {
                return true;
            }
            return !NpcSoftVisibility.isInvisibleTo((EntityNPCInterface) entity, player);
        };
        return filter == null ? softHide : filter.and(softHide);
    }
}
