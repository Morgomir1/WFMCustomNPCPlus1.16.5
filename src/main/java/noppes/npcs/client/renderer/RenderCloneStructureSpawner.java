package noppes.npcs.client.renderer;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.entity.EntityCloneStructureSpawner;

@OnlyIn(Dist.CLIENT)
public class RenderCloneStructureSpawner extends EntityRenderer<EntityCloneStructureSpawner> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("customnpcs", "textures/entity/clone_structure_spawner.png");

    private final ModelRenderer plate;

    public RenderCloneStructureSpawner(final EntityRendererManager manager) {
        super(manager);
        this.plate = new ModelRenderer(20, 26, 0, 0);
        this.plate.addBox(-5.0f, 0.0f, 0.0f, 10.0f, 26.0f, 0.0f);
    }

    @Override
    public void render(final EntityCloneStructureSpawner entity, final float entityYaw, final float partialTicks,
                       final MatrixStack matrix, final IRenderTypeBuffer buffer, final int packedLight) {
        final Minecraft mc = Minecraft.getInstance();
        final boolean hide = entity.isInvisibleTo(mc.player) || mc.options.hideGui;
        if (hide) {
            return;
        }

        matrix.pushPose();
        final float spin = MathHelper.lerp(partialTicks, (float) entity.spinO, (float) entity.spin);
        matrix.mulPose(Vector3f.YP.rotationDegrees(spin * 10.0f));
        final IVertexBuilder builder = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.plate.render(matrix, builder, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        matrix.popPose();

        final String name = entity.getCloneName();
        if (name != null && !name.isEmpty()) {
            final ITextComponent label = new StringTextComponent(
                    "Clone[" + entity.getCloneTab() + "]: " + name
                            + " [" + entity.describeStatusLabel() + "]");
            this.renderNameTag(entity, label, matrix, buffer, packedLight);
        }
        super.render(entity, entityYaw, partialTicks, matrix, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(final EntityCloneStructureSpawner entity) {
        return TEXTURE;
    }

    @Override
    protected boolean shouldShowName(final EntityCloneStructureSpawner entity) {
        return false;
    }
}
