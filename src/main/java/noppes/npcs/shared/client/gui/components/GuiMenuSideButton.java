package noppes.npcs.shared.client.gui.components;


import noppes.npcs.client.CustomNpcResourceListener;
import net.minecraft.util.*;
import noppes.npcs.shared.client.gui.listeners.*;
import com.mojang.blaze3d.matrix.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;

public class GuiMenuSideButton extends GuiButtonNop
{
    public static final ResourceLocation resource;
    public boolean active;
    
    public GuiMenuSideButton(final IGuiInterface gui, final int i, final int j, final int k, final String s) {
        this(gui, i, j, k, 200, 20, s);
    }
    
    public GuiMenuSideButton(final IGuiInterface gui, final int i, final int j, final int k, final int l, final int i1, final String s) {
        super(gui, i, j, k, l, i1, s);
    }
    
    public int getYImage(final boolean flag) {
        if (this.active) {
            return 0;
        }
        return 1;
    }
    
    @Override
    public void render(final MatrixStack matrixStack, final int i, final int j, final float partialTicks) {
        if (!this.visible) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        final FontRenderer fontrenderer = minecraft.font;
        minecraft.getTextureManager().bind(GuiMenuSideButton.resource);
        final int width = this.width + (this.active ? 2 : 0);
        this.isHovered = (i >= this.x && j >= this.y && i < this.x + width && j < this.y + this.height);
        final int k = this.getYImage(this.isHovered);
        this.blit(matrixStack, this.x, this.y, 0, k * 22, width, this.height);
        String text = "";
        final float maxWidth = width * 0.75f;
        final String displayString = this.getMessage().getString();
        if (fontrenderer.width(displayString) > maxWidth) {
            for (int h = 0; h < displayString.length(); ++h) {
                final char c = displayString.charAt(h);
                if (fontrenderer.width(text + c) > maxWidth) {
                    break;
                }
                text += c;
            }
            text += "...";
        }
        else {
            text = displayString;
        }
        if (this.active) {
            drawCenteredString(matrixStack, fontrenderer, text, this.x + width / 2, this.y + (this.height - 8) / 2, 16777120);
        }
        else if (this.isHovered) {
            drawCenteredString(matrixStack, fontrenderer, text, this.x + width / 2, this.y + (this.height - 8) / 2, 16777120);
        }
        else {
            drawCenteredString(matrixStack, fontrenderer, text, this.x + width / 2, this.y + (this.height - 8) / 2, CustomNpcResourceListener.DefaultTextColor);
        }
    }
    
    public boolean mouseClicked(final double i, final double j, final int button) {
        return !this.active && super.mouseClicked(i, j, button);
    }
    
    static {
        resource = new ResourceLocation("customnpcs", "textures/gui/menusidebutton.png");
    }
}
