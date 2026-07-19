package noppes.npcs.client.telegraph;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import noppes.npcs.telegraph.TelegraphInstance;
import noppes.npcs.telegraph.TelegraphType;
import org.lwjgl.opengl.GL11;

@OnlyIn(Dist.CLIENT)
public final class TelegraphWorldRenderer {
    private static final int CIRCLE_SEGMENTS = 32;
    private static final int CONE_SEGMENTS = 16;

    @SubscribeEvent
    public void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientTelegraphManager.tickClient();
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(final RenderWorldLastEvent event) {
        if (!ClientTelegraphManager.hasTelegraphs()) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        final float partialTicks = event.getPartialTicks();
        final Vector3d cam = mc.gameRenderer.getMainCamera().getPosition();
        final MatrixStack matrix = event.getMatrixStack();

        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableLighting();

        for (final TelegraphInstance instance : ClientTelegraphManager.getTelegraphs()) {
            if (instance.type == TelegraphType.NONE) {
                continue;
            }
            renderOne(matrix, instance, cam, partialTicks);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableTexture();
    }

    private void renderOne(
            final MatrixStack matrix,
            final TelegraphInstance instance,
            final Vector3d cam,
            final float partialTicks) {
        final double rx = instance.getInterpolatedX(partialTicks) - cam.x;
        final double ry = instance.getInterpolatedY(partialTicks) - cam.y + instance.heightOffset;
        final double rz = instance.getInterpolatedZ(partialTicks) - cam.z;

        final int color = instance.getAnimatedColor(partialTicks);
        final float a = ((color >> 24) & 0xFF) / 255.0f;
        final float r = ((color >> 16) & 0xFF) / 255.0f;
        final float g = ((color >> 8) & 0xFF) / 255.0f;
        final float b = (color & 0xFF) / 255.0f;

        matrix.pushPose();
        matrix.translate(rx, ry, rz);
        matrix.mulPose(net.minecraft.util.math.vector.Vector3f.YP.rotationDegrees(-instance.getInterpolatedYaw(partialTicks)));
        final Matrix4f mat = matrix.last().pose();

        switch (instance.type) {
            case CIRCLE:
                renderCircle(mat, instance.radius, r, g, b, a);
                break;
            case RING:
                renderRing(mat, instance.radius, instance.innerRadius, r, g, b, a);
                break;
            case LINE:
                renderLine(mat, instance.length, instance.width, r, g, b, a);
                break;
            case CONE:
                renderCone(mat, instance.length, instance.angle, instance.innerRadius, r, g, b, a);
                break;
            case SQUARE:
                renderSquare(mat, instance.radius, r, g, b, a);
                break;
            default:
                break;
        }
        matrix.popPose();
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
        for (int i = 0; i <= CIRCLE_SEGMENTS; i++) {
            final double angle = (Math.PI * 2.0 * i) / CIRCLE_SEGMENTS;
            buf.vertex(mat, (float) (Math.cos(angle) * radius), 0, (float) (Math.sin(angle) * radius))
                    .color(r, g, b, a).endVertex();
        }
        tess.end();
        renderCircleBorder(mat, radius, r, g, b, Math.min(1.0f, a * 2f));
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
        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            final double angle = (Math.PI * 2.0 * i) / CIRCLE_SEGMENTS;
            buf.vertex(mat, (float) (Math.cos(angle) * radius), 0.01f, (float) (Math.sin(angle) * radius))
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
        for (int i = 0; i <= CIRCLE_SEGMENTS; i++) {
            final double angle = (Math.PI * 2.0 * i) / CIRCLE_SEGMENTS;
            final float cos = (float) Math.cos(angle);
            final float sin = (float) Math.sin(angle);
            buf.vertex(mat, cos * outer, 0, sin * outer).color(r, g, b, a).endVertex();
            buf.vertex(mat, cos * inner, 0, sin * inner).color(r, g, b, a).endVertex();
        }
        tess.end();
        renderCircleBorder(mat, outer, r, g, b, Math.min(1.0f, a * 2f));
        renderCircleBorder(mat, inner, r, g, b, Math.min(1.0f, a * 2f));
    }

    private void renderLine(
            final Matrix4f mat,
            final float length,
            final float width,
            final float r,
            final float g,
            final float b,
            final float a) {
        final float half = width * 0.5f;
        final Tessellator tess = Tessellator.getInstance();
        final BufferBuilder buf = tess.getBuilder();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buf.vertex(mat, -half, 0, 0).color(r, g, b, a).endVertex();
        buf.vertex(mat, half, 0, 0).color(r, g, b, a).endVertex();
        buf.vertex(mat, half, 0, length).color(r, g, b, a).endVertex();
        buf.vertex(mat, -half, 0, length).color(r, g, b, a).endVertex();
        tess.end();

        buf.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        final float ba = Math.min(1.0f, a * 2f);
        buf.vertex(mat, -half, 0.01f, 0).color(r, g, b, ba).endVertex();
        buf.vertex(mat, half, 0.01f, 0).color(r, g, b, ba).endVertex();
        buf.vertex(mat, half, 0.01f, length).color(r, g, b, ba).endVertex();
        buf.vertex(mat, -half, 0.01f, length).color(r, g, b, ba).endVertex();
        tess.end();
    }

    private void renderCone(
            final Matrix4f mat,
            final float length,
            final float angleDeg,
            final float innerRadius,
            final float r,
            final float g,
            final float b,
            final float a) {
        final float half = (float) Math.toRadians(angleDeg * 0.5f);
        final Tessellator tess = Tessellator.getInstance();
        final BufferBuilder buf = tess.getBuilder();

        if (innerRadius <= 0.01f) {
            buf.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
            buf.vertex(mat, 0, 0, 0).color(r, g, b, a).endVertex();
            for (int i = 0; i <= CONE_SEGMENTS; i++) {
                final float seg = -half + (half * 2f * i) / CONE_SEGMENTS;
                buf.vertex(mat, (float) (Math.sin(seg) * length), 0, (float) (Math.cos(seg) * length))
                        .color(r, g, b, a).endVertex();
            }
            tess.end();
        } else {
            buf.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i <= CONE_SEGMENTS; i++) {
                final float seg = -half + (half * 2f * i) / CONE_SEGMENTS;
                final float s = (float) Math.sin(seg);
                final float c = (float) Math.cos(seg);
                buf.vertex(mat, s * length, 0, c * length).color(r, g, b, a).endVertex();
                buf.vertex(mat, s * innerRadius, 0, c * innerRadius).color(r, g, b, a).endVertex();
            }
            tess.end();
        }
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
}
