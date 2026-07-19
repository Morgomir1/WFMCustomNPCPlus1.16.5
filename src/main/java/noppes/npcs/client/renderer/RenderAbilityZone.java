package noppes.npcs.client.renderer;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.entity.EntityAbilityZone;
import noppes.npcs.entity.EntityAbilityZone.ZoneShape;
import org.lwjgl.opengl.GL11;

@OnlyIn(Dist.CLIENT)
public class RenderAbilityZone extends EntityRenderer<EntityAbilityZone> {
    private static final int SEGMENTS = 32;
    private static final ResourceLocation DUMMY =
            new ResourceLocation("minecraft", "textures/misc/white.png");

    public RenderAbilityZone(final EntityRendererManager manager) {
        super(manager);
        this.shadowRadius = 0;
    }

    @Override
    public void render(
            final EntityAbilityZone entity,
            final float entityYaw,
            final float partialTicks,
            final MatrixStack matrix,
            final IRenderTypeBuffer buffer,
            final int packedLight) {
        if (!entity.isZoneVisible() && entity.getZoneType() == EntityAbilityZone.ZoneType.TRAP
                && !entity.isTrapTriggered()) {
            return;
        }
        if (!entity.isZoneVisible() && !entity.isTrapTriggered()) {
            return;
        }

        final int color = entity.getColor();
        float a = ((color >> 24) & 0xFF) / 255.0f;
        final float r = ((color >> 16) & 0xFF) / 255.0f;
        final float g = ((color >> 8) & 0xFF) / 255.0f;
        final float b = (color & 0xFF) / 255.0f;

        if (entity.isTrapTriggered() && entity.getTriggerFlashTick() >= 0) {
            final float since = entity.tickCount - entity.getTriggerFlashTick() + partialTicks;
            a = Math.max(0f, a * (1f - since / 10f));
        }

        final float pulse = 0.75f + 0.25f * (float) Math.sin((entity.tickCount + partialTicks) * 0.15);
        a *= pulse;

        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        matrix.pushPose();
        matrix.translate(0, 0.05, 0);
        final Matrix4f mat = matrix.last().pose();
        final float radius = entity.getRadius();

        if (entity.isGroundFill()) {
            switch (entity.getShape()) {
                case RING:
                    renderRing(mat, radius, entity.getInnerRadius(), r, g, b, a * 0.55f);
                    break;
                case SQUARE:
                    renderSquare(mat, radius, r, g, b, a * 0.55f);
                    break;
                case CIRCLE:
                default:
                    renderCircle(mat, radius, r, g, b, a * 0.55f);
                    break;
            }
        }
        if (entity.isBorder()) {
            if (entity.getShape() == ZoneShape.SQUARE) {
                renderSquareBorder(mat, radius, r, g, b, Math.min(1f, a * 1.5f));
            } else {
                renderCircleBorder(mat, radius, r, g, b, Math.min(1f, a * 1.5f));
                if (entity.getShape() == ZoneShape.RING) {
                    renderCircleBorder(mat, entity.getInnerRadius(), r, g, b, Math.min(1f, a * 1.5f));
                }
            }
        }

        matrix.popPose();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableTexture();
    }

    private void renderCircle(
            final Matrix4f mat,
            final float radius,
            final float r,
            final float g,
            final float b,
            final float a) {
        final Tessellator tess = Tessellator.getInstance();
        final BufferBuilder buf = tess.getBuilder();
        buf.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        buf.vertex(mat, 0, 0, 0).color(r, g, b, a).endVertex();
        for (int i = 0; i <= SEGMENTS; i++) {
            final double ang = (Math.PI * 2 * i) / SEGMENTS;
            buf.vertex(mat, (float) (Math.cos(ang) * radius), 0, (float) (Math.sin(ang) * radius))
                    .color(r, g, b, a).endVertex();
        }
        tess.end();
    }

    private void renderCircleBorder(
            final Matrix4f mat,
            final float radius,
            final float r,
            final float g,
            final float b,
            final float a) {
        final Tessellator tess = Tessellator.getInstance();
        final BufferBuilder buf = tess.getBuilder();
        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < SEGMENTS; i++) {
            final double ang = (Math.PI * 2 * i) / SEGMENTS;
            buf.vertex(mat, (float) (Math.cos(ang) * radius), 0.02f, (float) (Math.sin(ang) * radius))
                    .color(r, g, b, a).endVertex();
        }
        tess.end();
    }

    private void renderRing(
            final Matrix4f mat,
            final float outer,
            final float inner,
            final float r,
            final float g,
            final float b,
            final float a) {
        final Tessellator tess = Tessellator.getInstance();
        final BufferBuilder buf = tess.getBuilder();
        buf.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= SEGMENTS; i++) {
            final double ang = (Math.PI * 2 * i) / SEGMENTS;
            final float c = (float) Math.cos(ang);
            final float s = (float) Math.sin(ang);
            buf.vertex(mat, c * outer, 0, s * outer).color(r, g, b, a).endVertex();
            buf.vertex(mat, c * inner, 0, s * inner).color(r, g, b, a).endVertex();
        }
        tess.end();
    }

    private void renderSquare(
            final Matrix4f mat,
            final float radius,
            final float r,
            final float g,
            final float b,
            final float a) {
        final Tessellator tess = Tessellator.getInstance();
        final BufferBuilder buf = tess.getBuilder();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buf.vertex(mat, -radius, 0, -radius).color(r, g, b, a).endVertex();
        buf.vertex(mat, radius, 0, -radius).color(r, g, b, a).endVertex();
        buf.vertex(mat, radius, 0, radius).color(r, g, b, a).endVertex();
        buf.vertex(mat, -radius, 0, radius).color(r, g, b, a).endVertex();
        tess.end();
    }

    private void renderSquareBorder(
            final Matrix4f mat,
            final float radius,
            final float r,
            final float g,
            final float b,
            final float a) {
        final Tessellator tess = Tessellator.getInstance();
        final BufferBuilder buf = tess.getBuilder();
        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        buf.vertex(mat, -radius, 0.02f, -radius).color(r, g, b, a).endVertex();
        buf.vertex(mat, radius, 0.02f, -radius).color(r, g, b, a).endVertex();
        buf.vertex(mat, radius, 0.02f, radius).color(r, g, b, a).endVertex();
        buf.vertex(mat, -radius, 0.02f, radius).color(r, g, b, a).endVertex();
        tess.end();
    }

    @Override
    public ResourceLocation getTextureLocation(final EntityAbilityZone entity) {
        return DUMMY;
    }
}
