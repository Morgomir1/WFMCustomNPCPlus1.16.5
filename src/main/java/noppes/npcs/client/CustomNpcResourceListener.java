package noppes.npcs.client;

import net.minecraft.resources.*;
import net.minecraft.client.resources.*;
import noppes.npcs.client.gui.select.*;
import net.minecraft.client.particle.*;
import noppes.npcs.mixin.*;
import net.minecraft.client.*;
import net.minecraft.util.registry.*;
import net.minecraft.particles.*;
import noppes.npcs.client.fx.*;
import noppes.npcs.client.model.part.head.*;
import net.minecraft.util.*;
import noppes.npcs.shared.client.util.*;
import net.minecraft.client.renderer.texture.*;

public class CustomNpcResourceListener implements IResourceManagerReloadListener
{
    public static int DefaultTextColor;
    
    public void onResourceManagerReload(final IResourceManager var1) {
        if (var1 instanceof SimpleReloadableResourceManager) {
            try {
                CustomNpcResourceListener.DefaultTextColor = Integer.parseInt(I18n.get("customnpcs.defaultTextColor", new Object[0]), 16);
            }
            catch (NumberFormatException e) {
                CustomNpcResourceListener.DefaultTextColor = 0xFFFFB6;
            }
        }
        GuiTextureSelection.clear();
        this.createTextureCache();
        EntityEnderFX.portalSprite = ((ParticleManagerMixin)Minecraft.getInstance().particleEngine).getPacks().get(Registry.PARTICLE_TYPE.getKey(ParticleTypes.PORTAL));
        ModelHeadwear.clear();
    }
    
    private void createTextureCache() {
        this.enlargeTexture("acacia_planks");
        this.enlargeTexture("birch_planks");
        this.enlargeTexture("crimson_planks");
        this.enlargeTexture("dark_oak_planks");
        this.enlargeTexture("jungle_planks");
        this.enlargeTexture("oak_planks");
        this.enlargeTexture("spruce_planks");
        this.enlargeTexture("warped_planks");
        this.enlargeTexture("iron_block");
        this.enlargeTexture("diamond_block");
        this.enlargeTexture("stone");
        this.enlargeTexture("gold_block");
        this.enlargeTexture("white_wool");
    }
    
    private void enlargeTexture(final String texture) {
        final TextureManager manager = Minecraft.getInstance().getTextureManager();
        final ResourceLocation location = new ResourceLocation("customnpcs:textures/cache/" + texture + ".png");
        Texture ob = manager.getTexture(location);
        if (!(ob instanceof TextureCache)) {
            ob = (Texture)new TextureCache(location, new ResourceLocation("textures/block/" + texture + ".png"));
            manager.register(location, ob);
        }
    }
    
    static {
        CustomNpcResourceListener.DefaultTextColor = 0xFFFFB6;
    }
}
