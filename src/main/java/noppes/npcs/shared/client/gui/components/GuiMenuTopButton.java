package noppes.npcs.shared.client.gui.components;


import noppes.npcs.client.CustomNpcResourceListener;
import net.minecraft.util.*;
import noppes.npcs.shared.client.gui.listeners.*;
import net.minecraft.client.*;
import net.minecraft.util.text.*;
import com.mojang.blaze3d.matrix.*;
import net.minecraft.util.math.vector.*;
import net.minecraft.client.gui.*;

public class GuiMenuTopButton extends GuiButtonNop
{
    public static final ResourceLocation resource;
    protected int height;
    public boolean active;
    public boolean hover;
    public boolean rotated;
    
    public GuiMenuTopButton(final IGuiInterface gui, final int i, final int j, final int k, final String s) {
        super(gui, i, j, k, s);
        this.hover = false;
        this.rotated = false;
        this.active = false;
        this.width = Minecraft.getInstance().font.width((ITextProperties)this.getMessage()) + 12;
        this.height = 20;
    }
    
    public GuiMenuTopButton(final IGuiInterface gui, final int i, final GuiButtonNop parent, final String s) {
        this(gui, i, parent.x + parent.getWidth(), parent.y, s);
    }
    
    public int getYImage(final boolean flag) {
        byte byte0 = 1;
        if (this.active) {
            byte0 = 0;
        }
        else if (flag) {
            byte0 = 2;
        }
        return byte0;
    }
    
    @Override
    public void render(final MatrixStack matrixStack, final int i, final int j, final float partialTicks) {
        if (!this.visible) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        matrixStack.pushPose();
        mc.getTextureManager().bind(GuiMenuTopButton.resource);
        final int height = this.height - (this.active ? 0 : 2);
        this.hover = (i >= this.x && j >= this.y && i < this.x + this.getWidth() && j < this.y + height);
        final int k = this.getYImage(this.hover);
        this.blit(matrixStack, this.x, this.y, 0, k * 20, this.getWidth() / 2, height);
        this.blit(matrixStack, this.x + this.getWidth() / 2, this.y, 200 - this.getWidth() / 2, k * 20, this.getWidth() / 2, height);
        final FontRenderer fontrenderer = mc.font;
        if (this.rotated) {
            matrixStack.mulPose(Vector3f.XP.rotationDegrees(90.0f));
        }
        if (this.active) {
            drawCenteredString(matrixStack, fontrenderer, this.getMessage(), this.x + this.getWidth() / 2, this.y + (height - 8) / 2, 16777120);
        }
        else if (this.hover) {
            drawCenteredString(matrixStack, fontrenderer, this.getMessage(), this.x + this.getWidth() / 2, this.y + (height - 8) / 2, 16777120);
        }
        else {
            drawCenteredString(matrixStack, fontrenderer, this.getMessage(), this.x + this.getWidth() / 2, this.y + (height - 8) / 2, CustomNpcResourceListener.DefaultTextColor);
        }
        matrixStack.popPose();
    }
    
    public boolean mouseDragged(final double p_mouseDragged_1_, final double p_mouseDragged_3_, final int p_mouseDragged_5_, final double p_mouseDragged_6_, final double p_mouseDragged_8_) {
        return false;
    }
    
    public boolean mouseReleased(final double i, final double j, final int button) {
        return false;
    }
    
    public boolean mouseClicked(final double i, final double j, final int button) {
        final boolean bo = !this.active && this.visible && this.hover;
        if (bo) {
            this.onClick(i, j);
        }
        return bo;
    }
    
    @Override
    public void onClick(final double x, final double y) {
        this.gui.buttonEvent(this);
    }
    
    static {
        resource = new ResourceLocation("customnpcs", "textures/gui/menutopbutton.png");
    }
}
