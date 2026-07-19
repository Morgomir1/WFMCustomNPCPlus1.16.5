package noppes.npcs.packets.client;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.client.telegraph.ClientTelegraphManager;
import noppes.npcs.shared.common.PacketBasic;
import noppes.npcs.telegraph.TelegraphInstance;
import noppes.npcs.telegraph.TelegraphType;

public class PacketTelegraphSpawn extends PacketBasic {
    private final String id;
    private final int typeId;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float radius;
    private final float innerRadius;
    private final float length;
    private final float width;
    private final float angle;
    private final float heightOffset;
    private final int color;
    private final int warningColor;
    private final int remainingTicks;
    private final int totalTicks;
    private final int followEntityId;
    private final boolean warning;

    public PacketTelegraphSpawn(
            final String id,
            final int typeId,
            final double x,
            final double y,
            final double z,
            final float yaw,
            final float radius,
            final float innerRadius,
            final float length,
            final float width,
            final float angle,
            final float heightOffset,
            final int color,
            final int warningColor,
            final int remainingTicks,
            final int totalTicks,
            final int followEntityId,
            final boolean warning) {
        this.id = id;
        this.typeId = typeId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.radius = radius;
        this.innerRadius = innerRadius;
        this.length = length;
        this.width = width;
        this.angle = angle;
        this.heightOffset = heightOffset;
        this.color = color;
        this.warningColor = warningColor;
        this.remainingTicks = remainingTicks;
        this.totalTicks = totalTicks;
        this.followEntityId = followEntityId;
        this.warning = warning;
    }

    public static PacketTelegraphSpawn from(final TelegraphInstance instance) {
        return new PacketTelegraphSpawn(
                instance.id,
                instance.type.ordinal(),
                instance.x,
                instance.y,
                instance.z,
                instance.yaw,
                instance.radius,
                instance.innerRadius,
                instance.length,
                instance.width,
                instance.angle,
                instance.heightOffset,
                instance.color,
                instance.warningColor,
                instance.remainingTicks,
                instance.totalTicks,
                instance.followEntityId,
                instance.warning);
    }

    public static void encode(final PacketTelegraphSpawn msg, final PacketBuffer buf) {
        buf.writeUtf(msg.id);
        buf.writeVarInt(msg.typeId);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeFloat(msg.yaw);
        buf.writeFloat(msg.radius);
        buf.writeFloat(msg.innerRadius);
        buf.writeFloat(msg.length);
        buf.writeFloat(msg.width);
        buf.writeFloat(msg.angle);
        buf.writeFloat(msg.heightOffset);
        buf.writeInt(msg.color);
        buf.writeInt(msg.warningColor);
        buf.writeVarInt(msg.remainingTicks);
        buf.writeVarInt(msg.totalTicks);
        buf.writeVarInt(msg.followEntityId);
        buf.writeBoolean(msg.warning);
    }

    public static PacketTelegraphSpawn decode(final PacketBuffer buf) {
        return new PacketTelegraphSpawn(
                buf.readUtf(),
                buf.readVarInt(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readInt(),
                buf.readInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean());
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void handle() {
        final TelegraphInstance instance = new TelegraphInstance(
                TelegraphType.byId(this.typeId),
                null,
                this.x,
                this.y,
                this.z,
                this.yaw,
                this.totalTicks);
        instance.radius = this.radius;
        instance.innerRadius = this.innerRadius;
        instance.length = this.length;
        instance.width = this.width;
        instance.angle = this.angle;
        instance.heightOffset = this.heightOffset;
        instance.color = this.color;
        instance.warningColor = this.warningColor;
        instance.remainingTicks = this.remainingTicks;
        instance.totalTicks = this.totalTicks;
        instance.followEntityId = this.followEntityId;
        instance.warning = this.warning;
        // Preserve server-assigned id
        ClientTelegraphManager.put(this.id, instance);
    }
}
