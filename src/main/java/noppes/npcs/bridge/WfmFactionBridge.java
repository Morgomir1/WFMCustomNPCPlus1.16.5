package noppes.npcs.bridge;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

public interface WfmFactionBridge {
    @Nullable
    ResourceLocation wfm$getFactionId();

    void wfm$setFactionId(@Nullable ResourceLocation id);
}