package noppes.npcs.client.renderer;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.entity.EntityNecroBeam;
import org.lwjgl.opengl.GL11;

/**
 * Ground-level elongated rectangular beam zones (Telegraph LINE style), not beacon beams.
 */
@OnlyIn(Dist.CLIENT)
public class RenderNecroBeam extends EntityRenderer<EntityNecroBeam> {
    private static final ResourceLocation DUMMY =
            new ResourceLocation("minecraft", "textures/misc/white.png");
    /** ARGB — purple hazard, matches necro theme. */
    private static final int COLOR = 0xA0B86BFF;

    public RenderNecroBeam(final EntityRendererManager manager) {
        super(manager);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(
            final EntityNecroBeam entity,
            final float entityYaw,
            final float partialTicks,
            final MatrixStack matrix,
            final IRenderTypeBuffer buffer,
            final int packedLight) {
        final int beams = entity.getBeamCount();
        final float baseYaw = entity.getInterpolatedYaw(partialTicks);
        final float length = (float) entity.getLength();
        final float halfW = (float) (EntityNecroBeam.ZONE_WIDTH * 0.5);

        final float a = ((COLOR >> 24) & 0xFF) / 255.0f;
        final float r = ((COLOR >> 16) & 0xFF) / 255.0f;
        final float g = ((COLOR >> 8) & 0xFF) / 255.0f;
        final float b = (COLOR & 0xFF) / 255.0f;
        final float pulse = 0.78f + 0.22f * (float) Math.sin((entity.tickCount + partialTicks) * 0.12);
        final float fillA = a * 0.55f * pulse;
        final float borderA = Math.min(1.0f, a * 1.45f * pulse);

        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        matrix.pushPose();
        matrix.translate(0.0, 0.05, 0.0);

        for (int i = 0; i < beams; i++) {
            final float yaw = EntityNecroBeam.beamYaw(baseYaw, i, beams);
            matrix.pushPose();
            // Same yaw convention as WFMTelegraph LINE: local +Z is the corridor.
            matrix.mulPose(Vector3f.YP.rotationDegrees(-yaw));
            final Matrix4f mat = matrix.last().pose();
            renderRect(mat, length, halfW, r, g, b, fillA);
            renderRectBorder(mat, length, halfW, r, g, b, borderA);
            matrix.popPose();
        }

        matrix.popPose();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableTexture();

        super.render(entity, entityYaw, partialTicks, matrix, buffer, packedLight);
    }

    private static void renderRect(
            final Matrix4f mat,
            final float length,
            final float halfW,
            final float r,
            final float g,
            final float b,
            final float a) {
        final Tessellator tess = Tessellator.getInstance();
        final BufferBuilder buf = tess.getBuilder();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buf.vertex(mat, -halfW, 0, 0).color(r, g, b, a).endVertex();
        buf.vertex(mat, halfW, 0, 0).color(r, g, b, a).endVertex();
        buf.vertex(mat, halfW, 0, length).color(r, g, b, a).endVertex();
        buf.vertex(mat, -halfW, 0, length).color(r, g, b, a).endVertex();
        tess.end();
    }

    private static void renderRectBorder(
            final Matrix4f mat,
            final float length,
            final float halfW,
            final float r,
            final float g,
            final float b,
            final float a) {
        final Tessellator tess = Tessellator.getInstance();
        final BufferBuilder buf = tess.getBuilder();
        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        buf.vertex(mat, -halfW, 0.02f, 0).color(r, g, b, a).endVertex();
        buf.vertex(mat, halfW, 0.02f, 0).color(r, g, b, a).endVertex();
        buf.vertex(mat, halfW, 0.02f, length).color(r, g, b, a).endVertex();
        buf.vertex(mat, -halfW, 0.02f, length).color(r, g, b, a).endVertex();
        tess.end();
    }

    @Override
    public ResourceLocation getTextureLocation(final EntityNecroBeam entity) {
        return DUMMY;
    }
}
