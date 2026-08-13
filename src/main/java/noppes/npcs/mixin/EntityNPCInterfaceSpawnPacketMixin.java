package noppes.npcs.mixin;

import net.minecraft.entity.CreatureEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.IPacket;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;

/**
 * {@link EntityNPCInterface} implements {@code IEntityAdditionalSpawnData}, but vanilla
 * {@code CreatureEntity#getAddEntityPacket} sends {@code SSpawnMobPacket} which does not
 * carry that NBT. Teleporting/flying to an NPC that spawned while this client was out of
 * range left Display on constructor defaults ("Noppes").
 */
@Mixin(EntityNPCInterface.class)
public abstract class EntityNPCInterfaceSpawnPacketMixin extends CreatureEntity {

    protected EntityNPCInterfaceSpawnPacketMixin(final EntityType<? extends CreatureEntity> type, final World world) {
        super(type, world);
    }

    @Override
    public IPacket<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
